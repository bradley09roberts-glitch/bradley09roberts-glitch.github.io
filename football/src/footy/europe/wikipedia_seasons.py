"""UEFA matches that openfootball lacks, from stored English Wikipedia pages (D-023).

openfootball has no Europa League or Conference League main-tournament files for 2025/26 (it
has their qualifying rounds). The league-phase and knockout-phase articles (also cross-check
pages, `footy.europe.of_crosscheck.CHECK_PAGES`) and the two final articles fill that gap.

Rules, as for 2026/27 (docs/europe/uefa-2627.md):
- kick-offs are CET/CEST as each page states (a page that doesn't say so is refused);
- the stored score is regulation time: after extra time it is rebuilt from the goal minutes,
  and left empty (`model_exclusion = "no_regulation_score"`) when the goal list doesn't add
  up; extra time and penalties are flags only;
- neutral when the stadium line names another country than the home club's (Welsh clubs at
  grounds just over the border excepted), and for finals;
- legs are numbered by date within a round; a tie that went to extra time or penalties when
  it shouldn't have (or didn't when it should) is kept with `model_exclusion =
  "tie_inconsistent"`; league phases must have 36 teams with the right number of matches.

Team names resolve through reference/team_aliases.csv (source "wikipedia", "Name (ISO)").
"""

import json
import re
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from datetime import date, datetime

from sqlalchemy.orm import Session

from footy.europe.of_crosscheck import WikiBox, times_are_cet, venue_abroad, wiki_boxes
from footy.europe.wiki_fetch import WikiPage
from footy.ingest.raw_store import latest_revision, read_raw

SOURCE = "wikipedia"
LEAGUE_MATCHES = {"UEL": (8, 144), "UECL": (6, 108)}  # per team, in all


@dataclass(frozen=True)
class GapPage:
    competition: str
    season: str
    kind: str  # league | knockout | final
    page: WikiPage


GAP_PAGES: tuple[GapPage, ...] = (
    GapPage(
        "UEL",
        "2526",
        "league",
        WikiPage("uel/2526/league-phase", "2025–26 UEFA Europa League league phase"),
    ),
    GapPage(
        "UEL",
        "2526",
        "knockout",
        WikiPage("uel/2526/knockout-phase", "2025–26 UEFA Europa League knockout phase"),
    ),
    GapPage("UEL", "2526", "final", WikiPage("uel/2526/final", "2026 UEFA Europa League final")),
    GapPage(
        "UECL",
        "2526",
        "league",
        WikiPage("uecl/2526/league-phase", "2025–26 UEFA Conference League league phase"),
    ),
    GapPage(
        "UECL",
        "2526",
        "knockout",
        WikiPage("uecl/2526/knockout-phase", "2025–26 UEFA Conference League knockout phase"),
    ),
    GapPage(
        "UECL", "2526", "final", WikiPage("uecl/2526/final", "2026 UEFA Conference League final")
    ),
)


@dataclass
class GapMatch:
    competition: str
    season: str
    stage: str
    match_date: date
    kickoff_utc: datetime | None
    home_alias: str  # "Name (ISO)", as in team_aliases.csv
    away_alias: str
    home_goals: int | None
    away_goals: int | None
    extra_time: bool
    penalties: bool
    neutral: bool
    status: str  # played | scheduled
    source_ref: str
    raw_file_id: int
    final_score: tuple[int, int] | None = None
    model_exclusion: str | None = None


@dataclass
class GapResult:
    matches: list[GapMatch] = field(default_factory=list)
    issues: list[tuple[int | None, str, str, str]] = field(default_factory=list)  # raw id,
    # severity, code, detail
    pages: dict[str, str] = field(default_factory=dict)


def knockout_stage(sections: tuple[str, ...]) -> str | None:
    for title in reversed(sections):
        t = title.lower()
        if "play-off" in t or "playoff" in t:
            return "Knockout play-off"
        if t.startswith("round of 16"):
            return "Round of 16"
        if t.startswith("quarter-final"):
            return "Quarter-final"
        if t.startswith("semi-final"):
            return "Semi-final"
    return None


def league_stage(sections: tuple[str, ...]) -> str | None:
    for title in reversed(sections):
        if m := re.fullmatch(r"Matchday (\d+)", title):
            return f"League MD{m.group(1)}"
    return None


def _match(spec: GapPage, box: WikiBox, stage: str, raw_id: int) -> GapMatch:
    status = "played" if box.score is not None else "scheduled"
    reg = box.regulation
    exclusion = None
    if status == "played" and reg is None:
        exclusion = "no_regulation_score"
    return GapMatch(
        competition=spec.competition,
        season=spec.season,
        stage=stage,
        match_date=box.listed_date,
        kickoff_utc=box.kickoff_utc,
        home_alias=f"{box.home} ({box.home_flag})",
        away_alias=f"{box.away} ({box.away_flag})",
        home_goals=reg[0] if reg else None,
        away_goals=reg[1] if reg else None,
        extra_time=box.extra_time,
        penalties=box.penalties,
        neutral=venue_abroad(box) or stage == "Final",
        status=status,
        source_ref=box.ref,
        raw_file_id=raw_id,
        final_score=box.score,
        model_exclusion=exclusion,
    )


