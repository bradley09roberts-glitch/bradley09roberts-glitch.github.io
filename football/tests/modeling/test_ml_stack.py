"""The learned meta-model (ml_stack family), on SYNTHETIC matches and SYNTHETIC prices only."""

import numpy as np
import pandas as pd
from scipy.special import expit

from footy.modeling.dixon_coles import DixonColes
from footy.modeling.grid import check_coherent, markets_from_grid, poisson_grid
from footy.modeling.lab.market_blend import consensus
from footy.modeling.lab.ml_stack import (
    CANDIDATES,
    CONFIGS,
    MarketMetaStack,
    MetaStack,
    StackSpec,
    fit_binary,
    fit_multinomial,
    predict_stack,
)
from footy.modeling.lab.ml_stream import shape_probs
from footy.modeling.ledger import headline
from footy.modeling.registry import candidates
from footy.modeling.walkforward import RESULT_COLUMNS, BacktestSpec, run_backtest

from .test_no_future_leakage import _league_with_deadlines, _synthetic_prices
from .test_walkforward import synthetic_league

PROBS = ["p_H", "p_D", "p_A", "p_over_1.5", "p_over_2.5", "p_over_3.5", "p_btts"]


class Inflated(DixonColes):
    """Dixon-Coles whose expected goals are 35% too high: a reference with a clear bias in
    the totals for the learner to correct."""

    def __init__(self):
        super().__init__(name="inflated")

    def predict(self, fixtures):
        lh, la = self.rates(fixtures)
        lh, la = 1.35 * lh, 1.35 * la
        return markets_from_grid(poisson_grid(lh, la, self.rho_), lh, la)


FACTORIES = {"dc": DixonColes, "inflated": Inflated}


def test_ridge_logistic_recovers_a_signal_and_keeps_the_offset_without_one():
    rng = np.random.default_rng(0)
    n = 6000
    Z = rng.normal(size=(n, 3))
    off = rng.normal(0, 0.5, n)
    y = (rng.random(n) < expit(off + 0.8 * Z[:, 0] - 0.4 * Z[:, 2])).astype(float)
    f = fit_binary(off, Z, y, np.ones(n), l2=1e-4, l2_int=1e-4)
    np.testing.assert_allclose(f.coef[1:] / f.sd, [0.8, 0.0, -0.4], atol=0.08)
    noise = (rng.random(n) < expit(off)).astype(float)
    g = fit_binary(off, Z, noise, np.ones(n), l2=1.0, l2_int=1.0)
    assert np.abs(g.coef).max() < 0.02  # heavy ridge, no signal: the offset is the forecast


def test_multinomial_learns_a_draw_bias():
    rng = np.random.default_rng(1)
    n = 8000
    off = np.log(np.tile([0.45, 0.25, 0.30], (n, 1)))
    truth = np.array([0.42, 0.31, 0.27])
    y = rng.choice(3, size=n, p=truth)
    f = fit_multinomial(off, rng.normal(size=(n, 1)), y, np.ones(n), l2=1e-3, l2_int=1e-4)
    eta = off[0] + np.r_[f.coef[:, 0], 0.0]
    p = np.exp(eta) / np.exp(eta).sum()
    np.testing.assert_allclose(p, truth, atol=0.015)


def _stack(**kw):
    return MetaStack(
        "ml_stack_test",
        ["inflated", "dc"],
        ref="inflated",
        others=["dc"],
        factories=FACTORIES,
        min_rows=50,
        **kw,
    )


