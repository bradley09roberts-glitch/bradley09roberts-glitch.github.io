"""Match-context effects on Dixon-Coles rates (model-lab family `congestion_context`).

`ContextDC` fits the core time-weighted Dixon-Coles model (footy.modeling.dixon_coles,
unchanged) and then multiplies its two expected-goal rates by context terms. Every market
still comes from one Dixon-Coles grid per fixture.

Context of a team before a match is computed from the schedule and from earlier results only
(`team_context`): days since its previous match in the data (any division the runner loads),
matches in the previous 14 and 21 days, matches played this season and in its division,
whether it was promoted (it played the second tier the season before), and whether it has
nothing left to play for in its division's table (`dead_rubbers`).

**Second stage (fitted in fit() on the history only).** A ridge Poisson regression of the
goals in the history on the context columns, with the Dixon-Coles in-sample log-rate as
offset and the Dixon-Coles time weights (`poisson_ridge`):

    log E[home goals] = DC log-rate + X_home · beta,   log E[away goals] = ... + X_away · beta

- team terms (`team_feats`), each with an effect on the team's own scoring and one on its
  opponent's scoring: `rest` (rest-day buckets <= 3, 4-5, 10-20, > 20 days; 6-9 is the
  reference), `congestion` (matches in the last 21 / 14 days above one a week), `rest_diff`
  (one antisymmetric term on the clipped rest difference), `promoted` (decaying with the
  matches played in the new division), `dead` (nothing left to play for: can no longer go
  down nor reach the top six, or already relegated, or already champion);
- `anti_feats`: the same team terms, but strength-only (as much off one side as on the other);
- match terms (`match_feats`), one effect on both rates: `late` (April-June), `end` (four or
  fewer matches left), `curve` (two annual harmonics of the date), `midweek`, `kickoff`
  (before 14:00 / from 19:00 local), and `season_level`: the current season's scoring level
  per tier, with its own much heavier ridge (`level_reg`), so it moves only once a season
  has shown a clearly different scoring rate (e.g. the 2023/24 added-time rules).

**Prediction-time adjustments (constants tuned before 2023/24).**
- `early` = (s0, s1, o0, o1, k): strength (attack + defence) and openness (attack - defence)
  move towards the mean of the team's division by s0 + s1·exp(-n/k) and o0 + o1·exp(-n/k),
  n = the team's matches so far this season. Out of sample (2020/21-2022/23), Dixon-Coles
  strength is well calibrated even in the first weeks (no strength shrinkage helps), but
  its openness differences are mostly noise early in a season.
- `promo` = (a, d, k): a newly promoted team's attack is lowered by a and its defence by d,
  times exp(-n/k), n = its matches in the new division. (a, d) = (0.113, 0.099) for k = 10
  is the maximum-likelihood fit to Dixon-Coles' out-of-sample errors on 2020/21-2022/23.

**On other models** (`ContextTilt`): the context's log-rate changes over plain Dixon-Coles are
applied to another registered model's score grid by exponential tilting (`tilt_grid`), which
for Poisson margins is exactly a rate multiplier, so every market stays coherent.
"""

import numpy as np
import pandas as pd

from footy.modeling.base import ALL_MARKETS
from footy.modeling.closed_doors import closed_doors_mask
from footy.modeling.data import SECOND_TIER, season_of
from footy.modeling.dixon_coles import DixonColes
from footy.modeling.grid import GOALS, MarketProbs, markets_from_grid, poisson_grid

TIER1 = set(SECOND_TIER)
TIER2 = set(SECOND_TIER.values())
KEEP = ["division", "season", "date", "kickoff_utc", "home_team", "away_team"]
GOAL_COLS = ["home_goals", "away_goals"]
REST_BUCKETS = (
    ("rest_le3", 0, 3),
    ("rest_4_5", 4, 5),
    ("rest_10_20", 10, 20),
    ("rest_gt20", 21, 10**6),
)


