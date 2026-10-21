"""openfootball football.txt parser (footy.europe.openfootball).

Every snippet here is SYNTHETIC: made-up clubs and scores in openfootball's layout, written
to exercise one rule each. None of it is real data.
"""

from datetime import UTC, date, datetime

import pytest

from footy.europe.openfootball import (
    normalise_stage,
    parse_openfootball,
    parse_score,
    team_country,
)

CUP = """\
= UEFA Champions League 2018/19

# Date       Tue Sep 18 2018 - Sat Jun 1 2019 (256d)
# Teams      4
# Matches    8

▪ Group A
  Tue Sep 18 2018
    18:55  Alpha FC (GER)          v Beta SK (AUT)            2-1 (1-0)
    21:00  Gamma (ESP)             v Delta (POR)              0-0
  Wed Oct 3
           Delta (POR)             v Alpha FC (GER)           1-1 (0-1)


▪ Round of 16
  Tue Feb 12 2019
    21:00  Alpha FC (GER)          v Gamma (ESP)              1-0 (0-0)
  Wed Mar 6
    21:00  Gamma (ESP)             v Alpha FC (GER)           2-1 a.e.t. (1-0, 0-0)


▪ Quarterfinals
  Tue Apr 9
    21:00  Beta SK (AUT)           v Delta (POR)              1-1 (0-1)
  Tue Apr 16
    21:00  Delta (POR)             v Beta SK (AUT)            3-4 pen. 1-1 a.e.t. (1-1, 0-0)


▪ Final
  Sat Jun 1
    21:00  Gamma (ESP)             v Beta SK (AUT)            4-3 pen. 1-1 a.e.t. (1-1, 0-1)
"""


def _parse(text: str, file_name: str = "cl.txt", season: str = "1819"):
    return parse_openfootball(text.encode("utf-8"), file_name, season)


def test_cup_file_rows_dates_times_and_stages():
    p = _parse(CUP)
    assert [i.code for i in p.issues] == ["no_kickoff_time"]  # Delta v Alpha has no time
    assert p.declared_matches == len(p.matches) == 8
    first, second, third = p.matches[:3]
    assert (first.competition, first.season, first.stage) == ("UCL", "1819", "Group A")
    assert first.match_date == date(2018, 9, 18)
    # 18:55 CEST is 16:55 UTC (17:55 in the UK).
    assert first.kickoff_utc == datetime(2018, 9, 18, 16, 55, tzinfo=UTC)
    assert (first.home_goals, first.away_goals, first.ht) == (2, 1, (1, 0))
    assert (second.kickoff_local, second.ht) == ("21:00", None)
    # A date line without a year takes the season year whose weekday matches; a time does
    # not carry over to a new date.
    assert third.match_date == date(2018, 10, 3) and third.kickoff_utc is None
    # 21:00 CET in February is 20:00 UTC (and 20:00 in the UK).
    r16 = p.matches[3]
    assert r16.match_date == date(2019, 2, 12)
    assert r16.kickoff_utc == datetime(2019, 2, 12, 20, 0, tzinfo=UTC)
    assert [m.stage for m in p.matches[3:]] == [
        "Round of 16 leg 1",
        "Round of 16 leg 2",
        "Quarter-final leg 1",
        "Quarter-final leg 2",
        "Final",
    ]
    assert [m.neutral for m in p.matches] == [False] * 7 + [True]


def test_extra_time_and_penalties_store_the_regulation_score():
    p = _parse(CUP)
    aet, pens, final = p.matches[4], p.matches[6], p.matches[7]
    assert (aet.home_goals, aet.away_goals, aet.aet, aet.pens) == (1, 0, (2, 1), None)
    assert aet.extra_time and not aet.penalties
    assert (pens.home_goals, pens.away_goals, pens.aet, pens.pens) == (1, 1, (1, 1), (3, 4))
    assert pens.extra_time and pens.penalties and pens.ht == (0, 0)
    assert (final.home_goals, final.away_goals, final.ht) == (1, 1, (0, 1))
    assert all(m.model_exclusion is None for m in p.matches)


