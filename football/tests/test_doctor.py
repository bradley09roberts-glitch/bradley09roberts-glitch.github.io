"""`footy doctor`, the server's health report (footy.doctor, docs/server.md). SYNTHETIC rows in
temporary databases, state folders and git repositories only."""

import json
import os
import subprocess
from datetime import UTC, date, datetime, timedelta
from pathlib import Path

import pytest
from sqlalchemy.orm import Session

from fdfixtures import REFERENCE
from footy import doctor
from footy.doctor import OK, PROBLEM, SKIPPED, WARN, Options
from footy.ingest.fd_import import sync_reference
from footy.models import FetchLog, JobRun, Prediction, PredictionSettlement

NOW = datetime(2026, 10, 22, 12, 0, tzinfo=UTC)


@pytest.fixture
def engine(migrated_engine):
    with Session(migrated_engine) as s:
        sync_reference(s, *REFERENCE)
        s.commit()
    return migrated_engine


def add_prediction(s: Session, comp: str, kickoff: datetime, *, created: datetime | None = None):
    p = Prediction(
        fixture_ref=f"{comp}:2627:{kickoff.date()}:eng-testtown:eng-sampleton",
        competition=comp,
        season="2627",
        kickoff_utc=kickoff,
        match_date=kickoff.date(),
        home_team="eng-testtown",
        away_team="eng-sampleton",
        edition="early",
        record_type="genuine_prematch",
        model_name="dixon_coles",
        model_version="test",
        training_cutoff_utc=kickoff - timedelta(days=1),
        created_at_utc=created or kickoff - timedelta(days=1),
        probabilities="{}",
        selections="{}",
        features="{}",
        data_freshness="{}",
        missing_fields="[]",
    )
    s.add(p)
    s.flush()
    return p


def by_name(report) -> dict:
    return {c.name: c for c in report.checks}


def test_schema_at_head(engine):
    assert doctor.check_schema(engine).status == OK


def test_schema_behind_is_a_problem(tmp_path):
    from footy.db import make_engine

    check = doctor.check_schema(make_engine(f"sqlite:///{tmp_path / 'never.sqlite3'}"))
    assert check.status == PROBLEM
    assert "footy migrate" in check.details[0]


def test_settlements_overdue_by_group(engine):
    with Session(engine) as s:
        add_prediction(s, "E0", NOW - timedelta(hours=5))  # recent: warn only
        add_prediction(s, "UCL", NOW - timedelta(days=3))  # UEFA, past its 2 days: problem
        settled = add_prediction(s, "E0", NOW - timedelta(days=9))
        s.add(
            PredictionSettlement(
                prediction_id=settled.id,
                settled_at_utc=NOW - timedelta(days=8),
                home_goals=1,
                away_goals=0,
                status="settled",
                outcomes="{}",
                source="test",
            )
        )
        add_prediction(s, "E0", NOW + timedelta(hours=2))  # not kicked off
        s.commit()
        check = doctor.check_settlements(s, NOW)
    assert check.status == PROBLEM
    assert check.summary.startswith("2 fixtures (2 records)")
    assert "UCL 1" in check.summary and "E0 1" in check.summary
    assert check.details[0].startswith("[problem] UCL")


def test_settlements_recent_only_warn(engine):
    with Session(engine) as s:
        add_prediction(s, "E0", NOW - timedelta(days=3))  # league: 4 days' grace
        s.commit()
        assert doctor.check_settlements(s, NOW).status == WARN
        assert doctor.check_settlements(s, NOW + timedelta(days=2)).status == PROBLEM


