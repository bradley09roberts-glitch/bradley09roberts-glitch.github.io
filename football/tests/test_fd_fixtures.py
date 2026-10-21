"""fixtures.csv parsing. SYNTHETIC rows (fictional teams, made-up prices) in football-data's
fixtures.csv column layout; used only by these tests."""

import logging
from datetime import UTC, datetime

import pandas as pd
import pytest
from sqlalchemy.orm import Session

from fdfixtures import REFERENCE
from footy.ingest import fd_fixtures
from footy.ingest.fd_fixtures import parse
from footy.ingest.fd_import import sync_reference
from footy.ingest.raw_store import store_raw
from footy.modeling.lab.market_blend import MarketTilt, combine_prices

HEADER = (
    "Div,Date,Time,HomeTeam,AwayTeam,Referee,B365H,B365D,B365A,AvgH,AvgD,AvgA,"
    "B365>2.5,B365<2.5,Avg>2.5,Avg<2.5"
)
ALIASES = {"Testtown": "eng-testtown", "Sampleton": "eng-sampleton", "Mockford": "eng-mockford"}
WHEN = datetime(2026, 10, 23, 16, 30, tzinfo=UTC)


def _csv(*rows: str) -> bytes:
    return ("﻿" + "\r\n".join([HEADER, *rows]) + "\r\n").encode("utf-8")


def test_parses_prices_and_maps_teams():
    df, report = parse(
        _csv(
            "E0,24/10/2026,15:00,Testtown,Sampleton,,2.10,3.40,3.50,2.05,3.30,3.60,1.90,1.95,1.88,1.97",
            "E2,24/10/2026,15:00,Nowhere,Elsewhere,,2.0,3.4,3.6,2.0,3.4,3.6,1.9,1.9,1.9,1.9",
        ),
        ALIASES,
        WHEN,
    )
    assert report["rows_in_covered_divisions"] == 1  # E2 is not a covered division
    assert report["fixtures_with_prices"] == 1
    assert set(df.bookmaker) == {"B365", "Avg"}
    assert set(df.market_key) == {"1X2", "OU_2.5"}
    row = df[(df.bookmaker == "B365") & (df.selection == "H")].iloc[0]
    assert (row.home_team, row.away_team, row.date, row.price) == (
        "eng-testtown",
        "eng-sampleton",
        "2026-10-24",
        2.10,
    )
    assert (df.quote_time_utc == WHEN).all()


def test_unmapped_names_and_bad_sets_are_reported_not_used():
    df, report = parse(
        _csv(
            "E0,24/10/2026,15:00,Testtown,Unknown FC,,2.10,3.40,3.50,,,,,,,",
            "E0,24/10/2026,17:30,Mockford,Testtown,,1.10,1.20,1.30,2.05,3.30,3.60,,,,",
        ),
        ALIASES,
        WHEN,
    )
    assert report["unmapped_names"] == ["Unknown FC"]
    assert len(report["dropped_price_sets"]) == 1  # B365 overround far outside 0.95-1.30
    assert set(df.bookmaker) == {"Avg"}
    assert set(df.market_key) == {"1X2"}


def test_publication_time_is_last_modified_capped_at_retrieval():
    from footy.ingest.fd_fixtures import published_time

    got = published_time("Tue, 20 Oct 2026 10:57:39 GMT", WHEN)
    assert got == datetime(2026, 10, 20, 10, 57, 39, tzinfo=UTC)
    assert published_time("Sat, 24 Oct 2026 10:00:00 GMT", WHEN) == WHEN  # clock skew
    naive = WHEN.replace(tzinfo=None)  # a naive UTC time, as the database may return it
    assert published_time("Fri, 23 Oct 2026 13:10:00 GMT", naive) == datetime(
        2026, 10, 23, 13, 10, tzinfo=UTC
    )


@pytest.mark.parametrize("header", [None, "", "not a date", "Tue, 20 Oct 2026", "20/10/2026 10:57"])
def test_no_readable_last_modified_means_unknown_not_the_retrieval_time(header):
    """The retrieval time can't stand in: a file can sit on the site for days before it is
    fetched, so its prices could be from an older collection."""
    from footy.ingest.fd_fixtures import published_time

    assert published_time(header, WHEN) is None
    assert published_time(header, WHEN.replace(tzinfo=None)) is None


