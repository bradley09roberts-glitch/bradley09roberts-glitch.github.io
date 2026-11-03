"""The Odds API adapter (https://the-odds-api.com/, v4). Validated against live responses on
26 Oct 2026 (events, event markets for regions uk and us, event odds for regions us).

Design rules
* The key is read from the environment (ODDS_API_KEY) server-side only; it is redacted
  from every stored URL, log line and error.
* Every response is stored raw and immutable (source 'the_odds_api') before parsing, with
  the credit headers recorded.
* Quotes are classified:
    verified_uk         bookmaker in the provider's `uk` region list (audited 25 Oct 2026)
                        AND returned for a request made with regions=uk
    research_reference  anything else (e.g. US books). Never presented as UK-accessible.
    synthetic_test      fixtures used by tests; never enter evaluation (rawstore excludes
                        synthetic files from `latest`, and money.py refuses them).
* Players are linked to GSIS ids only through an audited rule (normalised full name,
  unique within the two teams' rosters for that week); ambiguous or unmatched names stay
  unlinked (player_id NULL) and cannot be bet.
* Current odds are not historical coverage: only stored snapshots count as history.
* Credits: every pull opens with the free events call, whose x-requests-remaining header
  gives the credits left (the free sports list is asked if that header is missing). No paid
  call is made whose estimated cost would leave fewer than the floor (env NPL_ODDS_FLOOR,
  default 400), or while the credits left are unknown; skipped calls are listed (`Budget`).
* Game lines (moneyline, spread, total) for a whole slate come from one featured-markets call
  (3 credits for one region) into the append-only `game_lines` table (snapshot_game_lines).
* Closing snapshots (snapshot_close) re-price, shortly before tip-off, only the games and
  markets with props on the paper record, so track.clv has a late snapshot to compare with.
"""

from __future__ import annotations

import json
import os
import re
import sqlite3
import unicodedata
from dataclasses import dataclass, field

import pandas as pd

from .. import db, rawstore
from ..util import http_get, iso, log, parse_iso, utcnow

SOURCE = "the_odds_api"
BASE = "https://api.the-odds-api.com/v4"
SPORT = "americanfootball_nfl"

# provider market key -> (our market key, is_alternate)
MARKET_MAP = {
    "player_pass_yds": ("pass_yds", False), "player_pass_yds_alternate": ("pass_yds", True),
    "player_pass_attempts": ("pass_att", False),
    "player_pass_attempts_alternate": ("pass_att", True),
    "player_pass_completions": ("pass_cmp", False),
    "player_pass_completions_alternate": ("pass_cmp", True),
    "player_rush_yds": ("rush_yds", False), "player_rush_yds_alternate": ("rush_yds", True),
    "player_rush_attempts": ("rush_att", False),
    "player_rush_attempts_alternate": ("rush_att", True),
    "player_receptions": ("receptions", False),
    "player_receptions_alternate": ("receptions", True),
    "player_reception_yds": ("rec_yds", False),
    "player_reception_yds_alternate": ("rec_yds", True),
}
CORE_PROVIDER_MARKETS = [k for k, (_, alt) in MARKET_MAP.items() if not alt]
NBA_MARKET_MAP = {
    "player_points": ("pts", False), "player_points_alternate": ("pts", True),
    "player_rebounds": ("reb", False), "player_rebounds_alternate": ("reb", True),
    "player_assists": ("ast", False), "player_assists_alternate": ("ast", True),
    "player_threes": ("fg3m", False), "player_threes_alternate": ("fg3m", True),
    "player_points_rebounds_assists": ("pra", False),
    "player_points_rebounds_assists_alternate": ("pra", True),
}
SPORTS = {"nfl": ("americanfootball_nfl", MARKET_MAP), "nba": ("basketball_nba", NBA_MARKET_MAP)}
# provider team names that differ from the schedule's display names
NBA_NAME_ALIASES = {"Los Angeles Clippers": "LAC"}
# featured (game) markets: provider key -> our name in game_lines
GAME_MARKETS = {"h2h": "moneyline", "spreads": "spread", "totals": "total"}


def core_markets(sport: str) -> list[str]:
    return [k for k, (_, alt) in SPORTS[sport][1].items() if not alt]


def provider_markets(sport: str) -> dict[str, str]:
    """Our market key -> the provider's main-line key (alternate lines excluded)."""
    return {ours: k for k, (ours, alt) in SPORTS[sport][1].items() if not alt}

# The provider's `uk` region bookmakers (bookmaker-apis page, retrieved 25 Oct 2026).
UK_BOOKMAKERS = {
    "sport888", "betano_uk", "betfair_ex_uk", "betfair_sb_uk", "betfred_uk", "betvictor",
    "betway", "boylesports", "casumo", "coral", "grosvenor", "ladbrokes_uk", "leovegas",
    "livescorebet", "matchbook", "paddypower", "skybet", "smarkets", "unibet_uk", "virginbet",
    "williamhill",
}
EXCHANGES = {"betfair_ex_uk", "matchbook", "smarkets"}  # commission applies; not a bookmaker


