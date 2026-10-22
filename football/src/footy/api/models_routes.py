"""Every model's predictions: genuine pre-match records side by side, the live leaderboard, and
the backtest explorer (docs/model-comparison.md).

Two kinds of prediction, never mixed in one response and labelled in every one (`kind`):

- `genuine_prematch` (footy.model_compare): records saved before kickoff and never changed;
- `backtest` (footy.backtest_explorer): walk-forward predictions computed after the fact from
  the saved ledgers, on matches each model had not seen.

Read-only, like footy.api.routes. Timestamps are ISO 8601 in UTC with an explicit offset
(D-005). Bookmakers' prices are never read or sent (D-013, D-024), and no purely
market-derived forecast is shown.
"""

from collections.abc import Callable
from datetime import UTC, datetime

from fastapi import APIRouter, HTTPException, Query
from sqlalchemy import Engine, select
from sqlalchemy.orm import Session

from footy import backtest_explorer as bx
from footy import model_catalogue as cat
from footy import model_compare
from footy.api.routes import RecordsOfRecord, model_periods
from footy.config import Settings
from footy.media.service import media_summaries
from footy.models import Team


def make_models_router(engine_fn: Callable[[], Engine], settings: Settings) -> APIRouter:
    """Mounted under /api by footy.api.routes.make_router."""
    r = APIRouter()

    def names(s: Session) -> dict[str, str]:
        return {t.team_key: t.name for t in s.scalars(select(Team))}

    def shorts(s: Session) -> dict[str, str]:
        from footy.teams import teams_index  # here: footy.teams imports footy.api.routes

        return teams_index(s).shorts

    @r.get("/fixtures/{fixture_ref}/models")
    def fixture_models(fixture_ref: str) -> dict:
        """Every model's genuine pre-match record for one fixture (its first edition, later
        editions listed), the record of record first; once settled, each market's pick,
        outcome and whether it was right, the RPS and the log losses."""
        now = datetime.now(UTC)
        periods = model_periods()
        with Session(engine_fn()) as s:
            team_names = names(s)
            ror = RecordsOfRecord(s, periods)
            out = model_compare.fixture_models(
                s,
                fixture_ref,
                ror,
                periods,
                cat.load_shadow_models(),
                team_names,
                {},
                now,
            )
            if out is None:
                raise HTTPException(404, "no genuine pre-match record for this fixture")
            teams = {k: team_names.get(k, k) for k in (out["home_team"], out["away_team"])}
            media = media_summaries(teams, settings, shorts(s))
        out["home_media"] = media[out["home_team"]]
        out["away_media"] = media[out["away_team"]]
        return out

    @r.get("/model-leaderboard")
    def model_leaderboard(group: str = Query("domestic", pattern="^(domestic|uefa)$")) -> dict:
        """Every model with settled genuine pre-match records in the group: settled count,
        results right with a 95% Wilson interval, mean RPS, mean log loss for O/U 2.5 and
        BTTS, and a paired comparison with each match's record of record. Models still waiting
        for a result are listed under `waiting`."""
        now = datetime.now(UTC)
        periods = model_periods()
        with Session(engine_fn()) as s:
            ror = RecordsOfRecord(s, periods)
            return model_compare.leaderboard(s, group, ror, periods, cat.load_shadow_models(), now)

    @r.get("/backtest-models")
    def backtest_models() -> dict:
        """The backtest explorer's curated models: label, role, family, basis, description and
        the splits each was run on, with what each group's ledgers cover."""
        return bx.models_body(
            settings.data_dir, model_periods(), datetime.now(UTC), cat.load_shadow_models()
        )

    @r.get("/backtest-matches")
    def backtest_matches(
        division: str | None = Query(None, max_length=8),
        season: str | None = Query(None, max_length=4),
        team: str | None = Query(None, max_length=64),
        models: str | None = Query(None, max_length=2000),
        group: str | None = Query(None, pattern="^(domestic|uefa)$"),
        limit: int = Query(200, ge=1, le=bx.MAX_LIMIT),
        offset: int = Query(0, ge=0),
    ) -> dict:
        """Backtest predictions from the saved walk-forward ledgers, one row per match: per
        curated model its home/draw/away, over 2.5 and both-teams-to-score probabilities,
        whether its match result pick was right and its RPS (`columns`), with each model's
        summary over every match the filters select. Matches in date order. Never a genuine
        pre-match record: `kind` is "backtest"."""
        with Session(engine_fn()) as s:
            team_names = names(s)
            letters = shorts(s)

        def media(named: dict[str, str]) -> dict[str, dict]:
            return media_summaries(named, settings, letters)

        try:
            return bx.matches_body(
                settings.data_dir,
                model_periods(),
                datetime.now(UTC),
                team_names,
                media,
                group=group,
                division=division or None,
                season=season or None,
                team=team or None,
                models=models or None,
                limit=limit,
                offset=offset,
                shadow=cat.load_shadow_models(),
            )
        except bx.BadRequest as e:
            raise HTTPException(400, str(e)) from None

    return r
