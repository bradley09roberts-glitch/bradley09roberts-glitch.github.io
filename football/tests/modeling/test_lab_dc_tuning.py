"""Lab family dc_tuning: tuned and extended Dixon-Coles, on SYNTHETIC matches only."""

import numpy as np
import pandas as pd
import pytest

from footy.modeling.dixon_coles import DixonColes
from footy.modeling.grid import check_coherent
from footy.modeling.lab.dc_tuning import CANDIDATES, DCTuned
from footy.modeling.ledger import headline
from footy.modeling.walkforward import RESULT_COLUMNS, BacktestSpec, run_backtest

from .test_walkforward import synthetic_league

PROBS = ["p_H", "p_D", "p_A", "p_over_2.5", "p_btts"]


def _fit_predict(model, df, season="2324"):
    target = df[df.season == season]
    cutoff = target.cutoff_utc.min()
    hist = df[df.date < target.cutoff_date.min()]
    model.fit(hist, cutoff)
    fx = target[target.cutoff_utc == cutoff].drop(columns=RESULT_COLUMNS)
    return model.predict(fx)


def test_candidates_are_prefixed_and_few():
    assert 0 < len(CANDIDATES) <= 5
    assert all(name.startswith("dc_tuning_") for name in CANDIDATES)
    for name, factory in CANDIDATES.items():
        assert factory().name == name


def test_newton_fit_matches_the_core_dixon_coles():
    df = synthetic_league(seed=5, seasons=("2122", "2223", "2324"), teams=10)
    a = run_backtest(df, BacktestSpec(lambda: DixonColes(xi=0.002), "E0", ["2324"]))
    b = run_backtest(df, BacktestSpec(lambda: DCTuned(xi=0.002), "E0", ["2324"]))
    np.testing.assert_allclose(a[PROBS].to_numpy(float), b[PROBS].to_numpy(float), atol=2e-4)


@pytest.mark.parametrize("name", sorted(CANDIDATES))
def test_candidates_are_coherent_and_beat_frequency(name):
    from footy.modeling.baselines import FrequencyBaseline

    df = synthetic_league(seed=3, seasons=("2122", "2223", "2324"), teams=12)
    led = run_backtest(df, BacktestSpec(CANDIDATES[name], "E0", ["2324"]))
    fr = run_backtest(df, BacktestSpec(FrequencyBaseline, "E0", ["2324"]))
    assert headline(led)["1X2"]["rps"] < headline(fr)["1X2"]["rps"]
    mp = _fit_predict(CANDIDATES[name](), df)
    assert check_coherent(mp) == []


def test_equal_block_decays_reduce_to_a_single_decay():
    df = synthetic_league(seed=2, seasons=("2223", "2324"))
    a = _fit_predict(DCTuned(xi=0.003), df)
    b = _fit_predict(DCTuned(xi=0.001, xi_att=0.003, xi_def=0.003, xi_glob=0.003), df)
    np.testing.assert_allclose(a.p1x2, b.p1x2, atol=1e-9)


def test_separate_decays_converge_and_differ():
    df = synthetic_league(seed=2, seasons=("2122", "2223", "2324"))
    m = DCTuned(xi_att=0.004, xi_def=0.001)
    mp = _fit_predict(m, df)
    assert m.converged_ and check_coherent(mp) == []
    base = _fit_predict(DCTuned(), df)
    assert not np.allclose(mp.p1x2, base.p1x2)


def test_missing_red_cards_change_nothing():
    df = synthetic_league(seed=4)  # red cards are NaN in synthetic data
    a = _fit_predict(DCTuned(), df)
    b = _fit_predict(DCTuned(red_adjust=True, red_weight=0.5), df)
    np.testing.assert_allclose(a.p1x2, b.p1x2, atol=1e-7)


def test_red_card_covariates_have_the_expected_signs():
    df = synthetic_league(seed=6, seasons=("2122", "2223", "2324"), teams=12)
    rng = np.random.default_rng(1)
    df["home_red"], df["away_red"] = 0.0, 0.0
    sent_off = rng.random(len(df)) < 0.15
    df.loc[sent_off, "home_red"] = 1.0
    # a side reduced to ten concedes more: add goals for the away side in those matches
    df.loc[sent_off, "away_goals"] += rng.poisson(0.8, sent_off.sum())
    df["total_goals"] = df.home_goals + df.away_goals
    m = DCTuned(red_adjust=True)
    _fit_predict(m, df)
    assert m._x["red_opp"] > 0.2


def test_team_home_advantage_finds_the_strong_home_side():
    df = synthetic_league(seed=8, seasons=("2122", "2223", "2324"), teams=10)
    rng = np.random.default_rng(2)
    fortress = df.home_team == "eng-team3"
    df.loc[fortress, "home_goals"] += rng.poisson(1.0, fortress.sum())
    df["total_goals"] = df.home_goals + df.away_goals
    m = DCTuned(team_home_reg=5.0)
    _fit_predict(m, df)
    assert int(np.argmax(m.team_home)) == m.team_idx["eng-team3"]
    assert np.abs(m.team_home).max() < 1.0  # shrunk, finite


def test_season_discount_down_weights_old_seasons():
    df = synthetic_league(seed=9, seasons=("2122", "2223", "2324"))
    a = _fit_predict(DCTuned(), df)
    b = _fit_predict(DCTuned(season_discount=0.3), df)
    assert np.all(np.isfinite(b.p1x2)) and not np.allclose(a.p1x2, b.p1x2)


def test_deterministic():
    df = synthetic_league(seed=10)
    name = sorted(CANDIDATES)[0]
    a = run_backtest(df, BacktestSpec(CANDIDATES[name], "E0", ["2324"]))
    b = run_backtest(df, BacktestSpec(CANDIDATES[name], "E0", ["2324"]))
    pd.testing.assert_frame_equal(a, b)
