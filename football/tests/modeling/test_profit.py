"""Hand-worked settlement and fund-reservation examples (SYNTHETIC)."""

import pandas as pd

from footy.modeling.profit import Strategy, candidates, report, select, simulate


def bets(rows):
    df = pd.DataFrame(
        rows,
        columns=[
            "match_id",
            "batch",
            "date",
            "season",
            "league",
            "home_team",
            "away_team",
            "market",
            "selection",
            "p",
            "odds",
            "odds_close",
            "won",
        ],
    )
    df["ev"] = df.p * df.odds - 1
    df.attrs["no_stake"] = set()
    return df


def test_hand_worked_settlement():
    b = bets(
        [
            (
                1,
                "b1",
                "2024-08-10",
                "2425",
                "E0",
                "x",
                "y",
                "1X2",
                "H",
                0.55,
                2.10,
                2.00,
                True,
            ),  # +1.10
            (
                2,
                "b1",
                "2024-08-10",
                "2425",
                "E0",
                "z",
                "w",
                "1X2",
                "A",
                0.40,
                3.00,
                3.20,
                False,
            ),  # -1.00
            (
                3,
                "b2",
                "2024-08-17",
                "2425",
                "E0",
                "x",
                "w",
                "OU_2.5",
                "O",
                0.60,
                1.90,
                1.80,
                True,
            ),  # +0.90
        ]
    )
    sim = simulate(select(b, Strategy("all")), bankroll_gbp=100, unit_gbp=10)
    r = report(sim, 100, 10)
    assert r["bets"] == 3 and r["wins"] == 2 and r["losses"] == 1
    assert abs(r["profit_units"] - 1.0) < 1e-12
    assert abs(r["yield"] - 1.0 / 3) < 1e-12
    assert abs(r["returns_units"] - (2.10 + 1.90)) < 1e-12
    assert abs(r["bankroll_end_gbp"] - 110.0) < 1e-9
    assert r["longest_losing_run"] == 1 and abs(r["max_drawdown_units"] - 1.0) < 1e-12
    assert abs(r["closing_line_value_mean"] - ((2.1 / 2.0 + 3.0 / 3.2 + 1.9 / 1.8) / 3 - 1)) < 1e-12


def test_simultaneous_bets_cannot_overspend():
    b = bets(
        [
            (
                i,
                "b1",
                "2024-08-10",
                "2425",
                "E0",
                "x",
                "y",
                "1X2",
                "H",
                0.6,
                2.0 + i / 100,
                2.0,
                False,
            )
            for i in range(5)
        ]
    )
    sim = simulate(select(b, Strategy("all")), bankroll_gbp=30, unit_gbp=10)
    assert len(sim["ledger"]) == 3 and sim["skipped_insufficient_funds"] == 2
    assert sim["final_bank_gbp"] == 0.0
    # the highest expected returns were kept
    assert set(sim["ledger"].match_id) == {4, 3, 2}


def test_filters_and_one_per_fixture():
    b = bets(
        [
            (1, "b1", "2024-08-10", "2425", "E0", "x", "y", "1X2", "H", 0.50, 2.20, 2.1, True),
            (1, "b1", "2024-08-10", "2425", "E0", "x", "y", "OU_2.5", "O", 0.60, 1.95, 1.9, True),
            (2, "b1", "2024-08-10", "2425", "E0", "z", "w", "1X2", "D", 0.30, 3.20, 3.1, False),
        ]
    )
    assert len(select(b, Strategy("edge5", min_edge=0.05))) == 2
    one = select(b, Strategy("one", one_per_fixture=True))
    assert one.match_id.is_unique and set(one.market[one.match_id == 1]) == {"OU_2.5"}


def test_candidates_include_over_under_2_5():
    """SYNTHETIC ledger and prices. Both 1X2 and O/U 2.5 become candidates, priced at the
    pre-closing quote; the closing price is kept only for closing-line value. ("p_over_2.5"
    isn't an identifier, and an itertuples loop once dropped every O/U 2.5 candidate.)"""
    ledger = pd.DataFrame(
        [
            {
                "match_id": 7,
                "league": "E0",
                "season": "2425",
                "date": "2024-08-10",
                "batch_cutoff_utc": "2024-08-08T23:00:00+00:00",
                "home_team": "x",
                "away_team": "y",
                "home_goals": 2,
                "away_goals": 1,
                "status": "predicted",
                "p_H": 0.5,
                "p_D": 0.3,
                "p_A": 0.2,
                "p_over_2.5": 0.6,
            }
        ]
    )
    quote = "2024-08-09T16:00:00+00:00"
    rows = [
        (7, "1X2", "H", "pre_closing", 2.1, quote),
        (7, "1X2", "D", "pre_closing", 3.4, quote),
        (7, "1X2", "A", "pre_closing", 4.2, quote),
        (7, "OU_2.5", "O", "pre_closing", 1.8, quote),
        (7, "OU_2.5", "U", "pre_closing", 2.0, quote),
        (7, "OU_2.5", "O", "closing", 1.7, "2024-08-10T14:00:00+00:00"),
    ]
    prices = pd.DataFrame(
        rows, columns=["match_id", "market_key", "selection", "phase", "price", "quote_time_utc"]
    )
    prices.attrs["no_stake"] = set()
    c = candidates(ledger, prices).set_index(["market", "selection"])
    assert set(c.index) == {
        ("1X2", "H"),
        ("1X2", "D"),
        ("1X2", "A"),
        ("OU_2.5", "O"),
        ("OU_2.5", "U"),
    }
    over, under = c.loc[("OU_2.5", "O")], c.loc[("OU_2.5", "U")]
    assert over.p == 0.6 and over.odds == 1.8 and over.odds_close == 1.7 and over.won
    assert abs(over.ev - (0.6 * 1.8 - 1)) < 1e-12
    assert abs(under.p - 0.4) < 1e-12 and under.odds == 2.0 and not under.won
    assert pd.isna(under.odds_close)
    assert c.loc[("1X2", "H")].won and not c.loc[("1X2", "D")].won


def test_reports_withhold_price_figures_for_few_bets():
    """SYNTHETIC. The average odds of a single bet are its price (D-013): below the threshold
    a published result keeps only its counts; from the threshold on it is unchanged."""
    from footy.modeling.report import MIN_BETS_FOR_PRICE_FIGURES, withhold_prices

    one = report(
        simulate(
            bets(
                [(1, "b1", "2024-08-10", "2425", "E0", "x", "y", "1X2", "A", 0.3, 4.4, 4.0, False)]
            )
        )
    )
    out = withhold_prices(one)
    assert out["bets"] == 1 and out["losses"] == 1 and out["withheld"]
    assert 4.4 not in out.values() and not {
        "average_odds",
        "break_even_rate",
        "returns_units",
        "profit_units",
        "closing_line_value_mean",
        "equity_curve_units",
        "monthly",
        "top_teams_by_exposure",
    } & set(out)
    many = report(
        simulate(
            bets(
                [
                    (
                        i,
                        f"b{i}",
                        "2024-08-10",
                        "2425",
                        "E0",
                        "x",
                        "y",
                        "1X2",
                        "H",
                        0.6,
                        2.0,
                        2.0,
                        True,
                    )
                    for i in range(MIN_BETS_FOR_PRICE_FIGURES)
                ]
            )
        )
    )
    assert withhold_prices(many) is many
    assert withhold_prices({"bets": 0}) == {"bets": 0}
