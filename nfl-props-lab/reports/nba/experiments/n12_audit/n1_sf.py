"""v01: my own N1 (frozen nba-0.1) survival functions and live P(plays) for every played player-game
in the stored-line seasons, computed from the repo's B.frame + M.n1_pmfs (model.py / backtest.py are
unchanged since fc92cd5 and are not among the files being edited).

Universe exactly as n10 model_probs(): season, participation == played, n_played notna, not
box_incomplete, game has stored quotes. p_play = frozen availability.json (fc92cd5) logistic on
M.avail_matrix (features strictly before the game).
Saves data/n1_sf_{season}.npz with sf[m][i, k] = P(X >= k).
Checks: P(over) at the n10 line v the stored n10 p_over.
"""
import json
import os
import resource
import sqlite3
import time
from pathlib import Path

import numpy as np
import pandas as pd

from npl.config import DB_PATH, PROJECT_ROOT
from npl.nba import backtest as B
from npl.nba import model as M

REPO = str(PROJECT_ROOT)
HERE = f"{REPO}/data/runs/n12"  # git-ignored: raw prices stay in data/
Path(f"{HERE}/data").mkdir(parents=True, exist_ok=True)
t0 = time.time()
PARAMS = os.environ.get("N12_PARAMS", "nba-0.1")
p = M.load_params(PARAMS)
x, lg = B.frame(p)
print("frame", round(time.time() - t0), "s", x.shape, "maxrss MB",
      resource.getrusage(resource.RUSAGE_SELF).ru_maxrss // 1024, flush=True)
con = sqlite3.connect(f"file:{DB_PATH}?mode=ro", uri=True)
av = json.load(open(f"{REPO}/reports/nba/model/availability.json"))
res = {}
for season in (2025, 2026):
    gids = set(pd.read_sql("SELECT DISTINCT game_id FROM hist_quotes WHERE sport='nba' AND season=?", con,
                           params=(season,)).game_id)
    xs = x[(x.season == season) & (x.participation == "played") & x.n_played.notna() & ~x.box_incomplete]
    xs = xs[xs.game_id.isin(gids)].reset_index(drop=True)
    # how many played rows in quoted games are dropped by the n_played / box_incomplete filters
    allp = x[(x.season == season) & (x.participation == "played") & x.game_id.isin(gids)]
    res[season] = {"rows": int(len(xs)), "played_rows_in_quoted_games": int(len(allp)),
                   "dropped_box_incomplete": int(allp.box_incomplete.sum()),
                   "dropped_no_history": int(allp.n_played.isna().sum())}
    z = M.avail_matrix(xs) @ np.asarray(av["coef"]) + av["intercept"]
    p_play = 1 / (1 + np.exp(-z))
    # nba-0.3 rate factors apply to player-games with a posted line in any market (as live)
    lined = pd.read_sql("SELECT DISTINCT game_id, player_id FROM hist_quotes WHERE sport='nba' AND "
                        "season=? AND player_id IS NOT NULL", con, params=(season,))
    have = set(zip(lined.game_id.astype(str), lined.player_id.astype(str), strict=True))
    xs["has_line"] = [(str(g), str(q)) in have for g, q in zip(xs.game_id, xs.player_id, strict=True)]
    res[season]["has_line_share"] = round(float(xs.has_line.mean()), 4)
    pm = M.n1_pmfs(xs, p, lg)
    sf = {m: np.cumsum(pm[m][:, ::-1], axis=1)[:, ::-1] for m in M.MARKETS}
    np.savez_compressed(f"{HERE}/data/n1_sf_{PARAMS}_{season}.npz", game_id=xs.game_id.astype(str).to_numpy(),
                        player_id=xs.player_id.astype(str).to_numpy(), date=xs.date.astype(str).to_numpy(),
                        p_play=p_play, **sf)
    # check v stored n10 p_over at the n10 line
    n10 = pd.read_parquet(f"{PROJECT_ROOT}/data/runs/n10/n10_{season}_rows.parquet")
    key = {(g, q): i for i, (g, q) in enumerate(zip(xs.game_id.astype(str), xs.player_id.astype(str), strict=False))}
    ii = np.array([key.get((str(g), str(q)), -1) for g, q in zip(n10.game_id, n10.player_id, strict=False)])
    k = np.floor(n10.line.to_numpy()).astype(int) + 1
    mine = np.full(len(n10), np.nan)
    for m in M.MARKETS:
        s = (n10.market.to_numpy() == m) & (ii >= 0)
        kk = np.clip(k[s], 0, sf[m].shape[1] - 1)
        mine[s] = np.where(k[s] <= sf[m].shape[1] - 1, sf[m][ii[s], kk], 0.0)
    res[season].update({"n10_rows": int(len(n10)), "n10_rows_found": int((ii >= 0).sum()),
                        "max_abs_diff_vs_stored_p_over": float(np.nanmax(np.abs(mine - n10.p_over.to_numpy()))),
                        "p_play_ge_085_share": round(float((p_play >= 0.85).mean()), 4)})
    print(season, res[season], "t", round(time.time() - t0), "s maxrss MB",
          resource.getrusage(resource.RUSAGE_SELF).ru_maxrss // 1024, flush=True)
json.dump(res, open(f"{HERE}/v01_n1_{PARAMS}.json", "w"), indent=1)
