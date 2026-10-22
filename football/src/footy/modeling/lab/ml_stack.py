"""A learned meta-model on top of the models and the market (model-lab family `ml_stack`).

For each market of the D-025 composite a ridge-regularised logistic model learns how to
correct a reference forecast from what the other forecasts and the engineered features say:

- **1X2**: multinomial logistic, home and draw against away, with the reference's log
  probabilities as offset: log p_c = log r_c + a_c + z . b_c (+ a normaliser).
- **O/U 1.5, 2.5, 3.5 and BTTS**: binary logistic with the reference's log odds as offset:
  logit p = logit r + a + z . b.

With every coefficient at zero the forecast is the reference, and the ridge pulls towards it,
so the learner only moves a forecast where the training matches support it. The inputs z are
standardised on the training rows:

- the reference's own log odds (so it can sharpen or flatten them);
- each other member's log odds minus the reference's (where they disagree, and how much);
- with prices, how far the reference model's own grid was from the market before the tilt
  (1X2 log odds of home against away, O/U 2.5 log odds);
- ml_gbm's engineered team features (`feats`): home minus away for 1X2, home plus away for the
  totals and BTTS (rolling shots on target, shots, goals and corners for and against, Elo,
  rest days, congestion, matches this season).

The six learned probabilities (home, draw, over 1.5 / 2.5 / 3.5, BTTS) are then made one
coherent score grid by tilting the reference grid onto them (`ml_stream.tilt_markets`, the
minimum-information change, as MarketTilt does for three of them). Where they can't all be
reached the reference grid is kept (`fallbacks` counts these).

- **Reference.** With prices (`market=True`): the reference member's grid tilted onto the
  pre-closing consensus (for the shots model this is exactly `market_tilt_shots`), so the
  learner starts from the model of record. A fixture without usable prices keeps that member's
  own grid: the learner is trained on priced matches only. Without prices: a member's own grid
  (`congestion_context_on_elo` for the pure candidate).
- **Training rows.** Every match `MemberStream` has recorded out of sample (the league and its
  second tier, warm-up included) that is final before the cutoff and whose teams have three
  matches of history, weighted by exp(-xi x age in days). The learner is refitted at every
  cutoff, so it keeps learning as results arrive.

Hyper-parameters (ridge strengths, xi, members, features) were chosen on the training seasons
2021/22 and 2022/23 only; see docs/research/machine-learning.md.

**Validation 2023/24 (after the settings were fixed; D-028).** `ml_stack_market` 0.94484
against 0.94488 for `market_tilt_shots`; `ml_stack_pure` 0.95386 against 0.95174 for
`congestion_context_on_elo`. Neither was tested: the online mixture had the lower validation
composite with prices, and the pure learner lost to its target.

Uses only the DataFrames passed to fit/predict and no randomness.
"""

from collections.abc import Callable, Sequence
from dataclasses import dataclass, field

import numpy as np
import pandas as pd
from scipy.special import expit, log_softmax

from footy.modeling.base import ALL_MARKETS
from footy.modeling.grid import GOALS, MarketProbs, markets_from_grid
from footy.modeling.lab.market_blend import BOOKS, default_prices
from footy.modeling.lab.ml_stream import (
    Block,
    MarketFacing,
    MemberStream,
    feature_index,
    logit,
    outcomes,
    tilt_markets,
)

BINARY = ("o15", "o25", "o35", "btts")
COL = {"o15": 3, "o25": 4, "o35": 5, "btts": 6}  # MARKET_COLS positions
LOGP_MIN = np.log(1e-6)

# ---- design ------------------------------------------------------------------------------


@dataclass(frozen=True)
class StackSpec:
    """Which forecasts and features feed the learner. Indices are positions in the
    stream's member list."""

    ref: int
    others: tuple[int, ...]
    market: bool
    feats: tuple[str, ...] = ()
    l2: float = 1.0  # ridge on the standardised slopes, per training row
    l2_int: float = 0.1  # ridge on the intercepts
    xi: float = 0.0  # time decay of the training rows, per day
    markets: tuple[str, ...] = ("1x2", *BINARY)  # the markets the learner corrects
    pool: bool = False  # one input for the others' mean disagreement instead of one each


def _lp(p: np.ndarray) -> np.ndarray:
    return np.log(np.clip(p, 1e-6, 1.0))


