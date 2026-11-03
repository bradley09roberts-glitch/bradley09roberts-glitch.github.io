"""The NBA props payload's `calibration` field: the V4 slopes of the edition's model version, read
through npl.nba.calib from reports/nba/model/calibration.json (never hard-coded), and the slope
below which a market has no model view at the bookmakers' line."""

import json

from npl.config import REPORTS_DIR
from npl.nba import appdata, calib


def _file_slopes(version: str) -> dict:
    by = json.loads((REPORTS_DIR / "nba" / "model" / "calibration.json").read_text())["by_model"]
    return {m: c["b"] for m, c in by[version]["calibration_v4"]["coef"].items()}


def test_v4_slopes_follow_the_file_for_each_fitted_version():
    for v in ("nba-0.1", "nba-0.3"):
        s = calib.v4_slopes(v)
        assert list(s) == ["pts", "reb", "ast", "fg3m", "pra"]  # the model's market order
        assert s == _file_slopes(v)
    assert calib.v4_slopes("nba-0.2") is None  # no calibration fitted for that version


def test_calibration_field_and_flat_markets():
    c = appdata.calibration("nba-0.3")
    assert c == {"version": "nba-0.3", "slope": _file_slopes("nba-0.3"), "flat_below": 0.2}
    # points and PRA: the calibrated chance barely moves with the model's, so no model view
    assert [m for m, b in c["slope"].items() if b < c["flat_below"]] == ["pts", "pra"]
    assert appdata.calibration("nba-0.2") is None and appdata.calibration(None) is None


def _edition(version: str) -> dict:
    f = {"game_id": "g1", "player_id": "p1", "name": "A Player", "team": "AAA", "opponent": "BBB",
         "position": "G", "market": "reb", "model": "N1", "p_play": 0.97,
         "minutes_expected": 30.0, "mean": 6.1, "median": 6, "q10": 3, "q90": 9,
         "dist": {"lo": 0, "pmf": [0.5, 0.5]}, "injury": None}
    return {"edition_id": "nba-e1", "date_et": "2026-11-02", "headline_model": "N1",
            "published_at": "2026-11-02T19:00:00Z", "forecasts": [f],
            "games": [{"game_id": "g1", "home": "AAA", "away": "BBB",
                       "tip_utc": "2026-11-03T00:00:00Z"}],
            "code": {"model_version": version}}


def test_props_payload_carries_the_editions_calibration(monkeypatch):
    for name, value in (("game_logs", {}), ("opponent_factors", {}), ("market_lines", {}),
                        ("_simple", {})):
        monkeypatch.setattr(appdata, name, lambda *a, _v=value, **k: _v)
    monkeypatch.setattr(appdata, "_extras", lambda e, mkt: ({}, {}))
    for version, expect in (("nba-0.3", appdata.calibration("nba-0.3")), ("nba-9.9", None)):
        monkeypatch.setattr(appdata, "latest", lambda v=version: _edition(v))
        p = appdata._props()
        assert p["model_version"] == version and p["calibration"] == expect
        assert len(p["cards"]) == 1
