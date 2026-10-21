"""Alternative joint score distributions (model-lab family `bivariate_dispersion`).

Two stages, refitted at every decision batch:

1. **Expected goals.** The core time-weighted Poisson team model of
   footy.modeling.dixon_coles (shared ratings across a league and its second tier,
   per-division home advantage, a closed-doors home term from the core reference calendar
   exactly as in the core model, ridge shrinkage, exp(−xi·days) weights, second tier at
   half weight), fitted with rho = 0. `closed_doors=False` drops the calendar (then nothing
   but the DataFrames passed in is read; it cost ~0.0004 composite in 2023/24). Any other
   score model with a `rates(fixtures)` method can supply the means instead
   (`means_factory`, e.g. the shots_xg family's model).
2. **Shape.** A few global parameters that change the joint distribution of the two scores
   *without moving the means*, fitted by time-weighted maximum likelihood on the same
   training matches, given their fitted means.

Shapes (all give one full, coherent score grid per fixture, so 1X2, totals and BTTS agree):

- `dc`: the Dixon–Coles low-score correction (reference: close to the core model).
- `bp`: Karlis–Ntzoufras bivariate Poisson, X = W1 + W3, Y = W2 + W3 with
  lambda3 = corr · sqrt(mu_h · mu_a) (positive covariance only).
- `bp_diag`: diagonal-inflated bivariate Poisson, (1 − p) · BP + p · D, D a distribution
  over 0-0, 1-1 and 2-2 (Karlis & Ntzoufras 2003).
- `draw_tilt`: Poisson (optionally with the Dixon–Coles correction) times exp(delta_k) on
  the k-k cells, renormalised: draw inflation that scales with how drawn the match already
  is, unlike the additive inflation of `bp_diag`.
- `copula`: Frank copula (negative or positive dependence) on Poisson,
  Conway–Maxwell–Poisson (`com`, dispersion nu: nu > 1 under-dispersed) or Weibull-count
  (`weibull`, shape c: c > 1 under-dispersed; McShane et al. 2008, Boshnakov et al. 2017)
  margins, each mean-parametrised so the margin keeps the fitted expected goals.
- `birth`: a state-dependent birth process (in the spirit of Dixon & Robinson 1998). The
  match is cut into `periods` per half; in each period both teams score Poisson goals at a
  rate that depends on the score at the start of the period: a team that leads scores at
  exp(lead) times its base rate, a team that trails at exp(trail) times it; with
  `time_trend` those effects change linearly through the match, and with `draw_state` both
  teams' rates in a level game after goals (1-1, 2-2, ...) are multiplied by exp(level).
  Base rates are rescaled per match so the expected goals stay the stage-1 means. When
  half-time scores are present the likelihood uses them (first-half score, then the second
  half from that state), which identifies the game-state effects far better than full-time
  scores alone; rows without half-time scores use the full-time score only. Typical fits:
  trailing sides score ~8–10% above and leading sides ~7% below their base rate, which
  moves mass towards narrow results and draws.

Any shape can also take a diagonal inflation on top (`inflate=True`): (1 − p) · grid +
p · Discrete(0-0, 1-1, 2-2), fitted by full-time likelihood with the shape's grids fixed.

Expensive shapes (`birth`) refit their few global parameters only when the cutoff has moved
`shape_refit_days` (default a season) since the last shape fit, warm-started; the team model
is refitted at every batch. Everything is deterministic.

Lab findings (validation 2023/24, inner seasons 2020/21–2022/23): the joint distribution is a
second-order effect next to the expected goals; the best shapes move the D-022 composite by
about 0.001. The birth process gives the most consistent 1X2 gain (RPS); diagonal inflation
the most consistent totals / BTTS gain. Shapes fitted on in-sample means over-state
under-dispersion (the ratings were fitted to the same goals), so COM / Weibull margins that
help 2023/24 hurt the three earlier seasons; they are kept here but not registered.
"""

import numpy as np
import pandas as pd
from scipy.optimize import minimize
from scipy.special import gammaln
from scipy.stats import poisson

