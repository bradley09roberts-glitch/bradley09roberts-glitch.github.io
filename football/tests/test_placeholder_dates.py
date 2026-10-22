"""Placeholder fixture dates (audit M1, 22 Oct 2026). openfootball listed every La Liga matchday
10 game of 2026/27 on "Sun Oct 25" without times while they ran Friday to Monday. The price
join, the record's identity, the pre-match deadline and settlement must not depend on such a
date. SYNTHETIC fixtures, teams, prices and schedules throughout."""

import json
from datetime import UTC, date, datetime

import numpy as np
import pandas as pd
import pytest
from sqlalchemy import text
from sqlalchemy.orm import Session

from fdfixtures import REFERENCE
from footy.fixtures import ScheduledMatch, upcoming, with_fd_dates
from footy.ingest.fd_fixtures import COLUMNS
from footy.ingest.fd_import import sync_reference
from footy.modeling.grid import markets_from_grid, poisson_grid
from footy.modeling.lab.market_blend import MarketTilt, consensus
from footy.models import Team
from footy.publish import FixtureIn, publish, recorded_fixtures, recorded_refs, settle

FRIDAY = datetime(2026, 10, 23, 16, 0, tzinfo=UTC)  # after Friday's collection
SPANISH = ["esp-a", "esp-b", "esp-c", "esp-d", "esp-e", "esp-f"]


def _snapshot(*rows):
    """A fixtures.csv snapshot as `fd_fixtures.latest_snapshot` returns it (one 1X2 price
    row per fixture is enough for the dates)."""
    out = [
        {
            "division": div,
            "date": day,
            "kickoff_local": ko,
            "home_team": h,
            "away_team": a,
            "bookmaker": "B365",
            "market_key": "1X2",
            "selection": "H",
            "price": 2.0,
            "quote_time_utc": FRIDAY,
            "published_utc": FRIDAY,
        }
        for div, day, ko, h, a in rows
    ]
    df = pd.DataFrame(out, columns=COLUMNS)
    df.attrs["raw_file_id"] = 7
    df.attrs["retrieved_at"] = FRIDAY.isoformat()
    return df


def _placeholder(home, away, line=1):
    return ScheduledMatch(date(2026, 10, 25), None, home, away, None, None, line)


# ---- dates: football-data's fixtures.csv is the authority -----------------------------------


def test_fixtures_csv_dates_a_placeholder_fixture():
    fixtures = [
        (_placeholder("A", "B"), "esp-a", "esp-b"),
        (_placeholder("C", "D", 2), "esp-c", "esp-d"),
        (_placeholder("E", "F", 3), "esp-e", "esp-f"),
    ]
    snap = _snapshot(
        ("SP1", "2026-10-23", "20:00", "esp-a", "esp-b"),  # really on the Friday, 20:00 UK
        ("SP1", "2026-10-25", None, "esp-c", "esp-d"),  # the same day, no time either
        ("SP1", "2026-10-18", "15:00", "esp-e", "esp-f"),  # an older file's row: ignored
    )
    out, changed = with_fd_dates(fixtures, "SP1", snap, date(2026, 10, 23))
    (ab, _, _), (cd, _, _), (ef, _, _) = out
    assert ab.match_date == date(2026, 10, 23)
    assert ab.kickoff_utc == datetime(2026, 10, 23, 19, 0, tzinfo=UTC)  # 20:00 BST
    assert "fixtures.csv (raw file 7" in ab.date_source and ab.line_no == 1
    assert (cd.match_date, cd.kickoff_utc, cd.date_source) == (date(2026, 10, 25), None, None)
    assert (ef.match_date, ef.kickoff_utc) == (date(2026, 10, 25), None)
    assert changed == [
        {
            "fixture": "esp-a v esp-b",
            "openfootball": ["2026-10-25", None],
            "football_data": ["2026-10-23", "2026-10-23T19:00:00+00:00"],
        }
    ]


def test_a_league_where_pairings_meet_more_than_once_keeps_its_schedule_dates():
    fixtures = [(_placeholder("A", "B"), "sco-a", "sco-b")]
    snap = _snapshot(("SC0", "2026-10-24", "15:00", "sco-a", "sco-b"))
    assert with_fd_dates(fixtures, "SC0", snap, date(2026, 10, 23)) == (fixtures, [])


