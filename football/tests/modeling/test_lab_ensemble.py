"""Lab family ensemble: pooled score grids of registered models, on SYNTHETIC matches only."""

import numpy as np
import pandas as pd
import pytest

from footy.modeling.baselines import FrequencyBaseline
from footy.modeling.dixon_coles import DixonColes
from footy.modeling.grid import GOALS, check_coherent, markets_from_grid, mix_grids, poisson_grid
from footy.modeling.lab.ensemble import (
    CANDIDATES,
    CONFIGS,
    EnsembleModel,
    grid_means,
    log_pool,
    split_pool,
)
from footy.modeling.ledger import headline
from footy.modeling.registry import candidates
from footy.modeling.walkforward import RESULT_COLUMNS, BacktestSpec, run_backtest

from .test_walkforward import synthetic_league

PROBS = ["p_H", "p_D", "p_A", "p_over_1.5", "p_over_2.5", "p_over_3.5", "p_btts"]
TWO = {
    "slow": lambda: DixonColes(xi=0.001, name="slow"),
    "fast": lambda: DixonColes(xi=0.006, rho=False, name="fast"),
}


def _fit_predict(model, df, season="2324"):
    target = df[df.season == season]
    cutoff = target.cutoff_utc.min()
    hist = df[df.date < target.cutoff_date.min()]
    model.fit(hist, cutoff)
    fx = target[target.cutoff_utc == cutoff].drop(columns=RESULT_COLUMNS)
    return model.predict(fx)


def _grids(seed=0, n=6):
    rng = np.random.default_rng(seed)
    a = poisson_grid(rng.uniform(0.6, 2.6, n), rng.uniform(0.4, 2.2, n), -0.08)
    b = poisson_grid(rng.uniform(0.6, 2.6, n), rng.uniform(0.4, 2.2, n), 0.0)
    return a, b


def _ens(pool="linear", weights=(0.7, 0.3), diff_weights=None):
    return EnsembleModel(
        ["slow", "fast"], weights, "ens_test", pool, diff_weights=diff_weights, factories=TWO
    )


# ---- pooling rules ------------------------------------------------------------------------


def test_log_pool_identities():
    a, b = _grids()
    np.testing.assert_allclose(log_pool([a, b], [1.0, 0.0]), a, atol=1e-12)
    g = log_pool([a, b], [0.5, 0.5])
    assert np.allclose(g.sum(axis=(1, 2)), 1) and (g >= 0).all()
    # sharpening: a single grid raised to 2 concentrates its mode
    sharp = log_pool([a], [2.0])
    assert (sharp.max(axis=(1, 2)) > a.max(axis=(1, 2))).all()
    with pytest.raises(ValueError):
        log_pool([a, b], [-0.1, 1.0])


def test_split_pool_takes_goal_difference_from_one_grid_and_shape_from_the_other():
    a, b = _grids(1)
    g = split_pool(a, b)
    diff = GOALS[:, None] - GOALS[None, :]
    for d in range(-10, 11):
        on = diff == d
        np.testing.assert_allclose(g[:, on].sum(axis=1), a[:, on].sum(axis=1), atol=1e-12)
        share_g = g[:, on] / g[:, on].sum(axis=1, keepdims=True)
        share_b = b[:, on] / b[:, on].sum(axis=1, keepdims=True)
        np.testing.assert_allclose(share_g, share_b, atol=1e-9)
    # so 1X2 is exactly the difference grid's, and the result is one coherent grid
    np.testing.assert_allclose(markets_from_grid(g).p1x2, markets_from_grid(a).p1x2, atol=1e-12)
    assert check_coherent(markets_from_grid(g)) == []
    # a grid split with itself is unchanged
    np.testing.assert_allclose(split_pool(a, a), a, atol=1e-12)


def test_split_pool_spreads_an_empty_diagonal_evenly():
    a, b = _grids(2, n=1)
    b = b.copy()
    b[0, 10, 0] = 0.0  # the only cell of goal difference +10
    b /= b.sum()
    g = split_pool(a, b)
    assert g[0, 10, 0] == pytest.approx(a[0, 10, 0] / g.sum())
    assert np.isclose(g.sum(), 1)


