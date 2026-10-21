"""`footy euro-update` loader: raw store first, explicit team mapping, idempotent re-runs.

The openfootball file and the 2026/27 CSV here are SYNTHETIC (real club names so that the
committed alias table resolves them; dates and scores are made up)."""

import csv
import json

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from footy.europe.euro_load import run_euro_update
from footy.models import DataCorrection, EuroMatch, ImportIssue, JobRun, RawFile, Team

CL = """\
= UEFA Champions League 2018/19

▪ Group A
  Tue Sep 18 2018
    21:00  Bayern München (GER)    v Liverpool FC (ENG)       2-1 (1-0)
           SL Benfica (POR)        v AFC Ajax (NED)           0-0
▪ Final
  Sat Jun 1 2019
    21:00  Liverpool FC (ENG)      v AFC Ajax (NED)           1-1 a.e.t. (1-1, 0-0)
"""
CSV_COLUMNS = [
    "competition", "season", "stage", "match_date", "kickoff_utc", "home_name",
    "home_country", "away_name", "away_country", "home_goals", "away_goals", "extra_time",
    "penalties", "neutral", "status", "source", "source_ref",
]  # fmt: skip


def _wiki_row(**kw) -> dict:
    row = {
        "competition": "UCL",
        "season": "2627",
        "stage": "League MD3",
        "match_date": "2026-10-21",
        "kickoff_utc": "2026-10-21T19:00:00+00:00",
        "home_name": "Bayern Munich",
        "home_country": "DEU",
        "away_name": "Arsenal",
        "away_country": "ENG",
        "home_goals": "",
        "away_goals": "",
        "extra_time": "0",
        "penalties": "0",
        "neutral": "0",
        "status": "scheduled",
        "source": "wikipedia",
        "source_ref": "Synthetic page#Matchday 3 (rev 1, UEFA match 1)",
    }
    row.update(kw)
    return row


def _write_csv(path, rows):
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=CSV_COLUMNS)
        w.writeheader()
        w.writerows(rows)


def _setup(tmp_path, settings, cl=CL, rows=None):
    of_dir = tmp_path / "openfootball"
    (of_dir / "2018-19").mkdir(parents=True, exist_ok=True)
    (of_dir / "2018-19" / "cl.txt").write_text(cl, encoding="utf-8")
    csv_path = settings.data_dir / "europe" / "uefa_2627.csv"
    _write_csv(csv_path, rows if rows is not None else [_wiki_row()])
    return of_dir, csv_path


def _run(engine, settings, of_dir, csv_path):
    return run_euro_update(
        engine, settings, openfootball_dir=of_dir, wiki_csv=csv_path, rebuild_2627=False
    )


def _rows(engine):
    with Session(engine) as s:
        return {
            (m.competition, m.season, str(m.match_date), m.home_team, m.away_team): m
            for m in s.scalars(select(EuroMatch))
        }


def test_load_resolves_teams_and_flags(tmp_path, settings, migrated_engine):
    of_dir, csv_path = _setup(tmp_path, settings)
    detail = _run(migrated_engine, settings, of_dir, csv_path)
    assert detail["openfootball"]["inserted"] == 3
    assert detail["wikipedia"]["inserted"] == 1
    rows = _rows(migrated_engine)
    win = rows[("UCL", "1819", "2018-09-18", "deu-bayern-munich", "eng-liverpool")]
    assert (win.home_goals, win.away_goals, win.status, win.source) == (
        2,
        1,
        "played",
        "openfootball",
    )
    assert win.stage == "Group A" and not win.neutral
    assert win.kickoff_utc.isoformat() == "2018-09-18T19:00:00+00:00"  # 21:00 CEST
    assert win.source_ref == "2018-19/cl.txt line 5"
    final = rows[("UCL", "1819", "2019-06-01", "eng-liverpool", "nld-ajax")]
    # A one-match tie that is level after extra time must have gone to penalties.
    assert final.neutral and final.extra_time and (final.home_goals, final.away_goals) == (1, 1)
    assert final.model_exclusion == "tie_inconsistent"
    fixture = rows[("UCL", "2627", "2026-10-21", "deu-bayern-munich", "eng-arsenal")]
    assert fixture.status == "scheduled" and fixture.home_goals is None
    assert fixture.raw_file_id is None  # the synthetic page revision is not in the raw store
    with Session(migrated_engine) as s:
        raw = s.scalar(select(RawFile).where(RawFile.source == "openfootball"))
        assert raw.logical_key == "1819/cl.txt" and win.raw_file_id == raw.id
        teams = set(s.scalars(select(Team.team_key)))
        assert all(r.home_team in teams and r.away_team in teams for r in rows.values())
        runs = s.scalars(select(JobRun).where(JobRun.job == "euro_update")).all()
        assert [r.status for r in runs] == ["ok"]
        assert json.loads(runs[0].detail)["table"]["totals"]["rows"] == 4


