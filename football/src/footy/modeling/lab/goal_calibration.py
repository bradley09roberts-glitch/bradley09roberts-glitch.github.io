"""Out-of-sample grid calibration on top of Dixon-Coles (model-lab family `goal_calibration`).

A time-weighted Dixon-Coles model (the core footy.modeling.dixon_coles, or the dc_tuning
family's tuned version) supplies the ratings, the low-score correction and any grid tilt. A
few calibration parameters then reshape the expected goals and the joint score grid; every
market (1X2, totals, BTTS) still comes from one coherent grid per fixture.

Writing a fixture's Dixon-Coles log-rates as

    log λ_home = g + h + (dS + sO) / 2,    log λ_away = g + (−dS + sO) / 2,

with g the league intercept, h the division's home advantage, dS = strength(home) −
strength(away) (strength = attack + defence, what decides 1X2) and sO = openness(home) +
openness(away) (openness = attack − defence, how many goals a team's matches have), the
calibration is

    sO → c + k_o·(sO − c)        c: the division's mean sO over the model's last year,
    dS → k_s·dS,  h → k_h·h,
    log λ_home, log λ_away += level(division) ± split(division),
    grid cells k–k × exp(draw), renormalised.

- **League scoring level** (per division, task item a): both rates × exp(level), the
  out-of-sample goals residual of the last few weeks (exponential half-life and a normal prior
  on the log level). Dixon-Coles has one intercept averaged over about a year (half-life 365
  days), so it lags when a league's scoring rate moves, as in the 2023/24 Premier League.
- **Draw tilt** (task item b): inflation or deflation of the diagonal.
- **Home / away scaling** (task item c): a per-division split of the level, and k_h.
- **Openness scale** k_o: Dixon-Coles' ridge penalty treats attack and defence alike, so team
  openness, estimated from far noisier evidence than strength, is shrunk far too little.
  Fitted out of sample, k_o is about 0.3–0.6.
- **Recency blend** (`fast_xi`): log-rates mixed as (1 − a)·slow + a·fast between the
  Dixon-Coles fit and a second fit with a faster time decay, a fitted out of sample (and free
  to leave [0, 1]): the calibrated choice of how quickly ratings should react.

**Out-of-sample calibration.** Parameters fitted on the model's in-sample fit are biased (the
ratings were fitted to those same goals; openness in particular looks real in-sample). Here
they are fitted on genuine forecasts made inside `fit` from the history alone: on a fixed weekly
calendar of *anchor* dates (Fridays), the base model fitted on the `anchor_days` before the
anchor forecasts that week's matches. An anchor fit depends only on the matches in its own
window, so it is cached across batches (one new fit a week) and a fresh model reproduces it;
the global parameters are refitted on a fixed 28-day calendar from the forecasts dated before
the refit day, and the levels at every batch. `anchor_days` + `window` + 35 days stays inside the
three years of history the runner passes. Every parameter is fitted by time-weighted maximum
likelihood of the exact scores of those forecasts (never per market), with ridge priors
pulling it towards "no change".

Lab findings. Every setting was chosen on the inner seasons 2021/22 and 2022/23 (five leagues,
3,652 fixtures, D-025 composite against the frequency baseline):

| | inner 21/22–22/23 | validation 23/24 |
| --- | --- | --- |
| dixon_coles | 0.9712 | 0.9598 |
| goal_calibration_level (a) | 0.9707 | 0.9576 |
| goal_calibration_open_level | 0.9677 | 0.9570 |
| goal_calibration_recency | 0.9672 | 0.9571 |
| dc_tuning_tilt (base of the next row) | 0.9683 | 0.9560 |
| goal_calibration_tuned_level | 0.9680 | 0.9548 |
| goal_calibration_mix (tuned_level and recency grids, half each) | 0.9671 | 0.9555 |

The openness scale improves all five leagues in both inner seasons; the level matters most when a
league's scoring rate moves (Premier League 2023/24). The recency blend is the only change that
also lowers the inner RPS. Draw tilt (b), home/away split and home scale (c), the strength
scale, log-normal rate mixing (over-dispersion), a both-teams-score tilt, a total-goals
dispersion tilt, a rating-trend term, information-weighted openness shrinkage and a Kalman local
level all scored within 0.0005 of these or worse on the inner seasons, so no registered
candidate uses them (the code keeps draw, split, k_s and k_h). On 2023/24 a version with draw,
split and strength scale scored 0.9584 against 0.9571 for the same model without them (both with
a 30-day level half-life).
"""

