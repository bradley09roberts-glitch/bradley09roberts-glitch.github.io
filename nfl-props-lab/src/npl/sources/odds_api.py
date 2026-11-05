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
  markets with props on the paper record (optionally only some rules' props), so track.clv has
  a late snapshot to compare with. The free events call gives each game's start time: a game
  the provider shows as started, or starting within 2 minutes, is skipped and listed (its prices
  would be in-play), and a start time more than 10 minutes from the tip-off recorded with the
  tracked props is warned about. Each game's start time is checked again with a free events
  call immediately before its paid call, with the same rule.
* The pull log (table odds_pulls): every stored event-odds and featured-markets response is
  logged there with its quotes or game lines, one row per event, bookmaker and market with the
  provider's last_update, and one per market asked for. Quotes and game lines keep a row once,
  so a pull in which nothing changed stores none there; readers rebuild each pull's board from
  the log (npl.books.pull_board). backfill_pulls logs the responses stored before the log
  existed.
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
from ..util import FetchError, http_get, iso, log, parse_iso, utcnow

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
    # two-stat combos (keys from the provider's betting-markets page, checked 4 Nov 2026). Opt-in
    # only (OPT_IN): each costs about 1 credit per game per pull, so the default pull asks for
    # the five markets above and nothing else
    "player_points_rebounds": ("pr", False), "player_points_rebounds_alternate": ("pr", True),
    "player_points_assists": ("pa", False), "player_points_assists_alternate": ("pa", True),
    "player_rebounds_assists": ("ra", False),
    "player_rebounds_assists_alternate": ("ra", True),
}
# main-line markets a pull asks for only when told to (`npl quotes --sport nba --combos`)
OPT_IN = {"nba": ("player_points_rebounds", "player_points_assists", "player_rebounds_assists")}
SPORTS = {"nfl": ("americanfootball_nfl", MARKET_MAP), "nba": ("basketball_nba", NBA_MARKET_MAP)}
# provider team names that differ from the schedule's display names
NBA_NAME_ALIASES = {"Los Angeles Clippers": "LAC"}
# featured (game) markets: provider key -> our name in game_lines
GAME_MARKETS = {"h2h": "moneyline", "spreads": "spread", "totals": "total"}


def core_markets(sport: str, combos: bool = False) -> list[str]:
    """The main-line markets a pull asks for: the default set (NBA: points, rebounds, assists,
    threes, PRA), plus the opt-in ones (OPT_IN: NBA's two-stat combos) only when `combos`."""
    extra = OPT_IN.get(sport, ())
    return [k for k, (_, alt) in SPORTS[sport][1].items()
            if not alt and (combos or k not in extra)]


def pull_plan(sport: str = "nba", regions: str = "us", combos: bool = False,
              games: int | None = None) -> dict:
    """What a props pull would ask for and its estimated cost, without any call: the markets,
    credits per game (markets x regions; the provider bills only markets returned, so this is
    an upper bound) and, with `games`, for the slate. `combo_extra_per_game` is what the opt-in
    combos add (0 when they are not asked for), `combo_extra_if_asked` what they would add.
    Game lines (NBA: 3 credits for one region) are separate."""
    markets = core_markets(sport, combos=combos)
    per_region = n_regions(regions)
    per_game = len(markets) * per_region
    extra = len([m for m in markets if m in OPT_IN.get(sport, ())]) * per_region
    out = {"sport": sport, "regions": regions, "combos": combos, "markets": markets,
           "credits_per_game": per_game, "combo_extra_per_game": extra,
           "combo_markets_available": list(OPT_IN.get(sport, ())),
           "combo_extra_if_asked": len(OPT_IN.get(sport, ())) * per_region}
    if games is not None:
        out |= {"games": games, "credits_for_games": per_game * games,
                "combo_extra_for_games": extra * games}
    return out


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


def list_events(con, session=None, sport: str = "nfl", event_ids: list[str] | None = None):
    """Free: the sport's listed events (with the credit headers); `event_ids` asks for only
    those events."""
    params = {"eventIds": ",".join(event_ids)} if event_ids else {}
    return _get(con, "events", f"{sport}:{iso(utcnow())}",
                f"/sports/{SPORTS[sport][0]}/events", params, session)


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


