"""Lab family goal_calibration: out-of-sample grid calibration on top of Dixon-Coles, on
SYNTHETIC matches only."""

import numpy as np
import pandas as pd
import pytest

from footy.modeling.baselines import FrequencyBaseline
from footy.modeling.dixon_coles import DixonColes
from footy.modeling.grid import check_coherent, markets_from_grid, poisson_grid
from footy.modeling.lab.dc_tuning import DCTuned
from footy.modeling.lab.goal_calibration import (
    CANDIDATES,
    TUNED_TILT,
    GoalCalibratedDC,
    calibrated_grid,
    components,
    score_loglik,
)
from footy.modeling.ledger import headline
from footy.modeling.walkforward import RESULT_COLUMNS, BacktestSpec, run_backtest

from .test_walkforward import synthetic_league

PROBS = ["p_H", "p_D", "p_A", "p_over_2.5", "p_btts"]


def _fit_predict(model, df, batch=0, season="2324"):
    target = df[df.season == season]
    cutoff = sorted(target.cutoff_utc.unique())[batch]
    batch_rows = target[target.cutoff_utc == cutoff]
    model.fit(df[df.date < batch_rows.cutoff_date.iloc[0]], cutoff)
    return model.predict(batch_rows.drop(columns=RESULT_COLUMNS))


def test_candidates_are_prefixed_and_few():
    assert 0 < len(CANDIDATES) <= 5
    assert all(name.startswith("goal_calibration_") for name in CANDIDATES)
    for name, factory in CANDIDATES.items():
        assert factory().name == name


def test_components_rebuild_the_dixon_coles_rates():
    df = synthetic_league(seed=2, seasons=("2223", "2324"))
    m = DixonColes()
    m.fit(df[df.season == "2223"], pd.Timestamp("2023-07-01", tz="UTC"))
    fx = df[df.season == "2324"].head(12)
    c = components(m, fx)
    lh, la = m.rates(fx)
    np.testing.assert_allclose(np.exp(c.g + c.h + (c.dS + c.sO) / 2), lh, rtol=1e-12)
    np.testing.assert_allclose(np.exp(c.g + (-c.dS + c.sO) / 2), la, rtol=1e-12)


def test_no_calibration_is_exactly_the_base_model():
    df = synthetic_league(seed=3, seasons=("2223", "2324"))
    for base, model in [
        (DixonColes, GoalCalibratedDC(name="t", calib=(), level=None)),
        (
            lambda: DCTuned(**TUNED_TILT),
            GoalCalibratedDC(name="t", tuned=TUNED_TILT, calib=(), level=None),
        ),
    ]:
        a = run_backtest(df, BacktestSpec(base, "E0", ["2324"]))
        b = run_backtest(df, BacktestSpec(lambda m=model: m, "E0", ["2324"]))
        np.testing.assert_allclose(a[PROBS].to_numpy(float), b[PROBS].to_numpy(float), atol=1e-12)


@pytest.mark.parametrize(("draw", "both"), [(0.0, 0.0), (0.15, 0.0), (-0.1, 0.08)])
def test_fast_likelihood_matches_the_grid(draw, both):
    rng = np.random.default_rng(0)
    lh, la = rng.uniform(0.3, 3.0, 200), rng.uniform(0.3, 2.5, 200)
    hg, ag = rng.poisson(lh), rng.poisson(la)
    g = calibrated_grid(lh, la, -0.06, draw, both)
    cell = g[np.arange(200), np.minimum(hg, 10), np.minimum(ag, 10)]
    ll = score_loglik(lh, la, -0.06, hg, ag, draw, both)
    np.testing.assert_allclose(ll, np.log(cell), rtol=1e-10)


def test_draw_tilt_is_coherent_and_moves_mass_to_the_diagonal():
    lh, la = np.array([1.4, 2.2]), np.array([1.2, 0.6])
    base = markets_from_grid(poisson_grid(lh, la, -0.05))
    tilt = markets_from_grid(calibrated_grid(lh, la, -0.05, draw=0.12))
    assert (tilt.p1x2[:, 1] > base.p1x2[:, 1]).all()
    assert check_coherent(tilt) == []


