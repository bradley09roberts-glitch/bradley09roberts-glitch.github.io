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


# Migration 0006: distinct names for the two unique keys of raw_files.
RAW_KEYS = {
    "uq_raw_files_source_logical_key_revision": ["source", "logical_key", "revision"],
    "uq_raw_files_source_logical_key_sha256": ["source", "logical_key", "sha256"],
}
RAW_ROW = (
    "INSERT INTO raw_files (id, source, logical_key, revision, sha256, size_bytes, "
    "retrieved_at, storage_path) VALUES (:id, 'football-data', :key, :rev, :sha, 1, "
    "'2026-10-21 00:00:00', 'p')"
)


def raw_keys(engine) -> list[tuple[str, list[str]]]:
    return sorted(
        (uq["name"], uq["column_names"])
        for uq in inspect(engine).get_unique_constraints("raw_files")
    )


def raw_triggers(conn) -> list[str]:
    return sorted(
        r[0]
        for r in conn.execute(
            text("SELECT name FROM sqlite_master WHERE type = 'trigger' AND tbl_name = 'raw_files'")
        )
    )


def constraint_names(engine) -> list[str]:
    """Every named unique, foreign-key and check constraint and every index, per table."""
    insp = inspect(engine)
    names = []
    for table in insp.get_table_names():
        for kind in ("get_unique_constraints", "get_foreign_keys", "get_check_constraints"):
            names += [f"{table}.{c['name']}" for c in getattr(insp, kind)(table) if c["name"]]
        names += [f"{table}.{ix['name']}" for ix in insp.get_indexes(table)]
    return names


def test_whole_schema_matches_the_models_and_names_are_unique(migrated_engine):
    """`alembic check` for every table. The comparison keys constraints by name, and with two
    constraints of one name it kept one at random (by string hashing, so it varied between
    processes): it failed about 1 run in 15 before migration 0006. No name repeats now."""
    with migrated_engine.connect() as conn:
        assert compare_metadata(MigrationContext.configure(conn), Base.metadata) == []
    names = constraint_names(migrated_engine)
    assert len(names) == len(set(names)), sorted(n for n in names if names.count(n) > 1)
    assert raw_keys(migrated_engine) == sorted(RAW_KEYS.items())


def test_raw_files_keys_renamed_with_rows_triggers_and_foreign_keys_kept(db_url):
    alembic(db_url, "upgrade", "0005")
    engine = make_engine(db_url)
    assert raw_keys(engine) == [
        ("uq_raw_files_source", ["source", "logical_key", "revision"]),
        ("uq_raw_files_source", ["source", "logical_key", "sha256"]),
    ]
    with engine.begin() as conn:
        for stmt in REFERENCE:  # raw file 1, SC0 and its two teams
            conn.execute(text(stmt))
        conn.execute(text(RAW_ROW), {"id": 2, "key": "2526/SC0", "rev": 2, "sha": "y"})
        insert_match(conn, 1, "2025-09-13")
        conn.execute(text("UPDATE matches SET last_raw_file_id = 2 WHERE id = 1"))
        conn.execute(text(ODDS), {"m": 1})
    engine.dispose()

    alembic(db_url, "upgrade", "head")
    engine = make_engine(db_url)
    assert raw_keys(engine) == sorted(RAW_KEYS.items())
    with engine.connect() as conn:
        assert conn.execute(text("SELECT id, revision, sha256 FROM raw_files")).all() == [
            (1, 1, "x"),
            (2, 2, "y"),
        ]
        assert conn.execute(
            text("SELECT first_raw_file_id, last_raw_file_id FROM matches")
        ).all() == [(1, 2)]
        assert raw_triggers(conn) == ["raw_files_no_delete", "raw_files_no_update"]
        assert conn.execute(text("PRAGMA foreign_keys")).scalar() == 1
    for row in (
        {"id": 3, "key": "2526/SC0", "rev": 2, "sha": "z"},  # the same revision twice
        {"id": 3, "key": "2526/SC0", "rev": 3, "sha": "y"},  # the same file twice
    ):
        with pytest.raises(IntegrityError):
            with engine.begin() as conn:
                conn.execute(text(RAW_ROW), row)
    with pytest.raises(IntegrityError, match="append-only"):
        with engine.begin() as conn:
            conn.execute(text("UPDATE raw_files SET sha256 = 'w' WHERE id = 1"))
    with pytest.raises(IntegrityError):  # foreign keys are enforced again after the rebuild
        with engine.begin() as conn:
            # a new price row (another book) citing a raw file that doesn't exist
            conn.execute(text(ODDS.replace("'B365'", "'PS'").replace(", 1)", ", 99)")), {"m": 1})
    engine.dispose()

    alembic(db_url, "downgrade", "0005")
    engine = make_engine(db_url)
    assert raw_keys(engine) == [
        ("uq_raw_files_source", ["source", "logical_key", "revision"]),
        ("uq_raw_files_source", ["source", "logical_key", "sha256"]),
    ]
    with engine.connect() as conn:
        assert conn.execute(text("SELECT count(*) FROM raw_files")).scalar() == 2
        assert raw_triggers(conn) == ["raw_files_no_delete", "raw_files_no_update"]
        assert conn.execute(text("PRAGMA foreign_key_check")).all() == []
    engine.dispose()

    alembic(db_url, "upgrade", "head")  # and back up again
    engine = make_engine(db_url)
    assert raw_keys(engine) == sorted(RAW_KEYS.items())
    engine.dispose()
