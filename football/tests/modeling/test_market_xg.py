"""Market-implied grids: solving back from closing probabilities recovers the grid.

Every grid, price and match here is SYNTHETIC (made-up rates and fictional teams)."""

from datetime import date

import numpy as np
import pandas as pd
import pytest

import fdextrafixtures as fx
from footy.ingest.fd_update import run_fd_update
from footy.modeling.grid import GOALS, markets_from_grid, poisson_grid
from footy.modeling.market import devig_shin
from footy.modeling.market_xg import (
    FALLBACK_RHO,
    implied_grid,
    implied_grid_1x2,
    market_expected_goals,
    solve_expected_goals,
)

# SYNTHETIC rates: home favourite, away favourite, even, low- and high-scoring.
LH = np.array([1.2, 2.1, 0.8, 1.6, 0.6, 2.8])
LA = np.array([1.0, 0.7, 1.5, 1.6, 0.5, 1.9])


def _targets(lh, la, rho):
    m = markets_from_grid(poisson_grid(lh, la, rho))
    return m.p1x2, m.p_over[2.5]


def _xg(g):
    return (g * GOALS[None, :, None]).sum((1, 2)), (g * GOALS[None, None, :]).sum((1, 2))


def test_implied_grid_recovers_known_rates():
    lh, la, rho = LH[:4], LA[:4], -0.07
    g = poisson_grid(lh, la, rho)
    m = markets_from_grid(g)
    target = np.stack([m.p1x2[:, 0], m.p1x2[:, 1], m.p_over[2.5]], axis=1)
    x, g2, ok = implied_grid(target)
    assert ok.all()
    np.testing.assert_allclose(np.exp(x[:, 0]), lh, rtol=1e-4)
    np.testing.assert_allclose(np.exp(x[:, 1]), la, rtol=1e-4)
    np.testing.assert_allclose(x[:, 2], rho, atol=1e-4)
    np.testing.assert_allclose(
        (g2 * GOALS[None, :, None]).sum((1, 2)), (g * GOALS[None, :, None]).sum((1, 2)), rtol=1e-6
    )


@pytest.mark.parametrize("rho", [-0.12, -0.07, 0.0, 0.05])
def test_implied_grid_1x2_round_trip(rho):
    """With rho fixed at the grid's own value, (home rate, away rate) come back exactly and
    the solved grid reproduces the whole 1X2."""
    p1x2, _ = _targets(LH, LA, rho)
    x, g, ok = implied_grid_1x2(p1x2[:, :2], rho)
    assert ok.all()
    np.testing.assert_allclose(np.exp(x[:, 0]), LH, rtol=1e-5)
    np.testing.assert_allclose(np.exp(x[:, 1]), LA, rtol=1e-5)
    assert (x[:, 2] == rho).all()
    np.testing.assert_allclose(markets_from_grid(g).p1x2, p1x2, atol=1e-7)


def test_implied_grid_1x2_with_another_rho_still_matches_the_1x2():
    """A different rho still reproduces the 1X2 exactly, but moves the rates: without the
    draws a negative rho adds, the same draw probability needs fewer goals."""
    p1x2, _ = _targets(LH, LA, -0.12)
    x, g, ok = implied_grid_1x2(p1x2[:, :2], 0.0)
    assert ok.all()
    np.testing.assert_allclose(markets_from_grid(g).p1x2, p1x2, atol=1e-7)
    xh, xa = _xg(g)
    xh0, xa0 = _xg(poisson_grid(LH, LA, -0.12))
    assert (xh < xh0).all() and (xa < xa0).all()


def _frames(ids_both, ids_only, rhos):
    """SYNTHETIC consensus tables: matches in `ids_both` have 1X2 and O/U 2.5, those in
    `ids_only` 1X2 alone; match i uses rates LH[i], LA[i] and rho rhos[i]."""
    ids = [*ids_both, *ids_only]
    p1x2, pover = _targets(LH[ids], LA[ids], np.asarray(rhos)[ids])
    one = pd.DataFrame(p1x2, index=pd.Index(ids, name="match_id"), columns=["H", "D", "A"])
    ou = pd.DataFrame(
        {"O": pover[: len(ids_both)], "U": 1 - pover[: len(ids_both)]},
        index=pd.Index(ids_both, name="match_id"),
    )
    return one, ou, pd.Series(2, index=one.index)


