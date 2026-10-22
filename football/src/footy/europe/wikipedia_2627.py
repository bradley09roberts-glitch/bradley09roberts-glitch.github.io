"""2026/27 UEFA club competition matches from English Wikipedia, as a normalised CSV.

Input is the raw `action=parse` JSON stored by `footy.europe.wiki_fetch` (source "wikipedia");
nothing is parsed that was not first stored in the raw store. Each `{{#invoke:Football box}}`
on a page becomes one row: played matches with their regulation-time score, and scheduled
fixtures with their kick-off.

Rules (see docs/europe/uefa-2627.md):
- Kick-offs are listed by Wikipedia in CET/CEST, "as listed by UEFA"; each page must say so,
  and they are converted with the Europe/Paris zone. Where the box also gives the venue's local
  time and UTC offset, the two must agree.
- Scores are regulation time. For a match that went to extra time the regulation score is
  rebuilt from the goal minutes (90+x counts, 91 and later doesn't), and only kept when the
  goal list adds up to the final score. Extra time and penalties are flags only.
- Extra time is recognised from the box's `aet` flag or `{{aet}}`, and, outside the league
  phase, also from a goal after the 90th minute (not 90+x), a penalty shoot-out, or the page's
  own summary table marking the leg a.e.t. (sources do omit the marker). A two-legged tie, or
  a single-match tie, must then go to extra time exactly when it is level after 90 minutes;
  where it doesn't, both legs keep no regulation score (status played, nothing to settle) and
  an error is reported for a person to look at.
- Walkovers and awarded results are stored as `awarded` (with the awarded score, if given);
  abandoned, postponed or suspended matches as `postponed`, cancelled ones as `cancelled`, with
  no score (a score at the time of abandonment is not a result). A box that is not played
  doesn't count as a leg, so its replay keeps the leg number.
- `neutral` is set when the stadium line names a country other than the home team's (for
  example Shakhtar Donetsk at Stamford Bridge, London, England), or by `venue_overrides.csv`
  (a venue a permitted second source gives where the English page doesn't name the country).
- Every problem found is returned as a `ParseIssue`; nothing is silently dropped or guessed.

Wikipedia text is CC BY-SA 4.0; the CSV keeps the page, section and revision of every row.
"""

import csv
import json
import re
from collections import Counter, defaultdict
from collections.abc import Iterator
from dataclasses import dataclass, field
from datetime import UTC, date, datetime, time, timedelta
from pathlib import Path
from zoneinfo import ZoneInfo

from sqlalchemy.orm import Session

from footy.europe.wiki_fetch import SOURCE, WikiPage
from footy.ingest.raw_store import latest_revision, read_raw

SEASON = "2627"
UEFA_TZ = ZoneInfo("Europe/Paris")  # CET/CEST, the basis Wikipedia and UEFA list times in

CSV_COLUMNS = [
    "competition",
    "season",
    "stage",
    "match_date",
    "kickoff_utc",
    "home_name",
    "home_country",
    "away_name",
    "away_country",
    "home_goals",
    "away_goals",
    "extra_time",
    "penalties",
    "neutral",
    "status",
    "source",
    "source_ref",
]
TEAM_COLUMNS = [
    "competition",
    "season",
    "pot",
    "team_name",
    "country",
    "fifa_code",
    "wiki_title",
    "coefficient",
    "source_ref",
]


@dataclass(frozen=True)
class PageSpec:
    competition: str  # UCL | UEL | UECL | UCLQ | UELQ | UECLQ
    kind: str  # league | qualifying
    page: WikiPage


PAGES: tuple[PageSpec, ...] = (
    PageSpec(
        "UCL",
        "league",
        WikiPage("ucl/2627/league-phase", "2026–27 UEFA Champions League league phase"),
    ),
    PageSpec(
        "UEL",
        "league",
        WikiPage("uel/2627/league-phase", "2026–27 UEFA Europa League league phase"),
    ),
    PageSpec(
        "UECL",
        "league",
        WikiPage("uecl/2627/league-phase", "2026–27 UEFA Conference League league phase"),
    ),
    PageSpec(
        "UCLQ",
        "qualifying",
        WikiPage(
            "ucl/2627/qualifying",
            "2026–27 UEFA Champions League qualifying phase and play-off round",
        ),
    ),
    PageSpec(
        "UELQ",
        "qualifying",
        WikiPage(
            "uel/2627/qualifying", "2026–27 UEFA Europa League qualifying phase and play-off round"
        ),
    ),
    PageSpec(
        "UECLQ",
        "qualifying",
        WikiPage(
            "uecl/2627/qualifying-q1-q2",
            "2026–27 UEFA Conference League qualifying (first and second round matches)",
        ),
    ),
    PageSpec(
        "UECLQ",
        "qualifying",
        WikiPage(
            "uecl/2627/qualifying-q3-po",
            "2026–27 UEFA Conference League qualifying (third and play-off round matches)",
        ),
    ),
)
# League-phase size: matches per team and total matches.
LEAGUE_FORMAT = {"UCL": (8, 144), "UEL": (8, 144), "UECL": (6, 108)}