QUOTE_COLS = ["source", "bookmaker", "bookmaker_region", "availability", "provider_event_id",
              "game_id", "player_name_raw", "player_id", "market", "line", "selection",
              "decimal_price", "quote_time", "observed_at"]


def _insert_quotes(con: sqlite3.Connection, rows: pd.DataFrame, raw_file_id: int | None) -> int:
    n = 0
    for r in rows[QUOTE_COLS].itertuples(index=False):
        cur = con.execute(
            f"INSERT OR IGNORE INTO quotes({', '.join(QUOTE_COLS)}, raw_file_id) VALUES "
            f"({', '.join('?' * len(QUOTE_COLS))}, ?)", (*[None if pd.isna(v) else v for v in r],
                                                          raw_file_id))
        n += cur.rowcount
    return n


def store_quotes(con: sqlite3.Connection, rows: pd.DataFrame, raw_file_id: int | None) -> int:
    with db.tx(con):
        return _insert_quotes(con, rows, raw_file_id)


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


def _insert_game_lines(con: sqlite3.Connection, rows: pd.DataFrame, raw_id: int | None) -> int:
    n = 0
    for r in rows.reindex(columns=GAME_LINE_COLS).itertuples(index=False):
        cur = con.execute(
            f"INSERT OR IGNORE INTO game_lines({', '.join(GAME_LINE_COLS)}, raw_id) VALUES "
            f"({', '.join('?' * len(GAME_LINE_COLS))}, ?)",
            (*[None if pd.isna(v) else v for v in r], raw_id))
        n += cur.rowcount
    return n


def store_game_lines(con: sqlite3.Connection, rows: pd.DataFrame, raw_id: int | None) -> int:
    """Append game-line rows; a row already stored from the same response adds nothing."""
    with db.tx(con):
        return _insert_game_lines(con, rows, raw_id)


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


# ----------------------------------------------------------------------------- the pull log
PULL_COLS = ["raw_id", "dataset", "sport", "event_id", "game_id", "commence_time", "bookmaker",
             "market", "provider_market", "last_update", "observed_at"]


def requested_markets(url: str) -> list[str] | None:
    """The provider market keys a stored request asked for (its markets parameter; the stored
    address carries the parameters, the key redacted). None for a request limited to some
    bookmakers: the books it left out say nothing about the market."""
    from urllib.parse import parse_qs, urlsplit

    qs = parse_qs(urlsplit(url).query)
    if qs.get("bookmakers"):
        return None
    return [m for v in qs.get("markets", []) for m in v.split(",") if m]


def _sport_of(url: str) -> str | None:
    return next((s for s, (key, _) in SPORTS.items() if f"/sports/{key}/" in url), None)


def pull_rows(payload: dict | list, *, raw_id: int, dataset: str, sport: str, observed_at: str,
              requested: list[str] | None = None, game_ids: dict | None = None) -> list[dict]:
    """The pull-log rows (odds_pulls) of one stored response: for each event, one row per
    bookmaker-market we store (props markets for event odds, GAME_MARKETS for the featured call)
    with the provider's last_update for it, taken as quote rows take their quote_time (the
    market's last_update, else the bookmaker's), and one row with bookmaker None per market the
    pull asked for (`requested`) or got back."""
    if dataset == "featured_odds":
        mmap = dict(GAME_MARKETS)
        events = payload.get("data", []) if isinstance(payload, dict) else payload
    else:
        mmap = {k: ours for k, (ours, _) in SPORTS[sport][1].items()}
        events = [payload.get("data", payload)] if isinstance(payload, dict) else []
    out = []
    for ev in events or []:
        if not isinstance(ev, dict) or not ev.get("id"):
            continue
        eid = str(ev["id"])
        gid = (game_ids or {}).get(eid)
        base = {"raw_id": raw_id, "dataset": dataset, "sport": sport, "event_id": eid,
                "game_id": gid if isinstance(gid, str) and gid else None,
                "commence_time": ev.get("commence_time"), "observed_at": observed_at}
        got = []
        for bk in ev.get("bookmakers") or []:
            for mk in bk.get("markets") or []:
                key = mk.get("key")
                if key not in mmap or not bk.get("key"):
                    continue
                got.append(key)
                out.append({**base, "bookmaker": bk["key"], "market": mmap[key],
                            "provider_market": key,
                            "last_update": mk.get("last_update") or bk.get("last_update")})
        for key in dict.fromkeys([*(requested or []), *got]):
            if key in mmap:
                out.append({**base, "bookmaker": None, "market": mmap[key], "provider_market": key,
                            "last_update": None})
    return out


