"""Lab family dc_tuning: tuned and extended Dixon-Coles, on SYNTHETIC matches only."""

import numpy as np
import pandas as pd
import pytest

from footy.modeling.dixon_coles import DixonColes
from footy.modeling.grid import GOALS, check_coherent
from footy.modeling.lab.dc_tuning import CANDIDATES, DCTuned, crowd_mask
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
    b = _fit_predict(DCTuned(xi=0.001, xi_strength=0.003, xi_open=0.003, xi_glob=0.003), df)
    np.testing.assert_allclose(a.p1x2, b.p1x2, atol=1e-9)


def test_separate_decays_converge_and_differ():
    df = synthetic_league(seed=2, seasons=("2122", "2223", "2324"))
    m = DCTuned(xi_strength=0.004, xi_open=0.001, xi_glob=0.003)
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


def test_equal_strength_and_openness_ridge_is_the_core_ridge():
    df = synthetic_league(seed=11, seasons=("2223", "2324"))
    a = _fit_predict(DCTuned(reg=3.0), df)
    b = _fit_predict(DCTuned(reg=1.0, reg_strength=3.0, reg_open=3.0), df)
    np.testing.assert_allclose(a.p1x2, b.p1x2, atol=1e-9)


def test_heavy_openness_ridge_flattens_team_totals_not_1x2():
    df = synthetic_league(seed=12, seasons=("2122", "2223", "2324"), teams=12)
    loose, tight = DCTuned(), DCTuned(reg_strength=2.0, reg_open=1e4)
    a, b = _fit_predict(loose, df), _fit_predict(tight, df)
    total_a, total_b = a.lam_h + a.lam_a, b.lam_h + b.lam_a
    assert total_b.std() < 0.5 * total_a.std()
    assert np.abs(b.p1x2[:, 0] - b.p1x2[:, 2]).max() > 0.1  # still separates the teams


def test_btts_tilt_is_fitted_and_coherent():
    df = synthetic_league(seed=13, seasons=("2122", "2223", "2324"), teams=12)
    rng = np.random.default_rng(3)
    # turn some 1-0 / 0-1 results into 1-1: more "both teams score" than Poisson implies
    nil = ((df.home_goals == 1) & (df.away_goals == 0)) | (
        (df.home_goals == 0) & (df.away_goals == 1)
    )
    flip = nil & (rng.random(len(df)) < 0.5)
    df.loc[flip, ["home_goals", "away_goals"]] = 1
    m = DCTuned(tilt="btts")
    mp = _fit_predict(m, df)
    assert m.tilt_[0] > 0.05 and m.tilt_[1] == 0.0
    assert check_coherent(mp) == []
    plain = _fit_predict(DCTuned(), df)
    assert (mp.p_btts > plain.p_btts).all()
    # the reported expected goals move with the grid's means (more BTTS: more goals); the
    # rates themselves are the same fit, so the shift is exactly that of the grid means
    for lam, lam0, axis in ((mp.lam_h, plain.lam_h, 2), (mp.lam_a, plain.lam_a, 1)):
        shift = ((mp.grid - plain.grid).sum(axis=axis) * GOALS).sum(axis=1)
        np.testing.assert_allclose(lam - lam0, shift, atol=1e-12)
        assert (shift > 0).all()


def test_crowd_calendar_fallback_flags_covid_matches_only(monkeypatch):
    import footy.modeling.lab.dc_tuning as lab

    monkeypatch.setattr(lab, "periods", lambda statuses=("none",): {})  # no reference file
    days = pd.to_datetime(["2019-12-01", "2020-11-07", "2021-03-01", "2022-01-15", "2023-02-01"])
    df = pd.DataFrame({"division": ["D1"] * 5, "date": days})
    assert crowd_mask(df, False).tolist() == [False] * 5
    assert crowd_mask(df, True).tolist() == [False, True, True, True, False]
    df2 = pd.DataFrame({"division": ["F1"] * 2, "date": pd.to_datetime(["2020-09-01"] * 2)})
    assert not crowd_mask(df2, True).any() and crowd_mask(df2, "limited").all()


def test_crowd_mask_prefers_the_reference_file_when_present():
    from footy.modeling.closed_doors import closed_doors_mask, periods

    df = pd.DataFrame(
        {
            "division": ["E0", "D1", "F1", "SP1"],
            "date": pd.to_datetime(["2020-07-01", "2021-02-01", "2020-09-01", "2021-01-10"]),
        }
    )
    if not periods():
        pytest.skip("reference/closed_doors.csv absent: the fallback calendar is used")
    for mode, st in ((True, ("none",)), ("limited", ("none", "limited"))):
        np.testing.assert_array_equal(crowd_mask(df, mode), closed_doors_mask(df, st))


def test_form_term_converges():
    df = synthetic_league(seed=14, seasons=("2122", "2223", "2324"))
    m = DCTuned(xi_form=0.01, reg_form=10.0)
    mp = _fit_predict(m, df)
    assert m.converged_ and check_coherent(mp) == []
