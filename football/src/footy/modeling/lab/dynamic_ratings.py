"""Dynamic team ratings (model-lab family `dynamic_ratings`).

Two rating systems that move match by match, and a mixture of the two.

`DynamicPoisson`: a state-space model in which every team's ratings follow random walks,
filtered by an extended Kalman filter on the log-rates (a Gaussian approximation to the
Koopman & Lit dynamic Poisson model; Rue & Salvesen use the same Brownian dynamics with
MCMC). Per team a strength s (moves the goal difference) and a tempo t (moves the goal
total):

    log E[home goals] = mu_k + h_k + (s_i − s_j)/2 + (t_i + t_j)/2
    log E[away goals] = mu_k       + (s_j − s_i)/2 + (t_i + t_j)/2

The division goal level mu_k and home advantage h_k are states too. One full covariance
covers every team and division term, so a result also moves everyone correlated with the two
sides, e.g. a promoted team's old division. Matches on the same day are one joint update,
written in IRLS form: working response η + (y − λ)/λ with variance r/λ.

- Shots on target, total shots and corners can be extra measurements of the same ratings:
  log E[home SoT] = nu_k + g_k + b_s·(s_i − s_j)/2 + b_t·(t_i + t_j)/2 (+ small team SoT
  residuals), with their own division terms and noise scale. Missing values are skipped,
  row by row, so with no shot data at all the model reduces to the goals-only filter.
- Summer: ratings regress towards their division's mean and gain variance. Division
  levels get a summer shock that is shared within a country and between goals and the
  count statistics (a rule change such as 2023/24's added time moves them together, and
  the far more numerous shots then reveal it within weeks).
- Prediction: the season step if the fixture is in a new season, then
  λ = exp(E[η] + c·Var[η]/2), the Poisson grid with the Dixon-Coles rho fitted on the
  filter's own one-step-ahead predictions over the history.

`PiRatings`: pi-ratings (Constantinou & Fenton 2013): each team's home and away ratings
move after every match by lr·ψ(error) in goal-difference units (ψ = c·log10(1 + |error|)),
with the other venue's rating moving gamma times as much. The pre-match expected goal
difference, a goal-total ("tempo") rating per team and fading promoted / relegated flags
feed two weighted Poisson regressions (home goals, away goals; intercepts per division)
fitted by IRLS on the training history. Optionally the ratings learn from a blend of the
goal difference and a shots-on-target expected goal difference.

`GridBlend`: a mixture of score grids, which stays coherent across every market.

Uses only the DataFrames passed to fit/predict, and no randomness. The one fixed input is
a public calendar of COVID-19 closed-doors periods (optional, off in the registered
candidates). Every fit replays the filter over the history it is given, so a fit does not
depend on earlier calls.
"""

import math

import numpy as np
import pandas as pd
from scipy.linalg import solve_triangular
from scipy.linalg.blas import dsyrk
from scipy.optimize import minimize_scalar

from footy.modeling.base import ALL_MARKETS
from footy.modeling.data import SECOND_TIER
from footy.modeling.grid import MarketProbs, markets_from_grid, mix_grids, poisson_grid

TIER1 = set(SECOND_TIER)
TIER2 = set(SECOND_TIER.values())
DIVS = sorted(TIER1 | TIER2)
DIDX = {d: i for i, d in enumerate(DIVS)}
IS_T2 = np.array([d in TIER2 for d in DIVS])
EPOCH = pd.Timestamp("2000-01-01")
RHO_LIMIT = 0.25
# extra count statistics that measure the same latent ratings: columns, starting log level
STREAMS = {
    "sot": ("home_sot", "away_sot", math.log(4.3)),
    "shots": ("home_shots", "away_shots", math.log(12.0)),
    "corners": ("home_corners", "away_corners", math.log(5.0)),
}

# COVID-19 periods without spectators, by division: a fixed calendar of public facts (to
# within days), not derived from match data. With `covid`, these get their own home term.
NO_CROWD = {
    "E0": [("2020-06-17", "2021-05-17")],
    "E1": [("2020-06-20", "2021-05-08")],
    "SP1": [("2020-06-11", "2021-05-16")],
    "SP2": [("2020-06-10", "2021-05-30")],
    "I1": [("2020-06-20", "2020-09-18"), ("2020-10-25", "2021-05-23")],
    "I2": [("2020-06-20", "2020-09-25"), ("2020-10-25", "2021-05-30")],
    "D1": [
        ("2020-05-16", "2020-09-17"),
        ("2020-10-31", "2021-05-22"),
        ("2021-12-28", "2022-02-13"),
    ],
    "D2": [
        ("2020-05-16", "2020-09-17"),
        ("2020-10-31", "2021-05-23"),
        ("2021-12-28", "2022-02-13"),
    ],
    "F1": [("2020-10-30", "2021-05-23")],
    "F2": [("2020-10-30", "2021-05-22")],
}


