"""Parse a football-data "extra leagues" CSV (new/{code}.csv). No database access.

One file holds every season of one league. Layout, checked on the ten European files on
2026-10-21: Country, League, Season, Date, Time, Home, Away, HG, AG, Res, then closing prices
(PSC*, MaxC*, AvgC*, and BFEC*, B365C* in most files). Only results are parsed; the prices
stay in the raw file (D-016 limits prices to the five core top divisions). Season is
"2018/2019" for split-year leagues and "2018" for calendar-year ones. Times are read as UK
local time, as in the main layout (D-005, D-022).

Rules follow D-017 where they apply, plus two that only arise in these leagues (D-022):

- A pairing that recurs on a different date in one season (split leagues, three- and
  four-round formats, play-offs) is a genuine match, but `matches` holds one row per
  pairing per season, so it is held back with a `repeat_pairing` error. The same pairing
  on the same date is a `duplicate_fixture`.
- A club with only a handful of matches in a season where the others have many is a
  lower-division visitor in a promotion/relegation play-off. Its matches are kept, flagged
  and excluded from models: the source does not say whether play-off scores include extra
  time.
"""

import csv
import io
import statistics
from collections import Counter
from dataclasses import dataclass, field

from footy.ingest.fd_parse import (
    STAT_COLUMNS,
    Issue,
    ParsedMatch,
    _int,
    _parse_date,
    decode,
    uk_kickoff,
)
from footy.seasons import (
    FIRST_SEASON_START,
    calendar_season_key,
    season_key,
    season_label,
    season_start_year,
)

EXTRA_PARSER_VERSION = "fdx-1"
# football-data's Country value for each extra-league code.
FD_COUNTRY_NAMES = {
    "AUT": "Austria",
    "DNK": "Denmark",
    "SWZ": "Switzerland",
    "NOR": "Norway",
    "SWE": "Sweden",
    "POL": "Poland",
    "ROU": "Romania",
    "RUS": "Russia",
    "FIN": "Finland",
    "IRL": "Ireland",
}
REQUIRED_COLUMNS = ("Country", "League", "Season", "Date", "Home", "Away", "HG", "AG")
# Play-off visitor: at most this many matches, and at most 1/OUTSIDE_RATIO of the median
# club's count, in a season whose median club has played at least OUTSIDE_MIN_MEDIAN.
OUTSIDE_MAX_MATCHES = 4
OUTSIDE_RATIO = 5
OUTSIDE_MIN_MEDIAN = 10
PLAYOFF_EXCLUSION = "playoff_vs_outside_club"


@dataclass
class ParsedExtraFile:
    seasons: dict[str, list[ParsedMatch]]  # season key -> matches in file order
    issues: list[Issue]
    columns: list[str]
    encoding: str
    rows_before_first_season: int = 0
    league_labels: Counter = field(default_factory=Counter)


class ExtraLayoutError(ValueError):
    """The file is not in the extra-leagues layout (a required column is missing)."""


def parse_extra_season(value: str) -> str:
    """'2018/2019' -> '1819'; '2018' -> '1818' (calendar-year season)."""
    v = value.strip()
    if len(v) == 9 and v[4] == "/" and v[:4].isdigit() and v[5:].isdigit():
        start, end = int(v[:4]), int(v[5:])
        if end == start + 1:
            return season_key(start)
    elif len(v) == 4 and v.isdigit():
        return calendar_season_key(int(v))
    raise ValueError(value)


