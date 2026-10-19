from datetime import date

import httpx
from sqlalchemy import select
from sqlalchemy.orm import Session

from fdfixtures import csv_bytes
from footy.ingest.fd_download import USER_AGENT, PoliteClient, fetch_season_file
from footy.models import FetchLog

TODAY = date(2026, 10, 19)


class Recorder:
    def __init__(self, responses):
        self.responses = list(responses)
        self.requests: list[httpx.Request] = []
        self.sleeps: list[float] = []

    def handler(self, request: httpx.Request) -> httpx.Response:
        self.requests.append(request)
        return self.responses.pop(0)

    def client(self) -> PoliteClient:
        http = httpx.Client(
            transport=httpx.MockTransport(self.handler), headers={"User-Agent": USER_AGENT}
        )
        return PoliteClient(min_interval=2.0, client=http, sleep=self.sleeps.append)


def fetch(engine, settings, rec, season, **kw):
    with Session(engine) as s:
        res = fetch_season_file(
            s, rec.client(), settings.raw_dir, season=season, division="E0", today=TODAY, **kw
        )
        s.commit()
        return res


def test_complete_season_downloaded_once_then_cached(migrated_engine, settings):
    rec = Recorder([httpx.Response(200, content=csv_bytes())])
    assert fetch(migrated_engine, settings, rec, "2425").outcome == "new_revision"
    assert fetch(migrated_engine, settings, rec, "2425").outcome == "cached"
    assert len(rec.requests) == 1
    assert rec.requests[0].headers["User-Agent"] == USER_AGENT
    assert str(rec.requests[0].url) == "https://football-data.co.uk/mmz4281/2425/E0.csv"


def test_current_season_conditional_request(migrated_engine, settings):
    rec = Recorder(
        [
            httpx.Response(200, content=csv_bytes(), headers={"ETag": '"v1"'}),
            httpx.Response(304),
            httpx.Response(200, content=csv_bytes()),
        ]
    )
    assert fetch(migrated_engine, settings, rec, "2627").outcome == "new_revision"
    assert fetch(migrated_engine, settings, rec, "2627").outcome == "cached"  # < 12 h old
    assert fetch(migrated_engine, settings, rec, "2627", refresh_hours=0).outcome == "not_modified"
    assert rec.requests[1].headers["If-None-Match"] == '"v1"'
    # Same bytes again: recorded as unchanged, no new revision.
    assert fetch(migrated_engine, settings, rec, "2627", force=True).outcome == "unchanged"


def test_failures_are_logged_not_raised(migrated_engine, settings):
    rec = Recorder(
        [
            httpx.Response(404),
            httpx.Response(200, content=b"<html>blocked</html>"),
            httpx.Response(503),
            httpx.Response(503),
            httpx.Response(503),
        ]
    )
    assert fetch(migrated_engine, settings, rec, "2425").outcome == "not_found"
    assert fetch(migrated_engine, settings, rec, "2324").outcome == "error"
    res = fetch(migrated_engine, settings, rec, "2223")
    assert res.outcome == "error" and "503" in res.error
    with Session(migrated_engine) as s:
        outcomes = [f.outcome for f in s.scalars(select(FetchLog).order_by(FetchLog.id))]
    assert outcomes == ["not_found", "error", "error"]


def test_requests_are_spaced_and_retries_back_off(migrated_engine, settings):
    rec = Recorder([httpx.Response(503), httpx.Response(200, content=csv_bytes())])
    fetch(migrated_engine, settings, rec, "2425")
    # retry back-off of 2 s after the 503, then the 2 s politeness gap before the retry
    assert rec.sleeps[0] == 2.0 and 1.9 < rec.sleeps[1] <= 2.0