def test_rerun_creates_no_duplicates_and_changes_nothing(tmp_path, settings, migrated_engine):
    of_dir, csv_path = _setup(tmp_path, settings)
    _run(migrated_engine, settings, of_dir, csv_path)
    before = {k: (m.id, m.home_goals, m.stage) for k, m in _rows(migrated_engine).items()}
    detail = _run(migrated_engine, settings, of_dir, csv_path)
    assert detail["openfootball_store"] == {"unchanged": 1}
    assert detail["openfootball"]["inserted"] == detail["openfootball"]["updated"] == 0
    assert detail["openfootball"]["unchanged"] == 3
    assert detail["wikipedia"]["unchanged"] == 1
    after = {k: (m.id, m.home_goals, m.stage) for k, m in _rows(migrated_engine).items()}
    assert after == before
    with Session(migrated_engine) as s:
        assert s.scalar(select(func.count()).select_from(EuroMatch)) == 4
        assert s.scalar(select(func.count()).select_from(JobRun)) == 2


def test_new_revision_corrections_and_rescheduled_fixture(tmp_path, settings, migrated_engine):
    of_dir, csv_path = _setup(tmp_path, settings)
    _run(migrated_engine, settings, of_dir, csv_path)
    # The source corrects a score, and the 2026/27 fixture moves by a day.
    (of_dir / "2018-19" / "cl.txt").write_text(CL.replace("2-1 (1-0)", "3-1 (1-0)"), "utf-8")
    _write_csv(
        csv_path, [_wiki_row(match_date="2026-10-22", kickoff_utc="2026-10-22T19:00:00+00:00")]
    )
    detail = _run(migrated_engine, settings, of_dir, csv_path)
    assert detail["openfootball_store"] == {"new_revision": 1}
    assert detail["openfootball"]["updated"] == 1 and detail["openfootball"]["corrections"] == 1
    assert detail["openfootball"]["relinked"] == 2  # same values, newer raw revision
    assert detail["wikipedia"]["inserted"] == 1 and detail["wikipedia"]["deleted"] == 1
    rows = _rows(migrated_engine)
    assert rows[("UCL", "1819", "2018-09-18", "deu-bayern-munich", "eng-liverpool")].home_goals == 3
    assert ("UCL", "2627", "2026-10-21", "deu-bayern-munich", "eng-arsenal") not in rows
    assert ("UCL", "2627", "2026-10-22", "deu-bayern-munich", "eng-arsenal") in rows
    with Session(migrated_engine) as s:
        (c,) = s.scalars(select(DataCorrection)).all()
        assert (c.entity, c.field, c.old_value, c.new_value) == (
            "euro_match",
            "home_goals",
            "2",
            "3",
        )
        assert s.scalar(select(func.count()).select_from(RawFile)) == 2


def test_unmapped_club_is_reported_and_left_out(tmp_path, settings, migrated_engine):
    cl = CL.replace("SL Benfica (POR)", "Made-up United (POR)")
    of_dir, csv_path = _setup(tmp_path, settings, cl=cl, rows=[_wiki_row(away_name="Nobody FC")])
    detail = _run(migrated_engine, settings, of_dir, csv_path)
    assert detail["openfootball"]["inserted"] == 2 and detail["openfootball"]["skipped_rows"] == 1
    assert detail["openfootball"]["issues"]["unmapped_team"] == 1
    assert detail["wikipedia"]["issues"] == {"unmapped_team": 1}
    with Session(migrated_engine) as s:
        issue = s.scalar(select(ImportIssue).where(ImportIssue.code == "unmapped_team"))
        assert issue.detail == "Made-up United (POR)" and issue.source_row == 6
        run = s.scalar(select(JobRun).where(JobRun.job == "euro_update"))
        assert run.status == "partial"
