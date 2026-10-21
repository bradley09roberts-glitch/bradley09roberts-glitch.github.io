"""Machine learning on leakage-safe engineered features (model-lab family `ml_gbm`).

Every match in the history becomes two team-perspective rows (home side attacking, away side
attacking). Each row's features are computed by one chronological pass over the history, from
matches dated strictly before it: exponentially weighted goals, shots on target and shots for
and against (short and long half-lives, shrunk towards the division level), a goal-margin Elo,
online Poisson attack/defence ratings from goals and from shots on target, days of rest,
matches in the previous 14 days, matches played (overall and this season), a promoted /
relegated flag, the share of recent matches played in the other tier, and the division's
scoring level and home advantage. Fixture features come from the same pass's final state.

A Poisson-loss gradient booster (histogram trees, numpy only: sklearn is not a dependency)
maps the features to the expected goals of the attacking side; the two means go into the
Poisson grid with a Dixon-Coles rho fitted on the training rows. A Poisson GLM on the same
features is the linear counterpart.

Stacking: the stacked variants start every row from a Dixon-Coles log-rate and learn a
correction. Training rows use out-of-sample Dixon-Coles rates: one DC fit per weekly grid
date, on the history before that date, applied to the matches of the following week. The
fixtures use a DC fit at the batch cutoff. Optionally the final grid is a mixture with the
Dixon-Coles grid (a mixture of score grids stays coherent across every market).

Uses only the DataFrames passed to fit/predict and no randomness. The booster and GLM are
refitted every `refit_days`; between refits the fitted mapping is reused on features
recomputed from the latest history. The DC grid fits are cached by grid date; each one only
ever sees matches dated before its grid date.
"""

import copy

import numpy as np
import pandas as pd
from scipy.optimize import minimize, minimize_scalar

from footy.modeling.base import ALL_MARKETS
from footy.modeling.data import SECOND_TIER
from footy.modeling.dixon_coles import DixonColes
from footy.modeling.grid import MarketProbs, markets_from_grid, mix_grids, poisson_grid

TIER1 = set(SECOND_TIER)
TIER2 = set(SECOND_TIER.values())
GRID_ANCHOR = np.datetime64("2000-01-07", "D")  # a Friday: weekly DC grid dates are Fridays
DEFAULT_LEVEL = {"g": 1.35, "s": 4.4, "sh": 12.5, "c": 5.0}  # per team-match

# ---- gradient boosting -------------------------------------------------------------------


