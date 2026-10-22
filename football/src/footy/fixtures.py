"""Upcoming domestic fixtures from openfootball's published season schedules (CC0).

openfootball's league files (football.txt format) list every fixture of the season with local
kick-off times, and the scores of matches already played. Team names differ from
football-data's ("Nottingham Forest FC" vs "Nott'm Forest"), so names are matched to this
season's teams in our database by an optimal one-to-one assignment on name similarity, and
the mapping is then VERIFIED: every already-played fixture in the schedule must have the same
score in our football-data import. A mapping that fails verification is refused.

openfootball's dates can be placeholders: until the kick-off times are announced it may list
a whole round on one day without times (La Liga matchdays 10 and 11 of 2026/27 on their
Sundays). Where football-data's `fixtures.csv` (the latest stored copy, D-024) lists a pairing
of a single-meeting league, its date and UK kick-off time are the authority for that
fixture (`with_fd_dates`): they come from the bookmakers' own fixture lists and are refreshed
every Tuesday and Friday. The one exception keeps the pre-match deadline safe: a firm
schedule kick-off earlier than the file's stands (the stored file can be days old).
"""

import difflib
import logging
import re
import unicodedata
from collections import Counter
from dataclasses import dataclass, replace
from datetime import date, datetime, timedelta
from pathlib import Path
from zoneinfo import ZoneInfo

import numpy as np
from scipy.optimize import linear_sum_assignment
from sqlalchemy import Engine, text

log = logging.getLogger(__name__)

OPENFOOTBALL = Path("/home/user/openfootball")
SCHEDULES = {
    "E0": ("england/2026-27/1-premierleague.txt", "Europe/London"),
    "SP1": ("espana/2026-27/1-liga.txt", "Europe/Madrid"),
    "D1": ("deutschland/2026-27/1-bundesliga.txt", "Europe/Berlin"),
    "I1": ("italy/2026-27/1-seriea.txt", "Europe/Rome"),
    "F1": ("france/france/2026-27_fr1.txt", "Europe/Paris"),
    "E1": ("england/2026-27/2-championship.txt", "Europe/London"),
}
MONTHS = {
    m: i
    for i, m in enumerate(
        ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"], 1
    )
}
DATE_RE = re.compile(
    r"^\s*(?:Mon|Tue|Wed|Thu|Fri|Sat|Sun)\s+([A-Z][a-z]{2})\s+(\d{1,2})(?:\s+(\d{4}))?\s*$"
)
MATCH_RE = re.compile(
    r"^\s*(?:(\d{1,2}):(\d{2})\s+)?(.+?)\s+v\s+(.+?)(?:\s+(\d+)-(\d+)(?:\s.*)?)?\s*$"
)
STOP = {
    "fc",
    "cf",
    "afc",
    "ac",
    "as",
    "ss",
    "ssc",
    "us",
    "sc",
    "club",
    "de",
    "calcio",
    "1907",
    "1909",
    "1913",
    "cfc",
    "acf",
    "bc",
    "rc",
    "rcd",
    "ud",
    "cd",
    "sd",
    "ca",
    "vfb",
    "vfl",
    "tsg",
    "sv",
    "fsv",
    "1.",
    "04",
    "05",
    "1846",
    "1899",
    "the",
    "olympique",
    "stade",
    "hove",
    "albion",
    "united",
    "city",
    "town",
    "hotspur",
    "wanderers",
}
ALIASES = {
    "nott'm": "nottingham",
    "nott": "nottingham",
    "man": "manchester",
    "wolves": "wolverhampton",
    "m'gladbach": "monchengladbach",
    "ein": "eintracht",
    "ath": "atletico",
    "sociedad": "real sociedad",
    "espanol": "espanyol",
    "vallecano": "rayo",
    "inter": "internazionale",
    "paris": "paris saint germain",
    "sg": "saint germain",
    "st": "saint",
    "betis": "real betis",
    "celta": "celta vigo",
    "leverkusen": "bayer leverkusen",
    "bayern": "bayern munchen",
    "munich": "munchen",
    "dortmund": "borussia dortmund",
    "hoffenheim": "1899 hoffenheim",
    "verona": "hellas verona",
    "sheffield": "sheffield",
    "qpr": "queens park rangers",
    "west": "west",
}


@dataclass(frozen=True)
class ScheduledMatch:
    match_date: date  # local date of the venue
    kickoff_utc: datetime | None
    home_name: str
    away_name: str
    home_goals: int | None
    away_goals: int | None
    line_no: int
    # where the date and kick-off come from when not from this schedule line (football-data's
    # fixtures.csv: `with_fd_dates`)
    date_source: str | None = None


