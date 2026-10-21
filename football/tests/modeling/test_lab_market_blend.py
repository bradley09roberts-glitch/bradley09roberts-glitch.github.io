"""Model + market tilt: exact targets, coherence, and which prices may be used.

The prices here are SYNTHETIC (made-up numbers for fictional teams), used only by these tests.
"""

from datetime import UTC, datetime

import numpy as np
import pandas as pd
import pytest

from footy.modeling.grid import check_coherent, markets_from_grid, poisson_grid
from footy.modeling.lab.market_blend import MarketTilt, combine_prices, consensus, tilt
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


# --- Audit of 21 Oct 2026: numerics, staleness and source mixing (all numbers SYNTHETIC) ---


@pytest.mark.parametrize(
    ("lam_h", "lam_a", "rho", "target"),
    [
        (0.3, 0.3, -0.06, (0.047, 0.455, 0.173)),  # the old Newton iteration diverged here
        (0.5, 0.5, -0.12, (0.05, 0.06, 0.5)),  # and here
        (0.3, 4.0, -0.06, (0.057, 0.037, 0.753)),  # and here
        (1.3, 1.2, -0.05, (0.93, 0.05, 0.80)),  # heavy home favourite
        (2.8, 0.4, -0.05, (0.04, 0.06, 0.20)),  # the reverse: an away certainty, few goals
        (1.4, 1.1, -0.05, (0.42, 0.15, 0.20)),  # draw 0.15, over 2.5 near 0.2
        (0.6, 0.5, 0.0, (0.40, 0.25, 0.80)),  # over 2.5 near 0.8 from a low-scoring grid
        (6.0, 0.15, 0.1, (0.50, 0.30, 0.50)),  # near-zero cells in most of the grid
    ],
)
def test_tilt_converges_for_extreme_targets(lam_h, lam_a, rho, target):
    g = poisson_grid(np.array([lam_h]), np.array([lam_a]), rho=rho)
    t = np.array([target])
    q = tilt(g, t)
    m = markets_from_grid(q)
    np.testing.assert_allclose(np.c_[m.p1x2[:, :2], m.p_over[2.5]], t, atol=1e-12)
    assert np.isfinite(q).all() and (q >= 0).all()
    assert q.sum() == pytest.approx(1.0, abs=1e-12)
    assert not check_coherent(m)


def test_tilt_is_the_exponential_tilt():
    """The result is g * exp(a_result + b_over): the factor is constant on each of the six
    result x over-2.5 blocks, which is what makes it the minimum-KL change."""
    g = poisson_grid(np.array([1.7]), np.array([0.8]), rho=-0.07)
    q = tilt(g, np.array([[0.30, 0.33, 0.35]]))[0]
    ratio = np.log(q / g[0])
    over = np.add.outer(np.arange(11), np.arange(11)) > 2.5
    home, draw = np.greater.outer(np.arange(11), np.arange(11)), np.eye(11, dtype=bool)
    away = ~home & ~draw
    for result in (home, draw, away):
        for side in (over, ~over):
            block = ratio[result & side]
            assert block.max() - block.min() < 1e-9
    # and the log factor is additive: result effect + over effect
    eff = {
        (r, s): ratio[rm & sm][0]
        for r, rm in enumerate((home, draw, away))
        for s, sm in enumerate((over, ~over))
    }
    for r in range(3):
        assert eff[(r, 0)] - eff[(r, 1)] == pytest.approx(eff[(0, 0)] - eff[(0, 1)], abs=1e-9)


def test_tilt_refuses_targets_outside_the_simplex():
    g = poisson_grid(np.array([1.3]), np.array([1.1]))
    for bad in ((0.6, 0.4, 0.5), (0.5, 0.2, 1.0), (np.nan, 0.2, 0.5), (0.0, 0.3, 0.5)):
        with pytest.raises(ValueError, match="strictly inside"):
            tilt(g, np.array([bad]))