class PoissonBooster:
    """Histogram gradient boosting for counts: Poisson deviance, log link, depth-wise trees.

    Gain and leaf values use the Newton step (gradient mu - y, hessian mu), with an L2 penalty,
    a minimum number of rows per leaf and a cap on each leaf's step (as XGBoost's
    max_delta_step for Poisson). Optional per-row offset (log scale) makes it boost from a
    given model; base score is fitted on top of the offset."""

    def __init__(
        self,
        n_trees: int = 200,
        lr: float = 0.05,
        max_depth: int = 3,
        min_leaf: int = 60,
        l2: float = 10.0,
        max_bins: int = 32,
        max_delta: float = 0.7,
    ):
        self.n_trees, self.lr, self.max_depth = n_trees, lr, max_depth
        self.min_leaf, self.l2, self.max_bins, self.max_delta = min_leaf, l2, max_bins, max_delta
        self.edges: list[np.ndarray] = []
        self.trees: list[tuple[np.ndarray, np.ndarray, np.ndarray]] = []
        self.base = 0.0

    def _transform(self, X: np.ndarray) -> np.ndarray:
        B = np.empty(X.shape, dtype=np.int64)
        for j, e in enumerate(self.edges):
            B[:, j] = np.searchsorted(e, X[:, j], side="right")
        return B

    def fit(self, X, y, w=None, offset=None) -> "PoissonBooster":
        X, y = np.asarray(X, float), np.asarray(y, float)
        n, F = X.shape
        w = np.ones(n) if w is None else np.asarray(w, float)
        off = np.zeros(n) if offset is None else np.asarray(offset, float)
        qs = np.linspace(0, 1, self.max_bins + 1)[1:-1]
        self.edges = [np.unique(np.quantile(X[:, j], qs)) for j in range(F)]
        Bn = self._transform(X)
        nb = self.max_bins
        keys = Bn + (np.arange(F) * nb)[None, :]
        self.base = float(np.log(max((w * y).sum(), 1e-9) / (w * np.exp(off)).sum()))
        fx = off + self.base
        self.trees = []
        for _ in range(self.n_trees):
            mu = np.exp(fx)
            feat, thr, val, node = self._grow(keys, Bn, w * (mu - y), w * mu, F, nb)
            self.trees.append((feat, thr, val))
            fx = fx + val[node]
        return self

    def _grow(self, keys, Bn, g, h, F, nb):
        n = len(g)
        n_nodes = 2 ** (self.max_depth + 1) - 1
        feat = np.full(n_nodes, -1)
        thr = np.zeros(n_nodes, dtype=np.int64)
        open_ = np.zeros(n_nodes, dtype=bool)
        open_[0] = True
        node = np.zeros(n, dtype=np.int64)
        for depth in range(self.max_depth):
            start, L = 2**depth - 1, 2**depth
            idx = np.flatnonzero(open_[node])
            if idx.size == 0:
                break
            loc = node[idx] - start
            k = (keys[idx] + (loc * (F * nb))[:, None]).ravel()
            size = L * F * nb
            G = np.bincount(k, np.repeat(g[idx], F), size).reshape(L, F, nb).cumsum(axis=2)
            H = np.bincount(k, np.repeat(h[idx], F), size).reshape(L, F, nb).cumsum(axis=2)
            C = np.bincount(k, None, size).reshape(L, F, nb).cumsum(axis=2)
            Gt, Ht, Ct = G[:, :, -1:], H[:, :, -1:], C[:, :, -1:]
            l2 = self.l2
            gain = G**2 / (H + l2) + (Gt - G) ** 2 / (Ht - H + l2) - Gt**2 / (Ht + l2)
            ok = (self.min_leaf <= C) & (self.min_leaf <= Ct - C)
            gain = np.where(ok, gain, -np.inf).reshape(L, F * nb)
            best = gain.argmax(axis=1)
            best_gain = gain[np.arange(L), best]
            for li in range(L):
                nd = start + li
                if not open_[nd]:
                    continue
                open_[nd] = False
                if np.isfinite(best_gain[li]) and best_gain[li] > 1e-12:
                    feat[nd], thr[nd] = best[li] // nb, best[li] % nb
                    if depth + 1 < self.max_depth:
                        open_[2 * nd + 1] = open_[2 * nd + 2] = True
            nd = node[idx]
            split = feat[nd] >= 0
            rows, nd = idx[split], nd[split]
            right = Bn[rows, feat[nd]] > thr[nd]
            node[rows] = 2 * nd + 1 + right
        Gs = np.bincount(node, g, n_nodes)
        Hs = np.bincount(node, h, n_nodes)
        val = np.clip(-Gs / (Hs + self.l2), -self.max_delta, self.max_delta) * self.lr
        return feat, thr, val, node

    def predict_log(self, X, offset=None) -> np.ndarray:
        X = np.asarray(X, float)
        n = len(X)
        Bn = self._transform(X)
        out = np.full(n, self.base) + (0.0 if offset is None else np.asarray(offset, float))
        rows = np.arange(n)
        for feat, thr, val in self.trees:
            node = np.zeros(n, dtype=np.int64)
            for _ in range(self.max_depth):
                f = feat[node]
                inner = f >= 0
                if not inner.any():
                    break
                b = Bn[rows, np.maximum(f, 0)]
                node = np.where(inner, 2 * node + 1 + (b > thr[node]), node)
            out += val[node]
        return out

    def importance(self, names: list[str]) -> dict[str, int]:
        counts = np.zeros(len(names), dtype=int)
        for feat, _, _ in self.trees:
            for f in feat[feat >= 0]:
                counts[f] += 1
        return {n: int(c) for n, c in sorted(zip(names, counts, strict=True), key=lambda x: -x[1])}


class PoissonGLM:
    """Poisson regression with log link, ridge penalty on standardised features, optional
    offset. The linear counterpart of the booster. Columns listed in `free` are not
    penalised (e.g. a Dixon-Coles log-rate whose weight the data should set)."""

    def __init__(self, l2: float = 1.0, free: tuple[int, ...] = ()):
        self.l2, self.free = l2, tuple(free)

    def fit(self, X, y, w=None, offset=None) -> "PoissonGLM":
        X, y = np.asarray(X, float), np.asarray(y, float)
        n, F = X.shape
        w = np.ones(n) if w is None else np.asarray(w, float)
        off = np.zeros(n) if offset is None else np.asarray(offset, float)
        self.mean = X.mean(axis=0)
        sd = X.std(axis=0)
        self.keep = sd > 1e-9
        self.sd = np.where(self.keep, sd, 1.0)
        Z = ((X - self.mean) / self.sd)[:, self.keep]
        sw = w.sum()
        pen = np.full(F, self.l2 / 100)
        pen[list(self.free)] = 0.0
        pen = pen[self.keep]

        def f(b):
            eta = off + b[0] + Z @ b[1:]
            mu = np.exp(eta)
            r = w * (mu - y)
            obj = (w * (mu - y * eta)).sum() / sw + 0.5 * (pen * b[1:]) @ b[1:]
            g = np.concatenate([[r.sum()], Z.T @ r]) / sw
            g[1:] += pen * b[1:]
            return obj, g

        b0 = np.zeros(Z.shape[1] + 1)
        b0[0] = np.log(max((w * y).sum(), 1e-9) / (w * np.exp(off)).sum())
        res = minimize(f, b0, jac=True, method="L-BFGS-B", options={"maxiter": 500})
        self.coef = res.x
        return self

    def predict_log(self, X, offset=None) -> np.ndarray:
        Z = ((np.asarray(X, float) - self.mean) / self.sd)[:, self.keep]
        out = self.coef[0] + Z @ self.coef[1:]
        return out + (0.0 if offset is None else np.asarray(offset, float))


