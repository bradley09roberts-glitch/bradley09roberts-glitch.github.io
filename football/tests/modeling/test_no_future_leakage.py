"""Every registered model must give identical predictions when results on or after each
batch's cutoff are scrambled: proof that nothing from the future reaches it."""

import numpy as np
import pandas as pd
import pytest

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
