"""Migration 0005: `matches.meeting` and the (division, season, home, away, meeting) key.
SYNTHETIC rows only (the fictional teams of fdfixtures)."""

import pytest
from alembic import command
from alembic.autogenerate import compare_metadata
from alembic.config import Config
from alembic.runtime.migration import MigrationContext
from sqlalchemy import inspect, text
from sqlalchemy.exc import IntegrityError

from footy.config import PROJECT_ROOT
from footy.db import Base, make_engine

RAW = (
    "INSERT INTO raw_files (id, source, logical_key, revision, sha256, size_bytes, "
    "retrieved_at, storage_path) VALUES (1, 'football-data', '2526/SC0', 1, 'x', 1, "
    "'2026-10-21 00:00:00', 'p')"
)
REFERENCE = [
    "INSERT INTO divisions (code, name, country, tier) VALUES ('SC0', 'Test league', 'SCO', 1)",
    "INSERT INTO teams (team_key, name, country) VALUES ('sco-a', 'A', 'SCO'), "
    "('sco-b', 'B', 'SCO')",
    RAW,
]
MATCH = (
    "INSERT INTO matches (id, division, season, match_date, home_team, away_team, {meeting}"
    "home_goals, away_goals, result, source_row, first_raw_file_id, last_raw_file_id) "
    "VALUES (:id, 'SC0', '2526', :day, 'sco-a', 'sco-b', {value}1, 0, 'H', :id, 1, 1)"
)
ODDS = (
    "INSERT INTO fd_odds (match_id, bookmaker, market_key, selection, phase, price, "
    "quote_time_basis, source_column, raw_file_id) VALUES (:m, 'B365', '1X2', 'H', "
    "'closing', 2.0, 'test', 'B365CH', 1)"
)


def alembic(url: str, action: str, revision: str) -> None:
    cfg = Config(str(PROJECT_ROOT / "alembic.ini"))
    cfg.set_main_option("script_location", str(PROJECT_ROOT / "migrations"))
    cfg.set_main_option("sqlalchemy.url", url)
    cfg.attributes["configure_logger"] = False
    getattr(command, action)(cfg, revision)


def insert_match(conn, id_: int, day: str, meeting: int | None = None) -> None:
    sql = MATCH.format(
        meeting="" if meeting is None else "meeting, ",
        value="" if meeting is None else f"{meeting}, ",
    )
    conn.execute(text(sql), {"id": id_, "day": day})


def matches_key(engine) -> list[str]:
    (uq,) = inspect(engine).get_unique_constraints("matches")
    assert uq["name"] == "uq_matches_division"
    return uq["column_names"]


def test_head_schema_matches_the_models(migrated_engine):
    """`alembic check` for the tables this migration touches: no differences."""
    with migrated_engine.connect() as conn:
        diffs = compare_metadata(MigrationContext.configure(conn), Base.metadata)
    flat = [d for diff in diffs for d in (diff if isinstance(diff, list) else [diff])]
    touched = [d for d in flat if "matches" in repr(d) and "euro_matches" not in repr(d)]
    assert touched == []
    assert matches_key(migrated_engine) == [
        "division",
        "season",
        "home_team",
        "away_team",
        "meeting",
    ]


def test_meeting_joins_the_unique_key(migrated_engine):
    with migrated_engine.begin() as conn:
        for stmt in REFERENCE:
            conn.execute(text(stmt))
        insert_match(conn, 1, "2025-09-13")  # server default: meeting 1
        insert_match(conn, 2, "2026-02-14", meeting=2)
        assert conn.execute(text("SELECT id, meeting FROM matches ORDER BY id")).all() == [
            (1, 1),
            (2, 2),
        ]
    with pytest.raises(IntegrityError):
        with migrated_engine.begin() as conn:
            insert_match(conn, 3, "2026-04-18", meeting=2)  # the same meeting twice


def test_upgrade_keeps_rows_and_downgrade_drops_later_meetings(db_url):
    alembic(db_url, "upgrade", "0004")
    engine = make_engine(db_url)
    with engine.begin() as conn:
        for stmt in REFERENCE:
            conn.execute(text(stmt))
        insert_match(conn, 1, "2025-09-13")
        conn.execute(text(ODDS), {"m": 1})
    with pytest.raises(IntegrityError):  # the old key: one row per pairing per season
        with engine.begin() as conn:
            insert_match(conn, 2, "2026-02-14")
    engine.dispose()

    alembic(db_url, "upgrade", "head")
    engine = make_engine(db_url)
    with engine.begin() as conn:
        assert conn.execute(text("SELECT id, meeting FROM matches")).all() == [(1, 1)]
        assert conn.execute(text("SELECT match_id FROM fd_odds")).all() == [(1,)]
        assert conn.execute(text("PRAGMA foreign_keys")).scalar() == 1
        insert_match(conn, 2, "2026-02-14", meeting=2)
        conn.execute(text(ODDS), {"m": 2})
    with pytest.raises(IntegrityError):  # foreign keys are enforced again after the rebuild
        with engine.begin() as conn:
            conn.execute(text(ODDS.replace("'B365'", "'PS'")), {"m": 99})
    engine.dispose()

    alembic(db_url, "downgrade", "0004")
    engine = make_engine(db_url)
    assert matches_key(engine) == ["division", "season", "home_team", "away_team"]
    with engine.connect() as conn:
        assert conn.execute(text("SELECT id FROM matches")).all() == [(1,)]
        assert conn.execute(text("SELECT match_id FROM fd_odds")).all() == [(1,)]
        assert conn.execute(text("PRAGMA foreign_key_check")).all() == []
    engine.dispose()

    alembic(db_url, "upgrade", "head")  # and back up again
    engine = make_engine(db_url)
    assert matches_key(engine)[-1] == "meeting"
    engine.dispose()
