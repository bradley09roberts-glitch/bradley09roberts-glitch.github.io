"""Integrity checks on `euro_matches` (footy.europe.euro_integrity).

All rows here are SYNTHETIC: team keys are real-looking, dates and scores are made up."""

from dataclasses import replace
from datetime import UTC, date, datetime

from sqlalchemy import create_engine
from sqlalchemy.orm import Session

from footy.db import Base
from footy.europe.euro_integrity import (
    DomesticRec,
    EuroRec,
    check_calendar,
    check_database,
    check_duplicates,
    check_format,
    check_values,
    report_markdown,
    run_checks,
    split_leg,
)
from footy.models import EuroMatch, Team

NOW = datetime(2026, 10, 21, 12, 0, tzinfo=UTC)


def rec(i: int, home: str, away: str, day: date, hg=1, ag=0, **kw) -> EuroRec:
    values = {
        "id": i,
        "competition": "UCL",
        "season": "2526",
        "stage": "League MD1",
        "match_date": day,
        "kickoff_utc": datetime(day.year, day.month, day.day, 19, 0, tzinfo=UTC),
        "home": home,
        "away": away,
        "home_goals": hg,
        "away_goals": ag,
        "extra_time": False,
        "penalties": False,
        "status": "played",
    }
    values.update(kw)
    return EuroRec(**values)


def checks(findings) -> list[str]:
    return sorted(f.check for f in findings)


def test_split_leg():
    assert split_leg("Round of 16 leg 2") == ("Round of 16", 2)
    assert split_leg("Q2 CP leg 1") == ("Q2 CP", 1)
    assert split_leg("Final") == ("Final", None)


def test_consistent_rows_give_no_findings():
    rows = [
        rec(1, "a", "b", date(2026, 2, 17), 1, 0, stage="Round of 16 leg 1"),
        rec(2, "b", "a", date(2026, 2, 24), 1, 0, stage="Round of 16 leg 2", extra_time=True),
        rec(3, "c", "d", date(2026, 5, 30), 1, 1, stage="Final", extra_time=True, penalties=True),
        rec(
            4,
            "e",
            "f",
            date(2026, 11, 4),
            None,
            None,
            stage="Quarter-final leg 1",
            status="scheduled",
        ),
    ]
    assert run_checks(rows, now=NOW) == []


def test_same_pairing_on_one_day_in_two_competitions_is_a_duplicate():
    rows = [
        rec(1, "a", "b", date(2025, 7, 10), competition="UCLQ", stage="Q1 leg 1"),
        rec(2, "a", "b", date(2025, 7, 10), competition="UECLQ", stage="Q1 leg 1"),
    ]
    assert "duplicate_pairing_same_day" in checks(check_duplicates(rows))


def test_rescheduled_copy_of_a_fixture_is_caught():
    rows = [
        rec(1, "a", "b", date(2025, 10, 1), None, None, status="scheduled"),
        rec(2, "a", "b", date(2025, 10, 2), stage="League MD2"),
    ]
    assert checks(check_duplicates(rows)) == ["repeat_pairing_in_stage", "repeat_pairing_near_date"]


def test_group_meetings_home_and_away_are_not_repeats():
    rows = [
        rec(1, "a", "b", date(2022, 9, 7), stage="Group A"),
        rec(2, "b", "a", date(2022, 10, 26), stage="Group A"),
    ]
    assert "repeat_pairing_in_stage" not in checks(check_duplicates(rows))


def test_impossible_values():
    rows = [
        rec(1, "a", "a", date(2025, 9, 16)),
        rec(2, "b", "c", date(2025, 9, 16), None, 1),
        rec(3, "d", "e", date(2025, 9, 16), 2, 0, status="cancelled"),
        rec(4, "f", "g", date(2025, 9, 16), -1, 0),
        rec(5, "h", "i", date(2025, 9, 16), 11, 0),
    ]
    assert checks(check_values(rows, NOW)) == [
        "implausible_score",
        "negative_score",
        "same_team",
        "score_missing",
        "score_on_unplayed",
    ]