# FIFA trigram (as used by Wikipedia's {{fbaicon}}) -> ISO 3166-1 alpha-3, with the four UK
# football associations kept separate as in reference/teams.csv (England = ENG).
FIFA_TO_ISO = {
    "ALB": "ALB", "AND": "AND", "ARM": "ARM", "AUT": "AUT", "AZE": "AZE", "BEL": "BEL",
    "BIH": "BIH", "BLR": "BLR", "BUL": "BGR", "CRO": "HRV", "CYP": "CYP", "CZE": "CZE",
    "DEN": "DNK", "ENG": "ENG", "ESP": "ESP", "EST": "EST", "FIN": "FIN", "FRA": "FRA",
    "FRO": "FRO", "GEO": "GEO", "GER": "DEU", "GIB": "GIB", "GRE": "GRC", "HUN": "HUN",
    "IRL": "IRL", "ISL": "ISL", "ISR": "ISR", "ITA": "ITA", "KAZ": "KAZ", "KOS": "XKX",
    "LIE": "LIE", "LTU": "LTU", "LUX": "LUX", "LVA": "LVA", "MDA": "MDA", "MKD": "MKD",
    "MLT": "MLT", "MNE": "MNE", "NED": "NLD", "NIR": "NIR", "NOR": "NOR", "POL": "POL",
    "POR": "PRT", "ROU": "ROU", "RUS": "RUS", "SCO": "SCO", "SMR": "SMR", "SRB": "SRB",
    "SUI": "CHE", "SVK": "SVK", "SVN": "SVN", "SWE": "SWE", "TUR": "TUR", "UKR": "UKR",
    "WAL": "WAL",
    # Older or IOC codes that some Wikipedia pages still use.
    "LAT": "LVA", "ROM": "ROU", "SLO": "SVN",
}  # fmt: skip

# Country names as they appear at the end of a stadium line -> ISO code (for neutral venues).
COUNTRY_NAMES = {
    "Albania": "ALB", "Andorra": "AND", "Armenia": "ARM", "Austria": "AUT",
    "Azerbaijan": "AZE", "Belarus": "BLR", "Belgium": "BEL",
    "Bosnia and Herzegovina": "BIH", "Bulgaria": "BGR", "Croatia": "HRV", "Cyprus": "CYP",
    "Czech Republic": "CZE", "Czechia": "CZE", "Denmark": "DNK", "England": "ENG",
    "Estonia": "EST", "Faroe Islands": "FRO", "Finland": "FIN", "France": "FRA",
    "Georgia": "GEO", "Germany": "DEU", "Gibraltar": "GIB", "Greece": "GRC",
    "Hungary": "HUN", "Iceland": "ISL", "Israel": "ISR", "Italy": "ITA",
    "Kazakhstan": "KAZ", "Kosovo": "XKX", "Latvia": "LVA", "Liechtenstein": "LIE",
    "Lithuania": "LTU", "Luxembourg": "LUX", "Malta": "MLT", "Moldova": "MDA",
    "Montenegro": "MNE", "Netherlands": "NLD", "North Macedonia": "MKD",
    "Northern Ireland": "NIR", "Norway": "NOR", "Poland": "POL", "Portugal": "PRT",
    "Republic of Ireland": "IRL", "Ireland": "IRL", "Romania": "ROU", "Russia": "RUS",
    "San Marino": "SMR", "Scotland": "SCO", "Serbia": "SRB", "Slovakia": "SVK",
    "Slovenia": "SVN", "Spain": "ESP", "Sweden": "SWE", "Switzerland": "CHE",
    "Turkey": "TUR", "Türkiye": "TUR", "Ukraine": "UKR", "Wales": "WAL",
}  # fmt: skip
# Associations whose clubs are required to play home matches away from home (UEFA rulings
# since 2022); a home match of theirs with no venue country stated is reported for checking.
RELOCATED_ASSOCIATIONS = {"UKR", "BLR", "ISR", "RUS"}

QUALIFYING_ROUNDS = {
    "preliminary round": "Preliminary",
    "first qualifying round": "Q1",
    "second qualifying round": "Q2",
    "third qualifying round": "Q3",
    "play-off round": "Play-off",
}
PATHS = {"champions path": "CP", "league path": "LP", "main path": "MP"}


@dataclass
class EuroRow:
    competition: str
    stage: str
    match_date: date
    kickoff_utc: datetime | None
    home_name: str
    home_country: str
    away_name: str
    away_country: str
    home_goals: int | None
    away_goals: int | None
    extra_time: bool
    penalties: bool
    neutral: bool
    status: str  # played | scheduled | postponed | cancelled | awarded
    source_ref: str
    season: str = SEASON
    source: str = SOURCE
    uefa_match_id: str | None = None
    home_wiki_title: str | None = None
    away_wiki_title: str | None = None
    final_score: tuple[int, int] | None = None  # as listed, after extra time if any
    goals_text: tuple[str, str] = ("", "")  # the box's goals1 and goals2 (not in the CSV)

    def csv_row(self) -> dict[str, str]:
        def opt(v) -> str:
            return "" if v is None else str(v)

        return {
            "competition": self.competition,
            "season": self.season,
            "stage": self.stage,
            "match_date": self.match_date.isoformat(),
            "kickoff_utc": self.kickoff_utc.isoformat() if self.kickoff_utc else "",
            "home_name": self.home_name,
            "home_country": self.home_country,
            "away_name": self.away_name,
            "away_country": self.away_country,
            "home_goals": opt(self.home_goals),
            "away_goals": opt(self.away_goals),
            "extra_time": str(int(self.extra_time)),
            "penalties": str(int(self.penalties)),
            "neutral": str(int(self.neutral)),
            "status": self.status,
            "source": self.source,
            "source_ref": self.source_ref,
        }


@dataclass(frozen=True)
class TeamEntry:
    competition: str
    pot: int
    team_name: str
    country: str
    fifa_code: str
    wiki_title: str
    coefficient: str
    source_ref: str

    def csv_row(self) -> dict[str, str]:
        return {
            "competition": self.competition,
            "season": SEASON,
            "pot": str(self.pot),
            "team_name": self.team_name,
            "country": self.country,
            "fifa_code": self.fifa_code,
            "wiki_title": self.wiki_title,
            "coefficient": self.coefficient,
            "source_ref": self.source_ref,
        }


