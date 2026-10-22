"""Shadow records (footy.shadows, `footy publish-shadows`). SYNTHETIC fixtures, teams, models
and prices; the schedule and match history are stubbed out, records go to a temporary
database and records folder."""

import functools
import hashlib
import json
from datetime import UTC, date, datetime

import numpy as np
import pandas as pd
import pytest
from sqlalchemy import select
from sqlalchemy.orm import Session

import footy.live as live
from fdfixtures import REFERENCE
from footy import models_of_record, shadows
from footy.api.routes import RecordsOfRecord
from footy.fixtures import ScheduledMatch
from footy.ingest.fd_import import sync_reference
from footy.modeling.grid import markets_from_grid, poisson_grid
from footy.modeling.lab.market_blend import MarketTilt, consensus
from footy.models import Prediction, Team
from footy.publish import FixtureIn, PublishError, publish, recorded_refs

NOW = datetime(2026, 10, 23, 16, 0, tzinfo=UTC)  # Friday, after the collection
LATER = datetime(2026, 10, 24, 14, tzinfo=UTC)
AB = "E0:2627:2026-10-24:eng-a:eng-b"
CD = "E0:2627:2026-10-24:eng-c:eng-d"


class Fixed:
    name = "fixed"
    markets = frozenset({"1X2", "OU", "BTTS"})
    fits = 0

    def fit(self, history, cutoff):
        Fixed.fits += 1

    def predict(self, fixtures):
        n = len(fixtures)
        return markets_from_grid(
            poisson_grid(np.full(n, 1.5), np.full(n, 1.1), rho=-0.05),
            np.full(n, 1.5),
            np.full(n, 1.1),
        )

    def params(self):
        return {}


class Broken(Fixed):
    def fit(self, history, cutoff):
        raise RuntimeError("no data for this model")


def _match(day, kickoff, home, away, line):
    return ScheduledMatch(day, kickoff, home, away, None, None, line)


def _schedule(extra=()):
    return [
        (_match(date(2026, 10, 24), LATER, "A", "B", 10), "eng-a", "eng-b"),
        (_match(date(2026, 10, 24), LATER, "C", "D", 11), "eng-c", "eng-d"),
        *extra,
    ]


SYNTHETIC_TEAMS = [f"eng-{c}" for c in "abcdefghijkl"] + ["esp-a", "ita-b", "esp-d"]


@pytest.fixture
def engine(migrated_engine):
    with Session(migrated_engine) as s:
        sync_reference(s, *REFERENCE)
        s.add_all(Team(team_key=k, name=k, country=k[:3].upper()) for k in SYNTHETIC_TEAMS)
        s.commit()
    return migrated_engine


@pytest.fixture
def stub(monkeypatch, tmp_path):
    """The schedule, history and model version stubbed; the real publisher writing to a
    temporary records folder. Returns a setter for the schedule."""
    hist = pd.DataFrame(
        {
            "division": ["E0"],
            "date": [pd.Timestamp("2026-10-18")],
            "home_team": ["eng-a"],
            "away_team": ["eng-c"],
            "model_exclusion": [None],
        }
    )
    sched = {"fx": _schedule()}
    monkeypatch.setattr(live, "load_matches", lambda engine, divs: hist)
    monkeypatch.setattr(
        live, "upcoming", lambda *a, **k: (list(sched["fx"]), {"checked_results": 99})
    )
    monkeypatch.setattr(live, "model_version", lambda engine, extra="": "test")
    monkeypatch.setattr(live, "publish", functools.partial(publish, records_dir=tmp_path / "rec"))
    Fixed.fits = 0

    def set_schedule(fx):
        sched["fx"] = fx

    return set_schedule


def _shadow(model="fixed", role="machine_learning", group="domestic"):
    return shadows.Shadow(group, model, role, "A test model.")


def _run_one(engine, factory=Fixed, model="fixed", **kw):
    return shadows.run_one(engine, _shadow(model), factory, days=4, divisions=["E0"], now=NOW, **kw)


def _lines(tmp_path):
    files = sorted((tmp_path / "rec").glob("*.jsonl"))
    return [json.loads(x) for f in files for x in f.read_text().splitlines()]


# ---- the list -------------------------------------------------------------------------------


