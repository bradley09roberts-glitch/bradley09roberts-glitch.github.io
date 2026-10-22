"""`footy euro-import-results` (DECISIONS.md D-029): UEFA results from news reports, two
publishers each, while Wikipedia can't be fetched. Scores, URLs and records here are SYNTHETIC;
club keys are real only so that the committed reference files resolve them."""

import csv
import json
from datetime import UTC, date, datetime, timedelta
from functools import partial

from sqlalchemy import select
from sqlalchemy.orm import Session

from footy import cli
from footy.europe.euro_load import run_euro_update
from footy.europe.manual_results import import_results, registered_domain
from footy.europe.wikipedia_2627 import CSV_COLUMNS
from footy.ingest.fd_import import sync_reference
from footy.ingest.raw_store import store_raw
from footy.models import (
    DataCorrection,
    EuroMatch,
    JobRun,
    Prediction,
    PredictionSettlement,
    RawFile,
)
from footy.publish import settle

KICKOFF = datetime(2026, 10, 21, 19, 0, tzinfo=UTC)
AFTER = KICKOFF + timedelta(hours=3)
REF = "UCL:2627:2026-10-21:ita-como:eng-man-united"
SKY = {"publisher": "Sky Sports", "url": "https://www.skysports.com/football/news/1/como-4-1"}
TNT = {"publisher": "TNT Sports", "url": "https://www.tntsports.co.uk/football/live/2"}


def setup(engine, raw_dir, *, status="scheduled", goals=(None, None)):
    """Como v Manchester United, as the Wikipedia load left it, and one record for it."""
    with Session(engine) as s:
        sync_reference(s)
        page = {"parse": {"title": "UCL league phase", "revid": 7, "wikitext": "x"}}
        raw = store_raw(
            s,
            raw_dir,
            source="wikipedia",
            logical_key="ucl/2627/league-phase",
            content=json.dumps(page).encode(),
            retrieved_at=KICKOFF - timedelta(days=1),
            suffix=".json",
        ).raw
        s.add(
            EuroMatch(
                competition="UCL",
                season="2627",
                stage="League MD3",
                match_date=date(2026, 10, 21),
                kickoff_utc=KICKOFF,
                home_team="ita-como",
                away_team="eng-man-united",
                home_goals=goals[0],
                away_goals=goals[1],
                extra_time=False,
                penalties=False,
                neutral=False,
                status=status,
                source="wikipedia",
                source_ref="Synthetic page#Matchday 3 (rev 7, UEFA match 1)",
                raw_file_id=raw.id,
                model_exclusion=None,
            )
        )
        probs = {"1X2": {"H": 0.3, "D": 0.3, "A": 0.4}, "BTTS": {"Y": 0.6, "N": 0.4}}
        s.add(
            Prediction(
                fixture_ref=REF,
                competition="UCL",
                season="2627",
                kickoff_utc=KICKOFF,
                match_date=date(2026, 10, 21),
                home_team="ita-como",
                away_team="eng-man-united",
                edition="early",
                record_type="genuine_prematch",
                model_name="m",
                model_version="v1",
                training_cutoff_utc=KICKOFF - timedelta(days=1),
                created_at_utc=KICKOFF - timedelta(hours=5),
                probabilities=json.dumps(probs),
                selections=json.dumps({"1X2": {"pick": "A"}, "BTTS": {"pick": "Y"}}),
                features="{}",
                data_freshness="{}",
                missing_fields="[]",
            )
        )
        s.commit()
        return raw.id


def write(tmp_path, results, name="results.json"):
    path = tmp_path / name
    path.write_text(json.dumps({"results": results}), encoding="utf-8")
    return path


def entry(hg=4, ag=1, sources=(SKY, TNT), **kw):
    return {"fixture_ref": REF, "home_goals": hg, "away_goals": ag, "sources": list(sources)} | kw


def match(engine) -> EuroMatch:
    with Session(engine) as s:
        return s.scalars(select(EuroMatch)).one()