from footy.modeling.base import ALL_MARKETS
from footy.modeling.closed_doors import closed_doors_mask
from footy.modeling.dixon_coles import TIER1, TIER2, DixonColes
from footy.modeling.grid import MAX_GOALS, MarketProbs, markets_from_grid
from footy.modeling.walkforward import RESULT_COLUMNS

N_FIT = 16  # support 0..15 for closed-form shapes (mass beyond is < 1e-9)
MU_MIN, MU_MAX = 0.05, 8.0


# ---- margins ---------------------------------------------------------------------------------


def pois_pmf(mu: np.ndarray, n: int = N_FIT) -> np.ndarray:
    return poisson.pmf(np.arange(n)[None, :], np.asarray(mu, float)[:, None])


_KK = np.arange(40)
_LF = gammaln(_KK + 1)


def com_pmf(mu: np.ndarray, nu: float, n: int = N_FIT) -> np.ndarray:
    """Conway–Maxwell–Poisson, P(k) ∝ g^(nu·k) / (k!)^nu, with g solved so the mean is mu
    (interpolated on a table of means)."""
    if abs(nu - 1.0) < 1e-12:
        return pois_pmf(mu, n)
    lg = np.linspace(np.log(0.02), np.log(12.0), 600)
    logp = nu * (lg[:, None] * _KK[None, :] - _LF[None, :])
    p = np.exp(logp - logp.max(axis=1, keepdims=True))
    mean = (p / p.sum(axis=1, keepdims=True)) @ _KK
    g = np.interp(np.log(mu), np.log(mean), lg)
    logp = nu * (g[:, None] * _KK[None, :] - _LF[None, :])
    p = np.exp(logp - logp.max(axis=1, keepdims=True))
    return (p / p.sum(axis=1, keepdims=True))[:, :n]


_JW = 60


def _weibull_alpha(c: float) -> np.ndarray:
    """McShane et al. (2008) series coefficients alpha[j, k], j, k < _JW:
    alpha[j, 0] = Γ(cj+1)/Γ(j+1), alpha[j, k+1] = Σ_{m=k}^{j-1} alpha[m, k] · W[j − m] with
    W[d] = Γ(cd+1)/Γ(d+1), i.e. a discrete convolution per column."""
    j = np.arange(_JW)
    w = np.exp(gammaln(c * j + 1) - gammaln(j + 1))
    a = np.zeros((_JW, _JW))
    a[:, 0] = w
    w1 = w.copy()
    w1[0] = 0.0
    for k in range(_JW - 1):
        a[:, k + 1] = np.convolve(a[:, k], w1)[:_JW]
    return a


_WEIBULL_CACHE: dict[float, tuple] = {}


def _weibull_probs(lam: np.ndarray, c: float, mat: np.ndarray) -> np.ndarray:
    """P(N = k) = sum_j (-1)^(j+k) lam^j alpha[j, k] / Γ(cj + 1)  (t = 1)."""
    j = np.arange(_JW)
    base = np.exp(j[None, :] * np.log(lam)[:, None] - gammaln(c * j + 1)[None, :])
    return np.clip(base @ mat, 0, None)


def _weibull_setup(c: float) -> tuple:
    """Signed series matrix and mean table for shape c (memoised: a pure function of c)."""
    if c not in _WEIBULL_CACHE:
        if len(_WEIBULL_CACHE) >= 64:
            _WEIBULL_CACHE.pop(next(iter(_WEIBULL_CACHE)))
        j = np.arange(_JW)
        sign = (-1.0) ** (j[:, None] + j[None, :]) * (j[:, None] >= j[None, :])
        mat = np.ascontiguousarray((_weibull_alpha(c) * sign)[:, :N_FIT])
        ll = np.linspace(np.log(0.03), np.log(7.0), 300)
        p = _weibull_probs(np.exp(ll), c, mat)
        mean = (p / p.sum(axis=1, keepdims=True)) @ np.arange(N_FIT)
        _WEIBULL_CACHE[c] = (mat, np.log(mean), ll)
    return _WEIBULL_CACHE[c]


