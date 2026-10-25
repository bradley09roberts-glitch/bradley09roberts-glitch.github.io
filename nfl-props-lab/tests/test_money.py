"""Money: hand-calculated settlement, rules, no look-ahead quotes, bankroll reconciliation.
All prices here are SYNTHETIC test fixtures."""

import math

import numpy as np
import pandas as pd
import pytest

from npl import money
from npl.dist import Dist

R = money.RuleProfile("t")


def test_settlement_hand_calculated():
    assert money.settle("over", 54.5, 1.91, 2.0, "played", 60, R) == {
        "status": "win", "profit": pytest.approx(1.82), "returned": pytest.approx(3.82)}
    assert money.settle("over", 54.5, 1.91, 2.0, "played", 54, R)["profit"] == -2.0
    assert money.settle("under", 54.5, 2.05, 1.0, "played", 0, R)["profit"] == pytest.approx(1.05)


def test_integer_line_push_returns_stake():
    r = money.settle("over", 5, 1.8, 1.0, "played", 5, R)
    assert r["status"] == "push" and r["profit"] == 0 and r["returned"] == 1.0


def test_milestone_is_ge():
    assert money.settle("yes", 50, 2.5, 1.0, "played", 50, R)["status"] == "win"
    assert money.settle("yes", 50, 2.5, 1.0, "played", 49, R)["status"] == "loss"


def test_non_participant_void_not_an_under_win():
    for part in ("inactive", "dnp"):
        r = money.settle("under", 40.5, 1.9, 1.0, part, None, R)
        assert r["status"] == "void" and r["profit"] == 0
    # a book whose bets stand on non-participants is not guessed
    stands = money.RuleProfile("stands", void_if_not_played=False)
    assert money.settle("under", 40.5, 1.9, 1.0, "inactive", None, stands)["status"] == "pending"


def test_pending_states():
    for part in ("not_final", "active_unverified"):
        assert money.settle("over", 1.5, 2.0, 1.0, part, None, R)["status"] == "pending"
    assert money.settle("over", 1.5, 2.0, 1.0, "played", np.nan, R)["status"] == "pending"


def test_requires_start_rule():
    rs = money.RuleProfile("rs", requires_start=True)
    assert money.settle("over", 200.5, 1.9, 1.0, "played", 50, rs, started=False)["status"] == "void"


def test_exchange_commission_on_net_winnings():
    ex = money.RuleProfile("ex", commission=0.05)
    r = money.settle("over", 3.5, 3.0, 10.0, "played", 4, ex)
    assert r["profit"] == pytest.approx(10 * 2 * 0.95)


def test_demargin():
    d = money.demargin(1.91, 1.91)
    assert d["overround"] == pytest.approx(2 / 1.91 - 1)
    assert d["fair_over"] == pytest.approx(0.5)


def _quotes(rows):
    base = {"source": "t", "bookmaker_region": "uk", "provider_event_id": "e",
            "player_name_raw": "X", "id": None}
    return pd.DataFrame([{**base, **r} for r in rows])


def test_eligible_quotes_no_lookahead_and_freshness():
    t = pd.Timestamp("2026-10-25T12:00Z")
    q = _quotes([
        {"bookmaker": "skybet", "availability": "verified_uk", "game_id": "g", "player_id": "p",
         "market": "rec_yds", "line": 54.5, "selection": "over", "decimal_price": 1.9,
         "quote_time": "2026-10-25T11:30:00Z", "observed_at": "2026-10-25T11:31:00Z"},
        # observed after the decision: must be ignored (no look-ahead)
        {"bookmaker": "coral", "availability": "verified_uk", "game_id": "g", "player_id": "p",
         "market": "rec_yds", "line": 54.5, "selection": "over", "decimal_price": 2.4,
         "quote_time": "2026-10-25T12:30:00Z", "observed_at": "2026-10-25T12:31:00Z"},
        # stale: older than the freshness limit
        {"bookmaker": "betway", "availability": "verified_uk", "game_id": "g", "player_id": "p",
         "market": "rec_yds", "line": 54.5, "selection": "over", "decimal_price": 2.2,
         "quote_time": "2026-10-24T08:00:00Z", "observed_at": "2026-10-24T08:01:00Z"},
        # US book: research reference only, not eligible for a UK strategy
        {"bookmaker": "draftkings", "availability": "research_reference", "game_id": "g",
         "player_id": "p", "market": "rec_yds", "line": 54.5, "selection": "over",
         "decimal_price": 2.5, "quote_time": "2026-10-25T11:50:00Z",
         "observed_at": "2026-10-25T11:51:00Z"},
        # synthetic fixture never used
        {"bookmaker": "skybet", "availability": "synthetic_test", "game_id": "g",
         "player_id": "p", "market": "rec_yds", "line": 54.5, "selection": "over",
         "decimal_price": 9.0, "quote_time": "2026-10-25T11:50:00Z",
         "observed_at": "2026-10-25T11:51:00Z"},
    ])
    e = money.eligible_quotes(q, t, money.Strategy("s", freshness_minutes=120))
    assert e.bookmaker.tolist() == ["skybet"]
    assert e.decimal_price.tolist() == [1.9]


