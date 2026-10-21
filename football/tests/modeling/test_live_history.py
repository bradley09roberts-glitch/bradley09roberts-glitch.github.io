"""Live domestic predictions fit on exactly the history the walk-forward backtest gives a model
at the same cutoff: results final before the cutoff day, not excluded from models, and no
older than HISTORY_DAYS. Otherwise the model in use is not the model that was validated and
tested (live used to pass every result since 2018/19; the backtest passes three years).
SYNTHETIC league; the database, schedule and publisher are stubbed out."""

from datetime import timedelta

import numpy as np
import pandas as pd

import footy.live as live
from footy.fixtures import ScheduledMatch
from footy.modeling.grid import markets_from_grid, poisson_grid
from footy.modeling.walkforward import HISTORY_DAYS, BacktestSpec, run_backtest

from .test_walkforward import synthetic_league


class Recorder:
    """Records the history ids and cutoff each fit() receives."""

    name = "recorder"
    markets = frozenset({"1X2"})

    def __init__(self, seen: list):
        self.seen = seen

    def params(self):
        return {}

    def fit(self, history, cutoff):
        self.seen.append((frozenset(history.id), pd.Timestamp(cutoff)))

    def predict(self, fixtures):
        n = len(fixtures)
        return markets_from_grid(poisson_grid(np.full(n, 1.4), np.full(n, 1.1), rho=-0.05))


def test_live_fits_on_the_backtests_history(monkeypatch):
    # five SYNTHETIC seasons, so the history reaches back more than HISTORY_DAYS
    df = synthetic_league(seed=3, seasons=("1920", "2021", "2122", "2223", "2324"))
    backtest: list = []
    run_backtest(df, BacktestSpec(lambda: Recorder(backtest), "E0", ["2324"]))
    ids, cutoff = backtest[5]  # a batch well into the season
    target = df[df.cutoff_utc == cutoff].iloc[0]
    day = target.cutoff_date
    assert (df.date < day - timedelta(days=HISTORY_DAYS)).any()  # the window really binds

    fx = [
        (
            ScheduledMatch(target.date.date(), None, "H", "A", None, None, 1),
            target.home_team,
            target.away_team,
        )
    ]
    monkeypatch.setattr(live, "load_matches", lambda engine, divs: df)
    monkeypatch.setattr(live, "upcoming", lambda *a, **k: (fx, {"checked_results": 0}))
    seen: list = []
    now = cutoff + pd.Timedelta(hours=1)  # an hour into the collection day (UK)
    out = live.predict_domestic(
        None, lambda: Recorder(seen), "recorder", ["E0"], now=now.to_pydatetime(), dry_run=True
    )
    assert len(out) == 1
    ((live_ids, live_cutoff),) = seen
    assert live_cutoff == cutoff
    assert live_ids == ids