def weibull_pmf(mu: np.ndarray, c: float, n: int = N_FIT) -> np.ndarray:
    """Weibull-count distribution (renewal process with Weibull gaps, shape c; c = 1 is
    Poisson), with its scale solved so the mean is mu."""
    if abs(c - 1.0) < 1e-12:
        return pois_pmf(mu, n)
    mat, log_mean, ll = _weibull_setup(float(c))
    lam = np.exp(np.interp(np.log(mu), log_mean, ll))
    p = _weibull_probs(lam, c, mat)[:, :n]
    return p / p.sum(axis=1, keepdims=True)


MARGINS = {
    "poisson": (0, lambda mu, th: pois_pmf(mu)),
    "com": (1, lambda mu, th: com_pmf(mu, th[0])),
    "weibull": (1, lambda mu, th: weibull_pmf(mu, th[0])),
}


# ---- joint constructions -------------------------------------------------------------------


def frank_joint(ph: np.ndarray, pa: np.ndarray, theta: float) -> np.ndarray:
    """Frank copula on discrete margins: P(x, y) as the rectangle measure of C(F_h, F_a)."""
    g = ph[:, :, None] * pa[:, None, :]
    if abs(theta) < 1e-12:
        return g
    fh = np.clip(np.concatenate([np.zeros((len(ph), 1)), np.cumsum(ph, 1)], 1), 0, 1)
    fa = np.clip(np.concatenate([np.zeros((len(pa), 1)), np.cumsum(pa, 1)], 1), 0, 1)
    num = np.expm1(-theta * fh)[:, :, None] * np.expm1(-theta * fa)[:, None, :]
    C = -np.log1p(num / np.expm1(-theta)) / theta
    return np.clip(C[:, 1:, 1:] - C[:, :-1, 1:] - C[:, 1:, :-1] + C[:, :-1, :-1], 0, None)


def bivariate_poisson(mh: np.ndarray, ma: np.ndarray, corr: float) -> np.ndarray:
    l3 = np.clip(corr * np.sqrt(mh * ma), 0.0, 0.95 * np.minimum(mh, ma))
    p1, p2, p3 = pois_pmf(mh - l3), pois_pmf(ma - l3), pois_pmf(l3)
    g = np.zeros((len(mh), N_FIT, N_FIT))
    for k in range(N_FIT):
        if p3[:, k].max() < 1e-13:
            break
        g[:, k:, k:] += p3[:, k, None, None] * p1[:, : N_FIT - k, None] * p2[:, None, : N_FIT - k]
    return g


def dc_tau(g: np.ndarray, mh: np.ndarray, ma: np.ndarray, rho: float) -> np.ndarray:
    g = g.copy()
    g[:, 0, 0] *= np.clip(1 - mh * ma * rho, 0, None)
    g[:, 0, 1] *= np.clip(1 + mh * rho, 0, None)
    g[:, 1, 0] *= np.clip(1 + ma * rho, 0, None)
    g[:, 1, 1] *= max(1 - rho, 0.0)
    return g


def fold(g: np.ndarray, m: int = MAX_GOALS) -> np.ndarray:
    """Fold a grid on a wider support into 0..m with "m or more" in the last row/column."""
    out = np.zeros((g.shape[0], m + 1, m + 1))
    out[:, :m, :m] = g[:, :m, :m]
    out[:, m, :m] = g[:, m:, :m].sum(1)
    out[:, :m, m] = g[:, :m, m:].sum(2)
    out[:, m, m] = g[:, m:, m:].sum((1, 2))
    return out / out.sum(axis=(1, 2), keepdims=True)


# ---- shapes ----------------------------------------------------------------------------------


class GridShape:
    """A shape whose grid has a closed form: log-likelihood = log grid[x, y] (normalised)."""

    expensive = False
    init: list[float] = []
    bounds: list[tuple[float, float]] = []

    def grid(self, mh, ma, th) -> np.ndarray:
        raise NotImplementedError

    def loglik(self, d: dict, th) -> np.ndarray:
        g = self.grid(d["mh"], d["ma"], th)
        x, y = np.minimum(d["x"], g.shape[1] - 1), np.minimum(d["y"], g.shape[2] - 1)
        p = g[np.arange(len(x)), x, y] / g.sum(axis=(1, 2))
        return np.log(np.clip(p, 1e-300, None))