class GLMThenBoost:
    """A Poisson GLM, then a booster on its residual: the GLM's log-rate is the booster's
    offset, so the trees only add non-linear corrections."""

    def __init__(self, glm_l2: float, free: tuple[int, ...] = (), **gbm):
        self.glm = PoissonGLM(glm_l2, free)
        self.gbm = PoissonBooster(**gbm)

    def fit(self, X, y, w=None, offset=None) -> "GLMThenBoost":
        self.glm.fit(X, y, w, offset)
        self.gbm.fit(X, y, w, self.glm.predict_log(X, offset))
        return self

    def predict_log(self, X, offset=None) -> np.ndarray:
        return self.gbm.predict_log(X, self.glm.predict_log(X, offset))


class SplitVenue:
    """Separate learners for home-side and away-side rows (column 0 is the home flag)."""

    def __init__(self, make):
        self.make = make

    def fit(self, X, y, w=None, offset=None) -> "SplitVenue":
        home = X[:, 0] > 0.5
        w = np.ones(len(y)) if w is None else w
        self.models = {}
        for v, m in ((True, home), (False, ~home)):
            off = None if offset is None else offset[m]
            self.models[v] = self.make().fit(X[m], y[m], w[m], off)
        return self

    def predict_log(self, X, offset=None) -> np.ndarray:
        out = np.zeros(len(X))
        home = X[:, 0] > 0.5
        for v, m in ((True, home), (False, ~home)):
            if m.any():
                off = None if offset is None else offset[m]
                out[m] = self.models[v].predict_log(X[m], off)
        return out


# ---- features ------------------------------------------------------------------------------

STAT_COLS = {"g": ("home_goals", "away_goals"), "s": ("home_sot", "away_sot")}
STAT_COLS["sh"] = ("home_shots", "away_shots")
STAT_COLS["c"] = ("home_corners", "away_corners")
FEATURES = [
    "home",
    "tier2",
    "lg",
    "lg_hadv",
    "gf_s",
    "gf_l",
    "ga_o_s",
    "ga_o_l",
    "sf_s",
    "sf_l",
    "sa_o_s",
    "sa_o_l",
    "shf_l",
    "sha_o_l",
    "ga_t_l",
    "gf_o_l",
    "elo_d",
    "on_g",
    "on_s",
    "rest_t",
    "rest_o",
    "cong_t",
    "cong_o",
    "n_t",
    "n_o",
    "sn_t",
    "sn_o",
    "chg_t",
    "chg_o",
    "oth_t",
    "oth_o",
    "cf_l",
    "ca_o_l",
]


def _days(s: pd.Series) -> np.ndarray:
    return s.to_numpy().astype("datetime64[D]").astype(np.int64)


