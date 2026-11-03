"""Odds pulls against a fake provider (no network): the game_lines table, the featured-markets
parser, the credit floor, the props pull without its /markets pre-call, game-line snapshots and
`npl close` planning. Every price below is made up; the shapes follow real v4 responses."""

import argparse
import json
import re
import sqlite3
from datetime import UTC, datetime, timedelta
from urllib.parse import urlsplit

import pandas as pd
import pytest
import requests

from npl import cli, db, rawstore, track
from npl.sources import odds_api as oa

NOW = datetime(2026, 11, 4, 0, 0, tzinfo=UTC)


def t(minutes: float) -> str:
    return (NOW + timedelta(minutes=minutes)).strftime("%Y-%m-%dT%H:%M:%SZ")


# featured-markets (/odds) response, shaped like the real one of 3 Nov 2026; prices made up
FEATURED = [
    {"id": "ev1", "sport_key": "basketball_nba", "sport_title": "NBA",
     "commence_time": t(20), "home_team": "Detroit Pistons", "away_team": "Philadelphia 76ers",
     "bookmakers": [
         {"key": "fanduel", "title": "FanDuel", "last_update": t(-30), "markets": [
             {"key": "h2h", "last_update": t(-30), "outcomes": [
                 {"name": "Detroit Pistons", "price": 1.8},
                 {"name": "Philadelphia 76ers", "price": 2.05}]},
             {"key": "spreads", "last_update": t(-30), "outcomes": [
                 {"name": "Detroit Pistons", "price": 1.9, "point": -1.5},
                 {"name": "Philadelphia 76ers", "price": 1.92, "point": 1.5}]},
             {"key": "totals", "last_update": t(-30), "outcomes": [
                 {"name": "Over", "price": 1.91, "point": 218.5},
                 {"name": "Under", "price": 1.89, "point": 218.5}]}]},
         {"key": "betmgm", "title": "BetMGM", "last_update": t(-31), "markets": [
             {"key": "h2h", "last_update": t(-31), "outcomes": [
                 {"name": "Detroit Pistons", "price": 1.75},
                 {"name": "Philadelphia 76ers", "price": 1.0}]},  # not a price: skipped
             {"key": "h2h_lay", "last_update": t(-31), "outcomes": [  # not a game market here
                 {"name": "Detroit Pistons", "price": 2.2}]}]}]},
    {"id": "ev2", "sport_key": "basketball_nba", "sport_title": "NBA",
     "commence_time": t(240), "home_team": "Brooklyn Nets", "away_team": "Charlotte Hornets",
     "bookmakers": []},  # listed, not priced yet
    {"id": "ev3", "sport_key": "basketball_nba", "sport_title": "NBA",
     "commence_time": t(-30), "home_team": "Nowhere Ghosts", "away_team": "Charlotte Hornets",
     "bookmakers": [{"key": "draftkings", "title": "DraftKings", "last_update": t(-1),
                     "markets": [{"key": "totals", "last_update": t(-1), "outcomes": [
                         {"name": "Over", "price": 1.87, "point": 230.5},
                         {"name": "Under", "price": 1.95, "point": 230.5}]}]}]},
]

SCHEDULE = pd.DataFrame({
    "game_id": [401909922.0, 401909925.0, 401909929.0],
    "home_display_name": ["Detroit Pistons", "Brooklyn Nets", "Oklahoma City Thunder"],
    "home_abbreviation": ["DET", "BKN", "OKC"],
    "away_display_name": ["Philadelphia 76ers", "Charlotte Hornets", "Cleveland Cavaliers"],
    "away_abbreviation": ["PHI", "CHA", "CLE"],
    "date": [t(20), t(240), t(-10)]})
PLAYERS = pd.DataFrame({"season": 2027, "team": ["DET", "PHI", "CHA"],
                        "player_id": ["4432166", "4431678", "4433134"],
                        "name": ["Cade Cunningham", "Tyrese Maxey", "LaMelo Ball"]})


def _props(event_id, home, away, commence, lines):
    """An event-odds payload: lines = {book: [(market, player, point, over, under)]}."""
    return {"id": event_id, "sport_key": "basketball_nba", "commence_time": commence,
            "home_team": home, "away_team": away, "bookmakers": [
                {"key": book, "title": book, "markets": [
                    {"key": mk, "last_update": t(-5), "outcomes": [
                        {"name": "Over", "description": who, "price": o, "point": pt},
                        {"name": "Under", "description": who, "price": u, "point": pt}]}
                    for mk, who, pt, o, u in rows]}
                for book, rows in lines.items()]}


