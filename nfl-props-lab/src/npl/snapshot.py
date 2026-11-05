"""Build a self-contained static snapshot of the web app (one HTML file) for viewing
without the server: the same app.js and app.css, with API responses embedded as
window.NPL_SNAPSHOT. Downloads are replaced by copy buttons in snapshot mode.

The page embeds US bookmaker prices, so it is written under data/ (git-ignored; The Odds API
terms forbid redistributing raw data) unless another path is given. It must stay under the
hosting limit: build() measures the page and refuses to write one above MAX_BYTES unless
allow_oversize=True. What keeps it small: NFL player pages and photos only for players at or
above min_p_play; NBA cards, player pages and photos only at or above nba_min_p_play, 20-game
logs on cards and 15 on player pages, factor tabs (D-047) only on cards with a bookmaker line,
with what every card repeats kept once, in the payload's factor_tests (each factor's `tested`
lines and reliability label, and the overall line's closing sentence), and put back on each
card by REHYDRATE before the app runs, and their evidence cut to the figures the tabs show
(SHOWN); and in the card extras the newest SNAPSHOT_GRID games against tonight's opponent with
line and difference arrays left out where they hold no value (the app reads a missing array as
"no line")."""

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


# The evidence each factor tab shows (web/app.js, fxMatchup and fxRole): the offline copy keeps
# only these and, of the matchup's two places, the one its tab writes (counted from the fewest,
# `rank`, when the lean is under, else from the most, `rank_most`). The role's game count is in
# its headline. Other factors keep their evidence.
SHOWN = {"matchup": ("allowed", "of", "games"), "role": ("absent", "last3", "season")}


def _shown(k: str, f):
    """A factor's evidence cut to the figures its tab shows (SHOWN); otherwise as it is."""
    ev = f.get("evidence") if isinstance(f, dict) else None
    if k not in SHOWN or not isinstance(ev, dict):
        return f
    keep = set(SHOWN[k])
    if k == "matchup":
        keep.add("rank" if f.get("lean") == "under" or "rank_most" not in ev else "rank_most")
    return {**f, "evidence": {x: v for x, v in ev.items() if x in keep}}


def _unshared(k: str, f, shared: dict | None):
    """A factor without what the props payload keeps once (factor_tests, put back on the page by
    REHYDRATE): its `tested` lines when they are the payload's copy (factor_tests.tested) and its
    reliability label when it is the payload's label for its lean (factor_tests.reliability);
    otherwise the factor as it is."""
    if not isinstance(f, dict) or not shared:
        return f
    u = shared.get("untested") or {}
    if f.get("tested", 0) is None and isinstance(u.get("reliability"), str):
        # a combo's factor (untested): its None and its label are put back from `untested`
        if f.get("reliability") == u["reliability"]:
            return {x: v for x, v in f.items() if x not in ("tested", "reliability")}
        return f
    drop = set()
    t = (shared.get("tested") or {}).get(k)
    if t is not None and f.get("tested") == t:
        drop.add("tested")
    lab = ((shared.get("reliability") or {}).get(k) or {}).get(f.get("lean"))
    if isinstance(lab, str) and f.get("reliability") == lab:
        drop.add("reliability")
    return {x: v for x, v in f.items() if x not in drop} if drop else f


def _overall(o, shared: dict | None):
    """The overall line without its closing sentence when that is the props payload's
    (factor_tests.overall: "agree" when 3 or more factors agree, else "other"): `text` gives
    way to `counts`, the words before it, and REHYDRATE joins the two again."""
    if isinstance(o, dict) and o.get("untested"):
        end = ((shared or {}).get("untested") or {}).get("overall")
    else:
        end = ((shared or {}).get("overall") or {}).get(
            "agree" if isinstance(o, dict) and o.get("agree") else "other")
    t = o.get("text") if isinstance(o, dict) else None
    if not (isinstance(end, str) and end and isinstance(t, str)) or "counts" in o:
        return o
    if t != end and not t.endswith(" " + end):
        return o
    return {**{x: v for x, v in o.items() if x != "text"}, "counts": t[:-len(end)].rstrip()}


