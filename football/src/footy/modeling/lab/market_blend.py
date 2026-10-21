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
kickoff where it is known; and published no earlier than 00:00 UK on the fixture's own D-012
collection day, so a live run never uses an older file's prices (Tuesday's for a weekend
fixture, say) where a backtest would have had that collection's. A live price whose publication
time is unknown (its file had no readable HTTP Last-Modified) is never used. In backtests the
last two rules never bind: a season file's price is stamped at its own deadline.
"""

from collections.abc import Callable
from datetime import datetime
from functools import cache

import numpy as np
import pandas as pd
from scipy.special import expit
from sqlalchemy import inspect, text

from footy.ingest.fd_parse import model_cutoff
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
    the books that quote a complete set, the latest quote time used and the earliest
    publication time (how old the oldest price used may be).

    `raw` columns: division, date, home_team, away_team, bookmaker, market_key, selection,
    price, quote_time_utc, and optionally published_utc (when the prices were published, if
    earlier than quote_time_utc; a live file's Last-Modified time). Without that column the
    quote time stands in (a season file's price is published when quoted). An empty value in
    it means unknown, and stays unknown: a fixture with any such price gets an empty
    published_utc, so the staleness rule refuses it."""
    cols = [
        *KEY,
        "p_H",
        "p_D",
        "p_A",
        "p_O",
        "n_books_1x2",
        "n_books_ou",
        "quote_time_utc",
        "published_utc",
    ]
    if raw.empty:
        return pd.DataFrame(columns=cols).set_index(KEY)
    raw = raw[raw.bookmaker.isin(BOOKS)].copy()
    raw["date"] = pd.to_datetime(raw["date"])
    raw["quote_time_utc"] = pd.to_datetime(raw["quote_time_utc"], utc=True)
    if "published_utc" in raw:
        raw["published_utc"] = pd.to_datetime(raw["published_utc"], utc=True)
    else:
        raw["published_utc"] = raw["quote_time_utc"]
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
        times = m.groupby([*KEY, "bookmaker"])
        t, pub = times["quote_time_utc"].max(), _earliest(times["published_utc"])
        w = w.reindex(columns=sels).dropna()
        if w.empty:
            continue
        p = devig_shin(w.to_numpy())
        d = pd.DataFrame(p, index=w.index, columns=names)
        d["quote_time_utc"] = t.reindex(w.index).to_numpy()
        d["published_utc"] = pub.reindex(w.index).to_numpy()
        g = d.groupby(level=KEY)
        agg = g[names].mean()
        agg[f"n_books_{'1x2' if market == '1X2' else 'ou'}"] = g.size()
        agg[f"qt_{market}"] = g["quote_time_utc"].max()
        agg[f"pub_{market}"] = _earliest(g["published_utc"])
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
    pubs = [res[c] for c in ("pub_1X2", "pub_OU_2.5") if c in res]
    unknown = pd.Series(False, index=res.index)
    for market, n in (("1X2", "n_books_1x2"), ("OU_2.5", "n_books_ou")):
        if f"pub_{market}" in res:  # a market that has prices but no publication time
            unknown |= (res[n] > 0) & res[f"pub_{market}"].isna()
    res["published_utc"] = pd.concat(pubs, axis=1).min(axis=1).mask(unknown)
    return res[cols[4:]]


def _earliest(times) -> pd.Series:
    """The earliest time per group, or empty (unknown) if any time in the group is."""
    return times.min().where(times.count() == times.size())


def load_pre_closing(engine) -> pd.DataFrame:
    """football-data pre-closing prices of imported matches (never closing prices)."""
    sql = (
        "SELECT m.division, m.match_date AS date, m.home_team, m.away_team, o.bookmaker, "
        "o.market_key, o.selection, o.price, o.quote_time_utc FROM fd_odds o "
        "JOIN matches m ON m.id = o.match_id WHERE o.phase = 'pre_closing'"
    )
    with engine.connect() as conn:
        return pd.read_sql(text(sql), conn)


