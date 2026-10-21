"""football-data "extra leagues" layout: parser, download and import (SYNTHETIC data only)."""

from datetime import UTC, date, datetime

import httpx
import pytest
from sqlalchemy import func, select
from sqlalchemy.orm import Session

import fdextrafixtures as fx
from footy.ingest.fd_download import USER_AGENT, PoliteClient, fetch_extra_league_file
from footy.ingest.fd_extra_parse import (
    PLAYOFF_EXCLUSION,
    ExtraLayoutError,
    parse_extra_season,
    parse_fd_extra_csv,
)
from footy.ingest.fd_update import run_fd_update
from footy.models import DataCorrection, FdOdds, ImportIssue, Match, RawFile, RawImport

TODAY = date(2026, 6, 10)


def utc(*args):
    return datetime(*args, tzinfo=UTC)


@pytest.mark.parametrize(
    ("value", "key"),
    [("2018/2019", "1819"), ("2025/2026", "2526"), ("2018", "1818"), ("2025", "2525")],
)
def test_season_values(value, key):
    assert parse_extra_season(value) == key


@pytest.mark.parametrize("value", ["2018/2020", "18/19", "", "2018-2019"])
def test_bad_season_values(value):
    with pytest.raises(ValueError):
        parse_extra_season(value)


def test_parses_results_kickoffs_and_skips_old_seasons():
    pf = parse_fd_extra_csv(fx.csv_bytes(), "AUT")
    assert pf.rows_before_first_season == 1
    assert list(pf.seasons) == ["2526"]
    first = pf.seasons["2526"][0]
    assert (first.home_name, first.away_name, first.home_goals, first.away_goals) == (
        "Alpendorf",
        "Bergstadt",
        2,
        1,
    )
    assert first.result == "H" and first.match_date == date(2025, 8, 2)
    assert first.kickoff_local == "16:00" and first.kickoff_utc == utc(2025, 8, 2, 15)  # BST
    winter = pf.seasons["2526"][3]
    assert winter.kickoff_utc == utc(2025, 12, 6, 16)  # GMT
    # Results only: no half-time score, statistics or prices from this layout.
    assert first.ht_home_goals is None and first.odds == []
    assert set(first.stats.values()) == {None}


def test_repeat_meeting_is_kept_and_duplicate_rejected():
    rows = [*fx.ROWS, fx.ROWS[1], fx.ROWS[7]]  # both meetings of a pairing listed twice
    pf = parse_fd_extra_csv(fx.csv_bytes(rows), "AUT")
    codes = [(i.source_row, i.code, i.detail) for i in pf.issues if i.severity == "error"]
    assert codes == [
        (11, "duplicate_fixture", "2025/26: same match as row 2"),
        (12, "duplicate_fixture", "2025/26: same match as row 8"),
    ]
    stored = pf.seasons["2526"]
    assert len(stored) == 9
    meetings = [(m.source_row, m.meeting) for m in stored if m.home_name == "Alpendorf"]
    assert meetings == [(2, 1), (6, 1), (8, 2)]  # v Bergstadt twice, v Seeburg once
    assert all(m.meeting == 1 for m in stored if m.home_name != "Alpendorf")


PAIR = ("Alpendorf", "Bergstadt")


def test_meeting_beyond_max_meetings_is_held_back():
    later = [
        fx.row("2025/2026", day, "16:00", "Alpendorf", "Bergstadt", 1, 1)
        for day in ("04/04/2026", "02/05/2026")
    ]
    pf = parse_fd_extra_csv(fx.csv_bytes([*fx.ROWS, *later]), "AUT")  # AUT allows 3
    pairing = [m for m in pf.seasons["2526"] if (m.home_name, m.away_name) == PAIR]
    assert [(m.source_row, m.meeting) for m in pairing] == [(2, 1), (8, 2), (11, 3)]
    (held,) = [i for i in pf.issues if i.code == "repeat_pairing"]
    assert held.source_row == 12 and held.severity == "error"
    assert held.detail.startswith("2025/26: Alpendorf v Bergstadt on 2026-05-02 repeats row 2")
    assert "would be meeting 4 and AUT allows 3" in held.detail
    # The cap can be given explicitly (here: one meeting per pairing).
    pf = parse_fd_extra_csv(fx.csv_bytes(), "AUT", max_meetings=1)
    assert [(i.source_row, i.code) for i in pf.issues if i.severity == "error"] == [
        (8, "repeat_pairing")
    ]


