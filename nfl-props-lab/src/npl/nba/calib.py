"""Calibrated and market-anchored probabilities for NBA props (n11, D-043).

N1's raw over probabilities lean to unders and are far too confident when they disagree with the
line (calibration slope 0.29 on real 2024-25 lines). `calibrated` applies a per-market logistic
recalibration fitted on 2024-25 (valid near bookmakers' lines, not for arbitrary lines).
`anchored` is the frozen market-anchored blend: mostly the market's margin-free price, nudged by
the model in rebounds, assists and threes. Neither beat the market out of sample; the anchored
rule is tracked on paper only. Coefficients are stored per N1 parameter version
(calibration.json "by_model") and must match the model that produced p_over.

The two-stat combos (pr, pa, ra) have no real-line calibration: we hold no past bookmaker lines
for them. `calibrated` and `anchored` refuse them (KeyError), so no other market's coefficients
(PRA's included) can ever be applied to one. `combo_calibrated` reads only an optional, separate
block, by_model[version]["calibration_combo_proxy"] = {"form", "coef": {market: {"a", "b"}},
"logit_clip", "fitted_on", "validation"}, fitted elsewhere on something other than real lines
(hence "proxy"); without it, or without that market in it, a combo gets None: no model view at
any line. There is no anchored chance for a combo.
"""

from __future__ import annotations

import json
from functools import lru_cache

import numpy as np

from ..config import REPORTS_DIR

PATH = REPORTS_DIR / "nba" / "model" / "calibration.json"
# a market whose V4 slope is below this has no model view at the bookmakers' line: the calibrated
# chance barely moves with the model's (points and PRA under nba-0.3), so a gap to the market there
# mostly shows the price, not the model (D-043, D-046)
FLAT_BELOW = 0.2


@lru_cache(maxsize=4)
def _coef(version: str | None = None) -> dict:
    """The coefficients fitted for this N1 parameter version (default: the live one). They are
    only valid for the model they were fitted on, so any other version is refused."""
    from .live import LIVE_PARAMS

    v = version or LIVE_PARAMS
    by = json.loads(PATH.read_text())["by_model"]
    if v not in by:
        raise KeyError(f"no calibration fitted for N1 {v}; refit calibration.json first")
    return by[v]


def v4_slopes(version: str | None = None) -> dict[str, float] | None:
    """The V4 recalibration slope b per market for an N1 parameter version (default: the live
    one), in the model's market order; None when no calibration was fitted for that version."""
    from .model import MARKETS

    try:
        coef = _coef(version)["calibration_v4"]["coef"]
    except KeyError:
        return None
    return {m: float(coef[m]["b"]) for m in MARKETS if m in coef}


def _logit(p: float, clip: float) -> float:
    p = min(max(float(p), clip), 1 - clip)  # the clip each layer was fitted with
    return float(np.log(p / (1 - p)))


def _sig(z: float) -> float:
    return float(1 / (1 + np.exp(-z)))


COMBOS = ("pr", "pa", "ra")  # npl.nba.model.COMBOS (not imported: calib loads without model)
PROXY = "calibration_combo_proxy"


def _refuse_combo(market: str) -> None:
    if market in COMBOS:
        raise KeyError(f"{market}: a combo market has no real-line calibration or anchored "
                       "coefficients (use combo_calibrated)")


def calibrated(market: str, p_over: float, version: str | None = None) -> float:
    _refuse_combo(market)
    k = _coef(version)["calibration_v4"]
    c = k["coef"][market]
    return _sig(c["a"] + c["b"] * _logit(p_over, k["logit_clip"]))


def anchored(market: str, fair_over: float, p_over: float, version: str | None = None) -> float:
    _refuse_combo(market)
    k = _coef(version)["anchored_stack"]
    c, e = k["coef"][market], k["logit_clip"]
    return _sig(c["a"] + c["c"] * _logit(fair_over, e) + c["w"] * _logit(p_over, e))


def combo_proxy(version: str | None = None) -> dict | None:
    """The calibration_combo_proxy block for an N1 parameter version (default: the live one), or
    None when calibration.json has no such block for it (or no entry for that version)."""
    try:
        k = _coef(version).get(PROXY)
    except KeyError:
        return None
    return k if isinstance(k, dict) and isinstance(k.get("coef"), dict) else None


def combo_calibrated(market: str, p_over: float, version: str | None = None) -> float | None:
    """A combo's calibrated over chance from its own proxy coefficients only; None when the
    block, or this market in it, is absent. Never falls back to another market's coefficients."""
    if market not in COMBOS:
        raise KeyError(f"{market} is not a combo market")
    k = combo_proxy(version)
    c = (k or {}).get("coef", {}).get(market)
    if not isinstance(c, dict) or "a" not in c or "b" not in c:
        return None
    return _sig(float(c["a"]) + float(c["b"]) * _logit(p_over, float(k.get("logit_clip", 1e-4))))


def combo_slopes(version: str | None = None) -> dict[str, float] | None:
    """The proxy slope b per combo market that has one; None without a proxy block."""
    k = combo_proxy(version)
    if k is None:
        return None
    return {m: float(k["coef"][m]["b"]) for m in COMBOS
            if isinstance(k["coef"].get(m), dict) and "b" in k["coef"][m]}
