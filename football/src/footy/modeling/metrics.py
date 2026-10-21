"""Scoring rules, accuracy, calibration and block-bootstrap intervals.

Primary metrics (fixed by the brief): ranked probability score for 1X2, binary log loss for
each totals line and for BTTS. Everything else is secondary. Per-fixture values are returned
so headline numbers can always be recomputed from a ledger.
"""

import numpy as np

EPS = 1e-15
OUTCOMES = ("H", "D", "A")


def outcome_index(home_goals, away_goals) -> np.ndarray:
    h, a = np.asarray(home_goals), np.asarray(away_goals)
    return np.where(h > a, 0, np.where(h == a, 1, 2))


def rps(p1x2: np.ndarray, outcome: np.ndarray) -> np.ndarray:
    """Ranked probability score for the ordered outcomes home, draw, away (lower is better)."""
    obs = np.eye(3)[outcome]
    cp, co = np.cumsum(p1x2, axis=1)[:, :2], np.cumsum(obs, axis=1)[:, :2]
    return ((cp - co) ** 2).sum(axis=1) / 2


def logloss_multi(p: np.ndarray, outcome: np.ndarray) -> np.ndarray:
    return -np.log(np.clip(p[np.arange(len(outcome)), outcome], EPS, 1))


def logloss_binary(p: np.ndarray, y: np.ndarray) -> np.ndarray:
    p = np.clip(np.asarray(p, dtype=float), EPS, 1 - EPS)
    y = np.asarray(y, dtype=float)
    return -(y * np.log(p) + (1 - y) * np.log(1 - p))


def brier_multi(p: np.ndarray, outcome: np.ndarray) -> np.ndarray:
    return ((p - np.eye(p.shape[1])[outcome]) ** 2).sum(axis=1)


def brier_binary(p: np.ndarray, y: np.ndarray) -> np.ndarray:
    return (np.asarray(p, dtype=float) - np.asarray(y, dtype=float)) ** 2


def pick_1x2(p1x2: np.ndarray) -> np.ndarray:
    """The ordinary pick: the highest probability, draws included. Ties go home, draw, away."""
    return np.argmax(p1x2, axis=1)


def calibration_table(p: np.ndarray, y: np.ndarray, bins: int = 10) -> list[dict]:
    """Observed frequency against mean predicted probability, in equal-width bins."""
    p, y = np.asarray(p, dtype=float), np.asarray(y, dtype=float)
    edges = np.linspace(0, 1, bins + 1)
    idx = np.clip(np.digitize(p, edges[1:-1]), 0, bins - 1)
    out = []
    for b in range(bins):
        m = idx == b
        if m.any():
            out.append(
                {
                    "bin_low": float(edges[b]),
                    "bin_high": float(edges[b + 1]),
                    "n": int(m.sum()),
                    "mean_predicted": float(p[m].mean()),
                    "observed": float(y[m].mean()),
                }
            )
    return out


def block_bootstrap_ci(
    values: np.ndarray,
    blocks: np.ndarray,
    n_boot: int = 2000,
    seed: int = 20261021,
    level: float = 0.95,
) -> tuple[float, float]:
    """Percentile interval for the mean of per-fixture `values`, resampling whole blocks
    (matchweek batches) with replacement so within-week correlation is respected."""
    values, blocks = np.asarray(values, dtype=float), np.asarray(blocks)
    uniq, inv = np.unique(blocks, return_inverse=True)
    sums = np.bincount(inv, weights=values, minlength=len(uniq))
    counts = np.bincount(inv, minlength=len(uniq))
    rng = np.random.default_rng(seed)
    draws = rng.integers(0, len(uniq), size=(n_boot, len(uniq)))
    means = sums[draws].sum(axis=1) / counts[draws].sum(axis=1)
    a = (1 - level) / 2
    return float(np.quantile(means, a)), float(np.quantile(means, 1 - a))