def _insert_pulls(con: sqlite3.Connection, rows: list[dict]) -> int:
    n = 0
    for r in rows:
        cur = con.execute(f"INSERT OR IGNORE INTO odds_pulls({', '.join(PULL_COLS)}) VALUES "
                          f"({', '.join('?' * len(PULL_COLS))})", [r[c] for c in PULL_COLS])
        n += cur.rowcount
    return n


def _event_games(events: list, con: sqlite3.Connection, sport: str, lake_dir=None) -> dict:
    """Provider event id -> our game id for a response's events, matched as quotes are (team
    names, start within 6 hours); empty when the schedule or lake is not available."""
    rows = [{"event_id": str(e["id"]), "home_team": e.get("home_team"),
             "away_team": e.get("away_team"), "commence_time": e.get("commence_time")}
            for e in events if isinstance(e, dict) and e.get("id")]
    if not rows:
        return {}
    try:
        m = link_games(rows, con, sport, lake_dir)
    except Exception:  # noqa: BLE001 - no schedule or lake here: the log keeps the event id only
        return {}
    return {str(r.event_id): r.game_id for r in m.itertuples()
            if isinstance(r.game_id, str) and r.game_id}


def _events_of(payload, dataset: str) -> list:
    if dataset == "featured_odds":
        return (payload.get("data", []) if isinstance(payload, dict) else payload) or []
    return [payload.get("data", payload)] if isinstance(payload, dict) else []


def _pulls_for(con: sqlite3.Connection, raw_id: int, payload, *, dataset: str, sport: str,
               observed_at: str, game_ids: dict | None = None, lake_dir=None) -> list[dict]:
    """The pull-log rows of a response stored under raw_id; the markets asked for come from its
    stored address. A request limited to some bookmakers is not logged."""
    row = con.execute("SELECT url FROM raw_files WHERE id = ?", (raw_id,)).fetchone()
    requested = requested_markets(row[0]) if row else []
    if requested is None:
        return []
    ids = dict(game_ids or {})
    events = _events_of(payload, dataset)
    if any(str(e.get("id")) not in ids for e in events if isinstance(e, dict) and e.get("id")):
        ids = {**_event_games(events, con, sport, lake_dir), **ids}
    return pull_rows(payload, raw_id=raw_id, dataset=dataset, sport=sport,
                     observed_at=observed_at, requested=requested, game_ids=ids)


def record_pull(con: sqlite3.Connection, raw_id: int, payload, *, dataset: str, sport: str,
                observed_at: str, game_ids: dict | None = None, lake_dir=None) -> int:
    """Log one stored response in odds_pulls (a pull whose quotes or game lines are stored with
    link_and_store or snapshot_game_lines is logged there, in the same transaction)."""
    rows = _pulls_for(con, raw_id, payload, dataset=dataset, sport=sport,
                      observed_at=observed_at, game_ids=game_ids, lake_dir=lake_dir)
    with db.tx(con):
        return _insert_pulls(con, rows)


def pulls_frame(con: sqlite3.Connection, dataset: str | None = None, sport: str | None = None,
                *, pregame: bool = False) -> pd.DataFrame:
    """The pull log (synthetic responses left out); empty where the table has no rows.
    pregame=True keeps only pulls observed before the event's start time in that response."""
    sql = ("SELECT p.* FROM odds_pulls p JOIN raw_files r ON r.id = p.raw_id "
           "WHERE r.is_synthetic = 0")
    args: list = []
    if dataset:
        sql += " AND p.dataset = ?"
        args.append(dataset)
    if sport:
        sql += " AND p.sport = ?"
        args.append(sport)
    try:
        p = pd.read_sql_query(sql + " ORDER BY p.id", con, params=args)
    except Exception:  # noqa: BLE001 - a store without the table: no log, readers fall back
        return pd.DataFrame(columns=["id", *PULL_COLS])
    if pregame and len(p):
        start = pd.to_datetime(p.commence_time, utc=True, format="ISO8601")
        p = p[start.isna() | (pd.to_datetime(p.observed_at, utc=True, format="ISO8601") < start)]
    return p.reset_index(drop=True)


