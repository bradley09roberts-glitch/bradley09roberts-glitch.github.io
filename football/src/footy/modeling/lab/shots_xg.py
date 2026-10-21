"""Shots and shots on target as an xG stand-in (model-lab family `shots_xg`).

One joint, time-weighted Poisson fit over several match statistics (total shots, shots on
target, goals), on a league and its second tier. Each statistic k "owns" a pair of team
factors, written in strength / tempo form rather than attack / defence:

    strength s_i = how much team i out-produces its opponents   (moves the goal difference)
    tempo    t_i = how open team i's matches are                (moves the goal total)

For team i attacking team j, with statistics ordered from most to least numerous:

    log E[stat_k] = C_k[division(, season)] + home_k[division] · [i at home]
                    + Σ_{f ≤ k}  bs_kf · (s_f,i − s_f,j)  +  bt_kf · (t_f,i + t_f,j)

with bs_kk = bt_kk = 1. Earlier statistics' factors flow into later ones: goals inherit the
shot-on-target strength through a free coefficient bs (fitted ≈ 1.3–1.7, because better
sides also take better chances), and the shot tempo through a fixed, damped bt (free tempo
transfers made the totals worse in validation). The goals equation keeps its own residual
"finishing" factor, so team conversion skill is estimated but shrunk hard to the league.

Ridge penalties are set per factor and per direction. The validation finding that matters
most: strength wants little shrinkage, tempo a lot (the spread of predicted totals across
matches is mostly noise). Goals also enter the Poisson likelihood as a blend with a
shot-based expected-goals proxy (`blend`): proxy = a·SoT + b·off-target shots, with a and b
fitted by least squares per division and season (shot definitions change between seasons,
e.g. Serie A before 2021/22), shrunk to the division-wide values. The Dixon–Coles low-score
correction (rho) is applied to the actual goals.

Shots or SoT may be NaN, row by row or entirely (synthetic data): rows without the statistic
carry no weight in its likelihood, a statistic with no data at all is frozen out, and the
blend falls back to goals. With no shot data the model reduces to the goals-only
strength / tempo model (`shots_xg_noshots`, kept registered as the ablation control).

Uses only the DataFrames passed to fit/predict: no reference files (no closed-doors table;
in validation it made no difference) and no randomness.
"""

import numpy as np
import pandas as pd
from scipy.optimize import minimize

from footy.modeling.base import ALL_MARKETS
from footy.modeling.data import SECOND_TIER
from footy.modeling.dixon_coles import _tau_terms
from footy.modeling.grid import MarketProbs, markets_from_grid, poisson_grid

TIER1 = set(SECOND_TIER)
TIER2 = set(SECOND_TIER.values())
COLUMNS = {
    "shots": ("home_shots", "away_shots"),
    "sot": ("home_sot", "away_sot"),
    "goals": ("home_goals", "away_goals"),
}
DEFAULT_WEIGHT = {"shots": 0.3, "sot": 0.5, "goals": 1.0}  # quasi-likelihood weights


