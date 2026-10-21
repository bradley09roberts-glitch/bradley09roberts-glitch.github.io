"""Parse a football-data.co.uk season CSV into matches, prices and issues. No database access.

Column key: https://football-data.co.uk/notes.txt (checked 2026-10-19). Prices are parsed for
the top divisions only (DECISIONS.md D-016); second tiers are used for results and shots.
"""

import csv
import io
from dataclasses import dataclass, field
from datetime import date, datetime, time, timedelta
from zoneinfo import ZoneInfo

UK = ZoneInfo("Europe/London")
# fd-2: drop implausible-overround sets; fd-3: exclude bare results; fd-4: keep repeat
# meetings where the division allows them (matches.meeting, D-022).
PARSER_VERSION = "fd-4"

# (bookmaker, pre-closing columns, closing columns); "{s}" is H/D/A. The first column found
# is used. BbAv* is Betbrain's average, football-data's market average before 2019/20.
BOOKS_1X2 = {
    "B365": (["B365{s}"], ["B365C{s}"]),
    "PS": (["PS{s}"], ["PSC{s}"]),
    "BFE": (["BFE{s}"], ["BFEC{s}"]),
    "Avg": (["Avg{s}", "BbAv{s}"], ["AvgC{s}"]),
}
# "{s}" is ">" (over) or "<" (under), line 2.5.
BOOKS_OU25 = {
    "B365": (["B365{s}2.5"], ["B365C{s}2.5"]),
    "PS": (["P{s}2.5"], ["PC{s}2.5"]),
    "BFE": (["BFE{s}2.5"], ["BFEC{s}2.5"]),
    "Avg": (["Avg{s}2.5", "BbAv{s}2.5"], ["AvgC{s}2.5"]),
}
OU_SELECTIONS = {">": "O", "<": "U"}
OVERROUND_RANGE = (0.95, 1.30)

STAT_COLUMNS = {
    "home_shots": "HS",
    "away_shots": "AS",
    "home_sot": "HST",
    "away_sot": "AST",
    "home_corners": "HC",
    "away_corners": "AC",
    "home_fouls": "HF",
    "away_fouls": "AF",
    "home_yellow": "HY",
    "away_yellow": "AY",
    "home_red": "HR",
    "away_red": "AR",
}


@dataclass
class Issue:
    source_row: int  # 1-based data row; 0 = whole file
    severity: str  # error: the row or value was dropped | warning: kept, flagged
    code: str
    detail: str


@dataclass
class ParsedOdds:
    bookmaker: str
    market_key: str
    line: float | None
    selection: str
    phase: str
    price: float
    quote_time_utc: datetime | None
    quote_time_basis: str
    source_column: str


@dataclass
class ParsedMatch:
    source_row: int
    division: str
    match_date: date
    kickoff_local: str | None
    kickoff_utc: datetime | None
    home_name: str
    away_name: str
    home_goals: int
    away_goals: int
    result: str
    ht_home_goals: int | None
    ht_away_goals: int | None
    ht_result: str | None
    referee: str | None
    fd_home_xg: float | None
    fd_away_xg: float | None
    model_exclusion: str | None = None
    # 1st, 2nd, ... time this home team hosts this away team in the season, in file order
    # (D-022). Always 1 where a pairing meets once (max_meetings = 1, e.g. the core ten).
    meeting: int = 1
    stats: dict[str, int | None] = field(default_factory=dict)
    odds: list[ParsedOdds] = field(default_factory=list)


@dataclass
class ParsedFile:
    matches: list[ParsedMatch]
    issues: list[Issue]
    columns: list[str]
    encoding: str


def collection_deadline(match_date: date) -> datetime:
    """When football-data's pre-closing prices are documented as collected (D-012):
    by 17:00 UK on the Friday before a Friday-to-Monday fixture, by 13:00 UK on the Tuesday
    before a Tuesday-to-Thursday fixture. Returned in UTC."""
    wd = match_date.weekday()  # Monday 0 ... Sunday 6
    if wd in (4, 5, 6, 0):
        day, hour = match_date - timedelta(days=(wd - 4) % 7), 17
    else:
        day, hour = match_date - timedelta(days=wd - 1), 13
    return datetime.combine(day, time(hour), tzinfo=UK).astimezone(ZoneInfo("UTC"))


def model_cutoff(match_date: date) -> datetime:
    """Latest information time for a historical early-edition prediction (D-012): 00:00 UK
    on the collection day, so the model never sees a result the prices hadn't."""
    deadline_uk = collection_deadline(match_date).astimezone(UK)
    return datetime.combine(deadline_uk.date(), time(0), tzinfo=UK).astimezone(ZoneInfo("UTC"))


