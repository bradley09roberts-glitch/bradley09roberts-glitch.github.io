"""Online mixture weights (ml_online family), on SYNTHETIC matches and SYNTHETIC prices only."""

import numpy as np
import pandas as pd
import pytest

from footy.modeling.baselines import FrequencyBaseline
from footy.modeling.dixon_coles import DixonColes
from footy.modeling.grid import check_coherent, markets_from_grid, poisson_grid
from footy.modeling.lab.market_blend import consensus
from footy.modeling.lab.ml_online import (
    CANDIDATES,
    CONFIGS,
    SCALE,
    MarketOnlineMixture,
    OnlineMixture,
    eg_step,
    loss_and_grad,
)
from footy.modeling.ledger import headline
from footy.modeling.metrics import logloss_binary, rps
from footy.modeling.registry import candidates
from footy.modeling.walkforward import BacktestSpec, run_backtest

from .test_no_future_leakage import _league_with_deadlines, _synthetic_prices
from .test_walkforward import synthetic_league

PROBS = ["p_H", "p_D", "p_A", "p_over_1.5", "p_over_2.5", "p_over_3.5", "p_btts"]


class Flat:
    """A deliberately poor member: the same Poisson grid for every fixture."""

    name = "flat"
    markets = frozenset({"1X2", "OU", "BTTS"})

    def fit(self, history, cutoff):
        pass

    def predict(self, fixtures):
        n = len(fixtures)
        return markets_from_grid(poisson_grid(np.full(n, 1.4), np.full(n, 1.4)))

    def params(self):
        return {}


FACTORIES = {"dc": DixonColes, "flat": Flat}


def _probs(lh, la):
    m = markets_from_grid(poisson_grid(np.atleast_1d(lh), np.atleast_1d(la)))
    return np.r_[m.p1x2[0], m.p_over[1.5][0], m.p_over[2.5][0], m.p_over[3.5][0], m.p_btts[0]]


def _composite_loss(p, y):
    obs = np.array([y["1x2"]])
    out = rps(p[None, :3], obs)[0] / SCALE[0]
    for j, k in enumerate(("o15", "o25", "o35", "btts")):
        out += logloss_binary(p[3 + j], y[k]) / SCALE[1 + j]
    return float(out)


def test_loss_is_the_scaled_composite_and_its_gradient_is_exact():
    P = np.stack([_probs(1.6, 0.9), _probs(1.1, 1.3), _probs(2.2, 0.7)])
    w = np.array([0.5, 0.3, 0.2])
    for y in (
        {"1x2": 0, "o15": 1.0, "o25": 1.0, "o35": 0.0, "btts": 1.0},
        {"1x2": 1, "o15": 0.0, "o25": 0.0, "o35": 0.0, "btts": 0.0},
        {"1x2": 2, "o15": 1.0, "o25": 0.0, "o35": 0.0, "btts": 0.0},
    ):
        loss, grad = loss_and_grad(P, w, y)
        assert loss == pytest.approx(_composite_loss(w @ P, y))
        h = 1e-6
        num = [
            (_composite_loss((w + h * e) @ P, y) - _composite_loss((w - h * e) @ P, y)) / (2 * h)
            for e in np.eye(3)
        ]
        # off the simplex the two losses differ, by a term equal for every member; EG (and any
        # move that stays on the simplex) sees only the centred gradient
        np.testing.assert_allclose(grad - grad.mean(), np.subtract(num, np.mean(num)), atol=1e-6)


def test_eg_step_stays_on_the_simplex_and_moves_towards_the_lower_loss():
    w = np.array([0.25, 0.25, 0.5])
    v = eg_step(w, np.array([1.0, 0.0, 0.5]), eta=0.2, share=0.0)
    assert v.sum() == pytest.approx(1.0) and (v > 0).all()
    assert v[1] > w[1] and v[0] < w[0]
    shared = eg_step(w, np.array([50.0, 0.0, 50.0]), eta=1.0, share=0.1)
    assert shared.min() >= 0.1 / 3 - 1e-12  # the fixed share keeps every member alive
    # a member whose weight has underflowed to zero, and a step that would underflow the rest
    collapsed = eg_step(np.array([0.0, 1.0, 0.0]), np.array([0.0, 900.0, 0.0]), 20.0, 0.0)
    assert np.isfinite(collapsed).all() and collapsed.sum() == pytest.approx(1.0)


