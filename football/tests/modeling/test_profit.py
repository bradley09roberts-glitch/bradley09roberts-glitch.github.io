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
    published = withhold_prices(many)
    assert "withheld" not in published and "equity_curve_units" not in published
    for k in ("bets", "average_odds", "profit_units", "yield", "max_drawdown_units"):
        assert published[k] == many[k]
    assert withhold_prices({"bets": 0}) == {"bets": 0}


def spread_bets(per_month: dict[tuple[str, str], int], leagues=("E0",), seed=7) -> pd.DataFrame:
    """SYNTHETIC bets: `per_month[(season, "YYYY-MM")]` bets in that month, one a week, made-up
    teams and prices, leagues and outcomes drawn at random."""
    import numpy as np

    rng = np.random.default_rng(seed)
    rows, i = [], 0
    for (season, month), n in per_month.items():
        for k in range(n):
            day = f"{month}-{1 + k % 28:02d}"
            home, away = rng.choice(12, 2, replace=False)
            rows.append(
                (
                    i,
                    f"b{i}",
                    day,
                    season,
                    str(rng.choice(leagues)),
                    f"team{home}",
                    f"team{away}",
                    "1X2",
                    "H",
                    0.9,  # a positive expected return for every price: all are bets
                    float(np.round(rng.uniform(1.5, 6.0), 2)),
                    2.0,
                    bool(rng.random() < 0.4),
                )
            )
            i += 1
    return bets(rows)


def published_for(per_month, **kw) -> tuple[dict, dict]:
    from footy.modeling.report import withhold_prices

    raw = report(simulate(select(spread_bets(per_month, **kw), Strategy("all")), 1e6, 1.0))
    return raw, withhold_prices(raw)


def test_breakdowns_and_equity_curve_aggregated_for_many_bets():
    """SYNTHETIC, 40 bets: months and seasons with fewer than 5 bets keep their counts only,
    and the equity curve steps once per period of whole months with at least 5 bets."""
    per_month = {
        ("2324", "2024-04"): 3,  # 2023/24 has 3 bets: one block with 2024/25
        ("2425", "2024-08"): 1,
        ("2425", "2024-09"): 6,
        ("2425", "2024-10"): 2,
        ("2425", "2024-11"): 7,
        ("2425", "2024-12"): 5,
        ("2425", "2025-01"): 2,  # a short tail joins December
        ("2526", "2025-08"): 8,  # 2025/26 is a block on its own, each month a period
        ("2526", "2025-09"): 6,
    }
    raw, out = published_for(per_month, leagues=("E0", "SP1", "I1"))
    assert out["bets"] == raw["bets"] == 40 and "equity_curve_units" not in out
    seasons = {s["season"]: (s["count"], s["sum"]) for s in out["seasonal"]}
    raw_seasons = {s["season"]: s["sum"] for s in raw["seasonal"]}
    assert seasons == {"2324": (3, None), "2425": (23, None), "2526": (14, raw_seasons["2526"])}
    months = {(m["season"], m["month"]): (m["count"], m["sum"]) for m in out["monthly"]}
    raw_months = {(m["season"], m["month"]): m["sum"] for m in raw["monthly"]}
    assert {k: c for k, (c, _) in months.items()} == per_month
    shown = sorted(k for k, (_, v) in months.items() if v is not None)
    assert shown == [("2526", "2025-08"), ("2526", "2025-09")]
    assert all(months[k][1] == raw_months[k] for k in shown)
    curve = out["equity_curve_monthly"]
    assert [(p["month"], p["bets"]) for p in curve] == [
        ("2024-09", 10),  # April 2024 (3) + August (1) + September (6)
        ("2024-11", 19),
        ("2025-01", 26),  # December (5) + the short January tail (2)
        ("2025-08", 34),
        ("2025-09", 40),
    ]
    first = [("2324", "2024-04"), ("2425", "2024-08"), ("2425", "2024-09")]
    assert abs(curve[0]["profit_units"] - sum(raw_months[k] for k in first)) < 1e-9
    assert abs(curve[-1]["profit_units"] - raw["profit_units"]) < 1e-9
    assert [r["count"] for r in out["by_league"]] == [r["count"] for r in raw["by_league"]]
    assert all(r["sum"] is None for r in out["by_league"] + out["top_teams_by_exposure"])
    assert "breakdown_rule" in out


