"""Model + market: a score model's grid tilted towards football-data's pre-closing prices.

The prices are the ones football-data collected at the D-012 deadline (Friday 17:00 or
Tuesday 13:00 UK), before an early-edition prediction is made, so a backtest sees what a live
run would have seen. Closing prices are never read: they are the benchmark.

For each fixture the market's 1X2 and O/U 2.5 probabilities (Shin de-vigged, averaged over
Bet365 and the market average) are blended with the base model's own:
target = w * market + (1 - w) * model, with one weight for 1X2 and one for O/U 2.5. The base
grid is then moved to those targets with the smallest change in information (an exponential
tilt, i.e. the I-projection onto the targets). Its 1X2 and O/U 2.5 then equal the targets
exactly; O/U 1.5 and 3.5, BTTS and the scores follow from the tilted grid, so every market
stays coherent. A fixture without usable prices keeps the base model's grid unchanged.

Usable prices: phase 'pre_closing'; quoted no later than the prediction is made (the
fixture's `deadline_utc` in backtests, the time of the run when live); quoted before the
kickoff where it is known.
"""

from collections.abc import Callable
from datetime import datetime
from functools import cache

import numpy as np
import pandas as pd
from sqlalchemy import inspect, text

from footy.modeling.base import ALL_MARKETS
from footy.modeling.grid import GOALS, MarketProbs, markets_from_grid
from footy.modeling.market import devig_shin

BOOKS = ("B365", "Avg")  # both quoted in every period and in fixtures.csv (no Pinnacle there)
KEY = ["division", "date", "home_team", "away_team"]

_TOTAL = GOALS[:, None] + GOALS[None, :]
FEATURES = np.stack(
    [
        (GOALS[:, None] > GOALS[None, :]).astype(float),  # home win
        (GOALS[:, None] == GOALS[None, :]).astype(float),  # draw
        (_TOTAL > 2.5).astype(float),  # over 2.5
    ]
)  # (3, 11, 11)


def consensus(raw: pd.DataFrame) -> pd.DataFrame:
    """One row per fixture: de-vigged home/draw/away and over-2.5 probabilities averaged over
    the books that quote a complete set, and the latest quote time used.

    `raw` columns: division, date, home_team, away_team, bookmaker, market_key, selection,
    price, quote_time_utc."""
    cols = [*KEY, "p_H", "p_D", "p_A", "p_O", "n_books_1x2", "n_books_ou", "quote_time_utc"]
    if raw.empty:
        return pd.DataFrame(columns=cols).set_index(KEY)
    raw = raw[raw.bookmaker.isin(BOOKS)].copy()
    raw["date"] = pd.to_datetime(raw["date"])
    raw["quote_time_utc"] = pd.to_datetime(raw["quote_time_utc"], utc=True)
    out = []
    for market, sels, names in (
        ("1X2", ["H", "D", "A"], ["p_H", "p_D", "p_A"]),
        ("OU_2.5", ["O", "U"], ["p_O", "p_U"]),
    ):
        m = raw[raw.market_key == market]
        if m.empty:
            continue
        w = m.pivot_table(
            index=[*KEY, "bookmaker"], columns="selection", values="price", aggfunc="first"
        )
        t = m.groupby([*KEY, "bookmaker"])["quote_time_utc"].max()
        w = w.reindex(columns=sels).dropna()
        if w.empty:
            continue
        p = devig_shin(w.to_numpy())
        d = pd.DataFrame(p, index=w.index, columns=names)
        d["quote_time_utc"] = t.reindex(w.index).to_numpy()
        g = d.groupby(level=KEY)
        agg = g[names].mean()
        agg[f"n_books_{'1x2' if market == '1X2' else 'ou'}"] = g.size()
        agg[f"qt_{market}"] = g["quote_time_utc"].max()
        out.append(agg)
    if not out:
        return pd.DataFrame(columns=cols).set_index(KEY)
    res = out[0].join(out[1], how="outer") if len(out) == 2 else out[0]
    for c in ("p_H", "p_D", "p_A", "p_O"):
        if c not in res:
            res[c] = np.nan
    for c in ("n_books_1x2", "n_books_ou"):
        res[c] = res[c].fillna(0).astype(int) if c in res else 0
    qts = [res[c] for c in ("qt_1X2", "qt_OU_2.5") if c in res]
    res["quote_time_utc"] = pd.concat(qts, axis=1).max(axis=1)
    return res[cols[4:]]


