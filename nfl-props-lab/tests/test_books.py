"""Consensus line and prices (books.py, D-044)."""

import pandas as pd
import pytest

from npl import books


def _q(rows):
    out = []
    for book, line, o, u in rows:
        for sel, p in (("over", o), ("under", u)):
            if p is not None:
                out.append({"player_id": "p1", "market": "pts", "bookmaker": book, "line": line,
                            "selection": sel, "decimal_price": p})
    return pd.DataFrame(out)


def _c(rows):
    return books.consensus(books.pairs(_q(rows)))


def test_a_ladder_book_votes_once():
    # BetRivers hangs three lines; the row mode would pick 19.5, one vote per book picks 20.5
    c = _c([("betrivers", 19.5, 1.7, 2.1), ("betrivers", 20.5, 1.9, 1.9), ("betrivers", 21.5, 2.2, 1.65),
            ("fanduel", 20.5, 1.91, 1.91), ("draftkings", 19.5, 1.87, 1.95), ("betmgm", 20.5, 1.95, 1.87)])
    assert c["line"] == 20.5 and c["books"] == 3


def test_fair_uses_every_two_way_price_at_the_line():
    # Bovada's main line is 21.5, but its 20.5 rung still informs the fair price at 20.5
    c = _c([("fanduel", 20.5, 1.9, 1.9), ("betmgm", 20.5, 1.9, 1.9), ("bovada", 20.5, 1.6, 2.4),
            ("bovada", 21.5, 1.9, 1.9)])
    fb = (1 / 1.6) / (1 / 1.6 + 1 / 2.4)
    assert c["line"] == 20.5 and c["books"] == 3
    assert c["fair_over"] == pytest.approx((0.5 + 0.5 + fb) / 3, abs=1e-4)


def test_duplicates_do_not_vote_and_conflicting_books_are_dropped():
    q = _q([("betrivers", 19.5, 1.9, 1.9)] * 3 + [("fanduel", 20.5, 1.9, 1.9),
                                                   ("draftkings", 20.5, 1.9, 1.9)])
    assert books.consensus(books.pairs(q))["line"] == 20.5
    q = _q([("fanduel", 20.5, 1.9, 1.9), ("fanduel", 20.5, 2.0, 1.8), ("draftkings", 20.5, 1.95, 1.87)])
    c = books.consensus(books.pairs(q))
    assert c["books"] == 1 and c["best_over_book"] == "draftkings"


def test_a_vote_tie_goes_to_the_more_balanced_line():
    c = _c([("fanduel", 20.5, 1.7, 2.2), ("draftkings", 21.5, 1.9, 1.9)])
    assert c["line"] == 21.5


def test_one_way_quotes_offshore_prices_and_the_arbitrage_flag():
    c = _c([("fanduel", 20.5, 2.1, None), ("draftkings", 20.5, 1.9, 1.9), ("bovada", 20.5, 2.5, 1.6),
            ("betmgm", 20.5, 1.85, 2.0)])
    assert c["books"] == 3  # the one-way FanDuel quote is not a price pair
    assert c["best_over"] == 1.9 and c["best_over_book"] == "draftkings"  # never Bovada's 2.5
    assert c["best_under"] == 2.0 and c["best_under_book"] == "betmgm"
    assert c["arb"] is False
    c = _c([("fanduel", 20.5, 2.1, 1.75), ("draftkings", 20.5, 1.75, 2.1)])
    assert c["arb"] is True


def test_betmgm_is_priced_only_at_its_own_main_line():
    c = _c([("fanduel", 20.5, 1.9, 1.9), ("draftkings", 20.5, 1.9, 1.9), ("betmgm", 21.5, 1.9, 1.9),
            ("betmgm", 20.5, 1.6, 2.3)])
    assert c["line"] == 20.5 and c["mgm_line"] == 21.5
    assert c["mgm_over"] is None and c["mgm_under"] is None