def _prev_season(s: str) -> str:
    a = int(s[:2]) - 1
    return f"{a % 100:02d}{(a + 1) % 100:02d}"


# ---- context of each team before each match -----------------------------------------------


def team_context(matches: pd.DataFrame) -> tuple[pd.DataFrame, pd.DataFrame]:
    """Context of the home and of the away team of every row of `matches`, from rows dated
    before it (results are read only where `home_goals` is present and not missing). Returns
    two frames aligned with `matches` by position."""
    n = len(matches)
    pos = np.arange(n)
    day = matches["date"].to_numpy("datetime64[D]").astype(np.int64)
    teams = np.concatenate([matches.home_team.to_numpy(), matches.away_team.to_numpy()])
    codes = pd.factorize(teams, sort=True)[0]
    seasons = np.concatenate([matches.season.to_numpy()] * 2)
    divs = np.concatenate([matches.division.to_numpy()] * 2)
    tier = np.where(np.isin(divs, list(TIER1)), 1, np.where(np.isin(divs, list(TIER2)), 2, 0))
    d2 = np.concatenate([day, day])
    order = np.lexsort((np.repeat([0, 1], n), np.concatenate([pos, pos]), d2, codes))
    c, d, s, t_now = codes[order], d2[order], seasons[order], tier[order]
    same = np.r_[False, c[1:] == c[:-1]] if len(c) else np.zeros(0, bool)
    rest = np.where(same, np.r_[0, np.diff(d)], np.nan).astype(float)
    key = c.astype(np.int64) * 1_000_000 + d
    idx = np.arange(len(key))
    c14 = idx - np.searchsorted(key, key - 14, side="left")
    c21 = idx - np.searchsorted(key, key - 21, side="left")
    app = pd.DataFrame({"team": c, "season": s, "div": divs[order], "tier": t_now})
    n_season = app.groupby(["team", "season"]).cumcount().to_numpy()
    n_div = app.groupby(["team", "season", "div"]).cumcount().to_numpy()
    # each team's tier in each season (majority of its matches); -1: season not in the data
    lut = app.groupby(["team", "season"]).tier.mean().round().astype(int).to_dict()
    known = set(app.season.unique())
    prev_tier = np.array(
        [
            lut.get((t, p), 0 if p in known else -1)
            for t, p in zip(c, map(_prev_season, s), strict=True)
        ],
        dtype=int,
    )
    n_teams = app.groupby(["season", "div"]).team.transform("nunique").to_numpy()
    left = np.where(n_div >= 10, 2 * (n_teams - 1) - n_div - 1, 99)  # early: teams not all seen
    out = pd.DataFrame(
        {
            "rest": rest,
            "c14": c14.astype(float),
            "c21": c21.astype(float),
            "n_season": n_season.astype(float),
            "n_div_season": n_div.astype(float),
            "promoted": ((t_now == 1) & (prev_tier == 2)).astype(float),
            "left": left.astype(float),
        }
    )
    back = np.empty_like(order)
    back[order] = np.arange(len(order))
    out = out.iloc[back].reset_index(drop=True)
    home, away = out.iloc[:n].reset_index(drop=True), out.iloc[n:].reset_index(drop=True)
    if "home_goals" in matches:
        home["dead"], away["dead"] = dead_rubbers(matches)
    else:
        home["dead"] = away["dead"] = 0.0
    return home, away


