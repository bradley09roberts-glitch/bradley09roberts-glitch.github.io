"""n09: how players did against the best defences, relative to what was projected for them.

For every player-game with a pre-game N1 projection (2023-24 and 2024-25 validation ledger n02,
2025-26 test ledger n03, 2026-27 prospective ledger), the opponent's defensive rank in that
stat before tip: season-to-date allowed per 100 possessions (points, rebounds, assists, threes,
PRA), shrunk towards the league with 10 games, ranked 1 (allows least) to 30 among teams on
that date. "Top defence" = rank 1-10. Residual = actual - projection (N1 mean), and also actual
minus the player's season average before the game.

1. Does it persist? For players with 8+ games against top-10 defences in each of two seasons,
   correlate their mean residual v top defences (minus their residual v the rest) in 2023-24
   with the same in 2024-25; and split-half within a season (odd v even games). A real "plays
   up / plays down to good defences" trait would show a clearly positive correlation.
2. Does N1 already handle top defences? Mean residual by opponent rank band.
3. Tonight's picks (1 Nov 2026): each player's record v top-10 defences in that stat, by season.

Regular-season games only (verification, 1 Nov: N1 runs about 1 point high on points in the
playoffs and 55% of playoff games are against top-10 defences, which distorted the first run).
The split-half test correlates the top-minus-rest difference between alternate halves of each
player-season's games (the first run correlated top-10 games only, which measures a player's
general bias, not a top-defence effect).
"""
import json
import sys

import pandas as pd

from npl.nba import canon

MARKETS = ("pts", "reb", "ast", "fg3m", "pra")
K = 10.0


def defence_ranks(tg: pd.DataFrame) -> pd.DataFrame:
    """Rank (1 = allows least per 100 possessions) of every team before each of its games."""
    t = tg.merge(tg[["game_id", "team", "pts", "reb", "ast", "fg3m", "poss"]].rename(columns={"team": "opp"}),
                 on=["game_id", "opp"], suffixes=("", "_a"))
    t = t.assign(pra_a=t.pts_a + t.reb_a + t.ast_a)
    t = t[(t.minutes >= 235) & (t.poss_a >= 60)].sort_values("tip_utc")
    rows = []
    for season, ts in t.groupby("season"):
        dates = sorted(ts.date.unique())
        cum = {}
        for d in dates:
            # state of every team before date d (games strictly earlier)
            lg = {}
            if cum:
                tot = {s: sum(v[s] for v in cum.values()) for s in (*MARKETS, "poss", "n")}
                lg = {s: tot[s] / tot["poss"] for s in MARKETS}
                avg_poss = tot["poss"] / tot["n"]
                vals = {}
                for team, v in cum.items():
                    vals[team] = {s: (v[s] + K * avg_poss * lg[s]) / (v["poss"] + K * avg_poss) for s in MARKETS}
                for s in MARKETS:
                    order = sorted(vals, key=lambda tm, s=s: vals[tm][s])
                    for r, tm in enumerate(order, 1):
                        rows.append({"season": season, "date": d, "team": tm, "market": s, "rank": r,
                                     "n_teams": len(order), "games": cum[tm]["n"]})
            for r in ts[ts.date == d].itertuples():
                v = cum.setdefault(r.team, {s: 0.0 for s in (*MARKETS, "poss", "n")})
                for s in MARKETS:
                    v[s] += getattr(r, f"{s}_a")
                v["poss"] += r.poss_a
                v["n"] += 1
    return pd.DataFrame(rows)


def projections() -> pd.DataFrame:
    parts = []
    for f in ("n02-validation-N1-empirical-minutes", "n03-TEST-frozen-nba-0.1"):
        d = pd.read_parquet(f"data/nba/runs/{f}.parquet")
        d = d[d.model == "N1"][["game_id", "player_id", "market", "mean", "actual"]]
        d["player_id"] = d.player_id.astype(str).str.replace(r"\.0$", "", regex=True)
        parts.append(d.assign(source=f.split("-")[0]))
    p = pd.read_csv("reports/nba/record/prospective_ledger.csv", dtype={"game_id": str, "player_id": str})
    p = p[(p.model == "N1") & (p.participation == "played")]
    # one counting edition per game: the last published before tip (the ledger keeps one row per
    # player-game-market already when settled; keep the latest edition if repeated)
    p = p.sort_values("published_at").drop_duplicates(["game_id", "player_id", "market"], keep="last")
    parts.append(p[["game_id", "player_id", "market", "mean", "actual"]].assign(source="live"))
    return pd.concat(parts, ignore_index=True)


