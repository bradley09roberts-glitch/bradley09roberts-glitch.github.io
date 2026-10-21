"""UEFA club competition results from openfootball's football.txt files.

Source: https://github.com/openfootball/champions-league (public domain, CC0 1.0). One file
per competition and season: `cl.txt` (Champions League), `clq.txt` (its qualifying rounds),
`el.txt` / `elq.txt` (Europa League) and `conf.txt` / `confq.txt` (Conference League). The
loader stores each file in the raw store first; this module only parses stored bytes.

Format rules (docs/europe/openfootball.md):
- `▪ <stage>` starts a stage; a date line (`Tue Sep 18 2018`, year often omitted) starts a
  match day; a match line is `[HH:MM]  Home (CC)  v  Away (CC)  <score>`. A time carries on to
  the next lines of the same date until another time is given.
- Times are Central European local time (CET/CEST, as UEFA lists them) and are converted with
  the Europe/Paris zone. A date without a year takes the season year whose weekday matches.
- Scores: `3-1 (2-0)` full and half time; `0-0`; `3-1 a.e.t. (2-1, 1-1)` after extra time,
  then (regulation, half time); `1-4 pen. 0-1 a.e.t. (0-1, 0-1)` penalties first. A single
  pair after `a.e.t.` is the regulation score. The stored score is regulation time; extra
  time and penalties are flags only.
- `[awarded]` results are kept with status "awarded" and `model_exclusion = "awarded"`;
  `[cancelled]` fixtures are kept with status "cancelled", no score, excluded likewise.
- Knockout ties: a pairing met twice in a round is two legs; finals (and the single-match
  rounds of the 2019/20 Champions League final tournament in Lisbon) are neutral. A tie whose
  scores contradict its extra time or penalties (for example extra time after an aggregate
  that was not level) keeps its matches with `model_exclusion = "tie_inconsistent"`.
- Every problem is returned as an `OFIssue`; nothing is silently dropped or guessed.
"""

import re
from collections import defaultdict
from dataclasses import dataclass, field
from datetime import UTC, date, datetime, time
from zoneinfo import ZoneInfo

from footy.seasons import season_start_year

SOURCE = "openfootball"
PARSER_VERSION = "of-1"
OF_TZ = ZoneInfo("Europe/Paris")  # CET/CEST

# File name -> competition code (euro_matches.competition).
FILES: dict[str, str] = {
    "cl.txt": "UCL",
    "clq.txt": "UCLQ",
    "el.txt": "UEL",
    "elq.txt": "UELQ",
    "conf.txt": "UECL",
    "confq.txt": "UECLQ",
}
TITLES = {
    "UCL": "UEFA Champions League",
    "UCLQ": "UEFA Champions League - Quali",
    "UEL": "UEFA Europa League",
    "UELQ": "UEFA Europa League - Quali",
    "UECL": "UEFA Conference League",
    "UECLQ": "UEFA Conference League - Quali",
}
QUALIFYING = {"UCLQ", "UELQ", "UECLQ"}

# openfootball country codes (FIFA trigrams, plus MCO) -> ISO 3166-1 alpha-3, with the four UK
# football associations kept separate as in reference/teams.csv. Kosovo is XKX.
OF_COUNTRIES: dict[str, str] = {
    "ALB": "ALB", "AND": "AND", "ARM": "ARM", "AUT": "AUT", "AZE": "AZE", "BEL": "BEL",
    "BIH": "BIH", "BLR": "BLR", "BUL": "BGR", "CRO": "HRV", "CYP": "CYP", "CZE": "CZE",
    "DEN": "DNK", "ENG": "ENG", "ESP": "ESP", "EST": "EST", "FIN": "FIN", "FRA": "FRA",
    "FRO": "FRO", "GEO": "GEO", "GER": "DEU", "GIB": "GIB", "GRE": "GRC", "HUN": "HUN",
    "IRL": "IRL", "ISL": "ISL", "ISR": "ISR", "ITA": "ITA", "KAZ": "KAZ", "KOS": "XKX",
    "LIE": "LIE", "LTU": "LTU", "LUX": "LUX", "LVA": "LVA", "MCO": "MCO", "MDA": "MDA",
    "MKD": "MKD", "MLT": "MLT", "MNE": "MNE", "NED": "NLD", "NIR": "NIR", "NOR": "NOR",
    "POL": "POL", "POR": "PRT", "ROU": "ROU", "RUS": "RUS", "SCO": "SCO", "SMR": "SMR",
    "SRB": "SRB", "SUI": "CHE", "SVK": "SVK", "SVN": "SVN", "SWE": "SWE", "TUR": "TUR",
    "UKR": "UKR", "WAL": "WAL",
}  # fmt: skip
# A club takes the country of the league it plays in (D-022): Monaco plays in France and
# Liechtenstein's clubs in Switzerland.
LEAGUE_COUNTRY: dict[str, str] = {"MCO": "FRA", "LIE": "CHE"}

