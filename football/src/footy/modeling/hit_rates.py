"""Hit rates: how often the pick was right, per league or competition and per market.

A pick is the most likely outcome: the highest of home, draw and away for the match result
(1X2, a draw can be the pick), the likelier side of an over/under line, yes or no for both
teams to score. Its hit rate is the share of fixtures where the pick happened.

Everything here is counted from saved ledgers, never re-predicted:

- **Test seasons** (2024/25 and 2025/26): the test run of each model of record (`SOURCES`,
  D-026/D-027), set against its frequency baseline's run on the same fixtures. The baseline
  picks whatever had been most common before each fixture (in practice the home win, over or
  under 2.5 by league, both teams to score), so its hit rate is what always picking the usual
  outcome scores. A line like over/under 1.5 or 3.5, where one side happens most of the time,
  gives the model and the baseline nearly the same high rate.
- **Genuine pre-match record**: settled live predictions, each fixture once with the first
  edition of its record of record (as /api/performance counts them). See `prematch_tables`.

Each hit rate carries a 95% Wilson score interval. Confidence buckets show the hit rate on the
fixtures where the model was surer: the match-result favourite at 50%, 60% or 70% or more, and
over/under 2.5 and both teams to score at 60% or more either way.
"""

from __future__ import annotations

import json
import math
from collections.abc import Iterable, Sequence
from dataclasses import dataclass
from pathlib import Path

import numpy as np
import pandas as pd

from footy.reference import DIVISIONS

# Market key (as the API names markets) -> the ledger's 0/1 column for "the pick was right".
MARKETS: dict[str, str] = {
    "1X2": "correct_1x2",
    "OU_1.5": "correct_ou_1.5",
    "OU_2.5": "correct_ou_2.5",
    "OU_3.5": "correct_ou_3.5",
    "BTTS": "correct_btts",
}
MARKET_LABELS = {
    "1X2": "Match result (1X2)",
    "OU_1.5": "Over/under 1.5 goals",
    "OU_2.5": "Over/under 2.5 goals",
    "OU_3.5": "Over/under 3.5 goals",
    "BTTS": "Both teams to score",
}
# Confidence buckets: market -> (probability columns, thresholds). The match result uses the
# favourite's probability; a two-way market uses the likelier side's (max of p and 1 - p).
RESULT_THRESHOLDS = (0.5, 0.6, 0.7)
TWO_WAY_THRESHOLDS = (0.6,)
CONFIDENCE: dict[str, tuple[tuple[str, ...], tuple[float, ...]]] = {
    "1X2": (("p_H", "p_D", "p_A"), RESULT_THRESHOLDS),
    "OU_2.5": (("p_over_2.5",), TWO_WAY_THRESHOLDS),
    "BTTS": (("p_btts",), TWO_WAY_THRESHOLDS),
}

ALL = "ALL"
FAMILY_NAMES = {"domestic": "League games", "uefa": "UEFA competitions"}
UEFA_NAMES = {
    "UCL": "Champions League",
    "UEL": "Europa League",
    "UECL": "Conference League",
    "UCLQ": "Champions League qualifying",
    "UELQ": "Europa League qualifying",
    "UECLQ": "Conference League qualifying",
}
# Row order within a family; anything else follows, alphabetically.
GROUP_ORDER = {
    "domestic": ("E0", "SP1", "I1", "D1", "F1"),
    "uefa": ("UCL", "UEL", "UECL", "UCLQ", "UELQ", "UECLQ"),
}
MODEL_AND_MARKET = "model_and_market"
MODEL_ONLY = "model_only"
Z95 = 1.959963984540054


@dataclass(frozen=True)
class Source:
    """One model of record's test run and the frequency baseline's run on the same fixtures."""

    family: str  # "domestic" or "uefa" (the groups of reference/primary_models.json)
    model: str
    basis: str  # MODEL_AND_MARKET or MODEL_ONLY
    run_id: str
    baseline_model: str
    baseline_run_id: str
    root: str  # directory under the data dir: "backtests" or "backtests_euro"
    group_column: str  # "league" in domestic ledgers, "comp" in UEFA ones


