"""Read-only NBA views for the app: slate, prop cards (with recent game logs for hit rates),
player and game pages, and opponent factors. Built from the latest NBA edition and the lake."""

from __future__ import annotations

import json
from functools import lru_cache

import numpy as np
import pandas as pd

from .. import books
from ..config import LONDON
from ..dist import Dist
from ..util import log
from . import canon, live
from . import extras as X
from . import factors as F
from . import model as M

STAT_COLS = {"pts": "pts", "reb": "reb", "ast": "ast", "fg3m": "fg3m"}


def _london(ts) -> str:
    t = pd.Timestamp(ts)
    t = t.tz_localize("UTC") if t.tzinfo is None else t
    return t.tz_convert(LONDON).strftime("%a %d %b %H:%M %Z")


@lru_cache(maxsize=4)
def _edition(path: str, mtime: float) -> dict:
    return json.loads(open(path).read())


def latest() -> dict | None:
    p = live.latest_edition_path()
    return _edition(str(p), p.stat().st_mtime) if p else None


@lru_cache(maxsize=2)
def _lake(mtime: float) -> dict:
    return canon.load()


def lake() -> dict:
    return _lake((canon.NBA_LAKE / "player_games.parquet").stat().st_mtime)


def _values(pg: pd.DataFrame, mk: str) -> pd.Series:
    return pg.pts + pg.reb + pg.ast if mk == "pra" else pg[STAT_COLS[mk]]


def game_logs(cuts: dict, own: dict | None = None) -> dict[str, pd.DataFrame]:
    """Each player's played games, oldest first, that started strictly before his cut-off
    (`cuts`: {player_id: time}, the earlier of the edition's publication and his game's start,
    npl.nba.factors.cutoffs), without his game tonight (`own`: {player_id: game_id}) whatever its
    stored start. An edition published after a game had started never counts that game, even
    once its box score is stored."""
    pg = lake()["player_games"]
    pg = pg[pg.player_id.isin(set(cuts)) & (pg.participation == "played")]
    keep = pg.tip_utc < pg.player_id.map(cuts).astype(pg.tip_utc.dtype)
    if own:
        keep &= pg.game_id != pg.player_id.map(own)
    pg = pg[keep].sort_values("tip_utc")
    return {pid: g for pid, g in pg.groupby("player_id")}


def log_cuts(e: dict, fc: list) -> tuple[dict, dict]:
    """({player_id: cut-off}, {player_id: his game tonight}) for an edition's forecasts `fc`: the
    cut-off is the earlier of the edition's publication and his game's start (F.cutoffs)."""
    cuts = F.cutoffs(e)
    pub = F.utc(e["published_at"])
    return ({f["player_id"]: cuts.get(f["game_id"], pub) for f in fc},
            {f["player_id"]: f["game_id"] for f in fc})


def opponent_factors(at) -> dict:
    """Model opponent factor per team and stat (shrunk as N1 uses it), with ranks
    (1 = allows least), from team-games that started before `at` (the cards and game pages pass
    F.slate_cutoff: before publication and before any of tonight's games)."""
    L = lake()
    p = M.load_params(live.LIVE_PARAMS)  # the parameters live editions use (audit: was nba-0.2)
    ts = M.team_states(L["team_games"][L["team_games"].tip_utc < pd.Timestamp(at)], p)
    last = ts.sort_values("tip_utc").groupby("team").tail(1).set_index("team")
    out = {}
    for s in M.BASE_STATS:
        raw = last[f"def_{s}"] / last[f"lg_{s}"]
        f = 1 + (raw - 1) * last.n_team / (last.n_team + p.k_def)
        r = f.rank(method="min").astype(int)
        for t in f.index:
            out.setdefault(t, {})[s] = {"factor": round(float(f[t]), 4), "rank": int(r[t]),
                                        "games": int(last.n_team[t])}
    pace = last.pace_ew.rank(ascending=False, method="min").astype(int)
    for t in last.index:
        out[t]["pace"] = {"value": round(float(last.pace_ew[t]), 1), "rank": int(pace[t])}
    return out


