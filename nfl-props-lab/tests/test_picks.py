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


def test_calibration_refuses_coefficients_fitted_on_another_model():
    from npl.nba import calib
    for v in ("nba-0.1", "nba-0.3"):  # both fitted versions load, with their own clips
        assert 0.4 < calib.calibrated("reb", 0.6, v) < 0.6
    with pytest.raises(KeyError, match="no calibration"):
        calib.calibrated("reb", 0.6, "nba-0.2")


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


def test_tracked_rule_needs_betmgm_at_its_own_line_and_a_line_known_at_publish(monkeypatch):
    import numpy as np
    import pandas as pd

    from npl.dist import Dist
    from npl.nba import appdata as A

    pm = np.exp(-((np.arange(60) - 20.0) ** 2) / 50.0)
    card = {"name": "A", "player_id": "1", "team": "X", "opponent": "Y", "game_id": "g",
            "market": "reb", "p_play": 0.99, "injury": None, "line_known": True,
            "tip_utc": (pd.Timestamp.now(tz="UTC") + pd.Timedelta(hours=2)).isoformat(),
            "tip_london": "", "dist": Dist.from_pmf(0, pm / pm.sum()).to_json(),
            "market_line": {"line": 20.5, "fair_over": 0.5, "books": 5, "mgm_over": 2.3,
                            "mgm_under": 1.6, "mgm_line": 20.5, "arb": False,
                            "observed_at": "2026-11-03T01:00:00Z"}}
    P = {"edition_id": "e", "model_version": picks.TRACK_MODEL, "cards": [card]}
    monkeypatch.setattr(A, "props", lambda: P)
    r = picks.nba()["tracking"]
    assert len(r) == 1 and r[0]["book"] == "betmgm" and r[0]["side"] == "over"
    card["line_known"] = False  # published before the day's lines: no line-player factor
    assert picks.nba()["tracking"] == []
    card["line_known"] = True
    card["market_line"] |= {"mgm_over": None, "mgm_under": None, "mgm_line": 21.5}
    assert picks.nba()["tracking"] == []  # BetMGM hangs another line: no fallback book


def test_simple_rule_is_stopped_and_only_the_betmgm_rule_is_recorded(monkeypatch, tmp_path):
    """D-049: `npl picks` records the BetMGM rule only; the simple rule makes no new flags."""
    import argparse

    from npl import cli, config, db, track

    res = {"edition_id": "e1", "model_version": picks.TRACK_MODEL,
           "tracking": [{"player": "A"}], "simple_gap": [{"player": "B"}]}
    monkeypatch.setattr(picks, "nba", lambda: res)
    monkeypatch.setattr(db, "connect", lambda *a, **k: None)
    monkeypatch.setattr(config, "DATA_HOME", tmp_path)
    calls = []
    monkeypatch.setattr(track, "record",
                        lambda con, rows, *, rule, **k: calls.append((rule, rows)) or len(rows))
    assert cli.cmd_picks(argparse.Namespace(sport="nba", game=None)) == 0
    assert calls == [(picks.TRACK_RULE, [{"player": "A"}])]
    assert picks.SIMPLE_STOPPED.startswith("5 Nov 2026")


def test_track_table_labels_the_stopped_simple_rule():
    from npl import track

    s = {"flagged": 2, "won": 1, "lost": 1, "void": 0, "open": 0, "slates": 1}
    rep = {"rules": {picks.SIMPLE_RULE: s, picks.TRACK_RULE: s},
           "rows": [{c: None for c in ["rule", "player", "market", "side", "line", "price",
                                        "book", "status", "actual", "fair_close", "clv"]}]}
    lines = track.table(rep).splitlines()
    assert "stopped 5 Nov 2026, D-049" in lines[0] and "stopped" not in lines[1]
