"""Build a self-contained static snapshot of the web app (one HTML file) for viewing
without the server: the same app.js and app.css, with API responses embedded as
window.NPL_SNAPSHOT. Downloads are replaced by copy buttons in snapshot mode.

The page embeds US bookmaker prices, so it is written under data/ (git-ignored; The Odds API
terms forbid redistributing raw data) unless another path is given. It must stay under the
hosting limit: build() measures the page and refuses to write one above MAX_BYTES unless
allow_oversize=True. What keeps it small: NFL player pages and photos only for players at or
above min_p_play; NBA cards, player pages and photos only at or above nba_min_p_play, 20-game
logs on cards and 15 on player pages, factor tabs (D-047) only on cards with a bookmaker line,
and in the card extras the newest SNAPSHOT_GRID games against tonight's opponent with line and
difference arrays left out where they hold no value (the app reads a missing array as "no
line")."""

from __future__ import annotations

import json
from pathlib import Path

from . import appdata, headshots
from .config import DATA_HOME, PROJECT_ROOT
from .util import london, parse_iso, utcnow

WEB = PROJECT_ROOT / "web"
DEFAULT_OUT = DATA_HOME / "snapshots" / "nfl-props-lab.html"
MAX_BYTES = 16_000_000  # the host's page limit is 16 MB
SNAPSHOT_GRID = 5  # games against tonight's opponent kept per player (the server shows 10)


class SnapshotTooLarge(ValueError):
    """The page would exceed MAX_BYTES (build() with allow_oversize=True writes it anyway)."""


def _slim_card(c: dict, keep_dist: bool) -> dict:
    c = dict(c)
    if not keep_dist:
        c.pop("dist", None)
    return c


def _empty(v) -> bool:
    return v is None or (isinstance(v, list) and all(x is None for x in v))


def _grid(g, cap: int = SNAPSHOT_GRID):
    """A player's grid against tonight's opponent for the snapshot: the newest `cap` games (`n`
    still counts them all), each row without a `line` or `diff` array that holds no value."""
    if not isinstance(g, dict) or not isinstance(g.get("games"), list):
        return g
    rows = [{k: v for k, v in r.items() if not (k in ("line", "diff") and _empty(v))}
            for r in g["games"][:cap]]
    return {**g, "games": rows}


def _nba_card(c: dict) -> dict:
    """A kept NBA card for the snapshot: its log cut to the last 20 games, and its factor tabs
    (`factors`, npl.nba.factors) only when it has a bookmaker line: the rules were tested at
    bookmakers' lines, and on every card they would add about 1 KB each (1.9 MB on the 2 Nov
    slate, over the page limit). A new dict: the cached card is never changed."""
    out = {**c, "log": {k: (v[-20:] if isinstance(v, list) else v) for k, v in c["log"].items()}}
    if not c.get("market_line"):
        out.pop("factors", None)
    return out


def _nba_extras(ex: dict, cards: list) -> dict:
    """The props `extras` block cut to what the kept cards look up: their players (with the
    v-opponent grid trimmed, _grid), their opponents' and teams' defence, their (opponent,
    position) pairs and their games; the grid's note states the cap this copy uses. A new
    block: the cached one is never changed."""
    from .nba.extras import vs_opp_note

    if not ex or "players" not in ex:
        return ex
    pids = {c["player_id"] for c in cards}
    teams = {c["opponent"] for c in cards} | {c["team"] for c in cards}
    pvo = {f"{c['opponent']}|{c['position']}" for c in cards}
    gids = {c["game_id"] for c in cards}
    players = {k: ({**v, "vs_opp": _grid(v["vs_opp"])} if isinstance(v, dict) and "vs_opp" in v
                   else v) for k, v in ex["players"].items() if k in pids}
    out = {**ex, "players": players,
           "defence": {k: v for k, v in ex.get("defence", {}).items() if k in teams},
           "pos_v_opp": {k: v for k, v in ex.get("pos_v_opp", {}).items() if k in pvo},
           "game_lines": {k: v for k, v in ex.get("game_lines", {}).items() if k in gids}}
    if isinstance(ex.get("notes"), dict) and "vs_opp" in ex["notes"]:
        out["notes"] = {**ex["notes"], "vs_opp": vs_opp_note(SNAPSHOT_GRID)}
    return out


