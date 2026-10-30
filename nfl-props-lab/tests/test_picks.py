"""Pick rules (D-028): the gap window and the shrunk estimate."""

import pytest

from npl import picks


def test_estimate_trusts_a_third_of_the_gap():
    assert picks.estimate(0.70, 0.52) == pytest.approx(0.58)
    assert picks.GAP_MIN == 0.06 and picks.GAP_MAX == 0.15 and picks.NBA_GAP_MIN == 0.10


def test_table_lists_picks_and_leans_separately():
    row = {"player": "A", "team": "X", "opponent": "Y", "market": "pts", "side": "over", "line": 10.5,
           "model_low": 0.6, "market_p": 0.5, "estimate": 0.53, "min_price": 1.89, "best_price": 1.95}
    t = picks.table({"picks": [row], "leans": [], "left_out_15_plus": 2})
    assert t.startswith("Picks:\n") and "Leans (6-10 points, not picks): none" in t and "2 props left out" in t


def test_finish_adds_estimate_minimum_price_and_sorts_by_gap():
    rows = picks._finish([{"model_low": 0.60, "market_p": 0.50}, {"model_low": 0.64, "market_p": 0.52}])
    assert [r["gap"] for r in rows] == [0.12, 0.1]
    assert rows[0]["estimate"] == pytest.approx(0.56) and rows[0]["min_price"] == pytest.approx(1 / 0.56, abs=0.01)
