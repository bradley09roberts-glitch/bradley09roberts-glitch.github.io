"""Complete boards from the deduplicated stores (books.complete_board, D-046). quotes keep a row
once per (book, event, player, market, line, side, provider last_update, price) and game_lines
likewise without our observation time, so a later pull stores nothing for a book whose market
did not change. Readers rebuild the board per bookmaker: that book's rows from its own latest
observation at or before the reference time, dropped when more than 12 hours older. Every
price below is made up."""

import pandas as pd
import pytest

from npl import books, db, track
from npl.nba import appdata
from npl.nba import extras as X
from npl.sources import odds_api as oa

T1, T2 = "2026-11-02T14:00:00Z", "2026-11-02T23:30:00Z"  # 9.5 hours apart
T0 = "2026-11-02T10:00:00Z"  # 13.5 hours before T2
TIP = "2026-11-03T00:00:00Z"


def _quotes(obs, book, line, over, under, updated, player=("p1", "Ann Example")):
    pid, name = player
    return [{"source": "the_odds_api", "bookmaker": book, "bookmaker_region": "us",
             "availability": "research_reference", "provider_event_id": "ev1", "game_id": "g1",
             "player_name_raw": name, "player_id": pid, "market": "pts", "line": line,
             "selection": sel, "decimal_price": price, "quote_time": updated, "observed_at": obs}
            for sel, price in (("over", over), ("under", under))]


def _pull(con, rows):
    return oa.store_quotes(con, pd.DataFrame(rows), None)


def _fair(o, u):
    return (1 / o) / (1 / o + 1 / u)


@pytest.fixture
def con(tmp_path):
    return db.connect(tmp_path / "t.sqlite3")


def test_complete_board_keeps_each_books_latest_rows_within_12_hours():
    q = pd.DataFrame(_quotes(T0, "betmgm", 20.5, 1.9, 1.9, "u0")
                     + _quotes(T1, "draftkings", 20.5, 1.9, 1.9, "u1")
                     + _quotes(T1, "fanduel", 20.5, 1.9, 1.9, "u1")
                     + _quotes(T2, "fanduel", 21.5, 1.8, 2.0, "u2")
                     + _quotes("2026-11-03T00:30:00Z", "fanduel", 30.5, 1.8, 2.0, "u3"))
    b = books.complete_board(q, ref=T2)
    # fanduel from T2 only (its T1 rows are superseded, the later in-play rows are after the
    # reference), draftkings from T1 (unchanged), betmgm left out (13.5 hours older than T2)
    assert sorted(zip(b.bookmaker, b.observed_at, strict=True)) == [
        ("draftkings", T1), ("draftkings", T1), ("fanduel", T2), ("fanduel", T2)]
    # no reference: the game and market's own latest observation (here 00:30), so FanDuel's
    # 00:30 rows and DraftKings' T1 rows, 10.5 hours older
    late = books.complete_board(q)
    assert sorted(set(zip(late.bookmaker, late.observed_at, strict=True))) == [
        ("draftkings", T1), ("fanduel", "2026-11-03T00:30:00Z")]
    assert books.complete_board(q.iloc[:0]).empty


def test_an_unchanged_book_stays_in_the_consensus(con):
    first = (_quotes(T1, "fanduel", 20.5, 1.9, 1.9, "u1")
             + _quotes(T1, "draftkings", 20.5, 1.87, 1.95, "u1")
             + _quotes(T1, "betmgm", 20.5, 1.91, 1.91, "u1"))
    assert _pull(con, first) == 6
    # second pull: FanDuel moved its price; the other two books are unchanged, so the store
    # keeps none of their rows again
    second = (_quotes(T2, "fanduel", 20.5, 1.83, 2.0, "u2")
              + _quotes(T2, "draftkings", 20.5, 1.87, 1.95, "u1")
              + _quotes(T2, "betmgm", 20.5, 1.91, 1.91, "u1"))
    assert _pull(con, second) == 2
    q = oa.quotes_frame(con)
    assert set(q[q.observed_at == T2].bookmaker) == {"fanduel"}  # the partial latest pull
    m = appdata.consensus_lines(q)[("p1", "pts")]
    assert m["line"] == 20.5 and m["books"] == 3  # not 1, as the newest pull alone would give
    assert m["fair_over"] == pytest.approx(
        round((_fair(1.83, 2.0) + _fair(1.87, 1.95) + _fair(1.91, 1.91)) / 3, 4))
    assert m["mgm_over"] == 1.91 and m["observed_at"] == T2
    # the same board feeds the stored-lines grid (extras.live_lines)
    lines = X.live_lines(q, pd.Series([pd.Timestamp(TIP)], index=["g1"]))
    assert lines.books.tolist() == [3] and lines.line.tolist() == [20.5]