def test_the_shadow_list_names_registered_models_that_are_not_of_record():
    from footy.modeling.euro_registry import EURO_MODELS
    from footy.modeling.registry import candidates

    listed = shadows.load()
    assert set(listed) == {"domestic", "uefa"}
    domestic = {s.model for s in listed["domestic"]}
    uefa = {s.model for s in listed["uefa"]}
    assert domestic <= set(candidates())
    assert uefa <= set(EURO_MODELS)
    assert {"ml_online_market", "ml_stack_market", "ml_online_pure", "ml_stack_pure"} <= domestic
    of_record = shadows.models_of_record_from(datetime.now(UTC))
    assert not domestic & of_record["domestic"]
    assert not uefa & of_record["uefa"]
    for s in listed["domestic"] + listed["uefa"]:
        assert s.role in shadows.ROLES
        assert "\n" not in s.description and s.description.endswith(".")


@pytest.mark.parametrize(
    "entry, message",
    [
        ({"model": "x", "role": "hero", "description": "d"}, "unknown role"),
        ({"model": "x", "role": "baseline"}, "description"),
        ({"model": "x", "role": "baseline", "description": "two\nlines"}, "one line"),
        ({"model": "x", "role": "baseline", "description": "d", "colour": "red"}, "must have"),
    ],
)
def test_a_malformed_list_is_refused(tmp_path, entry, message):
    path = tmp_path / "shadow_models.json"
    path.write_text(json.dumps({"domestic": [entry]}))
    with pytest.raises(ValueError, match=message):
        shadows.load(path)


def test_a_model_listed_twice_is_refused(tmp_path):
    path = tmp_path / "shadow_models.json"
    e = {"model": "x", "role": "baseline", "description": "d"}
    path.write_text(json.dumps({"uefa": [e, e]}))
    with pytest.raises(ValueError, match="listed twice"):
        shadows.load(path)


# ---- notes ----------------------------------------------------------------------------------


def test_notes_say_shadow_record_and_role():
    sh = shadows.Shadow("domestic", "ml_online_market", "machine_learning", "Learns.")
    assert sh.notes == (
        "Shadow record (machine-learning challenger, D-028); not the model of record. Learns."
    )
    assert shadows.Shadow("uefa", "m", "challenger", "D.", "D-026").notes.startswith(
        "Shadow record (challenger, D-026); not the model of record."
    )
    assert "(baseline, D-025)" in shadows.Shadow("uefa", "m", "baseline", "D.").notes
    assert "(earlier model of record, D-026)" in (
        shadows.Shadow("uefa", "m", "earlier_record", "D.").notes
    )


def test_published_records_carry_the_notes(engine, stub, tmp_path):
    out = _run_one(engine)
    assert out.error is None and out.published == 2
    recs = _lines(tmp_path)
    assert [r["fixture_ref"] for r in recs] == [AB, CD]
    assert {r["notes"] for r in recs} == {_shadow().notes}
    assert {r["model_name"] for r in recs} == {"fixed"}
    assert {r["record_type"] for r in recs} == {"genuine_prematch"}


# ---- duplicates, kick-offs ------------------------------------------------------------------


def test_a_fixture_already_recorded_is_never_recorded_again(engine, stub, tmp_path):
    first = _run_one(engine)
    assert first.published == 2 and Fixed.fits == 1
    again = _run_one(engine)
    assert again.published == 0
    assert again.skipped == {live.RECORDED: 2}
    assert Fixed.fits == 1  # nothing new: the model isn't even fitted
    assert len(_lines(tmp_path)) == 2
    with Session(engine) as s:
        assert len(s.scalars(select(Prediction.id)).all()) == 2
    # a dry run says the same and writes nothing
    dry = _run_one(engine, dry_run=True)
    assert dry.published == 0 and dry.skipped == {live.RECORDED: 2}
    assert recorded_refs(engine, "fixed") == {AB, CD}


def test_a_new_fixture_is_added_without_repeating_the_others(engine, stub, tmp_path):
    _run_one(engine)
    stub(
        _schedule(
            [(_match(date(2026, 10, 25), LATER.replace(day=25), "E", "F", 12), "eng-e", "eng-f")]
        )
    )
    out = _run_one(engine)
    assert out.published == 1 and out.skipped == {live.RECORDED: 2} and out.fixtures == 3
    assert [r["fixture_ref"] for r in _lines(tmp_path)][-1] == "E0:2627:2026-10-25:eng-e:eng-f"


