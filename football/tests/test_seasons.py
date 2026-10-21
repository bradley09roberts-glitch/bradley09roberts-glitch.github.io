from datetime import date

import pytest

from footy.seasons import (
    calendar_season_key,
    current_season,
    is_calendar_season,
    season_is_complete,
    season_key,
    season_label,
    season_start_year,
    seasons_through,
)


def test_keys_and_labels():
    assert season_key(2025) == "2526"
    assert season_key(2099) == "9900"
    assert season_start_year("2526") == 2025
    assert season_label("1819") == "2018/19"
    with pytest.raises(ValueError):
        season_start_year("2527")


def test_current_season_starts_in_july():
    assert current_season(date(2026, 6, 30)) == "2526"
    assert current_season(date(2026, 7, 1)) == "2627"


def test_seasons_through_today():
    s = seasons_through(date(2026, 10, 19))
    assert s[0] == "1819" and s[-1] == "2627" and len(s) == 9


def test_season_complete_after_mid_august():
    assert not season_is_complete("2526", date(2026, 8, 15))
    assert season_is_complete("2526", date(2026, 8, 16))
    assert not season_is_complete("2627", date(2026, 10, 19))


def test_calendar_year_seasons():
    assert calendar_season_key(2025) == "2525"
    assert is_calendar_season("2525") and not is_calendar_season("2526")
    assert season_start_year("2525") == 2025
    assert season_label("2525") == "2025" and season_label("1818") == "2018"
    # Split-year keys always have halves one apart, so the two kinds never collide.
    assert season_label("2021") == "2020/21"
    assert not season_is_complete("2525", date(2026, 2, 15))
    assert season_is_complete("2525", date(2026, 2, 16))