def dead_rubbers(matches: pd.DataFrame, top: int = 6, drop: int = 3):
    """For each row, whether its home / away team has nothing left to play for in its
    division's table before the match day (points only, 3 for a win, from results dated
    before): it can neither fall into the bottom `drop` places nor reach the top `top`, or it
    is already relegated, or already champion. Only checked in the second half of a season."""
    n = len(matches)
    dh, da = np.zeros(n), np.zeros(n)
    day = matches["date"].to_numpy("datetime64[D]")
    hg = matches["home_goals"].to_numpy(float)
    ag = matches["away_goals"].to_numpy(float)
    home_t, away_t = matches.home_team.to_numpy(), matches.away_team.to_numpy()
    for ix in matches.groupby(["division", "season"], sort=False).indices.values():
        teams, codes = np.unique(np.r_[home_t[ix], away_t[ix]], return_inverse=True)
        N = len(teams)
        if N < top + drop + 2:
            continue
        G = 2 * (N - 1)
        hc, ac = codes[: len(ix)], codes[len(ix) :]
        days, di = np.unique(day[ix], return_inverse=True)
        played = ~np.isnan(hg[ix])
        h, a = np.nan_to_num(hg[ix]), np.nan_to_num(ag[ix])
        ph = np.where(h > a, 3, np.where(h == a, 1, 0)) * played
        pa = np.where(a > h, 3, np.where(h == a, 1, 0)) * played
        pts = np.zeros((len(days) + 1, N))
        cnt = np.zeros((len(days) + 1, N))
        np.add.at(pts, (di + 1, hc), ph)
        np.add.at(pts, (di + 1, ac), pa)
        np.add.at(cnt, (di + 1, hc), played)
        np.add.at(cnt, (di + 1, ac), played)
        pts, cnt = pts.cumsum(axis=0)[:-1], cnt.cumsum(axis=0)[:-1]  # before each match day
        dead = np.zeros((len(days), N), bool)
        for k in np.flatnonzero(cnt.max(axis=1) >= G / 2):
            P, L = pts[k], G - cnt[k]
            order = np.argsort(-P, kind="stable")
            rank = np.empty(N, int)
            rank[order] = np.arange(N)
            bottom = order[N - drop :]
            threat = (P[bottom] + 3 * L[bottom]).max()
            down = (rank >= N - drop) | (threat >= P)
            up = (rank < top) | (P + 3 * L >= P[order[top - 1]])
            doomed = P + 3 * L < P[order[N - drop - 1]]
            champion = (rank == 0) & (P > np.where(rank == 0, -np.inf, P + 3 * L).max())
            dead[k] = (~down & ~up) | doomed | champion
        dh[ix] = dead[di, hc]
        da[ix] = dead[di, ac]
    return dh, da


# ---- design of the context terms ----------------------------------------------------------


def team_columns(f: pd.DataFrame, feats: tuple[str, ...], flag_decay: float) -> np.ndarray:
    cols = []
    rest = f["rest"].fillna(99.0).to_numpy()
    for name in feats:
        if name == "rest":
            cols += [((rest >= lo) & (rest <= hi)).astype(float) for _, lo, hi in REST_BUCKETS]
        elif name == "congestion":
            cols.append(np.clip(f["c21"].to_numpy() - 3.0, 0, None))
            cols.append(np.clip(f["c14"].to_numpy() - 2.0, 0, None))
        elif name == "promoted":
            decay = np.exp(-f["n_div_season"].to_numpy() / flag_decay)
            cols.append(f["promoted"].to_numpy() * decay)
        elif name == "dead":
            cols.append(f["dead"].to_numpy())
        elif name != "rest_diff":
            raise ValueError(f"unknown team feature {name!r}")
    return np.stack(cols, axis=1) if cols else np.zeros((len(f), 0))


def team_column_names(feats: tuple[str, ...]) -> list[str]:
    names: list[str] = []
    for name in feats:
        if name == "rest":
            names += [b[0] for b in REST_BUCKETS]
        elif name == "congestion":
            names += ["c21_over3", "c14_over2"]
        elif name != "rest_diff":
            names.append(name)
    return names


