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
"""

from __future__ import annotations

import json
import os
import re
import sqlite3
import unicodedata
from dataclasses import dataclass

import pandas as pd

from .. import db, rawstore
from ..util import http_get, iso, parse_iso, utcnow

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


def core_markets(sport: str) -> list[str]:
    return [k for k, (_, alt) in SPORTS[sport][1].items() if not alt]

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
    return _get(con, "events", f"{sport}:{iso(utcnow())}",
                f"/sports/{SPORTS[sport][0]}/events", {}, session)


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
                          regions: str = "us", session=None):
    """Paid plans only. Cost = 10 x unique markets returned x regions."""
    params = {"markets": ",".join(markets), "oddsFormat": "decimal", "dateFormat": "iso",
              "regions": regions, "date": date_iso}
    return _get(con, "historical_event_odds", f"{event_id}:{regions}:{date_iso}",
                f"/historical/sports/{SPORT}/events/{event_id}/odds", params, session)


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


def status(con: sqlite3.Connection) -> dict:
    """What the UI shows about odds: blocked, or what is held."""
    try:
        api_key()
        keyed = True
    except MissingKey:
        keyed = False
    q = quotes_frame(con)
    return {"key_configured": keyed, "quotes": len(q),
            "verified_uk_quotes": int((q.availability == "verified_uk").sum()),
            "research_reference_quotes": int((q.availability == "research_reference").sum()),
            "first_observed": q.observed_at.min() if len(q) else None,
            "last_observed": q.observed_at.max() if len(q) else None,
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
    games = pd.read_parquet(lake_dir / "games.parquet")
    games["kickoff_utc"] = pd.to_datetime(games.kickoff_utc, utc=True)
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


def link_quotes_nba(rows: list[dict], con: sqlite3.Connection) -> pd.DataFrame:
    """NBA: event -> game via the SportsDataverse schedule (full team names, tip-off within
    6 hours); player -> unique normalised name among both teams' recent box-score listings."""
    from ..nba import canon as nba_canon
    from ..nba import live as nba_live

    q = pd.DataFrame(rows)
    if q.empty:
        return q
    sched, _ = nba_live._latest(con, "nba_schedule")
    names = dict(zip(sched.home_display_name, sched.home_abbreviation, strict=False))
    names.update(dict(zip(sched.away_display_name, sched.away_abbreviation, strict=False)))
    names.update(NBA_NAME_ALIASES)
    sg = pd.DataFrame({"game_id": sched.game_id.astype("int64").astype(str),
                       "home": sched.home_abbreviation, "away": sched.away_abbreviation,
                       "tip": pd.to_datetime(sched.date, utc=True)})
    q["home"], q["away"] = q.home_team.map(names), q.away_team.map(names)
    q["commence"] = pd.to_datetime(q.commence_time, utc=True)
    q["_qi"] = range(len(q))
    m = q.merge(sg, on=["home", "away"], how="left")
    m["_ok"] = (m.tip - m.commence).abs() <= pd.Timedelta(hours=6)
    m = m.sort_values(["_qi", "_ok"], ascending=[True, False]).drop_duplicates("_qi")
    m.loc[~m._ok, "game_id"] = None
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


def snapshot_upcoming(con, *, regions: str = "uk", hours_ahead: float = 48, session=None,
                      max_events: int = 20, markets: list[str] | None = None,
                      event_ids: list[str] | None = None, sport: str = "nfl") -> dict:
    """Collect current prop quotes for events kicking off within `hours_ahead`.
    One `markets` call (1 credit) per event first, then odds only for the wanted markets
    that some bookmaker offers (cost = markets returned x regions). Core markets only by
    default (alternate lines cost extra credits)."""
    markets = markets or core_markets(sport)
    events, credits, _ = list_events(con, session, sport)
    now = utcnow()
    done = []
    for e in events[:max_events]:
        if event_ids and e["id"] not in event_ids:
            continue
        ko = parse_iso(e["commence_time"])
        if not (now < ko <= now + pd.Timedelta(hours=hours_ahead)):
            continue
        mk, credits, _ = event_markets(con, e["id"], regions, session, sport)
        offered = {m["key"] for b in mk.get("bookmakers", []) for m in b.get("markets", [])}
        want = [m for m in markets if m in offered]
        label = f"{e['away_team']} @ {e['home_team']}"
        if not want:
            done.append({"event": label, "player_prop_markets_offered": 0})
            continue
        payload, credits, raw_id = event_odds(con, e["id"], want, regions, session=session,
                                              sport=sport)
        rows = parse_event_odds(payload, observed_at=iso(utcnow()), request_region=regions,
                                market_map=SPORTS[sport][1])
        done.append({"event": label, "markets": want,
                     **link_and_store(con, rows, raw_id, sport=sport)})
    return {"sport": sport, "regions": regions, "events": done, "credits": credits}


__all__ = ["parse_event_odds", "link_players", "link_event", "store_quotes", "status",
           "snapshot_upcoming", "link_and_store", "link_quotes_nba", "relink_raw", "MissingKey",
           "json", "core_markets", "NBA_MARKET_MAP"]
