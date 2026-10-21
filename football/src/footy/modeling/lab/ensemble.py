"""Ensembles of the lab's audited models (model-lab family `ensemble`).

`EnsembleModel` fits several registered models on the same history and cutoff, asks each for
its score grid for the same fixtures, and pools the grids into ONE grid per fixture. Every
market (1X2, O/U 0.5-4.5, BTTS) is then read off that grid with `markets_from_grid`, so the
ensemble is coherent in every market by construction. Three pooling rules:

- "linear" (the default): `mix_grids(grids, weights)`, a mixture of score distributions.
  Each market probability is the weighted mean of the members' probabilities.
- "log": a geometric (log-linear) pool, grid ∝ Π g_i^a_i, renormalised. The exponents are
  not forced to sum to 1, so the pool can sharpen (Σa > 1) or flatten (Σa < 1) the members'
  consensus; a linear pool can only flatten it.
- "split": the goal-difference distribution, which alone decides 1X2, comes from one linear
  pool (`diff_weights`), and the split of each goal-difference diagonal into scores (and so
  the totals and BTTS) from another (`weights`):
      grid(i, j) = P_A(i − j) · P_B(i, j) / P_B(i − j).
  It is still one joint distribution, so the markets cannot contradict each other.

**How the weights were obtained (none from 2023/24).** Every candidate member was run
walk-forward on 2022/23 (the runner's own `run_backtest`, five leagues, the same 1826
fixtures as `footy.modeling.runner.run_models([...], ["2223"])`, whose ledgers it
reproduces exactly), recording each fixture's grid. The weights minimise the D-025
composite (mean over 1X2 RPS and the log losses of O/U 1.5, 2.5, 3.5 and BTTS of model ÷
frequency baseline, five leagues pooled) of the pooled grids on those 2022/23 fixtures:
SLSQP over the simplex with an analytic gradient for "linear", Nelder-Mead on log weights
for "log" and "split". Members were chosen the same way, as the best pool of up to four.
2021/22 (also an inner season) was run the same way, only to cross-check the 2022/23
weights out of season and to fit `ensemble_linear_2s` on both inner seasons together.
2023/24 was used only to compare the registered candidates afterwards
(`footy lab-validate`); 2024/25 onwards was never run.

**Validation 2023/24 (1752 fixtures, after the weights were fixed).** Every ensemble beats
dixon_coles (0.9598) and three of its own members, scoring 0.9529-0.9535. None beats the
best single member that season: congestion_context_on_elo scores 0.9517 and elo_goals_gd
0.9528. ml_gbm_glm_boost, which carries 38% of ensemble_linear's weight from 2022/23, is
weak in 2023/24 (0.9581). Across seasons the ensemble is the steadier choice: it is near the
top in every season but the best in none, and the member rankings change from season to
season.

Members are looked up in the registry when the model is built (not at import, because the
registry imports this module), so a member renamed or retuned in its own family changes or
breaks the ensemble that names it. Each member keeps its own warm start across batches.
"""

from collections.abc import Callable, Sequence

import numpy as np
import pandas as pd

from footy.modeling.base import ALL_MARKETS, Model
from footy.modeling.grid import GOALS, MAX_GOALS, MarketProbs, markets_from_grid, mix_grids

POOLS = ("linear", "log", "split")
DIFF = GOALS[:, None] - GOALS[None, :]


def log_pool(grids: Sequence[np.ndarray], exponents) -> np.ndarray:
    """Normalised product of grids raised to `exponents` (a geometric pool)."""
    a = np.asarray(exponents, dtype=float)
    if (a < 0).any() or a.sum() <= 0:
        raise ValueError("log-pool exponents must be non-negative and not all zero")
    logs = np.stack([np.log(np.clip(g, 1e-300, None)) for g in grids])
    s = np.tensordot(a, logs, axes=1)
    s -= s.max(axis=(1, 2), keepdims=True)
    p = np.exp(s)
    return p / p.sum(axis=(1, 2), keepdims=True)


def split_pool(diff_grid: np.ndarray, shape_grid: np.ndarray) -> np.ndarray:
    """One grid whose goal-difference distribution is `diff_grid`'s and whose split of each
    goal-difference diagonal into scores is `shape_grid`'s. Both (n, k, k)."""
    out = np.zeros_like(shape_grid)
    for d in range(-MAX_GOALS, MAX_GOALS + 1):
        on = DIFF == d
        target = diff_grid[:, on].sum(axis=1)
        shape = shape_grid[:, on]
        have = shape.sum(axis=1, keepdims=True)
        # a diagonal the shape grid leaves empty is split evenly (cannot happen for
        # Poisson-type members, whose cells are all positive)
        share = np.where(have > 0, shape / np.where(have > 0, have, 1.0), 1.0 / on.sum())
        out[:, on] = share * target[:, None]
    return out / out.sum(axis=(1, 2), keepdims=True)


