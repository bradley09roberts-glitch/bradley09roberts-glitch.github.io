from datetime import UTC, date, datetime

import pytest

from fdfixtures import ROWS, csv_bytes, replace_field
from footy.ingest.fd_parse import collection_deadline, model_cutoff, parse_fd_csv


def utc(*args):
    return datetime(*args, tzinfo=UTC)


@pytest.mark.parametrize(
    ("match_day", "expected"),
    [
        # weekend fixtures: Friday 17:00 UK (BST = UTC+1 in September)
        (date(2025, 9, 19), utc(2025, 9, 19, 16)),  # Friday
        (date(2025, 9, 20), utc(2025, 9, 19, 16)),  # Saturday
        (date(2025, 9, 21), utc(2025, 9, 19, 16)),  # Sunday
        (date(2025, 9, 22), utc(2025, 9, 19, 16)),  # Monday
        # midweek fixtures: Tuesday 13:00 UK (GMT = UTC in December)
        (date(2025, 12, 2), utc(2025, 12, 2, 13)),  # Tuesday
        (date(2025, 12, 3), utc(2025, 12, 2, 13)),  # Wednesday
        (date(2025, 12, 4), utc(2025, 12, 2, 13)),  # Thursday
    ],
)
def test_collection_deadline(match_day, expected):
    assert collection_deadline(match_day) == expected


def test_collection_deadline_across_clock_change():
    # Clocks went back on Sunday 26 Oct 2025: Friday 24th is still BST.
    assert collection_deadline(date(2025, 10, 27)) == utc(2025, 10, 24, 16)


def test_model_cutoff_is_midnight_uk_on_collection_day():
    assert model_cutoff(date(2025, 9, 20)) == utc(2025, 9, 18, 23)  # 00:00 BST Friday
    assert model_cutoff(date(2025, 12, 3)) == utc(2025, 12, 2, 0)  # 00:00 GMT Tuesday


def test_parses_results_stats_and_kickoff_in_utc():
    pf = parse_fd_csv(csv_bytes(), "E0", with_odds=True)
    assert not pf.issues
    a, b, c = pf.matches
    assert (a.home_name, a.away_name, a.home_goals, a.away_goals, a.result) == (
        "Testtown",
        "Sampleton",
        2,
        1,
        "H",
    )
    assert a.kickoff_utc == utc(2025, 9, 13, 14)  # 15:00 BST
    assert b.kickoff_utc == utc(2025, 12, 3, 19, 45)  # 19:45 GMT
    assert (a.ht_home_goals, a.ht_away_goals, a.ht_result) == (1, 0, "H")
    assert a.stats["home_sot"] == 5 and a.stats["away_corners"] == 2
    assert b.result == "D" and c.result == "A"


def test_odds_phases_and_quote_times():
    m = parse_fd_csv(csv_bytes(), "E0", with_odds=True).matches[0]
    by_key = {(o.bookmaker, o.market_key, o.phase, o.selection): o for o in m.odds}
    pre = by_key[("B365", "1X2", "pre_closing", "H")]
    assert pre.price == 2.10 and pre.quote_time_utc == utc(2025, 9, 12, 16)
    assert pre.quote_time_basis == "fd_collection_deadline"
    close = by_key[("PS", "1X2", "closing", "A")]
    assert close.price == 3.70 and close.quote_time_utc == m.kickoff_utc
    assert by_key[("PS", "OU_2.5", "closing", "U")].price == 2.02
    assert by_key[("B365", "OU_2.5", "pre_closing", "O")].line == 2.5
    assert len(m.odds) == 3 + 3 + 2 + 2


def test_no_odds_without_with_odds():
    m = parse_fd_csv(csv_bytes(), "E0", with_odds=False).matches[0]
    assert m.odds == []


def test_betbrain_average_alias_is_used_when_avg_missing():
    header = "Div,Date,HomeTeam,AwayTeam,FTHG,FTAG,FTR,BbAvH,BbAvD,BbAvA"
    pf = parse_fd_csv(
        csv_bytes(["E0,13/09/18,Testtown,Sampleton,1,1,D,2.5,3.2,2.9"], header=header),
        "E0",
        with_odds=True,
    )
    m = pf.matches[0]
    assert m.match_date == date(2018, 9, 13) and m.kickoff_utc is None
    assert {o.source_column for o in m.odds} == {"BbAvH", "BbAvD", "BbAvA"}
    assert {o.bookmaker for o in m.odds} == {"Avg"}
    assert m.odds[0].quote_time_basis == "fd_collection_deadline"


def test_result_mismatch_rejects_row():
    rows = [replace_field(ROWS[0], "FTR", "A"), *ROWS[1:]]
    pf = parse_fd_csv(csv_bytes(rows), "E0", with_odds=True)
    assert len(pf.matches) == 2
    assert [(i.source_row, i.code) for i in pf.issues] == [(1, "result_mismatch")]


