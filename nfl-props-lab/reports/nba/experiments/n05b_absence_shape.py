"""n05b: as n05, with the freed minutes weighted towards rotation players with room for more
(shape "tent") and towards the absent player's own position group (same_pos), and a smaller
usage boost. n05 (minutes in proportion to expected minutes) was worse for every setting except
handing out half the minutes without a usage boost (points -0.7%, rebounds +0.3%). Same rows,
same scoring; chosen on 2021-2023, judged on 2024-2025."""
import json
import sys
import time

from npl.nba import backtest as B
from npl.nba import model as M

sys.path.insert(0, "reports/nba/experiments")
from n05_absence import week_ci  # noqa: E402

GRID = [("minutes", 0.0, 0.0, 0.0), ("tent", 0.0, 0.5, 0.0), ("tent", 0.0, 1.0, 0.0), ("tent", 2.0, 0.5, 0.0),
        ("tent", 2.0, 1.0, 0.0), ("tent", 2.0, 1.0, 0.15), ("tent", 2.0, 0.5, 0.15)]


def main(out_path, splits):
    p = M.load_params()
    x, lg = B.frame(p)
    x = x[x.n_played.notna() | (x.participation != "played")].reset_index(drop=True)
    mu = x.min_mu.fillna(0)
    absent = ((x.participation == "absent") | ((x.participation == "dnp") & (mu >= 15))).to_numpy()
    res = {"grid": GRID}
    for split, seasons in splits:
        sel = x.season.isin(seasons).to_numpy()
        xs, ab = x[sel].reset_index(drop=True), absent[sel]
        key = xs.game_id.astype(str) + "|" + xs.team.astype(str)
        hit = set(key[ab & (xs.min_mu.fillna(0) >= 15).to_numpy()])
        score = ((xs.participation == "played") & xs.n_played.notna() & ~xs.box_incomplete & key.isin(hit)).to_numpy()
        week = xs.loc[score, "date"].dt.strftime("%G-%V").to_numpy()
        out, crps = {"rows_scored": int(score.sum())}, {}
        for shape, sp, a, g in GRID:
            t1 = time.time()
            xa = M.absence_adjust(xs, ab, xs.frac_played_10.to_numpy(), a, g, p, shape=shape, same_pos=sp)
            ev = xa[score].reset_index(drop=True)
            pm = M.n1_pmfs(ev, p, lg)
            crps[(shape, sp, a, g)] = {s: M.crps_rows(pm[s], M.actual(ev, s)) for s in M.MARKETS}
            print(split, shape, sp, a, g, round(time.time() - t1), "s", file=sys.stderr, flush=True)
        base = crps[GRID[0]]
        for k, c in crps.items():
            if k == GRID[0]:
                continue
            out["|".join(map(str, k))] = {s: {"crps_change_pct": float((c[s] - base[s]).mean() / base[s].mean() * 100),
                                              "ci_pct": [v / base[s].mean() * 100 for v in week_ci(week, c[s] - base[s])]}
                                          for s in M.MARKETS}
        res[split] = out
        json.dump(res, open(out_path, "w"), indent=1)


if __name__ == "__main__":
    main(sys.argv[1], [("fit_2021_2023", (2021, 2022, 2023)), ("validation_2024_2025", (2024, 2025))])
