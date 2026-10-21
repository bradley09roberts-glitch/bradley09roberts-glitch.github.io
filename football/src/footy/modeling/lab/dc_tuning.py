"""Tuned and extended time-weighted Dixon-Coles (model-lab family `dc_tuning`).

`DCTuned` keeps the core model of footy.modeling.dixon_coles (shared team ratings across a
league and its second tier, per-division home advantage, a closed-doors home term, ridge
shrinkage, the Dixon-Coles low-score correction) and adds options explored in the lab:

- separate time decay for attack, defence and the global terms (`xi_att`, `xi_def`,
  `xi_glob`). The attack block is fitted with attack weights, the defence block with defence
  weights and the global block (intercepts, home advantage, rho) with global weights; the
  fitted parameters solve those weighted score equations jointly. With all three equal it is
  the ordinary weighted maximum likelihood fit of the core model;
- `red_weight` < 1 down-weights matches in which anyone was sent off;
- `red_adjust` adds two global covariates for red cards (effect on the carded side's goals and
  on its opponent's goals), so a result distorted by a red card moves the ratings less. At
  prediction time nobody has been sent off;
- `team_home_reg` > 0 adds a team-specific home-advantage deviation, shrunk towards the
  division's home advantage by a ridge penalty of that size;
- `season_discount` < 1 multiplies the weight of a match by that factor for every season
  boundary between it and the cutoff (squads change over the summer);
- `goal_cap` caps the goal counts used in the Poisson part of the likelihood (blow-outs
  move ratings less).

The fit is a damped Newton iteration on sparse design matrices (the Hessian of the Poisson
part, plus the ridge; the low-score correction enters the gradient exactly), warm-started
from the previous batch. Every market comes from one Dixon-Coles score grid.
"""

import numpy as np
import pandas as pd
from scipy import sparse

from footy.modeling.closed_doors import closed_doors_mask
from footy.modeling.data import season_of
from footy.modeling.dixon_coles import TIER1, TIER2, DixonColes, _tau_terms

RHO_LIMIT = 0.25
TINY = 1e-6  # ridge on the global terms so the Newton system stays positive definite