def test_fixtures_that_have_kicked_off_are_skipped(engine, stub, tmp_path):
    stub(
        _schedule(
            [
                # kicked off an hour before the run
                (
                    _match(
                        date(2026, 10, 23), datetime(2026, 10, 23, 15, tzinfo=UTC), "G", "H", 13
                    ),
                    "eng-g",
                    "eng-h",
                ),
                # today, and no kick-off time: its round has begun
                (_match(date(2026, 10, 23), None, "I", "J", 14), "eng-i", "eng-j"),
                # Monday, no kick-off time: its round began on Friday, 00:00 UK
                (_match(date(2026, 10, 26), None, "M", "N", 16), "eng-k", "eng-i"),
                # midweek, but no kick-off time yet: a shadow record waits for one
                (_match(date(2026, 10, 28), None, "K", "L", 15), "eng-k", "eng-l"),
            ]
        )
    )
    out = _run_one(engine)
    assert out.error is None
    assert out.published == 2 and out.fixtures == 6
    assert out.skipped == {live.KICKED_OFF: 1, live.DAY_STARTED: 2, live.NO_KICKOFF: 1}
    assert [r["fixture_ref"] for r in _lines(tmp_path)] == [AB, CD]


def test_a_fixture_that_kicks_off_while_the_model_fits_is_left_out():
    past = FixtureIn("E0", "2627", date(2000, 1, 1), datetime(2000, 1, 1, 15, tzinfo=UTC), "a", "b")
    future = FixtureIn(
        "E0", "2627", date(2100, 1, 1), datetime(2100, 1, 1, 15, tzinfo=UTC), "c", "d"
    )
    skipped = []
    # a live run reads the clock again once the forecasts are made
    started = datetime(2020, 1, 1, tzinfo=UTC)
    keep, stamp = live._still_before_kickoff([past, future], True, started, skipped)
    assert keep == [1] and stamp > started  # the records are stamped with the clock
    assert skipped == [{"fixture": past.ref, "reason": live.LATE}]


def test_publish_refuses_a_late_fixture_before_writing_anything(engine, tmp_path):
    ok = FixtureIn("E0", "2627", date(2026, 10, 24), LATER, "eng-a", "eng-b")
    late = FixtureIn(
        "E0", "2627", date(2026, 10, 23), datetime(2026, 10, 23, 15, tzinfo=UTC), "eng-c", "eng-d"
    )
    mp = markets_from_grid(poisson_grid([1.5, 1.5], [1.1, 1.1], -0.05), [1.5, 1.5], [1.1, 1.1])
    with pytest.raises(PublishError, match="kickoff has passed"):
        publish(
            engine,
            [ok, late],
            mp,
            model_name="m",
            model_version="v",
            training_cutoff_utc=NOW,
            data_freshness={},
            now=NOW,
            records_dir=tmp_path / "rec",
        )
    assert not (tmp_path / "rec").exists()
    assert recorded_refs(engine, "m") == set()


# ---- prices -----------------------------------------------------------------------------------


def _book():
    """Prices for eng-a v eng-b only (published on the collection day)."""
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


def _market():
    book = _book()
    return MarketTilt(Fixed, 1.0, 1.0, "market_test", prices=lambda: book)


def test_a_model_and_market_shadow_publishes_only_fixtures_with_prices(engine, stub, tmp_path):
    dry = _run_one(engine, _market, "market_test", dry_run=True)
    assert dry.published == 1 and dry.fixtures == 2
    assert dry.skipped == {live.NO_PRICES: 1}
    assert _lines(tmp_path) == []

    out = _run_one(engine, _market, "market_test")
    assert out.published == 1 and out.skipped == {live.NO_PRICES: 1}
    (rec,) = _lines(tmp_path)
    assert rec["fixture_ref"] == AB
    # the consensus probabilities it used, never a price
    m = rec["features"]["market_prices_used"]
    assert all(0 < m[k] < 1 for k in ("p_H", "p_D", "p_A", "p_over_2.5"))
    text = json.dumps(rec)
    for price in ("2.0", "3.5", "4.0", "1.8", "2.1"):
        assert f": {price}," not in text and f": {price}}}" not in text
    # the unpriced fixture is left for a later run with prices, not blocked by a record
    assert recorded_refs(engine, "market_test") == {AB}


