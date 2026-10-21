"""Context effects on Dixon-Coles rates (model-lab family `congestion_context`).

`ContextDC` is the core time-weighted Dixon-Coles model (shared ratings across a league and
its second tier, per-division home advantage, closed-doors term, ridge, low-score rho) with
multiplicative context terms on the two goal rates. Every term is estimated from the history
passed to fit() only, and fixture features use only the fixture's own schedule (date, teams,
season, division) plus earlier matches:

Fitted jointly with the ratings (log-linear covariates, ridge `reg_ctx`):
- rest: days since each team's previous match in the data (any division the runner loads),
  in buckets (<= 3, 4-5, 10-20, > 20 days; 6-9 is the reference), each with an effect on the
  team's own scoring and on its opponent's scoring;
- rest_diff: the clipped difference between the two teams' rest days (one continuous term);
- congestion: matches in the previous 21 days (and 14) above the usual one-a-week;
- phase: part of the season (Aug, Sep, Oct-Nov, Dec-Jan, Feb-Mar, Apr-Jun) on the home and
  away goal rates, so the scoring level follows the calendar rather than one time-weighted
  intercept; `midweek` (Tue-Thu) likewise.
- promoted / relegated: flags for a team in its first season after moving between the two
  tiers (from the previous season's matches in the data), decaying with the matches it has
  played in its new division (`flag_decay`).

Applied at prediction time (constants tuned on seasons before 2023/24):
- early-season shrinkage: each team's attack and defence move towards the mean of its
  division by s · exp(-n / k), n = the team's matches so far this season (`early`);
- promoted shift: a newly promoted team's attack is lowered and defence weakened by
  (a, d) · exp(-n / k), n = its matches in the new division (`promo`).

All markets come from one Dixon-Coles grid per fixture.
"""

import numpy as np
import pandas as pd
from scipy.optimize import minimize

from footy.modeling.closed_doors import closed_doors_mask
from footy.modeling.data import SECOND_TIER
from footy.modeling.dixon_coles import DixonColes, _tau_terms
from footy.modeling.grid import MarketProbs, markets_from_grid, poisson_grid

TIER1 = set(SECOND_TIER)
TIER2 = set(SECOND_TIER.values())
KEEP = ["division", "season", "date", "kickoff_utc", "home_team", "away_team"]
REST_BUCKETS = (("rest_le3", 0, 3), ("rest_4_5", 4, 5), ("rest_10_20", 10, 20), ("rest_gt20", 21, 10**6))
PHASES = (("ph_aug", (7, 8)), ("ph_sep", (9,)), ("ph_dec_jan", (12, 1)), ("ph_feb_mar", (2, 3)), ("ph_apr_jun", (4, 5, 6)))


def _prev_season(s: str) -> str:
    a = int(s[:2]) - 1
    return f"{a % 100:02d}{(a + 1) % 100:02d}"