def test_jobs_stale_failed_and_killed(engine):
    with Session(engine) as s:
        s.add_all(
            [
                JobRun(job="settle", started_at=NOW - timedelta(hours=1), status="ok"),
                JobRun(job="fd_update", started_at=NOW - timedelta(hours=40), status="ok"),
                JobRun(job="predict", started_at=NOW - timedelta(hours=2), status="ok"),
                JobRun(
                    job="predict",
                    started_at=NOW - timedelta(hours=1),
                    status="failed",
                    error="KeyError: 'x'",
                ),
                JobRun(job="euro_update", started_at=NOW - timedelta(hours=5), status="running"),
            ]
        )
        s.commit()
        check = doctor.check_jobs(s, NOW, provider_configured=False)
    assert check.status == PROBLEM
    text = "\n".join(check.details)
    assert "[problem] fd_update" in text
    assert "[problem] predict" in text and "KeyError" in text
    assert "settle: last ok" in text and "[problem] settle" not in text
    assert "still 'running'" in text and "euro_update #" in text


def test_odds_capture_only_checked_with_a_provider(engine):
    with Session(engine) as s:
        s.add(JobRun(job="odds_capture", started_at=NOW - timedelta(hours=3), status="ok"))
        s.commit()
        assert doctor.check_jobs(s, NOW, provider_configured=False).status == OK
        assert doctor.check_jobs(s, NOW, provider_configured=True).status == PROBLEM


def test_fetches_by_source_and_status(engine):
    def log(source, outcome, status, hours_ago):
        return FetchLog(
            source=source,
            logical_key="k",
            requested_at=NOW - timedelta(hours=hours_ago),
            http_status=status,
            outcome=outcome,
        )

    with Session(engine) as s:
        s.add_all(
            [
                log("wikipedia", "new_revision", 200, 60),  # before the window: last success
                log("wikipedia", "error", 403, 10),
                log("wikipedia", "error", 403, 2),
                log("football-data", "error", 503, 5),
                log("football-data", "not_modified", 304, 1),  # recovered
                log("rsssf", "error", 404, 1),  # not a main source
                log("football-data-extra", "inbox_new", None, 1),  # hand-downloaded: fine
                log("wikipedia-de", "not_found", 200, 1),  # no such page yet: an answer
            ]
        )
        s.commit()
        check = doctor.check_fetches(s, NOW)
    assert check.status == PROBLEM
    lines = {
        d.split(":")[0].removeprefix("[problem] ").removeprefix("[warn] "): d for d in check.details
    }
    assert lines["wikipedia"].startswith("[problem]") and "2 x HTTP 403" in lines["wikipedia"]
    assert "last success Tue 20 Oct 01:00" in lines["wikipedia"]  # 60 h before, UK time
    assert lines["football-data"].startswith("football-data")  # latest ok: not flagged
    assert lines["rsssf"].startswith("[warn]")
    assert "football-data-extra" not in lines and "wikipedia-de" not in lines


def test_timers_from_the_state_folder(tmp_path):
    state = tmp_path / "state"
    state.mkdir()

    def stamp(name, when):
        (state / name).write_text(when.strftime("%Y-%m-%dT%H:%M:%SZ") + "\n")

    stamp("last-success-daily", NOW - timedelta(hours=2))
    stamp("last-success-backup", NOW - timedelta(hours=30))  # late
    stamp("last-success-prices", NOW - timedelta(days=1))
    stamp("last-failure-prices", NOW - timedelta(hours=1))  # failed since
    check = doctor.check_timers(state, NOW)
    assert check.status == PROBLEM
    text = "\n".join(check.details)
    assert "[problem] backup" in text and "[problem] prices" in text and "failed" in text
    assert "daily" not in text
    assert "[warn] update: last success never (hasn't run yet)" in text  # a new server

    stamp("last-start-update", NOW - timedelta(minutes=10))  # running now: not late
    assert "update" not in "\n".join(doctor.check_timers(state, NOW).details)
    (state / "last-start-update").unlink()

    stamp("bootstrap.done", NOW - timedelta(days=10))  # up for 10 days: never ran is late
    assert "[problem] update" in "\n".join(doctor.check_timers(state, NOW).details)
    assert doctor.check_timers(tmp_path / "missing", NOW).status == SKIPPED