def test_import_fills_the_scheduled_row_and_settles_it(migrated_engine, settings, tmp_path):
    setup(migrated_engine, settings.raw_dir)
    path = write(tmp_path, [entry()])
    report = import_results(migrated_engine, settings.raw_dir, path, now=AFTER)
    assert [e.action for e in report.entries] == ["applied"]
    m = match(migrated_engine)
    assert (m.status, m.home_goals, m.away_goals, m.source) == ("played", 4, 1, "manual")
    ref = json.loads(m.source_ref)
    assert ref["import"] == "results.json" and ref["sources"] == [SKY, TNT]
    with Session(migrated_engine) as s:
        raw = s.get(RawFile, m.raw_file_id)
        assert (raw.source, raw.logical_key) == ("manual", "euro-results/results.json")
        assert (settings.raw_dir / raw.storage_path).read_bytes() == path.read_bytes()

    assert settle(migrated_engine, now=AFTER)["settled"] == 1
    with Session(migrated_engine) as s:
        st = s.scalars(select(PredictionSettlement)).one()
    assert (st.home_goals, st.away_goals, st.source) == (4, 1, "euro_matches:manual")
    assert json.loads(st.outcomes)["1X2"]["actual"] == "H"

    again = import_results(migrated_engine, settings.raw_dir, path, now=AFTER)
    assert [e.action for e in again.entries] == ["unchanged"]
    assert again.raw_file_id == report.raw_file_id  # same file, same raw revision


def test_weak_or_forbidden_evidence_is_refused(migrated_engine, settings, tmp_path):
    setup(migrated_engine, settings.raw_dir)
    same_site = {"publisher": "Sky Sports News", "url": "https://news.skysports.com/x"}
    uefa = {"publisher": "UEFA", "url": "https://www.uefa.com/uefachampionsleague/match/1/"}
    path = write(
        tmp_path,
        [
            entry(sources=[SKY]),
            entry(sources=[SKY, same_site]),
            entry(sources=[SKY, uefa]),
            entry(sources=[SKY, TNT | {"home_goals": 4, "away_goals": 2}]),
            entry(fixture_ref="UCL:2627:2026-10-22:ita-como:eng-man-united"),
            {
                "fixture_ref": "SP1:2627:2026-10-21:esp-levante:esp-ath-bilbao",
                "home_goals": 3,
                "away_goals": 1,
                "sources": [SKY, TNT],
            },  # fmt: skip
        ],
    )
    report = import_results(migrated_engine, settings.raw_dir, path, now=AFTER)
    actions = [(e.action, e.detail) for e in report.entries]
    assert [a for a, _ in actions] == ["refused"] * 5 + ["skipped"]
    assert "1 independent publisher" in actions[0][1]
    assert "1 independent publisher" in actions[1][1]  # one domain
    assert "uefa.com may not be used" in actions[2][1]
    assert "TNT Sports gives 4-2" in actions[3][1]
    assert actions[4][1] == "no such match in euro_matches"
    m = match(migrated_engine)
    assert (m.status, m.source) == ("scheduled", "wikipedia")  # nothing changed

    early = import_results(migrated_engine, settings.raw_dir, write(tmp_path, [entry()]),
                           now=KICKOFF + timedelta(minutes=100))  # fmt: skip
    assert early.entries[0].action == "refused" and "not finished" in early.entries[0].detail
    assert registered_domain("https://news.bbc.co.uk/x") == "bbc.co.uk"


def test_a_different_stored_score_is_never_overwritten_silently(
    migrated_engine, settings, tmp_path
):
    wiki_raw = setup(migrated_engine, settings.raw_dir, status="played", goals=(4, 2))
    path = write(tmp_path, [entry()])
    report = import_results(migrated_engine, settings.raw_dir, path, now=AFTER)
    assert report.entries[0].action == "refused" and "--correct" in report.entries[0].detail
    assert (match(migrated_engine).away_goals, match(migrated_engine).source) == (2, "wikipedia")

    report = import_results(migrated_engine, settings.raw_dir, path, now=AFTER, correct=True)
    assert report.entries[0].action == "corrected"
    m = match(migrated_engine)
    assert (m.home_goals, m.away_goals, m.source) == (4, 1, "manual")
    with Session(migrated_engine) as s:
        (c,) = s.scalars(select(DataCorrection)).all()
    assert (c.entity_id, c.field, c.old_value, c.new_value) == (m.id, "away_goals", "2", "1")
    assert (c.old_raw_file_id, c.new_raw_file_id) == (wiki_raw, report.raw_file_id)