def team_context(matches: pd.DataFrame) -> tuple[pd.DataFrame, pd.DataFrame]:
    """Context of the home and of the away team of every row of `matches` (oldest first is
    not required), using only rows dated before it. Returns two frames aligned with
    `matches` (positional): rest, c14, c21, n_season, n_div_season, promoted, relegated."""
    n = len(matches)
    pos = np.arange(n)
    day = matches["date"].to_numpy("datetime64[D]").astype(np.int64)
    teams = np.concatenate([matches.home_team.to_numpy(), matches.away_team.to_numpy()])
    codes, uniq = pd.factorize(teams, sort=True)
    seasons = np.concatenate([matches.season.to_numpy()] * 2)
    divs = np.concatenate([matches.division.to_numpy()] * 2)
    tier = np.where(np.isin(divs, list(TIER1)), 1, np.where(np.isin(divs, list(TIER2)), 2, 0))
    d2 = np.concatenate([day, day])
    row = np.concatenate([pos, pos])
    side = np.repeat([0, 1], n)
    order = np.lexsort((side, row, d2, codes))
    c, d, s = codes[order], d2[order], seasons[order]
    same = np.r_[False, c[1:] == c[:-1]] if len(c) else np.zeros(0, bool)
    rest = np.where(same, np.r_[0, np.diff(d)], np.nan).astype(float)
    key = c.astype(np.int64) * 1_000_000 + d
    idx = np.arange(len(key))
    c14 = idx - np.searchsorted(key, key - 14, side="left")
    c21 = idx - np.searchsorted(key, key - 21, side="left")
    app = pd.DataFrame({"team": c, "season": s, "div": divs[order], "tier": tier[order]})
    n_season = app.groupby(["team", "season"]).cumcount().to_numpy()
    n_div = app.groupby(["team", "season", "div"]).cumcount().to_numpy()
    # each team's tier in each season (majority of its matches), for promoted/relegated flags
    by = app.groupby(["team", "season"]).tier.mean().round().astype(int)
    known = set(app.season.unique())
    lut = by.to_dict()
    prev = np.array([_prev_season(x) for x in s]) if len(s) else np.zeros(0, str)
    prev_tier = np.array(
        [lut.get((t, p), 0 if p in known else -1) for t, p in zip(c, prev, strict=True)]
    )
    t_now = tier[order]
    out = pd.DataFrame(
        {
            "rest": rest,
            "c14": c14.astype(float),
            "c21": c21.astype(float),
            "n_season": n_season.astype(float),
            "n_div_season": n_div.astype(float),
            "promoted": ((t_now == 1) & (prev_tier == 2)).astype(float),
            "relegated": ((t_now == 2) & (prev_tier == 1)).astype(float),
        }
    )
    back = np.empty_like(order)
    back[order] = np.arange(len(order))
    out = out.iloc[back].reset_index(drop=True)
    home, away = out.iloc[:n].reset_index(drop=True), out.iloc[n:].reset_index(drop=True)
    return home, away


def team_design(f: pd.DataFrame, feats: tuple[str, ...], flag_decay: float) -> np.ndarray:
    """Columns for one side's team features (n, K)."""
    cols = []
    rest = f["rest"].fillna(99.0).to_numpy()
    for name in feats:
        if name == "rest":
            cols += [((rest >= lo) & (rest <= hi)).astype(float) for _, lo, hi in REST_BUCKETS]
        elif name == "congestion":
            cols.append(np.clip(f["c21"].to_numpy() - 3.0, 0, None))
            cols.append(np.clip(f["c14"].to_numpy() - 2.0, 0, None))
        elif name == "promoted":
            cols.append(f["promoted"].to_numpy() * np.exp(-f["n_div_season"].to_numpy() / flag_decay))
        elif name == "relegated":
            cols.append(f["relegated"].to_numpy() * np.exp(-f["n_div_season"].to_numpy() / flag_decay))
        elif name == "promoted_flat":
            cols.append(f["promoted"].to_numpy())
        elif name != "rest_diff":
            raise ValueError(f"unknown team feature {name!r}")
    return np.stack(cols, axis=1) if cols else np.zeros((len(f), 0))


def team_feature_names(feats: tuple[str, ...]) -> list[str]:
    names = []
    for name in feats:
        if name == "rest":
            names += [b[0] for b in REST_BUCKETS]
        elif name == "congestion":
            names += ["c21_over3", "c14_over2"]
        elif name != "rest_diff":
            names.append(name)
    return names


def match_design(m: pd.DataFrame, feats: tuple[str, ...], fh: pd.DataFrame, fa: pd.DataFrame):
    """Match-level columns (n, J) and, separately, the antisymmetric rest difference."""
    cols = []
    month = m["date"].dt.month.to_numpy()
    for name in feats:
        if name == "phase":
            cols += [np.isin(month, ms).astype(float) for _, ms in PHASES]
        elif name == "midweek":
            dow = m["date"].dt.dayofweek.to_numpy()
            cols.append(((dow >= 1) & (dow <= 3)).astype(float))
        else:
            raise ValueError(f"unknown match feature {name!r}")
    return np.stack(cols, axis=1) if cols else np.zeros((len(m), 0))


def match_feature_names(feats: tuple[str, ...]) -> list[str]:
    names = []
    for name in feats:
        if name == "phase":
            names += [p[0] for p in PHASES]
        else:
            names.append(name)
    return names