class MissingKey(RuntimeError):
    pass


def api_key() -> str:
    k = os.environ.get("ODDS_API_KEY", "").strip()
    if not k:
        raise MissingKey("ODDS_API_KEY is not set: odds collection is blocked (no ROI claims)")
    return k


@dataclass
class Call:
    endpoint: str
    params: dict


# ----------------------------------------------------------------------------- credits
FLOOR_ENV = "NPL_ODDS_FLOOR"
DEFAULT_FLOOR = 400


def credit_floor() -> int:
    """Credits that must still be left after any paid call: env NPL_ODDS_FLOOR, default 400."""
    v = os.environ.get(FLOOR_ENV, "").strip()
    if not v:
        return DEFAULT_FLOOR
    try:
        return int(v)
    except ValueError:
        raise ValueError(f"{FLOOR_ENV} must be a whole number of credits, not {v!r}") from None


def credits_left(credits: dict | None) -> int | None:
    """The x-requests-remaining header as a number (None when absent or unreadable)."""
    v = (credits or {}).get("x-requests-remaining")
    try:
        return int(float(v))
    except (TypeError, ValueError):
        return None


def n_regions(regions: str) -> int:
    return len([r for r in regions.split(",") if r.strip()]) or 1


@dataclass
class Budget:
    """Credits left, as the provider's x-requests-remaining header last reported them, and the
    floor no paid call may take them below. The free events call that opens a pull sets
    `remaining`; `allow` is asked before every paid call with that call's estimated (largest)
    cost and refuses, recording what was skipped, when the credits left would fall below the
    floor or are unknown."""

    floor: int = field(default_factory=credit_floor)
    remaining: int | None = None
    skipped: list[dict] = field(default_factory=list)

    def observe(self, credits: dict | None) -> None:
        r = credits_left(credits)
        if r is not None:
            self.remaining = r

    def allow(self, cost: int, what: str) -> bool:
        if self.remaining is None:
            why = "credits left unknown (no x-requests-remaining header)"
        elif self.remaining - cost < self.floor:
            why = (f"{self.remaining} credits left: {cost} more would go below the floor of "
                   f"{self.floor}")
        else:
            return True
        self.skipped.append({"call": what, "estimated_credits": cost,
                             "credits_left": self.remaining, "floor": self.floor, "reason": why})
        log.info("odds call skipped: %s (%s)", what, why)
        return False

    def spent(self, cost: int, credits: dict | None) -> None:
        """After a paid call: the header's figure, or the estimate taken off if it is absent."""
        r = credits_left(credits)
        if r is not None:
            self.remaining = r
        elif self.remaining is not None:
            self.remaining -= cost


def _get(con: sqlite3.Connection, dataset: str, part: str, path: str, params: dict,
         session=None) -> tuple[dict | list, dict, int]:
    """Returns (parsed JSON, credit headers, raw_files id of the stored response)."""
    key = api_key()
    url = f"{BASE}{path}"
    r = http_get(url, params={**params, "apiKey": key}, session=session, timeout=60)
    credits = {h: r.headers.get(h) for h in ("x-requests-remaining", "x-requests-used",
                                             "x-requests-last")}
    shown = f"{url}?" + "&".join(f"{k}={v}" for k, v in sorted(params.items()))
    stored = rawstore.store(con, source=SOURCE, dataset=dataset, part=part, url=shown,
                            content=r.content, filename=f"{dataset}.json",
                            content_type=r.headers.get("Content-Type"))
    return r.json(), credits, stored.id


def list_events(con, session=None, sport: str = "nfl"):
    """Free: the sport's listed events (with the credit headers)."""
    return _get(con, "events", f"{sport}:{iso(utcnow())}",
                f"/sports/{SPORTS[sport][0]}/events", {}, session)


def list_sports(con, session=None):
    """Free: the provider's sport list (with the credit headers)."""
    return _get(con, "sports", iso(utcnow()), "/sports", {}, session)


def _start(con, budget: Budget, session, sport: str) -> tuple[list, dict]:
    """The free events call that opens a pull: the sport's events, and the credits left from its
    header (or, if that header is missing, from the free sports list)."""
    events, credits, _ = list_events(con, session, sport)
    budget.observe(credits)
    if budget.remaining is None:
        _, credits, _ = list_sports(con, session)
        budget.observe(credits)
    return events, credits


def featured_odds(con, sport: str = "nba", regions: str = "us", session=None):
    """Moneyline, spread and total for every listed event of a sport in one call (featured
    markets h2h, spreads, totals). Cost = markets x regions: 3 credits for one region, nothing
    when no events are listed."""
    params = {"regions": regions, "markets": ",".join(GAME_MARKETS), "oddsFormat": "decimal",
              "dateFormat": "iso"}
    return _get(con, "featured_odds", f"{sport}:{regions}:{iso(utcnow())}",
                f"/sports/{SPORTS[sport][0]}/odds", params, session)


