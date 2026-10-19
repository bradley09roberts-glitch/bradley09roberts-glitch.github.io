"""Sportmonks Football API v3 adapter (DECISIONS.md D-010).

Written from the documentation fetched on 2026-10-19 (authentication, fixtures by date range,
pre-match odds, odd entity, cursor pagination, rate limits). It has NOT yet been run against a
live response: tests/test_sportmonks_contract.py checks it against recorded real responses once
the owner's key exists, and until then is skipped.
"""

import json
import time
from collections import Counter
from collections.abc import Callable, Iterator
from datetime import UTC, date, datetime, timedelta

import httpx

from footy.providers.base import (
    TOTAL_LINES,
    FetchedFixtures,
    FetchedOdds,
    ProviderFixture,
    ProviderQuote,
    RawPage,
)

BASE_URL = "https://api.sportmonks.com/v3/football"
# Sportmonks league IDs for our divisions, from its xG coverage list (docs, 2026-10-19).
LEAGUE_IDS = {"E0": 8, "SP1": 564, "I1": 384, "D1": 82, "F1": 301}
# Pre-match market IDs (docs: markets tutorial): 1 Fulltime Result, 80 Goals Over/Under,
# 14 Both Teams To Score.
MARKETS = {1: "1X2", 80: "OU", 14: "BTTS"}
SELECTIONS = {
    "1X2": {"home": "H", "1": "H", "draw": "D", "x": "D", "away": "A", "2": "A"},
    "OU": {"over": "O", "under": "U"},
    "BTTS": {"yes": "Y", "no": "N"},
}
MAX_RANGE_DAYS = 100
MAX_RATE_LIMIT_WAIT = 120.0  # seconds; longer waits fail the run instead of blocking cron


class SportmonksError(RuntimeError):
    pass


class SportmonksClient:
    def __init__(
        self,
        token: str,
        http: httpx.Client | None = None,
        sleep: Callable[[float], None] = time.sleep,
        max_retries: int = 3,
    ):
        self._token = token
        self.http = http or httpx.Client(timeout=30.0)
        self.sleep = sleep
        self.max_retries = max_retries

    def get(self, path: str, params: dict | None = None) -> tuple[dict, RawPage]:
        url = f"{BASE_URL}/{path.lstrip('/')}"
        for attempt in range(self.max_retries):
            # Token in the Authorization header (documented alternative to ?api_token=), so it
            # never appears in URLs, logs or the raw store.
            resp = self.http.get(url, params=params, headers={"Authorization": self._token})
            if resp.status_code == 429 and attempt < self.max_retries - 1:
                wait = _reset_seconds(resp)
                if wait > MAX_RATE_LIMIT_WAIT:
                    raise SportmonksError(f"rate limited for {path}; resets in {wait:.0f} s")
                self.sleep(wait)
                continue
            if resp.status_code >= 500 and attempt < self.max_retries - 1:
                self.sleep(2.0 * 2**attempt)
                continue
            if resp.status_code != 200:
                raise SportmonksError(f"HTTP {resp.status_code} for {path}")
            body = resp.json()
            safe_url = str(resp.request.url.copy_remove_param("api_token"))
            return body, RawPage(logical_key=path, url=safe_url, content=resp.content)
        raise SportmonksError(f"gave up on {path}")

    def paginate(self, path: str, params: dict) -> Iterator[tuple[dict, RawPage]]:
        params = dict(params)
        while True:
            body, page = self.get(path, params)
            yield body, page
            pag = body.get("pagination") or {}
            if not pag.get("has_more") or not pag.get("next_cursor"):
                return
            params["cursor"] = pag["next_cursor"]