@pytest.mark.parametrize(
    ("text", "expected"),
    [
        ("3-1 (2-0)", {"status": "played", "reg": (3, 1), "ht": (2, 0)}),
        ("0-0", {"status": "played", "reg": (0, 0), "ht": None}),
        (
            "3-1 a.e.t. (2-1, 1-1)",
            {"status": "played", "reg": (2, 1), "ht": (1, 1), "aet": (3, 1), "pens": None},
        ),
        (
            "1-4 pen. 0-1 a.e.t. (0-1, 0-1)",
            {"status": "played", "reg": (0, 1), "ht": (0, 1), "aet": (0, 1), "pens": (1, 4)},
        ),
        # A single pair after a.e.t. is the regulation score.
        (
            "2-0 a.e.t. (0-0)",
            {"status": "played", "reg": (0, 0), "ht": None, "aet": (2, 0), "pens": None},
        ),
        ("3-0    [awarded]", {"status": "awarded", "reg": (3, 0)}),
        ("[cancelled]", {"status": "cancelled"}),
        ("", {"status": "scheduled"}),
        ("3:1", None),
        ("2-1 n.V.", None),
    ],
)
def test_score_forms(text, expected):
    assert parse_score(text) == expected


@pytest.mark.parametrize(
    ("header", "qualifying", "stage"),
    [
        ("▪ Group A", False, "Group A"),
        ("▪ Gruppe G", False, "Group G"),
        ("▪ Group, Matchday 3", False, "Group MD3"),
        ("▪ League, Matchday 8", False, "League MD8"),
        ("▪ League phase", False, "League"),
        ("▪ Playoffs, Matchday 1", False, "Knockout play-off"),
        ("▪ Playoffs", False, "Knockout play-off"),
        ("▪ Sechzehntelfinale", False, "Round of 32"),
        ("▪ Finals, Round of 16", False, "Round of 16"),
        ("▪ Finals, Quarterfinals", False, "Quarter-final"),
        ("▪ Semifinals", False, "Semi-final"),
        ("▪ Finals, Final", False, "Final"),
        ("▪ Round 1", True, "Q1"),
        ("▪ 3. Round", True, "Q3"),
        ("▪ Play-offs", True, "Play-off"),
        ("▪ Playoffs", True, "Play-off"),
        ("▪ Round 1", False, None),
        ("▪ Mystery round", False, None),
    ],
)
def test_stage_headers(header, qualifying, stage):
    assert normalise_stage(header, qualifying) == stage


def test_country_codes_follow_the_league_a_club_plays_in():
    assert team_country("GER") == "DEU"
    assert team_country("SUI") == "CHE"
    assert team_country("CRO") == "HRV"
    assert team_country("BUL") == "BGR"
    assert team_country("KOS") == "XKX"
    assert team_country("SCO") == "SCO"
    assert team_country("MCO") == "FRA"  # Monaco plays in Ligue 1
    assert team_country("LIE") == "CHE"  # Vaduz plays in the Swiss league
    assert team_country("XXX") is None


QUALI = """\
= UEFA Europa League - Quali 2024/25

▪ Round 1
  Thu Jul 11 2024
    19:00  Alpha FC (GER)          v Beta SK (AUT)            0-3    [awarded]
           Gamma (ESP)             v Delta (POR)              [cancelled]
           Omega (XXX)             v Alpha FC (GER)           1-0
  Thu Jul 18
    20:00  Beta SK (AUT)           v Alpha FC (GER)           3-0    [awarded]
           Delta (POR)             v Gamma (ESP)              2-2 (3-1)
           Epsilon (ITA)           v Zeta (NED)               3:1
  Mon Jul 18
    20:00  Zeta (NED)              v Epsilon (ITA)            1-0
"""


def test_awarded_cancelled_and_problems_are_reported():
    p = _parse(QUALI, "elq.txt", "2425")
    by = {(m.home_name, m.away_name): m for m in p.matches}
    awarded = by[("Alpha FC (GER)", "Beta SK (AUT)")]
    assert (awarded.status, awarded.model_exclusion) == ("awarded", "awarded")
    assert (awarded.home_goals, awarded.away_goals, awarded.stage) == (0, 3, "Q1 leg 1")
    assert by[("Beta SK (AUT)", "Alpha FC (GER)")].stage == "Q1 leg 2"
    cancelled = by[("Gamma (ESP)", "Delta (POR)")]
    assert (cancelled.status, cancelled.model_exclusion) == ("cancelled", "cancelled")
    assert cancelled.home_goals is None
    # Gamma v Delta has no score, so the tie isn't checked; the half-time slip is reported.
    codes = {(i.code, i.line) for i in p.issues}
    assert ("unknown_country_code", 7) in codes
    assert ("half_time_above_full_time", 10) in codes
    assert ("unparsed_score", 11) in codes
    assert ("bad_date", 12) in codes  # 18 July was a Thursday in 2024, a Friday in 2025
    assert ("match_without_date", 13) in codes
    assert len(p.matches) == 4