def test_grid_means_match_poisson_rates():
    g = poisson_grid([1.3, 2.1], [0.9, 1.6], 0.0)
    lh, la = grid_means(g)
    np.testing.assert_allclose(lh, [1.3, 2.1], atol=1e-4)
    np.testing.assert_allclose(la, [0.9, 1.6], atol=1e-4)


# ---- the model ----------------------------------------------------------------------------


def test_linear_ensemble_is_the_weighted_mean_of_its_members():
    df = synthetic_league(seed=5, seasons=("2223", "2324"), teams=10)
    mp = _fit_predict(_ens(), df)
    slow, fast = _fit_predict(TWO["slow"](), df), _fit_predict(TWO["fast"](), df)
    assert check_coherent(mp) == []
    np.testing.assert_allclose(mp.grid, mix_grids([slow.grid, fast.grid], [0.7, 0.3]))
    np.testing.assert_allclose(mp.p1x2, 0.7 * slow.p1x2 + 0.3 * fast.p1x2, atol=1e-12)
    np.testing.assert_allclose(mp.p_btts, 0.7 * slow.p_btts + 0.3 * fast.p_btts, atol=1e-12)
    for line in mp.p_over:
        np.testing.assert_allclose(
            mp.p_over[line], 0.7 * slow.p_over[line] + 0.3 * fast.p_over[line], atol=1e-12
        )
    np.testing.assert_allclose(mp.lam_h, 0.7 * slow.lam_h + 0.3 * fast.lam_h)


@pytest.mark.parametrize(
    "pool, weights, diff",
    [("log", (0.6, 0.5), None), ("split", (0.2, 0.8), (0.9, 0.1))],
)
def test_other_pools_are_coherent_with_grid_expected_goals(pool, weights, diff):
    df = synthetic_league(seed=6, seasons=("2223", "2324"), teams=10)
    mp = _fit_predict(_ens(pool, weights, diff), df)
    assert check_coherent(mp) == []
    lh, la = grid_means(mp.grid)
    np.testing.assert_allclose(mp.lam_h, lh)
    np.testing.assert_allclose(mp.lam_a, la)


def test_split_with_equal_weights_is_the_linear_pool():
    df = synthetic_league(seed=8, seasons=("2223", "2324"), teams=10)
    lin = _fit_predict(_ens("linear", (0.4, 0.6)), df)
    spl = _fit_predict(_ens("split", (0.4, 0.6), (0.4, 0.6)), df)
    np.testing.assert_allclose(spl.grid, lin.grid, atol=1e-12)


def test_bad_configurations_are_refused():
    with pytest.raises(ValueError):
        _ens("median")
    with pytest.raises(ValueError):
        EnsembleModel(["slow"], [0.5, 0.5], "x", factories=TWO)
    with pytest.raises(ValueError):
        EnsembleModel(["slow", "slow"], [0.5, 0.5], "x", factories=TWO)
    with pytest.raises(ValueError):
        _ens("split", (0.5, 0.5), None)
    with pytest.raises(ValueError):
        _ens("linear", (0.0, 0.0))


def test_member_without_a_grid_is_refused():
    df = synthetic_league(seed=9, seasons=("2223", "2324"), teams=10)
    ens = EnsembleModel(
        ["slow", "freq"],
        [0.5, 0.5],
        "x",
        factories={"slow": TWO["slow"], "freq": FrequencyBaseline},
    )
    with pytest.raises(ValueError, match="no score grid"):
        _fit_predict(ens, df)


