"""Model + market records and the record of record in the API.

SYNTHETIC TEST DATA: fictional teams (fdfixtures), made-up probabilities and a made-up
provenance hash. Nothing here is a real price or a real prediction.
"""

import json
import re
from datetime import UTC, datetime, timedelta

import pytest
from sqlalchemy import text
from sqlalchemy.orm import Session

from fdfixtures import REFERENCE
from footy import models_of_record
from footy.api import routes
from footy.ingest.fd_import import sync_reference
from footy.modeling.grid import markets_from_grid, poisson_grid
from footy.publish import FixtureIn, publish, settle

# Fixtures a few days ahead of the real clock, so /api/fixtures (which uses today) lists them.
NOW = datetime.now(UTC).replace(microsecond=0)
DAY = (NOW + timedelta(days=2)).date()
KICKOFF = datetime(DAY.year, DAY.month, DAY.day, 14, 0, tzinfo=UTC)

SYNTHETIC_MARKET = {
    "books": ["B365", "Avg"],
    "p_H": 0.30,
    "p_D": 0.28,
    "p_A": 0.42,
    "p_over_2.5": 0.55,
    "quote_time_utc": "2026-10-20 12:00:00+00:00",  # saved with a space, as live.py writes it
    "provenance": {
        "source": "football-data fixtures.csv",
        "raw_file_id": 1,
        "raw_sha256": "0" * 64,  # SYNTHETIC
        "retrieved_at": "2026-10-20T12:00:00+00:00",
        "planted_price": 2.38,  # must never reach the API
    },
    "B365H": 3.3,  # SYNTHETIC price-like field: must never reach the API
}
TILT_PARAMS = {"base": "base_m", "w_1x2": 1.0, "w_ou": 1.0, "devig": "shin", "phase": "x"}


def fx(home: str, away: str, features: dict | None = None, missing=None) -> FixtureIn:
    return FixtureIn(
        "E0",
        "2627",
        DAY,
        KICKOFF,
        home,
        away,
        features=features or {"home_matches_in_window": 40, "away_matches_in_window": 38},
        missing_fields=missing or ["lineups", "injuries", "odds"],
    )


def pub(engine, tmp_path, fixtures, model_name, lam=(1.4, 1.1)):
    n = len(fixtures)
    mp = markets_from_grid(poisson_grid([lam[0]] * n, [lam[1]] * n), [lam[0]] * n, [lam[1]] * n)
    return publish(
        engine,
        fixtures,
        mp,
        model_name=model_name,
        model_version="v-test",
        training_cutoff_utc=NOW - timedelta(days=1),
        data_freshness={"latest_result_by_division": {"E0": "2026-10-19"}},
        now=NOW,
        records_dir=tmp_path / "records",
    )


@pytest.fixture
def primary_file(tmp_path, monkeypatch):
    """A temporary primary_models.json; the real file is never touched."""
    path = tmp_path / "primary_models.json"
    monkeypatch.setattr(routes, "PRIMARY_MODELS_PATH", path)

    def write(domestic):
        path.write_text(json.dumps({"_comment": "SYNTHETIC", "domestic": domestic}))

    return write


@pytest.fixture
def records(migrated_engine, tmp_path):
    """Fixture A (Testtown v Sampleton): a model-only record, then a model + market record
    built on it. Fixture B (Sampleton v Mockford): model-only only."""
    with Session(migrated_engine) as s:
        sync_reference(s, *REFERENCE)
        s.commit()
    (a_base, b_base) = pub(
        migrated_engine,
        tmp_path,
        [fx("eng-testtown", "eng-sampleton"), fx("eng-sampleton", "eng-mockford")],
        "base_m",
    )
    features = {
        "home_matches_in_window": 40,
        "away_matches_in_window": 38,
        "model_params": TILT_PARAMS,
        "market_prices_used": SYNTHETIC_MARKET,
    }
    (a_tilt,) = pub(
        migrated_engine,
        tmp_path,
        [fx("eng-testtown", "eng-sampleton", features, ["lineups", "injuries"])],
        "tilt_m",
        lam=(1.2, 1.3),
    )
    return {"a_base": a_base, "b_base": b_base, "a_tilt": a_tilt}


