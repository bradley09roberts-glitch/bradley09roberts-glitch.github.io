"""Lab family congestion_context: match-context terms on Dixon-Coles, on SYNTHETIC data only."""

import numpy as np
import pandas as pd
import pytest

from footy.modeling.baselines import FrequencyBaseline
from footy.modeling.dixon_coles import DixonColes
from footy.modeling.grid import check_coherent, poisson_grid
from footy.modeling.lab.congestion_context import (
    CANDIDATES,
    ContextDC,
    ContextTilt,
    dead_rubbers,
    poisson_ridge,
    team_context,
    tilt_grid,
)
from footy.modeling.ledger import headline
from footy.modeling.walkforward import RESULT_COLUMNS, BacktestSpec, run_backtest

from .test_walkforward import synthetic_league

PROBS = ["p_H", "p_D", "p_A", "p_over_2.5", "p_btts"]


def _fit_predict(model, df, season="2324", batch=0):
    target = df[df.season == season]
    cutoff = sorted(target.cutoff_utc.unique())[batch]
    fx = target[target.cutoff_utc == cutoff]
    hist = df[df.date < fx.cutoff_date.min()]
    model.fit(hist, cutoff)
    return model.predict(fx.drop(columns=RESULT_COLUMNS))


def _schedule(rows):
    """A tiny SYNTHETIC schedule: (date, division, season, home, away, home_goals, away_goals)."""
    df = pd.DataFrame(
        rows,
        columns=[
            "date",
            "division",
            "season",
            "home_team",
            "away_team",
            "home_goals",
            "away_goals",
        ],
    )
    df["date"] = pd.to_datetime(df["date"])
    df["kickoff_utc"] = pd.NaT
    return df


def test_candidates_are_prefixed_and_few():
    assert 0 < len(CANDIDATES) <= 5
    assert all(name.startswith("congestion_context_") for name in CANDIDATES)
    for name, factory in CANDIDATES.items():
        assert factory().name == name


@pytest.mark.parametrize("name", sorted(CANDIDATES))
def test_candidates_are_coherent_and_beat_frequency(name):
    df = synthetic_league(seed=3, seasons=("2122", "2223", "2324"), teams=12)
    led = run_backtest(df, BacktestSpec(CANDIDATES[name], "E0", ["2324"]))
    fr = run_backtest(df, BacktestSpec(FrequencyBaseline, "E0", ["2324"]))
    assert headline(led)["1X2"]["rps"] < headline(fr)["1X2"]["rps"]
    assert check_coherent(_fit_predict(CANDIDATES[name](), df)) == []


@pytest.mark.parametrize("name", sorted(CANDIDATES))
def test_candidates_are_deterministic(name):
    df = synthetic_league(seed=5)
    a = run_backtest(df, BacktestSpec(CANDIDATES[name], "E0", ["2324"]))
    b = run_backtest(df, BacktestSpec(CANDIDATES[name], "E0", ["2324"]))
    pd.testing.assert_frame_equal(a, b)


def test_without_context_it_is_the_core_dixon_coles():
    df = synthetic_league(seed=2, seasons=("2223", "2324"))
    a = run_backtest(df, BacktestSpec(ContextDC, "E0", ["2324"]))
    b = run_backtest(df, BacktestSpec(DixonColes, "E0", ["2324"]))
    np.testing.assert_allclose(a[PROBS].to_numpy(float), b[PROBS].to_numpy(float))


def test_team_context_rest_congestion_counts_and_promotion():
    df = _schedule(
        [
            ("2023-04-01", "E1", "2223", "a", "b", 1, 0),
            ("2023-04-05", "E1", "2223", "b", "a", 2, 2),
            ("2023-04-08", "E1", "2223", "a", "c", 0, 1),
            ("2023-08-12", "E0", "2324", "a", "x", 1, 1),  # a promoted, x was not in the data
            ("2023-08-15", "E0", "2324", "x", "a", np.nan, np.nan),  # a fixture: no result
        ]
    )
    fh, fa = team_context(df)
    assert np.isnan(fh.rest[0]) and fa.rest[1] == 4 and fh.rest[2] == 3
    assert fa.rest[4] == 3 and fh.rest[4] == 3
    assert fh.c14[2] == 2 and fh.c21[3] == 0  # 2 matches in the 14 days before 8 April
    assert list(fh.n_season) == [0, 1, 2, 0, 1]
    assert fh.promoted[3] == 1 and fa.promoted[4] == 1 and fa.promoted[3] == 0
    assert fh.promoted.iloc[:3].sum() == 0  # second-tier rows are never 'promoted'


