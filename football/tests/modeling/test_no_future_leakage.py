"""Every registered model must give identical predictions when results on or after each
batch's cutoff are scrambled: proof that nothing from the future reaches it. The model + market
tilt is also run on a SYNTHETIC league with a SYNTHETIC price table, so its prices can be
scrambled too (the registered candidate finds no real prices for the synthetic teams)."""

import numpy as np
import pandas as pd
import pytest

from footy.ingest.fd_parse import collection_deadline
from footy.modeling.dixon_coles import DixonColes
from footy.modeling.lab.market_blend import MarketTilt, consensus
from footy.modeling.registry import candidates
from footy.modeling.walkforward import BacktestSpec, run_backtest

from .test_walkforward import synthetic_league

PROB_COLS = ["p_H", "p_D", "p_A", "p_over_2.5", "p_btts"]


@pytest.mark.parametrize("name", sorted(candidates()))
def test_predictions_ignore_future_results(name):
    df = synthetic_league(seed=7, seasons=("2122", "2223", "2324"), teams=10)
    rng = np.random.default_rng(0)
    first_batch = df[df.season == "2324"].cutoff_date.min()
    later = df.date >= first_batch + pd.Timedelta(days=60)
    scrambled = df.copy()
    scrambled.loc[later, "home_goals"] = rng.integers(0, 6, later.sum())
    scrambled.loc[later, "away_goals"] = rng.integers(0, 6, later.sum())
    scrambled["outcome"] = np.where(
        scrambled.home_goals > scrambled.away_goals,
        0,
        np.where(scrambled.home_goals == scrambled.away_goals, 1, 2),
    )
    scrambled["total_goals"] = scrambled.home_goals + scrambled.away_goals
    factory = candidates()[name]
    a = run_backtest(df, BacktestSpec(factory, "E0", ["2324"]))
    b = run_backtest(scrambled, BacktestSpec(factory, "E0", ["2324"]))
    # Only predictions made before the scrambled period must match exactly.
    early = pd.to_datetime(a.date) < first_batch + pd.Timedelta(days=60)
    cols = [c for c in PROB_COLS if c in a]
    np.testing.assert_allclose(
        a.loc[early, cols].to_numpy(float), b.loc[early, cols].to_numpy(float)
    )


# --- Model + market: the prices themselves must not leak (SYNTHETIC league and prices) ---

PRICE_COLUMNS = [
    "division",
    "date",
    "home_team",
    "away_team",
    "bookmaker",
    "market_key",
    "selection",
    "price",
    "quote_time_utc",
]


def _league_with_deadlines():
    df = synthetic_league(seed=11, seasons=("2223", "2324"), teams=10)
    df["deadline_utc"] = pd.to_datetime([collection_deadline(d.date()) for d in df.date], utc=True)
    return df


def _synthetic_prices(df: pd.DataFrame, seed: int) -> pd.DataFrame:
    """SYNTHETIC pre-closing prices (made-up numbers) for every fixture of `df`, for Bet365
    and the market average, each quoted at the fixture's collection deadline."""
    rng = np.random.default_rng(seed)
    rows = []
    for r in df.itertuples():
        ph, pdr, po = rng.uniform(0.2, 0.6), rng.uniform(0.18, 0.32), rng.uniform(0.35, 0.7)
        sets = (
            ("1X2", (("H", ph), ("D", pdr), ("A", 1 - ph - pdr))),
            ("OU_2.5", (("O", po), ("U", 1 - po))),
        )
        for book, margin in (("B365", 1.06), ("Avg", 1.05)):
            for market, sels in sets:
                rows += [
                    (
                        r.division,
                        r.date,
                        r.home_team,
                        r.away_team,
                        book,
                        market,
                        s,
                        1 / (p * margin),
                        r.deadline_utc,
                    )
                    for s, p in sels
                ]
    return pd.DataFrame(rows, columns=PRICE_COLUMNS)


def _run(df, raw):
    book = consensus(raw)

    def factory():
        return MarketTilt(
            lambda: DixonColes(name="dc"), 0.8, 0.7, "market_tilt_synthetic", prices=lambda: book
        )

    return run_backtest(df, BacktestSpec(factory, "E0", ["2324"])).set_index("match_id")