def _nba_card(c: dict, shared: dict | None = None) -> dict:
    """A kept NBA card for the snapshot: its log cut to the last 20 games, and its factor tabs
    (`factors`, npl.nba.factors) only when it has a bookmaker line: the rules were tested at
    bookmakers' lines, and on the 2 Nov slate's other cards they would add about 0.7 MB. Each
    kept factor's evidence is cut to what its tab shows (SHOWN), and what is the same on every
    card is dropped when it matches `shared` (the payload's factor_tests): the `tested` lines
    (about 1.7 KB a card as the page writes them) and reliability labels (about 0.4 KB) of the
    factors and the overall line's closing sentence (_overall). The copy keeps those once and
    REHYDRATE puts them back; the tabs then take about 0.73 KB a card. A new dict: the cached
    card is never changed."""
    out = {**c, "log": {k: (v[-20:] if isinstance(v, list) else v) for k, v in c["log"].items()}}
    if not c.get("market_line"):
        out.pop("factors", None)
    elif isinstance(c.get("factors"), dict):
        out["factors"] = {k: (_overall(v, shared) if k == "overall" else
                              _unshared(k, _shown(k, v), shared))
                          for k, v in c["factors"].items()}
    return out


# Run before the app: puts back on each NBA card's factors what the copy keeps once, in the
# props payload's factor_tests (_nba_card): each factor's `tested` lines (factor_tests.tested)
# and reliability label for its lean (factor_tests.reliability), and the overall line's closing
# sentence after its `counts` (factor_tests.overall), so the app reads the same card shape as
# from the server (the evidence stays cut to what the tabs show). A factor that kept its own
# lines or label, or an overall line that kept its text, is left alone.
REHYDRATE_HEAD = """<script>(function () {
  var p = window.NPL_SNAPSHOT && window.NPL_SNAPSHOT.data["/api/nba/props"];
  if (!p || !Array.isArray(p.cards)) return;
%s
  var s = p.factor_tests;
  if (!s || typeof s !== "object") return;
  var t = s.tested || {}, r = s.reliability || {}, e = s.overall || {};
  var u = s.untested || {}, um = u.markets || [];
  p.cards.forEach(function (c) {
    var f = c.factors, combo = um.indexOf(c.market) >= 0;
    if (!f || typeof f !== "object") return;
    Object.keys(f).forEach(function (k) {
      var x = f[k];
      if (!x || typeof x !== "object") return;
      if (k === "overall") {
        if (typeof x.counts !== "string" || "text" in x) return;
        var end = combo ? u.overall : e[x.agree ? "agree" : "other"];
        x.text = typeof end === "string" ? (x.counts ? x.counts + " " + end : end) : x.counts;
        delete x.counts;
        return;
      }
      if (combo) {  // untested market: no test lines, the reason as the label
        if (!("tested" in x)) x.tested = null;
        if (!("reliability" in x) && typeof u.reliability === "string") x.reliability = u.reliability;
        return;
      }
      if (!("tested" in x) && t[k]) x.tested = t[k];
      var lab = r[k] && r[k][x.lean];
      if (!("reliability" in x) && typeof lab === "string") x.reliability = lab;
    });
  });
})();</script>"""


# Combo cards (pr, pa, ra) in the offline copy. Only for players the bookmakers price (a line in
# any market, `line_known`, or a stored line for that combo): the rest would add about 0.2 MB to a
# full slate (snapshot_combos_lined_only). A kept combo card leaves out what the page can rebuild
# exactly, and carries "slim": 1 instead: each field from COMBO_COPY that is the same as on the
# player's card for the combo's first part (points, or rebounds for R+A), the log and the
# with/without arrays when they are exactly the sums of the parts' arrays over the same games (as
# the server builds them), what market_info says once for every card of that market (_combo_info:
# market_label, untested, p_cal_basis) and the fields that are null (COMBO_NULL). COMBO_JS (run by
# REHYDRATE before the app) puts back each field the card does not have and removes `slim`, so the
# app reads the same card as from the server. A field that differs stays on the card, so putting
# back only what is missing is exact. A combo card is slimmed only when all its parts' cards are
# kept.
COMBO_COPY = ("name", "team", "opponent", "position", "p_play", "injury", "minutes_expected",
              "minutes_lineup_shift", "line_known", "tip_utc", "tip_london", "is_home",
              "opp_pace", "opp_factor", "teammates_out", "model")