# The models of record (D-026, D-027) and the test runs that confirmed them. Each must be a
# model of the latest period in reference/primary_models.json (a test checks); dixon_coles,
# listed last there for fixtures without a newer record, is left out.
SOURCES: tuple[Source, ...] = (
    Source(
        "domestic",
        "market_tilt_shots",
        MODEL_AND_MARKET,
        "market_tilt_shots__test__2425-2526",
        "baseline_frequency",
        "baseline_frequency__test__2425-2526",
        "backtests",
        "league",
    ),
    Source(
        "domestic",
        "congestion_context_on_elo",
        MODEL_ONLY,
        "congestion_context_on_elo__test__2425-2526",
        "baseline_frequency",
        "baseline_frequency__test__2425-2526",
        "backtests",
        "league",
    ),
    Source(
        "uefa",
        "pan_euro_mix_mxg",
        MODEL_ONLY,
        "pan_euro_mix_mxg__test_round3",
        "euro_frequency",
        "euro_frequency__test_round3",
        "backtests_euro",
        "comp",
    ),
)


class LedgerMismatch(ValueError):
    """A model's ledger and its baseline's disagree about a fixture, or name the wrong model."""


# ---------------------------------------------------------------------------------------------
# Counting


def wilson(correct: int, n: int, z: float = Z95) -> tuple[float, float] | None:
    """95% Wilson score interval for `correct` hits in `n` (None when n is 0)."""
    if n <= 0:
        return None
    p = correct / n
    denom = 1 + z * z / n
    centre = (p + z * z / (2 * n)) / denom
    half = z * math.sqrt(p * (1 - p) / n + z * z / (4 * n * n)) / denom
    # Exactly 0 or 1 at the edges, where the formula's ends are 0 and 1 up to rounding.
    lo = 0.0 if correct <= 0 else max(0.0, centre - half)
    hi = 1.0 if correct >= n else min(1.0, centre + half)
    return lo, hi


def rate(hits: Iterable[object]) -> dict:
    """n, correct, hit rate and its Wilson interval for 0/1 (or bool) values; NaN is skipped."""
    values = np.asarray([float(h) for h in hits], dtype=float)
    values = values[~np.isnan(values)]
    n, correct = int(len(values)), int(values.sum())
    ci = wilson(correct, n)
    return {
        "n": n,
        "correct": correct,
        "hit_rate": correct / n if n else None,
        "ci95": list(ci) if ci else None,
    }


def group_name(code: str) -> str:
    if code == ALL:
        return "All"
    if code in UEFA_NAMES:
        return UEFA_NAMES[code]
    d = DIVISIONS.get(code)
    return d.name if d else code


def ordered_groups(family: str, codes: Iterable[str]) -> list[str]:
    order = GROUP_ORDER.get(family, ())
    present = set(codes)
    return [c for c in order if c in present] + sorted(present - set(order))


# ---------------------------------------------------------------------------------------------
# Test seasons: a model of record against its baseline


def read_ledger(path: Path) -> pd.DataFrame:
    return pd.read_csv(path, float_precision="round_trip", dtype={"season": str})