class DCShape(GridShape):
    init, bounds = [-0.05], [(-0.3, 0.3)]

    def grid(self, mh, ma, th):
        return dc_tau(pois_pmf(mh)[:, :, None] * pois_pmf(ma)[:, None, :], mh, ma, th[0])


class BPShape(GridShape):
    init, bounds = [0.04], [(0.0, 0.6)]

    def grid(self, mh, ma, th):
        return bivariate_poisson(mh, ma, th[0])


class BPDiagShape(GridShape):
    """(1 − p) · BP + p · Discrete(0-0, 1-1, 2-2); p = logistic(th1), D = softmax(0, th2, th3)."""

    init, bounds = [0.03, -4.0, 0.0, 0.0], [(0.0, 0.6), (-9.0, 0.0), (-5.0, 5.0), (-5.0, 5.0)]

    def grid(self, mh, ma, th):
        g = bivariate_poisson(mh, ma, th[0])
        p = 1 / (1 + np.exp(-th[1]))
        d = np.exp([0.0, th[2], th[3]])
        g = (1 - p) * g
        for k, q in enumerate(d / d.sum()):
            g[:, k, k] += p * q
        return g


class DrawTiltShape(GridShape):
    """Poisson (× Dixon–Coles tau if `rho`) × exp(delta_k) on the k-k cells for k in `cells`."""

    def __init__(self, cells=(0, 1, 2), rho: bool = False):
        self.cells, self.rho = tuple(cells), rho
        self.init = [0.0] * len(self.cells) + ([-0.03] if rho else [])
        self.bounds = [(-2.0, 2.0)] * len(self.cells) + ([(-0.3, 0.3)] if rho else [])

    def grid(self, mh, ma, th):
        g = pois_pmf(mh)[:, :, None] * pois_pmf(ma)[:, None, :]
        if self.rho:
            g = dc_tau(g, mh, ma, th[len(self.cells)])
        for k, dk in zip(self.cells, th, strict=False):
            g[:, k, k] *= np.exp(dk)
        return g


class CopulaShape(GridShape):
    """Frank copula (if `copula`) on Poisson / COM / Weibull-count margins."""

    def __init__(self, margin: str = "com", copula: bool = True):
        self.margin, self.copula = margin, copula
        k, self._pmf = MARGINS[margin]
        mb = {"com": (0.6, 1.6), "weibull": (0.7, 1.4)}.get(margin)
        self.init = [1.05] * k + ([0.1] if copula else [])
        self.bounds = [mb] * k + ([(-4.0, 4.0)] if copula else [])
        self._k = k

    def grid(self, mh, ma, th):
        ph, pa = self._pmf(mh, th[: self._k]), self._pmf(ma, th[: self._k])
        if not self.copula:
            return ph[:, :, None] * pa[:, None, :]
        return frank_joint(ph, pa, th[self._k])


