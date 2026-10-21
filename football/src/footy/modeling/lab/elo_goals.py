"""Goal-based Elo ratings mapped to score grids (model-lab family `elo_goals`).

World Football Elo style ratings, run jointly over a league and its second tier:

    E_home = 1 / (1 + 10^(−(R_home + H_div − R_away) / 400))
    R_home += K · G(margin) · (W − E_home),   R_away −= the same

with W = 1, ½ or 0, a goal-difference multiplier G (World Football Elo: 1, 1.5, (11 + N)/8
for margins of ≤1, 2 and N ≥ 3 goals, scaled by `margin_scale`; or (1 + N)^p as in Hvattum &
Arntzen's goal-based Elo), an optional FiveThirtyEight-style damping of the multiplier for
heavy favourites (`autocorr`), and a home advantage H per division that learns online (plus
one shared closed-doors term).

The two divisions share one rating scale: second-tier sides start `tier2_offset` points
below the top flight, and promoted or relegated teams carry their ratings across, so the
exchange of teams keeps the offset calibrated. Summer regression towards the division mean
(`carry`), a provisional K boost for a team's first matches in the history, and a newcomer
rule for sides arriving from below the data complete the replay.

The rating difference (home advantage included) is mapped to home and away goal means by
two weighted Poisson regressions fitted on the history's own pre-match ratings:

    log E[goals] = a_div + b·d + c·|d| (+ extra rating features),   d = diff / 400

Extra features, each a goal-based rating learnt by the same online replay:
- a goal-total ("tempo") rating per team (`tempo_lr`): the sum for the two sides moves both
  goal means, which is what the totals and BTTS markets need;
- attack / defence ratings learnt by stochastic gradient steps on the Poisson likelihood
  (`ad_lr`), a goal-count analogue of Elo;
- a second Elo with its own K, carry and shots blend (`k2`), so the regression can weigh
  fast against slow form, or results against chances;
- a fast-moving division scoring level (`level_lr`).

Shots on target can stand in for part of each result (`sot_blend`, `tempo_sot`, `ad_sot`,
`sot_blend2`): expected goals = SoT × the division's goals-per-SoT rate in the history, the
"deserved" result W is the Poisson win probability (plus half the draw probability) of those
expected goals, and the margin is the blended goal difference. Where SoT is missing the
goals are used.

Grids are `poisson_grid` with the Dixon-Coles rho fitted by weighted maximum likelihood on
the training matches given the regression's rates. Every fit replays the history it is
given (no state is carried between fits), uses only the DataFrames passed to it and no
randomness. The closed-doors calendar is the core one (footy.modeling.closed_doors), as in
the core Dixon-Coles model.
"""

import math

import numpy as np
import pandas as pd
from scipy.optimize import minimize_scalar
from scipy.stats import poisson

from footy.modeling.base import ALL_MARKETS
from footy.modeling.closed_doors import closed_doors_mask
from footy.modeling.data import SECOND_TIER
from footy.modeling.grid import MarketProbs, markets_from_grid, mix_grids, poisson_grid

TIER1 = set(SECOND_TIER)
TIER2 = set(SECOND_TIER.values())
DIVS = sorted(TIER1 | TIER2)
DIDX = {d: i for i, d in enumerate(DIVS)}
IS_T2 = [d in TIER2 for d in DIVS]
EPOCH = pd.Timestamp("2000-01-01")
RHO_LIMIT = 0.25
FEATURES = ("d", "d2", "tsum", "adh", "ada", "lvl", "cd", "ph", "pa")

