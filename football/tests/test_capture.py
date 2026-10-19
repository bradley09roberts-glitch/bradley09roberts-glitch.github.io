"""Capture scheduling and storage, driven by a FAKE provider with SYNTHETIC prices."""

from datetime import UTC, date, datetime, timedelta

from sqlalchemy import select
from sqlalchemy.orm import Session

from footy.capture import plan, run_capture
from footy.models import CaptureEvent, JobRun, OddsSnapshot, ProviderFixture, RawFile
from footy.providers.base import (
    FetchedFixtures,
    FetchedOdds,
    ProviderQuote,
    RawPage,
)
from footy.providers.base import (
    ProviderFixture as PF,
)

KICKOFF = datetime(2026, 10, 24, 14, 0, tzinfo=UTC)


def at(**delta) -> datetime:
    """A time relative to kickoff, e.g. at(hours=-2) is two hours before."""
    return KICKOFF + timedelta(**delta)


def test_nothing_due_before_48h():
    assert plan([("1", KICKOFF)], set(), at(hours=-48, seconds=-1)) == []


def test_each_target_due_in_its_window():
    for now, target in [
        (at(hours=-48), "T-48h"),
        (at(hours=-24, minutes=-1), "T-48h"),
        (at(hours=-2, minutes=-5), "T-24h"),
        (at(minutes=-16), "T-2h"),
        (at(minutes=-1), "T-15m"),
    ]:
        done = {("1", t) for t in ("T-48h", "T-24h", "T-2h", "T-15m") if t != target}
        (p,) = plan([("1", KICKOFF)], done, now)
        assert (p.target, p.action) == (target, "capture"), now


def test_window_passed_is_recorded_as_missed():
    actions = {p.target: p.action for p in plan([("1", KICKOFF)], set(), at(hours=-1))}
    assert actions == {"T-48h": "miss", "T-24h": "miss", "T-2h": "capture"}


def test_never_captures_at_or_after_kickoff():
    assert {p.action for p in plan([("1", KICKOFF)], set(), KICKOFF)} == {"miss"}


class FakeProvider:
    """Test double. Every price here is invented."""

    name = "fake"

    def __init__(self, quotes=True, fail=False):
        self.quotes, self.fail = quotes, fail
        self.odds_calls = 0

    def upcoming_fixtures(self, start: date, end: date) -> FetchedFixtures:
        fx = PF("1", "E0", KICKOFF, "Testtown FC", "Sampleton FC", "1")
        return FetchedFixtures([fx], [RawPage("fixtures", "fake://fixtures", b'{"data":[1]}')])

    def prematch_odds(self, fid: str) -> FetchedOdds:
        self.odds_calls += 1
        if self.fail:
            raise RuntimeError("provider down")
        if not self.quotes:
            return FetchedOdds([], [RawPage(f"odds/{fid}", "fake://odds", b'{"data":[]}')])
        qt = at(hours=-3)
        quotes = [
            ProviderQuote("fake:1", "1X2", None, "H", 2.1, qt, page=0),
            ProviderQuote("fake:1", "BTTS", None, "Y", 1.8, qt, page=1),
        ]
        pages = [
            RawPage(f"odds/{fid}/1", "fake://odds/1", b'{"data":["1x2"]}'),
            RawPage(f"odds/{fid}/14", "fake://odds/14", b'{"data":["btts"]}'),
        ]
        return FetchedOdds(quotes, pages)


def test_capture_stores_snapshots_with_times_and_raw_pages(migrated_engine, settings):
    detail = run_capture(migrated_engine, settings, provider=FakeProvider(), now=at(hours=-2))
    assert detail["outcomes"] == {"missed": 2, "captured": 1}
    with Session(migrated_engine) as s:
        snaps = list(s.scalars(select(OddsSnapshot)))
        assert {(x.market_key, x.selection, x.target) for x in snaps} == {
            ("1X2", "H", "T-2h"),
            ("BTTS", "Y", "T-2h"),
        }
        assert all(x.captured_at_utc == at(hours=-2) for x in snaps)
        assert all(x.quote_time_utc == at(hours=-3) for x in snaps)
        pages = {x.selection: s.get(RawFile, x.raw_file_id).logical_key for x in snaps}
        assert pages == {"H": "odds/1/1", "Y": "odds/1/14"}
        fx = s.get(ProviderFixture, ("fake", "1"))
        assert fx.home_name == "Testtown FC" and fx.home_team is None  # no alias yet


def test_capture_is_not_repeated_once_done(migrated_engine, settings):
    provider = FakeProvider()
    run_capture(migrated_engine, settings, provider=provider, now=at(hours=-2))
    run_capture(migrated_engine, settings, provider=provider, now=at(hours=-2, minutes=5))
    assert provider.odds_calls == 1


def test_errors_retry_until_the_window_closes(migrated_engine, settings):
    failing = FakeProvider(fail=True)
    run_capture(migrated_engine, settings, provider=failing, now=at(minutes=-14))
    run_capture(migrated_engine, settings, provider=failing, now=at(minutes=-9))
    run_capture(migrated_engine, settings, provider=failing, now=at(minutes=1))
    with Session(migrated_engine) as s:
        events = [
            (e.target, e.status)
            for e in s.scalars(select(CaptureEvent).order_by(CaptureEvent.id))
            if e.target == "T-15m"
        ]
        runs = [r.status for r in s.scalars(select(JobRun).where(JobRun.job == "odds_capture"))]
    assert events == [("T-15m", "error"), ("T-15m", "error"), ("T-15m", "missed")]
    assert runs[:2] == ["partial", "partial"]


def test_no_odds_is_final_and_recorded(migrated_engine, settings):
    detail = run_capture(
        migrated_engine, settings, provider=FakeProvider(quotes=False), now=at(hours=-30)
    )
    assert detail["outcomes"] == {"no_odds": 1}


def test_no_provider_is_logged_not_failed(migrated_engine, settings):
    detail = run_capture(migrated_engine, settings, now=at(hours=-2))
    assert detail["status"] == "no_provider"
    with Session(migrated_engine) as s:
        run = s.scalar(select(JobRun).where(JobRun.job == "odds_capture"))
        assert run.status == "ok" and "no_provider" in run.detail
