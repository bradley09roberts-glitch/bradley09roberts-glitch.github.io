"""The pull log (odds_pulls) and the boards rebuilt from it (books.pull_board, D-046 follow-up).

quotes and game_lines keep a row once (unique on the provider's per-book update time and the
price), so a pull in which a book's market did not change stores nothing for it, and the stores
alone cannot tell an unchanged book from one that withdrew its market. The pull log records, for
every stored response, each bookmaker-market it held with its last_update and each market it
asked for. Pulls go through the real pipeline against a fake provider (no network); every price
below is made up."""

import json
import re
import sqlite3
from datetime import UTC, datetime, timedelta
from urllib.parse import urlsplit

import pandas as pd
import pytest
import requests

from npl import books, cli, db, rawstore, track
from npl.nba import appdata
from npl.nba import extras as X
from npl.sources import odds_api as oa

T1 = datetime(2026, 11, 3, 8, 0, tzinfo=UTC)
TIP = T1 + timedelta(hours=16)  # 00:00 UTC on 4 Nov
GID = "401909922"


def iso(t: datetime) -> str:
    return t.strftime("%Y-%m-%dT%H:%M:%SZ")


SCHEDULE = pd.DataFrame({"game_id": [401909922.0], "home_display_name": ["Detroit Pistons"],
                         "home_abbreviation": ["DET"],
                         "away_display_name": ["Philadelphia 76ers"],
                         "away_abbreviation": ["PHI"], "date": [iso(TIP)]})
PLAYERS = pd.DataFrame({"season": 2027, "team": ["DET", "PHI"],
                        "player_id": ["4432166", "4431678"],
                        "name": ["Cade Cunningham", "Tyrese Maxey"]})
EVENT = {"id": "ev1", "sport_key": "basketball_nba", "commence_time": iso(TIP),
         "home_team": "Detroit Pistons", "away_team": "Philadelphia 76ers"}


def pts(line, over, under, who="Cade Cunningham"):
    return ("player_points", who, line, over, under)


def reb(line, over, under, who="Tyrese Maxey"):
    return ("player_rebounds", who, line, over, under)


def props(books_: dict) -> dict:
    """An event-odds response: books_ = {book: (last_update, [(market, player, line, o, u)])}."""
    out = []
    for b, (upd, rows) in books_.items():
        markets: dict = {}
        for mk, who, pt, o, u in rows:
            markets.setdefault(mk, {"key": mk, "last_update": upd, "outcomes": []})[
                "outcomes"] += [{"name": "Over", "description": who, "price": o, "point": pt},
                                {"name": "Under", "description": who, "price": u, "point": pt}]
        out.append({"key": b, "title": b, "last_update": upd, "markets": list(markets.values())})
    return {**EVENT, "bookmakers": out}


def featured(books_: dict) -> list:
    """A featured-markets response for the one event: books_ = {book: (last_update, home spread,
    total, home moneyline)}."""
    return [{**EVENT, "bookmakers": [
        {"key": b, "title": b, "last_update": upd, "markets": [
            {"key": "spreads", "last_update": upd, "outcomes": [
                {"name": "Detroit Pistons", "price": 1.91, "point": hs},
                {"name": "Philadelphia 76ers", "price": 1.91, "point": -hs}]},
            {"key": "totals", "last_update": upd, "outcomes": [
                {"name": "Over", "price": 1.9, "point": tot},
                {"name": "Under", "price": 1.9, "point": tot}]},
            {"key": "h2h", "last_update": upd, "outcomes": [
                {"name": "Detroit Pistons", "price": ml},
                {"name": "Philadelphia 76ers", "price": round(4.6 - ml, 2)}]}]}
        for b, (upd, hs, tot, ml) in books_.items()]}]


class Response:
    def __init__(self, url, payload, remaining):
        self.url, self.status_code, self._payload = url, 200, payload
        self.content = json.dumps(payload).encode()
        self.headers = {"Content-Type": "application/json",
                        "x-requests-remaining": str(remaining)}

    def json(self):
        return self._payload


