"""Lab family bivariate_dispersion: alternative joint score distributions, SYNTHETIC data."""

import numpy as np
import pandas as pd
import pytest

from footy.modeling.baselines import FrequencyBaseline
from footy.modeling.dixon_coles import DixonColes
from footy.modeling.grid import check_coherent
from footy.modeling.lab.bivariate_dispersion import (
    CANDIDATES,
    BirthShape,
    BivariateDispersion,
    BPDiagShape,
    BPShape,
    CopulaShape,
    DCShape,
    DrawTiltShape,
    com_pmf,
    fold,
    frank_joint,
    pois_pmf,
    weibull_pmf,
)
from footy.modeling.ledger import headline
from footy.modeling.walkforward import RESULT_COLUMNS, BacktestSpec, run_backtest

from .test_walkforward import synthetic_league

PROBS = ["p_H", "p_D", "p_A", "p_over_2.5", "p_btts"]
MH = np.array([0.5, 1.0, 1.6, 2.4, 3.2])
MA = np.array([0.4, 1.2, 1.1, 0.7, 1.5])
K = np.arange(16)


def _fit_predict(model, df, season="2324"):
    target = df[df.season == season]
    cutoff = target.cutoff_utc.min()
    hist = df[df.date < target.cutoff_date.min()]
    model.fit(hist, cutoff)
    fx = target[target.cutoff_utc == cutoff].drop(columns=RESULT_COLUMNS)
    return model.predict(fx)


def _means(g):
    k = np.arange(g.shape[1])
    s = g.sum(axis=(1, 2))
    return (g.sum(2) @ k) / s, (g.sum(1) @ k) / s


def test_candidates_are_prefixed_and_few():
    assert 0 < len(CANDIDATES) <= 5
    assert all(name.startswith("bivariate_dispersion_") for name in CANDIDATES)
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
    assert np.isfinite(mp.grid).all()


@pytest.mark.parametrize(
    "shape, theta",
    [
        (DCShape(), [-0.08]),
        (BPShape(), [0.15]),
        (CopulaShape("com"), [1.2, 0.5]),
        (CopulaShape("weibull"), [1.1, -0.8]),
        (CopulaShape("poisson"), [-1.0]),
        (BirthShape(), [-0.2, 0.3, 0.45]),
        (BirthShape(draw_state=True), [-0.1, 0.2, -0.2, 0.45]),
        (BirthShape(time_trend=True), [-0.1, 0.2, 0.3, -0.3, 0.45]),
    ],
)
def test_shapes_keep_the_expected_goals(shape, theta):
    g = shape.grid(MH, MA, np.array(theta))
    assert (g >= 0).all() and np.allclose(g.sum(axis=(1, 2)), 1, atol=1e-6)
    eh, ea = _means(g)
    tol = 2e-2 if isinstance(shape, DCShape) else 2e-3  # DC's tau moves the means slightly
    np.testing.assert_allclose(eh, MH, rtol=tol)
    np.testing.assert_allclose(ea, MA, rtol=tol)


def test_dispersed_margins_keep_the_mean_and_change_the_variance():
    for pmf, under in [(lambda m: com_pmf(m, 1.25), True), (lambda m: com_pmf(m, 0.8), False)]:
        p = pmf(MH)
        mean = p @ K
        var = p @ K**2 - mean**2
        np.testing.assert_allclose(mean, MH, rtol=1e-3)
        assert ((var < MH) if under else (var > MH)).all()
    p = weibull_pmf(MH, 1.15)
    np.testing.assert_allclose(p @ K, MH, rtol=1e-3)
    assert (p @ K**2 - (p @ K) ** 2 < MH).all()
    np.testing.assert_allclose(weibull_pmf(MH, 1.0), pois_pmf(MH))


def test_frank_copula_signs_and_independence():
    ph, pa = pois_pmf(MH), pois_pmf(MA)
    np.testing.assert_allclose(frank_joint(ph, pa, 0.0), ph[:, :, None] * pa[:, None, :])
    for theta, sign in [(-2.0, -1), (2.0, 1)]:
        g = frank_joint(ph, pa, theta)
        eh, ea = _means(g)
        cov = (g * (K[:, None] * K[None, :])).sum(axis=(1, 2)) - eh * ea
        assert (np.sign(cov) == sign).all()
        np.testing.assert_allclose(eh, MH, rtol=1e-5)


def test_bivariate_poisson_covariance_is_lambda3():
    corr = 0.2
    g = BPShape().grid(MH, MA, [corr])
    eh, ea = _means(g)
    cov = (g * (K[:, None] * K[None, :])).sum(axis=(1, 2)) - eh * ea
    np.testing.assert_allclose(cov, corr * np.sqrt(MH * MA), rtol=1e-3)


def test_inflation_and_tilt_raise_draws():
    base = pois_pmf(MH)[:, :, None] * pois_pmf(MA)[:, None, :]
    draw = np.trace(base, axis1=1, axis2=2)
    g = BPDiagShape().grid(MH, MA, [0.0, -2.0, 1.0, 0.0])
    assert (np.trace(g, axis1=1, axis2=2) > draw).all() and np.allclose(g.sum(axis=(1, 2)), 1)
    t = DrawTiltShape().grid(MH, MA, [0.0, 0.3, 0.3])
    t = t / t.sum(axis=(1, 2), keepdims=True)
    assert (np.trace(t, axis1=1, axis2=2) > draw).all()