def paired(model: pd.DataFrame, baseline: pd.DataFrame, group_column: str) -> pd.DataFrame:
    """The fixtures both ledgers predicted, one row each: the model's columns as they are and
    the baseline's `correct_*` columns with a `base_` prefix. Raises LedgerMismatch if the two
    disagree on a fixture's teams, date or score."""
    keys = ["match_id", group_column, "season", "date", "home_team", "away_team"]
    m = model[model.status == "predicted"]
    b = baseline[baseline.status == "predicted"]
    for name, df in (("model", m), ("baseline", b)):
        if df.match_id.duplicated().any():
            raise LedgerMismatch(f"the {name} ledger lists a fixture twice")
    cols = [c for c in MARKETS.values() if c in b]
    base = b[[*keys, "home_goals", "away_goals", *cols]].rename(
        columns={
            **{c: f"base_{c}" for c in cols},
            **{k: f"base_{k}" for k in [*keys[1:], "home_goals", "away_goals"]},
        }
    )
    out = m.merge(base, on="match_id", how="inner", validate="one_to_one")
    for k in [*keys[1:], "home_goals", "away_goals"]:
        a, c = out[k].astype(str), out[f"base_{k}"].astype(str)
        if (a != c).any():
            bad = out.loc[a != c, "match_id"].iloc[0]
            raise LedgerMismatch(f"model and baseline ledgers disagree on {k} for match {bad}")
    return out.drop(columns=[f"base_{k}" for k in [*keys[1:], "home_goals", "away_goals"]])


def market_rates(df: pd.DataFrame) -> dict:
    """Per market: the model's hit rate and the baseline's on the same fixtures."""
    out = {}
    for market, col in MARKETS.items():
        if col not in df or df[col].isna().all():
            continue
        bcol = f"base_{col}"
        if bcol in df:
            both = df[df[col].notna() & df[bcol].notna()]
            r = rate(both[col])
            r["baseline"] = rate(both[bcol])
        else:  # the baseline doesn't predict this market
            r = rate(df[col])
            r["baseline"] = None
        out[market] = r
    return out


def confidence(df: pd.DataFrame) -> dict:
    """Hit rate where the model was surer. Per market, one bucket per threshold: the games
    whose favourite (match result) or likelier side (two-way markets) had at least that
    probability, their share of the games with a prediction, and the hit rate on them."""
    out: dict[str, list[dict]] = {}
    for market, (pcols, thresholds) in CONFIDENCE.items():
        col = MARKETS[market]
        if col not in df or any(p not in df for p in pcols):
            continue
        ok = df[df[col].notna() & df[list(pcols)].notna().all(axis=1)]
        if ok.empty:
            continue
        if len(pcols) == 1:
            p = ok[pcols[0]].astype(float)
            sure = np.maximum(p, 1 - p)
        else:
            sure = ok[list(pcols)].astype(float).max(axis=1)
        buckets = []
        for t in thresholds:
            # A small tolerance so a probability saved as 0.59999999999 counts as 60%.
            sel = ok[sure >= t - 1e-12]
            r = rate(sel[col])
            buckets.append(
                {"threshold": t, "games": r["n"], "share": r["n"] / len(ok), "of_games": len(ok)}
                | {k: r[k] for k in ("correct", "hit_rate", "ci95")}
            )
        out[market] = buckets
    return out


def rows_for(df: pd.DataFrame, family: str, group_column: str) -> list[dict]:
    """The All row, then one per league or competition."""
    rows = []
    for code in [ALL, *ordered_groups(family, df[group_column].astype(str).unique())]:
        part = df if code == ALL else df[df[group_column].astype(str) == code]
        rows.append(
            {
                "code": code,
                "name": group_name(code),
                "games": int(len(part)),
                "markets": market_rates(part),
                "confidence": confidence(part),
            }
        )
    return rows


def _manifest(run_dir: Path) -> dict:
    path = run_dir / "manifest.json"
    return json.loads(path.read_text()) if path.exists() else {}


