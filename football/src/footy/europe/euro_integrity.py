"""Integrity checks on `euro_matches`, run against the database as it stands.

These complement the loaders' own checks (which run per source file before anything is
stored) by looking at the whole table at once, across sources, competitions and seasons, and
against the domestic `matches` table:

- duplicates: the same pairing twice on one date, twice in one stage, or again within a few
  days in another competition;
- impossible values: a team playing itself, negative or missing scores, scores on unplayed
  fixtures, played matches in the future, stale fixtures in the past;
- calendar: a team playing twice on one day (UEFA or domestic), a kick-off whose UTC date is
  not the match date, kick-offs outside the usual hours;
- format: extra time or penalties outside knockout ties, legs not reversed or out of order,
  ties that went to extra time although not level after 90 minutes (or the reverse), league
  phases and groups with the wrong number of teams or matches.

Nothing is changed: the result is a list of findings for a person to look at.

    uv run python -m footy.europe.euro_integrity [--markdown PATH]
"""

import argparse
import re
from collections import Counter, defaultdict
from collections.abc import Iterable, Sequence
from dataclasses import dataclass, field
from datetime import UTC, date, datetime, timedelta
from pathlib import Path

from sqlalchemy import select
from sqlalchemy.orm import Session

from footy.models import EuroMatch, Match

ERROR = "error"
WARNING = "warning"
INFO = "info"

PLAYED = {"played", "awarded"}
UNPLAYED = {"scheduled", "postponed", "cancelled"}
# League-phase matches per team (2024/25 onwards) and group size (until 2023/24).
LEAGUE_PHASE_MATCHES = {"UCL": 8, "UEL": 8, "UECL": 6}
LEAGUE_PHASE_TEAMS = 36
GROUP_TEAMS = 4
# Usual kick-off window for UEFA club matches, in UTC hours (inclusive).
KICKOFF_HOURS = range(10, 22)
IMPLAUSIBLE_GOALS = 10
REPEAT_WINDOW_DAYS = 3

_LEG = re.compile(r"^(?P<base>.+) leg (?P<leg>[12])$")
_MATCHDAY = re.compile(r"^(League|Group) MD\d+$")


@dataclass(frozen=True)
class EuroRec:
    """The columns of one `euro_matches` row that the checks need."""

    id: int
    competition: str
    season: str
    stage: str
    match_date: date
    kickoff_utc: datetime | None
    home: str
    away: str
    home_goals: int | None
    away_goals: int | None
    extra_time: bool
    penalties: bool
    status: str


@dataclass(frozen=True)
class DomesticRec:
    id: int
    division: str
    match_date: date
    home: str
    away: str


@dataclass(frozen=True)
class Finding:
    check: str
    severity: str
    euro_ids: tuple[int, ...]
    detail: str


@dataclass
class IntegrityReport:
    rows: int
    domestic_rows: int
    findings: list[Finding] = field(default_factory=list)

    def counts(self) -> Counter:
        return Counter((f.severity, f.check) for f in self.findings)


def is_knockout(stage: str) -> bool:
    return not stage.startswith(("Group", "League"))


def split_leg(stage: str) -> tuple[str, int | None]:
    """("Round of 16", 1) for "Round of 16 leg 1"; (stage, None) for a single match."""
    m = _LEG.match(stage)
    return (m.group("base"), int(m.group("leg"))) if m else (stage, None)


def away_goals_rule(season: str) -> bool:
    """The away goals rule applied until 2020/21 and was abolished from 2021/22."""
    return int(season[:2]) < 21


def _pair(r: EuroRec) -> frozenset[str]:
    return frozenset((r.home, r.away))


def _desc(r: EuroRec) -> str:
    score = "" if r.home_goals is None else f" {r.home_goals}-{r.away_goals}"
    return f"#{r.id} {r.competition} {r.season} {r.stage} {r.match_date} {r.home} v {r.away}{score}"


# --- duplicates ---------------------------------------------------------------------------


def check_duplicates(rows: Sequence[EuroRec]) -> list[Finding]:
    out: list[Finding] = []
    by_key: dict[tuple, list[EuroRec]] = defaultdict(list)
    by_day: dict[tuple, list[EuroRec]] = defaultdict(list)
    by_pair: dict[frozenset, list[EuroRec]] = defaultdict(list)
    for r in rows:
        by_key[(r.competition, r.season, r.match_date, r.home, r.away)].append(r)
        by_day[(r.match_date, _pair(r))].append(r)
        by_pair[_pair(r)].append(r)
    for group in by_key.values():
        if len(group) > 1:
            out.append(_finding("duplicate_key", ERROR, group, "same competition, date and teams"))
    for group in by_day.values():
        if len(group) > 1 and len({(r.competition, r.home) for r in group}) > 1:
            out.append(_finding("duplicate_pairing_same_day", ERROR, group, "same teams, same day"))
    for group in by_pair.values():
        group = sorted(group, key=lambda r: r.match_date)
        for a, b in zip(group, group[1:], strict=False):
            gap = (b.match_date - a.match_date).days
            if 0 < gap <= REPEAT_WINDOW_DAYS:
                out.append(
                    _finding("repeat_pairing_near_date", WARNING, [a, b], f"{gap} days apart")
                )
    out += _repeat_in_stage(rows)
    return out


