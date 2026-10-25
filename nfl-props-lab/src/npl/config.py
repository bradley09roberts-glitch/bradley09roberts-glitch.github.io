"""Paths, season ranges and project-wide constants.

Everything that changes a published number is either here (and recorded in run
manifests) or passed explicitly on the command line.
"""

from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path
from zoneinfo import ZoneInfo

PROJECT_ROOT = Path(__file__).resolve().parents[2]
DATA_HOME = Path(os.environ.get("NPL_HOME") or PROJECT_ROOT / "data")

RAW_DIR = DATA_HOME / "raw"
LAKE_DIR = DATA_HOME / "lake"  # canonical analytical tables (Parquet)
FEATURE_DIR = DATA_HOME / "features"
RUNS_DIR = DATA_HOME / "runs"  # backtest runs (ledgers + manifests)
EDITIONS_DIR = PROJECT_ROOT / "records" / "editions"  # published editions (committed to git)
REPORTS_DIR = PROJECT_ROOT / "reports"  # committed summaries, coverage, ledgers
DB_PATH = DATA_HOME / "npl.sqlite3"

LONDON = ZoneInfo("Europe/London")
NEW_YORK = ZoneInfo("America/New_York")
UTC = ZoneInfo("UTC")

# Seasons ingested. 2012 is the first season with nflverse snap counts; 2012-2013 are
# used only as warm-up history for features, never as evaluation.
FIRST_SEASON = 2012
CURRENT_SEASON = 2026

USER_AGENT = "nfl-props-lab/0.1 (research; contact via repository owner)"

# Offensive positions eligible for the core markets.
SKILL_POSITIONS = ("QB", "RB", "FB", "WR", "TE")


@dataclass(frozen=True)
class Market:
    key: str
    label: str
    stat: str  # column in the canonical player-game table
    positions: tuple[str, ...]
    kind: str  # "count" or "yards"
    phase: str = "core"


CORE_MARKETS: tuple[Market, ...] = (
    Market("pass_yds", "Passing yards", "passing_yards", ("QB",), "yards"),
    Market("pass_att", "Pass attempts", "attempts", ("QB",), "count"),
    Market("pass_cmp", "Completions", "completions", ("QB",), "count"),
    Market("rush_yds", "Rushing yards", "rushing_yards", ("QB", "RB", "FB", "WR"), "yards"),
    Market("rush_att", "Rushing attempts", "carries", ("QB", "RB", "FB", "WR"), "count"),
    Market("receptions", "Receptions", "receptions", ("WR", "TE", "RB", "FB"), "count"),
    Market("rec_yds", "Receiving yards", "receiving_yards", ("WR", "TE", "RB", "FB"), "yards"),
)
MARKETS = {m.key: m for m in CORE_MARKETS}


def ensure_dirs() -> None:
    for d in (RAW_DIR, LAKE_DIR, FEATURE_DIR, RUNS_DIR, EDITIONS_DIR, REPORTS_DIR):
        d.mkdir(parents=True, exist_ok=True)