def decode(content: bytes) -> tuple[str, str]:
    try:
        return content.decode("utf-8-sig"), "utf-8"
    except UnicodeDecodeError:
        return content.decode("cp1252"), "cp1252"


def uk_kickoff(match_date: date, hhmm: str) -> tuple[str, datetime]:
    """football-data's "HH:MM" UK local kick-off as (normalised "HH:MM", UTC datetime).
    Raises ValueError for anything else."""
    t = datetime.strptime(hhmm, "%H:%M").time()  # noqa: DTZ007 (UK time-of-day)
    return t.strftime("%H:%M"), datetime.combine(match_date, t, tzinfo=UK).astimezone(
        ZoneInfo("UTC")
    )


def parse_fd_csv(
    content: bytes,
    division: str,
    *,
    with_odds: bool,
    repeats_expected: bool = False,
    max_meetings: int = 1,
) -> ParsedFile:
    """`repeats_expected` is for leagues where a pairing legitimately recurs in a season
    (split formats, three- and four-round formats; D-022). There, a repeat on a different
    date is a further meeting and is kept with `meeting` = 2, 3, ... in file order, up to
    `max_meetings` (reference.py); one beyond that is held back as `repeat_pairing`. The
    same pairing on the date of a kept meeting is a `duplicate_fixture`. Without
    `repeats_expected` (the core ten) any second row of a pairing is a `duplicate_fixture`."""
    text, encoding = decode(content)
    reader = csv.reader(io.StringIO(text))
    header = [h.strip() for h in next(reader)]
    issues: list[Issue] = []
    matches: list[ParsedMatch] = []
    kept: dict[tuple[str, str], list[ParsedMatch]] = {}  # pairing -> its meetings so far

    for n, values in enumerate(reader, start=1):
        if not any(v.strip() for v in values):
            continue
        row = {h: (values[i].strip() if i < len(values) else "") for i, h in enumerate(header)}
        parsed = _parse_row(n, row, division, with_odds, issues)
        if parsed is None:
            continue
        meetings = kept.setdefault((parsed.home_name, parsed.away_name), [])
        if meetings:
            first = meetings[0]
            same_day = next((m for m in meetings if m.match_date == parsed.match_date), None)
            if not repeats_expected or same_day is not None:
                ref = first if same_day is None else same_day
                issues.append(
                    Issue(n, "error", "duplicate_fixture", f"same pairing as row {ref.source_row}")
                )
                continue
            if len(meetings) >= max_meetings:
                issues.append(
                    Issue(
                        n,
                        "error",
                        "repeat_pairing",
                        f"{parsed.home_name} v {parsed.away_name} on {parsed.match_date} "
                        f"repeats row {first.source_row} ({first.match_date}); held back, it "
                        f"would be meeting {len(meetings) + 1} and {division} allows "
                        f"{max_meetings} per season (max_meetings)",
                    )
                )
                continue
        meetings.append(parsed)
        parsed.meeting = len(meetings)
        matches.append(parsed)
    return ParsedFile(matches, issues, header, encoding)