import copy

import numpy as np
import pandas as pd
from scipy.optimize import minimize
from scipy.special import gammaln

from footy.modeling.base import ALL_MARKETS
from footy.modeling.closed_doors import closed_doors_mask
from footy.modeling.dixon_coles import TIER2, DixonColes
from footy.modeling.grid import (
    GOALS,
    MAX_GOALS,
    MarketProbs,
    markets_from_grid,
    mix_grids,
    poisson_grid,
)

EPOCH = pd.Timestamp("2000-01-07")  # a Friday: the anchor and refit calendars start here
_K = np.arange(MAX_GOALS + 1)
_LOG_FACT = gammaln(_K + 1)
DRAW = (_K[:, None] == _K[None, :]).astype(float)
BOTH = ((_K[:, None] > 0) & (_K[None, :] > 0)).astype(float)
SCALES = ("ks", "ko", "kh")  # multiplicative parameters, estimated as k − 1
KNOWN = (*SCALES, "a", "draw")
COMP_COLS = ("g", "h", "dS", "sO", "cO")


# ---- grids -----------------------------------------------------------------------------------


def calibrated_grid(lh, la, rho, draw=0.0, both=0.0) -> np.ndarray:
    """Dixon-Coles grids with cells × exp(draw·[i = j] + both·[i > 0 and j > 0]),
    renormalised (draw and both are scalars or one value per fixture)."""
    g = poisson_grid(lh, la, rho)
    draw, both = np.broadcast_to(draw, len(g)), np.broadcast_to(both, len(g))
    if np.any(draw != 0) or np.any(both != 0):
        g *= np.exp(draw[:, None, None] * DRAW + both[:, None, None] * BOTH)
        g /= g.sum(axis=(1, 2), keepdims=True)
    return g


def _pmf(lam: np.ndarray) -> np.ndarray:
    """grid.capped_pmf, vectorised in numpy (no scipy call per evaluation)."""
    p = np.exp(_K * np.log(lam)[:, None] - lam[:, None] - _LOG_FACT)
    p[:, -1] = np.clip(1 - p[:, :-1].sum(axis=1), 0.0, None)
    return p


def score_loglik(lh, la, rho, hg, ag, draw=0.0, both=0.0) -> np.ndarray:
    """log P(observed score) under `calibrated_grid`, computed in numpy for speed."""
    lh, la = np.asarray(lh, float), np.asarray(la, float)
    rho = np.clip(
        np.broadcast_to(rho, lh.shape), np.maximum(-1 / lh, -1 / la), np.minimum(1 / (lh * la), 1)
    )
    g = _pmf(lh)[:, :, None] * _pmf(la)[:, None, :]
    g[:, 0, 0] *= 1 - lh * la * rho
    g[:, 0, 1] *= 1 + lh * rho
    g[:, 1, 0] *= 1 + la * rho
    g[:, 1, 1] *= 1 - rho
    np.clip(g, 0.0, None, out=g)
    draw, both = np.broadcast_to(draw, lh.shape), np.broadcast_to(both, lh.shape)
    g *= np.exp(draw[:, None, None] * DRAW + both[:, None, None] * BOTH)
    hg, ag = np.minimum(hg, MAX_GOALS), np.minimum(ag, MAX_GOALS)
    cell = g[np.arange(len(lh)), hg, ag] / g.sum(axis=(1, 2))
    return np.log(np.clip(cell, 1e-300, None))


# ---- Dixon-Coles rating components -----------------------------------------------------------


def components(m: DixonColes, frame: pd.DataFrame) -> pd.DataFrame:
    """Per fixture: intercept g, home advantage h, strength difference dS and openness sum sO,
    so that m.rates(frame) == exp(g + h + (dS + sO)/2), exp(g + (−dS + sO)/2). Works for any
    model storing the core coefficient layout (att, def, c, c_tier2, ...)."""
    T, x = m.T, m.coef
    att, dfn, c, ct2 = x[:T], x[T : 2 * T], x[2 * T], x[2 * T + 1]
    ih = np.array([m.team_idx.get(t, -1) for t in frame.home_team], dtype=int)
    ia = np.array([m.team_idx.get(t, -1) for t in frame.away_team], dtype=int)

    def pick(arr, i):
        return np.where(i >= 0, arr[np.maximum(i, 0)], 0.0)

    s, o = att + dfn, att - dfn
    mean_home = float(np.mean(list(m.home_by_div.values())))
    return pd.DataFrame(
        {
            "division": frame.division.to_numpy(),
            "g": c + ct2 * frame.division.isin(TIER2).to_numpy().astype(float),
            "h": [m.home_by_div.get(d, mean_home) for d in frame.division],
            "dS": pick(s, ih) - pick(s, ia),
            "sO": pick(o, ih) + pick(o, ia),
        },
        index=frame.index,
    )


