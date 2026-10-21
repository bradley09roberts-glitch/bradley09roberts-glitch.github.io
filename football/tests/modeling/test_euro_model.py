"""Pan-European model on SYNTHETIC data: two leagues of different strength, linked only by
cross-league (UEFA-style) matches."""

import numpy as np
import pandas as pd

from footy.modeling.euro import PanEuropeanDC


def two_leagues(seed=0, n_weeks=60):
    rng = np.random.default_rng(seed)
    strong = [f"aaa-s{i}" for i in range(6)]
    weak = [f"bbb-w{i}" for i in range(6)]
    true = {t: 0.4 + rng.normal(0, 0.15) for t in strong} | {
        t: -0.4 + rng.normal(0, 0.15) for t in weak
    }
    rows, mid, day = [], 1, pd.Timestamp("2023-08-05")
    for w in range(n_weeks):
        for league, teams in (("LA", strong), ("LB", weak)):
            order = rng.permutation(teams)
            for h, a in zip(order[::2], order[1::2], strict=True):
                rows.append((mid, league, "domestic", day, h, a, true[h], true[a]))
                mid += 1
        if w % 3 == 0:
            h, a = rng.choice(strong), rng.choice(weak)
            if rng.random() < 0.5:
                h, a = a, h
            rows.append((mid, "UCL", "euro", day + pd.Timedelta(days=3), h, a, true[h], true[a]))
            mid += 1
        day += pd.Timedelta(days=7)
    df = pd.DataFrame(
        rows, columns=["id", "comp", "kind", "date", "home_team", "away_team", "sh", "sa"]
    )
    lh = np.exp(0.2 + 0.25 + df.sh - df.sa)
    la = np.exp(0.2 + df.sa - df.sh)
    df["home_goals"] = rng.poisson(lh)
    df["away_goals"] = rng.poisson(la)
    df["neutral"] = 0
    df["status"] = "played"
    df["model_exclusion"] = None
    df["season"] = "2324"
    return df, true


def test_converges_to_the_same_optimum_from_another_start():
    df, _ = two_leagues(n_weeks=10)
    m = PanEuropeanDC(xi=0.002)
    m.fit(df, df.date.max() + pd.Timedelta(days=1))  # builds the parameter layout
    x1 = m.x.copy()
    m2 = PanEuropeanDC(xi=0.002)
    m2._x = {k: v + 0.3 for k, v in m._x.items()}
    m2.fit(df, df.date.max() + pd.Timedelta(days=1))
    assert m.converged_ and m2.converged_
    np.testing.assert_allclose(m2.x, x1, atol=2e-3)


def test_cross_league_matches_separate_the_leagues():
    df, true = two_leagues()
    m = PanEuropeanDC(xi=0.0, reg_team=1.0, reg_league=0.1, euro_weight=1.0)
    m.fit(df, df.date.max() + pd.Timedelta(days=1))
    strong = np.mean([m.strength(t)[0] for t in true if t.startswith("aaa")])
    weak = np.mean([m.strength(t)[0] for t in true if t.startswith("bbb")])
    assert strong - weak > 0.4  # true gap is 0.8
    fx = pd.DataFrame(
        {"comp": ["UCL"], "home_team": ["bbb-w0"], "away_team": ["aaa-s0"], "neutral": [0]}
    )
    p = m.predict(fx).p1x2[0]
    assert p[2] > p[0]  # the strong-league away side is favoured


def _capture_objective(module, fit):
    """Run fit() while capturing the objective handed to scipy's minimize."""
    captured = {}
    real = module.minimize

    def spy(f, x0, **kw):
        captured["f"], captured["x0"] = f, x0
        return real(f, x0, **kw)

    module.minimize = spy
    try:
        fit()
    finally:
        module.minimize = real
    return captured["f"], captured["x0"]


def test_pan_european_gradient_matches_finite_differences():
    from scipy.optimize import check_grad

    import footy.modeling.euro as eu

    df, _ = two_leagues(n_weeks=8)
    m = eu.PanEuropeanDC(xi=0.002)
    f, x0 = _capture_objective(eu, lambda: m.fit(df, df.date.max() + pd.Timedelta(days=1)))
    x = x0 + np.random.default_rng(1).normal(0, 0.1, len(x0))
    x[-1] = -0.05
    err = check_grad(lambda z: f(z)[0], lambda z: f(z)[1], x)
    assert err / np.linalg.norm(f(x)[1]) < 1e-5


def test_dixon_coles_gradient_matches_finite_differences():
    from scipy.optimize import check_grad

    import footy.modeling.dixon_coles as dcm

    from .test_walkforward import synthetic_league

    d = synthetic_league(seasons=("2223", "2324"))
    m = dcm.DixonColes()
    f, x0 = _capture_objective(dcm, lambda: m.fit(d, pd.Timestamp("2024-06-01", tz="UTC")))
    x = x0 + np.random.default_rng(2).normal(0, 0.1, len(x0))
    x[-1] = -0.05
    err = check_grad(lambda z: f(z)[0], lambda z: f(z)[1], x)
    assert err / np.linalg.norm(f(x)[1]) < 1e-5