def parse_schedule(text_: str, tz: str) -> list[ScheduledMatch]:
    zone = ZoneInfo(tz)
    year = None
    header = re.search(r"#\s*Date\s+\w{3}\s+([A-Z][a-z]{2})\s+\d{1,2}\s+(\d{4})", text_)
    if header:
        year, start_month = int(header.group(2)), MONTHS[header.group(1)]
    current: date | None = None
    last_time: tuple[int, int] | None = None
    out = []
    for n, line in enumerate(text_.splitlines(), 1):
        if not line.strip() or line.lstrip().startswith(("#", "=", "▪")):
            continue
        m = DATE_RE.match(line)
        if m:
            mon, day = MONTHS[m.group(1)], int(m.group(2))
            if m.group(3):
                year = int(m.group(3))
            y = year if mon >= start_month else year + 1 if not m.group(3) else year
            current = date(y, mon, day)
            last_time = None
            continue
        m = MATCH_RE.match(line)
        if not m or current is None or " v " not in line:
            continue
        if m.group(1):
            last_time = (int(m.group(1)), int(m.group(2)))
        ko = None
        if last_time:
            ko = datetime(
                current.year, current.month, current.day, *last_time, tzinfo=zone
            ).astimezone(ZoneInfo("UTC"))
        hg = int(m.group(5)) if m.group(5) else None
        ag = int(m.group(6)) if m.group(6) else None
        out.append(ScheduledMatch(current, ko, m.group(3).strip(), m.group(4).strip(), hg, ag, n))
    return out


def _tokens(name: str) -> set[str]:
    s = unicodedata.normalize("NFKD", name).encode("ascii", "ignore").decode().lower()
    s = s.replace("-", " ").replace("&", " ")
    words = []
    for w in s.split():
        w = ALIASES.get(w, w)
        words.extend(w.split())
    return {w for w in words if w not in STOP} or set(words)


def _similarity(a: str, b: str) -> float:
    ta, tb = _tokens(a), _tokens(b)
    jac = len(ta & tb) / max(len(ta | tb), 1)
    seq = difflib.SequenceMatcher(None, " ".join(sorted(ta)), " ".join(sorted(tb))).ratio()
    return 0.6 * jac + 0.4 * seq


def map_teams(engine: Engine, division: str, season: str, schedule: list[ScheduledMatch]):
    """One-to-one mapping schedule name -> team_key, verified against played results."""
    with engine.connect() as conn:
        rows = conn.execute(
            text(
                "SELECT m.home_team, m.away_team, m.home_goals, m.away_goals, th.name, ta.name "
                "FROM matches m JOIN teams th ON th.team_key = m.home_team "
                "JOIN teams ta ON ta.team_key = m.away_team "
                "WHERE m.division = :d AND m.season = :s ORDER BY m.meeting"
            ),
            {"d": division, "s": season},
        ).all()
    ours = {}
    for h, a, _, _, hn, an in rows:
        ours[h], ours[a] = hn, an
    theirs = sorted({x.home_name for x in schedule} | {x.away_name for x in schedule})
    keys = sorted(ours)
    if len(theirs) != len(keys):
        raise ValueError(f"{division}: {len(theirs)} schedule teams vs {len(keys)} in our data")
    sim = np.array([[_similarity(t, ours[k]) for k in keys] for t in theirs])
    r, c = linear_sum_assignment(-sim)
    mapping = {theirs[i]: keys[j] for i, j in zip(r, c, strict=True)}
    # Scores of each pairing's meetings, in `meeting` order (one meeting in the core ten). The
    # schedule's k-th played fixture of a pairing is checked against meeting k; with a single
    # stored meeting every scheduled result of the pairing is checked against it, as before.
    played: dict[tuple[str, str], list[tuple[int, int]]] = {}
    for h, a, hg, ag, _, _ in rows:
        played.setdefault((h, a), []).append((hg, ag))
    nth: Counter = Counter()
    checked, mismatches = 0, []
    for x in schedule:
        if x.home_goals is None:
            continue
        pair = (mapping[x.home_name], mapping[x.away_name])
        if pair in played:
            scores = played[pair]
            stored = scores[min(nth[pair], len(scores) - 1)]
            nth[pair] += 1
            checked += 1
            if stored != (x.home_goals, x.away_goals):
                mismatches.append((x.home_name, x.away_name, (x.home_goals, x.away_goals), stored))
    if checked < len(keys) or mismatches:
        raise ValueError(
            f"{division}: mapping not verified (checked {checked}, mismatches {mismatches[:5]})"
        )
    return mapping, {
        "checked_results": checked,
        "low_similarity": sorted(
            (float(sim[i, j]), theirs[i], ours[keys[j]]) for i, j in zip(r, c, strict=True)
        )[:3],
    }