def event_markets(con, event_id: str, regions: str = "uk", session=None, sport: str = "nfl"):
    """1 credit: which markets each bookmaker currently offers for an event."""
    return _get(con, "event_markets", f"{event_id}:{regions}",
                f"/sports/{SPORTS[sport][0]}/events/{event_id}/markets", {"regions": regions},
                session)


def event_odds(con, event_id: str, markets: list[str], regions: str = "uk",
               bookmakers: list[str] | None = None, session=None, sport: str = "nfl"):
    """Cost = unique markets returned x regions. Props are one event per call."""
    params = {"markets": ",".join(markets), "oddsFormat": "decimal", "dateFormat": "iso"}
    if bookmakers:
        params["bookmakers"] = ",".join(bookmakers)
    else:
        params["regions"] = regions
    return _get(con, "event_odds", f"{event_id}:{regions}:{iso(utcnow())}",
                f"/sports/{SPORTS[sport][0]}/events/{event_id}/odds", params, session)


def historical_event_odds(con, event_id: str, date_iso: str, markets: list[str],
                          regions: str = "us", session=None, sport: str = "nfl"):
    """Paid plans only. Cost = 10 x unique markets returned x regions."""
    params = {"markets": ",".join(markets), "oddsFormat": "decimal", "dateFormat": "iso",
              "regions": regions, "date": date_iso}
    return _get(con, "historical_event_odds", f"{event_id}:{regions}:{date_iso}",
                f"/historical/sports/{SPORTS[sport][0]}/events/{event_id}/odds", params, session)


# ----------------------------------------------------------------------------- parsing
def availability(bookmaker: str, request_region: str, synthetic: bool = False) -> str:
    if synthetic:
        return "synthetic_test"
    if bookmaker in UK_BOOKMAKERS and request_region == "uk":
        return "verified_uk"
    return "research_reference"


def parse_event_odds(payload: dict, *, observed_at: str, request_region: str,
                     synthetic: bool = False, market_map: dict | None = None) -> list[dict]:
    """Flatten an event-odds response into quote rows (one per outcome)."""
    data = payload.get("data", payload)  # historical responses wrap the event in `data`
    market_map = market_map or MARKET_MAP
    out = []
    for bk in data.get("bookmakers", []):
        for mk in bk.get("markets", []):
            if mk.get("key") not in market_map:
                continue
            ours, alt = market_map[mk["key"]]
            for o in mk.get("outcomes", []):
                name = str(o.get("name", "")).lower()
                sel = {"over": "over", "under": "under", "yes": "yes", "no": "no"}.get(name)
                price = o.get("price")
                if sel is None or price is None or float(price) <= 1.0:
                    continue
                out.append({
                    "source": SOURCE, "bookmaker": bk.get("key"),
                    "bookmaker_region": request_region,
                    "availability": availability(bk.get("key"), request_region, synthetic),
                    "provider_event_id": data.get("id"), "home_team": data.get("home_team"),
                    "away_team": data.get("away_team"),
                    "commence_time": data.get("commence_time"),
                    "player_name_raw": o.get("description") or "", "market": ours,
                    "alternate": alt, "line": o.get("point"), "selection": sel,
                    "decimal_price": float(price),
                    "quote_time": mk.get("last_update") or bk.get("last_update"),
                    "observed_at": observed_at,
                })
    return out


def parse_game_lines(payload: list | dict, *, observed_at: str, request_region: str,
                     sport: str, synthetic: bool = False) -> list[dict]:
    """Flatten a featured-markets (/odds) response into game_lines rows: one per event,
    bookmaker, market and outcome. `outcome` keeps the provider's name (a team, Over or
    Under); `side` reads it as home / away (by the event's team names), over, under or draw,
    and stays None for anything else. `point` is the spread or total (None for moneylines)."""
    events = payload.get("data", []) if isinstance(payload, dict) else payload
    out = []
    for ev in events:
        home, away = ev.get("home_team"), ev.get("away_team")
        for bk in ev.get("bookmakers", []):
            for mk in bk.get("markets", []):
                market = GAME_MARKETS.get(mk.get("key"))
                if market is None:
                    continue
                for o in mk.get("outcomes", []):
                    price = o.get("price")
                    if price is None or float(price) <= 1.0:
                        continue
                    name = str(o.get("name", ""))
                    side = ("home" if name == home else "away" if name == away else
                            {"over": "over", "under": "under", "draw": "draw"}.get(name.lower()))
                    out.append({
                        "source": SOURCE, "sport": sport, "event_id": ev.get("id"),
                        "commence_time": ev.get("commence_time"), "home_team": home,
                        "away_team": away, "bookmaker": bk.get("key"),
                        "bookmaker_region": request_region,
                        "availability": availability(bk.get("key"), request_region, synthetic),
                        "market": market, "outcome": name, "side": side,
                        "point": o.get("point"), "price": float(price),
                        "quote_time": mk.get("last_update") or bk.get("last_update"),
                        "observed_at": observed_at,
                    })
    return out


