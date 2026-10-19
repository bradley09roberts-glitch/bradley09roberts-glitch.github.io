"""Polite downloader for football-data.co.uk season CSVs (see DECISIONS.md D-013).

- At most one request every `min_interval` seconds, plain identifying User-Agent.
- Complete seasons are fetched once and then served from the raw store.
- The current season is re-fetched at most every `refresh_hours`, with conditional requests.
- Every attempt is written to `fetch_log`, including cache hits and failures.
"""

import time
from collections.abc import Callable
from dataclasses import dataclass
from datetime import date, timedelta
from pathlib import Path

import httpx
from sqlalchemy.orm import Session

from footy.ingest.raw_store import latest_revision, store_raw
from footy.models import FetchLog, utcnow
from footy.seasons import season_is_complete

SOURCE = "football-data"
URL_TEMPLATE = "https://football-data.co.uk/mmz4281/{season}/{division}.csv"
USER_AGENT = "footy-personal-model/0.1 (personal non-commercial research; polite, cached)"
RETRY_STATUSES = {429, 500, 502, 503, 504}


def logical_key(season: str, division: str) -> str:
    return f"{season}/{division}"


@dataclass
class FetchResult:
    logical_key: str
    outcome: str  # new_revision | unchanged | not_modified | cached | not_found | error
    raw_file_id: int | None
    error: str | None = None


class PoliteClient:
    def __init__(
        self,
        min_interval: float = 2.0,
        retries: int = 3,
        client: httpx.Client | None = None,
        sleep: Callable[[float], None] = time.sleep,
    ):
        self.min_interval = min_interval
        self.retries = retries
        self.sleep = sleep
        self._last = 0.0
        self.client = client or httpx.Client(
            follow_redirects=True, timeout=30.0, headers={"User-Agent": USER_AGENT}
        )

    def get(self, url: str, headers: dict[str, str] | None = None) -> httpx.Response:
        last_exc: Exception | None = None
        for attempt in range(self.retries):
            wait = self.min_interval - (time.monotonic() - self._last)
            if wait > 0:
                self.sleep(wait)
            self._last = time.monotonic()
            try:
                resp = self.client.get(url, headers=headers)
            except httpx.TransportError as exc:
                last_exc = exc
            else:
                if resp.status_code not in RETRY_STATUSES:
                    return resp
                last_exc = httpx.HTTPStatusError(
                    f"HTTP {resp.status_code}", request=resp.request, response=resp
                )
            if attempt < self.retries - 1:
                self.sleep(2.0 * 2**attempt)
        assert last_exc is not None
        raise last_exc


def fetch_season_file(
    session: Session,
    client: PoliteClient,
    raw_dir: Path,
    *,
    season: str,
    division: str,
    today: date,
    job_run_id: int | None = None,
    force: bool = False,
    refresh_hours: float = 12.0,
) -> FetchResult:
    key = logical_key(season, division)
    url = URL_TEMPLATE.format(season=season, division=division)
    latest = latest_revision(session, SOURCE, key)

    def log(outcome: str, status: int | None = None, raw_id=None, error=None) -> FetchResult:
        session.add(
            FetchLog(
                job_run_id=job_run_id,
                source=SOURCE,
                logical_key=key,
                url=url,
                requested_at=utcnow(),
                http_status=status,
                outcome=outcome,
                raw_file_id=raw_id,
                error=error,
            )
        )
        session.flush()
        return FetchResult(key, outcome, raw_id, error)

    if latest is not None and not force:
        if season_is_complete(season, today):
            return log("cached", raw_id=latest.id)
        if utcnow() - latest.retrieved_at < timedelta(hours=refresh_hours):
            return log("cached", raw_id=latest.id)

    headers = {}
    if latest is not None and not force:
        if latest.http_etag:
            headers["If-None-Match"] = latest.http_etag
        if latest.http_last_modified:
            headers["If-Modified-Since"] = latest.http_last_modified
    try:
        resp = client.get(url, headers=headers)
    except Exception as exc:  # logged, reported by the job, never silently dropped
        return log("error", error=f"{type(exc).__name__}: {exc}")

    if resp.status_code == 304 and latest is not None:
        return log("not_modified", 304, latest.id)
    if resp.status_code == 404:
        return log("not_found", 404)
    if resp.status_code != 200:
        return log("error", resp.status_code, error=f"HTTP {resp.status_code}")
    if not _looks_like_fd_csv(resp.content):
        return log("error", 200, error="response is not a football-data CSV")

    stored = store_raw(
        session,
        raw_dir,
        source=SOURCE,
        logical_key=key,
        content=resp.content,
        retrieved_at=utcnow(),
        url=str(resp.url),
        suffix=".csv",
        http_last_modified=resp.headers.get("Last-Modified"),
        http_etag=resp.headers.get("ETag"),
    )
    return log("new_revision" if stored.created else "unchanged", 200, stored.raw.id)


def _looks_like_fd_csv(content: bytes) -> bool:
    head = content[:200].lstrip(b"\xef\xbb\xbf")
    return head.startswith(b"Div,")
