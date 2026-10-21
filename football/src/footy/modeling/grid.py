"""Joint score grids and the coherent market probabilities derived from them.

Every market comes from one grid per fixture: rows are home goals 0..MAX_GOALS, columns away
goals 0..MAX_GOALS. The last row and column hold "MAX_GOALS or more", so no probability mass
is dropped; `tail_mass` reports how much landed there. Because 1X2, totals and BTTS are all
sums over the same grid, they cannot contradict each other.
"""

from dataclasses import dataclass, field

import numpy as np
from scipy.stats import poisson

MAX_GOALS = 10
GOALS = np.arange(MAX_GOALS + 1)
TOTAL_LINES = (0.5, 1.5, 2.5, 3.5, 4.5)
GRID_TOL = 1e-9


def capped_pmf(lam: np.ndarray, max_goals: int = MAX_GOALS) -> np.ndarray:
    """Poisson pmf over 0..max_goals with P(X >= max_goals) in the last bin. Shape (n, k)."""
    lam = np.asarray(lam, dtype=float)[:, None]
    k = np.arange(max_goals + 1)[None, :]
    pmf = poisson.pmf(k, lam)
    pmf[:, -1] = poisson.sf(max_goals - 1, lam[:, 0])
    return pmf


def rho_bounds(lam_h: np.ndarray, lam_a: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    """Range of the Dixon-Coles rho that keeps every adjusted cell non-negative."""
    lo = np.maximum(-1 / np.maximum(lam_h, 1e-9), -1 / np.maximum(lam_a, 1e-9))
    hi = np.minimum(1 / np.maximum(lam_h * lam_a, 1e-9), 1.0)
    return lo, hi


def poisson_grid(lam_h, lam_a, rho=0.0) -> np.ndarray:
    """Independent Poisson margins with the Dixon-Coles low-score adjustment (rho = 0 gives
    plain independence). The adjustment moves mass among 0-0, 1-0, 0-1 and 1-1 without
    changing the total."""
    lam_h = np.atleast_1d(np.asarray(lam_h, dtype=float))
    lam_a = np.atleast_1d(np.asarray(lam_a, dtype=float))
    rho = np.broadcast_to(np.asarray(rho, dtype=float), lam_h.shape).copy()
    lo, hi = rho_bounds(lam_h, lam_a)
    rho = np.clip(rho, lo, hi)
    g = capped_pmf(lam_h)[:, :, None] * capped_pmf(lam_a)[:, None, :]
    g[:, 0, 0] *= 1 - lam_h * lam_a * rho
    g[:, 0, 1] *= 1 + lam_h * rho
    g[:, 1, 0] *= 1 + lam_a * rho
    g[:, 1, 1] *= 1 - rho
    np.clip(g, 0.0, None, out=g)
    g /= g.sum(axis=(1, 2), keepdims=True)
    return g


def tail_mass(grid: np.ndarray) -> np.ndarray:
    """Probability held in the "MAX_GOALS or more" row or column, per fixture."""
    return grid[:, -1, :].sum(axis=1) + grid[:, :, -1].sum(axis=1) - grid[:, -1, -1]


@dataclass
class MarketProbs:
    """Probabilities for every market, one row per fixture. `grid` is set for score models;
    market-only baselines leave it None and may leave some markets as None."""

    p1x2: np.ndarray | None  # (n, 3): home, draw, away
    p_over: dict[float, np.ndarray] = field(default_factory=dict)  # line -> (n,)
    p_btts: np.ndarray | None = None  # (n,) probability both teams score
    grid: np.ndarray | None = None
    lam_h: np.ndarray | None = None
    lam_a: np.ndarray | None = None

    @property
    def n(self) -> int:
        for v in (self.p1x2, self.p_btts, *self.p_over.values()):
            if v is not None:
                return len(v)
        return 0


def markets_from_grid(grid: np.ndarray, lam_h=None, lam_a=None) -> MarketProbs:
    total = GOALS[:, None] + GOALS[None, :]
    home = np.tril(np.ones((MAX_GOALS + 1, MAX_GOALS + 1)), -1)  # i > j
    p_home = (grid * home).sum(axis=(1, 2))
    p_draw = np.trace(grid, axis1=1, axis2=2)
    p_away = (grid * home.T).sum(axis=(1, 2))
    p_over = {line: (grid * (total > line)).sum(axis=(1, 2)) for line in TOTAL_LINES}
    p_btts = grid[:, 1:, 1:].sum(axis=(1, 2))
    return MarketProbs(
        p1x2=np.stack([p_home, p_draw, p_away], axis=1),
        p_over=p_over,
        p_btts=p_btts,
        grid=grid,
        lam_h=None if lam_h is None else np.asarray(lam_h, dtype=float),
        lam_a=None if lam_a is None else np.asarray(lam_a, dtype=float),
    )


def check_coherent(mp: MarketProbs, tol: float = 1e-6) -> list[str]:
    """Coherence checks from the brief. Returns a list of violations (empty when fine)."""
    problems = []
    if mp.grid is not None:
        g = mp.grid
        if (g < -GRID_TOL).any():
            problems.append("negative grid cell")
        if not np.allclose(g.sum(axis=(1, 2)), 1.0, atol=tol):
            problems.append("grid does not sum to 1")
    if mp.p1x2 is not None:
        if not np.allclose(mp.p1x2.sum(axis=1), 1.0, atol=tol):
            problems.append("home + draw + away != 1")
        if (mp.p1x2 < -tol).any() or (mp.p1x2 > 1 + tol).any():
            problems.append("1X2 probability outside [0, 1]")
    lines = sorted(mp.p_over)
    for a, b in zip(lines, lines[1:], strict=False):
        if (mp.p_over[b] > mp.p_over[a] + tol).any():
            problems.append(f"P(over {b}) > P(over {a})")
    for line, p in mp.p_over.items():
        if (p < -tol).any() or (p > 1 + tol).any():
            problems.append(f"P(over {line}) outside [0, 1]")
    if mp.p_btts is not None and ((mp.p_btts < -tol).any() or (mp.p_btts > 1 + tol).any()):
        problems.append("BTTS outside [0, 1]")
    return problems


def mix_grids(grids: list[np.ndarray], weights) -> np.ndarray:
    """A mixture of score distributions is itself a score distribution, so ensembles built
    this way stay coherent across every market."""
    w = np.asarray(weights, dtype=float)
    w = w / w.sum()
    return np.tensordot(w, np.stack(grids), axes=1)
