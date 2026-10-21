"""Lab family elo_goals: goal-based Elo mapped to score grids, on SYNTHETIC matches only."""

import numpy as np
import pandas as pd
import pytest

from footy.modeling.baselines import FrequencyBaseline
from footy.modeling.grid import check_coherent
from footy.modeling.lab.elo_goals import (
    CANDIDATES,
    DIDX,
    EloBlend,
    EloGoals,
    expected_goals,
    poisson_points,
    wfe_index,
)
from footy.modeling.ledger import headline
from footy.modeling.walkforward import RESULT_COLUMNS, BacktestSpec, run_backtest

from .test_walkforward import synthetic_league


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


def _two_matches(goals):
    """Two SYNTHETIC matches between sides a and b, a week apart."""
    (h1, a1), (h2, a2) = goals
    return pd.DataFrame(
        {
            "id": [1, 2],
            "division": ["E0", "E0"],
            "season": ["2324", "2324"],
            "date": pd.to_datetime(["2023-08-12", "2023-08-19"]),
            "home_team": ["a", "b"],
            "away_team": ["b", "a"],
            "home_goals": [h1, h2],
            "away_goals": [a1, a2],
            "home_sot": [np.nan, np.nan],
            "away_sot": [np.nan, np.nan],
        }
    )


def test_candidates_are_prefixed_and_few():
    assert 0 < len(CANDIDATES) <= 5
    assert all(name.startswith("elo_goals_") for name in CANDIDATES)
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
    assert np.isfinite(mp.lam_h).all() and np.isfinite(mp.lam_a).all()


@pytest.mark.parametrize("name", sorted(CANDIDATES))
def test_candidates_are_deterministic(name):
    df = synthetic_league(seed=10)
    a = run_backtest(df, BacktestSpec(CANDIDATES[name], "E0", ["2324"]))
    b = run_backtest(df, BacktestSpec(CANDIDATES[name], "E0", ["2324"]))
    pd.testing.assert_frame_equal(a, b)


def test_a_fit_does_not_depend_on_earlier_fits():
    df = synthetic_league(seed=4, seasons=("2122", "2223", "2324"))
    fresh = _fit_predict(EloGoals(tempo_lr=0.03), df)
    used = EloGoals(tempo_lr=0.03)
    _fit_predict(used, df, season="2223")
    again = _fit_predict(used, df)
    np.testing.assert_allclose(fresh.grid, again.grid, atol=1e-12)


def test_world_football_elo_margin_index():
    assert [wfe_index(n) for n in (0, 1, 2, 3, 4, 6)] == [1, 1, 1.5, 1.75, 15 / 8, 17 / 8]
    assert wfe_index(1.5) == pytest.approx(1.25)  # blended margins interpolate


def test_elo_update_follows_world_football_elo():
    m = EloGoals(k=20.0, hfa0=60.0, hfa_lr=0.0, provisional=0.0, closed_doors=False)
    m.fit(_two_matches([(3, 0), (1, 1)]), pd.Timestamp("2023-08-25", tz="UTC"))
    # match 1: a (0) at home to b (0) with 60 points of home advantage; a wins by 3
    e1 = 1 / (1 + 10 ** (-60 / 400))
    d1 = 20 * 1.75 * (1 - e1)
    # match 2: b (−d1) at home to a (+d1); a draw
    e2 = 1 / (1 + 10 ** (-(-d1 + 60 - d1) / 400))
    d2 = 20 * 1.0 * (0.5 - e2)
    ra, rb = m.R_[m.tidx_["a"]], m.R_[m.tidx_["b"]]
    assert rb == pytest.approx(-d1 + d2)
    assert ra == pytest.approx(d1 - d2)


def test_goal_difference_update():
    m = EloGoals(
        update="gd",
        k=10.0,
        gd_slope=1.5,
        hfa0=60.0,
        hfa_lr=0.0,
        provisional=0.0,
        closed_doors=False,
    )
    m.fit(_two_matches([(2, 0), (0, 1)]), pd.Timestamp("2023-08-25", tz="UTC"))
    d1 = 10 * (2 - 1.5 * 60 / 400)
    d2 = 10 * (-1 - 1.5 * (-d1 + 60 - d1) / 400)
    assert m.R_[m.tidx_["a"]] == pytest.approx(d1 - d2)
    assert m.R_[m.tidx_["b"]] == pytest.approx(-d1 + d2)


def test_home_advantage_learns_from_home_results():
    df = synthetic_league(seed=5, seasons=("2223", "2324"))
    df.loc[df.season == "2223", "home_goals"] += 1  # home sides far stronger than usual
    m = EloGoals(hfa_lr=0.1, closed_doors=False)
    _fit_predict(m, df)
    assert m.H_[DIDX["E0"]] > 65.0 + 20


def test_ratings_recover_the_strength_order():
    df = synthetic_league(seed=12, seasons=("2122", "2223", "2324"), teams=10)
    for cfg in ({}, {"update": "gd", "k": 10.0}):
        m = EloGoals(**cfg)
        _fit_predict(m, df)
        rng = np.random.default_rng(12)
        true = rng.normal(0, 0.3, 10)
        est = np.array([m.R_[m.tidx_[f"eng-team{i}"]] for i in range(10)])
        assert np.corrcoef(true, est)[0, 1] > 0.8, cfg


