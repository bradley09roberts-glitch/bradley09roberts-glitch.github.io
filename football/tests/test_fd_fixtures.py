"""fixtures.csv parsing. SYNTHETIC rows (fictional teams, made-up prices) in football-data's
fixtures.csv column layout; used only by these tests."""

from datetime import UTC, datetime

from footy.ingest.fd_fixtures import parse

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
    assert published_time(None, WHEN) == WHEN
    assert published_time("not a date", WHEN) == WHEN
    assert published_time("Sat, 24 Oct 2026 10:00:00 GMT", WHEN) == WHEN  # clock skew
    naive = WHEN.replace(tzinfo=None)  # a naive UTC time, as the database may return it
    assert published_time(None, naive) == WHEN


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
