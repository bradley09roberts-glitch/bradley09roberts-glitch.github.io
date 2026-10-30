"""Read-only NBA views for the app: slate, prop cards (with recent game logs for hit rates),
player and game pages, and opponent factors. Built from the latest NBA edition and the lake."""

from __future__ import annotations

import json
from functools import lru_cache

import numpy as np
import pandas as pd

from ..config import LONDON
from . import canon, live
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


def game_logs(pids: set[str], before: str) -> dict[str, pd.DataFrame]:
    pg = lake()["player_games"]
    pg = pg[pg.player_id.isin(pids) & (pg.participation == "played") &
            (pg.tip_utc < pd.Timestamp(before))].sort_values("tip_utc")
    return {pid: g for pid, g in pg.groupby("player_id")}


def opponent_factors(at: str) -> dict:
    """Model opponent factor per team and stat (shrunk as N1 uses it), with ranks
    (1 = allows least)."""
    L = lake()
    p = M.load_params()
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


def market_lines(game_ids: set[str]) -> dict:
    """Latest stored quotes per (player, market) for these games: the most common line, the
    market's over chance with the bookmaker margin removed (average over books quoting both
    sides), best prices and when they were seen. US books are a research reference only."""
    from .. import db
    from ..sources import odds_api

    try:
        q = odds_api.quotes_frame(db.connect())
    except Exception:  # noqa: BLE001 - no database or table: no lines
        return {}
    return consensus_lines(q[q.game_id.isin(game_ids)])


def consensus_lines(q: pd.DataFrame) -> dict:
    """Only the most recent collection for each game and market counts: a line a bookmaker
    offered earlier but no longer shows (it moved 39.5 -> 38.5) must not stay in the mix."""
    q = q[q.player_id.notna() & q.market.isin(M.MARKETS)]
    if q.empty:
        return {}
    q = q[q.observed_at == q.groupby(["game_id", "market"]).observed_at.transform("max")]
    q = q.sort_values("observed_at").drop_duplicates(
        ["bookmaker", "player_id", "market", "line", "selection"], keep="last")
    out = {}
    for (pid, mk), x in q.groupby(["player_id", "market"]):
        line = x.line.mode().iloc[0]
        y = x[x.line == line].pivot_table(index="bookmaker", columns="selection",
                                          values="decimal_price", aggfunc="last")
        if not {"over", "under"} <= set(y.columns):
            continue
        y = y.dropna()
        if y.empty:
            continue
        fair = (1 / y.over) / (1 / y.over + 1 / y.under)
        out[(pid, mk)] = {"line": float(line), "fair_over": round(float(fair.mean()), 4),
                          "best_over": float(y.over.max()), "best_under": float(y.under.max()),
                          "books": int(len(y)), "margin": round(float((1 / y.over + 1 / y.under
                                                                        - 1).mean()), 4),
                          "observed_at": str(x.observed_at.max()),
                          "availability": "research_reference" if (x.availability !=
                                                                   "verified_uk").any()
                          else "verified_uk"}
    return out


def slate() -> dict:
    e = latest()
    if e is None:
        return {"error": "no NBA edition yet"}
    games = [{**g, "tip_london": _london(g["tip_utc"])} for g in e["games"]]
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
        q = con.execute("SELECT (SELECT MAX(id) FROM quotes), (SELECT MAX(id) FROM quote_links)"
                        ).fetchone()
        q = tuple(q)
    except Exception:  # noqa: BLE001 - no database: lines are simply absent
        q = None
    lk = (canon.NBA_LAKE / "player_games.parquet").stat().st_mtime
    return (str(p), p.stat().st_mtime if p else None, lk, q)


def props() -> dict:
    """Prop cards for the latest edition, cached until the edition, lake or stored quotes
    change."""
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
    logs = game_logs({f["player_id"] for f in fc}, e["published_at"])
    games = {g["game_id"]: g for g in e["games"]}
    opp = opponent_factors(e["published_at"])
    mkt = market_lines(set(games))
    season = e["date_et"][:4]
    out_by_team: dict[str, list] = {}
    for f in fc:
        if f["market"] == "pts" and f.get("injury") and f["injury"]["fantasy"] in ("OUT", "OFS"):
            lg = logs.get(f["player_id"])
            out_by_team.setdefault(f["team"], []).append(
                {"name": f["name"], "player_id": f["player_id"], "minutes": f["minutes_expected"],
                 "played_last": bool(lg is not None and len(lg) and
                                     lg.tip_utc.iloc[-1] >= _team_last_tip(f["team"], e))})
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
                                 "market", "p_play", "injury", "minutes_expected", "mean",
                                 "median", "q10", "q90", "dist")},
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
            # only absences that recent minutes cannot reflect yet (he played last game)
            "teammates_out": [t for t in out_by_team.get(f["team"], [])
                              if t["name"] != f["name"] and t["minutes"] >= 20 and
                              t["played_last"]],
            "wowy": _wowy(f, lg, logs, out_by_team.get(f["team"], []), int(season) + 1
                          if int(e["date_et"][5:7]) >= 8 else int(season))})
    return {"edition_id": e["edition_id"], "date_et": e["date_et"], "cards": cards,
            "markets": {k: M.MARKET_LABELS[k] for k in M.MARKETS},
            "published_london": _london(e["published_at"])}


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
    tg = lake()["team_games"]
    t = tg[(tg.team == team) & (tg.tip_utc < pd.Timestamp(e["published_at"]))]
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
            "edition_id": (e or {}).get("edition_id")}


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
    opp = opponent_factors(e["published_at"])
    return {"game": {**g, "tip_london": _london(g["tip_utc"])}, "edition_id": e["edition_id"],
            "players": sorted(players.values(), key=lambda d: -d["minutes_expected"]),
            "opp": {t: opp.get(t) for t in (g["home"], g["away"])}}


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


def shot_profile(pid: str) -> dict:
    from . import matchups as mu

    pg = lake()["player_games"]
    g = pg[pg.player_id == pid]
    if g.empty:
        return {"error": "unknown player"}
    last = g.sort_values("tip_utc").iloc[-1]
    return clean({"player_id": pid, "name": last["name"], "team": last.team,
                  "position": last.pos, "seasons": mu.player_profile(pid)})


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