def market_lines(starts: dict) -> dict:
    """Latest pre-game quotes per (player, market) for these games (`starts`: {game_id: its
    start, tip_utc}): the most common line, the market's over chance with the bookmaker margin
    removed (average over books quoting both sides), best prices and when they were seen. US
    books are a research reference only.

    Pre-game only, so a card's line (and the form count against it) never comes from in-play
    prices: quotes observed at or after their game's start are left out, and so are pulls taken
    then, by the provider's start time (odds_api.pulls_frame, pregame=True) or by ours."""
    from .. import db
    from ..sources import odds_api

    try:
        con = db.connect()
        q = odds_api.quotes_frame(con)
        pulls = odds_api.pulls_frame(con, "event_odds", "nba", pregame=True)
    except Exception:  # noqa: BLE001 - no database or table: no lines
        return {}
    q = pregame_quotes(q, starts)
    return consensus_lines(q, X._before_tip(pulls, q, "provider_event_id"))


def pregame_quotes(q: pd.DataFrame, starts: dict) -> pd.DataFrame:
    """Stored quotes of these games (`starts`: {game_id: start}) observed strictly before their
    game's start, with that start as `tip`; quotes of other games are left out."""
    tip = pd.Series(pd.to_datetime(list(starts.values()), utc=True),
                    index=[str(k) for k in starts], dtype="datetime64[ns, UTC]")
    q = q[q.game_id.notna()]
    t = q.game_id.astype(str).map(tip)
    seen = pd.to_datetime(q.observed_at, utc=True, format="ISO8601")
    return q.assign(tip=t)[t.notna() & (seen < t)]


def consensus_lines(q: pd.DataFrame, pulls: pd.DataFrame | None = None) -> dict:
    """Consensus line, fair price and best regulated prices per (player, market) (books.py),
    with fair_under = 1 - fair_over (the margin-free chance of each side, npl.parlay).

    The board is the one each game and market's latest pull saw, rebuilt from the pull log
    (books.pull_board): each book in that pull counts with its rows for the update time the
    pull reported, so a book whose market did not change since an earlier pull (the store keeps
    such rows once) still counts and a book that withdrew the market does not; `observed_at` is
    that pull's time. Rows no logged pull covers (`pulls` None) fall back to the 12-hour
    rebuild (books.complete_board)."""
    q = q[q.game_id.notna() & q.market.isin(M.MARKETS)]
    q, _ = books.pull_board(q, pulls, keys=("game_id", "market"))
    q = q[q.player_id.notna()]
    if q.empty:
        return {}
    meta = q.assign(uk=q.availability == "verified_uk").groupby(["player_id", "market"]).agg(
        seen=("board_at", "max"), uk=("uk", "all"))
    out = books.records(books.consensus_frame(books.pairs(q)))
    for key, c in out.items():
        s = meta.loc[key]
        c |= {"observed_at": str(s.seen),
              "availability": "verified_uk" if s.uk else "research_reference"}
        if c.get("fair_over") is not None:  # each side's margin-free chance, for combining legs
            c["fair_under"] = X.half_up(1 - c["fair_over"], 4)
    return out


def injured_counts(e: dict) -> dict:
    """{team: {"out": n, "questionable": n}} for both teams of every game in an edition, one
    count per player listed in it (all of its players, whatever their play probability): out =
    an injury entry of Out or out for the season (OUT, OFS), questionable = any other entry
    (a game-time decision), as the cards label them."""
    out = {t: {"out": 0, "questionable": 0} for g in e["games"] for t in (g["home"], g["away"])}
    seen = set()
    for f in e["forecasts"]:
        key = (f["game_id"], f["player_id"])
        if f["model"] != e["headline_model"] or key in seen:
            continue
        seen.add(key)
        inj = f.get("injury")
        if inj:
            kind = "out" if inj.get("fantasy") in ("OUT", "OFS") else "questionable"
            out.setdefault(f["team"], {"out": 0, "questionable": 0})[kind] += 1
    return out