def load_pre_closing(engine) -> pd.DataFrame:
    """football-data pre-closing prices of imported matches (never closing prices)."""
    sql = (
        "SELECT m.division, m.match_date AS date, m.home_team, m.away_team, o.bookmaker, "
        "o.market_key, o.selection, o.price, o.quote_time_utc FROM fd_odds o "
        "JOIN matches m ON m.id = o.match_id WHERE o.phase = 'pre_closing'"
    )
    with engine.connect() as conn:
        return pd.read_sql(text(sql), conn)


@cache
def default_prices() -> pd.DataFrame:
    """Pre-closing prices from the database plus the latest live snapshot of upcoming
    fixtures (footy.ingest.fd_fixtures), as one consensus table."""
    from footy.config import get_settings
    from footy.db import make_engine

    engine = make_engine(get_settings().database_url)
    if not inspect(engine).has_table("fd_odds"):  # an empty database (tests, CI)
        return consensus(pd.DataFrame())
    raw = [load_pre_closing(engine)]
    from footy.ingest.fd_fixtures import latest_snapshot

    snap = latest_snapshot(engine)
    if snap is not None and not snap.empty:
        raw.append(snap)
    book = consensus(pd.concat(raw, ignore_index=True))
    if snap is not None:
        book.attrs["live_snapshot"] = {
            k: snap.attrs[k] for k in ("raw_sha256", "retrieved_at", "raw_file_id")
        } | {"source": "football-data fixtures.csv"}
    return book


def tilt(grid: np.ndarray, target: np.ndarray, iters: int = 60, tol: float = 1e-11) -> np.ndarray:
    """Exponentially tilt each (11, 11) grid so that P(home), P(draw) and P(over 2.5) equal
    `target` (n, 3): the minimum-KL change to the grid that hits the targets (Newton's method
    on the convex dual)."""
    n = grid.shape[0]
    f = FEATURES.reshape(3, -1)  # (3, 121)
    g = np.clip(grid.reshape(n, -1), 1e-300, None)
    theta = np.zeros((n, 3))
    for _ in range(iters):
        logw = np.log(g) + theta @ f
        logw -= logw.max(axis=1, keepdims=True)
        q = np.exp(logw)
        q /= q.sum(axis=1, keepdims=True)
        m = q @ f.T  # (n, 3)
        r = m - target
        if np.abs(r).max() < tol:
            break
        cov = np.einsum("nc,kc,lc->nkl", q, f, f) - m[:, :, None] * m[:, None, :]
        cov += 1e-12 * np.eye(3)
        theta -= np.linalg.solve(cov, r[:, :, None])[:, :, 0]
    return q.reshape(grid.shape)