def backfill_pulls(con: sqlite3.Connection, lake_dir=None) -> dict:
    """One-off: log every stored event-odds and featured-markets response that is not in the
    pull log yet (those stored before it existed), from the stored JSON. A pull's observed_at is
    that of the rows stored from it (the time the pull used), else the response's retrieval
    time; game ids come from the stored rows (later relinks included), else from the schedule.
    Idempotent. Returns counts only."""
    have = {r[0] for r in con.execute("SELECT DISTINCT raw_id FROM odds_pulls")}
    raws = con.execute("SELECT id, dataset, url, retrieved_at, path FROM raw_files WHERE "
                       "source = ? AND dataset IN ('event_odds', 'featured_odds') ORDER BY id",
                       (SOURCE,)).fetchall()
    q = quotes_frame(con, include_synthetic=True)
    g = game_lines_frame(con, include_synthetic=True)
    seen_at = {**q.groupby("raw_file_id").observed_at.min().to_dict(),
               **g.groupby("raw_id").observed_at.min().to_dict()}
    linked = pd.concat([q[["provider_event_id", "game_id"]].rename(
        columns={"provider_event_id": "event_id"}), g[["event_id", "game_id"]]])
    linked = linked[linked.game_id.notna()]
    ids = linked.groupby("event_id").game_id.agg(lambda s: s.mode().iloc[0]).to_dict()
    out = {"responses": len(raws), "already_logged": 0, "logged": 0, "event_odds": 0,
           "featured_odds": 0, "unreadable": 0, "limited_to_bookmakers": 0, "rows": 0}
    events: set = set()
    for rid, dataset, url, retrieved_at, path in raws:
        if rid in have:
            out["already_logged"] += 1
            continue
        sport = _sport_of(url)
        try:
            payload = json.loads((rawstore.DATA_HOME / path).read_text())
        except (OSError, ValueError):
            out["unreadable"] += 1
            continue
        if sport is None:
            out["unreadable"] += 1
            continue
        if requested_markets(url) is None:
            out["limited_to_bookmakers"] += 1
            continue
        rows = _pulls_for(con, rid, payload, dataset=dataset, sport=sport,
                          observed_at=seen_at.get(rid) or retrieved_at,
                          game_ids={str(k): v for k, v in ids.items()}, lake_dir=lake_dir)
        with db.tx(con):
            out["rows"] += _insert_pulls(con, rows)
        out["logged"] += 1
        out[dataset] += 1
        events |= {r["event_id"] for r in rows}
    return {**out, "events": len(events)}


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
                   lake_dir=None, sport: str = "nfl", *, payload=None,
                   observed_at: str | None = None) -> dict:
    """Link and store an event-odds response's quote rows. Given the response itself
    (`payload`, stored under raw_file_id, and the pull's observed_at) the pull is logged in
    odds_pulls in the same transaction, also when it stored no new row (nothing had changed)."""
    q = ((link_quotes_nba(rows, con) if sport == "nba" else link_quotes(rows, lake_dir))
         if rows else pd.DataFrame())
    pulls = []
    if payload is not None and raw_file_id is not None:
        gids = ({} if q.empty else
                {str(e): g for e, g in zip(q.provider_event_id, q.game_id, strict=True)
                 if isinstance(g, str) and g})
        when = observed_at or (rows[0]["observed_at"] if rows else iso(utcnow()))
        pulls = _pulls_for(con, raw_file_id, payload, dataset="event_odds", sport=sport,
                           observed_at=when, game_ids=gids, lake_dir=lake_dir)
    with db.tx(con):
        stored = _insert_quotes(con, q, raw_file_id) if len(q) else 0
        logged = _insert_pulls(con, pulls)
    if q.empty:
        return {"quotes": 0, "stored": 0, "linked_players": 0, "pull_rows": logged}
    return {**_link_summary(q), "stored": stored, "pull_rows": logged}


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
                      budget: Budget | None = None, lake_dir=None,
                      combos: bool = False) -> dict:
    """Collect current prop quotes for events kicking off within `hours_ahead`. Without
    `markets`, the default main-line set (core_markets); the NBA's two-stat combos only with
    `combos` (about 1 extra credit per combo market per game).
    One event-odds call per event for the wanted markets, with no `markets` pre-call: the cost
    counts only the unique markets returned (x regions) and an event without props returns
    nothing and costs nothing, so the 1-credit pre-call only added cost. Core markets only by
    default (alternate lines cost extra credits). The free events call that opens the pull reads
    the credits left; a call whose estimated cost (markets requested x regions) would take them
    below the floor is skipped and listed under "skipped"."""
    markets = markets or core_markets(sport, combos=combos)
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
        observed = iso(utcnow())
        returned = {m["key"] for b in payload.get("bookmakers", []) for m in b.get("markets", [])}
        got = [m for m in markets if m in returned]
        if not got:  # nothing on offer: logged all the same (an earlier board is now empty)
            record_pull(con, raw_id, payload, dataset="event_odds", sport=sport,
                        observed_at=observed, lake_dir=lake_dir)
            done.append({"event": label, "player_prop_markets_offered": 0})
            continue
        rows = parse_event_odds(payload, observed_at=observed, request_region=regions,
                                market_map=SPORTS[sport][1])
        done.append({"event": label, "markets": got,
                     **link_and_store(con, rows, raw_id, lake_dir=lake_dir, sport=sport,
                                      payload=payload, observed_at=observed)})
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
    gids = ({} if q.empty else {str(e): g for e, g in zip(q.event_id, q.game_id, strict=True)
                                if isinstance(g, str) and g})
    # the pull log (every listed event, priced or not) with its rows, in one transaction
    pulls = _pulls_for(con, raw_id, payload, dataset="featured_odds", sport=sport,
                       observed_at=observed, game_ids=gids, lake_dir=lake_dir)
    with db.tx(con):
        stored = _insert_game_lines(con, q, raw_id) if len(q) else 0
        logged = _insert_pulls(con, pulls)
    ev = q.drop_duplicates("event_id") if len(q) else pd.DataFrame(columns=["game_id"])
    listed = payload.get("data", []) if isinstance(payload, dict) else payload
    started = [e for e in listed if e.get("commence_time") and
               parse_iso(e["commence_time"]) <= parse_iso(observed)]
    return {**out, "events_listed": len(listed), "events_priced": len(ev),
            "events_under_way": len(started), "rows": len(q), "stored": stored,
            "pull_rows": logged,
            "linked_games": sorted(ev.game_id.dropna().astype(str)),
            "unlinked_events": sorted(f"{r.away_team} @ {r.home_team}"
                                      for r in ev[ev.game_id.isna()].itertuples()),
            "credits": credits, "credits_left": budget.remaining, "floor": budget.floor,
            "skipped": budget.skipped[first_skip:]}


