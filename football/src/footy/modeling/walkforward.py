"""Walk-forward backtesting in decision-time batches, with leakage guards built in.

Fixtures are grouped by their early-edition cutoff (00:00 UK on the collection day, D-012),
which yields about two batches a week. For each batch the model is refitted on matches final
before that cutoff, then predicts the batch. The runner itself enforces the guards: training
data never includes a match on or after the cutoff (so never the target match, nor any later
result), and predict() never receives result columns.
"""

from collections.abc import Callable
from dataclasses import dataclass

import numpy as np
import pandas as pd

from footy.modeling.base import Model
from footy.modeling.data import history_before
from footy.modeling.grid import TOTAL_LINES, MarketProbs, check_coherent, tail_mass
from footy.modeling.metrics import (
    brier_binary,
    brier_multi,
    logloss_binary,
    logloss_multi,
    pick_1x2,
    rps,
)

RESULT_COLUMNS = [
    "home_goals",
    "away_goals",
    "result",
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
    "total_goals",
    "outcome",
]
SPLITS = {
    "burn_in": ["1819", "1920"],
    "train": ["2021", "2122", "2223"],
    "validation": ["2324"],
    "test": ["2425", "2526"],
    "live_retro": ["2627"],  # 2026/27 played before go-live: retrospective only
}


class LeakageError(AssertionError):
    pass


@dataclass
class BacktestSpec:
    model_factory: Callable[[], Model]
    league: str
    seasons: list[str]
    min_history: int = 3  # eligibility: matches per team before the cutoff (any division)
    history_days: int = 3 * 365  # matches older than this before the cutoff are not passed


def run_backtest(df: pd.DataFrame, spec: BacktestSpec) -> pd.DataFrame:
    """Return the per-fixture ledger for one model, league and set of seasons."""
    targets = df[(df.division == spec.league) & df.season.isin(spec.seasons)]
    rows: list[dict] = []
    model = spec.model_factory()
    for cutoff_utc, batch in targets.groupby("cutoff_utc", sort=True):
        cutoff_date = batch["cutoff_date"].iloc[0]
        hist = history_before(df, cutoff_date)
        hist = hist[hist["date"] >= cutoff_date - pd.Timedelta(days=spec.history_days)]
        _guard(hist, batch, cutoff_date)

        counts = pd.concat([hist.home_team, hist.away_team]).value_counts()
        excluded = batch[batch.model_exclusion.notna()]
        live = batch[batch.model_exclusion.isna()]
        n_home = live.home_team.map(counts).fillna(0).astype(int)
        n_away = live.away_team.map(counts).fillna(0).astype(int)
        eligible = (n_home >= spec.min_history) & (n_away >= spec.min_history)

        for _, r in excluded.iterrows():
            rows.append(
                _base_row(r, cutoff_utc, hist) | {"status": "excluded", "reason": r.model_exclusion}
            )
        for (_, r), nh, na in zip(
            live[~eligible].iterrows(), n_home[~eligible], n_away[~eligible], strict=True
        ):
            rows.append(
                _base_row(r, cutoff_utc, hist)
                | {"status": "ineligible", "reason": f"history home={nh} away={na}"}
            )
        fx = live[eligible]
        if fx.empty:
            continue
        model.fit(hist, cutoff_utc)  # refit every batch; models may warm-start internally
        mp = model.predict(fx.drop(columns=RESULT_COLUMNS))
        problems = check_coherent(mp)
        if problems:
            raise ValueError(
                f"{getattr(model, 'name', model)} incoherent at {cutoff_utc}: {problems}"
            )
        rows.extend(_scored_rows(fx, mp, cutoff_utc, hist, n_home[eligible], n_away[eligible]))
    ledger = pd.DataFrame(rows)
    ledger.insert(0, "model", getattr(model, "name", type(model).__name__))
    ledger.insert(1, "league", spec.league)
    return ledger


def _guard(hist: pd.DataFrame, batch: pd.DataFrame, cutoff_date: pd.Timestamp) -> None:
    if not hist.empty and hist["date"].max() >= cutoff_date:
        raise LeakageError(f"training data reaches {hist['date'].max()} >= cutoff {cutoff_date}")
    if hist["id"].isin(batch["id"]).any():
        raise LeakageError("a target match is in its own training data")
    if hist["model_exclusion"].notna().any():
        raise LeakageError("excluded matches in training data")


def _base_row(r, cutoff_utc, hist) -> dict:
    return {
        "match_id": int(r.id),
        "season": r.season,
        "date": r.date.date().isoformat(),
        "kickoff_utc": None if pd.isna(r.kickoff_utc) else r.kickoff_utc.isoformat(),
        "home_team": r.home_team,
        "away_team": r.away_team,
        "batch_cutoff_utc": cutoff_utc.isoformat(),
        "history_n": len(hist),
        "history_max_date": None if hist.empty else hist["date"].max().date().isoformat(),
        "home_goals": int(r.home_goals),
        "away_goals": int(r.away_goals),
    }


def _scored_rows(fx, mp: MarketProbs, cutoff_utc, hist, n_home, n_away) -> list[dict]:
    hg, ag = fx.home_goals.to_numpy(), fx.away_goals.to_numpy()
    outcome = fx.outcome.to_numpy()
    total = hg + ag
    cols: dict[str, np.ndarray] = {}
    if mp.p1x2 is not None:
        p = mp.p1x2
        cols |= {"p_H": p[:, 0], "p_D": p[:, 1], "p_A": p[:, 2]}
        cols["rps"] = rps(p, outcome)
        cols["ll_1x2"] = logloss_multi(p, outcome)
        cols["brier_1x2"] = brier_multi(p, outcome)
        cols["pick_1x2"] = np.array(["H", "D", "A"])[pick_1x2(p)]
        cols["correct_1x2"] = (pick_1x2(p) == outcome).astype(int)
    for line in TOTAL_LINES:
        if line not in mp.p_over:
            continue
        p, y, k = mp.p_over[line], (total > line).astype(int), f"{line:g}"
        cols[f"p_over_{k}"] = p
        cols[f"ll_ou_{k}"] = logloss_binary(p, y)
        cols[f"brier_ou_{k}"] = brier_binary(p, y)
        cols[f"pick_ou_{k}"] = np.where(p >= 0.5, "O", "U")
        cols[f"correct_ou_{k}"] = ((p >= 0.5) == (y == 1)).astype(int)
    if mp.p_btts is not None:
        p, y = mp.p_btts, ((hg > 0) & (ag > 0)).astype(int)
        cols["p_btts"] = p
        cols["ll_btts"] = logloss_binary(p, y)
        cols["brier_btts"] = brier_binary(p, y)
        cols["pick_btts"] = np.where(p >= 0.5, "Y", "N")
        cols["correct_btts"] = ((p >= 0.5) == (y == 1)).astype(int)
    if mp.lam_h is not None:
        cols["lam_h"], cols["lam_a"] = mp.lam_h, mp.lam_a
    if mp.grid is not None:
        cols["tail_mass"] = tail_mass(mp.grid)
    rows = []
    for j, ((_, r), nh, na) in enumerate(zip(fx.iterrows(), n_home, n_away, strict=True)):
        row = _base_row(r, cutoff_utc, hist) | {
            "status": "predicted",
            "reason": None,
            "n_hist_home": int(nh),
            "n_hist_away": int(na),
        }
        for k, v in cols.items():
            x = v[j]
            row[k] = x.item() if hasattr(x, "item") else x
        rows.append(row)
    return rows
