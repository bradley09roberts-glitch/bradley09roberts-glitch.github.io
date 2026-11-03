"""Shared helpers for the T3 verification (my own code, written from the claim text and the frozen
fc92cd5 sources in frozen/; the tester's common.py is not imported).

Two line/price constructions:
  live_props(q)       appdata.consensus_lines (fc92cd5) applied per game: dedup on
                      (game, book, player, market, line, selection) keep last, row-mode line,
                      books quoting both sides there, fair rounded to 4 dp.  -> V1p / V2 / V3
  corrected_props(q)  corrected consensus (claim T1/T3): exact dups dropped, a book with conflicting
                      prices for one (line, side) dropped from that prop, two-way pairs only, each
                      book ONE main line (min |fair-0.5|, then lower margin, then lower line),
                      consensus line = mode over books' main lines (tie: smaller mean |fair-0.5| of
                      voters, then lower line).                                   -> V4 / V4x / V4s
Both return (props, per-book pairs at the prop's line).
"""
from __future__ import annotations

import json
import os
from pathlib import Path  # noqa: E402

import numpy as np
import pandas as pd

REPO = str(Path(__file__).resolve().parents[4])
HERE = f"{REPO}/data/runs/n12"  # git-ignored: raw quotes and per-prop tables stay in data/
K = ["game_id", "player_id", "market"]
MARKETS = ["pts", "reb", "ast", "fg3m", "pra"]
REG = {"betmgm", "betrivers", "draftkings", "fanatics", "fanduel", "williamhill_us"}  # books present
OFF = {"bovada", "betonlineag"}
TRACK_EV = 0.02


def quotes(season: int) -> pd.DataFrame:
    q = pd.read_parquet(f"{HERE}/data/q_{season}.parquet")
    q = q[(q.alternate == 0) & q.player_id.notna() & q.line.notna() & q.market.isin(MARKETS)].copy()
    assert set(q.bookmaker) <= REG | OFF, set(q.bookmaker) - REG - OFF
    q["player_id"] = q.player_id.astype(str)
    q["game_id"] = q.game_id.astype(str)
    return q


def _pairs(r: pd.DataFrame) -> pd.DataFrame:
    """rows (one per book, line, selection) -> one row per (prop, book, line) with both sides."""
    o = r[r.selection == "over"].set_index(K + ["bookmaker", "line"]).decimal_price.rename("over")
    u = r[r.selection == "under"].set_index(K + ["bookmaker", "line"]).decimal_price.rename("under")
    w = pd.concat([o, u], axis=1, join="inner").reset_index()
    w["fair"] = (1 / w.over) / (1 / w.over + 1 / w.under)
    w["margin"] = 1 / w.over + 1 / w.under - 1
    w["reg"] = w.bookmaker.isin(REG)
    return w


def live_props(q: pd.DataFrame):
    # one snapshot per game, so the 'latest collection' filter keeps everything (checked in v00)
    r = q.sort_values(["snapshot", "id"], kind="mergesort").drop_duplicates(
        K + ["bookmaker", "line", "selection"], keep="last")
    # row mode with ties -> smallest line (Series.mode sorts)
    cnt = r.groupby(K + ["line"]).size().rename("n").reset_index()
    cnt = cnt.sort_values(K + ["n", "line"], ascending=[True, True, True, False, True], kind="mergesort")
    line = cnt.drop_duplicates(K, keep="first")[K + ["line"]]
    r = r.merge(line, on=K + ["line"])
    w = _pairs(r)
    g = w.groupby(K)
    pr = g.agg(line=("line", "first"), fair_raw=("fair", "mean"), books=("fair", "size"),
               best_over=("over", "max"), best_under=("under", "max")).reset_index()
    pr["fair_over"] = pr.fair_raw.round(4)
    reg = w[w.reg].groupby(K).agg(breg_over=("over", "max"), breg_under=("under", "max")).reset_index()
    mg = w[w.bookmaker == "betmgm"][K + ["over", "under"]].rename(columns={"over": "mgm_over", "under": "mgm_under"})
    pr = pr.merge(reg, on=K, how="left").merge(mg, on=K, how="left")
    return pr, w