def norm_name(s: str) -> str:
    s = unicodedata.normalize("NFKD", s).encode("ascii", "ignore").decode().lower()
    s = re.sub(r"[.'`’]", "", s)
    s = re.sub(r"\b(jr|sr|ii|iii|iv|v)\b", "", s)
    return re.sub(r"\s+", " ", s).strip()


def link_players(quotes: pd.DataFrame, roster: pd.DataFrame) -> pd.DataFrame:
    """roster: rows (player_id, name, team) for the two teams that week.
    Links a quote only when the normalised name is unique among them."""
    r = roster.assign(key=roster["name"].map(norm_name))
    counts = r.groupby("key").player_id.nunique()
    unique = r[r.key.map(counts) == 1].drop_duplicates("key").set_index("key")["player_id"]
    q = quotes.copy()
    q["name_key"] = q["player_name_raw"].map(norm_name)
    q["player_id"] = q["name_key"].map(unique)
    q["link_method"] = q["player_id"].notna().map(
        {True: "normalised full name, unique within both teams' weekly rosters",
         False: "UNLINKED (no unique roster match)"})
    return q


def link_event(quotes: pd.DataFrame, games: pd.DataFrame, teams: pd.DataFrame) -> pd.DataFrame:
    """Map provider events to game_id by full team names and kickoff within 6 hours."""
    names = teams.set_index("team_name")["team_abbr"].to_dict()
    q = quotes.copy()
    q["home"] = q["home_team"].map(names)
    q["away"] = q["away_team"].map(names)
    q["commence"] = pd.to_datetime(q["commence_time"], utc=True)
    q["_qi"] = range(len(q))
    g = games[["game_id", "home_team", "away_team", "kickoff_utc"]]
    m = q.merge(g, left_on=["home", "away"], right_on=["home_team", "away_team"], how="left",
                suffixes=("", "_g"))
    # the same fixture recurs every season: keep, per quote, only the game within 6 hours
    m["_ok"] = (m["kickoff_utc"] - m["commence"]).abs() <= pd.Timedelta(hours=6)
    m = m.sort_values(["_qi", "_ok"], ascending=[True, False]).drop_duplicates("_qi")
    m.loc[~m["_ok"], ["game_id", "kickoff_utc"]] = None
    return m.drop(columns=["home_team_g", "away_team_g", "_qi", "_ok"],
                  errors="ignore").reset_index(drop=True)


def store_quotes(con: sqlite3.Connection, rows: pd.DataFrame, raw_file_id: int | None) -> int:
    cols = ["source", "bookmaker", "bookmaker_region", "availability", "provider_event_id",
            "game_id", "player_name_raw", "player_id", "market", "line", "selection",
            "decimal_price", "quote_time", "observed_at"]
    n = 0
    with db.tx(con):
        for r in rows[cols].itertuples(index=False):
            cur = con.execute(
                f"INSERT OR IGNORE INTO quotes({', '.join(cols)}, raw_file_id) VALUES "
                f"({', '.join('?' * len(cols))}, ?)", (*[None if pd.isna(v) else v for v in r],
                                                        raw_file_id))
            n += cur.rowcount
    return n


def quotes_frame(con: sqlite3.Connection, include_synthetic: bool = False) -> pd.DataFrame:
    q = pd.read_sql_query("SELECT * FROM quotes", con)
    links = pd.read_sql_query("SELECT quote_id, game_id, player_id FROM quote_links WHERE id IN "
                              "(SELECT MAX(id) FROM quote_links GROUP BY quote_id)", con)
    if len(links):
        lk = links.set_index("quote_id")
        has = q.id.isin(lk.index)
        q.loc[has, "game_id"] = q.loc[has, "id"].map(lk.game_id)
        q.loc[has, "player_id"] = q.loc[has, "id"].map(lk.player_id)
    if not include_synthetic:
        q = q[q.availability != "synthetic_test"]
    return q


GAME_LINE_COLS = ["source", "sport", "event_id", "game_id", "commence_time", "home_team",
                  "away_team", "bookmaker", "bookmaker_region", "availability", "market",
                  "outcome", "side", "point", "price", "quote_time", "observed_at"]


def store_game_lines(con: sqlite3.Connection, rows: pd.DataFrame, raw_id: int | None) -> int:
    """Append game-line rows; a row already stored from the same response adds nothing."""
    n = 0
    with db.tx(con):
        for r in rows.reindex(columns=GAME_LINE_COLS).itertuples(index=False):
            cur = con.execute(
                f"INSERT OR IGNORE INTO game_lines({', '.join(GAME_LINE_COLS)}, raw_id) VALUES "
                f"({', '.join('?' * len(GAME_LINE_COLS))}, ?)",
                (*[None if pd.isna(v) else v for v in r], raw_id))
            n += cur.rowcount
    return n