@dataclass(frozen=True)
class ParseIssue:
    severity: str  # error (row or value left out) | warning
    code: str
    detail: str
    ref: str


@dataclass
class ParsedPage:
    rows: list[EuroRow] = field(default_factory=list)
    teams: list[TeamEntry] = field(default_factory=list)
    issues: list[ParseIssue] = field(default_factory=list)


# --- wikitext helpers -------------------------------------------------------------------

_COMMENT = re.compile(r"<!--.*?-->", re.S)
_REF = re.compile(r"<ref[^>/]*/>|<ref[^>]*>.*?</ref>", re.S | re.I)
_HEADING = re.compile(r"^(={2,6})\s*(.+?)\s*\1\s*$", re.M)
_BOX_START = re.compile(r"\{\{\s*(?:#invoke:\s*football box\s*\|\s*main|football ?box)\b", re.I)
_FBAICON = re.compile(r"\{\{\s*(?:#invoke:flag\s*\|\s*)?fbaicon\s*\|\s*([A-Z]{3})\s*\}\}")
_LINK = re.compile(r"\[\[([^|\]]+)(?:\|([^\]]*))?\]\]")
_ILL = re.compile(r"\{\{\s*ill\s*\|([^|}]+)(?:\|[^}]*)?\}\}", re.I)
_SCORE_LINK = re.compile(r"\{\{\s*score link\s*\|[^|}]*\|([^}]*)\}\}", re.I)
_START_DATE = re.compile(
    r"\{\{\s*start date\s*\|\s*(\d{4})\s*\|\s*(\d{1,2})\s*\|\s*(\d{1,2})", re.I
)
_HHMM = re.compile(r"\b(\d{1,2}):(\d{2})\b")
_LOCAL_TIME = re.compile(
    r"\(\s*(\d{1,2}):(\d{2})\s*\[\[\s*UTC\s*([+\-−±])\s*(\d{1,2}):?(\d{2})?", re.I
)
_SCORE = re.compile(r"^\s*(\d{1,2})\s*[-–−—]\s*(\d{1,2})\b")
_GOAL = re.compile(r"\{\{\s*goal\s*\|([^{}]*)\}\}", re.I)
_MINUTE = re.compile(r"'*(\d{1,3})'*\s*(?:\+\s*(\d{1,2}))?'*")
_UEFA_MATCH = re.compile(r"uefa\.com/[a-z]+/match/(\d+)|UEFA match attendance\s*\|\s*(\d+)", re.I)
_TRUE = {"y", "yes", "1", "true"}
_AWARDED = re.compile(r"awarded|\bawd\b|w/o|walkover", re.I)
_NOT_PLAYED = re.compile(
    r"\b(?:postponed|abandoned|suspended|called off|cancell?ed)\b|\b(?:P|A)\s*[-–−]\s*(?:P|A)\b"
)
_NOT_PLAYED_WORDS = re.compile(r"\b(?:postponed|abandoned|suspended|called off|cancell?ed)\b", re.I)
_AET = re.compile(r"\{\{\s*aet\s*\}\}|a\.\s*e\.\s*t\.?|after extra time", re.I)
_PSO = re.compile(r"\{\{\s*(?:pso|pen\.?|pens)\b|\bp(?:en)?s?\.?\s*\)|on penalties", re.I)


def strip_comments(text: str) -> str:
    return _COMMENT.sub("", text)


def _template_end(text: str, start: int) -> int:
    """Offset just past the `}}` closing the template that opens at `start`."""
    depth, i, n = 0, start, len(text)
    while i < n:
        if text.startswith("{{", i):
            depth += 1
            i += 2
        elif text.startswith("}}", i):
            depth -= 1
            i += 2
            if depth == 0:
                return i
        else:
            i += 1
    raise ValueError(f"unclosed template at offset {start}")


def split_top(body: str) -> list[str]:
    """A template body (without the outer braces) split at top-level `|`: name, then params."""
    parts, depth_t, depth_l, cur, i = [], 0, 0, [], 0
    while i < len(body):
        two = body[i : i + 2]
        if two == "{{":
            depth_t += 1
            cur.append(two)
            i += 2
            continue
        if two == "}}":
            depth_t -= 1
            cur.append(two)
            i += 2
            continue
        if two == "[[":
            depth_l += 1
            cur.append(two)
            i += 2
            continue
        if two == "]]":
            depth_l -= 1
            cur.append(two)
            i += 2
            continue
        ch = body[i]
        if ch == "|" and depth_t == 0 and depth_l == 0:
            parts.append("".join(cur))
            cur = []
        else:
            cur.append(ch)
        i += 1
    parts.append("".join(cur))
    return parts


def split_params(body: str) -> dict[str, str]:
    """Named parameters of a template body (without the outer braces)."""
    params: dict[str, str] = {}
    for part in split_top(body)[1:]:
        if "=" in part:
            key, _, value = part.partition("=")
            params[key.strip().lower()] = value.strip()
    return params


def _remove_templates(text: str) -> str:
    out, i = [], 0
    while True:
        j = text.find("{{", i)
        if j < 0:
            out.append(text[i:])
            return "".join(out)
        out.append(text[i:j])
        try:
            i = _template_end(text, j)
        except ValueError:
            out.append(text[j:])
            return "".join(out)


def plain_text(value: str) -> str:
    """Readable text of a wikitext value: refs and templates removed, links as their label."""
    text = _REF.sub("", value)
    text = _ILL.sub(r"\1", text)
    text = _SCORE_LINK.sub(r"\1", text)
    text = _remove_templates(text)
    text = _LINK.sub(lambda m: m.group(2) if m.group(2) is not None else m.group(1), text)
    text = text.replace("'''", "").replace("''", "").replace("&nbsp;", " ")
    return re.sub(r"\s+", " ", text).strip()


