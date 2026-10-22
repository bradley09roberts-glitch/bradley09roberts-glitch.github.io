"""Online learning: mixture weights over member score grids, updated after every settled match
(model-lab family `ml_online`).

The forecast is one mixture of the members' score grids, sum_k w_k grid_k, so every market is
read off one coherent distribution (a mixture of score distributions is a score distribution).
The weights keep learning: after every match that becomes final they take one exponentiated-
gradient step (EG, the multiplicative-weights / Hedge update for a convex loss) on that match's
loss,

    w_k <- w_k exp(-eta * dL/dw_k) / normaliser,   then   w <- (1 - share) w + share / K,

where L is the D-025 composite for one match: 1X2 RPS and the log losses of O/U 1.5, 2.5, 3.5
and BTTS, each divided by the frequency baseline's mean (`SCALE`). L is convex in w, so EG
tracks the best fixed mixture in hindsight (regret O(sqrt(T)) for a suitable eta), and the
fixed share (`share`) lets the weights move again when the members' relative form changes.
Because every market of a mixture is linear in w, the gradient needs only each member's market
probabilities for the match, which `MemberStream` records out of sample.

- **What it learns from.** `MemberStream` (footy.modeling.lab.ml_stream): each member's
  forecast for every match of the history (the league and its second tier), made at the
  match's own decision time; on the first fit, a warm-up walk-forward over the last
  `warmup_days`. Matches are taken in date order; a match updates the weights only once it is
  in the history passed to fit (final before the cutoff) and both teams have three matches of
  history, as in the runner.
- **Pooled or per division.** One weight vector for every division the run loads, or one per
  division (`per_division`), each updated on its own division's matches only.
- **With prices** (`market=True`): the members' grids are first tilted onto the pre-closing
  consensus as `MarketTilt` does (weights 1 / 1, same availability rules), so the mixture's
  1X2 and O/U 2.5 are the market's and the weights choose the grid's shape (O/U 1.5 and 3.5,
  BTTS, the scores). A fixture without usable prices mixes the members' own grids with the
  same weights.

Hyper-parameters (eta, share, pooled or per division, members) were chosen on the training
seasons 2021/22 and 2022/23 only; see docs/research/machine-learning.md.

**Validation 2023/24 (after the settings were fixed; D-028).** `ml_online_market` 0.94455
against 0.94488 for `market_tilt_shots`, so it had the one test run: 0.95574 against 0.95546
on 2024/25 + 2025/26, worse in O/U 1.5, 3.5 and BTTS, so it is not adopted. `ml_online_pure`
0.95421 against 0.95174 for `congestion_context_on_elo` (not tested).

Uses only the DataFrames passed to fit/predict and no randomness.
"""

from collections.abc import Callable, Sequence

import numpy as np
import pandas as pd

from footy.modeling.base import ALL_MARKETS
from footy.modeling.grid import MarketProbs, markets_from_grid
from footy.modeling.lab.market_blend import BOOKS, default_prices
from footy.modeling.lab.ml_stream import MarketFacing, MemberStream, outcomes

# Frequency-baseline means of 1X2 RPS and the log losses of O/U 1.5, 2.5, 3.5 and BTTS on the
# training seasons 2021/22 + 2022/23 (five leagues, data/backtests/
# baseline_frequency__custom__2122-2223): the D-025 composite's denominators.
SCALE = np.array([0.2301, 0.5409, 0.6896, 0.6122, 0.6888])
P_MIN = 1e-6


def match_probs(rows: np.ndarray) -> np.ndarray:
    """(n, K, 9) member market rows -> (n, K, 7): H, D, A, over 1.5 / 2.5 / 3.5, BTTS."""
    return rows[..., :7]


def loss_and_grad(P: np.ndarray, w: np.ndarray, y: dict) -> tuple[float, np.ndarray]:
    """The scaled composite loss of the mixture sum_k w_k P_k for one match, and its gradient
    in w. `P` (K, 7) member probabilities, `y` the match's labels (`outcomes`)."""
    p = w @ P  # (7,)
    cum_m = np.cumsum(p[:3])[:2]
    cum_k = np.cumsum(P[:, :3], axis=1)[:, :2]  # (K, 2)
    obs = np.cumsum(np.eye(3)[y["1x2"]])[:2]
    diff = cum_m - obs
    loss = 0.5 * float(diff @ diff) / SCALE[0]
    grad = (cum_k @ diff) / SCALE[0]
    for j, key in enumerate(("o15", "o25", "o35", "btts")):
        yy = y[key]
        q_k = P[:, 3 + j] if yy else 1.0 - P[:, 3 + j]
        q = max(float(w @ q_k), P_MIN)
        loss += -np.log(q) / SCALE[1 + j]
        grad -= q_k / q / SCALE[1 + j]
    return loss, grad


def eg_step(w: np.ndarray, grad: np.ndarray, eta: float, share: float) -> np.ndarray:
    """One exponentiated-gradient step, then the fixed share towards uniform."""
    g = -eta * (grad - grad.min())  # shifting the gradient leaves the update unchanged
    v = w * np.exp(g)
    s = v.sum()
    if not (s > 0 and np.isfinite(s)):  # every weight underflowed: the same step in logs
        lv = np.log(np.maximum(w, 1e-300)) + g
        v = np.exp(lv - lv.max())
        s = v.sum()
    v /= s
    return (1.0 - share) * v + share / len(v)