def test_degenerate_market_keeps_the_base_for_that_market():
    """A consensus at 0 or 1 (impossible from real prices, but a table could say so) is not
    tilted to; the other market still is."""
    q = pd.Timestamp("2025-09-12 16:00", tz="UTC")
    book = consensus(_raw(_prices("eng-a", "eng-b", "2025-09-13", q)))
    book.loc[:, "p_O"] = 1.0
    m = MarketTilt(Fixed, 1.0, 1.0, "t", prices=lambda: book)
    m.fit(pd.DataFrame(), q)
    out = m.predict(_fixtures("2025-09-12 16:00", "2025-09-13 14:00"))
    base = Fixed().predict(_fixtures("2025-09-12 16:00"))
    assert m.last_used.used_1x2.tolist() == [True, False]
    assert not m.last_used.used_ou.any()
    np.testing.assert_allclose(out.p1x2[0], book.iloc[0][["p_H", "p_D", "p_A"]].to_numpy(float))
    assert out.p_over[2.5][0] == pytest.approx(base.p_over[2.5][0], abs=1e-12)
    assert not check_coherent(out)


def test_consensus_when_one_book_has_no_over_under():
    q = pd.Timestamp("2025-09-12 16:00", tz="UTC")
    rows = [
        r
        for r in _prices("eng-a", "eng-b", "2025-09-13", q)
        if not (r[4] == "Avg" and r[5] == "OU_2.5")
    ]
    rows = [
        (*r[:7], 1.7 if (r[4], r[6]) == ("Avg", "H") else r[7], r[8]) for r in rows
    ]  # the books disagree on 1X2
    c = consensus(_raw(rows)).iloc[0]
    assert (c.n_books_1x2, c.n_books_ou) == (2, 1)
    assert c.p_O == pytest.approx(devig_shin(np.array([[1.8, 2.1]]))[0, 0])  # Bet365 alone
    both = (devig_shin(np.array([[2.0, 3.5, 4.0]])) + devig_shin(np.array([[1.7, 3.5, 4.0]]))) / 2
    np.testing.assert_allclose([c.p_H, c.p_D, c.p_A], both[0], atol=1e-12)


def test_shin_on_two_way_markets_sums_to_one():
    for prices in ([1.01, 15.0], [1.8, 2.1], [1.95, 1.95], [3.4, 1.28], [1.05, 9.5]):
        p = devig_shin(np.array([prices]))[0]
        assert p.sum() == pytest.approx(1.0, abs=1e-12)
        assert (p > 0).all() and p[0] < 1 / prices[0] + 1e-12  # the margin only comes off
    p = devig_shin(np.array([[2.05, 2.05]]))[0]  # an underround (an exchange): normalised
    np.testing.assert_allclose(p, [0.5, 0.5])


def _live(home, away, day, published, retrieved, kickoff):
    raw = _raw(_prices(home, away, day, pd.Timestamp(retrieved, tz="UTC")))
    raw["published_utc"] = pd.Timestamp(published, tz="UTC")
    fx = pd.DataFrame(
        {
            "division": ["E0"],
            "date": pd.to_datetime([day]),
            "home_team": [home],
            "away_team": [away],
            "kickoff_utc": pd.to_datetime([kickoff], utc=True),
        }
    )
    return consensus(raw), fx


