"""Hit rates per league or competition and market (footy.modeling.hit_rates).

SYNTHETIC TEST DATA: made-up ledgers with fictional teams, probabilities and results. Nothing
here is a real prediction, result or price.
"""

import json
import math

import pandas as pd
import pytest

from footy import models_of_record
from footy.modeling import hit_rates as hr
from footy.reference import REFERENCE_DIR

DOM = hr.Source(
    "domestic",
    "synthetic_model",
    hr.MODEL_ONLY,
    "synthetic_model__test__2425",
    "synthetic_base",
    "synthetic_base__test__2425",
    "backtests",
    "league",
)
EURO = hr.Source(
    "uefa",
    "synthetic_euro",
    hr.MODEL_ONLY,
    "synthetic_euro__test",
    "synthetic_euro_base",
    "synthetic_euro_base__test",
    "backtests_euro",
    "comp",
)


def row(model, i, league, p, hits, status="predicted", key="league", goals=(1, 0)):
    """SYNTHETIC ledger row: `p` = (p_H, p_D, p_A, p_over_2.5, p_btts); `hits` = the 0/1
    correct flags for 1X2, O/U 1.5, 2.5, 3.5 and BTTS."""
    out = {
        "model": model,
        key: league,
        "match_id": i,
        "season": "2425",
        "date": "2024-09-01",
        "home_team": f"syn-home-{i}",
        "away_team": f"syn-away-{i}",
        "home_goals": goals[0],
        "away_goals": goals[1],
        "status": status,
        "p_H": p[0],
        "p_D": p[1],
        "p_A": p[2],
        "p_over_2.5": p[3],
        "p_btts": p[4],
    }
    for col, h in zip(hr.MARKETS.values(), hits, strict=True):
        out[col] = h if status == "predicted" else None
    return out


def write(root, run_id, model, rows):
    d = root / run_id
    d.mkdir(parents=True)
    (d / "manifest.json").write_text(
        json.dumps(
            {
                "run_id": run_id,
                "model": model,
                "split": "test",
                "created_at_utc": "2026-10-01T00:00:00+00:00",
            }
        )
    )
    pd.DataFrame(rows).to_csv(d / "ledger.csv", index=False)


@pytest.fixture
def dom_runs(tmp_path):
    """Five SYNTHETIC fixtures: four in E0, one in SP1; the model excluded fixture 5 and the
    baseline also predicts it (so it must drop out)."""
    root = tmp_path / "backtests"
    model = [
        row("synthetic_model", 1, "E0", (0.72, 0.18, 0.10, 0.65, 0.35), (1, 1, 1, 1, 1)),
        row("synthetic_model", 2, "E0", (0.61, 0.24, 0.15, 0.40, 0.55), (0, 1, 1, 0, 1)),
        row("synthetic_model", 3, "E0", (0.45, 0.30, 0.25, 0.52, 0.50), (1, 1, 0, 1, 0)),
        row("synthetic_model", 4, "E0", (0.20, 0.30, 0.50, 0.58, 0.62), (0, 0, 0, 1, 0)),
        row("synthetic_model", 6, "SP1", (0.55, 0.25, 0.20, 0.30, 0.40), (1, 1, 1, 1, 1)),
        row("synthetic_model", 5, "SP1", (0.5, 0.3, 0.2, 0.5, 0.5), (0,) * 5, status="excluded"),
    ]
    base = [
        row("synthetic_base", i, lg, (0.45, 0.27, 0.28, 0.5, 0.52), h)
        for i, lg, h in [
            (1, "E0", (1, 1, 0, 1, 1)),
            (2, "E0", (0, 1, 0, 0, 1)),
            (3, "E0", (1, 1, 1, 1, 0)),
            (4, "E0", (0, 0, 1, 1, 0)),
            (5, "SP1", (1, 1, 1, 1, 1)),  # the model has no prediction for this one
            (6, "SP1", (0, 1, 1, 1, 0)),
        ]
    ]
    write(root, DOM.run_id, "synthetic_model", model)
    write(root, DOM.baseline_run_id, "synthetic_base", base)
    return tmp_path


def by_code(table):
    return {r["code"]: r for r in table["rows"]}


