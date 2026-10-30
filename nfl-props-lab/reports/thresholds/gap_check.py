"""How far must our projection be above a line before an over wins often enough? (descriptive)

For each market, take our model's projection (forecast mean) on the validation seasons (NFL X1
v0.4.2, 2022-2023; NBA N1 nba-0.1, 2024-2025), players who took part, projections in the range
where props are usually offered. For a gap g, the line is the highest half-point at least g below
the projection, and the over wins when the actual beats it. This measures our own forecast
errors only: the line here carries no information beyond our projection, whereas a bookmaker's
line does, so real over rates at the same gap would be lower. It cannot show that any gap beats
a bookmaker.
"""
import glob
import json
import sys

import numpy as np
import pandas as pd

NFL_REF = [  # (market, positions, minimum projection, reference over gap from the user's research board)
    ("pass_yds", ("QB",), 180, 25), ("pass_att", ("QB",), 25, 3), ("pass_cmp", ("QB",), 15, 2.5),
    ("rush_yds", ("RB",), 30, 7.5), ("rush_yds", ("QB",), 8, 5), ("rush_att", ("RB",), 7, 2),
    ("rush_att", ("QB",), 3, None), ("rec_yds", ("WR", "TE"), 25, 7.5), ("rec_yds", ("RB",), 10, 2.5),
    ("receptions", ("WR", "TE"), 2, 1), ("receptions", ("RB",), 1.5, None)]
NBA_REF = [("pts", 8), ("reb", 3), ("ast", 2), ("fg3m", 0.8), ("pra", 12)]
GAPS = np.arange(0, 40.5, 0.5)


def over_rate(proj, act, g):
    line = np.floor(proj - g - 0.5) + 0.5
    return (act > line).astype(float)


def summarise(proj, act, block, ref, rng):
    out = {"n": int(len(proj))}
    rates = {float(g): float(over_rate(proj, act, g).mean()) for g in GAPS}
    for target in (0.524, 0.55, 0.60):
        ok = [g for g, r in rates.items() if r >= target]
        out[f"gap_for_{int(target * 1000) / 10:g}%"] = min(ok) if ok else None
    out["over_rate_at_gap"] = {g: round(rates[g], 3) for g in (0.0, 0.5, 1.0, 2.0, 2.5, 3.0, 5.0, 7.5, 10.0, 15.0, 25.0)}
    if ref is not None:
        hit = over_rate(proj, act, ref)
        df = pd.DataFrame({"b": block, "h": hit}).groupby("b").h.agg(["sum", "size"])
        bs = [df.loc[rng.choice(df.index, len(df))].pipe(lambda a: a["sum"].sum() / a["size"].sum()) for _ in range(1000)]
        out["reference_gap"] = ref
        out["over_rate_at_reference"] = float(hit.mean())
        out["ci"] = [float(np.percentile(bs, 2.5)), float(np.percentile(bs, 97.5))]
    return out


def main(out_path):
    rng = np.random.default_rng(12)
    res = {"nfl": {}, "nba": {}}
    p = sorted(glob.glob("data/runs/exp11-validation-backup-trim-d0.7-2*/ledger.csv.gz"))[-1]
    led = pd.read_csv(p, usecols=["model", "market", "position", "mean", "actual", "scored", "season", "week"])
    led = led[(led.model == "X1") & led.scored]
    for mk, pos, lo, ref in NFL_REF:
        x = led[(led.market == mk) & led.position.isin(pos) & (led["mean"] >= lo)]
        res["nfl"][f"{mk}|{'/'.join(pos)}"] = {"min_projection": lo, **summarise(
            x["mean"].to_numpy(), x.actual.to_numpy(), (x.season * 100 + x.week).to_numpy(), ref, rng)}
    nb = pd.read_parquet("data/nba/runs/n02-validation-N1-empirical-minutes.parquet")
    nb = nb[(nb.model == "N1") & nb.actual.notna()]
    for mk, lo in NBA_REF:
        x = nb[(nb.market == mk) & (nb["mean"] >= lo)]
        res["nba"][mk] = {"min_projection": lo, **summarise(x["mean"].to_numpy(), x.actual.to_numpy(),
                                                            x.date.astype(str).to_numpy(), None, rng)}
    json.dump(res, open(out_path, "w"), indent=1)
    for sport, d in res.items():
        for k, v in d.items():
            print(sport, k, v["n"], "ref", v.get("reference_gap"), "rate", round(v.get("over_rate_at_reference", np.nan), 3),
                  [round(c, 3) for c in v.get("ci", [])], "| gap 52.4%:", v["gap_for_52.4%"], "55%:", v["gap_for_55%"], "60%:", v["gap_for_60%"],
                  "| at 0:", v["over_rate_at_gap"][0.0])


if __name__ == "__main__":
    main(sys.argv[1])