DEFAULTS = {
    # Elo replay
    "k": 20.0,  # K factor (Elo points)
    "k_tier2": 1.0,  # K multiplier for second-tier matches
    "margin": "wfe",  # goal-difference multiplier: "wfe", "pow" or "none"
    "margin_scale": 1.0,  # wfe: G = 1 + scale·(G_wfe − 1)
    "margin_p": 0.5,  # pow: G = (1 + N)^p
    "margin_cap": 0.0,  # cap the goal margin used by the multiplier (0: no cap)
    "autocorr": 0.0,  # 538-style damping: G·2.2 / (2.2 + autocorr·0.001·winner's edge)
    "hfa0": 65.0,  # starting home advantage (Elo points)
    "hfa_lr": 0.075,  # each rating change moves the division's home advantage this much
    "closed_doors": True,  # separate home advantage for matches without spectators
    "tier2_offset": 250.0,  # second-tier sides start this far below the top flight
    "newcomer": 50.0,  # a side new to the data starts this far below its division's mean
    "carry": 0.9,  # summer: ratings keep this share of their distance to the division mean
    "provisional": 1.0,  # K·(1 + provisional·exp(−n / provisional_n)) for a side's n-th match
    "provisional_n": 10.0,
    "season_boost": 0.0,  # K·(1 + boost·exp(−n / season_boost_n)) for a side's n-th match
    "season_boost_n": 8.0,  # of the season
    "sot_blend": 0.0,  # Elo result and margin from (1 − a)·goals + a·SoT expected goals
    # extra rating features
    "k2": 0.0,  # a second Elo with this K (0: off)
    "carry2": 0.9,
    "sot_blend2": 0.0,
    "tempo_lr": 0.0,  # goal-total rating learning rate (0: off)
    "tempo_carry": 0.8,
    "tempo_sot": 0.0,
    "ad_lr": 0.0,  # attack / defence SGD learning rate (0: off)
    "ad_carry": 0.8,
    "ad_sot": 0.0,
    "level_lr": 0.0,  # fast division scoring level as a feature (0: off)
    "promo_decay": 0.0,  # promoted (+1) / relegated (−1) flags fading over this many
    # matches of the new season, as regression features (0: off)
    # goal regression
    "abs_term": True,  # |d| in the regression
    "sq_term": False,  # d² in the regression
    "xi": 0.0015,  # time decay of the regression weights (per day)
    "warmup_days": 250.0,  # matches this early in the history are not regressed on
    "tier2_weight": 1.0,  # regression weight of second-tier matches
    "ridge": 0.1,  # ridge on the non-intercept coefficients
    "rho_shift": 0.0,  # added to the fitted rho
}


def wfe_index(margin: float) -> float:
    """World Football Elo goal-difference index (1, 1, 1.5, (11 + N)/8 for N = 0, 1, 2, 3+),
    linear between whole margins so blended (fractional) margins work too."""
    if margin <= 1.0:
        return 1.0
    if margin <= 2.0:
        return 1.0 + 0.5 * (margin - 1.0)
    if margin <= 3.0:
        return 1.5 + 0.25 * (margin - 2.0)
    return (11.0 + margin) / 8.0


def poisson_points(xh: np.ndarray, xa: np.ndarray, max_goals: int = 12) -> np.ndarray:
    """P(home win) + ½·P(draw) for independent Poisson goals with means xh, xa."""
    k = np.arange(max_goals + 1)
    ph = poisson.pmf(k[None, :], np.maximum(xh, 1e-6)[:, None])
    pa = poisson.pmf(k[None, :], np.maximum(xa, 1e-6)[:, None])
    cdf_a = np.cumsum(pa, axis=1)
    below = np.concatenate([np.zeros((len(xa), 1)), cdf_a[:, :-1]], axis=1)  # P(A < k)
    p_home = (ph * below).sum(axis=1)
    p_draw = (ph * pa).sum(axis=1)
    return p_home + 0.5 * p_draw


