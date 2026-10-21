"""Run named models over leagues and seasons in parallel, write ledgers, compare.

Model selection (DECISIONS.md D-025): a single composite of the brief's primary metrics,
pooled over the five leagues on identical fixtures: the mean, over 1X2 RPS and the log losses
of O/U 1.5, 2.5, 3.5 and BTTS, of (model metric ÷ frequency-baseline metric). Lower is
better. Accuracy is always reported alongside.

Worker processes run with one BLAS / OpenMP thread each (`limit_threads`): with several
workers, numpy's own thread pool only makes them compete for the same cores.
"""

import ctypes
import json
import os
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

# The divisions a league's backtest loads: the league itself, then its partner division in
# the same country (the other tier), whose matches rate the clubs that move between them.
# Kept here rather than in data.SECOND_TIER, which other modules use to build their tier
# sets (E1 must stay a second tier there).
LEAGUE_DIVISIONS: dict[str, tuple[str, ...]] = {
    **{top: (top, second) for top, second in SECOND_TIER.items()},
    "E1": ("E1", "E0"),
}

THREAD_ENV = ("OPENBLAS_NUM_THREADS", "OMP_NUM_THREADS")
# Setters exported by the OpenBLAS builds numpy and scipy bundle (64-bit-integer and plain
# interfaces), then by a stock OpenBLAS.
_OPENBLAS_SETTERS = (
    "scipy_openblas_set_num_threads64_",
    "scipy_openblas_set_num_threads",
    "openblas_set_num_threads64_",
    "openblas_set_num_threads",
)


def divisions_for(league: str, extra_divisions: tuple[str, ...] = ()) -> list[str]:
    """The divisions loaded for `league`'s backtest (targets are `league` only)."""
    if league not in LEAGUE_DIVISIONS:
        known = ", ".join(sorted(LEAGUE_DIVISIONS))
        raise ValueError(f"no division map for league {league!r} (known: {known})")
    return list(dict.fromkeys([*LEAGUE_DIVISIONS[league], *extra_divisions]))


def loaded_openblas() -> list[ctypes.CDLL]:
    """OpenBLAS libraries already loaded into this process (Linux; empty elsewhere)."""
    try:
        with open("/proc/self/maps", encoding="utf-8") as f:
            paths = {line.split()[-1] for line in f if "openblas" in line.lower()}
    except OSError:
        return []
    return [ctypes.CDLL(p) for p in sorted(paths) if p.startswith("/")]


def limit_threads() -> int:
    """Worker initializer: one BLAS / OpenMP thread per process, before any numpy work.

    The environment variables cover libraries loaded later in the worker. They can't reach an
    OpenBLAS that is already loaded: it reads them once, at load, and a forked worker inherits
    the parent's (numpy is imported before the pool starts). So that library is also set to
    one thread directly. Returns how many loaded OpenBLAS libraries were set."""
    for var in THREAD_ENV:
        os.environ[var] = "1"
    done = 0
    for lib in loaded_openblas():
        for name in _OPENBLAS_SETTERS:
            setter = getattr(lib, name, None)
            if setter is not None:
                setter(1)
                done += 1
                break
    return done


def _one(args) -> pd.DataFrame:
    model_name, league, seasons, extra_divisions = args
    from footy.db import make_engine

    engine = make_engine(get_settings().database_url)
    df = load_matches(engine, divisions_for(league, extra_divisions))
    factory = candidates()[model_name]
    return run_backtest(df, BacktestSpec(factory, league, seasons))


def run_models(
    model_names: list[str],
    seasons: list[str],
    leagues: list[str] = LEAGUES,
    workers: int = 4,
    extra_divisions: tuple[str, ...] = (),
) -> dict[str, pd.DataFrame]:
    for lg in leagues:
        divisions_for(lg)  # fail before starting workers
    jobs = [(m, lg, seasons, extra_divisions) for m in model_names for lg in leagues]
    with ProcessPoolExecutor(max_workers=workers, initializer=limit_threads) as ex:
        results = list(ex.map(_one, jobs))
    out: dict[str, list] = {}
    for (m, *_), led in zip(jobs, results, strict=True):
        out.setdefault(m, []).append(led)
    return {m: pd.concat(parts, ignore_index=True) for m, parts in out.items()}


def run_id_for(model_name: str, split: str, seasons: list[str], leagues: list[str]) -> str:
    """`<model>__<split>__<seasons>`, plus `__<leagues>` unless the leagues are exactly the
    five of LEAGUES, so five-league runs keep their paths and other runs don't overwrite them."""
    run_id = f"{model_name}__{split}__{'-'.join(seasons)}"
    if sorted(leagues) != sorted(LEAGUES):
        run_id += f"__{'-'.join(sorted(leagues))}"
    return run_id


def save(model_name: str, ledger: pd.DataFrame, split: str, root: Path | None = None) -> Path:
    from footy.db import get_engine

    root = root or get_settings().data_dir / "backtests"
    seasons = sorted(ledger.season.unique())
    run_id = run_id_for(model_name, split, seasons, sorted(ledger.league.unique()))
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
        "selection_rule": "D-025 composite on validation 2023/24",
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