class MarketTilt:
    """A base score model whose grid is tilted towards pre-closing market prices."""

    markets = ALL_MARKETS

    def __init__(
        self,
        base_factory: Callable,
        w_1x2: float,
        w_ou: float,
        name: str,
        prices: Callable[[], pd.DataFrame] = default_prices,
    ):
        self.base_factory = base_factory
        self.base = None
        self.w_1x2, self.w_ou = float(w_1x2), float(w_ou)
        self.name = name
        self._prices = prices
        self.decision_time: datetime | None = None  # live runs only; backtests use deadline_utc
        self.last_used: pd.DataFrame | None = None  # prices used for the last predict()

    def fit(self, history: pd.DataFrame, cutoff: pd.Timestamp) -> None:
        if self.base is None:
            self.base = self.base_factory()
        self.base.fit(history, cutoff)

    def market_for(self, fixtures: pd.DataFrame) -> pd.DataFrame:
        """The usable consensus prices for each fixture (NaN where there are none)."""
        book = self._prices()
        idx = pd.MultiIndex.from_arrays(
            [
                fixtures.division.to_numpy(),
                pd.to_datetime(fixtures.date).to_numpy(),
                fixtures.home_team.to_numpy(),
                fixtures.away_team.to_numpy(),
            ],
            names=KEY,
        )
        mk = book.reindex(idx).reset_index(drop=True)
        qt = pd.to_datetime(mk["quote_time_utc"], utc=True)
        if "deadline_utc" in fixtures.columns:  # backtest: made at the collection deadline
            made = pd.to_datetime(fixtures["deadline_utc"], utc=True).reset_index(drop=True)
        elif self.decision_time is not None:  # live: set by the caller to the time of the run
            made = pd.Series(pd.Timestamp(self.decision_time).tz_convert("UTC"), index=mk.index)
        elif qt.notna().any():
            raise ValueError("prices found but no decision time: pass deadline_utc or set it")
        else:
            made = pd.Series(pd.NaT, index=mk.index, dtype="datetime64[ns, UTC]")
        ok = qt.notna() & (qt <= made)
        if "kickoff_utc" in fixtures.columns:
            ko = pd.to_datetime(fixtures["kickoff_utc"], utc=True).reset_index(drop=True)
            ok &= ko.isna() | (qt < ko)
        mk.loc[~ok.to_numpy(), ["p_H", "p_D", "p_A", "p_O"]] = np.nan
        return mk

    def predict(self, fixtures: pd.DataFrame) -> MarketProbs:
        mp = self.base.predict(fixtures)
        grid = mp.grid
        base = markets_from_grid(grid)
        mk = self.market_for(fixtures)
        target = np.stack([base.p1x2[:, 0], base.p1x2[:, 1], base.p_over[2.5]], axis=1)
        has_1x2 = mk[["p_H", "p_D", "p_A"]].notna().all(axis=1).to_numpy()
        has_ou = mk["p_O"].notna().to_numpy()
        w1, wo = self.w_1x2, self.w_ou
        target[has_1x2, 0] = w1 * mk.p_H.to_numpy()[has_1x2] + (1 - w1) * target[has_1x2, 0]
        target[has_1x2, 1] = w1 * mk.p_D.to_numpy()[has_1x2] + (1 - w1) * target[has_1x2, 1]
        target[has_ou, 2] = wo * mk.p_O.to_numpy()[has_ou] + (1 - wo) * target[has_ou, 2]
        move = has_1x2 | has_ou
        out = grid.copy()
        if move.any():
            out[move] = tilt(grid[move], target[move])
        self.last_used = mk.assign(used_1x2=has_1x2, used_ou=has_ou)
        return markets_from_grid(out, lam_h=mp.lam_h, lam_a=mp.lam_a)

    def provenance(self) -> dict | None:
        """Where live prices came from (the latest fixtures.csv), if any were loaded."""
        return self._prices().attrs.get("live_snapshot")

    def params(self) -> dict:
        base = self.base or self.base_factory()
        return {
            "base": getattr(base, "name", type(base).__name__),
            "base_params": base.params(),
            "w_1x2": self.w_1x2,
            "w_ou": self.w_ou,
            "books": list(BOOKS),
            "phase": "pre_closing",
            "devig": "shin",
        }


def _base(name: str) -> Callable:
    def make():
        from footy.modeling.registry import candidates

        return candidates()[name]()

    return make


# Weights chosen on the inner season 2022/23 only, by a grid search over w_1x2 in 0..1 (step
# 0.05) and w_ou in 0..1 (step 0.1) on recorded base grids (docs/research/market-blend.md).
CANDIDATES: dict[str, Callable] = {
    "market_tilt_dc": lambda: MarketTilt(_base("dixon_coles"), 1.0, 1.0, "market_tilt_dc"),
}
