"""Simple baselines the joint model must beat.

A  recency-weighted game log: EW mean of the player's own stat in games he played,
   lightly shrunk to the position mean; counts ~ negative binomial with a market-level
   dispersion, yards ~ discretised normal with a fitted mean-SD relation (allows < 0).
C  role climatology: the empirical distribution of the stat among training player-games
   of the same position and recent-usage tier (no player-specific mean at all).

Both are conditional on the player taking part, like the joint model. Every fitted
quantity uses only the History passed in (rows before the slate).
"""

from __future__ import annotations

import numpy as np
import pandas as pd
from scipy import stats as sps

from .config import CORE_MARKETS
from .dist import Dist
from .features import ROLE_POS, History, ew_weights

HL_A = 4.0
DECAY_A = 0.55
PRIOR_A = 2.0


def _played(h: History) -> pd.DataFrame:
    pg = h.pg[(h.pg.participation == "played") & h.pg.position.isin(ROLE_POS)].copy()
    pg["role"] = pg.position.map(ROLE_POS)
    return pg


def fit_a(h: History, train_from: int = 2016) -> dict:
    """Per-market dispersion fitted on history: NB size r for counts, log-SD on log-mean
    for yards."""
    pg = _played(h)
    pg = pg[pg.season >= train_from]
    out = {}
    for m in CORE_MARKETS:
        d = pg[pg.role.isin([ROLE_POS[p] for p in m.positions])]
        g = d.groupby(["player_id", "season"])[m.stat]
        mu, var, n = g.mean(), g.var(), g.size()
        ok = (n >= 6) & (mu > 0.5)
        mu, var = mu[ok], var[ok]
        if m.kind == "count":
            r = (mu ** 2 / (var - mu).clip(lower=1e-3)).clip(0.5, 500)
            out[m.key] = {"r": float(np.median(r))}
        else:
            sd = np.sqrt(var)
            good = sd > 0
            b, a = np.polyfit(np.log(mu[good].clip(lower=1)), np.log(sd[good]), 1)
            out[m.key] = {"a": float(a), "b": float(b)}
    return out


def ew_means(h: History) -> pd.DataFrame:
    pg = _played(h).sort_values(["season", "week"], ascending=False)
    pg["_ord"] = pg.groupby("player_id").cumcount()
    pg["_w"] = ew_weights(pg._ord.to_numpy(), pg.season.to_numpy(), h.season, HL_A, DECAY_A)
    cols = sorted({m.stat for m in CORE_MARKETS})
    w = pg["_w"]
    agg = pg[cols].mul(w, axis=0).groupby(pg.player_id).sum().div(
        w.groupby(pg.player_id).sum(), axis=0)
    agg["_wsum"] = w.groupby(pg.player_id).sum()
    agg["_n"] = pg.groupby("player_id").size()
    return agg


def role_means(h: History) -> pd.DataFrame:
    pg = _played(h)
    pg = pg[pg.season >= h.season - 2]
    return pg.groupby("role")[sorted({m.stat for m in CORE_MARKETS})].median()


def dist_a(market, mean: float, fit: dict) -> Dist:
    mean = max(mean, 0.05)
    if market.kind == "count":
        r = fit[market.key]["r"]
        p = r / (r + mean)
        hi = int(sps.nbinom.ppf(0.99995, r, p)) + 1
        k = np.arange(0, hi + 1)
        return Dist.from_pmf(0, sps.nbinom.pmf(k, r, p))
    a, b = fit[market.key]["a"], fit[market.key]["b"]
    sd = float(np.exp(a + b * np.log(max(mean, 1.0))))
    lo, hi = int(np.floor(mean - 5 * sd)), int(np.ceil(mean + 5 * sd))
    lo = max(lo, -30)
    k = np.arange(lo, hi + 1)
    pmf = sps.norm.cdf(k + 0.5, mean, sd) - sps.norm.cdf(k - 0.5, mean, sd)
    return Dist.from_pmf(lo, pmf)


def predict_a(h: History, player_ids: list[str], roles: dict, fit: dict, ew: pd.DataFrame,
              rm: pd.DataFrame) -> dict:
    out = {}
    for pid in player_ids:
        role = roles[pid]
        for m in CORE_MARKETS:
            if role not in {ROLE_POS[p] for p in m.positions}:
                continue
            prior = float(rm.loc[role, m.stat]) if role in rm.index else 0.0
            if pid in ew.index:
                w = float(ew.loc[pid, "_wsum"])
                mean = (float(ew.loc[pid, m.stat]) * w + prior * PRIOR_A) / (w + PRIOR_A)
            else:
                mean = prior
            out[(pid, m.key)] = dist_a(m, mean, fit)
    return out


def fit_c(h: History, train_from: int = 2016) -> dict:
    """Empirical outcome distributions by role and usage tier (from EW opportunity share
    before each game would be ideal; here the tier is the player's season-to-date share
    rank within team, a pre-game quantity)."""
    pg = _played(h)
    pg = pg[pg.season >= train_from].sort_values(["season", "week"])
    pg["opp"] = np.where(pg.role == "QB", pg.attempts, pg.targets + pg.carries)
    # opportunity share before the game (expanding mean within season, shifted)
    pg["prev_opp"] = pg.groupby(["player_id", "season"]).opp.transform(
        lambda s: s.shift(1).expanding().mean())
    pg["tier"] = _tier(pg.role, pg.prev_opp)
    out = {}
    for m in CORE_MARKETS:
        roles = {ROLE_POS[p] for p in m.positions}
        d = pg[pg.role.isin(roles)]
        for (role, tier), g in d.groupby(["role", "tier"]):
            x = g[m.stat].dropna().to_numpy().astype(int)
            if len(x) >= 30:
                out[(m.key, role, tier)] = Dist.from_samples(x)
    return out


def _tier(role: pd.Series, prev_opp: pd.Series) -> np.ndarray:
    bins = {"QB": [-1, 0.5, 15, 28, 1e9], "RB": [-1, 0.5, 4, 10, 1e9],
            "WR": [-1, 0.5, 3, 6, 1e9], "TE": [-1, 0.5, 2, 4.5, 1e9]}
    out = np.full(len(role), "t0", dtype=object)
    po = prev_opp.fillna(-0.5).to_numpy()
    for r, b in bins.items():
        sel = role.to_numpy() == r
        out[sel] = np.array(["none", "low", "mid", "high"])[
            np.clip(np.digitize(po[sel], b[1:-1], right=False), 0, 3)]
    return out


def predict_c(h: History, player_ids: list[str], roles: dict, fit: dict) -> dict:
    pg = _played(h)
    cur = pg[pg.season == h.season]
    opp = np.where(cur.role == "QB", cur.attempts, cur.targets + cur.carries)
    prev = pd.Series(opp, index=cur.index).groupby(cur.player_id).mean()
    out = {}
    for pid in player_ids:
        role = roles[pid]
        tier = _tier(pd.Series([role]), pd.Series([prev.get(pid, np.nan)]))[0]
        for m in CORE_MARKETS:
            key = (m.key, role, tier)
            if key in fit:
                out[(pid, m.key)] = fit[key]
            elif (m.key, role, "low") in fit:
                out[(pid, m.key)] = fit[(m.key, role, "low")]
    return out
