"""Paper record (D-044): first flag only, settlement from box scores, closing-line value."""

import sqlite3

import pandas as pd
import pytest

from npl import db, track


def _row(**kw):
    r = {"game_id": "g1", "player_id": "p1", "player": "A", "team": "X", "opponent": "Y",
         "market": "pts", "line": 20.5, "side": "over", "price": 2.0, "book": "betmgm",
         "p_rule": 0.53, "market_p": 0.5, "ev": 0.06, "tip_utc": "2026-11-03T00:00:00+00:00",
         "line_seen": "2026-11-02T18:00:00+00:00"}
    return r | kw


def test_record_keeps_the_first_flag_only(tmp_path):
    con = db.connect(tmp_path / "t.sqlite3")
    kw = {"rule": "r", "sport": "nba", "edition_id": "e1", "flagged_at": "2026-11-02T18:01:00"}
    assert track.record(con, [_row(), _row(player_id="p2")], **kw) == 2
    # a later run sees the line moved and the price shortened: the record keeps the first flag
    assert track.record(con, [_row(line=21.5, price=1.8)], **{**kw, "edition_id": "e2"}) == 0
    t = track.load(con)
    assert len(t) == 2 and t.loc[t.player_id == "p1", "line"].item() == 20.5
    assert t.loc[t.player_id == "p1", "price"].item() == 2.0
    with pytest.raises(sqlite3.IntegrityError, match="append-only"):
        con.execute("UPDATE tracked_props SET price = 3")


def test_settle_win_loss_void_and_open():
    t = pd.DataFrame([_row(), _row(player_id="p2", side="under"), _row(player_id="p3"),
                      _row(game_id="g2")])
    pg = pd.DataFrame({"game_id": ["g1"] * 3, "player_id": ["p1", "p2", "p3"],
                       "participation": ["played", "played", "dnp"],
                       "pts": [21.0, 21.0, None], "reb": [5.0, 5.0, None], "ast": [2.0, 2.0, None]})
    s = track.settle(t, pg)
    assert s.status.tolist() == ["win", "loss", "void", "open"]
    assert s.actual.iloc[0] == 21.0


def _q(obs, line, prices):
    out = []
    for book, (o, u) in prices.items():
        for sel, p in (("over", o), ("under", u)):
            out.append({"game_id": "g1", "player_id": "p1", "market": "pts", "line": line,
                        "bookmaker": book, "selection": sel, "decimal_price": p, "observed_at": obs})
    return out


def test_clv_uses_the_last_snapshot_before_tip_at_the_same_line():
    t = pd.DataFrame([_row()])
    q = pd.DataFrame(_q("2026-11-02T18:00:00+00:00", 20.5, {"a": (1.9, 1.9), "b": (1.9, 1.9)})
                     + _q("2026-11-02T23:50:00+00:00", 20.5, {"a": (1.7, 2.1), "b": (1.75, 2.05)})
                     + _q("2026-11-03T00:30:00+00:00", 20.5, {"a": (3.0, 1.3), "b": (3.0, 1.3)}))
    c = track.clv(t, q)  # the in-play snapshot after tip is ignored
    fa = (1 / 1.7) / (1 / 1.7 + 1 / 2.1)
    fb = (1 / 1.75) / (1 / 1.75 + 1 / 2.05)
    assert c.fair_close.item() == pytest.approx((fa + fb) / 2, abs=1e-4)
    assert c.clv.item() == pytest.approx(2.0 * (fa + fb) / 2 - 1, abs=1e-3)
    assert c.close_lead_min.item() == 10.0


def test_clv_missing_without_a_later_snapshot_or_enough_books():
    t = pd.DataFrame([_row()])
    early = pd.DataFrame(_q("2026-11-02T18:00:00+00:00", 20.5, {"a": (1.9, 1.9), "b": (1.9, 1.9)}))
    assert track.clv(t, early).clv.isna().all()
    moved = pd.DataFrame(_q("2026-11-02T23:00:00+00:00", 21.5, {"a": (1.9, 1.9), "b": (1.9, 1.9)})
                         + _q("2026-11-02T23:00:00+00:00", 20.5, {"a": (1.8, 2.0)}))
    c = track.clv(t, moved)
    assert c.clv.isna().all() and c.close_books.item() == 1


def test_summary_counts_and_roi():
    t = pd.DataFrame([_row(), _row(player_id="p2"), _row(player_id="p3")])
    t["status"] = ["win", "loss", "void"]
    t["clv"] = [0.05, -0.01, None]
    t["close_lead_min"] = [10.0, 12.0, None]
    s = track.summary(t)
    assert (s["won"], s["lost"], s["void"], s["settled"]) == (1, 1, 1, 2)
    assert s["roi"] == 0.0 and s["profit_units"] == 0.0
    assert s["clv_bets"] == 2 and s["clv_mean"] == pytest.approx(0.02)


def test_best_prices_come_from_regulated_books_only():
    from npl.nba import appdata
    rows = []
    for book, (o, u) in {"bovada": (2.3, 1.7), "fanduel": (1.95, 1.87), "betmgm": (1.9, 1.9)}.items():
        for sel, p in (("over", o), ("under", u)):
            rows.append({"game_id": "g1", "player_id": "p1", "market": "pts", "line": 20.5,
                         "bookmaker": book, "selection": sel, "decimal_price": p,
                         "observed_at": "2026-11-02T18:00:00+00:00", "availability": "research_reference"})
    m = appdata.consensus_lines(pd.DataFrame(rows))[("p1", "pts")]
    assert m["books"] == 3  # offshore prices still inform the consensus
    assert m["best_over"] == 1.95 and m["best_over_book"] == "fanduel"
    assert m["best_under"] == 1.9 and m["best_under_book"] == "betmgm"


def test_settle_grades_a_push_and_leaves_a_missing_stat_open():
    t = pd.DataFrame([_row(line=21.0), _row(player_id="p2", side="under")])
    pg = pd.DataFrame({"game_id": ["g1", "g1"], "player_id": ["p1", "p2"],
                       "participation": ["played", "played"], "pts": [21.0, None],
                       "reb": [5.0, 5.0], "ast": [2.0, 2.0]})
    assert track.settle(t, pg).status.tolist() == ["push", "open"]
