"""Build the 2026/27 UEFA CSVs from the raw store (no fetching, no database writes).

Outputs, under `<data_dir>/europe/`:
- `uefa_2627.csv`: every match (played or scheduled) from the Wikipedia pages, normalised;
- `uefa_2627_league_phase_teams.csv`: the 36 teams of each league phase, by pot, with country;
- `uefa_2627_crosscheck.csv`: each row that has a stored UEFA.com match page, side by side
  with UEFA's kick-off, team labels and venue country.
"""

from collections.abc import Iterable
from dataclasses import dataclass
from pathlib import Path

from sqlalchemy import Engine
from sqlalchemy.orm import Session

from footy.europe import uefa_crosscheck
from footy.europe.uefa_crosscheck import CrossCheck, apply_uefa_venues, crosscheck_rows
from footy.europe.wiki_fetch import WikiClient, WikiFetchResult, fetch_and_store, fetch_wiki_page
from footy.europe.wikipedia_2627 import (
    CSV_COLUMNS,
    PAGES,
    TEAM_COLUMNS,
    BuildResult,
    EuroRow,
    PageSpec,
    build_uefa_2627,
    write_csv,
)
from footy.ingest.raw_store import latest_revision


def fetch_wikipedia(
    engine: Engine,
    raw_dir: Path,
    *,
    client: WikiClient | None = None,
    specs: Iterable[PageSpec] = PAGES,
) -> list[WikiFetchResult]:
    """Fetch every page's current revision into the raw store (one transaction per page)."""
    client = client or WikiClient()
    results = []
    for spec in specs:
        with Session(engine) as s:
            results.append(fetch_wiki_page(s, client, raw_dir, spec.page))
            s.commit()
    return results


def fetch_uefa_pages(
    engine: Engine,
    raw_dir: Path,
    rows: Iterable[EuroRow],
    *,
    client: WikiClient | None = None,
    refetch: bool = False,
) -> list[WikiFetchResult]:
    """Fetch the UEFA.com match page of each row (skipping pages already stored, unless
    `refetch`), for the second-source check."""
    client = client or WikiClient(min_interval=2.5)
    results = []
    for row in rows:
        if not row.uefa_match_id or row.competition not in uefa_crosscheck.COMPETITION_PATHS:
            continue
        key = uefa_crosscheck.logical_key(row.competition, row.uefa_match_id)
        with Session(engine) as s:
            if not refetch and latest_revision(s, uefa_crosscheck.SOURCE, key) is not None:
                continue
            results.append(
                fetch_and_store(
                    s,
                    client,
                    raw_dir,
                    source=uefa_crosscheck.SOURCE,
                    logical_key=key,
                    url=uefa_crosscheck.match_url(row.competition, row.uefa_match_id),
                    suffix=".html",
                )
            )
            s.commit()
    return results


@dataclass
class Build2627:
    wikipedia: BuildResult
    checks: list[CrossCheck]
    neutral_from_uefa: int
    paths: dict[str, Path]


def build_all(session: Session, raw_dir: Path, data_dir: Path) -> Build2627:
    out = data_dir / "europe"
    paths = {
        "matches": out / "uefa_2627.csv",
        "teams": out / "uefa_2627_league_phase_teams.csv",
        "crosscheck": out / "uefa_2627_crosscheck.csv",
    }
    result = build_uefa_2627(session, raw_dir)
    checks = crosscheck_rows(session, raw_dir, result.rows, out_csv=paths["crosscheck"])
    changed = apply_uefa_venues(checks)
    write_csv(paths["matches"], CSV_COLUMNS, (r.csv_row() for r in result.rows))
    write_csv(paths["teams"], TEAM_COLUMNS, (t.csv_row() for t in result.teams))
    return Build2627(result, checks, changed, paths)
