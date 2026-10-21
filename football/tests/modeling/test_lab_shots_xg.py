"""The shots_xg lab family, on SYNTHETIC matches only."""

import numpy as np
import pandas as pd
import pytest

from footy.modeling.baselines import FrequencyBaseline
from footy.modeling.data import history_before
from footy.modeling.lab.shots_xg import CANDIDATES, ShotsXG
from footy.modeling.ledger import headline
from footy.modeling.walkforward import BacktestSpec, run_backtest

from .test_walkforward import synthetic_league

PROB_COLS = ["p_H", "p_D", "p_A", "p_over_1.5", "p_over_2.5", "p_over_3.5", "p_btts"]


def with_shots(df: pd.DataFrame, seed: int = 0) -> pd.DataFrame:
    """Add shot counts that carry the same signal as the goals (goals are a subset of shots
    on target, which are a subset of shots)."""
    rng = np.random.default_rng(seed)
    out = df.copy()
    for side in ("home", "away"):
        goals = out[f"{side}_goals"].to_numpy()
        sot = goals + rng.poisson(2.0 + 0.8 * goals)
        out[f"{side}_sot"] = sot.astype(float)
        out[f"{side}_shots"] = (sot + rng.poisson(5.0 + 1.5 * goals)).astype(float)
    return out


def fit_at(df: pd.DataFrame, model, k: int = 5):
    cutoff_utc = sorted(df[df.season == df.season.max()].cutoff_utc.unique())[k]
    batch = df[df.cutoff_utc == cutoff_utc]
    hist = history_before(df, batch.cutoff_date.iloc[0])
    model.fit(hist, pd.Timestamp(cutoff_utc))
    return model, hist, batch


def test_registered_names_are_prefixed_and_few():
    assert 0 < len(CANDIDATES) <= 5
    for name, factory in CANDIDATES.items():
        assert name.startswith("shots_xg_")
        assert factory().name == name


@pytest.mark.parametrize("name", sorted(CANDIDATES))
@pytest.mark.parametrize("shots", [False, True])
def test_candidates_run_coherently_with_and_without_shots(name, shots):
    df = synthetic_league(seed=2)
    if shots:
        df = with_shots(df)
    led = run_backtest(df, BacktestSpec(CANDIDATES[name], "E0", ["2324"]))
    pred = led[led.status == "predicted"]
    assert len(pred) > 50
    p = pred[PROB_COLS].to_numpy(float)
    assert np.isfinite(p).all() and (p > 0).all() and (p < 1).all()
    assert np.allclose(pred[["p_H", "p_D", "p_A"]].sum(axis=1), 1)


def test_without_shot_data_the_shot_models_reduce_to_goals_only():
    """Synthetic data has no shots: SoT factors are frozen out and the blend falls back to
    goals, so predictions match the goals-only model with the same goal settings."""
    df = synthetic_league(seed=4)
    joint, _, batch = fit_at(df, CANDIDATES["shots_xg_sot_joint"]())
    goals_only, _, _ = fit_at(
        df,
        ShotsXG(
            stats=("goals",),
            reg_strength={"goals": joint.reg_strength["goals"]},
            reg_tempo={"goals": joint.reg_tempo["goals"]},
            xi=joint.xi,
            blend=0.0,
        ),
    )
    a, b = joint.predict(batch), goals_only.predict(batch)
    np.testing.assert_allclose(a.lam_h, b.lam_h, rtol=1e-4)
    np.testing.assert_allclose(a.lam_a, b.lam_a, rtol=1e-4)

    proxy, _, _ = fit_at(df, CANDIDATES["shots_xg_proxy"]())
    control, _, _ = fit_at(df, CANDIDATES["shots_xg_noshots"]())
    np.testing.assert_allclose(proxy.predict(batch).p1x2, control.predict(batch).p1x2)


@pytest.mark.parametrize("season_rw", [None, 50.0])
def test_gradient_matches_finite_differences(season_rw):
    df = with_shots(synthetic_league(seed=5))
    df.loc[df.index[::7], ["home_shots", "away_sot"]] = np.nan  # partly missing statistics
    model = ShotsXG(
        stats=("shots", "sot", "goals"), season_rw=season_rw, tempo_transfer=None, blend=0.3
    )
    cutoff_utc = sorted(df[df.season == "2324"].cutoff_utc.unique())[6]
    hist = history_before(df, df[df.cutoff_utc == cutoff_utc].cutoff_date.iloc[0])
    f, x0, bounds, _, _ = model._problem(hist, pd.Timestamp(cutoff_utc))
    rng = np.random.default_rng(0)
    lo = np.array([-np.inf if b[0] is None else b[0] for b in bounds])
    hi = np.array([np.inf if b[1] is None else b[1] for b in bounds])
    x = np.clip(x0 + rng.normal(0, 0.05, len(x0)), lo, hi)
    _, g = f(x)
    free = np.flatnonzero(lo < hi)
    for i in rng.choice(free, 30, replace=False):
        e = np.zeros_like(x)
        e[i] = 1e-6
        num = (f(x + e)[0] - f(x - e)[0]) / 2e-6
        assert num == pytest.approx(g[i], rel=1e-4, abs=1e-4)