@dataclass(frozen=True)
class RawBox:
    offset: int
    line: int
    sections: tuple[str, ...]  # heading path, outermost first
    params: dict[str, str]


def iter_boxes(wikitext: str) -> Iterator[RawBox]:
    """Every football box on a page, with the heading path it sits under."""
    text = strip_comments(wikitext)
    headings = [
        (m.start(), len(m.group(1)), plain_text(m.group(2))) for m in _HEADING.finditer(text)
    ]
    pos = 0
    while True:
        m = _BOX_START.search(text, pos)
        if m is None:
            return
        end = _template_end(text, m.start())
        path: dict[int, str] = {}
        for off, level, title in headings:
            if off > m.start():
                break
            path = {lv: t for lv, t in path.items() if lv < level}
            path[level] = title
        yield RawBox(
            offset=m.start(),
            line=text.count("\n", 0, m.start()) + 1,
            sections=tuple(path[k] for k in sorted(path)),
            params=split_params(text[m.start() + 2 : end - 2]),
        )
        pos = end


# --- field parsers ----------------------------------------------------------------------


def parse_team(value: str) -> tuple[str, str | None, str | None]:
    """(display name, FIFA code from {{fbaicon}}, linked article title) of a team1/team2 value."""
    flag = _FBAICON.search(value)
    link = _LINK.search(_REF.sub("", value))
    name = plain_text(_FBAICON.sub("", value))
    return name, flag.group(1) if flag else None, link.group(1).strip() if link else None


def parse_score(value: str) -> tuple[int, int] | None:
    m = _SCORE.match(plain_text(value))
    return (int(m.group(1)), int(m.group(2))) if m else None


def goal_minutes(value: str) -> list[tuple[int, int]]:
    """(minute, added time) of every goal listed with {{goal}} in a goals1/goals2 value.

    {{goal}} alternates minute and goal type ({{goal|80||85|pen.}}), but pages sometimes drop
    the empty type ({{goal|72|120+5|pen}}), so every argument that is a minute counts."""
    minutes: list[tuple[int, int]] = []
    for m in _GOAL.finditer(_REF.sub("", value)):
        for arg in m.group(1).split("|"):
            mm = _MINUTE.fullmatch(arg.strip())
            if mm:
                minutes.append((int(mm.group(1)), int(mm.group(2) or 0)))
    return minutes


def regulation_goals(value: str) -> int:
    return sum(1 for minute, _ in goal_minutes(value) if minute <= 90)


def regulation_from_goals(score: tuple[int, int], goals: tuple[str, str]) -> tuple[int, int] | None:
    """The score after 90 minutes of a match that went to extra time, from the goal minutes;
    None unless the goal lists add up to the final score."""
    listed = (len(goal_minutes(goals[0])), len(goal_minutes(goals[1])))
    if listed != score:
        return None
    return regulation_goals(goals[0]), regulation_goals(goals[1])


def not_played_status(text: str) -> str | None:
    """ "cancelled" or "postponed" (abandoned, suspended, called off, P–P, A–A) when a score
    line says the match was not played to a result."""
    if not (_NOT_PLAYED_WORDS.search(text) or _NOT_PLAYED.search(text)):
        return None
    return "cancelled" if re.search(r"cancell?ed", text, re.I) else "postponed"


def kickoff_from_box(match_date: date, time_value: str) -> tuple[datetime | None, str | None]:
    """UTC kick-off from a CET/CEST time; the second item describes any disagreement with the
    venue-local time given in parentheses."""
    text = _REF.sub("", time_value)
    m = _HHMM.search(text)
    if m is None:
        return None, None
    local = datetime.combine(match_date, time(int(m.group(1)), int(m.group(2))), tzinfo=UEFA_TZ)
    kickoff = local.astimezone(UTC)
    venue = _LOCAL_TIME.search(text)
    if venue:
        sign = -1 if venue.group(3) in "-−" else 1
        offset = timedelta(hours=int(venue.group(4)), minutes=int(venue.group(5) or 0)) * sign
        shown = (kickoff + offset).time()
        if (shown.hour, shown.minute) != (int(venue.group(1)), int(venue.group(2))):
            return kickoff, f"{m.group(0)} CET/CEST but venue time {venue.group(0)}"
    return kickoff, None


def venue_date(match_date: date, kickoff: datetime | None, time_value: str) -> date:
    """The venue's local date: the listed date unless the venue's own time crosses midnight."""
    venue = _LOCAL_TIME.search(_REF.sub("", time_value))
    if kickoff is None or venue is None:
        return match_date
    sign = -1 if venue.group(3) in "-−" else 1
    offset = timedelta(hours=int(venue.group(4)), minutes=int(venue.group(5) or 0)) * sign
    return (kickoff + offset).date()


def stadium_country(value: str) -> str | None:
    """ISO code of a country named as the last part of a stadium line, if there is one."""
    text = plain_text(value)
    last = text.rsplit(",", 1)[-1].strip() if "," in text else ""
    return COUNTRY_NAMES.get(last)


def uefa_match_id(params: dict[str, str]) -> str | None:
    for key in ("report", "attendance"):
        m = _UEFA_MATCH.search(params.get(key, ""))
        if m:
            return m.group(1) or m.group(2)
    return None


def _country(code: str | None, ref: str, issues: list[ParseIssue]) -> str:
    if code is None:
        issues.append(ParseIssue("warning", "no_country_flag", "team has no {{fbaicon}}", ref))
        return ""
    iso = FIFA_TO_ISO.get(code)
    if iso is None:
        issues.append(ParseIssue("warning", "unknown_country_code", code, ref))
        return code
    return iso