def test_rows_carry_the_publication_time_and_bad_dates_are_reported():
    published = datetime(2026, 10, 23, 12, 0, tzinfo=UTC)
    df, report = parse(
        _csv(
            "E0,24/10/2026,15:00,Testtown,Sampleton,,2.10,3.40,3.50,2.05,3.30,3.60,1.90,1.95,1.88,1.97",
            "E0,,15:00,Mockford,Testtown,,2.10,3.40,3.50,2.05,3.30,3.60,1.90,1.95,1.88,1.97",
            "E0,25/10/26,15:00,Sampleton,Mockford,,2.10,3.40,3.50,2.05,3.30,3.60,1.90,1.95,1.88,1.97",
        ),
        ALIASES,
        WHEN,
        published,
    )
    assert report["unreadable_dates"] == ["E0 Mockford v Testtown: ''"]
    assert set(df.date) == {"2026-10-24", "2026-10-25"}  # a two-digit year is read too
    assert (df.published_utc == published).all()
    assert (df.quote_time_utc == WHEN).all()


def test_rows_without_a_publication_time_leave_it_empty():
    df, _ = parse(
        _csv("E0,24/10/2026,15:00,Testtown,Sampleton,,2.10,3.40,3.50,2.05,3.30,3.60,,,,"),
        ALIASES,
        WHEN,
    )
    assert len(df) == 6 and df.published_utc.isna().all()


ROW = "E0,24/10/2026,15:00,Testtown,Sampleton,,2.10,3.40,3.50,2.05,3.30,3.60,1.90,1.95,1.88,1.97"


class Fixed:
    """A base model with one Poisson grid for every fixture."""

    name = "fixed"

    def fit(self, history, cutoff):
        pass

    def predict(self, fixtures):
        from footy.modeling.grid import markets_from_grid, poisson_grid

        n = len(fixtures)
        return markets_from_grid(poisson_grid([1.4] * n, [1.1] * n))

    def params(self):
        return {}


def _snapshot(engine, tmp_path, last_modified):
    with Session(engine) as s:
        sync_reference(s, *REFERENCE)
        store_raw(
            s,
            tmp_path / "raw",
            source=fd_fixtures.SOURCE,
            logical_key=fd_fixtures.LOGICAL_KEY,
            content=_csv(ROW),
            retrieved_at=WHEN,
            http_last_modified=last_modified,
        )
        s.commit()
    return fd_fixtures.latest_snapshot(engine, tmp_path / "raw")


def _used(snap):
    """Whether a live run on Friday 16:45 UTC uses the snapshot's prices for the Saturday
    fixture, and why not if it doesn't."""
    book = combine_prices(pd.DataFrame(), snap)
    m = MarketTilt(Fixed, 1.0, 1.0, "t", prices=lambda: book)
    m.fit(pd.DataFrame(), pd.Timestamp("2026-10-23", tz="UTC"))
    m.decision_time = datetime(2026, 10, 23, 16, 45, tzinfo=UTC)
    m.predict(
        pd.DataFrame(
            {
                "division": ["E0"],
                "date": pd.to_datetime(["2026-10-24"]),
                "home_team": ["eng-testtown"],
                "away_team": ["eng-sampleton"],
                "kickoff_utc": pd.to_datetime(["2026-10-24 14:00"], utc=True),
            }
        )
    )
    reason = m.last_used.refused[0]
    return bool(m.last_used.used_1x2[0]), None if pd.isna(reason) else reason


def test_a_file_without_last_modified_is_not_used_live(migrated_engine, tmp_path, caplog):
    """SYNTHETIC fixtures.csv in the raw store, retrieved on Friday after the collection but
    with no Last-Modified header: its prices are refused, and the reason is logged."""
    with caplog.at_level(logging.WARNING, logger="footy.ingest.fd_fixtures"):
        snap = _snapshot(migrated_engine, tmp_path, None)
    assert snap.attrs["published_at"] is None
    assert "no Last-Modified header" in snap.attrs["live_use_refused"]
    assert snap.attrs["report"]["live_use_refused"] == snap.attrs["live_use_refused"]
    assert "will not be used for live predictions" in caplog.text
    assert snap.published_utc.isna().all() and len(snap) == 10
    assert _used(snap) == (False, "publication time unknown (no readable Last-Modified)")


def test_an_unreadable_last_modified_is_not_used_live(migrated_engine, tmp_path, caplog):
    with caplog.at_level(logging.WARNING, logger="footy.ingest.fd_fixtures"):
        snap = _snapshot(migrated_engine, tmp_path, "Friday afternoon")
    assert "unreadable Last-Modified header 'Friday afternoon'" in snap.attrs["live_use_refused"]
    assert "Friday afternoon" in caplog.text
    assert _used(snap)[0] is False


def test_the_same_file_with_last_modified_is_used(migrated_engine, tmp_path, caplog):
    with caplog.at_level(logging.WARNING, logger="footy.ingest.fd_fixtures"):
        snap = _snapshot(migrated_engine, tmp_path, "Fri, 23 Oct 2026 13:10:00 GMT")
    assert snap.attrs["published_at"] == "2026-10-23T13:10:00+00:00"
    assert "live_use_refused" not in snap.attrs and "live_use_refused" not in snap.attrs["report"]
    assert caplog.text == ""
    assert _used(snap) == (True, None)
