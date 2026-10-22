"""Every model's predictions: each fixture's genuine records side by side, the live leaderboard
and the backtest explorer (footy.api.models_routes, footy.model_compare,
footy.backtest_explorer).

SYNTHETIC TEST DATA: fictional teams (fdfixtures), made-up probabilities, a made-up provenance
hash and synthetic ledgers (btfixtures). Nothing here is a real price, prediction or result.
"""

import json
import math
from datetime import UTC, datetime, timedelta
from urllib.parse import quote

import pytest
from sqlalchemy import text
from sqlalchemy.orm import Session

import btfixtures
from fdfixtures import REFERENCE
from footy import backtest_explorer, model_catalogue
from footy.api import routes
from footy.ingest.fd_import import sync_reference
from footy.modeling.grid import markets_from_grid, poisson_grid
from footy.modeling.hit_rates import wilson
from footy.publish import FixtureIn, publish, settle
from footy.snapshot import find_prices

NOW = datetime.now(UTC).replace(microsecond=0)
DAY = (NOW + timedelta(days=2)).date()
KICKOFF = datetime(DAY.year, DAY.month, DAY.day, 14, 0, tzinfo=UTC)
REF_A = f"E0:2627:{DAY.isoformat()}:eng-testtown:eng-sampleton"
REF_B = f"E0:2627:{DAY.isoformat()}:eng-sampleton:eng-mockford"

SYNTHETIC_MARKET = {
    "books": ["B365", "Avg"],
    "p_H": 0.30,
    "p_D": 0.28,
    "p_A": 0.42,
    "p_over_2.5": 0.55,
    "quote_time_utc": "2026-10-20 12:00:00+00:00",
    "provenance": {"source": "SYNTHETIC", "planted_price": 2.38},  # must never be sent
    "B365H": 3.3,  # SYNTHETIC price-like field: must never be sent
}
TILT = {"base": "base_m", "w_1x2": 1.0, "w_ou": 1.0, "devig": "shin"}


def fx(home, away, features=None):
    return FixtureIn(
        "E0",
        "2627",
        DAY,
        KICKOFF,
        home,
        away,
        features=features or {"home_matches_in_window": 40, "away_matches_in_window": 38},
        missing_fields=["lineups", "injuries"],
    )


def pub(engine, tmp_path, fixtures, model, lam=(1.4, 1.1), **kw):
    n = len(fixtures)
    mp = markets_from_grid(poisson_grid([lam[0]] * n, [lam[1]] * n), [lam[0]] * n, [lam[1]] * n)
    return publish(
        engine,
        fixtures,
        mp,
        model_name=model,
        model_version="v-test",
        training_cutoff_utc=NOW - timedelta(days=1),
        data_freshness={},
        now=kw.pop("now", NOW),
        records_dir=tmp_path / "records",
        **kw,
    )


@pytest.fixture
def primary_file(tmp_path, monkeypatch):
    path = tmp_path / "primary_models.json"
    monkeypatch.setattr(routes, "PRIMARY_MODELS_PATH", path)
    path.write_text(json.dumps({"_comment": "SYNTHETIC", "domestic": ["tilt_m", "base_m"]}))
    return path


@pytest.fixture
def shadow_file(tmp_path, monkeypatch):
    path = tmp_path / "shadow_models.json"
    monkeypatch.setattr(model_catalogue, "SHADOW_MODELS_PATH", path)
    path.write_text(
        json.dumps(
            {
                "_about": "SYNTHETIC",
                "domestic": [
                    {"model": "shadow_x", "role": "challenger", "description": "SYNTHETIC x"},
                    {"model": "shadow_y", "role": "not-a-role"},
                ],
            }
        )
    )
    return path