def fd_dates(snapshot, division: str) -> dict[tuple[str, str], tuple[date, datetime | None]]:
    """(home key, away key) -> (date, kick-off in UTC or None) of `division`'s fixtures in a
    football-data fixtures.csv snapshot (`footy.ingest.fd_fixtures.latest_snapshot`), whose
    `kickoff_local` is football-data's UK time. A pairing listed twice with different dates
    is left out (no authority)."""
    from footy.ingest.fd_parse import uk_kickoff

    if snapshot is None or len(snapshot) == 0:
        return {}
    rows = snapshot[snapshot["division"] == division].drop_duplicates(
        ["date", "kickoff_local", "home_team", "away_team"]
    )
    out: dict[tuple[str, str], tuple[date, datetime | None]] = {}
    clash: set[tuple[str, str]] = set()
    for r in rows.itertuples(index=False):
        day = date.fromisoformat(str(r.date)[:10])
        ko = None
        if isinstance(r.kickoff_local, str) and r.kickoff_local.strip():
            try:
                ko = uk_kickoff(day, r.kickoff_local.strip())[1]
            except ValueError:
                ko = None
        pair = (r.home_team, r.away_team)
        if pair in out and out[pair] != (day, ko):
            clash.add(pair)
        out[pair] = (day, ko)
    return {k: v for k, v in out.items() if k not in clash}


def with_fd_dates(
    fixtures: list[tuple[ScheduledMatch, str, str]],
    division: str,
    snapshot,
    start: date,
) -> tuple[list[tuple[ScheduledMatch, str, str]], list[dict]]:
    """The schedule's unplayed fixtures with football-data's date and kick-off wherever its
    fixtures.csv lists the pairing on or after `start` (stale rows of an older file are
    ignored), in a league where a pairing meets once a season (elsewhere a listed pairing
    can't be tied to one meeting, so nothing changes). Where football-data gives no time and
    the dates agree, the schedule's time is kept.

    The pre-match deadline is never later than a kick-off either source gives: where the
    schedule has a firm kick-off (a time, on or after `start`) earlier than football-data's
    deadline for the fixture (its kick-off, or the round start where it has no time), the
    schedule's date and kick-off stand (the stored fixtures.csv can be days old, so a game
    brought forward since is still listed at its old time). Returns the fixtures and what
    changed; a disagreement settled for the schedule is listed with `kept`."""
    from footy.ingest.fd_parse import model_cutoff
    from footy.publish import single_meeting

    if not single_meeting(division):
        return fixtures, []
    fd = fd_dates(snapshot, division)
    if not fd:
        return fixtures, []
    attrs = getattr(snapshot, "attrs", {}) or {}
    source = "football-data fixtures.csv" + (
        f" (raw file {attrs['raw_file_id']}, retrieved {attrs.get('retrieved_at')})"
        if attrs.get("raw_file_id") is not None
        else ""
    )
    out, changed = [], []
    for x, h, a in fixtures:
        got = fd.get((h, a))
        if got is None or got[0] < start:
            out.append((x, h, a))
            continue
        day, ko = got
        if ko is None and day == x.match_date:
            ko = x.kickoff_utc
        if (day, ko) != (x.match_date, x.kickoff_utc):
            entry = {
                "fixture": f"{h} v {a}",
                "openfootball": [str(x.match_date), _iso(x.kickoff_utc)],
                "football_data": [str(day), _iso(ko)],
            }
            fd_deadline = ko if ko is not None else model_cutoff(day)
            if x.kickoff_utc is not None and x.match_date >= start and x.kickoff_utc < fd_deadline:
                changed.append(entry | {"kept": "openfootball: its kick-off is earlier"})
                out.append((x, h, a))
                continue
            changed.append(entry)
            x = replace(x, match_date=day, kickoff_utc=ko, date_source=source)
        out.append((x, h, a))
    return out, changed


def _iso(t: datetime | None) -> str | None:
    return None if t is None else t.isoformat()


_LATEST = object()


def _latest_fd_snapshot(engine: Engine):
    """The latest stored fixtures.csv as parsed rows, or None (never fetched, or unreadable:
    the schedule then stands as it is, and its undated fixtures keep the round-start
    deadline of `publish.pre_match_deadline`)."""
    from footy.ingest.fd_fixtures import latest_snapshot

    try:
        return latest_snapshot(engine)
    except Exception as exc:  # the schedule must still work without it
        log.warning("fixtures.csv dates not used: %s", exc)
        return None


def upcoming(
    engine: Engine,
    division: str,
    season: str,
    start: date,
    days: int = 7,
    root: Path = OPENFOOTBALL,
    fd_snapshot=_LATEST,
):
    """The schedule's unplayed fixtures from `start` to `days` later, with their team keys,
    and the mapping check. Dates and kick-offs come from football-data's latest fixtures.csv
    where it lists the pairing (`with_fd_dates`; `fd_snapshot` overrides which copy, None for
    none); the check's `fd_dates` lists every fixture it re-dated."""
    rel, tz = SCHEDULES[division]
    sched = parse_schedule((root / rel).read_text(encoding="utf-8"), tz)
    mapping, check = map_teams(engine, division, season, sched)
    end = start + timedelta(days=days)
    unplayed = [
        (x, mapping[x.home_name], mapping[x.away_name]) for x in sched if x.home_goals is None
    ]
    snapshot = _latest_fd_snapshot(engine) if fd_snapshot is _LATEST else fd_snapshot
    unplayed, changed = with_fd_dates(unplayed, division, snapshot, start)
    check = {**check, "fd_dates": changed}
    return [(x, h, a) for x, h, a in unplayed if start <= x.match_date <= end], check