def test_a_book_older_than_12_hours_drops_out_of_the_consensus(con):
    _pull(con, _quotes(T0, "betmgm", 20.5, 1.91, 1.91, "u0"))
    _pull(con, _quotes(T1, "draftkings", 20.5, 1.87, 1.95, "u1")
          + _quotes(T2, "fanduel", 20.5, 1.83, 2.0, "u2"))
    m = appdata.consensus_lines(oa.quotes_frame(con))[("p1", "pts")]
    assert m["books"] == 2 and m["mgm_line"] is None  # BetMGM's rows are 13.5 hours old


def _tracked(con, line=20.5, flagged=T1):
    track.record(con, [{"game_id": "g1", "player_id": "p1", "player": "Ann Example",
                        "team": "AAA", "opponent": "BBB", "market": "pts", "line": line,
                        "side": "over", "price": 2.05, "book": "betmgm", "p_rule": 0.52,
                        "market_p": 0.5, "ev": 0.066, "tip_utc": TIP, "line_seen": flagged}],
                 rule="r", sport="nba", edition_id="e1", flagged_at=flagged)
    return track.load(con)


def test_the_closing_board_includes_unchanged_books(con):
    t = _tracked(con)
    _pull(con, _quotes(T1, "fanduel", 20.5, 1.9, 1.9, "u1")
          + _quotes(T1, "draftkings", 20.5, 1.87, 1.95, "u1")
          + _quotes(T1, "betmgm", 20.5, 1.91, 1.91, "u1")
          # another player's prop at FanDuel moves at the close: FanDuel's whole market gets a
          # new update time, so all its rows are stored again
          + _quotes(T1, "fanduel", 9.5, 1.9, 1.9, "u1", player=("p2", "Bo Example")))
    _pull(con, _quotes(T2, "fanduel", 20.5, 1.9, 1.9, "u2")
          + _quotes(T2, "fanduel", 10.5, 1.9, 1.9, "u2", player=("p2", "Bo Example"))
          + _quotes(T2, "draftkings", 20.5, 1.87, 1.95, "u1")  # unchanged: not stored again
          + _quotes(T2, "betmgm", 20.5, 1.91, 1.91, "u1"))
    c = track.clv(t, oa.quotes_frame(con)).iloc[0]
    fair = (_fair(1.9, 1.9) + _fair(1.87, 1.95) + _fair(1.91, 1.91)) / 3
    assert c.close_at == pd.Timestamp(T2).isoformat() and c.close_lead_min == 30.0
    assert c.close_books == 3  # FanDuel at T2 with DraftKings and BetMGM from T1
    assert c.fair_close == pytest.approx(fair, abs=1e-4)
    assert c.clv == pytest.approx(2.05 * fair - 1, abs=1e-3)


def test_the_closing_board_drops_books_older_than_12_hours(con):
    t = _tracked(con, flagged=T0)
    _pull(con, _quotes(T0, "betmgm", 20.5, 1.91, 1.91, "u0"))
    _pull(con, _quotes(T1, "draftkings", 20.5, 1.87, 1.95, "u1")
          + _quotes(T2, "fanduel", 20.5, 1.9, 1.9, "u2"))
    c = track.clv(t, oa.quotes_frame(con)).iloc[0]
    assert c.close_books == 2 and c.fair_close == pytest.approx(
        (_fair(1.9, 1.9) + _fair(1.87, 1.95)) / 2, abs=1e-4)


