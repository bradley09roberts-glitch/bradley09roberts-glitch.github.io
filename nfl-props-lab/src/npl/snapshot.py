"""Build a self-contained static snapshot of the web app (one HTML file) for viewing
without the server: the same app.js and app.css, with API responses embedded as
window.NPL_SNAPSHOT. Downloads are replaced by copy buttons in snapshot mode."""

from __future__ import annotations

import json
from pathlib import Path

from . import appdata
from .config import PROJECT_ROOT
from .util import london, utcnow

WEB = PROJECT_ROOT / "web"


def _slim_card(c: dict, keep_dist: bool) -> dict:
    c = dict(c)
    if not keep_dist:
        c.pop("dist", None)
    return c


def build(out: Path, *, min_p_play: float = 0.5, model: str | None = None) -> dict:
    data = {}
    st = appdata.data_status()
    idx = appdata.editions_index()
    data["/api/status"] = appdata._clean({"editions": [{k: v for k, v in e.items() if k != "games"}
                                                       for e in idx[:10]],
                                          "latest": idx[0]["edition_id"] if idx else None, **st})
    data["/api/data"] = st
    data["/api/slate"] = appdata.slate()
    props = appdata.props(model=model)
    data["/api/props"] = props
    data["/api/backtests"] = appdata.backtests()
    data["/api/record"] = appdata.record()
    data["/api/defense"] = appdata.defense()
    game_keep = ("player_id", "name", "position", "team", "market", "median", "q10", "q90",
                 "p_play", "injury_report", "status")
    for g in data["/api/slate"]["games"]:
        gm = appdata.game(g["game_id"])
        gm["cards"] = [{k: c.get(k) for k in game_keep} for c in gm.get("cards", [])]
        data[f"/api/game/{g['game_id']}"] = gm
    players = sorted({c["player_id"] for c in props["cards"]
                      if c.get("status") == "ok" and (c.get("p_play") or 0) >= min_p_play})
    for pid in players:
        p = appdata.player(pid)
        if "error" in p:
            continue
        fkeep = ("edition_id", "model", "market", "market_label", "mean", "median", "q10",
                 "q90", "p_play", "status")
        latest_id = props.get("edition_id")
        p["forecasts"] = [{k: f.get(k) for k in fkeep} for f in p.get("forecasts", [])
                          if f.get("edition_id") == latest_id]
        lkeep = ("season", "week", "season_type", "team", "opponent", "participation",
                 "attempts", "completions", "passing_yards", "carries", "rushing_yards",
                 "targets", "receptions", "receiving_yards")
        p["game_log"] = [{k: g.get(k) for k in lkeep} for g in p["game_log"]]
        data[f"/api/player/{pid}"] = p
    snap = {"built_at": utcnow().isoformat(), "built_london": london(utcnow()), "data": data}
    blob = json.dumps(snap, separators=(",", ":"), default=str).replace("</", "<\\/")
    css = (WEB / "app.css").read_text()
    js = (WEB / "app.js").read_text()
    html = f"""<title>NFL Props Lab</title>
<meta name="claude:viewport-fit-sides" content="cover">
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Barlow+Condensed:wght@500;600;700&family=JetBrains+Mono:wght@400;600&family=Source+Sans+3:wght@400;600;700&display=swap">
<style>
{css}
</style>
<div id="app"></div>
<script>window.NPL_SNAPSHOT = {blob};</script>
<script type="module">
{js}
</script>
"""
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(html)
    return {"path": str(out), "bytes": out.stat().st_size, "players": len(players),
            "games": len(data["/api/slate"]["games"]), "paths": len(data)}