# Competition-seasons whose single-match knockout rounds were played at neutral venues.
NEUTRAL_SINGLE_MATCH = {("UCL", "1920"): "2019/20 final tournament in Lisbon"}

_DATE = re.compile(
    r"^\s+(Mon|Tue|Wed|Thu|Fri|Sat|Sun)\s+"
    r"(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\s+(\d{1,2})(?:\s+(\d{4}))?\s*$"
)
_MATCH = re.compile(
    r"^\s+(?:(?P<time>\d{1,2}[:.]\d{2})\s+)?"
    r"(?P<home>\S.*?\([A-Z]{3}\))\s+v\s+(?P<away>\S.*?\([A-Z]{3}\))(?P<score>.*)$"
)
_TEAM = re.compile(r"^(?P<name>.*\S) \((?P<code>[A-Z]{3})\)$")
_FT = re.compile(r"^(\d{1,2})-(\d{1,2})(?:\s+\((\d{1,2})-(\d{1,2})\))?$")
_AET = re.compile(
    r"^(?:(?P<p1>\d{1,2})-(?P<p2>\d{1,2})\s+pen\.\s+)?(?P<e1>\d{1,2})-(?P<e2>\d{1,2})\s+a\.e\.t\.\s+"
    r"\((?P<r1>\d{1,2})-(?P<r2>\d{1,2})(?:,\s*(?P<h1>\d{1,2})-(?P<h2>\d{1,2}))?\)$"
)
_AWARDED = re.compile(r"^(\d{1,2})-(\d{1,2})\s+\[awarded\]$")
_TITLE = re.compile(r"^=\s*(?P<title>.+?)\s+(?P<y1>\d{4})/(?P<y2>\d{2})\s*$")
_HEADER_MATCHES = re.compile(r"^#\s*Matches\s+(\d+)")
_HEADER_DATES = re.compile(
    r"^#\s*Date\s+(\w{3})\s+(\w{3})\s+(\d{1,2})(?:\s+(\d{4}))?\s*-\s*"
    r"(\w{3})\s+(\w{3})\s+(\d{1,2})\s+(\d{4})"
)
_WEEKDAYS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]
_MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"]


@dataclass(frozen=True)
class OFIssue:
    severity: str  # error (row or value left out) | warning
    code: str
    detail: str
    line: int = 0  # 0 = whole file


@dataclass
class OFMatch:
    competition: str
    season: str
    stage: str  # normalised, e.g. "Group A", "League MD3", "Round of 16 leg 2", "Q2 leg 1"
    match_date: date  # CET/CEST date, as listed
    kickoff_local: str | None  # "HH:MM" CET/CEST
    kickoff_utc: datetime | None
    home_name: str  # as in the file, with its country code: "FC Bayern München (GER)"
    away_name: str
    home_goals: int | None  # regulation time
    away_goals: int | None
    ht: tuple[int, int] | None = None
    aet: tuple[int, int] | None = None  # score after extra time
    pens: tuple[int, int] | None = None
    status: str = "played"  # played | awarded | cancelled | scheduled
    model_exclusion: str | None = None
    neutral: bool = False
    line: int = 0
    stage_header: str = ""

    @property
    def extra_time(self) -> bool:
        return self.aet is not None

    @property
    def penalties(self) -> bool:
        return self.pens is not None

    @property
    def home_code(self) -> str:
        return team_code(self.home_name)

    @property
    def away_code(self) -> str:
        return team_code(self.away_name)


@dataclass
class OFParse:
    competition: str
    season: str
    matches: list[OFMatch] = field(default_factory=list)
    issues: list[OFIssue] = field(default_factory=list)
    declared_matches: int | None = None
    declared_dates: tuple[date, date] | None = None  # from the "# Date" header


def team_code(name: str) -> str:
    m = _TEAM.match(name)
    return m.group("code") if m else ""


def team_country(code: str) -> str | None:
    """Country (as in reference/teams.csv) of a club entered under this openfootball code."""
    iso = OF_COUNTRIES.get(code)
    return None if iso is None else LEAGUE_COUNTRY.get(iso, iso)