class OnlineMixture:
    """A mixture of member score grids whose weights learn online (module docstring)."""

    markets = ALL_MARKETS

    def __init__(
        self,
        name: str,
        members: Sequence[str],
        eta: float,
        share: float = 0.0,
        per_division: bool = False,
        market: bool = False,
        warmup_days: int = 365,
        prices: Callable[[], pd.DataFrame] = default_prices,
        factories: dict | None = None,
    ):
        self.name = name
        self.eta, self.share, self.per_division = float(eta), float(share), per_division
        self.market = market
        self.stream = MemberStream(
            members, market=market, warmup_days=warmup_days, prices=prices, factories=factories
        )
        K = len(self.stream.member_names)
        self.w: dict[str, np.ndarray] = {}
        self.uniform = np.full(K, 1.0 / K)
        self.done = 0  # records already learned from
        self.updates = 0

    def params(self) -> dict:
        return {
            "eta": self.eta,
            "share": self.share,
            "per_division": self.per_division,
            "loss": "D-025 composite per match (RPS, LL O/U 1.5 2.5 3.5, LL BTTS / SCALE)",
            "scale": SCALE.tolist(),
            "stream": self.stream.params(),
            **(
                {"books": list(BOOKS), "phase": "pre_closing", "devig": "shin"}
                if self.market
                else {}
            ),
        }

    def weights(self, division: str) -> np.ndarray:
        key = division if self.per_division else "all"
        return self.w.get(key, self.uniform)

    def fit(self, history: pd.DataFrame, cutoff) -> None:
        self.stream.update(history, cutoff)
        rec = self.stream.records()
        if rec is None or self.done >= len(rec):
            return
        sl = slice(self.done, len(rec))
        rows = rec.tilted if self.market else rec.raw
        P = match_probs(rows[sl])
        ys = outcomes(rec.goals[sl])
        ok = rec.eligible[sl]
        for i in np.flatnonzero(ok):
            key = rec.division[sl][i] if self.per_division else "all"
            w = self.w.get(key, self.uniform)
            y = {k: v[i] for k, v in ys.items()}
            _, g = loss_and_grad(P[i], w, y)
            self.w[key] = eg_step(w, g, self.eta, self.share)
            self.updates += 1
        self.done = len(rec)

    def predict(self, fixtures: pd.DataFrame) -> MarketProbs:
        b = self.stream.predict(fixtures)
        grids = b.tilted_grids if self.market else b.grids  # (K, n, 11, 11)
        rows = b.tilted if self.market else b.raw
        W = np.stack([self.weights(d) for d in fixtures.division])  # (n, K)
        grid = np.einsum("nk,knij->nij", W, grids)
        lam_h = (W * rows[:, :, 7]).sum(axis=1)
        lam_a = (W * rows[:, :, 8]).sum(axis=1)
        if self.market:
            self.last_used = b.last_used
        return markets_from_grid(grid, lam_h, lam_a)


class MarketOnlineMixture(MarketFacing, OnlineMixture):
    """OnlineMixture over market-tilted grids, with what footy.live needs of a model + market
    candidate (`decision_time`, `last_used`, `provenance`)."""

    def __init__(self, name: str, members: Sequence[str], eta: float, **kw):
        super().__init__(name, members, eta, market=True, **kw)


# ---- registered candidates (hyper-parameters chosen on 2021/22 and 2022/23 only) ---------
#
# Search on the training seasons (docs/research/machine-learning.md), replayed walk-forward
# from recorded out-of-sample member forecasts, five leagues pooled: member sets of the six
# recorded members (shots, ctx, Dixon-Coles, GLM booster, bivariate shots, Elo), eta 0.003 to
# 100, share 0 / 0.002 / 0.01, pooled or per division, warm-up 365 / 730 days; 600 settings
# (896 replays) on 2022/23, the promising ones also on 2021/22, ranked by the mean of the two
# seasons' D-025 composites. The curves are flat around the chosen eta (with prices 10-100,
# without 0.2-0.5); per-division weights and the fixed share never helped.
#   with prices:    0.95506 / 0.96014 against the shots model + market 0.95568 / 0.96077
#   without prices: 0.96067 / 0.96532 against congestion_context_on_elo 0.96349 / 0.96677
SHOTS, CTX, DC, GLM = (
    "shots_xg_sot_joint_rw",
    "congestion_context_on_elo",
    "dixon_coles",
    "ml_gbm_glm_boost",
)
CONFIGS: dict[str, dict] = {
    # the shots model, ctx and the GLM booster, each tilted onto the pre-closing prices
    "ml_online_market": {"members": (SHOTS, CTX, GLM), "eta": 20.0, "market": True},
    # the shots model, ctx, Dixon-Coles and the GLM booster, without prices
    "ml_online_pure": {"members": (SHOTS, CTX, DC, GLM), "eta": 0.3},
}


def _factory(name: str, cfg: dict):
    cls = MarketOnlineMixture if cfg.get("market") else OnlineMixture
    cfg = {k: v for k, v in cfg.items() if not (k == "market" and cls is MarketOnlineMixture)}
    return lambda: cls(name=name, **cfg)


CANDIDATES = {name: _factory(name, cfg) for name, cfg in CONFIGS.items()}