class ShotsXG:
    markets = ALL_MARKETS

    def __init__(
        self,
        stats: tuple[str, ...] = ("sot", "goals"),
        weights: dict[str, float] | None = None,
        reg_strength: dict[str, float] | None = None,
        reg_tempo: dict[str, float] | None = None,
        xi: float = 0.003,
        tier2_weight: float = 0.5,
        season_rw: float | None = None,
        tempo_transfer: float | None = 0.5,
        blend: float = 0.25,
        proxy: str = "both",
        proxy_prior: float = 200.0,
        name: str = "shots_xg",
    ):
        if stats[-1] != "goals" or any(k not in COLUMNS for k in stats):
            raise ValueError(f"stats must be drawn from {sorted(COLUMNS)} and end with goals")
        self.stats = tuple(stats)
        self.weights = {k: (weights or {}).get(k, DEFAULT_WEIGHT[k]) for k in self.stats}
        self.weights["goals"] = 1.0
        self.reg_strength = {k: (reg_strength or {}).get(k, 4.0) for k in self.stats}
        self.reg_tempo = {k: (reg_tempo or {}).get(k, 20.0) for k in self.stats}
        self.xi = xi
        self.tier2_weight = tier2_weight
        self.season_rw = season_rw  # random-walk penalty linking a division's seasons
        self.tempo_transfer = tempo_transfer  # None: free coefficients
        self.blend = blend
        self.proxy = proxy  # "sot": SoT x conversion; "both": SoT and off-target shots
        self.proxy_prior = proxy_prior
        self.name = name
        self._x: dict[str, float] = {}  # warm start, keyed by parameter name

    def params(self) -> dict:
        return {
            "stats": list(self.stats),
            "weights": self.weights,
            "reg_strength": self.reg_strength,
            "reg_tempo": self.reg_tempo,
            "xi": self.xi,
            "tier2_weight": self.tier2_weight,
            "season_rw": self.season_rw,
            "tempo_transfer": self.tempo_transfer,
            "blend": self.blend,
            "proxy": self.proxy,
            "proxy_prior": self.proxy_prior,
        }

    # ---- fitting ---------------------------------------------------------------------

    def fit(self, history: pd.DataFrame, cutoff) -> None:
        f, x0, bounds, names, layout = self._problem(history, cutoff)
        res = minimize(f, x0, jac=True, method="L-BFGS-B", bounds=bounds, options={"maxiter": 2000})
        self.x_ = res.x
        self._x = dict(zip(names, res.x, strict=True))
        self.layout_ = layout
        self.rho_ = float(res.x[layout["sl"]["rho"]][0])
        self.converged_ = bool(res.success)

    def _problem(self, history: pd.DataFrame, cutoff):
        """Objective (negative penalised log-likelihood, with gradient), start, bounds."""
        h = history[history.division.isin(TIER1 | TIER2)]
        S = self.stats
        teams = sorted(set(h.home_team) | set(h.away_team))
        tidx = {t: i for i, t in enumerate(teams)}
        T, N = len(teams), len(h)
        divs = sorted(h.division.unique())
        didx = {d: i for i, d in enumerate(divs)}
        D = len(divs)
        per_season = self.season_rw is not None
        keys = list(zip(h.division, h.season if per_season else ["all"] * N, strict=True))
        groups = sorted(set(keys))
        gidx = {g: i for i, g in enumerate(groups)}
        G = len(groups)
        links = [
            (gidx[a], gidx[b]) for a, b in zip(groups, groups[1:], strict=False) if a[0] == b[0]
        ]
        pa = np.array([p[0] for p in links], int)
        pb = np.array([p[1] for p in links], int)

        hi = h.home_team.map(tidx).to_numpy()
        ai = h.away_team.map(tidx).to_numpy()
        di = h.division.map(didx).to_numpy()
        gi = np.array([gidx[k] for k in keys], int)
        days = (cutoff.tz_convert("UTC").tz_localize(None) - h["date"]).dt.days.to_numpy()
        wt_match = np.exp(-self.xi * days) * np.where(
            h.division.isin(TIER2).to_numpy(), self.tier2_weight, 1.0
        )

        # Two rows per match: the home side attacking, then the away side attacking.
        att = np.concatenate([hi, ai])
        dfd = np.concatenate([ai, hi])
        home = np.concatenate([np.ones(N, bool), np.zeros(N, bool)])
        hd = np.concatenate([di, di])
        grp = np.concatenate([gi, gi])
        w2 = np.concatenate([wt_match, wt_match])
        y, wt, has = {}, {}, {}
        for k in S:
            a, b = COLUMNS[k]
            v = np.concatenate([h[a].to_numpy(float), h[b].to_numpy(float)])
            ok = ~np.isnan(v)
            has[k] = bool(ok.any())
            y[k] = np.nan_to_num(v)
            wt[k] = w2 * self.weights[k] * ok
        if self.blend > 0:
            y["goals"] = self._blend_goals(h, y["goals"])
        gh, ga = h.home_goals.to_numpy(), h.away_goals.to_numpy()

        names: list[str] = []
        sl: dict[str, slice] = {}

        def add(key, labels):
            sl[key] = slice(len(names), len(names) + len(labels))
            names.extend(labels)

        for k in S:
            add(f"s_{k}", [f"s_{k}:{t}" for t in teams])
            add(f"t_{k}", [f"t_{k}:{t}" for t in teams])
        for k in S:
            add(f"C_{k}", [f"C_{k}:{g[0]}:{g[1]}" for g in groups])
            add(f"H_{k}", [f"H_{k}:{d}" for d in divs])
        transfers = [(S[a], S[b], dr) for a in range(len(S)) for b in range(a) for dr in "st"]
        add("B", [f"b:{k}:{f}:{dr}" for k, f, dr in transfers])
        add("rho", ["rho"])
        bpos = {key: sl["B"].start + j for j, key in enumerate(transfers)}
        P = len(names)

        x0 = np.array([self._x.get(n, np.nan) for n in names])
        if np.isnan(x0).any():
            init = np.zeros(P)
            for k in S:
                m = wt[k] > 0
                level = np.log(max(np.average(y[k][m], weights=wt[k][m]), 0.1)) if m.any() else 0.0
                init[sl[f"C_{k}"]] = level
                init[sl[f"H_{k}"]] = 0.12
                for j, g in enumerate(groups):  # a new season starts from the last one
                    prev = [v for n, v in self._x.items() if n.startswith(f"C_{k}:{g[0]}:")]
                    if prev:
                        init[sl[f"C_{k}"].start + j] = prev[-1]
            init[sl["B"]] = 1.0
            init[sl["rho"]] = -0.05
            x0 = np.where(np.isnan(x0), init, x0)

        lo, hi_ = np.full(P, -np.inf), np.full(P, np.inf)
        lo[sl["B"]], hi_[sl["B"]] = -3.0, 5.0
        lo[sl["rho"]], hi_[sl["rho"]] = -0.25, 0.25
        for (k, f_, dr), j in bpos.items():
            fixed = None
            if not (has[k] and has[f_]):
                fixed = 1.0  # multiplies a frozen, all-zero factor: harmless
            elif dr == "t" and self.tempo_transfer is not None:
                fixed = self.tempo_transfer
            if fixed is not None:
                lo[j] = hi_[j] = x0[j] = fixed
        for k in S:
            if not has[k]:  # no data for this statistic: freeze its parameters
                for key in (f"C_{k}", f"H_{k}", f"s_{k}", f"t_{k}"):
                    lo[sl[key]] = hi_[sl[key]] = x0[sl[key]]
        x0 = np.clip(x0, lo, hi_)
        bounds = [
            (None if np.isinf(a) else a, None if np.isinf(b) else b)
            for a, b in zip(lo, hi_, strict=True)
        ]

        regs = [(f"s_{k}", self.reg_strength[k]) for k in S]
        regs += [(f"t_{k}", self.reg_tempo[k]) for k in S]
        rw = self.season_rw

        def f(x):
            obj = 0.0
            g = np.zeros_like(x)
            ds = {k: x[sl[f"s_{k}"]][att] - x[sl[f"s_{k}"]][dfd] for k in S}
            dt = {k: x[sl[f"t_{k}"]][att] + x[sl[f"t_{k}"]][dfd] for k in S}
            rho = x[sl["rho"]][0]
            for kk, k in enumerate(S):
                C, H = x[sl[f"C_{k}"]], x[sl[f"H_{k}"]]
                eta = C[grp] + np.where(home, H[hd], 0.0)
                terms = []
                for f_ in S[: kk + 1]:
                    if f_ == k:
                        cs = ct = 1.0
                    else:
                        cs, ct = x[bpos[(k, f_, "s")]], x[bpos[(k, f_, "t")]]
                    terms.append((f_, cs, ct))
                    eta = eta + cs * ds[f_] + ct * dt[f_]
                lam = np.exp(eta)
                r = wt[k] * (y[k] - lam)  # d loglik / d eta
                obj -= (wt[k] * (y[k] * eta - lam)).sum()
                if k == "goals":
                    tau, d_rho, d_eh, d_ea = _tau_terms(gh, ga, lam[:N], lam[N:], rho)
                    obj -= (wt_match * np.log(tau)).sum()
                    r = r + np.concatenate([wt_match * d_eh, wt_match * d_ea])
                    g[sl["rho"]] = -(wt_match * d_rho).sum()
                g[sl[f"C_{k}"]] -= np.bincount(grp, r, G)
                g[sl[f"H_{k}"]] -= np.bincount(hd[home], r[home], D)
                ra, rd = np.bincount(att, r, T), np.bincount(dfd, r, T)
                for f_, cs, ct in terms:
                    g[sl[f"s_{f_}"]] -= cs * (ra - rd)
                    g[sl[f"t_{f_}"]] -= ct * (ra + rd)
                    if f_ != k:
                        g[bpos[(k, f_, "s")]] -= (r * ds[f_]).sum()
                        g[bpos[(k, f_, "t")]] -= (r * dt[f_]).sum()
            for key, lam_reg in regs:
                v = x[sl[key]]
                obj += 0.5 * lam_reg * (v @ v)
                g[sl[key]] += lam_reg * v
            if rw is not None and len(pa):
                for k in S:
                    dl = x[sl[f"C_{k}"]][pb] - x[sl[f"C_{k}"]][pa]
                    obj += 0.5 * rw * (dl @ dl)
                    g[sl[f"C_{k}"]] += np.bincount(pb, rw * dl, G) - np.bincount(pa, rw * dl, G)
            return obj, g

        layout = {
            "sl": sl,
            "bpos": bpos,
            "tidx": tidx,
            "groups": groups,
            "gidx": gidx,
            "didx": didx,
            "per_season": per_season,
        }
        return f, x0, bounds, names, layout

    def _blend_goals(self, h: pd.DataFrame, goals: np.ndarray) -> np.ndarray:
        """(1 − blend)·goals + blend·proxy, where proxy is a per division-season least-squares
        fit of goals on SoT (and off-target shots), shrunk to the division's fit."""
        shots = np.concatenate([h.home_shots.to_numpy(float), h.away_shots.to_numpy(float)])
        sot = np.concatenate([h.home_sot.to_numpy(float), h.away_sot.to_numpy(float)])
        ok = ~np.isnan(sot) & (~np.isnan(shots) if self.proxy == "both" else True)
        if not ok.any():
            return goals
        off = np.clip(np.nan_to_num(shots) - np.nan_to_num(sot), 0, None)
        sot = np.nan_to_num(sot)
        div = np.concatenate([h.division.to_numpy()] * 2)
        season = np.concatenate([h.season.to_numpy()] * 2)

        def coef(m):
            if self.proxy == "sot":
                return np.array([goals[m].sum() / max(sot[m].sum(), 1.0), 0.0])
            X = np.stack([sot[m], off[m]], axis=1)
            return np.clip(np.linalg.lstsq(X, goals[m], rcond=None)[0], 0.0, None)

        proxy = goals.copy()
        k0 = self.proxy_prior
        for d in np.unique(div[ok]):
            md = ok & (div == d)
            c_div = coef(md)
            for s in np.unique(season[md]):
                ms = md & (season == s)
                n = ms.sum()
                c = (n * coef(ms) + k0 * c_div) / (n + k0)
                proxy[ms] = c[0] * sot[ms] + c[1] * off[ms]
        return np.where(ok, (1 - self.blend) * goals + self.blend * proxy, goals)

    # ---- prediction --------------------------------------------------------------------

    def rates(self, fixtures: pd.DataFrame) -> tuple[np.ndarray, np.ndarray]:
        x, L, S = self.x_, self.layout_, self.stats
        sl, tidx = L["sl"], L["tidx"]

        def team(col, key):
            v = x[sl[key]]
            return np.array([v[tidx[t]] if t in tidx else 0.0 for t in fixtures[col]])

        C, H = x[sl["C_goals"]], x[sl["H_goals"]]
        level, hadv = [], []
        for d, s in zip(fixtures.division, fixtures.season, strict=True):
            key = (d, s if L["per_season"] else "all")
            if key not in L["gidx"]:  # first batch of a season: the latest one known
                same = [g for g in L["groups"] if g[0] == d]
                key = same[-1] if same else None
            level.append(C[L["gidx"][key]] if key is not None else C.mean())
            hadv.append(H[L["didx"][d]] if d in L["didx"] else H.mean())
        level, hadv = np.array(level), np.array(hadv)
        out = []
        for a_col, d_col, at_home in (
            ("home_team", "away_team", 1.0),
            ("away_team", "home_team", 0.0),
        ):
            eta = level + at_home * hadv
            for f_ in S:
                if f_ == "goals":
                    cs = ct = 1.0
                else:
                    cs, ct = x[L["bpos"][("goals", f_, "s")]], x[L["bpos"][("goals", f_, "t")]]
                ds = team(a_col, f"s_{f_}") - team(d_col, f"s_{f_}")
                dt = team(a_col, f"t_{f_}") + team(d_col, f"t_{f_}")
                eta = eta + cs * ds + ct * dt
            out.append(np.exp(eta))
        return out[0], out[1]

    def predict(self, fixtures: pd.DataFrame) -> MarketProbs:
        lh, la = self.rates(fixtures)
        return markets_from_grid(poisson_grid(lh, la, self.rho_), lh, la)


