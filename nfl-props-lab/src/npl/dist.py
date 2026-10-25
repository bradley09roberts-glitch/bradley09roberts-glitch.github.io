"""Discrete predictive distributions and line probabilities.

All probabilities for a market come from ONE saved integer distribution, so:
  * alternate lines are monotone (a higher threshold never has a higher Over probability)
  * a half-point line has P(Over) + P(Under) = 1
  * an integer line has P(Over) + P(Push) + P(Under) = 1
  * a milestone "50+" is P(X >= 50), never P(X > 50)
"""

from __future__ import annotations

import math
from dataclasses import dataclass

import numpy as np


@dataclass(frozen=True)
class Dist:
    lo: int  # value of counts[0]
    counts: tuple[int, ...]  # integer support lo .. lo+len-1

    @classmethod
    def from_samples(cls, x: np.ndarray) -> Dist:
        x = np.asarray(x, dtype=np.int64)
        if x.size == 0:
            raise ValueError("no draws")
        lo = int(x.min())
        c = np.bincount(x - lo)
        return cls(lo, tuple(int(v) for v in c))

    @classmethod
    def from_pmf(cls, lo: int, pmf: np.ndarray, scale: int = 1_000_000) -> Dist:
        c = np.rint(np.asarray(pmf) / np.sum(pmf) * scale).astype(np.int64)
        return cls(lo, tuple(int(v) for v in c))

    @property
    def n(self) -> int:
        return int(sum(self.counts))

    @property
    def support(self) -> np.ndarray:
        return np.arange(self.lo, self.lo + len(self.counts))

    @property
    def pmf(self) -> np.ndarray:
        c = np.asarray(self.counts, dtype=float)
        return c / c.sum()

    def p_ge(self, k: float) -> float:
        """P(X >= k) for real k (X integer)."""
        kk = math.ceil(k)
        i = kk - self.lo
        if i <= 0:
            return 1.0
        if i >= len(self.counts):
            return 0.0
        return float(np.asarray(self.counts[i:], dtype=float).sum() / self.n)

    def p_eq(self, k: int) -> float:
        i = int(k) - self.lo
        if i < 0 or i >= len(self.counts):
            return 0.0
        return self.counts[i] / self.n

    def line(self, line: float) -> dict:
        """Over/Under (and Push for integer lines) for a total line."""
        if float(line).is_integer():
            push = self.p_eq(int(line))
            over = self.p_ge(line + 1)
            under = 1.0 - over - push
            return {"over": over, "under": max(under, 0.0), "push": push}
        over = self.p_ge(line)
        return {"over": over, "under": 1.0 - over, "push": 0.0}

    def milestone(self, k: int) -> float:
        """'k+' milestone: P(X >= k)."""
        return self.p_ge(k)

    def quantile(self, q: float) -> int:
        cdf = np.cumsum(self.pmf)
        return int(self.lo + np.searchsorted(cdf, q - 1e-12))

    def mean(self) -> float:
        return float((self.support * self.pmf).sum())

    def sd(self) -> float:
        m = self.mean()
        return float(np.sqrt(((self.support - m) ** 2 * self.pmf).sum()))

    def median(self) -> int:
        return self.quantile(0.5)

    def mc_se(self, p: float) -> float:
        """Monte Carlo standard error of a probability estimated from these draws."""
        return math.sqrt(max(p * (1 - p), 0.0) / max(self.n, 1))

    def crps(self, y: float) -> float:
        """Continuous ranked probability score for an integer outcome (lower is better)."""
        # Discrete form: sum over integers k of (P(X <= k) - 1{y <= k})^2.
        y = int(round(y))
        cdf = np.cumsum(self.pmf)
        hi_s = self.lo + len(self.counts) - 1
        lo, hi = min(self.lo, y) - 1, max(hi_s, y)
        grid = np.arange(lo, hi + 1)
        idx = np.clip(grid - self.lo, -1, len(cdf) - 1)
        F = np.where(idx < 0, 0.0, cdf[np.clip(idx, 0, None)])
        H = (grid >= y).astype(float)
        return float(((F - H) ** 2).sum())

    def log_score(self, y: int, floor: float = 1e-4) -> float:
        return -math.log(max(self.p_eq(int(y)), floor))

    def to_json(self) -> dict:
        return {"lo": self.lo, "counts": list(self.counts)}

    @classmethod
    def from_json(cls, d: dict) -> Dist:
        return cls(int(d["lo"]), tuple(int(v) for v in d["counts"]))


def fair_decimal(p_win: float, p_push: float = 0.0) -> float | None:
    """Break-even decimal price: p_win*(d-1) = p_loss  =>  d = (p_win + p_loss)/p_win."""
    p_loss = 1.0 - p_win - p_push
    if p_win <= 0:
        return None
    return (p_win + p_loss) / p_win


def expected_return(p_win: float, decimal_odds: float, p_push: float = 0.0) -> float:
    """Expected net return per unit staked (push returns the stake)."""
    p_loss = 1.0 - p_win - p_push
    return p_win * (decimal_odds - 1.0) - p_loss
