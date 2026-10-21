"""Dynamic team ratings (model-lab family `dynamic_ratings`).

Draft: extended-Kalman-filter Poisson model with date-grouped updates, optional
shots-on-target observations; pi-ratings with a Poisson regression map.
"""

import math

import numpy as np
import pandas as pd
from scipy.optimize import minimize_scalar

from footy.modeling.base import ALL_MARKETS
from footy.modeling.data import SECOND_TIER
from footy.modeling.grid import (
    MAX_GOALS,
    MarketProbs,
    markets_from_grid,
    mix_grids,
    poisson_grid,
)

TIER1 = set(SECOND_TIER)
TIER2 = set(SECOND_TIER.values())
DIVS = sorted(TIER1 | TIER2)
_GRID_SHAPE = (MAX_GOALS + 1, MAX_GOALS + 1)
DIDX = {d: i for i, d in enumerate(DIVS)}
IS_T2 = np.array([d in TIER2 for d in DIVS])
EPOCH = pd.Timestamp("2000-01-01")
# extra count statistics that measure the same latent ratings: columns, starting level
STREAMS = {
    "sot": ("home_sot", "away_sot", math.log(4.3)),
    "shots": ("home_shots", "away_shots", math.log(12.0)),
    "corners": ("home_corners", "away_corners", math.log(5.0)),
}

# COVID-19 periods without spectators, by division: a fixed calendar of public facts (to
# within days), not derived from match data. Matches in them get their own home term.
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
    "q_s": 4e-5,  # daily random-walk variance of strength
    "q_t": 5e-6,  # ... of tempo
    "p0_s": 0.08,  # prior variance of a new team's strength
    "p0_t": 0.01,
    "season_var_s": 0.01,  # extra variance every summer
    "season_var_t": 0.002,
    "season_shrink_s": 0.85,  # summer regression towards the division mean
    "season_shrink_t": 0.7,
    "season_var_mu": 0.0,  # extra summer variance of division goal / count levels
    "season_var_h": 0.0,  # ... and of division home advantages
    "level_corr": 0.0,  # correlation of the summer level shocks within a country
    "q_mu": 2e-6,  # daily drift of a division's goal level
    "q_h": 2e-6,  # ... and of its home advantage
    "p0_mu": 0.02,
    "p0_h": 0.02,
    "tier2_offset": 0.4,  # tier-2 sides start this much weaker (strength units)
    "newcomer_s": -0.15,  # a side new to the data starts below its division's mean
    "r_scale": 1.0,  # measurement-noise scale (quasi-Poisson dispersion)
    "r_tier2": 1.0,  # extra scale for second-tier matches
    "iterations": 1,  # >1: iterated EKF (posterior mode)
    "var_correction": 0.0,  # predict exp(eta + c·var(eta)/2)
    "rho_days": 1000.0,
    "warmup_days": 300.0,
    "team_home": 0.0,  # prior variance of team-specific home advantage (0: none)
    "q_team_home": 0.0,
    # shots on target as a second, noisier measurement of the same latent ratings
    "r_sot": 0.0,  # SoT measurement-noise scale (0: SoT unused)
    "sot_s": 0.67,  # loading of SoT log-rates on strength (goals load 1)
    "sot_t": 1.0,  # ... on tempo
    "sot_resid": 0.0,  # prior variance of team SoT-specific strength / tempo (0: none)
    "q_sot_resid": 0.0,
    "r_shots": 0.0,  # total shots as a third measurement (0: unused)
    "shots_s": 0.4,
    "shots_t": 0.6,
    "r_corners": 0.0,  # corners as a fourth measurement (0: unused)
    "corners_s": 0.3,
    "corners_t": 0.3,
    "covid": False,  # separate home term for matches without spectators
    "r_red": 1.0,  # measurement-noise scale for matches with a red card
    "y_cap": 0.0,  # cap goal counts at this value in the update (0: no cap)
    # data-driven starting ratings: a first pass over the window's first `prewarm_days`
    # gives each team's starting mean (variance p0 × prewarm_p) for the full pass
    "prewarm_days": 0.0,
    "prewarm_p": 1.0,
    # predictive mixture: integrate the Poisson grid over the uncertainty of the two
    # log-rates (state covariance × quad_scale, plus match-level noise) with quad² points
    "quad": 0,
    "quad_scale": 1.0,
    "match_var": 0.0,  # independent match-level log-rate noise (also in the update)
    "match_cov": 0.0,  # shared (home and away) match-level log-rate noise
}