def test_a_machine_learning_shadow_with_prices_publishes_the_priced_fixture(
    engine, stub, tmp_path, monkeypatch
):
    """The learners with prices (ml_online_market, ml_stack_market) go through the same price
    gate as MarketTilt: the priced fixture is published with the consensus it used, never a
    price, and the unpriced one is left for a later run."""
    from footy.modeling.lab.ml_online import MarketOnlineMixture
    from footy.modeling.walkforward import RESULT_COLUMNS

    days = pd.date_range("2026-08-01", "2026-10-17", freq="7D")
    pairs = [("eng-a", "eng-b"), ("eng-c", "eng-d"), ("eng-b", "eng-c"), ("eng-d", "eng-a")]
    rows = [
        {"id": 100 * i + j, "division": "E0", "date": d, "home_team": h, "away_team": a}
        for i, d in enumerate(days)
        for j, (h, a) in enumerate(pairs)
    ]
    hist = pd.DataFrame(rows).assign(model_exclusion=None, **dict.fromkeys(RESULT_COLUMNS, 1))
    monkeypatch.setattr(live, "load_matches", lambda engine, divs: hist)
    book = _book()

    def learner():
        return MarketOnlineMixture(
            "ml_test", ["fixed"], 1.0, prices=lambda: book, factories={"fixed": Fixed}
        )

    out = _run_one(engine, learner, "ml_test")
    assert out.error is None
    assert out.published == 1 and out.skipped == {live.NO_PRICES: 1}
    (rec,) = _lines(tmp_path)
    assert rec["fixture_ref"] == AB and rec["missing_fields"] == ["lineups", "injuries"]
    m = rec["features"]["market_prices_used"]
    assert all(0 < m[k] < 1 for k in ("p_H", "p_D", "p_A", "p_over_2.5"))
    assert m["n_books_1x2"] == 2 and m["books"] == ["B365", "Avg"]
    # the forecast is the prices' (weights 1 / 1 on every member's grid)
    assert rec["probabilities"]["1X2"]["H"] == pytest.approx(m["p_H"], abs=1e-6)
    text = json.dumps(rec)
    for price in ("2.0", "3.5", "4.0", "1.8", "2.1"):
        assert f": {price}," not in text and f": {price}}}" not in text


# ---- failures, models of record ---------------------------------------------------------------


def test_one_model_failing_does_not_stop_the_others(engine, stub, tmp_path):
    listed = {
        "domestic": [
            _shadow("broken"),
            _shadow("missing"),
            _shadow("fixed", "baseline"),
        ]
    }
    seen = []
    outs = shadows.run(
        engine,
        ["domestic"],
        days=4,
        divisions=["E0"],
        now=NOW,
        shadows=listed,
        factories={"domestic": {"broken": Broken, "fixed": Fixed}},
        report=seen.append,
    )
    assert [o.model for o in outs] == ["broken", "missing", "fixed"] == [o.model for o in seen]
    assert "RuntimeError: no data for this model" in outs[0].error
    assert "not a registered domestic model" in outs[1].error
    assert outs[2].error is None and outs[2].published == 2
    assert {r["model_name"] for r in _lines(tmp_path)} == {"fixed"}
    assert "FAILED" in outs[0].line() and "published   2" in outs[2].line()


def test_one_division_failing_does_not_stop_the_others(engine, stub, tmp_path, monkeypatch):
    """A league whose schedule can't be read (here Spain's, listed first) costs only its own
    fixtures: every model would otherwise lose every league after it."""
    real = live.upcoming

    def upcoming(engine, div, *a, **k):
        if div == "SP1":
            raise ValueError("SP1: mapping not verified")
        return real(engine, div, *a, **k)

    monkeypatch.setattr(live, "upcoming", upcoming)
    out = shadows.run_one(engine, _shadow(), Fixed, days=4, divisions=["SP1", "E0"], now=NOW)
    assert out.published == 2 and [r["fixture_ref"] for r in _lines(tmp_path)] == [AB, CD]
    assert out.error is not None and "SP1" in out.error and "mapping not verified" in out.error
    assert "FAILED" in out.line()


def test_a_model_of_record_is_never_run_as_a_shadow(engine, stub, tmp_path):
    primary = tmp_path / "primary_models.json"
    primary.write_text(json.dumps({"domestic": [{"from_utc": None, "models": ["fixed"]}]}))
    outs = shadows.run(
        engine,
        ["domestic"],
        days=4,
        divisions=["E0"],
        now=NOW,
        shadows={"domestic": [_shadow("fixed")]},
        primary_path=primary,
        factories={"domestic": {"fixed": Fixed}},
    )
    assert "model of record" in outs[0].error
    assert _lines(tmp_path) == [] and Fixed.fits == 0


