"""`ledger.data_revision`: what the data fingerprint covers. SYNTHETIC rows only (fictional
teams, made-up file hashes)."""

import re

from sqlalchemy import text

from footy.modeling import ledger
from footy.modeling.ledger import data_revision

REFERENCE = [
    "INSERT INTO divisions (code, name, country, tier) VALUES ('E0', 'Test league', 'ENG', 1)",
    "INSERT INTO teams (team_key, name, country) VALUES ('eng-a', 'A', 'ENG'), "
    "('eng-b', 'B', 'ENG')",
]
MATCH = (
    "INSERT INTO matches (id, division, season, match_date, home_team, away_team, home_goals, "
    "away_goals, result, source_row, first_raw_file_id, last_raw_file_id) VALUES "
    "(:id, 'E0', '2526', '2025-09-13', 'eng-a', 'eng-b', 1, 0, 'H', :id, 1, 1)"
)
ODDS = (
    "INSERT INTO fd_odds (match_id, bookmaker, market_key, selection, phase, price, "
    "quote_time_basis, source_column, raw_file_id) VALUES (1, 'B365', '1X2', 'H', "
    "'closing', 2.0, 'test', 'B365CH', 1)"
)
EURO = (
    "INSERT INTO euro_matches (competition, season, stage, match_date, home_team, away_team, "
    "extra_time, penalties, neutral, status, source) VALUES ('UCL', '2526', 'League MD1', "
    "'2025-09-16', 'eng-a', 'eng-b', 0, 0, 0, 'scheduled', 'wikipedia')"
)


def add_raw(conn, source: str, key: str, revision: int, sha: str) -> None:
    conn.execute(
        text(
            "INSERT INTO raw_files (source, logical_key, revision, sha256, size_bytes, "
            "retrieved_at, storage_path) VALUES (:s, :k, :r, :h, 1, '2026-10-21 00:00:00', 'p')"
        ),
        {"s": source, "k": key, "r": revision, "h": sha},
    )


def changes(engine, statement) -> bool:
    before = data_revision(engine)
    with engine.begin() as conn:
        statement(conn)
    after = data_revision(engine)
    assert re.fullmatch(r"[0-9a-f]{16}", after)
    return after != before


def test_revision_covers_sources_parsers_and_row_counts(migrated_engine, monkeypatch):
    e = migrated_engine
    assert re.fullmatch(r"[0-9a-f]{16}", data_revision(e))
    assert data_revision(e) == data_revision(e)

    # Raw revisions of every source the models read.
    assert changes(e, lambda c: add_raw(c, "football-data", "2526/E0", 1, "a" * 64))
    assert changes(e, lambda c: add_raw(c, "football-data", "2526/E0", 2, "b" * 64))
    assert changes(e, lambda c: add_raw(c, "football-data-extra", "new/AUT", 1, "c" * 64))
    assert changes(e, lambda c: add_raw(c, "openfootball", "2025-26/cl.txt", 1, "d" * 64))
    assert changes(e, lambda c: add_raw(c, "wikipedia", "2026-27 UCL", 1, "e" * 64))
    # Sources the models don't read leave it alone.
    assert not changes(e, lambda c: add_raw(c, "uefa", "match/1", 1, "f" * 64))
    assert not changes(e, lambda c: add_raw(c, "sportmonks", "odds/1", 1, "0" * 64))

    # Imported rows: matches, prices and UEFA matches.
    def reference_and_match(c):
        for stmt in REFERENCE:
            c.execute(text(stmt))
        c.execute(text(MATCH), {"id": 1})

    assert changes(e, reference_and_match)
    assert changes(e, lambda c: c.execute(text(ODDS)))
    assert changes(e, lambda c: c.execute(text(EURO)))

    # Parser versions in the code.
    base = data_revision(e)
    for name in ("PARSER_VERSION", "EXTRA_PARSER_VERSION", "OF_PARSER_VERSION"):
        with monkeypatch.context() as m:
            m.setattr(ledger, name, "test-99")
            assert data_revision(e) != base, name
    assert data_revision(e) == base
