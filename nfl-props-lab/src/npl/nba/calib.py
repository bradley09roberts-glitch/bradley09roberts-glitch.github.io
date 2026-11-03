"""Calibrated and market-anchored probabilities for NBA props (n11, D-043).

N1's raw over probabilities lean to unders and are far too confident when they disagree with the
line (calibration slope 0.29 on real 2024-25 lines). `calibrated` applies a per-market logistic
recalibration fitted on 2024-25 (valid near bookmakers' lines, not for arbitrary lines).
`anchored` is the frozen market-anchored blend: mostly the market's margin-free price, nudged by
the model in rebounds, assists and threes. Neither beat the market out of sample; the anchored
rule is tracked on paper only. Coefficients are stored per N1 parameter version
(calibration.json "by_model") and must match the model that produced p_over.
"""

from __future__ import annotations

import json
from functools import lru_cache

import numpy as np

from ..config import REPORTS_DIR

PATH = REPORTS_DIR / "nba" / "model" / "calibration.json"


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


def _logit(p: float, clip: float) -> float:
    p = min(max(float(p), clip), 1 - clip)  # the clip each layer was fitted with
    return float(np.log(p / (1 - p)))


def _sig(z: float) -> float:
    return float(1 / (1 + np.exp(-z)))


def calibrated(market: str, p_over: float, version: str | None = None) -> float:
    k = _coef(version)["calibration_v4"]
    c = k["coef"][market]
    return _sig(c["a"] + c["b"] * _logit(p_over, k["logit_clip"]))


def anchored(market: str, fair_over: float, p_over: float, version: str | None = None) -> float:
    k = _coef(version)["anchored_stack"]
    c, e = k["coef"][market], k["logit_clip"]
    return _sig(c["a"] + c["c"] * _logit(fair_over, e) + c["w"] * _logit(p_over, e))