def _rows(raw, df, ids):
    """Index of `raw`'s price rows for the fixtures with these match ids."""
    keys = df.set_index("id").loc[list(ids), ["date", "home_team", "away_team"]]
    wanted = set(map(tuple, keys.to_numpy()))
    have = zip(raw.date, raw.home_team, raw.away_team, strict=True)
    return raw[[k in wanted for k in have]].index


def test_market_tilt_ignores_later_prices_and_results():
    df = _league_with_deadlines()
    raw = _synthetic_prices(df, seed=1)
    split = df[df.season == "2324"].cutoff_date.min() + pd.Timedelta(days=60)
    later = df.date >= split

    scrambled = df.copy()
    rng = np.random.default_rng(5)
    scrambled.loc[later, "home_goals"] = rng.integers(0, 6, later.sum())
    scrambled.loc[later, "away_goals"] = rng.integers(0, 6, later.sum())
    scrambled["outcome"] = np.where(
        scrambled.home_goals > scrambled.away_goals,
        0,
        np.where(scrambled.home_goals == scrambled.away_goals, 1, 2),
    )
    scrambled["total_goals"] = scrambled.home_goals + scrambled.away_goals
    raw_later = _synthetic_prices(df, seed=2)  # different prices for every fixture...
    raw_scrambled = pd.concat(
        [raw[raw.date < split], raw_later[raw_later.date >= split]]  # ...used only for later ones
    )

    a, b = _run(df, raw), _run(scrambled, raw_scrambled)
    no_prices = _run(df, raw.iloc[0:0])
    early = pd.to_datetime(a.date) < split
    cols = [c for c in PROB_COLS if c in a]
    np.testing.assert_allclose(
        a.loc[early, cols].to_numpy(float), b.loc[early, cols].to_numpy(float)
    )
    # the prices are really used: early predictions differ from the base model's, and the
    # scrambled later prices change the later ones
    assert np.abs(a.loc[early, "p_H"] - no_prices.loc[early, "p_H"]).min() > 1e-4
    assert np.abs(a.loc[~early, "p_H"] - b.loc[~early, "p_H"]).max() > 1e-3


def test_market_tilt_ignores_a_price_quoted_after_the_deadline_or_kickoff():
    df = _league_with_deadlines()
    raw = _synthetic_prices(df, seed=3)
    first = df[df.season == "2324"].cutoff_utc.min()
    batch = df[(df.season == "2324") & (df.cutoff_utc == first)]
    late_id, early_ko_id, normal_id = batch.id.iloc[:3]

    # one fixture's prices are quoted a minute after its deadline (and are wildly different)
    edited = raw.copy()
    rows = _rows(edited, df, [late_id])
    edited.loc[rows, "quote_time_utc"] = edited.loc[rows, "quote_time_utc"] + pd.Timedelta(
        minutes=1
    )
    edited.loc[rows, "price"] = np.where(edited.loc[rows, "selection"].isin(["H", "O"]), 1.05, 15.0)
    # another fixture kicks off half an hour before its deadline (a D-012 exclusion)
    df_ko = df.copy()
    df_ko["kickoff_utc"] = pd.to_datetime(df_ko["kickoff_utc"], utc=True)
    df_ko.loc[df_ko.id == early_ko_id, "kickoff_utc"] = df_ko.loc[
        df_ko.id == early_ko_id, "deadline_utc"
    ] - pd.Timedelta(minutes=30)

    with_late = _run(df_ko, edited)
    without = _run(df_ko, raw.drop(index=_rows(raw, df, [late_id, early_ko_id])))
    on_time = _run(df_ko, raw)
    cols = [c for c in PROB_COLS if c in with_late]
    for mid in (late_id, early_ko_id):
        np.testing.assert_allclose(
            with_late.loc[mid, cols].to_numpy(float), without.loc[mid, cols].to_numpy(float)
        )
    np.testing.assert_allclose(
        with_late.loc[normal_id, cols].to_numpy(float), on_time.loc[normal_id, cols].to_numpy(float)
    )
    assert abs(on_time.loc[late_id, "p_H"] - without.loc[late_id, "p_H"]) > 1e-4  # on time: used
