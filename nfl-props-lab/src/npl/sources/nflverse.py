"""nflverse adapter: downloads release assets from github.com/nflverse/nflverse-data.

URL patterns match nflreadpy 0.1.5 (`nflreadpy/downloader.py` and the `load_*`
modules). Each dataset keeps its own upstream-source and licence note; nflverse
packaging a dataset does not by itself grant rights to the upstream data. See
docs/DATA_AUDIT.md for the audited status of each one.
"""

from __future__ import annotations

import sqlite3
from dataclasses import dataclass
from datetime import timedelta

from .. import rawstore
from ..config import CURRENT_SEASON, FIRST_SEASON
from ..util import FetchError, http_get, log, parse_iso, utcnow

BASE = "https://github.com/nflverse/nflverse-data/releases/download/"
SOURCE = "nflverse"


@dataclass(frozen=True)
class Dataset:
    key: str
    path: str  # release tag/file, with {season} when partitioned by season
    first_season: int | None  # None = single file covering all seasons
    upstream: str
    licence: str
    doc: str
    core: bool = True  # needed by the core pipeline

    def url(self, season: int | None = None) -> str:
        return BASE + (self.path.format(season=season) if season is not None else self.path)

    def parts(self, seasons: list[int]) -> list[int | None]:
        if self.first_season is None:
            return [None]
        return [s for s in seasons if s >= self.first_season]


DOCS = "https://nflreadr.nflverse.com/reference/"
CC_BY = "CC-BY 4.0 (nflverse-data); upstream terms apply"

DATASETS: dict[str, Dataset] = {
    d.key: d
    for d in [
        Dataset("schedules", "schedules/games.parquet", None,
                "nflverse/nfldata games (Lee Sharpe); includes betting-line columns",
                CC_BY, DOCS + "load_schedules.html"),
        Dataset("players", "players/players.parquet", None,
                "nflverse player directory (NFL GSIS ids + cross-ids)", CC_BY,
                DOCS + "load_players.html"),
        Dataset("teams", "teams/teams_colors_logos.parquet", None, "nflverse", CC_BY,
                DOCS + "load_teams.html"),
        Dataset("pbp", "pbp/play_by_play_{season}.parquet", 1999,
                "NFL game feeds via nflfastR", CC_BY, DOCS + "load_pbp.html"),
        Dataset("stats_player_week", "stats_player/stats_player_week_{season}.parquet", 1999,
                "derived from nflfastR play-by-play", CC_BY, DOCS + "load_player_stats.html"),
        Dataset("stats_team_week", "stats_team/stats_team_week_{season}.parquet", 1999,
                "derived from nflfastR play-by-play", CC_BY, DOCS + "load_team_stats.html"),
        Dataset("rosters_weekly", "weekly_rosters/roster_weekly_{season}.parquet", 2002,
                "NFL roster/game-status feeds", CC_BY, DOCS + "load_rosters_weekly.html"),
        Dataset("snap_counts", "snap_counts/snap_counts_{season}.parquet", 2012,
                "Pro Football Reference", CC_BY + "; PFR terms", DOCS + "load_snap_counts.html"),
        Dataset("injuries", "injuries/injuries_{season}.parquet", 2009,
                "NFL injury reports", CC_BY, DOCS + "load_injuries.html"),
        Dataset("depth_charts", "depth_charts/depth_charts_{season}.parquet", 2001,
                "NFL depth charts", CC_BY, DOCS + "load_depth_charts.html", core=False),
        Dataset("participation", "pbp_participation/pbp_participation_{season}.parquet", 2016,
                "NGS / FTN participation", CC_BY, DOCS + "load_participation.html", core=False),
        Dataset("ftn_charting", "ftn_charting/ftn_charting_{season}.parquet", 2022,
                "FTN Data", "CC-BY-SA 4.0 (FTN)", DOCS + "load_ftn_charting.html", core=False),
        Dataset("ngs_passing", "nextgen_stats/ngs_passing.parquet", None, "NFL Next Gen Stats",
                CC_BY, DOCS + "load_nextgen_stats.html", core=False),
        Dataset("ngs_receiving", "nextgen_stats/ngs_receiving.parquet", None,
                "NFL Next Gen Stats", CC_BY, DOCS + "load_nextgen_stats.html", core=False),
        Dataset("ngs_rushing", "nextgen_stats/ngs_rushing.parquet", None, "NFL Next Gen Stats",
                CC_BY, DOCS + "load_nextgen_stats.html", core=False),
        Dataset("pfr_adv_rec", "pfr_advstats/advstats_week_rec_{season}.parquet", 2018,
                "Pro Football Reference", CC_BY + "; PFR terms", DOCS + "load_pfr_advstats.html",
                core=False),
    ]
}


def default_seasons() -> list[int]:
    return list(range(FIRST_SEASON, CURRENT_SEASON + 1))


def fetch(
    con: sqlite3.Connection,
    keys: list[str] | None = None,
    seasons: list[int] | None = None,
    *,
    max_age_hours: float = 6.0,
    force: bool = False,
    include_optional: bool = True,
) -> dict:
    """Download datasets into the raw store. Returns a per-file outcome summary.

    A file retrieved within `max_age_hours` is not re-downloaded (cache) unless forced.
    A 404 for a season is recorded as 'absent' (the coverage matrix reports it), not
    as a failure of the job.
    """
    seasons = seasons or default_seasons()
    keys = keys or [k for k, d in DATASETS.items() if d.core or include_optional]
    out = {"new": [], "unchanged": [], "cached": [], "absent": [], "failed": []}
    for key in keys:
        ds = DATASETS[key]
        for season in ds.parts(seasons):
            part = str(season) if season is not None else "all"
            url = ds.url(season)
            prev = rawstore.latest(con, SOURCE, key, part)
            if prev and not force:
                age = utcnow() - parse_iso(rawstore.last_observed(con, prev["id"]) or
                                           prev["retrieved_at"])
                # Completed seasons rarely change, but stat corrections do happen: we
                # still re-check them, just less often.
                limit = max_age_hours if (season is None or season >= CURRENT_SEASON) else 24 * 7
                if age < timedelta(hours=limit):
                    out["cached"].append(f"{key}/{part}")
                    continue
            try:
                r = http_get(url, timeout=180)
            except FetchError as e:
                if e.status == 404:
                    out["absent"].append(f"{key}/{part}")
                    rawstore.record_failure(con, source=SOURCE, dataset=key, part=part, url=url,
                                            status=404, error="absent (404)")
                    continue
                out["failed"].append(f"{key}/{part}: {e}")
                rawstore.record_failure(con, source=SOURCE, dataset=key, part=part, url=url,
                                        status=e.status, error=str(e))
                continue
            fname = url.rsplit("/", 1)[-1]
            sf = rawstore.store(
                con, source=SOURCE, dataset=key, part=part, url=url, content=r.content,
                filename=fname, content_type=r.headers.get("Content-Type"),
                upstream_last_modified=r.headers.get("Last-Modified"),
            )
            (out["new"] if sf.changed else out["unchanged"]).append(f"{key}/{part}")
            log.info("%s %s/%s rows=%s", "NEW" if sf.changed else "same", key, part, sf.n_rows)
    return out