def rest_diff(fh: pd.DataFrame, fa: pd.DataFrame, clip: float = 4.0) -> np.ndarray:
    """Home rest minus away rest in days, clipped (unknown rest counts as a week)."""
    rh = fh["rest"].fillna(7.0).clip(upper=14).to_numpy()
    ra = fa["rest"].fillna(7.0).clip(upper=14).to_numpy()
    return np.clip(rh - ra, -clip, clip)


class ContextDC(DixonColes):
    def __init__(
        self,
        team_feats: tuple[str, ...] = (),
        match_feats: tuple[str, ...] = (),
        reg_ctx: float = 10.0,
        flag_decay: float = 10.0,
        early: tuple[float, float, float, float, float] | None = None,
        promo: tuple[float, float, float] | None = None,
        xi: float = 0.0019,
        reg: float = 2.0,
        tier2_weight: float = 0.5,
        name: str | None = None,
    ):
        super().__init__(xi=xi, rho=True, reg=reg, tier2_weight=tier2_weight, name=name)
        self.name = name or "congestion_context"
        self.team_feats, self.match_feats = tuple(team_feats), tuple(match_feats)
        self.reg_ctx, self.flag_decay = reg_ctx, flag_decay
        self.early, self.promo = early, promo
        self.beta_: dict[str, float] = {}
        self._hist: pd.DataFrame | None = None

    def params(self) -> dict:
        return super().params() | {
            "team_feats": list(self.team_feats),
            "match_feats": list(self.match_feats),
            "reg_ctx": self.reg_ctx,
            "flag_decay": self.flag_decay,
            "early": self.early,
            "promo": self.promo,
        }

    # ---- fitting -------------------------------------------------------------------------

    def _designs(self, m: pd.DataFrame, fh: pd.DataFrame, fa: pd.DataFrame):
        Fh = team_design(fh, self.team_feats, self.flag_decay)
        Fa = team_design(fa, self.team_feats, self.flag_decay)
        M = match_design(m, self.match_feats, fh, fa)
        rd = rest_diff(fh, fa)[:, None] if "rest_diff" in self.team_feats else np.zeros((len(m), 0))
        return Fh, Fa, M, rd

    def fit(self, history: pd.DataFrame, cutoff) -> None:
        h = history[history.division.isin(TIER1 | TIER2)]
        self._hist = h[KEEP].reset_index(drop=True)
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
        cd = closed_doors_mask(h)
        days = (cutoff.tz_convert("UTC").tz_localize(None) - h["date"]).dt.days.to_numpy()
        w = np.exp(-self.xi * days) * np.where(tier2 > 0, self.tier2_weight, 1.0)
        yh = h.home_goals.to_numpy(float)
        ya = h.away_goals.to_numpy(float)
        gh, ga = h.home_goals.to_numpy(), h.away_goals.to_numpy()

        fh, fa = team_context(self._hist)
        Fh, Fa, M, RD = self._designs(self._hist, fh, fa)
        K, J, R = Fh.shape[1], M.shape[1], RD.shape[1]
        tnames, mnames = team_feature_names(self.team_feats), match_feature_names(self.match_feats)
        ctx_names = (
            [f"own:{x}" for x in tnames]
            + [f"opp:{x}" for x in tnames]
            + [f"h:{x}" for x in mnames]
            + [f"a:{x}" for x in mnames]
            + (["rest_diff_own", "rest_diff_opp"] if R else [])
        )
        names = (
            [f"att:{t}" for t in teams]
            + [f"def:{t}" for t in teams]
            + ["c", "c_tier2"]
            + [f"home:{d}" for d in divs]
            + ["home_cd", "rho"]
            + ctx_names
        )
        n_dc = 2 * T + 2 + D + 2
        n_par = len(names)
        x0 = np.array([self._x.get(nm, 0.0) for nm in names])
        if "c" not in self._x:
            x0[2 * T] = np.log(max(np.average(yh, weights=w), 0.1))
            x0[2 * T + 2 : 2 * T + 2 + D] = 0.25
            x0[n_dc - 1] = -0.05
        rho_idx, hcd_idx = n_dc - 1, n_dc - 2
        o_own, o_opp, o_mh, o_ma, o_rd = n_dc, n_dc + K, n_dc + 2 * K, n_dc + 2 * K + J, n_dc + 2 * K + 2 * J
        rd = RD[:, 0] if R else None

        def f(x):
            att, dfn = x[:T], x[T : 2 * T]
            c, ct2 = x[2 * T], x[2 * T + 1]
            home = x[2 * T + 2 : 2 * T + 2 + D]
            hcd, rho = x[hcd_idx], x[rho_idx]
            b_own, b_opp = x[o_own : o_own + K], x[o_opp : o_opp + K]
            g_h, g_a = x[o_mh : o_mh + J], x[o_ma : o_ma + J]
            hadv = np.where(cd, hcd, home[di])
            ctx_h = Fh @ b_own + Fa @ b_opp + M @ g_h
            ctx_a = Fa @ b_own + Fh @ b_opp + M @ g_a
            if R:
                r_own, r_opp = x[o_rd], x[o_rd + 1]
                ctx_h = ctx_h + r_own * rd - r_opp * rd
                ctx_a = ctx_a - r_own * rd + r_opp * rd
            eta_h = c + ct2 * tier2 + hadv + att[hi] - dfn[ai] + ctx_h
            eta_a = c + ct2 * tier2 + att[ai] - dfn[hi] + ctx_a
            lh, la = np.exp(eta_h), np.exp(eta_a)
            ll = w * (yh * eta_h - lh + ya * eta_a - la)
            gh_ = w * (yh - lh)
            ga_ = w * (ya - la)
            tau, dt_rho, dt_eh, dt_ea = _tau_terms(gh, ga, lh, la, rho)
            ll = ll + w * np.log(tau)
            gh_ = gh_ + w * dt_eh
            ga_ = ga_ + w * dt_ea
            bctx = x[n_dc:]
            obj = -ll.sum() + 0.5 * self.reg * (att @ att + dfn @ dfn)
            obj += 0.5 * self.reg_ctx * (bctx @ bctx)
            g = np.zeros_like(x)
            g[:T] = -(np.bincount(hi, gh_, T) + np.bincount(ai, ga_, T)) + self.reg * att
            g[T : 2 * T] = (np.bincount(ai, gh_, T) + np.bincount(hi, ga_, T)) + self.reg * dfn
            g[2 * T] = -(gh_.sum() + ga_.sum())
            g[2 * T + 1] = -((gh_ + ga_) * tier2).sum()
            g[2 * T + 2 : 2 * T + 2 + D] = -np.bincount(di[~cd], gh_[~cd], D)
            g[hcd_idx] = -gh_[cd].sum()
            g[rho_idx] = -(w * dt_rho).sum()
            g[o_own : o_own + K] = -(Fh.T @ gh_ + Fa.T @ ga_)
            g[o_opp : o_opp + K] = -(Fa.T @ gh_ + Fh.T @ ga_)
            g[o_mh : o_mh + J] = -(M.T @ gh_)
            g[o_ma : o_ma + J] = -(M.T @ ga_)
            if R:
                g[o_rd] = -(rd @ gh_ - rd @ ga_)
                g[o_rd + 1] = -(-rd @ gh_ + rd @ ga_)
            g[n_dc:] += self.reg_ctx * bctx
            return obj, g

        bounds = [(None, None)] * n_par
        bounds[rho_idx] = (-0.25, 0.25)
        if not cd.any():
            bounds[hcd_idx] = (0.0, 0.0)
        res = minimize(f, x0, jac=True, method="L-BFGS-B", bounds=bounds, options={"maxiter": 500})
        self.coef = res.x
        self._x = dict(zip(names, res.x, strict=True))
        self.T, self.D = T, D
        self.home_by_div = {d: float(res.x[2 * T + 2 + i]) for d, i in self.div_idx.items()}
        self.rho_ = float(res.x[rho_idx])
        self.beta_ = dict(zip(ctx_names, res.x[n_dc:], strict=True))
        self.converged_ = bool(res.success)
        self._layout = (n_dc, K, J, R)
        # each division's mean attack/defence over the teams whose latest match was in it
        last_div = (
            pd.concat(
                [
                    h[["date", "home_team", "division"]].rename(columns={"home_team": "team"}),
                    h[["date", "away_team", "division"]].rename(columns={"away_team": "team"}),
                ]
            )
            .sort_values("date", kind="stable")
            .groupby("team")
            .division.last()
        )
        att, dfn = res.x[:T], res.x[T : 2 * T]
        self.div_mean = {}
        for d, g_ in last_div.groupby(last_div):
            ix = [self.team_idx[t] for t in g_.index]
            self.div_mean[d] = (float(att[ix].mean()), float(dfn[ix].mean()))

    # ---- prediction ----------------------------------------------------------------------

    def predict(self, fixtures: pd.DataFrame) -> MarketProbs:
        lh, la = self.context_rates(fixtures)
        return markets_from_grid(poisson_grid(lh, la, self.rho_), lh, la)

    def _shrink(self, a, d, m, n):
        """Move strength (attack + defence) and openness (attack - defence) towards the
        division mean by s0 + s1·exp(-n/k) and o0 + o1·exp(-n/k)."""
        s0, s1, o0, o1, k = self.early
        e = np.exp(-n / k)
        fs, fo = s0 + s1 * e, o0 + o1 * e
        st, op = a + d, a - d
        ms, mo = m[:, 0] + m[:, 1], m[:, 0] - m[:, 1]
        st = st - fs * (st - ms)
        op = op - fo * (op - mo)
        return (st + op) / 2, (st - op) / 2

    def context_rates(self, fixtures: pd.DataFrame) -> tuple[np.ndarray, np.ndarray]:
        n = len(fixtures)
        both = pd.concat([self._hist, fixtures[KEEP]], ignore_index=True)
        fh_all, fa_all = team_context(both)
        fh, fa = fh_all.iloc[-n:].reset_index(drop=True), fa_all.iloc[-n:].reset_index(drop=True)
        T, x = self.T, self.coef
        att, dfn, c, ct2 = x[:T], x[T : 2 * T], x[2 * T], x[2 * T + 1]

        def team(col, arr, default=0.0):
            return np.array([arr[self.team_idx[t]] if t in self.team_idx else default for t in fixtures[col]])

        a_h, d_h = team("home_team", att), team("home_team", dfn)
        a_a, d_a = team("away_team", att), team("away_team", dfn)
        if self.early is not None:
            m = np.array([self.div_mean.get(d, (0.0, 0.0)) for d in fixtures.division])
            a_h, d_h = self._shrink(a_h, d_h, m, fh["n_season"].to_numpy())
            a_a, d_a = self._shrink(a_a, d_a, m, fa["n_season"].to_numpy())
        if self.promo is not None:
            pa, pdf, k = self.promo
            ph = fh["promoted"].to_numpy() * np.exp(-fh["n_div_season"].to_numpy() / k)
            pa_ = fa["promoted"].to_numpy() * np.exp(-fa["n_div_season"].to_numpy() / k)
            a_h, d_h = a_h - pa * ph, d_h - pdf * ph
            a_a, d_a = a_a - pa * pa_, d_a - pdf * pa_
        tier2 = fixtures.division.isin(TIER2).to_numpy().astype(float)
        mean_home = np.mean(list(self.home_by_div.values()))
        home = np.array([self.home_by_div.get(d, mean_home) for d in fixtures.division])
        eta_h = c + ct2 * tier2 + home + a_h - d_a
        eta_a = c + ct2 * tier2 + a_a - d_h
        n_dc, K, J, R = self._layout
        if K or J or R:
            Fh, Fa, M, RD = self._designs(fixtures.reset_index(drop=True), fh, fa)
            b = x[n_dc:]
            b_own, b_opp = b[:K], b[K : 2 * K]
            g_h, g_a = b[2 * K : 2 * K + J], b[2 * K + J : 2 * K + 2 * J]
            eta_h = eta_h + Fh @ b_own + Fa @ b_opp + M @ g_h
            eta_a = eta_a + Fa @ b_own + Fh @ b_opp + M @ g_a
            if R:
                r_own, r_opp = b[2 * K + 2 * J], b[2 * K + 2 * J + 1]
                rd = RD[:, 0]
                eta_h = eta_h + (r_own - r_opp) * rd
                eta_a = eta_a - (r_own - r_opp) * rd
        return np.exp(eta_h), np.exp(eta_a)


CANDIDATES: dict = {}
