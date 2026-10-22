"""The Past page's API (/api/past), the counterpart fallback, consensus labels, the withheld
ledger download and the fetch status (audit 22 Oct 2026: H2, L16, M2, L15, M3).

SYNTHETIC TEST DATA: fictional teams (fdfixtures), made-up probabilities, a made-up consensus,
fetch log and backtest ledger. Nothing here is a real price, prediction or result.
"""

import json
from datetime import UTC, datetime, timedelta

import pytest
from sqlalchemy import text
from sqlalchemy.orm import Session

from fdfixtures import REFERENCE
from footy.api import routes
from footy.ingest.fd_import import sync_reference
from footy.modeling.grid import markets_from_grid, poisson_grid
from footy.publish import FixtureIn, publish, settle

NOW = datetime.now(UTC).replace(microsecond=0)
DAY = (NOW + timedelta(days=2)).date()
KICKOFF = datetime(DAY.year, DAY.month, DAY.day, 14, 0, tzinfo=UTC)
LAM = (1.4, 1.1)


def _markets(n: int, lam=LAM):
    return markets_from_grid(poisson_grid([lam[0]] * n, [lam[1]] * n), [lam[0]] * n, [lam[1]] * n)


# SYNTHETIC consensus equal to the grid's own match result and over 2.5, as a tilt with weight
# 1.0 saves it (the record's figures are the consensus).
_MP = _markets(1)
CONSENSUS = {
    "books": ["B365", "Avg"],
    "p_H": float(_MP.p1x2[0][0]),
    "p_D": float(_MP.p1x2[0][1]),
    "p_A": float(_MP.p1x2[0][2]),
    "p_over_2.5": float(_MP.p_over[2.5][0]),
    "quote_time_utc": "2026-10-20T12:00:00+00:00",
    "provenance": {"source": "SYNTHETIC"},
}


def fx(home: str, away: str, features: dict | None = None, day=DAY, kickoff=KICKOFF) -> FixtureIn:
    return FixtureIn(
        "E0",
        "2627",
        day,
        kickoff,
        home,
        away,
        features=features or {"home_matches_in_window": 40, "away_matches_in_window": 38},
        missing_fields=["lineups", "injuries", "odds"],
    )


def pub(engine, tmp_path, fixtures, model, edition="early", lam=LAM, now=None):
    return publish(
        engine,
        fixtures,
        _markets(len(fixtures), lam),
        model_name=model,
        model_version="v-test",
        training_cutoff_utc=NOW - timedelta(days=1),
        data_freshness={"latest_result_by_division": {"E0": "2026-10-19"}},
        edition=edition,
        now=now or NOW,
        records_dir=tmp_path / "records",
    )


def add_result(engine, home, away, hg, ag, day=DAY):
    with engine.begin() as conn:
        if conn.execute(text("SELECT count(*) FROM raw_files WHERE id = 1")).scalar() == 0:
            conn.execute(
                text(
                    "INSERT INTO raw_files (id, source, logical_key, revision, sha256, "
                    "size_bytes, retrieved_at, storage_path) "
                    "VALUES (1, 't', 'k', 1, 'x', 1, '2026-10-25', 'p')"
                )
            )
        conn.execute(
            text(
                "INSERT INTO matches (division, season, match_date, home_team, away_team, "
                "home_goals, away_goals, result, source_row, first_raw_file_id, "
                "last_raw_file_id) VALUES ('E0', '2627', :d, :h, :a, :hg, :ag, :r, 1, 1, 1)"
            ),
            {
                "d": day.isoformat(),
                "h": home,
                "a": away,
                "hg": hg,
                "ag": ag,
                "r": "H" if hg > ag else "D" if hg == ag else "A",
            },
        )


@pytest.fixture
def primary(tmp_path, monkeypatch):
    """A temporary primary_models.json naming rec_m the domestic model of record."""
    path = tmp_path / "primary_models.json"
    path.write_text(json.dumps({"_comment": "SYNTHETIC", "domestic": ["rec_m"]}))
    monkeypatch.setattr(routes, "PRIMARY_MODELS_PATH", path)


