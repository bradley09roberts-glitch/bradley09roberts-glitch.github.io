"""SQLite application store: raw-file registry, job runs, identities, editions, quotes,
settlements.

SQLite in WAL mode is the transactional store. Published editions, forecasts, raw-file
records, quotes and settlement revisions are append-only: triggers refuse UPDATE and
DELETE, so a correction is always a new row that supersedes an older one.
"""

from __future__ import annotations

import sqlite3
from contextlib import contextmanager
from pathlib import Path

from .config import DB_PATH

SCHEMA_VERSION = 1

SCHEMA = """
CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT NOT NULL);

CREATE TABLE IF NOT EXISTS raw_files (
    id INTEGER PRIMARY KEY,
    source TEXT NOT NULL,
    dataset TEXT NOT NULL,
    part TEXT NOT NULL,                 -- partition, e.g. season '2024' or 'all'
    url TEXT NOT NULL,                  -- secrets redacted
    retrieved_at TEXT NOT NULL,         -- UTC ISO-8601
    sha256 TEXT NOT NULL,
    bytes INTEGER NOT NULL,
    path TEXT NOT NULL,                 -- relative to DATA_HOME
    content_type TEXT,
    upstream_last_modified TEXT,
    schema_fingerprint TEXT,
    n_rows INTEGER,
    is_synthetic INTEGER NOT NULL DEFAULT 0,   -- trial/scrambled data: integration tests only
    UNIQUE (source, dataset, part, sha256)
);

CREATE TABLE IF NOT EXISTS raw_observations (
    id INTEGER PRIMARY KEY,
    raw_file_id INTEGER NOT NULL REFERENCES raw_files(id),
    observed_at TEXT NOT NULL,
    changed INTEGER NOT NULL            -- 1 if this retrieval produced new content
);

CREATE TABLE IF NOT EXISTS fetch_failures (
    id INTEGER PRIMARY KEY,
    source TEXT NOT NULL, dataset TEXT NOT NULL, part TEXT NOT NULL,
    url TEXT NOT NULL, attempted_at TEXT NOT NULL, status INTEGER, error TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS job_runs (
    id INTEGER PRIMARY KEY,
    job TEXT NOT NULL,
    args TEXT,
    started_at TEXT NOT NULL,
    finished_at TEXT,
    status TEXT NOT NULL CHECK (status IN ('running','ok','partial','failed')),
    detail TEXT
);

CREATE TABLE IF NOT EXISTS player_id_map (
    id INTEGER PRIMARY KEY,
    player_id TEXT NOT NULL,            -- canonical id (= GSIS id where one exists)
    source TEXT NOT NULL,               -- e.g. 'gsis','pfr','espn','odds_api_name'
    source_id TEXT NOT NULL,
    method TEXT NOT NULL,               -- how the link was made (never surname-only)
    evidence TEXT,
    created_at TEXT NOT NULL,
    UNIQUE (source, source_id)
);

CREATE TABLE IF NOT EXISTS editions (
    edition_id TEXT PRIMARY KEY,
    season INTEGER NOT NULL,
    week INTEGER NOT NULL,
    season_type TEXT NOT NULL,
    kind TEXT NOT NULL,                 -- 'early' | 'late'
    cutoff_at TEXT NOT NULL,
    published_at TEXT NOT NULL,
    model_id TEXT NOT NULL,
    code_version TEXT NOT NULL,
    inputs_hash TEXT NOT NULL,
    n_forecasts INTEGER NOT NULL,
    file_path TEXT NOT NULL,
    file_sha256 TEXT NOT NULL,
    retrospective INTEGER NOT NULL DEFAULT 0  -- 1 = research backfill, never a published record
);

CREATE TABLE IF NOT EXISTS edition_games (
    edition_id TEXT NOT NULL REFERENCES editions(edition_id),
    game_id TEXT NOT NULL,
    kickoff_at TEXT NOT NULL,
    published_before_kickoff INTEGER NOT NULL,
    PRIMARY KEY (edition_id, game_id)
);

CREATE TABLE IF NOT EXISTS quotes (
    id INTEGER PRIMARY KEY,
    source TEXT NOT NULL,
    bookmaker TEXT NOT NULL,
    bookmaker_region TEXT NOT NULL,
    availability TEXT NOT NULL CHECK (availability IN
        ('verified_uk','research_reference','synthetic_test')),
    provider_event_id TEXT NOT NULL,
    game_id TEXT,
    player_name_raw TEXT NOT NULL,
    player_id TEXT,                     -- NULL until an audited mapping exists
    market TEXT NOT NULL,
    line REAL,
    selection TEXT NOT NULL,            -- 'over' | 'under' | 'yes'
    decimal_price REAL NOT NULL CHECK (decimal_price > 1.0),
    quote_time TEXT,                    -- bookmaker/market last_update from the provider
    observed_at TEXT NOT NULL,          -- when we retrieved it
    raw_file_id INTEGER REFERENCES raw_files(id),
    UNIQUE (source, bookmaker, provider_event_id, player_name_raw, market, line, selection,
            quote_time, decimal_price)
);

CREATE TABLE IF NOT EXISTS settlements (
    id INTEGER PRIMARY KEY,
    game_id TEXT NOT NULL,
    player_id TEXT NOT NULL,
    market TEXT NOT NULL,
    revision INTEGER NOT NULL,
    status TEXT NOT NULL CHECK (status IN ('final','inactive','dnp','missing','not_final')),
    value REAL,
    observed_at TEXT NOT NULL,
    raw_file_id INTEGER REFERENCES raw_files(id),
    UNIQUE (game_id, player_id, market, revision)
);

-- a later, corrected game/player link for a stored quote (the quote row never changes;
-- the newest link per quote supersedes the row's own ids)
CREATE TABLE IF NOT EXISTS quote_links (
    id INTEGER PRIMARY KEY,
    quote_id INTEGER NOT NULL REFERENCES quotes(id),
    game_id TEXT,
    player_id TEXT,
    link_method TEXT NOT NULL,
    linked_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS paper_bets (
    id INTEGER PRIMARY KEY,
    strategy_id TEXT NOT NULL,
    edition_id TEXT NOT NULL REFERENCES editions(edition_id),
    quote_id INTEGER NOT NULL REFERENCES quotes(id),
    game_id TEXT NOT NULL, player_id TEXT NOT NULL, market TEXT NOT NULL,
    line REAL NOT NULL, selection TEXT NOT NULL,
    model_prob REAL NOT NULL, decimal_price REAL NOT NULL,
    stake REAL NOT NULL, decided_at TEXT NOT NULL,
    UNIQUE (strategy_id, edition_id, game_id, player_id, market, line, selection)
);
"""

