"""Polite downloader for football-data.co.uk CSVs (see DECISIONS.md D-013).

- At most one request every `min_interval` seconds, plain identifying User-Agent; HTTP 429
  and 5xx are retried with back-off, honouring `Retry-After`.
- Complete seasons are fetched once and then served from the raw store.
- The current season is re-fetched at most every `refresh_hours`, with conditional requests.
  So is each "extra league" file (new/{code}.csv), which holds every season including the
  current one.
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
# "Extra leagues": one file per league with every season, in a different column layout.
EXTRA_SOURCE = "football-data-extra"
EXTRA_URL_TEMPLATE = "https://football-data.co.uk/new/{code}.csv"
USER_AGENT = "footy-personal-model/0.1 (personal non-commercial research; polite, cached)"
RETRY_STATUSES = {429, 500, 502, 503, 504}
MAX_RETRY_AFTER = 300.0  # seconds; a longer Retry-After fails the request instead


def logical_key(season: str, division: str) -> str:
    return f"{season}/{division}"


def extra_logical_key(code: str) -> str:
    return f"new/{code}"


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
                backoff = 2.0 * 2**attempt
                retry_after = _retry_after(last_exc)
                if retry_after is not None:
                    if retry_after > MAX_RETRY_AFTER:
                        break
                    backoff = max(backoff, retry_after)
                self.sleep(backoff)
        assert last_exc is not None
        raise last_exc


def _retry_after(exc: Exception | None) -> float | None:
    """Seconds asked for by a 429/503 response's Retry-After header, if it gives a number."""
    resp = getattr(exc, "response", None)
    value = resp.headers.get("Retry-After") if resp is not None else None
    try:
        return float(value) if value is not None else None
    except ValueError:
        return None


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
    """One main-layout season file (mmz4281/{season}/{division}.csv)."""
    return _fetch_file(
        session,
        client,
        raw_dir,
        source=SOURCE,
        key=logical_key(season, division),
        url=URL_TEMPLATE.format(season=season, division=division),
        final=season_is_complete(season, today),
        looks_ok=_looks_like_fd_csv,
        job_run_id=job_run_id,
        force=force,
        refresh_hours=refresh_hours,
    )


def fetch_extra_league_file(
    session: Session,
    client: PoliteClient,
    raw_dir: Path,
    *,
    code: str,
    job_run_id: int | None = None,
    force: bool = False,
    refresh_hours: float = 12.0,
) -> FetchResult:
    """One extra-league file (new/{code}.csv). It always includes the current season, so it
    is never final: it is re-fetched at most every `refresh_hours`."""
    return _fetch_file(
        session,
        client,
        raw_dir,
        source=EXTRA_SOURCE,
        key=extra_logical_key(code),
        url=EXTRA_URL_TEMPLATE.format(code=code),
        final=False,
        looks_ok=_looks_like_extra_csv,
        job_run_id=job_run_id,
        force=force,
        refresh_hours=refresh_hours,
    )


def _fetch_file(
    session: Session,
    client: PoliteClient,
    raw_dir: Path,
    *,
    source: str,
    key: str,
    url: str,
    final: bool,
    looks_ok: Callable[[bytes], bool],
    job_run_id: int | None,
    force: bool,
    refresh_hours: float,
) -> FetchResult:
    latest = latest_revision(session, source, key)

    def log(outcome: str, status: int | None = None, raw_id=None, error=None) -> FetchResult:
        session.add(
            FetchLog(
                job_run_id=job_run_id,
                source=source,
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
        if final:
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
    if not looks_ok(resp.content):
        return log("error", 200, error="response is not a football-data CSV")

    stored = store_raw(
        session,
        raw_dir,
        source=source,
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


def _looks_like_extra_csv(content: bytes) -> bool:
    head = content[:200].lstrip(b"\xef\xbb\xbf")
    return head.startswith(b"Country,")