class DynamicPoisson:
    """Attack / defence as random walks, filtered match by match (extended Kalman filter on
    the log-rates, a Gaussian approximation to the Koopman & Lit state-space model).

    Per team: strength s (moves the goal difference) and tempo t (moves the goal total):

        log E[home goals] = mu_k + h_k + (s_i − s_j)/2 + (t_i + t_j)/2
        log E[away goals] = mu_k       + (s_j − s_i)/2 + (t_i + t_j)/2

    with division goal level mu_k and home advantage h_k also in the state (slow drift). The
    state has one full covariance over every team and division term, so a result moves
    everyone who is correlated with the two teams (e.g. a promoted side's old division).
    Matches on the same day are one joint update.

    With `r_sot` > 0, shots on target are a second measurement of the same ratings:

        log E[home SoT] = nu_k + g_k + b_s·(s_i − s_j)/2 + b_t·(t_i + t_j)/2 (+ residuals)
    """

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
        teams = sorted(set(h.home_team) | set(h.away_team))
        warm = None
        if c["prewarm_days"] > 0 and len(h):
            self.start(teams)
            self.process(h[h["date"] < h["date"].iloc[0] + pd.Timedelta(days=c["prewarm_days"])])
            warm = (self.m_.copy(), self.seen_.copy())
        self.start(teams, warm)
        pre_lh, pre_la = self.process(h)
        day = (h["date"] - EPOCH).dt.days.to_numpy()
        cut_day = (cutoff.tz_convert("UTC").tz_localize(None) - EPOCH).days
        use = (day >= day[0] + c["warmup_days"]) if len(h) else np.zeros(0, bool)
        w = np.exp(-(cut_day - day) / c["rho_days"]) * use
        yh, ya = h.home_goals.to_numpy(float), h.away_goals.to_numpy(float)
        self.rho_ = _fit_rho(yh, ya, pre_lh, pre_la, w)
        self.pre_eta_ = (np.log(np.maximum(pre_lh, 1e-9)), np.log(np.maximum(pre_la, 1e-9)))

    def start(self, teams: list[str], warm=None) -> None:
        """Empty state for these teams (each joins the filter at its first match). `warm`:
        (state mean, seen) of an earlier pass, used for starting values."""
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
        self.warm_ = warm
        if warm is not None:  # division terms start where the first pass ended
            m[2 * T :] = warm[0][2 * T :]
            if "th" in L:
                m[L["th"] : L["th"] + T] = 0.0
            for key in ("u", "v"):
                if key in L:
                    m[L[key] : L[key] + T] = 0.0
        self.m_, self.P_ = m, P
        self.seen_ = np.zeros(T, bool)
        self.last_div_ = np.full(T, -1)
        self.cur_day_ = None
        self.last_season_ = None

    def process(self, h: pd.DataFrame) -> tuple[np.ndarray, np.ndarray]:
        """Filter matches `h` (sorted by date) into the state; returns the one-step-ahead
        expected goals of each match, computed before its own update."""
        c, L, T = self.cfg, self.L_, self.T_
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
        mv, mc = c["match_var"], c["match_cov"]
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
            mm = len(g)
            nobs = 2 * mm * (1 + len(streams))
            H = np.zeros((nobs, n))
            r_ = np.arange(mm)
            gh, ga = 2 * r_, 2 * r_ + 1  # goal rows
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
                vs = np.where(ok, c[f"r_{k}"] * rw[g], 1e12)
                var_scale[sh_], var_scale[sa_] = vs, vs
                extra.append((sh_, sa_, ok, xh[g], xa[g]))
            PHt = P @ H.T
            HPH = H @ PHt
            eta0 = H @ m
            lam0 = np.exp(eta0)
            pre_lh[g], pre_la[g] = lam0[gh], lam0[ga]
            for sh_, sa_, ok, xh, xa in extra:  # missing: the observation is its prediction
                y[sh_] = np.where(ok, xh, lam0[sh_])
                y[sa_] = np.where(ok, xa, lam0[sa_])
            z = m
            for _ in range(int(c["iterations"])):
                eta = H @ z
                lz = np.exp(eta)
                # working response and its variance (IRLS form of the EKF update)
                work = eta + (y - lz) / lz
                R = np.diag(var_scale / lz)
                if mv or mc:
                    R[gh, gh] += mv + mc
                    R[ga, ga] += mv + mc
                    R[gh, ga] += mc
                    R[ga, gh] += mc
                gain = np.linalg.inv(HPH + R)
                z = m + PHt @ (gain @ (work - eta0))
            m = z
            P = P - PHt @ gain @ PHt.T
            P = 0.5 * (P + P.T)
        self.m_, self.P_ = m, P
        return pre_lh, pre_la

    def _add_team(self, m, P, t_, is_tier2, seen, last_div, dk):
        c, L = self.cfg, self.L_
        # a side first seen after the start (promoted from below the data) gets the
        # newcomer prior relative to the teams of its division; at the start of the
        # window, tier-2 sides start below tier 1.
        members = np.flatnonzero(seen & (last_div == dk))
        scale = 1.0
        if self.warm_ is not None and self.warm_[1][t_]:
            m[t_], m[L["t"] + t_] = self.warm_[0][t_], self.warm_[0][L["t"] + t_]
            scale = c["prewarm_p"]
        elif len(members) >= 6:
            m[t_] = m[members].mean() + c["newcomer_s"]
        else:
            m[t_] = -c["tier2_offset"] if is_tier2 else 0.0
        for key, var in (
            ("s", c["p0_s"] * scale),
            ("t", c["p0_t"] * scale),
            ("th", c["team_home"]),
            ("u", c["sot_resid"]),
            ("v", c["sot_resid"]),
        ):
            if key in L:
                P[L[key] + t_, L[key] + t_] = var
        seen[t_] = True

    def _season_step(self, m, P, seen, last_div):
        """Summer: ratings regress towards their division's mean and become less certain."""
        c, L, T = self.cfg, self.L_, self.T_
        n = len(m)
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
        D = len(DIVS)
        rho_l = c["level_corr"]
        for key in ("mu", *(f"lvl_{k}" for k in STREAMS)):
            if key in L:
                sv[L[key] : L[key] + D] = c["season_var_mu"] * (1 - rho_l)
        if rho_l and c["season_var_mu"]:
            # one shared shock per country moves its goal and count levels together
            for top, second in SECOND_TIER.items():
                idx = np.array(
                    [
                        L[key] + DIDX[d]
                        for key in ("mu", *(f"lvl_{k}" for k in STREAMS))
                        if key in L
                        for d in (top, second)
                    ]
                )
                P[np.ix_(idx, idx)] += c["season_var_mu"] * rho_l
        for key in ("h", *(f"home_{k}" for k in STREAMS)):
            if key in L:
                sv[L[key] : L[key] + D] = c["season_var_h"]
        P[np.arange(n), np.arange(n)] += sv
        return m, P

    # ---- prediction ------------------------------------------------------------------

    def rates(self, fixtures: pd.DataFrame) -> tuple[np.ndarray, np.ndarray]:
        eta, cov = self._moments(fixtures)
        c = self.cfg
        if c["var_correction"]:
            eta = eta + 0.5 * c["var_correction"] * np.stack([cov[:, 0, 0], cov[:, 1, 1]], 1)
        return np.exp(eta[:, 0]), np.exp(eta[:, 1])

    def _moments(self, fixtures: pd.DataFrame) -> tuple[np.ndarray, np.ndarray]:
        """Mean (N, 2) and covariance (N, 2, 2) of each fixture's (home, away) log-rates."""
        c, L, T = self.cfg, self.L_, self.T_
        m, P = self.m_, self.P_
        seen, last_div = self.seen_, self.last_div_
        season = fixtures.season.str[:2].astype(int).to_numpy()
        if season.max() != self.last_season_:
            m, P = self._season_step(m, P, seen, last_div)
        n = len(m)
        N = len(fixtures)
        H = np.zeros((2 * N, n))
        extra = np.zeros(2 * N)  # newcomers' prior means (unseen teams)
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
        eta = (H @ m + extra).reshape(N, 2)
        H3 = H.reshape(N, 2, n)
        cov = np.einsum("kin,nm,kjm->kij", H3, P, H3)
        cov[:, 0, 0] += evar[0::2]
        cov[:, 1, 1] += evar[1::2]
        return eta, cov

    def predict(self, fixtures: pd.DataFrame) -> MarketProbs:
        c = self.cfg
        if not c["quad"]:
            lh, la = self.rates(fixtures)
            return markets_from_grid(poisson_grid(lh, la, self.rho_), lh, la)
        eta, cov = self._moments(fixtures)
        cov = cov * c["quad_scale"]
        cov[:, 0, 0] += c["match_var"] + c["match_cov"]
        cov[:, 1, 1] += c["match_var"] + c["match_cov"]
        cov[:, 0, 1] += c["match_cov"]
        cov[:, 1, 0] += c["match_cov"]
        x, w = np.polynomial.hermite.hermgauss(int(c["quad"]))
        z1, z2 = np.meshgrid(x * math.sqrt(2.0), x * math.sqrt(2.0), indexing="ij")
        wq = (np.outer(w, w) / math.pi).ravel()
        Z = np.stack([z1.ravel(), z2.ravel()])  # (2, Q)
        chol = np.linalg.cholesky(cov + 1e-12 * np.eye(2))  # (N, 2, 2)
        pts = eta[:, :, None] + chol @ Z  # (N, 2, Q)
        lh, la = np.exp(pts[:, 0, :]).ravel(), np.exp(pts[:, 1, :]).ravel()
        g = poisson_grid(lh, la, self.rho_).reshape(len(eta), len(wq), *_GRID_SHAPE)
        grid = np.tensordot(g, wq, axes=([1], [0]))
        mean_h = (np.exp(pts[:, 0, :]) * wq).sum(1)
        mean_a = (np.exp(pts[:, 1, :]) * wq).sum(1)
        return markets_from_grid(grid, mean_h, mean_a)


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
    "warmup_days": 300.0,
    "rho_days": 1000.0,
    "sot_blend": 0.0,  # pi-ratings learn from (1-a)·GD + a·(SoT-based expected GD)
    "tempo_sot": 0.0,  # tempo learns from (1-a)·total + a·(SoT-based expected total)
    "gd_sq": False,  # add ĝ·|ĝ| as a regressor
    "ekf": None,  # dict of DynamicPoisson options: add its log-rates as features
}