def gap_matches(session: Session, raw_dir, pages: tuple[GapPage, ...] = GAP_PAGES) -> GapResult:
    out = GapResult()
    by_comp: dict[tuple[str, str], list[GapMatch]] = defaultdict(list)
    for spec in pages:
        raw = latest_revision(session, SOURCE, spec.page.logical_key)
        if raw is None:
            out.pages[spec.page.logical_key] = "missing"
            out.issues.append((None, "warning", "page_missing", spec.page.title))
            continue
        body = json.loads(read_raw(raw_dir, raw)).get("parse", {})
        wikitext, revid = body.get("wikitext", ""), body.get("revid")
        out.pages[spec.page.logical_key] = f"rev {revid}"
        if spec.kind != "final" and not times_are_cet(wikitext):
            out.issues.append((raw.id, "error", "time_basis_unknown", spec.page.title))
            continue
        boxes = wiki_boxes(wikitext, f"{spec.page.title} (rev {revid})")
        if spec.kind == "final":
            boxes = boxes[:1]
            if not boxes:
                out.issues.append((raw.id, "error", "no_final_box", spec.page.title))
        for box in boxes:
            if spec.kind == "league":
                stage = league_stage(box.sections)
            elif spec.kind == "knockout":
                stage = knockout_stage(box.sections)
            else:
                stage = "Final"
            if stage is None:
                out.issues.append((raw.id, "error", "no_stage_heading", box.ref))
                continue
            if box.home_flag is None or box.away_flag is None:
                out.issues.append((raw.id, "error", "no_country_flag", box.ref))
                continue
            m = _match(spec, box, stage, raw.id)
            if box.venue_time_note:
                out.issues.append((raw.id, "warning", "venue_time_mismatch", box.venue_time_note))
            if m.model_exclusion:
                out.issues.append((raw.id, "error", "regulation_score_unknown", box.ref))
            by_comp[(spec.competition, spec.season)].append(m)
    for (comp, _season), matches in by_comp.items():
        out.issues += _check_league(comp, [m for m in matches if m.stage.startswith("League")])
        out.issues += _number_legs_and_check(
            [m for m in matches if not m.stage.startswith("League")]
        )
        out.matches += matches
    return out


def _check_league(comp: str, matches: list[GapMatch]) -> list[tuple]:
    if not matches:
        return []
    per_team, total = LEAGUE_MATCHES[comp]
    raw_id = matches[0].raw_file_id
    issues = []
    count: Counter = Counter()
    per_md: Counter = Counter()
    for m in matches:
        for t in (m.home_alias, m.away_alias):
            count[t] += 1
            per_md[(m.stage, t)] += 1
    if len(matches) != total or len(count) != 36:
        issues.append(
            (raw_id, "error", "league_size", f"{len(matches)} matches, {len(count)} teams")
        )
    for team, n in sorted(count.items()):
        if n != per_team:
            issues.append((raw_id, "error", "matches_per_team", f"{team}: {n}"))
    for (stage, team), n in sorted(per_md.items()):
        if n != 1:
            issues.append((raw_id, "error", "matches_per_matchday", f"{stage} {team}: {n}"))
    return issues


def _number_legs_and_check(matches: list[GapMatch]) -> list[tuple]:
    issues = []
    ties: dict[tuple[str, frozenset], list[GapMatch]] = defaultdict(list)
    for m in matches:
        ties[(m.stage, frozenset((m.home_alias, m.away_alias)))].append(m)
    for (stage, _), legs in ties.items():
        legs.sort(key=lambda x: x.match_date)
        if len(legs) == 2:
            first, second = legs
            first.stage, second.stage = f"{stage} leg 1", f"{stage} leg 2"
            if "played" != first.status or "played" != second.status:
                continue
            if None in (first.home_goals, second.home_goals):
                continue
            a = first.away_goals + second.home_goals
            b = first.home_goals + second.away_goals
            after_et = None
            if second.extra_time and second.final_score:
                after_et = (
                    first.away_goals + second.final_score[0],
                    first.home_goals + second.final_score[1],
                )
            issues += _decided([first, second], a == b, after_et, second)
        elif len(legs) == 1 and stage == "Final":
            m = legs[0]
            if m.status == "played" and m.home_goals is not None:
                after_et = m.final_score if m.extra_time else None
                issues += _decided([m], m.home_goals == m.away_goals, after_et, m)
        else:
            raw_id = legs[0].raw_file_id
            issues.append((raw_id, "warning", "tie_size", f"{stage}: {len(legs)} matches"))
    return issues


def _decided(tie: list[GapMatch], level_90: bool, after_et, last: GapMatch) -> list[tuple]:
    problem = None
    if last.extra_time and not level_90:
        problem = "extra_time_not_level"
    elif level_90 and not last.extra_time:
        problem = "level_without_extra_time"
    elif after_et is not None:
        level_et = after_et[0] == after_et[1]
        if last.penalties != level_et:
            problem = "penalties_not_level" if last.penalties else "level_without_penalties"
    if problem is None:
        return []
    for m in tie:
        m.model_exclusion = m.model_exclusion or "tie_inconsistent"
    return [(last.raw_file_id, "warning", problem, last.source_ref)]