class SportmonksProvider:
    name = "sportmonks"

    def __init__(self, client: SportmonksClient, league_ids: dict[str, int] | None = None):
        self.client = client
        self.league_ids = league_ids or LEAGUE_IDS
        self.league_codes = {v: k for k, v in self.league_ids.items()}

    def upcoming_fixtures(self, start: date, end: date) -> FetchedFixtures:
        out = FetchedFixtures([], [])
        skipped: Counter = Counter()
        cur = start
        while cur <= end:
            stop = min(end, cur + timedelta(days=MAX_RANGE_DAYS - 1))
            path = f"fixtures/between/{cur.isoformat()}/{stop.isoformat()}"
            params = {
                "include": "participants",
                "filters": "fixtureLeagues:" + ",".join(str(i) for i in self.league_ids.values()),
                "per_page": 50,
            }
            for body, page in self.client.paginate(path, params):
                out.pages.append(page)
                for item in body.get("data", []):
                    fx, reason = parse_fixture(item, self.league_codes)
                    if fx is None:
                        skipped[reason] += 1
                    else:
                        out.fixtures.append(fx)
            cur = stop + timedelta(days=1)
        out.skipped = dict(skipped)
        return out

    def prematch_odds(self, provider_fixture_id: str) -> FetchedOdds:
        out = FetchedOdds([], [])
        skipped: Counter = Counter()
        for market_id in MARKETS:
            path = f"odds/pre-match/fixtures/{provider_fixture_id}/markets/{market_id}"
            for body, page in self.client.paginate(path, {"per_page": 50}):
                out.pages.append(page)
                for item in body.get("data", []):
                    q, reason = parse_odd(item, page=len(out.pages) - 1)
                    if q is None:
                        skipped[reason] += 1
                    else:
                        out.quotes.append(q)
        out.skipped = dict(skipped)
        return out


def parse_fixture(item: dict, league_codes: dict[int, str]):
    league = league_codes.get(item.get("league_id"))
    if league is None:
        return None, "league_not_in_scope"
    kickoff = _utc(item.get("starting_at"))
    if kickoff is None:
        return None, "no_kickoff"
    sides = {}
    for p in item.get("participants") or []:
        loc = (p.get("meta") or {}).get("location")
        if loc in ("home", "away"):
            sides[loc] = p.get("name")
    if not sides.get("home") or not sides.get("away"):
        return None, "participants_missing"
    return (
        ProviderFixture(
            provider_fixture_id=str(item["id"]),
            league_code=league,
            kickoff_utc=kickoff,
            home_name=sides["home"],
            away_name=sides["away"],
            status=str(item.get("state_id")),
        ),
        None,
    )


def parse_odd(item: dict, page: int = 0):
    family = MARKETS.get(item.get("market_id"))
    if family is None:
        return None, "market_not_in_scope"
    if item.get("stopped"):
        return None, "stopped"
    label = str(item.get("label") or "").strip().lower()
    selection = SELECTIONS[family].get(label)
    if selection is None:
        return None, f"unknown_label:{family}:{label}"
    try:
        price = float(item.get("value"))
    except (TypeError, ValueError):
        return None, "bad_price"
    if price <= 1.0:
        return None, "bad_price"
    line = None
    market_key = family
    if family == "OU":
        try:
            line = float(item.get("total"))
        except (TypeError, ValueError):
            return None, "no_total"
        if line not in TOTAL_LINES:
            return None, "line_not_in_scope"
        market_key = f"OU_{line:g}"
    return (
        ProviderQuote(
            bookmaker=f"sportmonks:{item.get('bookmaker_id')}",
            market_key=market_key,
            line=line,
            selection=selection,
            price=price,
            quote_time_utc=_utc(item.get("latest_bookmaker_update")),
            page=page,
        ),
        None,
    )


def _utc(s) -> datetime | None:
    """Sportmonks timestamps are UTC, "YYYY-MM-DD HH:MM:SS"."""
    if not s:
        return None
    try:
        return datetime.strptime(str(s)[:19], "%Y-%m-%d %H:%M:%S").replace(tzinfo=UTC)
    except ValueError:
        return None


def _reset_seconds(resp: httpx.Response) -> float:
    try:
        rl = json.loads(resp.content).get("rate_limit") or {}
        return float(rl.get("resets_in_seconds", 60))
    except (ValueError, AttributeError):
        return 60.0
