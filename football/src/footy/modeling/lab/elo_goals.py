"""Goal-based Elo ratings mapped to score grids (model-lab family `elo_goals`).

**Ratings.** Elo ratings replayed over the history, jointly over a league and its second
tier. Two update rules:

- "elo" (World Football Elo): E = 1 / (1 + 10^(−(R_home + H_div − R_away) / 400)) and
  R_home += K·G·(W − E), R_away −= the same, with W = 1, ½ or 0 and the goal-difference
  index G = 1, 1, 1.5, (11 + N)/8 for margins 0, 1, 2, N ≥ 3 (`margin_scale` stretches it;
  `margin="pow"` gives Hvattum & Arntzen's (1 + N)^p; `autocorr` damps it for heavy
  favourites, FiveThirtyEight style). The home advantage H learns per division (`hfa_lr`).
- "gd": a goal-difference Elo, R_home += K·(GD − gd_slope·edge/400): every goal of the
  margin counts, not just who won (an online least-squares rating, close to pi-ratings).

Both divisions share one scale: second-tier sides start `tier2_offset` points lower and
promoted or relegated teams carry their ratings across, so the exchange of teams keeps the
offset calibrated. Summer regression towards the division mean (`carry`), a provisional K
boost for a side's first matches, a newcomer rule for sides arriving from below the data,
and a smaller K after a red card (`red_k`) complete the replay.

**Goal-count ratings** learnt by the same replay:
- tempo (`tempo_lr`): each side's share of the goal total above the division's mean;
- attack / defence (`ad_lr`): stochastic gradient steps on the Poisson likelihood of the
  goals scored and conceded, a goal-count analogue of Elo;
- the division's scoring level (`level_lr`): a fast running mean of the goal total, used
  as an offset log(level / 2.7)·`level_weight` on both goal means (`level_offset`). This was
  the largest single gain: the time-weighted intercepts lag a league-wide change in
  scoring, such as 2023/24's added time.

**Shots on target** (`sot_blend`, `tempo_sot`, `ad_sot`, `level_sot`): expected goals =
SoT × the division's goals-per-SoT rate in the history; each rating learns from
(1 − a)·goals + a·expected goals (for "elo", W becomes the Poisson win probability plus half
the draw probability of the blended goals). Where SoT is missing (NaN) the goals are used,
so without shot data the model is goals-only.

**Goal means.** Poisson regressions on the history's own pre-match features (after a
warm-up while the ratings settle), with division intercepts:

    log E[goals] = a_div (+ h_div for the home side) ± b·d + c·|d| + e·tempo sum
                   (+ f·own attack − opponent defence) + log-level offset,   d = edge / 400

`tied` fits one regression for home and away goals (shared slopes, d sign-flipped), which
halves the parameters; the separate pair is the default.

**Grids.** `poisson_grid` with the Dixon-Coles rho fitted by weighted maximum likelihood on
the training matches given the fitted rates (plus `rho_shift`), so every market comes from
one coherent grid. `EloBlend` mixes the grids of several models.

Explored and left off in the registered candidates (they did not help on 2022/23 or
2023/24): a second Elo as an extra feature (`k2`), Glicko-style rating variances
(`glicko`), separate home and away ratings (`gamma`), a log-goal-ratio update ("lgr"),
season-start K boosts, promoted / relegated flags, d², a rho linear in |d|, and an
expected-goals proxy that adds off-target shots and corners (it moved xG by ~0.02 goals).

Every fit replays the history it is given (no state is carried between fits), uses only
the DataFrames passed to it and no randomness. The closed-doors calendar is the core one
(footy.modeling.closed_doors), as in the core Dixon-Coles model.
"""

import math

