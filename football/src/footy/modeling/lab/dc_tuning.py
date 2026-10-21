"""Tuned and extended time-weighted Dixon-Coles (model-lab family `dc_tuning`).

`DCTuned` keeps the core model of footy.modeling.dixon_coles (shared team ratings across a
league and its second tier, per-division home advantage, a closed-doors home term, ridge
shrinkage, the Dixon-Coles low-score correction) and adds options explored in the lab:

- team ratings re-expressed as strength s = attack + defence (decides 1X2) and openness
  o = attack − defence (decides how many goals a team's matches have), with their own ridge
  penalties `reg_strength` / `reg_open` (0.25·(reg_s·s² + reg_o·o²), which is the core
  0.5·reg·(att² + def²) when both equal reg), so totals can be shrunk much harder than 1X2;
- separate time decay for strength, openness and the global terms (`xi_strength`, `xi_open`,
  `xi_glob`): each block of parameters solves its own weighted score equations (the global
  block: intercepts, home advantage, rho). With all three equal it is the ordinary weighted
  maximum likelihood fit;
- `red_weight` < 1 down-weights matches in which anyone was sent off;
- `red_adjust` adds two global covariates for red cards (effect on the carded side's goals and
  on its opponent's goals), so a result distorted by a red card moves the ratings less. At
  prediction time nobody has been sent off;
- `team_home_reg` > 0 adds a team-specific home-advantage deviation, shrunk towards the
  division's home advantage by a ridge penalty of that size;
- `season_discount` < 1 multiplies the weight of a match by that factor for every season
  boundary between it and the cutoff (squads change over the summer);
- `xi_form` adds a short-term form rating per team (a second strength term with its own fast
  decay and ridge `reg_form`);
- `shots_strength` / `shots_open` let the strength and openness blocks use a different
  goals/shots-on-target blend from the global block (`shots_weight`), so shots can inform
  who is better without moving the scoring level;
- `tilt` ("btts", "draw" or "both") multiplies the score grid by exp(kappa·[both teams
  score] + delta·[draw]) and renormalises; kappa and delta are fitted by weighted maximum
  likelihood on the training matches given the fitted rates. Still one coherent grid;
- `goal_cap` caps the goal counts used in the Poisson part of the likelihood (blow-outs
  move ratings less);
- `closed_doors="limited"` also gives capped-crowd periods the closed-doors home term. The
  periods come from reference/closed_doors.csv when it exists, else from CROWD_CALENDAR.

The fit is a damped Newton iteration on sparse design matrices (the Hessian of the Poisson
part, plus the ridge; the low-score correction enters the gradient exactly), warm-started
from the previous batch. Every market comes from one Dixon-Coles score grid.
"""

import numpy as np
import pandas as pd
from scipy import sparse
from scipy.sparse.linalg import spsolve

from footy.modeling.closed_doors import closed_doors_mask, periods
from footy.modeling.data import season_of
from footy.modeling.dixon_coles import TIER1, TIER2, DixonColes, _tau_terms
from footy.modeling.grid import GOALS, MAX_GOALS, MarketProbs, markets_from_grid, poisson_grid

# COVID-19 crowd restrictions by division, used only when the core reference file
# (reference/closed_doors.csv) is absent. A fixed calendar of public facts, not data-derived;
# approximate to within days. "none": no spectators; "limited": capped crowds.
CROWD_CALENDAR: dict[str, list[tuple[str, str, str]]] = {
    "E0": [("none", "2020-06-17", "2021-05-17"), ("limited", "2021-05-18", "2021-05-23")],
    "E1": [("none", "2020-06-20", "2021-05-08"), ("limited", "2021-05-09", "2021-05-29")],
    "SP1": [
        ("none", "2020-06-11", "2021-05-16"),
        ("limited", "2021-05-17", "2021-05-23"),
        ("limited", "2021-08-13", "2021-09-30"),
    ],
    "SP2": [("none", "2020-06-10", "2021-05-30"), ("limited", "2021-08-13", "2021-09-30")],
    "I1": [
        ("none", "2020-06-20", "2020-09-18"),
        ("limited", "2020-09-19", "2020-10-24"),
        ("none", "2020-10-25", "2021-05-23"),
        ("limited", "2022-01-06", "2022-01-31"),
    ],
    "I2": [
        ("none", "2020-06-20", "2020-09-25"),
        ("limited", "2020-09-26", "2020-10-24"),
        ("none", "2020-10-25", "2021-05-30"),
        ("limited", "2022-01-06", "2022-01-31"),
    ],
    "D1": [
        ("none", "2020-05-16", "2020-09-17"),
        ("limited", "2020-09-18", "2020-10-30"),
        ("none", "2020-10-31", "2021-05-22"),
        ("limited", "2021-11-27", "2021-12-27"),
        ("none", "2021-12-28", "2022-02-13"),
        ("limited", "2022-02-14", "2022-03-19"),
    ],
    "D2": [
        ("none", "2020-05-16", "2020-09-17"),
        ("limited", "2020-09-18", "2020-10-30"),
        ("none", "2020-10-31", "2021-05-23"),
        ("limited", "2021-11-27", "2021-12-27"),
        ("none", "2021-12-28", "2022-02-13"),
        ("limited", "2022-02-14", "2022-03-19"),
    ],
    "F1": [
        ("limited", "2020-08-21", "2020-10-29"),
        ("none", "2020-10-30", "2021-05-23"),
        ("limited", "2022-01-03", "2022-02-01"),
    ],
    "F2": [("limited", "2020-08-22", "2020-10-29"), ("none", "2020-10-30", "2021-05-22")],
}
RHO_LIMIT = 0.25
TINY = 1e-6  # ridge on the global terms so the Newton system stays positive definite


