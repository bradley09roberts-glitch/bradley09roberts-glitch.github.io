"""HTTP API and static web app. Read-only views over saved editions, lake tables and
backtest reports; no model fitting or simulation runs in a request handler."""

from __future__ import annotations

import csv
import io
import json

from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.gzip import GZipMiddleware
from fastapi.responses import FileResponse, JSONResponse, Response
from fastapi.staticfiles import StaticFiles

from . import appdata
from .config import PROJECT_ROOT, REPORTS_DIR

WEB = PROJECT_ROOT / "web"
GZIP_MIN_BYTES = 1000  # smaller responses are sent as they are
# the NBA props body of the 2 Nov 2026 final: 14.2 MB, 0.94 MB at level 6 in 0.12 s (level 9:
# 0.92 MB in 0.18 s)
GZIP_LEVEL = 6
app = FastAPI(title="NFL Props Lab", docs_url="/api/docs", openapi_url="/api/openapi.json")
# compressed for clients that accept gzip (every browser): the JSON payloads are large and
# repetitive (each NBA card repeats its factors' labels and tested lines)
app.add_middleware(GZipMiddleware, minimum_size=GZIP_MIN_BYTES, compresslevel=GZIP_LEVEL)


def _j(obj) -> JSONResponse:
    return JSONResponse(appdata._clean(obj))


@app.get("/api/status")
def status():
    idx = appdata.editions_index()
    return _j({"editions": [{k: v for k, v in e.items() if k != "games"} for e in idx[:10]],
               "latest": idx[0]["edition_id"] if idx else None, **appdata.data_status()})


@app.get("/api/slate")
def slate(season: int | None = None, week: int | None = None):
    return _j(appdata.slate(season, week))


@app.get("/api/props")
def props(season: int | None = None, week: int | None = None, model: str | None = None):
    return _j(appdata.props(season, week, model))


@app.get("/api/manual-line")
def manual_line(edition_id: str, game_id: str, player_id: str, market: str,
                line: float = Query(...), model: str = "J1"):
    r = appdata.manual_line(edition_id, game_id, player_id, market, model, line)
    if "error" in r:
        raise HTTPException(422, r["error"])
    return _j(r)


@app.get("/api/player/{player_id}")
def player(player_id: str, season: int | None = None):
    r = appdata.player(player_id, season)
    if "error" in r:
        raise HTTPException(404, r["error"])
    return _j(r)


@app.get("/api/game/{game_id}")
def game(game_id: str):
    r = appdata.game(game_id)
    if "error" in r:
        raise HTTPException(404, r["error"])
    return _j(r)


@app.get("/api/scenario/{game_id}")
def scenario(game_id: str, unavailable: str = ""):
    ids = [x for x in unavailable.split(",") if x]
    r = appdata.scenario(game_id, ids)
    if "error" in r:
        raise HTTPException(422, r["error"])
    return _j(r)


@app.get("/api/defense")
def defense(season: int | None = None, week: int | None = None):
    r = appdata.defense(season, week)
    if "error" in r:
        raise HTTPException(404, r["error"])
    return _j(r)


@app.get("/api/nba/slate")
def nba_slate():
    from .nba import appdata as nba

    d = nba.slate()
    if "error" in d:
        raise HTTPException(404, d["error"])
    return _j(nba.clean(d))


@app.get("/api/nba/props")
def nba_props():
    from .nba import appdata as nba

    d = nba.props()
    if "error" in d:
        raise HTTPException(404, d["error"])
    return _j(nba.clean(d))


@app.get("/api/nba/parlay")
def nba_parlay(legs: str, price: float | None = None):
    """Combined margin-free chance of some NBA card legs, assuming independence, with same-game
    and same-player flags (npl.parlay): legs = player_id:market:side,... ; price = an offered
    decimal price. Research arithmetic only."""
    from .nba import appdata as nba

    d = nba.parlay(legs, price)
    if "error" in d:
        raise HTTPException(422, d["error"])
    return _j(nba.clean(d))