def main(out_path):
    L = canon.load()
    pg, tg = L["player_games"], L["team_games"]
    pr = projections()
    meta = pg[pg.participation == "played"][["game_id", "player_id", "name", "team", "opp", "season", "date",
                                              "season_type", "minutes"]]
    d = pr.merge(meta, on=["game_id", "player_id"])
    d = d[d.season_type == "REG"]
    ranks = defence_ranks(tg)
    d = d.merge(ranks.rename(columns={"team": "opp"}), on=["season", "date", "opp", "market"], how="left")
    d = d[d["rank"].notna()].copy()
    d["resid"] = d.actual - d["mean"]
    d["top"] = d["rank"] <= 10
    # season average before the game (same player, same season, earlier dates)
    pgp = pg[pg.participation == "played"].assign(pra=lambda x: x.pts + x.reb + x.ast).sort_values("tip_utc")
    for s in MARKETS:
        pgp[f"avg_{s}"] = pgp.groupby(["player_id", "season"])[s].transform(lambda v: v.shift().expanding().mean())
    av = pgp.melt(id_vars=["game_id", "player_id"], value_vars=[f"avg_{s}" for s in MARKETS],
                  var_name="market", value_name="season_avg")
    av["market"] = av.market.str[4:]
    d = d.merge(av, on=["game_id", "player_id", "market"], how="left")
    d["resid_avg"] = d.actual - d.season_avg
    res = {"rows": int(len(d)), "by_source": d.source.value_counts().to_dict()}
    # 2. does N1 already handle top defences?
    d["band"] = pd.cut(d["rank"], [0, 5, 10, 20, 25, 30], labels=["1-5", "6-10", "11-20", "21-25", "26-30"])
    res["residual_by_rank_band"] = {
        s: d[d.market == s].groupby("band", observed=True).agg(
            n=("resid", "size"), n1_resid=("resid", "mean"), vs_season_avg=("resid_avg", "mean")).round(3).to_dict("index")
        for s in MARKETS}
    # 1. persistence of a player's v-top-defence effect
    pers = {}
    for s in MARKETS:
        x = d[(d.market == s) & d.season.isin([2024, 2025])]
        g = x.groupby(["player_id", "season", "top"]).resid.agg(["mean", "size"]).unstack("top")
        g = g[(g[("size", True)] >= 8) & (g[("size", False)] >= 8)]
        eff = (g[("mean", True)] - g[("mean", False)]).unstack("season").dropna()
        r_season = float(eff[2024].corr(eff[2025])) if len(eff) > 5 else None
        # split half: alternate games within each player-season and top/rest cell, then
        # correlate the top-minus-rest difference between the halves
        y = x.sort_values("date").copy()
        y["half"] = y.groupby(["player_id", "season", "top"]).cumcount() % 2
        h = y.groupby(["player_id", "season", "half", "top"]).resid.agg(["mean", "size"]).unstack(["half", "top"])
        ok = (h["size"] >= 4).all(axis=1)
        h = h[ok]
        dh0 = h[("mean", 0, True)] - h[("mean", 0, False)]
        dh1 = h[("mean", 1, True)] - h[("mean", 1, False)]
        r_half = float(dh0.corr(dh1)) if len(h) > 5 else None
        pers[s] = {"players_both_seasons": int(len(eff)), "corr_2024_v_2025": r_season,
                   "player_seasons_split_half": int(len(h)), "corr_split_half": r_half}
    res["persistence"] = pers
    # 3. tonight's picks
    picks = [("Jayson Tatum", "pts", "under", 26.5, "ORL"), ("Andrew Nembhard", "pts", "under", 18.5, "BKN"),
             ("Kris Dunn", "pts", "under", 8.5, "GS"), ("Jakob Poeltl", "reb", "under", 6.5, "LAL"),
             ("Scottie Barnes", "reb", "under", 7.5, "LAL"), ("Rui Hachimura", "pts", "under", 15.5, "GS"),
             ("Andrew Nembhard", "reb", "under", 3.5, "BKN"), ("Pascal Siakam", "reb", "under", 7.5, "BKN"),
             ("Derrick White", "ast", "under", 4.5, "ORL"), ("Walker Kessler", "pts", "under", 12.5, "TOR"),
             ("Austin Reaves", "pts", "under", 22.5, "TOR")]
    latest = ranks[ranks.season == ranks.season.max()]
    latest = latest[latest.date == latest.date.max()]
    tonight = []
    for name, mk, side, line, opp in picks:
        x = d[(d.name == name) & (d.market == mk)]
        top = x[x.top]

        def hit(v, line=line, side=side):
            return int(((v.actual < line) if side == "under" else (v.actual > line)).sum())

        orank = latest[(latest.team == opp) & (latest.market == mk)]
        by_season = {int(sn): {"games": int(len(v)), "minutes": round(float(v.minutes.mean()), 1),
                               "avg": round(float(v.actual.mean()), 1), "side_hit": f"{hit(v)}/{len(v)}",
                               "v_top10_avg": round(float(v[v.top].actual.mean()), 1) if v.top.any() else None}
                     for sn, v in x.groupby("season")}
        tonight.append({"player": name, "market": mk, "side": side, "line": line, "opponent": opp,
                        "by_season": by_season,
                        "opp_rank_now": int(orank["rank"].iloc[0]) if len(orank) else None,
                        "games": int(len(x)), "games_v_top10": int(len(top)),
                        "resid_v_top10": round(float(top.resid.mean()), 2) if len(top) else None,
                        "resid_v_rest": round(float(x[~x.top].resid.mean()), 2) if (~x.top).any() else None,
                        "avg_v_top10": round(float(top.actual.mean()), 1) if len(top) else None,
                        "side_hit_v_top10": f"{hit(top)}/{len(top)}" if len(top) else None,
                        "side_hit_all": f"{hit(x)}/{len(x)}"})
    res["tonight"] = tonight
    json.dump(res, open(out_path, "w"), indent=1, default=str)
    print(json.dumps(res, indent=1, default=str))


if __name__ == "__main__":
    main(sys.argv[1])