def match_columns(
    m: pd.DataFrame,
    feats: tuple[str, ...],
    fh: pd.DataFrame,
    fa: pd.DataFrame,
    cur_season: str | None,
    level_scale: float,
) -> np.ndarray:
    cols = []
    month = m["date"].dt.month.to_numpy()
    for name in feats:
        if name == "late":
            cols.append(np.isin(month, (4, 5, 6)).astype(float))
        elif name == "end":
            left = np.minimum(fh["left"].to_numpy(), fa["left"].to_numpy())
            cols.append((left <= 4).astype(float))
        elif name == "curve":
            doy = m["date"].dt.dayofyear.to_numpy() * (2 * np.pi / 365.25)
            cols += [np.sin(doy), np.cos(doy), np.sin(2 * doy), np.cos(2 * doy)]
        elif name == "midweek":
            dow = m["date"].dt.dayofweek.to_numpy()
            cols.append(((dow >= 1) & (dow <= 3)).astype(float))
        elif name == "kickoff":
            ko = pd.to_datetime(m["kickoff_utc"], utc=True)
            hour = np.where(
                m["division"].str.startswith("E").to_numpy(),
                ko.dt.tz_convert("Europe/London").dt.hour.to_numpy(float),
                ko.dt.tz_convert("Europe/Paris").dt.hour.to_numpy(float),
            )
            hour = np.nan_to_num(hour, nan=15.0)
            cols += [(hour < 14).astype(float), (hour >= 19).astype(float)]
        elif name == "season_level":
            cur = (m["season"].to_numpy() == cur_season).astype(float) * level_scale
            t2 = m["division"].isin(TIER2).to_numpy()
            cols += [cur * ~t2, cur * t2]
        else:
            raise ValueError(f"unknown match feature {name!r}")
    return np.stack(cols, axis=1) if cols else np.zeros((len(m), 0))


def match_column_names(feats: tuple[str, ...]) -> list[str]:
    multi = {
        "curve": ["sin1", "cos1", "sin2", "cos2"],
        "kickoff": ["ko_before14", "ko_from19"],
        "season_level": ["level_tier1", "level_tier2"],
    }
    return [x for name in feats for x in multi.get(name, [name])]


def rest_diff(fh: pd.DataFrame, fa: pd.DataFrame, clip: float = 4.0) -> np.ndarray:
    """Home rest minus away rest in days, clipped (unknown rest counts as a week)."""
    rh = fh["rest"].fillna(7.0).clip(upper=14).to_numpy()
    ra = fa["rest"].fillna(7.0).clip(upper=14).to_numpy()
    return np.clip(rh - ra, -clip, clip)


def poisson_ridge(X, y, offset, w, reg, b0=None, iters=30) -> np.ndarray:
    """Weighted Poisson regression with an offset and ridge penalty 0.5·reg·|b|², by Newton."""
    b = np.zeros(X.shape[1]) if b0 is None else b0.copy()
    for _ in range(iters):
        mu = np.exp(offset + X @ b)
        g = X.T @ (w * (y - mu)) - reg * b
        H = (X * (w * mu)[:, None]).T @ X + reg * np.eye(len(b))
        step = np.linalg.solve(H, g)
        b += step
        if np.abs(step).max() < 1e-10:
            break
    return b


# ---- the model ----------------------------------------------------------------------------


