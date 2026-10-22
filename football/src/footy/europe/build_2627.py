"""Build the 2026/27 UEFA CSVs from the raw store (no fetching, no database writes).

Outputs, under `<data_dir>/europe/`:
- `uefa_2627.csv`: every match (played, scheduled, postponed, cancelled or awarded) from the
  Wikipedia pages, normalised;
- `uefa_2627_league_phase_teams.csv`: the 36 teams of each league phase, by pot, with country.

UEFA.com is not used (its terms, section 6.2; DECISIONS.md D-023): its match pages are neither
fetched nor read. The one fact the build used to take from them (Union Saint-Gilloise v Hapoel
Be'er Sheva played in Düsseldorf, so neutral) now comes from German Wikipedia through
`venue_overrides.csv` (`footy.europe.wikipedia_2627.apply_venue_overrides`).
"""

from collections.abc import Iterable
from dataclasses import dataclass
from pathlib import Path

from sqlalchemy import Engine
from sqlalchemy.orm import Session

from footy.europe.wiki_fetch import WikiClient, WikiFetchResult, fetch_wiki_page
from footy.europe.wikipedia_2627 import (
    CSV_COLUMNS,
    PAGES,
    TEAM_COLUMNS,
    BuildResult,
    PageSpec,
    ParseIssue,
    build_uefa_2627,
    write_csv,
)


def fetch_wikipedia(
    engine: Engine,
    raw_dir: Path,
    *,
    client: WikiClient | None = None,
    specs: Iterable[PageSpec] = PAGES,
    job_run_id: int | None = None,
) -> list[WikiFetchResult]:
    """Fetch every page's current revision into the raw store (one transaction per page).

    Stops at the first page the server refuses (HTTP 403 or 429), or at once if `client` was
    already refused: asking again straight away only prolongs a block. Pages not asked for
    are returned as `skipped` (not requested, so not in `fetch_log`)."""
    client = client or WikiClient()
    results: list[WikiFetchResult] = []
    for spec in specs:
        if client.refused is not None:
            results.append(
                WikiFetchResult(
                    spec.page.logical_key,
                    spec.page.title,
                    "skipped",
                    None,
                    error=f"not requested: {client.refused} refused this client",
                )
            )
            continue
        with Session(engine) as s:
            results.append(fetch_wiki_page(s, client, raw_dir, spec.page, job_run_id=job_run_id))
            s.commit()
    return results


def fetch_uefa_pages(*_args, **_kwargs) -> list[WikiFetchResult]:
    """DISABLED. UEFA.com's terms (section 6.2) forbid scripted collection and using its
    content to build models (DECISIONS.md D-023), so this project must not fetch it."""
    raise RuntimeError("UEFA.com may not be fetched by script (terms 6.2; DECISIONS.md D-023)")


@dataclass
class Build2627:
    wikipedia: BuildResult
    paths: dict[str, Path]

    @property
    def errors(self) -> list[ParseIssue]:
        """Issues that left a row or a value out. While there are any, the loader deletes no
        stored row (a row the build dropped is not a row the source dropped)."""
        return [i for i in self.wikipedia.issues if i.severity == "error"]


def build_all(session: Session, raw_dir: Path, data_dir: Path) -> Build2627:
    out = data_dir / "europe"
    paths = {
        "matches": out / "uefa_2627.csv",
        "teams": out / "uefa_2627_league_phase_teams.csv",
    }
    result = build_uefa_2627(session, raw_dir)
    write_csv(paths["matches"], CSV_COLUMNS, (r.csv_row() for r in result.rows))
    write_csv(paths["teams"], TEAM_COLUMNS, (t.csv_row() for t in result.teams))
    return Build2627(result, paths)
