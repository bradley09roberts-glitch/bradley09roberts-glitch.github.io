"""Season keys follow football-data's URL scheme: "2526" is the 2025/26 season.

Leagues played within a calendar year (Norway, Sweden, Finland, Ireland) use the same
four-character scheme with equal halves: "2525" is the 2025 season (DECISIONS.md D-022). The
two kinds never collide, since a split-year key's halves always differ by one.
"""

from datetime import date

FIRST_SEASON_START = 2018  # brief: data from 2018/19


def season_key(start_year: int) -> str:
    return f"{start_year % 100:02d}{(start_year + 1) % 100:02d}"


def calendar_season_key(year: int) -> str:
    """Key of a season played within one calendar year: 2025 -> "2525"."""
    return f"{year % 100:02d}{year % 100:02d}"


def is_calendar_season(key: str) -> bool:
    return len(key) == 4 and key.isdigit() and key[:2] == key[2:]


def season_start_year(key: str) -> int:
    if is_calendar_season(key):
        return 2000 + int(key[:2])
    if len(key) != 4 or not key.isdigit() or (int(key[:2]) + 1) % 100 != int(key[2:]):
        raise ValueError(f"not a season key: {key!r}")
    return 2000 + int(key[:2])


def season_label(key: str) -> str:
    start = season_start_year(key)
    if is_calendar_season(key):
        return str(start)
    return f"{start}/{(start + 1) % 100:02d}"


def current_season(today: date) -> str:
    """Seasons are taken to start on 1 July."""
    return season_key(today.year if today.month >= 7 else today.year - 1)


def seasons_through(today: date, first_start: int = FIRST_SEASON_START) -> list[str]:
    last = season_start_year(current_season(today))
    return [season_key(y) for y in range(first_start, last + 1)]


def season_is_complete(key: str, today: date) -> bool:
    """Complete once 15 August after the season's end has passed, which leaves time for
    late corrections to land in the source before the file is treated as final. A calendar
    season is complete once 15 February of the following year has passed."""
    if is_calendar_season(key):
        return today > date(season_start_year(key) + 1, 2, 15)
    return today > date(season_start_year(key) + 1, 8, 15)