def game_lines_frame(con: sqlite3.Connection, sport: str | None = None, *, pregame: bool = False,
                     include_synthetic: bool = False) -> pd.DataFrame:
    """Stored game lines. The featured call also returns games already under way, whose lines
    are in-play: pregame=True keeps only rows observed before the event's start time."""
    sql = "SELECT * FROM game_lines" + (" WHERE sport = ?" if sport else "") + " ORDER BY id"
    g = pd.read_sql_query(sql, con, params=(sport,) if sport else ())
    if not include_synthetic:
        g = g[g.availability != "synthetic_test"]
    if pregame and len(g):
        g = g[pd.to_datetime(g.observed_at, utc=True, format="ISO8601")
              < pd.to_datetime(g.commence_time, utc=True, format="ISO8601")]
    return g.reset_index(drop=True)


def status(con: sqlite3.Connection) -> dict:
    """What the UI shows about odds: blocked, or what is held."""
    try:
        api_key()
        keyed = True
    except MissingKey:
        keyed = False
    q = quotes_frame(con)
    gl = con.execute("SELECT COUNT(*), MAX(observed_at) FROM game_lines WHERE "
                     "availability != 'synthetic_test'").fetchone()
    return {"key_configured": keyed, "quotes": len(q),
            "verified_uk_quotes": int((q.availability == "verified_uk").sum()),
            "research_reference_quotes": int((q.availability == "research_reference").sum()),
            "first_observed": q.observed_at.min() if len(q) else None,
            "last_observed": q.observed_at.max() if len(q) else None,
            "game_lines": gl[0], "game_lines_last_observed": gl[1],
            "blocker": None if keyed else "No ODDS_API_KEY configured; ROI unavailable."}


def link_quotes(rows: list[dict], lake_dir=None) -> pd.DataFrame:
    """Link parsed quotes to a game (team names + kickoff within 6 hours) and players
    (unique normalised name within both teams' weekly rosters). Unlinked quotes keep NULL
    ids and can never be used for a bet."""
    from ..config import LAKE_DIR

    lake_dir = lake_dir or LAKE_DIR
    q = pd.DataFrame(rows)
    if q.empty:
        return q
    games = _nfl_games(lake_dir)
    q = link_event(q, games, pd.read_parquet(lake_dir / "teams.parquet"))
    mem = pd.read_parquet(lake_dir / "membership.parquet",
                          columns=["season", "week", "team", "player_id", "full_name"])
    parts = []
    for gid, x in q.groupby(q.game_id.fillna("")):
        g = games[games.game_id == gid]
        if g.empty:
            parts.append(x.assign(player_id=None, link_method="UNLINKED (no matching game)"))
            continue
        g = g.iloc[0]
        roster = mem[(mem.season == g.season) & (mem.week == g.week) &
                     mem.team.isin([g.home_team, g.away_team])].rename(
            columns={"full_name": "name"})[["player_id", "name", "team"]]
        parts.append(link_players(x, roster))
    return pd.concat(parts, ignore_index=True)


def _nfl_games(lake_dir) -> pd.DataFrame:
    games = pd.read_parquet(lake_dir / "games.parquet")
    games["kickoff_utc"] = pd.to_datetime(games.kickoff_utc, utc=True)
    return games


def link_events_nba(q: pd.DataFrame, con: sqlite3.Connection) -> pd.DataFrame:
    """NBA: provider event -> SportsDataverse game id by full team names (with
    NBA_NAME_ALIASES) and tip-off within 6 hours. Adds home and away (abbreviations) and
    game_id (None when no game matches)."""
    from ..nba import live as nba_live

    sched, _ = nba_live._latest(con, "nba_schedule")
    names = dict(zip(sched.home_display_name, sched.home_abbreviation, strict=False))
    names.update(dict(zip(sched.away_display_name, sched.away_abbreviation, strict=False)))
    names.update(NBA_NAME_ALIASES)
    sg = pd.DataFrame({"game_id": sched.game_id.astype("int64").astype(str),
                       "home": sched.home_abbreviation, "away": sched.away_abbreviation,
                       "tip": pd.to_datetime(sched.date, utc=True)})
    q = q.copy()
    q["home"], q["away"] = q.home_team.map(names), q.away_team.map(names)
    q["commence"] = pd.to_datetime(q.commence_time, utc=True)
    q["_qi"] = range(len(q))
    m = q.merge(sg, on=["home", "away"], how="left")
    m["_ok"] = (m.tip - m.commence).abs() <= pd.Timedelta(hours=6)
    m = m.sort_values(["_qi", "_ok"], ascending=[True, False]).drop_duplicates("_qi")
    m.loc[~m._ok, "game_id"] = None
    return m.drop(columns=["_qi", "_ok", "tip"]).reset_index(drop=True)


