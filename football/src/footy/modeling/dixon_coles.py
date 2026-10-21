"""Time-weighted Dixon-Coles, fitted jointly on a league and its second tier.

    log E[home goals] = c + c_tier2·[tier 2] + h_div·(not closed doors) + h_cd·(closed doors)
                        + attack_home − defence_away
    log E[away goals] = c + c_tier2·[tier 2] + attack_away − defence_home

with the Dixon-Coles low-score correction (rho) on the joint distribution. Team parameters are
shared across the two divisions, so promoted teams arrive with ratings earned in the second
tier, and teams that moved between divisions tie the two scales together. Matches are
weighted by exp(−xi · days before the cutoff); second-tier matches can be down-weighted.
A small ridge penalty shrinks teams with little data towards average and fixes the scale.

Options explored in the model lab:
- `shots_weight` > 0 replaces part of each goal count with a shots-on-target estimate of
  goals (SoT × the league's goals-per-SoT rate): a cheap stand-in for xG.
- `rho=False` with xi = 0 gives the independent Poisson baseline.
"""

import numpy as np
import pandas as pd
from scipy.optimize import minimize

from footy.modeling.base import ALL_MARKETS
from footy.modeling.closed_doors import closed_doors_mask
from footy.modeling.data import SECOND_TIER
from footy.modeling.grid import MarketProbs, markets_from_grid, poisson_grid

TIER1 = set(SECOND_TIER)
TIER2 = set(SECOND_TIER.values())