SCHEDULE = """= Testland Primera 2026/27

# Date       Sat Aug 15 2026 - Sun May 30 2027 (288d)

▪ Matchday 1
  Sat Aug 15 2026
    19:30  Testtown CF   v Sampleton   1-0 (0-0)
  Sun Aug 16
    19:30  Sampleton     v Mockford    0-0 (0-0)
    21:30  Mockford      v Testtown CF 1-3 (0-2)

▪ Matchday 10
  Sun Oct 25
           Sampleton     v Testtown CF
"""


def test_upcoming_takes_the_date_and_kickoff_from_fixtures_csv(migrated_engine, tmp_path):
    with Session(migrated_engine) as s:
        sync_reference(s, *REFERENCE)
        s.commit()
    with migrated_engine.begin() as conn:
        conn.execute(
            text(
                "INSERT INTO raw_files (id, source, logical_key, revision, sha256, size_bytes, "
                "retrieved_at, storage_path) VALUES (1, 't', 'k', 1, 'x', 1, '2026-10-01', 'p')"
            )
        )
        for i, (h, a, hg, ag, day) in enumerate(
            [
                ("eng-testtown", "eng-sampleton", 1, 0, "2026-08-15"),
                ("eng-sampleton", "eng-mockford", 0, 0, "2026-08-16"),
                ("eng-mockford", "eng-testtown", 1, 3, "2026-08-16"),
            ],
            1,
        ):
            conn.execute(
                text(
                    "INSERT INTO matches (division, season, match_date, home_team, away_team, "
                    "home_goals, away_goals, result, source_row, first_raw_file_id, "
                    "last_raw_file_id) VALUES ('SP1', '2627', :d, :h, :a, :hg, :ag, 'H', :i, 1, 1)"
                ),
                {"d": day, "h": h, "a": a, "hg": hg, "ag": ag, "i": i},
            )
    path = tmp_path / "espana" / "2026-27" / "1-liga.txt"
    path.parent.mkdir(parents=True)
    path.write_text(SCHEDULE, encoding="utf-8")
    kw = dict(days=4, root=tmp_path)
    plain, _ = upcoming(migrated_engine, "SP1", "2627", date(2026, 10, 22), fd_snapshot=None, **kw)
    ((x, h, a),) = plain
    assert (h, a, x.match_date, x.kickoff_utc) == (
        "eng-sampleton",
        "eng-testtown",
        date(2026, 10, 25),
        None,
    )
    snap = _snapshot(("SP1", "2026-10-23", "20:00", "eng-sampleton", "eng-testtown"))
    dated, check = upcoming(
        migrated_engine, "SP1", "2627", date(2026, 10, 22), fd_snapshot=snap, **kw
    )
    ((x, _, _),) = dated
    assert x.match_date == date(2026, 10, 23)
    assert x.kickoff_utc == datetime(2026, 10, 23, 19, 0, tzinfo=UTC)
    assert check["checked_results"] == 3 and len(check["fd_dates"]) == 1
    # a fixture football-data moves out of the window leaves it
    later = _snapshot(("SP1", "2026-10-30", "20:00", "eng-sampleton", "eng-testtown"))
    gone, _ = upcoming(migrated_engine, "SP1", "2627", date(2026, 10, 22), fd_snapshot=later, **kw)
    assert gone == []


# ---- the price join ---------------------------------------------------------------------------


class Fixed:
    name = "fixed"

    def fit(self, history, cutoff):
        pass

    def predict(self, fixtures):
        n = len(fixtures)
        return markets_from_grid(poisson_grid(np.full(n, 1.4), np.full(n, 1.1)))

    def params(self):
        return {}


def _book(*fixtures):
    """Friday's prices (published 13:00, retrieved 15:00 UTC) for (division, date, home, away)."""
    q = pd.Timestamp("2026-10-23 15:00", tz="UTC")
    rows = []
    for div, day, h, a in fixtures:
        for book in ("B365", "Avg"):
            rows += [
                (div, day, h, a, book, "1X2", s, p, q)
                for s, p in (("H", 2.1), ("D", 3.6), ("A", 3.4))
            ]
            rows += [(div, day, h, a, book, "OU_2.5", s, p, q) for s, p in (("O", 1.8), ("U", 2.1))]
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


def _used(book, rows, season="2627"):
    m = MarketTilt(Fixed, 1.0, 1.0, "t", prices=lambda: book)
    m.fit(pd.DataFrame(), pd.Timestamp("2026-10-23", tz="UTC"))
    m.decision_time = FRIDAY
    fx = pd.DataFrame(
        {
            "division": [r[0] for r in rows],
            "season": season,
            "date": pd.to_datetime([r[1] for r in rows]),
            "home_team": [r[2] for r in rows],
            "away_team": [r[3] for r in rows],
            "kickoff_utc": [None] * len(rows),
        }
    )
    m.predict(fx)
    return m.last_used