def no_crowd_mask(df: pd.DataFrame) -> np.ndarray:
    mask = np.zeros(len(df), dtype=bool)
    div, day = df.division.to_numpy(), df["date"].to_numpy()
    for d, ranges in NO_CROWD.items():
        for start, end in ranges:
            mask |= (div == d) & (day >= np.datetime64(start)) & (day <= np.datetime64(end))
    return mask


EKF_DEFAULTS = {
    # team dynamics (variances in strength / tempo units; per day unless "season")
    "q_s": 4e-5,  # daily random-walk variance of strength
    "q_t": 5e-6,  # ... of tempo
    "p0_s": 0.08,  # prior variance of a team's strength when it joins the filter
    "p0_t": 0.01,
    "season_var_s": 0.01,  # extra variance every summer
    "season_var_t": 0.002,
    "season_shrink_s": 0.85,  # summer regression towards the division mean (1: none)
    "season_shrink_t": 0.7,
    "tier2_offset": 0.4,  # at the start of the history, tier-2 sides start this much weaker
    "newcomer_s": -0.15,  # a side new to the data starts this far below its division's mean
    # division terms
    "q_mu": 2e-6,  # daily drift of a division's goal level
    "q_h": 2e-6,  # ... and of its home advantage
    "p0_mu": 0.02,
    "p0_h": 0.02,
    "season_var_mu": 0.0,  # summer shock to division goal / count levels
    "season_var_h": 0.0,  # ... and to division home advantages
    "level_corr": 0.0,  # share of the level shock common to a country's levels
    "team_home": 0.0,  # prior variance of team-specific home advantage (0: none)
    "q_team_home": 0.0,
    "covid": False,  # separate home term for matches without spectators
    # measurement
    "r_scale": 1.0,  # goal measurement-noise scale (quasi-Poisson dispersion)
    "r_tier2": 1.0,  # extra scale for second-tier matches
    "r_red": 1.0,  # extra scale for matches with a red card
    "y_cap": 0.0,  # cap goal counts at this value in the update (0: no cap)
    "r_sot": 0.0,  # shots-on-target noise scale (0: SoT unused)
    "sot_s": 0.67,  # loading of SoT log-rates on strength (goals load 1)
    "sot_t": 1.0,  # ... on tempo
    "sot_resid": 0.0,  # prior variance of team SoT-specific strength / tempo (0: none)
    "q_sot_resid": 0.0,
    "r_shots": 0.0,  # total shots (0: unused)
    "shots_s": 0.4,
    "shots_t": 0.6,
    "r_corners": 0.0,  # corners (0: unused)
    "corners_s": 0.3,
    "corners_t": 0.3,
    # prediction
    "var_correction": 0.0,  # predict exp(E[η] + c·Var[η]/2)
    "rho_days": 1000.0,  # time scale of the weights of the rho fit
    "rho_shift": 0.0,  # added to the fitted rho
    "warmup_days": 300.0,  # one-step predictions this early in the history fit no rho
}