CLOSE_ODDS = _props("ev1", "Detroit Pistons", "Philadelphia 76ers", t(20), {
    "fanduel": [("player_points", "Cade Cunningham", 25.5, 1.8, 2.0),
                ("player_rebounds", "Tyrese Maxey", 3.5, 2.1, 1.72)],
    "draftkings": [("player_points", "Cade Cunningham", 25.5, 1.83, 1.97),
                   ("player_rebounds", "Tyrese Maxey", 3.5, 2.05, 1.76)]})


class FakeResponse:
    def __init__(self, url, payload, headers):
        self.url, self.status_code, self._payload = url, 200, payload
        self.content = json.dumps(payload).encode()
        self.headers = {"Content-Type": "application/json", **headers}

    def json(self):
        return self._payload


class FakeOddsAPI:
    """Stands in for requests.Session. Routes by path and charges credits as the provider
    documents: events and sports free; featured odds = markets x regions (nothing when no
    events are listed); event odds = unique markets returned x regions; markets = 1."""

    def __init__(self, remaining=1000, events=(), featured=(), event_odds=None,
                 header_paths=None):
        self.remaining, self.events, self.featured = remaining, list(events), list(featured)
        self.event_odds = event_odds or {}
        self.header_paths = header_paths  # None = credit headers on every response
        self.calls = []

    def get(self, url, params=None, timeout=None, headers=None, allow_redirects=True):
        path, params = urlsplit(url).path, dict(params or {})
        assert params.pop("apiKey") == "test-key"
        self.calls.append((path, params))
        regions = len(params.get("regions", "us").split(","))
        if path == "/v4/sports" or path.endswith("/events"):
            payload, cost = ([{"key": "basketball_nba"}] if path == "/v4/sports"
                             else self.events), 0
        elif path.endswith("/markets"):
            payload, cost = {"bookmakers": []}, 1
        elif m := re.fullmatch(r".*/events/([^/]+)/odds", path):
            payload = self.event_odds.get(m.group(1), {"id": m.group(1), "bookmakers": []})
            got = {k["key"] for b in payload["bookmakers"] for k in b["markets"]}
            cost = len(got & set(params["markets"].split(","))) * regions
        elif path.endswith("/odds"):
            payload = self.featured
            cost = len(params["markets"].split(",")) * regions if payload else 0
        else:
            raise AssertionError(f"unexpected call {path}")
        self.remaining -= cost
        hdr = ({"x-requests-remaining": str(self.remaining), "x-requests-last": str(cost)}
               if self.header_paths is None or path in self.header_paths else {})
        return FakeResponse(url, payload, hdr)

    def paths(self):
        return [p for p, _ in self.calls]


@pytest.fixture(autouse=True)
def offline(monkeypatch, tmp_path):
    def refuse(*a, **k):
        raise AssertionError("a test tried to reach the network")

    monkeypatch.setattr(requests, "get", refuse)
    monkeypatch.setattr(requests.Session, "get", refuse)
    monkeypatch.setenv("ODDS_API_KEY", "test-key")
    monkeypatch.delenv("NPL_ODDS_FLOOR", raising=False)
    monkeypatch.setattr(rawstore, "DATA_HOME", tmp_path)
    monkeypatch.setattr(oa, "utcnow", lambda: NOW)


@pytest.fixture
def con(tmp_path, monkeypatch):
    from npl.nba import canon, live

    monkeypatch.setattr(live, "_latest", lambda con, dataset: (SCHEDULE, {"dataset": dataset}))
    monkeypatch.setattr(canon, "load", lambda: {"player_games": PLAYERS})
    return db.connect(tmp_path / "t.sqlite3")


