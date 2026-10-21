"""Hand-worked settlement and fund-reservation examples (SYNTHETIC)."""

import pandas as pd

from footy.modeling.profit import Strategy, report, select, simulate


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