def test_wilson_matches_the_formula_and_edge_cases():
    lo, hi = hr.wilson(50, 100)
    assert (round(lo, 4), round(hi, 4)) == (0.4038, 0.5962)
    lo, hi = hr.wilson(0, 10)
    assert lo == 0.0 and math.isclose(hi, 0.2775, abs_tol=1e-4)
    lo, hi = hr.wilson(10, 10)
    assert hi == 1.0 and math.isclose(lo, 0.7225, abs_tol=1e-4)
    assert hr.wilson(0, 0) is None
    assert hr.rate([]) == {"n": 0, "correct": 0, "hit_rate": None, "ci95": None}
    r = hr.rate([1, 0, float("nan"), True])
    assert (r["n"], r["correct"]) == (3, 2) and math.isclose(r["hit_rate"], 2 / 3)


def test_hit_rates_per_group_against_the_baseline_on_the_same_fixtures(dom_runs):
    t = hr.source_table(DOM, dom_runs)
    assert (t["model"], t["run_id"], t["baseline_run_id"]) == (
        "synthetic_model",
        DOM.run_id,
        DOM.baseline_run_id,
    )
    assert t["seasons"] == ["2425"] and t["games"] == 5
    assert t["run_created_at_utc"] == "2026-10-01T00:00:00+00:00"
    rows = by_code(t)
    assert [r["code"] for r in t["rows"]] == ["ALL", "E0", "SP1"]
    assert [r["name"] for r in t["rows"]] == ["All", "Premier League", "La Liga"]
    assert (rows["ALL"]["games"], rows["E0"]["games"], rows["SP1"]["games"]) == (5, 4, 1)

    res = rows["ALL"]["markets"]["1X2"]
    assert (res["n"], res["correct"]) == (5, 3) and math.isclose(res["hit_rate"], 0.6)
    assert res["ci95"] == pytest.approx(list(hr.wilson(3, 5)))
    # The baseline is counted on the same five fixtures: fixture 5 (only the baseline
    # predicted it) is left out, so its 1X2 record is 2 of 5, not 3 of 6.
    assert (res["baseline"]["n"], res["baseline"]["correct"]) == (5, 2)
    e0 = rows["E0"]["markets"]
    assert (e0["OU_2.5"]["correct"], e0["OU_2.5"]["baseline"]["correct"]) == (2, 2)
    assert (e0["OU_3.5"]["correct"], e0["BTTS"]["correct"]) == (3, 2)
    assert rows["SP1"]["markets"]["OU_1.5"]["hit_rate"] == 1.0
    assert set(rows["ALL"]["markets"]) == set(hr.MARKETS)


def test_confidence_buckets(dom_runs):
    conf = by_code(hr.source_table(DOM, dom_runs))["ALL"]["confidence"]
    res = {b["threshold"]: b for b in conf["1X2"]}
    # Favourites: 0.72, 0.61, 0.45, 0.50 (away), 0.55.
    assert (res[0.5]["games"], res[0.5]["correct"]) == (4, 2)  # 0.72, 0.61, 0.50, 0.55
    assert (res[0.6]["games"], res[0.6]["correct"]) == (2, 1)
    assert (res[0.7]["games"], res[0.7]["correct"]) == (1, 1)
    assert math.isclose(res[0.6]["share"], 2 / 5) and res[0.6]["of_games"] == 5
    assert res[0.7]["ci95"] == pytest.approx(list(hr.wilson(1, 1)))
    # Two-way markets, 60% or more either way: over 2.5 at 0.65, 0.40 (under 0.60,
    # exactly on the line) and 0.30; BTTS at 0.35 (no 0.65), 0.62 and 0.40 (no 0.60).
    (ou,) = conf["OU_2.5"]
    assert (ou["threshold"], ou["games"], ou["correct"]) == (0.6, 3, 3)
    (btts,) = conf["BTTS"]
    assert (btts["games"], btts["correct"]) == (3, 2)