class DCTuned(DixonColes):
    def __init__(
        self,
        xi: float = 0.0019,
        reg: float = 2.0,
        tier2_weight: float = 0.5,
        closed_doors: bool = True,
        shots_weight: float = 0.0,
        xi_att: float | None = None,
        xi_def: float | None = None,
        xi_glob: float | None = None,
        red_weight: float = 1.0,
        red_adjust: bool = False,
        team_home_reg: float = 0.0,
        season_discount: float = 1.0,
        goal_cap: float | None = None,
        name: str | None = None,
    ):
        super().__init__(
            xi=xi,
            rho=True,
            reg=reg,
            tier2_weight=tier2_weight,
            shots_weight=shots_weight,
            closed_doors=closed_doors,
            name=name or "dc_tuning",
        )
        self.xi_att = xi if xi_att is None else xi_att
        self.xi_def = xi if xi_def is None else xi_def
        self.xi_glob = xi if xi_glob is None else xi_glob
        self.red_weight = red_weight
        self.red_adjust = red_adjust
        self.team_home_reg = team_home_reg
        self.season_discount = season_discount
        self.goal_cap = goal_cap
        self.team_home: np.ndarray | None = None

    def params(self) -> dict:
        return super().params() | {
            "xi_att": self.xi_att,
            "xi_def": self.xi_def,
            "xi_glob": self.xi_glob,
            "red_weight": self.red_weight,
            "red_adjust": self.red_adjust,
            "team_home_reg": self.team_home_reg,
            "season_discount": self.season_discount,
            "goal_cap": self.goal_cap,
        }

    # ---- fitting -------------------------------------------------------------------------

    def fit(self, history: pd.DataFrame, cutoff) -> None:
        h = history[history.division.isin(TIER1 | TIER2)]
        teams = sorted(set(h.home_team) | set(h.away_team))
        self.team_idx = {t: i for i, t in enumerate(teams)}
        divs = sorted(h.division.unique())
        self.div_idx = {d: i for i, d in enumerate(divs)}
        T, D, n = len(teams), len(divs), len(h)

        hi = h.home_team.map(self.team_idx).to_numpy()
        ai = h.away_team.map(self.team_idx).to_numpy()
        di = h.division.map(self.div_idx).to_numpy()
        tier2 = h.division.isin(TIER2).to_numpy().astype(float)
        cd = closed_doors_mask(h) if self.closed_doors else np.zeros(n, bool)
        rh = np.nan_to_num(h.home_red.to_numpy(float)) if "home_red" in h else np.zeros(n)
        ra = np.nan_to_num(h.away_red.to_numpy(float)) if "away_red" in h else np.zeros(n)

        cut_day = cutoff.tz_convert("UTC").tz_localize(None)
        days = (cut_day - h["date"]).dt.days.to_numpy().astype(float)
        base = np.where(tier2 > 0, self.tier2_weight, 1.0)
        if self.red_weight != 1.0:
            base = base * np.where(rh + ra > 0, self.red_weight, 1.0)
        if self.season_discount != 1.0:
            cut_season = int(season_of(cut_day.date())[:2])
            back = (cut_season - h.season.str[:2].astype(int).to_numpy()) % 100
            base = base * self.season_discount ** back.astype(float)
        weights = {
            "att": np.exp(-self.xi_att * days) * base,
            "def": np.exp(-self.xi_def * days) * base,
            "glob": np.exp(-self.xi_glob * days) * base,
        }

        yh = h.home_goals.to_numpy(float)
        ya = h.away_goals.to_numpy(float)
        if self.shots_weight > 0:
            yh, ya = self._blend_shots(h, yh, ya)
        if self.goal_cap is not None:
            yh, ya = np.minimum(yh, self.goal_cap), np.minimum(ya, self.goal_cap)
        gh, ga = h.home_goals.to_numpy(), h.away_goals.to_numpy()

        # ---- parameter layout: att(T), def(T), c, c_tier2, home(D), home_cd, [reds], [hteam]
        names = [f"att:{t}" for t in teams] + [f"def:{t}" for t in teams]
        names += ["c", "c_tier2"] + [f"home:{d}" for d in divs] + ["home_cd"]
        i_c, i_t2, i_home, i_cd = 2 * T, 2 * T + 1, 2 * T + 2, 2 * T + 2 + D
        P = i_cd + 1
        i_red = None
        if self.red_adjust:
            i_red = P
            names += ["red_own", "red_opp"]
            P += 2
        i_ht = None
        if self.team_home_reg > 0:
            i_ht = P
            names += [f"hteam:{t}" for t in teams]
            P += T

        r = np.arange(n)
        rows_h = [r, r, r, r, r, r]
        cols_h = [hi, T + ai, np.full(n, i_c), np.full(n, i_t2), i_home + di, np.full(n, i_cd)]
        vals_h = [np.ones(n), -np.ones(n), np.ones(n), tier2, (~cd).astype(float), cd * 1.0]
        rows_a = [r, r, r, r]
        cols_a = [ai, T + hi, np.full(n, i_c), np.full(n, i_t2)]
        vals_a = [np.ones(n), -np.ones(n), np.ones(n), tier2]
        if i_red is not None:
            rows_h += [r, r]
            cols_h += [np.full(n, i_red), np.full(n, i_red + 1)]
            vals_h += [rh, ra]
            rows_a += [r, r]
            cols_a += [np.full(n, i_red), np.full(n, i_red + 1)]
            vals_a += [ra, rh]
        if i_ht is not None:
            rows_h.append(r)
            cols_h.append(i_ht + hi)
            vals_h.append(np.ones(n))

        def design(rows, cols, vals):
            m = sparse.coo_matrix(
                (np.concatenate(vals), (np.concatenate(rows), np.concatenate(cols))), shape=(n, P)
            )
            return m.tocsr()

        Xh, Xa = design(rows_h, cols_h, vals_h), design(rows_a, cols_a, vals_a)
        XhT, XaT = Xh.T.tocsr(), Xa.T.tocsr()

        pen = np.full(P, TINY)
        pen[: 2 * T] = self.reg
        if i_ht is not None:
            pen[i_ht:] = self.team_home_reg
        block = np.full(P, "glob", dtype=object)
        block[:T], block[T : 2 * T] = "att", "def"
        keys = ["att", "def", "glob"]
        distinct = not (self.xi_att == self.xi_def == self.xi_glob)
        if not distinct:
            weights = {k: weights["glob"] for k in keys}
        masks = {k: block == k for k in keys}

        x = np.array([self._x.get(nm, 0.0) for nm in names])
        rho = self._x.get("rho", -0.05)
        if "c" not in self._x:
            x[i_c] = np.log(max(np.average(yh, weights=weights["glob"]), 0.1))
            x[i_home : i_home + D] = 0.25
        if not cd.any():
            x[i_cd] = 0.0

        def state(x, rho):
            eh, ea = Xh @ x, Xa @ x
            lh, la = np.exp(eh), np.exp(ea)
            tau, dt_rho, dt_eh, dt_ea = _tau_terms(gh, ga, lh, la, rho)
            return eh, ea, lh, la, tau, dt_rho, dt_eh, dt_ea

        def grad(x, s):
            eh, ea, lh, la, tau, _, dt_eh, dt_ea = s
            sh, sa = yh - lh + dt_eh, ya - la + dt_ea
            g = np.empty(P)
            cache = {}
            for k in keys:
                wk = weights[k]
                kk = id(wk)
                if kk not in cache:
                    cache[kk] = XhT @ (wk * sh) + XaT @ (wk * sa)
                g[masks[k]] = -cache[kk][masks[k]]
            return g + pen * x

        def objective(x, s):
            eh, ea, lh, la, tau, *_ = s
            w = weights["glob"]
            ll = w * (yh * eh - lh + ya * ea - la + np.log(tau))
            return -ll.sum() + 0.5 * (pen * x * x).sum()

        def jacobian(s):
            _, _, lh, la, *_ = s
            J = np.zeros((P, P))
            cache = {}
            for k in keys:
                wk = weights[k]
                kk = id(wk)
                if kk not in cache:
                    H = XhT @ Xh.multiply((wk * lh)[:, None]) + XaT @ Xa.multiply(
                        (wk * la)[:, None]
                    )
                    cache[kk] = np.asarray(H.todense())
                J[masks[k]] = cache[kk][masks[k]]
            J[np.diag_indices(P)] += pen
            return J

        def rho_step(s, rho):
            _, _, lh, la, *_ = s
            w = weights["glob"]
            _, d_rho, _, _ = _tau_terms(gh, ga, lh, la, rho)
            d2 = _tau_d2(gh, ga, lh, la, rho)
            score, curv = (w * d_rho).sum(), (w * d2).sum()
            new = rho - score / curv if curv < 0 else rho + np.sign(score) * 0.01
            return float(np.clip(new, -RHO_LIMIT, RHO_LIMIT))

        s = state(x, rho)
        g = grad(x, s)
        merit = objective(x, s) if not distinct else float(g @ g)
        converged = False
        for _ in range(100):
            step = np.linalg.solve(jacobian(s), -g)
            step = np.clip(step, -1.0, 1.0)
            t = 1.0
            for _ in range(20):
                x_new = x + t * step
                s_new = state(x_new, rho)
                g_new = grad(x_new, s_new)
                m_new = objective(x_new, s_new) if not distinct else float(g_new @ g_new)
                if m_new <= merit + 1e-12 * abs(merit) or t < 1e-3:
                    break
                t /= 2
            x, s = x_new, s_new
            rho_new = rho_step(s, rho)
            d_rho = abs(rho_new - rho)
            rho = rho_new
            s = state(x, rho)
            g = grad(x, s)
            merit = objective(x, s) if not distinct else float(g @ g)
            if np.max(np.abs(t * step)) < 1e-7 and d_rho < 1e-8:
                converged = True
                break

        self.coef = x
        self._x = dict(zip(names, x, strict=True)) | {"rho": rho}
        self.T, self.D = T, D
        self.home_by_div = {d: float(x[i_home + i]) for d, i in self.div_idx.items()}
        self.rho_ = rho
        self.team_home = None if i_ht is None else x[i_ht : i_ht + T].copy()
        self.converged_ = converged

    # ---- prediction ----------------------------------------------------------------------

    def rates(self, fixtures: pd.DataFrame) -> tuple[np.ndarray, np.ndarray]:
        lh, la = super().rates(fixtures)
        if self.team_home is not None:
            dev = np.array(
                [
                    self.team_home[self.team_idx[t]] if t in self.team_idx else 0.0
                    for t in fixtures.home_team
                ]
            )
            lh = lh * np.exp(dev)
        return lh, la


def _tau_d2(gh, ga, lh, la, rho):
    """Second derivative of log tau with respect to rho."""
    d2 = np.zeros(len(gh))
    m00 = (gh == 0) & (ga == 0)
    m01 = (gh == 0) & (ga == 1)
    m10 = (gh == 1) & (ga == 0)
    m11 = (gh == 1) & (ga == 1)
    p = lh[m00] * la[m00]
    d2[m00] = -(p**2) / np.maximum(1 - p * rho, 1e-10) ** 2
    d2[m01] = -(lh[m01] ** 2) / np.maximum(1 + lh[m01] * rho, 1e-10) ** 2
    d2[m10] = -(la[m10] ** 2) / np.maximum(1 + la[m10] * rho, 1e-10) ** 2
    d2[m11] = -1 / (1 - rho) ** 2
    return d2


CANDIDATES: dict = {}