class DynamicPoisson:
    """Extended-Kalman-filter dynamic Poisson ratings (see the module docstring)."""

    markets = ALL_MARKETS

    def __init__(self, name: str = "dynamic_ratings_ekf", **cfg):
        unknown = set(cfg) - set(EKF_DEFAULTS)
        if unknown:
            raise TypeError(f"unknown options {sorted(unknown)}")
        self.cfg = EKF_DEFAULTS | cfg
        self.name = name

    def params(self) -> dict:
        return dict(self.cfg)

    def _layout(self, T: int) -> dict:
        c, D = self.cfg, len(DIVS)
        lay, o = {}, 0
        for key, size, on in (
            ("s", T, True),
            ("t", T, True),
            ("mu", D, True),
            ("h", D, True),
            ("th", T, c["team_home"] > 0),
            *((f"lvl_{k}", D, c[f"r_{k}"] > 0) for k in STREAMS),
            *((f"home_{k}", D, c[f"r_{k}"] > 0) for k in STREAMS),
            ("u", T, c["r_sot"] > 0 and c["sot_resid"] > 0),
            ("v", T, c["r_sot"] > 0 and c["sot_resid"] > 0),
            ("hcd", 1, bool(c["covid"])),
        ):
            if on:
                lay[key] = o
                o += size
        lay["n"] = o
        return lay

    # ---- filtering -------------------------------------------------------------------

    def fit(self, history: pd.DataFrame, cutoff) -> None:
        c = self.cfg
        h = history[history.division.isin(TIER1 | TIER2)].sort_values(["date", "id"])
        self.start(sorted(set(h.home_team) | set(h.away_team)))
        pre_lh, pre_la = self.process(h)
        day = (h["date"] - EPOCH).dt.days.to_numpy()
        cut_day = (cutoff.tz_convert("UTC").tz_localize(None) - EPOCH).days
        use = (day >= day[0] + c["warmup_days"]) if len(h) else np.zeros(0, bool)
        w = np.exp(-(cut_day - day) / c["rho_days"]) * use
        yh, ya = h.home_goals.to_numpy(float), h.away_goals.to_numpy(float)
        rho = _fit_rho(yh, ya, pre_lh, pre_la, w) + c["rho_shift"]
        self.rho_ = float(np.clip(rho, -RHO_LIMIT, RHO_LIMIT))

    def start(self, teams: list[str]) -> None:
        """Empty state for these teams (each joins the filter at its first match)."""
        c = self.cfg
        T, D = len(teams), len(DIVS)
        self.tidx_ = {t: i for i, t in enumerate(teams)}
        L = self._layout(T)
        n = L["n"]
        self.T_, self.L_ = T, L
        m = np.zeros(n)
        P = np.zeros((n, n))
        dg = np.arange(n)

        def init(key, mean, var, size):
            sl = slice(L[key], L[key] + size)
            m[sl] = mean
            P[dg[sl], dg[sl]] = var

        init("mu", math.log(1.3), c["p0_mu"], D)
        init("h", 0.25, c["p0_h"], D)
        for k, (_, _, level) in STREAMS.items():
            if f"lvl_{k}" in L:
                init(f"lvl_{k}", level, c["p0_mu"], D)
                init(f"home_{k}", 0.12, c["p0_h"], D)
        if "hcd" in L:
            init("hcd", 0.05, c["p0_h"], 1)
        q = np.zeros(n)
        for key, val, size in (
            ("s", c["q_s"], T),
            ("t", c["q_t"], T),
            ("mu", c["q_mu"], D),
            ("h", c["q_h"], D),
            ("th", c["q_team_home"], T),
            *((f"lvl_{k}", c["q_mu"], D) for k in STREAMS),
            *((f"home_{k}", c["q_h"], D) for k in STREAMS),
            ("u", c["q_sot_resid"], T),
            ("v", c["q_sot_resid"], T),
        ):
            if key in L:
                q[L[key] : L[key] + size] = val
        self.q_ = q
        self.m_, self.P_ = m, P
        self.seen_ = np.zeros(T, bool)
        self.last_div_ = np.full(T, -1)
        self.cur_day_ = None
        self.last_season_ = None

    def process(self, h: pd.DataFrame) -> tuple[np.ndarray, np.ndarray]:
        """Filter matches `h` (sorted by date, later than anything filtered so far) into the
        state; returns each match's one-step-ahead expected goals (before its update)."""
        c, L = self.cfg, self.L_
        m, P, q = self.m_, self.P_, self.q_
        seen, last_div = self.seen_, self.last_div_
        n = L["n"]
        dg = np.arange(n)
        streams = [k for k in STREAMS if f"lvl_{k}" in L]
        hi = h.home_team.map(self.tidx_).to_numpy()
        ai = h.away_team.map(self.tidx_).to_numpy()
        di = h.division.map(DIDX).to_numpy()
        yh = h.home_goals.to_numpy(float)
        ya = h.away_goals.to_numpy(float)
        stats = {}
        for k in streams:
            xh = h[STREAMS[k][0]].to_numpy(float)
            xa = h[STREAMS[k][1]].to_numpy(float)
            stats[k] = (xh, xa, ~(np.isnan(xh) | np.isnan(xa)))
        day = (h["date"] - EPOCH).dt.days.to_numpy()
        season = h.season.str[:2].astype(int).to_numpy()
        rw = np.where(IS_T2[di], c["r_scale"] * c["r_tier2"], c["r_scale"])
        if c["r_red"] != 1.0:
            red = (h.home_red.fillna(0).to_numpy() + h.away_red.fillna(0).to_numpy()) > 0
            rw = np.where(red, rw * c["r_red"], rw)
        if c["y_cap"] > 0:
            yh, ya = np.minimum(yh, c["y_cap"]), np.minimum(ya, c["y_cap"])
        cd = no_crowd_mask(h) if "hcd" in L else np.zeros(len(h), bool)
        N = len(h)
        pre_lh, pre_la = np.zeros(N), np.zeros(N)
        starts = np.flatnonzero(np.r_[True, day[1:] != day[:-1]]) if N else np.zeros(0, int)
        ends = np.r_[starts[1:], N]
        for s0, e0 in zip(starts, ends, strict=True):
            if self.cur_day_ is not None:
                P[dg, dg] += q * (day[s0] - self.cur_day_)
            self.cur_day_ = day[s0]
            if self.last_season_ is not None and season[s0] != self.last_season_:
                m, P = self._season_step(m, P, seen, last_div)
            self.last_season_ = season[s0]
            g = np.arange(s0, e0)
            for k in g:
                for t_ in (hi[k], ai[k]):
                    if not seen[t_]:
                        self._add_team(m, P, t_, IS_T2[di[k]], seen, last_div, di[k])
            last_div[hi[g]] = di[g]
            last_div[ai[g]] = di[g]
            # observation rows: home goals, away goals, then each extra statistic
            mm = len(g)
            nobs = 2 * mm * (1 + len(streams))
            H = np.zeros((nobs, n))
            r_ = np.arange(mm)
            gh, ga = 2 * r_, 2 * r_ + 1
            hg, ag, dv = hi[g], ai[g], di[g]
            _rows(H, gh, ga, hg, ag, L["s"], L["t"], 1.0, 1.0)
            H[gh, L["mu"] + dv] += 1.0
            H[ga, L["mu"] + dv] += 1.0
            cdg = cd[g]
            H[gh[~cdg], L["h"] + dv[~cdg]] += 1.0
            if cdg.any():
                H[gh[cdg], L["hcd"]] += 1.0
            if "th" in L:
                H[gh, L["th"] + hg] += 1.0
            y = np.empty(nobs)
            y[gh], y[ga] = yh[g], ya[g]
            var_scale = np.empty(nobs)
            var_scale[gh], var_scale[ga] = rw[g], rw[g]
            extra = []
            for j, k in enumerate(streams):
                sh_, sa_ = 2 * mm * (j + 1) + 2 * r_, 2 * mm * (j + 1) + 2 * r_ + 1
                _rows(H, sh_, sa_, hg, ag, L["s"], L["t"], c[f"{k}_s"], c[f"{k}_t"])
                if k == "sot" and "u" in L:
                    _rows(H, sh_, sa_, hg, ag, L["u"], L["v"], 1.0, 1.0)
                H[sh_, L[f"lvl_{k}"] + dv] += 1.0
                H[sa_, L[f"lvl_{k}"] + dv] += 1.0
                H[sh_, L[f"home_{k}"] + dv] += 1.0
                xh, xa, ok_all = stats[k]
                ok = ok_all[g]
                vs = np.where(ok, c[f"r_{k}"] * rw[g], 1e12)  # missing: no information
                var_scale[sh_], var_scale[sa_] = vs, vs
                extra.append((sh_, sa_, ok, xh[g], xa[g]))
            cols = np.flatnonzero(H.any(axis=0))  # H is sparse: the day's teams and divisions
            Hc = H[:, cols]
            PHt = P[:, cols] @ Hc.T
            lam = np.exp(Hc @ m[cols])
            pre_lh[g], pre_la[g] = lam[gh], lam[ga]
            for sh_, sa_, ok, xh, xa in extra:  # missing: the observation is its prediction
                y[sh_] = np.where(ok, xh, lam[sh_])
                y[sa_] = np.where(ok, xa, lam[sa_])
            # Kalman update on the working response (one Newton step from the prior mean):
            # S = H P H' + R = C C', A = C⁻¹ H P, m += A' C⁻¹ e, P −= A' A
            chol = np.linalg.cholesky(Hc @ PHt[cols] + np.diag(var_scale / lam))
            A = solve_triangular(chol, PHt.T, lower=True)
            m = m + A.T @ solve_triangular(chol, (y - lam) / lam, lower=True)
            up = dsyrk(-1.0, A, beta=1.0, c=P, trans=1)  # upper triangle of P − A'A
            P = np.triu(up) + np.triu(up, 1).T
        self.m_, self.P_ = m, P
        return pre_lh, pre_la

    def _add_team(self, m, P, t_, is_tier2, seen, last_div, dk):
        c, L = self.cfg, self.L_
        # a side first seen after the start (promoted from below the data) gets the
        # newcomer prior relative to the teams of its division; at the start of the
        # history, tier-2 sides start below tier-1 sides
        members = np.flatnonzero(seen & (last_div == dk))
        if len(members) >= 6:
            m[t_] = m[members].mean() + c["newcomer_s"]
        else:
            m[t_] = -c["tier2_offset"] if is_tier2 else 0.0
        for key, var in (
            ("s", c["p0_s"]),
            ("t", c["p0_t"]),
            ("th", c["team_home"]),
            ("u", c["sot_resid"]),
            ("v", c["sot_resid"]),
        ):
            if key in L:
                P[L[key] + t_, L[key] + t_] = var
        seen[t_] = True

    def _season_step(self, m, P, seen, last_div):
        """Summer: ratings regress towards their division's mean and become less certain;
        division levels take a shock that is partly shared within each country."""
        c, L, T = self.cfg, self.L_, self.T_
        n, D = len(m), len(DIVS)
        A = np.eye(n)
        for dk in np.unique(last_div[seen]):
            members = np.flatnonzero(seen & (last_div == dk))
            k = len(members)
            for key, phi in (("s", c["season_shrink_s"]), ("t", c["season_shrink_t"])):
                rows = members + L[key]
                A[np.ix_(rows, rows)] = (1 - phi) / k
                A[rows, rows] += phi
        m = A @ m
        P = A @ P @ A.T
        sv = np.zeros(n)
        sv[L["s"] : L["s"] + T] = c["season_var_s"] * seen
        sv[L["t"] : L["t"] + T] = c["season_var_t"] * seen
        levels = [key for key in ("mu", *(f"lvl_{k}" for k in STREAMS)) if key in L]
        shared = c["level_corr"] * c["season_var_mu"]
        for key in levels:
            sv[L[key] : L[key] + D] = c["season_var_mu"] - shared
        if shared:
            for top, second in SECOND_TIER.items():
                idx = np.array([L[key] + DIDX[d] for key in levels for d in (top, second)])
                P[np.ix_(idx, idx)] += shared
        for key in ("h", *(f"home_{k}" for k in STREAMS)):
            if key in L:
                sv[L[key] : L[key] + D] = c["season_var_h"]
        P[np.arange(n), np.arange(n)] += sv
        return m, P

    # ---- prediction ------------------------------------------------------------------

    def rates(self, fixtures: pd.DataFrame) -> tuple[np.ndarray, np.ndarray]:
        c, L = self.cfg, self.L_
        m, P = self.m_, self.P_
        seen, last_div = self.seen_, self.last_div_
        season = fixtures.season.str[:2].astype(int).to_numpy()
        if season.max() != self.last_season_:
            m, P = self._season_step(m, P, seen, last_div)
        n, N = len(m), len(fixtures)
        H = np.zeros((2 * N, n))
        extra = np.zeros(2 * N)  # prior means of sides the filter has never seen
        evar = np.zeros(2 * N)
        cd = no_crowd_mask(fixtures) if "hcd" in L else np.zeros(N, bool)
        for k, (ht, at, dv) in enumerate(
            zip(fixtures.home_team, fixtures.away_team, fixtures.division, strict=True)
        ):
            dk = DIDX[dv]
            for t_, sgn in ((ht, 1.0), (at, -1.0)):
                if t_ in self.tidx_:
                    i = self.tidx_[t_]
                    H[2 * k, L["s"] + i] += 0.5 * sgn
                    H[2 * k + 1, L["s"] + i] -= 0.5 * sgn
                    H[2 * k, L["t"] + i] += 0.5
                    H[2 * k + 1, L["t"] + i] += 0.5
                    if "th" in L and sgn > 0:
                        H[2 * k, L["th"] + i] += 1.0
                else:
                    members = np.flatnonzero(seen & (last_div == dk))
                    s0 = m[members].mean() + c["newcomer_s"] if len(members) else 0.0
                    extra[2 * k] += 0.5 * sgn * s0
                    extra[2 * k + 1] -= 0.5 * sgn * s0
                    evar[2 * k : 2 * k + 2] += 0.25 * (c["p0_s"] + c["p0_t"])
            H[2 * k, L["mu"] + dk] += 1.0
            H[2 * k + 1, L["mu"] + dk] += 1.0
            H[2 * k, L["hcd"] if cd[k] else L["h"] + dk] += 1.0
        eta = H @ m + extra
        if c["var_correction"]:
            var = np.einsum("ij,jk,ik->i", H, P, H) + evar
            eta = eta + 0.5 * c["var_correction"] * var
        lam = np.exp(eta)
        return lam[0::2], lam[1::2]

    def predict(self, fixtures: pd.DataFrame) -> MarketProbs:
        lh, la = self.rates(fixtures)
        return markets_from_grid(poisson_grid(lh, la, self.rho_), lh, la)