@pytest.mark.parametrize(
    ("day", "published", "retrieved", "now", "kickoff", "used"),
    [
        # Tuesday's file (published Tue 10:57) still current on Friday morning: refused for a
        # Saturday fixture, whose prices belong to Friday's collection.
        (
            "2026-10-24",
            "2026-10-20 10:57",
            "2026-10-23 07:30",
            "2026-10-23 08:00",
            "2026-10-24 14:00",
            False,
        ),
        # Friday's file for the same fixture: used.
        (
            "2026-10-24",
            "2026-10-23 13:10",
            "2026-10-23 15:00",
            "2026-10-23 16:00",
            "2026-10-24 14:00",
            True,
        ),
        # Tuesday's file for a Wednesday fixture (Levante v Athletic, 21 Oct): used.
        (
            "2026-10-21",
            "2026-10-20 10:57",
            "2026-10-21 03:06",
            "2026-10-21 04:00",
            "2026-10-21 18:00",
            True,
        ),
        # First retrieved on Friday but published on Thursday: an older collection, refused.
        (
            "2026-10-24",
            "2026-10-22 20:00",
            "2026-10-23 09:00",
            "2026-10-23 10:00",
            "2026-10-24 14:00",
            False,
        ),
        # Clocks go back on Sun 25 Oct: the collection day of a Sunday or Monday fixture is
        # Fri 23 Oct, which starts at 00:00 BST = Thu 22 Oct 23:00 UTC.
        (
            "2026-10-25",
            "2026-10-22 23:00",
            "2026-10-23 12:00",
            "2026-10-23 12:30",
            "2026-10-25 14:00",
            True,
        ),
        (
            "2026-10-26",
            "2026-10-22 22:59",
            "2026-10-23 12:00",
            "2026-10-23 12:30",
            "2026-10-26 20:00",
            False,
        ),
        # Retrieved after kickoff (a file still listing yesterday's games): refused.
        (
            "2026-10-20",
            "2026-10-20 10:57",
            "2026-10-21 03:06",
            "2026-10-21 04:00",
            "2026-10-20 18:45",
            False,
        ),
    ],
)
def test_live_prices_must_come_from_the_fixtures_own_collection(
    day, published, retrieved, now, kickoff, used
):
    book, fx = _live("eng-a", "eng-b", day, published, retrieved, kickoff)
    m = MarketTilt(Fixed, 1.0, 1.0, "t", prices=lambda: book)
    m.fit(pd.DataFrame(), pd.Timestamp(now, tz="UTC"))
    m.decision_time = pd.Timestamp(now, tz="UTC").to_pydatetime()
    m.predict(fx)
    assert m.last_used.used_1x2.tolist() == [used]


def test_consensus_keeps_the_earliest_publication_time():
    q = pd.Timestamp("2026-10-23 15:00", tz="UTC")
    raw = _raw(_prices("eng-a", "eng-b", "2026-10-24", q))
    raw["published_utc"] = pd.Timestamp("2026-10-23 13:00", tz="UTC")
    raw.loc[raw.bookmaker == "Avg", "published_utc"] = pd.Timestamp("2026-10-23 11:00", tz="UTC")
    c = consensus(raw).iloc[0]
    assert c.published_utc == pd.Timestamp("2026-10-23 11:00", tz="UTC")
    assert c.quote_time_utc == q
    no_col = consensus(_raw(_prices("eng-a", "eng-b", "2026-10-24", q))).iloc[0]
    assert no_col.published_utc == q  # a season file's price: published when quoted


def test_a_fixture_in_the_database_never_takes_the_live_snapshot():
    deadline = pd.Timestamp("2026-10-23 16:00", tz="UTC")
    db = _raw(_prices("eng-a", "eng-b", "2026-10-24", deadline))
    late = pd.Timestamp("2026-10-23 18:00", tz="UTC")
    snap = _raw(
        _prices("eng-a", "eng-b", "2026-10-24", late, h=1.5)
        + _prices("eng-c", "eng-d", "2026-10-24", late, h=3.0)
    )
    snap["published_utc"] = late
    book = combine_prices(db, snap)
    a = book.loc[("E0", pd.Timestamp("2026-10-24"), "eng-a", "eng-b")]
    assert a.quote_time_utc == deadline  # not the snapshot's later retrieval
    assert a.p_H == pytest.approx(devig_shin(np.array([[2.0, 3.5, 4.0]]))[0, 0])
    c = book.loc[("E0", pd.Timestamp("2026-10-24"), "eng-c", "eng-d")]
    assert c.quote_time_utc == late
    assert c.p_H == pytest.approx(devig_shin(np.array([[3.0, 3.5, 4.0]]))[0, 0])
    # a backtest of the database fixture still uses its price (it would have been refused if
    # the snapshot's retrieval time had replaced the deadline stamp)
    m = MarketTilt(Fixed, 1.0, 1.0, "t", prices=lambda: book)
    m.fit(pd.DataFrame(), deadline)
    fx = pd.DataFrame(
        {
            "division": ["E0"],
            "date": pd.to_datetime(["2026-10-24"]),
            "home_team": ["eng-a"],
            "away_team": ["eng-b"],
            "kickoff_utc": pd.to_datetime(["2026-10-24 14:00"], utc=True),
            "deadline_utc": [deadline],
        }
    )
    m.predict(fx)
    assert m.last_used.used_1x2.tolist() == [True]