def corrected_props(q: pd.DataFrame, conflict: str = "drop_book"):
    r = q.drop_duplicates(K + ["bookmaker", "line", "selection", "decimal_price"])
    nprice = r.groupby(K + ["bookmaker", "line", "selection"]).decimal_price.transform("nunique")
    if conflict == "drop_book":   # a book with two prices for one (line, side) leaves that prop
        badbp = r.loc[nprice > 1, K + ["bookmaker"]].drop_duplicates()
        r = r.merge(badbp.assign(_x=1), on=K + ["bookmaker"], how="left")
        r = r[r._x.isna()].drop(columns="_x")
    else:                          # keep the last-inserted price
        r = r.sort_values("id", kind="mergesort").drop_duplicates(K + ["bookmaker", "line", "selection"], keep="last")
    w = _pairs(r)
    w["bal"] = (w.fair - 0.5).abs()
    # each book's main line
    w = w.sort_values(K + ["bookmaker", "bal", "margin", "line"], kind="mergesort")
    m = w.drop_duplicates(K + ["bookmaker"], keep="first").copy()
    # consensus line over main lines: votes desc, mean bal asc, line asc
    v = m.groupby(K + ["line"]).agg(votes=("bookmaker", "size"), mbal=("bal", "mean")).reset_index()
    v = v.sort_values(K + ["votes", "mbal", "line"], ascending=[True, True, True, False, True, True], kind="mergesort")
    cl = v.drop_duplicates(K, keep="first")[K + ["line"]].rename(columns={"line": "cline"})
    m = m.merge(cl, on=K)
    at = m[m.line == m.cline].copy()          # books whose main line is the consensus line
    g = at.groupby(K)
    pr = g.agg(line=("line", "first"), fair_over=("fair", "mean"), books=("fair", "size"),
               best_over=("over", "max"), best_under=("under", "max")).reset_index()
    reg = at[at.reg].groupby(K).agg(breg_over=("over", "max"), breg_under=("under", "max")).reset_index()
    mg = at[at.bookmaker == "betmgm"][K + ["over", "under"]].rename(columns={"over": "mgm_over", "under": "mgm_under"})
    pr = pr.merge(reg, on=K, how="left").merge(mg, on=K, how="left")
    pr["arb_reg"] = (1 / pr.breg_over + 1 / pr.breg_under) < 1
    return pr, at, m


def add_loo(pr: pd.DataFrame, w: pd.DataFrame, rnd: int | None, tie: str = "max_ev") -> pd.DataFrame:
    """Leave-one-out fair for the priced book. w: per-book pairs at the prop's line (all books).
    loo_mgm: fair of the other books when BetMGM prices; loo_o / loo_u: fair excluding the regulated
    book giving the best over / under price (tie between equal-price books: 'max_ev' = the one giving
    the higher EV on that side, as the claim says; 'min_ev' = the lower; 'mean' = average of tied)."""
    s = w.groupby(K).agg(_s=("fair", "sum"), _n=("fair", "size")).reset_index()
    x = w.merge(s, on=K)
    x["loo"] = np.where(x._n - 1 >= 2, (x._s - x.fair) / np.maximum(x._n - 1, 1), np.nan)
    if rnd is not None:
        x["loo"] = x.loo.round(rnd)
    out = pr.merge(x[x.bookmaker == "betmgm"][K + ["loo"]].rename(columns={"loo": "loo_mgm"}), on=K, how="left")
    xr = x[x.reg].merge(pr[K + ["breg_over", "breg_under"]], on=K)
    to = xr[xr.over == xr.breg_over].groupby(K).loo
    tu = xr[xr.under == xr.breg_under].groupby(K).loo
    if tie == "max_ev":
        lo, lu = to.max(), tu.min()
    elif tie == "min_ev":
        lo, lu = to.min(), tu.max()
    else:
        lo, lu = to.mean(), tu.mean()
    out = out.merge(lo.rename("loo_o").reset_index(), on=K, how="left").merge(lu.rename("loo_u").reset_index(), on=K, how="left")
    return out


# ----------------------------------------------------------------------------- calibration
# N12_PARAMS picks the N1 version (nba-0.1 frozen, or nba-0.3) and its anchored-stack coefficients;
# the clip is the one the stack was fitted with (live calib.anchored since ad80b68).
PARAMS = os.environ.get("N12_PARAMS", "nba-0.1")
_CJ = json.load(open(f"{REPO}/reports/nba/model/calibration.json"))["by_model"][PARAMS]["anchored_stack"]
CAL, CLIP = _CJ["coef"], _CJ["logit_clip"]


def anchored(market, fair, p_model):
    """calib.anchored, vectorised."""
    mk = np.asarray(market)
    a = np.array([CAL[m]["a"] for m in mk])
    c = np.array([CAL[m]["c"] for m in mk])
    wv = np.array([CAL[m]["w"] for m in mk])

    def lg(p):
        p = np.clip(np.asarray(p, float), CLIP, 1 - CLIP)
        return np.log(p / (1 - p))
    return 1 / (1 + np.exp(-(a + c * lg(fair) + wv * lg(p_model))))