PI_DEFAULTS = {
    "lr": 0.035,  # pi-rating learning rate (Constantinou & Fenton: 0.035)
    "gamma": 0.7,  # how much a home result moves the away rating, and vice versa
    "b": 10.0,  # rating -> goal-difference base and scale
    "c": 3.0,
    "tempo_lr": 0.04,  # learning rate of each team's goal-total rating
    "season_shrink": 1.0,  # summer regression of pi-ratings towards zero
    "promo_decay": 12.0,  # promoted / relegated flags fade over this many matches
    "xi": 0.002,  # time decay of the regression weights (per day)
    "ridge": 1.0,  # ridge on the non-intercept regression coefficients
    "warmup_days": 300.0,  # matches this early in the history are not regressed on
    "rho_days": 1000.0,
    "sot_blend": 0.0,  # ratings learn from (1-a)·GD + a·(SoT-based expected GD)
    "tempo_sot": 0.0,  # tempo learns from (1-a)·total + a·(SoT-based expected total)
}


class PiRatings:
    """Pi-ratings mapped to goal expectancies by Poisson regression (module docstring)."""

    markets = ALL_MARKETS

    def __init__(self, name: str = "dynamic_ratings_pi", **cfg):
        unknown = set(cfg) - set(PI_DEFAULTS)
        if unknown:
            raise TypeError(f"unknown options {sorted(unknown)}")
        self.cfg = PI_DEFAULTS | cfg
        self.name = name

    def params(self) -> dict:
        return dict(self.cfg)

    def _gd(self, r: float) -> float:
        """Expected goal difference against an average side, from one rating."""
        c = self.cfg
        x = c["b"] ** (abs(r) / c["c"]) - 1.0
        return x if r >= 0 else -x

    def fit(self, history: pd.DataFrame, cutoff) -> None:
        c = self.cfg
        h = history[history.division.isin(TIER1 | TIER2)].sort_values(["date", "id"])
        N = len(h)
        home, away = h.home_team.to_numpy(), h.away_team.to_numpy()
        div = h.division.to_numpy()
        yh = h.home_goals.to_numpy(float)
        ya = h.away_goals.to_numpy(float)
        season = h.season.str[:2].astype(int).to_numpy()
        gd_obs, tot_obs = yh - ya, yh + ya
        if c["sot_blend"] > 0 or c["tempo_sot"] > 0:
            sh, sa = h.home_sot.to_numpy(float), h.away_sot.to_numpy(float)
            ok = ~(np.isnan(sh) | np.isnan(sa))
            conv = (yh[ok].sum() + ya[ok].sum()) / max(sh[ok].sum() + sa[ok].sum(), 1.0)
            xgd = np.where(ok, conv * (np.nan_to_num(sh) - np.nan_to_num(sa)), gd_obs)
            xtot = np.where(ok, conv * (np.nan_to_num(sh) + np.nan_to_num(sa)), tot_obs)
            gd_obs = (1 - c["sot_blend"]) * gd_obs + c["sot_blend"] * xgd
            tot_obs = (1 - c["tempo_sot"]) * tot_obs + c["tempo_sot"] * xtot
        self.rh_: dict[str, float] = {}
        self.ra_: dict[str, float] = {}
        self.tempo_: dict[str, float] = {}
        self.div_total_: dict[str, float] = {}
        self.team_div_: dict[str, dict[int, str]] = {}  # team -> season -> division
        self.n_in_season_: dict[str, int] = {}
        X = np.zeros((N, 4))  # ĝ, tempo sum, promoted-minus-relegated (home, away)
        cur_season = season[0] if N else 0
        lr, gam, tl = c["lr"], c["gamma"], c["tempo_lr"]
        for k in range(N):
            if season[k] != cur_season:
                self._season_step()
                cur_season = season[k]
            ht, at, dv, sk = home[k], away[k], div[k], season[k]
            for t_ in (ht, at):
                self.team_div_.setdefault(t_, {})[sk] = dv
            rh = self.rh_.get(ht, 0.0)
            ra = self.ra_.get(at, 0.0)
            ghat = self._gd(rh) - self._gd(ra)
            th, ta = self.tempo_.get(ht, 0.0), self.tempo_.get(at, 0.0)
            X[k] = (ghat, th + ta, *self._flags(ht, at, dv, sk))
            # pi-rating update
            err = gd_obs[k] - ghat
            psi = c["c"] * math.log10(1.0 + abs(err))
            ph = psi if err > 0 else -psi
            self.rh_[ht] = rh + lr * ph
            self.ra_[ht] = self.ra_.get(ht, 0.0) + gam * lr * ph
            self.ra_[at] = ra - lr * ph
            self.rh_[at] = self.rh_.get(at, 0.0) - gam * lr * ph
            # tempo: goal total relative to the division's running mean
            dm = self.div_total_.get(dv, 2.7)
            resid = tot_obs[k] - dm
            self.div_total_[dv] = dm + 0.01 * resid
            self.tempo_[ht] = th + tl * (resid / 2.0 - th)
            self.tempo_[at] = ta + tl * (resid / 2.0 - ta)
            self.n_in_season_[ht] = self.n_in_season_.get(ht, 0) + 1
            self.n_in_season_[at] = self.n_in_season_.get(at, 0) + 1
        self.last_season_ = cur_season
        self.divs_ = sorted(set(div))
        Z = np.column_stack([self._dummies(div), X])
        day = (h["date"] - EPOCH).dt.days.to_numpy()
        cut_day = (cutoff.tz_convert("UTC").tz_localize(None) - EPOCH).days
        use = day >= (day[0] + c["warmup_days"] if N else 0)
        w = np.exp(-c["xi"] * (cut_day - day)) * use
        nd = len(self.divs_)
        self.beta_h_ = _poisson_irls(Z, yh, w, nd, c["ridge"])
        self.beta_a_ = _poisson_irls(Z, ya, w, nd, c["ridge"])
        lh, la = np.exp(Z @ self.beta_h_), np.exp(Z @ self.beta_a_)
        self.rho_ = _fit_rho(yh, ya, lh, la, np.exp(-(cut_day - day) / c["rho_days"]) * use)

    def _dummies(self, div) -> np.ndarray:
        return np.array([[d == x for x in self.divs_] for d in div], float).reshape(
            len(div), len(self.divs_)
        )

    def _flags(self, ht, at, dv, sk) -> tuple[float, float]:
        """Promoted (+) or relegated (−) last summer, fading with this season's matches."""
        out = [0.0, 0.0]
        for j, t_ in enumerate((ht, at)):
            prev = self.team_div_.get(t_, {}).get(sk - 1)
            if prev is None or prev == dv:
                continue
            fade = math.exp(-self.n_in_season_.get(t_, 0) / self.cfg["promo_decay"])
            if dv in TIER1 and prev in TIER2:
                out[j] = fade
            elif dv in TIER2 and prev in TIER1:
                out[j] = -fade
        return out[0], out[1]

    def _season_step(self) -> None:
        f = self.cfg["season_shrink"]
        if f != 1.0:
            self.rh_ = {k: v * f for k, v in self.rh_.items()}
            self.ra_ = {k: v * f for k, v in self.ra_.items()}
        self.n_in_season_ = {}

    def rates(self, fixtures: pd.DataFrame) -> tuple[np.ndarray, np.ndarray]:
        season = fixtures.season.str[:2].astype(int).to_numpy()
        if season.max() != self.last_season_:
            self._season_step()
            self.last_season_ = season.max()
        X = np.array(
            [
                (
                    self._gd(self.rh_.get(ht, 0.0)) - self._gd(self.ra_.get(at, 0.0)),
                    self.tempo_.get(ht, 0.0) + self.tempo_.get(at, 0.0),
                    *self._flags(ht, at, dv, sk),
                )
                for ht, at, dv, sk in zip(
                    fixtures.home_team, fixtures.away_team, fixtures.division, season, strict=True
                )
            ]
        ).reshape(len(fixtures), 4)
        Z = np.column_stack([self._dummies(fixtures.division.to_numpy()), X])
        return np.exp(Z @ self.beta_h_), np.exp(Z @ self.beta_a_)

    def predict(self, fixtures: pd.DataFrame) -> MarketProbs:
        lh, la = self.rates(fixtures)
        return markets_from_grid(poisson_grid(lh, la, self.rho_), lh, la)