def test_birth_process_without_state_effects_is_close_to_poisson():
    g = fold(BirthShape(periods=9).grid(MH[:3], MA[:3], np.array([0.0, 0.0, 0.45])))
    ind = fold(pois_pmf(MH[:3])[:, :, None] * pois_pmf(MA[:3])[:, None, :])
    assert np.abs(g - ind).max() < 5e-3


def test_birth_state_effects_move_mass_to_narrow_results():
    shape = BirthShape()
    calm = fold(shape.grid(MH, MA, np.array([0.0, 0.0, 0.45])))
    state = fold(shape.grid(MH, MA, np.array([-0.2, 0.25, 0.45])))
    gd = np.arange(11)[:, None] - np.arange(11)[None, :]
    narrow = np.abs(gd) <= 1
    assert ((state * narrow).sum(axis=(1, 2)) > (calm * narrow).sum(axis=(1, 2))).all()


def _simulate_state_dependent(seed, n, lead, trail, periods=6):
    """Matches from a state-dependent process with known effects, with half-time scores."""
    rng = np.random.default_rng(seed)
    mh, ma = rng.uniform(0.8, 2.2, n), rng.uniform(0.6, 1.8, n)
    x, y = np.zeros(n, int), np.zeros(n, int)
    hx, hy = np.zeros(n), np.zeros(n)
    for k in range(2 * periods):
        r = 1 / (2 * periods)
        d = x - y
        fh = np.exp(np.where(d > 0, lead, np.where(d < 0, trail, 0.0)))
        fa = np.exp(np.where(d < 0, lead, np.where(d > 0, trail, 0.0)))
        x = x + rng.poisson(mh * r * fh)
        y = y + rng.poisson(ma * r * fa)
        if k == periods - 1:
            hx, hy = x.astype(float), y.astype(float)
    return {"mh": mh, "ma": ma, "x": x, "y": y, "hx": hx, "hy": hy}


def test_birth_process_recovers_planted_game_state_effects():
    from scipy.optimize import minimize

    d = _simulate_state_dependent(seed=1, n=6000, lead=-0.3, trail=0.3)
    shape = BirthShape()
    # simulated rates are base rates; give the shape the realised mean structure
    res = minimize(
        lambda th: -shape.loglik(d, th).mean(),
        shape.init,
        method="L-BFGS-B",
        bounds=shape.bounds,
        options={"eps": 1e-6},
    )
    assert res.x[0] < -0.1 and res.x[1] > 0.1


def test_dc_shape_is_close_to_the_core_dixon_coles():
    df = synthetic_league(seed=5, seasons=("2122", "2223", "2324"), teams=10)
    a = run_backtest(df, BacktestSpec(lambda: DixonColes(), "E0", ["2324"]))
    b = run_backtest(df, BacktestSpec(lambda: BivariateDispersion("dc"), "E0", ["2324"]))
    np.testing.assert_allclose(a[PROBS].to_numpy(float), b[PROBS].to_numpy(float), atol=0.02)


def test_missing_half_times_fall_back_to_full_time():
    df = synthetic_league(seed=4)  # half-time goals are NaN in synthetic data
    m = BivariateDispersion("birth")
    mp = _fit_predict(m, df)
    assert check_coherent(mp) == [] and np.isfinite(m.theta_).all()


def test_half_times_are_used_when_present():
    df = synthetic_league(seed=6, seasons=("2223", "2324"))
    rng = np.random.default_rng(0)
    df["ht_home_goals"] = rng.binomial(df.home_goals, 0.45).astype(float)
    df["ht_away_goals"] = rng.binomial(df.away_goals, 0.45).astype(float)
    a, b = BivariateDispersion("birth"), BivariateDispersion("birth", shape_kw={"use_ht": False})
    _fit_predict(a, df)
    _fit_predict(b, df)
    assert not np.allclose(a.theta_, b.theta_)


def test_expensive_shapes_refit_on_schedule():
    df = synthetic_league(seed=8, seasons=("2223", "2324"))
    m = BivariateDispersion("birth", shape_refit_days=10_000)
    calls = []
    real = m._fit_shape
    m._fit_shape = lambda h, c: (calls.append(c), real(h, c))
    run_backtest(df, BacktestSpec(lambda: m, "E0", ["2324"]))
    assert len(calls) == 1


def test_deterministic():
    df = synthetic_league(seed=10)
    for name in sorted(CANDIDATES):
        a = run_backtest(df, BacktestSpec(CANDIDATES[name], "E0", ["2324"]))
        b = run_backtest(df, BacktestSpec(CANDIDATES[name], "E0", ["2324"]))
        pd.testing.assert_frame_equal(a, b)


def test_diagonal_inflation_finds_planted_draws():
    df = synthetic_league(seed=13, seasons=("2122", "2223", "2324"), teams=12)
    rng = np.random.default_rng(3)
    flip = (df.home_goals + df.away_goals == 1) & (rng.random(len(df)) < 0.5)
    df.loc[flip, ["home_goals", "away_goals"]] = 1  # extra 1-1 draws
    df["total_goals"] = df.home_goals + df.away_goals
    m = BivariateDispersion("dc", inflate=True)
    mp = _fit_predict(m, df)
    plain = _fit_predict(BivariateDispersion("dc"), df)
    assert check_coherent(mp) == []
    assert (mp.grid[:, 1, 1] > plain.grid[:, 1, 1]).all()


def test_any_score_model_can_supply_the_means():
    df = synthetic_league(seed=14, seasons=("2223", "2324"))
    m = BivariateDispersion("draw_tilt", means_factory=lambda: DixonColes(xi=0.003))
    mp = _fit_predict(m, df)
    assert check_coherent(mp) == [] and m.params()["means"] == "dixon_coles_xi0.003_sw0"