def test_playoff_visitor_is_flagged_and_excluded():
    rows = list(fx.ROWS)
    # Repeat meetings count towards a club's matches, so the regular clubs reach the
    # visitor rule's minimum (median >= 10); meetings beyond AUT's three are held back.
    for i in range(5):
        rows += [
            fx.row("2025/2026", f"{10 + i:02d}/01/2026", "16:00", h, a, 1, 0)
            for h, a in [
                ("Alpendorf", "Bergstadt"),
                ("Bergstadt", "Seeburg"),
                ("Seeburg", "Alpendorf"),
            ]
        ]
    pf = parse_fd_extra_csv(fx.csv_bytes(rows), "AUT")
    stored = {(m.home_name, m.away_name): m for m in pf.seasons["2526"]}
    assert stored[("Talheim", "Seeburg")].model_exclusion == PLAYOFF_EXCLUSION
    assert stored[("Seeburg", "Talheim")].model_exclusion == PLAYOFF_EXCLUSION
    assert stored[("Bergstadt", "Seeburg")].model_exclusion is None
    flagged = [i for i in pf.issues if i.code == PLAYOFF_EXCLUSION]
    assert len(flagged) == 2 and all(i.severity == "warning" for i in flagged)
    assert "Talheim (2 matches)" in flagged[0].detail


def test_short_season_is_not_checked_for_visitors():
    pf = parse_fd_extra_csv(fx.csv_bytes(), "AUT")
    assert all(m.model_exclusion is None for m in pf.seasons["2526"])


def test_calendar_year_season_key():
    pf = parse_fd_extra_csv(fx.csv_bytes(fx.CALENDAR_ROWS), "AUT")
    assert list(pf.seasons) == ["2525"]


def test_row_level_rules():
    rows = [
        fx.row("2025/2026", "02/08/2025", "16:00", "Alpendorf", "Bergstadt", 2, 1, res="A"),
        fx.row("2025/2026", "02/08/2025", "16:00", "Seeburg", "Talheim", 1, 0, country="Italy"),
        fx.row("2025/2026", "31/02/2025", "16:00", "Bergstadt", "Seeburg", 1, 0),
        fx.row("2025/2026", "09/08/2025", "25:99", "Bergstadt", "Seeburg", 1, 0),
        fx.row("25-26", "09/08/2025", "16:00", "Seeburg", "Alpendorf", 1, 0),
        "Austria,Bundesliga,2025/2026,16/08/2025,16:00,Talheim,Alpendorf,,,," + fx.ODDS,
    ]
    pf = parse_fd_extra_csv(fx.csv_bytes(rows), "AUT")
    assert [(i.source_row, i.code) for i in pf.issues] == [
        (1, "result_mismatch"),
        (2, "wrong_country"),
        (3, "bad_date"),
        (4, "bad_time"),
        (5, "bad_season"),
        (6, "no_result"),
    ]
    (kept,) = pf.seasons["2526"]
    assert kept.kickoff_utc is None and kept.match_date == date(2025, 8, 9)


def test_other_league_label_is_a_file_warning():
    rows = [
        *fx.ROWS[1:3],
        fx.row(
            "2025/2026", "01/06/2026", "16:00", "Talheim", "Seeburg", 0, 1, league="Second League"
        ),
    ]
    pf = parse_fd_extra_csv(fx.csv_bytes(rows), "AUT")
    (warn,) = [i for i in pf.issues if i.code == "other_league_label"]
    assert warn.source_row == 0 and "Second League" in warn.detail


def test_wrong_layout_is_refused():
    with pytest.raises(ExtraLayoutError):
        parse_fd_extra_csv(b"Div,Date,HomeTeam,AwayTeam,FTHG,FTAG\r\n", "AUT")