class BirthShape:
    """State-dependent birth process; see the module docstring.

    th = [lead, trail, (level,) (lead_t, trail_t,) first-half share]. With `time_trend` the
    lead / trail effects grow linearly through the match: effect(t) = e + e_t · (t − ½), t the
    fraction of the match played at the start of the period."""

    expensive = True

    def __init__(
        self,
        periods: int = 6,
        draw_state: bool = False,
        time_trend: bool = False,
        use_ht: bool = True,
    ):
        self.P, self.draw_state, self.time_trend, self.use_ht = (
            periods,
            draw_state,
            time_trend,
            use_ht,
        )
        k = 2 + draw_state
        self.init = [-0.07, 0.1] + [0.0] * (k - 2 + 2 * time_trend) + [0.45]
        self.bounds = [(-0.6, 0.6)] * k + [(-1.2, 1.2)] * (2 * time_trend) + [(0.35, 0.55)]

    def _mults(self, th, G, tau):
        """Rate multipliers for (home, away) by state, at centred match time tau."""
        e = np.arange(G)[:, None] - np.arange(G)[None, :]
        lead, trail = th[0], th[1]
        if self.time_trend:
            i = 2 + self.draw_state
            lead, trail = lead + th[i] * tau, trail + th[i + 1] * tau
        log_h = np.where(e > 0, lead, np.where(e < 0, trail, 0.0))
        if self.draw_state:
            log_h = log_h + th[2] * ((e == 0) & (np.arange(G)[:, None] > 0))
        mh = np.exp(log_h)
        return mh, mh.T  # the away team's own score is the column index

    def propagate(self, bh, ba, th, G, start=None, halves=(0, 1)):
        n, P, s1 = len(bh), self.P, th[-1]
        if start is None:
            pm = np.zeros((n, G, G))
            pm[:, 0, 0] = 1.0
        else:
            pm = start
        pad = np.zeros((n, G + 2, G + 2))
        ht = None
        for half in halves:
            r = (s1 if half == 0 else 1 - s1) / P
            for step in range(P):
                if step == 0 or self.time_trend:
                    tau = (half * P + step + 0.5) / (2 * P) - 0.5
                    mh, ma = self._mults(th, G, tau)
                    lh = bh[:, None, None] * (r * mh)[None]
                    la = ba[:, None, None] * (r * ma)[None]
                    eh, ea = np.exp(-lh), np.exp(-la)
                    ph = (eh, lh * eh, 1 - eh - lh * eh)  # 0, 1, 2+ goals in the period
                    pa = (ea, la * ea, 1 - ea - la * ea)
                pad[:] = 0.0
                for a in range(3):
                    xa = pm * ph[a]
                    for b in range(3):
                        pad[:, a : a + G, b : b + G] += xa * pa[b]
                pm = pad[:, :G, :G].copy()
                pm[:, G - 1, :] += pad[:, G:, :G].sum(1)
                pm[:, :, G - 1] += pad[:, :G, G:].sum(2)
                pm[:, G - 1, G - 1] += pad[:, G:, G:].sum((1, 2))
            if half == 0:
                ht = pm
        return pm, ht

    def calibrate(self, mh, ma, th, G, iters):
        bh, ba, k = mh.copy(), ma.copy(), np.arange(G)
        for _ in range(iters):
            g, _ = self.propagate(bh, ba, th, G)
            bh = bh * mh / np.maximum(g.sum(2) @ k, 1e-9)
            ba = ba * ma / np.maximum(g.sum(1) @ k, 1e-9)
        return bh, ba

    def grid(self, mh, ma, th):
        G = MAX_GOALS + 2
        bh, ba = self.calibrate(mh, ma, th, G, iters=3)
        return self.propagate(bh, ba, th, G)[0]

    def loglik(self, d: dict, th) -> np.ndarray:
        G = 9  # training: 8+ goals for one side share the last cell
        bh, ba = self.calibrate(d["mh"], d["ma"], th, G, iters=1)
        ft, ht = self.propagate(bh, ba, th, G)
        x, y = np.minimum(d["x"], G - 1), np.minimum(d["y"], G - 1)
        ll = np.log(np.clip(ft[np.arange(len(x)), x, y], 1e-300, None))
        hx, hy = d["hx"], d["hy"]
        ok = ~(np.isnan(hx) | np.isnan(hy))
        if self.use_ht and ok.any():
            ok &= (np.nan_to_num(hx) <= d["x"]) & (np.nan_to_num(hy) <= d["y"])
            a = np.minimum(hx[ok].astype(int), G - 1)
            b = np.minimum(hy[ok].astype(int), G - 1)
            j = np.arange(ok.sum())
            start = np.zeros((ok.sum(), G, G))
            start[j, a, b] = 1.0
            ft2, _ = self.propagate(bh[ok], ba[ok], th, G, start=start, halves=(1,))
            ll[ok] = np.log(np.clip(ht[ok][j, a, b], 1e-300, None)) + np.log(
                np.clip(ft2[j, x[ok], y[ok]], 1e-300, None)
            )
        return ll


SHAPES = {
    "dc": DCShape,
    "bp": BPShape,
    "bp_diag": BPDiagShape,
    "draw_tilt": DrawTiltShape,
    "copula": CopulaShape,
    "birth": BirthShape,
}


INFLATION_BOUNDS = [(-9.0, 0.0), (-5.0, 5.0), (-5.0, 5.0)]


def _inflation(z) -> tuple[float, np.ndarray]:
    """Inflation weight p = logistic(z0) and its distribution over 0-0, 1-1, 2-2."""
    q = np.exp([0.0, z[1], z[2]])
    return float(1 / (1 + np.exp(-z[0]))), q / q.sum()


