"""Expected match counts in the coverage report, with repeat meetings (SYNTHETIC rows)."""

import itertools

import pandas as pd

from footy.coverage import expected_matches
from footy.ingest.fd_extra_parse import PLAYOFF_EXCLUSION


def season(pairs, exclusion=None):
    """One row per (home, away, meeting)."""
    return pd.DataFrame(
        {
            "home_team": [h for h, _, _ in pairs],
            "away_team": [a for _, a, _ in pairs],
            "meeting": [m for _, _, m in pairs],
            "model_exclusion": [exclusion(h, a) if exclusion else None for h, a, _ in pairs],
        }
    )


CLUBS = ["a", "b", "c", "d"]
ROUND_ROBIN = [(h, a, 1) for h, a in itertools.permutations(CLUBS, 2)]


def test_double_round_robin_is_teams_times_teams_minus_one():
    assert expected_matches(season(ROUND_ROBIN)) == 4 * 3
    # In progress: still the full count, so the shortfall shows.
    assert expected_matches(season(ROUND_ROBIN[:5])) == 4 * 3


def test_further_meetings_are_added_once_played():
    split = ROUND_ROBIN + [("a", "b", 2), ("b", "a", 2), ("a", "b", 3)]
    assert expected_matches(season(split)) == 12 + 3
    # A season that stopped early: unplayed first meetings still count.
    assert expected_matches(season(split[2:])) == 12 + 3


def test_playoff_visitors_are_not_counted_as_clubs():
    playoff = ROUND_ROBIN + [("v", "d", 1), ("d", "v", 1)]
    g = season(playoff, lambda h, a: PLAYOFF_EXCLUSION if "v" in (h, a) else None)
    assert expected_matches(g) == 12 + 2  # not 5 x 4


def test_prices_are_covered_for_every_division(migrated_engine):
    """SYNTHETIC: one second-tier match without prices and one European match with a full
    market-average closing 1X2 set. Price coverage is computed for both (prices are imported
    for every division, D-022), and the European section reports it."""
    from datetime import UTC, datetime

    from sqlalchemy import text

    from footy.coverage import coverage_markdown, coverage_rows

    match = (
        "INSERT INTO matches (id, division, season, match_date, home_team, away_team, "
        "home_goals, away_goals, result, source_row, first_raw_file_id, last_raw_file_id) "
        "VALUES (:id, :div, '2526', '2025-09-13', :h, :a, 1, 0, 'H', 1, 1, 1)"
    )
    odds = (
        "INSERT INTO fd_odds (match_id, bookmaker, market_key, selection, phase, price, "
        "quote_time_basis, source_column, raw_file_id) VALUES (2, 'Avg', '1X2', :s, 'closing', "
        "2.0, 'test', 'AvgC', 1)"
    )
    with migrated_engine.begin() as conn:
        conn.execute(
            text(
                "INSERT INTO raw_files (id, source, logical_key, revision, sha256, size_bytes, "
                "retrieved_at, storage_path) VALUES (1, 'football-data', '2526/SC0', 1, 'x', 1, "
                "'2026-10-21 00:00:00', 'p')"
            )
        )
        conn.execute(
            text(
                "INSERT INTO divisions (code, name, country, tier) VALUES "
                "('E1', 'Test second tier', 'ENG', 2), ('SC0', 'Test league', 'SCO', 1)"
            )
        )
        conn.execute(
            text(
                "INSERT INTO teams (team_key, name, country) VALUES ('eng-a', 'A', 'ENG'), "
                "('eng-b', 'B', 'ENG'), ('sco-a', 'A', 'SCO'), ('sco-b', 'B', 'SCO')"
            )
        )
        conn.execute(text(match), {"id": 1, "div": "E1", "h": "eng-a", "a": "eng-b"})
        conn.execute(text(match), {"id": 2, "div": "SC0", "h": "sco-a", "a": "sco-b"})
        for sel in "HDA":
            conn.execute(text(odds), {"s": sel})
    rows = {r["division"]: r for r in coverage_rows(migrated_engine)}
    assert rows["E1"]["odds::B365 pre 1X2"] == 0.0  # computed, not left empty
    assert rows["SC0"]["odds::Avg close 1X2"] == 100.0
    assert rows["SC0"]["odds::B365 pre 1X2"] == 0.0
    md = coverage_markdown(list(rows.values()), generated_at=datetime(2026, 10, 21, tzinfo=UTC))
    europe = md[md.index("## European leagues") :]
    assert "results only" not in md and "no prices" not in md
    assert "| Avg close 1X2 % | B365 pre 1X2 % | Avg close O/U2.5 % |" in europe
    assert europe.count("| 100 | 0 | 0 |") == 1  # the SC0 row's three price columns
    prices = md[md.index("## Prices") : md.index("## European leagues")]
    assert "| E1 | 2025/26 |" in prices  # second tiers are in the core prices table