def test_half_time_above_full_time_drops_only_ht():
    rows = [replace_field(ROWS[0], "HTHG", "3"), *ROWS[1:]]
    pf = parse_fd_csv(csv_bytes(rows), "E0", with_odds=False)
    assert pf.matches[0].ht_home_goals is None and pf.matches[0].home_goals == 2
    assert pf.issues[0].code == "ht_exceeds_ft"


def test_invalid_and_implausible_prices_are_dropped():
    rows = [replace_field(ROWS[0], "B365H", "0"), replace_field(ROWS[1], "PSCA", "1.01"), ROWS[2]]
    pf = parse_fd_csv(csv_bytes(rows), "E0", with_odds=True)
    codes = sorted(i.code for i in pf.issues)
    assert codes == ["implausible_overround", "invalid_price"]
    m0, m1 = pf.matches[0], pf.matches[1]
    assert not any(o.source_column == "B365H" for o in m0.odds)
    assert not any(o.bookmaker == "PS" and o.market_key == "1X2" for o in m1.odds)


def test_missing_score_and_duplicates_and_blank_lines():
    rows = [ROWS[0], replace_field(ROWS[1], "FTHG", ""), ",,,,,", ROWS[0]]
    pf = parse_fd_csv(csv_bytes(rows), "E0", with_odds=False)
    assert len(pf.matches) == 1
    assert [i.code for i in pf.issues] == ["no_result", "duplicate_fixture"]


def test_bare_result_is_flagged_and_excluded_from_models():
    bare = ROWS[0]
    for col in ("HTHG", "HTAG", "HTR", "HS", "AS", "HST", "AST", "HC", "AC"):
        bare = replace_field(bare, col, "")
    pf = parse_fd_csv(csv_bytes([bare]), "E0", with_odds=False)
    assert pf.matches[0].model_exclusion == "result_without_stats"
    assert pf.issues[0].code == "result_without_stats"


def test_cp1252_file_decodes():
    header = "Div,Date,HomeTeam,AwayTeam,FTHG,FTAG,FTR"
    content = csv_bytes(["I2,01/09/18,Testtown,Sampleton,1,0,H"], header=header, encoding="utf-8")
    content = content.replace(b"Testtown", "Testt\xf6wn".encode("cp1252"))
    pf = parse_fd_csv(content, "I2", with_odds=False)
    assert pf.encoding == "cp1252" and pf.matches[0].home_name == "Testtöwn"


def test_wrong_division_rejected():
    pf = parse_fd_csv(csv_bytes([ROWS[0]]), "SP1", with_odds=False)
    assert not pf.matches and pf.issues[0].code == "wrong_division"


def test_repeat_meetings_are_numbered_where_expected():
    first = replace_field(ROWS[0], "Div", "SC0")
    second = replace_field(first, "Date", "14/02/2026")
    third = replace_field(first, "Date", "18/04/2026")
    rows = [first, second, first, second, third]  # rows 3 and 4 repeat a kept meeting's date
    pf = parse_fd_csv(
        csv_bytes(rows), "SC0", with_odds=False, repeats_expected=True, max_meetings=3
    )
    assert [(m.source_row, m.meeting) for m in pf.matches] == [(1, 1), (2, 2), (5, 3)]
    assert [(i.source_row, i.code, i.detail) for i in pf.issues] == [
        (3, "duplicate_fixture", "same pairing as row 1"),
        (4, "duplicate_fixture", "same pairing as row 2"),
    ]
    # A meeting beyond the division's max_meetings is held back.
    pf = parse_fd_csv(
        csv_bytes([first, second, third]),
        "SC0",
        with_odds=False,
        repeats_expected=True,
        max_meetings=2,
    )
    assert [m.meeting for m in pf.matches] == [1, 2]
    assert [(i.source_row, i.code) for i in pf.issues] == [(3, "repeat_pairing")]
    assert "on 2026-04-18 repeats row 1 (2025-09-13)" in pf.issues[0].detail
    assert "would be meeting 3 and SC0 allows 2" in pf.issues[0].detail


def test_core_divisions_keep_one_meeting_per_pairing():
    # Core divisions keep the M1 rule: any second row of a pairing is a duplicate.
    pf = parse_fd_csv(
        csv_bytes([ROWS[0], replace_field(ROWS[0], "Date", "14/02/2026")]), "E0", with_odds=False
    )
    assert [m.meeting for m in pf.matches] == [1]
    assert [(i.code, i.detail) for i in pf.issues] == [
        ("duplicate_fixture", "same pairing as row 1")
    ]
    assert {m.meeting for m in parse_fd_csv(csv_bytes(), "E0", with_odds=True).matches} == {1}
