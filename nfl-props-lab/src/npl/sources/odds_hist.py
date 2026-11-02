"""Historical player-prop lines from The Odds API (paid plan), for testing the models against
real bookmaker prices on past seasons (D-040).

One call per game, at a fixed time before tip-off/kick-off (NBA: 3 hours; NFL: 2 hours), for
the core markets in the `us` region; the provider returns its snapshot at or before that time
(5-minute snapshots). Event ids come from one historical events call per date. Cost per game =
10 x markets returned (NBA up to 50, NFL up to 70); every call is checked against a hard floor
of remaining credits before it is made. Responses are stored unchanged in the raw store and the
parsed, linked rows in the `hist_quotes` table (both under data/, never in git: the provider's
terms forbid redistributing raw odds). Runs resume: games already fetched are skipped.

    uv run python -m npl.sources.odds_hist nba 2025 --dry-run
    uv run python -m npl.sources.odds_hist nba 2025 --floor 38000
"""

from __future__ import annotations

import argparse
import json
import sqlite3
import time

import pandas as pd

from .. import db
from ..util import FetchError, iso, log
from . import odds_api as oa

LEAD = {"nba": pd.Timedelta(hours=3), "nfl": pd.Timedelta(hours=2)}
SPORT_KEY = {"nba": "basketball_nba", "nfl": "americanfootball_nfl"}

SCHEMA = """
CREATE TABLE IF NOT EXISTS hist_quotes (
    id INTEGER PRIMARY KEY,
    sport TEXT NOT NULL, season INTEGER NOT NULL, game_id TEXT, provider_event_id TEXT NOT NULL,
    snapshot TEXT NOT NULL, requested_at TEXT NOT NULL, bookmaker TEXT NOT NULL,
    player_name_raw TEXT NOT NULL, player_id TEXT, link_method TEXT, market TEXT NOT NULL,
    alternate INTEGER NOT NULL, line REAL, selection TEXT NOT NULL, decimal_price REAL NOT NULL,
    quote_time TEXT, raw_file_id INTEGER
);
CREATE INDEX IF NOT EXISTS hist_quotes_game ON hist_quotes(sport, game_id);
CREATE TABLE IF NOT EXISTS hist_fetches (
    sport TEXT NOT NULL, season INTEGER NOT NULL, game_id TEXT NOT NULL, provider_event_id TEXT,
    requested_at TEXT, snapshot TEXT, rows INTEGER, credits_last INTEGER, status TEXT NOT NULL,
    fetched_at TEXT NOT NULL, PRIMARY KEY (sport, game_id)
);
"""


def ensure(con: sqlite3.Connection) -> None:
    con.executescript(SCHEMA)


# --------------------------------------------------------------------------- games to fetch
def nba_games(season: int, every: int = 1) -> pd.DataFrame:
    from ..nba import canon
    g = canon.load()["games"]
    g = g[(g.season == season) & (g.season_type == "REG")].copy()
    dates = sorted(g.date.unique())[::every]
    g = g[g.date.isin(dates)]
    return pd.DataFrame({"game_id": g.game_id.astype(str), "home": g.home, "away": g.away,
                         "tip": pd.to_datetime(g.tip_utc, utc=True), "date": g.date})


def nfl_games(season: int) -> pd.DataFrame:
    from .. import appdata
    g = appdata.lake()["games"]
    g = g[g.season == season].copy()
    return pd.DataFrame({"game_id": g.game_id.astype(str), "home": g.home_team,
                         "away": g.away_team, "tip": pd.to_datetime(g.kickoff_utc, utc=True),
                         "date": pd.to_datetime(g.kickoff_utc, utc=True).dt.tz_convert(
                             "America/New_York").dt.date})


def team_names(sport: str, con: sqlite3.Connection) -> dict:
    """Provider full team name -> our abbreviation."""
    if sport == "nba":
        from ..nba import live
        sched, _ = live._latest(con, "nba_schedule")
        m = dict(zip(sched.home_display_name, sched.home_abbreviation, strict=False))
        m.update(dict(zip(sched.away_display_name, sched.away_abbreviation, strict=False)))
        m.update(oa.NBA_NAME_ALIASES)
        return m
    from .. import appdata
    t = appdata.lake()["teams"]
    # the teams table lists some franchises twice (Rams: LA and LAR); keep the abbreviations the
    # schedule uses
    used = set(appdata.lake()["games"][["home_team", "away_team"]].to_numpy().ravel())
    t = t[t.team_abbr.isin(used)]
    return dict(zip(t.team_name, t.team_abbr, strict=False))