def fixture_cards(client) -> dict[int, dict]:
    body = client.get("/api/fixtures").json()
    return {c["id"]: c for comp in body["competitions"] for c in comp["fixtures"]}


def test_primary_models_string_or_list(tmp_path):
    path = tmp_path / "p.json"
    path.write_text(json.dumps({"_comment": "x", "domestic": "a", "uefa": ["b", "c", "b"]}))
    assert routes.primary_models(path) == {"domestic": ["a"], "uefa": ["b", "c"]}
    for bad in (["a", 3], [], [""], "", None, {"a": 1}):
        path.write_text(json.dumps({"domestic": bad}))
        with pytest.raises(ValueError, match="domestic"):
            routes.primary_models(path)
    assert routes.primary_models(tmp_path / "missing.json") == {}


def test_real_primary_models_file_parses():
    primary = routes.primary_models()
    assert set(primary) == {"domestic", "uefa"} and all(primary.values())


def test_string_form_keeps_one_model_of_record(client, records, primary_file):
    primary_file("base_m")
    cards = fixture_cards(client)
    assert cards[records["a_base"]]["is_primary"] is True
    assert cards[records["b_base"]]["is_primary"] is True
    assert cards[records["a_tilt"]]["is_primary"] is False


def test_list_form_picks_first_listed_model_with_a_record(client, records, primary_file):
    primary_file(["tilt_m", "base_m"])
    cards = fixture_cards(client)
    # Fixture A has both: the model + market record is the record of record.
    assert cards[records["a_tilt"]]["is_primary"] is True
    assert cards[records["a_base"]]["is_primary"] is False
    # Fixture B has no tilt_m record, so base_m is.
    assert cards[records["b_base"]]["is_primary"] is True
    detail = client.get(f"/api/predictions/{records['a_base']}").json()
    assert {e["id"]: e["is_primary"] for e in detail["editions"]} == {
        records["a_base"]: False,
        records["a_tilt"]: True,
    }


def test_unlisted_models_are_never_primary(client, records, primary_file):
    primary_file(["someone_else"])
    assert not any(c["is_primary"] for c in fixture_cards(client).values())


def test_basis_and_value_selection_placeholder(client, records, primary_file):
    primary_file("base_m")
    cards = fixture_cards(client)
    tilt, base = cards[records["a_tilt"]], cards[records["a_base"]]
    assert tilt["basis"] == "model_and_market" and base["basis"] == "model_only"
    # Real prices were used: a placeholder that names no bet. No prices: no key at all.
    assert tilt["value_selection"]["status"] == "not_published"
    assert tilt["value_selection"]["selections"] == []
    assert "value_selection" not in base


def test_detail_shows_market_probabilities_and_provenance_never_prices(
    client, records, primary_file
):
    primary_file(["tilt_m", "base_m"])
    raw = client.get(f"/api/predictions/{records['a_tilt']}")
    d = raw.json()
    m = d["market"]
    assert m["probabilities"]["1X2"] == {"H": 0.30, "D": 0.28, "A": 0.42}
    assert m["probabilities"]["OU_2.5"]["O"] == 0.55
    assert m["probabilities"]["OU_2.5"]["U"] == pytest.approx(0.45)
    assert m["books"] == ["B365", "Avg"] and m["base_model"] == "base_m"
    assert m["weights"] == {"1X2": 1.0, "OU_2.5": 1.0}
    assert m["quote_time_utc"] == "2026-10-20T12:00:00+00:00"
    assert m["provenance"] == {
        "source": "football-data fixtures.csv",
        "raw_file_id": 1,
        "raw_sha256": "0" * 64,
        "retrieved_at": "2026-10-20T12:00:00+00:00",
    }
    # Planted price-like fields appear nowhere in the response.
    assert "planted_price" not in raw.text and "B365H" not in raw.text and "2.38" not in raw.text
    assert d["features"]["market_prices_used"]["quote_time_utc"] == "2026-10-20T12:00:00+00:00"
    # The pure model's record sits beside it.
    cp = d["counterpart"]
    assert cp["id"] == records["a_base"] and cp["basis"] == "model_only"
    assert cp["model_name"] == "base_m" and "1X2" in cp["probabilities"]
    assert d["explanation"].startswith("Before the bookmakers' prices were blended in")
    assert "are the bookmakers' consensus" in d["explanation"]


