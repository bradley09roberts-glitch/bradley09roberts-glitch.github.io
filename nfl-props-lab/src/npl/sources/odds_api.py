"""The Odds API adapter (https://the-odds-api.com/, v4). Status: implemented, NOT validated
against live responses (no API key is configured; see docs/DATA_AUDIT.md).

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
         session=None) -> tuple[dict | list, dict]:
    key = api_key()
    url = f"{BASE}{path}"
    r = http_get(url, params={**params, "apiKey": key}, session=session, timeout=60)
    credits = {h: r.headers.get(h) for h in ("x-requests-remaining", "x-requests-used",
                                             "x-requests-last")}
    shown = f"{url}?" + "&".join(f"{k}={v}" for k, v in sorted(params.items()))
    rawstore.store(con, source=SOURCE, dataset=dataset, part=part, url=shown,
                   content=r.content, filename=f"{dataset}.json",
                   content_type=r.headers.get("Content-Type"))
    return r.json(), credits


def list_events(con, session=None):
    return _get(con, "events", iso(utcnow()), f"/sports/{SPORT}/events", {}, session)


def event_markets(con, event_id: str, regions: str = "uk", session=None):
    """1 credit: which markets each bookmaker currently offers for an event."""
    return _get(con, "event_markets", f"{event_id}:{regions}",
                f"/sports/{SPORT}/events/{event_id}/markets", {"regions": regions}, session)


def event_odds(con, event_id: str, markets: list[str], regions: str = "uk",
               bookmakers: list[str] | None = None, session=None):
    """Cost = unique markets returned x regions. Props are one event per call."""
    params = {"markets": ",".join(markets), "oddsFormat": "decimal", "dateFormat": "iso"}
    if bookmakers:
        params["bookmakers"] = ",".join(bookmakers)
    else:
        params["regions"] = regions
    return _get(con, "event_odds", f"{event_id}:{regions}:{iso(utcnow())}",
                f"/sports/{SPORT}/events/{event_id}/odds", params, session)


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
                     synthetic: bool = False) -> list[dict]:
    """Flatten an event-odds response into quote rows (one per outcome)."""
    data = payload.get("data", payload)  # historical responses wrap the event in `data`
    out = []
    for bk in data.get("bookmakers", []):
        for mk in bk.get("markets", []):
            if mk.get("key") not in MARKET_MAP:
                continue
            ours, alt = MARKET_MAP[mk["key"]]
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
    g = games[["game_id", "home_team", "away_team", "kickoff_utc"]]
    m = q.merge(g, left_on=["home", "away"], right_on=["home_team", "away_team"], how="left",
                suffixes=("", "_g"))
    ok = (m["kickoff_utc"] - m["commence"]).abs() <= pd.Timedelta(hours=6)
    m.loc[~ok, "game_id"] = None
    return m.drop(columns=["home_team_g", "away_team_g"], errors="ignore")


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


def snapshot_upcoming(con, *, regions: str = "uk", hours_ahead: float = 48, session=None,
                      max_events: int = 20) -> dict:
    """Collect current prop quotes for events kicking off within `hours_ahead`.
    One `markets` call (1 credit) per event first, then odds only for markets offered."""
    events, credits = list_events(con, session)
    now = utcnow()
    done = []
    for e in events[:max_events]:
        ko = parse_iso(e["commence_time"])
        if not (now < ko <= now + pd.Timedelta(hours=hours_ahead)):
            continue
        mk, _ = event_markets(con, e["id"], regions, session)
        offered = {m["key"] for b in mk.get("bookmakers", []) for m in b.get("markets", [])}
        want = [m for m in MARKET_MAP if m in offered]
        if not want:
            done.append({"event": e["id"], "markets": 0})
            continue
        payload, credits = event_odds(con, e["id"], want, regions, session=session)
        rows = parse_event_odds(payload, observed_at=iso(utcnow()), request_region=regions)
        done.append({"event": e["id"], "markets": len(want), "quotes": len(rows)})
    return {"events": done, "credits": credits}


__all__ = ["parse_event_odds", "link_players", "link_event", "store_quotes", "status",
           "snapshot_upcoming", "MissingKey", "json"]
