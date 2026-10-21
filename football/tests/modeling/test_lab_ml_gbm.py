"""The ml_gbm lab family, on SYNTHETIC matches only."""

import numpy as np
import pandas as pd
import pytest

from footy.modeling.baselines import FrequencyBaseline
from footy.modeling.data import history_before
from footy.modeling.lab.ml_gbm import (
    CANDIDATES,
    FEATURES,
    MLGBM,
    FeatureScan,
    PoissonBooster,
    PoissonGLM,
)
from footy.modeling.ledger import headline
from footy.modeling.walkforward import BacktestSpec, run_backtest

from .test_walkforward import synthetic_league

PROB_COLS = ["p_H", "p_D", "p_A", "p_over_1.5", "p_over_2.5", "p_over_3.5", "p_btts"]


def with_stats(df: pd.DataFrame, seed: int = 0) -> pd.DataFrame:
    """Shots, shots on target and corners that carry the same signal as the goals."""
    rng = np.random.default_rng(seed)
    out = df.copy()
    for side in ("home", "away"):
        goals = out[f"{side}_goals"].to_numpy()
        sot = goals + rng.poisson(2.0 + 0.8 * goals)
        out[f"{side}_sot"] = sot.astype(float)
        out[f"{side}_shots"] = (sot + rng.poisson(5.0 + 1.5 * goals)).astype(float)
        out[f"{side}_corners"] = rng.poisson(4.0 + 0.5 * goals).astype(float)
    return out


def batch_at(df: pd.DataFrame, k: int):
    cutoff_utc = sorted(df[df.season == df.season.max()].cutoff_utc.unique())[k]
    batch = df[df.cutoff_utc == cutoff_utc]
    return pd.Timestamp(cutoff_utc), history_before(df, batch.cutoff_date.iloc[0]), batch


def test_registered_names_are_prefixed_and_few():
    assert 0 < len(CANDIDATES) <= 5
    for name, factory in CANDIDATES.items():
        assert name.startswith("ml_gbm_")
        assert factory().name == name


@pytest.mark.parametrize("name", sorted(CANDIDATES))
@pytest.mark.parametrize("stats", [False, True])
def test_candidates_run_coherently_with_and_without_shots(name, stats):
    df = synthetic_league(seed=2)
    if stats:
        df = with_stats(df)
    led = run_backtest(df, BacktestSpec(CANDIDATES[name], "E0", ["2324"]))
    pred = led[led.status == "predicted"]
    assert len(pred) > 50
    p = pred[PROB_COLS].to_numpy(float)
    assert np.isfinite(p).all() and (p > 0).all() and (p < 1).all()
    assert np.allclose(pred[["p_H", "p_D", "p_A"]].sum(axis=1), 1)


def test_features_only_use_matches_from_earlier_days():
    """Changing the results of one day changes no feature of that day or before it, and
    does change later ones."""
    df = with_stats(synthetic_league(seed=3)).sort_values(["date", "id"])
    day = df.date.unique()[30]
    changed = df.copy()
    on_day = changed.date == day
    for c in ("home_goals", "away_goals", "home_sot", "away_sot", "home_shots", "away_corners"):
        changed.loc[on_day, c] = changed.loc[on_day, c] + 3
    a, b = FeatureScan(), FeatureScan()
    a.extend(df)
    b.extend(changed)
    upto = df.id[df.date <= day]
    later = df.id[df.date > day]
    for x, y in zip(a.rows(upto), b.rows(upto), strict=True):
        np.testing.assert_array_equal(x, y)
    assert not np.allclose(a.rows(later)[0], b.rows(later)[0])


def test_incremental_scan_equals_one_pass():
    df = with_stats(synthetic_league(seed=4)).sort_values(["date", "id"])
    cut = df.date.unique()[20]
    one = FeatureScan()
    one.extend(df)
    inc = FeatureScan()
    inc.extend(df[df.date < cut])
    assert inc.covers(df)
    inc.extend(df)
    for x, y in zip(one.rows(df.id), inc.rows(df.id), strict=True):
        np.testing.assert_allclose(x, y)
    fx = df[df.date == df.date.max()]
    for x, y in zip(one.fixture_features(fx), inc.fixture_features(fx), strict=True):
        np.testing.assert_allclose(x, y)


def test_scan_restarts_when_history_is_not_an_extension():
    df = synthetic_league(seed=4).sort_values(["date", "id"])
    scan = FeatureScan()
    scan.extend(df[df.date < df.date.unique()[20]])
    gappy = df.drop(index=df.index[5])  # an old match missing from a later history is fine
    assert scan.covers(gappy)
    extra = df.copy()
    extra.loc[extra.index[3], "id"] = 10**6  # an old-dated match the scan never processed
    assert not scan.covers(extra)


