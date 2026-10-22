"""Team pictures: GET /api/teams/{team_key}/media, and the pictures themselves under
/media/teams/<team_key>/ (downloaded crests and venue photos, and our generated badge.svg).

Only /api/teams/{team_key}/media is defined here; /api/teams and /api/teams/{team_key} live
in footy.api.teams_routes. A path parameter never matches a "/", so neither route can
shadow the other, and this router is included before the others in any case.

Pictures are served from data/media/teams/ by an explicit route, not a static mount: only
`crest.*` and `venue.*` image files can be fetched, never the manifests or anything else.
See docs/research/team-media.md.
"""

import re
from collections.abc import Callable

from fastapi import APIRouter, HTTPException, Response
from fastapi.responses import FileResponse
from sqlalchemy import Engine
from sqlalchemy.orm import Session

from footy.config import Settings
from footy.media import service
from footy.media.badge import badge_svg
from footy.models import Team

TEAM_KEY = re.compile(r"[a-z0-9][a-z0-9-]{0,63}")
PICTURE_FILE = re.compile(r"(crest|venue)\.(png|jpg|gif|webp)")
CACHE = "public, max-age=86400"
# An SVG opened on its own is a document: allow it no scripts, fetches or frames.
SVG_CSP = "default-src 'none'; style-src 'unsafe-inline'"


def make_media_router(engine_fn: Callable[[], Engine], settings: Settings) -> APIRouter:
    r = APIRouter()

    def team_name(team_key: str) -> str:
        if not TEAM_KEY.fullmatch(team_key):
            raise HTTPException(404, "no such team")
        with Session(engine_fn()) as s:
            team = s.get(Team, team_key)
        if team is None:
            raise HTTPException(404, "no such team")
        return team.name

    @r.get("/api/teams/{team_key}/media")
    def team_media(team_key: str) -> dict:
        """Crest (or our generated badge), venue and colours, each with its licence and
        attribution; urls point at /media/teams/."""
        team_name(team_key)
        return service.team_media(team_key, settings)

    @r.get("/media/teams/{team_key}/badge.svg")
    def badge(team_key: str) -> Response:
        name = team_name(team_key)
        facts = service.load_facts().get(team_key) or {}
        hexes = [c["hex"] for c in facts.get("colours") or [] if c.get("hex")]
        return Response(
            badge_svg(team_key, name, hexes),
            media_type="image/svg+xml",
            headers={"Cache-Control": CACHE, "Content-Security-Policy": SVG_CSP},
        )

    @r.get("/media/teams/{team_key}/{filename}")
    def picture(team_key: str, filename: str) -> FileResponse:
        if not TEAM_KEY.fullmatch(team_key) or not PICTURE_FILE.fullmatch(filename):
            raise HTTPException(404, "no such picture")
        root = service.media_dir(settings).resolve()
        path = (root / team_key / filename).resolve()
        if root not in path.parents or not path.is_file():
            raise HTTPException(404, "no such picture")
        return FileResponse(path, headers={"Cache-Control": CACHE})

    return r
