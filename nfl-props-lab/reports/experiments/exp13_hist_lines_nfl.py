"""exp13: the NFL models against real bookmaker lines for the 2023 season.

Lines: The Odds API historical snapshots 2 hours before kick-off, US books, core markets, main
lines (hist_quotes; D-040). Models: the walk-forward forecasts of every 2023 slate with the
current code (v0.4.2; same seed and refits as the backtests), headline X1 and its parts J1 and M,
and baseline A. Only players who took part (props are void otherwise). 2023 is a validation
season: the stacking weights (2022-2023) and the v0.4.2 trim were chosen on it, so this is
slightly favourable to the model.

Reported: log loss v the market's margin-free probability, results by gap band, the NFL pick rule
(6-15 points, J1 and M on the same side by 6+, A agreeing), BetMGM-only, by market and side.
"""
import json
import sys

import numpy as np
import pandas as pd

sys.path.insert(0, "reports/nba/experiments")
from n10_hist_lines import bets, boot_ci, consensus  # noqa: E402

from npl import backtest, db, forecast  # noqa: E402
from npl.dist import Dist  # noqa: E402
from npl.features import load_lake  # noqa: E402


def main(out_path):
    con = db.connect()
    h = pd.read_sql("SELECT * FROM hist_quotes WHERE sport='nfl' AND season=2023", con)
    props = consensus(h)
    lake = load_lake()
    g = lake["games"]
    weeks = sorted({int(w) for w in g[g.season == 2023].week})
    state = {"models": None, "season": None, "fits": []}
    want = props.set_index(["game_id", "player_id", "market"])
    rows = []
    for w in weeks:
        mlm = backtest._ml_models_for(lake, 2023, w, state)
        res = forecast.run_slate(lake, 2023, w, n_sims=4000, seed=7, models=("X1", "J1", "J0", "A"), ml_models=mlm)
        for f in res["forecasts"]:
            if f.get("status") != "ok" or f["model"] not in ("X1", "J1", "M", "A"):
                continue
            k = (f["game_id"], f["player_id"], f["market"])
            if k not in want.index:
                continue
            line = float(want.loc[k, "line"]) if not isinstance(want.loc[k, "line"], pd.Series) else float(want.loc[k, "line"].iloc[0])
            rows.append({"game_id": k[0], "player_id": k[1], "market": k[2], "model": f["model"],
                         "p_over": Dist.from_json(f["dist"]).line(line)["over"], "position": f.get("position")})
        print("week", w, len(rows), file=sys.stderr, flush=True)
    m = pd.DataFrame(rows).pivot_table(index=["game_id", "player_id", "market"], columns="model", values="p_over").reset_index()
    pg = lake["player_games"]
    stat = {"pass_yds": "passing_yards", "pass_att": "attempts", "pass_cmp": "completions", "rush_yds": "rushing_yards",
            "rush_att": "carries", "receptions": "receptions", "rec_yds": "receiving_yards"}
    pl = pg[(pg.season == 2023) & (pg.participation == "played")]
    act = pd.concat([pl[["game_id", "player_id", "week"]].assign(market=k, actual=pl[v].fillna(0).to_numpy()) for k, v in stat.items()])
    d = props.merge(m, on=["game_id", "player_id", "market"]).merge(act, on=["game_id", "player_id", "market"])
    d = d[(d.actual != d.line) & d.X1.notna()].reset_index(drop=True)
    d["date"] = d.week
    y = (d.actual > d.line).astype(float).to_numpy()
    pk = d.fair_over.clip(1e-4, 1 - 1e-4).to_numpy()

    def ll(p):
        p = np.clip(p, 1e-4, 1 - 1e-4)
        return float(-np.mean(y * np.log(p) + (1 - y) * np.log(1 - p)))
    res = {"props": int(len(d)), "games": int(d.game_id.nunique()), "margin_mean": float(d.margin.mean()),
           "log_loss": {"market": ll(pk), **{mdl: ll(d[mdl].to_numpy()) for mdl in ("X1", "J1", "M", "A") if mdl in d}}}
    ws = np.linspace(0, 1, 101)
    lls = [ll(pk + w_ * (d.X1.to_numpy() - pk)) for w_ in ws]
    res["trust_fraction_best"] = float(ws[int(np.argmin(lls))])
    res["log_loss_diff_ci_X1"] = boot_ci(d.date, (-(y * np.log(np.clip(d.X1, 1e-4, 1 - 1e-4)) + (1 - y) * np.log(np.clip(1 - d.X1, 1e-4, 1)))) - (-(y * np.log(pk) + (1 - y) * np.log(1 - pk))))
    side = np.where(d.X1 >= d.fair_over, "over", "under")
    sp = lambda c: np.where(side == "over", d[c], 1 - d[c])  # noqa: E731
    kp = np.where(side == "over", d.fair_over, 1 - d.fair_over)
    d["side"], d["gap"] = side, sp("X1") - kp
    elig = (d.books >= 3) & (d.line % 1 != 0)
    agree = (np.minimum(sp("J1"), sp("M")) - kp >= 0.06) & (sp("A") > 0.5)
    res["bands"] = {f"{int(lo * 100)}-{int(hi * 100)}": bets(d[elig & (d.gap >= lo) & (d.gap < hi)], d.side[elig & (d.gap >= lo) & (d.gap < hi)].to_numpy())
                    for lo, hi in ((0, .06), (.06, .10), (.10, .15), (.15, 1))}
    pick = elig & (d.gap >= .06) & (d.gap < .15) & agree
    res["pick_rule"] = bets(d[pick], d.side[pick].to_numpy())
    res["pick_rule_by_market"] = {mk: bets(d[pick & (d.market == mk)], d.side[pick & (d.market == mk)].to_numpy()) for mk in stat}
    res["pick_rule_by_side"] = {s_: bets(d[pick & (d.side == s_)], d.side[pick & (d.side == s_)].to_numpy()) for s_ in ("over", "under")}
    g2 = d[d.mgm_fair.notna()]
    gs = np.where(g2.X1 >= g2.mgm_fair, "over", "under")
    gg = np.where(gs == "over", g2.X1 - g2.mgm_fair, g2.mgm_fair - g2.X1)
    res["betmgm_bands"] = {f"{int(lo * 100)}-{int(hi * 100)}": bets(g2[(gg >= lo) & (gg < hi)], gs[(gg >= lo) & (gg < hi)], ("mgm_over", "mgm_under"))
                           for lo, hi in ((0, .06), (.06, .15), (.15, 1))}
    json.dump(res, open(out_path, "w"), indent=1, default=float)
    d.to_parquet(out_path.replace(".json", "_rows.parquet"))
    print(json.dumps(res, indent=1, default=float))


if __name__ == "__main__":
    main(sys.argv[1])