def times_are_cet(wikitext: str) -> bool:
    """True when the page states that its times are CET/CEST (the basis this parser assumes)."""
    m = re.search(r"Times are (.{0,200})", wikitext)
    return bool(m and re.search(r"\bCES?T\b", m.group(1)))


# --- page parser ------------------------------------------------------------------------


def parse_page(
    spec: PageSpec,
    wikitext: str,
    *,
    revid: int | None,
    retrieved_at: datetime | None = None,
) -> ParsedPage:
    """Turn one stored page into rows (and, for league phases, the team list)."""
    out = ParsedPage()
    page_ref = f"{spec.page.title} (rev {revid})"
    if not times_are_cet(wikitext):
        out.issues.append(
            ParseIssue(
                "error", "time_basis_unknown", "page does not say times are CET/CEST", page_ref
            )
        )
        return out

    legs_seen: dict[tuple[str, str], Counter] = defaultdict(Counter)
    for box in iter_boxes(wikitext):
        ref = f"{spec.page.title}#{box.sections[-1] if box.sections else ''} (rev {revid}"
        p = box.params
        match_id = uefa_match_id(p)
        ref += f", UEFA match {match_id})" if match_id else f", line {box.line})"

        dm = _START_DATE.search(p.get("date", ""))
        if dm is None:
            out.issues.append(ParseIssue("error", "no_date", p.get("date", "")[:80], ref))
            continue
        listed_date = date(int(dm.group(1)), int(dm.group(2)), int(dm.group(3)))
        kickoff, mismatch = kickoff_from_box(listed_date, p.get("time", ""))
        if kickoff is None:
            out.issues.append(ParseIssue("warning", "no_kickoff_time", p.get("time", "")[:80], ref))
        if mismatch:
            out.issues.append(ParseIssue("warning", "venue_time_mismatch", mismatch, ref))

        home, home_code, home_title = parse_team(p.get("team1", ""))
        away, away_code, away_title = parse_team(p.get("team2", ""))
        if not home or not away:
            out.issues.append(ParseIssue("error", "missing_team", f"{home!r} v {away!r}", ref))
            continue
        home_country = _country(home_code, ref, out.issues)
        away_country = _country(away_code, ref, out.issues)

        score_raw = p.get("score", "")
        score_text = plain_text(score_raw)
        marks = _REF.sub("", score_raw)  # footnotes may mention an earlier postponement
        score = parse_score(score_raw)
        status = "scheduled"
        if _AWARDED.search(marks):
            status = "awarded"
            out.issues.append(ParseIssue("warning", "awarded_result", score_text or marks, ref))
        elif (not_played := not_played_status(marks)) is not None:
            status, score = not_played, None
            out.issues.append(ParseIssue("warning", f"{status}_match", score_text or marks, ref))

        # A match that was not played is not a leg: its replay keeps the leg number.
        stage = _stage(
            spec,
            box.sections,
            home,
            away,
            legs_seen,
            ref,
            out.issues,
            counts=status not in ("postponed", "cancelled"),
        )
        if stage is None:
            continue
        if status == "scheduled" and score_text and score is None:
            out.issues.append(ParseIssue("error", "unparsed_score", score_text, ref))
            continue

        extra_time = p.get("aet", "").strip().lower() in _TRUE or bool(_AET.search(marks))
        penalties = bool(p.get("penaltyscore", "").strip()) or bool(
            re.search(r"\{\{\s*pso\b", marks, re.I)
        )
        goals = (p.get("goals1", ""), p.get("goals2", ""))
        home_goals = away_goals = None
        if status == "awarded":
            if score is not None:
                home_goals, away_goals = score
            extra_time = penalties = False
        elif score is not None:
            status = "played"
            home_goals, away_goals = score
            if not extra_time and spec.kind != "league":
                why = []
                if any(minute > 90 for minute, _ in goal_minutes(goals[0] + goals[1])):
                    why.append("a goal after the 90th minute")
                if penalties:
                    why.append("a penalty shoot-out")
                if why:
                    extra_time = True
                    out.issues.append(
                        ParseIssue(
                            "warning",
                            "extra_time_inferred",
                            f"no extra-time marker, but {' and '.join(why)}",
                            ref,
                        )
                    )
            listed_goals = (len(goal_minutes(goals[0])), len(goal_minutes(goals[1])))
            if not extra_time and listed_goals != score:
                out.issues.append(
                    ParseIssue(
                        "warning",
                        "goal_list_mismatch",
                        f"score {score[0]}–{score[1]}, goals listed {listed_goals}",
                        ref,
                    )
                )
            if extra_time:
                regulation = regulation_from_goals(score, goals)
                if regulation is None:
                    out.issues.append(
                        ParseIssue(
                            "error",
                            "regulation_score_unknown",
                            f"after extra time {score[0]}–{score[1]}, goal list {listed_goals}",
                            ref,
                        )
                    )
                    home_goals = away_goals = None
                else:
                    home_goals, away_goals = regulation
        elif extra_time or penalties:
            out.issues.append(ParseIssue("warning", "flags_without_score", score_text, ref))

        if retrieved_at is not None and kickoff is not None:
            if status == "scheduled" and kickoff + timedelta(hours=3) < retrieved_at:
                out.issues.append(
                    ParseIssue("warning", "past_fixture_without_score", kickoff.isoformat(), ref)
                )
            if status == "played" and kickoff > retrieved_at:
                out.issues.append(
                    ParseIssue("error", "score_before_kickoff", kickoff.isoformat(), ref)
                )

        venue_country = stadium_country(p.get("stadium", ""))
        neutral = venue_country is not None and venue_country != home_country
        if not neutral and venue_country is None and home_country in RELOCATED_ASSOCIATIONS:
            out.issues.append(
                ParseIssue(
                    "warning",
                    "venue_country_unstated",
                    f"{home} ({home_country}) at {plain_text(p.get('stadium', ''))}",
                    ref,
                )
            )

        out.rows.append(
            EuroRow(
                competition=spec.competition,
                stage=stage,
                match_date=venue_date(listed_date, kickoff, p.get("time", "")),
                kickoff_utc=kickoff,
                home_name=home,
                home_country=home_country,
                away_name=away,
                away_country=away_country,
                home_goals=home_goals,
                away_goals=away_goals,
                extra_time=extra_time,
                penalties=penalties,
                neutral=neutral,
                status=status,
                source_ref=ref,
                uefa_match_id=match_id,
                home_wiki_title=home_title,
                away_wiki_title=away_title,
                final_score=score,
                goals_text=goals,
            )
        )

    if spec.kind == "league":
        out.teams = parse_league_phase_teams(spec, wikitext, revid=revid)
        out.issues.extend(validate_league_phase(spec, out.rows, out.teams, page_ref))
    else:
        out.issues.extend(check_against_summaries(wikitext, out.rows, page_ref))
        out.issues.extend(check_ties(out.rows))
    return out