def slate() -> dict:
    e = latest()
    if e is None:
        return {"error": "no NBA edition yet"}
    inj = injured_counts(e)
    games = [{**g, "tip_london": _london(g["tip_utc"]),
              "injured": {t: inj[t] for t in (g["home"], g["away"])}} for g in e["games"]]
    return {"edition_id": e["edition_id"], "date_et": e["date_et"],
            "published_at": e["published_at"], "published_london": _london(e["published_at"]),
            "kind": e["kind"], "games": games, "notes": e.get("notes", []),
            "inputs": e.get("inputs", {}), "model_version": e["code"]["model_version"]}


_PROPS: dict = {}


def _props_key():
    from .. import db

    p = live.latest_edition_path()
    try:
        con = db.connect()
        q = con.execute("SELECT (SELECT MAX(id) FROM quotes), (SELECT MAX(id) FROM quote_links), "
                        "(SELECT MAX(id) FROM game_lines), (SELECT MAX(id) FROM odds_pulls), "
                        "(SELECT MAX(id) FROM raw_files WHERE source = 'sportsdataverse')"
                        ).fetchone()
        q = tuple(q)
        try:  # historical lines (bought once, D-040); the table exists only where they were
            q += tuple(con.execute("SELECT MAX(id) FROM hist_quotes").fetchone())
        except Exception:  # noqa: BLE001
            pass
    except Exception:  # noqa: BLE001 - no database: lines are simply absent
        q = None
    lk = _lake_key()
    return (str(p), p.stat().st_mtime if p else None, lk, q)


def _lake_key() -> float:
    return (canon.NBA_LAKE / "player_games.parquet").stat().st_mtime


def _con():
    from .. import db

    try:
        return db.connect()
    except Exception:  # noqa: BLE001 - no database: extras that need it are left out
        return None


def props() -> dict:
    """Prop cards for the latest edition, cached until the edition, lake, stored quotes, game
    lines, pull log or SportsDataverse files change."""
    key = _props_key()
    if _PROPS.get("key") != key:
        _PROPS.clear()
        _PROPS.update(key=key, data=_props())
    return _PROPS["data"]