def link_games(rows: list[dict], con: sqlite3.Connection, sport: str,
               lake_dir=None) -> pd.DataFrame:
    """Provider events -> our game ids, matched exactly as prop quotes are (NBA: the
    SportsDataverse schedule; NFL: the lake's games and team names, kick-off within 6 hours)."""
    q = pd.DataFrame(rows)
    if q.empty:
        return q
    if sport == "nba":
        return link_events_nba(q, con)
    from ..config import LAKE_DIR

    lake_dir = lake_dir or LAKE_DIR
    return link_event(q, _nfl_games(lake_dir), pd.read_parquet(lake_dir / "teams.parquet"))


def link_quotes_nba(rows: list[dict], con: sqlite3.Connection) -> pd.DataFrame:
    """NBA: event -> game via the SportsDataverse schedule (full team names, tip-off within
    6 hours); player -> unique normalised name among both teams' recent box-score listings."""
    from ..nba import canon as nba_canon

    q = pd.DataFrame(rows)
    if q.empty:
        return q
    m = link_events_nba(q, con)
    pg = nba_canon.load()["player_games"]
    pg = pg[pg.season == pg.season.max()]
    parts = []
    for gid, x in m.groupby(m.game_id.fillna("")):
        if not gid:
            parts.append(x.assign(player_id=None, link_method="UNLINKED (no matching game)"))
            continue
        teams = [x.home.iloc[0], x.away.iloc[0]]
        roster = pg[pg.team.isin(teams)].drop_duplicates("player_id")[
            ["player_id", "name", "team"]]
        parts.append(link_players(x, roster))
    return pd.concat(parts, ignore_index=True).drop(columns=["_qi", "_ok", "tip"],
                                                    errors="ignore")


def _link_summary(q: pd.DataFrame) -> dict:
    return {"quotes": len(q), "linked_players": int(q.player_id.notna().sum()),
            "unlinked_names": sorted(q.loc[q.player_id.isna(), "player_name_raw"].unique())[:20],
            "game_ids": sorted(q.game_id.dropna().unique())}


def link_and_store(con: sqlite3.Connection, rows: list[dict], raw_file_id: int | None,
                   lake_dir=None, sport: str = "nfl") -> dict:
    q = link_quotes_nba(rows, con) if sport == "nba" else link_quotes(rows, lake_dir)
    if q.empty:
        return {"quotes": 0, "stored": 0, "linked_players": 0}
    return {**_link_summary(q), "stored": store_quotes(con, q, raw_file_id)}


def relink_raw(con: sqlite3.Connection, raw_file_id: int, lake_dir=None) -> dict:
    """Re-link the quotes of one stored event-odds response (e.g. after a linking fix).
    Quote rows are append-only and stay as stored; corrected game/player ids are appended
    to quote_links and supersede them in `quotes_frame`."""
    row = con.execute("SELECT path, url, retrieved_at FROM raw_files WHERE id = ? AND "
                      "source = ? AND dataset = 'event_odds'", (raw_file_id, SOURCE)).fetchone()
    if row is None:
        raise ValueError(f"raw file {raw_file_id} is not a stored event-odds response")
    path, url, retrieved_at = row
    region = re.search(r"regions=([a-z,]+)", url).group(1)
    payload = json.loads((rawstore.DATA_HOME / path).read_text())
    sport = "nba" if SPORTS["nba"][0] in url else "nfl"
    rows = parse_event_odds(payload, observed_at=retrieved_at, request_region=region,
                            market_map=SPORTS[sport][1])
    q = link_quotes_nba(rows, con) if sport == "nba" else link_quotes(rows, lake_dir)
    have = quotes_frame(con, include_synthetic=True)
    have = have[have.raw_file_id == raw_file_id]
    key = ["bookmaker", "provider_event_id", "player_name_raw", "market", "line",
           "selection", "quote_time", "decimal_price"]
    norm = {"line": -1e9, "quote_time": ""}
    m = have.fillna(norm).merge(q.fillna(norm)[key + ["game_id", "player_id", "link_method"]],
                                on=key, suffixes=("_old", ""))
    changed = m[(m.game_id.fillna("") != m.game_id_old.fillna("")) |
                (m.player_id.fillna("") != m.player_id_old.fillna(""))]
    now = iso(utcnow())
    with db.tx(con):
        for r in changed.itertuples():
            con.execute("INSERT INTO quote_links(quote_id, game_id, player_id, link_method, "
                        "linked_at) VALUES (?, ?, ?, ?, ?)",
                        (int(r.id), None if pd.isna(r.game_id) else r.game_id,
                         None if pd.isna(r.player_id) else r.player_id, r.link_method, now))
    return {**_link_summary(q), "matched_rows": len(m), "links_added": len(changed)}