class PiRatings:
    """Pi-ratings (Constantinou & Fenton 2013) mapped to goal expectancies by a Poisson
    regression fitted on the training history.

    Every team has a home and an away rating, in goal-difference units on a log scale;
    after each match both teams' ratings move by lr·ψ(error) (the other venue's rating by
    gamma times that). The expected goal difference ĝ = ĝ_home − ĝ_away of a fixture, a
    goal-total ("tempo") rating per team, and fading promoted / relegated flags are the
    regressors of two Poisson regressions (home goals, away goals) with an intercept per
    division, fitted by weighted IRLS on the pre-match features of every history match.

    With `ekf`, the one-step-ahead log-rates of a DynamicPoisson filter are added as two
    more regressors, so the regression stacks the two rating systems.
    """

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
        gd_obs = yh - ya
        tot_obs = yh + ya
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
        feats = np.zeros((N, 4))  # ĝ, tempo sum, promoted / relegated flags (home, away)
        flags = np.zeros((N, 4))
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
            feats[k, 0], feats[k, 1] = ghat, th + ta
            flags[k] = self._flags(ht, at, dv, sk)
            # pi-rating update
            err = gd_obs[k] - ghat
            psi = c["c"] * math.log10(1.0 + abs(err))
            ph = psi if err > 0 else -psi
            self.rh_[ht] = rh + lr * ph
            self.ra_[ht] = self.ra_.get(ht, 0.0) + gam * lr * ph
            self.ra_[at] = ra - lr * ph
            self.rh_[at] = self.rh_.get(at, 0.0) - gam * lr * ph
            # tempo: goal total relative to the division's running mean
            tot = tot_obs[k]
            dm = self.div_total_.get(dv, 2.7)
            resid = tot - dm
            self.div_total_[dv] = dm + 0.01 * resid
            self.tempo_[ht] = th + tl * (resid / 2.0 - th)
            self.tempo_[at] = ta + tl * (resid / 2.0 - ta)
            self.n_in_season_[ht] = self.n_in_season_.get(ht, 0) + 1
            self.n_in_season_[at] = self.n_in_season_.get(at, 0) + 1
        self.last_season_ = cur_season
        feats[:, 2:] = flags[:, :2] - flags[:, 2:]  # promoted minus relegated, per side
        X = self._design(feats)
        if c["ekf"] is not None:
            self.ekf_ = DynamicPoisson(**c["ekf"])
            self.ekf_.fit(history, cutoff)
            eh, ea = self.ekf_.pre_eta_
            X = np.column_stack([X, eh, ea])
        divs = sorted(set(div))
        self.divs_ = divs
        D = np.array([[d == x for x in divs] for d in div], float).reshape(N, len(divs))
        day = (h["date"] - EPOCH).dt.days.to_numpy()
        cut_day = (cutoff.tz_convert("UTC").tz_localize(None) - EPOCH).days
        use = day >= (day[0] + c["warmup_days"] if N else 0)
        w = np.exp(-c["xi"] * (cut_day - day)) * use
        self.beta_h_ = _poisson_irls(np.column_stack([D, X]), yh, w, len(divs), c["ridge"])
        self.beta_a_ = _poisson_irls(np.column_stack([D, X]), ya, w, len(divs), c["ridge"])
        Z = np.column_stack([D, X])
        lh, la = np.exp(Z @ self.beta_h_), np.exp(Z @ self.beta_a_)
        self.rho_ = _fit_rho(yh, ya, lh, la, np.exp(-(cut_day - day) / c["rho_days"]) * use)

    def _design(self, feats: np.ndarray) -> np.ndarray:
        if self.cfg["gd_sq"]:
            return np.column_stack([feats, feats[:, 0] * np.abs(feats[:, 0])])
        return feats

    def _flags(self, ht, at, dv, sk) -> np.ndarray:
        """Promoted (home, away) and relegated (home, away) flags, fading with matches."""
        out = np.zeros(4)
        for j, t_ in enumerate((ht, at)):
            prev = self.team_div_.get(t_, {}).get(sk - 1)
            if prev is None or prev == dv:
                continue
            fade = math.exp(-self.n_in_season_.get(t_, 0) / self.cfg["promo_decay"])
            if dv in TIER1 and prev in TIER2:
                out[j] = fade
            elif dv in TIER2 and prev in TIER1:
                out[2 + j] = fade
        return out

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
        N = len(fixtures)
        feats = np.zeros((N, 4))
        for k, (ht, at, dv, sk) in enumerate(
            zip(fixtures.home_team, fixtures.away_team, fixtures.division, season, strict=True)
        ):
            feats[k, 0] = self._gd(self.rh_.get(ht, 0.0)) - self._gd(self.ra_.get(at, 0.0))
            feats[k, 1] = self.tempo_.get(ht, 0.0) + self.tempo_.get(at, 0.0)
            prev = {t_: self.team_div_.get(t_, {}) for t_ in (ht, at)}
            # this season's division is the fixture's, whatever the history says
            for t_ in (ht, at):
                prev[t_] = {**prev[t_], sk: dv}
            saved = self.team_div_
            self.team_div_ = {**saved, **prev}
            fl = self._flags(ht, at, dv, sk)
            self.team_div_ = saved
            feats[k, 2:] = fl[:2] - fl[2:]
        X = self._design(feats)
        if self.cfg["ekf"] is not None:
            lh_e, la_e = self.ekf_.rates(fixtures)
            X = np.column_stack([X, np.log(lh_e), np.log(la_e)])
        D = np.array([[d == x for x in self.divs_] for d in fixtures.division], float)
        Z = np.column_stack([D.reshape(N, len(self.divs_)), X])
        return np.exp(Z @ self.beta_h_), np.exp(Z @ self.beta_a_)

    def predict(self, fixtures: pd.DataFrame) -> MarketProbs:
        lh, la = self.rates(fixtures)
        return markets_from_grid(poisson_grid(lh, la, self.rho_), lh, la)


