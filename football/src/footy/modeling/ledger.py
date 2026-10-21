"""Ledgers and manifests: every headline number is recomputed from the exported ledger."""

import hashlib
import json
import subprocess
from datetime import UTC, datetime
from pathlib import Path

import numpy as np
import pandas as pd
from sqlalchemy import Engine, text

from footy.config import PROJECT_ROOT
from footy.ingest.fd_parse import PARSER_VERSION
from footy.modeling.grid import TOTAL_LINES
from footy.modeling.metrics import block_bootstrap_ci

PRIMARY = {"1X2": "rps", **{f"OU_{k:g}": f"ll_ou_{k:g}" for k in TOTAL_LINES}, "BTTS": "ll_btts"}


def headline(ledger: pd.DataFrame, with_ci: bool = False) -> dict:
    """Headline metrics computed only from ledger rows with status == "predicted"."""
    pred = ledger[ledger.status == "predicted"]
    out = {
        "fixtures_total": int(len(ledger)),
        "predicted": int(len(pred)),
        "excluded": int((ledger.status == "excluded").sum()),
        "ineligible": int((ledger.status == "ineligible").sum()),
    }
    metrics = {
        "1X2": ["rps", "ll_1x2", "brier_1x2", "correct_1x2"],
        "BTTS": ["ll_btts", "brier_btts", "correct_btts"],
        **{
            f"OU_{k:g}": [f"ll_ou_{k:g}", f"brier_ou_{k:g}", f"correct_ou_{k:g}"]
            for k in TOTAL_LINES
        },
    }
    for market, cols in metrics.items():
        if cols[0] not in pred or pred[cols[0]].isna().all():
            continue
        m = {}
        for c in cols:
            key = (
                "rps"
                if c == "rps"
                else "log_loss"
                if c.startswith("ll")
                else "brier"
                if c.startswith("brier")
                else "accuracy"
            )
            m[key] = float(pred[c].astype(float).mean())
        m["n"] = int(pred[cols[0]].notna().sum())
        if with_ci:
            m["primary_ci95"] = block_bootstrap_ci(
                pred[PRIMARY[market]].to_numpy(float), pred.batch_cutoff_utc.to_numpy()
            )
        out[market] = m
    return out


def data_revision(engine: Engine) -> str:
    """Fingerprint of the raw football-data revisions in the database."""
    with engine.connect() as conn:
        rows = conn.execute(
            text(
                "SELECT logical_key, revision, sha256 FROM raw_files "
                "WHERE source = 'football-data' ORDER BY logical_key, revision"
            )
        ).all()
    h = hashlib.sha256()
    for r in rows:
        h.update(f"{r[0]}:{r[1]}:{r[2]};".encode())
    return h.hexdigest()[:16]


def git_commit() -> str | None:
    try:
        return subprocess.run(
            ["git", "rev-parse", "HEAD"],
            cwd=PROJECT_ROOT,
            capture_output=True,
            text=True,
            check=True,
        ).stdout.strip()
    except (OSError, subprocess.CalledProcessError):
        return None


def write_run(
    out_dir: Path,
    ledger: pd.DataFrame,
    manifest: dict,
) -> Path:
    """Write ledger.csv, ledger.json and manifest.json (with headline metrics) to out_dir."""
    out_dir.mkdir(parents=True, exist_ok=True)
    ledger.to_csv(out_dir / "ledger.csv", index=False, float_format="%.17g")  # exact round trip
    ledger.to_json(out_dir / "ledger.json", orient="records", indent=1)
    manifest = {
        **manifest,
        "created_at_utc": datetime.now(UTC).isoformat(timespec="seconds"),
        "git_commit": git_commit(),
        "parser_version": PARSER_VERSION,
        "headline": headline(ledger, with_ci=True),
    }
    (out_dir / "manifest.json").write_text(json.dumps(manifest, indent=2, default=_json))
    return out_dir


def recompute_from_files(out_dir: Path) -> dict:
    """Re-derive the headline from ledger.csv alone (the acceptance test for M2)."""
    return headline(pd.read_csv(out_dir / "ledger.csv", float_precision="round_trip"), with_ci=True)


def _json(x):
    if isinstance(x, np.generic):
        return x.item()
    if isinstance(x, (datetime, pd.Timestamp)):
        return x.isoformat()
    return str(x)