def test_shots_are_used_when_present_and_the_model_beats_frequency():
    df = with_shots(synthetic_league(seed=3, seasons=("2122", "2223", "2324"), teams=12))
    model, _, _ = fit_at(df, CANDIDATES["shots_xg_two_process"]())
    L = model.layout_
    b_goal_sot = model.x_[L["bpos"][("goals", "sot", "s")]]
    assert b_goal_sot != 1.0  # a fitted transfer, not the frozen no-data value
    s_sot = model.x_[L["sl"]["s_sot"]]
    assert np.abs(s_sot).max() > 0.01  # the SoT strength factor carries signal
    led = run_backtest(df, BacktestSpec(CANDIDATES["shots_xg_two_process"], "E0", ["2324"]))
    fr = run_backtest(df, BacktestSpec(FrequencyBaseline, "E0", ["2324"]))
    assert headline(led)["1X2"]["rps"] < headline(fr)["1X2"]["rps"]


def test_new_season_uses_the_latest_scoring_level():
    """With per-season levels, the first batch of a season has no rows of its own season and
    must fall back to the previous season's level for that division."""
    df = with_shots(synthetic_league(seed=6))
    first = df[df.season == "2324"].cutoff_utc.min()
    batch = df[df.cutoff_utc == first]
    hist = history_before(df, batch.cutoff_date.iloc[0])
    assert "2324" not in set(hist.season)
    model = CANDIDATES["shots_xg_sot_joint_rw"]()
    model.fit(hist, pd.Timestamp(first))
    lh, la = model.rates(batch)
    assert np.isfinite(lh).all() and np.isfinite(la).all() and (lh > 0).all()


def test_same_inputs_same_outputs():
    df = with_shots(synthetic_league(seed=8))
    a = run_backtest(df, BacktestSpec(CANDIDATES["shots_xg_sot_joint"], "E0", ["2324"]))
    b = run_backtest(df, BacktestSpec(CANDIDATES["shots_xg_sot_joint"], "E0", ["2324"]))
    pd.testing.assert_frame_equal(a, b)


def test_a_statistic_without_data_ignores_stale_warm_start_factors():
    """A refit with no shot data must not reuse SoT factors from an earlier fit that had
    them: they would still reach the goal rates through the frozen transfer of 1.0."""
    df = synthetic_league(seed=4)
    cut = sorted(df[df.season == "2324"].cutoff_utc.unique())[5]
    batch = df[df.cutoff_utc == cut]
    hist = history_before(df, batch.cutoff_date.iloc[0])
    hist_shots = history_before(with_shots(df), batch.cutoff_date.iloc[0])
    warm = CANDIDATES["shots_xg_sot_joint"]()
    warm.fit(hist_shots, pd.Timestamp(cut))
    warm.fit(hist, pd.Timestamp(cut))
    fresh = CANDIDATES["shots_xg_sot_joint"]()
    fresh.fit(hist, pd.Timestamp(cut))
    assert not warm.x_[warm.layout_["sl"]["s_sot"]].any()
    np.testing.assert_allclose(warm.rates(batch), fresh.rates(batch), rtol=1e-3)


@pytest.mark.parametrize("name", ["shots_xg_sot_joint_rw", "shots_xg_two_process"])
def test_predictions_ignore_future_results_and_shots(name):
    """The shared leakage test runs without shots; here later goals AND shot counts are
    scrambled, in the league and its second tier, and earlier predictions must not move."""
    seasons = ("2122", "2223", "2324")
    top = with_shots(synthetic_league(seed=7, seasons=seasons))
    tier2 = with_shots(synthetic_league(seed=17, seasons=seasons), seed=3)
    tier2["division"] = "E1"
    for col in ("home_team", "away_team"):
        tier2[col] = tier2[col].str.replace("eng-", "eng2-")
    tier2["id"] += 100_000
    df = pd.concat([top, tier2]).sort_values(["date", "id"]).reset_index(drop=True)
    split = df[df.season == "2324"].cutoff_date.min() + pd.Timedelta(days=60)
    later = df.date >= split
    rng = np.random.default_rng(0)
    scrambled = df.copy()
    for col, hi in [("home_goals", 6), ("away_goals", 6), ("home_sot", 15), ("away_sot", 15)]:
        scrambled.loc[later, col] = rng.integers(0, hi, later.sum())
    for col in ("home_shots", "away_shots"):
        scrambled.loc[later, col] = rng.integers(15, 40, later.sum()).astype(float)
    scrambled["outcome"] = np.where(
        scrambled.home_goals > scrambled.away_goals,
        0,
        np.where(scrambled.home_goals == scrambled.away_goals, 1, 2),
    )
    scrambled["total_goals"] = scrambled.home_goals + scrambled.away_goals
    a = run_backtest(df, BacktestSpec(CANDIDATES[name], "E0", ["2324"]))
    b = run_backtest(scrambled, BacktestSpec(CANDIDATES[name], "E0", ["2324"]))
    early = (pd.to_datetime(a.date) < split).to_numpy()
    pa, pb = a[PROB_COLS].to_numpy(float), b[PROB_COLS].to_numpy(float)
    assert early.sum() > 20
    np.testing.assert_allclose(pa[early], pb[early])
    assert np.abs(pa[~early] - pb[~early]).max() > 1e-3  # the scrambled data is really used