def _wiki_csv(path, **kw):
    row = {c: "" for c in CSV_COLUMNS} | {
        "competition": "UCL",
        "season": "2627",
        "stage": "League MD3",
        "match_date": "2026-10-21",
        "kickoff_utc": KICKOFF.isoformat(),
        "home_name": "Como",
        "home_country": "ITA",
        "away_name": "Manchester United",
        "away_country": "ENG",
        "extra_time": "0",
        "penalties": "0",
        "neutral": "0",
        "status": "scheduled",
        "source": "wikipedia",
        "source_ref": "Synthetic page#Matchday 3 (rev 7, UEFA match 1)",
    }
    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=CSV_COLUMNS)
        w.writeheader()
        w.writerow(row | kw)


def test_a_later_wikipedia_load_keeps_confirms_or_corrects(migrated_engine, settings, tmp_path):
    setup(migrated_engine, settings.raw_dir)
    import_results(migrated_engine, settings.raw_dir, write(tmp_path, [entry()]), now=AFTER)
    csv_path = settings.data_dir / "europe" / "uefa_2627.csv"
    load = partial(
        run_euro_update,
        migrated_engine,
        settings,
        openfootball_dir=tmp_path / "no-clone",
        wiki_csv=csv_path,
        rebuild_2627=False,
    )

    _wiki_csv(csv_path)  # Wikipedia still has no score: the manual result stays
    detail = load()["wikipedia"]
    assert detail["manual_kept"] == 1
    assert (match(migrated_engine).source, match(migrated_engine).home_goals) == ("manual", 4)

    _wiki_csv(csv_path, status="played", home_goals="4", away_goals="1")  # the same result
    detail = load()["wikipedia"]
    assert detail["manual_confirmed"] == 1 and "manual_result_disagrees" not in detail["issues"]
    m = match(migrated_engine)
    assert (m.source, m.home_goals, m.away_goals) == ("wikipedia", 4, 1)
    assert m.source_ref.endswith("(rev 7, UEFA match 1)")

    # Another manual import (corrected by hand), then Wikipedia disagrees: Wikipedia's score
    # is taken, the change is a data correction, and the disagreement is an error.
    fix = write(tmp_path, [entry(hg=5)], name="fix.json")
    import_results(migrated_engine, settings.raw_dir, fix, now=AFTER, correct=True)
    detail = load()["wikipedia"]
    assert detail["manual_corrected"] == 1 and detail["issues"]["manual_result_disagrees"] == 1
    assert "(manual) but wikipedia has played 4-1" in detail["manual_disagreements"][0]
    assert (match(migrated_engine).source, match(migrated_engine).home_goals) == ("wikipedia", 4)
    with Session(migrated_engine) as s:
        fields = [(c.field, c.old_value, c.new_value) for c in s.scalars(select(DataCorrection))]
        run = s.scalars(select(JobRun).order_by(JobRun.id.desc())).first()
    assert fields == [("home_goals", "4", "5"), ("home_goals", "5", "4")]
    assert run.status == "partial"


def test_cli_dry_run_writes_nothing(tmp_path, monkeypatch, capsys, migrated_engine, settings):
    from footy import config, db

    setup(migrated_engine, settings.raw_dir)
    monkeypatch.setenv("FOOTY_DATABASE_URL", str(migrated_engine.url))
    monkeypatch.setenv("FOOTY_DATA_DIR", str(settings.data_dir))
    config.get_settings.cache_clear()
    db.get_engine.cache_clear()
    path = write(tmp_path, [entry(), entry(sources=[SKY])], name="two.json")
    try:
        assert cli.main(["euro-import-results", str(path), "--dry-run"]) == 1  # one refused
        out = json.loads(capsys.readouterr().out)
        assert out["dry_run"] and out["counts"] == {"applied": 1, "refused": 1}
        with Session(migrated_engine) as s:
            assert s.scalars(select(RawFile).where(RawFile.source == "manual")).all() == []
            assert s.scalars(select(JobRun)).all() == []
        assert match(migrated_engine).status == "scheduled"
        assert cli.main(["euro-import-results", str(write(tmp_path, [entry()]))]) == 0
        assert match(migrated_engine).source == "manual"
        with Session(migrated_engine) as s:
            (run,) = s.scalars(select(JobRun)).all()
        assert (run.job, run.status) == ("euro_import_results", "ok")
    finally:
        config.get_settings.cache_clear()
        db.get_engine.cache_clear()