def _repeat_in_stage(rows: Sequence[EuroRec]) -> list[Finding]:
    """A pairing may meet once per league phase, twice per group (home and away, reversed)
    and once per leg of a knockout round."""
    out: list[Finding] = []
    league: dict[tuple, list[EuroRec]] = defaultdict(list)
    ordered: dict[tuple, list[EuroRec]] = defaultdict(list)
    for r in rows:
        if r.stage.startswith("League"):
            league[(r.competition, r.season, _pair(r))].append(r)
        elif r.stage.startswith("Group"):
            # Groups named by matchday ("Group MD3") can't be told apart; the per-matchday
            # check covers them.
            if not _MATCHDAY.match(r.stage):
                ordered[(r.competition, r.season, r.stage, r.home, r.away)].append(r)
        else:
            base, _ = split_leg(r.stage)
            ordered[(r.competition, r.season, base, r.home, r.away)].append(r)
    for group in league.values():
        if len(group) > 1:
            out.append(_finding("repeat_pairing_in_stage", ERROR, group, "league phase"))
    for group in ordered.values():
        if len(group) > 1:
            out.append(_finding("repeat_pairing_in_stage", ERROR, group, group[0].stage))
    return out


# --- values -------------------------------------------------------------------------------


def check_values(rows: Sequence[EuroRec], now: datetime) -> list[Finding]:
    out: list[Finding] = []
    for r in rows:
        goals = (r.home_goals, r.away_goals)
        if r.home == r.away:
            out.append(_finding("same_team", ERROR, [r], "home and away are the same team"))
        if r.status in PLAYED and None in goals:
            out.append(_finding("score_missing", ERROR, [r], r.status))
        if r.status in UNPLAYED and goals != (None, None):
            out.append(_finding("score_on_unplayed", ERROR, [r], r.status))
        if r.status not in PLAYED | UNPLAYED:
            out.append(_finding("unknown_status", ERROR, [r], r.status))
        if None not in goals:
            if min(goals) < 0:
                out.append(_finding("negative_score", ERROR, [r], f"{goals}"))
            elif max(goals) >= IMPLAUSIBLE_GOALS:
                out.append(_finding("implausible_score", WARNING, [r], f"{goals}"))
        start = r.kickoff_utc or datetime(*r.match_date.timetuple()[:3], 23, 59, tzinfo=UTC)
        if r.status == "played" and start > now:
            out.append(_finding("played_in_future", ERROR, [r], f"starts {start:%Y-%m-%d %H:%M}"))
        if r.status == "scheduled" and start + timedelta(hours=3) < now:
            out.append(_finding("scheduled_in_past", WARNING, [r], "no result loaded yet"))
    return out


# --- calendar -----------------------------------------------------------------------------


def check_calendar(rows: Sequence[EuroRec], domestic: Sequence[DomesticRec]) -> list[Finding]:
    out: list[Finding] = []
    for r in rows:
        if r.kickoff_utc is None:
            continue
        if r.kickoff_utc.date() != r.match_date:
            out.append(
                _finding(
                    "kickoff_date_mismatch", WARNING, [r], f"kick-off {r.kickoff_utc:%F %H:%M}Z"
                )
            )
        if r.kickoff_utc.hour not in KICKOFF_HOURS or r.kickoff_utc.minute % 5:
            out.append(
                _finding("kickoff_hour_unusual", WARNING, [r], f"kick-off {r.kickoff_utc:%H:%M}Z")
            )
    out += _check_rest(rows, domestic)
    return out


@dataclass(frozen=True)
class _Appearance:
    day: date
    label: str
    euro_id: int | None