# ----------------------------------------------------------------------- closing snapshots
TIP_TOLERANCE_MIN = 10  # provider start v recorded tip-off: a larger gap is warned about
CLOSE_MARGIN_MIN = 2  # no closing call for a game starting within this many minutes, or started


def plan_close(con: sqlite3.Connection, sport: str = "nba", within_minutes: float = 30,
               now=None, rules: list[str] | None = None) -> list[dict]:
    """Games that tip off within the next `within_minutes` and have not started, with props on
    the paper record (tracked_props; any rule, or only `rules`), and for each the provider
    markets to request: only the markets tracked in that game, main lines only (alternates cost
    extra). Tip-off here is the time recorded with the tracked props, the same cut-off track.clv
    uses; snapshot_close then checks it against the provider's start time."""
    now = pd.Timestamp(now or utcnow())
    sql = "SELECT game_id, market, tip_utc FROM tracked_props WHERE sport = ?"
    if rules:
        sql += f" AND rule IN ({', '.join('?' * len(rules))})"
    t = pd.read_sql_query(sql + " ORDER BY id", con, params=(sport, *(rules or [])))
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
    """Our game id -> the provider's event id, start time and a short label, matched as quotes
    are."""
    rows = [{"event_id": e.get("id"), "home_team": e.get("home_team"),
             "away_team": e.get("away_team"), "commence_time": e.get("commence_time")}
            for e in events]
    m = link_games(rows, con, sport, lake_dir)
    out: dict[str, dict] = {}
    for r in (m.itertuples() if len(m) else []):
        if isinstance(r.game_id, str) and r.game_id:
            short = isinstance(r.away, str) and isinstance(r.home, str)
            out.setdefault(r.game_id, {"event_id": r.event_id,
                                       "commence_time": r.commence_time
                                       if isinstance(r.commence_time, str) else None,
                                       "label": f"{r.away} @ {r.home}" if short
                                       else f"{r.away_team} @ {r.home_team}"})
    return out


