"""Team pages: every club's matches across competitions, and a profile built from them
(footy.teams, docs/teams.md).

Read-only, like footy.api.routes: everything comes from the imported matches and the saved
prediction records. Timestamps are ISO 8601 in UTC with an explicit offset (D-005); dates are
plain `YYYY-MM-DD`. Bookmakers' prices are never read or sent (D-013, D-024).
"""

from collections.abc import Callable
from datetime import UTC, datetime
from typing import Annotated

from fastapi import APIRouter, HTTPException, Query
from sqlalchemy import Engine
from sqlalchemy.orm import Session

from footy import teams as team_stats
from footy.api.routes import iso_utc, model_periods

TYPE_PATTERN = "^(" + "|".join(team_stats.COMPETITION_TYPES) + ")$"


def make_teams_router(engine_fn: Callable[[], Engine]) -> APIRouter:
    """Mounted under /api by footy.api.routes.make_router."""
    r = APIRouter()

    @r.get("/teams")
    def teams(
        competition: Annotated[list[str] | None, Query()] = None,
        q: str | None = Query(None, max_length=100),
    ) -> dict:
        """Every team with a stored match or fixture, by name: country, current division,
        competitions this season (each league's latest season held; for UEFA, the latest UEFA
        season), last-5 form (oldest first), counted matches and the descriptive strength
        rating. `competition` (repeatable or comma-separated) keeps teams in any of those
        competitions this season; `q` searches names and keys."""
        codes = [c.strip() for v in competition or [] for c in v.split(",") if c.strip()]
        with Session(engine_fn()) as s:
            idx = team_stats.teams_index(s)  # once: the list and its date from the same build
            rows = team_stats.teams_list(s, codes, q, idx)
        return {
            "generated_at_utc": iso_utc(datetime.now(UTC), "seconds"),
            "latest_match_held": idx.latest_date,
            "filters": {"competition": codes, "q": q},
            "count": len(rows),
            "teams": rows,
        }

    @r.get("/teams/{team_key}")
    def team(team_key: str) -> dict:
        """One team's profile: identity, summaries by competition type, competition, season
        and venue, form, streaks, upcoming fixtures with our published record, and the
        descriptive strength series."""
        with Session(engine_fn()) as s:
            out = team_stats.team_profile(s, team_key, periods=model_periods())
        if out is None:
            raise HTTPException(404, "no such team")
        return out

    @r.get("/teams/{team_key}/matches")
    def team_matches(
        team_key: str,
        type_: str | None = Query(None, alias="type", pattern=TYPE_PATTERN),
        competition: str | None = None,
        season: str | None = None,
        limit: int = Query(100, ge=1, le=2000),
        offset: int = Query(0, ge=0),
    ) -> dict:
        """The team's match log, newest first: every stored match from its side, with every
        statistic the source has (null otherwise), flagged exclusions, the descriptive
        strength before and after, and our pre-match record of record where there is one."""
        with Session(engine_fn()) as s:
            rows = team_stats.team_matches(
                s,
                team_key,
                periods=model_periods(),
                type_=type_,
                competition=competition,
                season=season,
            )
        if rows is None:
            raise HTTPException(404, "no such team")
        return {
            "team": team_key,
            "filters": {"type": type_, "competition": competition, "season": season},
            "total": len(rows),
            "offset": offset,
            "limit": limit,
            "notes": team_stats.NOTES,
            "matches": rows[offset : offset + limit],
        }

    return r