def test_walk_forward_is_deterministic_and_beats_frequency():
    df = synthetic_league(seed=3, seasons=("2122", "2223", "2324"), teams=12)
    a = run_backtest(
        df, BacktestSpec(lambda: _ens("split", (0.5, 0.5), (0.8, 0.2)), "E0", ["2324"])
    )
    b = run_backtest(
        df, BacktestSpec(lambda: _ens("split", (0.5, 0.5), (0.8, 0.2)), "E0", ["2324"])
    )
    pd.testing.assert_frame_equal(a, b)
    fr = run_backtest(df, BacktestSpec(FrequencyBaseline, "E0", ["2324"]))
    assert headline(a)["1X2"]["rps"] < headline(fr)["1X2"]["rps"]


# ---- registered candidates ----------------------------------------------------------------


def test_candidates_are_prefixed_few_and_name_registered_members():
    assert 0 < len(CANDIDATES) <= 5
    registry = candidates()
    for name, cfg in CONFIGS.items():
        assert name.startswith("ensemble_")
        assert 1 <= len(cfg["members"]) <= 4
        assert all(m in registry and not m.startswith("ensemble_") for m in cfg["members"])
        assert all(w >= 0 for w in cfg["weights"])
        model = CANDIDATES[name]()
        assert model.name == name
        assert model.params()["members"] == list(cfg["members"])
        if model.pool != "log":
            assert np.isclose(model.weights.sum(), 1.0)


def _with_stats(df, seed=0):
    """SYNTHETIC shots, shots on target, corners, reds and half-time goals, loosely tied to
    the goals so that the members that read them get some signal."""
    rng = np.random.default_rng(seed)
    df = df.copy()
    df["home_sot"] = (df.home_goals + rng.poisson(3.0, len(df))).astype(float)
    df["away_sot"] = (df.away_goals + rng.poisson(2.5, len(df))).astype(float)
    df["home_shots"] = df.home_sot + rng.poisson(6.0, len(df))
    df["away_shots"] = df.away_sot + rng.poisson(5.0, len(df))
    df["home_corners"] = rng.poisson(5.5, len(df)).astype(float)
    df["away_corners"] = rng.poisson(4.5, len(df)).astype(float)
    df["home_red"] = rng.binomial(1, 0.05, len(df)).astype(float)
    df["away_red"] = rng.binomial(1, 0.06, len(df)).astype(float)
    df["ht_home_goals"] = rng.binomial(df.home_goals, 0.45).astype(float)
    df["ht_away_goals"] = rng.binomial(df.away_goals, 0.45).astype(float)
    return df


@pytest.mark.parametrize("name", sorted(CANDIDATES))
def test_candidates_ignore_every_later_result_column(name):
    """The shared leakage test scrambles goals on data without match statistics; here every
    result column (goals, half-time goals, shots, SoT, corners, reds) exists and is scrambled
    from a split date on, so each member's statistics path is exercised too."""
    df = _with_stats(synthetic_league(seed=21, seasons=("2122", "2223", "2324"), teams=10))
    rng = np.random.default_rng(4)
    split = df[df.season == "2324"].cutoff_date.min() + pd.Timedelta(days=45)
    later = df.date >= split
    scrambled = df.copy()
    for c in RESULT_COLUMNS[:13]:
        if c != "result":
            scrambled.loc[later, c] = rng.integers(0, 9, later.sum()).astype(float)
    scrambled["outcome"] = np.sign(scrambled.away_goals - scrambled.home_goals).astype(int) + 1
    scrambled["total_goals"] = scrambled.home_goals + scrambled.away_goals
    scrambled["result"] = np.array(["H", "D", "A"])[scrambled.outcome]
    a = run_backtest(df, BacktestSpec(CANDIDATES[name], "E0", ["2324"]))
    b = run_backtest(scrambled, BacktestSpec(CANDIDATES[name], "E0", ["2324"]))
    early = pd.to_datetime(a.date) < split
    assert early.sum() > 0 and (~early).sum() > 0
    np.testing.assert_array_equal(
        a.loc[early, PROBS].to_numpy(float), b.loc[early, PROBS].to_numpy(float)
    )
    assert np.abs(a.loc[~early, "p_H"] - b.loc[~early, "p_H"]).max() > 1e-3
    assert (a.loc[a.status == "predicted", "tail_mass"] < 0.05).all()