import numpy as np
import pandas as pd
from scipy.optimize import minimize, minimize_scalar
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
    "update": "elo",  # "elo": K·G·(W − E); "gd": K·(goal difference − expected)
    "gd_slope": 1.5,  # gd: expected goal difference per 400 points of rating edge
    "gd_cap": 0.0,  # gd: cap on the error (0: none)
    "gd_psi": False,  # gd: compress the error as log2(1 + |error|)
    "gamma": 1.0,  # < 1: separate home and away ratings (pi-rating style); a result moves
    # the side's rating at the other venue gamma times as much
    "glicko": False,  # Glicko-style rating variances set each side's own K (Kalman gain)
    "v0": 22500.0,  # glicko: starting variance (points²)
    "q_day": 3.0,  # glicko: variance added per day
    "v_season": 2500.0,  # glicko: variance added every summer
    "obs_var": 2.8,  # glicko: noise variance of the result (goals² for "gd", W² for "elo")
    "red_k": 1.0,  # K multiplier for matches with a red card
    "sot_blend": 0.0,  # Elo result and margin from (1 − a)·goals + a·SoT expected goals
    # extra rating features
    "k2": 0.0,  # a second Elo with this K (0: off)
    "update2": "elo",
    "carry2": 0.9,
    "sot_blend2": 0.0,
    "tempo_lr": 0.0,  # goal-total rating learning rate (0: off)
    "tempo_carry": 0.8,
    "tempo_sot": 0.0,
    "ad_lr": 0.0,  # attack / defence SGD learning rate (0: off)
    "ad_carry": 0.8,
    "ad_sot": 0.0,
    "level_lr": 0.0,  # fast running division scoring level (0: off) ...
    "level_offset": False,  # ... as an offset of both log goal means (else a feature)
    "level_sot": 0.0,  # ... learnt from (1 − a)·total goals + a·SoT expected total
    "level_weight": 1.0,  # ... the offset is this share of log(level / 2.7)
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
    "rho_abs": False,  # rho = r0 + r1·|d| per fixture (both fitted) instead of one rho
    "tied": False,  # one regression for home and away goals (shared slopes, sign-flipped d)
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


def expected_goals(h: pd.DataFrame) -> tuple[np.ndarray, np.ndarray]:
    """Shots-on-target expected goals per side: SoT × the division's goals per SoT in `h`
    itself; the goals where SoT is missing. (Adding off-target shots and corners by
    non-negative least squares changed these by about 0.02 goals: not worth it.)"""
    gh, ga = h.home_goals.to_numpy(float), h.away_goals.to_numpy(float)
    xh, xa = gh.copy(), ga.copy()
    if "home_sot" not in h or "away_sot" not in h:
        return xh, xa
    sh, sa = h.home_sot.to_numpy(float), h.away_sot.to_numpy(float)
    ok = ~(np.isnan(sh) | np.isnan(sa))
    div = h.division.to_numpy()
    for d in np.unique(div[ok]):
        m = ok & (div == d)
        rate = (gh[m].sum() + ga[m].sum()) / max(sh[m].sum() + sa[m].sum(), 1.0)
        xh[m], xa[m] = rate * sh[m], rate * sa[m]
    return xh, xa