# Settings chosen on the inner validation season 2022/23 and confirmed on 2023/24.
_SOT_JOINT = {
    "stats": ("sot", "goals"),
    "reg_strength": {"sot": 2.0, "goals": 6.0},
    "reg_tempo": {"sot": 20.0, "goals": 100.0},
    "tempo_transfer": 0.5,
    "blend": 0.25,
    "proxy": "both",
    "xi": 0.003,
}


def _make(name: str, **changes):
    def factory():
        return ShotsXG(**{**_SOT_JOINT, **changes, "name": name})

    return factory


CANDIDATES = {
    # SoT strength and (damped) tempo factors feeding goals, goals target blended with the
    # SoT + off-target-shots proxy.
    "shots_xg_sot_joint": _make("shots_xg_sot_joint"),
    # The same with per-season league scoring levels linked by a random walk.
    "shots_xg_sot_joint_rw": _make("shots_xg_sot_joint_rw", season_rw=300.0),
    # Two-process chain: shot volume -> shots on target -> goals.
    "shots_xg_two_process": _make(
        "shots_xg_two_process",
        stats=("shots", "sot", "goals"),
        reg_strength={"shots": 4.0, "sot": 2.0, "goals": 6.0},
        reg_tempo={"shots": 20.0, "sot": 20.0, "goals": 100.0},
    ),
    # Goals-only strength / tempo model fitted on the blended xG-proxy target.
    "shots_xg_proxy": _make(
        "shots_xg_proxy",
        stats=("goals",),
        reg_strength={"goals": 2.0},
        reg_tempo={"goals": 100.0},
    ),
    # Ablation control: identical structure, no shot information at all.
    "shots_xg_noshots": _make(
        "shots_xg_noshots",
        stats=("goals",),
        reg_strength={"goals": 2.0},
        reg_tempo={"goals": 100.0},
        blend=0.0,
    ),
}