@pytest.fixture
def world(migrated_engine, tmp_path, primary_file, shadow_file):
    """Fixture A (Testtown v Sampleton): base_m (an early, then a lineup edition), tilt_m (model
    + market, the record of record), shadow_x (a challenger), a market-only record and a
    retrospective one. Fixture B (Sampleton v Mockford): base_m only."""
    with Session(migrated_engine) as s:
        sync_reference(s, *REFERENCE)
        s.commit()
    a_base, b_base = pub(
        migrated_engine,
        tmp_path,
        [fx("eng-testtown", "eng-sampleton"), fx("eng-sampleton", "eng-mockford")],
        "base_m",
    )
    (a_lineup,) = pub(
        migrated_engine,
        tmp_path,
        [fx("eng-testtown", "eng-sampleton")],
        "base_m",
        lam=(1.5, 1.0),
        edition="lineup",
        now=NOW + timedelta(minutes=5),
    )
    tilt_features = {"model_params": TILT, "market_prices_used": SYNTHETIC_MARKET}
    (a_tilt,) = pub(
        migrated_engine,
        tmp_path,
        [fx("eng-testtown", "eng-sampleton", tilt_features)],
        "tilt_m",
        lam=(1.2, 1.3),
    )
    (a_shadow,) = pub(
        migrated_engine, tmp_path, [fx("eng-testtown", "eng-sampleton")], "shadow_x", lam=(0.9, 1.6)
    )
    # A forecast made of prices alone (no base model): never shown.
    market_only = {"market_prices_used": SYNTHETIC_MARKET, "model_params": {"devig": "shin"}}
    (a_market,) = pub(
        migrated_engine, tmp_path, [fx("eng-testtown", "eng-sampleton", market_only)], "mkt_only"
    )
    (a_retro,) = pub(
        migrated_engine,
        tmp_path,
        [fx("eng-testtown", "eng-sampleton")],
        "retro_m",
        record_type="retrospective_backtest",
    )
    return {
        "a_base": a_base,
        "a_lineup": a_lineup,
        "b_base": b_base,
        "a_tilt": a_tilt,
        "a_shadow": a_shadow,
        "a_market": a_market,
        "a_retro": a_retro,
    }


def settle_a(engine, hg, ag):
    with engine.begin() as conn:
        conn.execute(
            text(
                "INSERT INTO raw_files (id, source, logical_key, revision, sha256, size_bytes, "
                "retrieved_at, storage_path) VALUES (1, 't', 'k', 1, 'x', 1, '2026-10-25', 'p')"
            )
        )
        conn.execute(
            text(
                "INSERT INTO matches (division, season, match_date, home_team, away_team, "
                "home_goals, away_goals, result, source_row, first_raw_file_id, "
                "last_raw_file_id) VALUES ('E0', '2627', :d, 'eng-testtown', 'eng-sampleton', "
                ":hg, :ag, 'A', 1, 1, 1)"
            ),
            {"d": DAY.isoformat(), "hg": hg, "ag": ag},
        )
    settle(engine, now=KICKOFF + timedelta(hours=3))


def models_url(ref):
    return f"/api/fixtures/{quote(ref, safe='')}/models"


def rps_of(p, outcome):
    cum = [p["H"], p["H"] + p["D"]]
    obs = [[1, 1], [0, 1], [0, 0]][outcome]
    return sum((c - o) ** 2 for c, o in zip(cum, obs, strict=True)) / 2


def no_prices(raw: str, body) -> None:
    for planted in ("B365H", "planted_price", "2.38", "market_prices_used", "0.6111"):
        assert planted not in raw
    assert find_prices({"bodies": [body]}) == []


# --- Every model for one fixture ------------------------------------------------------------


def test_fixture_models_lists_every_genuine_record_record_of_record_first(client, world):
    raw = client.get(models_url(REF_A))
    assert raw.status_code == 200
    d = raw.json()
    assert d["kind"] == "genuine_prematch" and "before kickoff" in d["explanation"]
    assert d["fixture_ref"] == REF_A and d["record_of_record"] == "tilt_m"
    assert (d["home_name"], d["away_name"]) == ("Testtown", "Sampleton")
    assert d["home_media"]["badge"]["short"] and d["result"] is None
    rows = d["models"]
    # The record of record, then the other listed model, then the shadow (by role). No
    # market-only record, no retrospective one.
    assert [m["model"] for m in rows] == ["tilt_m", "base_m", "shadow_x"]
    assert [m["role"] for m in rows] == ["model_of_record", "fallback_of_record", "challenger"]
    assert [m["is_record_of_record"] for m in rows] == [True, False, False]
    assert [m["basis"] for m in rows] == ["model_and_market", "model_only", "model_only"]
    tilt, base, shadow = rows
    assert tilt["expected_goals_before_prices"] is True
    assert shadow["description"] == "SYNTHETIC x" and shadow["label"] == "Shadow x"
    assert base["label"] == "Base m" and base["description"] is None
    # First edition shown, the later one listed.
    assert base["prediction_id"] == world["a_base"] and base["edition"] == "early"
    assert [e["prediction_id"] for e in base["later_editions"]] == [world["a_lineup"]]
    assert base["later_editions"][0]["edition"] == "lineup"
    assert base["published_at_utc"].endswith("+00:00")
    p = base["probabilities"]
    assert set(p) == {"1X2", "OU_1.5", "OU_2.5", "OU_3.5", "BTTS", "expected_goals", "top_score"}
    assert set(p["top_score"]) == {"score", "p"}
    assert all(m["settlement"] is None for m in rows)
    no_prices(raw.text, d)


