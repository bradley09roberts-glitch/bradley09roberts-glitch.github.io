"""Publishing and settlement, with SYNTHETIC fixtures and probabilities."""

import json
from datetime import UTC, date, datetime, timedelta
from pathlib import Path

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


def insert_result(conn, division, day, hg, ag, meeting=1):
    conn.execute(
        text(
            "INSERT OR IGNORE INTO raw_files (id, source, logical_key, revision, sha256, "
            "size_bytes, retrieved_at, storage_path) VALUES (1, 't', 'k', 1, 'x', 1, "
            "'2026-10-25', 'p')"
        )
    )
    conn.execute(
        text(
            "INSERT INTO matches (division, season, match_date, home_team, away_team, meeting, "
            "home_goals, away_goals, result, source_row, first_raw_file_id, last_raw_file_id) "
            "VALUES (:d, '2627', :day, 'eng-testtown', 'eng-sampleton', :m, :hg, :ag, 'H', 1, "
            "1, 1)"
        ),
        {"d": division, "day": day, "m": meeting, "hg": hg, "ag": ag},
    )


def test_repeat_meeting_settles_only_on_its_own_result(migrated_engine, tmp_path):
    """SC0 pairings meet up to three times a season (D-022): a prediction for the second
    meeting must not take the first meeting's score."""
    setup(migrated_engine)
    mp = markets_from_grid(poisson_grid([1.6], [1.1], -0.05), [1.6], [1.1])
    second = FixtureIn("SC0", "2627", date(2026, 12, 5), None, "eng-testtown", "eng-sampleton")
    publish(
        migrated_engine,
        [second],
        mp,
        model_name="m",
        model_version="v1",
        training_cutoff_utc=datetime(2026, 10, 21, tzinfo=UTC),
        data_freshness={},
        now=datetime(2026, 10, 21, 1, 0, tzinfo=UTC),
        records_dir=tmp_path / "records",
    )
    with migrated_engine.begin() as conn:
        insert_result(conn, "SC0", "2026-09-12", 3, 0)  # the first meeting
    assert settle(migrated_engine)["settled"] == 0
    with migrated_engine.begin() as conn:
        insert_result(conn, "SC0", "2026-12-05", 1, 1, meeting=2)
    assert settle(migrated_engine)["settled"] == 1
    with migrated_engine.connect() as conn:
        assert conn.execute(
            text("SELECT home_goals, away_goals FROM prediction_settlements")
        ).one() == (1, 1)


def test_single_meeting_league_settles_a_moved_match(migrated_engine, tmp_path):
    """Core divisions meet once a season, so a match played on another date than predicted
    (postponed) still settles, as before."""
    setup(migrated_engine)
    go(migrated_engine, tmp_path, datetime(2026, 10, 21, tzinfo=UTC))
    with migrated_engine.begin() as conn:
        insert_result(conn, "E0", "2026-11-04", 0, 1)
    assert settle(migrated_engine)["settled"] == 1


# ---- the records file and the database stay in step -------------------------------------------


def _two(home=("eng-testtown", "eng-sampleton"), away=("eng-sampleton", "eng-testtown")):
    return [
        FixtureIn("E0", "2627", date(2026, 10, 24), KICKOFF, h, a, features={"lam": 1})
        for h, a in zip(home, away, strict=True)
    ]


def _publish_two(engine, records_dir, fixtures=None, model_name="m"):
    fixtures = fixtures or _two()
    n = len(fixtures)
    mp = markets_from_grid(poisson_grid([1.6] * n, [1.1] * n, -0.05), [1.6] * n, [1.1] * n)
    return publish(
        engine,
        fixtures,
        mp,
        model_name=model_name,
        model_version="v1",
        training_cutoff_utc=datetime(2026, 10, 21, tzinfo=UTC),
        data_freshness={},
        now=datetime(2026, 10, 22, 9, 0, tzinfo=UTC),
        records_dir=records_dir,
    )


def _file_and_db(engine, records_dir):
    """Every line of the records folder, and every row of the predictions table, as
    (id, fixture_ref, model_name)."""
    lines = [
        json.loads(line)
        for path in sorted(records_dir.glob("*.jsonl"))
        for line in path.read_text().splitlines()
    ]
    with engine.connect() as conn:
        rows = conn.execute(text("SELECT id, fixture_ref, model_name FROM predictions")).all()
    return (
        sorted((r["id"], r["fixture_ref"], r["model_name"]) for r in lines),
        sorted(tuple(r) for r in rows),
    )


# Where the process is killed: just before the commit (the lines are written), once the pending
# note is on disk but before the lines are, part-way through writing the lines, and after the
# commit but before the note is deleted.
KILL_AT = {
    "commit": "from sqlalchemy.orm import Session\nSession.commit = lambda self: os._exit(9)",
    "note": "import footy.publish as p\np._fsync_dir = lambda path: os._exit(9)",
    "partial": "from sqlalchemy.orm import Session\nSession.commit = lambda self: os._exit(9)",
    "unlink": "import pathlib\npathlib.Path.unlink = lambda self, missing_ok=False: os._exit(9)",
}


def _kill_during_publish(db_url, records, where):
    import subprocess
    import sys
    import textwrap

    script = "\n".join(
        [
            "import os, sys",
            f"sys.path.insert(0, {str(Path(__file__).parent)!r})",
            KILL_AT[where],
            textwrap.dedent(
                f"""
                from pathlib import Path
                from footy.db import make_engine
                from test_publish import _publish_two
                _publish_two(make_engine({db_url!r}), Path({str(records)!r}))
                """
            ),
        ]
    )
    killed = subprocess.run([sys.executable, "-c", script], capture_output=True, text=True)
    assert killed.returncode == 9, killed.stderr


