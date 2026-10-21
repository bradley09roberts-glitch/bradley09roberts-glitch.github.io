"""Polite fetching of English Wikipedia pages through the MediaWiki Action API.

- One request every `min_interval` seconds (2 s by default), identifying User-Agent.
- HTTP 429 and 5xx are retried with exponential backoff, honouring `Retry-After`.
- Every successful response is stored in the append-only raw store before anything parses it.
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
USER_AGENT = "footy-personal-model/0.1 (personal non-commercial research)"
RETRY_STATUSES = {429, 500, 502, 503, 504}


class WikiClient:
    """HTTP client with per-host pacing and Retry-After-aware exponential backoff."""

    def __init__(
        self,
        min_interval: float = 2.0,
        retries: int = 8,
        max_backoff: float = 300.0,
        client: httpx.Client | None = None,
        sleep: Callable[[float], None] = time.sleep,
    ):
        self.min_interval = min_interval
        self.retries = retries
        self.max_backoff = max_backoff
        self.sleep = sleep
        self._last = 0.0
        self.client = client or httpx.Client(
            follow_redirects=True, timeout=60.0, headers={"User-Agent": USER_AGENT}
        )

    def get(self, url: str, params: dict[str, str] | None = None) -> httpx.Response:
        last: httpx.Response | Exception | None = None
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
                if resp.status_code not in RETRY_STATUSES:
                    return resp
                last = resp
                retry_after = _retry_after_seconds(resp)
            if attempt < self.retries - 1:
                backoff = min(self.max_backoff, 4.0 * 2**attempt)
                self.sleep(max(backoff, retry_after or 0.0))
        if isinstance(last, httpx.Response):
            return last
        assert last is not None
        raise last


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
    outcome: str  # new_revision | unchanged | not_found | error
    raw_file_id: int | None
    revid: int | None = None
    error: str | None = None


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
        return WikiFetchResult(page.logical_key, page.title, outcome, raw_id, revid, error)

    try:
        resp = client.get(API_URL, params=parse_params(page.title))
    except Exception as exc:  # logged and reported, never silently dropped
        return log("error", None, error=f"{type(exc).__name__}: {exc}")
    url = str(resp.url)
    if resp.status_code != 200:
        return log("error", resp.status_code, error=f"HTTP {resp.status_code}", url=url)
    try:
        body = json.loads(resp.content)
    except ValueError:
        return log("error", 200, error="response is not JSON", url=url)
    if "error" in body:
        code = body["error"].get("code", "unknown")
        outcome = "not_found" if code == "missingtitle" else "error"
        return log(outcome, 200, error=f"API error {code}", url=url)

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
        return WikiFetchResult(logical_key, url, outcome, raw_id, None, error)

    try:
        resp = client.get(url)
    except Exception as exc:  # logged and reported, never silently dropped
        return log("error", None, error=f"{type(exc).__name__}: {exc}")
    if resp.status_code != 200:
        return log("error", resp.status_code, error=f"HTTP {resp.status_code}")
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