def test_an_unknown_publication_time_stays_unknown():
    """One book's price has no publication time (a live file without Last-Modified): the
    fixture's publication time is unknown, not the other book's, and a live run refuses it.
    SYNTHETIC prices."""
    q = pd.Timestamp("2026-10-23 15:00", tz="UTC")
    raw = _raw(_prices("eng-a", "eng-b", "2026-10-24", q))
    raw["published_utc"] = pd.Timestamp("2026-10-23 13:00", tz="UTC")
    raw.loc[raw.bookmaker == "Avg", "published_utc"] = pd.NaT
    c = consensus(raw).iloc[0]
    assert pd.isna(c.published_utc) and c.n_books_1x2 == 2
    book, fx = _live(
        "eng-a", "eng-b", "2026-10-24", "2026-10-23 13:00", "2026-10-23 15:00", "2026-10-24 14:00"
    )
    book["published_utc"] = pd.NaT
    m = MarketTilt(Fixed, 1.0, 1.0, "t", prices=lambda: book)
    m.fit(pd.DataFrame(), q)
    m.decision_time = (q + pd.Timedelta(hours=1)).to_pydatetime()
    m.predict(fx)
    assert m.last_used.used_1x2.tolist() == [False]
    assert m.last_used.refused.tolist() == ["publication time unknown (no readable Last-Modified)"]


def test_database_prices_stay_usable_beside_an_unknown_snapshot():
    """The season file's prices are published when quoted, even when combined with a live
    snapshot whose publication time is unknown (which is refused). SYNTHETIC prices."""
    deadline = pd.Timestamp("2026-10-23 16:00", tz="UTC")
    db = _raw(_prices("eng-a", "eng-b", "2026-10-24", deadline))
    snap = _raw(_prices("eng-c", "eng-d", "2026-10-24", deadline + pd.Timedelta(hours=1)))
    snap["published_utc"] = pd.NaT
    book = combine_prices(db, snap)
    a = book.loc[("E0", pd.Timestamp("2026-10-24"), "eng-a", "eng-b")]
    c = book.loc[("E0", pd.Timestamp("2026-10-24"), "eng-c", "eng-d")]
    assert a.published_utc == deadline and pd.isna(c.published_utc)
    m = MarketTilt(Fixed, 1.0, 1.0, "t", prices=lambda: book)
    m.fit(pd.DataFrame(), deadline)
    m.decision_time = (deadline + pd.Timedelta(hours=2)).to_pydatetime()
    fx = pd.DataFrame(
        {
            "division": ["E0", "E0"],
            "date": pd.to_datetime(["2026-10-24", "2026-10-24"]),
            "home_team": ["eng-a", "eng-c"],
            "away_team": ["eng-b", "eng-d"],
            "kickoff_utc": pd.to_datetime(["2026-10-24 14:00"] * 2, utc=True),
        }
    )
    m.predict(fx)
    assert m.last_used.used_1x2.tolist() == [True, False]