def design(b: Block, spec: StackSpec) -> dict[str, tuple[np.ndarray, np.ndarray]]:
    """Per market: (offset, Z). Offset is the reference's log probabilities (1X2, (n, 3)) or
    log odds (binary, (n,)); Z the raw (unstandardised) inputs."""
    ref = b.tilted[:, spec.ref] if spec.market else b.raw[:, spec.ref]
    lr = _lp(ref[:, :3])
    s1 = lr[:, 0] - lr[:, 2]  # log odds home against away
    s2 = lr[:, 1] - 0.5 * (lr[:, 0] + lr[:, 2])  # draw against the mean of the others
    common = []
    if spec.market:  # the reference model's own grid against the market, before the tilt
        own = b.raw[:, spec.ref]
        lo = _lp(own[:, :3])
        common += [lo[:, 0] - lo[:, 2] - s1, logit(own[:, 4]) - logit(ref[:, 4])]
    fi = feature_index(spec.feats)
    out = {}
    if "1x2" in spec.markets:
        z = [s1, s2, *common]
        d1, d2 = [], []
        for k in spec.others:
            lk = _lp(b.raw[:, k, :3])
            d1.append(lk[:, 0] - lk[:, 2] - s1)
            d2.append(lk[:, 1] - 0.5 * (lk[:, 0] + lk[:, 2]) - s2)
        if spec.pool and d1:
            z += [np.mean(d1, axis=0), np.mean(d2, axis=0)]
        else:
            z += [x for pair in zip(d1, d2, strict=True) for x in pair]
        if fi:
            z += list((b.xh[:, fi] - b.xa[:, fi]).T)
        out["1x2"] = (lr, np.column_stack(z))
    for m in BINARY:
        if m not in spec.markets:
            continue
        c = COL[m]
        lr_m = logit(ref[:, c])
        z = [lr_m, *common]
        src = b.tilted if (spec.market and m != "o25") else b.raw
        d = [logit(src[:, k, c]) - lr_m for k in spec.others]
        z += [np.mean(d, axis=0)] if (spec.pool and d) else d
        if fi:
            z += list((b.xh[:, fi] + b.xa[:, fi]).T)
        out[m] = (lr_m, np.column_stack(z))
    return out


# ---- ridge logistic fits -----------------------------------------------------------------


@dataclass
class Fitted:
    mean: np.ndarray
    sd: np.ndarray
    coef: np.ndarray  # binary: (1 + F,); 1X2: (2, 1 + F)
    n: int = 0
    extra: dict = field(default_factory=dict)


def _standardise(Z: np.ndarray, w: np.ndarray):
    mean = np.average(Z, axis=0, weights=w)
    sd = np.sqrt(np.average((Z - mean) ** 2, axis=0, weights=w))
    sd = np.where(sd > 1e-9, sd, 1.0)
    return (Z - mean) / sd, mean, sd


def _newton(f, x0: np.ndarray, tol: float = 1e-10, iters: int = 50) -> np.ndarray:
    """Minimise a smooth, strictly convex f (returning value, gradient and Hessian) by Newton
    steps with backtracking. The ridge makes every objective here strictly convex, so the
    optimum is unique and this finds it to `tol` in a handful of steps."""
    x = x0
    val, g, H = f(x)
    for _ in range(iters):
        if np.abs(g).max() < tol:
            break
        step = -np.linalg.solve(H, g)
        t = 1.0
        for _ in range(40):
            new = f(x + t * step)
            if new[0] <= val + 1e-4 * t * (g @ step):
                break
            t /= 2
        x = x + t * step
        val, g, H = new
    return x


def fit_binary(off, Z, y, w, l2: float, l2_int: float) -> Fitted:
    """Ridge logistic regression with offset: minimises the weighted mean log loss plus
    l2/2 |b|^2 + l2_int/2 a^2 (b on standardised inputs)."""
    S, mean, sd = _standardise(Z, w)
    sw = w.sum()
    pen = np.r_[l2_int, np.full(S.shape[1], l2)]
    X = np.column_stack([np.ones(len(S)), S])

    def f(b):
        eta = off + X @ b
        p = expit(eta)
        ll = np.logaddexp(0.0, eta) - y * eta
        grad = X.T @ (w * (p - y)) / sw + pen * b
        hess = (X * (w * p * (1 - p))[:, None]).T @ X / sw + np.diag(pen)
        return (w @ ll) / sw + 0.5 * (pen * b) @ b, grad, hess

    return Fitted(mean, sd, _newton(f, np.zeros(X.shape[1])), len(y))