# --------------------------------------------------------------------------- game_lines table
def test_parse_game_lines_reads_every_book_market_and_outcome():
    rows = oa.parse_game_lines(FEATURED, observed_at=t(0), request_region="us", sport="nba")
    assert len(rows) == 9  # 6 FanDuel + 1 BetMGM moneyline + 2 DraftKings totals
    fd = {(r["market"], r["side"]): r for r in rows if r["bookmaker"] == "fanduel"}
    assert set(fd) == {("moneyline", "home"), ("moneyline", "away"), ("spread", "home"),
                       ("spread", "away"), ("total", "over"), ("total", "under")}
    assert fd[("moneyline", "home")]["point"] is None
    assert fd[("moneyline", "home")]["outcome"] == "Detroit Pistons"
    assert fd[("spread", "home")]["point"] == -1.5 and fd[("total", "under")]["point"] == 218.5
    assert fd[("total", "over")]["quote_time"] == t(-30)
    assert {r["availability"] for r in rows} == {"research_reference"}  # US books
    mgm = [r for r in rows if r["bookmaker"] == "betmgm"]
    assert len(mgm) == 1 and mgm[0]["side"] == "home"  # the 1.0 price and h2h_lay are dropped


def test_parse_game_lines_sides_and_regions():
    ev = {"id": "x", "commence_time": t(0), "home_team": "A", "away_team": "B", "bookmakers": [
        {"key": "williamhill", "markets": [{"key": "h2h", "outcomes": [
            {"name": "A", "price": 2.1}, {"name": "Draw", "price": 3.4},
            {"name": "Somebody Else", "price": 4.0}]}]}]}
    rows = oa.parse_game_lines({"data": [ev]}, observed_at=t(0), request_region="uk",
                               sport="nba")  # historical responses wrap events in `data`
    assert [r["side"] for r in rows] == ["home", "draw", None]
    assert {r["availability"] for r in rows} == {"verified_uk"}


def test_game_lines_table_is_append_only_and_stores_a_response_once(con):
    rows = oa.parse_game_lines(FEATURED, observed_at=t(0), request_region="us", sport="nba")
    q = oa.link_games(rows, con, "nba")
    assert oa.store_game_lines(con, q, None) == 9
    assert oa.store_game_lines(con, q, None) == 0  # moneylines too, though their point is NULL
    g = oa.game_lines_frame(con, "nba")
    assert len(g) == 9 and set(g.game_id.dropna()) == {"401909922"}
    assert g[g.event_id == "ev3"].game_id.isna().all()  # no such team: left unlinked
    for sql in ("UPDATE game_lines SET price = 3", "DELETE FROM game_lines"):
        with pytest.raises(sqlite3.DatabaseError, match="append-only"):
            con.execute(sql)
    with pytest.raises(sqlite3.IntegrityError):
        con.execute("INSERT INTO game_lines(source, sport, event_id, commence_time, home_team,"
                    " away_team, bookmaker, bookmaker_region, availability, market, outcome,"
                    " price, observed_at) VALUES ('s','nba','e','t','A','B','b','us',"
                    "'research_reference','moneyline','A',1.0,'t')")
    # the in-play event (started before the snapshot) drops out of the pre-game view
    assert set(oa.game_lines_frame(con, "nba", pregame=True).event_id) == {"ev1"}


def test_snapshot_game_lines_one_call_for_the_slate(con):
    fake = FakeOddsAPI(remaining=1000, events=FEATURED, featured=FEATURED)
    out = oa.snapshot_game_lines(con, "nba", "us", session=fake)
    assert fake.paths() == ["/v4/sports/basketball_nba/events", "/v4/sports/basketball_nba/odds"]
    assert fake.calls[1][1] == {"regions": "us", "markets": "h2h,spreads,totals",
                                "oddsFormat": "decimal", "dateFormat": "iso"}
    assert out["stored"] == 9 and out["events_listed"] == 3 and out["events_priced"] == 2
    assert out["events_under_way"] == 1 and out["linked_games"] == ["401909922"]
    assert out["unlinked_events"] == ["Charlotte Hornets @ Nowhere Ghosts"]
    assert out["credits_left"] == 997 and out["skipped"] == []
    raw = con.execute("SELECT url FROM raw_files WHERE dataset = 'featured_odds'").fetchall()
    assert len(raw) == 1 and "test-key" not in raw[0][0]
    assert oa.status(con)["game_lines"] == 9