class Provider:
    """Stands in for requests.Session: /events lists EVENT (with `start` as its start time),
    event odds and featured odds answer with the next queued payload (the last one repeats).
    Credits as the provider documents them; events are free."""

    def __init__(self, event_odds=(), featured_odds=(), start=None):
        self.queue = {"odds": list(event_odds), "featured": list(featured_odds)}
        self.start, self.remaining, self.calls = start or iso(TIP), 1000, []

    def _next(self, kind):
        q = self.queue[kind]
        return q.pop(0) if len(q) > 1 else q[0]

    def get(self, url, params=None, timeout=None, headers=None, allow_redirects=True):
        path, params = urlsplit(url).path, dict(params or {})
        assert params.pop("apiKey") == "test-key"
        self.calls.append((path, params))
        if path.endswith("/events"):
            payload = [{**EVENT, "commence_time": self.start}]
        elif re.fullmatch(r".*/events/[^/]+/odds", path):
            payload = self._next("odds")
            got = {m["key"] for b in payload["bookmakers"] for m in b["markets"]}
            self.remaining -= len(got & set(params["markets"].split(",")))
        elif path.endswith("/odds"):
            payload = self._next("featured")
            self.remaining -= 3
        else:
            raise AssertionError(f"unexpected call {path}")
        return Response(url, payload, self.remaining)


@pytest.fixture
def clock(monkeypatch):
    now = {"t": T1}
    monkeypatch.setattr(oa, "utcnow", lambda: now["t"])
    return now


@pytest.fixture(autouse=True)
def offline(monkeypatch, tmp_path):
    def refuse(*a, **k):
        raise AssertionError("a test tried to reach the network")

    monkeypatch.setattr(requests, "get", refuse)
    monkeypatch.setattr(requests.Session, "get", refuse)
    monkeypatch.setenv("ODDS_API_KEY", "test-key")
    monkeypatch.delenv("NPL_ODDS_FLOOR", raising=False)
    monkeypatch.setattr(rawstore, "DATA_HOME", tmp_path)


@pytest.fixture
def con(tmp_path, monkeypatch):
    from npl.nba import canon, live

    monkeypatch.setattr(live, "_latest", lambda con, dataset: (SCHEDULE, {"dataset": dataset}))
    monkeypatch.setattr(canon, "load", lambda: {"player_games": PLAYERS})
    return db.connect(tmp_path / "t.sqlite3")


def pull(con, clock, at: datetime, payload: dict) -> dict:
    """One props pull (npl quotes) at `at` answered with `payload`."""
    clock["t"] = at
    out = oa.snapshot_upcoming(con, regions="us", session=Provider([payload]), sport="nba")
    return out["events"][0]


def close(con, clock, at: datetime, payload: dict) -> dict:
    """One closing snapshot (npl close) at `at` answered with `payload`."""
    clock["t"] = at
    return oa.snapshot_close(con, "nba", 30, session=Provider([payload]))


def fair(o, u):
    return (1 / o) / (1 / o + 1 / u)


THREE = {"fanduel": (iso(T1), [pts(25.5, 1.9, 1.9), reb(3.5, 2.1, 1.72)]),
         "draftkings": (iso(T1), [pts(25.5, 1.87, 1.95), reb(3.5, 2.05, 1.76)]),
         "betmgm": (iso(T1), [pts(25.5, 1.91, 1.91)])}


# --------------------------------------------------------------------------- the log itself
def test_every_pull_is_logged_even_when_nothing_changed(con, clock):
    first = pull(con, clock, T1, props(THREE))
    assert first["stored"] == 10 and first["pull_rows"] == 5 + 5  # 5 book-markets, 5 asked
    again = pull(con, clock, T1 + timedelta(hours=1), props(THREE))  # nothing changed
    assert again["stored"] == 0 and again["pull_rows"] == 10
    p = oa.pulls_frame(con)
    assert len(p) == 20 and p.raw_id.nunique() == 2 and set(p.dataset) == {"event_odds"}
    assert set(p.game_id) == {GID} and set(p.event_id) == {"ev1"} and set(p.sport) == {"nba"}
    asked = p[p.bookmaker.isna() & (p.raw_id == p.raw_id.min())]
    assert asked.provider_market.tolist() == oa.core_markets("nba")  # every market asked for
    assert asked.last_update.isna().all()
    held = p[p.bookmaker.notna() & (p.raw_id == p.raw_id.max())]
    assert sorted(zip(held.bookmaker, held.market, strict=True)) == [
        ("betmgm", "pts"), ("draftkings", "pts"), ("draftkings", "reb"), ("fanduel", "pts"),
        ("fanduel", "reb")]
    assert set(held.last_update) == {iso(T1)} and set(held.observed_at) == {
        iso(T1 + timedelta(hours=1))}
    # the log's update times are the stored rows' quote_time, the pull's time their observed_at
    q = oa.quotes_frame(con)
    assert set(q.quote_time) == {iso(T1)} and set(q.observed_at) == {iso(T1)}
    for sql in ("UPDATE odds_pulls SET market = 'x'", "DELETE FROM odds_pulls"):
        with pytest.raises(sqlite3.DatabaseError, match="append-only"):
            con.execute(sql)


