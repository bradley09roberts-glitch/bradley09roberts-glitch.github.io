"""Market-implied grids: solving back from closing probabilities recovers the grid.

Every grid, price and match here is SYNTHETIC (made-up rates and fictional teams)."""

from datetime import date

import numpy as np
import pandas as pd
import pytest

import fdextrafixtures as fx
import fdfixtures as fd
from footy.ingest.fd_update import run_fd_update
from footy.modeling.grid import GOALS, markets_from_grid, poisson_grid
from footy.modeling.market import devig_shin
from footy.modeling.market_xg import (
    FALLBACK_RHO,
    SCOPES,
    implied_grid,
    implied_grid_1x2,
    market_expected_goals,
    scope_filter,
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


def test_market_xg_from_on_or_after_the_cutoff_never_reaches_a_uefa_prediction(monkeypatch):
    """Leakage guard for `market_alpha` (SYNTHETIC two-league data and made-up market xG):
    the walk-forward fit uses market xG only for domestic matches in its history, so
    rewriting the market xG of every domestic match dated on or after a batch's cutoff
    (the cutoff day itself and the match day included) leaves that batch's predictions
    exactly as they were, while rewriting the earlier matches' market xG moves them."""
    import footy.modeling.market_xg as mx
    from footy.modeling.euro import EuroSpec, PanEuropeanDC, run_euro_backtest

    from .test_euro_model import two_leagues

    df, _ = two_leagues(n_weeks=13)
    target = df[df.kind == "euro"].date.max()
    cutoff = target - pd.Timedelta(days=1)
    # Two more SYNTHETIC domestic matches on the cutoff day and on the match day itself.
    extra = df[df.kind == "domestic"].tail(2).copy()
    extra["id"] = [df.id.max() + 1, df.id.max() + 2]
    extra["date"] = [cutoff, target]
    df = pd.concat([df, extra], ignore_index=True).sort_values(["date", "id"])
    df["outcome"] = np.select(
        [df.home_goals > df.away_goals, df.home_goals == df.away_goals], [0, 1], 2
    )
    dom = df[df.kind == "domestic"]
    later = dom.date >= cutoff
    assert later.sum() >= 2 and (~later).sum() > 0

    def market_xg(seed_before, seed_later):
        xg = pd.DataFrame(index=pd.Index(dom.id, name="match_id"), columns=["xg_home", "xg_away"])
        for mask, seed in ((~later, seed_before), (later, seed_later)):
            rng = np.random.default_rng(seed)
            xg.loc[dom.id[mask], ["xg_home", "xg_away"]] = rng.uniform(0.3, 3.0, (mask.sum(), 2))
        return xg.astype(float)

    def predictions(xg):
        monkeypatch.setattr(mx, "default_market_xg", lambda scope="all": xg)
        spec = EuroSpec(
            lambda: PanEuropeanDC(xi=0.002, market_alpha=0.0), ["2324"], ("UCL",), min_history=1
        )
        led = run_euro_backtest(df, spec, {})
        led = led[(led.status == "predicted") & (led.date == target.date().isoformat())]
        assert len(led) == 1
        return led[["p_H", "p_D", "p_A", "p_over_2.5", "p_btts"]].to_numpy(float)

    p = predictions(market_xg(1, 2))
    np.testing.assert_array_equal(predictions(market_xg(1, 3)), p)  # later values: no effect
    assert np.abs(predictions(market_xg(4, 2)) - p).max() > 1e-3  # earlier values: used


def test_scope_filter_keeps_only_the_rows_of_its_scope():
    """SYNTHETIC closing price rows: two top divisions, a second tier and an extra league,
    with Pinnacle, Bet365, the average and Betfair Exchange on both sides of 23 Jul 2025."""
    rows = []
    for mid, (div, day) in enumerate(
        [("E0", "2025-07-22"), ("SP1", "2025-07-23"), ("E1", "2024-03-02"), ("AUT", "2025-08-02")]
    ):
        for book in ("PS", "B365", "Avg", "BFE"):
            for sel, price in (("H", 2.1), ("D", 3.4), ("A", 3.5)):
                rows.append((mid, book, "1X2", sel, price, div, day))
    raw = pd.DataFrame(
        rows,
        columns=[
            "match_id",
            "bookmaker",
            "market_key",
            "selection",
            "price",
            "division",
            "match_date",
        ],
    )
    pinned = scope_filter(raw, "top5_exact")
    assert set(pinned.division) == {"E0", "SP1"}
    assert set(pinned[pinned.division == "SP1"].bookmaker) == {"PS", "B365", "Avg", "BFE"}
    assert len(scope_filter(raw, "all")) == len(raw)
    d014 = scope_filter(raw, "all_d014")
    assert set(d014.division) == {"E0", "SP1", "E1", "AUT"}
    ps = d014[d014.bookmaker == "PS"]
    # Pinnacle is kept up to 22 Jul 2025 and dropped from 23 Jul 2025 (D-014).
    assert set(ps.division) == {"E0", "E1"}
    assert len(d014) == len(raw) - 2 * 3


def test_scope_exact_only_never_uses_the_1x2_only_solve():
    rhos = [-0.03, -0.09, -0.06, -0.06, -0.06, -0.06]
    one, ou, n_books = _frames([0, 1, 2], [3, 4, 5], rhos)
    out = solve_expected_goals(one, ou, n_books, exact_only=True)
    assert list(out.index) == [0, 1, 2] and set(out.method) == {"1x2_ou25"}
    assert out.attrs["without_ou25_left_out"] == 3 and out.attrs["not_converged"] == 0
    assert out.attrs["rho_1x2_only"] is None
    full = solve_expected_goals(one, ou, n_books)
    pd.testing.assert_frame_equal(out, full.loc[[0, 1, 2]])


def test_market_scopes_on_imported_prices(migrated_engine, settings):
    """End to end on SYNTHETIC files: a Premier League file (Pinnacle closing 1X2 and O/U 2.5,
    every match after 23 Jul 2025; one match without closing O/U) and an Austrian extra-league
    file (closing 1X2 only). The pinned scope sees only the top division's exact solves; the
    D-014 scope drops Pinnacle after 23 Jul 2025, which leaves the Premier League matches
    without closing prices and the Austrian ones with Bet365 and the average."""
    settings.fd_inbox_dir.mkdir(parents=True, exist_ok=True)
    e0 = list(fd.ROWS)
    e0[1] = fd.replace_field(fd.replace_field(e0[1], "PC>2.5", ""), "PC<2.5", "")
    (settings.fd_inbox_dir / "2526_E0.csv").write_bytes(fd.csv_bytes(e0))
    (settings.fd_inbox_dir / "new_AUT.csv").write_bytes(fx.csv_bytes())
    run_fd_update(
        migrated_engine,
        settings,
        today=date(2026, 6, 10),
        allow_download=False,
        reference=(fd.TEAMS + fx.TEAMS, fd.ALIASES + fx.ALIASES),
    )
    from sqlalchemy import text

    with migrated_engine.connect() as conn:
        div = dict(conn.execute(text("SELECT id, division FROM matches")).all())

    every = market_expected_goals(migrated_engine)
    assert every.attrs["scope"] == "all"
    by_div = every.index.map(div)
    assert (by_div == "E0").sum() == 3 and (by_div == "AUT").sum() == 9
    assert sorted(every[by_div == "E0"].method) == ["1x2", "1x2_ou25", "1x2_ou25"]
    assert (every[by_div == "AUT"].n_books == 3).all()

    pinned = market_expected_goals(migrated_engine, scope="top5_exact")
    assert set(pinned.index.map(div)) == {"E0"} and set(pinned.method) == {"1x2_ou25"}
    assert len(pinned) == 2 and pinned.attrs["without_ou25_left_out"] == 1
    # Same exact solves as in the full table (the extra league adds no O/U 2.5 prices).
    pd.testing.assert_frame_equal(
        pinned[["xg_home", "xg_away", "rho"]],
        every.loc[pinned.index, ["xg_home", "xg_away", "rho"]],
    )

    d014 = market_expected_goals(migrated_engine, scope="all_d014")
    assert set(d014.index.map(div)) == {"AUT"} and len(d014) == 9
    assert (d014.n_books == 2).all() and d014.attrs["rho_1x2_only"] == FALLBACK_RHO
    odds = [float(v) for v in fx.ODDS.split(",")]
    target = devig_shin(np.array([odds[6:9], odds[12:15]])).mean(axis=0)  # AvgC, B365C
    _, g, _ = implied_grid_1x2(target[None, :2], FALLBACK_RHO)
    xh, xa = _xg(g)
    np.testing.assert_allclose(d014.xg_home, xh[0], rtol=1e-9)
    np.testing.assert_allclose(d014.xg_away, xa[0], rtol=1e-9)


def test_pan_european_dc_reads_the_market_xg_of_its_own_scope(monkeypatch):
    """The model asks for its scope's market xG (SYNTHETIC two-league data), and an unknown
    scope is refused."""
    import footy.modeling.market_xg as mx
    from footy.modeling.euro import PanEuropeanDC

    from .test_euro_model import two_leagues

    df, _ = two_leagues(n_weeks=6)
    asked = []

    def fake(scope="all"):
        asked.append(scope)
        return pd.DataFrame(columns=["xg_home", "xg_away"], dtype=float)

    monkeypatch.setattr(mx, "default_market_xg", fake)
    m = PanEuropeanDC(xi=0.002, market_alpha=0.25, market_scope="top5_exact")
    m.fit(df, df.date.max() + pd.Timedelta(days=1))
    assert asked == ["top5_exact"]
    assert m.params()["market_scope"] == "top5_exact" and m.name.endswith("_ma0.25_top5_exact")
    assert "market_scope" not in PanEuropeanDC().params()
    assert set(SCOPES) >= {"all", "top5_exact", "all_d014"}
    with pytest.raises(ValueError):
        PanEuropeanDC(market_alpha=0.25, market_scope="top5")
