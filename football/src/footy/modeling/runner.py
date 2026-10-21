"""Run named models over leagues and seasons in parallel, write ledgers, compare.

Model selection (DECISIONS.md D-022): a single composite of the brief's primary metrics,
pooled over the five leagues on identical fixtures: the mean, over 1X2 RPS and the log losses
of O/U 1.5, 2.5, 3.5 and BTTS, of (model metric ÷ frequency-baseline metric). Lower is
better. Accuracy is always reported alongside.
"""

import json
from concurrent.futures import ProcessPoolExecutor
from pathlib import Path

import numpy as np
import pandas as pd

from footy.config import get_settings
from footy.modeling.data import SECOND_TIER, load_matches
from footy.modeling.ledger import data_revision, headline, write_run
from footy.modeling.metrics import block_bootstrap_ci
from footy.modeling.registry import candidates
from footy.modeling.walkforward import SPLITS, BacktestSpec, run_backtest

LEAGUES = ["E0", "SP1", "I1", "D1", "F1"]
COMPOSITE = ["rps", "ll_ou_1.5", "ll_ou_2.5", "ll_ou_3.5", "ll_btts"]


def _one(args) -> pd.DataFrame:
    model_name, league, seasons, extra_divisions = args
    from footy.db import make_engine

    engine = make_engine(get_settings().database_url)
    divisions = [league, SECOND_TIER[league], *extra_divisions]
    df = load_matches(engine, divisions)
    factory = candidates()[model_name]
    return run_backtest(df, BacktestSpec(factory, league, seasons))


def run_models(
    model_names: list[str],
    seasons: list[str],
    leagues: list[str] = LEAGUES,
    workers: int = 4,
    extra_divisions: tuple[str, ...] = (),
) -> dict[str, pd.DataFrame]:
    jobs = [(m, lg, seasons, extra_divisions) for m in model_names for lg in leagues]
    with ProcessPoolExecutor(max_workers=workers) as ex:
        results = list(ex.map(_one, jobs))
    out: dict[str, list] = {}
    for (m, *_), led in zip(jobs, results, strict=True):
        out.setdefault(m, []).append(led)
    return {m: pd.concat(parts, ignore_index=True) for m, parts in out.items()}


def save(model_name: str, ledger: pd.DataFrame, split: str, root: Path | None = None) -> Path:
    from footy.db import get_engine

    root = root or get_settings().data_dir / "backtests"
    seasons = sorted(ledger.season.unique())
    run_id = f"{model_name}__{split}__{'-'.join(seasons)}"
    model = candidates()[model_name]()
    manifest = {
        "run_id": run_id,
        "model": model_name,
        "model_params": model.params(),
        "split": split,
        "seasons": seasons,
        "leagues": sorted(ledger.league.unique()),
        "splits": SPLITS,
        "decision_time": "early edition: 00:00 UK on the football-data collection day (D-012)",
        "refit": "every decision batch",
        "data_revision": data_revision(get_engine()),
        "selection_rule": "D-022 composite on validation 2023/24",
    }
    return write_run(root / run_id, ledger, manifest)


def compare(ledgers: dict[str, pd.DataFrame], baseline: str = "baseline_frequency") -> pd.DataFrame:
    """Per model: primary metrics and accuracies on the fixtures every model predicted, and
    the composite relative to the baseline."""
    pred = {m: led[led.status == "predicted"].set_index("match_id") for m, led in ledgers.items()}
    common = sorted(set.intersection(*(set(p.index) for p in pred.values())))
    rows = []
    base = pred[baseline].loc[common]
    for m, p in pred.items():
        p = p.loc[common]
        row = {"model": m, "fixtures": len(common)}
        ratios = []
        for col in COMPOSITE:
            if col in p and p[col].notna().all():
                row[col] = float(p[col].mean())
                ratios.append(row[col] / float(base[col].mean()))
            else:
                row[col] = np.nan
        row["composite"] = float(np.mean(ratios)) if len(ratios) == len(COMPOSITE) else np.nan
        for acc in ["correct_1x2", "correct_ou_2.5", "correct_btts"]:
            row[acc.replace("correct", "acc")] = float(p[acc].mean()) if acc in p else np.nan
        lo, hi = (
            block_bootstrap_ci(p["rps"].to_numpy(float), p["batch_cutoff_utc"].to_numpy())
            if "rps" in p
            else (np.nan, np.nan)
        )
        row["rps_ci95"] = f"[{lo:.4f}, {hi:.4f}]"
        rows.append(row)
    return pd.DataFrame(rows).sort_values("composite").reset_index(drop=True)


def per_league(ledger: pd.DataFrame) -> dict:
    return {lg: headline(g) for lg, g in ledger.groupby("league")}


def to_json(df: pd.DataFrame) -> str:
    return json.dumps(df.replace({np.nan: None}).to_dict(orient="records"), indent=1)