class GridBlend:
    """A mixture of the score grids of several rating models (a mixture of score
    distributions is a score distribution, so every market stays coherent)."""

    markets = ALL_MARKETS

    def __init__(self, parts, weights, name: str = "dynamic_ratings_blend"):
        self.parts = list(parts)
        self.weights = np.asarray(weights, dtype=float) / np.sum(weights)
        self.name = name

    def params(self) -> dict:
        return {
            "parts": [{"model": type(p).__name__, **p.params()} for p in self.parts],
            "weights": self.weights.tolist(),
        }

    def fit(self, history: pd.DataFrame, cutoff) -> None:
        for p in self.parts:
            p.fit(history, cutoff)

    def predict(self, fixtures: pd.DataFrame) -> MarketProbs:
        mps = [p.predict(fixtures) for p in self.parts]
        grid = mix_grids([mp.grid for mp in mps], self.weights)
        lh = sum(w * mp.lam_h for w, mp in zip(self.weights, mps, strict=True))
        la = sum(w * mp.lam_a for w, mp in zip(self.weights, mps, strict=True))
        return markets_from_grid(grid, lh, la)


def _rows(H, rh, ra, hi, ai, o_s, o_t, bs, bt):
    """Strength / tempo loadings of the home-side (rh) and away-side (ra) count rows."""
    H[rh, o_s + hi] += 0.5 * bs
    H[rh, o_s + ai] -= 0.5 * bs
    H[ra, o_s + ai] += 0.5 * bs
    H[ra, o_s + hi] -= 0.5 * bs
    for row in (rh, ra):
        H[row, o_t + hi] += 0.5 * bt
        H[row, o_t + ai] += 0.5 * bt


