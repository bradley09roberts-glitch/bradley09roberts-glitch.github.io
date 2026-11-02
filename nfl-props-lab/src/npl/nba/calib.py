"""Calibrated and market-anchored probabilities for NBA props (n11, D-043).

N1's raw over probabilities lean to unders and are far too confident when they disagree with the
line (calibration slope 0.29 on real 2024-25 lines). `calibrated` applies a per-market logistic
recalibration fitted on 2024-25 (valid near bookmakers' lines, not for arbitrary lines).
`anchored` is the frozen market-anchored blend: mostly the market's margin-free price, nudged by
the model in rebounds, assists and threes. Neither beat the market out of sample; the anchored
rule is tracked on paper only.
"""

from __future__ import annotations

import json
from functools import lru_cache

import numpy as np

from ..config import REPORTS_DIR

PATH = REPORTS_DIR / "nba" / "model" / "calibration.json"


@lru_cache(maxsize=1)
def _coef() -> dict:
    return json.loads(PATH.read_text())


def _logit(p: float) -> float:
    p = min(max(float(p), 1e-6), 1 - 1e-6)  # the clip the coefficients were fitted with
    return float(np.log(p / (1 - p)))


def _sig(z: float) -> float:
    return float(1 / (1 + np.exp(-z)))


def calibrated(market: str, p_over: float) -> float:
    c = _coef()["calibration_v4"]["coef"][market]
    return _sig(c["a"] + c["b"] * _logit(p_over))


def anchored(market: str, fair_over: float, p_over: float) -> float:
    c = _coef()["anchored_stack"]["coef"][market]
    return _sig(c["a"] + c["c"] * _logit(fair_over) + c["w"] * _logit(p_over))
