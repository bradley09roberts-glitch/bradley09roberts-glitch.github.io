"""Rebuilding the predictions table from the records files (audit H4, 22 Oct 2026) and
settling under the write lock (audit L8). SYNTHETIC fixtures and probabilities; every database
and records folder is temporary."""

import json
import threading
import time
from datetime import UTC, date, datetime

import pytest
from sqlalchemy import func, select, text
from sqlalchemy.orm import Session

import footy.publish as pub
from fdfixtures import REFERENCE
from footy.db import make_engine, upgrade_to_head
from footy.ingest.fd_import import sync_reference
from footy.modeling.grid import markets_from_grid, poisson_grid
from footy.models import Prediction, PredictionSettlement
from footy.publish import (
    FixtureIn,
    PublishError,
    check_records,
    import_records,
    latest_settlements,
    publish,
    settle,
)

KO = datetime(2026, 10, 24, 14, 0, tzinfo=UTC)
AB = FixtureIn("E0", "2627", date(2026, 10, 24), KO, "eng-testtown", "eng-sampleton")
BC = FixtureIn("E0", "2627", date(2026, 10, 24), KO, "eng-sampleton", "eng-mockford")


def fresh(tmp_path, name):
    url = f"sqlite:///{tmp_path / name}"
    upgrade_to_head(url)
    engine = make_engine(url)
    with Session(engine) as s:
        sync_reference(s, *REFERENCE)
        s.commit()
    return engine


def go(engine, recs, fx, model, now):
    mp = markets_from_grid(poisson_grid([1.6], [1.1], -0.05), [1.6], [1.1])
    return publish(
        engine,
        [fx],
        mp,
        model_name=model,
        model_version="v1",
        training_cutoff_utc=datetime(2026, 10, 21, tzinfo=UTC),
        data_freshness={},
        now=now,
        records_dir=recs,
    )


def lines(recs):
    return [json.loads(x) for f in sorted(recs.glob("*.jsonl")) for x in f.read_text().splitlines()]


def rows(engine):
    with Session(engine) as s:
        return [
            (p.id, p.fixture_ref, p.model_name)
            for p in s.scalars(select(Prediction).order_by(Prediction.id))
        ]


@pytest.fixture
def published(tmp_path):
    """A first database with two records (ids 1 and 2) and their records folder."""
    recs = tmp_path / "records"
    first = fresh(tmp_path, "first.sqlite3")
    go(first, recs, AB, "m", datetime(2026, 10, 21, 1, tzinfo=UTC))
    go(first, recs, AB, "other", datetime(2026, 10, 21, 2, tzinfo=UTC))
    return first, recs


def test_a_rebuilt_database_refuses_to_publish(published, tmp_path):
    """Before 22 Oct 2026 a fresh database recorded the same fixture again under id 1 (and
    gave a new record id 1, which in git was another model's record)."""
    _, recs = published
    rebuilt = fresh(tmp_path, "rebuilt.sqlite3")
    with pytest.raises(PublishError, match="import-records"):
        go(rebuilt, recs, AB, "m", datetime(2026, 10, 23, 9, tzinfo=UTC))
    with pytest.raises(PublishError, match="import-records"):
        go(rebuilt, recs, BC, "new_model", datetime(2026, 10, 23, 9, tzinfo=UTC))
    with pytest.raises(PublishError, match="not in the database"):
        check_records(rebuilt, recs)
    assert rows(rebuilt) == [] and len(lines(recs)) == 2


def test_a_database_holding_other_records_under_the_same_ids_refuses(published, tmp_path):
    _, recs = published
    other = fresh(tmp_path, "other.sqlite3")
    go(other, tmp_path / "elsewhere", BC, "x", datetime(2026, 10, 21, tzinfo=UTC))  # id 1
    with pytest.raises(PublishError, match="belong to different records"):
        check_records(other, recs)
    with pytest.raises(PublishError, match="id 1 is a different record"):
        import_records(other, recs)