def _poisson_irls(X, y, w, n_free, ridge, iters=25) -> np.ndarray:
    """Weighted Poisson regression by Newton steps; the first `n_free` columns (division
    intercepts) are unpenalised, the rest carry a ridge penalty."""
    p = X.shape[1]
    beta = np.zeros(p)
    mean = max(float(np.average(y, weights=w)) if w.sum() > 0 else 1.0, 0.1)
    beta[:n_free] = math.log(mean)
    pen = np.full(p, float(ridge))
    pen[:n_free] = 1e-6
    for _ in range(iters):
        mu = np.exp(np.clip(X @ beta, -5, 3))
        grad = X.T @ (w * (y - mu)) - pen * beta
        hess = (X * (w * mu)[:, None]).T @ X + np.diag(pen)
        step = np.linalg.solve(hess, grad)
        beta = beta + step
        if np.abs(step).max() < 1e-8:
            break
    return beta


def _fit_rho(yh, ya, lh, la, w) -> float:
    """Dixon-Coles rho maximising the weighted low-score likelihood of given rates."""
    if w.sum() <= 0:
        return 0.0
    m00 = (yh == 0) & (ya == 0)
    m01 = (yh == 0) & (ya == 1)
    m10 = (yh == 1) & (ya == 0)
    m11 = (yh == 1) & (ya == 1)

    def nll(rho):
        v = (
            (w[m00] * np.log(np.clip(1 - lh[m00] * la[m00] * rho, 1e-10, None))).sum()
            + (w[m01] * np.log(np.clip(1 + lh[m01] * rho, 1e-10, None))).sum()
            + (w[m10] * np.log(np.clip(1 + la[m10] * rho, 1e-10, None))).sum()
            + w[m11].sum() * np.log(max(1 - rho, 1e-10))
        )
        return -v

    res = minimize_scalar(nll, bounds=(-RHO_LIMIT, RHO_LIMIT), method="bounded")
    return float(res.x)


