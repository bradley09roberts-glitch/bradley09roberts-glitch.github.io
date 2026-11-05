"""n13 step 0: walk-forward nba-0.3 N1 pmfs for all eight markets (the five originals and the
combos pr, pa, ra) on every played player-game in the stored-line games of 2024-25 (season key
2025, fit) and the 2025-26 sample (2026, test).

Universe and inputs exactly as n12_audit/n1_sf.py: B.frame (every input strictly before the game),
participation == played, a previous game, complete box score, game has stored quotes; has_line =
the player has a stored line in any of the five original markets for that game (as live).

Saves (git-ignored, data/runs/n13/data): per season sf[m][i, k] = P(X >= k), the pmf mean and
variance, the randomised PIT (fixed seed) and the outcome, for each market.
Check: the largest difference of the five original markets' sf from n12's stored nba-0.3 arrays
(3 Nov; inputs have changed since, see README). head_check.py shows HEAD's (pre-combo) code gives
the same five arrays bit for bit.
"""
import json
import sqlite3
import time
from pathlib import Path

import numpy as np
import pandas as pd

from npl.config import DB_PATH, PROJECT_ROOT
from npl.nba import backtest as B
from npl.nba import model as M

REPO = str(PROJECT_ROOT)
OUT = f"{REPO}/data/runs/n13"  # git-ignored
N12 = f"{REPO}/data/runs/n12/data"
Path(f"{OUT}/data").mkdir(parents=True, exist_ok=True)
t0 = time.time()
p = M.load_params("nba-0.3")
x, lg = B.frame(p)
print("frame", round(time.time() - t0), "s", x.shape, flush=True)
con = sqlite3.connect(f"file:{DB_PATH}?mode=ro", uri=True)
res = {}
for season in (2025, 2026):
    gids = set(pd.read_sql("SELECT DISTINCT game_id FROM hist_quotes WHERE sport='nba' AND season=?", con,
                           params=(season,)).game_id)
    xs = x[(x.season == season) & (x.participation == "played") & x.n_played.notna() & ~x.box_incomplete]
    xs = xs[xs.game_id.isin(gids)].reset_index(drop=True)
    lined = pd.read_sql("SELECT DISTINCT game_id, player_id FROM hist_quotes WHERE sport='nba' AND "
                        "season=? AND player_id IS NOT NULL AND market IN ('pts','reb','ast','fg3m','pra')",
                        con, params=(season,))
    have = set(zip(lined.game_id.astype(str), lined.player_id.astype(str), strict=True))
    xs["has_line"] = [(str(g), str(q)) in have for g, q in zip(xs.game_id, xs.player_id, strict=True)]
    pm = M.n1_pmfs(xs, p, lg)
    rng = np.random.default_rng(20261105 + season)
    arrs = {}
    for m in M.MARKETS:
        P = pm[m]
        k = np.arange(P.shape[1])
        mu = P @ k
        arrs[f"sf_{m}"] = np.cumsum(P[:, ::-1], axis=1)[:, ::-1]
        arrs[f"mean_{m}"] = mu
        arrs[f"var_{m}"] = P @ (k ** 2) - mu ** 2
        y = M.actual(xs, m)
        arrs[f"y_{m}"] = y
        arrs[f"pit_{m}"] = M.pit_rows(P, y, rng)
        arrs[f"trunc_{m}"] = (y > P.shape[1] - 1)
    np.savez_compressed(f"{OUT}/data/n1_{season}.npz", game_id=xs.game_id.astype(str).to_numpy(),
                        player_id=xs.player_id.astype(str).to_numpy(), date=xs.date.astype(str).to_numpy(),
                        has_line=xs.has_line.to_numpy(), min_y=xs.minutes_y.to_numpy(),
                        min_mu=xs.min_mu.to_numpy(), **arrs)
    # comparison with n12's stored nba-0.3 survival functions (written 3 Nov, 01:06 UTC), rows aligned by
    # (game, player): not bit-identical, because inputs changed after that run (README); the
    # bit-identity of the five markets before / after the combos is checked against HEAD's code instead
    z = np.load(f"{N12}/n1_sf_nba-0.3_{season}.npz", allow_pickle=True)
    ka = pd.Series(np.arange(len(z["game_id"])), index=pd.MultiIndex.from_arrays([z["game_id"], z["player_id"]]))
    ii = ka.reindex(pd.MultiIndex.from_arrays([xs.game_id.astype(str), xs.player_id.astype(str)])).to_numpy()
    found = ~np.isnan(ii)
    ii = ii[found].astype(int)
    ident = {m: float(np.abs(z[m][ii] - arrs[f"sf_{m}"][found]).max()) for m in M.ORIGINAL_MARKETS}
    res[season] = {"rows": int(len(xs)), "has_line_share": round(float(xs.has_line.mean()), 4),
                   "rows_found_in_n12": int(found.sum()), "max_abs_sf_diff_vs_n12": ident,
                   "outcomes_above_max_k": {m: int(arrs[f"trunc_{m}"].sum()) for m in M.MARKETS}}
    print(season, res[season], "t", round(time.time() - t0), "s", flush=True)
json.dump(res, open(f"{OUT}/pmfs_check.json", "w"), indent=1)