def _round_robin(teams):
    """Double round robin by the circle method: every team plays once per round."""
    n, arr, rounds = len(teams), list(teams), []
    for _ in range(n - 1):
        rounds.append([(arr[i], arr[n - 1 - i]) for i in range(n // 2)])
        arr = [arr[0], arr[-1], *arr[1:-1]]
    return rounds + [[(a, h) for h, a in rd] for rd in rounds]


def test_dead_rubbers_flags_settled_relegated_and_not_early_teams():
    # 12 SYNTHETIC teams; the lower-numbered team always wins 2-0, so the final table is
    # t00 (66 points) down to t11 (0). Before the last round t06 and t07 can neither reach the
    # top six nor fall into the bottom three, and t10 and t11 are already relegated.
    teams = [f"t{i:02d}" for i in range(12)]
    rows, day = [], pd.Timestamp("2023-08-05")
    for rd in _round_robin(teams):
        for h, a in rd:
            rows.append((day, "E0", "2324", h, a, *((2, 0) if h < a else (0, 2))))
        day += pd.Timedelta(days=7)
    df = _schedule(rows)
    dh, da = dead_rubbers(df)
    last = (df.date == df.date.max()).to_numpy()
    dead = dict(zip(df.home_team[last], dh[last], strict=True))
    dead |= dict(zip(df.away_team[last], da[last], strict=True))
    assert len(dead) == 12
    assert dead["t06"] == dead["t07"] == dead["t10"] == dead["t11"] == 1
    assert dead["t02"] == dead["t03"] == 0  # still in the top six
    early = (df.date < df.date.min() + pd.Timedelta(days=7 * 10)).to_numpy()
    assert dh[early].sum() == 0 and da[early].sum() == 0  # not checked before mid-season
    # only results dated before the match day count
    scrambled = df.copy()
    scrambled.loc[last, ["home_goals", "away_goals"]] = 9, 0
    dh2, da2 = dead_rubbers(scrambled)
    np.testing.assert_array_equal(dh, dh2)
    np.testing.assert_array_equal(da, da2)


def test_poisson_ridge_recovers_a_planted_effect():
    rng = np.random.default_rng(0)
    x = (rng.random(20_000) < 0.3).astype(float)
    offset = np.log(rng.uniform(0.8, 2.0, x.size))
    y = rng.poisson(np.exp(offset + 0.2 * x))
    b = poisson_ridge(x[:, None], y, offset, np.ones(x.size), reg=1.0)
    assert abs(b[0] - 0.2) < 0.03


def test_second_stage_learns_a_late_season_scoring_boost():
    df = synthetic_league(seed=4, seasons=("2122", "2223", "2324"), teams=12)
    start = pd.to_datetime("20" + df.season.str[:2] + "-08-06")
    df["date"] = start + (df.date - start) * 2  # fortnightly rounds: August to June
    rng = np.random.default_rng(9)
    late = df.date.dt.month.isin([4, 5, 6])
    df.loc[late, "home_goals"] += rng.poisson(0.5, late.sum())
    df.loc[late, "away_goals"] += rng.poisson(0.4, late.sum())
    cutoff = pd.Timestamp("2024-03-01", tz="UTC")
    hist = df[df.date < "2024-03-01"]
    model = ContextDC(match_feats=("late",))
    model.fit(hist, cutoff)
    assert model.beta_["match:late"] > 0.08
    fx = df[(df.date >= "2024-03-01") & (df.date < "2024-05-01")].drop(columns=RESULT_COLUMNS)
    lh, la = model.context_rates(fx)
    base = DixonColes()
    base.fit(hist, cutoff)
    bh, ba = base.rates(fx)
    april = (fx.date.dt.month == 4).to_numpy()
    assert april.any() and not april.all()
    np.testing.assert_allclose(lh[~april], bh[~april])  # no boost outside April-June
    np.testing.assert_allclose(lh[april] / bh[april], np.exp(model.beta_["match:late"]))


def test_promoted_penalty_and_early_openness_shrink_apply_only_where_they_should():
    df = synthetic_league(seed=6, seasons=("2223", "2324"), teams=10)
    # SYNTHETIC second tier: eng-team0 played 2022/23 in E1 against made-up clubs
    e1 = df[(df.season == "2223")].copy()
    e1["division"] = "E1"
    e1["home_team"] = e1.home_team.str.replace("eng-team", "eng-lower")
    e1["away_team"] = e1.away_team.str.replace("eng-team", "eng-lower")
    e1["home_team"] = e1.home_team.replace("eng-lower0", "eng-team0")
    e1["away_team"] = e1.away_team.replace("eng-lower0", "eng-team0")
    e1["id"] += 100_000
    top = df[
        ~((df.season == "2223") & ((df.home_team == "eng-team0") | (df.away_team == "eng-team0")))
    ]
    both = pd.concat([top, e1], ignore_index=True).sort_values(["date", "id"])
    target = both[both.season == "2324"]
    plays = (target.home_team == "eng-team0") | (target.away_team == "eng-team0")
    batch = sorted(target.cutoff_utc.unique()).index(target[plays].cutoff_utc.min())
    plain = _fit_predict(ContextDC(), both, batch=batch)
    promo = _fit_predict(ContextDC(promo=(0.2, 0.2, 10.0)), both, batch=batch)
    fx = target[target.cutoff_utc == target[plays].cutoff_utc.min()]
    involved = ((fx.home_team == "eng-team0") | (fx.away_team == "eng-team0")).to_numpy()
    assert involved.any() and not involved.all()
    np.testing.assert_allclose(plain.lam_h[~involved], promo.lam_h[~involved])
    home0 = (fx.home_team == "eng-team0").to_numpy()
    away0 = (fx.away_team == "eng-team0").to_numpy()
    assert (promo.lam_h[home0] < plain.lam_h[home0]).all()
    assert (promo.lam_a[away0] < plain.lam_a[away0]).all()
    # full openness shrinkage: the log-rate difference (strength) is unchanged, the log-rate
    # sum (openness) becomes the division's, the same for every fixture
    shrunk = _fit_predict(ContextDC(early=(0.0, 0.0, 1.0, 0.0, 10.0)), both, batch=batch)
    np.testing.assert_allclose(
        np.log(shrunk.lam_h / shrunk.lam_a), np.log(plain.lam_h / plain.lam_a)
    )
    total = np.log(shrunk.lam_h * shrunk.lam_a)
    np.testing.assert_allclose(total, total[0])
    assert np.log(plain.lam_h * plain.lam_a).std() > 1e-3


def test_tilt_multiplies_poisson_rates():
    lh, la = np.array([0.9, 1.4]), np.array([1.1, 0.6])
    dh, da = np.array([0.1, -0.2]), np.array([0.05, 0.3])
    np.testing.assert_allclose(
        tilt_grid(poisson_grid(lh, la), 0 * dh, 0 * da), poisson_grid(lh, la)
    )
    tilted = tilt_grid(poisson_grid(lh, la), dh, da)
    np.testing.assert_allclose(tilted, poisson_grid(lh * np.exp(dh), la * np.exp(da)), atol=1e-8)


def test_tilt_without_context_is_the_base_model():
    df = synthetic_league(seed=8, seasons=("2223", "2324"))
    a = run_backtest(df, BacktestSpec(lambda: ContextTilt("dixon_coles", {}, "t"), "E0", ["2324"]))
    b = run_backtest(df, BacktestSpec(DixonColes, "E0", ["2324"]))
    np.testing.assert_allclose(a[PROBS].to_numpy(float), b[PROBS].to_numpy(float))