def test_select_bets_uses_model_probability_and_threshold():
    t = pd.Timestamp("2026-10-25T12:00Z")
    d = Dist.from_samples(np.array([60] * 60 + [40] * 40))  # P(over 54.5) = 0.6
    f = pd.DataFrame([{"game_id": "g", "player_id": "p", "market": "rec_yds", "dist": d,
                       "decision_at": t, "kickoff": pd.Timestamp("2026-10-25T17:00Z")}])
    q = _quotes([{"bookmaker": "skybet", "availability": "verified_uk", "game_id": "g",
                  "player_id": "p", "market": "rec_yds", "line": 54.5, "selection": sel,
                  "decimal_price": price, "quote_time": "2026-10-25T11:30:00Z",
                  "observed_at": "2026-10-25T11:31:00Z"}
                 for sel, price in (("over", 1.9), ("under", 1.9))])
    s = money.Strategy("s", min_ev=0.05, prob_margin=0.02)
    out = money.select_bets(f, q, s)
    over = out[out.selection == "over"].iloc[0]
    assert over.p_win == pytest.approx(0.6)
    assert over.ev == pytest.approx(0.58 * 1.9 - 1)
    assert bool(over.selected)
    assert not bool(out[out.selection == "under"].iloc[0].selected)


def test_no_quotes_means_roi_unavailable():
    r = money.report(pd.DataFrame(), n_eligible_forecasts=500)
    assert r["state"] == "ROI unavailable" and r["bets"] == 0


def test_bankroll_reserves_funds_and_reconciles():
    t0 = pd.Timestamp("2026-10-25T12:00Z")
    ko = pd.Timestamp("2026-10-25T17:00Z")
    bets = pd.DataFrame([
        {"decision_at": t0, "kickoff": ko, "stake_units": 1.0, "status": "win",
         "profit_units": 0.9},
        {"decision_at": t0, "kickoff": ko, "stake_units": 1.0, "status": "loss",
         "profit_units": -1.0},
        {"decision_at": t0, "kickoff": ko, "stake_units": 1.0, "status": "loss",
         "profit_units": -1.0},
    ])
    # Bankroll 2 GBP at 1 GBP stakes: the third concurrent bet cannot be funded
    b, summ = money.run_bankroll(bets, money.Bankroll(start=2.0, stake=1.0))
    assert summ["skipped_insufficient"] == 1
    assert b.placed.tolist() == [True, True, False]
    assert summ["end"] == pytest.approx(2.0 + 0.9 - 1.0)
    # later bet after settlement can use returned funds
    late = pd.concat([bets.iloc[:2], pd.DataFrame([{"decision_at": ko + pd.Timedelta(hours=5),
                                                    "kickoff": ko + pd.Timedelta(days=1),
                                                    "stake_units": 1.0, "status": "win",
                                                    "profit_units": 1.0}])], ignore_index=True)
    b2, s2 = money.run_bankroll(late, money.Bankroll(start=2.0, stake=1.0))
    assert s2["skipped_insufficient"] == 0 and s2["end"] == pytest.approx(2.9)


def test_drawdown_and_losing_run():
    dd_units, dd_frac = money.drawdown(np.array([1, -1, -1, -1, 2]), start=10)
    assert dd_units == 3 and dd_frac == pytest.approx(3 / 11)
    assert money.longest_losing_run(["win", "loss", "loss", "push", "loss", "win", "loss"]) == 3


def test_report_counts_from_ledger():
    df = pd.DataFrame({
        "decision_at": pd.date_range("2026-01-01", periods=4, freq="D", tz="UTC"),
        "game_id": ["a", "b", "c", "d"], "player_id": ["p"] * 4, "market": ["rec_yds"] * 4,
        "decimal_price": [2.0, 2.0, 1.5, 3.0], "stake_units": [1.0] * 4,
        "status": ["win", "loss", "push", "void"], "profit_units": [1.0, -1.0, 0.0, 0.0],
        "week_key": [1, 1, 2, 2]})
    r = money.report(df, n_eligible_forecasts=10)
    assert r["counts"] == {"win": 1, "loss": 1, "push": 1, "void": 1}
    assert r["profit_units"] == 0 and r["staked_units"] == 4 and r["yield"] == 0
    assert math.isclose(r["odds_coverage"], 0.4)