def test_models_of_record_from_now_include_later_periods(tmp_path):
    primary = tmp_path / "primary_models.json"
    primary.write_text(
        json.dumps(
            {
                "uefa": [
                    {"from_utc": None, "models": ["old"]},
                    {"from_utc": "2026-10-21T04:30:00Z", "models": ["now"]},
                    {"from_utc": "2026-11-01T00:00:00Z", "models": ["next"]},
                ]
            }
        )
    )
    assert shadows.models_of_record_from(NOW, primary) == {"uefa": {"now", "next"}}


def test_shadow_records_never_change_the_record_of_record(engine, stub, tmp_path):
    """The model of record keeps every fixture it has a record for, a fixture it has none for
    gets none, and primary_models.json is not touched."""
    primary = tmp_path / "primary_models.json"
    primary.write_text(
        json.dumps(
            {
                "domestic": [
                    {"from_utc": None, "models": ["old_record"]},
                    {"from_utc": "2026-10-23T12:00:00Z", "models": ["of_record", "fallback"]},
                ]
            }
        )
    )
    real = shadows.PRIMARY_MODELS_PATH
    before = hashlib.sha256(primary.read_bytes()).hexdigest()
    real_before = hashlib.sha256(real.read_bytes()).hexdigest()
    # the model of record has a record for eng-a v eng-b only (published at 13:00)
    mp = markets_from_grid(poisson_grid([1.2], [1.0], -0.05), [1.2], [1.0])
    publish(
        engine,
        [FixtureIn("E0", "2627", date(2026, 10, 24), LATER, "eng-a", "eng-b")],
        mp,
        model_name="of_record",
        model_version="v",
        training_cutoff_utc=NOW,
        data_freshness={},
        now=NOW.replace(hour=13),
        records_dir=tmp_path / "rec",
    )

    def of_record():
        with Session(engine) as s:
            ror = RecordsOfRecord(s, models_of_record.load(primary))
            return {ref: ror.model_for(ref, "E0") for ref in (AB, CD)}

    assert of_record() == {AB: "of_record", CD: None}
    listed = {
        "domestic": [
            _shadow("shadow_ml"),
            _shadow("old_record", "earlier_record"),  # listed only in an earlier period
        ]
    }
    outs = shadows.run(
        engine,
        ["domestic"],
        days=4,
        divisions=["E0"],
        now=NOW,
        shadows=listed,
        primary_path=primary,
        factories={"domestic": {"shadow_ml": Fixed, "old_record": Fixed}},
    )
    assert [o.published for o in outs] == [2, 2]
    assert of_record() == {AB: "of_record", CD: None}
    with Session(engine) as s:
        ror = RecordsOfRecord(s, models_of_record.load(primary))
        flags = {
            (p.model_name, p.fixture_ref): ror.is_primary(p) for p in s.scalars(select(Prediction))
        }
    assert {k for k, v in flags.items() if v} == {("of_record", AB)}
    assert hashlib.sha256(primary.read_bytes()).hexdigest() == before
    assert hashlib.sha256(real.read_bytes()).hexdigest() == real_before


# ---- UEFA ---------------------------------------------------------------------------------------