def test_played_in_future_and_stale_fixture():
    rows = [
        rec(1, "a", "b", date(2026, 10, 21)),  # 19:00 UTC, after NOW
        rec(2, "c", "d", date(2026, 10, 20), None, None, status="scheduled"),
    ]
    assert checks(check_values(rows, NOW)) == ["played_in_future", "scheduled_in_past"]


def test_team_twice_on_one_day_including_domestic():
    rows = [
        rec(1, "a", "b", date(2025, 9, 16)),
        rec(2, "c", "d", date(2025, 9, 18), competition="UEL"),
    ]
    domestic = [
        DomesticRec(10, "E0", date(2025, 9, 16), "x", "a"),
        DomesticRec(11, "E0", date(2025, 9, 19), "d", "y"),
        DomesticRec(12, "E0", date(2025, 9, 13), "x", "y"),  # domestic only: ignored
        DomesticRec(13, "E0", date(2025, 9, 13), "y", "z"),
    ]
    found = check_calendar(rows, domestic)
    assert checks(found) == ["team_on_consecutive_days", "team_twice_same_day"]
    twice = next(f for f in found if f.check == "team_twice_same_day")
    assert twice.euro_ids == (1,) and "domestic #10 E0" in twice.detail


def test_kickoff_checks():
    late = datetime(2025, 9, 16, 22, 30, tzinfo=UTC)
    rows = [
        rec(1, "a", "b", date(2025, 9, 16), kickoff_utc=late),
        rec(2, "c", "d", date(2025, 9, 16), kickoff_utc=datetime(2025, 9, 17, 0, 5, tzinfo=UTC)),
    ]
    assert checks(check_calendar(rows, [])) == [
        "kickoff_date_mismatch",
        "kickoff_hour_unusual",
        "kickoff_hour_unusual",
    ]


def test_extra_time_flags_outside_knockout():
    rows = [
        rec(1, "a", "b", date(2025, 9, 16), 1, 1, extra_time=True),
        rec(2, "c", "d", date(2025, 8, 5), 1, 1, stage="Q3 leg 1", extra_time=True),
        rec(3, "e", "f", date(2025, 5, 31), 1, 1, stage="Final", penalties=True),
    ]
    found = checks(check_format(rows))
    assert found.count("extra_time_outside_knockout") == 2
    assert "penalties_without_extra_time" in found


def test_tie_needs_extra_time_exactly_when_level():
    level_no_et = [
        rec(1, "a", "b", date(2025, 2, 11), 2, 1, stage="Round of 16 leg 1"),
        rec(2, "b", "a", date(2025, 2, 19), 1, 0, stage="Round of 16 leg 2"),
    ]
    assert checks(check_format(level_no_et)) == ["level_without_extra_time"]
    not_level_et = [
        rec(3, "c", "d", date(2025, 2, 11), 2, 1, stage="Round of 16 leg 1"),
        rec(4, "d", "c", date(2025, 2, 19), 3, 1, stage="Round of 16 leg 2", extra_time=True),
    ]
    assert checks(check_format(not_level_et)) == ["extra_time_not_level"]
    final = [rec(5, "e", "f", date(2025, 5, 31), 2, 1, stage="Final", extra_time=True)]
    assert checks(check_format(final)) == ["extra_time_not_level"]


def test_away_goals_rule_until_2020_21():
    # 2-1 away, 1-0 home: level 2-2 on aggregate, but b scored the away goal, so no extra time.
    rows = [
        rec(1, "a", "b", date(2021, 2, 17), 2, 1, season="2021", stage="Round of 16 leg 1"),
        rec(2, "b", "a", date(2021, 3, 9), 1, 0, season="2021", stage="Round of 16 leg 2"),
    ]
    assert check_format(rows) == []
    same_from_2021_22 = [replace(r, season="2122") for r in rows]
    assert checks(check_format(same_from_2021_22)) == ["level_without_extra_time"]


