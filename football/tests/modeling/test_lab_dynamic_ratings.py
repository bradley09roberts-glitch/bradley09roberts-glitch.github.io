"""Lab family dynamic_ratings: Kalman-filtered and pi-ratings, on SYNTHETIC matches only."""

import numpy as np
import pandas as pd
import pytest

from footy.modeling.baselines import FrequencyBaseline
from footy.modeling.grid import check_coherent
from footy.modeling.lab.dynamic_ratings import (
    CANDIDATES,
    DynamicPoisson,
    GridBlend,
    PiRatings,
    no_crowd_mask,
)
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


def _with_sot(df, seed=0):
    """Shots on target roughly 3x goals plus noise, so they carry the same team signal."""
    rng = np.random.default_rng(seed)
    df = df.copy()
    df["home_sot"] = (df.home_goals + rng.poisson(3.0, len(df))).astype(float)
    df["away_sot"] = (df.away_goals + rng.poisson(2.5, len(df))).astype(float)
    return df


def test_candidates_are_prefixed_and_few():
    assert 0 < len(CANDIDATES) <= 5
    assert all(name.startswith("dynamic_ratings_") for name in CANDIDATES)
    for name, factory in CANDIDATES.items():
        assert factory().name == name


@pytest.mark.parametrize("name", sorted(CANDIDATES))
def test_candidates_are_coherent_and_beat_frequency(name):
    df = synthetic_league(seed=3, seasons=("2122", "2223", "2324"), teams=12)
    led = run_backtest(df, BacktestSpec(CANDIDATES[name], "E0", ["2324"]))
    fr = run_backtest(df, BacktestSpec(FrequencyBaseline, "E0", ["2324"]))
    assert headline(led)["1X2"]["rps"] < headline(fr)["1X2"]["rps"]
    mp = _fit_predict(CANDIDATES[name](), df)
    assert check_coherent(mp) == []


@pytest.mark.parametrize("name", sorted(CANDIDATES))
def test_candidates_are_deterministic(name):
    df = synthetic_league(seed=10)
    a = run_backtest(df, BacktestSpec(CANDIDATES[name], "E0", ["2324"]))
    b = run_backtest(df, BacktestSpec(CANDIDATES[name], "E0", ["2324"]))
    pd.testing.assert_frame_equal(a, b)


def test_incremental_filtering_equals_one_pass():
    df = synthetic_league(seed=4, seasons=("2223", "2324"))
    df = df.sort_values(["date", "id"])
    teams = sorted(set(df.home_team))
    a, b = DynamicPoisson(), DynamicPoisson()
    a.start(teams)
    lh_a, _ = a.process(df)
    b.start(teams)
    cut = len(df) // 2
    cut = int(np.searchsorted(df.date.to_numpy(), df.date.to_numpy()[cut]))  # whole days
    lh_b1, _ = b.process(df.iloc[:cut])
    lh_b2, _ = b.process(df.iloc[cut:])
    np.testing.assert_allclose(lh_a, np.r_[lh_b1, lh_b2], rtol=1e-10)
    np.testing.assert_allclose(a.m_, b.m_, atol=1e-10)


def test_filter_recovers_the_strength_order():
    df = synthetic_league(seed=5, seasons=("2122", "2223", "2324"), teams=10)
    m = DynamicPoisson()
    _fit_predict(m, df)
    # synthetic strengths come from the same generator: rebuild them
    rng = np.random.default_rng(5)
    true = rng.normal(0, 0.3, 10)
    est = np.array([m.m_[m.tidx_[f"eng-team{i}"]] for i in range(10)])
    assert np.corrcoef(true, est)[0, 1] > 0.8


def test_filter_follows_a_change_of_strength():
    df = synthetic_league(seed=6, seasons=("2122", "2223", "2324"), teams=10)
    rng = np.random.default_rng(7)
    late = (df.season == "2223") & (df.home_team == "eng-team2")
    df.loc[late, "home_goals"] += rng.poisson(1.5, late.sum())
    df["total_goals"] = df.home_goals + df.away_goals
    before = df[df.season == "2122"]
    moves = []
    for q in (1e-5, 4e-4):  # slow and fast random walks
        m1, m2 = DynamicPoisson(q_s=q, q_t=q / 4), DynamicPoisson(q_s=q, q_t=q / 4)
        m1.fit(df[df.date < before.date.max()], before.cutoff_utc.max())
        _fit_predict(m2, df)
        i1, i2 = m1.tidx_["eng-team2"], m2.tidx_["eng-team2"]
        moves.append(m2.m_[i2] - m1.m_[i1])
    assert 0 < moves[0] < moves[1]
    assert moves[1] > 0.15