def openness_centres(m: DixonColes, history: pd.DataFrame, day: pd.Timestamp, days: int) -> dict:
    """Mean openness sum of each division's matches in the `days` before `day`, under m."""
    recent = history[history["date"] >= day - pd.Timedelta(days=days)]
    if recent.empty:
        return {}
    return components(m, recent).groupby("division")["sO"].mean().to_dict()


def _with_centres(comp: pd.DataFrame, centres: dict, suffix: str = "") -> pd.DataFrame:
    comp[f"cO{suffix}"] = [centres.get(d, 0.0) for d in comp.division]
    return comp


def calibrated_rates(comp: pd.DataFrame, p: dict, adj: dict | None = None):
    """Rates after the recency blend (a), the openness (ko), strength (ks) and home (kh)
    scales, then the per-division level and split in `adj`."""
    cols = {k: comp[k].to_numpy(float) for k in COMP_COLS}
    a = p.get("a", 0.0)
    if a != 0.0:
        for k in COMP_COLS:
            cols[k] = (1 - a) * cols[k] + a * comp[f"{k}_f"].to_numpy(float)
    ks, ko, kh = p.get("ks", 1.0), p.get("ko", 1.0), p.get("kh", 1.0)
    dS = ks * cols["dS"]
    sO = cols["cO"] + ko * (cols["sO"] - cols["cO"])
    eh = cols["g"] + kh * cols["h"] + (dS + sO) / 2
    ea = cols["g"] + (-dS + sO) / 2
    if adj:
        lev = np.array([adj.get(d, (0.0, 0.0)) for d in comp.division]).reshape(-1, 2)
        eh, ea = eh + lev[:, 0] + lev[:, 1], ea + lev[:, 0] - lev[:, 1]
    return np.exp(eh), np.exp(ea)


def _base_tilt(m) -> tuple[float, float]:
    """A grid tilt the base model applies itself (dc_tuning: exp(both·[both score] +
    draw·[draw])), as (draw, both)."""
    if getattr(m, "tilt", None):
        both, draw = m.tilt_
        return float(draw), float(both)
    return 0.0, 0.0


def _offset_newton(x, lam, w, sd: float, iters: int = 30) -> float:
    """Weighted Poisson MLE of a log multiplier on `lam`, with a N(0, sd²) prior."""
    sx, sl = float((w * x).sum()), float((w * lam).sum())
    b = 0.0
    for _ in range(iters):
        e = np.exp(b) * sl
        step = (sx - e - b / sd**2) / (e + 1 / sd**2)
        b += step
        if abs(step) < 1e-12:
            break
    return b


def _uk_day(cutoff: pd.Timestamp) -> pd.Timestamp:
    return cutoff.tz_convert("Europe/London").tz_localize(None).normalize()


# ---- the model -------------------------------------------------------------------------------


