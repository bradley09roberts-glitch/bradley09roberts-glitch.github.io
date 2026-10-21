"""Model + market tilt: exact targets, coherence, and which prices may be used.

The prices here are SYNTHETIC (made-up numbers for fictional teams), used only by these tests.
"""

from datetime import UTC, datetime

import numpy as np
import pandas as pd
import pytest

from footy.modeling.grid import check_coherent, markets_from_grid, poisson_grid
from footy.modeling.lab.market_blend import MarketTilt, consensus, tilt
from footy.modeling.market import devig_shin


class Fixed:
    """A base model that returns the same Poisson grid for every fixture."""

    name = "fixed"
    markets = frozenset({"1X2", "OU", "BTTS"})

    def fit(self, history, cutoff):
        pass

    def predict(self, fixtures):
        n = len(fixtures)
        return markets_from_grid(poisson_grid(np.full(n, 1.5), np.full(n, 1.1), rho=-0.05))

    def params(self):
        return {}


def test_tilt_hits_targets_and_stays_a_distribution():
    g = poisson_grid(np.array([1.4, 0.9, 2.2]), np.array([1.1, 1.6, 0.5]), rho=-0.05)
    t = np.array([[0.45, 0.27, 0.52], [0.25, 0.30, 0.40], [0.70, 0.18, 0.62]])
    m = markets_from_grid(tilt(g, t))
    np.testing.assert_allclose(np.c_[m.p1x2[:, :2], m.p_over[2.5]], t, atol=1e-9)
    assert not check_coherent(m)


def test_tilt_to_own_probabilities_changes_nothing():
    g = poisson_grid(np.array([1.3]), np.array([1.2]), rho=-0.08)
    m = markets_from_grid(g)
    q = tilt(g, np.array([[m.p1x2[0, 0], m.p1x2[0, 1], m.p_over[2.5][0]]]))
    np.testing.assert_allclose(q, g, atol=1e-12)


def _raw(rows):
    cols = [
        "division",
        "date",
        "home_team",
        "away_team",
        "bookmaker",
        "market_key",
        "selection",
        "price",
        "quote_time_utc",
    ]
    return pd.DataFrame(rows, columns=cols)


def _prices(home, away, day, quoted, h=2.0, d=3.5, a=4.0, o=1.8, u=2.1):
    rows = []
    for book in ("B365", "Avg"):
        rows += [
            ("E0", day, home, away, book, "1X2", s, p, quoted)
            for s, p in (("H", h), ("D", d), ("A", a))
        ]
        rows += [
            ("E0", day, home, away, book, "OU_2.5", s, p, quoted) for s, p in (("O", o), ("U", u))
        ]
    return rows


def test_consensus_is_devigged_and_ignores_other_books():
    q = pd.Timestamp("2025-09-12 16:00", tz="UTC")
    rows = _prices("eng-a", "eng-b", "2025-09-13", q)
    rows += [("E0", "2025-09-13", "eng-a", "eng-b", "PS", "1X2", s, 9.0, q) for s in "HDA"]
    c = consensus(_raw(rows))
    assert len(c) == 1
    p = c.iloc[0]
    np.testing.assert_allclose(
        [p.p_H, p.p_D, p.p_A], devig_shin(np.array([[2.0, 3.5, 4.0]]))[0], atol=1e-12
    )
    assert p.p_O == pytest.approx(devig_shin(np.array([[1.8, 2.1]]))[0, 0])
    assert p.n_books_1x2 == 2


def _fixtures(deadline, kickoff=None):
    return pd.DataFrame(
        {
            "id": [1, 2],
            "division": "E0",
            "date": pd.to_datetime(["2025-09-13", "2025-09-13"]),
            "home_team": ["eng-a", "eng-c"],
            "away_team": ["eng-b", "eng-d"],
            "kickoff_utc": pd.to_datetime([kickoff, kickoff], utc=True),
            "deadline_utc": pd.to_datetime([deadline, deadline], utc=True),
        }
    )


def test_only_prices_quoted_by_the_decision_are_used():
    on_time = pd.Timestamp("2025-09-12 16:00", tz="UTC")
    late = pd.Timestamp("2025-09-12 18:00", tz="UTC")
    book = consensus(
        _raw(
            _prices("eng-a", "eng-b", "2025-09-13", on_time)
            + _prices("eng-c", "eng-d", "2025-09-13", late, h=1.5)
        )
    )
    m = MarketTilt(Fixed, 1.0, 1.0, "t", prices=lambda: book)
    m.fit(pd.DataFrame(), pd.Timestamp("2025-09-11 23:00", tz="UTC"))
    out = m.predict(_fixtures("2025-09-12 16:00", "2025-09-13 14:00"))
    base = Fixed().predict(_fixtures("2025-09-12 16:00"))
    used = m.last_used
    assert used.used_1x2.tolist() == [True, False]  # the late quote is refused
    np.testing.assert_allclose(out.p1x2[0], book.iloc[0][["p_H", "p_D", "p_A"]].to_numpy(float))
    np.testing.assert_allclose(out.grid[1], base.grid[1])  # no usable price: base unchanged
    assert not check_coherent(out)


def test_prices_quoted_after_kickoff_are_refused():
    q = pd.Timestamp("2025-09-12 16:00", tz="UTC")
    book = consensus(_raw(_prices("eng-a", "eng-b", "2025-09-13", q)))
    m = MarketTilt(Fixed, 1.0, 1.0, "t", prices=lambda: book)
    m.fit(pd.DataFrame(), q)
    m.predict(_fixtures("2025-09-12 16:00", "2025-09-12 15:00"))
    assert not m.last_used.used_1x2.any()


def test_live_needs_a_decision_time():
    q = pd.Timestamp("2026-10-23 16:00", tz="UTC")
    book = consensus(_raw(_prices("eng-a", "eng-b", "2025-09-13", q)))
    m = MarketTilt(Fixed, 0.8, 0.6, "t", prices=lambda: book)
    m.fit(pd.DataFrame(), q)
    fx = _fixtures("2025-09-12 16:00").drop(columns="deadline_utc")
    with pytest.raises(ValueError, match="decision time"):
        m.predict(fx)
    m.decision_time = datetime(2026, 10, 23, 15, 0, tzinfo=UTC)  # before the quote: refused
    m.predict(fx)
    assert not m.last_used.used_1x2.any()
    m.decision_time = datetime(2026, 10, 23, 17, 0, tzinfo=UTC)
    out = m.predict(fx)
    assert m.last_used.used_1x2.tolist() == [True, False]
    base = Fixed().predict(fx)
    mk = book.iloc[0]
    want = 0.8 * mk.p_H + 0.2 * base.p1x2[0, 0]
    assert out.p1x2[0, 0] == pytest.approx(want, abs=1e-9)
