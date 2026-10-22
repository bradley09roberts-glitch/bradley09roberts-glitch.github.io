"""Polite fetching of English Wikipedia pages through the MediaWiki Action API.

- One request every `min_interval` seconds (2 s by default), identifying User-Agent.
- HTTP 5xx and network errors are retried with exponential backoff. HTTP 429 is retried once,
  only when `Retry-After` asks for at most `max_retry_wait` seconds; otherwise the 429 is
  returned, and a sweep over several pages stops at the first 403 or 429 (never a loop).
- Every successful response is stored in the append-only raw store before anything parses it.
- Every attempt that failed is written to `fetch_log` with its HTTP status and the first 500
  bytes of the response body, and a fetch that succeeded only after retries says so.
"""

import json
import time
from collections.abc import Callable
from dataclasses import dataclass
from pathlib import Path

import httpx
from sqlalchemy.orm import Session

from footy.ingest.raw_store import store_raw
from footy.models import FetchLog, utcnow

SOURCE = "wikipedia"
API_URL = "https://en.wikipedia.org/w/api.php"
# Wikimedia's User-Agent policy asks for contact information; the project's repository is it.
USER_AGENT = (
    "footy-personal-model/0.1 "
    "(https://github.com/bradley09roberts-glitch/bradley09roberts-glitch.github.io; "
    "personal non-commercial research) python-httpx"
)
RETRY_STATUSES = {500, 502, 503, 504}
# A refusal: the server does not want more requests from this client now. A sweep over several
# pages stops at the first one (as footy.media.commons does).
REFUSED_STATUSES = {403, 429}
ERROR_BODY_BYTES = 500  # of an error response, kept in fetch_log.error


class WikiClient:
    """HTTP client with per-host pacing and Retry-After-aware exponential backoff."""

    def __init__(
        self,
        min_interval: float = 2.0,
        retries: int = 8,
        max_backoff: float = 300.0,
        client: httpx.Client | None = None,
        sleep: Callable[[float], None] = time.sleep,
        max_retry_wait: float = 60.0,
    ):
        self.min_interval = min_interval
        self.retries = retries
        self.max_backoff = max_backoff
        self.max_retry_wait = max_retry_wait
        self.sleep = sleep
        self._last = 0.0
        # The responses (or network errors) of the last get() that were retried, oldest first.
        self.retried: list[str] = []
        # The first refusal (HTTP 403 or 429) this client got, e.g. "ucl/2627/qualifying: HTTP
        # 403": sweeps sharing the client ask nothing more (footy.europe.build_2627).
        self.refused: str | None = None
        self.client = client or httpx.Client(
            follow_redirects=True, timeout=60.0, headers={"User-Agent": USER_AGENT}
        )

    def get(self, url: str, params: dict[str, str] | None = None) -> httpx.Response:
        last: httpx.Response | Exception | None = None
        self.retried = []
        for attempt in range(self.retries):
            wait = self.min_interval - (time.monotonic() - self._last)
            if wait > 0:
                self.sleep(wait)
            self._last = time.monotonic()
            try:
                resp = self.client.get(url, params=params)
            except httpx.TransportError as exc:
                last = exc
                retry_after = None
            else:
                retry_after = _retry_after_seconds(resp)
                if resp.status_code == 429:
                    # Rate limited: wait once, and only as long as the server asks if that is
                    # short; never retry in a loop.
                    if (
                        attempt == 0
                        and retry_after is not None
                        and retry_after <= self.max_retry_wait
                    ):
                        self.retried.append(describe(resp))
                        self.sleep(retry_after)
                        continue
                    return resp
                if resp.status_code not in RETRY_STATUSES:
                    return resp
                last = resp
            if attempt < self.retries - 1:
                self.retried.append(describe(last))
                backoff = min(self.max_backoff, 4.0 * 2**attempt)
                self.sleep(max(backoff, retry_after or 0.0))
        if isinstance(last, httpx.Response):
            return last
        assert last is not None
        raise last


def describe(resp: httpx.Response | Exception) -> str:
    """ "HTTP 429 (Retry-After 60)", or the network error's name."""
    if isinstance(resp, Exception):
        return f"{type(resp).__name__}: {resp}"
    retry_after = resp.headers.get("Retry-After")
    return f"HTTP {resp.status_code}" + (f" (Retry-After {retry_after})" if retry_after else "")


def error_text(resp: httpx.Response) -> str:
    """The status, any Retry-After, and the first 500 bytes of the body of an error response
    (a 403 from a proxy or a block page says who refused, which the status alone doesn't)."""
    body = resp.content[:ERROR_BODY_BYTES].decode("utf-8", errors="replace")
    return f"{describe(resp)}: {body}" if body else describe(resp)


def _retry_after_seconds(resp: httpx.Response) -> float | None:
    value = resp.headers.get("Retry-After")
    if value is None:
        return None
    try:
        return float(value)
    except ValueError:
        return None