COMBO_NULL = ("market_line", "p_cal_market", "p_cal_basis", "simple")


def _combo_info(m: dict | None) -> dict:
    """What a combo card's market_info entry says for every card of that market."""
    m = m or {}
    return {"market_label": m.get("label"), "untested": not m.get("tested", True),
            "p_cal_basis": m.get("calibration")}


def _sum_lists(arrs: list) -> list | None:
    """Element-wise sums of equal-length lists (None at a position where any is None); None when
    the lengths differ."""
    if not arrs or any(not isinstance(a, list) or len(a) != len(arrs[0]) for a in arrs):
        return None
    return [None if any(a[i] is None for a in arrs) else sum(a[i] for a in arrs)
            for i in range(len(arrs[0]))]


def _summed_log(src: list) -> dict | None:
    logs = [s.get("log") for s in src]
    if not all(isinstance(lg, dict) for lg in logs):
        return None
    v = _sum_lists([lg.get("values") for lg in logs])
    if v is None or any({k: x for k, x in lg.items() if k != "values"} !=
                        {k: x for k, x in logs[0].items() if k != "values"} for lg in logs):
        return None
    return {**logs[0], "values": v}


def _summed_wowy(src: list) -> list | None:
    ws = [s.get("wowy") for s in src]
    if not all(isinstance(w, list) and len(w) == len(ws[0]) for w in ws):
        return None
    out = []
    for i, w in enumerate(ws[0]):
        rows = [x[i] for x in ws]
        if not all(isinstance(r, dict) and r.get("name") == w.get("name") for r in rows):
            return None
        wi, wo = _sum_lists([r.get("with") for r in rows]), _sum_lists([r.get("without")
                                                                        for r in rows])
        if wi is None or wo is None:
            return None
        out.append({**w, "with": wi, "without": wo})
    return out


def _slim_combos(cards: list, info: dict) -> list:
    """The kept cards with each combo card slimmed (see COMBO_COPY) when all of its parts' cards
    are kept; others unchanged. `info`: the payload's market_info (parts, label, tested,
    calibration per market). A new list: the cards themselves are never changed."""
    by = {(c.get("player_id"), c.get("market")): c for c in cards}
    out = []
    for c in cards:
        mi = info.get(c.get("market")) or {}
        ps = mi.get("parts") if c.get("untested") else None
        src = [by.get((c.get("player_id"), q)) for q in ps or ()]
        if not ps or not all(src):
            out.append(c)
            continue
        first, ci = src[0], _combo_info(mi)
        drop = {k for k in COMBO_COPY if k in c and k in first and c[k] == first[k]}
        drop |= {k for k, v in ci.items() if k in c and c[k] == v}
        drop |= {k for k in COMBO_NULL if k in c and c[k] is None}
        if "log" in c and c["log"] == _summed_log(src):
            drop.add("log")
        if "wowy" in c and c["wowy"] == _summed_wowy(src):
            drop.add("wowy")
        out.append({**{k: v for k, v in c.items() if k not in drop}, "slim": 1} if drop else c)
    return out


def _combos_kept(cards: list) -> list:
    """The cards without combo cards of players the bookmakers do not price: kept when he has a
    line in any market (line_known) or a stored line for that combo (market_line)."""
    return [c for c in cards if not c.get("untested") or c.get("line_known") is True
            or c.get("market_line")]