def test_a_uefa_model_without_team_ratings_can_publish(engine, monkeypatch, tmp_path):
    """The frequency baseline has no ratings: its records' rating features are empty. The
    same skips apply as in league games."""
    import footy.modeling.euro as euro
    import footy.modeling.euro_eval as euro_eval
    from footy.modeling.euro_registry import euro_factory

    k = pd.Timestamp("2026-10-23 19:00", tz="UTC")
    NA = np.nan
    played = [
        ("UEL", "euro", "played", pd.Timestamp("2026-09-25"), "esp-a", "ita-b", 2, 1, pd.NaT),
        ("UEL", "euro", "played", pd.Timestamp("2026-10-02"), "ita-b", "eng-c", 0, 0, pd.NaT),
        ("UEL", "euro", "played", pd.Timestamp("2026-10-02"), "eng-c", "esp-a", 1, 3, pd.NaT),
    ]
    scheduled = [
        ("UEL", "euro", "scheduled", pd.Timestamp("2026-10-23"), "esp-a", "eng-c", NA, NA, k),
        ("UEL", "euro", "scheduled", pd.Timestamp("2026-10-23"), "ita-b", "esp-d", NA, NA, k),
        # kicked off at 15:00, an hour before the run
        (
            "UEL",
            "euro",
            "scheduled",
            pd.Timestamp("2026-10-23"),
            "eng-c",
            "ita-b",
            NA,
            NA,
            pd.Timestamp("2026-10-23 15:00", tz="UTC"),
        ),
    ]
    df = pd.DataFrame(
        played + scheduled,
        columns=[
            "comp",
            "kind",
            "status",
            "date",
            "home_team",
            "away_team",
            "home_goals",
            "away_goals",
            "kickoff_utc",
        ],
    )
    df["season"] = "2627"
    df["neutral"] = 0
    df["stage"] = "League MD3"
    df["outcome"] = np.where(
        df.home_goals > df.away_goals, 0, np.where(df.home_goals == df.away_goals, 1, 2)
    )
    monkeypatch.setattr(euro, "load_combined", lambda engine, include_scheduled=False: df)
    monkeypatch.setattr(euro_eval, "team_countries", lambda engine: {})
    monkeypatch.setattr(live, "model_version", lambda engine, extra="": "test")
    monkeypatch.setattr(live, "publish", functools.partial(publish, records_dir=tmp_path / "rec"))
    sh = shadows.Shadow("uefa", "euro_frequency", "baseline", "Rates.")
    out = shadows.run_one(engine, sh, euro_factory("euro_frequency"), days=1, now=NOW)
    assert out.error is None, out.error
    assert out.published == 2 and out.skipped == {live.KICKED_OFF: 1}
    recs = _lines(tmp_path)
    assert {r["model_name"] for r in recs} == {"euro_frequency"}
    assert all(r["features"]["home_attack_defence"] is None for r in recs)
    assert all(abs(sum(r["probabilities"]["1X2"].values()) - 1) < 1e-9 for r in recs)
    again = shadows.run_one(engine, sh, euro_factory("euro_frequency"), days=1, now=NOW)
    assert again.published == 0 and again.skipped == {live.KICKED_OFF: 1, live.RECORDED: 2}


def test_an_undated_fixture_waits_unless_it_already_has_a_record_without_a_time(
    engine, stub, tmp_path
):
    """A record without a kick-off time dates its fixture from 00:00 UK, so a shadow record
    without one could move the fixture into an earlier model-of-record period; where the
    model of record's own record has no time either, nothing can move."""
    kl = "E0:2627:2026-10-28:eng-k:eng-l"
    stub([(_match(date(2026, 10, 28), None, "K", "L", 15), "eng-k", "eng-l")])
    assert _run_one(engine).skipped == {live.NO_KICKOFF: 1}
    mp = markets_from_grid(poisson_grid([1.2], [1.0], -0.05), [1.2], [1.0])
    publish(
        engine,
        [FixtureIn("E0", "2627", date(2026, 10, 28), None, "eng-k", "eng-l")],
        mp,
        model_name="of_record",
        model_version="v",
        training_cutoff_utc=NOW,
        data_freshness={},
        now=NOW,
        records_dir=tmp_path / "rec",
    )
    assert shadows.undated_refs(engine) == {kl}
    out = _run_one(engine)
    assert out.published == 1 and out.skipped == {}
    assert [r["fixture_ref"] for r in _lines(tmp_path)] == [kl, kl]


def test_the_command_refuses_a_model_that_is_not_listed(capsys):
    from footy.cli import main

    with pytest.raises(SystemExit) as e:
        main(["publish-shadows", "--group", "uefa", "--models", "ml_online_market", "--dry-run"])
    assert e.value.code == 2
    assert "not listed as uefa shadow models: ml_online_market" in capsys.readouterr().err


def test_a_fixture_re_dated_since_its_record_is_not_recorded_again(engine, stub, tmp_path):
    """openfootball's placeholder date (a whole round on its Sunday, no times) is replaced by
    the real one once football-data lists the fixture (audit M1, 22 Oct 2026): the model's
    record of the placeholder still counts, so it isn't fitted or recorded again."""
    stub([(_match(date(2026, 10, 25), None, "A", "B", 10), "eng-a", "eng-b")])
    placeholder = "E0:2627:2026-10-25:eng-a:eng-b"
    first = shadows.run_one(
        engine,
        _shadow(),
        Fixed,
        days=4,
        divisions=["E0"],
        now=datetime(2026, 10, 21, 12, tzinfo=UTC),
        undated={placeholder},  # as when the model of record had recorded it without a time
    )
    assert first.published == 1 and Fixed.fits == 1
    stub([(_match(date(2026, 10, 24), LATER, "A", "B", 10), "eng-a", "eng-b")])
    again = _run_one(engine)
    assert again.published == 0 and again.skipped == {live.RECORDED: 1}
    assert Fixed.fits == 1
    assert [r["fixture_ref"] for r in _lines(tmp_path)] == [placeholder]