def test_fixture_models_after_settlement(client, migrated_engine, world):
    settle_a(migrated_engine, 0, 1)  # an away win
    d = client.get(models_url(REF_A)).json()
    assert d["result"]["home_goals"] == 0 and d["result"]["away_goals"] == 1
    assert d["result"]["settled_at_utc"].endswith("+00:00")
    for m in d["models"]:
        s = m["settlement"]
        assert set(s["markets"]) == {"1X2", "OU_1.5", "OU_2.5", "OU_3.5", "BTTS"}
        x = s["markets"]["1X2"]
        assert x["actual"] == "A" and x["correct"] == (x["pick"] == "A")
        assert s["rps"] == pytest.approx(rps_of(m["probabilities"]["1X2"], 2))
        ou = s["markets"]["OU_2.5"]
        assert ou["actual"] == "U"
        assert s["log_loss"]["OU_2.5"] == pytest.approx(-math.log(ou["p_actual"]))
        assert ou["p_actual"] == pytest.approx(m["probabilities"]["OU_2.5"]["U"])


def test_fixture_models_unknown_fixture_and_retrospective_only(client, world):
    assert client.get(models_url("E0:2627:2026-01-01:nobody:none")).status_code == 404


def test_fixture_models_without_a_shadow_file(client, world, shadow_file):
    shadow_file.unlink()
    rows = client.get(models_url(REF_A)).json()["models"]
    assert {m["model"]: m["role"] for m in rows}["shadow_x"] == "other"
    shadow_file.write_text("{ half-written")
    rows = client.get(models_url(REF_A)).json()["models"]
    assert {m["model"]: m["role"] for m in rows}["shadow_x"] == "other"


def test_shadow_file_list_and_mapping_forms(tmp_path):
    assert model_catalogue.load_shadow_models(tmp_path / "missing.json") == {}
    path = tmp_path / "s.json"
    path.write_text(
        json.dumps(
            {
                "domestic": {"m1": "baseline", "m2": {"role": "machine_learning", "label": "L"}},
                "uefa": [{"model": "u1", "role": "earlier_record", "description": "D"}],
                "other_group": [{"model": "z", "role": "baseline"}],
            }
        )
    )
    got = model_catalogue.load_shadow_models(path)
    assert got["domestic"]["m1"]["role"] == "baseline"
    assert got["domestic"]["m2"] == {"role": "machine_learning", "label": "L", "description": None}
    assert got["uefa"]["u1"]["description"] == "D" and "other_group" not in got


def test_market_only_detection():
    assert model_catalogue.is_market_only("market_consensus")
    assert model_catalogue.is_market_only("x", {"market_prices_used": {}, "model_params": {}})
    assert not model_catalogue.is_market_only("x", {"market_prices_used": {}, "model_params": TILT})
    assert not model_catalogue.is_market_only("dixon_coles", {})
    assert backtest_explorer.market_only_ledger({"model": "m", "model_params": {"books": ["A"]}})
    assert not backtest_explorer.market_only_ledger(
        {"model": "market_tilt_dc", "model_params": {"base": "dixon_coles", "books": ["A"]}}
    )


# --- Leaderboard ----------------------------------------------------------------------------


def test_leaderboard_before_anything_is_settled(client, world):
    d = client.get("/api/model-leaderboard?group=domestic").json()
    assert d["kind"] == "genuine_prematch" and d["models"] == []
    assert {m["model"] for m in d["waiting"]} == {"tilt_m", "base_m", "shadow_x"}
    assert d["record"]["published"] == 2 and d["record"]["settled"] == 0
    assert d["models_of_record_now"] == ["tilt_m", "base_m"]