def source_table(source: Source, data_dir: Path) -> dict:
    """One model of record's hit rates on its test run. Raises FileNotFoundError if a ledger
    is missing and LedgerMismatch if the ledgers don't fit together."""
    run_dir = data_dir / source.root / source.run_id
    base_dir = data_dir / source.root / source.baseline_run_id
    model = read_ledger(run_dir / "ledger.csv")
    baseline = read_ledger(base_dir / "ledger.csv")
    for df, want, rid in (
        (model, source.model, source.run_id),
        (baseline, source.baseline_model, source.baseline_run_id),
    ):
        names = set(df["model"].astype(str)) if "model" in df else set()
        if names != {want}:
            raise LedgerMismatch(f"{rid}: the ledger names {sorted(names)}, not {want}")
    df = paired(model, baseline, source.group_column)
    manifest, base_manifest = _manifest(run_dir), _manifest(base_dir)
    return {
        "family": source.family,
        "family_name": FAMILY_NAMES.get(source.family, source.family),
        "model": source.model,
        "basis": source.basis,
        "run_id": source.run_id,
        "split": manifest.get("split"),
        "run_created_at_utc": manifest.get("created_at_utc"),
        "baseline_model": source.baseline_model,
        "baseline_run_id": source.baseline_run_id,
        "baseline_run_created_at_utc": base_manifest.get("created_at_utc"),
        "seasons": sorted(df.season.astype(str).unique().tolist()),
        "games": int(len(df)),
        "rows": rows_for(df, source.family, source.group_column),
    }


def backtest_tables(
    data_dir: Path, sources: Sequence[Source] | None = None
) -> tuple[list[dict], list[dict]]:
    """(tables, missing): a table per source (default SOURCES) whose ledgers exist and fit
    together, and the sources left out, each with the reason (a ledger not saved, or ledgers
    that disagree)."""
    tables, missing = [], []
    for s in SOURCES if sources is None else sources:
        try:
            tables.append(source_table(s, data_dir))
        except FileNotFoundError:
            gone = [
                rid
                for rid in (s.run_id, s.baseline_run_id)
                if not (data_dir / s.root / rid / "ledger.csv").exists()
            ]
            missing.append(
                {
                    "family": s.family,
                    "model": s.model,
                    "reason": "ledger not saved",
                    "runs": gone or [s.run_id, s.baseline_run_id],
                }
            )
        except LedgerMismatch as e:
            missing.append(
                {
                    "family": s.family,
                    "model": s.model,
                    "reason": str(e),
                    "runs": [s.run_id, s.baseline_run_id],
                }
            )
    return tables, missing


# ---------------------------------------------------------------------------------------------
# Genuine pre-match record


@dataclass(frozen=True)
class SettledRecord:
    """One settled genuine pre-match prediction: the fixture's record of record, first edition."""

    family: str
    model: str
    basis: str
    competition: str
    outcomes: dict  # market -> {"correct": bool, ...}, as saved by footy.publish.settle


def prematch_tables(records: Iterable[SettledRecord]) -> list[dict]:
    """Hit rates of the settled pre-match record, one table per family (league games, UEFA
    competitions): an All row and one per competition, each market's hit rate with its
    interval. Each fixture counts once, with its record of record, whichever model of record
    that was (`models` counts them). No baseline: none is published live. Empty when nothing
    is settled."""
    by: dict[str, list[SettledRecord]] = {}
    for r in records:
        by.setdefault(r.family, []).append(r)
    order = {f: i for i, f in enumerate(FAMILY_NAMES)}
    tables = []
    for family in sorted(by, key=lambda f: (order.get(f, len(order)), f)):
        recs = by[family]
        rows = []
        for code in [ALL, *ordered_groups(family, (r.competition for r in recs))]:
            part = recs if code == ALL else [r for r in recs if r.competition == code]
            markets = {}
            for market in MARKETS:
                hits = [bool(r.outcomes[market]["correct"]) for r in part if market in r.outcomes]
                if hits:
                    markets[market] = rate(hits)
            rows.append(
                {"code": code, "name": group_name(code), "games": len(part), "markets": markets}
            )
        models: dict[str, int] = {}
        bases: dict[str, int] = {}
        for r in recs:
            models[r.model] = models.get(r.model, 0) + 1
            bases[r.basis] = bases.get(r.basis, 0) + 1
        tables.append(
            {
                "family": family,
                "family_name": FAMILY_NAMES.get(family, family),
                "games": len(recs),
                "models": dict(sorted(models.items(), key=lambda kv: (-kv[1], kv[0]))),
                "bases": dict(sorted(bases.items())),
                "rows": rows,
            }
        )
    return tables
