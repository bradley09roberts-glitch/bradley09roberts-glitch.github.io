"""n10: N1 (frozen nba-0.1) against real bookmaker lines on a full past season.

Lines: The Odds API historical snapshots 3 hours before tip, US books, core markets, main lines
only (hist_quotes; D-040). Per (game, player, market): the most common line across books; the
market's over chance = average over books quoting both sides at that line of the margin-free
over probability; best prices across books; BetMGM's own line and prices kept separately.
Model: N1's distribution for players who played (props are void otherwise), from the backtest
frame strictly before each game. Outcomes: box scores.

Reported: model v market log loss; results by gap band (model side minus market); flat-stake
ROI at the best US price and at BetMGM's price; the pick rules (10-15 band, 6-10 leans);
the trust fraction w that best combines them (p = market + w x (model - market), fitted by log
loss); the simple baseline's gap rule; by market and by side. Date-block bootstrap intervals.
"""
import json
import sys

import numpy as np
import pandas as pd

from npl import db
from npl.nba import backtest as B
from npl.nba import canon
from npl.nba import model as M
from npl.nba import simple as SM


def consensus(h: pd.DataFrame) -> pd.DataFrame:
    h = h[(h.alternate == 0) & h.player_id.notna() & h.line.notna()]
    line = h.groupby(["game_id", "player_id", "market"]).line.agg(lambda s: s.mode().iloc[0]).rename("line")
    h = h.merge(line.reset_index(), on=["game_id", "player_id", "market", "line"])
    w = h.pivot_table(index=["game_id", "player_id", "market", "line", "bookmaker"], columns="selection",
                      values="decimal_price", aggfunc="last").dropna(subset=["over", "under"]).reset_index()
    w["fair"] = (1 / w.over) / (1 / w.over + 1 / w.under)
    w["margin"] = 1 / w.over + 1 / w.under - 1
    g = w.groupby(["game_id", "player_id", "market", "line"])
    out = g.agg(fair_over=("fair", "mean"), books=("fair", "size"), best_over=("over", "max"),
                best_under=("under", "max"), margin=("margin", "mean")).reset_index()
    mgm = w[w.bookmaker == "betmgm"][["game_id", "player_id", "market", "line", "over", "under", "fair"]].rename(
        columns={"over": "mgm_over", "under": "mgm_under", "fair": "mgm_fair"})
    return out.merge(mgm, on=["game_id", "player_id", "market", "line"], how="left")


def model_probs(props: pd.DataFrame, season: int) -> pd.DataFrame:
    p = M.load_params("nba-0.1")
    x, lg = B.frame(p)
    x = x[(x.season == season) & (x.participation == "played") & x.n_played.notna() & ~x.box_incomplete]
    x = x[x.game_id.isin(set(props.game_id))].reset_index(drop=True)
    pm = M.n1_pmfs(x, p, lg)
    rows = []
    key = {(g, pid): i for i, (g, pid) in enumerate(zip(x.game_id, x.player_id, strict=True))}
    for r in props.itertuples():
        i = key.get((r.game_id, r.player_id))
        if i is None:
            continue
        pmf = pm[r.market][i]
        k = int(np.floor(r.line)) + 1
        rows.append({"game_id": r.game_id, "player_id": r.player_id, "market": r.market,
                     "p_over": float(pmf[k:].sum()), "n1_mean": float((pmf * np.arange(len(pmf))).sum())})
    return pd.DataFrame(rows)


def boot_ci(dates, vals, n=1000, seed=10):
    d = pd.DataFrame({"d": dates, "v": vals}).groupby("d").v.agg(["sum", "count"])
    rng = np.random.default_rng(seed)
    idx = rng.integers(0, len(d), (n, len(d)))
    b = d["sum"].to_numpy()[idx].sum(1) / d["count"].to_numpy()[idx].sum(1)
    return [float(np.percentile(b, 2.5)), float(np.percentile(b, 97.5))]


def bets(d: pd.DataFrame, side: np.ndarray, price_cols=("best_over", "best_under")) -> dict:
    won = np.where(side == "over", d.actual > d.line, d.actual < d.line)
    price = np.where(side == "over", d[price_cols[0]], d[price_cols[1]])
    ok = ~np.isnan(price)
    won, price, dates = won[ok], price[ok], d.date.to_numpy()[ok]
    pnl = np.where(won, price - 1, -1.0)
    n = int(len(pnl))
    return {"bets": n, "win_rate": round(float(won.mean()), 4) if n else None,
            "roi": round(float(pnl.mean()), 4) if n else None,
            "roi_ci": [round(v, 4) for v in boot_ci(dates, pnl)] if n > 30 else None,
            "avg_price": round(float(price.mean()), 3) if n else None}