def test_download_url_layout_check_and_refresh(migrated_engine, settings):
    requests, sleeps = [], []
    responses = [
        httpx.Response(200, content=fx.csv_bytes(), headers={"ETag": '"x1"'}),
        httpx.Response(304),
        httpx.Response(200, content=b"Div,Date,HomeTeam\r\n"),
    ]

    def handler(request):
        requests.append(request)
        return responses.pop(0)

    http = httpx.Client(transport=httpx.MockTransport(handler), headers={"User-Agent": USER_AGENT})
    client = PoliteClient(client=http, sleep=sleeps.append)

    def fetch(**kw):
        with Session(migrated_engine) as s:
            res = fetch_extra_league_file(s, client, settings.raw_dir, code="AUT", **kw)
            s.commit()
            return res

    assert fetch().outcome == "new_revision"
    assert str(requests[0].url) == "https://football-data.co.uk/new/AUT.csv"
    assert requests[0].headers["User-Agent"] == USER_AGENT
    assert fetch().outcome == "cached"  # under 12 hours old: no request
    assert fetch(refresh_hours=0).outcome == "not_modified"
    assert requests[1].headers["If-None-Match"] == '"x1"'
    res = fetch(force=True)
    assert res.outcome == "error" and "not a football-data CSV" in res.error
    assert len(requests) == 3
    with Session(migrated_engine) as s:
        raw = s.scalar(select(RawFile))
        assert (raw.source, raw.logical_key) == ("football-data-extra", "new/AUT")


def test_retry_after_is_honoured(migrated_engine, settings):
    responses = [
        httpx.Response(429, headers={"Retry-After": "30"}),
        httpx.Response(200, content=fx.csv_bytes()),
    ]
    sleeps = []
    http = httpx.Client(transport=httpx.MockTransport(lambda r: responses.pop(0)))
    client = PoliteClient(client=http, sleep=sleeps.append)
    with Session(migrated_engine) as s:
        res = fetch_extra_league_file(s, client, settings.raw_dir, code="AUT")
    assert res.outcome == "new_revision" and sleeps[0] == 30.0


def drop(settings, content: bytes, name: str = "new_AUT.csv") -> None:
    settings.fd_inbox_dir.mkdir(parents=True, exist_ok=True)
    (settings.fd_inbox_dir / name).write_bytes(content)


def update(engine, settings, **kw):
    return run_fd_update(
        engine, settings, today=TODAY, allow_download=False, reference=fx.REFERENCE, **kw
    )


def test_import_from_inbox_then_rerun_changes_nothing(migrated_engine, settings):
    drop(settings, fx.csv_bytes())
    detail = update(migrated_engine, settings)
    assert detail["inbox"] == {"inbox_new": 1}
    assert detail["import"]["matches_inserted"] == 9  # the repeat meeting included
    assert detail["import"]["rows_rejected"] == 0
    assert detail["import"]["odds_inserted"] == 0  # prices are never parsed from this layout

    with Session(migrated_engine) as s:
        matches = s.scalars(select(Match).order_by(Match.source_row)).all()
        assert {m.division for m in matches} == {"AUT"} and {m.season for m in matches} == {"2526"}
        assert matches[0].home_team == "aut-alpendorf" and matches[0].kickoff_utc == utc(
            2025, 8, 2, 15
        )
        pairing = [
            (m.meeting, m.match_date, m.home_goals, m.away_goals)
            for m in matches
            if (m.home_team, m.away_team) == ("aut-alpendorf", "aut-bergstadt")
        ]
        assert pairing == [(1, date(2025, 8, 2), 2, 1), (2, date(2026, 3, 7), 0, 1)]
        assert s.scalar(select(func.count()).select_from(FdOdds)) == 0
        imp = s.scalar(select(RawImport))
        assert imp.parser_version == "fdx-2" and imp.rows_imported == 9
        codes = {i.code for i in s.scalars(select(ImportIssue))}
        assert "repeat_pairing" not in codes and "duplicate_fixture" not in codes

    before = _snapshot(migrated_engine)
    update(migrated_engine, settings)
    update(migrated_engine, settings, reimport=True)
    assert _snapshot(migrated_engine) == before