# Settings tuned by coordinate search on 2021/22, 2022/23 and 2023/24 pooled (five leagues;
# a one-pass replay of the filter stood in for the walk-forward during the search).
EKF_GOALS = {
    "q_s": 9.6e-5,
    "q_t": 4.5e-5,
    "p0_s": 1.28,
    "p0_t": 0.18,
    "season_var_s": 0.01,
    "season_var_t": 0.005,
    "season_shrink_s": 0.98,
    "q_mu": 2e-5,
    "q_h": 2e-7,
    "p0_h": 0.00125,
    "r_scale": 1.15,
    "y_cap": 5.0,
    "var_correction": 1.0,
    "rho_shift": -0.05,
}
EKF_SHOTS = {
    "q_s": 1.35e-4,
    "q_t": 4.5e-5,
    "p0_s": 2.5,
    "p0_t": 0.56,
    "season_var_s": 0.0025,
    "season_var_t": 0.0072,
    "season_shrink_s": 1.0,
    "q_mu": 3e-8,
    "q_h": 3e-8,
    "p0_mu": 0.08,
    "p0_h": 0.00125,
    "season_var_mu": 0.00125,
    "level_corr": 0.8,
    "r_scale": 1.3,
    "y_cap": 5.0,
    "r_sot": 2.0,
    "sot_s": 0.92,
    "sot_t": 0.6,
    "sot_resid": 0.01,
    "r_corners": 4.0,
    "var_correction": 1.5,
    "rho_shift": -0.05,
}
PI = {
    "lr": 0.053,
    "gamma": 0.85,
    "tempo_lr": 0.024,
    "promo_decay": 48.0,
    "xi": 0.001,
    "ridge": 1.0,
    "warmup_days": 200.0,
    "sot_blend": 0.6,
    "tempo_sot": 0.5,
}


CANDIDATES = {
    "dynamic_ratings_ekf_goals": lambda: DynamicPoisson("dynamic_ratings_ekf_goals", **EKF_GOALS),
    "dynamic_ratings_ekf": lambda: DynamicPoisson("dynamic_ratings_ekf", **EKF_SHOTS),
    "dynamic_ratings_pi": lambda: PiRatings("dynamic_ratings_pi", **PI),
    # mixture weights chosen on the pooled seasons from the components' ledgers
    "dynamic_ratings_blend": lambda: GridBlend(
        [DynamicPoisson(**EKF_SHOTS), PiRatings(**PI)], [0.8, 0.2], name="dynamic_ratings_blend"
    ),
    "dynamic_ratings_blend3": lambda: GridBlend(
        [DynamicPoisson(**EKF_SHOTS), DynamicPoisson(**EKF_GOALS), PiRatings(**PI)],
        [0.6, 0.2, 0.2],
        name="dynamic_ratings_blend3",
    ),
}
