"""Leakage guards and ledger reproducibility, on SYNTHETIC matches."""

import numpy as np
import pandas as pd
import pytest

from footy.ingest.fd_parse import model_cutoff
from footy.modeling.baselines import EloBaseline, FrequencyBaseline
from footy.modeling.dixon_coles import DixonColes
from footy.modeling.grid import MarketProbs, markets_from_grid, poisson_grid
from footy.modeling.ledger import headline, recompute_from_files, write_run
from footy.modeling.walkforward import RESULT_COLUMNS, BacktestSpec, LeakageError, run_backtest


def synthetic_league(seed=1, seasons=("2223", "2324"), teams=10):
    """Double round robins with random Poisson scores; Saturdays from August."""
    rng = np.random.default_rng(seed)
    names = [f"eng-team{i}" for i in range(teams)]
    strength = dict(zip(names, rng.normal(0, 0.3, teams), strict=True))
    rows, mid = [], 1
    for s in seasons:
        day = pd.Timestamp(f"20{s[:2]}-08-06")  # a Saturday
        pairs = [(h, a) for h in names for a in names if h != a]
        rng.shuffle(pairs)
        for k in range(0, len(pairs), teams // 2):
            for h, a in pairs[k : k + teams // 2]:
                lh = np.exp(0.25 + strength[h] - strength[a])
                la = np.exp(strength[a] - strength[h])
                rows.append((mid, "E0", s, day, h, a, rng.poisson(lh), rng.poisson(la)))
                mid += 1
            day += pd.Timedelta(days=7)
    df = pd.DataFrame(
        rows,
        columns=[
            "id",
            "division",
            "season",
            "date",
            "home_team",
            "away_team",
            "home_goals",
            "away_goals",
        ],
    )
    df["kickoff_utc"] = pd.NaT
    df["model_exclusion"] = None
    for c in [
        "ht_home_goals",
        "ht_away_goals",
        "home_shots",
        "away_shots",
        "home_sot",
        "away_sot",
        "home_corners",
        "away_corners",
        "home_red",
        "away_red",
    ]:
        df[c] = np.nan
    df["result"] = np.where(
        df.home_goals > df.away_goals, "H", np.where(df.home_goals == df.away_goals, "D", "A")
    )
    df["outcome"] = np.where(
        df.home_goals > df.away_goals, 0, np.where(df.home_goals == df.away_goals, 1, 2)
    )
    df["total_goals"] = df.home_goals + df.away_goals
    df["cutoff_utc"] = pd.to_datetime([model_cutoff(d.date()) for d in df.date], utc=True)
    df["cutoff_date"] = (
        df.cutoff_utc.dt.tz_convert("Europe/London").dt.tz_localize(None).dt.normalize()
    )
    return df


class Spy:
    """Records what the runner hands to fit() and predict()."""

    name = "spy"
    markets = frozenset({"1X2"})

    def __init__(self):
        self.calls = []

    def params(self):
        return {}

    def fit(self, history, cutoff):
        self.calls.append(("fit", history["date"].max(), cutoff, set(history.id)))

    def predict(self, fixtures):
        self.calls.append(("predict", set(fixtures.columns), set(fixtures.id)))
        return MarketProbs(p1x2=np.full((len(fixtures), 3), 1 / 3))


def test_training_never_reaches_the_cutoff_and_predict_sees_no_results():
    df = synthetic_league()
    spy = Spy()
    run_backtest(df, BacktestSpec(lambda: spy, "E0", ["2324"]))
    fits = [c for c in spy.calls if c[0] == "fit"]
    preds = [c for c in spy.calls if c[0] == "predict"]
    assert fits and len(fits) == len(preds)
    for (_, max_date, cutoff, ids), (_, cols, target_ids) in zip(fits, preds, strict=True):
        cutoff_day = cutoff.tz_convert("Europe/London").tz_localize(None).normalize()
        assert max_date < cutoff_day
        assert not ids & target_ids
        assert not set(RESULT_COLUMNS) & cols


def test_guard_trips_if_a_future_match_slips_into_history(monkeypatch):
    df = synthetic_league()
    import footy.modeling.walkforward as wf

    real = wf.history_before
    monkeypatch.setattr(wf, "history_before", lambda d, c: d)  # a buggy history filter
    with pytest.raises(LeakageError):
        run_backtest(df, BacktestSpec(FrequencyBaseline, "E0", ["2324"]))
    monkeypatch.setattr(wf, "history_before", real)


def test_excluded_matches_are_ledgered_not_scored_or_trained_on():
    df = synthetic_league()
    df.loc[df.index[-1], "model_exclusion"] = "result_without_stats"
    led = run_backtest(df, BacktestSpec(FrequencyBaseline, "E0", ["2324"]))
    assert (led.status == "excluded").sum() == 1
    assert led.loc[led.status == "excluded", "reason"].iloc[0] == "result_without_stats"


def test_ledger_files_reproduce_the_headline(tmp_path):
    df = synthetic_league()
    led = run_backtest(df, BacktestSpec(lambda: DixonColes(xi=0.002), "E0", ["2324"]))
    out = write_run(tmp_path / "run", led, {"model": "dc"})
    import json

    saved = json.loads((out / "manifest.json").read_text())["headline"]
    again = json.loads(json.dumps(recompute_from_files(out)))
    assert saved == again  # exact, not approximate


def test_same_inputs_same_outputs():
    df = synthetic_league()
    a = run_backtest(df, BacktestSpec(lambda: DixonColes(), "E0", ["2324"]))
    b = run_backtest(df, BacktestSpec(lambda: DixonColes(), "E0", ["2324"]))
    pd.testing.assert_frame_equal(a, b)


def test_dixon_coles_recovers_strength_order_and_beats_frequency():
    df = synthetic_league(seed=3, seasons=("2122", "2223", "2324"), teams=12)
    dc = headline(run_backtest(df, BacktestSpec(lambda: DixonColes(xi=0.001), "E0", ["2324"])))
    fr = headline(run_backtest(df, BacktestSpec(FrequencyBaseline, "E0", ["2324"])))
    assert dc["1X2"]["rps"] < fr["1X2"]["rps"]


def test_elo_probabilities_valid():
    df = synthetic_league()
    led = run_backtest(df, BacktestSpec(EloBaseline, "E0", ["2324"]))
    p = led[["p_H", "p_D", "p_A"]].to_numpy()
    assert np.allclose(p.sum(axis=1), 1) and (p > 0).all()


def test_dc_grid_coherent():
    mp = markets_from_grid(poisson_grid([1.5, 0.9], [1.2, 2.1], -0.08))
    assert np.allclose(mp.p1x2.sum(axis=1), 1)