def test_snapshot_game_lines_respects_the_floor(con):
    fake = FakeOddsAPI(remaining=402, featured=FEATURED)  # 402 - 3 = 399 < 400
    out = oa.snapshot_game_lines(con, "nba", "us", session=fake)
    assert fake.paths() == ["/v4/sports/basketball_nba/events"] and out["stored"] == 0
    assert "below the floor of 400" in out["skipped"][0]["reason"]


# --------------------------------------------------------------------------- credit floor
def test_floor_from_env(monkeypatch):
    assert oa.credit_floor() == 400 and oa.Budget().floor == 400
    monkeypatch.setenv("NPL_ODDS_FLOOR", "250")
    assert oa.Budget().floor == 250
    monkeypatch.setenv("NPL_ODDS_FLOOR", "lots")
    with pytest.raises(ValueError, match="NPL_ODDS_FLOOR"):
        oa.Budget()


def test_budget_allows_down_to_the_floor_and_counts_without_a_header():
    b = oa.Budget(floor=400)
    assert not b.allow(1, "x") and "unknown" in b.skipped[-1]["reason"]  # nothing read yet
    b.observe({"x-requests-remaining": "405"})
    assert b.allow(5, "five") and not b.allow(6, "six")
    assert b.skipped[-1] == {"call": "six", "estimated_credits": 6, "credits_left": 405,
                             "floor": 400, "reason": "405 credits left: 6 more would go below "
                                                     "the floor of 400"}
    b.spent(5, {})  # header missing on the paid response: the estimate is taken off
    assert b.remaining == 400
    b.spent(2, {"x-requests-remaining": "401"})  # the provider's figure wins when present
    assert b.remaining == 401


def test_props_pull_stops_at_the_floor_and_lists_what_was_skipped(con):
    events = [{"id": f"ev{i}", "home_team": "Detroit Pistons", "away_team": "Philadelphia 76ers",
               "commence_time": t(20)} for i in (1, 2, 3)]
    odds = {f"ev{i}": CLOSE_ODDS for i in (1, 2, 3)}
    fake = FakeOddsAPI(remaining=408, events=events, event_odds=odds)
    out = oa.snapshot_upcoming(con, regions="us", hours_ahead=24, session=fake, sport="nba")
    # 5 core markets requested per event (estimate 5), 2 returned and charged:
    # 408 -> 406 -> 404, and a third call could leave 399
    assert [p for p in fake.paths() if p.endswith("/odds")] == [
        "/v4/sports/basketball_nba/events/ev1/odds", "/v4/sports/basketball_nba/events/ev2/odds"]
    assert out["credits_left"] == 404 and out["floor"] == 400
    assert [s["call"] for s in out["skipped"]] == ["Philadelphia 76ers @ Detroit Pistons props"]
    assert out["events"][2] == {"event": "Philadelphia 76ers @ Detroit Pistons",
                                "skipped": out["skipped"][0]["reason"]}


def test_unknown_credits_fall_back_to_the_sports_list_then_refuse(con):
    events = [{"id": "ev1", "home_team": "Detroit Pistons", "away_team": "Philadelphia 76ers",
               "commence_time": t(20)}]
    fake = FakeOddsAPI(remaining=1000, events=events, event_odds={"ev1": CLOSE_ODDS},
                       header_paths={"/v4/sports"})  # events call without the header
    out = oa.snapshot_upcoming(con, hours_ahead=24, session=fake, sport="nba")
    assert fake.paths()[:2] == ["/v4/sports/basketball_nba/events", "/v4/sports"]
    assert out["events"][0]["stored"] == 8 and out["skipped"] == []
    none = FakeOddsAPI(remaining=1000, events=events, event_odds={"ev1": CLOSE_ODDS},
                       header_paths=set())
    out = oa.snapshot_upcoming(con, hours_ahead=24, session=none, sport="nba")
    assert not [p for p in none.paths() if p.endswith("/odds")]
    assert "unknown" in out["skipped"][0]["reason"]