def _stage(
    spec: PageSpec,
    sections: tuple[str, ...],
    home: str,
    away: str,
    legs_seen: dict[tuple[str, str], Counter],
    ref: str,
    issues: list[ParseIssue],
    *,
    counts: bool = True,
) -> str | None:
    """The row's stage. In qualifying rounds the leg is the tie's n-th box; a box that does
    not count (a postponed or cancelled match) takes the number of the next leg without
    using it up."""
    if spec.kind == "league":
        for title in reversed(sections):
            m = re.fullmatch(r"Matchday (\d+)", title)
            if m:
                return f"League MD{m.group(1)}"
        issues.append(ParseIssue("error", "no_matchday_heading", " / ".join(sections), ref))
        return None

    round_name = next(
        (QUALIFYING_ROUNDS[t.lower()] for t in sections if t.lower() in QUALIFYING_ROUNDS), None
    )
    if round_name is None:
        issues.append(ParseIssue("error", "no_round_heading", " / ".join(sections), ref))
        return None
    path = next((code for t in sections for name, code in PATHS.items() if name in t.lower()), None)
    key = (round_name, path or "")
    tie = "|".join(sorted((_norm(home), _norm(away))))
    leg = legs_seen[key][tie] + 1
    if counts:
        legs_seen[key][tie] = leg
    if leg > 2:
        issues.append(ParseIssue("error", "tie_seen_three_times", tie, ref))
        return None
    return " ".join(x for x in (round_name, path, f"leg {leg}") if x)


def _norm(name: str) -> str:
    return re.sub(r"\s+", " ", name).strip().casefold()


_POT = re.compile(r"^\|\+\s*Pot\s+(\d+)", re.M)
_TEAM_ROW = re.compile(
    r"^\|\s*\{\{\s*#invoke:flag\s*\|\s*fbaicon\s*\|\s*([A-Z]{3})\s*\}\}\s*"
    r"\[\[([^|\]]+)(?:\|([^\]]+))?\]\][^\n]*\n\|\s*([\d.]+)",
    re.M,
)


def parse_league_phase_teams(
    spec: PageSpec, wikitext: str, *, revid: int | None
) -> list[TeamEntry]:
    """The teams by pot from a league-phase page's "Teams and seeding" section."""
    text = strip_comments(wikitext)
    m = re.search(r"^==\s*Teams and seeding\s*==\s*$(.*?)^==[^=]", text, re.M | re.S)
    if m is None:
        return []
    section = m.group(1)
    pots = [(pm.start(), int(pm.group(1))) for pm in _POT.finditer(section)]
    teams: list[TeamEntry] = []
    for tm in _TEAM_ROW.finditer(section):
        pot = max((n for off, n in pots if off < tm.start()), default=0)
        code, title, label, coeff = tm.groups()
        teams.append(
            TeamEntry(
                competition=spec.competition,
                pot=pot,
                team_name=(label or title).strip(),
                country=FIFA_TO_ISO.get(code, code),
                fifa_code=code,
                wiki_title=title.strip(),
                coefficient=coeff,
                source_ref=f"{spec.page.title}#Teams and seeding (rev {revid})",
            )
        )
    return teams


def validate_league_phase(
    spec: PageSpec, rows: list[EuroRow], teams: list[TeamEntry], page_ref: str
) -> list[ParseIssue]:
    """Structural checks: 36 teams, fixtures per team, one match per team per matchday, no
    repeated pairing, every team in the pots with the same country."""
    issues: list[ParseIssue] = []
    per_team, n_total = LEAGUE_FORMAT[spec.competition]
    if len(teams) != 36:
        issues.append(ParseIssue("error", "team_count", f"{len(teams)} teams in pots", page_ref))
    if len(rows) != n_total:
        issues.append(ParseIssue("error", "fixture_count", f"{len(rows)} of {n_total}", page_ref))
    names = {t.team_name for t in teams}
    count: Counter = Counter()
    pairs: Counter = Counter()
    per_matchday: Counter = Counter()
    for r in rows:
        count[r.home_name] += 1
        count[r.away_name] += 1
        pairs[(r.home_name, r.away_name)] += 1
        per_matchday[(r.stage, r.home_name)] += 1
        per_matchday[(r.stage, r.away_name)] += 1
        for name, country in ((r.home_name, r.home_country), (r.away_name, r.away_country)):
            if names and name not in names:
                issues.append(ParseIssue("error", "team_not_in_pots", name, r.source_ref))
            team = next((t for t in teams if t.team_name == name), None)
            if team and team.country != country:
                issues.append(
                    ParseIssue("error", "country_mismatch", f"{name}: {country}", r.source_ref)
                )
    for name, n in sorted(count.items()):
        if n != per_team:
            issues.append(ParseIssue("error", "fixtures_per_team", f"{name}: {n}", page_ref))
    for pair, n in pairs.items():
        if n > 1:
            issues.append(ParseIssue("error", "repeated_pairing", f"{pair}: {n}", page_ref))
    for (stage, name), n in sorted(per_matchday.items()):
        if n != 1:
            issues.append(
                ParseIssue("error", "matches_per_matchday", f"{stage} {name}: {n}", page_ref)
            )
    stages = {r.stage for r in rows}
    for stage in sorted(stages):
        playing = {name for (st, name) in per_matchday if st == stage}
        if names and playing != names:
            missing = ", ".join(sorted(names - playing))
            issues.append(
                ParseIssue("error", "team_missing_from_matchday", f"{stage}: {missing}", page_ref)
            )
    return issues