class EloGoals:
    """Goal-based Elo replay plus Poisson regressions (see the module docstring)."""

    markets = ALL_MARKETS

    def __init__(self, name: str = "elo_goals", **cfg):
        unknown = set(cfg) - set(DEFAULTS)
        if unknown:
            raise TypeError(f"unknown options {sorted(unknown)}")
        self.cfg = DEFAULTS | cfg
        self.name = name

    def params(self) -> dict:
        return dict(self.cfg)

    # ---- inputs ----------------------------------------------------------------------

    def _results(self, h: pd.DataFrame) -> dict[str, list]:
        """Per match: the Elo result W, the margin and the goal targets of every rating,
        each blending goals with SoT expected goals by its own weight."""
        c = self.cfg
        gh, ga = h.home_goals.to_numpy(float), h.away_goals.to_numpy(float)
        xh, xa = gh.copy(), ga.copy()
        if "home_sot" in h and "away_sot" in h:
            sh, sa = h.home_sot.to_numpy(float), h.away_sot.to_numpy(float)
            ok = ~(np.isnan(sh) | np.isnan(sa))
            div = h.division.to_numpy()
            for d in np.unique(div[ok]):
                m = ok & (div == d)
                rate = (gh[m].sum() + ga[m].sum()) / max(sh[m].sum() + sa[m].sum(), 1.0)
                xh[m], xa[m] = rate * sh[m], rate * sa[m]
        w_goals = np.where(gh > ga, 1.0, np.where(gh == ga, 0.5, 0.0))
        need_w = c["sot_blend"] > 0 or (c["k2"] > 0 and c["sot_blend2"] > 0)
        w_xg = poisson_points(xh, xa) if need_w and len(h) else w_goals
        out = {}
        for key, a in (("1", c["sot_blend"]), ("2", c["sot_blend2"])):
            out[f"w{key}"] = ((1 - a) * w_goals + a * w_xg).tolist()
            out[f"gd{key}"] = ((1 - a) * (gh - ga) + a * (xh - xa)).tolist()
        a = c["tempo_sot"]
        out["tot"] = ((1 - a) * (gh + ga) + a * (xh + xa)).tolist()
        a = c["ad_sot"]
        out["yh"] = ((1 - a) * gh + a * xh).tolist()
        out["ya"] = ((1 - a) * ga + a * xa).tolist()
        return out

    # ---- replay ----------------------------------------------------------------------

    def _g(self, gd: float, edge: float) -> float:
        """Goal-difference multiplier for a (possibly fractional) goal difference `gd`;
        `edge` is the home side's rating edge (home advantage included)."""
        c = self.cfg
        margin = abs(gd)
        if c["margin_cap"]:
            margin = min(margin, c["margin_cap"])
        if c["margin"] == "wfe":
            g = 1.0 + c["margin_scale"] * (wfe_index(margin) - 1.0)
        elif c["margin"] == "pow":
            g = (1.0 + margin) ** c["margin_p"]
        else:
            g = 1.0
        if c["autocorr"] and margin > 0.5:
            winner_edge = edge if gd > 0 else -edge
            g *= 2.2 / max(2.2 + c["autocorr"] * 0.001 * winner_edge, 0.5)
        return g

    def fit(self, history: pd.DataFrame, cutoff) -> None:
        c = self.cfg
        h = history[history.division.isin(TIER1 | TIER2)].sort_values(["date", "id"])
        teams = sorted(set(h.home_team) | set(h.away_team))
        self.tidx_ = {t: i for i, t in enumerate(teams)}
        feats = self._replay(h, len(teams))
        day = (h["date"] - EPOCH).dt.days.to_numpy()
        cut_day = (cutoff.tz_convert("UTC").tz_localize(None) - EPOCH).days
        use = day >= (day[0] + c["warmup_days"]) if len(h) else np.zeros(0, bool)
        w = np.exp(-c["xi"] * (cut_day - day)) * use
        w = w * np.where(h.division.isin(TIER2).to_numpy(), c["tier2_weight"], 1.0)
        di = h.division.map(DIDX).to_numpy()
        Zh, Za = self._design(di, feats)
        yh, ya = h.home_goals.to_numpy(float), h.away_goals.to_numpy(float)
        self.beta_h_ = _poisson_irls(Zh, yh, w, len(DIVS), c["ridge"])
        self.beta_a_ = _poisson_irls(Za, ya, w, len(DIVS), c["ridge"])
        lh, la = np.exp(Zh @ self.beta_h_), np.exp(Za @ self.beta_a_)
        rho = _fit_rho(yh, ya, lh, la, w) + c["rho_shift"]
        self.rho_ = float(np.clip(rho, -RHO_LIMIT, RHO_LIMIT))

    def _replay(self, h: pd.DataFrame, T: int) -> dict[str, np.ndarray]:
        """Run every rating over `h` (sorted by date); return each match's pre-match
        features and keep the final state for prediction."""
        c = self.cfg
        N, D = len(h), len(DIVS)
        hi = h.home_team.map(self.tidx_).to_numpy().tolist()
        ai = h.away_team.map(self.tidx_).to_numpy().tolist()
        di = h.division.map(DIDX).to_numpy().tolist()
        seasons = h.season.to_numpy().tolist()
        cd = closed_doors_mask(h).tolist() if c["closed_doors"] and N else [False] * N
        res = self._results(h)
        w1, gd1, w2, gd2 = res["w1"], res["gd1"], res["w2"], res["gd2"]
        tot, yht, yat = res["tot"], res["yh"], res["ya"]
        R, R2, Tm, O, Df = ([0.0] * T for _ in range(5))
        ng = [0] * T
        ns = [0] * T  # matches this season
        prev_div = [-1] * T  # division last season
        seen = [False] * T
        last_div = [-1] * T
        H = [c["hfa0"]] * D
        Hcd = 0.3 * c["hfa0"]
        mt = [2.7] * D  # slow running mean of total goals per division (tempo reference)
        lv = [2.7] * D  # fast running mean (level feature)
        first_season = seasons[0] if N else None
        k, k2 = c["k"], c["k2"]
        prov, prov_n = c["provisional"], c["provisional_n"]
        hfa_lr, tl, al, ll = c["hfa_lr"], c["tempo_lr"], c["ad_lr"], c["level_lr"]
        kt2 = c["k_tier2"]
        sb, sb_n, pdec = c["season_boost"], c["season_boost_n"], c["promo_decay"]
        out = {key: np.zeros(N) for key in FEATURES}
        f_d, f_d2, f_ts, f_adh, f_ada, f_lv, f_cd, f_ph, f_pa = (out[x] for x in FEATURES)
        cur = None
        for j in range(N):
            s = seasons[j]
            if cur is not None and s != cur:
                self._summer(R, R2, Tm, O, Df, seen, last_div)
                prev_div = list(last_div)
                ns = [0] * T
            cur = s
            i_h, i_a, dv = hi[j], ai[j], di[j]
            for t in (i_h, i_a):
                if not seen[t]:
                    start = self._start_rating(R, seen, last_div, dv, s == first_season)
                    R[t] = R2[t] = start
                    seen[t] = True
                last_div[t] = dv
            hadv = Hcd if cd[j] else H[dv]
            diff = R[i_h] + hadv - R[i_a]
            f_d[j] = diff / 400.0
            f_ts[j] = Tm[i_h] + Tm[i_a]
            f_adh[j] = O[i_h] - Df[i_a]
            f_ada[j] = O[i_a] - Df[i_h]
            f_lv[j] = math.log(lv[dv])
            f_cd[j] = cd[j]
            if pdec:
                f_ph[j] = _promo(prev_div[i_h], dv, ns[i_h], pdec)
                f_pa[j] = _promo(prev_div[i_a], dv, ns[i_a], pdec)
            mh = 1.0 + prov * math.exp(-ng[i_h] / prov_n)
            ma = 1.0 + prov * math.exp(-ng[i_a] / prov_n)
            if sb:
                mh *= 1.0 + sb * math.exp(-ns[i_h] / sb_n)
                ma *= 1.0 + sb * math.exp(-ns[i_a] / sb_n)
            kd = kt2 if IS_T2[dv] else 1.0
            # main Elo
            e_h = 1.0 / (1.0 + 10.0 ** (-diff / 400.0))
            delta = kd * self._g(gd1[j], diff) * (w1[j] - e_h)
            R[i_h] += k * mh * delta
            R[i_a] -= k * ma * delta
            if cd[j]:
                Hcd += hfa_lr * k * delta
            else:
                H[dv] += hfa_lr * k * delta
            if k2:
                diff2 = R2[i_h] + hadv - R2[i_a]
                f_d2[j] = diff2 / 400.0
                e2 = 1.0 / (1.0 + 10.0 ** (-diff2 / 400.0))
                delta2 = kd * self._g(gd2[j], diff2) * (w2[j] - e2)
                R2[i_h] += k2 * mh * delta2
                R2[i_a] -= k2 * ma * delta2
            if tl:
                resid = tot[j] - mt[dv] - Tm[i_h] - Tm[i_a]
                Tm[i_h] += tl * resid / 2.0
                Tm[i_a] += tl * resid / 2.0
            if al:
                base = math.log(mt[dv] / 2.0)
                lam_h = math.exp(base + hadv / 400.0 + O[i_h] - Df[i_a])
                lam_a = math.exp(base + O[i_a] - Df[i_h])
                r_h, r_a = min(yht[j], 6.0) - lam_h, min(yat[j], 6.0) - lam_a
                O[i_h] += al * r_h
                Df[i_a] -= al * r_h
                O[i_a] += al * r_a
                Df[i_h] -= al * r_a
            mt[dv] += 0.01 * (tot[j] - mt[dv])
            if ll:
                lv[dv] += ll * (tot[j] - lv[dv])
            ng[i_h] += 1
            ng[i_a] += 1
            ns[i_h] += 1
            ns[i_a] += 1
        self.R_, self.R2_, self.Tm_ = R, R2, Tm
        self.ns_, self.prev_div_ = ns, prev_div
        self.O_, self.D_ = O, Df
        self.seen_, self.last_div_ = seen, last_div
        self.H_, self.Hcd_, self.lv_ = H, Hcd, lv
        self.last_season_ = cur
        return out

    def _start_rating(self, R, seen, last_div, dv, at_start) -> float:
        c = self.cfg
        if not at_start:
            members = [R[t] for t in range(len(R)) if seen[t] and last_div[t] == dv]
            if len(members) >= 6:
                return sum(members) / len(members) - c["newcomer"]
        return -c["tier2_offset"] if IS_T2[dv] else 0.0

    def _summer(self, R, R2, Tm, O, Df, seen, last_div) -> None:
        """New season: Elo ratings regress towards their division's mean; the goal-count
        ratings shrink towards zero."""
        c = self.cfg
        for dv in {last_div[t] for t in range(len(R)) if seen[t]}:
            members = [t for t in range(len(R)) if seen[t] and last_div[t] == dv]
            for arr, carry in ((R, c["carry"]), (R2, c["carry2"])):
                m = sum(arr[t] for t in members) / len(members)
                for t in members:
                    arr[t] = m + carry * (arr[t] - m)
        for t in range(len(R)):
            Tm[t] *= c["tempo_carry"]
            O[t] *= c["ad_carry"]
            Df[t] *= c["ad_carry"]

    # ---- regression ------------------------------------------------------------------

    def _design(self, di: np.ndarray, f: dict[str, np.ndarray]) -> tuple[np.ndarray, np.ndarray]:
        c = self.cfg
        n = len(di)
        dummies = np.zeros((n, len(DIVS)))
        dummies[np.arange(n), di] = 1.0
        common = [f["d"]]
        if c["abs_term"]:
            common.append(np.abs(f["d"]))
        if c["sq_term"]:
            common.append(f["d"] ** 2)
        if c["k2"]:
            common.append(f["d2"])
        if c["tempo_lr"]:
            common.append(f["tsum"])
        if c["level_lr"]:
            common.append(f["lvl"])
        if c["closed_doors"]:
            common.append(f["cd"])
        if c["promo_decay"]:
            common += [f["ph"], f["pa"]]
        base = np.column_stack([dummies, *common])
        if c["ad_lr"]:
            return (
                np.column_stack([base, f["adh"], f["ada"]]),
                np.column_stack([base, f["ada"], f["adh"]]),
            )
        return base, base

    # ---- prediction ------------------------------------------------------------------

    def _state_for(self, season: str):
        """Ratings as they stand for a fixture in `season` (summer step if it is new)."""
        R, R2, Tm = list(self.R_), list(self.R2_), list(self.Tm_)
        O, Df = list(self.O_), list(self.D_)
        ns, prev_div = self.ns_, self.prev_div_
        if self.last_season_ is not None and season != self.last_season_:
            self._summer(R, R2, Tm, O, Df, self.seen_, self.last_div_)
            ns, prev_div = [0] * len(R), list(self.last_div_)
        return R, R2, Tm, O, Df, ns, prev_div

    def rates(self, fixtures: pd.DataFrame) -> tuple[np.ndarray, np.ndarray]:
        c = self.cfg
        n = len(fixtures)
        f = {key: np.zeros(n) for key in FEATURES}
        cd = closed_doors_mask(fixtures) if c["closed_doors"] else np.zeros(n, bool)
        states = {}
        rows = zip(
            fixtures.home_team, fixtures.away_team, fixtures.division, fixtures.season, strict=True
        )
        for j, (ht, at, dvn, s) in enumerate(rows):
            if s not in states:
                states[s] = self._state_for(s)
            R, R2, Tm, O, Df, ns, prev_div = states[s]
            dv = DIDX[dvn]
            vals = []
            for t in (ht, at):
                i = self.tidx_.get(t)
                if i is not None and self.seen_[i]:
                    vals.append((R[i], R2[i], Tm[i], O[i], Df[i]))
                else:
                    r0 = self._start_rating(R, self.seen_, self.last_div_, dv, False)
                    vals.append((r0, r0, 0.0, 0.0, 0.0))
            (rh, r2h, th, oh, dh), (ra, r2a, ta, oa, da) = vals
            hadv = self.Hcd_ if cd[j] else self.H_[dv]
            f["d"][j] = (rh + hadv - ra) / 400.0
            f["d2"][j] = (r2h + hadv - r2a) / 400.0
            f["tsum"][j] = th + ta
            f["adh"][j] = oh - da
            f["ada"][j] = oa - dh
            f["lvl"][j] = math.log(self.lv_[dv])
            f["cd"][j] = cd[j]
            if c["promo_decay"]:
                for key, t in (("ph", ht), ("pa", at)):
                    i = self.tidx_.get(t)
                    if i is not None:
                        f[key][j] = _promo(prev_div[i], dv, ns[i], c["promo_decay"])
        di = fixtures.division.map(DIDX).to_numpy()
        Zh, Za = self._design(di, f)
        return np.exp(Zh @ self.beta_h_), np.exp(Za @ self.beta_a_)

    def predict(self, fixtures: pd.DataFrame) -> MarketProbs:
        lh, la = self.rates(fixtures)
        return markets_from_grid(poisson_grid(lh, la, self.rho_), lh, la)