class DixonColes:
    markets = ALL_MARKETS

    def __init__(
        self,
        xi: float = 0.0019,
        rho: bool = True,
        reg: float = 2.0,
        tier2_weight: float = 0.5,
        shots_weight: float = 0.0,
        closed_doors: bool = True,
        name: str | None = None,
    ):
        self.xi, self.use_rho, self.reg = xi, rho, reg
        self.tier2_weight, self.shots_weight, self.closed_doors = (
            tier2_weight,
            shots_weight,
            closed_doors,
        )
        self.name = name or (
            f"dixon_coles_xi{xi:g}_sw{shots_weight:g}" if rho else f"poisson_xi{xi:g}"
        )
        self._x: dict[str, float] = {}  # warm start, keyed by parameter name
        self.team_idx: dict[str, int] = {}
        self.coef: np.ndarray | None = None
        self.home_by_div: dict[str, float] = {}

    def params(self) -> dict:
        return {
            "xi": self.xi,
            "rho": self.use_rho,
            "reg": self.reg,
            "tier2_weight": self.tier2_weight,
            "shots_weight": self.shots_weight,
            "closed_doors": self.closed_doors,
        }

    # ---- fitting -------------------------------------------------------------------------

    def fit(self, history: pd.DataFrame, cutoff) -> None:
        h = history[history.division.isin(TIER1 | TIER2)]
        teams = sorted(set(h.home_team) | set(h.away_team))
        self.team_idx = {t: i for i, t in enumerate(teams)}
        T = len(teams)
        divs = sorted(h.division.unique())
        self.div_idx = {d: i for i, d in enumerate(divs)}
        D = len(divs)

        hi = h.home_team.map(self.team_idx).to_numpy()
        ai = h.away_team.map(self.team_idx).to_numpy()
        di = h.division.map(self.div_idx).to_numpy()
        tier2 = h.division.isin(TIER2).to_numpy().astype(float)
        cd = closed_doors_mask(h) if self.closed_doors else np.zeros(len(h), bool)
        days = (cutoff.tz_convert("UTC").tz_localize(None) - h["date"]).dt.days.to_numpy()
        w = np.exp(-self.xi * days) * np.where(tier2 > 0, self.tier2_weight, 1.0)

        yh = h.home_goals.to_numpy(float)
        ya = h.away_goals.to_numpy(float)
        if self.shots_weight > 0:
            yh, ya = self._blend_shots(h, yh, ya)
        gh, ga = h.home_goals.to_numpy(), h.away_goals.to_numpy()

        # x = [att(T), def(T), c, c_tier2, home(D), home_cd, rho]
        n_par = 2 * T + 2 + D + 2
        names = (
            [f"att:{t}" for t in teams]
            + [f"def:{t}" for t in teams]
            + ["c", "c_tier2"]
            + [f"home:{d}" for d in divs]
            + ["home_cd", "rho"]
        )
        x0 = np.array([self._x.get(n, 0.0) for n in names])
        if "c" not in self._x:
            x0[2 * T] = np.log(max(np.average(yh, weights=w), 0.1))
            x0[2 * T + 2 : 2 * T + 2 + D] = 0.25
            x0[-1] = -0.05
        rho_idx, hcd_idx = n_par - 1, n_par - 2

        def f(x):
            att, dfn = x[:T], x[T : 2 * T]
            c, ct2 = x[2 * T], x[2 * T + 1]
            home = x[2 * T + 2 : 2 * T + 2 + D]
            hcd, rho = x[hcd_idx], (x[rho_idx] if self.use_rho else 0.0)
            hadv = np.where(cd, hcd, home[di])
            eta_h = c + ct2 * tier2 + hadv + att[hi] - dfn[ai]
            eta_a = c + ct2 * tier2 + att[ai] - dfn[hi]
            lh, la = np.exp(eta_h), np.exp(eta_a)
            ll = w * (yh * eta_h - lh + ya * eta_a - la)
            gh_ = w * (yh - lh)  # d ll / d eta_h
            ga_ = w * (ya - la)
            g_rho = 0.0
            if self.use_rho:
                tau, dt_rho, dt_eh, dt_ea = _tau_terms(gh, ga, lh, la, rho)
                ll = ll + w * np.log(tau)
                gh_ = gh_ + w * dt_eh
                ga_ = ga_ + w * dt_ea
                g_rho = (w * dt_rho).sum()
            obj = -ll.sum() + 0.5 * self.reg * (att @ att + dfn @ dfn)
            g = np.zeros_like(x)
            g[:T] = -(np.bincount(hi, gh_, T) + np.bincount(ai, ga_, T)) + self.reg * att
            g[T : 2 * T] = (np.bincount(ai, gh_, T) + np.bincount(hi, ga_, T)) + self.reg * dfn
            g[2 * T] = -(gh_.sum() + ga_.sum())
            g[2 * T + 1] = -((gh_ + ga_) * tier2).sum()
            g[2 * T + 2 : 2 * T + 2 + D] = -np.bincount(di[~cd], gh_[~cd], D)
            g[hcd_idx] = -gh_[cd].sum()
            g[rho_idx] = -g_rho if self.use_rho else 0.0
            return obj, g

        bounds = [(None, None)] * n_par
        bounds[rho_idx] = (-0.25, 0.25) if self.use_rho else (0.0, 0.0)
        if not cd.any():
            bounds[hcd_idx] = (0.0, 0.0)
        res = minimize(f, x0, jac=True, method="L-BFGS-B", bounds=bounds, options={"maxiter": 500})
        self.coef = res.x
        self._x = dict(zip(names, res.x, strict=True))
        self.T, self.D = T, D
        self.home_by_div = {d: float(res.x[2 * T + 2 + i]) for d, i in self.div_idx.items()}
        self.rho_ = float(res.x[rho_idx]) if self.use_rho else 0.0
        self.converged_ = bool(res.success)

    def _blend_shots(self, h: pd.DataFrame, yh: np.ndarray, ya: np.ndarray):
        """Mix each goal count with SoT × (goals per SoT in that division's training data)."""
        sh, sa = h.home_sot.to_numpy(float), h.away_sot.to_numpy(float)
        ok = ~(np.isnan(sh) | np.isnan(sa))
        conv = {}
        for d, g in h[ok].groupby("division"):
            conv[d] = (g.home_goals.sum() + g.away_goals.sum()) / max(
                g.home_sot.sum() + g.away_sot.sum(), 1
            )
        rate = h.division.map(conv).to_numpy(float)
        s = self.shots_weight
        bh = np.where(ok, (1 - s) * yh + s * sh * rate, yh)
        ba = np.where(ok, (1 - s) * ya + s * sa * rate, ya)
        return bh, ba

    # ---- prediction ----------------------------------------------------------------------

    def rates(self, fixtures: pd.DataFrame) -> tuple[np.ndarray, np.ndarray]:
        T, x = self.T, self.coef
        att, dfn, c, ct2 = x[:T], x[T : 2 * T], x[2 * T], x[2 * T + 1]

        def team(col, arr):
            return np.array(
                [arr[self.team_idx[t]] if t in self.team_idx else 0.0 for t in fixtures[col]]
            )

        tier2 = fixtures.division.isin(TIER2).to_numpy().astype(float)
        home = np.array(
            [
                self.home_by_div.get(d, np.mean(list(self.home_by_div.values())))
                for d in fixtures.division
            ]
        )
        eta_h = c + ct2 * tier2 + home + team("home_team", att) - team("away_team", dfn)
        eta_a = c + ct2 * tier2 + team("away_team", att) - team("home_team", dfn)
        return np.exp(eta_h), np.exp(eta_a)

    def predict(self, fixtures: pd.DataFrame) -> MarketProbs:
        lh, la = self.rates(fixtures)
        return markets_from_grid(poisson_grid(lh, la, self.rho_), lh, la)


def _tau_terms(gh, ga, lh, la, rho):
    """Dixon-Coles tau and its derivatives w.r.t. rho and the two log-rates."""
    n = len(gh)
    tau = np.ones(n)
    d_rho, d_eh, d_ea = np.zeros(n), np.zeros(n), np.zeros(n)
    m00 = (gh == 0) & (ga == 0)
    m01 = (gh == 0) & (ga == 1)
    m10 = (gh == 1) & (ga == 0)
    m11 = (gh == 1) & (ga == 1)
    t = 1 - lh[m00] * la[m00] * rho
    tau[m00] = t
    d_rho[m00] = -lh[m00] * la[m00] / t
    d_eh[m00] = -lh[m00] * la[m00] * rho / t
    d_ea[m00] = d_eh[m00]
    t = 1 + lh[m01] * rho
    tau[m01] = t
    d_rho[m01] = lh[m01] / t
    d_eh[m01] = lh[m01] * rho / t
    t = 1 + la[m10] * rho
    tau[m10] = t
    d_rho[m10] = la[m10] / t
    d_ea[m10] = la[m10] * rho / t
    tau[m11] = 1 - rho
    d_rho[m11] = -1 / (1 - rho)
    tau = np.clip(tau, 1e-10, None)
    return tau, d_rho, d_eh, d_ea