def test_mismatched_or_mislabelled_ledgers_are_refused(dom_runs):
    path = dom_runs / "backtests" / DOM.baseline_run_id / "ledger.csv"
    led = pd.read_csv(path)
    led.loc[led.match_id == 2, "home_team"] = "syn-somebody-else"
    led.to_csv(path, index=False)
    with pytest.raises(hr.LedgerMismatch, match="home_team"):
        hr.source_table(DOM, dom_runs)
    tables, missing = hr.backtest_tables(dom_runs, [DOM])
    assert tables == [] and "home_team" in missing[0]["reason"]

    led.loc[led.match_id == 2, "home_team"] = "syn-home-2"
    led["model"] = "not_the_baseline"
    led.to_csv(path, index=False)
    with pytest.raises(hr.LedgerMismatch, match="not_the_baseline"):
        hr.source_table(DOM, dom_runs)


def test_a_missing_ledger_is_reported_not_raised(dom_runs):
    tables, missing = hr.backtest_tables(dom_runs, [DOM, EURO])
    assert [t["model"] for t in tables] == ["synthetic_model"]
    assert missing == [
        {
            "family": "uefa",
            "model": "synthetic_euro",
            "reason": "ledger not saved",
            "runs": [EURO.run_id, EURO.baseline_run_id],
        }
    ]


def test_uefa_ledgers_group_by_competition_in_order(tmp_path):
    root = tmp_path / "backtests_euro"
    comps = ["UECLQ", "UCL", "UEL", "UCL"]
    model = [
        row("synthetic_euro", i, c, (0.5, 0.25, 0.25, 0.5, 0.5), (1, 1, 0, 1, 0), key="comp")
        for i, c in enumerate(comps)
    ]
    base = [
        row("synthetic_euro_base", i, c, (0.4, 0.3, 0.3, 0.5, 0.5), (0, 1, 0, 1, 1), key="comp")
        for i, c in enumerate(comps)
    ]
    write(root, EURO.run_id, "synthetic_euro", model)
    write(root, EURO.baseline_run_id, "synthetic_euro_base", base)
    t = hr.source_table(EURO, tmp_path)
    assert [r["code"] for r in t["rows"]] == ["ALL", "UCL", "UEL", "UECLQ"]
    assert t["rows"][-1]["name"] == "Conference League qualifying"
    ucl = by_code(t)["UCL"]["markets"]
    assert (ucl["1X2"]["n"], ucl["1X2"]["hit_rate"], ucl["1X2"]["baseline"]["hit_rate"]) == (
        2,
        1.0,
        0.0,
    )


def test_prematch_tables_empty_and_counted():
    assert hr.prematch_tables([]) == []
    rec = [
        hr.SettledRecord(
            "domestic",
            "synthetic_tilt",
            hr.MODEL_AND_MARKET,
            "E0",
            {"1X2": {"correct": True}, "OU_2.5": {"correct": False}},
        ),
        hr.SettledRecord(
            "domestic", "synthetic_pure", hr.MODEL_ONLY, "SP1", {"1X2": {"correct": False}}
        ),
        hr.SettledRecord(
            "uefa", "synthetic_euro", hr.MODEL_ONLY, "UCL", {"1X2": {"correct": True}}
        ),
    ]
    tables = hr.prematch_tables(rec)
    assert [t["family"] for t in tables] == ["domestic", "uefa"]
    dom = tables[0]
    assert dom["games"] == 2
    assert dom["models"] == {"synthetic_pure": 1, "synthetic_tilt": 1}
    assert dom["bases"] == {hr.MODEL_AND_MARKET: 1, hr.MODEL_ONLY: 1}
    rows = by_code(dom)
    assert [r["code"] for r in dom["rows"]] == ["ALL", "E0", "SP1"]
    assert (rows["ALL"]["markets"]["1X2"]["n"], rows["ALL"]["markets"]["1X2"]["correct"]) == (2, 1)
    assert rows["ALL"]["markets"]["OU_2.5"]["n"] == 1  # only one record settled that market
    assert "OU_2.5" not in rows["SP1"]["markets"]


def test_sources_are_models_of_record_now():
    """Each source's model must be in the latest period of the real primary_models.json
    (read only), so a switch of model of record can't leave the hit rates on an old model."""
    periods = models_of_record.load(REFERENCE_DIR / "primary_models.json")
    for s in hr.SOURCES:
        assert s.model in periods[s.family][-1].models, s.model
        assert s.run_id.startswith(f"{s.model}__test")