def test_an_empty_response_is_logged_too(con, clock):
    pull(con, clock, T1, props(THREE))
    clock["t"] = T1 + timedelta(hours=1)
    out = oa.snapshot_upcoming(con, session=Provider([{**EVENT, "bookmakers": []}]), sport="nba")
    assert out["events"][0] == {"event": "Philadelphia 76ers @ Detroit Pistons",
                                "player_prop_markets_offered": 0}
    p = oa.pulls_frame(con)
    last = p[p.raw_id == p.raw_id.max()]
    assert len(last) == 5 and last.bookmaker.isna().all() and set(last.game_id) == {GID}
    # nothing on offer at the latest pull: no card line, where the 12-hour rule kept the old one
    assert appdata.consensus_lines(oa.quotes_frame(con), p) == {}
    assert appdata.consensus_lines(oa.quotes_frame(con))[("4432166", "pts")]["books"] == 3


def test_requested_markets_and_limited_requests():
    url = ("https://api.the-odds-api.com/v4/sports/basketball_nba/events/e/odds?dateFormat=iso&"
           "markets=player_points,player_rebounds&oddsFormat=decimal&regions=us")
    assert oa.requested_markets(url) == ["player_points", "player_rebounds"]
    assert oa.requested_markets(url.replace("regions=us", "bookmakers=fanduel")) is None
    rows = oa.pull_rows({**EVENT, "bookmakers": [
        {"key": "fanduel", "last_update": "bk", "markets": [
            {"key": "player_points", "outcomes": []},  # no market time: the book's is used
            {"key": "player_blocks", "last_update": "m", "outcomes": []}]}]},  # not stored
        raw_id=1, dataset="event_odds", sport="nba", observed_at="t",
        requested=["player_points", "player_assists"], game_ids={"ev1": "g"})
    assert [(r["bookmaker"], r["market"], r["last_update"]) for r in rows] == [
        ("fanduel", "pts", "bk"), (None, "pts", None), (None, "ast", None)]
    assert {r["game_id"] for r in rows} == {"g"}


# --------------------------------------------------------------------------- card boards
def test_a_withdrawn_book_drops_out_of_the_card_board(con, clock):
    pull(con, clock, T1, props(THREE))
    later = T1 + timedelta(hours=2)
    moved = {"fanduel": (iso(later), [pts(25.5, 1.83, 2.0), reb(3.5, 2.1, 1.72)]),
             "draftkings": THREE["draftkings"]}  # unchanged; BetMGM has withdrawn
    assert pull(con, clock, later, props(moved))["stored"] == 4  # FanDuel's market re-stamped
    q, p = oa.quotes_frame(con), oa.pulls_frame(con)
    m = appdata.consensus_lines(q, p)[("4432166", "pts")]
    assert m["books"] == 2 and m["mgm_line"] is None and m["observed_at"] == iso(later)
    assert m["fair_over"] == pytest.approx(round((fair(1.83, 2.0) + fair(1.87, 1.95)) / 2, 4))
    # the 12-hour rule alone (no log) cannot tell: it still counts BetMGM's earlier rows
    assert appdata.consensus_lines(q)[("4432166", "pts")]["books"] == 3


def test_an_unchanged_book_stays_on_the_card_board_however_old(con, clock):
    pull(con, clock, T1, props(THREE))
    later = T1 + timedelta(hours=13)
    moved = {**THREE, "fanduel": (iso(later), [pts(25.5, 1.83, 2.0), reb(3.5, 2.1, 1.72)])}
    pull(con, clock, later, props(moved))
    q, p = oa.quotes_frame(con), oa.pulls_frame(con)
    m = appdata.consensus_lines(q, p)[("4432166", "pts")]
    assert m["books"] == 3 and m["mgm_over"] == 1.91 and m["observed_at"] == iso(later)
    # the 12-hour rule drops DraftKings and BetMGM, unchanged for 13 hours
    assert appdata.consensus_lines(q)[("4432166", "pts")]["books"] == 1
    # the stored-lines grid reads the same board (extras.live_lines)
    tips = pd.Series([pd.Timestamp(TIP)], index=[GID])
    lines = X.live_lines(q, tips, p).set_index("market")
    assert lines.loc["pts", "books"] == 3 and lines.loc["pts", "snapshot"] == iso(later)