def test_legs_must_be_reversed_and_in_order():
    rows = [
        rec(1, "a", "b", date(2025, 7, 15), 1, 0, stage="Q1 leg 1"),
        rec(2, "a", "b", date(2025, 7, 8), 0, 2, stage="Q1 leg 2"),
    ]
    assert checks(check_format(rows)) == ["legs_not_reversed", "legs_out_of_order"]


def test_matchday_and_group_shape():
    rows = [
        rec(1, "a", "b", date(2023, 9, 19), stage="Group MD1"),
        rec(2, "a", "c", date(2023, 9, 20), stage="Group MD1"),
        rec(3, "d", "e", date(2022, 9, 6), stage="Group B"),
    ]
    assert checks(check_format(rows)) == ["group_shape", "matchday_repeat"]


def test_check_database_and_report(tmp_path):
    engine = create_engine(f"sqlite:///{tmp_path / 'db.sqlite3'}")
    Base.metadata.create_all(engine)
    with Session(engine) as s:
        for key in ("eng-a", "esp-b"):
            s.add(Team(team_key=key, name=key, country=key[:3].upper()))
        s.flush()
        s.add(
            EuroMatch(
                competition="UCL",
                season="2627",
                stage="League MD3",
                match_date=date(2026, 10, 21),
                kickoff_utc=datetime(2026, 10, 21, 19, 0, tzinfo=UTC),
                home_team="eng-a",
                away_team="eng-a",
                status="scheduled",
                source="wikipedia",
            )
        )
        s.add(
            EuroMatch(
                competition="UCL",
                season="2627",
                stage="League MD3",
                match_date=date(2026, 10, 21),
                kickoff_utc=datetime(2026, 10, 21, 19, 0, tzinfo=UTC),
                home_team="esp-b",
                away_team="eng-a",
                status="scheduled",
                source="wikipedia",
            )
        )
        s.commit()
        report = check_database(s, now=NOW)
    assert report.rows == 2 and report.domestic_rows == 0
    found = checks(report.findings)
    assert "same_team" in found and "team_twice_same_day" in found
    text = report_markdown(report)
    assert "| error | `same_team` | 1 |" in text


def test_duplicate_key_and_unknown_status():
    rows = [
        rec(1, "a", "b", date(2025, 9, 16)),
        rec(2, "a", "b", date(2025, 9, 16), status="abandoned"),
    ]
    assert "duplicate_key" in checks(check_duplicates(rows))
    assert checks(check_values(rows, NOW)) == ["unknown_status"]


def test_incomplete_tie_only_when_nothing_is_left_to_play():
    played = [rec(1, "a", "b", date(2025, 8, 5), stage="Q3 leg 1")]
    assert checks(check_format(played)) == ["incomplete_tie"]
    pending = [
        rec(2, "c", "d", date(2026, 11, 5), None, None, stage="Q3 leg 1", status="scheduled")
    ]
    assert check_format(pending) == []


def test_league_phase_shape():
    teams = [f"t{i:02d}" for i in range(36)]
    # A full UECL-sized phase: each team plays the next three at home (6 matches each, 3 home).
    rows = [
        rec(n, teams[i], teams[(i + k) % 36], date(2025, 10, 2), competition="UECL", stage="League")
        for n, (i, k) in enumerate(((i, k) for i in range(36) for k in (1, 2, 3)), start=1)
    ]
    assert check_format(rows) == []
    short = rows[:-1]  # one match missing: two teams on 5
    found = checks(check_format(short))
    assert "league_phase_matches" in found and "league_phase_home_away" in found
    assert "league_phase_teams" not in found
    assert checks(check_format(rows[:3])) == [
        "league_phase_home_away",
        "league_phase_matches",
        "league_phase_teams",
    ]