def rosters(sport: str, season: int) -> pd.DataFrame:
    """player_id, name, team for every player who took part in a game that season."""
    if sport == "nba":
        from ..nba import canon
        pg = canon.load()["player_games"]
        pg = pg[pg.season == season]
        return pg[["game_id", "player_id", "name", "team"]].astype({"game_id": str})
    from .. import appdata
    pg = appdata.lake()["pg"]
    pg = pg[pg.season == season]
    names = appdata.lake()["players"].set_index("gsis_id").display_name
    return pd.DataFrame({"game_id": pg.game_id.astype(str), "player_id": pg.player_id,
                         "name": pg.player_id.map(names).fillna(""), "team": pg.team})


# --------------------------------------------------------------------------- calls
def _call(con, sport, path, params, dataset, part):
    from .. import rawstore
    from ..util import http_get
    r = http_get(f"{oa.BASE}{path}", params={**params, "apiKey": oa.api_key()}, timeout=90,
                 max_tries=3)
    shown = f"{oa.BASE}{path}?" + "&".join(f"{k}={v}" for k, v in sorted(params.items()))
    sf = rawstore.store(con, source=oa.SOURCE, dataset=dataset, part=part, url=shown,
                        content=r.content, filename=f"{dataset}.json",
                        content_type=r.headers.get("Content-Type"))
    rem = r.headers.get("x-requests-remaining")
    last = r.headers.get("x-requests-last")
    return r.json(), (int(float(rem)) if rem else None), (int(float(last)) if last else None), sf.id


def events_at(con, sport: str, when: pd.Timestamp):
    """Historical events list at `when` (1 credit)."""
    return _call(con, sport, f"/historical/sports/{SPORT_KEY[sport]}/events",
                 {"date": when.strftime("%Y-%m-%dT%H:%M:%SZ")}, "historical_events",
                 f"{sport}:{when.isoformat()}")


def event_odds_at(con, sport: str, event_id: str, when: pd.Timestamp):
    params = {"markets": ",".join(oa.core_markets(sport)), "regions": "us",
              "oddsFormat": "decimal", "dateFormat": "iso",
              "date": when.strftime("%Y-%m-%dT%H:%M:%SZ")}
    return _call(con, sport, f"/historical/sports/{SPORT_KEY[sport]}/events/{event_id}/odds",
                 params, "historical_event_odds", f"{sport}:{event_id}:{params['date']}")


# --------------------------------------------------------------------------- run
def plan(sport: str, season: int, every: int = 1) -> dict:
    g = nba_games(season, every) if sport == "nba" else nfl_games(season)
    per = 10 * len(oa.core_markets(sport))
    return {"sport": sport, "season": season, "games": len(g), "dates": int(g.date.nunique()),
            "max_credits": int(len(g) * per + g.date.nunique())}