def _props() -> dict:
    e = latest()
    if e is None:
        return {"error": "no NBA edition yet"}
    fc = [f for f in e["forecasts"] if f["model"] == e["headline_model"]]
    others = {(f["player_id"], f["market"], f["model"]): f for f in e["forecasts"]
              if f["model"] != e["headline_model"]}
    # every box-score input stops at the earlier of publication and his game's start (D-047)
    logs = game_logs(*log_cuts(e, fc))
    games = {g["game_id"]: g for g in e["games"]}
    opp = opponent_factors(F.slate_cutoff(e))
    mkt = market_lines({gid: g["tip_utc"] for gid, g in games.items()})  # pre-game quotes only
    season = e["date_et"][:4]
    ex, inj = _extras(e, mkt)
    fx = _factors(e, fc, logs, mkt, ex)
    out_by_team: dict[str, list] = {}
    for f in fc:
        if f["market"] == "pts" and f.get("injury") and f["injury"]["fantasy"] in ("OUT", "OFS"):
            lg = logs.get(f["player_id"])
            out_by_team.setdefault(f["team"], []).append(
                {"name": f["name"], "player_id": f["player_id"], "minutes": f["minutes_expected"],
                 "played_last": bool(lg is not None and len(lg) and
                                     lg.tip_utc.iloc[-1] >= _team_last_tip(f["team"], e)),
                 # everyone listed here is Out; the snapshot's structured fields, if it lists him
                 "report": inj.get(f["player_id"])})
    simple_s = _simple(fc, games)
    cards = []
    for f in fc:
        lg = logs.get(f["player_id"])
        vals, opps, dates = [], [], []
        if lg is not None and len(lg):
            v = _values(lg, f["market"]).tail(40)
            sub = lg.tail(40)
            vals = [int(z) for z in v]
            opps = sub.opp.tolist()
            dates = sub.date.dt.strftime("%d %b").tolist()
            seasons = sub.season.astype(str).tolist()
        else:
            seasons = []
        g = games[f["game_id"]]
        o = opp.get(f["opponent"], {})
        stat = "pts" if f["market"] == "pra" else f["market"]
        cards.append({
            **{k: f[k] for k in ("game_id", "player_id", "name", "team", "opponent", "position",
                                 "market", "p_play", "minutes_expected", "mean", "median", "q10",
                                 "q90", "dist")},
            # the edition's injury entry plus the snapshot's structured fields under "report"
            "injury": X.with_report(f["injury"], inj.get(f["player_id"])),
            "minutes_lineup_shift": f.get("minutes_lineup_shift"),
            "line_known": f.get("line_known"),
            "simple": simple_s.get((f["player_id"], f["market"])),
            "market_label": M.MARKET_LABELS[f["market"]], "model": e["headline_model"],
            "tip_utc": g["tip_utc"], "tip_london": _london(g["tip_utc"]),
            "is_home": g["home"] == f["team"],
            "baselines": {m: others[(f["player_id"], f["market"], m)]["mean"]
                          for m in ("A", "C") if (f["player_id"], f["market"], m) in others},
            "log": {"values": vals, "opp": opps, "date": dates, "season": seasons,
                    "current_season": str(int(season) + 1) if int(e["date_et"][5:7]) >= 8
                    else season},
            "opp_factor": o.get(stat), "opp_pace": o.get("pace"),
            "market_line": mkt.get((f["player_id"], f["market"])),
            "p_cal_market": _p_cal(f, mkt.get((f["player_id"], f["market"])),
                                   e["code"]["model_version"]),
            # only absences that recent minutes cannot reflect yet (he played last game)
            "teammates_out": [t for t in out_by_team.get(f["team"], [])
                              if t["name"] != f["name"] and t["minutes"] >= 20 and
                              t["played_last"]],
            "wowy": _wowy(f, lg, logs, out_by_team.get(f["team"], []), int(season) + 1
                          if int(e["date_et"][5:7]) >= 8 else int(season)),
            # matchup, form, game script and role leans from the backtested rules (D-047)
            "factors": fx.get((f["player_id"], f["market"]))})
    return {"edition_id": e["edition_id"], "date_et": e["date_et"], "cards": cards,
            "model_version": e["code"]["model_version"],
            "calibration": calibration(e["code"]["model_version"]),
            "markets": {k: M.MARKET_LABELS[k] for k in M.MARKETS},
            "published_london": _london(e["published_at"]), "extras": ex,
            "factor_tests": F.tests_payload()}


def _factors(e: dict, fc: list, logs: dict, mkt: dict, ex: dict) -> dict:
    """{(player_id, market): factor breakdown} for the cards (npl.nba.factors, D-047). A display
    extra: a failure leaves the cards without one (None) and never blocks them."""
    try:
        return F.build(e, fc, logs, mkt, ex if isinstance(ex, dict) else {}, lake())
    except Exception as err:  # noqa: BLE001
        log.warning("NBA factor breakdown unavailable: %r", err)
        return {}


def parlay(spec: str, price: float | None = None) -> dict:
    """npl.parlay.fair_combo for legs of the latest cards, written player_id:market:side and
    separated by commas (side over or under), each at its card's margin-free chance of that
    side (market_line.fair_over / fair_under), with an offered decimal price or None. Figures
    are rounded half up for display (chances 4 dp, fair odds 2 dp); `selections` lists the
    legs as resolved. {"error": ...} for a leg that cannot be resolved."""
    from .. import parlay as P

    pr = props()
    if "error" in pr:
        return pr
    cards = {(c["player_id"], c["market"]): c for c in pr["cards"]}
    legs = []
    for i, part in enumerate((x.strip() for x in spec.split(",") if x.strip()), 1):
        bits = part.split(":")
        if len(bits) != 3 or bits[2] not in ("over", "under"):
            return {"error": f"leg {i}: write player_id:market:over or player_id:market:under"}
        pid, mk, side = bits
        c = cards.get((pid, mk))
        if c is None:
            return {"error": f"leg {i}: no card for player {pid}, market {mk}"}
        m = c.get("market_line")
        if not m or m.get(f"fair_{side}") is None:
            return {"error": f"leg {i}: no bookmaker line stored for {c['name']} {mk}"}
        legs.append({"player_id": pid, "name": c["name"], "game_id": c["game_id"],
                     "market": mk, "side": side, "line": m["line"],
                     "chance": m[f"fair_{side}"], "books": m["books"]})
    try:
        r = P.fair_combo(legs, price)
    except ValueError as err:
        return {"error": str(err)}
    nd = {"chance": 4, "fair_odds": 2, "ev": 4, "breakeven": 4}
    return {**r, **{k: None if r[k] is None else X.half_up(r[k], n) for k, n in nd.items()},
            "selections": legs}