def test_notes_are_standing_warnings(tmp_path):
    assert doctor.check_notes(tmp_path).status == OK
    (tmp_path / "warn-wikipedia").write_text("Wikipedia didn't answer\nthe server.\n")
    check = doctor.check_notes(tmp_path)
    assert check.status == WARN
    assert check.details == ["wikipedia: Wikipedia didn't answer the server."]


def git(cwd: Path, *args: str) -> str:
    return subprocess.run(
        ["git", "-C", str(cwd), *args], check=True, capture_output=True, text=True
    ).stdout


@pytest.fixture
def records_repo(tmp_path):
    repo = tmp_path / "repo"
    records = repo / "football" / "records" / "predictions"
    records.mkdir(parents=True)
    git(repo, "init", "-q", "-b", "main")
    (records / "2026-10-21.jsonl").write_text('{"id": 1}\n')
    (records / "2026-10-22.jsonl").write_text('{"id": 2}\n')
    git(repo, "add", "-A")
    git(repo, "-c", "user.name=t", "-c", "user.email=t@example.invalid", "commit", "-qm", "r")
    return records


def test_records_compared_with_the_pushed_branch(engine, records_repo, monkeypatch):
    from footy import publish

    # these synthetic lines aren't real records: only the comparison with git is tested here
    monkeypatch.setattr(publish, "check_records", lambda *a, **k: None, raising=False)

    def run(now=NOW):
        return doctor.check_records(engine, records_repo, now=now, ref="HEAD", state_dir=None)

    in_sync = run()
    assert "every file matches HEAD" in in_sync.summary
    assert in_sync.summary.startswith("2 records in 2 files")

    path = records_repo / "2026-10-22.jsonl"
    path.write_text('{"id": 2}\n{"id": 3}\n')  # a new line, not pushed yet
    written = datetime.fromtimestamp(path.stat().st_mtime, UTC)
    assert run(written + timedelta(minutes=5)).status == WARN
    late = run(written + timedelta(hours=3))
    assert late.status == PROBLEM and "2026-10-22.jsonl" in late.details[-1]

    path.write_text('{"id": 2}\n')
    (records_repo / "2026-10-21.jsonl").unlink()  # pushed, but missing here
    missing = run()
    assert missing.status == PROBLEM and "lacks: 2026-10-21.jsonl" in missing.details[-1]


def test_records_the_database_lacks(engine, tmp_path):
    from footy import publish

    if not hasattr(publish, "check_records"):
        pytest.skip("footy.publish has no check_records")
    records = tmp_path / "records"
    records.mkdir()
    (records / "2026-10-22.jsonl").write_text('{"id": 1, "sha256": "0"}\n')
    check = doctor.check_records(engine, records, now=NOW, ref=None, state_dir=None)
    assert check.status == PROBLEM
    assert "disagree" in check.details[0]


def test_disk(monkeypatch, tmp_path):
    from collections import namedtuple

    usage = namedtuple("usage", "total used free")
    monkeypatch.setattr(
        doctor.shutil, "disk_usage", lambda p: usage(100 * 1024**3, 0, 50 * 1024**3)
    )
    assert doctor.check_disk(tmp_path).status == OK
    monkeypatch.setattr(doctor.shutil, "disk_usage", lambda p: usage(100 * 1024**3, 0, 1024**3))
    assert doctor.check_disk(tmp_path).status == PROBLEM


def test_web():
    answers = {"http://api/health": (200, ""), "https://site/": (401, "")}
    ok = doctor.check_web("http://api/health", "https://site/", None, lambda u: answers[u])
    assert ok.status == OK
    down = doctor.check_web(
        "http://api/health", "https://site/", None, lambda u: (None, "ConnectError: refused")
    )
    assert down.status == PROBLEM and len(down.details) == 2
    assert doctor.check_web(None, None, None).status == SKIPPED