def test_a_pull_of_some_markets_leaves_the_other_markets_alone(con, clock):
    """npl close asks only for the tracked markets: its pull says nothing about the others."""
    pull(con, clock, T1, props(THREE))
    _track(con)
    at = TIP - timedelta(minutes=20)
    out = close(con, clock, at, props({b: (u, [r for r in rows if r[0] == "player_points"])
                                       for b, (u, rows) in THREE.items()}))
    assert out["results"][0]["stored"] == 0 and out["results"][0]["pull_rows"] == 4
    m = appdata.consensus_lines(oa.quotes_frame(con), oa.pulls_frame(con))
    assert m[("4432166", "pts")]["observed_at"] == iso(at)  # the closing pull
    assert m[("4431678", "reb")]["books"] == 2 and m[("4431678", "reb")]["observed_at"] == iso(T1)


def test_card_lines_come_from_before_the_start_only(con, clock, monkeypatch):
    """Pulls taken after tip-off (the provider still listing the game as upcoming) never set a
    card's US line, nor the form count against it: market_lines keeps the quotes observed
    before the game's start and the pulls taken before it, by the provider's start time
    (pulls_frame, pregame=True) and by ours."""
    pull(con, clock, T1, props(THREE))
    later = iso(TIP + timedelta(hours=2))  # the provider's events call: still upcoming
    moved = {b: (iso(TIP + timedelta(minutes=20)), [pts(27.5, 1.8, 2.0), reb(4.5, 1.9, 1.9)])
             for b in THREE}
    for at, payload in ((TIP + timedelta(minutes=20), props(moved)),  # in play, its start TIP
                        # nothing changed; this response gives a start later than ours
                        (TIP + timedelta(minutes=40), {**props(moved), "commence_time": later})):
        clock["t"] = at
        oa.snapshot_upcoming(con, regions="us", session=Provider([payload], start=later),
                             sport="nba")
    q = oa.quotes_frame(con)
    assert (pd.to_datetime(q.observed_at, utc=True) >= pd.Timestamp(TIP)).any()  # stored
    monkeypatch.setattr(db, "connect", lambda *a, **k: con)
    m = appdata.market_lines({GID: iso(TIP)})
    c = m[("4432166", "pts")]
    assert (c["line"], c["books"], c["observed_at"]) == (25.5, 3, iso(T1))  # the morning board
    assert m[("4431678", "reb")]["line"] == 3.5
    # read as before, the last pull (taken in play) set the line
    assert appdata.consensus_lines(q, oa.pulls_frame(con))[("4432166", "pts")]["line"] == 27.5
    # the provider's start alone is not enough: its later start keeps the 00:40 pull, whose
    # board (the 00:20 rows, left out) is then empty
    start = appdata.pregame_quotes(q, {GID: iso(TIP)})
    assert (pd.to_datetime(start.observed_at, utc=True) < pd.Timestamp(TIP)).all()
    assert ("4432166", "pts") not in appdata.consensus_lines(
        start, oa.pulls_frame(con, "event_odds", "nba", pregame=True))
    assert appdata.market_lines({"another game": iso(TIP)}) == {}


def test_fallback_where_no_pull_was_logged(con, clock):
    """Rows of an event no logged pull covers (stored before the log, not backfilled) use the
    12-hour rule; a logged event in the same frame uses its pulls."""
    pull(con, clock, T1, props(THREE))
    old = [{"source": "the_odds_api", "bookmaker": b, "bookmaker_region": "us",
            "availability": "research_reference", "provider_event_id": "ev9", "game_id": "g9",
            "player_name_raw": "Bo Example", "player_id": "p9", "market": "pts", "line": 9.5,
            "selection": s, "decimal_price": 1.9, "quote_time": "u", "observed_at": o}
           for b, o in (("fanduel", iso(T1)), ("draftkings", iso(T1 - timedelta(hours=13))))
           for s in ("over", "under")]
    assert oa.store_quotes(con, pd.DataFrame(old), None) == 4
    q, p = oa.quotes_frame(con), oa.pulls_frame(con)
    m = appdata.consensus_lines(q, p)
    assert m[("p9", "pts")]["books"] == 1  # DraftKings' rows are 13 hours older
    assert m[("p9", "pts")]["observed_at"] == iso(T1) and m[("4432166", "pts")]["books"] == 3
    rows, times = books.pull_board(q[q.game_id == "g9"], p)
    assert set(rows.bookmaker) == {"fanduel"} and times.board_at.tolist() == [iso(T1)]
    empty, none = books.pull_board(q.iloc[:0], p)
    assert empty.empty and none.empty