def _check_rest(rows: Sequence[EuroRec], domestic: Sequence[DomesticRec]) -> list[Finding]:
    """A team can't play twice on one day; two days running is flagged for a look. Only
    clashes that involve at least one UEFA match are reported (cancelled ones don't count)."""
    seen: dict[str, list[_Appearance]] = defaultdict(list)
    for r in rows:
        if r.status != "cancelled":
            for team in (r.home, r.away):
                seen[team].append(_Appearance(r.match_date, f"#{r.id} {r.competition}", r.id))
    for d in domestic:
        for team in (d.home, d.away):
            seen[team].append(_Appearance(d.match_date, f"domestic #{d.id} {d.division}", None))
    out: list[Finding] = []
    for team, apps in sorted(seen.items()):
        apps.sort(key=lambda a: (a.day, a.label))
        for a, b in zip(apps, apps[1:], strict=False):
            if a.euro_id is None and b.euro_id is None:
                continue
            gap = (b.day - a.day).days
            if gap > 1:
                continue
            check, severity = (
                ("team_twice_same_day", ERROR) if gap == 0 else ("team_on_consecutive_days", INFO)
            )
            ids = tuple(i for i in (a.euro_id, b.euro_id) if i is not None)
            detail = f"{team}: {a.day} {a.label}; {b.day} {b.label}"
            out.append(Finding(check, severity, ids, detail))
    return out


# --- format -------------------------------------------------------------------------------


def check_format(rows: Sequence[EuroRec]) -> list[Finding]:
    out: list[Finding] = []
    for r in rows:
        base, leg = split_leg(r.stage)
        if (r.extra_time or r.penalties) and (not is_knockout(r.stage) or leg == 1):
            out.append(_finding("extra_time_outside_knockout", ERROR, [r], r.stage))
        if r.penalties and not r.extra_time:
            out.append(_finding("penalties_without_extra_time", ERROR, [r], r.stage))
    out += _check_ties(rows)
    out += _check_league_phases(rows)
    out += _check_groups(rows)
    return out


def _check_ties(rows: Sequence[EuroRec]) -> list[Finding]:
    out: list[Finding] = []
    ties: dict[tuple, dict[int, list[EuroRec]]] = defaultdict(lambda: defaultdict(list))
    for r in rows:
        if not is_knockout(r.stage):
            continue
        base, leg = split_leg(r.stage)
        ties[(r.competition, r.season, base, _pair(r))][leg or 0].append(r)
    for (_, season, base, _), legs in ties.items():
        if 0 in legs:  # single match: final or single-leg round
            for r in legs[0]:
                if r.status == "played" and r.home_goals is not None:
                    out += _decided(
                        [r], r.home_goals == r.away_goals, r.extra_time, f"{base} (single match)"
                    )
            continue
        first, second = legs.get(1, []), legs.get(2, [])
        if len(first) != 1 or len(second) != 1:
            group = first + second
            if not any(r.status in UNPLAYED for r in group):
                out.append(
                    _finding("incomplete_tie", WARNING, group, f"{base}: legs {sorted(legs)}")
                )
            continue
        a, b = first[0], second[0]
        if (a.home, a.away) != (b.away, b.home):
            out.append(_finding("legs_not_reversed", ERROR, [a, b], base))
        if b.match_date <= a.match_date:
            out.append(_finding("legs_out_of_order", ERROR, [a, b], base))
        if a.status != "played" or b.status != "played" or None in (a.home_goals, b.home_goals):
            continue
        # Goals of the second leg's home team (x) and away team (y) over both legs.
        x = a.away_goals + b.home_goals
        y = a.home_goals + b.away_goals
        level = x == y and (not away_goals_rule(season) or a.away_goals == b.away_goals)
        out += _decided([a, b], level, b.extra_time, f"{base}: aggregate {x}-{y} after 90")
    return out


def _decided(tie: list[EuroRec], level: bool, extra_time: bool, detail: str) -> list[Finding]:
    """A tie goes to extra time exactly when it is level after 90 minutes."""
    if extra_time and not level:
        return [_finding("extra_time_not_level", ERROR, tie, detail)]
    if level and not extra_time:
        return [_finding("level_without_extra_time", ERROR, tie, detail)]
    return []