def _add_nba(data: dict, min_p_play: float = 0.5) -> None:
    """NBA pages for the latest NBA edition (if any), trimmed: cards of players with a play
    probability of at least `min_p_play` (recorded as the props payload's snapshot_min_p_play),
    20-game logs on cards and factor tabs only on cards with a bookmaker line (_nba_card;
    recorded as snapshot_factors_lined_only), the last 15 games on player pages. Card extras
    stay in the props payload only: player pages here carry none (each one is a slate player
    whose extras are in props `extras`). data["snapshot_player_pages"] lists the players with a
    page in the copy.
    The slate's injury counts cover every player of the edition (nba.appdata.injured_counts),
    trimmed cards or not."""
    from .nba import appdata as nba

    s = nba.slate()
    if "error" in s:
        return
    data["/api/nba/slate"] = nba.clean(s)
    props = dict(nba.props())  # a copy: the cached payload itself is never trimmed
    props["cards"] = [_nba_card(c) for c in props["cards"] if c["p_play"] >= min_p_play]
    props["snapshot_min_p_play"] = min_p_play
    props["snapshot_factors_lined_only"] = True  # factor tabs only on cards with a line
    props["extras"] = _nba_extras(props.get("extras") or {}, props["cards"])
    data["/api/nba/props"] = nba.clean(props)
    data["/api/nba/backtests"] = nba.clean(nba.backtests())
    for g in s["games"]:
        data[f"/api/nba/game/{g['game_id']}"] = nba.clean(nba.game(g["game_id"]))
    try:
        data["/api/nba/matchups"] = nba.matchups()
    except FileNotFoundError:
        pass
    # player pages, photos and shot charts only for games not yet started: the page must stay
    # under the host's size limit on a full slate (cards of started games show initials)
    now = utcnow()
    started = {g["game_id"] for g in s["games"] if parse_iso(g["tip_utc"]) <= now}
    pids = {c["player_id"] for c in props["cards"] if c["game_id"] not in started} or \
        {c["player_id"] for c in props["cards"]}
    headshots.fetch("nba", pids)
    headshots.fetch_logos("nba")
    data["/api/visuals/nba"] = nba.visuals(pids)
    pages = []
    for pid in sorted(pids):
        sp = nba.shot_profile(pid)
        if "error" not in sp:
            data[f"/api/nba/shotprofile/{pid}"] = sp
        p = nba.player(pid)
        if "error" in p:
            continue
        p["game_log"] = p["game_log"][-15:]
        p.pop("forecasts", None)
        p.pop("extras", None)  # the same figures are in /api/nba/props extras (size limit)
        data[f"/api/nba/player/{pid}"] = nba.clean(p)
        pages.append(pid)
    data["snapshot_player_pages"] = pages


def build(out: Path = DEFAULT_OUT, *, min_p_play: float = 0.5, nba_min_p_play: float = 0.5,
          model: str | None = None, standalone: bool = False,
          allow_oversize: bool = False) -> dict:
    """Write the snapshot page to `out` and report its size in bytes. The play probability a
    player needs to be included: min_p_play for an NFL player page and photo (every NFL card is
    kept), nba_min_p_play for NBA cards, player page and photo. standalone=True writes a complete
    HTML document (doctype, charset, viewport) to open straight from disk; the default is a
    fragment for hosts that add their own skeleton. A page above MAX_BYTES raises
    SnapshotTooLarge and is not written, unless allow_oversize."""
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
    headshots.fetch("nfl", set(players))
    headshots.fetch_logos("nfl")
    data["/api/visuals/nfl"] = appdata.visuals(set(players))
    _add_nba(data, min_p_play=nba_min_p_play)
    snap = {"built_at": utcnow().isoformat(), "built_london": london(utcnow()), "data": data}
    blob = json.dumps(snap, separators=(",", ":"), default=str).replace("</", "<\\/")
    css = (WEB / "app.css").read_text()
    js = (WEB / "app.js").read_text()
    html = f"""<title>Props Lab</title>
<meta name="claude:viewport-fit-sides" content="cover">
<link rel="preconnect" href="https://fonts.googleapis.com">
<link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Archivo:wdth,wght@62..125,500..900&family=Figtree:wght@400;500;600;700;800&family=JetBrains+Mono:wght@400;600&display=swap">
<style>
{css}
</style>
<div id="app"></div>
<script>window.NPL_SNAPSHOT = {blob};</script>
<script type="module">
{js}
</script>
"""
    if standalone:
        html = ('<!doctype html>\n<html lang="en-GB">\n<head>\n<meta charset="utf-8">\n'
                '<meta name="viewport" content="width=device-width, initial-scale=1, '
                'viewport-fit=cover">\n' + html.replace('<div id="app"></div>',
                                                         '</head>\n<body>\n<div id="app"></div>', 1)
                + "</body>\n</html>\n")
    page = html.encode("utf-8")
    nba_cards = len((data.get("/api/nba/props") or {}).get("cards", []))
    report = {"path": str(out), "bytes": len(page), "limit_bytes": MAX_BYTES,
              "players": len(players), "games": len(data["/api/slate"]["games"]),
              "nba_cards": nba_cards, "min_p_play": min_p_play,
              "nba_min_p_play": nba_min_p_play, "paths": len(data)}
    if len(page) > MAX_BYTES and not allow_oversize:
        raise SnapshotTooLarge(
            f"snapshot would be {len(page):,} bytes, over the {MAX_BYTES:,}-byte limit; not "
            f"written. Raise --min-p-play ({min_p_play:g}) or --nba-min-p-play "
            f"({nba_min_p_play:g}), or pass --allow-oversize")
    out = Path(out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_bytes(page)
    return report