def test_a_placeholder_date_still_finds_the_pairings_prices():
    """Before 22 Oct 2026 the join was on the date: Barcelona v Real Madrid priced for
    Saturday 24 Oct found nothing for the fixture dated Sunday 25 Oct (p_H NaN)."""
    book = _book(("SP1", "2026-10-24", "esp-a", "esp-b"))
    used = _used(book, [("SP1", "2026-10-25", "esp-a", "esp-b")])
    assert used.used_1x2.tolist() == [True] and used.used_ou.tolist() == [True]
    assert used.p_H[0] == pytest.approx(_book(("SP1", "2026-10-25", "esp-a", "esp-b")).p_H.iloc[0])
    assert pd.isna(used.refused[0])


def test_the_pairing_join_is_only_for_single_meeting_leagues_and_the_same_season():
    book = _book(
        ("SC0", "2026-10-24", "sco-a", "sco-b"),  # a pairing can meet three times a season
        ("SP1", "2025-10-24", "esp-c", "esp-d"),  # last season's meeting
        ("SP1", "2026-10-24", "esp-e", "esp-f"),
    )
    used = _used(
        book,
        [
            ("SC0", "2026-10-25", "sco-a", "sco-b"),
            ("SP1", "2026-10-25", "esp-c", "esp-d"),
            ("SP1", "2026-10-25", "esp-f", "esp-e"),  # the reverse pairing is another match
        ],
    )
    assert used.used_1x2.tolist() == [False, False, False]


# ---- identity: one record per model and single-meeting pairing --------------------------------


@pytest.fixture
def engine(migrated_engine):
    with Session(migrated_engine) as s:
        sync_reference(s, *REFERENCE)
        s.add_all(Team(team_key=k, name=k, country="ESP") for k in SPANISH)
        s.commit()
    return migrated_engine


def _publish(engine, tmp_path, fx, model, now):
    mp = markets_from_grid(poisson_grid([1.5], [1.1], -0.05), [1.5], [1.1])
    return publish(
        engine,
        [fx],
        mp,
        model_name=model,
        model_version="v",
        training_cutoff_utc=datetime(2026, 10, 21, tzinfo=UTC),
        data_freshness={},
        now=now,
        records_dir=tmp_path / "rec",
    )


def _lines(tmp_path):
    return [
        json.loads(x)
        for f in sorted((tmp_path / "rec").glob("*.jsonl"))
        for x in f.read_text().splitlines()
    ]


PLACEHOLDER = FixtureIn("SP1", "2627", date(2026, 10, 25), None, "esp-a", "esp-b")
REAL = FixtureIn(
    "SP1", "2627", date(2026, 10, 23), datetime(2026, 10, 23, 19, tzinfo=UTC), "esp-a", "esp-b"
)


def test_a_re_dated_fixture_is_not_recorded_twice_by_a_model(engine, tmp_path):
    """Before 22 Oct 2026 the real date gave the fixture a new reference, and every model
    that had recorded the placeholder published a second "genuine" record of the match."""
    (first,) = _publish(engine, tmp_path, PLACEHOLDER, "m", datetime(2026, 10, 21, tzinfo=UTC))
    assert _publish(engine, tmp_path, REAL, "m", datetime(2026, 10, 23, 12, tzinfo=UTC)) == [first]
    assert len(_lines(tmp_path)) == 1
    assert REAL.ref not in recorded_refs(engine, "m")
    assert REAL.key in recorded_fixtures(engine, "m") and PLACEHOLDER.ref in recorded_fixtures(
        engine, "m"
    )


def test_a_new_models_record_of_a_re_dated_fixture_joins_the_others(engine, tmp_path):
    """Its record keeps the real date and kick-off but takes the fixture's first reference,
    so the match page lists every model's record of the match together."""
    _publish(engine, tmp_path, PLACEHOLDER, "m", datetime(2026, 10, 21, tzinfo=UTC))
    (new,) = _publish(engine, tmp_path, REAL, "market", datetime(2026, 10, 23, 12, tzinfo=UTC))
    rec = _lines(tmp_path)[-1]
    assert rec["id"] == new and rec["model_name"] == "market"
    assert rec["fixture_ref"] == PLACEHOLDER.ref
    assert (rec["match_date"], rec["kickoff_utc"]) == ("2026-10-23", "2026-10-23T19:00:00+00:00")