class GoalCalibratedDC:
    """Dixon-Coles with out-of-sample grid calibration (see the module docstring).

    base: keyword arguments of the core DixonColes; tuned: keyword arguments of the dc_tuning
    family's DCTuned instead (its grid tilt is kept); fast_xi: decay of the second fit for the
    recency blend (core base only); calib: calibration parameters among ks, ko, kh, a, draw;
    level / split: (half-life days, prior sd) of the per-division level and home/away split.
    """

    markets = ALL_MARKETS

    def __init__(
        self,
        name: str = "goal_calibration",
        base: dict | None = None,
        tuned: dict | None = None,
        fast_xi: float | None = None,
        calib: tuple[str, ...] = ("ko",),
        cal_half_life: float = 365.0,
        cal_sd: float = 0.3,
        blend_sd: float = 1.0,
        tier2_weight: float = 1.0,
        level: tuple[float, float] | None = (21.0, 0.06),
        split: tuple[float, float] | None = None,
        anchor_days: int = 690,
        window: int = 364,
        refit_days: int = 28,
        centre_days: int = 365,
        min_rows: int = 60,
    ):
        unknown = set(calib) - set(KNOWN)
        if unknown:
            raise ValueError(f"unknown calibration parameters {sorted(unknown)}")
        if "a" in calib and fast_xi is None:
            raise ValueError("the recency blend 'a' needs fast_xi")
        if fast_xi is not None and tuned is not None:
            raise ValueError("the recency blend is implemented for the core base only")
        self.name = name
        self.base, self.tuned, self.fast_xi = dict(base or {}), tuned, fast_xi
        self.calib = tuple(calib)
        self.cal_half_life, self.cal_sd, self.blend_sd = cal_half_life, cal_sd, blend_sd
        self.tier2_weight = tier2_weight
        self.level, self.split = level, split
        self.anchor_days, self.window, self.refit_days = anchor_days, window, refit_days
        self.centre_days, self.min_rows = centre_days, min_rows
        self.main = self._make_base()
        self.main_fast = self._make_base(fast=True) if fast_xi is not None else None
        self._anchor_models = (self._make_base(), self._make_base(fast=True))
        self._anchors: dict[pd.Timestamp, dict | None] = {}
        self._rows: dict[pd.Timestamp, pd.DataFrame | None] = {}  # complete anchor weeks
        self._cal: tuple[pd.Timestamp, dict] | None = None
        self.cal_: dict = {}
        self.adj_: dict[str, tuple[float, float]] = {}
        self.centres_: tuple[dict, dict] = ({}, {})

    def _make_base(self, fast: bool = False):
        if self.tuned is not None:
            from footy.modeling.lab.dc_tuning import DCTuned

            return DCTuned(**self.tuned)
        if fast:
            return DixonColes(**(self.base | {"xi": self.fast_xi or 0.0}))
        return DixonColes(**self.base)

    def params(self) -> dict:
        return {
            "base": self.base,
            "tuned": self.tuned,
            "fast_xi": self.fast_xi,
            "calib": list(self.calib),
            "cal_half_life": self.cal_half_life,
            "cal_sd": self.cal_sd,
            "blend_sd": self.blend_sd,
            "tier2_weight": self.tier2_weight,
            "level": self.level,
            "split": self.split,
            "anchor_days": self.anchor_days,
            "window": self.window,
            "refit_days": self.refit_days,
            "centre_days": self.centre_days,
            "min_rows": self.min_rows,
        }

    # ---- out-of-sample forecasts ---------------------------------------------------------

    def _anchor(self, history: pd.DataFrame, a: pd.Timestamp) -> dict | None:
        if a in self._anchors:
            return self._anchors[a]
        h = history[
            (history["date"] < a) & (history["date"] >= a - pd.Timedelta(days=self.anchor_days))
        ]
        entry = None
        if len(h) >= self.min_rows and h.home_team.nunique() >= 4:
            cut = a.tz_localize("Europe/London").tz_convert("UTC")
            slow, fast = self._anchor_models
            slow.fit(h, cut)
            entry = {"slow": copy.copy(slow)}
            entry["c_slow"] = openness_centres(entry["slow"], h, a, self.centre_days)
            if self.fast_xi is not None:
                fast.fit(h, cut)
                entry["fast"] = copy.copy(fast)
                entry["c_fast"] = openness_centres(entry["fast"], h, a, self.centre_days)
        self._anchors[a] = entry
        return entry

    def _frame(self, slow, fast, c_slow: dict, c_fast: dict, frame: pd.DataFrame):
        comp = _with_centres(components(slow, frame), c_slow)
        if fast is not None:
            f = _with_centres(components(fast, frame), c_fast)
            for k in COMP_COLS:
                comp[f"{k}_f"] = f[k].to_numpy()
        draw, both = _base_tilt(slow)
        return comp.assign(rho=slow.rho_, tilt_draw=draw, tilt_both=both)

    def _forecasts(self, history: pd.DataFrame, start: pd.Timestamp, day: pd.Timestamp):
        """Out-of-sample forecasts (rating components) of the history matches dated in
        [start, day), each made by the latest anchor on or before its date."""
        k0, k1 = (start - EPOCH).days // 7, -(-(day - EPOCH).days // 7)
        anchors = [a for a in (EPOCH + pd.Timedelta(days=7 * k) for k in range(k0, k1)) if a < day]
        keep = set(anchors)
        for a in [a for a in self._anchors if a not in keep]:
            del self._anchors[a]
            self._rows.pop(a, None)
        parts = []
        for a in anchors:
            complete = a + pd.Timedelta(days=7) <= day
            if complete and a in self._rows:
                rows = self._rows[a]
            else:
                rows, e = None, self._anchor(history, a)
                if e is not None:
                    g = history[
                        (history["date"] >= a) & (history["date"] < a + pd.Timedelta(days=7))
                    ]
                    if self._closed_doors():
                        g = g[~closed_doors_mask(g)]
                    g = g[
                        g.home_team.isin(e["slow"].team_idx) & g.away_team.isin(e["slow"].team_idx)
                    ]
                    if len(g):
                        rows = self._frame(
                            e["slow"], e.get("fast"), e["c_slow"], e.get("c_fast", {}), g
                        ).assign(
                            date=g["date"].to_numpy(),
                            hg=g.home_goals.to_numpy(int),
                            ag=g.away_goals.to_numpy(int),
                        )
                if complete:
                    self._rows[a] = rows
            if rows is not None:
                parts.append(rows)
        if not parts:
            return None
        out = pd.concat(parts, ignore_index=True)
        return out[out["date"] >= start].reset_index(drop=True)

    def _closed_doors(self) -> bool:
        return bool((self.tuned or self.base).get("closed_doors", True))

    # ---- fitting -------------------------------------------------------------------------

    def fit(self, history: pd.DataFrame, cutoff) -> None:
        self.main.fit(history, cutoff)
        day = _uk_day(cutoff)
        c_fast = {}
        if self.main_fast is not None:
            self.main_fast.fit(history, cutoff)
            c_fast = openness_centres(self.main_fast, history, day, self.centre_days)
        self.centres_ = (openness_centres(self.main, history, day, self.centre_days), c_fast)
        refit_day = EPOCH + pd.Timedelta(
            days=self.refit_days * ((day - EPOCH).days // self.refit_days)
        )
        oos = None
        if self.calib or self.level or self.split:
            start = refit_day - pd.Timedelta(days=self.window)
            oos = self._forecasts(history, start, day)
        if self.calib and (self._cal is None or self._cal[0] != refit_day):
            past = None if oos is None else oos[oos["date"] < refit_day]
            self._cal = (refit_day, self._fit_calibration(past, refit_day))
        self.cal_ = self._cal[1] if self.calib else {}
        self.adj_ = {}
        if oos is not None and (self.level or self.split):
            recent = oos[oos["date"] >= day - pd.Timedelta(days=self.window)]
            self.adj_ = self._fit_levels(recent.reset_index(drop=True), day)

    def _unpack(self, th) -> dict:
        return {n: (1.0 + v if n in SCALES else v) for n, v in zip(self.calib, th, strict=True)}

    def _fit_calibration(self, oos: pd.DataFrame | None, day: pd.Timestamp) -> dict:
        if oos is None or len(oos) < self.min_rows:
            return {}
        age = (day - oos["date"]).dt.days.to_numpy(float)
        w = 0.5 ** (age / self.cal_half_life)
        w = np.where(oos.division.isin(TIER2), w * self.tier2_weight, w)
        hg, ag, rho = oos.hg.to_numpy(), oos.ag.to_numpy(), oos.rho.to_numpy(float)
        t_draw, t_both = oos.tilt_draw.to_numpy(float), oos.tilt_both.to_numpy(float)
        sd = np.array([self.blend_sd if n == "a" else self.cal_sd for n in self.calib])
        wsum = w.sum()

        def nll(th):
            p = self._unpack(th)
            lh, la = calibrated_rates(oos, p)
            ll = score_loglik(lh, la, rho, hg, ag, t_draw + p.get("draw", 0.0), t_both)
            return -(w @ ll) / wsum + 0.5 * ((th / sd) ** 2).sum() / wsum

        res = minimize(nll, np.zeros(len(self.calib)), method="L-BFGS-B")
        return self._unpack(res.x)

    def _fit_levels(self, oos: pd.DataFrame, day: pd.Timestamp) -> dict:
        lh, la = calibrated_rates(oos, self.cal_)
        age = (day - oos["date"]).dt.days.to_numpy(float)
        hg, ag = oos.hg.to_numpy(float), oos.ag.to_numpy(float)
        out = {}
        for d in sorted(oos.division.unique()):
            m = (oos.division == d).to_numpy()
            lev = spl = 0.0
            if self.level:
                w = 0.5 ** (age[m] / self.level[0])
                lev = _offset_newton(hg[m] + ag[m], lh[m] + la[m], w, self.level[1])
            if self.split:
                w = 0.5 ** (age[m] / self.split[0])
                bh = _offset_newton(hg[m], lh[m] * np.exp(lev), w, self.split[1])
                ba = _offset_newton(ag[m], la[m] * np.exp(lev), w, self.split[1])
                spl = (bh - ba) / 2
            out[d] = (lev, spl)
        return out

    # ---- prediction ----------------------------------------------------------------------

    def rates(self, fixtures: pd.DataFrame) -> tuple[np.ndarray, np.ndarray]:
        comp = self._frame(self.main, self.main_fast, *self.centres_, fixtures)
        return calibrated_rates(comp, self.cal_, self.adj_)

    def predict(self, fixtures: pd.DataFrame) -> MarketProbs:
        lh, la = self.rates(fixtures)
        draw, both = _base_tilt(self.main)
        draw += self.cal_.get("draw", 0.0)
        g = calibrated_grid(lh, la, self.main.rho_, draw, both)
        if draw != 0.0 or both != 0.0:
            # A tilt moves mass between cells and so changes the expected goals: report those of
            # the grid actually returned (published as expected goals), so they agree with its
            # probabilities. Measured on the capped grid, the shift is exactly zero without a tilt.
            shift = g - poisson_grid(lh, la, self.main.rho_)
            lh = lh + (shift * GOALS[None, :, None]).sum(axis=(1, 2))
            la = la + (shift * GOALS[None, None, :]).sum(axis=(1, 2))
        return markets_from_grid(g, lh, la)


class CalibratedMix:
    """Equal-weight mixture of the score grids of several calibrated models (a mixture of
    score distributions is itself one, so every market stays coherent)."""

    markets = ALL_MARKETS

    def __init__(self, name: str, members: list[GoalCalibratedDC], weights=None):
        self.name, self.members = name, members
        self.weights = np.full(len(members), 1 / len(members)) if weights is None else weights

    def params(self) -> dict:
        return {"members": [m.params() for m in self.members], "weights": list(self.weights)}

    def fit(self, history: pd.DataFrame, cutoff) -> None:
        for m in self.members:
            m.fit(history, cutoff)

    def predict(self, fixtures: pd.DataFrame) -> MarketProbs:
        mps = [m.predict(fixtures) for m in self.members]
        w = np.asarray(self.weights, float)
        g = mix_grids([mp.grid for mp in mps], w)
        lh = sum(wi * mp.lam_h for wi, mp in zip(w, mps, strict=True))
        la = sum(wi * mp.lam_a for wi, mp in zip(w, mps, strict=True))
        return markets_from_grid(g, lh, la)


# Selected on the inner seasons 2021/22 and 2022/23 (five leagues); see the module docstring.
LEVEL = (21.0, 0.06)
TUNED_TILT = {
    "reg_strength": 1.0,
    "reg_open": 50.0,
    "xi_glob": 0.004,
    "xi_strength": 0.0028,
    "tilt": "btts",
}  # = dc_tuning's dc_tuning_tilt configuration
CONFIGS: dict[str, dict] = {
    # (a) alone: the league scoring level
    "goal_calibration_level": {"calib": (), "level": LEVEL},
    # openness scale + level
    "goal_calibration_open_level": {"calib": ("ko",), "level": LEVEL},
    # + recency blend with a faster-decay fit
    "goal_calibration_recency": {"calib": ("ko", "a"), "fast_xi": 0.0045, "level": LEVEL},
    # the level on top of the dc_tuning family's tuned Dixon-Coles (BTTS tilt kept)
    "goal_calibration_tuned_level": {"tuned": TUNED_TILT, "calib": (), "level": LEVEL},
}


def _factory(name: str, cfg: dict):
    def make() -> GoalCalibratedDC:
        return GoalCalibratedDC(name=name, **cfg)

    return make


CANDIDATES = {name: _factory(name, cfg) for name, cfg in CONFIGS.items()}
# equal mixture of the tuned-base and recency-blend calibrations
CANDIDATES["goal_calibration_mix"] = lambda: CalibratedMix(
    "goal_calibration_mix",
    [
        GoalCalibratedDC(name="tuned_level", **CONFIGS["goal_calibration_tuned_level"]),
        GoalCalibratedDC(name="recency", **CONFIGS["goal_calibration_recency"]),
    ],
)