# ---- the model -------------------------------------------------------------------------------


class BivariateDispersion:
    markets = ALL_MARKETS

    def __init__(
        self,
        shape: str = "birth",
        shape_kw: dict | None = None,
        xi: float = 0.0019,
        reg: float = 2.0,
        tier2_weight: float = 0.5,
        shots_weight: float = 0.0,
        closed_doors: bool = True,
        shape_xi: float | None = None,
        shape_refit_days: int | None = None,
        means_factory=None,
        inflate: bool = False,
        name: str = "bivariate_dispersion",
    ):
        self.name = name
        self.inflate = inflate
        self.infl_ = np.array([-4.0, 0.0, 0.0])
        self.means_factory = means_factory
        self.shape_name, self.shape_kw = shape, dict(shape_kw or {})
        self.shape = SHAPES[shape](**self.shape_kw)
        self.xi, self.reg, self.tier2_weight = xi, reg, tier2_weight
        self.shots_weight, self.closed_doors = shots_weight, closed_doors
        self.shape_xi = xi if shape_xi is None else shape_xi
        self.shape_refit_days = (
            shape_refit_days
            if shape_refit_days is not None
            else (365 if self.shape.expensive else 0)
        )
        # Any score model exposing rates(fixtures) -> (lam_h, lam_a) can supply the means;
        # by default the core Poisson team model (Dixon–Coles with rho = 0).
        self.means = (
            means_factory()
            if means_factory is not None
            else DixonColes(
                xi=xi,
                rho=False,
                reg=reg,
                tier2_weight=tier2_weight,
                shots_weight=shots_weight,
                closed_doors=closed_doors,
                name=f"{name}:means",
            )
        )
        self.theta_ = np.array(self.shape.init, float)
        self._shape_cutoff: pd.Timestamp | None = None
        self.converged_ = True

    def params(self) -> dict:
        return {
            "shape": self.shape_name,
            "shape_kw": self.shape_kw,
            "xi": self.xi,
            "reg": self.reg,
            "tier2_weight": self.tier2_weight,
            "shots_weight": self.shots_weight,
            "closed_doors": self.closed_doors,
            "shape_xi": self.shape_xi,
            "shape_refit_days": self.shape_refit_days,
            "means": getattr(self.means, "name", type(self.means).__name__),
            "inflate": self.inflate,
        }

    # ---- fitting -------------------------------------------------------------------------

    def fit(self, history: pd.DataFrame, cutoff) -> None:
        self.means.fit(history, cutoff)
        due = (
            self._shape_cutoff is None
            or (cutoff - self._shape_cutoff).days >= self.shape_refit_days
        )
        if due and (len(self.theta_) or self.inflate):
            self._fit_shape(history[history.division.isin(TIER1 | TIER2)], cutoff)
            self._shape_cutoff = cutoff

    def _train_means(self, h: pd.DataFrame) -> tuple[np.ndarray, np.ndarray]:
        """Fitted expected goals for the training matches themselves."""
        m = self.means
        if not isinstance(m, DixonColes):
            return m.rates(h.drop(columns=RESULT_COLUMNS, errors="ignore"))
        T, D, x = m.T, m.D, m.coef
        att, dfn, c, ct2 = x[:T], x[T : 2 * T], x[2 * T], x[2 * T + 1]
        home = x[2 * T + 2 : 2 * T + 2 + D]
        hi = h.home_team.map(m.team_idx).to_numpy()
        ai = h.away_team.map(m.team_idx).to_numpy()
        di = h.division.map(m.div_idx).to_numpy()
        tier2 = h.division.isin(TIER2).to_numpy().astype(float)
        cd = closed_doors_mask(h) if self.closed_doors else np.zeros(len(h), bool)
        hadv = np.where(cd, x[-2], home[di])
        eta_h = c + ct2 * tier2 + hadv + att[hi] - dfn[ai]
        eta_a = c + ct2 * tier2 + att[ai] - dfn[hi]
        return np.exp(eta_h), np.exp(eta_a)

    def _fit_shape(self, h: pd.DataFrame, cutoff) -> None:
        mh, ma = self._train_means(h)
        days = (cutoff.tz_convert("UTC").tz_localize(None) - h["date"]).dt.days.to_numpy()
        w = np.exp(-self.shape_xi * days)
        w = w * np.where(h.division.isin(TIER2).to_numpy(), self.tier2_weight, 1.0)
        w = w / w.sum()
        d = {
            "mh": np.clip(mh, MU_MIN, MU_MAX),
            "ma": np.clip(ma, MU_MIN, MU_MAX),
            "x": h.home_goals.to_numpy(int),
            "y": h.away_goals.to_numpy(int),
            "hx": h.ht_home_goals.to_numpy(float),
            "hy": h.ht_away_goals.to_numpy(float),
        }
        if len(self.theta_):

            def nll(th):
                return -(w * self.shape.loglik(d, th)).sum()

            res = minimize(
                nll,
                self.theta_,
                method="L-BFGS-B",
                bounds=self.shape.bounds,
                options={"maxiter": 60 if self.shape.expensive else 200, "eps": 1e-6},
            )
            self.theta_ = res.x
            self.converged_ = bool(res.success)
        if self.inflate:
            self._fit_inflation(d, w)

    def _fit_inflation(self, d: dict, w: np.ndarray) -> None:
        """Diagonal inflation on top of the fitted shape, by full-time likelihood with the
        shape's grids held fixed: (1 − p) · grid + p · Discrete(0-0, 1-1, 2-2)."""
        g = self.shape.grid(d["mh"], d["ma"], self.theta_)
        g = g / g.sum(axis=(1, 2), keepdims=True)
        x, y = np.minimum(d["x"], g.shape[1] - 1), np.minimum(d["y"], g.shape[2] - 1)
        obs = g[np.arange(len(x)), x, y]
        cell = np.where((x == y) & (x <= 2), x, -1)

        def nll(z):
            p, q = _inflation(z)
            extra = np.where(cell >= 0, q[np.maximum(cell, 0)], 0.0)
            return -(w * np.log(np.clip((1 - p) * obs + p * extra, 1e-300, None))).sum()

        res = minimize(nll, self.infl_, method="L-BFGS-B", bounds=INFLATION_BOUNDS)
        self.infl_ = res.x

    # ---- prediction ----------------------------------------------------------------------

    def predict(self, fixtures: pd.DataFrame) -> MarketProbs:
        lh, la = self.means.rates(fixtures)
        mh, ma = np.clip(lh, MU_MIN, MU_MAX), np.clip(la, MU_MIN, MU_MAX)
        g = self.shape.grid(mh, ma, self.theta_)
        if self.inflate:
            g = g / g.sum(axis=(1, 2), keepdims=True)
            p, q = _inflation(self.infl_)
            g = (1 - p) * g
            for k in range(3):
                g[:, k, k] += p * q[k]
        return markets_from_grid(fold(g), lh, la)


def _factory(name: str, cfg: dict):
    return lambda: BivariateDispersion(**cfg, name=name)


def _shots_xg_means():
    """Expected goals from the shots_xg lab family's validated model (shots on target and
    goals, strength / tempo form). Imported lazily: another family's module."""
    from footy.modeling.lab.shots_xg import CANDIDATES as SHOTS_XG

    return SHOTS_XG["shots_xg_sot_joint_rw"]()


CONFIGS: dict[str, dict] = {
    "bivariate_dispersion_birth_time": {"shape": "birth", "shape_kw": {"time_trend": True}},
    "bivariate_dispersion_birth_draw_time": {
        "shape": "birth",
        "shape_kw": {"draw_state": True, "time_trend": True},
    },
    "bivariate_dispersion_bp_diag": {"shape": "bp_diag"},
    "bivariate_dispersion_draw_tilt": {"shape": "draw_tilt"},
    # The birth-process shape on top of the shots_xg family's expected goals.
    "bivariate_dispersion_sx_birth_time": {
        "shape": "birth",
        "shape_kw": {"time_trend": True},
        "means_factory": _shots_xg_means,
    },
}
CANDIDATES = {name: _factory(name, cfg) for name, cfg in CONFIGS.items()}