def _check_league_phases(rows: Sequence[EuroRec]) -> list[Finding]:
    out: list[Finding] = []
    phases: dict[tuple[str, str], list[EuroRec]] = defaultdict(list)
    for r in rows:
        if r.stage.startswith("League") and r.status != "cancelled":
            phases[(r.competition, r.season)].append(r)
    for (comp, season), group in sorted(phases.items()):
        per_team = Counter(t for r in group for t in (r.home, r.away))
        home = Counter(r.home for r in group)
        want = LEAGUE_PHASE_MATCHES.get(comp)
        if len(per_team) != LEAGUE_PHASE_TEAMS:
            out.append(
                _finding("league_phase_teams", ERROR, [], f"{comp} {season}: {len(per_team)} teams")
            )
        wrong = {t: n for t, n in per_team.items() if want and n != want}
        if wrong:
            out.append(
                _finding(
                    "league_phase_matches", ERROR, [], f"{comp} {season}: {sorted(wrong.items())}"
                )
            )
        unbalanced = {t: home[t] for t in per_team if want and home[t] != want // 2}
        if unbalanced:
            out.append(
                _finding(
                    "league_phase_home_away",
                    WARNING,
                    [],
                    f"{comp} {season}: home matches {sorted(unbalanced.items())}",
                )
            )
    out += _check_matchdays(rows)
    return out


def _check_matchdays(rows: Sequence[EuroRec]) -> list[Finding]:
    """Each team plays exactly once per matchday (stages named "League MD3", "Group MD3")."""
    out: list[Finding] = []
    days: dict[tuple, list[EuroRec]] = defaultdict(list)
    for r in rows:
        if _MATCHDAY.match(r.stage) and r.status != "cancelled":
            for team in (r.home, r.away):
                days[(r.competition, r.season, r.stage, team)].append(r)
    for (comp, season, stage, team), group in sorted(days.items()):
        if len(group) > 1:
            out.append(
                _finding("matchday_repeat", ERROR, group, f"{comp} {season} {stage}: {team}")
            )
    return out


def _check_groups(rows: Sequence[EuroRec]) -> list[Finding]:
    """Lettered groups (until 2023/24): four teams, each meeting the other three home and away."""
    out: list[Finding] = []
    groups: dict[tuple, list[EuroRec]] = defaultdict(list)
    for r in rows:
        if r.stage.startswith("Group") and not _MATCHDAY.match(r.stage):
            groups[(r.competition, r.season, r.stage)].append(r)
    for (comp, season, stage), group in sorted(groups.items()):
        teams = {t for r in group for t in (r.home, r.away)}
        if len(teams) != GROUP_TEAMS or len(group) != GROUP_TEAMS * (GROUP_TEAMS - 1):
            out.append(
                _finding(
                    "group_shape",
                    ERROR,
                    group,
                    f"{comp} {season} {stage}: {len(teams)} teams, {len(group)} matches",
                )
            )
    return out


# --- running ------------------------------------------------------------------------------


def _finding(check: str, severity: str, rows: Iterable[EuroRec], detail: str) -> Finding:
    rows = list(rows)
    text = detail if not rows else f"{detail}: " + "; ".join(_desc(r) for r in rows)
    return Finding(check, severity, tuple(r.id for r in rows), text)


def run_checks(
    rows: Sequence[EuroRec], domestic: Sequence[DomesticRec] = (), now: datetime | None = None
) -> list[Finding]:
    now = now or datetime.now(UTC)
    return (
        check_duplicates(rows)
        + check_values(rows, now)
        + check_calendar(rows, domestic)
        + check_format(rows)
    )


def load(session: Session) -> tuple[list[EuroRec], list[DomesticRec]]:
    rows = [
        EuroRec(
            id=m.id,
            competition=m.competition,
            season=m.season,
            stage=m.stage,
            match_date=m.match_date,
            kickoff_utc=m.kickoff_utc,
            home=m.home_team,
            away=m.away_team,
            home_goals=m.home_goals,
            away_goals=m.away_goals,
            extra_time=bool(m.extra_time),
            penalties=bool(m.penalties),
            status=m.status,
        )
        for m in session.scalars(select(EuroMatch).order_by(EuroMatch.id))
    ]
    domestic = [
        DomesticRec(m.id, m.division, m.match_date, m.home_team, m.away_team)
        for m in session.scalars(select(Match).order_by(Match.id))
    ]
    return rows, domestic


def check_database(session: Session, now: datetime | None = None) -> IntegrityReport:
    rows, domestic = load(session)
    return IntegrityReport(len(rows), len(domestic), run_checks(rows, domestic, now))


def report_markdown(report: IntegrityReport) -> str:
    lines = [
        f"`euro_matches`: {report.rows} rows; domestic `matches`: {report.domestic_rows} rows.",
        "",
        "| Severity | Check | Findings |",
        "| --- | --- | ---: |",
    ]
    for (severity, check), n in sorted(report.counts().items()):
        lines.append(f"| {severity} | `{check}` | {n} |")
    if not report.findings:
        lines.append("| | (none) | 0 |")
    lines.append("")
    for f in report.findings:
        lines.append(f"- **{f.severity}** `{f.check}`: {f.detail}")
    return "\n".join(lines) + "\n"


def main(argv: Sequence[str] | None = None) -> int:
    from footy.db import get_engine

    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--markdown", type=Path, help="also write the report to this file")
    args = parser.parse_args(argv)
    with Session(get_engine()) as session:
        report = check_database(session)
    text = report_markdown(report)
    print(text)
    if args.markdown:
        args.markdown.write_text(text, encoding="utf-8")
    return 1 if any(f.severity == ERROR for f in report.findings) else 0


if __name__ == "__main__":
    raise SystemExit(main())
