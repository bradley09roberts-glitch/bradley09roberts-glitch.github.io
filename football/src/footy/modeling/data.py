"""Match data for modelling, with the decision-time columns of DECISIONS.md D-012."""

from datetime import date

import numpy as np
import pandas as pd
from sqlalchemy import Engine, text

from footy.ingest.fd_parse import collection_deadline, model_cutoff

SECOND_TIER = {"E0": "E1", "SP1": "SP2", "I1": "I2", "D1": "D2", "F1": "F2"}
MATCH_COLUMNS = (
    "id, division, season, match_date, kickoff_utc, home_team, away_team, home_goals, "
    "away_goals, result, ht_home_goals, ht_away_goals, home_shots, away_shots, home_sot, "
    "away_sot, home_corners, away_corners, home_red, away_red, model_exclusion"
)


def load_matches(engine: Engine, divisions: list[str] | None = None) -> pd.DataFrame:
    """All imported matches (or those of `divisions`), oldest first, with:
    - `date`: the match's UK local date (datetime64, midnight);
    - `deadline_utc`: when football-data's pre-closing prices were collected;
    - `cutoff_utc` / `cutoff_date`: the latest information time for an early-edition
      prediction; only matches dated strictly before `cutoff_date` may be used.
    """
    sql = f"SELECT {MATCH_COLUMNS} FROM matches"
    params = {}
    if divisions:
        names = ", ".join(f":d{i}" for i in range(len(divisions)))
        sql += f" WHERE division IN ({names})"
        params = {f"d{i}": d for i, d in enumerate(divisions)}
    with engine.connect() as conn:
        df = pd.read_sql(text(sql), conn, params=params)
    if df.empty:
        return df
    df["date"] = pd.to_datetime(df["match_date"])
    df["kickoff_utc"] = pd.to_datetime(df["kickoff_utc"], utc=True)
    by_day = {d: (collection_deadline(d), model_cutoff(d)) for d in df["date"].dt.date.unique()}
    df["deadline_utc"] = pd.to_datetime([by_day[d][0] for d in df["date"].dt.date], utc=True)
    df["cutoff_utc"] = pd.to_datetime([by_day[d][1] for d in df["date"].dt.date], utc=True)
    df["cutoff_date"] = df["cutoff_utc"].dt.tz_convert("Europe/London").dt.tz_localize(None)
    df["cutoff_date"] = df["cutoff_date"].dt.normalize()
    df["total_goals"] = df["home_goals"] + df["away_goals"]
    df["outcome"] = np.where(
        df.home_goals > df.away_goals, 0, np.where(df.home_goals == df.away_goals, 1, 2)
    )
    return df.sort_values(["date", "id"]).reset_index(drop=True)


def history_before(df: pd.DataFrame, cutoff_date: pd.Timestamp) -> pd.DataFrame:
    """Matches final before an early-edition decision: dated strictly before the cutoff day,
    and not excluded from models (D-018)."""
    return df[(df["date"] < cutoff_date) & df["model_exclusion"].isna()]


def season_of(day: date) -> str:
    start = day.year if day.month >= 7 else day.year - 1
    return f"{start % 100:02d}{(start + 1) % 100:02d}"