class EloBlend:
    """A mixture of the score grids of several models (stays coherent in every market)."""

    markets = ALL_MARKETS

    def __init__(self, parts, weights, name: str = "elo_goals_blend"):
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


def _promo(prev: int, dv: int, n: int, decay: float) -> float:
    """+1 for a side promoted last summer, −1 if relegated, fading with its matches since."""
    if prev < 0 or prev == dv:
        return 0.0
    sign = 1.0 if IS_T2[prev] and not IS_T2[dv] else -1.0 if IS_T2[dv] and not IS_T2[prev] else 0.0
    return sign * math.exp(-n / decay)


def _poisson_irls(X, y, w, n_free, ridge, iters=30) -> np.ndarray:
    """Weighted Poisson regression by Newton steps; the first `n_free` columns (division
    intercepts) are all but unpenalised, the rest carry a ridge penalty."""
    p = X.shape[1]
    beta = np.zeros(p)
    mean = max(float(np.average(y, weights=w)) if w.sum() > 0 else 1.0, 0.1)
    beta[:n_free] = math.log(mean)
    pen = np.full(p, float(ridge))
    pen[:n_free] = 1e-4
    for _ in range(iters):
        mu = np.exp(np.clip(X @ beta, -5, 3))
        grad = X.T @ (w * (y - mu)) - pen * beta
        hess = (X * (w * mu)[:, None]).T @ X + np.diag(pen)
        step = np.linalg.solve(hess, grad)
        beta = beta + step
        if np.abs(step).max() < 1e-9:
            break
    return beta


def _fit_rho(yh, ya, lh, la, w) -> float:
    """Dixon-Coles rho maximising the weighted likelihood of the low scores, given rates."""
    if w.sum() <= 0:
        return 0.0
    m00 = (yh == 0) & (ya == 0)
    m01 = (yh == 0) & (ya == 1)
    m10 = (yh == 1) & (ya == 0)
    m11 = (yh == 1) & (ya == 1)

    def nll(rho):
        return -(
            (w[m00] * np.log(np.clip(1 - lh[m00] * la[m00] * rho, 1e-10, None))).sum()
            + (w[m01] * np.log(np.clip(1 + lh[m01] * rho, 1e-10, None))).sum()
            + (w[m10] * np.log(np.clip(1 + la[m10] * rho, 1e-10, None))).sum()
            + w[m11].sum() * np.log(max(1 - rho, 1e-10))
        )

    return float(minimize_scalar(nll, bounds=(-RHO_LIMIT, RHO_LIMIT), method="bounded").x)


CANDIDATES = {
    "elo_goals_wfe": lambda: EloGoals("elo_goals_wfe", tempo_lr=0.03),
}
