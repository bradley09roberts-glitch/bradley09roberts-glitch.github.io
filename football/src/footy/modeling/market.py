"""Bookmaker-implied probabilities with the margin removed, for the market benchmark gate.

Sources by kickoff date (DECISIONS.md D-014): Pinnacle closing before 2025-07-23, Betfair
Exchange closing from then on; the market-average closing price is a robustness check.
Market prices are used only for benchmarking: they never enter the primary model.
"""

import numpy as np
import pandas as pd
from scipy.optimize import brentq
from sqlalchemy import Engine, text

PINNACLE_UNRELIABLE_FROM = pd.Timestamp("2025-07-23")


def devig_power(prices: np.ndarray) -> np.ndarray:
    """Power method: p_i = (1/o_i)^k with k chosen so the probabilities sum to one."""
    inv = 1.0 / np.asarray(prices, dtype=float)
    out = np.empty_like(inv)
    for r in range(len(inv)):
        q = inv[r]
        k = brentq(lambda k, q=q: (q**k).sum() - 1.0, 0.2, 5.0)
        out[r] = q**k
    return out


def devig_shin(prices: np.ndarray) -> np.ndarray:
    """Shin's method (insider-trading model), solved for z per fixture."""
    inv = 1.0 / np.asarray(prices, dtype=float)
    out = np.empty_like(inv)
    for r in range(len(inv)):
        q = inv[r]
        s = q.sum()

        def probs(z, q=q, s=s):
            return (np.sqrt(z**2 + 4 * (1 - z) * q**2 / s) - z) / (2 * (1 - z))

        if s <= 1.0:
            out[r] = q / s
            continue
        z = brentq(lambda z: probs(z).sum() - 1.0, 0.0, 0.5)
        out[r] = probs(z)
    return out


def closing_market(engine: Engine, match_ids: list[int], method: str = "shin") -> pd.DataFrame:
    """Per match: de-vigged closing probabilities for 1X2 and O/U 2.5 from the benchmark
    source of D-014, plus the market average. Matches without a complete set are omitted for
    that market (coverage is reported by the caller)."""
    if not match_ids:
        return pd.DataFrame()
    q = text(
        "SELECT o.match_id, m.match_date, o.bookmaker, o.market_key, o.selection, o.price "
        "FROM fd_odds o JOIN matches m ON m.id = o.match_id "
        "WHERE o.phase = 'closing' AND o.bookmaker IN ('PS', 'BFE', 'Avg') "
        f"AND o.match_id IN ({','.join(str(int(i)) for i in match_ids)})"
    )
    with engine.connect() as conn:
        df = pd.read_sql(q, conn)
    if df.empty:
        return df
    df["match_date"] = pd.to_datetime(df.match_date)
    wide = df.pivot_table(
        index=["match_id", "match_date"],
        columns=["bookmaker", "market_key", "selection"],
        values="price",
    )
    devig = devig_shin if method == "shin" else devig_power
    out = pd.DataFrame(index=wide.index.get_level_values("match_id"))
    dates = wide.index.get_level_values("match_date")
    primary_book = np.where(dates < PINNACLE_UNRELIABLE_FROM, "PS", "BFE")
    for label, books in (("bench", None), ("avg", "Avg")):
        for market, sels in (("1X2", ["H", "D", "A"]), ("OU_2.5", ["O", "U"])):
            cols = [f"{label}_{market}_{s}" for s in sels]
            vals = np.full((len(wide), len(sels)), np.nan)
            book_col = []
            for r in range(len(wide)):
                book = books or primary_book[r]
                try:
                    pr = np.array([wide.iloc[r][(book, market, s)] for s in sels], dtype=float)
                except KeyError:
                    pr = np.full(len(sels), np.nan)
                book_col.append(book)
                if np.isfinite(pr).all():
                    vals[r] = devig(pr[None, :])[0]
            out[cols] = vals
            if label == "bench":
                out[f"bench_{market}_source"] = book_col
    return out.reset_index().rename(columns={"index": "match_id"})