@app.get("/api/nba/player/{player_id}")
def nba_player(player_id: str):
    from .nba import appdata as nba

    d = nba.player(player_id)
    if "error" in d:
        raise HTTPException(404, d["error"])
    return _j(nba.clean(d))


@app.get("/api/nba/game/{game_id}")
def nba_game(game_id: str):
    from .nba import appdata as nba

    d = nba.game(game_id)
    if "error" in d:
        raise HTTPException(404, d["error"])
    return _j(nba.clean(d))


@app.get("/api/nba/matchups")
def nba_matchups():
    from .nba import appdata as nba

    return _j(nba.matchups())


@app.get("/api/nba/shotprofile/{player_id}")
def nba_shot_profile(player_id: str):
    from .nba import appdata as nba

    d = nba.shot_profile(player_id)
    if "error" in d:
        raise HTTPException(404, d["error"])
    return _j(d)


@app.get("/api/nba/backtests")
def nba_backtests():
    from .nba import appdata as nba

    return _j(nba.clean(nba.backtests()))


@app.get("/api/visuals/{sport}")
def visuals(sport: str):
    """Team colours and cached player photos for one sport (photos as data URIs)."""
    if sport == "nfl":
        return _j(appdata.visuals())
    if sport == "nba":
        from .nba import appdata as nba
        return _j(nba.visuals())
    raise HTTPException(404, "sport must be nfl or nba")


@app.get("/api/backtests")
def backtests():
    return _j(appdata.backtests())


@app.get("/api/record")
def record():
    return _j(appdata.record())


@app.get("/api/data")
def data():
    return _j(appdata.data_status())


@app.get("/api/export/props.{fmt}")
def export_props(fmt: str, season: int | None = None, week: int | None = None,
                 model: str | None = None):
    p = appdata.props(season, week, model)
    rows = [{k: v for k, v in c.items() if not isinstance(v, dict | list)} |
            {f"p_ge_{m['k']}": m["p"] for m in c.get("milestones", [])} for c in p["cards"]]
    return _export(rows, fmt, f"props_{p.get('edition_id')}")


@app.get("/api/export/player/{player_id}.{fmt}")
def export_player(player_id: str, fmt: str):
    p = appdata.player(player_id)
    if "error" in p:
        raise HTTPException(404, p["error"])
    return _export(p["game_log"], fmt, f"gamelog_{player_id}")


@app.get("/api/export/record.{fmt}")
def export_record(fmt: str):
    r = appdata.record()
    rows = [{k: v for k, v in e.items() if k != "games"} for e in r["editions"]]
    return _export(rows, fmt, "editions")


@app.get("/api/export/ledger/{run}.csv.gz")
def export_ledger(run: str):
    p = REPORTS_DIR / "backtests" / run / "ledger.csv.gz"
    if not p.exists() or ".." in run:
        raise HTTPException(404, "no such run")
    # already gzip: so typed, the compression middleware leaves it as it is
    return FileResponse(p, filename=f"{run}_ledger.csv.gz", media_type="application/gzip")


def _export(rows: list[dict], fmt: str, name: str) -> Response:
    rows = appdata._clean(rows)
    if fmt == "json":
        return Response(json.dumps(rows, indent=1), media_type="application/json",
                        headers={"Content-Disposition": f'attachment; filename="{name}.json"'})
    if fmt != "csv":
        raise HTTPException(400, "format must be csv or json")
    buf = io.StringIO()
    keys = sorted({k for r in rows for k in r})
    w = csv.DictWriter(buf, fieldnames=keys)
    w.writeheader()
    w.writerows(rows)
    return Response(buf.getvalue(), media_type="text/csv",
                    headers={"Content-Disposition": f'attachment; filename="{name}.csv"'})


if WEB.exists():
    app.mount("/static", StaticFiles(directory=WEB), name="static")


@app.get("/")
def index():
    return FileResponse(WEB / "index.html")