def test_reported_expected_goals_follow_the_tilted_grid():
    # The tuned base's BTTS tilt moves mass between cells. The reported expected goals
    # (published as xG) must move with it: lam − E_grid[goals] is then the same as for the
    # untilted Dixon-Coles grid (only the capped tail differs), and the shift is non-zero.
    df = synthetic_league(seed=5, seasons=("2122", "2223", "2324"), teams=12)
    target = df[df.season == "2324"]
    cutoff = sorted(target.cutoff_utc.unique())[3]
    fx = target[target.cutoff_utc == cutoff]
    model = GoalCalibratedDC(name="t", tuned=TUNED_TILT, calib=(), level=None)
    model.fit(df[df.date < fx.cutoff_date.iloc[0]], cutoff)
    mp = model.predict(fx.drop(columns=RESULT_COLUMNS))
    lh, la = model.rates(fx)
    g0 = poisson_grid(lh, la, model.main.rho_)
    goals = np.arange(g0.shape[1])
    for lam, raw, axis in ((mp.lam_h, lh, 2), (mp.lam_a, la, 1)):
        mean = (mp.grid.sum(axis=axis) * goals).sum(axis=1)
        mean0 = (g0.sum(axis=axis) * goals).sum(axis=1)
        np.testing.assert_allclose(lam - mean, raw - mean0, atol=1e-12)
    assert abs(model.main.tilt_[0]) > 1e-4
    assert np.abs(mp.lam_h - lh).max() > 1e-6


def test_openness_noise_is_shrunk_out_of_sample():
    # The synthetic league has team strength but no team openness, so every openness rating
    # Dixon-Coles estimates is noise: the out-of-sample scale must come out below one.
    df = synthetic_league(seed=11, seasons=("2122", "2223", "2324"), teams=12)
    model = GoalCalibratedDC(name="t", calib=("ks", "ko"), level=None)
    _fit_predict(model, df, batch=10)
    assert model.cal_["ko"] < 0.95
    assert 0.6 < model.cal_["ks"] < 1.4


def test_level_tracks_a_rise_in_scoring():
    df = synthetic_league(seed=4, seasons=("2122", "2223"), teams=12)
    late = df.date >= pd.Timestamp("2022-09-15")
    rng = np.random.default_rng(0)
    df.loc[late, "home_goals"] += rng.poisson(0.6, late.sum())
    df.loc[late, "away_goals"] += rng.poisson(0.6, late.sum())
    flat = GoalCalibratedDC(name="t", calib=(), level=None)
    model = GoalCalibratedDC(name="t", calib=(), level=(60.0, 0.3))
    base = _fit_predict(flat, df, batch=14, season="2223")
    mp = _fit_predict(model, df, batch=14, season="2223")
    assert model.adj_["E0"][0] > 0.05
    assert (mp.p_over[2.5] > base.p_over[2.5]).all()


def test_a_fresh_model_reproduces_a_walked_one():
    df = synthetic_league(seed=6, seasons=("2122", "2223", "2324"), teams=10)
    cfg = {"calib": ("ko", "a", "draw"), "fast_xi": 0.005, "split": (120.0, 0.05)}
    walked = GoalCalibratedDC(name="t", **cfg)
    for b in range(6):
        walked_mp = _fit_predict(walked, df, batch=b)
    fresh = GoalCalibratedDC(name="t", **cfg)
    fresh_mp = _fit_predict(fresh, df, batch=5)
    assert walked.cal_ and walked.cal_.keys() == fresh.cal_.keys()
    # equal up to the optimisers' tolerance (warm starts follow different paths)
    np.testing.assert_allclose(walked_mp.grid, fresh_mp.grid, atol=1e-4)


@pytest.mark.parametrize("name", sorted(CANDIDATES))
def test_candidates_are_coherent_and_beat_frequency(name):
    df = synthetic_league(seed=3, seasons=("2122", "2223", "2324"), teams=12)
    led = run_backtest(df, BacktestSpec(CANDIDATES[name], "E0", ["2324"]))
    fr = run_backtest(df, BacktestSpec(FrequencyBaseline, "E0", ["2324"]))
    assert headline(led)["1X2"]["rps"] < headline(fr)["1X2"]["rps"]
    assert led.loc[led.status == "predicted", PROBS].notna().all().all()
    assert check_coherent(_fit_predict(CANDIDATES[name](), df)) == []