def fit_multinomial(off, Z, y, w, l2: float, l2_int: float) -> Fitted:
    """Ridge multinomial logistic (home, draw against away) with (n, 3) log-probability
    offsets."""
    S, mean, sd = _standardise(Z, w)
    sw = w.sum()
    n, F = S.shape
    X = np.column_stack([np.ones(n), S])
    pen = np.tile(np.r_[l2_int, np.full(F, l2)], 2)
    Y = np.eye(3)[y]

    def f(v):
        B = v.reshape(2, F + 1)
        eta = off.copy()
        eta[:, :2] += X @ B.T
        lq = log_softmax(eta, axis=1)
        p = np.exp(lq)
        ll = -(Y * lq).sum(axis=1)
        grad = (((p - Y)[:, :2] * w[:, None]).T @ X).ravel() / sw + pen * v
        hess = np.empty((2 * (F + 1), 2 * (F + 1)))
        for c in range(2):
            for d in range(2):
                wt = w * p[:, c] * ((c == d) - p[:, d])
                hess[c * (F + 1) : (c + 1) * (F + 1), d * (F + 1) : (d + 1) * (F + 1)] = (
                    (X * wt[:, None]).T @ X / sw
                )
        hess += np.diag(pen)
        return (w @ ll) / sw + 0.5 * (pen * v) @ v, grad, hess

    return Fitted(mean, sd, _newton(f, np.zeros(2 * (F + 1))).reshape(2, F + 1), n)


def fit_stack(
    rec: Block, mask: np.ndarray, spec: StackSpec, cut_day: np.datetime64
) -> dict[str, Fitted]:
    """Fit every market's learner on the records selected by `mask`."""
    sub = _subset(rec, mask)
    age = (cut_day - sub.date).astype("timedelta64[D]").astype(float)
    w = np.exp(-spec.xi * np.clip(age, 0, None))
    ys = outcomes(sub.goals)
    out = {}
    for m, (off, Z) in design(sub, spec).items():
        fit = fit_multinomial if m == "1x2" else fit_binary
        out[m] = fit(off, Z, ys[m], w, spec.l2, spec.l2_int)
    return out


def predict_stack(b: Block, spec: StackSpec, fits: dict[str, Fitted]) -> np.ndarray:
    """(n, 6) learned P(home), P(draw), P(over 1.5 / 2.5 / 3.5), P(BTTS); the reference's own
    value for a market without a learner."""
    ref = b.tilted[:, spec.ref] if spec.market else b.raw[:, spec.ref]
    t = np.column_stack([ref[:, 0], ref[:, 1], ref[:, 3], ref[:, 4], ref[:, 5], ref[:, 6]])
    for m, (off, Z) in design(b, spec).items():
        f = fits.get(m)
        if f is None:
            continue
        X = np.column_stack([np.ones(len(Z)), (Z - f.mean) / f.sd])
        if m == "1x2":
            eta = off.copy()
            eta[:, :2] += X @ f.coef.T
            p = np.exp(log_softmax(eta, axis=1))
            t[:, 0], t[:, 1] = p[:, 0], p[:, 1]
        else:
            t[:, 1 + BINARY.index(m) + 1] = expit(off + X @ f.coef)
    return t


def _subset(b: Block, mask: np.ndarray) -> Block:
    def take(x):
        return None if x is None else x[mask]

    return Block(
        ids=b.ids[mask],
        division=b.division[mask],
        date=b.date[mask],
        raw=b.raw[mask],
        tilted=take(b.tilted),
        market=take(b.market),
        xh=take(b.xh),
        xa=take(b.xa),
        n_home=b.n_home[mask],
        n_away=b.n_away[mask],
        goals=take(b.goals),
    )


# ---- the model ---------------------------------------------------------------------------