def calibration(version: str | None) -> dict | None:
    """The props payload's `calibration` field: the V4 slope per market for the edition's model
    version (npl.nba.calib, from calibration.json) and the slope below which a market has no
    model view at the bookmakers' line; None when that version has no fitted calibration."""
    from . import calib

    slopes = calib.v4_slopes(version) if version else None
    if slopes is None:
        return None
    return {"version": version, "slope": slopes, "flat_below": calib.FLAT_BELOW}


def _extras(e: dict, mkt: dict) -> tuple[dict, dict]:
    """(the props payload's `extras` block, {player_id: injury report}) for an edition: research
    context from the box scores, play-by-play, injury snapshot, stored lines and game lines
    (npl.nba.extras). Display extras: a failure leaves them out and never blocks the cards."""
    con = _con()
    if con is None:
        return {}, {}
    try:
        inj = X.edition_injuries(con, e)
    except Exception as err:  # noqa: BLE001
        log.warning("NBA injury reports unavailable: %r", err)
        inj = {}
    try:
        ex = X.build(e, mkt, lake(), con, lake_key=_lake_key())
    except Exception as err:  # noqa: BLE001
        log.warning("NBA card extras unavailable: %r", err)
        ex = {"error": f"extras unavailable: {type(err).__name__}"}
    return ex, inj


def _p_cal(f: dict, m: dict | None, version: str) -> float | None:
    """Calibrated model over chance at the bookmakers' line (D-043), with the coefficients fitted
    for the model version that made this edition; None without a line or without coefficients."""
    if not m:
        return None
    from . import calib
    try:
        p = calib.calibrated(f["market"], Dist.from_json(f["dist"]).line(m["line"])["over"],
                             version)
    except KeyError:  # an edition from a model with no fitted calibration
        return None
    return round(p, 4)


def _simple(fc: list, games: dict) -> dict:
    """The simple baseline S (season average x opponent x rest x minutes trend; D-038) for each
    (player, market) of the edition; empty if it cannot be computed."""
    from . import simple, source

    rows = pd.DataFrame([{"player_id": f["player_id"], "team": f["team"], "opp": f["opponent"],
                          "game_id": f["game_id"], "season": source.CURRENT_SEASON,
                          "tip_utc": pd.Timestamp(games[f["game_id"]]["tip_utc"])}
                         for f in fc if f["market"] == "pts"])
    if rows.empty:
        return {}
    try:
        sp = simple.tonight(rows)
    except Exception:  # noqa: BLE001 - a display extra; never block the cards
        return {}
    return {(pid, m): round(float(v), 1) for pid, (_, r) in zip(rows.player_id, sp.iterrows(), strict=True)
            for m, v in r.items() if pd.notna(v)}


