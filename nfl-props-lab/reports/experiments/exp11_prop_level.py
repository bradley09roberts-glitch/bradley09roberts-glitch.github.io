"""exp11, prop level: the full models with the designed-run trim (0.7) v the same code without
it, on the 2022-2023 validation slates (same seed, so the two runs share random numbers).

Runs (ledgers in data/runs/, reproducible from commit 2470be7):
    backtest.run(<2022-2023 slates>, label="exp11-validation-baseline", models=("X1", "J1", "J0"),
                 n_sims=4000, seed=7, with_ml=True)
    the same with features.HP["ctx_backup_trim_d"] = 0.7, label="exp11-validation-backup-trim-d0.7"

Scores: CRPS (lower is better) and log score at the exact count (higher is better) per market,
for all player-games and for those whose forecast mean moved; and over/under thresholds (the
events a prop settles on): Brier and binary log loss (lower is better). Week-block bootstrap.
"""
import glob
import json
import sys

import numpy as np
import pandas as pd


def led(label):
    p = sorted(glob.glob(f"data/runs/{label}-2*/ledger.csv.gz"))[-1]
    return pd.read_csv(p, low_memory=False)


def boot(wk, v, rng, n=1000):
    g = pd.DataFrame({"wk": wk, "v": v}).groupby("wk").v.agg(["sum", "size"])
    w = g.index.to_numpy()
    s = [g.loc[rng.choice(w, len(w))].pipe(lambda a: a["sum"].sum() / a["size"].sum()) for _ in range(n)]
    return [float(np.percentile(s, 2.5)), float(np.percentile(s, 97.5))]


def main(out_path):
    b, t = led("exp11-validation-baseline"), led("exp11-validation-backup-trim-d0.7")
    m = b.merge(t, on=["model", "game_id", "player_id", "market"], suffixes=("_b", "_t"))
    m = m[m.scored_b & m.scored_t & m.model.isin(["X1", "J1", "J0"])].copy()
    m["wk"] = m.season_b * 100 + m.week_b
    rng = np.random.default_rng(11)
    out = {"distribution": {}, "thresholds": {}}
    for (mod, mk), x in m.groupby(["model", "market"]):
        for scope, y in (("all", x), ("moved", x[(x.mean_t - x.mean_b).abs() > 0.05])):
            if len(y) < 30:
                continue
            dc, dl = y.crps_t - y.crps_b, y.log_score_t - y.log_score_b
            base = y.crps_b.mean()
            out["distribution"][f"{mod}|{mk}|{scope}"] = {
                "n": int(len(y)), "crps_change_pct": float(dc.mean() / base * 100),
                "crps_ci_pct": [v / base * 100 for v in boot(y.wk, dc, rng)],
                "log_score_change": float(dl.mean()), "log_score_ci": boot(y.wk, dl, rng)}
        if mk not in ("rush_att", "rush_yds", "receptions", "rec_yds"):
            continue
        for c in [c for c in b.columns if c.startswith("p_ge_")]:
            if x[c + "_b"].isna().all():
                continue
            y = x[x[c + "_b"].notna()]
            o = y["y_ge_" + c[5:] + "_b"].astype(float)
            pb, pt = y[c + "_b"].clip(1e-4, 1 - 1e-4), y[c + "_t"].clip(1e-4, 1 - 1e-4)
            db = (pt - o) ** 2 - (pb - o) ** 2
            dl = -(o * np.log(pt) + (1 - o) * np.log(1 - pt)) + (o * np.log(pb) + (1 - o) * np.log(1 - pb))
            out["thresholds"][f"{mod}|{mk}|>={c[5:]}"] = {
                "n": int(len(y)), "base_rate": float(o.mean()), "brier_change": float(db.mean()),
                "logloss_change": float(dl.mean()), "logloss_ci": boot(y.wk, dl, rng, 500)}
    json.dump(out, open(out_path, "w"), indent=1)


if __name__ == "__main__":
    main(sys.argv[1])
