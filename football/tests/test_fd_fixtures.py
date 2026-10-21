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