def _hm(ts: pd.Timestamp) -> str:
    return ts.strftime("%H:%M UTC")


def _utc_ts(v) -> pd.Timestamp:
    t = pd.Timestamp(v)
    return t.tz_localize("UTC") if t.tzinfo is None else t.tz_convert("UTC")


def _too_close(st: pd.Timestamp, now: pd.Timestamp) -> str | None:
    """Why no closing call is made for a game the provider shows starting at `st`: it has
    started, or starts within CLOSE_MARGIN_MIN minutes (prices would be, or may soon be,
    in-play); None when there is time."""
    lead = (st - now).total_seconds() / 60
    if lead <= 0:
        return (f"already started at {_hm(st)} by the provider's start time (prices would be "
                "in-play)")
    if lead <= CLOSE_MARGIN_MIN:
        return (f"starts at {_hm(st)} by the provider's start time, within {CLOSE_MARGIN_MIN} "
                "min (prices could be in-play)")
    return None


def _check_start(p: dict, start: str | None, now: pd.Timestamp) -> str | None:
    """Use the provider's start time for a planned game: tip_utc and minutes_to_tip follow it
    (the recorded tip-off is kept as recorded_tip_utc), `started` is set when it is at or before
    now and `too_close` when it is within CLOSE_MARGIN_MIN minutes, and a warning is returned
    when it is more than TIP_TOLERANCE_MIN minutes from the recorded tip-off."""
    p["started"], p["too_close"] = False, False
    if not start:
        return None
    st, rec = _utc_ts(start), pd.Timestamp(p["tip_utc"])
    p |= {"recorded_tip_utc": p["tip_utc"], "tip_utc": iso(st.to_pydatetime()),
          "minutes_to_tip": round((st - now).total_seconds() / 60, 1), "started": st <= now,
          "too_close": now < st <= now + pd.Timedelta(minutes=CLOSE_MARGIN_MIN)}
    gap = (st - rec).total_seconds() / 60
    if abs(gap) <= TIP_TOLERANCE_MIN:
        return None
    return (f"{p.get('label') or p['game_id']}: the provider's start time ({_hm(st)}) is "
            f"{abs(gap):.0f} min {'later' if gap > 0 else 'earlier'} than the tip-off recorded "
            f"with the tracked props ({_hm(rec)}), which closing-line value uses as its cut-off")