def test_matches_without_ou_use_the_median_rho_of_those_with_both():
    rhos = [-0.03, -0.09, -0.06, -0.06, -0.06, -0.06]
    one, ou, n_books = _frames([0, 1, 2], [3, 4, 5], rhos)
    out = solve_expected_goals(one, ou, n_books)
    assert list(out.index) == [0, 1, 2, 3, 4, 5]
    assert list(out.method) == ["1x2_ou25"] * 3 + ["1x2"] * 3
    assert out.attrs["rho_1x2_only"] == pytest.approx(-0.06, abs=1e-4)
    assert out.attrs["rho_1x2_only_source"] == "median_of_1x2_ou25"
    assert (out.attrs["solved_1x2_ou25"], out.attrs["solved_1x2_only"]) == (3, 3)
    assert out.attrs["not_converged"] == 0
    # Exact three-parameter solve where O/U exists.
    np.testing.assert_allclose(out.rho.iloc[:3], rhos[:3], atol=1e-4)
    # The 1X2-only matches were built with rho = the median, so their rates come back.
    xh, xa = _xg(poisson_grid(LH, LA, np.asarray(rhos)))
    np.testing.assert_allclose(out.xg_home, xh, rtol=1e-4)
    np.testing.assert_allclose(out.xg_away, xa, rtol=1e-4)
    assert (out.n_books == 2).all()


def test_given_rho_and_fallback_rho():
    one, ou, n_books = _frames([], [0, 1], [-0.05, -0.05])
    out = solve_expected_goals(one, ou, n_books)
    assert out.attrs["rho_1x2_only"] == FALLBACK_RHO
    assert out.attrs["rho_1x2_only_source"] == "fallback_constant"
    assert list(out.method) == ["1x2", "1x2"] and (out.rho == FALLBACK_RHO).all()
    out = solve_expected_goals(one, ou, n_books, rho_1x2_only=-0.05)
    assert out.attrs["rho_1x2_only_source"] == "given"
    xh, xa = _xg(poisson_grid(LH[:2], LA[:2], -0.05))
    np.testing.assert_allclose(out.xg_home, xh, rtol=1e-5)
    np.testing.assert_allclose(out.xg_away, xa, rtol=1e-5)


def test_ou_that_cannot_be_matched_falls_back_to_1x2_only():
    """An O/U 2.5 probability no Dixon-Coles grid reaches with this 1X2 (rho is bounded):
    the match still gets market xG, from its 1X2."""
    one, ou, n_books = _frames([0, 1], [], [-0.07, -0.07])
    ou.loc[1, ["O", "U"]] = [0.995, 0.005]
    out = solve_expected_goals(one, ou, n_books)
    assert list(out.method) == ["1x2_ou25", "1x2"]
    assert out.attrs["with_ou25_not_converged"] == 1
    assert out.attrs["rho_1x2_only"] == pytest.approx(-0.07, abs=1e-4)


def test_market_expected_goals_from_imported_extra_league_prices(migrated_engine, settings):
    """End to end on a SYNTHETIC extra-league file: closing 1X2 prices only, so every match
    gets 1X2-only market xG with the fallback rho, from the books in BOOKS (Pinnacle,
    Bet365 and the average; not Betfair Exchange or the maximum)."""
    settings.fd_inbox_dir.mkdir(parents=True, exist_ok=True)
    (settings.fd_inbox_dir / "new_AUT.csv").write_bytes(fx.csv_bytes())
    run_fd_update(
        migrated_engine,
        settings,
        today=date(2026, 6, 10),
        allow_download=False,
        reference=fx.REFERENCE,
    )
    out = market_expected_goals(migrated_engine)
    assert len(out) == 9 and set(out.method) == {"1x2"} and (out.n_books == 3).all()
    odds = [float(v) for v in fx.ODDS.split(",")]
    books = [odds[0:3], odds[6:9], odds[12:15]]  # PSC, AvgC, B365C
    target = devig_shin(np.array(books)).mean(axis=0)
    x, g, ok = implied_grid_1x2(target[None, :2], FALLBACK_RHO)
    xh, xa = _xg(g)
    np.testing.assert_allclose(out.xg_home, xh[0], rtol=1e-9)
    np.testing.assert_allclose(out.xg_away, xa[0], rtol=1e-9)
