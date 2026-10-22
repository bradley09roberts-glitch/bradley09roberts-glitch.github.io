"""The out-of-sample member stream and the six-market tilt shared by the ml_stack and
ml_online families, on SYNTHETIC matches and SYNTHETIC prices only."""

import numpy as np
import pandas as pd
import pytest

from footy.modeling.dixon_coles import DixonColes
from footy.modeling.grid import check_coherent, markets_from_grid, poisson_grid
from footy.modeling.lab.market_blend import consensus, tilt
from footy.modeling.lab.ml_stream import MemberStream, shape_probs, tilt_markets
from footy.modeling.walkforward import RESULT_COLUMNS, LeakageError, training_history

from .test_no_future_leakage import _league_with_deadlines, _rows, _synthetic_prices
from .test_walkforward import synthetic_league


def _grids(n=200, seed=0):
    rng = np.random.default_rng(seed)
    return poisson_grid(
        rng.uniform(0.5, 2.5, n), rng.uniform(0.4, 2.0, n), rng.uniform(-0.1, 0.05, n)
    )


# ---- tilt_markets -------------------------------------------------------------------------


def test_six_market_tilt_hits_every_target_and_stays_a_distribution():
    g = _grids()
    rng = np.random.default_rng(1)
    t = np.clip(shape_probs(g) + rng.normal(0, 0.02, (len(g), 6)), 0.02, 0.98)
    out, ok = tilt_markets(g, t)
    assert ok.mean() > 0.98
    np.testing.assert_allclose(shape_probs(out)[ok], t[ok], atol=1e-9)
    assert not check_coherent(markets_from_grid(out))


def test_six_market_tilt_to_own_probabilities_changes_nothing():
    g = _grids(20)
    out, ok = tilt_markets(g, shape_probs(g))
    assert ok.all()
    np.testing.assert_allclose(out, g, atol=1e-12)


def test_six_market_tilt_is_market_blends_tilt_when_three_targets_move():
    """Moving home, draw and over 2.5 only (the other three at the result's own values) is
    MarketTilt's exponential tilt: the same minimum-information projection."""
    g = _grids(50, seed=2)
    p = shape_probs(g)
    a = tilt(g, p[:, [0, 1, 3]] + np.array([0.03, -0.02, 0.04]))
    b, ok = tilt_markets(g, shape_probs(a))
    assert ok.all()
    np.testing.assert_allclose(a, b, atol=1e-8)


def test_unreachable_targets_keep_the_base_grid():
    g = _grids(10, seed=3)
    t = shape_probs(g)
    t[:, 5] = np.minimum(t[:, 2] + 0.05, 0.99)  # both teams score more often than 2+ goals
    t[0] = np.nan
    out, ok = tilt_markets(g, t)
    assert not ok.any()
    np.testing.assert_array_equal(out, g)


# ---- the stream ---------------------------------------------------------------------------


class Spy(DixonColes):
    """Dixon-Coles that records, at every fit, the cutoff and the newest history date, and
    at every predict, which match ids it was asked about and when it was last fitted."""

    def __init__(self, log):
        super().__init__(name="spy")
        self.log = log
        self._last = None

    def fit(self, history, cutoff):
        self._last = (pd.Timestamp(cutoff), history["date"].max())
        super().fit(history, cutoff)

    def predict(self, fixtures):
        assert not set(RESULT_COLUMNS) & set(fixtures.columns), "results reached predict()"
        for i, d in zip(fixtures.get("id", []), fixtures.date, strict=False):
            self.log.append((int(i), pd.Timestamp(d), *self._last))
        return super().predict(fixtures)


def _drive(df, stream, seasons=("2324",)):
    """Feed `stream` as the runner feeds a model: one update per decision batch."""
    targets = df[df.season.isin(seasons)]
    for c, batch in targets.groupby("cutoff_utc", sort=True):
        stream.update(training_history(df, batch.cutoff_date.iloc[0]), c)
    return stream