@dataclass(frozen=True)
class WikiPage:
    logical_key: str  # e.g. "ucl/2627/league-phase"
    title: str


@dataclass
class WikiFetchResult:
    logical_key: str
    title: str
    outcome: str  # new_revision | unchanged | not_found | error | skipped (not requested)
    raw_file_id: int | None
    revid: int | None = None
    error: str | None = None
    http_status: int | None = None


def _with_retries(error: str | None, client: WikiClient) -> str | None:
    """`error`, plus the responses that were retried before the final one."""
    if not client.retried:
        return error
    note = "after retrying " + ", ".join(client.retried)
    return f"{error}; {note}" if error else note


def parse_params(title: str) -> dict[str, str]:
    """`action=parse` parameters returning the page's current wikitext and revision id."""
    return {
        "action": "parse",
        "page": title,
        "prop": "wikitext|revid|displaytitle",
        "redirects": "1",
        "format": "json",
        "formatversion": "2",
    }


def fetch_wiki_page(
    session: Session,
    client: WikiClient,
    raw_dir: Path,
    page: WikiPage,
    *,
    job_run_id: int | None = None,
) -> WikiFetchResult:
    """Fetch one page's wikitext and store the JSON response verbatim in the raw store."""

    def log(outcome: str, status: int | None, raw_id=None, error=None, revid=None, url=None):
        error = _with_retries(error, client)
        session.add(
            FetchLog(
                job_run_id=job_run_id,
                source=SOURCE,
                logical_key=page.logical_key,
                url=url or API_URL,
                requested_at=utcnow(),
                http_status=status,
                outcome=outcome,
                raw_file_id=raw_id,
                error=error,
            )
        )
        session.flush()
        return WikiFetchResult(page.logical_key, page.title, outcome, raw_id, revid, error, status)

    client.retried = []
    try:
        resp = client.get(API_URL, params=parse_params(page.title))
    except Exception as exc:  # logged and reported, never silently dropped
        return log("error", None, error=f"{type(exc).__name__}: {exc}")
    url = str(resp.url)
    if resp.status_code != 200:
        if resp.status_code in REFUSED_STATUSES and client.refused is None:
            client.refused = f"{page.logical_key}: HTTP {resp.status_code}"
        return log("error", resp.status_code, error=error_text(resp), url=url)
    try:
        body = json.loads(resp.content)
    except ValueError:
        return log("error", 200, error="response is not JSON: " + error_text(resp), url=url)
    if "error" in body:
        code = body["error"].get("code", "unknown")
        outcome = "not_found" if code == "missingtitle" else "error"
        detail = json.dumps(body["error"], ensure_ascii=False)[:ERROR_BODY_BYTES]
        return log(outcome, 200, error=f"API error {code}: {detail}", url=url)

    stored = store_raw(
        session,
        raw_dir,
        source=SOURCE,
        logical_key=page.logical_key,
        content=resp.content,
        retrieved_at=utcnow(),
        url=url,
        suffix=".json",
    )
    revid = body.get("parse", {}).get("revid")
    outcome = "new_revision" if stored.created else "unchanged"
    return log(outcome, 200, stored.raw.id, revid=revid, url=url)


def fetch_and_store(
    session: Session,
    client: WikiClient,
    raw_dir: Path,
    *,
    source: str,
    logical_key: str,
    url: str,
    suffix: str = "",
    job_run_id: int | None = None,
) -> WikiFetchResult:
    """Fetch any URL politely and store a 200 response verbatim (used for second sources)."""

    def log(outcome: str, status: int | None, raw_id=None, error=None):
        error = _with_retries(error, client)
        session.add(
            FetchLog(
                job_run_id=job_run_id,
                source=source,
                logical_key=logical_key,
                url=url,
                requested_at=utcnow(),
                http_status=status,
                outcome=outcome,
                raw_file_id=raw_id,
                error=error,
            )
        )
        session.flush()
        return WikiFetchResult(logical_key, url, outcome, raw_id, None, error, status)

    client.retried = []
    try:
        resp = client.get(url)
    except Exception as exc:  # logged and reported, never silently dropped
        return log("error", None, error=f"{type(exc).__name__}: {exc}")
    if resp.status_code != 200:
        if resp.status_code in REFUSED_STATUSES and client.refused is None:
            client.refused = f"{logical_key}: HTTP {resp.status_code}"
        return log("error", resp.status_code, error=error_text(resp))
    stored = store_raw(
        session,
        raw_dir,
        source=source,
        logical_key=logical_key,
        content=resp.content,
        retrieved_at=utcnow(),
        url=str(resp.url),
        suffix=suffix,
        http_last_modified=resp.headers.get("Last-Modified"),
        http_etag=resp.headers.get("ETag"),
    )
    return log("new_revision" if stored.created else "unchanged", 200, stored.raw.id)