def test_fixture_features_handle_unseen_teams_and_new_seasons():
    df = synthetic_league(seed=5)
    cutoff, hist, batch = batch_at(df, 3)
    model = CANDIDATES[sorted(CANDIDATES)[0]]()
    model.fit(hist, cutoff)
    fx = batch.drop(columns=["home_goals", "away_goals"]).copy()
    fx.loc[fx.index[0], "home_team"] = "eng-brand-new"
    fx["season"] = "2425"
    mp = model.predict(fx)
    assert np.isfinite(mp.lam_h).all() and (mp.lam_h > 0).all()
    assert np.allclose(mp.p1x2.sum(axis=1), 1)


def test_booster_learns_a_nonlinear_poisson_signal_deterministically():
    rng = np.random.default_rng(0)
    X = rng.normal(size=(4000, 3))
    mu = np.exp(0.2 + 0.4 * X[:, 0] - 0.3 * X[:, 1] ** 2)
    y = rng.poisson(mu)
    a = PoissonBooster(n_trees=150, lr=0.1, max_depth=2, min_leaf=50).fit(X, y)
    b = PoissonBooster(n_trees=150, lr=0.1, max_depth=2, min_leaf=50).fit(X, y)
    Xt = rng.normal(size=(2000, 3))
    mt = np.exp(0.2 + 0.4 * Xt[:, 0] - 0.3 * Xt[:, 1] ** 2)
    pa = np.exp(a.predict_log(Xt))
    np.testing.assert_array_equal(pa, np.exp(b.predict_log(Xt)))
    assert np.corrcoef(pa, mt)[0, 1] > 0.9
    # boosting from an offset only adds to it
    off = np.full(len(y), 0.5)
    c = PoissonBooster(n_trees=0).fit(X, y, offset=off)
    np.testing.assert_allclose(c.predict_log(X, off), off + c.base)


def test_glm_recovers_coefficients_and_leaves_free_columns_unpenalised():
    rng = np.random.default_rng(1)
    X = rng.normal(size=(20000, 2))
    y = rng.poisson(np.exp(0.3 + 0.5 * X[:, 0] - 0.2 * X[:, 1]))
    m = PoissonGLM(l2=0.0).fit(X, y)
    slope = m.coef[1:] / m.sd
    np.testing.assert_allclose(slope, [0.5, -0.2], atol=0.03)
    heavy = PoissonGLM(l2=1e4).fit(X, y)
    freed = PoissonGLM(l2=1e4, free=(0,)).fit(X, y)
    assert abs(heavy.coef[1]) < 0.05
    assert freed.coef[1] / freed.sd[0] == pytest.approx(0.5, abs=0.03)


def test_stacked_dc_rates_for_training_rows_are_out_of_sample():
    """Each weekly DC snapshot sees only matches before its grid date, so scrambling later
    results leaves the out-of-sample rates of earlier matches unchanged."""
    df = synthetic_league(seed=6, seasons=("2122", "2223", "2324"))
    cutoff, hist, _ = batch_at(df, 10)
    split = hist.date.unique()[-5]
    scrambled = hist.copy()
    late = scrambled.date >= split
    rng = np.random.default_rng(0)
    scrambled.loc[late, "home_goals"] = rng.integers(0, 6, late.sum())
    a = MLGBM("a", learner="glm", stack=True, stack_offset=False)
    b = MLGBM("b", learner="glm", stack=True, stack_offset=False)
    a.fit(hist, cutoff)
    b.fit(scrambled, cutoff)
    early = hist[hist.date < split]
    ra, rb = a._oos_rates(early), b._oos_rates(early)
    assert np.isfinite(ra[0]).sum() > 50
    np.testing.assert_allclose(ra[0], rb[0])
    np.testing.assert_allclose(ra[1], rb[1])


def test_glm_beats_frequency_on_synthetic_data():
    df = with_stats(synthetic_league(seed=3, seasons=("2122", "2223", "2324"), teams=12))
    led = run_backtest(df, BacktestSpec(lambda: MLGBM("g", learner="glm"), "E0", ["2324"]))
    fr = run_backtest(df, BacktestSpec(FrequencyBaseline, "E0", ["2324"]))
    assert headline(led)["1X2"]["rps"] < headline(fr)["1X2"]["rps"]


def test_same_inputs_same_outputs():
    df = with_stats(synthetic_league(seed=8))
    name = sorted(CANDIDATES)[-1]
    a = run_backtest(df, BacktestSpec(CANDIDATES[name], "E0", ["2324"]))
    b = run_backtest(df, BacktestSpec(CANDIDATES[name], "E0", ["2324"]))
    pd.testing.assert_frame_equal(a, b)


def test_feature_list_is_unique():
    assert len(FEATURES) == len(set(FEATURES))