class FeatureScan:
    """A chronological pass over a history: features for each match from the state before its
    day (matches on the same day never see each other), and fixture features from the latest
    state. `extend` processes only matches newer than any seen so far, so a walk-forward run
    scans each match once; the features cached for a match never change afterwards."""

    # per-team state arrays: name -> (team axis, fill value, dtype)
    TEAM_STATE = {
        "S": (1, 0.0, float),  # EW sums [decay, team, stat]: goals, SoT, shots for / against
        "W": (1, 0.0, float),  # EW weights (separate per stat: SoT and shots can be missing)
        "tierS": (0, 0.0, float),
        "tierW": (0, 0.0, float),
        "elo": (0, 0.0, float),
        "att": (1, 0.0, float),  # online Poisson attack [goals / SoT, team]
        "dfn": (1, 0.0, float),
        "last_day": (0, -10_000, np.int64),
        "recent": (0, -10_000, np.int64),  # the last four match days
        "n": (0, 0.0, float),
        "sn": (0, 0.0, float),
        "last_season": (0, -1, np.int64),
        "last_tier": (0, -1, np.int64),
        "prev_tier": (0, -1, np.int64),
    }

    def __init__(
        self,
        hl_short: float = 4.0,
        hl_long: float = 16.0,
        hl_league: float = 200.0,
        prior_n: float = 3.0,
        elo_k: float = 20.0,
        elo_home: float = 60.0,
        eta: float = 0.05,
    ):
        self.d = np.array([0.5 ** (1 / hl_short), 0.5 ** (1 / hl_long)])
        self.d_lg = 0.5 ** (1 / hl_league)
        self.prior_n, self.elo_k, self.elo_home, self.eta = prior_n, elo_k, elo_home, eta
        self.tid: dict[str, int] = {}
        self.did = {d: i for i, d in enumerate(sorted(TIER1 | TIER2))}
        self.seasons: dict[str, int] = {}
        D = len(self.did)
        shapes = {"S": (2, 0, 8), "W": (2, 0, 8), "att": (2, 0), "dfn": (2, 0), "recent": (0, 4)}
        for name, (_, fill, dtype) in self.TEAM_STATE.items():
            setattr(self, name, np.full(shapes.get(name, (0,)), fill, dtype=dtype))
        lv = np.array([DEFAULT_LEVEL[k] for k in ("g", "s", "sh", "c")])
        self.lgS = np.tile(lv * 5.0, (D, 1))  # per division: EW mean per team-match, x weight
        self.lgH = np.tile(np.array([1.5, 1.2]) * 5.0, (D, 1))  # home goals, away goals
        self.lgW = np.full(D, 5.0)
        self.last_processed = -(10**9)
        self.ids: dict[int, int] = {}  # match id -> row in the feature cache
        self._rows_h: list[np.ndarray] = []
        self._rows_a: list[np.ndarray] = []
        self._Xh = np.zeros((0, len(FEATURES)))
        self._Xa = np.zeros((0, len(FEATURES)))

    def _team_index(self, names) -> np.ndarray:
        new = sorted({t for t in names if t not in self.tid})
        if new:
            for t in new:
                self.tid[t] = len(self.tid)
            for name, (axis, fill, dtype) in self.TEAM_STATE.items():
                a = getattr(self, name)
                shape = list(a.shape)
                shape[axis] = len(new)
                setattr(self, name, np.concatenate([a, np.full(shape, fill, dtype)], axis=axis))
        return np.array([self.tid[t] for t in names], dtype=np.int64)

    def covers(self, h: pd.DataFrame) -> bool:
        """True when `h` adds only matches dated after everything already processed."""
        old = _days(h["date"]) <= self.last_processed
        return bool(np.isin(h["id"].to_numpy()[old], list(self.ids)).all())

    def extend(self, h: pd.DataFrame) -> None:
        """Process the matches of `h` (sorted by date) dated after the last processed day."""
        h = h[_days(h["date"]) > self.last_processed]
        n = len(h)
        if not n:
            return
        hi = self._team_index(h.home_team.tolist())
        ai = self._team_index(h.away_team.tolist())
        di = h.division.map(self.did).to_numpy()
        t2 = h.division.isin(TIER2).to_numpy().astype(np.int64)
        for s in h.season:
            self.seasons.setdefault(s, len(self.seasons))
        si = h.season.map(self.seasons).to_numpy()
        day = _days(h["date"])
        y = {k: (h[a].to_numpy(float), h[b].to_numpy(float)) for k, (a, b) in STAT_COLS.items()}
        Xh = np.zeros((n, len(FEATURES)))
        Xa = np.zeros((n, len(FEATURES)))
        cut = np.flatnonzero(np.diff(day)) + 1
        for lo, hi_ in zip(np.r_[0, cut], np.r_[cut, n], strict=True):
            m = slice(lo, hi_)
            args = (di[m], t2[m], si[m], day[m])
            Xh[m] = self._features(hi[m], ai[m], 1, *args)
            Xa[m] = self._features(ai[m], hi[m], 0, *args)
            self._update(hi[m], ai[m], *args, {k: (a[m], b[m]) for k, (a, b) in y.items()})
        base = len(self._Xh)
        self._Xh = np.vstack([self._Xh, Xh])
        self._Xa = np.vstack([self._Xa, Xa])
        for j, i in enumerate(h["id"].to_numpy()):
            self.ids[int(i)] = base + j
        self.last_processed = int(day[-1])

    def rows(self, ids) -> tuple[np.ndarray, np.ndarray]:
        """Cached pre-match features of processed matches."""
        idx = np.array([self.ids[int(i)] for i in ids], dtype=np.int64)
        return self._Xh[idx], self._Xa[idx]

    def fixture_features(self, fx: pd.DataFrame):
        hi = self._team_index(fx.home_team.tolist())  # unseen teams get a fresh prior state
        ai = self._team_index(fx.away_team.tolist())
        args = (
            fx.division.map(self.did).to_numpy(),
            fx.division.isin(TIER2).to_numpy().astype(np.int64),
            np.array([self.seasons.get(s, -2) for s in fx.season]),  # -2: a season not seen
            _days(fx["date"]),
        )
        return self._features(hi, ai, 1, *args), self._features(ai, hi, 0, *args)

    def _level(self, di):
        """Division level per team-match for goals, SoT, shots; home and away goal rates."""
        lv = self.lgS[di] / self.lgW[di][:, None]
        hv = self.lgH[di] / self.lgW[di][:, None]
        return lv, hv

    def _ew(self, k, t, stat, prior):
        return np.log(
            (self.S[k, t, stat] + self.prior_n * prior) / (self.W[k, t, stat] + self.prior_n)
        )

    def _features(self, t, o, home, di, t2, si, day):
        lv, hv = self._level(di)
        g, s, sh = lv[:, 0], lv[:, 1], lv[:, 2]
        ha = np.log(hv[:, 0] / hv[:, 1])
        sgn = 1.0 if home else -1.0
        cur_tier = t2 + 1

        def chg(x):
            prev = np.where(self.last_season[x] == si, self.prev_tier[x], self.last_tier[x])
            return np.where(prev < 0, 0, prev - cur_tier)  # +1 promoted, -1 relegated

        def oth(x):
            frac2 = self.tierS[x] / np.maximum(self.tierW[x], 1e-9)
            return np.where(self.tierW[x] > 0, np.abs(frac2 - t2), 0.0)

        on_base = np.log(hv[:, 0] if home else hv[:, 1])
        sot_base = np.log(s) + sgn * ha / 2
        cols = [
            np.full(len(t), float(home)),
            t2.astype(float),
            np.log(g),
            ha,
            self._ew(0, t, 0, g),
            self._ew(1, t, 0, g),
            self._ew(0, o, 1, g),
            self._ew(1, o, 1, g),
            self._ew(0, t, 2, s),
            self._ew(1, t, 2, s),
            self._ew(0, o, 3, s),
            self._ew(1, o, 3, s),
            self._ew(1, t, 4, sh),
            self._ew(1, o, 5, sh),
            self._ew(1, t, 1, g),
            self._ew(1, o, 0, g),
            (self.elo[t] - self.elo[o] + sgn * self.elo_home) / 400,
            on_base + self.att[0, t] - self.dfn[0, o],
            sot_base + self.att[1, t] - self.dfn[1, o],
            np.clip(day - self.last_day[t], 0, 21).astype(float),
            np.clip(day - self.last_day[o], 0, 21).astype(float),
            (day[:, None] - self.recent[t] <= 14).sum(axis=1).astype(float),
            (day[:, None] - self.recent[o] <= 14).sum(axis=1).astype(float),
            np.minimum(self.n[t], 60.0),
            np.minimum(self.n[o], 60.0),
            np.where(self.last_season[t] == si, self.sn[t], 0.0),
            np.where(self.last_season[o] == si, self.sn[o], 0.0),
            chg(t).astype(float),
            chg(o).astype(float),
            oth(t),
            oth(o),
            self._ew(1, t, 6, lv[:, 3]),
            self._ew(1, o, 7, lv[:, 3]),
        ]
        return np.column_stack(cols)

    def _update(self, hi, ai, di, t2, si, day, y):
        lv, hv = self._level(di)
        gh, ga = y["g"]
        # EW team stats
        for k in range(2):
            d = self.d[k]
            for stat_i, (key, own) in enumerate(
                [("g", 0), ("g", 1), ("s", 0), ("s", 1), ("sh", 0), ("sh", 1), ("c", 0), ("c", 1)]
            ):
                a, b = y[key]
                xh, xa = (a, b) if own == 0 else (b, a)  # for: own count; against: opponent's
                for team, x in ((hi, xh), (ai, xa)):
                    ok = ~np.isnan(x)
                    self.S[k, team, stat_i] *= d
                    self.W[k, team, stat_i] *= d
                    self.S[k, team[ok], stat_i] += x[ok]
                    self.W[k, team[ok], stat_i] += 1.0
        d1 = self.d[1]
        for team in (hi, ai):
            self.tierS[team] = d1 * self.tierS[team] + t2
            self.tierW[team] = d1 * self.tierW[team] + 1.0
        # division levels (EW per match)
        dl = self.d_lg
        for j, key in enumerate(("g", "s", "sh", "c")):
            a, b = y[key]
            x = (a + b) / 2
            ok = ~np.isnan(x)
            mean_x = np.where(ok, x, lv[:, j])
            np.multiply.at(self.lgS[:, j], di, dl)
            np.add.at(self.lgS[:, j], di, mean_x)
        np.multiply.at(self.lgH[:, 0], di, dl)
        np.add.at(self.lgH[:, 0], di, gh)
        np.multiply.at(self.lgH[:, 1], di, dl)
        np.add.at(self.lgH[:, 1], di, ga)
        np.multiply.at(self.lgW, di, dl)
        np.add.at(self.lgW, di, 1.0)
        # Elo with a goal-margin multiplier
        dr = self.elo[hi] - self.elo[ai] + self.elo_home
        e = 1 / (1 + 10 ** (-dr / 400))
        res = np.where(gh > ga, 1.0, np.where(gh == ga, 0.5, 0.0))
        gd = np.abs(gh - ga)
        mult = np.where(gd <= 1, 1.0, np.where(gd == 2, 1.5, (11 + gd) / 8))
        delta = self.elo_k * mult * (res - e)
        self.elo[hi] += delta
        self.elo[ai] -= delta
        # online Poisson ratings: goals (level = division home / away rates) and SoT
        ha = np.log(hv[:, 0] / hv[:, 1])
        for r, (a, b), base_h, base_a, scale in (
            (0, y["g"], np.log(hv[:, 0]), np.log(hv[:, 1]), lv[:, 0]),
            (1, y["s"], np.log(lv[:, 1]) + ha / 2, np.log(lv[:, 1]) - ha / 2, lv[:, 1]),
        ):
            mh = np.exp(base_h + self.att[r, hi] - self.dfn[r, ai])
            ma = np.exp(base_a + self.att[r, ai] - self.dfn[r, hi])
            rh = np.nan_to_num((a - mh) / scale)
            ra = np.nan_to_num((b - ma) / scale)
            self.att[r, hi] += self.eta * rh
            self.dfn[r, ai] -= self.eta * rh
            self.att[r, ai] += self.eta * ra
            self.dfn[r, hi] -= self.eta * ra
        # schedule and seasons
        for team in (hi, ai):
            new_season = self.last_season[team] != si
            self.prev_tier[team] = np.where(new_season, self.last_tier[team], self.prev_tier[team])
            self.sn[team] = np.where(new_season, 1.0, self.sn[team] + 1.0)
            self.last_season[team] = si
            self.last_tier[team] = t2 + 1
            self.n[team] += 1
            self.last_day[team] = day
            self.recent[team] = np.column_stack([self.recent[team][:, 1:], day])