def snapshot_upcoming(con, *, regions: str = "us", hours_ahead: float = 48, session=None,
                      max_events: int = 20, markets: list[str] | None = None,
                      event_ids: list[str] | None = None, sport: str = "nfl",
                      budget: Budget | None = None, lake_dir=None) -> dict:
    """Collect current prop quotes for events kicking off within `hours_ahead`.
    One event-odds call per event for the wanted markets, with no `markets` pre-call: the cost
    counts only the unique markets returned (x regions) and an event without props returns
    nothing and costs nothing, so the 1-credit pre-call only added cost. Core markets only by
    default (alternate lines cost extra credits). The free events call that opens the pull reads
    the credits left; a call whose estimated cost (markets requested x regions) would take them
    below the floor is skipped and listed under "skipped"."""
    markets = markets or core_markets(sport)
    budget = budget or Budget()
    first_skip = len(budget.skipped)
    events, credits = _start(con, budget, session, sport)
    now = utcnow()
    done = []
    # filter first, then cap: a cap applied to the provider's whole list could drop wanted games
    events = [e for e in events if (not event_ids or e["id"] in event_ids)
              and now < parse_iso(e["commence_time"]) <= now + pd.Timedelta(hours=hours_ahead)]
    cost = len(markets) * n_regions(regions)
    for e in events[:max_events]:
        label = f"{e['away_team']} @ {e['home_team']}"
        if not budget.allow(cost, f"{label} props"):
            done.append({"event": label, "skipped": budget.skipped[-1]["reason"]})
            continue
        payload, credits, raw_id = event_odds(con, e["id"], markets, regions, session=session,
                                              sport=sport)
        budget.spent(cost, credits)
        returned = {m["key"] for b in payload.get("bookmakers", []) for m in b.get("markets", [])}
        got = [m for m in markets if m in returned]
        if not got:
            done.append({"event": label, "player_prop_markets_offered": 0})
            continue
        rows = parse_event_odds(payload, observed_at=iso(utcnow()), request_region=regions,
                                market_map=SPORTS[sport][1])
        done.append({"event": label, "markets": got,
                     **link_and_store(con, rows, raw_id, lake_dir=lake_dir, sport=sport)})
    return {"sport": sport, "regions": regions, "events": done, "credits": credits,
            "credits_left": budget.remaining, "floor": budget.floor,
            "skipped": budget.skipped[first_skip:]}


def snapshot_game_lines(con, sport: str = "nba", regions: str = "us", *, session=None,
                        budget: Budget | None = None, lake_dir=None) -> dict:
    """Moneyline, spread and total at every listed book for the whole slate in one
    featured-markets call (3 credits for one region), stored in the append-only game_lines
    table and linked to our game ids the same way as prop quotes. Called without the `budget`
    of the pull it belongs to, it first makes the free events call to read the credits left."""
    first_skip = len(budget.skipped) if budget is not None else 0
    if budget is None:
        budget = Budget()
        _start(con, budget, session, sport)
    cost = len(GAME_MARKETS) * n_regions(regions)
    out = {"sport": sport, "regions": regions, "estimated_credits": cost}
    if not budget.allow(cost, f"{sport.upper()} game lines ({regions})"):
        return {**out, "stored": 0, "skipped": budget.skipped[first_skip:],
                "credits_left": budget.remaining, "floor": budget.floor}
    payload, credits, raw_id = featured_odds(con, sport, regions, session=session)
    budget.spent(cost, credits)
    observed = iso(utcnow())
    rows = parse_game_lines(payload, observed_at=observed, request_region=regions, sport=sport)
    q = link_games(rows, con, sport, lake_dir)
    stored = store_game_lines(con, q, raw_id) if len(q) else 0
    ev = q.drop_duplicates("event_id") if len(q) else pd.DataFrame(columns=["game_id"])
    listed = payload.get("data", []) if isinstance(payload, dict) else payload
    started = [e for e in listed if e.get("commence_time") and
               parse_iso(e["commence_time"]) <= parse_iso(observed)]
    return {**out, "events_listed": len(listed), "events_priced": len(ev),
            "events_under_way": len(started), "rows": len(q), "stored": stored,
            "linked_games": sorted(ev.game_id.dropna().astype(str)),
            "unlinked_events": sorted(f"{r.away_team} @ {r.home_team}"
                                      for r in ev[ev.game_id.isna()].itertuples()),
            "credits": credits, "credits_left": budget.remaining, "floor": budget.floor,
            "skipped": budget.skipped[first_skip:]}