def test_learner_corrects_a_biased_reference_and_returns_one_coherent_grid():
    df = synthetic_league(seed=12, seasons=("2122", "2223", "2324"), teams=12)
    fixed = run_backtest(df, BacktestSpec(Inflated, "E0", ["2324"]))
    led = run_backtest(df, BacktestSpec(lambda: _stack(l2=0.01, l2_int=0.01), "E0", ["2324"]))
    a, b = headline(fixed), headline(led)
    assert b["OU_2.5"]["log_loss"] < a["OU_2.5"]["log_loss"] - 0.005
    assert b["OU_1.5"]["log_loss"] < a["OU_1.5"]["log_loss"]

    m = _stack(l2=0.01, l2_int=0.01)
    last = df[df.cutoff_utc == df[df.season == "2324"].cutoff_utc.max()]
    from footy.modeling.walkforward import training_history

    m.fit(training_history(df, last.cutoff_date.iloc[0]), last.cutoff_utc.iloc[0])
    fx = last.drop(columns=RESULT_COLUMNS)
    out = m.predict(fx)
    assert not check_coherent(out)
    t = predict_stack(m.stream.predict(fx), m.spec, m.fits)
    np.testing.assert_allclose(shape_probs(out.grid), t, atol=1e-8)  # the grid hits them all
    assert m.fallbacks == 0


def test_zero_coefficients_give_the_reference():
    df = synthetic_league(seed=13, seasons=("2223", "2324"), teams=10)
    m = _stack(l2=1e6, l2_int=1e6)  # everything shrunk to zero
    led = run_backtest(df, BacktestSpec(lambda: m, "E0", ["2324"])).set_index("match_id")
    ref = run_backtest(df, BacktestSpec(Inflated, "E0", ["2324"])).set_index("match_id")
    np.testing.assert_allclose(
        led[PROBS].to_numpy(float), ref.loc[led.index, PROBS].to_numpy(float), atol=1e-4
    )


def test_spec_names_members_by_position():
    m = _stack()
    assert m.spec == StackSpec(ref=0, others=(1,), market=False, l2=1.0, l2_int=0.1, xi=0.0)
    assert m.params()["reference"] == "inflated"


def test_market_stack_ignores_later_results_and_prices():
    """SYNTHETIC prices: scrambling every result and price from a split date on leaves every
    earlier forecast identical; the prices are really used."""
    df = _league_with_deadlines()
    raw = _synthetic_prices(df, seed=1)
    split = df[df.season == "2324"].cutoff_date.min() + pd.Timedelta(days=60)
    later = df.date >= split
    scrambled = df.copy()
    rng = np.random.default_rng(6)
    scrambled.loc[later, "home_goals"] = rng.integers(0, 6, later.sum())
    scrambled.loc[later, "away_goals"] = rng.integers(0, 6, later.sum())
    scrambled["outcome"] = np.sign(scrambled.away_goals - scrambled.home_goals).astype(int) + 1
    scrambled["total_goals"] = scrambled.home_goals + scrambled.away_goals
    raw_later = _synthetic_prices(df, seed=2)
    raw_scrambled = pd.concat([raw[raw.date < split], raw_later[raw_later.date >= split]])

    def run(frame, prices):
        book = consensus(prices)
        return run_backtest(
            frame,
            BacktestSpec(
                lambda: MarketMetaStack(
                    "ml_stack_test",
                    ["inflated", "dc"],
                    "inflated",
                    ["dc"],
                    l2=0.01,
                    min_rows=30,
                    prices=lambda: book,
                    factories=FACTORIES,
                ),
                "E0",
                ["2324"],
            ),
        ).set_index("match_id")

    a, b = run(df, raw), run(scrambled, raw_scrambled)
    early = pd.to_datetime(a.date) < split
    np.testing.assert_allclose(
        a.loc[early, PROBS].to_numpy(float), b.loc[early, PROBS].to_numpy(float)
    )
    assert np.abs(a.loc[~early, "p_H"] - b.loc[~early, "p_H"]).max() > 1e-3
    no_prices = run(df, raw.iloc[0:0])
    assert np.abs(a.loc[early, "p_H"] - no_prices.loc[early, "p_H"]).min() > 1e-4


def test_registered_candidates_are_prefixed_few_and_name_registered_members():
    assert 0 < len(CANDIDATES) <= 3
    registry = candidates()
    for name, cfg in CONFIGS.items():
        assert name.startswith("ml_stack_")
        assert all(
            m in registry and not m.startswith(("ml_online_", "ml_stack_")) for m in cfg["members"]
        )
        model = CANDIDATES[name]()
        assert model.name == name
        assert hasattr(model, "decision_time") == bool(cfg.get("market"))
