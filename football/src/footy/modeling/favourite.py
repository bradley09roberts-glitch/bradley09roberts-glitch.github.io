"""The bookmakers' favourite's hit rate on the fixtures of a hit-rates table (/api/hit-rates).

The model of record for league games moves its score grid all the way onto the bookmakers'
de-vigged prices (D-024): its match-result and over/under 2.5 picks are the bookmakers'
favourites, so its hit rate there is the favourite's. Beating the frequency baseline says
nothing about betting; beating the bookmakers does. So each league row of a hit-rates table
gets the favourite's hit rate on the same fixtures, for those two markets.

The favourite is the outcome with the shortest price at football-data's market average
before the close (`Avg`, pre-closing: one of the two price sets behind the model + market
records). A fixture without a full set of those prices, or whose two shortest prices are
equal, has no favourite and is left out (`n` says how many count). Only how often the
favourite won is reported, never a price (D-013, D-024). UEFA matches have no prices here.
"""

from __future__ import annotations

from collections.abc import Iterable, Sequence
from pathlib import Path

import numpy as np
import pandas as pd
from sqlalchemy import select
from sqlalchemy.orm import Session

from footy.modeling import hit_rates as hr
from footy.models import FdOdds

BOOKMAKER = "Avg"
PHASE = "pre_closing"
SELECTIONS = {"1X2": ("H", "D", "A"), "OU_2.5": ("O", "U")}
NOTE = (
    "The bookmakers' favourite: the outcome with the shortest price at the market average "
    "before the close (football-data's Avg pre-closing prices), on the same fixtures. Only "
    "fixtures with a full set of those prices and one clear favourite count."
)


def favourites(s: Session, match_ids: Iterable[int]) -> dict[str, dict[int, str]]:
    """Per market, match id -> the favourite's selection, where there is exactly one."""
    ids = sorted({int(i) for i in match_ids})
    prices: dict[tuple[str, int], dict[str, float]] = {}
    for start in range(0, len(ids), 500):
        chunk = ids[start : start + 500]
        for mid, market, sel, price in s.execute(
            select(FdOdds.match_id, FdOdds.market_key, FdOdds.selection, FdOdds.price).where(
                FdOdds.match_id.in_(chunk),
                FdOdds.bookmaker == BOOKMAKER,
                FdOdds.phase == PHASE,
                FdOdds.market_key.in_(tuple(SELECTIONS)),
            )
        ):
            prices.setdefault((market, mid), {})[sel] = float(price)
    out: dict[str, dict[int, str]] = {m: {} for m in SELECTIONS}
    for (market, mid), by_sel in prices.items():
        if set(by_sel) != set(SELECTIONS[market]):
            continue
        ranked = sorted(by_sel.items(), key=lambda kv: kv[1])
        if ranked[0][1] < ranked[1][1]:  # one clear favourite
            out[market][mid] = ranked[0][0]
    return out


def _actual(df: pd.DataFrame, market: str) -> pd.Series:
    hg, ag = df.home_goals.astype(int), df.away_goals.astype(int)
    if market == "1X2":
        return pd.Series(np.where(hg > ag, "H", np.where(hg == ag, "D", "A")), index=df.index)
    return pd.Series(np.where(hg + ag > 2.5, "O", "U"), index=df.index)


def favourite_rates(df: pd.DataFrame, fav: dict[str, dict[int, str]]) -> dict[str, dict]:
    """Per market, the favourite's hit rate on these fixtures (hr.rate's fields)."""
    out = {}
    for market in SELECTIONS:
        picks = df.match_id.astype(int).map(fav[market])
        has = picks.notna()
        if not has.any():
            continue
        out[market] = hr.rate((picks[has] == _actual(df[has], market)).astype(int))
    return out


def add_favourites(s: Session, data_dir: Path, tables: Sequence[dict]) -> None:
    """Give each league-games table's rows a `favourite` per market (match result, over/under
    2.5): the bookmakers' favourite's hit rate on that row's fixtures. A table whose ledgers
    can't be read again is left as it is."""
    sources = {src.run_id: src for src in hr.SOURCES}
    for table in tables:
        src = sources.get(table.get("run_id"))
        if src is None or src.family != "domestic":
            continue
        try:
            model = hr.read_ledger(data_dir / src.root / src.run_id / "ledger.csv")
            base = hr.read_ledger(data_dir / src.root / src.baseline_run_id / "ledger.csv")
            df = hr.paired(model, base, src.group_column)
        except (FileNotFoundError, hr.LedgerMismatch):
            continue
        fav = favourites(s, df.match_id)
        table["favourite_note"] = NOTE
        for row in table.get("rows") or []:
            code = row.get("code")
            part = df if code == hr.ALL else df[df[src.group_column].astype(str) == code]
            for market, r in favourite_rates(part, fav).items():
                if market in row.get("markets", {}):
                    row["markets"][market]["favourite"] = r