def main(season, out_path):
    con = db.connect()
    h = pd.read_sql("SELECT * FROM hist_quotes WHERE sport='nba' AND season=?", con, params=(season,))
    props = consensus(h)
    mp = model_probs(props, season)
    L = canon.load()
    pg = L["player_games"]
    pg = pg[pg.participation == "played"].assign(pra=lambda v: v.pts + v.reb + v.ast)
    act = pg.melt(id_vars=["game_id", "player_id", "date", "team", "opp", "season", "tip_utc"],
                  value_vars=list(M.MARKETS), var_name="market", value_name="actual")
    d = props.merge(mp, on=["game_id", "player_id", "market"]).merge(act, on=["game_id", "player_id", "market"])
    d = d[d.actual != d.line].reset_index(drop=True)  # pushes are void (whole-number lines)
    y = (d.actual > d.line).astype(float).to_numpy()
    pm_, pk = d.p_over.clip(1e-4, 1 - 1e-4).to_numpy(), d.fair_over.clip(1e-4, 1 - 1e-4).to_numpy()
    ll = lambda p: float(-np.mean(y * np.log(p) + (1 - y) * np.log(1 - p)))  # noqa: E731
    res = {"season": season, "props": int(len(d)), "games": int(d.game_id.nunique()),
           "books_median": float(d.books.median()), "margin_mean": float(d.margin.mean()),
           "log_loss": {"model": ll(pm_), "market": ll(pk)},
           "log_loss_diff_ci": boot_ci(d.date, (-(y * np.log(pm_) + (1 - y) * np.log(1 - pm_))) - (-(y * np.log(pk) + (1 - y) * np.log(1 - pk))))}
    # trust fraction
    ws = np.linspace(0, 1, 101)
    lls = [ll(np.clip(pk + w * (pm_ - pk), 1e-4, 1 - 1e-4)) for w in ws]
    res["trust_fraction_best"] = float(ws[int(np.argmin(lls))])
    res["log_loss_at_trust"] = {f"{w:.2f}": float(lls[int(round(w * 100))]) for w in (0, 0.1, 0.2, 1 / 3, 0.5, 1.0)}
    # sides and gaps
    side = np.where(d.p_over >= d.fair_over, "over", "under")
    gap = np.where(side == "over", d.p_over - d.fair_over, d.fair_over - d.p_over)
    d["side"], d["gap"] = side, gap
    elig = (d.books >= 3) & (d.line % 1 != 0)
    bands = {}
    for lo, hi in ((0, .03), (.03, .06), (.06, .10), (.10, .15), (.15, .20), (.20, 1)):
        m = elig & (d.gap >= lo) & (d.gap < hi)
        x = d[m]
        bands[f"{int(lo * 100)}-{int(hi * 100)}"] = {
            **bets(x, x.side.to_numpy()),
            "model_said": round(float(np.where(x.side == "over", x.p_over, 1 - x.p_over).mean()), 4) if len(x) else None,
            "market_said": round(float(np.where(x.side == "over", x.fair_over, 1 - x.fair_over).mean()), 4) if len(x) else None}
    res["bands_best_price"] = bands
    # rules
    rules = {"all_model_side": elig, "picks_10_15": elig & (d.gap >= .10) & (d.gap < .15),
             "leans_6_10": elig & (d.gap >= .06) & (d.gap < .10), "gap_6_15": elig & (d.gap >= .06) & (d.gap < .15)}
    res["rules_best_price"] = {k: bets(d[m], d.side[m].to_numpy()) for k, m in rules.items()}
    # BetMGM only: its own line, its own margin-free price, its own odds
    g = d[d.mgm_fair.notna()].copy()
    gs = np.where(g.p_over >= g.mgm_fair, "over", "under")
    gg = np.where(gs == "over", g.p_over - g.mgm_fair, g.mgm_fair - g.p_over)
    mg = {}
    for lo, hi in ((0, .06), (.06, .10), (.10, .15), (.15, 1)):
        m = (gg >= lo) & (gg < hi) & (g.line % 1 != 0).to_numpy()
        mg[f"{int(lo * 100)}-{int(hi * 100)}"] = bets(g[m], gs[m], ("mgm_over", "mgm_under"))
    res["betmgm_bands"] = mg
    # by market and side for the picks band
    pk_m = rules["picks_10_15"]
    res["picks_by_market"] = {mk: bets(d[pk_m & (d.market == mk)], d.side[pk_m & (d.market == mk)].to_numpy()) for mk in M.MARKETS}
    res["picks_by_side"] = {sd: bets(d[pk_m & (d.side == sd)], d.side[pk_m & (d.side == sd)].to_numpy()) for sd in ("over", "under")}
    # simple baseline gap rule
    f = SM.features(d[["player_id", "team", "opp", "game_id", "season", "tip_utc"]], L["player_games"], L["team_games"], L["games"])
    sp = SM.project(f, SM.load_rest())
    d["S"] = [sp.at[i, m] for i, m in enumerate(d.market)]
    for gthr in (1.0, 2.0):
        dd = d.S - d.line
        s2 = np.where(dd >= gthr, "over", np.where(dd <= -gthr, "under", None))
        m = elig & pd.notna(s2)
        res[f"simple_gap_{gthr}"] = bets(d[m], s2[m.to_numpy()])
    json.dump(res, open(out_path, "w"), indent=1, default=float)
    d.to_parquet(out_path.replace(".json", "_rows.parquet"))  # data/ path given by caller keeps odds out of git
    print(json.dumps(res, indent=1, default=float))


if __name__ == "__main__":
    main(int(sys.argv[1]), sys.argv[2])