class DCTuned(DixonColes):
    def __init__(
        self,
        xi: float = 0.0019,
        reg: float = 2.0,
        tier2_weight: float = 0.5,
        closed_doors: bool | str = True,
        shots_weight: float = 0.0,
        reg_strength: float | None = None,
        reg_open: float | None = None,
        xi_strength: float | None = None,
        xi_open: float | None = None,
        xi_glob: float | None = None,
        red_weight: float = 1.0,
        red_adjust: bool = False,
        team_home_reg: float = 0.0,
        season_discount: float = 1.0,
        goal_cap: float | None = None,
        shots_strength: float | None = None,
        shots_open: float | None = None,
        xi_form: float | None = None,
        reg_form: float = 20.0,
        tilt: str | None = None,
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
        self.reg_strength = reg if reg_strength is None else reg_strength
        self.reg_open = reg if reg_open is None else reg_open
        self.xi_strength = xi if xi_strength is None else xi_strength
        self.xi_open = xi if xi_open is None else xi_open
        self.xi_glob = xi if xi_glob is None else xi_glob
        self.red_weight = red_weight
        self.red_adjust = red_adjust
        self.team_home_reg = team_home_reg
        self.season_discount = season_discount
        self.goal_cap = goal_cap
        self.shots_strength = shots_weight if shots_strength is None else shots_strength
        self.shots_open = shots_weight if shots_open is None else shots_open
        self.xi_form, self.reg_form = xi_form, reg_form
        self.tilt = tilt
        self.tilt_ = (0.0, 0.0)
        self.team_home: np.ndarray | None = None

    def params(self) -> dict:
        return super().params() | {
            "reg_strength": self.reg_strength,
            "reg_open": self.reg_open,
            "xi_strength": self.xi_strength,
            "xi_open": self.xi_open,
            "xi_glob": self.xi_glob,
            "red_weight": self.red_weight,
            "red_adjust": self.red_adjust,
            "team_home_reg": self.team_home_reg,
            "season_discount": self.season_discount,
            "goal_cap": self.goal_cap,
            "shots_strength": self.shots_strength,
            "shots_open": self.shots_open,
            "xi_form": self.xi_form,
            "reg_form": self.reg_form,
            "tilt": self.tilt,
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
        cd = crowd_mask(h, self.closed_doors)
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
        # each block of parameters has its own decay and its own target (goals, or goals
        # blended with a shots-on-target estimate)
        keys = ["str", "open", "glob"] + (["form"] if self.xi_form is not None else [])
        xis = {"str": self.xi_strength, "open": self.xi_open, "glob": self.xi_glob}
        xis["form"] = self.xi_form
        sws = {"str": self.shots_strength, "open": self.shots_open, "glob": self.shots_weight}
        sws["form"] = self.shots_strength
        w_cache: dict = {}
        y_cache: dict = {}
        weights, ys = {}, {}
        for k in keys:
            if xis[k] not in w_cache:
                w_cache[xis[k]] = np.exp(-xis[k] * days) * base
            if sws[k] not in y_cache:
                y_cache[sws[k]] = self._targets(h, sws[k])
            weights[k], ys[k] = w_cache[xis[k]], y_cache[sws[k]]
        distinct = len(w_cache) > 1 or len(y_cache) > 1
        glob = weights["glob"]
        yh, ya = ys["glob"]
        gh, ga = h.home_goals.to_numpy(), h.away_goals.to_numpy()

        # ---- parameters: strength s = att + def and openness o = att − def per team, then
        # c, c_tier2, home(D), home_cd, [red_own, red_opp], [hteam(T)].
        #   eta_home = ... + (s_h − s_a)/2 + (o_h + o_a)/2
        #   eta_away = ... + (s_a − s_h)/2 + (o_h + o_a)/2
        names = [f"str:{t}" for t in teams] + [f"open:{t}" for t in teams]
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
        i_form = None
        if self.xi_form is not None:  # short-term form: a second strength, fast decay
            i_form = P
            names += [f"form:{t}" for t in teams]
            P += T

        r, one, half = np.arange(n), np.ones(n), np.full(n, 0.5)
        common_c = [np.full(n, i_c), np.full(n, i_t2), T + hi, T + ai]
        common_v = [one, tier2, half, half]
        cols_h = [hi, ai, i_home + di, np.full(n, i_cd), *common_c]
        vals_h = [half, -half, (~cd).astype(float), cd * 1.0, *common_v]
        cols_a = [ai, hi, *common_c]
        vals_a = [half, -half, *common_v]
        if i_red is not None:
            cols_h += [np.full(n, i_red), np.full(n, i_red + 1)]
            vals_h += [rh, ra]
            cols_a += [np.full(n, i_red), np.full(n, i_red + 1)]
            vals_a += [ra, rh]
        if i_ht is not None:
            cols_h.append(i_ht + hi)
            vals_h.append(one)
        if i_form is not None:
            cols_h += [i_form + hi, i_form + ai]
            vals_h += [half, -half]
            cols_a += [i_form + ai, i_form + hi]
            vals_a += [half, -half]

        def design(cols, vals):
            rows = np.tile(r, len(cols))
            m = sparse.coo_matrix(
                (np.concatenate(vals), (rows, np.concatenate(cols))), shape=(n, P)
            )
            return m.tocsr()  # duplicate entries (o_h + o_a for a team v itself) are summed

        Xh, Xa = design(cols_h, vals_h), design(cols_a, vals_a)
        XhT, XaT = Xh.T.tocsr(), Xa.T.tocsr()

        # ridge 0.25·(reg_strength·s² + reg_open·o²) = 0.5·reg·(att² + def²) when both are reg
        pen = np.full(P, TINY)
        pen[:T], pen[T : 2 * T] = 0.5 * self.reg_strength, 0.5 * self.reg_open
        block = np.full(P, "glob", dtype=object)
        block[:T], block[T : 2 * T] = "str", "open"
        if i_ht is not None:
            pen[i_ht : i_ht + T] = self.team_home_reg
        if i_form is not None:
            pen[i_form : i_form + T] = 0.5 * self.reg_form
            block[i_form : i_form + T] = "form"
        masks = {k: block == k for k in keys}

        x = np.array([self._x.get(nm, 0.0) for nm in names])
        rho = self._x.get("rho", -0.05)
        if "c" not in self._x:
            x[i_c] = np.log(max(np.average(yh, weights=glob), 0.1))
            x[i_home : i_home + D] = 0.25
        if not cd.any():
            x[i_cd] = 0.0

        def state(x, rho):
            eh, ea = Xh @ x, Xa @ x
            lh, la = np.exp(eh), np.exp(ea)
            tau, _, dt_eh, dt_ea = _tau_terms(gh, ga, lh, la, rho)
            return eh, ea, lh, la, tau, dt_eh, dt_ea

        def grad(x, st):
            _, _, lh, la, _, dt_eh, dt_ea = st
            g = np.empty(P)
            cache = {}
            for k in keys:
                wk, (yhk, yak) = weights[k], ys[k]
                ck = (id(wk), id(yhk))
                if ck not in cache:
                    cache[ck] = XhT @ (wk * (yhk - lh + dt_eh)) + XaT @ (wk * (yak - la + dt_ea))
                g[masks[k]] = -cache[ck][masks[k]]
            return g + pen * x

        def objective(x, st):
            eh, ea, lh, la, tau, *_ = st
            ll = glob * (yh * eh - lh + ya * ea - la + np.log(tau))
            return -ll.sum() + 0.5 * (pen * x * x).sum()

        def jacobian(st):
            _, _, lh, la, *_ = st
            J = np.zeros((P, P))
            cache = {}
            for k in keys:
                wk = weights[k]
                if id(wk) not in cache:
                    H = XhT @ Xh.multiply((wk * lh)[:, None]) + XaT @ Xa.multiply(
                        (wk * la)[:, None]
                    )
                    cache[id(wk)] = H.toarray()
                J[masks[k]] = cache[id(wk)][masks[k]]
            J[np.diag_indices(P)] += pen
            return J

        def rho_step(st, rho):
            _, _, lh, la, *_ = st
            _, d_rho, _, _ = _tau_terms(gh, ga, lh, la, rho)
            score = (glob * d_rho).sum()
            curv = (glob * _tau_d2(gh, ga, lh, la, rho)).sum()
            new = rho - score / curv if curv < 0 else rho + np.sign(score) * 0.01
            return float(np.clip(new, -RHO_LIMIT, RHO_LIMIT))

        def merit_of(x, st, g):
            return float(g @ g) if distinct else objective(x, st)

        st = state(x, rho)
        g = grad(x, st)
        merit = merit_of(x, st, g)
        converged = False
        for _ in range(100):
            # sparse LU: dense LAPACK spins its thread pool and crawls on a busy machine
            step = np.clip(spsolve(sparse.csc_matrix(jacobian(st)), -g), -1.0, 1.0)
            t = 1.0
            for _ in range(20):
                x_new = x + t * step
                st_new = state(x_new, rho)
                g_new = grad(x_new, st_new)
                if merit_of(x_new, st_new, g_new) <= merit + 1e-12 * abs(merit) or t < 1e-3:
                    break
                t /= 2
            x = x_new
            rho_new = rho_step(st_new, rho)
            d_rho, rho = abs(rho_new - rho), rho_new
            st = state(x, rho)
            g = grad(x, st)
            merit = merit_of(x, st, g)
            if np.max(np.abs(t * step)) < 1e-7 and d_rho < 1e-8:
                converged = True
                break

        self._x = dict(zip(names, x, strict=True)) | {"rho": rho}
        s_, o_ = x[:T].copy(), x[T : 2 * T]
        if i_form is not None:
            s_ += x[i_form : i_form + T]
        # store in the core layout (att, def, c, c_tier2, ...) so DixonColes.rates applies
        self.coef = np.concatenate([(s_ + o_) / 2, (s_ - o_) / 2, x[2 * T :]])
        self.T, self.D = T, D
        self.home_by_div = {d: float(x[i_home + i]) for d, i in self.div_idx.items()}
        self.rho_ = rho
        self.team_home = None if i_ht is None else x[i_ht : i_ht + T].copy()
        self.converged_ = converged
        if self.tilt:
            lh, la = np.exp(Xh @ x), np.exp(Xa @ x)
            self.tilt_ = self._fit_tilt(poisson_grid(lh, la, rho), gh, ga, glob)

    def _fit_tilt(self, grids, gh, ga, w) -> tuple[float, float]:
        """Weighted ML for the grid tilt exp(kappa·[both score] + delta·[draw]), given the
        fitted rates. The four cell groups make it a two-parameter exponential family."""
        b, d = _tilt_masks()
        m = np.stack(
            [(grids * ((b == i) & (d == j))).sum(axis=(1, 2)) for i in (0, 1) for j in (0, 1)],
            axis=1,
        )  # masses of (both, draw) = (0,0), (0,1), (1,0), (1,1)
        gi, gj = np.minimum(gh, MAX_GOALS), np.minimum(ga, MAX_GOALS)
        obs = np.stack([b[gi, gj], d[gi, gj]], axis=1)
        free = np.array(
            {"btts": (True, False), "draw": (False, True), "both": (True, True)}[self.tilt]
        )
        th = np.array(self.tilt_, dtype=float)
        feats = np.array([[0, 0], [0, 1], [1, 0], [1, 1]], dtype=float)
        for _ in range(50):
            e = m * np.exp(feats @ th)[None, :]
            q = e / e.sum(axis=1, keepdims=True)  # tilted group probabilities
            mean = q @ feats
            g = (w[:, None] * (obs - mean)).sum(axis=0)
            cov = np.einsum("ng,gi,gj->nij", q, feats, feats) - mean[:, :, None] * mean[:, None, :]
            H = (w[:, None, None] * cov).sum(axis=0)  # minus the Hessian of the log-likelihood
            step = np.zeros(2)
            step[free] = _solve_small(H[np.ix_(free, free)], g[free])
            th = th + np.clip(step, -0.5, 0.5)
            if np.abs(step).max() < 1e-9:
                break
        return float(th[0]), float(th[1])

    def _targets(self, h: pd.DataFrame, shots_weight: float) -> tuple[np.ndarray, np.ndarray]:
        """Goal counts, optionally blended with SoT × goals-per-SoT (NaN shots: goals)."""
        yh, ya = h.home_goals.to_numpy(float), h.away_goals.to_numpy(float)
        if shots_weight > 0:
            saved, self.shots_weight = self.shots_weight, shots_weight
            yh, ya = self._blend_shots(h, yh, ya)
            self.shots_weight = saved
        if self.goal_cap is not None:
            yh, ya = np.minimum(yh, self.goal_cap), np.minimum(ya, self.goal_cap)
        return yh, ya

    # ---- prediction ----------------------------------------------------------------------

    def predict(self, fixtures: pd.DataFrame) -> MarketProbs:
        lh, la = self.rates(fixtures)
        g = poisson_grid(lh, la, self.rho_)
        if self.tilt:
            b, d = _tilt_masks()
            t = g * np.exp(self.tilt_[0] * b + self.tilt_[1] * d)[None]
            t /= t.sum(axis=(1, 2), keepdims=True)
            # the tilt moves mass between cells and so changes the expected goals: report
            # those of the grid actually returned (published as expected goals / xG), so they
            # agree with its probabilities. The shift is measured on the capped grid, so it is
            # exactly zero when the tilt is.
            lh = lh + ((t - g) * GOALS[None, :, None]).sum(axis=(1, 2))
            la = la + ((t - g) * GOALS[None, None, :]).sum(axis=(1, 2))
            g = t
        return markets_from_grid(g, lh, la)

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


def crowd_mask(df: pd.DataFrame, mode) -> np.ndarray:
    """Matches given the closed-doors home term. mode False: none; True: no-spectator
    periods; "limited": capped-crowd periods as well (sensitivity check)."""
    if not mode:
        return np.zeros(len(df), dtype=bool)
    statuses = ("none", "limited") if mode == "limited" else ("none",)
    if periods(statuses):
        return closed_doors_mask(df, statuses)
    mask = np.zeros(len(df), dtype=bool)
    div, day = df.division.to_numpy(), df["date"].to_numpy()
    for d, ranges in CROWD_CALENDAR.items():
        for status, start, end in ranges:
            if status in statuses:
                mask |= (div == d) & (day >= np.datetime64(start)) & (day <= np.datetime64(end))
    return mask


def _solve_small(A: np.ndarray, b: np.ndarray) -> np.ndarray:
    """Solve a 1×1 or 2×2 positive-definite system in closed form."""
    if len(b) == 1:
        return b / max(A[0, 0], 1e-12)
    det = max(A[0, 0] * A[1, 1] - A[0, 1] * A[1, 0], 1e-18)
    return np.array([A[1, 1] * b[0] - A[0, 1] * b[1], A[0, 0] * b[1] - A[1, 0] * b[0]]) / det


def _tilt_masks() -> tuple[np.ndarray, np.ndarray]:
    k = np.arange(MAX_GOALS + 1)
    both = ((k[:, None] > 0) & (k[None, :] > 0)).astype(float)
    draw = (k[:, None] == k[None, :]).astype(float)
    return both, draw


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


# Selected by coordinate descent on the D-025 composite, pooled over inner validation 2022/23
# and validation 2023/24 (five leagues each). Shared base: strength/openness ridge split and a
# faster decay for the global terms.
SPLIT = {"reg_strength": 1.0, "reg_open": 50.0, "xi_glob": 0.004}
FULL = SPLIT | {
    "xi_strength": 0.0028,
    "shots_weight": 0.25,
    "season_discount": 0.85,
    "tilt": "btts",
}
CONFIGS: dict[str, dict] = {
    "dc_tuning_split": SPLIT,
    "dc_tuning_shots": SPLIT | {"xi_strength": 0.0028, "shots_weight": 0.25},
    "dc_tuning_tilt": SPLIT | {"xi_strength": 0.0028, "tilt": "btts"},
    "dc_tuning_full": FULL,
    # the brief's closed-doors sensitivity check: the same model without the closed-doors term
    "dc_tuning_full_nocd": FULL | {"closed_doors": False},
}


def _factory(name: str, cfg: dict):
    def make() -> DCTuned:
        return DCTuned(**cfg, name=name)

    return make


CANDIDATES = {name: _factory(name, cfg) for name, cfg in CONFIGS.items()}