def normalise_stage(header: str, qualifying: bool) -> str | None:
    """Stage label for a `▪` header, or None when the header is not recognised."""
    h = re.sub(r"\s+", " ", header.replace("▪", "")).strip()
    h = re.sub(r"^Finals, ", "", h)
    if m := re.fullmatch(r"(?:Group|Gruppe) ([A-L])", h):
        return f"Group {m.group(1)}"
    if m := re.fullmatch(r"(Group|League), Matchday (\d+)", h):
        return f"{m.group(1)} MD{m.group(2)}"
    if h == "League phase":
        return "League"
    if m := re.fullmatch(r"(?:Round (\d)|(\d)\. Round)", h):
        return f"Q{m.group(1) or m.group(2)}" if qualifying else None
    if re.fullmatch(r"Play-?offs(?:, Matchday \d)?", h):
        return "Play-off" if qualifying else "Knockout play-off"
    return {
        "Sechzehntelfinale": "Round of 32",
        "Round of 32": "Round of 32",
        "Round of 16": "Round of 16",
        "Quarterfinals": "Quarter-final",
        "Quarter-finals": "Quarter-final",
        "Semifinals": "Semi-final",
        "Semi-finals": "Semi-final",
        "Final": "Final",
    }.get(h)


def is_knockout(stage: str) -> bool:
    return not stage.startswith(("Group", "League"))


def _resolve_year(wd: str, mon: str, day: int, year: int | None, years: tuple[int, int]):
    """(date, problem) for a date line: an explicit year is checked against the weekday; a
    missing year is the season year whose weekday matches."""
    month = _MONTHS.index(mon) + 1
    want = _WEEKDAYS.index(wd)
    candidates = [year] if year else list(years)
    found = []
    for y in candidates:
        try:
            d = date(y, month, day)
        except ValueError:
            continue
        if d.weekday() == want:
            found.append(d)
    if len(found) == 1:
        return found[0], None
    if year:
        try:
            return date(year, month, day), f"{wd} {mon} {day} {year}: weekday does not match"
        except ValueError:
            return None, f"{mon} {day} {year} is not a date"
    return None, f"{wd} {mon} {day}: no single season year matches the weekday"


def _header_dates(m: re.Match) -> tuple[date, date] | None:
    """The "# Date" header's first and last day; a first day without a year is in the last
    day's year."""
    end_year = int(m.group(8))
    last, problem = _resolve_year(
        m.group(5), m.group(6), int(m.group(7)), end_year, (end_year,) * 2
    )
    year = int(m.group(4)) if m.group(4) else end_year
    first, problem_first = _resolve_year(m.group(1), m.group(2), int(m.group(3)), year, (year,) * 2)
    if problem or problem_first or first is None or last is None:
        return None
    return first, last


def parse_score(text: str) -> dict | None:
    """The parts of a score string, or None if the form is not recognised."""
    s = re.sub(r"\s+", " ", text).strip()
    if s == "":
        return {"status": "scheduled"}
    if s == "[cancelled]":
        return {"status": "cancelled"}
    if m := _AWARDED.match(s):
        return {"status": "awarded", "reg": (int(m.group(1)), int(m.group(2)))}
    if m := _FT.match(s):
        ht = (int(m.group(3)), int(m.group(4))) if m.group(3) else None
        return {"status": "played", "reg": (int(m.group(1)), int(m.group(2))), "ht": ht}
    if m := _AET.match(s):
        g = m.groupdict()
        return {
            "status": "played",
            "reg": (int(g["r1"]), int(g["r2"])),
            "ht": (int(g["h1"]), int(g["h2"])) if g["h1"] else None,
            "aet": (int(g["e1"]), int(g["e2"])),
            "pens": (int(g["p1"]), int(g["p2"])) if g["p1"] else None,
        }
    return None


