"""n05: hand absent regulars' minutes and usage to teammates (model.absence_adjust).

Candidate rows of every team-game (players seen for the team in its previous ten games). Absent
= not listed for the game, or listed without minutes when his expected minutes were 15+ (a
regular sitting is nearly always known before tip; a bench player's coach's decision is not).
This treats the absences as known before tip, as an injury report would make them; late scratches
make the live case slightly harder. Chance of playing for recipients = share of the last ten team
games played (known before tip).

Scored: N1 CRPS on players who played, in team-games with at least one absent regular (all other
rows are unchanged by construction). alpha (share of minutes handed out) and gamma (usage) are
chosen on 2021-2023 and judged on the 2024-2025 validation seasons; 2026 (test) is not used.
"""
import json
import sys
import time

import numpy as np
import pandas as pd

from npl.nba import backtest as B
from npl.nba import model as M

GRID = [(0.0, 0.0), (0.5, 0.0), (1.0, 0.0), (0.5, 0.5), (1.0, 0.5), (1.0, 1.0), (0.5, 1.0)]


def week_ci(week, diff, n=1000, seed=5):
    d = pd.DataFrame({"w": week, "d": diff}).groupby("w").d.agg(["sum", "count"])
    rng = np.random.default_rng(seed)
    idx = rng.integers(0, len(d), (n, len(d)))
    b = d["sum"].to_numpy()[idx].sum(1) / d["count"].to_numpy()[idx].sum(1)
    return [float(np.percentile(b, 2.5)), float(np.percentile(b, 97.5))]


def main(out_path):
    p = M.load_params()
    t0 = time.time()
    x, lg = B.frame(p)
    print("frame", round(time.time() - t0), "s", len(x), file=sys.stderr, flush=True)
    x = x[x.n_played.notna() | (x.participation != "played")].reset_index(drop=True)
    mu = x.min_mu.fillna(0)
    absent = ((x.participation == "absent") | ((x.participation == "dnp") & (mu >= 15))).to_numpy()
    res = {"grid": GRID}
    for split, seasons in (("fit_2021_2023", (2021, 2022, 2023)), ("validation_2024_2025", (2024, 2025))):
        sel = x.season.isin(seasons).to_numpy()
        xs, ab = x[sel].reset_index(drop=True), absent[sel]
        key = xs.game_id.astype(str) + "|" + xs.team.astype(str)
        hit = set(key[ab & (xs.min_mu.fillna(0) >= 15).to_numpy()])
        score = (xs.participation == "played") & xs.n_played.notna() & ~xs.box_incomplete & key.isin(hit)
        week = xs.loc[score, "date"].dt.strftime("%G-%V").to_numpy()
        out = {"rows_scored": int(score.sum()), "team_games": len(hit)}
        crps = {}
        for a, g in GRID:
            t1 = time.time()
            xa = M.absence_adjust(xs, ab, xs.frac_played_10.to_numpy(), a, g, p)
            ev = xa[score.to_numpy()].reset_index(drop=True)
            pm = M.n1_pmfs(ev, p, lg)
            crps[(a, g)] = {s: M.crps_rows(pm[s], M.actual(ev, s)) for s in M.MARKETS}
            if (a, g) == (1.0, 0.0):
                out["mean_minutes_added"] = float(ev.absence_minutes_added.mean())
            print(split, a, g, round(time.time() - t1), "s", file=sys.stderr, flush=True)
        base = crps[(0.0, 0.0)]
        for (a, g), c in crps.items():
            if (a, g) == (0.0, 0.0):
                continue
            out[f"a{a}_g{g}"] = {s: {"crps_change_pct": float((c[s] - base[s]).mean() / base[s].mean() * 100),
                                     "ci_pct": [v / base[s].mean() * 100 for v in week_ci(week, c[s] - base[s])]}
                                 for s in M.MARKETS}
        out["base_crps"] = {s: float(base[s].mean()) for s in M.MARKETS}
        res[split] = out
        json.dump(res, open(out_path, "w"), indent=1)
    print(json.dumps(res, indent=1))


if __name__ == "__main__":
    main(sys.argv[1])