# --------------------------------------------------------------------------- closing-line value
def _track(con, flagged=T1):
    track.record(con, [{"game_id": GID, "player_id": "4432166", "player": "Cade Cunningham",
                        "team": "DET", "opponent": "PHI", "market": "pts", "line": 25.5,
                        "side": "over", "price": 2.05, "book": "betmgm", "p_rule": 0.52,
                        "market_p": 0.5, "ev": 0.066, "tip_utc": iso(TIP),
                        "line_seen": iso(flagged)}],
                 rule="r", sport="nba", edition_id="e1", flagged_at=iso(flagged))
    return track.load(con)


def test_a_no_change_closing_pull_still_closes_at_its_own_time(con, clock):
    pull(con, clock, T1, props(THREE))
    t = _track(con)
    at = TIP - timedelta(minutes=20)
    out = close(con, clock, at, props(THREE))  # nothing changed since the morning
    assert out["results"][0]["stored"] == 0 and out["results"][0]["pull_rows"] > 0
    q, p = oa.quotes_frame(con), oa.pulls_frame(con)
    c = track.clv(t, q, p).iloc[0]
    f = (fair(1.9, 1.9) + fair(1.87, 1.95) + fair(1.91, 1.91)) / 3
    assert c.close_at == pd.Timestamp(at).isoformat() and c.close_lead_min == 20.0
    assert c.close_books == 3 and c.fair_close == pytest.approx(f, abs=1e-4)
    assert c.clv == pytest.approx(2.05 * f - 1, abs=1e-3)
    # from the stored rows alone the last snapshot is the morning one, at the flag: no close
    assert pd.isna(track.clv(t, q).iloc[0].clv)
    # npl track reads the log
    rep = track.report(con, pg=pd.DataFrame(columns=["game_id", "player_id", "participation",
                                                     "pts", "reb", "ast"]))
    assert rep["rows"][0]["close_lead_min"] == 20.0


def test_a_book_that_withdrew_before_the_close_drops_out_of_clv(con, clock):
    first = TIP - timedelta(hours=3)
    morning = {"fanduel": (iso(first), [pts(25.5, 1.9, 1.9)]),
               "draftkings": (iso(first), [pts(25.5, 1.87, 1.95)]),
               "betmgm": (iso(first), [pts(25.5, 1.91, 1.91)])}
    pull(con, clock, first, props(morning))
    t = _track(con, flagged=first)
    at = TIP - timedelta(minutes=15)  # FanDuel moved, DraftKings unchanged, BetMGM withdrew
    late = {"fanduel": (iso(at), [pts(25.5, 1.83, 2.0)]), "draftkings": morning["draftkings"]}
    assert close(con, clock, at, props(late))["results"][0]["stored"] == 2
    q = oa.quotes_frame(con)
    c = track.clv(t, q, oa.pulls_frame(con)).iloc[0]
    f = (fair(1.83, 2.0) + fair(1.87, 1.95)) / 2
    assert c.close_books == 2 and c.fair_close == pytest.approx(f, abs=1e-4)
    assert c.close_lead_min == 15.0 and c.clv == pytest.approx(2.05 * f - 1, abs=1e-3)
    # the 12-hour rule alone keeps BetMGM's morning prices on the closing board
    assert track.clv(t, q).iloc[0].close_books == 3


# --------------------------------------------------------------------------- game lines
def test_game_lines_from_the_pull_log(con, clock):
    clock["t"] = T1
    one = {"fanduel": (iso(T1), -4.5, 228.5, 1.55), "draftkings": (iso(T1), -5.0, 229.0, 1.6),
           "betmgm": (iso(T1), -5.0, 228.5, 1.5)}
    later = T1 + timedelta(hours=13)
    two = {"fanduel": (iso(later), -6.0, 226.5, 1.45), "draftkings": one["draftkings"]}
    s1 = oa.snapshot_game_lines(con, "nba", session=Provider(featured_odds=[featured(one)]))
    assert s1["stored"] == 18 and s1["pull_rows"] == 9 + 3
    clock["t"] = later  # FanDuel moved, DraftKings unchanged for 13 hours, BetMGM withdrew
    assert oa.snapshot_game_lines(con, "nba", session=Provider(
        featured_odds=[featured(two)]))["stored"] == 6
    last = later + timedelta(hours=1)
    clock["t"] = last  # and a pull in which nothing changed
    assert oa.snapshot_game_lines(con, "nba", session=Provider(
        featured_odds=[featured(two)]))["stored"] == 0
    s = X.stored_game_lines(con, {GID})[GID]
    assert s["books"] == {"spread": 2, "total": 2, "moneyline": 2}
    assert s["home_spread"] == -5.5 and s["total"] == 227.75  # FanDuel's new and DK's old
    assert s["moneyline"]["home"] == 1.6 and s["moneyline"]["home_book"] == "draftkings"
    assert (s["seen"], s["first_seen"], s["observations"]) == (iso(last), iso(T1), 3)
    assert s["first"]["home_spread"] == -5.0 and s["first"]["total"] == 228.5
    assert s["move"] == {"home_spread": -0.5, "total": -0.75}
    # without the log the 12-hour rule drops DraftKings and keeps nothing else old
    g = oa.game_lines_frame(con, "nba", pregame=True)
    assert X.game_lines(g, {GID})[GID]["books"]["spread"] == 1


