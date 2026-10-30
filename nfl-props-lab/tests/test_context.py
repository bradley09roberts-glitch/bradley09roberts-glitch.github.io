import pandas as pd
import pytest

from npl import features as F


def _case():
    # GB: starter S played games 1-2 only; backup B played all four, with a bigger share while S was out
    rows = [("S", g, 0.6) for g in ("g1", "g2")] + [("B", "g1", 0.1), ("B", "g2", 0.1), ("B", "g3", 0.6), ("B", "g4", 0.6)]
    played = pd.DataFrame(rows, columns=["player_id", "game_id", "dshare"]).assign(team="GB", tshare=0.05, _w=1.0)
    ph = pd.DataFrame({"dshare": [0.6, 0.35], "tshare": [0.05, 0.05], "played": [0.9, 1.0], "_wsum": [2.0, 4.0]},
                      index=["S", "B"])
    roster = pd.DataFrame({"player_id": ["S", "B"], "team": "GB", "position": "RB"})
    return played, ph, roster


def test_trim_keeps_weight_and_scales_share():
    played, ph, roster = _case()
    out = F.context_shares(played, ph, roster, 1.0, trim_d=0.5)
    assert out.at["B", "dshare"] == pytest.approx((0.1 + 0.1 + 0.3 + 0.3) / 4)
    assert out.at["B", "_wsum_d"] == pytest.approx(4.0)
    assert out.at["S", "dshare"] == pytest.approx(0.6)          # the starter is not touched
    assert out.at["B", "tshare"] == pytest.approx(0.05)         # no key receiver: targets unchanged


def test_downweight_thins_history():
    played, ph, roster = _case()
    out = F.context_shares(played, ph, roster, 0.5)
    assert out.at["B", "dshare"] == pytest.approx((0.1 + 0.1 + 0.3 + 0.3) / 3)
    assert out.at["B", "_wsum_d"] == pytest.approx(3.0)


def test_starter_listed_out_is_not_coming_back():
    played, ph, roster = _case()
    out = F.context_shares(played, ph, roster, 1.0, {"S"}, trim_d=0.5)
    assert out.at["B", "dshare"] == pytest.approx(0.35)


def test_live_settings_trim_runs_only():
    # v0.4.2 (D-031): no down-weighting, designed runs trimmed to 70%, targets untouched
    assert F.HP["ctx_absent_weight"] == 1.0 and F.HP["ctx_backup_trim_d"] == 0.7 and F.HP["ctx_backup_trim_t"] == 1.0
    played, ph, roster = _case()
    out = F.context_shares(played, ph, roster, F.HP["ctx_absent_weight"], trim_d=F.HP["ctx_backup_trim_d"],
                           trim_t=F.HP["ctx_backup_trim_t"])
    assert out.at["B", "dshare"] == pytest.approx((0.1 + 0.1 + 0.42 + 0.42) / 4)
    assert out.at["B", "_wsum_d"] == pytest.approx(4.0)