def poisson_points(xh: np.ndarray, xa: np.ndarray, max_goals: int = 15) -> np.ndarray:
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
        xh, xa = expected_goals(h)
        w_goals = np.where(gh > ga, 1.0, np.where(gh == ga, 0.5, 0.0))
        need_w = c["sot_blend"] > 0 or (c["k2"] > 0 and c["sot_blend2"] > 0)
        w_xg = poisson_points(xh, xa) if need_w and len(h) else w_goals
        out = {}
        for key, a in (("1", c["sot_blend"]), ("2", c["sot_blend2"])):
            out[f"w{key}"] = ((1 - a) * w_goals + a * w_xg).tolist()
            out[f"gd{key}"] = ((1 - a) * (gh - ga) + a * (xh - xa)).tolist()
            lgr = np.log((gh + 0.5) / (ga + 0.5))
            lgr_x = np.log((xh + 0.5) / (xa + 0.5))
            out[f"lgr{key}"] = ((1 - a) * lgr + a * lgr_x).tolist()
        a = c["tempo_sot"]
        out["tot"] = ((1 - a) * (gh + ga) + a * (xh + xa)).tolist()
        a = c["level_sot"]
        out["lev"] = ((1 - a) * (gh + ga) + a * (xh + xa)).tolist()
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

    def _step(self, mode: str, w: float, gd: float, diff: float) -> float:
        """Rating change per unit of K. "elo": G(margin)·(W − E); "gd": the error of the
        expected goal difference gd_slope·diff/400, optionally compressed (pi-rating style
        ψ = log2(1 + |error|))."""
        if mode == "elo":
            return self._g(gd, diff) * (w - 1.0 / (1.0 + 10.0 ** (-diff / 400.0)))
        c = self.cfg
        err = gd - c["gd_slope"] * diff / 400.0
        if c["gd_cap"]:
            err = max(-c["gd_cap"], min(c["gd_cap"], err))
        if c["gd_psi"]:
            err = math.copysign(math.log2(1.0 + abs(err)), err)
        return err

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
        off = self._offset(feats)
        yh, ya = h.home_goals.to_numpy(float), h.away_goals.to_numpy(float)
        if c["tied"]:
            beta = _poisson_irls(
                np.vstack([Zh, Za]),
                np.r_[yh, ya],
                np.r_[w, w],
                2 * len(DIVS),
                c["ridge"],
                np.r_[off, off],
            )
            self.beta_h_ = self.beta_a_ = beta
        else:
            self.beta_h_ = _poisson_irls(Zh, yh, w, len(DIVS), c["ridge"], off)
            self.beta_a_ = _poisson_irls(Za, ya, w, len(DIVS), c["ridge"], off)
        lh, la = np.exp(Zh @ self.beta_h_ + off), np.exp(Za @ self.beta_a_ + off)
        if c["rho_abs"]:
            r0, r1 = _fit_rho_linear(yh, ya, lh, la, w, np.abs(feats["d"]))
        else:
            r0, r1 = _fit_rho(yh, ya, lh, la, w), 0.0
        self.rho_, self.rho_slope_ = r0 + c["rho_shift"], r1

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
        if c["red_k"] != 1.0 and "home_red" in h:
            red = (h.home_red.fillna(0).to_numpy() + h.away_red.fillna(0).to_numpy()) > 0
            kred = np.where(red, c["red_k"], 1.0).tolist()
        else:
            kred = [1.0] * N
        w1, w2 = res["w1"], res["w2"]
        gd1 = res["lgr1"] if c["update"] == "lgr" else res["gd1"]
        gd2 = res["lgr2"] if c["update2"] == "lgr" else res["gd2"]
        tot, yht, yat, lev = res["tot"], res["yh"], res["ya"], res["lev"]
        R, R2, Tm, Att, Df, RA = ([0.0] * T for _ in range(6))
        split = c["gamma"] != 1.0
        gam = c["gamma"]
        days = (h["date"] - EPOCH).dt.days.to_numpy().tolist()
        V = [c["v0"]] * T
        vday = [0] * T
        gl, q_day, obs_var = c["glicko"], c["q_day"], c["obs_var"]
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
                self._summer(R, R2, Tm, Att, Df, seen, last_div, RA)
                V = [v + c["v_season"] for v in V]
                prev_div = list(last_div)
                ns = [0] * T
            cur = s
            i_h, i_a, dv = hi[j], ai[j], di[j]
            for t in (i_h, i_a):
                if not seen[t]:
                    start = self._start_rating(R, seen, last_div, dv, s == first_season)
                    R[t] = R2[t] = RA[t] = start
                    seen[t] = True
                    V[t] = c["v0"]
                else:
                    V[t] += q_day * (days[j] - vday[t])
                vday[t] = days[j]
                last_div[t] = dv
            hadv = Hcd if cd[j] else H[dv]
            diff = R[i_h] + hadv - (RA[i_a] if split else R[i_a])
            f_d[j] = diff / 400.0
            f_ts[j] = Tm[i_h] + Tm[i_a]
            f_adh[j] = Att[i_h] - Df[i_a]
            f_ada[j] = Att[i_a] - Df[i_h]
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
            kd = (kt2 if IS_T2[dv] else 1.0) * kred[j]
            # main Elo
            delta = kd * self._step(c["update"], w1[j], gd1[j], diff)
            if gl:
                if c["update"] != "elo":
                    hd = c["gd_slope"] / 400.0
                else:
                    e = 1.0 / (1.0 + 10.0 ** (-diff / 400.0))
                    hd = math.log(10.0) / 400.0 * e * (1.0 - e)
                S = hd * hd * (V[i_h] + V[i_a]) + obs_var
                R[i_h] += V[i_h] * hd / S * delta
                R[i_a] -= V[i_a] * hd / S * delta
                V[i_h] -= (V[i_h] * hd) ** 2 / S
                V[i_a] -= (V[i_a] * hd) ** 2 / S
            elif split:
                R[i_h] += k * mh * delta
                RA[i_h] += gam * k * mh * delta
                RA[i_a] -= k * ma * delta
                R[i_a] -= gam * k * ma * delta
            else:
                R[i_h] += k * mh * delta
                R[i_a] -= k * ma * delta
            if cd[j]:
                Hcd += hfa_lr * k * delta
            else:
                H[dv] += hfa_lr * k * delta
            if k2:
                diff2 = R2[i_h] + hadv - R2[i_a]
                f_d2[j] = diff2 / 400.0
                delta2 = kd * self._step(c["update2"], w2[j], gd2[j], diff2)
                R2[i_h] += k2 * mh * delta2
                R2[i_a] -= k2 * ma * delta2
            if tl:
                resid = tot[j] - mt[dv] - Tm[i_h] - Tm[i_a]
                Tm[i_h] += tl * resid / 2.0
                Tm[i_a] += tl * resid / 2.0
            if al:
                base = math.log(mt[dv] / 2.0)
                lam_h = math.exp(base + hadv / 400.0 + Att[i_h] - Df[i_a])
                lam_a = math.exp(base + Att[i_a] - Df[i_h])
                r_h, r_a = min(yht[j], 6.0) - lam_h, min(yat[j], 6.0) - lam_a
                Att[i_h] += al * r_h
                Df[i_a] -= al * r_h
                Att[i_a] += al * r_a
                Df[i_h] -= al * r_a
            mt[dv] += 0.01 * (tot[j] - mt[dv])
            if ll:
                lv[dv] += ll * (lev[j] - lv[dv])
            ng[i_h] += 1
            ng[i_a] += 1
            ns[i_h] += 1
            ns[i_a] += 1
        self.R_, self.R2_, self.Tm_, self.RA_ = R, R2, Tm, (RA if split else R)
        self.ns_, self.prev_div_ = ns, prev_div
        self.att_, self.dfn_ = Att, Df
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

    def _summer(self, R, R2, Tm, Att, Df, seen, last_div, RA) -> None:
        """New season: Elo ratings regress towards their division's mean; the goal-count
        ratings shrink towards zero."""
        c = self.cfg
        for dv in {last_div[t] for t in range(len(R)) if seen[t]}:
            members = [t for t in range(len(R)) if seen[t] and last_div[t] == dv]
            for arr, carry in ((R, c["carry"]), (R2, c["carry2"]), (RA, c["carry"])):
                m = sum(arr[t] for t in members) / len(members)
                for t in members:
                    arr[t] = m + carry * (arr[t] - m)
        for t in range(len(R)):
            Tm[t] *= c["tempo_carry"]
            Att[t] *= c["ad_carry"]
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
        if c["level_lr"] and not c["level_offset"]:
            common.append(f["lvl"])
        if c["closed_doors"]:
            common.append(f["cd"])
        if c["promo_decay"]:
            common += [f["ph"], f["pa"]]
        if c["tied"]:
            return self._design_tied(dummies, f)
        base = np.column_stack([dummies, *common])
        if c["ad_lr"]:
            return (
                np.column_stack([base, f["adh"], f["ada"]]),
                np.column_stack([base, f["ada"], f["adh"]]),
            )
        return base, base

    def _design_tied(self, dummies: np.ndarray, f: dict[str, np.ndarray]):
        """Rows for home goals and for away goals with the same columns: division levels,
        division home advantages (home rows only), the rating edges with their sign flipped
        for the away side, and the symmetric features shared."""
        c = self.cfg
        signed = [f["d"]] + ([f["d2"]] if c["k2"] else [])
        sym = []
        if c["abs_term"]:
            sym.append(np.abs(f["d"]))
        if c["sq_term"]:
            sym.append(f["d"] ** 2)
        if c["tempo_lr"]:
            sym.append(f["tsum"])
        if c["level_lr"] and not c["level_offset"]:
            sym.append(f["lvl"])
        zero = np.zeros_like(f["d"])
        home_only = [f["cd"]] if c["closed_doors"] else []
        own_h, own_a = [], []
        if c["promo_decay"]:
            own_h += [f["ph"], f["pa"]]
            own_a += [f["pa"], f["ph"]]
        if c["ad_lr"]:
            own_h.append(f["adh"])
            own_a.append(f["ada"])
        zh = np.column_stack([dummies, dummies, *signed, *sym, *home_only, *own_h])
        za = np.column_stack(
            [dummies, dummies * 0, *(-x for x in signed), *sym, *(zero for _ in home_only), *own_a]
        )
        return zh, za

    # ---- prediction ------------------------------------------------------------------

    def _state_for(self, season: str):
        """Ratings as they stand for a fixture in `season` (summer step if it is new)."""
        R, R2, Tm = list(self.R_), list(self.R2_), list(self.Tm_)
        Att, Df, RA = list(self.att_), list(self.dfn_), list(self.RA_)
        ns, prev_div = self.ns_, self.prev_div_
        if self.last_season_ is not None and season != self.last_season_:
            self._summer(R, R2, Tm, Att, Df, self.seen_, self.last_div_, RA)
            ns, prev_div = [0] * len(R), list(self.last_div_)
        return R, R2, Tm, Att, Df, ns, prev_div, RA

    def _offset(self, f: dict[str, np.ndarray]) -> np.ndarray:
        """log of the running scoring level relative to 2.7 goals, when used as an offset."""
        c = self.cfg
        if c["level_lr"] and c["level_offset"]:
            return c["level_weight"] * (f["lvl"] - math.log(2.7))
        return np.zeros(len(f["d"]))

    def rates(self, fixtures: pd.DataFrame) -> tuple[np.ndarray, np.ndarray]:
        di, f = self._fixture_features(fixtures)
        Zh, Za = self._design(di, f)
        off = self._offset(f)
        return np.exp(Zh @ self.beta_h_ + off), np.exp(Za @ self.beta_a_ + off)

    def _fixture_features(self, fixtures: pd.DataFrame):
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
            R, R2, Tm, Att, Df, ns, prev_div, RA = states[s]
            dv = DIDX[dvn]
            vals = []
            for t, own in ((ht, R), (at, RA)):
                i = self.tidx_.get(t)
                if i is not None and self.seen_[i]:
                    vals.append((own[i], R2[i], Tm[i], Att[i], Df[i]))
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
        return fixtures.division.map(DIDX).to_numpy(), f

    def predict(self, fixtures: pd.DataFrame) -> MarketProbs:
        di, f = self._fixture_features(fixtures)
        Zh, Za = self._design(di, f)
        off = self._offset(f)
        lh, la = np.exp(Zh @ self.beta_h_ + off), np.exp(Za @ self.beta_a_ + off)
        rho = np.clip(self.rho_ + self.rho_slope_ * np.abs(f["d"]), -RHO_LIMIT, RHO_LIMIT)
        return markets_from_grid(poisson_grid(lh, la, rho), lh, la)


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