# --------------------------------------------------------------------------- backfill
def _raw(con, dataset, part, url, payload, at):
    return rawstore.store(con, source=oa.SOURCE, dataset=dataset, part=part, url=url,
                          content=json.dumps(payload).encode(), filename=f"{dataset}.json",
                          retrieved_at=iso(at)).id


BASE = "https://api.the-odds-api.com/v4/sports/basketball_nba"
PROPS_URL = (f"{BASE}/events/ev1/odds?dateFormat=iso&markets=player_points,player_rebounds&"
             "oddsFormat=decimal&regions=us")


def test_backfill_logs_the_stored_responses_once(con, clock, capsys, monkeypatch):
    """Responses stored before the log existed: their quotes are in the store, the log is
    empty. The backfill reads the stored JSON; counts only."""
    a = _raw(con, "event_odds", "ev1:us:a", PROPS_URL, props(THREE), T1)
    rows = oa.parse_event_odds(props(THREE), observed_at=iso(T1 + timedelta(seconds=1)),
                               request_region="us", market_map=oa.NBA_MARKET_MAP)
    assert oa.store_quotes(con, oa.link_quotes_nba(rows, con), a) == 10
    later = T1 + timedelta(hours=13)
    b = _raw(con, "event_odds", "ev1:us:b", PROPS_URL, props(THREE), later)  # nothing changed
    feat = featured({"fanduel": (iso(T1), -4.5, 228.5, 1.55)})
    c = _raw(con, "featured_odds", "nba:us:c",
             f"{BASE}/odds?dateFormat=iso&markets=h2h,spreads,totals&oddsFormat=decimal&"
             "regions=us", feat, T1)
    oa.store_game_lines(con, oa.link_games(oa.parse_game_lines(
        feat, observed_at=iso(T1), request_region="us", sport="nba"), con, "nba"), c)
    _raw(con, "event_odds", "ev1:us:d", PROPS_URL.replace("regions=us", "bookmakers=fanduel"),
         props(THREE), T1)
    assert oa.pulls_frame(con).empty
    out = oa.backfill_pulls(con)
    # a and b: 5 book-markets and the 2 markets asked for; c: 3 book-markets and 3 asked for
    assert out == {"responses": 4, "already_logged": 0, "logged": 3, "event_odds": 2,
                   "featured_odds": 1, "unreadable": 0, "limited_to_bookmakers": 1,
                   "rows": 7 + 7 + 6, "events": 1}
    p = oa.pulls_frame(con)
    when = p.groupby("raw_id").observed_at.first()
    assert when[a] == iso(T1 + timedelta(seconds=1))  # the time its stored rows carry
    assert when[b] == iso(later) and when[c] == iso(T1)  # the retrieval time otherwise
    assert set(p.game_id) == {GID}
    again = oa.backfill_pulls(con)
    assert again["already_logged"] == 3 and again["rows"] == 0 and again["logged"] == 0
    # the backfilled log now dates the board: the later pull saw every book unchanged
    m = appdata.consensus_lines(oa.quotes_frame(con), p)[("4432166", "pts")]
    assert m["books"] == 3 and m["observed_at"] == iso(later)
    monkeypatch.setattr(db, "connect", lambda *a, **k: con)
    assert cli.cmd_backfill_pulls(cli.build_parser().parse_args(["backfill-pulls"])) == 0
    printed = json.loads(capsys.readouterr().out)
    assert printed["already_logged"] == 3 and all(isinstance(v, int) for v in printed.values())