# ---- the model ---------------------------------------------------------------------------


class MLGBM:
    """Feature-based goals model: booster or GLM, optionally stacked on Dixon-Coles."""

    markets = ALL_MARKETS

    def __init__(
        self,
        name: str = "ml_gbm",
        learner: str = "gbm",
        split_venue: bool = False,
        stack: bool = False,
        stack_offset: bool = True,
        free: tuple[str, ...] = (),
        mix_dc: float = 0.0,
        features: tuple[str, ...] | None = None,
        n_trees: int = 200,
        lr: float = 0.05,
        max_depth: int = 3,
        min_leaf: int = 60,
        l2: float = 10.0,
        glm_l2: float = 1.0,
        xi: float = 0.0019,
        tier2_weight: float = 0.7,
        burn_days: int = 150,
        refit_days: int = 28,
        dc_step: int = 7,
        sot_target: float = 0.0,
        scan: dict | None = None,
        dc: dict | None = None,
    ):
        self.name = name
        self.learner, self.stack, self.mix_dc = learner, stack, mix_dc
        self.split_venue, self.stack_offset = split_venue, stack_offset
        self.free = tuple(free)
        self.features = tuple(features) if features else tuple(FEATURES)
        if "home" in self.features:  # the home flag first (SplitVenue relies on it)
            self.features = ("home", *[f for f in self.features if f != "home"])
        self.gbm = dict(n_trees=n_trees, lr=lr, max_depth=max_depth, min_leaf=min_leaf, l2=l2)
        self.glm_l2 = glm_l2
        self.xi, self.tier2_weight, self.burn_days = xi, tier2_weight, burn_days
        self.refit_days, self.dc_step, self.sot_target = refit_days, dc_step, sot_target
        self.scan_cfg = scan or {}
        self.dc_cfg = dc or {}
        self.col = [FEATURES.index(f) for f in self.features]
        self.learner_ = None
        self.scanner: FeatureScan | None = None
        self.fitted_at: np.datetime64 | None = None
        self.dc_now = DixonColes(**self.dc_cfg) if (stack or mix_dc > 0) else None
        self.dc_grid = DixonColes(**self.dc_cfg) if stack else None
        self.snapshots: dict[int, DixonColes] = {}  # grid day -> DC fitted before that day
        self.oos: dict[int, tuple[float, float]] = {}  # match id -> out-of-sample DC rates

    def params(self) -> dict:
        return {
            "learner": self.learner,
            "split_venue": self.split_venue,
            "stack": self.stack,
            "stack_offset": self.stack_offset,
            "free": list(self.free),
            "mix_dc": self.mix_dc,
            "features": list(self.features),
            **self.gbm,
            "glm_l2": self.glm_l2,
            "xi": self.xi,
            "tier2_weight": self.tier2_weight,
            "burn_days": self.burn_days,
            "refit_days": self.refit_days,
            "dc_step": self.dc_step,
            "sot_target": self.sot_target,
            "scan": self.scan_cfg,
            "dc": self.dc_cfg,
        }

    # ---- Dixon-Coles inputs ---------------------------------------------------------------

    def _update_dc_grid(self, h: pd.DataFrame, cutoff_day: int, start_day: int) -> None:
        """Fit DC at every weekly grid date in [start_day, cutoff_day] not yet cached, each on
        the matches dated strictly before it."""
        anchor = int(GRID_ANCHOR.astype(np.int64))
        step = self.dc_step
        first = anchor + step * int(np.ceil((start_day - anchor) / step))
        day = _days(h["date"])
        for g in range(first, cutoff_day + 1, step):
            if g in self.snapshots:
                continue
            past = h[day < g]
            if len(past) < 100:
                continue
            self.dc_grid.fit(past, pd.Timestamp(np.datetime64(g, "D")).tz_localize("UTC"))
            self.snapshots[g] = copy.copy(self.dc_grid)

    def _oos_rates(self, h: pd.DataFrame) -> tuple[np.ndarray, np.ndarray]:
        anchor = int(GRID_ANCHOR.astype(np.int64))
        step = self.dc_step
        ids = h["id"].to_numpy()
        todo = np.array([i not in self.oos for i in ids])
        if todo.any():
            sub = h[todo]
            g = anchor + step * ((_days(sub["date"]) - anchor) // step)
            for gd, part in sub.groupby(g):
                snap = self.snapshots.get(int(gd))
                if snap is None:
                    continue
                lh, la = snap.rates(part)
                for i, a, b in zip(part["id"].to_numpy(), lh, la, strict=True):
                    self.oos[int(i)] = (float(a), float(b))
        lh = np.array([self.oos.get(int(i), (np.nan, np.nan))[0] for i in ids])
        la = np.array([self.oos.get(int(i), (np.nan, np.nan))[1] for i in ids])
        return lh, la

    # ---- fit / predict --------------------------------------------------------------------

    def _design(self, Xh, Xa, dch=None, dca=None):
        X = np.vstack([Xh, Xa])[:, self.col]
        if dch is None:
            return X, None
        lh, la = np.log(dch), np.log(dca)
        own = np.r_[lh, la]
        other = np.r_[la, lh]
        return np.column_stack([X, own, other]), (own if self.stack_offset else None)

    def fit(self, history: pd.DataFrame, cutoff) -> None:
        h = history[history.division.isin(TIER1 | TIER2)].sort_values(["date", "id"])
        cut = pd.Timestamp(cutoff).tz_convert("UTC").tz_localize(None)
        cutoff_day = int(np.datetime64(cut.normalize(), "D").astype(np.int64))
        if self.scanner is None or not self.scanner.covers(h):
            self.scanner = FeatureScan(**self.scan_cfg)
        self.scanner.extend(h)
        start_day = int(_days(h["date"]).min()) + self.burn_days if len(h) else cutoff_day
        if self.dc_now is not None:
            self.dc_now.fit(h, cutoff)
        if self.stack:
            self._update_dc_grid(h, cutoff_day, start_day)
        refit = (
            self.learner_ is None
            or (np.datetime64(cut, "D") - self.fitted_at).astype(int) >= self.refit_days
        )
        if not refit:
            return
        day = _days(h["date"])
        keep = day >= start_day
        dch = dca = None
        if self.stack:
            dch, dca = self._oos_rates(h)
            miss = np.isnan(dch)
            if miss.mean() > 0.5:  # short history, no weekly DC fits yet: in-sample rates
                ih, ia = self.dc_now.rates(h[miss])
                dch[miss], dca[miss] = ih, ia
            keep &= ~np.isnan(dch)
        if keep.sum() < 200:  # too little history for mature features: use everything
            keep = np.ones(len(h), bool) if dch is None else ~np.isnan(dch)
        hk = h[keep]
        sub = self.scanner.rows(hk["id"])
        X, off = self._design(*sub, *(() if dch is None else (dch[keep], dca[keep])))
        y = np.r_[self._target(hk, "home"), self._target(hk, "away")]
        age = (cutoff_day - day[keep]).astype(float)
        w = np.exp(-self.xi * age) * np.where(hk.division.isin(TIER2), self.tier2_weight, 1.0)
        w = np.r_[w, w]
        model = self._make_learner().fit(X, y, w, off)
        self.learner_ = model
        self.fitted_at = np.datetime64(cut, "D")
        eta = model.predict_log(X, off)
        k = len(hk)
        self.rho_ = _fit_rho(
            hk.home_goals.to_numpy(),
            hk.away_goals.to_numpy(),
            np.exp(eta[:k]),
            np.exp(eta[k:]),
            w[:k],
        )

    def _make_learner(self):
        cols = [*self.features, *(("dc_own", "dc_other") if self.stack else ())]
        free = tuple(cols.index(f) for f in self.free if f in cols)

        def make():
            if self.learner == "glm":
                return PoissonGLM(self.glm_l2, free)
            if self.learner == "glm_gbm":
                return GLMThenBoost(self.glm_l2, free, **self.gbm)
            return PoissonBooster(**self.gbm)

        return SplitVenue(make) if self.split_venue else make()

    def _target(self, h: pd.DataFrame, side: str) -> np.ndarray:
        goals = h[f"{side}_goals"].to_numpy(float)
        if self.sot_target <= 0:
            return goals
        sot = h[f"{side}_sot"].to_numpy(float)
        ok = ~np.isnan(sot) & ~np.isnan(
            h["home_sot"].to_numpy(float) + h["away_sot"].to_numpy(float)
        )
        tot_g = (h.home_goals + h.away_goals).to_numpy(float)[ok].sum()
        tot_s = (h.home_sot + h.away_sot).to_numpy(float)[ok].sum()
        conv = tot_g / max(tot_s, 1.0)
        return np.where(ok, (1 - self.sot_target) * goals + self.sot_target * conv * sot, goals)

    def rates(self, fixtures: pd.DataFrame) -> tuple[np.ndarray, np.ndarray]:
        Xh, Xa = self.scanner.fixture_features(fixtures)
        dch = dca = None
        if self.stack:
            dch, dca = self.dc_now.rates(fixtures)
        X, off = self._design(Xh, Xa, dch, dca)
        eta = self.learner_.predict_log(X, off)
        k = len(fixtures)
        return np.exp(eta[:k]), np.exp(eta[k:])

    def predict(self, fixtures: pd.DataFrame) -> MarketProbs:
        lh, la = self.rates(fixtures)
        grid = poisson_grid(lh, la, self.rho_)
        if self.mix_dc > 0:
            dh, da = self.dc_now.rates(fixtures)
            dgrid = poisson_grid(dh, da, self.dc_now.rho_)
            w = self.mix_dc
            grid = mix_grids([grid, dgrid], [1 - w, w])
            lh, la = (1 - w) * lh + w * dh, (1 - w) * la + w * da
        return markets_from_grid(grid, lh, la)


def _log_tau(gh, ga, lh, la, rho):
    tau = np.ones(len(gh))
    m = (gh == 0) & (ga == 0)
    tau[m] = 1 - lh[m] * la[m] * rho
    m = (gh == 0) & (ga == 1)
    tau[m] = 1 + lh[m] * rho
    m = (gh == 1) & (ga == 0)
    tau[m] = 1 + la[m] * rho
    m = (gh == 1) & (ga == 1)
    tau[m] = 1 - rho
    return np.log(np.clip(tau, 1e-10, None))


def _fit_rho(gh, ga, lh, la, w) -> float:
    res = minimize_scalar(
        lambda r: -(w * _log_tau(gh, ga, lh, la, r)).sum(), bounds=(-0.2, 0.2), method="bounded"
    )
    return float(res.x)


CANDIDATES: dict = {
    "ml_gbm_pooled": lambda: MLGBM("ml_gbm_pooled"),
    "ml_gbm_glm": lambda: MLGBM("ml_gbm_glm", learner="glm"),
    "ml_gbm_stack": lambda: MLGBM("ml_gbm_stack", stack=True, n_trees=100),
    "ml_gbm_glm_stack": lambda: MLGBM("ml_gbm_glm_stack", learner="glm", stack=True),
}