APPEND_ONLY = ("raw_files", "raw_observations", "editions", "edition_games", "quotes",
               "quote_links", "settlements", "paper_bets", "player_id_map")


def _triggers() -> str:
    out = []
    for t in APPEND_ONLY:
        out.append(
            f"CREATE TRIGGER IF NOT EXISTS {t}_no_update BEFORE UPDATE ON {t} "
            f"BEGIN SELECT RAISE(ABORT, '{t} is append-only'); END;"
        )
        out.append(
            f"CREATE TRIGGER IF NOT EXISTS {t}_no_delete BEFORE DELETE ON {t} "
            f"BEGIN SELECT RAISE(ABORT, '{t} is append-only'); END;"
        )
    return "\n".join(out)


def connect(path: Path | None = None) -> sqlite3.Connection:
    p = Path(path or DB_PATH)
    p.parent.mkdir(parents=True, exist_ok=True)
    con = sqlite3.connect(p, timeout=30, isolation_level=None)
    con.row_factory = sqlite3.Row
    con.execute("PRAGMA journal_mode=WAL")
    con.execute("PRAGMA foreign_keys=ON")
    con.executescript(SCHEMA)
    con.executescript(_triggers())
    con.execute(
        "INSERT OR IGNORE INTO meta(key, value) VALUES ('schema_version', ?)", (str(SCHEMA_VERSION),)
    )
    return con


@contextmanager
def tx(con: sqlite3.Connection):
    con.execute("BEGIN IMMEDIATE")
    try:
        yield con
    except BaseException:
        con.execute("ROLLBACK")
        raise
    else:
        con.execute("COMMIT")
