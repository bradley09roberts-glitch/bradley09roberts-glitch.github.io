"""Publishing and settlement, with SYNTHETIC fixtures and probabilities."""

import json
from datetime import UTC, date, datetime

import pytest
from sqlalchemy import text
from sqlalchemy.exc import DatabaseError
from sqlalchemy.orm import Session

from fdfixtures import REFERENCE
from footy.ingest.fd_import import sync_reference
from footy.modeling.grid import markets_from_grid, poisson_grid
from footy.publish import FixtureIn, PublishError, publish, settle

KICKOFF = datetime(2026, 10, 24, 14, 0, tzinfo=UTC)


def fixture():
    return FixtureIn(
        "E0",
        "2627",
        date(2026, 10, 24),
        KICKOFF,
        "eng-testtown",
        "eng-sampleton",
        features={"lam": 1},
        missing_fields=["lineups"],
    )


def setup(engine):
    with Session(engine) as s:
        sync_reference(s, *REFERENCE)
        s.commit()


def go(engine, tmp_path, now, **kw):
    mp = markets_from_grid(poisson_grid([1.6], [1.1], -0.05), [1.6], [1.1])
    return publish(
        engine,
        [fixture()],
        mp,
        model_name="m",
        model_version="v1",
        training_cutoff_utc=datetime(2026, 10, 21, tzinfo=UTC),
        data_freshness={"E0": "2026-10-19"},
        now=now,
        records_dir=tmp_path / "records",
        **kw,
    )


def test_publish_writes_db_and_jsonl_once(migrated_engine, tmp_path):
    setup(migrated_engine)
    now = datetime(2026, 10, 21, 1, 0, tzinfo=UTC)
    ids = go(migrated_engine, tmp_path, now)
    again = go(migrated_engine, tmp_path, now)
    assert ids == again  # no duplicate
    lines = (tmp_path / "records" / "2026-10-21.jsonl").read_text().splitlines()
    assert len(lines) == 1
    rec = json.loads(lines[0])
    assert rec["record_type"] == "genuine_prematch" and rec["edition"] == "early"
    assert abs(sum(rec["probabilities"]["1X2"].values()) - 1) < 1e-9
    assert rec["selections"]["1X2"]["pick"] == "H" and len(rec["sha256"]) == 64


def test_cannot_publish_pre_match_after_kickoff(migrated_engine, tmp_path):
    setup(migrated_engine)
    with pytest.raises(PublishError, match="kickoff has passed"):
        go(migrated_engine, tmp_path, KICKOFF)


def test_published_rows_are_immutable(migrated_engine, tmp_path):
    setup(migrated_engine)
    go(migrated_engine, tmp_path, datetime(2026, 10, 21, tzinfo=UTC))
    with migrated_engine.connect() as conn:
        for stmt in ("UPDATE predictions SET model_version = 'x'", "DELETE FROM predictions"):
            with pytest.raises(DatabaseError, match="append-only"):
                conn.execute(text(stmt))


def test_settlement_marks_every_market(migrated_engine, tmp_path):
    setup(migrated_engine)
    go(migrated_engine, tmp_path, datetime(2026, 10, 21, tzinfo=UTC))
    assert settle(migrated_engine)["settled"] == 0  # no result yet
    with migrated_engine.begin() as conn:
        conn.execute(
            text(
                "INSERT INTO raw_files (source, logical_key, revision, sha256, size_bytes, "
                "retrieved_at, storage_path) VALUES ('t', 'k', 1, 'x', 1, '2026-10-25', 'p')"
            )
        )
        conn.execute(
            text(
                "INSERT INTO matches (division, season, match_date, home_team, away_team, "
                "home_goals, away_goals, result, source_row, first_raw_file_id, last_raw_file_id) "
                "VALUES ('E0', '2627', '2026-10-24', 'eng-testtown', 'eng-sampleton', 2, 2, 'D', "
                "1, 1, 1)"
            )
        )
    assert settle(migrated_engine)["settled"] == 1
    assert settle(migrated_engine)["settled"] == 0  # settled once only
    with migrated_engine.connect() as conn:
        out = json.loads(
            conn.execute(text("SELECT outcomes FROM prediction_settlements")).scalar_one()
        )
    assert out["1X2"] == {
        "pick": "H",
        "actual": "D",
        "correct": False,
        "p_actual": out["1X2"]["p_actual"],
    }
    assert (
        out["BTTS"]["actual"] == "Y"
        and out["OU_3.5"]["actual"] == "O"
        and out["OU_4.5"]["actual"] == "U"
    )


def test_unannounced_kickoff_must_be_published_before_the_match_day(migrated_engine, tmp_path):
    setup(migrated_engine)
    fx = FixtureIn("E0", "2627", date(2026, 10, 25), None, "eng-testtown", "eng-mockford")
    mp = markets_from_grid(poisson_grid([1.2], [1.0]))
    kw = dict(
        model_name="m",
        model_version="v1",
        training_cutoff_utc=datetime(2026, 10, 21, tzinfo=UTC),
        data_freshness={},
        records_dir=tmp_path / "r",
    )
    assert publish(migrated_engine, [fx], mp, now=datetime(2026, 10, 24, 22, 59, tzinfo=UTC), **kw)
    fx2 = FixtureIn("E0", "2627", date(2026, 10, 25), None, "eng-mockford", "eng-testtown")
    with pytest.raises(PublishError):
        publish(migrated_engine, [fx2], mp, now=datetime(2026, 10, 24, 23, 0, tzinfo=UTC), **kw)