# ----------------------------------------------------------------------------- N1 + outcomes
class N1:
    def __init__(self, season):
        z = np.load(f"{HERE}/data/n1_sf_{PARAMS}_{season}.npz", allow_pickle=True)  # my own file (v01)
        self.ix = pd.Series(np.arange(len(z["game_id"])),
                            index=pd.MultiIndex.from_arrays([z["game_id"], z["player_id"]]))
        self.sf = {m: z[m] for m in MARKETS}
        self.p_play = z["p_play"]

    def attach(self, d: pd.DataFrame) -> pd.DataFrame:
        i = self.ix.reindex(pd.MultiIndex.from_arrays([d.game_id, d.player_id])).to_numpy()
        d = d.assign(i=i)
        d = d[d.i.notna()].copy()
        d["i"] = d.i.astype(int)
        k = np.floor(d.line.to_numpy()).astype(int) + 1
        raw = np.full(len(d), np.nan)
        for m in MARKETS:
            s = d.market.to_numpy() == m
            sf = self.sf[m]
            kk = k[s]
            raw[s] = np.where(kk <= sf.shape[1] - 1, sf[d.i.to_numpy()[s], np.clip(kk, 0, sf.shape[1] - 1)], 0.0)
        d["raw"] = raw
        d["p_play"] = self.p_play[d.i.to_numpy()]
        return d


def outcomes(season: int) -> pd.DataFrame:
    from npl.nba import canon
    pg = canon.load()["player_games"]
    pg = pg[(pg.season == season) & (pg.participation == "played")].copy()
    pg["pra"] = pg.pts + pg.reb + pg.ast
    a = pg.melt(id_vars=["game_id", "player_id", "date"], value_vars=MARKETS, var_name="market", value_name="actual")
    a["game_id"] = a.game_id.astype(str)
    a["player_id"] = a.player_id.astype(str)
    a["date"] = a.date.astype(str)
    return a


# ----------------------------------------------------------------------------- rule
def run_rule(d, fo, fu, po, pu, elig):
    """picks.nba(): pa = anchored(market, fair, raw); EV per side at its price; side = argmax (over
    wins ties); track when EV >= 0.02. fo / fu: fair fed for the over / under evaluation."""
    pao = anchored(d.market.to_numpy(), fo, d.raw.to_numpy())
    pau = anchored(d.market.to_numpy(), fu, d.raw.to_numpy())
    evo = pao * po - 1
    evu = (1 - pau) * pu - 1
    evo = np.where(np.isfinite(evo), evo, -np.inf)
    evu = np.where(np.isfinite(evu), evu, -np.inf)
    over = evo >= evu
    ev = np.where(over, evo, evu)
    sel = np.asarray(elig, bool) & (ev >= TRACK_EV)
    side = np.where(over, "over", "under")
    price = np.where(over, po, pu)
    act = d.actual.to_numpy()
    ln = d.line.to_numpy()
    won = np.where(over, act > ln, act < ln)
    push = act == ln
    prof = np.where(push, 0.0, np.where(won, price - 1, -1.0))
    return pd.DataFrame({"sel": sel, "side": side, "price": price, "ev": ev, "won": won, "push": push,
                         "profit": prof}, index=d.index)


# ----------------------------------------------------------------------------- bootstrap
def boot_idx(n_dates, B=10000, seed=1):
    rng = np.random.default_rng(seed)
    return rng.integers(0, n_dates, (B, n_dates))


def roi_ci(dates, profit, date_list, idx, void=None):
    """ROI over bets with action; date-block bootstrap over date_list (whole dates resampled)."""
    profit = np.asarray(profit, float)
    dates = np.asarray(dates)
    if void is not None:
        keep = ~np.asarray(void, bool)
        profit, dates = profit[keep], dates[keep]
    n = len(profit)
    if n == 0:
        return {"bets": 0}
    pos = pd.Series(np.arange(len(date_list)), index=date_list)
    j = pos.reindex(dates).to_numpy()
    assert not np.isnan(j).any()
    j = j.astype(int)
    S = np.bincount(j, weights=profit, minlength=len(date_list))
    N = np.bincount(j, minlength=len(date_list)).astype(float)
    num = S[idx].sum(1)
    den = N[idx].sum(1)
    ok = den > 0
    r = num[ok] / den[ok]
    roi = profit.sum() / n
    p = (1 + np.sum(r - roi >= roi)) / (1 + len(r))
    return {"bets": int(n), "roi": round(float(roi), 4), "ci": [round(float(np.quantile(r, .025)), 4),
            round(float(np.quantile(r, .975)), 4)], "p_one": round(float(p), 4), "units": round(float(profit.sum()), 2),
            "dates_with_bets": int((N > 0).sum())}