class MetaStack:
    """Learned corrections of a reference forecast, as one coherent grid (module docstring)."""

    markets = ALL_MARKETS

    def __init__(
        self,
        name: str,
        members: Sequence[str],
        ref: str,
        others: Sequence[str],
        market: bool = False,
        feats: Sequence[str] = (),
        l2: float = 1.0,
        l2_int: float = 0.1,
        xi: float = 0.0,
        warmup_days: int = 365,
        min_rows: int = 300,
        prices: Callable[[], pd.DataFrame] = default_prices,
        factories: dict | None = None,
    ):
        self.name = name
        self.stream = MemberStream(
            members,
            market=market,
            features=bool(feats),
            warmup_days=warmup_days,
            prices=prices,
            factories=factories,
        )
        names = self.stream.member_names
        self.spec = StackSpec(
            ref=names.index(ref),
            others=tuple(names.index(o) for o in others),
            market=market,
            feats=tuple(feats),
            l2=l2,
            l2_int=l2_int,
            xi=xi,
        )
        self.min_rows = min_rows
        self.fits: dict[str, Fitted] = {}
        self.n_train = 0
        self.fallbacks = 0  # fixtures whose learned targets could not all be reached

    def params(self) -> dict:
        s = self.spec
        names = self.stream.member_names
        return {
            "reference": names[s.ref],
            "others": [names[k] for k in s.others],
            "feats": list(s.feats),
            "l2": s.l2,
            "l2_int": s.l2_int,
            "xi": s.xi,
            "min_rows": self.min_rows,
            "learners": "ridge multinomial logistic (1X2), ridge logistic (O/U 1.5 2.5 3.5, BTTS)",
            "grid": "reference grid tilted onto the six learned probabilities",
            "stream": self.stream.params(),
            **({"books": list(BOOKS), "phase": "pre_closing", "devig": "shin"} if s.market else {}),
        }

    def training_mask(self, rec: Block) -> np.ndarray:
        mask = rec.eligible.copy()
        if self.spec.market:
            mask &= rec.priced
        return mask

    def fit(self, history: pd.DataFrame, cutoff) -> None:
        self.stream.update(history, cutoff)
        rec = self.stream.records()
        if rec is None:
            self.fits, self.n_train = {}, 0
            return
        mask = self.training_mask(rec)
        self.n_train = int(mask.sum())
        if self.n_train < self.min_rows:
            self.fits = {}
            return
        cut = pd.Timestamp(cutoff)
        cut = cut.tz_convert("Europe/London").tz_localize(None) if cut.tzinfo else cut
        self.fits = fit_stack(rec, mask, self.spec, np.datetime64(cut.normalize(), "ns"))

    def predict(self, fixtures: pd.DataFrame) -> MarketProbs:
        b = self.stream.predict(fixtures)
        k = self.spec.ref
        grid = (b.tilted_grids if self.spec.market else b.grids)[k].copy()
        if self.spec.market:
            self.last_used = b.last_used
        learn = np.ones(len(b), dtype=bool) if not self.spec.market else b.priced
        if self.fits and learn.any():
            t = predict_stack(b, self.spec, self.fits)
            new, ok = tilt_markets(grid[learn], t[learn])
            grid[learn] = new
            self.fallbacks += int((~ok).sum())
        lam_h = (grid.sum(axis=2) * GOALS).sum(axis=1)
        lam_a = (grid.sum(axis=1) * GOALS).sum(axis=1)
        return markets_from_grid(grid, lam_h, lam_a)


class MarketMetaStack(MarketFacing, MetaStack):
    """MetaStack on the market-tilted reference, with what footy.live needs of a model +
    market candidate (`decision_time`, `last_used`, `provenance`)."""

    def __init__(self, name: str, members: Sequence[str], ref: str, others: Sequence[str], **kw):
        super().__init__(name, members, ref, others, market=True, **kw)


# ---- registered candidates (hyper-parameters chosen on 2021/22 and 2022/23 only) ---------
#
# Search on the training seasons (docs/research/machine-learning.md), replayed walk-forward
# from recorded out-of-sample member forecasts, five leagues pooled: ridge 0.001 to 1e6,
# engineered features none / 14 of ml_gbm's, the others' disagreement per member or pooled,
# time decay 0 / 0.001, all five markets or the four binary ones, warm-up 365 / 730 days;
# 102 settings (156 replays) on 2022/23, 54 of them also on 2021/22, ranked by the mean of
# the two seasons' D-025 composites.
# Light ridge overfits badly (0.962-0.992 on 2022/23) and every engineered-feature setting
# lost to none: with ~2,000 training rows only a few inputs can be learned.
#   with prices:    0.95581 / 0.96027 against the shots model + market 0.95568 / 0.96077
#   without prices: 0.96258 / 0.96579 against congestion_context_on_elo 0.96349 / 0.96677
# On both, the online mixture (ml_online) did better on the same seasons.
SHOTS, CTX, GLM = "shots_xg_sot_joint_rw", "congestion_context_on_elo", "ml_gbm_glm_boost"
CONFIGS: dict[str, dict] = {
    # corrections of market_tilt_shots from ctx's and the GLM booster's own forecasts
    "ml_stack_market": {
        "members": (SHOTS, CTX, GLM),
        "ref": SHOTS,
        "others": (CTX, GLM),
        "market": True,
        "l2": 1.0,
        "l2_int": 0.01,
        "xi": 0.001,
        "warmup_days": 730,
    },
    # corrections of congestion_context_on_elo from the shots model's and the GLM booster's
    "ml_stack_pure": {
        "members": (CTX, SHOTS, GLM),
        "ref": CTX,
        "others": (SHOTS, GLM),
        "l2": 0.3,
        "l2_int": 0.01,
        "warmup_days": 730,
    },
}


def _factory(name: str, cfg: dict):
    cls = MarketMetaStack if cfg.get("market") else MetaStack
    cfg = {k: v for k, v in cfg.items() if not (k == "market" and cls is MarketMetaStack)}
    return lambda: cls(name=name, **cfg)


CANDIDATES = {name: _factory(name, cfg) for name, cfg in CONFIGS.items()}