def combine_prices(db: pd.DataFrame, snap: pd.DataFrame | None) -> pd.DataFrame:
    """One consensus table from the database's pre-closing prices and a live snapshot. A
    fixture already in the database keeps only its season file's prices (stamped at its D-012
    deadline): the snapshot's copy is left out, so the two sources are never mixed and a
    later retrieval time can't override the deadline stamp. The database's prices are
    published when quoted (stamped at their deadline)."""
    if "quote_time_utc" in db and "published_utc" not in db:
        db = db.assign(published_utc=db["quote_time_utc"])
    raw = [db]
    if snap is not None and not snap.empty:
        if not db.empty:

            def keys(df: pd.DataFrame) -> pd.MultiIndex:
                return pd.MultiIndex.from_frame(df[KEY].assign(date=pd.to_datetime(df["date"])))

            snap = snap[~keys(snap).isin(keys(db))]
        raw.append(snap)
    return consensus(pd.concat(raw, ignore_index=True))


@cache
def default_prices() -> pd.DataFrame:
    """Pre-closing prices from the database plus the latest live snapshot of upcoming
    fixtures (footy.ingest.fd_fixtures), as one consensus table."""
    from footy.config import get_settings
    from footy.db import make_engine

    engine = make_engine(get_settings().database_url)
    if not inspect(engine).has_table("fd_odds"):  # an empty database (tests, CI)
        return consensus(pd.DataFrame())
    from footy.ingest.fd_fixtures import latest_snapshot

    snap = latest_snapshot(engine)
    book = combine_prices(load_pre_closing(engine), snap)
    if snap is not None:
        book.attrs["live_snapshot"] = {
            k: snap.attrs[k]
            for k in ("raw_sha256", "retrieved_at", "published_at", "raw_file_id")
            if k in snap.attrs
        } | {"source": "football-data fixtures.csv"}
    return book


# The six blocks the three indicators split a grid into: result (home, draw, away) x over 2.5.
_RESULT = np.stack([FEATURES[0], FEATURES[1], 1.0 - FEATURES[0] - FEATURES[1]])  # (3, 11, 11)
_OVER_BLOCK = _RESULT * FEATURES[2]
_UNDER_BLOCK = _RESULT * (1.0 - FEATURES[2])


def tilt(grid: np.ndarray, target: np.ndarray, iters: int = 200) -> np.ndarray:
    """Exponentially tilt each (11, 11) grid so that P(home), P(draw) and P(over 2.5) equal
    `target` (n, 3): the minimum-KL change to the grid that hits the targets.

    The tilt multiplies every cell by a factor that depends only on its result (home, draw,
    away) and on whether it has over 2.5 goals, so it is the raking of a 3 x 2 table of block
    masses to the target margins. Each result row is scaled exactly; the one remaining
    unknown, the odds u of over against under, solves sum_r t_r * expit(u + c_r) = t_over
    (c_r: the base grid's log odds of over within result r). That sum is increasing in u, so
    bisection always finds the root, however far the targets are from the base grid (an
    undamped Newton iteration on the dual diverged for extreme targets)."""
    target = np.asarray(target, dtype=float)
    t_h, t_d, t_o = target[:, 0], target[:, 1], target[:, 2]
    t_r = np.stack([t_h, t_d, 1.0 - t_h - t_d], axis=1)  # (n, 3)
    if not (np.isfinite(target).all() and (t_r > 0).all() and ((t_o > 0) & (t_o < 1)).all()):
        raise ValueError("tilt targets must be probabilities strictly inside (0, 1)")
    g = np.clip(grid, 1e-300, None)
    m_over = np.einsum("nij,rij->nr", g, _OVER_BLOCK)  # (n, 3) block masses
    m_under = np.einsum("nij,rij->nr", g, _UNDER_BLOCK)
    c = np.log(m_over) - np.log(m_under)
    logit_o = np.log(t_o) - np.log1p(-t_o)
    lo, hi = logit_o - c.max(axis=1), logit_o - c.min(axis=1)  # brackets the root
    for _ in range(iters):
        mid = 0.5 * (lo + hi)
        below = (t_r * expit(mid[:, None] + c)).sum(axis=1) < t_o
        lo, hi = np.where(below, mid, lo), np.where(below, hi, mid)
    x = 0.5 * (lo + hi)[:, None] + c  # log odds of over 2.5 within each result
    f_over, f_under = t_r * expit(x) / m_over, t_r * expit(-x) / m_under
    factor = np.einsum("nr,rij->nij", f_over, _OVER_BLOCK) + np.einsum(
        "nr,rij->nij", f_under, _UNDER_BLOCK
    )
    return g * factor


