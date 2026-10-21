"""Validate and test pan-European model configurations on UEFA competitions.

Splits mirror the domestic ones: select on 2023/24 (validation), confirm once on 2024/25 and
2025/26 (test). 2026/27 matches already played are reported as retrospective only. All three
UEFA competitions (and their qualifiers) are scored; the Champions League is also reported on
its own. There are no historical prices for these matches, so no market benchmark exists.
"""

import json
from concurrent.futures import ProcessPoolExecutor

import numpy as np
import pandas as pd
from sqlalchemy import text

from footy.config import get_settings
from footy.modeling.euro import (
    EuroFrequency,
    EuroSpec,
    PanEuropeanDC,
    load_combined,
    run_euro_backtest,
)
from footy.modeling.metrics import block_bootstrap_ci

COMPS = ("UCL", "UEL", "UECL", "UCLQ", "UELQ", "UECLQ")
METRICS = ["rps", "ll_ou_1.5", "ll_ou_2.5", "ll_ou_3.5", "ll_btts"]


def team_countries(engine) -> dict[str, str]:
    with engine.connect() as conn:
        return dict(conn.execute(text("SELECT team_key, country FROM teams")).all())


def _run(args):
    config, seasons, comps = args
    from footy.db import make_engine

    engine = make_engine(get_settings().database_url)
    df = load_combined(engine)
    factory = (lambda: EuroFrequency()) if config is None else (lambda: PanEuropeanDC(**config))
    return run_euro_backtest(df, EuroSpec(factory, seasons, comps), team_countries(engine))


def evaluate(configs: list[dict | None], seasons: list[str], comps=COMPS, workers: int = 3) -> dict:
    with ProcessPoolExecutor(max_workers=workers) as ex:
        leds = list(ex.map(_run, [(c, seasons, comps) for c in configs]))
    return {led.model.iloc[0]: led for led in leds}


def summarise(leds: dict[str, pd.DataFrame], baseline: str = "euro_frequency") -> pd.DataFrame:
    pred = {m: led[led.status == "predicted"].set_index("match_id") for m, led in leds.items()}
    common = sorted(set.intersection(*(set(p.index) for p in pred.values())))
    base = pred[baseline].loc[common]
    rows = []
    for m, p in pred.items():
        p = p.loc[common]
        row = {"model": m, "fixtures": len(common)}
        ratios = []
        for c in METRICS:
            row[c] = float(p[c].mean())
            ratios.append(row[c] / float(base[c].mean()))
        row["composite"] = float(np.mean(ratios))
        ucl = p[p.comp == "UCL"]
        row["ucl_fixtures"] = len(ucl)
        row["ucl_rps"] = float(ucl.rps.mean()) if len(ucl) else np.nan
        row["ucl_acc_1x2"] = float(ucl.correct_1x2.mean()) if len(ucl) else np.nan
        row["acc_1x2"] = float(p.correct_1x2.mean())
        row["acc_ou_2.5"] = float(p["correct_ou_2.5"].mean())
        row["acc_btts"] = float(p.correct_btts.mean())
        lo, hi = block_bootstrap_ci(p.rps.to_numpy(float), p.batch_cutoff_utc.to_numpy())
        row["rps_ci95"] = f"[{lo:.4f}, {hi:.4f}]"
        rows.append(row)
    return pd.DataFrame(rows).sort_values("composite").reset_index(drop=True)


def save(leds: dict[str, pd.DataFrame], split: str) -> None:
    root = get_settings().data_dir / "backtests_euro"
    for m, led in leds.items():
        d = root / f"{m}__{split}"
        d.mkdir(parents=True, exist_ok=True)
        led.to_csv(d / "ledger.csv", index=False, float_format="%.17g")
        (d / "manifest.json").write_text(json.dumps({"model": m, "split": split}, indent=1))