def run(sport: str, season: int, *, floor: int, every: int = 1, pause: float = 0.2,
        max_dates: int | None = None) -> dict:
    con = db.connect()
    ensure(con)
    games = nba_games(season, every) if sport == "nba" else nfl_games(season)
    done = {r[0] for r in con.execute("SELECT game_id FROM hist_fetches WHERE sport=? AND "
                                      "status IN ('ok','no_props','no_event')", (sport,))}
    todo = games[~games.game_id.isin(done)].sort_values("tip")
    names = team_names(sport, con)
    roster = rosters(sport, season)
    out = {"games_todo": len(todo), "fetched": 0, "rows": 0, "no_event": 0, "no_props": 0,
           "remaining": None}
    for k, (date, day) in enumerate(todo.groupby("date", sort=True)):
        if max_dates is not None and k >= max_dates:
            break
        # event ids: the provider's list a little before the day's first game
        when = day.tip.min() - LEAD[sport] - pd.Timedelta(hours=1)
        try:
            ev, rem, _, _ = events_at(con, sport, when)
        except FetchError as e:
            log.warning("events %s %s: %s", sport, date, e)
            continue
        out["remaining"] = rem
        evs = pd.DataFrame(ev.get("data", []))
        if len(evs):
            evs["home"] = evs.home_team.map(names)
            evs["away"] = evs.away_team.map(names)
            evs["commence"] = pd.to_datetime(evs.commence_time, utc=True)
        for g in day.itertuples():
            m = evs[(evs.home == g.home) & (evs.away == g.away) &
                    ((evs.commence - g.tip).abs() <= pd.Timedelta(hours=6))] if len(evs) else evs
            now = iso(pd.Timestamp.now(tz="UTC"))
            if m.empty:
                con.execute("INSERT OR REPLACE INTO hist_fetches VALUES (?,?,?,?,?,?,?,?,?,?)",
                            (sport, season, g.game_id, None, None, None, 0, 0, "no_event", now))
                con.commit()
                out["no_event"] += 1
                continue
            if out["remaining"] is not None and out["remaining"] - 10 * len(oa.core_markets(sport)) < floor:
                out["stopped"] = f"credit floor {floor} reached"
                return out
            eid = m.iloc[0]["id"]
            at = g.tip - LEAD[sport]
            try:
                j, rem, last, raw_id = event_odds_at(con, sport, eid, at)
            except FetchError as e:
                log.warning("odds %s %s: %s", sport, g.game_id, e)
                con.execute("INSERT OR REPLACE INTO hist_fetches VALUES (?,?,?,?,?,?,?,?,?,?)",
                            (sport, season, g.game_id, eid, iso(at), None, 0, None, "failed", now))
                con.commit()
                continue
            out["remaining"] = rem
            rows = oa.parse_event_odds(j, observed_at=j.get("timestamp") or iso(at),
                                       request_region="us", market_map=oa.SPORTS[sport][1])
            n = store(con, sport, season, g, eid, j.get("timestamp"), iso(at), rows, roster, raw_id)
            status = "ok" if n else "no_props"
            con.execute("INSERT OR REPLACE INTO hist_fetches VALUES (?,?,?,?,?,?,?,?,?,?)",
                        (sport, season, g.game_id, eid, iso(at), j.get("timestamp"), n, last,
                         status, now))
            con.commit()
            out["fetched"] += 1
            out["rows"] += n
            out["no_props"] += status == "no_props"
            time.sleep(pause)
        log.info("%s %s done; remaining %s", sport, date, out["remaining"])
    return out


def store(con, sport, season, g, eid, snapshot, requested, rows, roster, raw_id) -> int:
    if not rows:
        return 0
    q = pd.DataFrame(rows)
    r = roster[(roster.game_id == g.game_id)].drop_duplicates("player_id")[["player_id", "name", "team"]]
    q = oa.link_players(q, r)
    recs = [(sport, season, g.game_id, eid, snapshot or requested, requested, x.bookmaker,
             x.player_name_raw, x.player_id if isinstance(x.player_id, str) else None,
             x.link_method, x.market, int(bool(x.alternate)), x.line, x.selection,
             x.decimal_price, x.quote_time, raw_id) for x in q.itertuples()]
    con.executemany("INSERT INTO hist_quotes(sport, season, game_id, provider_event_id, snapshot,"
                    " requested_at, bookmaker, player_name_raw, player_id, link_method, market,"
                    " alternate, line, selection, decimal_price, quote_time, raw_file_id) VALUES"
                    " (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", recs)
    con.commit()
    return len(recs)


def main() -> None:
    from ..cli import load_dotenv
    load_dotenv()
    ap = argparse.ArgumentParser()
    ap.add_argument("sport", choices=["nba", "nfl"])
    ap.add_argument("season", type=int)
    ap.add_argument("--every", type=int, default=1, help="NBA: every n-th game date")
    ap.add_argument("--floor", type=int, default=2000, help="stop when credits would fall below")
    ap.add_argument("--max-dates", type=int, default=None, help="stop after this many dates")
    ap.add_argument("--dry-run", action="store_true")
    a = ap.parse_args()
    if a.dry_run:
        print(json.dumps(plan(a.sport, a.season, a.every)))
        return
    print(json.dumps(run(a.sport, a.season, floor=a.floor, every=a.every, max_dates=a.max_dates),
                     default=str))


if __name__ == "__main__":
    main()