def test_all_missing_shots_on_target_change_nothing():
    df = synthetic_league(seed=8)  # SoT is NaN in synthetic data
    a = _fit_predict(DynamicPoisson(), df)
    b = _fit_predict(DynamicPoisson(r_sot=1.0, r_shots=1.0), df)
    np.testing.assert_allclose(a.p1x2, b.p1x2, atol=1e-6)
    np.testing.assert_allclose(a.p_over[2.5], b.p_over[2.5], atol=1e-6)


def test_shots_on_target_inform_the_ratings():
    df = _with_sot(synthetic_league(seed=9, seasons=("2122", "2223", "2324")))
    a = _fit_predict(DynamicPoisson(), df)
    b = _fit_predict(DynamicPoisson(r_sot=1.0), df)
    assert check_coherent(b) == []
    assert not np.allclose(a.p1x2, b.p1x2, atol=1e-3)


def test_prewarm_and_options_stay_coherent():
    df = _with_sot(synthetic_league(seed=11, seasons=("2122", "2223", "2324"), teams=12))
    for cfg in (
        {"prewarm_days": 200.0, "prewarm_p": 0.5},
        {"team_home": 0.01, "covid": True, "r_red": 2.0, "y_cap": 4.0, "iterations": 2},
        {"r_sot": 1.0, "sot_resid": 0.01, "var_correction": 1.0},
    ):
        m = DynamicPoisson(**cfg)
        mp = _fit_predict(m, df)
        assert check_coherent(mp) == [], cfg
        assert np.isfinite(mp.lam_h).all() and -0.25 <= m.rho_ <= 0.25


def test_unknown_option_is_rejected():
    with pytest.raises(TypeError):
        DynamicPoisson(q_x=1.0)
    with pytest.raises(TypeError):
        PiRatings(alpha=1.0)


def test_pi_rating_update_follows_constantinou_fenton():
    m = PiRatings(tempo_lr=0.0)
    days = pd.to_datetime(["2023-08-12", "2023-08-19"])
    df = pd.DataFrame(
        {
            "id": [1, 2],
            "division": ["E0", "E0"],
            "season": ["2324", "2324"],
            "date": days,
            "home_team": ["a", "b"],
            "away_team": ["b", "a"],
            "home_goals": [3, 0],
            "away_goals": [0, 0],
            "home_sot": [np.nan, np.nan],
            "away_sot": [np.nan, np.nan],
        }
    )
    m.fit(df, pd.Timestamp("2023-08-25", tz="UTC"))
    # match 1: expected GD 0, observed 3, error 3, psi = 3·log10(4)
    psi = 3 * np.log10(4.0)
    d1 = 0.035 * psi
    # match 2: b at home (rating −0.7·d1) v a away (0.7·d1)
    gd = m._gd(-0.7 * d1) - m._gd(0.7 * d1)
    psi2 = 3 * np.log10(1 + abs(0 - gd))
    d2 = 0.035 * psi2  # observed 0 > expected (negative): home side b gains
    assert m.rh_["a"] == pytest.approx(d1 - 0.7 * d2)
    assert m.ra_["a"] == pytest.approx(0.7 * d1 - d2)
    assert m.rh_["b"] == pytest.approx(-0.7 * d1 + d2)
    assert m.ra_["b"] == pytest.approx(-d1 + 0.7 * d2)


def test_pi_ratings_rank_the_teams():
    df = synthetic_league(seed=12, seasons=("2122", "2223", "2324"), teams=10)
    m = PiRatings()
    mp = _fit_predict(m, df)
    assert check_coherent(mp) == []
    rng = np.random.default_rng(12)
    true = rng.normal(0, 0.3, 10)
    est = np.array([m.rh_[f"eng-team{i}"] + m.ra_[f"eng-team{i}"] for i in range(10)])
    assert np.corrcoef(true, est)[0, 1] > 0.8


def test_blend_of_one_model_is_that_model():
    df = synthetic_league(seed=13)
    a = _fit_predict(DynamicPoisson(), df)
    b = _fit_predict(GridBlend([DynamicPoisson(), DynamicPoisson()], [0.3, 0.7]), df)
    np.testing.assert_allclose(a.grid, b.grid, atol=1e-12)
    c = _fit_predict(GridBlend([DynamicPoisson(), PiRatings()], [0.5, 0.5]), df)
    assert check_coherent(c) == []


def test_no_crowd_calendar():
    df = pd.DataFrame(
        {
            "division": ["E0", "E0", "D1", "D1", "F1"],
            "date": pd.to_datetime(
                ["2019-12-01", "2020-11-07", "2022-01-15", "2022-03-01", "2020-09-01"]
            ),
        }
    )
    assert no_crowd_mask(df).tolist() == [False, True, True, False, False]
