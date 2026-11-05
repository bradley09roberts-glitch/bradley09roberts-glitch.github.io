"""n13 (reports/nba/experiments/n13_combos): the proxy-line check failed, so the committed
calibration.json must carry no combo proxy block, and the committed summary holds aggregates only."""
import json

from npl.config import PROJECT_ROOT
from npl.nba import calib

SUMMARY = PROJECT_ROOT / "reports" / "nba" / "experiments" / "n13_combos" / "n13_summary.json"


def test_no_combo_proxy_calibration_was_written():
    d = json.loads(calib.PATH.read_text())
    for version, block in d["by_model"].items():
        assert calib.PROXY not in block, version
    calib._coef.cache_clear()
    for m in calib.COMBOS:
        assert calib.combo_calibrated(m, 0.6, "nba-0.3") is None
    assert calib.combo_slopes("nba-0.3") is None


def _walk(x, path=""):
    if isinstance(x, dict):
        for k, v in x.items():
            yield path + "/" + str(k), k, v
            yield from _walk(v, path + "/" + str(k))
    elif isinstance(x, list):
        for i, v in enumerate(x):
            yield from _walk(v, f"{path}[{i}]")


def test_n13_summary_is_aggregates_only_and_matches_its_decision():
    s = json.loads(SUMMARY.read_text())
    crit = s["B_pra_proxy_validation"]["criterion"]
    assert crit["proxy_validates"] == (crit["b_close"] and crit["ll_close"] and crit["ll_ci_upper_ok"])
    assert s["decision"]["write_calibration_combo_proxy"] is crit["proxy_validates"] is False
    banned = {"game_id", "player_id", "bookmaker", "decimal_price", "over", "under", "best_over",
              "best_under", "mgm_over", "mgm_under", "name"}
    for path, k, v in _walk(s):
        assert k not in banned, path
        if isinstance(v, list):
            assert len(v) <= 10, path  # intervals, quantiles and PIT deciles only