def test_all_missing_shots_on_target_change_nothing():
    df = synthetic_league(seed=8)  # SoT is NaN in synthetic data
    for cfg in ({"update": "gd", "tempo_lr": 0.03}, {"tempo_lr": 0.03, "ad_lr": 0.01}):
        a = _fit_predict(EloGoals(**cfg), df)
        b = _fit_predict(EloGoals(**cfg, sot_blend=0.6, tempo_sot=0.7, ad_sot=1.0), df)
        np.testing.assert_allclose(a.grid, b.grid, atol=1e-12)


def test_shots_on_target_inform_the_ratings():
    df = _with_sot(synthetic_league(seed=9, seasons=("2122", "2223", "2324")))
    a = _fit_predict(EloGoals(update="gd"), df)
    b = _fit_predict(EloGoals(update="gd", sot_blend=0.5, tempo_lr=0.03, tempo_sot=0.5), df)
    assert check_coherent(b) == []
    assert not np.allclose(a.p1x2, b.p1x2, atol=1e-3)


def test_expected_goals_fall_back_to_goals():
    df = _with_sot(synthetic_league(seed=1))
    df.loc[df.index[:5], "home_sot"] = np.nan
    xh, xa = expected_goals(df)
    np.testing.assert_allclose(xh[:5], df.home_goals.to_numpy(float)[:5])
    ok = df.home_sot.notna().to_numpy() & df.away_sot.notna().to_numpy()
    assert xh[ok].sum() + xa[ok].sum() == pytest.approx(
        df.home_goals[ok].sum() + df.away_goals[ok].sum()
    )


def test_poisson_points():
    p = poisson_points(np.array([1.3, 2.5, 0.5]), np.array([1.3, 0.5, 2.5]))
    assert p[0] == pytest.approx(0.5, abs=1e-6)
    assert p[1] > 0.8 and p[2] < 0.2
    assert p[1] + p[2] == pytest.approx(1.0, abs=1e-5)


def test_second_tier_sides_start_below_the_top_flight():
    top = synthetic_league(seed=2, seasons=("2223", "2324"), teams=8)
    low = synthetic_league(seed=3, seasons=("2223", "2324"), teams=8)
    low["division"] = "E1"
    low["home_team"] = low.home_team.str.replace("eng-", "low-")
    low["away_team"] = low.away_team.str.replace("eng-", "low-")
    low["id"] += 10_000
    df = pd.concat([top, low]).sort_values(["date", "id"]).reset_index(drop=True)
    m = EloGoals(tier2_offset=300.0)
    mp = _fit_predict(m, df)
    assert check_coherent(mp) == []
    r = np.array(m.R_)
    tier1 = [m.tidx_[t] for t in set(top.home_team)]
    tier2 = [m.tidx_[t] for t in set(low.home_team)]
    assert r[tier1].mean() - r[tier2].mean() == pytest.approx(300.0, abs=60.0)


def test_unseen_team_and_new_season_are_handled():
    df = synthetic_league(seed=6, seasons=("2223", "2324"))
    hist = df[df.season == "2223"]
    m = EloGoals(update="gd", tempo_lr=0.03, promo_decay=20.0, ad_lr=0.01)
    m.fit(hist, df[df.season == "2324"].cutoff_utc.min())
    fx = df[df.season == "2324"].head(3).drop(columns=RESULT_COLUMNS).copy()
    fx.loc[fx.index[0], "home_team"] = "eng-newcomer"
    mp = m.predict(fx)
    assert check_coherent(mp) == []
    assert np.isfinite(mp.lam_h).all() and np.isfinite(mp.lam_a).all()


def test_options_stay_coherent():
    df = _with_sot(synthetic_league(seed=11, seasons=("2122", "2223", "2324"), teams=12))
    for cfg in (
        {"margin": "pow", "margin_p": 1.0, "autocorr": 1.0, "margin_cap": 4},
        {"update": "gd", "glicko": True, "sot_blend": 0.4},
        {"update": "lgr", "gd_slope": 1.0, "k": 15.0, "k2": 5.0, "update2": "gd"},
        {"season_boost": 1.0, "promo_decay": 20.0, "level_lr": 0.02, "sq_term": True},
        {"update": "gd", "gd_psi": True, "gd_cap": 3.0, "red_k": 0.5, "k_tier2": 0.8},
    ):
        mp = _fit_predict(EloGoals(**cfg), df)
        assert check_coherent(mp) == [], cfg
        assert np.isfinite(mp.lam_h).all()


def test_blend_of_one_model_is_that_model():
    df = synthetic_league(seed=13)
    a = _fit_predict(EloGoals(tempo_lr=0.03), df)
    b = _fit_predict(EloBlend([EloGoals(tempo_lr=0.03), EloGoals(tempo_lr=0.03)], [1, 3]), df)
    np.testing.assert_allclose(a.grid, b.grid, atol=1e-12)


def test_unknown_option_is_rejected():
    with pytest.raises(TypeError):
        EloGoals(kfactor=10.0)
