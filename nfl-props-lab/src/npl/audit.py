"""Coverage audit: what we actually hold, per dataset x season x field.

Writes reports/coverage/coverage_matrix.csv (one row per dataset/season) and
reports/coverage/field_missingness.csv (one row per dataset/season/field), plus a
Markdown summary. Every number comes from the stored raw files, never from docs.
"""

from __future__ import annotations

import json
import sqlite3

import pandas as pd

from . import db
from .config import DATA_HOME, REPORTS_DIR
from .sources.nflverse import DATASETS, SOURCE

# Fields that matter to the core models, per dataset (missingness is reported for these).
KEY_FIELDS = {
    "pbp": ["play_type", "posteam", "passer_player_id", "receiver_player_id", "rusher_player_id",
            "air_yards", "yards_after_catch", "qb_scramble", "qb_kneel", "sack", "xpass",
            "spread_line", "total_line", "vegas_wp", "weather", "temp", "wind"],
    "stats_player_week": ["player_id", "game_id", "attempts", "completions", "passing_yards",
                          "carries", "rushing_yards", "targets", "receptions", "receiving_yards",
                          "receiving_air_yards", "target_share"],
    "rosters_weekly": ["gsis_id", "pfr_id", "status", "status_description_abbr", "position",
                       "depth_chart_position", "birth_date", "entry_year", "years_exp"],
    "snap_counts": ["game_id", "pfr_player_id", "offense_snaps", "offense_pct", "st_snaps"],
    "injuries": ["gsis_id", "report_status", "practice_status", "report_primary_injury"],
    "depth_charts": ["gsis_id", "depth_team", "pos_abb", "dt", "week"],
    "participation": ["offense_players", "defense_players", "offense_formation", "route",
                      "was_pressure", "time_to_throw"],
    "ftn_charting": ["nflverse_game_id", "is_play_action", "is_motion", "n_offense_backfield",
                     "is_qb_out_of_pocket", "is_drop"],
    "schedules": ["gameday", "gametime", "away_score", "home_score", "roof", "surface", "temp",
                  "wind", "spread_line", "total_line", "away_moneyline", "away_qb_id", "referee"],
    "players": ["gsis_id", "pfr_id", "espn_id", "birth_date", "position", "latest_team"],
    "ngs_receiving": ["player_gsis_id", "avg_separation", "avg_cushion", "avg_yac_above_expectation"],
    "ngs_passing": ["player_gsis_id", "avg_time_to_throw", "completion_percentage_above_expectation"],
    "ngs_rushing": ["player_gsis_id", "efficiency", "rush_yards_over_expected"],
    "pfr_adv_rec": ["pfr_player_id", "receiving_drop", "receiving_broken_tackles", "receiving_rat"],
    "stats_team_week": ["team", "game_id", "attempts", "carries"],
    "teams": ["team_abbr", "team_division"],
}


def _season_col(df: pd.DataFrame) -> str | None:
    for c in ("season", "nflverse_season"):
        if c in df.columns:
            return c
    return None


def _week_col(df: pd.DataFrame) -> str | None:
    for c in ("week", "nflverse_week"):
        if c in df.columns:
            return c
    return None


def run(con: sqlite3.Connection | None = None) -> dict:
    con = con or db.connect()
    rows, miss = [], []
    files = con.execute(
        "SELECT r.* FROM raw_files r JOIN (SELECT dataset, part, MAX(retrieved_at) t FROM raw_files"
        " WHERE source=? AND is_synthetic=0 GROUP BY dataset, part) m"
        " ON r.dataset=m.dataset AND r.part=m.part AND r.retrieved_at=m.t WHERE r.source=?",
        (SOURCE, SOURCE),
    ).fetchall()
    for f in files:
        ds = DATASETS.get(f["dataset"])
        df = pd.read_parquet(DATA_HOME / f["path"])
        scol, wcol = _season_col(df), _week_col(df)
        groups = df.groupby(scol) if scol else [(f["part"], df)]
        for season, g in groups:
            if f["part"] != "all" and str(season) != f["part"]:
                # season-partitioned file containing another season: report it separately
                pass
            rec = {
                "dataset": f["dataset"], "season": int(season) if str(season).isdigit() else season,
                "file_part": f["part"], "rows": len(g),
                "min_week": int(g[wcol].min()) if wcol and g[wcol].notna().any() else None,
                "max_week": int(g[wcol].max()) if wcol and g[wcol].notna().any() else None,
                "game_types": json.dumps(g["game_type"].value_counts().to_dict())
                if "game_type" in g else (json.dumps(g["season_type"].value_counts().to_dict())
                                          if "season_type" in g else None),
                "n_games": int(g["game_id"].nunique()) if "game_id" in g else None,
                "retrieved_at": f["retrieved_at"], "sha256": f["sha256"][:16],
                "upstream_last_modified": f["upstream_last_modified"], "url": f["url"],
                "upstream": ds.upstream if ds else None, "licence": ds.licence if ds else None,
            }
            rows.append(rec)
            for fld in KEY_FIELDS.get(f["dataset"], []):
                present = fld in g.columns
                miss.append({
                    "dataset": f["dataset"], "season": rec["season"], "field": fld,
                    "present": present,
                    "non_null_rows": int(g[fld].notna().sum()) if present else 0,
                    "rows": len(g),
                    "missing_pct": round(100 * (1 - g[fld].notna().mean()), 2) if present and len(g) else 100.0,
                })
    cov = pd.DataFrame(rows).sort_values(["dataset", "season"])
    fm = pd.DataFrame(miss).sort_values(["dataset", "field", "season"])
    absent = pd.DataFrame([dict(r) for r in con.execute(
        "SELECT dataset, part, MAX(attempted_at) attempted_at, status, error FROM fetch_failures"
        " WHERE source=? GROUP BY dataset, part", (SOURCE,)).fetchall()])
    if len(absent):  # a part that was later retrieved successfully is not absent
        held = {(f["dataset"], f["part"]) for f in files}
        absent = absent[[(d, p) not in held for d, p in zip(absent.dataset, absent.part, strict=True)]]
    out = REPORTS_DIR / "coverage"
    out.mkdir(parents=True, exist_ok=True)
    cov.to_csv(out / "coverage_matrix.csv", index=False)
    fm.to_csv(out / "field_missingness.csv", index=False)
    if len(absent):
        absent.to_csv(out / "absent_files.csv", index=False)
    return {"datasets": cov["dataset"].nunique(), "rows": len(cov), "fields": len(fm),
            "absent": len(absent)}
