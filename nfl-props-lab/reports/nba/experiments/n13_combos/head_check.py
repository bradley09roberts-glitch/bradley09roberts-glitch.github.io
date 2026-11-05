"""n13 check: the five original markets from the committed (pre-combo) code equal pmfs.py's arrays
bit for bit on the real backtest rows of both seasons.

Run with HEAD's tree on the path instead of the working tree (git archive, never a stash), e.g.
    git archive HEAD src reports/nba/model | tar -x -C <scratch>/head
    NPL_HOME=$PWD/data PYTHONPATH=<scratch>/head/src .venv/bin/python \
        reports/nba/experiments/n13_combos/head_check.py
NPL_HOME keeps the data reads on this repo's data/ (read-only: B.frame and a mode=ro SQLite).
"""
import json
import os
import sqlite3

import numpy as np
import pandas as pd

import npl
from npl.nba import backtest as B
from npl.nba import model as M

DATA = os.environ["NPL_HOME"]
print("npl imported from", npl.__file__)
p = M.load_params("nba-0.3")
x, lg = B.frame(p)
con = sqlite3.connect(f"file:{DATA}/npl.sqlite3?mode=ro", uri=True)
out = {"npl_path": npl.__file__}
for s in (2025, 2026):
    gids = set(pd.read_sql("SELECT DISTINCT game_id FROM hist_quotes WHERE sport='nba' AND season=?", con,
                           params=(s,)).game_id)
    xs = x[(x.season == s) & (x.participation == "played") & x.n_played.notna() & ~x.box_incomplete]
    xs = xs[xs.game_id.isin(gids)].reset_index(drop=True)
    lined = pd.read_sql("SELECT DISTINCT game_id, player_id FROM hist_quotes WHERE sport='nba' AND season=? "
                        "AND player_id IS NOT NULL", con, params=(s,))
    have = set(zip(lined.game_id.astype(str), lined.player_id.astype(str), strict=True))
    xs["has_line"] = [(str(g), str(q)) in have for g, q in zip(xs.game_id, xs.player_id, strict=True)]
    pm = M.n1_pmfs(xs, p, lg, only=("pts", "reb", "ast", "fg3m", "pra"))
    z = np.load(f"{DATA}/runs/n13/data/n1_{s}.npz", allow_pickle=True)
    rows = bool(np.array_equal(z["game_id"], xs.game_id.astype(str).to_numpy())
                and np.array_equal(z["player_id"], xs.player_id.astype(str).to_numpy()))
    out[s] = {"rows": int(len(xs)), "same_rows": rows,
              **{m: bool(rows and np.array_equal(np.cumsum(pm[m][:, ::-1], axis=1)[:, ::-1], z[f"sf_{m}"]))
                 for m in ("pts", "reb", "ast", "fg3m", "pra")}}
    print(s, out[s], flush=True)
json.dump(out, open(f"{DATA}/runs/n13/head_check.json", "w"), indent=1)
