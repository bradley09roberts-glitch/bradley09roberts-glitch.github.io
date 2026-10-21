"""Market-implied expected goals of past matches, from their closing prices.

For every imported match with closing 1X2 and O/U 2.5 prices (football-data's Pinnacle,
Bet365 and market-average closing prices where quoted, Shin de-vigged and averaged), find the
Dixon-Coles grid (home rate, away rate, rho) that reproduces P(home win), P(draw) and
P(over 2.5) exactly, and return its expected home and away goals. They say how strong each
side was believed to be at kickoff, with much less noise than the goals actually scored, so a
rating model can use them as part of its targets.

A match's closing price is known at its kickoff, before any later prediction. Models only see
matches final before their cutoff, so using these values for those matches adds no future
information.
"""

from functools import cache

import numpy as np
import pandas as pd
from sqlalchemy import inspect, text

from footy.modeling.grid import GOALS, poisson_grid
from footy.modeling.market import devig_shin

BOOKS = ("PS", "B365", "Avg")
_HOME = (GOALS[:, None] > GOALS[None, :]).astype(float)
_DRAW = np.eye(len(GOALS))
_OVER = ((GOALS[:, None] + GOALS[None, :]) > 2.5).astype(float)


def _probs(x: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    g = poisson_grid(np.exp(x[:, 0]), np.exp(x[:, 1]), x[:, 2])
    p = np.stack([(g * _HOME).sum((1, 2)), (g * _DRAW).sum((1, 2)), (g * _OVER).sum((1, 2))], 1)
    return p, g


def implied_grid(target: np.ndarray, iters: int = 40, tol: float = 1e-8):
    """Solve (log home rate, log away rate, rho) per row so that the grid's P(home), P(draw)
    and P(over 2.5) equal `target` (n, 3). Damped Newton with a finite-difference Jacobian.
    Returns (x, grid, converged)."""
    n = len(target)
    x = np.tile([np.log(1.45), np.log(1.15), -0.05], (n, 1))
    eps = 1e-6
    for _ in range(iters):
        p, _ = _probs(x)
        r = p - target
        if np.abs(r).max() < tol:
            break
        jac = np.empty((n, 3, 3))
        for k in range(3):
            dx = x.copy()
            dx[:, k] += eps
            jac[:, :, k] = (_probs(dx)[0] - p) / eps
        step = np.linalg.solve(jac + 1e-10 * np.eye(3), r[:, :, None])[:, :, 0]
        step = np.clip(step, -0.5, 0.5)
        x = x - step
        x[:, 2] = np.clip(x[:, 2], -0.3, 0.3)
    p, g = _probs(x)
    return x, g, np.abs(p - target).max(axis=1) < 1e-5


def market_expected_goals(engine) -> pd.DataFrame:
    """Index: match id. Columns: xg_home, xg_away (expected goals of the implied grid), rho,
    n_books. Matches whose prices can't be matched exactly are left out."""
    if not inspect(engine).has_table("fd_odds"):
        return pd.DataFrame(columns=["xg_home", "xg_away", "rho", "n_books"])
    sql = (
        "SELECT match_id, bookmaker, market_key, selection, price FROM fd_odds "
        "WHERE phase = 'closing' AND market_key IN ('1X2', 'OU_2.5')"
    )
    with engine.connect() as conn:
        raw = pd.read_sql(text(sql), conn)
    raw = raw[raw.bookmaker.isin(BOOKS)]
    parts = {}
    for market, sels in (("1X2", ["H", "D", "A"]), ("OU_2.5", ["O", "U"])):
        w = raw[raw.market_key == market].pivot_table(
            index=["match_id", "bookmaker"], columns="selection", values="price", aggfunc="first"
        )
        w = w.reindex(columns=sels).dropna()
        p = pd.DataFrame(devig_shin(w.to_numpy()), index=w.index, columns=sels)
        parts[market] = p.groupby(level="match_id").mean()
        if market == "1X2":
            n_books = p.groupby(level="match_id").size()
    both = parts["1X2"].join(parts["OU_2.5"], how="inner")
    if both.empty:
        return pd.DataFrame(columns=["xg_home", "xg_away", "rho", "n_books"])
    target = both[["H", "D", "O"]].to_numpy()
    x, g, ok = implied_grid(target)
    xg_h = (g * GOALS[None, :, None]).sum((1, 2))
    xg_a = (g * GOALS[None, None, :]).sum((1, 2))
    out = pd.DataFrame(
        {"xg_home": xg_h, "xg_away": xg_a, "rho": x[:, 2], "n_books": n_books.reindex(both.index)},
        index=both.index,
    )
    return out[ok]


@cache
def default_market_xg() -> pd.DataFrame:
    from footy.config import get_settings
    from footy.db import make_engine

    return market_expected_goals(make_engine(get_settings().database_url))