def _wowy(f: dict, lg, logs: dict, out: list, cur_season: int, top: int = 2) -> list:
    """With/without splits for the player's market against teammates ruled out tonight (15+
    expected minutes, most minutes first): his games for this team this season and last, in
    seasons the teammate also played for the team, split by whether the teammate played."""
    res = []
    if lg is None or not len(lg):
        return res
    for t in sorted(out, key=lambda t: -t["minutes"]):
        if len(res) >= top or t["player_id"] == f["player_id"] or t["minutes"] < 15:
            continue
        tl = logs.get(t["player_id"])
        if tl is None:
            continue
        tl = tl[tl.team == f["team"]]
        seasons = set(tl.season) & {cur_season, cur_season - 1}
        mine = lg[(lg.team == f["team"]) & lg.season.isin(seasons)]
        if mine.empty:
            continue
        w = mine.game_id.isin(set(tl.game_id)).to_numpy()
        v = _values(mine, f["market"]).to_numpy()
        mins = mine.minutes.to_numpy(float)
        # the most recent 20 games each way keep the page small
        vw, vo, mw, mo = v[w][-20:], v[~w][-20:], mins[w][-20:], mins[~w][-20:]
        res.append({"name": t["name"], "with": [int(z) for z in vw], "without": [int(z) for z in vo],
                    "min_with": round(float(mw.mean()), 1) if len(mw) else None,
                    "min_without": round(float(mo.mean()), 1) if len(mo) else None,
                    "seasons": sorted(int(z) for z in seasons)})
    return res


def _team_last_tip(team: str, e: dict):
    """Start of the team's latest game before its game tonight, as the edition could see it:
    games that started before the earlier of publication and tonight's start (F.cutoffs)."""
    gid = next((g["game_id"] for g in e["games"] if team in (g["home"], g["away"])), None)
    cut = F.cutoffs(e).get(gid, F.utc(e["published_at"]))
    tg = lake()["team_games"]
    t = tg[(tg.team == team) & (tg.tip_utc < cut) & (tg.game_id != gid)]
    return t.tip_utc.max() if len(t) else pd.Timestamp(0, tz="UTC")


def player(pid: str) -> dict:
    e = latest()
    pg = lake()["player_games"]
    g = pg[pg.player_id == pid].sort_values("tip_utc")
    if g.empty:
        return {"error": "unknown player"}
    rows = [{"date": r.date.strftime("%Y-%m-%d"), "season": int(r.season),
             "season_type": r.season_type, "team": r.team, "opp": r.opp, "home": bool(r.is_home),
             "participation": r.participation, "starter": bool(r.starter),
             **{k: (None if pd.isna(getattr(r, k)) else float(getattr(r, k)))
                for k in ("minutes", "pts", "reb", "ast", "fg3m")}}
            for r in g.tail(100).itertuples()]
    fc = [f for f in (e or {}).get("forecasts", []) if f["player_id"] == pid]
    last = g.iloc[-1]
    return {"player_id": pid, "name": last["name"], "team": last.team, "position": last.pos,
            "game_log": rows, "forecasts": fc,
            "edition_id": (e or {}).get("edition_id"), "extras": _player_extras(pid, e)}


def _player_extras(pid: str, e: dict | None) -> dict:
    """The player page's research context. A player in the latest edition gets the figures on
    his cards (the cached props extras, npl.nba.extras.player_view); anyone else his volume and
    game script as of today (extras.for_player)."""
    try:
        if e is not None and any(f["player_id"] == pid for f in e["forecasts"]):
            p = props()
            card = next((c for c in p.get("cards", []) if c["player_id"] == pid), None)
            return X.player_view(p.get("extras") or {}, card) if card else {}
        con = _con()
        return X.for_player(pid, lake(), con, lake_key=_lake_key()) if con is not None else {}
    except Exception as err:  # noqa: BLE001 - a display extra; never block the page
        log.warning("NBA player extras unavailable for %s: %r", pid, err)
        return {}