def _poisson_irls(X, y, w, n_free, ridge, iters=25) -> np.ndarray:
    """Weighted Poisson regression by Newton steps; the first `n_free` columns (division
    intercepts) are unpenalised, the rest carry a ridge penalty."""
    p = X.shape[1]
    beta = np.zeros(p)
    mean = max(float(np.average(y, weights=w)) if w.sum() > 0 else 1.0, 0.1)
    beta[:n_free] = math.log(mean)
    pen = np.full(p, ridge)
    pen[:n_free] = 1e-6
    for _ in range(iters):
        eta = np.clip(X @ beta, -5, 3)
        mu = np.exp(eta)
        grad = X.T @ (w * (y - mu)) - pen * beta
        hess = (X * (w * mu)[:, None]).T @ X + np.diag(pen)
        step = np.linalg.solve(hess, grad)
        beta = beta + step
        if np.abs(step).max() < 1e-8:
            break
    return beta


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
    """Add strength / tempo loadings for the home-side and away-side count rows."""
    H[rh, o_s + hi] += 0.5 * bs
    H[rh, o_s + ai] -= 0.5 * bs
    H[ra, o_s + ai] += 0.5 * bs
    H[ra, o_s + hi] -= 0.5 * bs
    for row in (rh, ra):
        H[row, o_t + hi] += 0.5 * bt
        H[row, o_t + ai] += 0.5 * bt


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

    res = minimize_scalar(nll, bounds=(-0.25, 0.25), method="bounded")
    return float(res.x)


CANDIDATES = {
    "dynamic_ratings_ekf": lambda: DynamicPoisson(),
}
