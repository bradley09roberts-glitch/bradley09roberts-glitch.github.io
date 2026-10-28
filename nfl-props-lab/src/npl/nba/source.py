"""SportsDataverse NBA release assets (github.com/sportsdataverse/sportsdataverse-data).

Data originate from ESPN game feeds; the release repository is MIT-licensed and labels
its data CC BY 4.0 (attribution: SportsDataverse). Seasons are named by their ending
year (2027 = 2026-27). See docs/DATA_AUDIT.md (NBA section) for the audited status.
"""

from __future__ import annotations

import sqlite3
from dataclasses import dataclass
from datetime import timedelta

from .. import rawstore
from ..util import FetchError, http_get, log, parse_iso, utcnow

BASE = "https://github.com/sportsdataverse/sportsdataverse-data/releases/download/"
SOURCE = "sportsdataverse"
FIRST_SEASON = 2016  # 2015-16; 2016-2017 are warm-up history only
CURRENT_SEASON = 2027  # 2026-27


@dataclass(frozen=True)
class Dataset:
    key: str
    path: str
    first_season: int

    def url(self, season: int) -> str:
        return BASE + self.path.format(season=season)


DATASETS = {d.key: d for d in [
    Dataset("nba_player_box", "espn_nba_player_boxscores/player_box_{season}.parquet", 2002),
]}


def fetch(con: sqlite3.Connection, keys: list[str] | None = None,
          seasons: list[int] | None = None, *, max_age_hours: float = 0.5,
          force: bool = False) -> dict:
    """Same rules as the nflverse adapter: cached by age, completed seasons re-checked
    weekly, 404 recorded as absent, every response stored immutably with its hash."""
    seasons = seasons or list(range(FIRST_SEASON, CURRENT_SEASON + 1))
    out = {"new": [], "unchanged": [], "cached": [], "absent": [], "failed": []}
    for key in keys or list(DATASETS):
        ds = DATASETS[key]
        for season in [s for s in seasons if s >= ds.first_season]:
            part, url = str(season), ds.url(season)
            prev = rawstore.latest(con, SOURCE, key, part)
            if prev and not force:
                age = utcnow() - parse_iso(rawstore.last_observed(con, prev["id"]) or
                                           prev["retrieved_at"])
                limit = max_age_hours if season >= CURRENT_SEASON else 24 * 7
                if age < timedelta(hours=limit):
                    out["cached"].append(f"{key}/{part}")
                    continue
            try:
                r = http_get(url, timeout=180)
            except FetchError as e:
                kind = "absent" if e.status == 404 else "failed"
                out[kind].append(f"{key}/{part}")
                rawstore.record_failure(con, source=SOURCE, dataset=key, part=part, url=url,
                                        status=e.status, error=str(e))
                continue
            sf = rawstore.store(con, source=SOURCE, dataset=key, part=part, url=url,
                                content=r.content, filename=url.rsplit("/", 1)[-1],
                                content_type=r.headers.get("Content-Type"),
                                upstream_last_modified=r.headers.get("Last-Modified"))
            (out["new"] if sf.changed else out["unchanged"]).append(f"{key}/{part}")
            log.info("%s %s/%s rows=%s", "NEW" if sf.changed else "same", key, part, sf.n_rows)
    return out