def _inside(*probs: np.ndarray) -> np.ndarray:
    """True where every given probability is finite and strictly between 0 and 1."""
    a = np.stack(probs)
    return np.isfinite(a).all(axis=0) & (a > 0).all(axis=0) & (a < 1).all(axis=0)


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
        # Why a fixture's prices are refused: the first rule that fails (empty if they were
        # used, or if there were none).
        refused = pd.Series(None, index=mk.index, dtype=object)

        def refuse(bad: pd.Series, reason: str) -> None:
            refused[bad.to_numpy() & refused.isna().to_numpy() & qt.notna().to_numpy()] = reason

        refuse(~(qt <= made), "quoted after the prediction was made")
        if "kickoff_utc" in fixtures.columns:
            ko = pd.to_datetime(fixtures["kickoff_utc"], utc=True).reset_index(drop=True)
            refuse(~(ko.isna() | (qt < ko)), "quoted at or after kickoff")
        # Staleness: only prices published on or after 00:00 UK on the fixture's own D-012
        # collection day (the start of its decision batch), as in a backtest. A price whose
        # publication time is unknown is refused too: it could be from any earlier collection.
        pub = mk["published_utc"] if "published_utc" in mk else mk["quote_time_utc"]
        pub = pd.to_datetime(pub, utc=True)
        days = pd.Series(idx.get_level_values("date")).dt.date
        first = {d: model_cutoff(d) for d in days.unique()}
        refuse(pub.isna(), "publication time unknown (no readable Last-Modified)")
        refuse(
            ~(pub >= pd.to_datetime(days.map(first), utc=True)),
            "published before the fixture's collection day",
        )
        ok = qt.notna() & refused.isna()
        mk.loc[~ok.to_numpy(), ["p_H", "p_D", "p_A", "p_O"]] = np.nan
        mk["refused"] = refused
        return mk

    def predict(self, fixtures: pd.DataFrame) -> MarketProbs:
        mp = self.base.predict(fixtures)
        grid = mp.grid
        base = markets_from_grid(grid)
        mk = self.market_for(fixtures)
        own = np.stack([base.p1x2[:, 0], base.p1x2[:, 1], base.p_over[2.5]], axis=1)
        has_1x2 = np.array(mk[["p_H", "p_D", "p_A"]].notna().all(axis=1), dtype=bool)
        has_ou = np.array(mk["p_O"].notna(), dtype=bool)
        w1, wo = self.w_1x2, self.w_ou
        target = own.copy()
        target[:, 0] = w1 * mk.p_H.to_numpy(float) + (1 - w1) * own[:, 0]
        target[:, 1] = w1 * mk.p_D.to_numpy(float) + (1 - w1) * own[:, 1]
        target[:, 2] = wo * mk.p_O.to_numpy(float) + (1 - wo) * own[:, 2]
        # Only probabilities strictly inside (0, 1) can be tilted to; a market whose target
        # isn't (a price at 1, a degenerate base grid) keeps the base model's own value.
        has_1x2 &= _inside(target[:, 0], target[:, 1], 1 - target[:, 0] - target[:, 1])
        has_ou &= _inside(target[:, 2], 1 - target[:, 2])
        target[~has_1x2, :2] = own[~has_1x2, :2]
        target[~has_ou, 2] = own[~has_ou, 2]
        t_h, t_d, t_o = target.T
        row_ok = _inside(t_h, t_d, 1 - t_h - t_d, t_o, 1 - t_o)  # the base's part may not be
        has_1x2 &= row_ok
        has_ou &= row_ok
        move = has_1x2 | has_ou
        out = grid.copy()
        if move.any():
            out[move] = tilt(grid[move], target[move])
        self.last_used = mk.assign(used_1x2=has_1x2, used_ou=has_ou)
        # expected goals of the grid actually returned (the tilted one where prices were used),
        # so they agree with its probabilities; the last bin counts as MAX_GOALS goals
        lam_h = (out * GOALS[None, :, None]).sum(axis=(1, 2))
        lam_a = (out * GOALS[None, None, :]).sum(axis=(1, 2))
        return markets_from_grid(out, lam_h=lam_h, lam_a=lam_a)

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
    "market_tilt_shots": lambda: MarketTilt(
        _base("shots_xg_sot_joint_rw"), 1.0, 1.0, "market_tilt_shots"
    ),
}