def test_every_record_was_forecast_before_its_own_decision_day():
    df = synthetic_league(seed=5, seasons=("2122", "2223", "2324"), teams=10)
    log: list = []
    s = _drive(df, MemberStream(["spy"], factories={"spy": lambda: Spy(log)}, warmup_days=400))
    rec = s.records()
    assert len(rec) > 150  # the 2022/23 warm-up and 2023/24 so far
    by_id = df.set_index("id")
    seen = {}
    for mid, day, cutoff, hist_max in log:
        seen.setdefault(mid, (day, cutoff, hist_max))
    for mid in rec.ids:
        day, cutoff, hist_max = seen[int(mid)]
        own = by_id.loc[mid, "cutoff_date"]
        assert hist_max < own <= day  # fitted only on matches before the match's own day
        assert cutoff == by_id.loc[mid, "cutoff_utc"]  # ...at the match's own cutoff
    # the warm-up starts warmup_days before the first decision and nothing is recorded twice
    first = df[df.season == "2324"].cutoff_date.min()
    assert pd.Timestamp(rec.date.min()) >= first - pd.Timedelta(days=400)
    assert len(set(rec.ids)) == len(rec.ids)
    np.testing.assert_array_equal(
        rec.goals, by_id.loc[rec.ids, ["home_goals", "away_goals"]].to_numpy(float)
    )


def test_records_ignore_later_results():
    df = synthetic_league(seed=6, seasons=("2223", "2324"), teams=10)
    split = df[df.season == "2324"].cutoff_date.min() + pd.Timedelta(days=50)
    later = df.date >= split
    scrambled = df.copy()
    rng = np.random.default_rng(1)
    scrambled.loc[later, "home_goals"] = rng.integers(0, 6, later.sum())
    scrambled.loc[later, "away_goals"] = rng.integers(0, 6, later.sum())

    def run(frame):
        return _drive(frame, MemberStream(["dc"], factories={"dc": DixonColes})).records()

    a, b = run(df), run(scrambled)
    early = pd.to_datetime(a.date) < split
    np.testing.assert_array_equal(a.ids, b.ids)
    np.testing.assert_allclose(a.raw[early], b.raw[early])
    assert np.abs(a.raw[~early] - b.raw[~early]).max() > 1e-4


def test_history_reaching_the_cutoff_day_is_refused():
    df = synthetic_league(seed=7, seasons=("2223", "2324"), teams=8)
    batch = df[df.season == "2324"]
    c = batch.cutoff_utc.iloc[0]
    s = MemberStream(["dc"], factories={"dc": DixonColes})
    with pytest.raises(LeakageError):
        s.update(df[df.date <= batch.date.iloc[0]], c)


def test_market_records_use_only_prices_quoted_by_each_deadline():
    df = _league_with_deadlines()
    raw = _synthetic_prices(df, seed=4)
    first = df[df.season == "2324"].cutoff_utc.min()
    late_id = df[(df.season == "2324") & (df.cutoff_utc == first)].id.iloc[0]
    edited = raw.copy()
    rows = _rows(edited, df, [late_id])
    edited.loc[rows, "quote_time_utc"] += pd.Timedelta(minutes=1)  # quoted after the deadline

    def run(prices):
        book = consensus(prices)
        s = MemberStream(["dc"], market=True, prices=lambda: book, factories={"dc": DixonColes})
        return _drive(df, s).records()

    a, b = run(raw), run(edited)
    j = int(np.flatnonzero(a.ids == late_id)[0])
    assert np.isfinite(a.market[j]).all() and np.isnan(b.market[j]).all()
    np.testing.assert_allclose(b.tilted[j, :, :7], b.raw[j, :, :7])  # no price: own grid
    others = a.ids != late_id
    np.testing.assert_allclose(a.tilted[others], b.tilted[others])
    # where prices were used, every tilted member's 1X2 and O/U 2.5 are the consensus
    used = np.isfinite(a.market).all(axis=1)
    np.testing.assert_allclose(a.tilted[used, 0, :3], a.market[used, :3], atol=1e-9)
    np.testing.assert_allclose(a.tilted[used, 0, 4], a.market[used, 3], atol=1e-9)


def test_features_are_those_of_the_decision_time():
    """A record's engineered features equal ml_gbm's scan of the history before its own
    decision day, whatever happened later."""
    df = synthetic_league(seed=8, seasons=("2223", "2324"), teams=10)
    s = _drive(df, MemberStream(["dc"], features=True, factories={"dc": DixonColes}))
    rec = s.records()
    from footy.modeling.lab.ml_gbm import FeatureScan

    j = len(rec) // 2
    mid = rec.ids[j]
    row = df[df.id == mid]
    scan = FeatureScan()
    scan.extend(df[df.date < row.cutoff_date.iloc[0]].sort_values(["date", "id"]))
    xh, xa = scan.fixture_features(row)
    np.testing.assert_allclose(rec.xh[j], xh[0])
    np.testing.assert_allclose(rec.xa[j], xa[0])