class ContextDC(DixonColes):
    def __init__(
        self,
        team_feats: tuple[str, ...] = (),
        anti_feats: tuple[str, ...] = (),
        match_feats: tuple[str, ...] = (),
        reg_ctx: float = 10.0,
        level_reg: float = 300.0,
        flag_decay: float = 10.0,
        early: tuple[float, float, float, float, float] | None = None,
        promo: tuple[float, float, float] | None = None,
        xi: float = 0.0019,
        reg: float = 2.0,
        tier2_weight: float = 0.5,
        name: str | None = None,
    ):
        super().__init__(xi=xi, rho=True, reg=reg, tier2_weight=tier2_weight)
        self.name = name or "congestion_context"
        self.team_feats, self.anti_feats = tuple(team_feats), tuple(anti_feats)
        self.match_feats = tuple(match_feats)
        self.reg_ctx, self.level_reg, self.flag_decay = reg_ctx, level_reg, flag_decay
        self.early, self.promo = early, promo
        self.ctx_ = np.zeros(0)
        self.beta_: dict[str, float] = {}
        self.div_mean: dict[str, tuple[float, float]] = {}
        self._hist: pd.DataFrame | None = None
        self._cur_season: str | None = None

    def params(self) -> dict:
        return super().params() | {
            "team_feats": list(self.team_feats),
            "anti_feats": list(self.anti_feats),
            "match_feats": list(self.match_feats),
            "reg_ctx": self.reg_ctx,
            "level_reg": self.level_reg,
            "flag_decay": self.flag_decay,
            "early": None if self.early is None else list(self.early),
            "promo": None if self.promo is None else list(self.promo),
        }

    # ---- context design ------------------------------------------------------------------

    def ctx_names(self) -> list[str]:
        t = team_column_names(self.team_feats)
        out = [f"own:{x}" for x in t] + [f"opp:{x}" for x in t]
        out += [f"match:{x}" for x in match_column_names(self.match_feats)]
        out += ["rest_diff"] if "rest_diff" in self.team_feats else []
        return out + [f"anti:{x}" for x in team_column_names(self.anti_feats)]

    def ctx_design(self, m: pd.DataFrame, fh: pd.DataFrame, fa: pd.DataFrame):
        """Context columns for the home-goal and the away-goal log-rates (X_h, X_a)."""
        Fh = team_columns(fh, self.team_feats, self.flag_decay)
        Fa = team_columns(fa, self.team_feats, self.flag_decay)
        scale = np.sqrt(self.reg_ctx / self.level_reg)  # ridge level_reg on season_level
        M = match_columns(m, self.match_feats, fh, fa, self._cur_season, scale)
        xh, xa = [Fh, Fa, M], [Fa, Fh, M]
        if "rest_diff" in self.team_feats:
            rd = rest_diff(fh, fa)[:, None]
            xh.append(rd)
            xa.append(-rd)
        if self.anti_feats:
            Ah = team_columns(fh, self.anti_feats, self.flag_decay)
            Aa = team_columns(fa, self.anti_feats, self.flag_decay)
            xh.append(Ah - Aa)
            xa.append(Aa - Ah)
        return np.hstack(xh), np.hstack(xa)

    # ---- fitting -------------------------------------------------------------------------

    def fit(self, history: pd.DataFrame, cutoff) -> None:
        super().fit(history, cutoff)  # the core Dixon-Coles fit (warm-started)
        h = history[history.division.isin(TIER1 | TIER2)]
        self._hist = h[KEEP + GOAL_COLS].reset_index(drop=True)
        self._cur_season = season_of(cutoff.tz_convert("Europe/London").date())
        if self.team_feats or self.anti_feats or self.match_feats:
            fh, fa = team_context(self._hist)
            Xh, Xa = self.ctx_design(self._hist, fh, fa)
            eta_h, eta_a, w = self._in_sample(h, cutoff)
            P = Xh.shape[1]
            self.ctx_ = poisson_ridge(
                np.vstack([Xh, Xa]),
                np.r_[h.home_goals.to_numpy(float), h.away_goals.to_numpy(float)],
                np.r_[eta_h, eta_a],
                np.r_[w, w],
                self.reg_ctx,
                self.ctx_ if len(self.ctx_) == P else None,
            )
        self.beta_ = dict(zip(self.ctx_names(), map(float, self.ctx_), strict=True))
        # each division's mean attack and defence, over the teams whose latest match was in it
        T = self.T
        att, dfn = self.coef[:T], self.coef[T : 2 * T]
        apps = pd.concat(
            [
                h[["date", "home_team", "division"]].set_axis(["date", "team", "div"], axis=1),
                h[["date", "away_team", "division"]].set_axis(["date", "team", "div"], axis=1),
            ]
        )
        last_div = apps.sort_values("date", kind="stable").groupby("team")["div"].last()
        self.div_mean = {}
        for d, g in last_div.groupby(last_div):
            ix = [self.team_idx[t] for t in g.index]
            self.div_mean[d] = (float(att[ix].mean()), float(dfn[ix].mean()))

    def _in_sample(self, h: pd.DataFrame, cutoff):
        """The fitted Dixon-Coles log-rates of the training matches, and their weights."""
        T, D, x = self.T, self.D, self.coef
        hi = h.home_team.map(self.team_idx).to_numpy()
        ai = h.away_team.map(self.team_idx).to_numpy()
        di = h.division.map(self.div_idx).to_numpy()
        tier2 = h.division.isin(TIER2).to_numpy().astype(float)
        cd = closed_doors_mask(h) if self.closed_doors else np.zeros(len(h), bool)
        days = (cutoff.tz_convert("UTC").tz_localize(None) - h["date"]).dt.days.to_numpy()
        w = np.exp(-self.xi * days) * np.where(tier2 > 0, self.tier2_weight, 1.0)
        att, dfn, c, ct2 = x[:T], x[T : 2 * T], x[2 * T], x[2 * T + 1]
        hadv = np.where(cd, x[2 * T + 2 + D], x[2 * T + 2 : 2 * T + 2 + D][di])
        eta_h = c + ct2 * tier2 + hadv + att[hi] - dfn[ai]
        eta_a = c + ct2 * tier2 + att[ai] - dfn[hi]
        return eta_h, eta_a, w

    # ---- prediction ----------------------------------------------------------------------

    def predict(self, fixtures: pd.DataFrame) -> MarketProbs:
        lh, la = self.context_rates(fixtures)
        return markets_from_grid(poisson_grid(lh, la, self.rho_), lh, la)

    def _shrink(self, a, d, m, n):
        """Strength (a + d) and openness (a - d) towards the division mean."""
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
        fx = fixtures[KEEP].reset_index(drop=True)
        fh, fa = team_context(pd.concat([self._hist, fx], ignore_index=True))
        fh, fa = fh.iloc[-n:].reset_index(drop=True), fa.iloc[-n:].reset_index(drop=True)
        T, x = self.T, self.coef
        att, dfn, c, ct2 = x[:T], x[T : 2 * T], x[2 * T], x[2 * T + 1]

        def team(col, arr):
            return np.array([arr[self.team_idx[t]] if t in self.team_idx else 0.0 for t in fx[col]])

        a_h, d_h = team("home_team", att), team("home_team", dfn)
        a_a, d_a = team("away_team", att), team("away_team", dfn)
        if self.early is not None:
            m = np.array([self.div_mean.get(d, (0.0, 0.0)) for d in fx.division])
            a_h, d_h = self._shrink(a_h, d_h, m, fh["n_season"].to_numpy())
            a_a, d_a = self._shrink(a_a, d_a, m, fa["n_season"].to_numpy())
        if self.promo is not None:
            pa, pd_, k = self.promo
            ph = fh["promoted"].to_numpy() * np.exp(-fh["n_div_season"].to_numpy() / k)
            pw = fa["promoted"].to_numpy() * np.exp(-fa["n_div_season"].to_numpy() / k)
            a_h, d_h = a_h - pa * ph, d_h - pd_ * ph
            a_a, d_a = a_a - pa * pw, d_a - pd_ * pw
        tier2 = fx.division.isin(TIER2).to_numpy().astype(float)
        mean_home = np.mean(list(self.home_by_div.values()))
        home = np.array([self.home_by_div.get(d, mean_home) for d in fx.division])
        eta_h = c + ct2 * tier2 + home + a_h - d_a
        eta_a = c + ct2 * tier2 + a_a - d_h
        if len(self.ctx_):
            Xh, Xa = self.ctx_design(fx, fh, fa)
            eta_h = eta_h + Xh @ self.ctx_
            eta_a = eta_a + Xa @ self.ctx_
        return np.exp(eta_h), np.exp(eta_a)