COMBO_JS = ("\n  var COPY = " + json.dumps(list(COMBO_COPY)) + ", NUL = "
            + json.dumps(list(COMBO_NULL)) + ";" + """
  var info = p.market_info || {}, by = {};
  p.cards.forEach(function (c) { by[c.player_id + "|" + c.market] = c; });
  function sum(arrs) {
    return arrs[0].map(function (_, i) {
      var t = 0;
      for (var j = 0; j < arrs.length; j++) {
        if (arrs[j][i] == null) return null;
        t += arrs[j][i];
      }
      return t;
    });
  }
  p.cards.forEach(function (c) {
    var mi = info[c.market];
    if (c.slim !== 1 || !mi || !Array.isArray(mi.parts)) return;
    var src = mi.parts.map(function (m) { return by[c.player_id + "|" + m]; });
    if (src.some(function (x) { return !x; })) return;
    var first = src[0];
    COPY.forEach(function (k) { if (!(k in c) && k in first) c[k] = first[k]; });
    if (!("log" in c) && first.log) {
      c.log = Object.assign({}, first.log, { values: sum(src.map(function (x) { return x.log.values; })) });
    }
    if (!("wowy" in c) && Array.isArray(first.wowy)) {
      c.wowy = first.wowy.map(function (w, i) {
        var ws = src.map(function (x) { return x.wowy[i]; });
        return Object.assign({}, w, { "with": sum(ws.map(function (x) { return x["with"]; })),
                                      without: sum(ws.map(function (x) { return x.without; })) });
      });
    }
    if (!("market_label" in c)) c.market_label = mi.label;
    if (!("untested" in c)) c.untested = !mi.tested;
    if (!("p_cal_basis" in c)) c.p_cal_basis = mi.calibration == null ? null : mi.calibration;
    NUL.forEach(function (k) { if (!(k in c)) c[k] = null; });
    delete c.slim;
  });
""")
REHYDRATE = REHYDRATE_HEAD.replace("%s", COMBO_JS, 1)


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
    recorded as snapshot_factors_lined_only), their evidence cut to what the tabs show and what
    every card repeats kept once, in factor_tests: the `tested` lines (recorded as
    snapshot_tested_shared), the reliability labels and the overall line's closing sentence
    (snapshot_labels_shared); the last 15 games on player pages. Card extras stay in the props
    payload only: player pages here carry none (each one is a slate player whose extras are in
    props `extras`). data["snapshot_player_pages"] lists the players with a page in the copy.
    The slate's injury counts cover every player of the edition (nba.appdata.injured_counts),
    trimmed cards or not."""
    from .nba import appdata as nba

    s = nba.slate()
    if "error" in s:
        return
    data["/api/nba/slate"] = nba.clean(s)
    props = dict(nba.props())  # a copy: the cached payload itself is never trimmed
    shared = props.get("factor_tests") or None
    props["cards"] = [_nba_card(c, shared) for c in props["cards"] if c["p_play"] >= min_p_play]
    props["cards"] = _combos_kept(props["cards"])  # combos only for players the books price
    props["snapshot_combos_lined_only"] = True
    props["snapshot_min_p_play"] = min_p_play
    props["snapshot_factors_lined_only"] = True  # factor tabs only on cards with a line
    if shared and shared.get("tested"):  # each factor's `tested` lines kept once (REHYDRATE)
        props["snapshot_tested_shared"] = True
    if shared and (shared.get("reliability") or shared.get("overall")):  # labels, likewise
        props["snapshot_labels_shared"] = True
    props["extras"] = _nba_extras(props.get("extras") or {}, props["cards"])
    # last, once everything else has read the full cards: combo cards without what REHYDRATE
    # rebuilds from their parts' cards (_slim_combos)
    props["cards"] = _slim_combos(props["cards"], props.get("market_info") or {})
    if any("slim" in c for c in props["cards"]):
        props["snapshot_combos_slim"] = True
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
        sp = nba.shot_profile(pid, before_date=s.get("date_et"))
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
{REHYDRATE}
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
