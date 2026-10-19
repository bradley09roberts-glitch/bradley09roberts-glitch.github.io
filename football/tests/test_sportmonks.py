"""Sportmonks adapter: request shape, auth, paging, rate limits and parsing.

Responses here are either copied from Sportmonks' published documentation examples (marked
DOCS EXAMPLE) or synthetic. Behaviour against real responses is checked separately by
test_sportmonks_contract.py once recorded responses exist.
"""

import json
from datetime import UTC, date, datetime

import httpx
import pytest

from footy.providers.sportmonks import (
    SportmonksClient,
    SportmonksError,
    SportmonksProvider,
    parse_fixture,
    parse_odd,
)

# DOCS EXAMPLE: docs.sportmonks.com pre-match odds response (fetched 2026-10-19), first two items.
DOCS_ODDS = [
    {
        "id": 1,
        "fixture_id": 18533878,
        "market_id": 1,
        "bookmaker_id": 34,
        "label": "Home",
        "value": "1.48",
        "stopped": False,
        "total": None,
        "latest_bookmaker_update": "2023-01-11 14:40:25",
    },
    {
        "id": 2,
        "fixture_id": 18533878,
        "market_id": 80,
        "bookmaker_id": 34,
        "label": "Over",
        "value": "1.85",
        "stopped": False,
        "total": "2.75",
        "latest_bookmaker_update": "2023-01-11 14:40:25",
    },
]


def test_parse_docs_example_odds():
    q, reason = parse_odd(DOCS_ODDS[0])
    assert (q.market_key, q.selection, q.price, q.bookmaker) == ("1X2", "H", 1.48, "sportmonks:34")
    assert q.quote_time_utc == datetime(2023, 1, 11, 14, 40, 25, tzinfo=UTC)
    # 2.75 is a quarter line: out of scope, skipped with a reason rather than rounded.
    assert parse_odd(DOCS_ODDS[1]) == (None, "line_not_in_scope")


@pytest.mark.parametrize(
    ("item", "expected"),
    [
        ({"market_id": 80, "label": "Under", "value": "2.05", "total": "3.5"}, ("OU_3.5", "U")),
        ({"market_id": 80, "label": "Over", "value": "1.40", "total": "1.5"}, ("OU_1.5", "O")),
        ({"market_id": 14, "label": "Yes", "value": "1.75"}, ("BTTS", "Y")),
        ({"market_id": 1, "label": "Draw", "value": "3.4"}, ("1X2", "D")),
    ],
)
def test_parse_supported_markets(item, expected):
    q, _ = parse_odd(item)
    assert (q.market_key, q.selection) == expected


@pytest.mark.parametrize(
    ("item", "reason"),
    [
        ({"market_id": 1, "label": "Home", "value": "2.0", "stopped": True}, "stopped"),
        ({"market_id": 1, "label": "Home", "value": "1.0"}, "bad_price"),
        ({"market_id": 14, "label": "Maybe", "value": "2.0"}, "unknown_label:BTTS:maybe"),
        ({"market_id": 80, "label": "Over", "value": "1.9", "total": None}, "no_total"),
        ({"market_id": 2, "label": "Home", "value": "1.9"}, "market_not_in_scope"),
    ],
)
def test_unusable_odds_are_skipped_with_reason(item, reason):
    assert parse_odd(item) == (None, reason)


def test_parse_fixture_uses_participant_locations():
    item = {
        "id": 19146700,
        "league_id": 8,
        "state_id": 1,
        "starting_at": "2026-10-24 14:00:00",
        "participants": [
            {"name": "Away Side", "meta": {"location": "away"}},
            {"name": "Home Side", "meta": {"location": "home"}},
        ],
    }
    fx, _ = parse_fixture(item, {8: "E0"})
    assert (fx.home_name, fx.away_name, fx.league_code) == ("Home Side", "Away Side", "E0")
    assert fx.kickoff_utc == datetime(2026, 10, 24, 14, tzinfo=UTC)
    assert parse_fixture({**item, "participants": []}, {8: "E0"}) == (None, "participants_missing")
    assert parse_fixture({**item, "league_id": 501}, {8: "E0"}) == (None, "league_not_in_scope")


def client_with(handler, sleeps=None):
    http = httpx.Client(transport=httpx.MockTransport(handler))
    return SportmonksClient(
        "SECRET-TOKEN", http=http, sleep=(sleeps if sleeps is not None else []).append
    )


def test_token_sent_as_header_never_in_url_or_raw_page():
    seen = []

    def handler(req):
        seen.append(req)
        return httpx.Response(200, json={"data": []})

    body, page = client_with(handler).get("fixtures/between/2026-10-19/2026-10-22")
    assert seen[0].headers["Authorization"] == "SECRET-TOKEN"
    assert "SECRET-TOKEN" not in str(seen[0].url)
    assert "SECRET-TOKEN" not in page.url and b"SECRET-TOKEN" not in page.content


def test_cursor_pagination_and_league_filter():
    calls = []

    def handler(req):
        calls.append(req)
        if "cursor" not in req.url.params:
            return httpx.Response(
                200, json={"data": [], "pagination": {"has_more": True, "next_cursor": "abc"}}
            )
        return httpx.Response(200, json={"data": [], "pagination": {"has_more": False}})

    out = SportmonksProvider(client_with(handler)).upcoming_fixtures(
        date(2026, 10, 19), date(2026, 10, 22)
    )
    assert len(calls) == 2 and len(out.pages) == 2
    assert calls[0].url.params["filters"] == "fixtureLeagues:8,564,384,82,301"
    assert calls[1].url.params["cursor"] == "abc"


def test_long_ranges_split_into_100_day_requests():
    paths = []

    def handler(req):
        paths.append(req.url.path)
        return httpx.Response(200, json={"data": []})

    SportmonksProvider(client_with(handler)).upcoming_fixtures(date(2026, 1, 1), date(2026, 6, 1))
    assert paths == [
        "/v3/football/fixtures/between/2026-01-01/2026-04-10",
        "/v3/football/fixtures/between/2026-04-11/2026-06-01",
    ]


def test_rate_limit_waits_for_reset_then_retries():
    sleeps = []
    responses = [
        httpx.Response(429, json={"rate_limit": {"resets_in_seconds": 30}}),
        httpx.Response(200, json={"data": []}),
    ]
    client = client_with(lambda req: responses.pop(0), sleeps)
    client.get("odds/pre-match/fixtures/1/markets/1")
    assert sleeps == [30.0]


def test_long_rate_limit_fails_instead_of_blocking():
    resp = httpx.Response(429, content=json.dumps({"rate_limit": {"resets_in_seconds": 3000}}))
    with pytest.raises(SportmonksError, match="resets in 3000"):
        client_with(lambda req: resp).get("odds/pre-match/fixtures/1/markets/1")


def test_prematch_odds_requests_each_market_and_tags_pages():
    def handler(req):
        market = int(req.url.path.rsplit("/", 1)[1])
        label = {1: "Home", 80: "Over", 14: "Yes"}[market]
        item = {"market_id": market, "label": label, "value": "1.9", "total": "2.5"}
        return httpx.Response(200, json={"data": [item]})

    out = SportmonksProvider(client_with(handler)).prematch_odds("42")
    assert [p.logical_key for p in out.pages] == [
        "odds/pre-match/fixtures/42/markets/1",
        "odds/pre-match/fixtures/42/markets/80",
        "odds/pre-match/fixtures/42/markets/14",
    ]
    assert [(q.market_key, q.page) for q in out.quotes] == [("1X2", 0), ("OU_2.5", 1), ("BTTS", 2)]