# ---- the same context terms on another model's grid ---------------------------------------


def tilt_grid(grid: np.ndarray, dh: np.ndarray, da: np.ndarray) -> np.ndarray:
    """Exponentially tilt each score grid: cell (i, j) × exp(dh·i + da·j), renormalised. For
    independent Poisson margins this multiplies the two rates by exp(dh) and exp(da) exactly,
    so it carries multiplicative context effects onto any coherent score grid."""
    i = GOALS[None, :, None]
    j = GOALS[None, None, :]
    g = grid * np.exp(dh[:, None, None] * i + da[:, None, None] * j)
    return g / g.sum(axis=(1, 2), keepdims=True)


class ContextTilt:
    """Another registered score-grid model (`base`, looked up when the model is built) with
    the context terms of a ContextDC (`ctx`) applied as a tilt: each fixture's grid is
    tilted by the ContextDC's log-rate change over its own plain Dixon-Coles rates."""

    markets = ALL_MARKETS

    def __init__(self, base: str, ctx: dict, name: str):
        from footy.modeling.registry import candidates  # late: the registry imports this module

        self.base_name, self.name = base, name
        self.base = candidates()[base]()
        self.ctx = ContextDC(**ctx)

    def params(self) -> dict:
        return {"base": self.base_name, "base_params": self.base.params(), "ctx": self.ctx.params()}

    def fit(self, history: pd.DataFrame, cutoff) -> None:
        self.base.fit(history, cutoff)
        self.ctx.fit(history, cutoff)

    def predict(self, fixtures: pd.DataFrame) -> MarketProbs:
        mp = self.base.predict(fixtures)
        if mp.grid is None:
            raise ValueError(f"{self.base_name} has no score grid to tilt")
        lh, la = self.ctx.context_rates(fixtures)
        bh, ba = DixonColes.rates(self.ctx, fixtures)
        dh, da = np.log(lh / bh), np.log(la / ba)
        return markets_from_grid(
            tilt_grid(mp.grid, dh, da),
            None if mp.lam_h is None else mp.lam_h * np.exp(dh),
            None if mp.lam_a is None else mp.lam_a * np.exp(da),
        )