# --------------------------------------------------------------------------- props pull
def test_props_pull_makes_no_markets_precall(con):
    events = [
        {"id": "ev1", "home_team": "Detroit Pistons", "away_team": "Philadelphia 76ers",
         "commence_time": t(20)},
        {"id": "ev2", "home_team": "Brooklyn Nets", "away_team": "Charlotte Hornets",
         "commence_time": t(240)},  # no props posted yet: empty response, costs nothing
        {"id": "ev9", "home_team": "Brooklyn Nets", "away_team": "Charlotte Hornets",
         "commence_time": t(60 * 72)}]  # outside the window
    fake = FakeOddsAPI(remaining=1000, events=events, event_odds={"ev1": CLOSE_ODDS})
    out = oa.snapshot_upcoming(con, regions="us", hours_ahead=48, session=fake, sport="nba")
    assert not any(p.endswith("/markets") for p in fake.paths())
    assert fake.paths() == ["/v4/sports/basketball_nba/events",
                            "/v4/sports/basketball_nba/events/ev1/odds",
                            "/v4/sports/basketball_nba/events/ev2/odds"]
    assert fake.calls[1][1]["markets"] == ",".join(oa.core_markets("nba"))
    assert out["events"][0]["markets"] == ["player_points", "player_rebounds"]
    assert out["events"][0]["stored"] == 8 and out["events"][0]["linked_players"] == 8
    assert out["events"][1] == {"event": "Charlotte Hornets @ Brooklyn Nets",
                                "player_prop_markets_offered": 0}
    assert fake.remaining == 998 and out["credits_left"] == 998  # 2 markets came back
    q = oa.quotes_frame(con)
    assert set(q.game_id) == {"401909922"} and set(q.market) == {"pts", "reb"}


# --------------------------------------------------------------------------- npl close
def _track(con):
    base = {"team": "DET", "opponent": "PHI", "side": "over", "price": 1.95, "book": "betmgm",
            "line_seen": t(-120)}
    rows_a = [{**base, "game_id": "401909922", "player_id": "4432166", "player": "Cade Cunningham",
               "market": "pts", "line": 25.5, "tip_utc": t(20)},
              {**base, "game_id": "401909925", "player_id": "4433134", "player": "LaMelo Ball",
               "market": "ast", "line": 7.5, "tip_utc": t(240)},  # later tonight
              {**base, "game_id": "401909929", "player_id": "1", "player": "Started Already",
               "market": "pts", "line": 20.5, "tip_utc": t(-10)}]
    rows_b = [{**base, "game_id": "401909922", "player_id": "4432166", "player": "Cade Cunningham",
               "market": "pts", "line": 25.5, "tip_utc": t(20)},
              {**base, "game_id": "401909922", "player_id": "4431678", "player": "Tyrese Maxey",
               "team": "PHI", "opponent": "DET", "market": "reb", "line": 3.5, "side": "under",
               "price": 1.9, "tip_utc": t(20)}]
    kw = {"sport": "nba", "edition_id": "e1", "flagged_at": t(-120)}
    track.record(con, rows_a, rule="rule-a", **kw)
    track.record(con, rows_b, rule="rule-b", **kw)


def test_close_plan_picks_tracked_games_about_to_tip_and_their_markets(con):
    _track(con)
    plan = oa.plan_close(con, "nba", 30)
    assert [p["game_id"] for p in plan] == ["401909922"]  # not later tonight, not started
    assert plan[0]["markets"] == ["player_points", "player_rebounds"]  # tracked markets only
    assert plan[0]["tracked_props"] == 3 and plan[0]["minutes_to_tip"] == 20.0
    assert [p["game_id"] for p in oa.plan_close(con, "nba", 300)] == ["401909922", "401909925"]
    assert oa.plan_close(con, "nba", 10) == []


def test_close_dry_run_makes_no_paid_call(con):
    _track(con)
    fake = FakeOddsAPI(remaining=1000, events=FEATURED, event_odds={"ev1": CLOSE_ODDS})
    out = oa.snapshot_close(con, "nba", 30, dry_run=True, session=fake)
    assert fake.paths() == ["/v4/sports/basketball_nba/events"] and fake.remaining == 1000
    p = out["planned"][0]
    assert (p["event_id"], p["label"], p["estimated_credits"]) == ("ev1", "PHI @ DET", 2)
    assert out["estimated_credits"] == 2 and out["credits_left"] == 1000
    assert con.execute("SELECT COUNT(*) FROM quotes").fetchone()[0] == 0
    text = "\n".join(cli.close_lines(out))
    assert "Planned: 1 call, up to 2 credits" in text and "Dry run: no paid call made." in text