def _parse_row(n, row, division, with_odds, issues) -> ParsedMatch | None:
    if row.get("Div") and row["Div"] != division:
        issues.append(Issue(n, "error", "wrong_division", f"Div={row['Div']!r}"))
        return None
    home, away = row.get("HomeTeam", ""), row.get("AwayTeam", "")
    if not home or not away:
        issues.append(Issue(n, "error", "missing_team", f"{home!r} v {away!r}"))
        return None
    try:
        match_date = _parse_date(row.get("Date", ""))
    except ValueError:
        issues.append(Issue(n, "error", "bad_date", f"Date={row.get('Date')!r}"))
        return None

    fthg, ftag = _int(row.get("FTHG")), _int(row.get("FTAG"))
    if fthg is None or ftag is None:
        issues.append(
            Issue(n, "error", "no_result", f"FTHG={row.get('FTHG')!r} FTAG={row.get('FTAG')!r}")
        )
        return None
    result = "H" if fthg > ftag else "A" if ftag > fthg else "D"
    if row.get("FTR") and row["FTR"] != result:
        issues.append(Issue(n, "error", "result_mismatch", f"FTR={row['FTR']} but {fthg}-{ftag}"))
        return None

    hthg, htag = _int(row.get("HTHG")), _int(row.get("HTAG"))
    ht_result = None
    if hthg is not None and htag is not None:
        if hthg > fthg or htag > ftag:
            issues.append(Issue(n, "error", "ht_exceeds_ft", f"HT {hthg}-{htag}, FT {fthg}-{ftag}"))
            hthg = htag = None
        else:
            ht_result = "H" if hthg > htag else "A" if htag > hthg else "D"
            if row.get("HTR") and row["HTR"] != ht_result:
                issues.append(Issue(n, "warning", "ht_result_mismatch", f"HTR={row['HTR']}"))
    else:
        hthg = htag = None

    kickoff_local, kickoff_utc = None, None
    if row.get("Time"):
        try:
            kickoff_local, kickoff_utc = uk_kickoff(match_date, row["Time"])
        except ValueError:
            issues.append(Issue(n, "warning", "bad_time", f"Time={row['Time']!r}"))

    stats = {}
    for name, col in STAT_COLUMNS.items():
        raw = row.get(col, "")
        val = _int(raw)
        if raw and val is None:
            issues.append(Issue(n, "warning", "bad_stat", f"{col}={raw!r}"))
        stats[name] = val
    if (
        hthg is None
        and all(stats[k] is None for k in ("home_shots", "away_shots"))
        and ("HS" in row and "HTHG" in row)
    ):
        # Every other row has these; a bare score usually means a result awarded off the pitch.
        issues.append(
            Issue(n, "warning", "result_without_stats", f"{fthg}-{ftag} with no HT score or stats")
        )
        exclusion = "result_without_stats"
    else:
        exclusion = None
    for shots, sot in (("home_shots", "home_sot"), ("away_shots", "away_sot")):
        if stats[shots] is not None and stats[sot] is not None and stats[sot] > stats[shots]:
            issues.append(Issue(n, "warning", "sot_exceeds_shots", f"{sot} > {shots}"))

    m = ParsedMatch(
        source_row=n,
        division=division,
        match_date=match_date,
        kickoff_local=kickoff_local,
        kickoff_utc=kickoff_utc,
        home_name=home,
        away_name=away,
        home_goals=fthg,
        away_goals=ftag,
        result=result,
        ht_home_goals=hthg,
        ht_away_goals=htag,
        ht_result=ht_result,
        referee=row.get("Referee") or None,
        fd_home_xg=_float(row.get("HxG")),
        fd_away_xg=_float(row.get("AxG")),
        model_exclusion=exclusion,
        stats=stats,
    )
    if with_odds:
        m.odds = _parse_odds(n, row, m, issues)
    return m


def _parse_odds(n, row, m: ParsedMatch, issues) -> list[ParsedOdds]:
    pre_time = collection_deadline(m.match_date)
    if m.kickoff_utc is not None and m.kickoff_utc <= pre_time:
        issues.append(
            Issue(
                n, "warning", "kickoff_before_collection", "pre-closing time is not before kickoff"
            )
        )
    if m.kickoff_utc is not None:
        close_time, close_basis = m.kickoff_utc, "fd_closing_at_kickoff"
    else:
        close_time, close_basis = None, "fd_closing_kickoff_unknown"

    out: list[ParsedOdds] = []
    groups = [("1X2", None, BOOKS_1X2, {"H": "H", "D": "D", "A": "A"})]
    groups.append(("OU_2.5", 2.5, BOOKS_OU25, OU_SELECTIONS))
    for market_key, line, books, selections in groups:
        for book, (pre_cols, close_cols) in books.items():
            for phase, templates, qtime, basis in (
                ("pre_closing", pre_cols, pre_time, "fd_collection_deadline"),
                ("closing", close_cols, close_time, close_basis),
            ):
                found = []
                for token, sel in selections.items():
                    col = next(
                        (t.format(s=token) for t in templates if t.format(s=token) in row), None
                    )
                    if col is None or not row[col]:
                        continue
                    price = _float(row[col])
                    if price is None or price <= 1.0:
                        issues.append(Issue(n, "error", "invalid_price", f"{col}={row[col]!r}"))
                        continue
                    found.append(
                        ParsedOdds(book, market_key, line, sel, phase, price, qtime, basis, col)
                    )
                if len(found) == len(selections):
                    overround = sum(1 / o.price for o in found)
                    lo, hi = OVERROUND_RANGE
                    if not lo <= overround <= hi:
                        # A whole set this far from a real book is a data fault: drop it.
                        issues.append(
                            Issue(
                                n,
                                "error",
                                "implausible_overround",
                                f"{book} {market_key} {phase} {overround:.3f} (set dropped)",
                            )
                        )
                        continue
                out.extend(found)
    return out


def _parse_date(s: str) -> date:
    for fmt in ("%d/%m/%Y", "%d/%m/%y"):
        try:
            return datetime.strptime(s, fmt).date()  # noqa: DTZ007 (date only)
        except ValueError:
            pass
    raise ValueError(s)


def _float(s: str | None) -> float | None:
    if s is None or s == "":
        return None
    try:
        return float(s)
    except ValueError:
        return None


def _int(s: str | None) -> int | None:
    f = _float(s)
    if f is None or f < 0 or f != int(f):
        return None
    return int(f)