_SERIES_START = re.compile(r"\{\{\s*#invoke:\s*sports series\s*\|\s*main\b", re.I)


@dataclass(frozen=True)
class SeriesTie:
    round_name: str | None
    team1: str
    team2: str
    leg1: str  # team1 at home, as listed (may include {{aet}} etc.)
    leg2: str  # team2 at home, but listed with team1's goals first


def iter_series(wikitext: str) -> Iterator[SeriesTie]:
    """Two-legged ties from {{#invoke:Sports series}} summary tables."""
    text = strip_comments(wikitext)
    headings = [
        (m.start(), len(m.group(1)), plain_text(m.group(2))) for m in _HEADING.finditer(text)
    ]
    pos = 0
    while True:
        m = _SERIES_START.search(text, pos)
        if m is None:
            return
        end = _template_end(text, m.start())
        pos = end
        round_name = None
        for off, level, title in headings:
            if off > m.start():
                break
            if level == 2:
                round_name = QUALIFYING_ROUNDS.get(title.lower())
        positional = [x.strip() for x in split_top(text[m.start() + 2 : end - 2])[2:]]
        positional = [x for x in positional if not re.match(r"^[a-z_0-9]+\s*=", x)]
        for i in range(0, len(positional) - 6, 7):
            t1, _c1, _agg, t2, _c2, leg1, leg2 = positional[i : i + 7]
            yield SeriesTie(round_name, plain_text(t1), plain_text(t2), leg1, leg2)


def check_against_summaries(wikitext: str, rows: list[EuroRow], page_ref: str) -> list[ParseIssue]:
    """Compare match boxes with the page's own summary tables: every tie in a summary must
    have both legs among the boxes, with the same (final) scores."""
    issues: list[ParseIssue] = []
    by_key = {(r.stage.split(" ")[0], _norm(r.home_name), _norm(r.away_name)): r for r in rows}
    matched: set[int] = set()
    for tie in iter_series(wikitext):
        rnd = tie.round_name or "?"
        if {_norm(tie.team1), _norm(tie.team2)} & {"n/a", "bye"}:
            continue  # a bye, not a match
        for home, away, listed, flip in (
            (tie.team1, tie.team2, tie.leg1, False),
            (tie.team2, tie.team1, tie.leg2, True),
        ):
            row = by_key.get((rnd, _norm(home), _norm(away)))
            if row is None:
                issues.append(
                    ParseIssue(
                        "error", "summary_leg_without_box", f"{rnd}: {home} v {away}", page_ref
                    )
                )
                continue
            matched.add(id(row))
            issues.extend(_summary_extra_time(row, listed))
            expected = parse_score(listed)
            if expected is not None and flip:
                expected = (expected[1], expected[0])
            if expected != row.final_score:
                issues.append(
                    ParseIssue(
                        "error",
                        "summary_score_mismatch",
                        f"{rnd}: {home} v {away} summary {plain_text(listed)!r}, "
                        f"box {row.final_score}",
                        row.source_ref,
                    )
                )
    for r in rows:
        if id(r) not in matched:
            issues.append(
                ParseIssue(
                    "warning", "box_not_in_summary", f"{r.home_name} v {r.away_name}", r.source_ref
                )
            )
    return issues


def _summary_extra_time(row: EuroRow, listed: str) -> list[ParseIssue]:
    """The summary table marks a leg a.e.t. (or on penalties) but its box doesn't: the box's
    score is after extra time, so the regulation score is rebuilt from its goal minutes (or
    left unknown)."""
    text = _REF.sub("", listed)
    aet = bool(_AET.search(text))
    pens = bool(re.search(r"\{\{\s*pso\b", text, re.I)) or bool(_PSO.search(text))
    if row.status != "played" or row.extra_time or not (aet or pens):
        return []
    row.extra_time = True
    row.penalties = row.penalties or pens
    issues = [
        ParseIssue(
            "warning",
            "extra_time_from_summary",
            f"{row.home_name} v {row.away_name}: summary {plain_text(listed)!r}, box without aet",
            row.source_ref,
        )
    ]
    regulation = regulation_from_goals(row.final_score, row.goals_text) if row.final_score else None
    if regulation is None:
        row.home_goals = row.away_goals = None
        issues.append(
            ParseIssue(
                "error",
                "regulation_score_unknown",
                f"after extra time {row.final_score}, goal list does not add up",
                row.source_ref,
            )
        )
    else:
        row.home_goals, row.away_goals = regulation
    return issues