def _poisson_irls(X, y, w, n_free, ridge, offset=None, iters=30) -> np.ndarray:
    """Weighted Poisson regression by Newton steps; the first `n_free` columns (division
    intercepts) are all but unpenalised, the rest carry a ridge penalty."""
    p = X.shape[1]
    beta = np.zeros(p)
    off = np.zeros(len(y)) if offset is None else offset
    mean = max(float(np.average(y, weights=w)) if w.sum() > 0 else 1.0, 0.1)
    beta[:n_free] = math.log(mean)
    pen = np.full(p, float(ridge))
    pen[:n_free] = 1e-4
    for _ in range(iters):
        mu = np.exp(np.clip(X @ beta + off, -5, 3))
        grad = X.T @ (w * (y - mu)) - pen * beta
        hess = (X * (w * mu)[:, None]).T @ X + np.diag(pen)
        step = np.linalg.solve(hess, grad)
        beta = beta + step
        if np.abs(step).max() < 1e-9:
            break
    return beta


def _fit_rho_linear(yh, ya, lh, la, w, x) -> tuple[float, float]:
    """rho_i = r0 + r1·x_i maximising the weighted likelihood of the low scores."""
    if w.sum() <= 0:
        return 0.0, 0.0
    low = (yh <= 1) & (ya <= 1) & (w > 0)
    yh, ya, lh, la, w, x = yh[low], ya[low], lh[low], la[low], w[low], x[low]
    m00, m01 = (yh == 0) & (ya == 0), (yh == 0) & (ya == 1)
    m10, m11 = (yh == 1) & (ya == 0), (yh == 1) & (ya == 1)

    def nll(r):
        rho = np.clip(r[0] + r[1] * x, -RHO_LIMIT, RHO_LIMIT)
        tau = np.ones_like(rho)
        tau[m00] = 1 - lh[m00] * la[m00] * rho[m00]
        tau[m01] = 1 + lh[m01] * rho[m01]
        tau[m10] = 1 + la[m10] * rho[m10]
        tau[m11] = 1 - rho[m11]
        return -(w * np.log(np.clip(tau, 1e-10, None))).sum()

    r0 = _fit_rho(yh, ya, lh, la, w)
    res = minimize(nll, np.array([r0, 0.0]), method="Nelder-Mead", options={"xatol": 1e-5})
    return float(res.x[0]), float(res.x[1])


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


