"""n08: the simple baseline S (npl.nba.simple) against N1 and A, and its line-gap rule.

1. Point accuracy on every 2024-2025 player-game (the n02 rows): mean absolute error and bias of
   S, N1's mean and A's mean. The rest factors are fitted on 2018-2023 first.
2. Against bookmakers: every eligible prop with a stored US line (28, 29, 30 Oct 2026; the
   counting editions), the gap rule "bet the side S favours when it beats the line by g" for
   g = 0.5, 1, 1.5, 2 (points and PRA) and 0.5, 1 (rebounds, assists, threes), against the pick
   rules on the same props. Three nights are far too few to judge a betting rule; they only show
   how often it fires and what happened.
"""
import json
import sys

import numpy as np
import pandas as pd

sys.path.insert(0, "reports/thresholds")
import before_after as BA  # noqa: E402

from npl import db  # noqa: E402
from npl.nba import backtest as B  # noqa: E402
from npl.nba import canon  # noqa: E402
from npl.nba import simple as SM  # noqa: E402
from npl.sources import odds_api as oa  # noqa: E402

EDITIONS = ["records/editions/nba/2026-10-28/NBA-2026-10-28-final-20261028T1908Z.json",
            "records/editions/nba/2026-10-29/NBA-2026-10-29-early-20261029T0855Z.json",
            "records/editions/nba/2026-10-30/NBA-2026-10-30-final-20261030T2201Z.json"]


def main(out_path):
    L = canon.load()
    pg, tg, games = L["player_games"], L["team_games"], L["games"]
    played = SM._with_pra(pg[pg.participation == "played"])
    res = {}
    # 1. fit rest factors on the fit seasons, point accuracy on validation
    fit = played[played.season.isin(B.FIT_SEASONS)].reset_index(drop=True)
    ff = SM.features(fit[["player_id", "team", "opp", "game_id", "season", "tip_utc"]], pg, tg, games)
    rest = SM.fit_rest(ff, fit)
    SM.save_rest(rest)
    res["rest_factors"] = rest
    led = pd.read_parquet("data/nba/runs/n02-validation-N1-empirical-minutes.parquet")
    led["player_id"] = led.player_id.str.replace(r"\.0$", "", regex=True)
    val = played[played.season.isin(B.VALIDATION)].reset_index(drop=True)
    fv = SM.features(val[["player_id", "team", "opp", "game_id", "season", "tip_utc"]], pg, tg, games)
    sv = SM.project(fv, rest)
    acc = {}
    for s in SM.MARKETS:
        d = val[["game_id", "player_id"]].assign(S=sv[s].to_numpy(), y=val[s].to_numpy())
        m = led[led.market == s].pivot_table(index=["game_id", "player_id"], columns="model", values="mean").reset_index()
        d = d.merge(m, on=["game_id", "player_id"]).dropna(subset=["S", "N1", "A"])
        acc[s] = {"n": int(len(d)), **{f"mae_{k}": float((d[k] - d.y).abs().mean()) for k in ("S", "N1", "A")},
                  **{f"bias_{k}": float((d[k] - d.y).mean()) for k in ("S", "N1", "A")}}
    res["validation_point_accuracy"] = acc
    # 2. the gap rule on the three nights of stored lines
    q = oa.quotes_frame(db.connect())
    rows = pd.concat([pd.DataFrame(BA.nba_rows(e, q, pg)) for e in EDITIONS], ignore_index=True)
    rows = rows[rows.eligible].reset_index(drop=True)
    meta = pg[["game_id", "player_id", "name", "team", "opp", "season", "tip_utc"]].drop_duplicates(["game_id", "name"])
    eds = [json.load(open(e)) for e in EDITIONS]
    who = pd.DataFrame([{"night": e["date_et"], "player": f["name"], "game_id": f["game_id"], "player_id": f["player_id"]}
                        for e in eds for f in e["forecasts"] if f["model"] == "N1" and f["market"] == "pts"])
    rows = rows.merge(who, on=["night", "player"], how="left").merge(
        meta.drop(columns="name"), on=["game_id", "player_id"], how="left")
    fr = SM.features(rows[["player_id", "team", "opp", "game_id", "season", "tip_utc"]], pg, tg, games)
    sp = SM.project(fr, rest)
    rows["S"] = [sp.at[i, m] for i, m in enumerate(rows.market)]
    rows["side_m"] = np.where(rows.p_over >= rows.fair_over, "over", "under")
    rows["gap_m"] = np.where(rows.side_m == "over", rows.p_over - rows.fair_over, rows.fair_over - rows.p_over)
    out = {}

    def score(x, side):
        won = [BA.settle(a, ln, sd) for a, ln, sd in zip(x.actual, x.line, side, strict=True)]
        price = np.where(side == "over", x.best_over, x.best_under)
        w = np.array([v is True for v in won])
        lo = np.array([v is False for v in won])
        n = int(w.sum() + lo.sum())
        return {"bets": n, "won": int(w.sum()), "win_rate": round(w.sum() / n, 3) if n else None,
                "units": round(float((w * (price - 1)).sum() - lo.sum()), 2)}
    for g_big, g_small in ((0.5, 0.5), (1.0, 0.5), (1.0, 1.0), (1.5, 1.0), (2.0, 1.0)):
        g = np.where(rows.market.isin(["pts", "pra"]), g_big, g_small)
        d = rows.S - rows.line
        side = np.where(d >= g, "over", np.where(-d >= g, "under", None))
        x = rows[side != None]  # noqa: E711
        out[f"S_gap_{g_big}/{g_small}"] = score(x, side[side != None])  # noqa: E711
    pick = (rows.gap_m >= 0.10) & (rows.gap_m < 0.15)
    out["pick_rules_10_15"] = score(rows[pick], rows.side_m[pick].to_numpy())
    out["all_model_side"] = score(rows, rows.side_m.to_numpy())
    agree = (np.sign(rows.S - rows.line) == np.where(rows.side_m == "over", 1, -1)) & pick
    out["pick_rules_and_S_agrees"] = score(rows[agree], rows.side_m[agree].to_numpy())
    res["three_nights_lines"] = {"eligible_props": int(len(rows)), **out}
    json.dump(res, open(out_path, "w"), indent=1)
    print(json.dumps(res, indent=1))


if __name__ == "__main__":
    main(sys.argv[1])