def test_two_spellings_of_one_club_are_one_pairing(migrated_engine, settings):
    rows = [
        fx.row("2025/2026", "02/08/2025", "16:00", "Alpendorf", "Bergstadt", 2, 1),
        fx.row("2025/2026", "02/08/2025", "16:00", "Alpendorf FC", "Bergstadt", 2, 1),
        fx.row("2025/2026", "07/03/2026", "16:00", "Alpendorf FC", "Bergstadt", 0, 0),
        fx.row("2025/2026", "04/04/2026", "16:00", "Alpendorf", "Bergstadt", 1, 0),
        fx.row("2025/2026", "02/05/2026", "16:00", "Alpendorf FC", "Bergstadt", 3, 3),
    ]
    drop(settings, fx.csv_bytes(rows))
    detail = update(migrated_engine, settings)
    # Each spelling alone is within AUT's three meetings; as one club, row 2 repeats row 1's
    # date and row 5 would be a fourth meeting.
    assert detail["import"]["matches_inserted"] == 3
    with Session(migrated_engine) as s:
        stored = s.scalars(select(Match).order_by(Match.meeting)).all()
        assert [(m.meeting, m.source_row) for m in stored] == [(1, 1), (2, 3), (3, 4)]
        issues = {
            i.source_row: i
            for i in s.scalars(select(ImportIssue).where(ImportIssue.severity == "error"))
        }
        assert sorted(issues) == [2, 5]
        assert issues[2].code == "duplicate_fixture"
        assert issues[2].detail.startswith("2025/26: Alpendorf FC v Bergstadt on 2025-08-02")
        assert issues[5].code == "repeat_pairing"
        assert "would be meeting 4 and AUT allows 3" in issues[5].detail


def test_new_revision_logs_correction_and_missing_match(migrated_engine, settings):
    drop(settings, fx.csv_bytes())
    update(migrated_engine, settings)
    changed = [fx.row("2025/2026", "02/08/2025", "16:00", "Alpendorf", "Bergstadt", 3, 1)]
    drop(settings, fx.csv_bytes([*changed, *fx.ROWS[2:]]))
    detail = update(migrated_engine, settings)
    assert detail["import"]["corrections"] == 1
    with Session(migrated_engine) as s:
        m = s.scalar(
            select(Match).where(
                Match.home_team == "aut-alpendorf", Match.season == "2526", Match.meeting == 1
            )
        )
        assert m.home_goals == 3


def test_corrections_and_missing_rows_follow_the_meeting(migrated_engine, settings):
    drop(settings, fx.csv_bytes())
    update(migrated_engine, settings)
    with Session(migrated_engine) as s:
        ids = {
            m.meeting: m.id
            for m in s.scalars(
                select(Match).where(
                    Match.home_team == "aut-alpendorf", Match.away_team == "aut-bergstadt"
                )
            )
        }
    # A new revision changes the second meeting's score only.
    rows = list(fx.ROWS)
    rows[7] = fx.row("2025/2026", "07/03/2026", "16:00", "Alpendorf", "Bergstadt", 0, 2)
    drop(settings, fx.csv_bytes(rows))
    detail = update(migrated_engine, settings)
    assert detail["import"]["corrections"] == 1 and detail["import"]["matches_inserted"] == 0
    with Session(migrated_engine) as s:
        (corr,) = s.scalars(select(DataCorrection)).all()
        assert (corr.entity_id, corr.field, corr.old_value, corr.new_value) == (
            ids[2],
            "away_goals",
            "1",
            "2",
        )
        assert s.get(Match, ids[1]).away_goals == 1
    # The next revision drops the second meeting: its row stays, with a warning naming it.
    drop(settings, fx.csv_bytes([r for i, r in enumerate(rows) if i != 7]))
    update(migrated_engine, settings)
    with Session(migrated_engine) as s:
        (missing,) = s.scalars(
            select(ImportIssue).where(ImportIssue.code == "match_missing_from_revision")
        ).all()
        assert missing.detail == f"aut-alpendorf v aut-bergstadt meeting 2 (match {ids[2]})"
        assert s.get(Match, ids[2]) is not None


def test_scope_filters_divisions(migrated_engine, settings):
    drop(settings, fx.csv_bytes())
    detail = update(migrated_engine, settings, scope="core")
    assert detail["import"] == {}
    detail = update(migrated_engine, settings, scope="europe")
    assert detail["import"]["matches_inserted"] == 9


def _snapshot(engine):
    with Session(engine) as s:
        return (
            [
                (
                    m.id,
                    m.season,
                    m.home_team,
                    m.away_team,
                    m.meeting,
                    m.home_goals,
                    m.away_goals,
                    m.source_row,
                )
                for m in s.scalars(select(Match).order_by(Match.id))
            ],
            s.scalar(select(func.count()).select_from(RawFile)),
            sorted((i.source_row, i.code) for i in s.scalars(select(ImportIssue))),
        )