@pytest.mark.parametrize("where", list(KILL_AT))
def test_a_publish_killed_at_any_point_leaves_no_orphan_or_duplicate(
    migrated_engine, db_url, tmp_path, where
):
    """The container can be restarted at any moment: a run killed while publishing must not
    leave lines the database lacks (or rows the file lacks), and the rerun must not record the
    fixtures again (ids are reused after a rollback)."""
    setup(migrated_engine)
    records = tmp_path / "records"
    _kill_during_publish(db_url, records, where)
    day = records / "2026-10-22.jsonl"
    if where == "partial":  # the write was cut short half-way through the second line
        whole = day.read_bytes()
        day.write_bytes(whole[: len(whole) - len(whole.splitlines()[-1]) // 2])
    # the rerun (a later check-in)
    ids = _publish_two(migrated_engine, records)
    in_file, in_db = _file_and_db(migrated_engine, records)
    assert in_file == in_db
    assert len(in_db) == 2 and sorted(ids) == [r[0] for r in in_db]
    assert all(json.loads(line) for line in day.read_text().splitlines())
    assert not list(records.glob(".*"))  # nothing left to recover


def test_a_killed_publish_is_finished_even_when_nothing_new_is_published(
    migrated_engine, db_url, tmp_path
):
    """After kickoff a rerun publishes nothing, so `recover_pending` (run by the publishing
    commands) finishes the killed run's records exactly as they were written."""
    from footy.publish import recover_pending

    setup(migrated_engine)
    records = tmp_path / "records"
    _kill_during_publish(db_url, records, "commit")
    written = (records / "2026-10-22.jsonl").read_text()
    assert recover_pending(migrated_engine, records) == 1
    assert recover_pending(migrated_engine, records) == 0
    in_file, in_db = _file_and_db(migrated_engine, records)
    assert in_file == in_db and len(in_db) == 2
    assert (records / "2026-10-22.jsonl").read_text() == written
    with migrated_engine.connect() as conn:
        row = conn.execute(text("SELECT created_at_utc, probabilities FROM predictions")).first()
    first = json.loads(written.splitlines()[0])
    assert json.loads(row.probabilities) == first["probabilities"]
    assert str(row.created_at_utc).startswith("2026-10-22 09:00:00")


def test_a_publish_that_fails_part_way_leaves_no_line_behind(migrated_engine, tmp_path):
    """A later fixture failing to save (here an unknown team) must not leave the earlier
    fixtures' lines in the records file without their rows."""
    setup(migrated_engine)
    records = tmp_path / "records"
    bad = _two(home=("eng-testtown", "eng-nobody"), away=("eng-sampleton", "eng-testtown"))
    with pytest.raises(DatabaseError):
        _publish_two(migrated_engine, records, fixtures=bad)
    in_file, in_db = _file_and_db(migrated_engine, records)
    assert in_file == in_db == []


def test_two_publishers_racing_for_one_fixture_make_one_record(migrated_engine, db_url, tmp_path):
    """Two runs of the same model at once (an overlapping check-in) record a fixture once."""
    import threading

    import footy.publish as pub
    from footy.db import make_engine

    setup(migrated_engine)
    records = tmp_path / "records"
    checked, done = threading.Event(), threading.Event()
    real = pub.market_dict

    def slow_market_dict(mp, j):  # runs after the first publisher found no record
        if threading.current_thread().name == "first":
            checked.set()
            done.wait(2)
        return real(mp, j)

    pub.market_dict = slow_market_dict
    errors = []

    def run(name):
        try:
            if name == "second":
                checked.wait(5)
            _publish_two(make_engine(db_url), records, fixtures=_two()[:1])
        except Exception as exc:  # pragma: no cover - reported below
            errors.append(exc)
        finally:
            if name == "second":
                done.set()

    try:
        threads = [threading.Thread(target=run, args=(n,), name=n) for n in ("first", "second")]
        for t in threads:
            t.start()
        for t in threads:
            t.join(60)
    finally:
        pub.market_dict = real
    assert not errors
    in_file, in_db = _file_and_db(migrated_engine, records)
    assert in_file == in_db and len(in_db) == 1


def test_a_live_publish_is_stamped_and_checked_once_it_holds_the_lock(
    migrated_engine, db_url, tmp_path
):
    """Without a fixed time, the record's time is taken once `publish` holds the write lock,
    so a fixture that kicks off while another writer holds it is refused rather than saved
    after kickoff with an earlier time."""
    import sqlite3
    import threading
    import time

    setup(migrated_engine)
    kickoff = datetime.now(UTC).replace(microsecond=0) + timedelta(seconds=2)
    fx = FixtureIn(
        "E0", "2627", kickoff.date(), kickoff, "eng-testtown", "eng-sampleton", features={}
    )
    blocker = sqlite3.connect(
        db_url.removeprefix("sqlite:///"), timeout=30, check_same_thread=False
    )
    blocker.execute("BEGIN IMMEDIATE")  # another job writing
    threading.Timer(3.0, blocker.rollback).start()
    started = time.monotonic()
    mp = markets_from_grid(poisson_grid([1.6], [1.1], -0.05), [1.6], [1.1])
    with pytest.raises(PublishError, match="kickoff has passed"):
        publish(
            migrated_engine,
            [fx],
            mp,
            model_name="m",
            model_version="v1",
            training_cutoff_utc=datetime(2026, 10, 21, tzinfo=UTC),
            data_freshness={},
            records_dir=tmp_path / "records",
        )
    assert time.monotonic() - started >= 2.5  # it waited for the lock
    blocker.close()
    in_file, in_db = _file_and_db(migrated_engine, tmp_path / "records")
    assert in_file == in_db == []