def test_shown_rows_are_whole_periods():
    """SYNTHETIC, many random layouts. Read only from the published result: every step of
    the curve covers at least 5 bets; a shown month is one whole step; a shown season starts
    and ends on steps; league and team rows keep their counts only."""
    import numpy as np

    from footy.modeling.report import MIN_BETS_PER_ROW as K

    rng = np.random.default_rng(2026)
    months = (
        [("2324", f"2024-0{m}") for m in range(1, 6)]
        + [("2425", f"2024-{m:02d}") for m in range(8, 13)]
        + [("2425", f"2025-0{m}") for m in range(1, 6)]
    )
    for trial in range(200):
        per_month = {
            m: int(n)
            for m, n in zip(months, rng.poisson(rng.uniform(0.3, 6), len(months)), strict=True)
            if n
        }
        if sum(per_month.values()) < 20:
            continue
        raw, out = published_for(per_month, leagues=("E0", "SP1", "I1", "D1", "F1"), seed=trial)
        curve = out["equity_curve_monthly"]
        steps = np.diff([0] + [p["bets"] for p in curve])
        assert (steps >= K).all() and curve[-1]["bets"] == raw["bets"]
        assert abs(curve[-1]["profit_units"] - raw["profit_units"]) < 1e-9
        ends = {(p["season"], p["month"]): p["bets"] for p in curve}
        order = [(m["season"], m["month"]) for m in out["monthly"]]
        cum = dict(zip(order, np.cumsum([m["count"] for m in out["monthly"]]), strict=True))
        for k, m in enumerate(out["monthly"]):
            if m["sum"] is not None:  # a whole step: the curve ends here and at the month before
                key = (m["season"], m["month"])
                assert m["count"] >= K and ends.get(key) == cum[key]
                assert k == 0 or ends.get(order[k - 1]) == cum[order[k - 1]]
        for s in out["seasonal"]:
            if s["sum"] is not None:
                keys = [o for o in order if o[0] == s["season"]]
                assert ends.get(keys[-1]) == cum[keys[-1]]
                first = order.index(keys[0])
                assert first == 0 or ends.get(order[first - 1]) == cum[order[first - 1]]
        assert sum(r["count"] for r in out["by_league"]) == raw["bets"]
        assert all(r["sum"] is None for r in out["by_league"] + out["top_teams_by_exposure"])


def test_no_subtraction_across_breakdowns_singles_out_a_bet():
    """SYNTHETIC, 25 bets on made-up teams. September has 7 bets: all 6 Serie A bets and one
    Premier League bet won at 3.40. With league sums shown, September minus Serie A was that
    bet's profit, 2.40, and so its price. Every published profit must now be a sum of whole
    periods: no two published sums differ by fewer than 5 bets."""
    from itertools import permutations

    from footy.modeling.report import MIN_BETS_PER_ROW as K
    from footy.modeling.report import withhold_prices

    rows = []

    def add(date, league, home, away, odds, won):
        i = len(rows)
        rows.append(
            (i, f"b{i:03d}", date, "2425", league, home, away, "1X2", "H", 0.9, odds, 2.0, won)
        )

    outcomes = [(2.1, True), (1.8, False), (2.5, False), (1.9, True), (3.0, False), (2.2, False)]
    for k, (odds, won) in enumerate(outcomes):
        add(f"2024-09-{k + 2:02d}", "I1", f"ita-syn{k}", f"ita-syn{k + 6}", odds, won)
    add("2024-09-20", "E0", "eng-syn0", "eng-syn1", 3.40, True)
    for k in range(9):
        add(f"2024-10-{k + 1:02d}", "E0", f"eng-syn{2 + k % 5}", f"eng-syn{8 + k % 3}", 2.0, k < 3)
    for k in range(9):
        add(f"2024-11-{k + 1:02d}", "SP1", f"esp-syn{k % 4}", f"esp-syn{5 + k % 4}", 1.9, k < 4)
    df = bets(rows)
    raw = report(simulate(df, 1e6, 1.0))
    out = withhold_prices(raw)
    assert out["bets"] == 25 and "withheld" not in out

    month = df.date.str[:7]
    published = {"total": set(df.index)}
    for m in out["monthly"]:
        if m["sum"] is not None:
            published[f"month {m['month']}"] = set(df.index[month == m["month"]])
    for s in out["seasonal"]:
        if s["sum"] is not None:
            published[f"season {s['season']}"] = set(df.index[df.season == s["season"]])
    for p in out["equity_curve_monthly"]:
        published[f"to {p['month']}"] = set(df.index[month <= p["month"]])
    for r in out["by_league"]:
        if r["sum"] is not None:
            published[f"league {r['league']}"] = set(df.index[df.league == r["league"]])
    for r in out["top_teams_by_exposure"]:
        if r["sum"] is not None:
            team = (df.home_team == r["team"]) | (df.away_team == r["team"])
            published[f"team {r['team']}"] = set(df.index[team])
    assert "month 2024-09" in published  # September is a period on its own
    for (a, sa), (b, sb) in permutations(published.items(), 2):
        assert not (sb < sa and len(sa - sb) < K), (a, b, sorted(sa - sb))