def parse_openfootball(content: bytes, file_name: str, season: str) -> OFParse:
    """Parse one football.txt file (`season` is the project key, e.g. "1819")."""
    competition = FILES[file_name]
    out = OFParse(competition, season)
    start = season_start_year(season)
    years = (start, start + 1)
    qualifying = competition in QUALIFYING
    stage: str | None = None
    header = ""
    day: date | None = None
    kickoff: str | None = None
    try:
        text = content.decode("utf-8")
    except UnicodeDecodeError as exc:
        out.issues.append(OFIssue("error", "not_utf8", str(exc)))
        return out

    for n, raw_line in enumerate(text.splitlines(), 1):
        line = raw_line.rstrip()
        stripped = line.strip()
        if not stripped:
            continue
        if stripped.startswith("="):
            m = _TITLE.match(stripped)
            expected = f"{TITLES[competition]} {start}/{(start + 1) % 100:02d}"
            if not m or f"{m.group('title')} {m.group('y1')}/{m.group('y2')}" != expected:
                out.issues.append(OFIssue("error", "unexpected_title", stripped, n))
            continue
        if stripped.startswith("#"):
            if m := _HEADER_MATCHES.match(stripped):
                out.declared_matches = int(m.group(1))
            if m := _HEADER_DATES.match(stripped):
                out.declared_dates = _header_dates(m)
                if out.declared_dates is None:
                    out.issues.append(OFIssue("warning", "bad_date_header", stripped, n))
            continue
        if stripped.startswith("▪"):
            header = stripped
            stage = normalise_stage(stripped, qualifying)
            day, kickoff = None, None
            if stage is None:
                out.issues.append(OFIssue("error", "unknown_stage", stripped, n))
            continue
        if m := _DATE.match(line):
            year = int(m.group(4)) if m.group(4) else None
            day, problem = _resolve_year(m.group(1), m.group(2), int(m.group(3)), year, years)
            kickoff = None
            if problem:
                out.issues.append(
                    OFIssue("error" if day is None else "warning", "bad_date", problem, n)
                )
            continue
        m = _MATCH.match(line)
        if m is None:
            out.issues.append(OFIssue("error", "unparsed_line", stripped, n))
            continue
        if m.group("time"):
            kickoff = m.group("time").replace(".", ":").zfill(5)
        if stage is None or day is None:
            code = "match_without_stage" if stage is None else "match_without_date"
            out.issues.append(OFIssue("error", code, stripped, n))
            continue
        home, away = m.group("home").strip(), m.group("away").strip()
        bad = [t for t in (home, away) if team_country(team_code(t)) is None]
        if bad:
            out.issues.append(OFIssue("error", "unknown_country_code", ", ".join(bad), n))
            continue
        score = parse_score(m.group("score"))
        if score is None:
            out.issues.append(OFIssue("error", "unparsed_score", stripped, n))
            continue
        kickoff_utc = None
        if kickoff:
            hh, mm = (int(x) for x in kickoff.split(":"))
            local = datetime.combine(day, time(hh, mm), tzinfo=OF_TZ)
            kickoff_utc = local.astimezone(UTC)
        else:
            out.issues.append(OFIssue("warning", "no_kickoff_time", stripped, n))
        reg = score.get("reg")
        match = OFMatch(
            competition=competition,
            season=season,
            stage=stage,
            match_date=day,
            kickoff_local=kickoff,
            kickoff_utc=kickoff_utc,
            home_name=home,
            away_name=away,
            home_goals=reg[0] if reg else None,
            away_goals=reg[1] if reg else None,
            ht=score.get("ht"),
            aet=score.get("aet"),
            pens=score.get("pens"),
            status=score["status"],
            model_exclusion=score["status"]
            if score["status"] in ("awarded", "cancelled")
            else None,
            line=n,
            stage_header=header,
        )
        out.issues += _check_match(match)
        out.matches.append(match)

    _assign_legs(out)
    if out.declared_matches is not None and out.declared_matches != len(out.matches):
        out.issues.append(
            OFIssue(
                "warning",
                "match_count",
                f"header says {out.declared_matches} matches, file has {len(out.matches)}",
            )
        )
    if out.declared_dates is not None:
        first, last = out.declared_dates
        for mt in out.matches:
            if not first <= mt.match_date <= last:
                out.issues.append(
                    OFIssue(
                        "warning",
                        "date_outside_header_range",
                        f"{mt.match_date} not in {first} to {last}",
                        mt.line,
                    )
                )
    seen: dict[tuple, int] = {}
    for mt in out.matches:
        key = (mt.match_date, mt.home_name, mt.away_name)
        if key in seen:
            out.issues.append(
                OFIssue("error", "duplicate_fixture", f"same as line {seen[key]}", mt.line)
            )
        seen.setdefault(key, mt.line)
    return out