def test_a_player_off_the_closing_board_has_no_close(con):
    """His books re-posted their markets without him: their latest rows hold no price for him,
    so his earlier rows are not taken as the close."""
    t = _tracked(con)
    for book in ("fanduel", "draftkings"):
        _pull(con, _quotes(T1, book, 20.5, 1.9, 1.9, "u1")
              + _quotes(T1, book, 9.5, 1.9, 1.9, "u1", player=("p2", "Bo Example")))
        _pull(con, _quotes(T2, book, 10.5, 1.9, 1.9, "u2", player=("p2", "Bo Example")))
    c = track.clv(t, oa.quotes_frame(con)).iloc[0]
    assert c.close_books == 0 and pd.isna(c.clv)


def _gl(obs, book, home_spread, total, ml_home, updated):
    base = {"source": "the_odds_api", "sport": "nba", "event_id": "ev1", "game_id": "g1",
            "commence_time": TIP, "home_team": "Home", "away_team": "Away", "bookmaker": book,
            "bookmaker_region": "us", "availability": "research_reference",
            "quote_time": updated, "observed_at": obs}
    return [{**base, "market": "spread", "outcome": "Home", "side": "home",
             "point": home_spread, "price": 1.91},
            {**base, "market": "spread", "outcome": "Away", "side": "away",
             "point": -home_spread, "price": 1.91},
            {**base, "market": "total", "outcome": "Over", "side": "over", "point": total,
             "price": 1.9},
            {**base, "market": "total", "outcome": "Under", "side": "under", "point": total,
             "price": 1.9},
            {**base, "market": "moneyline", "outcome": "Home", "side": "home", "point": None,
             "price": ml_home},
            {**base, "market": "moneyline", "outcome": "Away", "side": "away", "point": None,
             "price": round(4.6 - ml_home, 2)}]


def test_game_lines_keep_unchanged_books_on_the_latest_board(con):
    first = (_gl(T1, "fanduel", -4.5, 228.5, 1.55, "u1") + _gl(T1, "draftkings", -5.0, 229.0, 1.6, "u1")
             + _gl(T1, "betmgm", -5.0, 228.5, 1.5, "u1"))
    assert oa.store_game_lines(con, pd.DataFrame(first), None) == 18
    second = (_gl(T2, "fanduel", -6.0, 226.5, 1.45, "u2")
              + _gl(T2, "draftkings", -5.0, 229.0, 1.6, "u1")  # unchanged: stored once only
              + _gl(T2, "betmgm", -5.0, 228.5, 1.5, "u1"))
    assert oa.store_game_lines(con, pd.DataFrame(second), None) == 6
    s = X.stored_game_lines(con, {"g1"})["g1"]
    assert s["books"] == {"spread": 3, "total": 3, "moneyline": 3}  # not FanDuel alone
    assert s["home_spread"] == -5.0 and s["total"] == 228.5  # medians of -6, -5, -5 and totals
    assert s["moneyline"]["home"] == 1.6 and s["moneyline"]["home_book"] == "draftkings"
    assert s["first"]["home_spread"] == -5.0 and s["first"]["total"] == 228.5
    assert s["move"] == {"home_spread": 0.0, "total": 0.0} and s["observations"] == 2
    assert s["seen"] == T2 and s["first_seen"] == T1


def test_game_lines_drop_a_book_older_than_12_hours(con):
    rows = (_gl(T0, "betmgm", -9.0, 240.0, 1.2, "u0") + _gl(T1, "draftkings", -5.0, 229.0, 1.6, "u1")
            + _gl(T2, "fanduel", -6.0, 226.5, 1.45, "u2"))
    oa.store_game_lines(con, pd.DataFrame(rows), None)
    s = X.stored_game_lines(con, {"g1"})["g1"]
    assert s["books"] == {"spread": 2, "total": 2, "moneyline": 2}  # BetMGM 13.5 hours old
    assert s["home_spread"] == -5.5 and s["total"] == 227.75  # medians of the two books
    assert s["first"]["home_spread"] == -9.0  # the first board is BetMGM's at T0