# ---- registered candidates ----------------------------------------------------------------

PROMO = (0.113, 0.099, 10.0)  # ML fit to Dixon-Coles' out-of-sample errors, 2020/21-2022/23
EARLY = (0.0, 0.0, 0.0, 0.6, 15.0)  # openness only; tuned on 2021/22 + 2022/23
CONFIGS: dict[str, dict] = {
    # the literal congestion hypothesis: rest days, fixture congestion, rest difference
    "congestion_context_rest": {"team_feats": ("rest", "congestion", "rest_diff")},
    # motivation and calendar: dead rubbers, April-June, newly promoted penalty
    "congestion_context_motivation": {
        "team_feats": ("dead",),
        "match_feats": ("late",),
        "promo": PROMO,
    },
    # ... plus early-season openness shrinkage
    "congestion_context_early": {
        "team_feats": ("dead",),
        "match_feats": ("late",),
        "promo": PROMO,
        "early": EARLY,
    },
    # ... plus the current season's scoring level (heavy ridge)
    "congestion_context_full": {
        "team_feats": ("dead",),
        "match_feats": ("late", "season_level"),
        "promo": PROMO,
        "early": EARLY,
    },
}


# dead rubbers and April-June on the goal-based Elo of the elo_goals family (its own promoted
# and scoring-level terms make PROMO and season_level redundant there; EARLY did not stack)
TILTS: dict[str, tuple[str, dict]] = {
    "congestion_context_on_elo": (
        "elo_goals_gd",
        {"team_feats": ("dead",), "match_feats": ("late",)},
    ),
}


def _factory(name: str, cfg: dict):
    return lambda: ContextDC(name=name, **cfg)


def _tilt_factory(name: str, base: str, cfg: dict):
    return lambda: ContextTilt(base, cfg, name)


CANDIDATES = {name: _factory(name, cfg) for name, cfg in CONFIGS.items()} | {
    name: _tilt_factory(name, base, cfg) for name, (base, cfg) in TILTS.items()
}
