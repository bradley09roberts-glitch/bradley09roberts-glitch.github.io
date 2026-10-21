"""GET /api/hit-rates: test-season hit rates against the baseline, and the pre-match record.

SYNTHETIC TEST DATA: fictional teams (fdfixtures), made-up ledgers, probabilities and results.
Nothing here is a real prediction, result or price.
"""

import json
from datetime import UTC, datetime, timedelta

import pandas as pd
import pytest
from sqlalchemy import text
from sqlalchemy.orm import Session

from fdfixtures import REFERENCE
from footy.api import routes
from footy.ingest.fd_import import sync_reference
from footy.modeling import hit_rates as hr
from footy.modeling.grid import markets_from_grid, poisson_grid
from footy.publish import FixtureIn, publish, settle

NOW = datetime.now(UTC).replace(microsecond=0)
DAY = (NOW + timedelta(days=2)).date()
KICKOFF = datetime(DAY.year, DAY.month, DAY.day, 14, 0, tzinfo=UTC)

SOURCE = hr.Source(
    "domestic",
    "synthetic_model",
    hr.MODEL_ONLY,
    "synthetic_model__test__2425",
    "synthetic_base",
    "synthetic_base__test__2425",
    "backtests",
    "league",
)


def ledger(model: str, hits: list[int]) -> pd.DataFrame:
    """SYNTHETIC: one E0 fixture per entry of `hits` (the 0/1 flag used for every market)."""
    return pd.DataFrame(
        [
            {
                "model": model,
                "league": "E0",
                "match_id": i,
                "season": "2425",
                "date": "2024-09-01",
                "home_team": f"syn-home-{i}",
                "away_team": f"syn-away-{i}",
                "home_goals": 1,
                "away_goals": 0,
                "status": "predicted",
                "p_H": 0.65,
                "p_D": 0.2,
                "p_A": 0.15,
                "p_over_2.5": 0.45,
                "p_btts": 0.5,
                **{col: h for col in hr.MARKETS.values()},
            }
            for i, h in enumerate(hits)
        ]
    )


@pytest.fixture
def runs(settings, monkeypatch):
    monkeypatch.setattr(hr, "SOURCES", (SOURCE,))
    root = settings.data_dir / "backtests"
    for run_id, model, hits in (
        (SOURCE.run_id, "synthetic_model", [1, 1, 1, 0]),
        (SOURCE.baseline_run_id, "synthetic_base", [1, 0, 0, 0]),
    ):
        d = root / run_id
        d.mkdir(parents=True)
        manifest = {"model": model, "split": "test", "created_at_utc": "2026-10-01 06:00:00"}
        (d / "manifest.json").write_text(json.dumps(manifest))
        ledger(model, hits).to_csv(d / "ledger.csv", index=False)


@pytest.fixture
def primary_file(tmp_path, monkeypatch):
    """A temporary primary_models.json; the real file is never touched."""
    path = tmp_path / "primary_models.json"
    monkeypatch.setattr(routes, "PRIMARY_MODELS_PATH", path)
    path.write_text(json.dumps({"_comment": "SYNTHETIC", "domestic": ["mor_m", "other_m"]}))


def publish_and_settle(engine, tmp_path, hg, ag):
    """Two SYNTHETIC records for one fixture (the model of record's and another model's),
    then the result, then settlement."""
    with Session(engine) as s:
        sync_reference(s, *REFERENCE)
        s.commit()
    fx = FixtureIn(
        "E0",
        "2627",
        DAY,
        KICKOFF,
        "eng-testtown",
        "eng-sampleton",
        features={"home_matches_in_window": 40, "away_matches_in_window": 38},
        missing_fields=["lineups", "injuries", "odds"],
    )
    mp = markets_from_grid(poisson_grid([1.6], [0.9]), [1.6], [0.9])  # picks the home win
    for model in ("other_m", "mor_m"):
        publish(
            engine,
            [fx],
            mp,
            model_name=model,
            model_version="v-test",
            training_cutoff_utc=NOW - timedelta(days=1),
            data_freshness={"latest_result_by_division": {"E0": "2026-10-19"}},
            now=NOW,
            records_dir=tmp_path / "records",
        )
    with engine.begin() as conn:
        conn.execute(
            text(
                "INSERT INTO raw_files (id, source, logical_key, revision, sha256, size_bytes, "
                "retrieved_at, storage_path) VALUES (1, 't', 'k', 1, 'x', 1, '2026-10-25', 'p')"
            )
        )
        conn.execute(
            text(
                "INSERT INTO matches (division, season, match_date, home_team, away_team, "
                "home_goals, away_goals, result, source_row, first_raw_file_id, "
                "last_raw_file_id) VALUES ('E0', '2627', :d, 'eng-testtown', 'eng-sampleton', "
                ":hg, :ag, 'H', 1, 1, 1)"
            ),
            {"d": DAY.isoformat(), "hg": hg, "ag": ag},
        )
    assert settle(engine, now=KICKOFF + timedelta(hours=3))["settled"] == 2