def test_leaderboard_counts_settled_first_editions(client, migrated_engine, world):
    settle_a(migrated_engine, 0, 1)
    raw = client.get("/api/model-leaderboard?group=domestic")
    d = raw.json()
    rows = {m["model"]: m for m in d["models"]}
    assert set(rows) == {"tilt_m", "base_m", "shadow_x"}  # no market-only, no retrospective
    assert d["waiting"] == []
    fixture = client.get(models_url(REF_A)).json()
    rps = {m["model"]: m["settlement"]["rps"] for m in fixture["models"]}
    for name, m in rows.items():
        assert m["mean_rps"] == pytest.approx(rps[name])
        assert m["small_sample"] is True
        r = m["results_right"]
        assert r["n"] == 1 and r["ci95"] == pytest.approx(list(wilson(r["correct"], 1)))
    # base_m: fixture A settled (first edition, not the lineup one), fixture B pending.
    base = rows["base_m"]
    assert (base["published"], base["settled"], base["pending"]) == (2, 1, 1)
    assert base["role"] == "model_of_record" and rows["shadow_x"]["role"] == "challenger"
    # Paired with the record of record (tilt_m on fixture A); tilt_m has nothing to pair.
    assert base["vs_record"]["n"] == 1
    assert base["vs_record"]["mean_rps_difference"] == pytest.approx(rps["base_m"] - rps["tilt_m"])
    assert rows["tilt_m"]["vs_record"]["n"] == 0 and rows["tilt_m"]["records_of_record"] == 1
    assert d["record"]["settled"] == 1 and d["record"]["mean_rps"] == pytest.approx(rps["tilt_m"])
    # Sorted by mean RPS, best first.
    means = [m["mean_rps"] for m in d["models"]]
    assert means == sorted(means)
    lineup = client.get(f"/api/predictions/{world['a_lineup']}").json()
    assert lineup["settlement"] is not None  # settled too, but never counted twice
    assert base["settled"] == 1
    no_prices(raw.text, d)


def test_leaderboard_groups(client, world):
    d = client.get("/api/model-leaderboard?group=uefa").json()
    assert d["models"] == [] and d["waiting"] == [] and d["record"]["published"] == 0
    assert client.get("/api/model-leaderboard?group=elsewhere").status_code == 422


# --- Backtest explorer ----------------------------------------------------------------------


@pytest.fixture
def ledgers(settings, migrated_engine):
    with Session(migrated_engine) as s:
        sync_reference(s, *REFERENCE)
        s.commit()
    btfixtures.write_curated(settings.data_dir)
    return settings.data_dir


def test_backtest_models_lists_curated_models_and_refuses_market_ledgers(client, ledgers):
    d = client.get("/api/backtest-models").json()
    assert d["kind"] == "backtest"
    assert "not genuine pre-match records" in d["explanation"]
    models = {m["model"]: m for m in d["models"]}
    assert set(models) == {"dixon_coles", "baseline_frequency", "pan_euro_mix_mxg", "pan_euro_mix"}
    assert "market_tilt_dc" not in models  # a market-only ledger under a curated name
    dom = next(g for g in d["groups"] if g["group"] == "domestic")
    assert dom["skipped_runs"] == [
        {
            "model": "market_tilt_dc",
            "run_id": "market_tilt_dc__validation__2324",
            "reason": "market-derived ledger (never shown)",
        }
    ]
    assert [s["split"] for s in models["dixon_coles"]["splits"]] == ["validation", "test"]
    assert models["dixon_coles"]["splits"][1]["matches"] == 3
    # The earlier record's test run, saved under its lab name, is its own.
    assert models["pan_euro_mix"]["role"] in ("earlier_record", "other")
    assert models["pan_euro_mix"]["splits"][0]["run_id"] == "euro_mix_0.7_0.3__test_mxg"
    assert dom["coverage"] == [
        {"division": "E0", "season": "2324", "split": "validation", "matches": 3},
        {"division": "E0", "season": "2425", "split": "test", "matches": 2},
        {"division": "E0", "season": "2526", "split": "test", "matches": 1},
    ]
    assert dom["divisions"] == [{"code": "E0", "name": "Premier League"}]
    uefa = next(g for g in d["groups"] if g["group"] == "uefa")
    assert uefa["coverage"] == [
        {"division": "UCL", "season": "2425", "split": "test", "matches": 2}
    ]