@pytest.fixture
def world(migrated_engine, tmp_path, primary):
    """Fixture A (Testtown v Sampleton): the model of record rec_m (early, then a lineup
    edition), a shadow, a model + market tilt whose base model saved no record, and a record of
    prices alone. Fixture B (Sampleton v Mockford): a shadow only. Both settled."""
    with Session(migrated_engine) as s:
        sync_reference(s, *REFERENCE)
        s.commit()
    a, b = ("eng-testtown", "eng-sampleton"), ("eng-sampleton", "eng-mockford")
    (rec,) = pub(migrated_engine, tmp_path, [fx(*a)], "rec_m")
    (rec_lineup,) = pub(migrated_engine, tmp_path, [fx(*a)], "rec_m", edition="lineup")
    shadow_a, shadow_b = pub(
        migrated_engine, tmp_path, [fx(*a), fx(*b)], "shadow_m", lam=(1.0, 1.6)
    )
    tilt_features = {
        "home_matches_in_window": 40,
        "away_matches_in_window": 38,
        # a learner names no `base`: its own base model has no record of this fixture
        "model_params": {"stream": ["x", "y"], "w_1x2": 1.0, "w_ou": 1.0},
        "market_prices_used": CONSENSUS,
    }
    (tilt,) = pub(migrated_engine, tmp_path, [fx(*a, tilt_features)], "learner_market")
    only_prices = {**tilt_features, "model_params": {}, "market_only": True}
    (prices,) = pub(migrated_engine, tmp_path, [fx(*a, only_prices)], "consensus_copy")
    add_result(migrated_engine, *a, 2, 1)
    add_result(migrated_engine, *b, 0, 0)
    settle(migrated_engine, now=KICKOFF + timedelta(hours=3))
    return {
        "rec": rec,
        "rec_lineup": rec_lineup,
        "shadow_a": shadow_a,
        "shadow_b": shadow_b,
        "tilt": tilt,
        "prices": prices,
    }


def test_past_lists_each_fixture_once_with_the_record_performance_counts(client, world):
    body = client.get("/api/past").json()
    cards = body["matches"]
    refs = [c["fixture_ref"] for c in cards]
    assert len(refs) == len(set(refs)) == 1  # fixture B has no model of record's record
    (card,) = cards
    # The first edition of the record of record, as /api/performance counts it.
    assert card["id"] == world["rec"] and card["counted"] is True and card["is_primary"]
    assert card["model_label"]
    # Every model with a settled record (prices alone are never listed).
    assert card["models_for_fixture"] == 3
    assert body["models"] == "record" and body["fixtures"] == 1 and body["truncated"] is False
    # The list sends only what it shows.
    assert not set(routes.PAST_CARD_DROPPED) & set(card)
    perf = client.get("/api/performance").json()
    assert perf["settled"] == 1 and perf["markets"]["1X2"]["n"] == 1
    assert card["settlement"]["outcomes"]["1X2"]["correct"] == bool(
        perf["markets"]["1X2"]["correct"]
    )


def test_past_all_models_lists_every_models_record_counted_first(client, world):
    body = client.get("/api/past", params={"models": "all"}).json()
    by_ref: dict[str, list[dict]] = {}
    for c in body["matches"]:
        by_ref.setdefault(c["fixture_ref"], []).append(c)
    assert body["fixtures"] == 2
    ids = {c["id"] for c in body["matches"]}
    assert world["prices"] not in ids  # a record of prices alone is never shown (D-013)
    assert {world["rec"], world["rec_lineup"], world["shadow_a"], world["tilt"]} <= ids
    assert world["shadow_b"] in ids  # a fixture without a model of record's record is listed
    a = next(v for v in by_ref.values() if any(c["id"] == world["rec"] for c in v))
    assert a[0]["id"] == world["rec"] and a[0]["counted"] is True
    assert [c["counted"] for c in a[1:]] == [False] * (len(a) - 1)
    # Only one card per fixture is counted, never a shadow's.
    assert sum(c["counted"] for c in body["matches"]) == 1


def test_past_limit_counts_fixtures_and_says_when_it_cut(client, world):
    body = client.get("/api/past", params={"models": "all", "limit": 1}).json()
    assert body["fixtures"] == 1 and body["truncated"] is True
    assert len({c["fixture_ref"] for c in body["matches"]}) == 1
    assert len(body["matches"]) > 1  # every model's card of that fixture


