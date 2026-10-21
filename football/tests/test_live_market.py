"""predict_domestic with a model + market candidate: records under its name only where it
had prices. SYNTHETIC fixtures, teams and prices; the database, schedule and publisher are
stubbed out."""

from datetime import UTC, date, datetime

import numpy as np
import pandas as pd

import footy.live as live
from footy.fixtures import ScheduledMatch
from footy.modeling.grid import markets_from_grid, poisson_grid
from footy.modeling.lab.market_blend import MarketTilt, consensus

NOW = datetime(2026, 10, 23, 16, 0, tzinfo=UTC)  # Friday, after the collection


class Fixed:
    name = "fixed"
    markets = frozenset({"1X2", "OU", "BTTS"})

    def fit(self, history, cutoff):
        pass

    def predict(self, fixtures):
        n = len(fixtures)
        return markets_from_grid(poisson_grid(np.full(n, 1.5), np.full(n, 1.1), rho=-0.05))

    def params(self):
        return {}


def _book():
    q = pd.Timestamp("2026-10-23 15:00", tz="UTC")
    rows = []
    for book in ("B365", "Avg"):
        rows += [
            ("E0", "2026-10-24", "eng-a", "eng-b", book, "1X2", s, p, q)
            for s, p in (("H", 2.0), ("D", 3.5), ("A", 4.0))
        ]
        rows += [
            ("E0", "2026-10-24", "eng-a", "eng-b", book, "OU_2.5", s, p, q)
            for s, p in (("O", 1.8), ("U", 2.1))
        ]
    raw = pd.DataFrame(
        rows,
        columns=[
            "division",
            "date",
            "home_team",
            "away_team",
            "bookmaker",
            "market_key",
            "selection",
            "price",
            "quote_time_utc",
        ],
    )
    raw["published_utc"] = pd.Timestamp("2026-10-23 13:00", tz="UTC")
    return consensus(raw)


def _stub(monkeypatch):
    hist = pd.DataFrame(
        {
            "division": ["E0"],
            "date": [pd.Timestamp("2026-10-18")],
            "home_team": ["eng-a"],
            "away_team": ["eng-c"],
            "model_exclusion": [None],
        }
    )
    fx = [
        (
            ScheduledMatch(
                date(2026, 10, 24), datetime(2026, 10, 24, 14, tzinfo=UTC), "A", "B", None, None, 10
            ),
            "eng-a",
            "eng-b",
        ),
        (
            ScheduledMatch(
                date(2026, 10, 24), datetime(2026, 10, 24, 14, tzinfo=UTC), "C", "D", None, None, 11
            ),
            "eng-c",
            "eng-d",
        ),
    ]
    published = []
    monkeypatch.setattr(live, "load_matches", lambda engine, divs: hist)
    monkeypatch.setattr(live, "upcoming", lambda *a, **k: (fx, {"checked_results": 99}))
    monkeypatch.setattr(live, "model_version", lambda engine, extra="": "test")

    def publish(engine, fixtures, mp, **kw):
        published.extend(f.ref for f in fixtures)
        assert len(fixtures) == mp.n
        return list(range(len(fixtures)))

    monkeypatch.setattr(live, "publish", publish)
    return published


def _market():
    book = _book()
    return MarketTilt(Fixed, 1.0, 1.0, "market_test", prices=lambda: book)


def test_market_model_publishes_only_fixtures_with_prices(monkeypatch):
    published = _stub(monkeypatch)
    out = live.predict_domestic(None, _market, "market_test", ["E0"], now=NOW)
    assert published == ["E0:2627:2026-10-24:eng-a:eng-b"]  # not the unpriced fixture
    assert len(out) == 1


def test_a_dry_run_still_lists_every_fixture(monkeypatch):
    published = _stub(monkeypatch)
    out = live.predict_domestic(None, _market, "market_test", ["E0"], now=NOW, dry_run=True)
    assert [r["market_used"] for r in out] == [True, False]
    assert published == []


def test_a_pure_model_publishes_every_fixture(monkeypatch):
    published = _stub(monkeypatch)
    live.predict_domestic(None, Fixed, "fixed", ["E0"], now=NOW)
    assert len(published) == 2
