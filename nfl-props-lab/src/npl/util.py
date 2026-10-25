"""Small shared helpers: hashing, time, HTTP with bounded retries, secret redaction."""

from __future__ import annotations

import hashlib
import json
import logging
import re
import time
from datetime import UTC, datetime
from pathlib import Path
from typing import Any

import requests

from .config import LONDON, USER_AGENT

log = logging.getLogger("npl")

_SECRET_PARAMS = re.compile(r"(api[_-]?key|apikey|token|key)=([^&\s]+)", re.IGNORECASE)


def redact(text: str) -> str:
    """Remove secret query parameters from URLs and messages before logging or storing."""
    return _SECRET_PARAMS.sub(lambda m: f"{m.group(1)}=REDACTED", text)


def utcnow() -> datetime:
    return datetime.now(UTC)


def iso(dt: datetime) -> str:
    if dt.tzinfo is None:
        raise ValueError("naive datetime refused: every timestamp must carry a zone")
    return dt.astimezone(UTC).strftime("%Y-%m-%dT%H:%M:%SZ")


def parse_iso(s: str) -> datetime:
    dt = datetime.fromisoformat(s.replace("Z", "+00:00"))
    if dt.tzinfo is None:
        raise ValueError(f"timestamp without zone: {s}")
    return dt.astimezone(UTC)


def london(dt: datetime) -> str:
    """Display form in UK time; the zone abbreviation (GMT/BST) is always shown."""
    return dt.astimezone(LONDON).strftime("%a %d %b %Y %H:%M %Z")


def sha256_bytes(b: bytes) -> str:
    return hashlib.sha256(b).hexdigest()


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            h.update(chunk)
    return h.hexdigest()


def stable_json(obj: Any) -> str:
    return json.dumps(obj, sort_keys=True, separators=(",", ":"), default=str)


def sha256_json(obj: Any) -> str:
    return sha256_bytes(stable_json(obj).encode())


class FetchError(RuntimeError):
    def __init__(self, message: str, status: int | None = None):
        super().__init__(redact(message))
        self.status = status


RETRYABLE = {429, 500, 502, 503, 504}


def http_get(
    url: str,
    *,
    params: dict | None = None,
    timeout: float = 60,
    max_tries: int = 5,
    base_delay: float = 2.0,
    max_delay: float = 60.0,
    session: requests.Session | None = None,
    sleep=time.sleep,
) -> requests.Response:
    """GET with bounded exponential backoff on transient failures.

    Retries connection errors and 429/5xx; honours Retry-After (capped). Non-retryable
    4xx responses raise immediately. Secrets in the URL never reach logs or errors.
    """
    s = session or requests
    last: Exception | None = None
    for attempt in range(1, max_tries + 1):
        retry_after = None
        try:
            r = s.get(url, params=params, timeout=timeout, headers={"User-Agent": USER_AGENT})
        except requests.RequestException as e:  # connection reset, DNS, timeout
            last = FetchError(f"{type(e).__name__} fetching {url}")
        else:
            if r.status_code < 400:
                return r
            last = FetchError(f"HTTP {r.status_code} fetching {r.url}", r.status_code)
            if r.status_code not in RETRYABLE:
                raise last
            retry_after = r.headers.get("Retry-After")
        if attempt == max_tries:
            break
        delay = min(max_delay, base_delay * 2 ** (attempt - 1))
        if retry_after and retry_after.isdigit():
            delay = min(max_delay, float(retry_after))
        log.warning("retry %d/%d in %.0fs: %s", attempt, max_tries, delay, redact(str(last)))
        sleep(delay)
    assert last is not None
    raise last
