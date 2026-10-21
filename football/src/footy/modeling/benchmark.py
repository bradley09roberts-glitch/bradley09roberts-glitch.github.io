"""Market benchmark gate: model vs de-vigged closing prices on identical fixtures.

For 1X2 (RPS) and O/U 2.5 (log loss), the difference model − market is computed per fixture
and given a block-bootstrap interval by decision batch. If the model is worse (positive
difference), every value-selection result must carry the brief's warning label.
"""

import numpy as np
import pandas as pd
from sqlalchemy import Engine

from footy.modeling.market import closing_market
from footy.modeling.metrics import block_bootstrap_ci, logloss_binary, rps

WARNING = "Model does not beat the closing market; positive ROI is not expected."


def benchmark(engine: Engine, ledger: pd.DataFrame) -> dict:
    pred = ledger[ledger.status == "predicted"]
    out: dict = {"fixtures_predicted": int(len(pred))}
    for method in ("shin", "power"):
        mk = closing_market(engine, pred.match_id.tolist(), method=method)
        j = pred.merge(mk, on="match_id", how="inner")
        res = {}
        for label in ("bench", "avg"):
            cols = [f"{label}_1X2_{s}" for s in "HDA"]
            ok = j[cols].notna().all(axis=1)
            x = j[ok]
            if len(x):
                outcome = np.where(
                    x.home_goals > x.away_goals, 0, np.where(x.home_goals == x.away_goals, 1, 2)
                )
                m_rps = rps(x[cols].to_numpy(float), outcome)
                d = x.rps.to_numpy(float) - m_rps
                lo, hi = block_bootstrap_ci(d, x.batch_cutoff_utc.to_numpy())
                res[f"{label}_1X2"] = {
                    "fixtures": int(len(x)),
                    "coverage_of_predicted": float(len(x) / max(len(pred), 1)),
                    "model_rps": float(x.rps.mean()),
                    "market_rps": float(m_rps.mean()),
                    "difference": float(d.mean()),
                    "difference_ci95": [lo, hi],
                    "sources": x["bench_1X2_source"].value_counts().to_dict()
                    if label == "bench"
                    else {"Avg": int(len(x))},
                }
            cols = [f"{label}_OU_2.5_{s}" for s in "OU"]
            if "ll_ou_2.5" in j:
                ok = j[cols].notna().all(axis=1) & j["ll_ou_2.5"].notna()
                x = j[ok]
                if len(x):
                    y = ((x.home_goals + x.away_goals) > 2.5).astype(int).to_numpy()
                    m_ll = logloss_binary(x[cols[0]].to_numpy(float), y)
                    d = x["ll_ou_2.5"].to_numpy(float) - m_ll
                    lo, hi = block_bootstrap_ci(d, x.batch_cutoff_utc.to_numpy())
                    res[f"{label}_OU_2.5"] = {
                        "fixtures": int(len(x)),
                        "coverage_of_predicted": float(len(x) / max(len(pred), 1)),
                        "model_log_loss": float(x["ll_ou_2.5"].mean()),
                        "market_log_loss": float(m_ll.mean()),
                        "difference": float(d.mean()),
                        "difference_ci95": [lo, hi],
                    }
        out[method] = res
    primary = out["shin"].get("bench_1X2")
    beats = primary is not None and primary["difference"] < 0
    out["gate"] = {
        "primary": "1X2 RPS vs de-vigged (Shin) closing benchmark "
        "(Pinnacle before 2025-07-23, Betfair Exchange after)",
        "model_beats_market": bool(beats),
        "significant": bool(primary is not None and primary["difference_ci95"][1] < 0),
        "label": None if beats else WARNING,
    }
    return out
