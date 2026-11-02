"""Pick rules (D-028): the gap window and the shrunk estimate."""

import pytest

from npl import picks


def test_estimate_trusts_a_third_of_the_gap():
    assert picks.estimate(0.70, 0.52) == pytest.approx(0.58)
    assert picks.GAP_MIN == 0.06 and picks.GAP_MAX == 0.15 and picks.NBA_GAP_MIN == 0.10


def test_table_shows_tracking_and_simple_lists_as_research_only():
    row = {"player": "A", "team": "X", "opponent": "Y", "market": "reb", "side": "under", "line": 6.5,
           "p_anchor": 0.56, "p_model_cal": 0.58, "market_p": 0.53, "price": 1.95, "price_source": "betmgm", "ev": 0.092}
    t = picks.table({"tracking": [row], "simple_gap": []})
    assert t.startswith("Paper tracking") and "Simple baseline beats the line by 1+ (tracking only): none" in t
    assert "Nothing here is a bet" in t


def test_calibration_pulls_points_to_the_market_and_keeps_some_rebound_signal():
    from npl.nba import calib
    # points: the model's view carries almost no information once calibrated (slope ~0.06)
    assert abs(calib.calibrated("pts", 0.80) - calib.calibrated("pts", 0.20)) < 0.06
    # rebounds keep part of it
    assert calib.calibrated("reb", 0.80) - calib.calibrated("reb", 0.20) > 0.2
    # the anchored blend stays close to the market's price
    assert abs(calib.anchored("reb", 0.50, 0.80) - 0.50) < 0.05


def test_stars_out_flags_a_recently_absent_starter_only():
    import pandas as pd
    games = [f"g{i}" for i in range(5)]
    rows = [("S", "MIN", g, 34, 30) for g in games[:4]] + [("R", "MIN", g, 30, 12) for g in games] + \
           [("V", "LAC", g, 30, 20) for g in games[:1]] + [("W", "LAC", g, 25, 10) for g in games]
    cur = pd.DataFrame(rows, columns=["player_id", "team", "game_id", "minutes", "pts"])
    cards = [{"player_id": "S", "name": "Star", "team": "MIN", "injury": {"status": "Out"}},
             {"player_id": "V", "name": "Vet", "team": "LAC", "injury": {"status": "Out"}},
             {"player_id": "R", "name": "Role", "team": "MIN", "injury": None}]
    # S missed 1 of 5 games: flagged; V has missed 4 of 5, so the model has seen LAC without him
    assert picks.stars_out(cards, cur) == {"MIN": ["Star"]}