def _check_match(m: OFMatch) -> list[OFIssue]:
    issues = []
    reg = (m.home_goals, m.away_goals)
    if m.ht and reg[0] is not None and (m.ht[0] > reg[0] or m.ht[1] > reg[1]):
        issues.append(OFIssue("warning", "half_time_above_full_time", f"{m.ht} v {reg}", m.line))
    if m.aet is not None:
        if m.aet[0] < reg[0] or m.aet[1] < reg[1]:
            issues.append(OFIssue("error", "extra_time_below_regulation", f"{m.aet}", m.line))
        if not is_knockout(m.stage):
            issues.append(OFIssue("warning", "extra_time_in_league_stage", m.stage, m.line))
    if m.pens is not None and m.pens[0] == m.pens[1]:
        issues.append(OFIssue("warning", "penalties_level", f"{m.pens}", m.line))
    return issues


def _assign_legs(out: OFParse) -> None:
    """Number the legs of two-legged ties, mark neutral matches and check that every tie went
    to extra time and penalties exactly when it was level."""
    ties: dict[tuple[str, frozenset], list[OFMatch]] = defaultdict(list)
    for m in out.matches:
        if is_knockout(m.stage):
            ties[(m.stage, frozenset((m.home_name, m.away_name)))].append(m)
    for (stage, _), legs in ties.items():
        legs.sort(key=lambda x: (x.match_date, x.line))
        if len(legs) == 2:
            first, second = legs
            if (first.home_name, first.away_name) != (second.away_name, second.home_name):
                out.issues.append(OFIssue("warning", "legs_not_reversed", stage, second.line))
            first.stage, second.stage = f"{stage} leg 1", f"{stage} leg 2"
            if first.extra_time or first.penalties:
                out.issues.append(OFIssue("warning", "extra_time_in_first_leg", stage, first.line))
            _check_two_legs(out, first, second)
        elif len(legs) == 1:
            m = legs[0]
            if stage == "Final" or (m.competition, m.season) in NEUTRAL_SINGLE_MATCH:
                m.neutral = True
            else:
                out.issues.append(
                    OFIssue("warning", "single_match_tie", f"{stage}: one match only", m.line)
                )
            if m.status == "played":
                _check_decided(out, [m], m.home_goals == m.away_goals, m.aet, m)
        else:
            for m in legs:
                out.issues.append(
                    OFIssue("warning", "tie_has_more_than_two_matches", stage, m.line)
                )


def _check_two_legs(out: OFParse, first: OFMatch, second: OFMatch) -> None:
    if first.status != "played" or second.status != "played":
        return
    # Goals of the second leg's home team (A) and away team (B) over both legs.
    a_first, b_first = first.away_goals, first.home_goals
    a_reg = a_first + second.home_goals
    b_reg = b_first + second.away_goals
    away_goals_rule = int(first.season[:2]) < 21  # abolished from 2021/22
    level = a_reg == b_reg and (not away_goals_rule or a_first == second.away_goals)
    after_et = None
    if second.aet:
        a_et, b_et = a_first + second.aet[0], b_first + second.aet[1]
        after_et = (a_et, b_et) if not away_goals_rule or a_first == second.aet[1] else (1, 0)
    _check_decided(out, [first, second], level, after_et, second)


def _check_decided(
    out: OFParse,
    tie: list[OFMatch],
    level_after_90: bool,
    after_et: tuple[int, int] | None,
    last: OFMatch,
) -> None:
    """A knockout tie goes to extra time exactly when it is level after 90 minutes, and to
    penalties exactly when it is still level after extra time. `after_et` is the tie's
    (aggregate) score after extra time, or None when there was none."""
    if last.extra_time and not level_after_90:
        _inconsistent(out, tie, "extra_time_not_level", "not level after 90 minutes")
    elif level_after_90 and not last.extra_time:
        _inconsistent(out, tie, "level_without_extra_time", "level after 90, no extra time")
    elif after_et is not None:
        level_et = after_et[0] == after_et[1]
        if last.penalties and not level_et:
            _inconsistent(out, tie, "penalties_not_level", "not level after extra time")
        elif level_et and not last.penalties:
            _inconsistent(out, tie, "level_without_penalties", "level after extra time")


def _inconsistent(out: OFParse, matches: list[OFMatch], code: str, detail: str) -> None:
    """A tie whose scores contradict its extra time or penalties: one of its scores is wrong,
    and which one can't be told, so every match of the tie is kept but excluded from models."""
    for m in matches:
        if m.model_exclusion is None:
            m.model_exclusion = "tie_inconsistent"
    out.issues.append(
        OFIssue("warning", code, f"{detail}; tie excluded from models", matches[-1].line)
    )