def test_backtest_matches_rows_and_summary(client, ledgers):
    raw = client.get("/api/backtest-matches?division=E0&season=2324&limit=1000")
    d = raw.json()
    assert d["kind"] == "backtest" and "computed after the fact" in d["explanation"]
    assert [m["model"] for m in d["models"]] == ["dixon_coles", "baseline_frequency"]
    assert d["columns"] == [
        "p_home",
        "p_draw",
        "p_away",
        "p_over_2_5",
        "p_btts_yes",
        "result_right",
        "rps",
    ]
    assert d["total"] == d["count"] == 3
    first, second, third = d["matches"]
    assert first["date"] == "2023-08-12" and first["score"] == [2, 1]
    assert first["home"] == "eng-testtown" and first["home_name"] == "Testtown"
    assert first["split"] == "validation" and "stage" not in first
    assert first["p"][0] == [0.5, 0.3, 0.2, 0.512, 0.499, 1, 0.145]
    assert third["p"][1] is None  # baseline_frequency skipped that match
    assert set(d["teams"]) == set(btfixtures.TEAMS)
    assert d["teams"]["eng-testtown"]["name"] == "Testtown"
    summary = {s["model"]: s for s in d["summary"]}
    dc = summary["dixon_coles"]
    assert (dc["matches"], dc["results_right"]) == (3, 1)  # home pick: only the home win
    rps = [0.145, 0.145, 0.445]  # home win, draw, away win against 0.5 / 0.3 / 0.2
    assert dc["mean_rps"] == pytest.approx(sum(rps) / 3)
    assert dc["ci95"] == pytest.approx(list(wilson(1, 3)))
    assert summary["baseline_frequency"]["matches"] == 2
    # No genuine-record fields; nothing from the refused market-only ledger; no prices.
    for m in d["matches"]:
        assert "prediction_id" not in m and "record_type" not in m
    no_prices(raw.text, d)


def test_backtest_matches_filters_and_paging(client, ledgers):
    d = client.get("/api/backtest-matches?division=E0&team=eng-mockford").json()
    assert d["total"] == 4  # every season
    assert all("eng-mockford" in (m["home"], m["away"]) for m in d["matches"])
    one = client.get("/api/backtest-matches?division=E0&models=baseline_frequency").json()
    assert [m["model"] for m in one["models"]] == ["baseline_frequency"]
    assert one["total"] == 2 and all(len(m["p"]) == 1 for m in one["matches"])
    page = client.get("/api/backtest-matches?division=E0&limit=2&offset=2").json()
    full = client.get("/api/backtest-matches?division=E0").json()
    assert page["total"] == full["total"] == 6 and page["count"] == 2
    assert page["matches"] == full["matches"][2:4]
    assert page["summary"] == full["summary"]  # the summary covers every selected match
    test = client.get("/api/backtest-matches?division=E0&season=2526").json()
    assert test["matches"][0]["split"] == "test" and test["matches"][0]["p"][1] is None
    ucl = client.get("/api/backtest-matches?division=UCL&season=2425").json()
    assert ucl["group"] == "uefa" and ucl["matches"][0]["stage"] is None
    assert [m["model"] for m in ucl["models"]] == ["pan_euro_mix_mxg", "pan_euro_mix"]
    assert ucl["matches"][0]["p"][1][:3] == [0.35, 0.3, 0.35]


def test_backtest_matches_bad_requests(client, ledgers):
    for q in (
        "division=XX",
        "season=1999",
        "models=market_tilt_dc",  # refused ledger: not offered
        "models=pan_euro_mix",  # a UEFA model for league games
        "division=UCL&group=domestic",
    ):
        assert client.get(f"/api/backtest-matches?{q}").status_code == 400, q
    assert client.get("/api/backtest-matches?limit=5000").status_code == 422


def test_backtests_and_genuine_records_never_mix(client, migrated_engine, world, ledgers):
    """The leaderboard and the fixture lists hold genuine records only; the explorer holds
    backtests only, and says so."""
    settle_a(migrated_engine, 0, 1)
    board = client.get("/api/model-leaderboard?group=domestic").json()
    assert board["kind"] == "genuine_prematch"
    assert not {m["model"] for m in board["models"]} & {"dixon_coles", "baseline_frequency"}
    bt = client.get("/api/backtest-matches?division=E0").json()
    assert bt["kind"] == "backtest"
    assert not {m["model"] for m in bt["models"]} & {"base_m", "tilt_m", "shadow_x"}
    listed = client.get("/api/backtest-models").json()
    assert all("backtest" == listed["kind"] for _ in listed["models"])


def test_every_timestamp_is_utc_with_an_offset(client, migrated_engine, world):
    settle_a(migrated_engine, 1, 1)

    def walk(o):
        if isinstance(o, dict):
            for v in o.values():
                yield from walk(v)
        elif isinstance(o, list):
            for v in o:
                yield from walk(v)
        elif isinstance(o, str) and len(o) > 10 and o[:4].isdigit() and o[10:11] in ("T", " "):
            yield o

    seen = 0
    for url in (models_url(REF_A), models_url(REF_B), "/api/model-leaderboard?group=domestic"):
        for value in walk(client.get(url).json()):
            seen += 1
            assert "T" in value and value.endswith("+00:00"), (url, value)
    assert seen >= 8