def test_dates_outside_the_header_range_are_reported():
    """A weekday can pick the wrong season year (Fri Jul 18 is 2025); the header catches it."""
    text = QUALI.replace(
        "\n\u25aa Round 1", "\n# Date       Thu Jul 11 - Thu Jul 18 2024 (8d)\n\n\u25aa Round 1"
    ).replace("Mon Jul 18", "Fri Jul 18")
    p = _parse(text, "elq.txt", "2425")
    assert p.declared_dates == (date(2024, 7, 11), date(2024, 7, 18))
    late = [i for i in p.issues if i.code == "date_outside_header_range"]
    assert [i.detail for i in late] == ["2025-07-18 not in 2024-07-11 to 2024-07-18"]


def test_title_must_match_the_file_and_season():
    p = _parse(CUP, "el.txt", "1819")
    assert ("unexpected_title", 1) in {(i.code, i.line) for i in p.issues}
    p = _parse(CUP, "cl.txt", "1920")
    assert "unexpected_title" in {i.code for i in p.issues}


INCONSISTENT = """\
= UEFA Conference League - Quali 2025/26

▪ 2. Round
  Thu Jul 24 2025
    19:00  Alpha FC (GER)          v Beta SK (AUT)            1-2 (1-0)
  Thu Jul 31
    19:00  Beta SK (AUT)           v Alpha FC (GER)           1-1 a.e.t. (1-1, 1-0)
    20:00  Gamma (ESP)             v Delta (POR)              1-1 (0-0)
  Thu Aug 7
    19:00  Delta (POR)             v Gamma (ESP)              0-0
"""


def test_a_tie_that_contradicts_its_extra_time_is_excluded():
    """Beta lead 2-1 and draw the second leg 1-1 after 90 minutes, so there should have been
    no extra time; Gamma and Delta are level after 90 minutes but play none."""
    p = _parse(INCONSISTENT, "confq.txt", "2526")
    assert [m.model_exclusion for m in p.matches] == ["tie_inconsistent"] * 4
    assert sorted(i.code for i in p.issues) == ["extra_time_not_level", "level_without_extra_time"]


def test_away_goals_rule_before_2021_22():
    """1-1 then 0-0 is level on aggregate but decided on away goals before 2021/22."""
    text = """\
= UEFA Champions League 2019/20

▪ Round of 16
  Tue Feb 18 2020
    21:00  Alpha FC (GER)          v Beta SK (AUT)            1-1 (0-0)
  Wed Mar 11 2020
    21:00  Beta SK (AUT)           v Alpha FC (GER)           0-0
"""
    assert _parse(text, "cl.txt", "1920").issues == []
    later = text.replace("2019/20", "2021/22").replace("2020", "2022")
    later = later.replace("Tue Feb 18", "Fri Feb 18").replace("Wed Mar 11", "Fri Mar 11")
    codes = [i.code for i in _parse(later, "cl.txt", "2122").issues]
    assert codes == ["level_without_extra_time"]


def test_single_match_rounds_of_the_2019_20_final_tournament_are_neutral():
    text = """\
= UEFA Champions League 2019/20

▪ Quarterfinals
  Wed Aug 12 2020
    21:00  Alpha FC (GER)          v Beta SK (AUT)            2-1 (0-0)
"""
    m = _parse(text, "cl.txt", "1920").matches[0]
    assert m.neutral and m.stage == "Quarter-final"
    earlier = text.replace("2019/20", "2018/19").replace("Wed Aug 12 2020", "Wed Apr 10 2019")
    other = _parse(earlier, "cl.txt", "1819")
    assert [i.code for i in other.issues] == ["single_match_tie"]
    assert not other.matches[0].neutral