def check_ties(rows: list[EuroRow]) -> list[ParseIssue]:
    """A tie goes to extra time exactly when it is level after 90 minutes: over both legs for a
    two-legged tie (in the second leg; never in the first), in the match itself for a
    single-match round (the preliminary round). Where that fails, the box is missing an
    extra-time marker or has a wrong score, so neither leg keeps a regulation score (nothing to
    settle or fit) until the page or the parser is fixed. The away goals rule was abolished
    in 2021."""
    issues: list[ParseIssue] = []
    ties: dict[tuple, dict[int, EuroRow]] = defaultdict(dict)
    for r in rows:
        m = re.fullmatch(r"(.+) leg (\d)", r.stage)
        if m is None or r.status != "played":
            continue
        tie = (m.group(1), frozenset((_norm(r.home_name), _norm(r.away_name))))
        ties[tie][int(m.group(2))] = r

    def hold(legs: list[EuroRow], detail: str) -> None:
        for r in legs:
            r.home_goals = r.away_goals = None
        issues.append(ParseIssue("error", "tie_inconsistent", detail, legs[-1].source_ref))

    for (round_stage, _), legs in ties.items():
        first, second = legs.get(1), legs.get(2)
        if round_stage.startswith("Preliminary"):  # single matches (semi-finals and final)
            for r in legs.values():
                if r.home_goals is None:
                    continue
                level = r.home_goals == r.away_goals
                if level != r.extra_time:
                    hold(
                        [r],
                        f"{r.home_name} v {r.away_name}: single match, level after 90 "
                        f"{level}, extra time {r.extra_time}",
                    )
            continue
        if first is not None and first.extra_time:
            hold([first], f"{first.home_name} v {first.away_name}: extra time in a first leg")
            continue
        if first is None or second is None or None in (first.home_goals, second.home_goals):
            continue
        # Goals of the first leg's home team (a) and away team (b) over both legs, after 90.
        a = first.home_goals + second.away_goals
        b = first.away_goals + second.home_goals
        if (a == b) != second.extra_time:
            hold(
                [first, second],
                f"{first.home_name} v {first.away_name}: aggregate {a}–{b} after 90 minutes, "
                f"extra time {second.extra_time}",
            )
    return issues


# --- venue overrides --------------------------------------------------------------------

VENUE_OVERRIDES = Path(__file__).with_name("venue_overrides.csv")


def apply_venue_overrides(rows: list[EuroRow], path: Path = VENUE_OVERRIDES) -> list[ParseIssue]:
    """Set `neutral` from `venue_overrides.csv` (a venue a permitted second source states where
    the English page's stadium line names no country). Each change is noted in the row's
    `source_ref`; an override that matches no row is reported."""
    issues: list[ParseIssue] = []
    if not path.is_file():
        return issues
    by_key = {(r.competition, r.season, r.match_date, r.home_name, r.away_name): r for r in rows}
    with path.open(newline="", encoding="utf-8") as f:
        for o in csv.DictReader(f):
            key = (
                o["competition"],
                o["season"],
                date.fromisoformat(o["match_date"]),
                o["home_name"],
                o["away_name"],
            )
            row = by_key.get(key)
            if row is None:
                issues.append(
                    ParseIssue("warning", "venue_override_unmatched", " ".join(map(str, key)), "")
                )
                continue
            neutral = o["neutral"].strip() in ("1", "true", "True")
            if row.neutral != neutral:
                row.neutral = neutral
                row.source_ref += f"; neutral: {o['venue']} per {o['source']}"
    return issues


# --- raw store to CSV -------------------------------------------------------------------


@dataclass
class BuildResult:
    rows: list[EuroRow]
    teams: list[TeamEntry]
    issues: list[ParseIssue]
    pages: list[dict]  # per page: logical key, revision, revid, retrieved_at, counts
    missing_pages: list[str]


def build_uefa_2627(
    session: Session,
    raw_dir: Path,
    *,
    out_csv: Path | None = None,
    teams_csv: Path | None = None,
    specs: tuple[PageSpec, ...] = PAGES,
) -> BuildResult:
    """Parse the latest stored revision of every page and (optionally) write the CSVs."""
    result = BuildResult([], [], [], [], [])
    for spec in specs:
        raw = latest_revision(session, SOURCE, spec.page.logical_key)
        if raw is None:
            result.missing_pages.append(spec.page.logical_key)
            continue
        body = json.loads(read_raw(raw_dir, raw))
        parse = body["parse"]
        parsed = parse_page(
            spec, parse["wikitext"], revid=parse.get("revid"), retrieved_at=raw.retrieved_at
        )
        result.rows.extend(parsed.rows)
        result.teams.extend(parsed.teams)
        result.issues.extend(parsed.issues)
        result.pages.append(
            {
                "logical_key": spec.page.logical_key,
                "title": parse.get("title"),
                "revid": parse.get("revid"),
                "raw_file_id": raw.id,
                "raw_revision": raw.revision,
                "retrieved_at": raw.retrieved_at.isoformat(),
                "rows": len(parsed.rows),
                "played": sum(r.status == "played" for r in parsed.rows),
                "scheduled": sum(r.status == "scheduled" for r in parsed.rows),
            }
        )
    result.issues.extend(apply_venue_overrides(result.rows))
    result.rows.sort(
        key=lambda r: (r.competition, r.match_date, r.kickoff_utc or _MAX, r.home_name)
    )
    if out_csv is not None:
        write_csv(out_csv, CSV_COLUMNS, (r.csv_row() for r in result.rows))
    if teams_csv is not None:
        write_csv(teams_csv, TEAM_COLUMNS, (t.csv_row() for t in result.teams))
    return result


_MAX = datetime.max.replace(tzinfo=UTC)


def write_csv(path: Path, columns: list[str], rows) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_name(path.name + ".tmp")
    with tmp.open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=columns)
        writer.writeheader()
        writer.writerows(rows)
    tmp.replace(path)