def test_model_only_detail_points_to_its_model_and_market_record(client, records, primary_file):
    primary_file("base_m")
    d = client.get(f"/api/predictions/{records['a_base']}").json()
    assert d["market"] is None and d["basis"] == "model_only"
    assert d["counterpart"]["id"] == records["a_tilt"]
    assert d["counterpart"]["basis"] == "model_and_market"
    assert d["explanation"].startswith("The model expects")
    other = client.get(f"/api/predictions/{records['b_base']}").json()
    assert other["counterpart"] is None


def settle_fixture_a(engine, hg, ag):
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
    assert settle(engine, now=KICKOFF + timedelta(hours=3))["settled"] == 2


def test_performance_reports_each_basis_separately(client, migrated_engine, records, primary_file):
    primary_file(["tilt_m", "base_m"])
    settle_fixture_a(migrated_engine, 0, 1)
    perf = client.get("/api/performance").json()
    assert perf["primary_models"] == {"domestic": ["tilt_m", "base_m"]}
    # Headline: one record per fixture, the record of record (tilt_m for A, base_m for B).
    assert (perf["published"], perf["settled"], perf["pending"]) == (2, 1, 1)
    mm, mo = perf["by_basis"]["model_and_market"], perf["by_basis"]["model_only"]
    assert (mm["published"], mm["settled"], mm["models"]) == (1, 1, ["tilt_m"])
    assert (mo["published"], mo["settled"], mo["models"]) == (2, 1, ["base_m"])
    assert mm["markets"]["1X2"]["n"] == 1 and mo["markets"]["1X2"]["n"] == 1
    assert perf["paired"]["fixtures"] == 1
    assert perf["paired"]["model_and_market"]["settled"] == 1
    assert perf["paired"]["model_only"]["settled"] == 1
    assert perf["last_settled_at_utc"].endswith("+00:00")
    # The string form still works: base_m is the record of record everywhere, and the
    # model + market record is still reported on its own.
    primary_file("base_m")
    perf = client.get("/api/performance").json()
    assert perf["published"] == 2 and perf["by_basis"]["model_and_market"]["published"] == 1
    every = client.get("/api/performance?primary_only=false").json()
    assert every["published"] == 3


TIMESTAMP = re.compile(r"^\d{4}-\d{2}-\d{2}[T ]\d{2}:\d{2}")


def timestamps(o, path=""):
    if isinstance(o, dict):
        for k, v in o.items():
            yield from timestamps(v, f"{path}.{k}")
    elif isinstance(o, list):
        for i, v in enumerate(o):
            yield from timestamps(v, f"{path}[{i}]")
    elif isinstance(o, str) and TIMESTAMP.match(o):
        yield path, o


def test_every_timestamp_has_a_utc_offset(client, migrated_engine, records, primary_file):
    # The second period starts long after these fixtures, so it changes nothing; its start is
    # sent like every other timestamp.
    primary_file(
        [
            {"from_utc": None, "models": ["tilt_m", "base_m"]},
            {"from_utc": "2030-01-01T00:00:00Z", "models": ["base_m"]},
        ]
    )
    settle_fixture_a(migrated_engine, 2, 2)
    urls = [
        "/api/health",
        "/api/fixtures",
        "/api/past",
        "/api/performance",
        "/api/refresh-history",
        "/api/coverage",
        *(f"/api/predictions/{i}" for i in records.values()),
    ]
    seen = 0
    for url in urls:
        for path, value in timestamps(client.get(url).json()):
            seen += 1
            assert "T" in value and value.endswith("+00:00"), (url, path, value)
    assert seen > 20


