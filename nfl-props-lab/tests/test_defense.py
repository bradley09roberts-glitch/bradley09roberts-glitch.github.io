"""Team defence profiles: opponent adjustment, shrinkage, labels and no look-ahead."""

import numpy as np
import pandas as pd
import pytest

from npl import defense
from npl.config import LAKE_DIR

YPT = defense.Metric("ypt_X", "vs_WR", "Yards per target", "yds", "tgt", "lower", "dec2")


def _frame(rows):
    g = pd.DataFrame(rows, columns=["game_id", "season", "week", "offense", "defense", "yds",
                                    "tgt"])
    g["games"] = 1.0
    return g


def test_expected_uses_opponents_other_games_only():
    # offence O: 10 yds/target in its two other games, 4 against defence D
    g = _frame([("g1", 2026, 1, "O", "D", 40, 10), ("g2", 2026, 2, "O", "E", 100, 10),
                ("g3", 2026, 3, "O", "F", 100, 10), ("g1", 2026, 1, "D", "O", 50, 10)])
    r = defense._metric_rows(g, g, YPT).set_index("team")
    assert r.loc["D", "actual"] == pytest.approx(4.0)
    assert r.loc["D", "expected"] == pytest.approx(10.0)
    assert r.loc["D", "diff"] == pytest.approx(-6.0)
    # D's offence has only one game, so O's expected falls back to the league rate
    assert r.loc["O", "expected"] == pytest.approx(290 / 40)


def test_shrinkage_probability_and_labels():
    rows = pd.DataFrame({"team": ["A", "B", "C"], "metric": "m", "better": "lower",
                         "games": [6, 6, 6], "diff": [-3.0, 0.0, 3.0], "res_sd": 2.0})
    out = defense._shrink(rows, {"m": {"K": 6.0, "sigma": 4.0}}).set_index("team")
    assert out.loc["A", "shrunk"] == pytest.approx(-1.5)  # half carries over (n = K)
    assert out.loc["A", "se"] == pytest.approx(4.0 / np.sqrt(12))
    assert out.loc["B", "p_better"] == pytest.approx(0.5)
    assert out.loc["A", "p_better"] > 0.9 and out.loc["A", "verdict"] == "likely strong"
    assert out.loc["C", "verdict"] == "likely weak"
    assert list(out["rank"]) == [1, 2, 3]
    # a noisy measure (large K) cannot earn a label from the same evidence
    noisy = defense._shrink(rows, {"m": {"K": 300.0, "sigma": 4.0}}).set_index("team")
    assert (noisy.verdict == "can't tell yet").all()


def test_summary_needs_clear_evidence():
    def m(v):
        return {"label": "x", "verdict": v}
    cols = defense.HEAT_COLUMNS
    none = {k: m("can't tell yet") for k in cols}
    assert defense.summarise(none)["tag"] == "no clear strengths or weaknesses yet"
    good = {**none, "ypp": m("likely strong"), "net_ypd": m("likely strong"),
            "ypc": m("likely strong")}
    assert defense.summarise(good)["tag"] == "strong across the board"
    mixed = {**good, "ypg_TE": m("likely weak")}
    assert defense.summarise(mixed)["tag"].startswith("mixed")


lake_built = pytest.mark.skipif(not (LAKE_DIR / "plays.parquet").exists(),
                                reason="lake not built")


@pytest.fixture(scope="module")
def lake():
    return {k: pd.read_parquet(LAKE_DIR / f"{k}.parquet")
            for k in ("games", "team_games", "player_games", "plays")}


@lake_built
def test_profiles_2026_week7_and_no_look_ahead(lake):
    prof = defense.profiles(lake, 2026, 7)
    assert len(prof["teams"]) == 32
    for key in defense.HEAT_COLUMNS:
        ranks = [t["metrics"][key]["rank"] for t in prof["teams"].values()]
        assert min(ranks) == 1 and max(ranks) <= 32
    for t in prof["teams"].values():
        for x in t["metrics"].values():
            assert 0 <= x["p_better"] <= 1
            assert (x["verdict"] != "can't tell yet") == (abs(x["p_better"] - 0.5) >= 0.3)
    # removing every play from week 7 on changes nothing
    cut = {**lake, "plays": lake["plays"][~((lake["plays"].season == 2026) &
                                            (lake["plays"].week >= 7))]}
    prof2 = defense.profiles(cut, 2026, 7)
    a = prof["teams"]["KC"]["metrics"]["net_ypd"]
    b = prof2["teams"]["KC"]["metrics"]["net_ypd"]
    assert a["shrunk"] == pytest.approx(b["shrunk"]) and a["rank"] == b["rank"]


def test_calibration_file_covers_every_metric():
    cal = defense.load_calibration()
    if not cal:
        pytest.skip("stability study not run")
    assert {m.key for m in defense.METRICS} <= set(cal)
    assert all(v["K"] > 0 and v["sigma"] > 0 for v in cal.values())