def grid_means(grid: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    """Expected home and away goals of each grid (the last row/column counts MAX_GOALS)."""
    return (grid.sum(axis=2) * GOALS).sum(axis=1), (grid.sum(axis=1) * GOALS).sum(axis=1)


class EnsembleModel:
    """Pools the score grids of registered models fitted on the same history."""

    markets = ALL_MARKETS

    def __init__(
        self,
        members: Sequence[str],
        weights: Sequence[float],
        name: str,
        pool: str = "linear",
        diff_weights: Sequence[float] | None = None,
        factories: dict[str, Callable[[], Model]] | None = None,
    ):
        if pool not in POOLS:
            raise ValueError(f"unknown pool {pool!r}; one of {POOLS}")
        if len(members) != len(weights) or len(set(members)) != len(members):
            raise ValueError("one weight per member, and no member twice")
        if pool == "split" and (diff_weights is None or len(diff_weights) != len(members)):
            raise ValueError("the split pool needs one diff weight per member")
        w = np.asarray(weights, dtype=float)
        if (w < 0).any() or w.sum() <= 0:
            raise ValueError("weights must be non-negative and not all zero")
        if factories is None:
            from footy.modeling.registry import candidates  # late: the registry imports us

            factories = candidates()
        self.name, self.pool = name, pool
        self.member_names = list(members)
        # linear and split weights are normalised; log-pool exponents are kept as given
        self.weights = w if pool == "log" else w / w.sum()
        self.diff_weights = (
            None if diff_weights is None else np.asarray(diff_weights, float) / np.sum(diff_weights)
        )
        self.models = [factories[m]() for m in self.member_names]

    def params(self) -> dict:
        out = {
            "pool": self.pool,
            "members": self.member_names,
            "weights": [round(float(x), 6) for x in self.weights],
            "weights_from": "D-025 composite on walk-forward 2022/23 (five leagues); not 2023/24",
            "member_params": {
                n: m.params() for n, m in zip(self.member_names, self.models, strict=True)
            },
        }
        if self.diff_weights is not None:
            out["diff_weights"] = [round(float(x), 6) for x in self.diff_weights]
        return out

    def fit(self, history: pd.DataFrame, cutoff) -> None:
        for m in self.models:
            m.fit(history, cutoff)

    def member_probs(self, fixtures: pd.DataFrame) -> list[MarketProbs]:
        mps = [m.predict(fixtures) for m in self.models]
        for n, mp in zip(self.member_names, mps, strict=True):
            if mp.grid is None:
                raise ValueError(f"ensemble member {n} has no score grid")
        return mps

    def predict(self, fixtures: pd.DataFrame) -> MarketProbs:
        mps = self.member_probs(fixtures)
        grids = [mp.grid for mp in mps]
        if self.pool == "linear":
            grid = mix_grids(grids, self.weights)
            if all(mp.lam_h is not None for mp in mps):
                # the mean of a mixture is the mixture of the members' means
                lh = sum(w * mp.lam_h for w, mp in zip(self.weights, mps, strict=True))
                la = sum(w * mp.lam_a for w, mp in zip(self.weights, mps, strict=True))
                return markets_from_grid(grid, lh, la)
        elif self.pool == "log":
            grid = log_pool(grids, self.weights)
        else:
            grid = split_pool(mix_grids(grids, self.diff_weights), mix_grids(grids, self.weights))
        return markets_from_grid(grid, *grid_means(grid))


# ---- registered candidates (weights fitted on 2022/23 only; see the module docstring) ----
#
# Search on 2022/23 (1826 fixtures, five leagues): 20 audited candidates and family
# components were run walk-forward; every subset of up to four was given its best
# linear-pool weights (6195 subsets). The best subset is also the sparse optimum of a
# simplex over all 20 (0.964878, against 0.966515 for the best single member
# ml_gbm_glm_boost_dc and 0.974428 for dixon_coles). dixon_coles, shots_xg, dc_tuning,
# goal_calibration and bivariate_dispersion members got zero weight in every top pool.
# Cross-season check on 2021/22 (also inner): the 2022/23 weights score 0.961699 there
# against 0.961626 for its best single member (dynamic_ratings_ekf), so the gain over the
# best member is small; per-league and early/late-season weights did worse out of season.

ELO, CTX = "elo_goals_wfe", "congestion_context_on_elo"
GLM, EKF = "ml_gbm_glm_boost", "dynamic_ratings_ekf"
CONFIGS: dict[str, dict] = {
    # the task's ensemble: linear pool, members and weights from 2022/23 (composite 0.964878)
    "ensemble_linear": {
        "members": (ELO, CTX, GLM, EKF),
        "weights": (0.0855, 0.3328, 0.3800, 0.2018),
    },
    # geometric pool, exponents fitted on 2022/23 (elo_goals_wfe's went to 0); they sum to
    # 1.05, a slight sharpening of the members' consensus (0.964571; on 2021/22 0.961671)
    "ensemble_log": {
        "members": (CTX, GLM, EKF),
        "weights": (0.4138, 0.3436, 0.2952),
        "pool": "log",
    },
    # 1X2 (goal difference) from one pool, the score split of each diagonal from another,
    # both fitted on 2022/23 (0.964323); out of season it overfits (2021/22 0.962149)
    "ensemble_split": {
        "members": (ELO, CTX, GLM, EKF),
        "diff_weights": (0.1986, 0.0, 0.4044, 0.3970),
        "weights": (0.0, 0.7303, 0.2686, 0.0011),
        "pool": "split",
    },
    # control with no fitted weights: equal thirds (2022/23 0.964922, 2021/22 0.961279)
    "ensemble_equal": {
        "members": (CTX, GLM, EKF),
        "weights": (1.0, 1.0, 1.0),
    },
    # linear pool with members and weights fitted on 2021/22 + 2022/23 pooled (3652
    # fixtures; 0.963071 against 0.964079 for the best single member, dynamic_ratings_ekf)
    "ensemble_linear_2s": {
        "members": (CTX, GLM, EKF),
        "weights": (0.3088, 0.2817, 0.4096),
    },
}


def _factory(name: str, cfg: dict):
    return lambda: EnsembleModel(name=name, **cfg)


CANDIDATES = {name: _factory(name, cfg) for name, cfg in CONFIGS.items()}