def test_iso_helpers():
    naive = datetime(2026, 10, 21, 18, 0, tzinfo=UTC).replace(tzinfo=None)  # stored form
    assert routes.iso_utc(naive) == "2026-10-21T18:00:00+00:00"
    assert routes.iso_utc(None) is None
    assert routes.iso_utc_text("2026-10-19 14:49:41.427434") == "2026-10-19T14:49:41.427434+00:00"
    assert routes.iso_utc_text("2026-10-21T19:00:00+01:00") == "2026-10-21T18:00:00+00:00"
    assert routes.iso_utc_text("not a time") == "not a time"


# --- Effective-from periods (D-026). SYNTHETIC records as above. ------------------------------


def period(start: datetime | None, *models: str) -> dict:
    return {"from_utc": models_of_record.format_utc(start), "models": list(models)}


def test_a_fixture_takes_the_period_in_force_at_its_kickoff(
    client, migrated_engine, records, primary_file, tmp_path
):
    """Fixture A kicks off at KICKOFF; fixture C (also with both records) a day later. With a
    switch between them, A keeps base_m and C takes tilt_m."""
    later = KICKOFF + timedelta(days=1)
    c = FixtureIn("E0", "2627", later.date(), later, "eng-mockford", "eng-testtown")
    tilt_features = {"model_params": TILT_PARAMS, "market_prices_used": SYNTHETIC_MARKET}
    (c_base,) = pub(migrated_engine, tmp_path, [c], "base_m")
    c_tilt_fx = FixtureIn(
        "E0", "2627", later.date(), later, "eng-mockford", "eng-testtown", features=tilt_features
    )
    (c_tilt,) = pub(migrated_engine, tmp_path, [c_tilt_fx], "tilt_m")
    switch = KICKOFF + timedelta(hours=6)
    primary_file([period(None, "base_m"), period(switch, "tilt_m", "base_m")])
    cards = fixture_cards(client)
    assert cards[records["a_base"]]["is_primary"] is True  # before the switch
    assert cards[records["a_tilt"]]["is_primary"] is False
    assert cards[c_tilt]["is_primary"] is True  # after it: the first listed model with a record
    assert cards[c_base]["is_primary"] is False
    # A switch exactly at a kickoff applies to that fixture.
    primary_file([period(None, "base_m"), period(KICKOFF, "tilt_m", "base_m")])
    assert fixture_cards(client)[records["a_tilt"]]["is_primary"] is True
    perf = client.get("/api/performance").json()
    assert perf["primary_model_periods"]["domestic"][1] == {
        "from_utc": KICKOFF.isoformat(),
        "models": ["tilt_m", "base_m"],
    }


def test_a_later_period_never_changes_a_settled_fixture(
    client, migrated_engine, records, primary_file
):
    """The verifier's counter-example: with a plain list, naming a new model of record after a
    fixture was played moved its headline record and so its accuracy. Appending a period from
    the time of the switch leaves every played fixture as it was."""
    primary_file("base_m")
    settle_fixture_a(migrated_engine, 0, 1)  # an away win: base_m picked home, tilt_m away

    def snapshot():
        perf = client.get("/api/performance").json()
        past = client.get("/api/past").json()["matches"]
        return (
            {k: perf[k] for k in ("published", "settled", "pending", "markets")},
            perf["by_basis"],
            perf["paired"],
            sorted((c["id"], c["is_primary"]) for c in past),
        )

    before = snapshot()
    assert before[0]["markets"]["1X2"]["accuracy"] == 0.0
    switched_at = KICKOFF + timedelta(hours=3)  # after fixture A was played and settled
    models_of_record.append_period(
        routes.PRIMARY_MODELS_PATH, "domestic", ["tilt_m", "base_m"], switched_at, now=switched_at
    )
    assert snapshot() == before
    detail = client.get(f"/api/predictions/{records['a_tilt']}").json()
    assert detail["is_primary"] is False
    # Fixture B (same kickoff, base_m only) keeps base_m too.
    assert fixture_cards(client)[records["b_base"]]["is_primary"] is True
    # The plain list (no start time) would have rewritten the played fixture: the test bites.
    primary_file(["tilt_m", "base_m"])
    rewritten = client.get("/api/performance").json()
    assert rewritten["markets"]["1X2"]["accuracy"] == 1.0