def _mixture(**kw):
    return OnlineMixture("ml_online_test", ["dc", "flat"], factories=FACTORIES, **kw)


def test_weights_learn_to_prefer_the_better_member_and_the_grid_is_their_mixture():
    df = synthetic_league(seed=9, seasons=("2122", "2223", "2324"), teams=12)
    led = run_backtest(df, BacktestSpec(lambda: _mixture(eta=0.5), "E0", ["2324"]))
    pred = led[led.status == "predicted"]
    assert len(pred) > 100

    m = _mixture(eta=0.5)
    batch = df[(df.season == "2324")]
    c = batch.cutoff_utc.max()
    last = batch[batch.cutoff_utc == c]
    from footy.modeling.walkforward import RESULT_COLUMNS, training_history

    m.fit(training_history(df, last.cutoff_date.iloc[0]), c)
    w = m.weights("E0")
    assert w[0] > 0.6  # Dixon-Coles over the flat grid
    assert m.updates > 100
    fx = last.drop(columns=RESULT_COLUMNS)
    out = m.predict(fx)
    assert not check_coherent(out)
    b = m.stream.predict(fx)
    np.testing.assert_allclose(out.grid, np.einsum("k,knij->nij", w, b.grids))
    fr = run_backtest(df, BacktestSpec(FrequencyBaseline, "E0", ["2324"]))
    assert headline(led)["1X2"]["rps"] < headline(fr)["1X2"]["rps"]


def test_per_division_weights_learn_only_from_their_division():
    df = synthetic_league(seed=10, seasons=("2223", "2324"), teams=10)
    other = synthetic_league(seed=11, seasons=("2223", "2324"), teams=10)
    other["division"] = "E1"
    other["id"] += 10_000
    other["home_team"] = other.home_team.str.replace("eng", "two")
    other["away_team"] = other.away_team.str.replace("eng", "two")
    both = pd.concat([df, other]).sort_values(["date", "id"]).reset_index(drop=True)
    m = _mixture(eta=0.3, per_division=True)
    run_backtest(both, BacktestSpec(lambda: m, "E0", ["2324"]))
    assert set(m.w) == {"E0", "E1"}
    assert not np.allclose(m.w["E0"], m.w["E1"])
    pooled = _mixture(eta=0.3)
    run_backtest(both, BacktestSpec(lambda: pooled, "E0", ["2324"]))
    assert set(pooled.w) == {"all"}


def test_market_mixture_ignores_later_results_and_prices():
    """SYNTHETIC prices: scrambling every result and price from a split date on leaves every
    earlier forecast identical, and the prices are really used."""
    df = _league_with_deadlines()
    raw = _synthetic_prices(df, seed=1)
    split = df[df.season == "2324"].cutoff_date.min() + pd.Timedelta(days=60)
    later = df.date >= split
    scrambled = df.copy()
    rng = np.random.default_rng(5)
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
                lambda: MarketOnlineMixture(
                    "ml_online_test", ["dc", "flat"], 0.3, prices=lambda: book, factories=FACTORIES
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


def test_live_interface_only_on_market_mixtures():
    assert not hasattr(_mixture(eta=0.1), "decision_time")
    m = MarketOnlineMixture(
        "ml_online_test", ["dc"], 0.1, prices=lambda: consensus(pd.DataFrame()), factories=FACTORIES
    )
    m.decision_time = pd.Timestamp("2026-10-23 17:00", tz="UTC")
    assert m.stream.tilter.decision_time == m.decision_time
    assert m.params()["books"]


def test_registered_candidates_are_prefixed_few_and_name_registered_members():
    assert 0 < len(CANDIDATES) <= 3
    registry = candidates()
    for name, cfg in CONFIGS.items():
        assert name.startswith("ml_online_")
        assert all(
            m in registry and not m.startswith(("ml_online_", "ml_stack_")) for m in cfg["members"]
        )
        model = CANDIDATES[name]()
        assert model.name == name
        assert hasattr(model, "decision_time") == bool(cfg.get("market"))