def snapshot_close(con: sqlite3.Connection, sport: str = "nba", within_minutes: float = 30, *,
                   regions: str = "us", dry_run: bool = False, session=None, now=None,
                   budget: Budget | None = None, lake_dir=None,
                   rules: list[str] | None = None) -> dict:
    """Closing snapshot for closing-line value: event odds for the games in `plan_close` (props
    of `rules` only, when given), for their tracked markets only, stored through the normal
    quote pipeline, so track.clv (the last stored snapshot before tip-off) uses them. Estimated
    cost per game = markets x regions. With nothing planned no call is made at all. The free
    events call that opens the pull gives each game's event id and the provider's start time: a
    game that has started by it, or starts within CLOSE_MARGIN_MIN minutes, is skipped (started
    games are listed under `started`; their prices would be in-play), and a start more than
    TIP_TOLERANCE_MIN minutes from the recorded tip-off is listed under `warnings`. Immediately
    before each paid call the game's start time is checked again with another free events call
    (time has passed, and the start may have moved), with the same rule. A dry run makes only the
    first free call and no paid call."""
    plan = plan_close(con, sport, within_minutes, now, rules=rules)
    per_region = n_regions(regions)
    for p in plan:
        p["estimated_credits"] = len(p["markets"]) * per_region
    out = {"sport": sport, "within_minutes": within_minutes, "regions": regions,
           "rules": list(rules) if rules else None, "dry_run": dry_run, "planned": plan,
           "estimated_credits": sum(p["estimated_credits"] for p in plan), "results": [],
           "skipped": [], "started": [], "warnings": []}
    if not plan:
        return out
    budget = budget or Budget()
    first_skip = len(budget.skipped)
    events, credits = _start(con, budget, session, sport)
    ids = _event_ids(events, con, sport, lake_dir)
    clock = (lambda: pd.Timestamp(now)) if now is not None else (lambda: pd.Timestamp(utcnow()))
    at = clock()
    for p in plan:
        ev = ids.get(p["game_id"], {})
        p["event_id"], p["label"] = ev.get("event_id"), ev.get("label")
        if warn := _check_start(p, ev.get("commence_time"), at):
            out["warnings"].append(warn)
            log.warning("npl close: %s", warn)
        if p["started"]:
            out["started"].append({"game_id": p["game_id"], "label": p["label"],
                                   "start_utc": p["tip_utc"]})
    callable_ = [p for p in plan if p["event_id"] and p["markets"] and not p["started"]
                 and not p["too_close"]]
    out |= {"credits_left": budget.remaining, "floor": budget.floor,
            "estimated_credits": sum(p["estimated_credits"] for p in callable_)}
    if dry_run:
        return out
    for p in plan:
        what = f"{p['label'] or p['game_id']} closing props"
        if not p["event_id"]:
            out["skipped"].append({"call": what, "reason": "not in the provider's event list"})
            continue
        if p["started"] or p["too_close"]:
            out["skipped"].append({"call": what, "reason": _too_close(_utc_ts(p["tip_utc"]), at)})
            continue
        if not p["markets"]:
            out["skipped"].append({"call": what, "reason": "no tracked market the provider prices"})
            continue
        if not budget.allow(p["estimated_credits"], what):
            continue
        if why := _recheck_start(con, session, sport, p, clock):
            out["skipped"].append({"call": what, "reason": why})
            if p.get("started_at_check"):
                out["started"].append({"game_id": p["game_id"], "label": p["label"],
                                       "start_utc": p["checked_start_utc"]})
            continue
        payload, credits, raw_id = event_odds(con, p["event_id"], p["markets"], regions,
                                              session=session, sport=sport)
        budget.spent(p["estimated_credits"], credits)
        observed = iso(utcnow())
        rows = parse_event_odds(payload, observed_at=observed, request_region=regions,
                                market_map=SPORTS[sport][1])
        out["results"].append({"game_id": p["game_id"], "label": p["label"],
                               **link_and_store(con, rows, raw_id, lake_dir=lake_dir,
                                                sport=sport, payload=payload,
                                                observed_at=observed)})
    out["skipped"] += budget.skipped[first_skip:]
    out |= {"credits": credits, "credits_left": budget.remaining}
    return out


def _recheck_start(con: sqlite3.Connection, session, sport: str, p: dict, clock) -> str | None:
    """The check made immediately before a game's paid closing call: its start time from a fresh
    free events call (for that event only) against the time now. Returns why the call is not
    made (started, starts within CLOSE_MARGIN_MIN minutes, no longer listed, no start time, or
    the check failed), or None to go ahead; the start seen is kept in the plan as
    checked_start_utc, checked_minutes_to_tip and started_at_check."""
    try:
        events, _, _ = list_events(con, session, sport, event_ids=[p["event_id"]])
    except FetchError as err:
        return f"start time not re-checked before the call ({err})"
    ev = next((e for e in events or [] if isinstance(e, dict) and e.get("id") == p["event_id"]),
              None)
    if ev is None:
        return "re-checked before the call: no longer in the provider's event list"
    if not ev.get("commence_time"):
        return "re-checked before the call: no start time from the provider"
    st, at = _utc_ts(ev["commence_time"]), clock()
    p["checked_start_utc"] = iso(st.to_pydatetime())
    p["checked_minutes_to_tip"] = round((st - at).total_seconds() / 60, 1)
    p["started_at_check"] = bool(st <= at)
    why = _too_close(st, at)
    return None if why is None else f"re-checked before the call: {why}"


__all__ = ["parse_event_odds", "parse_game_lines", "link_players", "link_event", "link_games",
           "store_quotes", "store_game_lines", "game_lines_frame", "status", "snapshot_upcoming",
           "snapshot_game_lines", "snapshot_close", "plan_close", "Budget", "credit_floor",
           "link_and_store", "link_quotes_nba", "relink_raw", "MissingKey", "json",
           "core_markets", "provider_markets", "pull_plan", "OPT_IN", "NBA_MARKET_MAP",
           "GAME_MARKETS", "pull_rows",
           "record_pull", "pulls_frame", "backfill_pulls", "requested_markets"]