def test_a_period_starting_in_the_past_is_refused(records, primary_file):
    primary_file("base_m")
    with pytest.raises(ValueError, match="before now"):
        models_of_record.append_period(
            routes.PRIMARY_MODELS_PATH,
            "domestic",
            ["tilt_m", "base_m"],
            KICKOFF - timedelta(hours=1),
            now=KICKOFF,
        )


def test_appending_a_period_never_changes_a_fixture_that_kicked_off_before_it(
    client, migrated_engine, records, primary_file, tmp_path
):
    """Independent check (verifier): append a period from SWITCH and compare every fixture's
    record of record before and after. A fixture kicking off before SWITCH must not change; one
    kicking off at or after it takes the new period. SYNTHETIC fixtures and models.

    Includes a kickoff brought forward between records: the base_m record was saved with the
    old kickoff (after SWITCH), the tilt_m record with the new one (before SWITCH). The fixture
    kicked off before the switch was made, so it must keep base_m."""
    switch = KICKOFF + timedelta(hours=1)
    tilt_features = {"model_params": TILT_PARAMS, "market_prices_used": SYNTHETIC_MARKET}

    def both(home, away, day, base_kickoff, tilt_kickoff=None, comp="E0"):
        """A base_m record, then a tilt_m record (with its own kickoff if given)."""
        tk = base_kickoff if tilt_kickoff is None else tilt_kickoff
        (b,) = pub(
            migrated_engine,
            tmp_path,
            [FixtureIn(comp, "2627", day, base_kickoff, home, away)],
            "base_m",
        )
        (t,) = pub(
            migrated_engine,
            tmp_path,
            [FixtureIn(comp, "2627", day, tk, home, away, features=tilt_features)],
            "tilt_m",
        )
        return b, t

    nxt = DAY + timedelta(days=1)
    cases = {
        # name: ((base id, tilt id), kicked off before the switch?)
        "a: 14:00, an hour before": ((records["a_base"], records["a_tilt"]), True),
        "one second before": (
            both("eng-mockford", "eng-testtown", DAY, switch - timedelta(seconds=1)),
            True,
        ),
        "exactly at the switch": (both("eng-testtown", "eng-mockford", DAY, switch), False),
        "brought forward to 14:00": (
            both("eng-sampleton", "eng-testtown", DAY, KICKOFF + timedelta(hours=3), KICKOFF),
            True,
        ),
        "kickoff unknown, switch day": (both("eng-mockford", "eng-sampleton", DAY, None), True),
        # another competition: an E0 pairing meets once a season, so the same pairing on
        # another day would be the same fixture (footy.publish.FixtureIn.key)
        "kickoff unknown, next day": (
            both("eng-mockford", "eng-sampleton", nxt, None, comp="E1"),
            False,
        ),
    }
    primary_file("base_m")
    before = {cid: c["is_primary"] for cid, c in fixture_cards(client).items()}
    models_of_record.append_period(
        routes.PRIMARY_MODELS_PATH, "domestic", ["tilt_m", "base_m"], switch, now=switch
    )
    after = {cid: c["is_primary"] for cid, c in fixture_cards(client).items()}
    for name, ((b, t), played) in cases.items():
        assert before[b] is True and before[t] is False, name
        expected = (True, False) if played else (False, True)
        assert (after[b], after[t]) == expected, name
