"""Matches played behind closed doors (COVID-19), from reference/closed_doors.csv.

Rows with crowd_status "none" mark the closed-doors periods that get their own home-advantage
term; "limited" periods (capped crowds) are kept for a sensitivity check. If the file is
missing, no match is flagged and the model falls back to a single home-advantage term; a
warning is logged (once per process), since that changes every model that uses the term.
"""

import csv
import logging
from functools import lru_cache

import numpy as np
import pandas as pd

from footy.reference import REFERENCE_DIR

PATH = REFERENCE_DIR / "closed_doors.csv"
log = logging.getLogger(__name__)
_warned_missing = False


def _warn_missing() -> None:
    global _warned_missing
    if _warned_missing:
        return
    _warned_missing = True
    log.warning(
        "%s is missing: no match is flagged as played behind closed doors, so models fit "
        "no closed-doors home-advantage term",
        PATH,
    )


@lru_cache
def periods(
    statuses: tuple[str, ...] = ("none",),
) -> dict[str, list[tuple[pd.Timestamp, pd.Timestamp]]]:
    if not PATH.exists():
        _warn_missing()
        return {}
    out: dict[str, list] = {}
    with PATH.open(newline="", encoding="utf-8") as f:
        for r in csv.DictReader(f):
            if r.get("crowd_status", "").strip() in statuses:
                out.setdefault(r["division"].strip(), []).append(
                    (pd.Timestamp(r["start_date"]), pd.Timestamp(r["end_date"]))
                )
    return out


def closed_doors_mask(df: pd.DataFrame, statuses: tuple[str, ...] = ("none",)) -> np.ndarray:
    mask = np.zeros(len(df), dtype=bool)
    p = periods(statuses)
    if not p:
        return mask
    div, day = df.division.to_numpy(), df["date"].to_numpy()
    for d, ranges in p.items():
        for start, end in ranges:
            mask |= (div == d) & (day >= np.datetime64(start)) & (day <= np.datetime64(end))
    return mask