def test_week_summary(engine):
    with Session(engine) as s:
        add_prediction(s, "E0", NOW + timedelta(days=2), created=NOW - timedelta(days=1))
        add_prediction(s, "UCL", NOW + timedelta(days=1), created=NOW - timedelta(days=2))
        add_prediction(s, "E0", NOW - timedelta(days=20), created=NOW - timedelta(days=21))
        s.commit()
        summary = doctor.week_summary(s, NOW)
    assert summary["records_published"] == 2
    assert summary["by_group"] == {
        "domestic": {"records": 1, "fixtures": 1},
        "uefa": {"records": 1, "fixtures": 1},
    }
    assert summary["upcoming_fixtures_with_records"] == 2
    assert doctor.summary_lines(summary)[0].startswith("Last 7 days: 2 records published")


def test_run_and_report(engine, settings, tmp_path, monkeypatch):
    monkeypatch.setattr(doctor, "MIN_FREE_BYTES", 0)
    monkeypatch.setattr(doctor, "MIN_FREE_FRACTION", 0)
    state = tmp_path / "state"
    state.mkdir()
    (state / "warn-media").write_text("pictures not downloaded")
    records = tmp_path / "records"
    records.mkdir()
    with Session(engine) as s:
        add_prediction(s, "UCL", NOW - timedelta(days=3))
        s.commit()
    report = doctor.run(
        engine,
        settings,
        Options(state_dir=state, summary=True),
        now=NOW,
        records_dir=records,
    )
    checks = by_name(report)
    assert checks["schema"].status == OK
    assert checks["settlements"].status == PROBLEM
    assert checks["notes"].status == WARN
    assert checks["web"].status == SKIPPED
    assert report.exit_code() == 1
    quiet = report.text(quiet=True)
    assert "settlements" in quiet and "schema" not in quiet
    assert "Last 7 days" in quiet
    data = report.as_json()
    assert data["problems"] >= 1 and "summary" in data
    json.dumps(data)


def test_cli_doctor(tmp_path, monkeypatch, capsys):
    from footy import cli, config, db, publish
    from footy.db import upgrade_to_head

    url = f"sqlite:///{tmp_path / 'cli.sqlite3'}"
    upgrade_to_head(url)
    for var in list(os.environ):
        if var.startswith("FOOTY_") or var in ("SPORTMONKS_API_TOKEN", "API_FOOTBALL_KEY"):
            monkeypatch.delenv(var)
    monkeypatch.setenv("FOOTY_DATABASE_URL", url)
    monkeypatch.setenv("FOOTY_DATA_DIR", str(tmp_path))
    monkeypatch.setenv("FOOTY_LIVE_PROVIDER", "none")
    monkeypatch.setattr(publish, "RECORDS_DIR", tmp_path / "records")
    monkeypatch.setattr(doctor, "MIN_FREE_BYTES", 0)
    monkeypatch.setattr(doctor, "MIN_FREE_FRACTION", 0)
    config.get_settings.cache_clear()
    db.get_engine.cache_clear()
    try:
        assert cli.main(["doctor", "--json"]) == 0  # an empty, migrated database: warnings only
        data = json.loads(capsys.readouterr().out)
        assert data["problems"] == 0
        assert {c["name"] for c in data["checks"]} >= {"schema", "jobs", "settlements", "disk"}
        assert cli.main(["doctor", "--quiet"]) == 0
        assert capsys.readouterr().out.startswith("footy doctor, ")
    finally:
        config.get_settings.cache_clear()
        db.get_engine.cache_clear()


def test_date_helpers():
    assert doctor._uk(datetime(2026, 7, 1, 12, 0, tzinfo=UTC)) == "Wed 01 Jul 13:00"
    assert doctor._uk(None) == "never"
    assert doctor.worst(OK, WARN, SKIPPED) == WARN
    assert doctor.worst(WARN, PROBLEM) == PROBLEM
    assert date(2026, 10, 22) == NOW.date()