# ----------------------------------------------------------------------- closing snapshots
def plan_close(con: sqlite3.Connection, sport: str = "nba", within_minutes: float = 30,
               now=None) -> list[dict]:
    """Games that tip off within the next `within_minutes` and have not started, with props on
    the paper record (tracked_props, any rule), and for each the provider markets to request:
    only the markets tracked in that game, main lines only (alternates cost extra). Tip-off is
    the time recorded with the tracked props, the same cut-off track.clv uses."""
    now = pd.Timestamp(now or utcnow())
    t = pd.read_sql_query("SELECT game_id, market, tip_utc FROM tracked_props WHERE sport = ? "
                          "ORDER BY id", con, params=(sport,))
    if t.empty:
        return []
    t["tip"] = pd.to_datetime(t.tip_utc, utc=True, format="ISO8601")
    keys = provider_markets(sport)
    order = list(keys.values())
    out = []
    for gid, x in t.groupby("game_id", sort=False):
        tip = x.tip.iloc[-1]  # as last recorded
        if not now < tip <= now + pd.Timedelta(minutes=within_minutes):
            continue
        out.append({"game_id": str(gid), "tip_utc": iso(tip.to_pydatetime()),
                    "minutes_to_tip": round((tip - now).total_seconds() / 60, 1),
                    "markets": sorted({keys[m] for m in x.market if m in keys}, key=order.index),
                    "tracked_props": len(x),
                    "unpriced_markets": sorted(set(x.market) - set(keys))})
    return sorted(out, key=lambda p: p["tip_utc"])


def _event_ids(events: list[dict], con: sqlite3.Connection, sport: str,
               lake_dir=None) -> dict[str, dict]:
    """Our game id -> the provider's event id and a short label, matched as quotes are."""
    rows = [{"event_id": e.get("id"), "home_team": e.get("home_team"),
             "away_team": e.get("away_team"), "commence_time": e.get("commence_time")}
            for e in events]
    m = link_games(rows, con, sport, lake_dir)
    out: dict[str, dict] = {}
    for r in (m.itertuples() if len(m) else []):
        if isinstance(r.game_id, str) and r.game_id:
            short = isinstance(r.away, str) and isinstance(r.home, str)
            out.setdefault(r.game_id, {"event_id": r.event_id,
                                       "label": f"{r.away} @ {r.home}" if short
                                       else f"{r.away_team} @ {r.home_team}"})
    return out


def snapshot_close(con: sqlite3.Connection, sport: str = "nba", within_minutes: float = 30, *,
                   regions: str = "us", dry_run: bool = False, session=None, now=None,
                   budget: Budget | None = None, lake_dir=None) -> dict:
    """Closing snapshot for closing-line value: event odds for the games in `plan_close`, for
    their tracked markets only, stored through the normal quote pipeline, so track.clv (the last
    stored snapshot before tip-off) uses them. Estimated cost per game = markets x regions.
    With nothing planned no call is made at all; a dry run makes only the free events call
    (credits left, current event ids) and no paid call."""
    plan = plan_close(con, sport, within_minutes, now)
    per_region = n_regions(regions)
    for p in plan:
        p["estimated_credits"] = len(p["markets"]) * per_region
    out = {"sport": sport, "within_minutes": within_minutes, "regions": regions,
           "dry_run": dry_run, "planned": plan,
           "estimated_credits": sum(p["estimated_credits"] for p in plan), "results": [],
           "skipped": []}
    if not plan:
        return out
    budget = budget or Budget()
    first_skip = len(budget.skipped)
    events, credits = _start(con, budget, session, sport)
    ids = _event_ids(events, con, sport, lake_dir)
    for p in plan:
        ev = ids.get(p["game_id"], {})
        p["event_id"], p["label"] = ev.get("event_id"), ev.get("label")
    out |= {"credits_left": budget.remaining, "floor": budget.floor}
    if dry_run:
        return out
    for p in plan:
        what = f"{p['label'] or p['game_id']} closing props"
        if not p["event_id"]:
            out["skipped"].append({"call": what, "reason": "not in the provider's event list"})
            continue
        if not p["markets"]:
            out["skipped"].append({"call": what, "reason": "no tracked market the provider prices"})
            continue
        if not budget.allow(p["estimated_credits"], what):
            continue
        payload, credits, raw_id = event_odds(con, p["event_id"], p["markets"], regions,
                                              session=session, sport=sport)
        budget.spent(p["estimated_credits"], credits)
        rows = parse_event_odds(payload, observed_at=iso(utcnow()), request_region=regions,
                                market_map=SPORTS[sport][1])
        out["results"].append({"game_id": p["game_id"], "label": p["label"],
                               **link_and_store(con, rows, raw_id, lake_dir=lake_dir,
                                                sport=sport)})
    out["skipped"] += budget.skipped[first_skip:]
    out |= {"credits": credits, "credits_left": budget.remaining}
    return out


__all__ = ["parse_event_odds", "parse_game_lines", "link_players", "link_event", "link_games",
           "store_quotes", "store_game_lines", "game_lines_frame", "status", "snapshot_upcoming",
           "snapshot_game_lines", "snapshot_close", "plan_close", "Budget", "credit_floor",
           "link_and_store", "link_quotes_nba", "relink_raw", "MissingKey", "json",
           "core_markets", "provider_markets", "NBA_MARKET_MAP", "GAME_MARKETS"]