# Settings found by coordinate search on the mean D-025 composite of 2022/23 and 2023/24
# (five leagues, real walk-forward). Shared chassis: tempo, attack / defence and level
# ratings, tied regression, all history after the warm-up weighted equally.
CHASSIS = {
    "tempo_lr": 0.045,
    "tempo_sot": 0.5,
    "ad_lr": 0.0025,
    "ad_sot": 0.5,
    "level_offset": True,
    "level_lr": 0.02,
    "level_sot": 0.5,
    "level_weight": 0.5,
    "tied": True,
    "xi": 0.0,
    "newcomer": 100.0,
    "red_k": 0.6,
    "rho_shift": -0.05,
}
GD = CHASSIS | {"update": "gd", "k": 10.0, "gd_slope": 1.5, "sot_blend": 0.4, "hfa_lr": 0.0}
WFE = CHASSIS | {"update": "elo", "k": 25.0, "margin_scale": 2.0, "sot_blend": 0.4}
GOALS_ONLY = GD | {"sot_blend": 0.0, "tempo_sot": 0.0, "ad_sot": 0.0, "level_sot": 0.0}
CLASSIC = {"update": "elo", "k": 20.0}  # World Football Elo + |d| regression, nothing else

CANDIDATES = {
    "elo_goals_gd": lambda: EloGoals("elo_goals_gd", **GD),
    "elo_goals_wfe": lambda: EloGoals("elo_goals_wfe", **WFE),
    "elo_goals_blend": lambda: EloBlend(
        [EloGoals(**GD), EloGoals(**WFE)], [0.6, 0.4], name="elo_goals_blend"
    ),
    "elo_goals_goals_only": lambda: EloGoals("elo_goals_goals_only", **GOALS_ONLY),
    "elo_goals_classic": lambda: EloGoals("elo_goals_classic", **CLASSIC),
}
