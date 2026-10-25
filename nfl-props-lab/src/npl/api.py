"""HTTP API and static web app. Read-only views over saved editions, lake tables and
backtest reports; no model fitting or simulation runs in a request handler."""

from __future__ import annotations

import csv
import io
import json

from fastapi import FastAPI, HTTPException, Query
from fastapi.responses import FileResponse, JSONResponse, Response
from fastapi.staticfiles import StaticFiles

from . import appdata
from .config import PROJECT_ROOT, REPORTS_DIR

WEB = PROJECT_ROOT / "web"
app = FastAPI(title="NFL Props Lab", docs_url="/api/docs", openapi_url="/api/openapi.json")


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
    return FileResponse(p, filename=f"{run}_ledger.csv.gz")


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