def test_import_records_keeps_every_id_then_publishing_carries_on(published, tmp_path):
    first, recs = published
    rebuilt = fresh(tmp_path, "rebuilt.sqlite3")
    res = import_records(rebuilt, recs)
    assert res == {
        "files": 1,
        "records": 2,
        "imported": 2,
        "already_present": 0,
        "largest_id": 2,
    }
    assert rows(rebuilt) == rows(first)
    check_records(rebuilt, recs)  # nothing missing now
    # the same fixture and model is not recorded again; a new record takes the next id
    assert go(rebuilt, recs, AB, "m", datetime(2026, 10, 23, 9, tzinfo=UTC)) == [1]
    assert go(rebuilt, recs, BC, "m", datetime(2026, 10, 23, 9, tzinfo=UTC)) == [3]
    assert [r["id"] for r in lines(recs)] == [1, 2, 3]
    # importing again changes nothing
    again = import_records(rebuilt, recs)
    assert (again["imported"], again["already_present"]) == (0, 3)
    # the imported records settle as the originals would
    with rebuilt.begin() as conn:
        conn.execute(
            text(
                "INSERT INTO raw_files (id, source, logical_key, revision, sha256, size_bytes, "
                "retrieved_at, storage_path) VALUES (1, 't', 'k', 1, 'x', 1, '2026-10-25', 'p')"
            )
        )
        conn.execute(
            text(
                "INSERT INTO matches (division, season, match_date, home_team, away_team, "
                "home_goals, away_goals, result, source_row, first_raw_file_id, last_raw_file_id) "
                "VALUES ('E0', '2627', '2026-10-24', 'eng-testtown', 'eng-sampleton', 1, 1, 'D', "
                "1, 1, 1)"
            )
        )
    assert settle(rebuilt)["settled"] == 2


def test_import_refuses_a_line_whose_sha256_fails(published, tmp_path):
    _, recs = published
    path = next(recs.glob("*.jsonl"))
    rec = json.loads(path.read_text().splitlines()[1])
    rec["probabilities"]["1X2"]["H"] = 0.99  # edited after it was written
    path.write_text(
        path.read_text().splitlines()[0] + "\n" + json.dumps(rec, sort_keys=True) + "\n"
    )
    rebuilt = fresh(tmp_path, "rebuilt.sqlite3")
    with pytest.raises(PublishError, match="line 2: sha256 doesn't match"):
        import_records(rebuilt, recs)
    assert rows(rebuilt) == []  # nothing imported, not even the sound line


def test_import_needs_the_teams_first(published, tmp_path):
    _, recs = published
    url = f"sqlite:///{tmp_path / 'bare.sqlite3'}"
    upgrade_to_head(url)
    with pytest.raises(PublishError, match="fd-update"):
        import_records(make_engine(url), recs)


# ---- settling under the write lock (L8) -------------------------------------------------------


def test_two_settle_runs_at_once_settle_each_record_once(published, monkeypatch):
    """Before 22 Oct 2026 two `footy settle` processes could both settle the same records
    (seen in 1 of 5 trials; settlements are append-only, so duplicates stay)."""
    first, _ = published
    with first.begin() as conn:
        conn.execute(
            text(
                "INSERT INTO raw_files (id, source, logical_key, revision, sha256, size_bytes, "
                "retrieved_at, storage_path) VALUES (1, 't', 'k', 1, 'x', 1, '2026-10-25', 'p')"
            )
        )
        conn.execute(
            text(
                "INSERT INTO matches (division, season, match_date, home_team, away_team, "
                "home_goals, away_goals, result, source_row, first_raw_file_id, last_raw_file_id) "
                "VALUES ('E0', '2627', '2026-10-24', 'eng-testtown', 'eng-sampleton', 2, 0, 'H', "
                "1, 1, 1)"
            )
        )
    real = pub._final_score

    def slow(s, p):  # widen the window between reading what is settled and writing
        time.sleep(0.3)
        return real(s, p)

    monkeypatch.setattr(pub, "_final_score", slow)
    url = first.url.render_as_string(hide_password=False)
    results = []
    threads = [
        threading.Thread(target=lambda: results.append(settle(make_engine(url)))) for _ in range(2)
    ]
    for t in threads:
        t.start()
    for t in threads:
        t.join()
    assert sorted(r["settled"] for r in results) == [0, 2]
    with Session(first) as s:
        n = s.scalar(select(func.count()).select_from(PredictionSettlement))
        assert n == 2
        assert set(latest_settlements(s)) == {1, 2}