def parse_fd_extra_csv(
    content: bytes, division: str, *, first_start: int = FIRST_SEASON_START
) -> ParsedExtraFile:
    text, encoding = decode(content)
    reader = csv.reader(io.StringIO(text))
    header = [h.strip() for h in next(reader)]
    missing = [c for c in REQUIRED_COLUMNS if c not in header]
    if missing:
        raise ExtraLayoutError(f"missing columns: {missing}")
    expected_country = FD_COUNTRY_NAMES[division]

    out = ParsedExtraFile({}, [], header, encoding)
    seen: dict[tuple[str, str, str], tuple[int, object]] = {}
    # Matches per club per season, repeat meetings included (for the play-off visitor rule).
    apps: dict[str, Counter] = {}
    for n, values in enumerate(reader, start=1):
        if not any(v.strip() for v in values):
            continue
        row = {h: (values[i].strip() if i < len(values) else "") for i, h in enumerate(header)}
        try:
            season = parse_extra_season(row["Season"])
        except ValueError:
            out.issues.append(Issue(n, "error", "bad_season", f"Season={row['Season']!r}"))
            continue
        if season_start_year(season) < first_start:
            out.rows_before_first_season += 1
            continue
        label = season_label(season)
        pm = _parse_row(n, row, division, expected_country, label, out.issues)
        if pm is None:
            continue
        out.league_labels[row["League"]] += 1
        apps.setdefault(season, Counter()).update([pm.home_name, pm.away_name])
        pair = (season, pm.home_name, pm.away_name)
        if pair in seen:
            first_row, first_date = seen[pair]
            if first_date == pm.match_date:
                out.issues.append(
                    Issue(
                        n, "error", "duplicate_fixture", f"{label}: same match as row {first_row}"
                    )
                )
            else:
                out.issues.append(
                    Issue(
                        n,
                        "error",
                        "repeat_pairing",
                        f"{label}: {pm.home_name} v {pm.away_name} on {pm.match_date} repeats "
                        f"row {first_row} ({first_date}); held back, one row per pairing per "
                        "season in matches",
                    )
                )
            continue
        seen[pair] = (n, pm.match_date)
        out.seasons.setdefault(season, []).append(pm)

    if len(out.league_labels) > 1:
        main_label = out.league_labels.most_common(1)[0][0]
        for lab, count in out.league_labels.items():
            if lab != main_label:
                out.issues.append(
                    Issue(0, "warning", "other_league_label", f"League={lab!r} on {count} rows")
                )
    for season, matches in out.seasons.items():
        _flag_outside_clubs(season, matches, apps[season], out.issues)
    return out


def _parse_row(n, row, division, expected_country, label, issues) -> ParsedMatch | None:
    if row["Country"] != expected_country:
        issues.append(Issue(n, "error", "wrong_country", f"{label}: Country={row['Country']!r}"))
        return None
    home, away = row["Home"], row["Away"]
    if not home or not away:
        issues.append(Issue(n, "error", "missing_team", f"{label}: {home!r} v {away!r}"))
        return None
    try:
        match_date = _parse_date(row["Date"])
    except ValueError:
        issues.append(Issue(n, "error", "bad_date", f"{label}: Date={row['Date']!r}"))
        return None
    hg, ag = _int(row["HG"]), _int(row["AG"])
    if hg is None or ag is None:
        issues.append(Issue(n, "error", "no_result", f"{label}: HG={row['HG']!r} AG={row['AG']!r}"))
        return None
    result = "H" if hg > ag else "A" if ag > hg else "D"
    if row.get("Res") and row["Res"] != result:
        issues.append(
            Issue(n, "error", "result_mismatch", f"{label}: Res={row['Res']} but {hg}-{ag}")
        )
        return None

    kickoff_local, kickoff_utc = None, None
    if row.get("Time"):
        try:
            kickoff_local, kickoff_utc = uk_kickoff(match_date, row["Time"])
        except ValueError:
            issues.append(Issue(n, "warning", "bad_time", f"{label}: Time={row['Time']!r}"))

    return ParsedMatch(
        source_row=n,
        division=division,
        match_date=match_date,
        kickoff_local=kickoff_local,
        kickoff_utc=kickoff_utc,
        home_name=home,
        away_name=away,
        home_goals=hg,
        away_goals=ag,
        result=result,
        ht_home_goals=None,
        ht_away_goals=None,
        ht_result=None,
        referee=None,
        fd_home_xg=None,
        fd_away_xg=None,
        stats={name: None for name in STAT_COLUMNS},
    )


def _flag_outside_clubs(
    season: str, matches: list[ParsedMatch], apps: Counter, issues: list[Issue]
) -> None:
    median = statistics.median(apps.values())
    if median < OUTSIDE_MIN_MEDIAN:
        return
    outside = {
        t for t, c in apps.items() if c <= OUTSIDE_MAX_MATCHES and c * OUTSIDE_RATIO <= median
    }
    label = season_label(season)
    for m in matches:
        clubs = sorted({m.home_name, m.away_name} & outside)
        if not clubs:
            continue
        m.model_exclusion = PLAYOFF_EXCLUSION
        who = ", ".join(f"{c} ({apps[c]} matches)" for c in clubs)
        issues.append(
            Issue(
                m.source_row,
                "warning",
                PLAYOFF_EXCLUSION,
                f"{label}: {m.home_name} v {m.away_name}; play-off visitor {who}; excluded "
                "from models (score may include extra time)",
            )
        )