def test_uefa_fixtures_keep_the_date_in_their_identity(engine, tmp_path):
    """A UEFA pairing can meet twice in a season (league phase and knockout), so a fixture on
    another date is another match."""
    with Session(engine) as s:
        s.add_all(Team(team_key=k, name=k, country="X") for k in ("x-a", "x-b"))
        s.commit()
    one = FixtureIn(
        "UCL", "2627", date(2026, 11, 4), datetime(2026, 11, 4, 20, tzinfo=UTC), "x-a", "x-b"
    )
    two = FixtureIn(
        "UCL", "2627", date(2027, 2, 17), datetime(2027, 2, 17, 20, tzinfo=UTC), "x-a", "x-b"
    )
    now = datetime(2026, 10, 30, tzinfo=UTC)
    assert _publish(engine, tmp_path, one, "m", now) != _publish(engine, tmp_path, two, "m", now)
    assert len(_lines(tmp_path)) == 2


def test_a_placeholder_record_settles_on_the_real_match_whatever_its_date(engine, tmp_path):
    """The 140 records of 21-22 Oct dated 25 Oct must settle against the real match."""
    _publish(engine, tmp_path, PLACEHOLDER, "m", datetime(2026, 10, 21, tzinfo=UTC))
    _publish(engine, tmp_path, REAL, "market", datetime(2026, 10, 23, 12, tzinfo=UTC))
    with engine.begin() as conn:
        conn.execute(
            text(
                "INSERT INTO raw_files (id, source, logical_key, revision, sha256, size_bytes, "
                "retrieved_at, storage_path) VALUES (1, 't', 'k', 1, 'x', 1, '2026-10-24', 'p')"
            )
        )
        conn.execute(
            text(
                "INSERT INTO matches (division, season, match_date, home_team, away_team, "
                "home_goals, away_goals, result, source_row, first_raw_file_id, last_raw_file_id) "
                "VALUES ('SP1', '2627', '2026-10-23', 'esp-a', 'esp-b', 2, 1, 'H', 1, 1, 1)"
            )
        )
    assert settle(engine, now=datetime(2026, 10, 24, tzinfo=UTC)) == {"settled": 2}


# ---- display ----------------------------------------------------------------------------------


def test_the_site_shows_a_re_dated_fixture_on_its_real_date(client, engine, tmp_path):
    """A record dated by the placeholder is shown on the date and kickoff its fixture is last
    known by (a later record's, or the played match's); the saved values come alongside."""
    (old,) = _publish(engine, tmp_path, PLACEHOLDER, "m", datetime(2026, 10, 21, tzinfo=UTC))
    (new,) = _publish(engine, tmp_path, REAL, "market", datetime(2026, 10, 23, 12, tzinfo=UTC))
    body = client.get(f"/api/predictions/{old}").json()
    assert (body["match_date"], body["kickoff_utc"]) == ("2026-10-23", "2026-10-23T19:00:00+00:00")
    assert (body["recorded_match_date"], body["recorded_kickoff_utc"]) == ("2026-10-25", None)
    body = client.get(f"/api/predictions/{new}").json()
    assert body["match_date"] == "2026-10-23" and "recorded_match_date" not in body
    # a fixture whose records all have the placeholder: the played match's date once in
    cd = FixtureIn("SP1", "2627", date(2026, 10, 25), None, "esp-c", "esp-d")
    (only,) = _publish(engine, tmp_path, cd, "m", datetime(2026, 10, 21, tzinfo=UTC))
    assert client.get(f"/api/predictions/{only}").json()["match_date"] == "2026-10-25"
    with engine.begin() as conn:
        conn.execute(
            text(
                "INSERT INTO raw_files (id, source, logical_key, revision, sha256, size_bytes, "
                "retrieved_at, storage_path) VALUES (1, 't', 'k', 1, 'x', 1, '2026-10-24', 'p')"
            )
        )
        conn.execute(
            text(
                "INSERT INTO matches (division, season, match_date, home_team, away_team, "
                "home_goals, away_goals, result, source_row, first_raw_file_id, last_raw_file_id) "
                "VALUES ('SP1', '2627', '2026-10-24', 'esp-c', 'esp-d', 0, 0, 'D', 1, 1, 1)"
            )
        )
    body = client.get(f"/api/predictions/{only}").json()
    assert (body["match_date"], body["recorded_match_date"]) == ("2026-10-24", "2026-10-25")