def test_close_snapshot_stores_quotes_that_clv_uses(con):
    _track(con)
    fake = FakeOddsAPI(remaining=1000, events=FEATURED, event_odds={"ev1": CLOSE_ODDS})
    out = oa.snapshot_close(con, "nba", 30, session=fake)
    assert fake.paths()[-1] == "/v4/sports/basketball_nba/events/ev1/odds"
    assert fake.calls[-1][1]["markets"] == "player_points,player_rebounds"
    assert out["results"][0]["stored"] == 8 and out["credits_left"] == 998
    c = track.clv(track.load(con), oa.quotes_frame(con)).set_index(["rule", "player_id"])
    cade = c.loc[("rule-a", "4432166")]
    fair = ((1 / 1.8) / (1 / 1.8 + 1 / 2.0) + (1 / 1.83) / (1 / 1.83 + 1 / 1.97)) / 2
    assert cade.close_lead_min == 20.0 and cade.close_books == 2
    assert cade.fair_close == pytest.approx(fair, abs=1e-4)
    assert cade.clv == pytest.approx(1.95 * fair - 1, abs=1e-3)
    assert c.loc[("rule-b", "4431678")].clv == pytest.approx(
        1.9 * ((1 / 1.72) / (1 / 2.1 + 1 / 1.72) + (1 / 1.76) / (1 / 2.05 + 1 / 1.76)) / 2 - 1,
        abs=1e-3)
    assert pd.isna(c.loc[("rule-a", "4433134")].clv)  # not re-priced: tips later


def test_close_skips_below_the_floor_and_unlisted_events(con):
    _track(con)
    fake = FakeOddsAPI(remaining=401, events=FEATURED, event_odds={"ev1": CLOSE_ODDS})
    out = oa.snapshot_close(con, "nba", 30, session=fake)
    assert not [p for p in fake.paths() if p.endswith("/odds")] and out["results"] == []
    assert "below the floor of 400" in out["skipped"][0]["reason"]
    gone = FakeOddsAPI(remaining=1000, events=[], event_odds={"ev1": CLOSE_ODDS})
    out = oa.snapshot_close(con, "nba", 30, session=gone)
    assert out["skipped"][0]["reason"] == "not in the provider's event list"
    assert gone.paths() == ["/v4/sports/basketball_nba/events"]


def _ev1(commence):
    return [{"id": "ev1", "home_team": "Detroit Pistons", "away_team": "Philadelphia 76ers",
             "commence_time": commence}]


def test_close_skips_a_game_the_provider_shows_as_started(con):
    """The tip-off recorded with the tracked props says 20 minutes from now; the free events
    call says the game started 5 minutes ago: no call (its prices would be in-play)."""
    _track(con)
    fake = FakeOddsAPI(remaining=1000, events=_ev1(t(-5)), event_odds={"ev1": CLOSE_ODDS})
    out = oa.snapshot_close(con, "nba", 30, session=fake)
    assert fake.paths() == ["/v4/sports/basketball_nba/events"] and fake.remaining == 1000
    assert out["started"] == [{"game_id": "401909922", "label": "PHI @ DET", "start_utc": t(-5)}]
    assert "already started" in out["skipped"][0]["reason"] and out["results"] == []
    p = out["planned"][0]
    assert (p["tip_utc"], p["recorded_tip_utc"], p["minutes_to_tip"]) == (t(-5), t(20), -5.0)
    assert len(out["warnings"]) == 1 and "25 min earlier" in out["warnings"][0]
    assert out["estimated_credits"] == 0
    text = "\n".join(cli.close_lines(out))
    assert "started at" in text and "WARNING PHI @ DET" in text and "Planned: 0 calls" in text
    assert con.execute("SELECT COUNT(*) FROM quotes").fetchone()[0] == 0
    dry = oa.snapshot_close(con, "nba", 30, dry_run=True,
                            session=FakeOddsAPI(events=_ev1(t(0))))  # at "now" counts as started
    assert [g["game_id"] for g in dry["started"]] == ["401909922"]