def game(gid: str) -> dict:
    e = latest()
    if e is None:
        return {"error": "no NBA edition"}
    g = next((x for x in e["games"] if x["game_id"] == gid), None)
    if g is None:
        return {"error": "game not in the latest NBA edition"}
    fc = [f for f in e["forecasts"] if f["game_id"] == gid and f["model"] == e["headline_model"]]
    players: dict[str, dict] = {}
    for f in fc:
        d = players.setdefault(f["player_id"], {k: f[k] for k in (
            "player_id", "name", "team", "position", "p_play", "injury", "minutes_expected")})
        d[f["market"]] = {"median": f["median"], "q10": f["q10"], "q90": f["q90"],
                          "mean": f["mean"]}
    opp = opponent_factors(F.slate_cutoff(e))  # the cards' ranks: none of tonight's games
    gl, dfn = None, {}
    con = _con()
    if con is not None:
        try:
            gl = X.stored_game_lines(con, {gid}).get(gid)
            dfn = X.team_defence(con, lake(), e["date_et"], {g["home"], g["away"]},
                                 lake_key=_lake_key())
        except Exception as err:  # noqa: BLE001 - a display extra; never block the page
            log.warning("NBA game extras unavailable for %s: %r", gid, err)
    return {"game": {**g, "tip_london": _london(g["tip_utc"])}, "edition_id": e["edition_id"],
            "players": sorted(players.values(), key=lambda d: -d["minutes_expected"]),
            "opp": {t: opp.get(t) for t in (g["home"], g["away"])},
            # pre-game consensus of the stored game lines (None when none were pulled) and each
            # team's defence additions (npl.nba.extras)
            "game_lines": gl, "defence": dfn}


def backtests() -> list[dict]:
    from ..config import REPORTS_DIR
    out = []
    for f in sorted((REPORTS_DIR / "nba" / "backtests").glob("*/summary.json")):
        d = json.loads(f.read_text())
        out.append({k: d[k] for k in ("label", "seasons", "markets", "participation",
                                      "rows_scored") if k in d})
    return out


def clean(o):
    if isinstance(o, dict):
        return {k: clean(v) for k, v in o.items()}
    if isinstance(o, list | tuple):
        return [clean(v) for v in o]
    if isinstance(o, float | np.floating):
        return None if np.isnan(o) else float(o)
    if isinstance(o, np.integer):
        return int(o)
    if isinstance(o, pd.Timestamp):
        return o.isoformat()
    return o


_MU: dict = {}


def matchups() -> dict:
    """Defensive-matchup payload (rebuilt when the shot tables change)."""
    from . import matchups as mu
    from . import shots

    key = (shots.SHOTS_LAKE / "def_shots.parquet").stat().st_mtime
    if _MU.get("key") != key:
        _MU.clear()
        _MU.update(key=key, data=clean(mu.payload()))
    return _MU["data"]


def shot_profile(pid: str, before_date: str | None = None) -> dict:
    """His shot profile. With `before_date` (an edition's US date) only games played before that
    date count, so the page shown with that edition never includes the game being forecast."""
    from . import matchups as mu

    pg = lake()["player_games"]
    g = pg[pg.player_id == pid]
    if g.empty:
        return {"error": "unknown player"}
    last = g.sort_values("tip_utc").iloc[-1]
    ids = None
    if before_date:
        ids = set(g[g.date < pd.Timestamp(before_date)].game_id.astype(str))
    return clean({"player_id": pid, "name": last["name"], "team": last.team,
                  "position": last.pos, "seasons": mu.player_profile(pid, game_ids=ids),
                  "games_before": before_date})


# ------------------------------------------------------------------ look: colours, photos
@lru_cache(maxsize=1)
def _team_colors(sha: str) -> dict:
    from .. import db

    b, _ = live._latest(db.connect(), "nba_player_box")
    b = b.drop_duplicates("team_abbreviation")
    return {r.team_abbreviation: [f"#{r.team_color}", f"#{r.team_alternate_color}"]
            for r in b.itertuples() if isinstance(r.team_color, str)}


def visuals(pids: set | None = None) -> dict:
    """Team colours and cached player photos (data URIs) for the app's NBA pages."""
    from .. import db, headshots

    sha = db.connect().execute("SELECT MAX(sha256) FROM raw_files WHERE dataset='nba_player_box'"
                               ).fetchone()[0] or ""
    if pids is None:
        pids = {c["player_id"] for c in props()["cards"]}
    return {"teams": _team_colors(sha), "photos": headshots.data_uris("nba", set(pids)),
            "logos": headshots.logo_uris("nba"),
            "logos_dark": headshots.logo_uris("nba", dark=True)}
