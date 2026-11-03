"""v00: read-only extract of NBA hist_quotes (seasons 2025 = 2024-25, 2026 = 2025-26) to scratch parquet.

Opens the SQLite store read-only (mode=ro), never via npl.db.connect() (which runs schema DDL).
Output files hold raw prices: scratch only, never to git. Prints aggregates only.
"""
import json
import sqlite3
from pathlib import Path

import pandas as pd

from npl.config import DB_PATH, PROJECT_ROOT

REPO = str(PROJECT_ROOT)
HERE = f"{REPO}/data/runs/n12"  # git-ignored: raw prices stay in data/
Path(f"{HERE}/data").mkdir(parents=True, exist_ok=True)
con = sqlite3.connect(f"file:{DB_PATH}?mode=ro", uri=True)
out = {}
for season in (2025, 2026):
    h = pd.read_sql("SELECT id, season, game_id, snapshot, requested_at, bookmaker, player_name_raw, player_id, "
                    "market, alternate, line, selection, decimal_price, quote_time FROM hist_quotes "
                    "WHERE sport='nba' AND season=?", con, params=(season,))
    h.to_parquet(f"{HERE}/data/q_{season}.parquet")
    out[season] = {"rows": len(h), "games": int(h.game_id.nunique()), "books": sorted(h.bookmaker.unique()),
                   "alternate_rows": int((h.alternate != 0).sum()), "null_player": int(h.player_id.isna().sum()),
                   "null_line": int(h.line.isna().sum()),
                   "snapshots_per_game_max": int(h.groupby("game_id").snapshot.nunique().max())}
print(json.dumps(out, indent=1))
json.dump(out, open(f"{HERE}/v00_extract.json", "w"), indent=1)