@pytest.mark.parametrize("start,warned", [(t(35), True), (t(28), False), (t(10), False)])
def test_close_warns_when_the_provider_start_is_over_10_minutes_off(con, start, warned):
    _track(con)
    fake = FakeOddsAPI(remaining=1000, events=_ev1(start), event_odds={"ev1": CLOSE_ODDS})
    out = oa.snapshot_close(con, "nba", 30, session=fake)
    assert fake.paths()[-1] == "/v4/sports/basketball_nba/events/ev1/odds"  # still taken
    assert out["results"][0]["stored"] == 8 and out["started"] == []
    assert bool(out["warnings"]) is warned
    if warned:
        assert "15 min later" in out["warnings"][0] and "(00:20 UTC)" in out["warnings"][0]
    p = out["planned"][0]
    assert p["tip_utc"] == start and p["recorded_tip_utc"] == t(20)


def test_close_can_be_limited_to_some_rules(con, monkeypatch):
    _track(con)
    a = oa.plan_close(con, "nba", 30, rules=["rule-a"])
    assert [(p["game_id"], p["markets"], p["tracked_props"]) for p in a] == [
        ("401909922", ["player_points"], 1)]
    b = oa.plan_close(con, "nba", 30, rules=["rule-b", "rule-a"])
    assert b[0]["markets"] == ["player_points", "player_rebounds"] and b[0]["tracked_props"] == 3
    assert oa.plan_close(con, "nba", 30, rules=["no-such-rule"]) == []
    fake = FakeOddsAPI(remaining=1000, events=FEATURED, event_odds={"ev1": CLOSE_ODDS})
    out = oa.snapshot_close(con, "nba", 30, session=fake, rules=["rule-a"])
    assert fake.calls[-1][1]["markets"] == "player_points" and out["rules"] == ["rule-a"]
    none = oa.snapshot_close(con, "nba", 30, session=FakeOddsAPI(), rules=["no-such-rule"])
    assert "Nothing planned: no game with props tracked under no-such-rule" in "\n".join(
        cli.close_lines(none))
    args = cli.build_parser().parse_args(["close", "--within", "30", "--rule", "rule-a",
                                          "--rule", "rule-b"])
    assert args.rules == ["rule-a", "rule-b"]
    assert cli.build_parser().parse_args(["close", "--within", "30"]).rules is None
    seen = {}
    monkeypatch.setattr(db, "connect", lambda *a, **k: con)
    monkeypatch.setattr(oa, "snapshot_close", lambda con, **kw: seen.update(kw) or none)
    cli.cmd_close(args)
    assert seen["rules"] == ["rule-a", "rule-b"]


def test_close_with_nothing_planned_makes_no_call(con):
    fake = FakeOddsAPI()
    out = oa.snapshot_close(con, "nba", 30, session=fake)
    assert out["planned"] == [] and fake.calls == []
    assert "Nothing planned" in "\n".join(cli.close_lines(out))


# --------------------------------------------------------------------------- command line
def test_cli_close_and_quotes_flags():
    a = cli.build_parser().parse_args(["close", "--sport", "nba", "--within", "30", "--dry-run"])
    assert a.func is cli.cmd_close and a.within == 30 and a.dry_run and a.regions == "us"
    with pytest.raises(SystemExit):
        cli.build_parser().parse_args(["close", "--sport", "nba"])  # --within is required
    a = cli.build_parser().parse_args(["quotes", "--sport", "nba", "--no-game-lines"])
    assert a.no_game_lines


@pytest.mark.parametrize("sport,flag,expect", [("nba", False, True), ("nba", True, False),
                                               ("nfl", False, False)])
def test_cli_quotes_takes_game_lines_for_the_nba(con, monkeypatch, capsys, sport, flag, expect):
    seen = {}

    def props(con, **kw):
        seen["budget"] = kw["budget"]
        kw["budget"].observe({"x-requests-remaining": "999"})
        return {"events": []}

    def lines(con, **kw):
        seen["lines_budget"] = kw["budget"]
        return {"stored": 0}

    monkeypatch.setattr(db, "connect", lambda *a, **k: con)
    monkeypatch.setattr(oa, "snapshot_upcoming", props)
    monkeypatch.setattr(oa, "snapshot_game_lines", lines)
    cli.cmd_quotes(argparse.Namespace(regions="us", hours_ahead=24, sport=sport,
                                      no_game_lines=flag))
    assert ("lines_budget" in seen) is expect
    if expect:
        assert seen["lines_budget"] is seen["budget"]  # one floor across the whole pull
    assert "Credits left: 999 (floor 400" in capsys.readouterr().out
