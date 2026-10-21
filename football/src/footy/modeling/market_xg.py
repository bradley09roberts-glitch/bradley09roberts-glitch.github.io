"""Market-implied expected goals of past matches, from their closing prices.

For every imported match with closing 1X2 prices (football-data's Pinnacle, Bet365 and
market-average closing prices where quoted, Shin de-vigged and averaged), find a Dixon-Coles
grid that reproduces them and return its expected home and away goals. They say how strong
each side was believed to be at kickoff, with much less noise than the goals actually scored,
so a rating model can use them as part of its targets.

- **1X2 and O/U 2.5** (method "1x2_ou25"): the grid (home rate, away rate, rho) that
  reproduces P(home win), P(draw) and P(over 2.5) exactly.
- **1X2 only** (method "1x2"): matches without closing O/U 2.5 prices (every extra-league
  file, and 2018/19 in the main layout), or whose three-parameter solve does not converge.
  rho is fixed and (home rate, away rate) reproduce P(home win) and P(draw) exactly. The
  fixed rho is the median rho of the matches solved with both markets, computed on each call
  and returned in `attrs["rho_1x2_only"]` (on 21 Oct 2026, every league imported: see
  DECISIONS.md D-022). It is one constant for every season, so an early season borrows it
  from later ones; it is a shape parameter, and expected goals barely move with it.

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
# Fixed rho for a 1X2-only solve when no match in the call has both markets (tests, a
# database without O/U prices): the median over the five top divisions before European
# prices were imported (21 Oct 2026, 12,797 matches).
FALLBACK_RHO = -0.078
COLUMNS = ["xg_home", "xg_away", "rho", "n_books", "method"]
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


def implied_grid_1x2(target: np.ndarray, rho: float, iters: int = 40, tol: float = 1e-8):
    """Solve (log home rate, log away rate) per row, with rho fixed, so that the grid's
    P(home) and P(draw) equal `target` (n, 2); P(away) follows. Damped Newton with a
    finite-difference Jacobian. Returns (x (n, 3) with rho in the last column, grid,
    converged)."""
    n = len(target)
    x = np.tile([np.log(1.45), np.log(1.15), rho], (n, 1))
    eps = 1e-6
    for _ in range(iters):
        p, _ = _probs(x)
        r = p[:, :2] - target
        if np.abs(r).max() < tol:
            break
        jac = np.empty((n, 2, 2))
        for k in range(2):
            dx = x.copy()
            dx[:, k] += eps
            jac[:, :, k] = (_probs(dx)[0][:, :2] - p[:, :2]) / eps
        step = np.linalg.solve(jac + 1e-10 * np.eye(2), r[:, :, None])[:, :, 0]
        x[:, :2] -= np.clip(step, -0.5, 0.5)
    p, g = _probs(x)
    return x, g, np.abs(p[:, :2] - target).max(axis=1) < 1e-5


def _expected_goals(g: np.ndarray) -> tuple[np.ndarray, np.ndarray]:
    return (g * GOALS[None, :, None]).sum((1, 2)), (g * GOALS[None, None, :]).sum((1, 2))


def consensus_closing(raw: pd.DataFrame) -> tuple[pd.DataFrame, pd.DataFrame, pd.Series]:
    """From closing price rows (match_id, bookmaker, market_key, selection, price), the
    Shin de-vigged probabilities averaged over BOOKS: (1X2 with columns H, D, A; O/U 2.5
    with O, U; number of books behind each 1X2), indexed by match id. Incomplete sets are
    left out."""
    raw = raw[raw.bookmaker.isin(BOOKS)]
    parts, n_books = {}, pd.Series(dtype=int)
    for market, sels in (("1X2", ["H", "D", "A"]), ("OU_2.5", ["O", "U"])):
        w = raw[raw.market_key == market].pivot_table(
            index=["match_id", "bookmaker"], columns="selection", values="price", aggfunc="first"
        )
        w = w.reindex(columns=sels).dropna()
        if w.empty:
            parts[market] = pd.DataFrame(columns=sels, dtype=float)
            continue
        p = pd.DataFrame(devig_shin(w.to_numpy()), index=w.index, columns=sels)
        parts[market] = p.groupby(level="match_id").mean()
        if market == "1X2":
            n_books = p.groupby(level="match_id").size()
    return parts["1X2"], parts["OU_2.5"], n_books


def solve_expected_goals(
    p1x2: pd.DataFrame, pou: pd.DataFrame, n_books: pd.Series, rho_1x2_only: float | None = None
) -> pd.DataFrame:
    """Market expected goals for every match in `p1x2` (index match id; columns H, D, A),
    with `pou` (columns O, U) where it has the match. Columns: COLUMNS. `attrs` holds the
    rho used for the 1X2-only solve, its source and the counts per method. Matches whose
    solve does not converge are left out (counted in attrs)."""
    both = p1x2.join(pou, how="inner")
    frames, xg3 = [], pd.DataFrame(columns=COLUMNS)
    if len(both):
        x, g, ok = implied_grid(both[["H", "D", "O"]].to_numpy())
        xh, xa = _expected_goals(g)
        xg3 = pd.DataFrame(
            {"xg_home": xh, "xg_away": xa, "rho": x[:, 2], "method": "1x2_ou25"}, index=both.index
        )[ok]
        frames.append(xg3)
    if rho_1x2_only is not None:
        rho, source = float(rho_1x2_only), "given"
    elif len(xg3):
        rho, source = float(np.median(xg3.rho.to_numpy(float))), "median_of_1x2_ou25"
    else:
        rho, source = FALLBACK_RHO, "fallback_constant"
    rest = p1x2.loc[~p1x2.index.isin(xg3.index)]
    n_rest_ok = 0
    if len(rest):
        x, g, ok = implied_grid_1x2(rest[["H", "D"]].to_numpy(), rho)
        xh, xa = _expected_goals(g)
        xg2 = pd.DataFrame(
            {"xg_home": xh, "xg_away": xa, "rho": x[:, 2], "method": "1x2"}, index=rest.index
        )[ok]
        n_rest_ok = len(xg2)
        frames.append(xg2)
    out = (
        pd.concat(frames).sort_index()
        if frames
        else pd.DataFrame(columns=COLUMNS).rename_axis("match_id")
    )
    out["n_books"] = n_books.reindex(out.index)
    out = out[COLUMNS]
    out.attrs = {
        "rho_1x2_only": rho,
        "rho_1x2_only_source": source,
        "matches_1x2": len(p1x2),
        "solved_1x2_ou25": len(xg3),
        "solved_1x2_only": n_rest_ok,
        "with_ou25_not_converged": len(both) - len(xg3),
        "not_converged": len(p1x2) - len(out),
    }
    return out


def market_expected_goals(engine, rho_1x2_only: float | None = None) -> pd.DataFrame:
    """Index: match id. Columns: xg_home, xg_away (expected goals of the implied grid), rho,
    n_books (1X2 books averaged) and method ("1x2_ou25" or "1x2"). Every match with a
    closing 1X2 price from BOOKS is included unless its solve fails to converge."""
    if not inspect(engine).has_table("fd_odds"):
        return pd.DataFrame(columns=COLUMNS)
    sql = (
        "SELECT match_id, bookmaker, market_key, selection, price FROM fd_odds "
        "WHERE phase = 'closing' AND market_key IN ('1X2', 'OU_2.5')"
    )
    with engine.connect() as conn:
        raw = pd.read_sql(text(sql), conn)
    p1x2, pou, n_books = consensus_closing(raw)
    if p1x2.empty:
        return pd.DataFrame(columns=COLUMNS)
    return solve_expected_goals(p1x2, pou, n_books, rho_1x2_only)


@cache
def default_market_xg() -> pd.DataFrame:
    from footy.config import get_settings
    from footy.db import make_engine

    return market_expected_goals(make_engine(get_settings().database_url))