def test_hit_rates_from_saved_ledgers(client, runs):
    body = client.get("/api/hit-rates").json()
    assert body["generated_at_utc"].endswith("+00:00")
    assert [m["key"] for m in body["markets"]] == ["1X2", "OU_1.5", "OU_2.5", "OU_3.5", "BTTS"]
    assert body["markets"][0]["label"] == "Match result (1X2)"
    assert body["confidence_thresholds"] == {"1X2": [0.5, 0.6, 0.7], "OU_2.5": [0.6], "BTTS": [0.6]}
    assert body["backtest"]["missing"] == []
    (t,) = body["backtest"]["tables"]
    assert (t["run_id"], t["baseline_run_id"], t["seasons"]) == (
        SOURCE.run_id,
        SOURCE.baseline_run_id,
        ["2425"],
    )
    assert t["run_created_at_utc"] == "2026-10-01T06:00:00+00:00"  # naive saved time is UTC
    assert t["baseline_run_created_at_utc"] == "2026-10-01T06:00:00+00:00"
    all_, e0 = t["rows"]
    assert (all_["code"], e0["code"], e0["name"], e0["games"]) == ("ALL", "E0", "Premier League", 4)
    res = all_["markets"]["1X2"]
    assert (res["correct"], res["n"], res["hit_rate"]) == (3, 4, 0.75)
    assert (res["baseline"]["correct"], res["baseline"]["hit_rate"]) == (1, 0.25)
    lo, hi = res["ci95"]
    assert 0 < lo < 0.75 < hi < 1
    (b60,) = [b for b in all_["confidence"]["1X2"] if b["threshold"] == 0.6]
    assert (b60["games"], b60["share"], b60["hit_rate"]) == (4, 1.0, 0.75)
    # Nothing published or settled.
    assert body["prematch"] == {
        "published": 0,
        "settled": 0,
        "last_settled_at_utc": None,
        "tables": [],
    }


def test_missing_ledgers_are_listed(client, settings, monkeypatch):
    monkeypatch.setattr(hr, "SOURCES", (SOURCE,))
    body = client.get("/api/hit-rates").json()
    assert body["backtest"]["tables"] == []
    assert body["backtest"]["missing"] == [
        {
            "family": "domestic",
            "model": "synthetic_model",
            "reason": "ledger not saved",
            "runs": [SOURCE.run_id, SOURCE.baseline_run_id],
        }
    ]


def test_prematch_record_counts_the_record_of_record_once(
    client, migrated_engine, tmp_path, runs, primary_file
):
    publish_and_settle(migrated_engine, tmp_path, 2, 2)  # a draw: the home pick was wrong
    pre = client.get("/api/hit-rates").json()["prematch"]
    assert (pre["published"], pre["settled"]) == (1, 1)  # one fixture, not two records
    assert pre["last_settled_at_utc"].endswith("+00:00")
    (t,) = pre["tables"]
    assert (t["family"], t["family_name"], t["games"]) == ("domestic", "League games", 1)
    assert t["models"] == {"mor_m": 1} and t["bases"] == {"model_only": 1}
    all_, e0 = t["rows"]
    assert (all_["code"], e0["code"]) == ("ALL", "E0")
    assert (e0["markets"]["1X2"]["n"], e0["markets"]["1X2"]["correct"]) == (1, 0)
    assert e0["markets"]["1X2"]["ci95"][0] == 0.0
    assert e0["markets"]["OU_3.5"]["correct"] == 0  # 2-2 is over 3.5; the pick was under
    assert "baseline" not in e0["markets"]["1X2"]