def test_past_rejects_an_unknown_models_choice(client, world):
    assert client.get("/api/past", params={"models": "some"}).status_code == 422


def test_counterpart_falls_back_to_the_pure_record_of_record_and_names_it(client, world):
    d = client.get(f"/api/predictions/{world['tilt']}").json()
    cp = d["counterpart"]
    assert cp is not None and cp["basis"] == "model_only"
    # The fixture's pure record of record (its latest edition, as on the fixtures list)
    assert cp["model_name"] == "rec_m" and cp["relation"] == "pure_record_of_record"


def test_consensus_markets_name_the_figures_that_are_the_bookmakers(client, world):
    d = client.get(f"/api/predictions/{world['tilt']}").json()
    assert d["consensus_markets"] == ["1X2", "OU_2.5"]
    assert "consensus_markets" not in client.get(f"/api/predictions/{world['rec']}").json()
    # A genuine blend's figures differ from the consensus: nothing is claimed.
    blend = {"p_H": 0.5, "p_D": 0.3, "p_A": 0.2, "p_over_2.5": 0.4}
    assert routes.consensus_markets(d["probabilities"], {"market_prices_used": blend}) == []


def test_a_price_identical_ledger_is_not_sent_match_by_match(client, settings):
    root = settings.data_dir / "backtests"
    for run_id, model in (
        ("market_tilt_shots__test__2425-2526", "market_tilt_shots"),
        ("synthetic_pure__test__2425", "synthetic_pure"),
    ):
        d = root / run_id
        d.mkdir(parents=True)
        (d / "manifest.json").write_text(json.dumps({"model": model, "seasons": ["2425"]}))
        (d / "ledger.csv").write_text("model,p_H\nSYNTHETIC,0.5\n")
    for fmt in ("csv", "json"):
        res = client.get(f"/api/backtests/market_tilt_shots__test__2425-2526/ledger.{fmt}")
        assert res.status_code == 403 and "consensus" in res.json()["detail"]
    assert client.get("/api/backtests/synthetic_pure__test__2425/ledger.csv").status_code == 200
    runs = {r["run_id"]: r["per_match"] for r in client.get("/api/backtests").json()["runs"]}
    assert runs == {"market_tilt_shots__test__2425-2526": False, "synthetic_pure__test__2425": True}


def test_fetch_status_shows_a_failing_source_while_its_job_ended_ok(client, migrated_engine):
    rows = [
        ("wikipedia", "2026-10-21 02:20:00", 200, "unchanged", None),
        ("wikipedia", "2026-10-21 18:49:10", 403, "error", "HTTP 403"),
        (
            "wikipedia",
            "2026-10-22 07:31:46",
            403,
            "error",
            "Client error '403' for url 'https://example.org/w/api.php?action=x&api_token=SYN'",
        ),
        ("football-data", "2026-10-22 07:32:03", 200, "new_revision", None),
        ("football-data", "2026-10-20 07:32:03", 500, "error", "HTTP 500"),
    ]
    with migrated_engine.begin() as conn:
        conn.execute(
            text(
                "INSERT INTO job_runs (job, started_at, finished_at, status) "
                "VALUES ('euro_update', '2026-10-22 07:31:00', '2026-10-22 07:32:00', 'ok')"
            )
        )
        for source, at, status, outcome, error in rows:
            conn.execute(
                text(
                    "INSERT INTO fetch_log (source, logical_key, url, requested_at, http_status, "
                    "outcome, error) VALUES (:s, 'syn/key', 'https://example.org', :at, :st, "
                    ":o, :e)"
                ),
                {"s": source, "at": at, "st": status, "o": outcome, "e": error},
            )
    body = client.get("/api/fetch-status").json()
    first, second = body["sources"]
    assert first["source"] == "wikipedia" and first["failing"] is True
    assert first["failures_since_ok"] == 2
    assert first["failing_since_utc"] == "2026-10-21T18:49:10+00:00"
    assert first["last_ok_utc"] == "2026-10-21T02:20:00+00:00"
    assert first["last_error"]["http_status"] == 403
    assert "api_token" not in first["last_error"]["error"]  # a URL's query string is cut
    assert second["source"] == "football-data" and second["failing"] is False
    assert second["failures_since_ok"] == 0 and second["last_error"]["http_status"] == 500
