"""Second-source check of the openfootball matches against English Wikipedia.

Each stored Wikipedia season page (fetched with `footy.europe.wiki_fetch`, source
"wikipedia", key "<comp>/<season>/<page>") is read box by box: date, kick-off (CET/CEST, as
the page states; checked against the venue's local time where the box gives one), teams with
their flag codes, the score as listed, extra-time and penalty flags, and the regulation score
rebuilt from the goal minutes when the goal list adds up to the score.

Each box is paired with the openfootball match of the same date whose two clubs come from the
same countries (names decide between candidates). Kick-off, regulation score and the two
flags are then compared. Nothing is changed: the result is a report (and a CSV).
"""

import csv
import difflib
import json
import re
import unicodedata
from collections import Counter
from dataclasses import dataclass, field
from datetime import date, datetime, timedelta
from pathlib import Path

from sqlalchemy.orm import Session

from footy.europe.openfootball import LEAGUE_COUNTRY, OF_COUNTRIES, OFMatch
from footy.europe.wiki_fetch import WikiPage
from footy.europe.wikipedia_2627 import (
    _START_DATE,
    FIFA_TO_ISO,
    goal_minutes,
    iter_boxes,
    kickoff_from_box,
    parse_score,
    parse_team,
    plain_text,
    regulation_goals,
    stadium_country,
    times_are_cet,
)
from footy.ingest.raw_store import latest_revision, read_raw

SOURCE = "wikipedia"
# {{fbaicon|FRA}}, also with a flag variant ({{fbaicon|FRA|1974}}) as some older pages have.
_FLAG = re.compile(r"\{\{\s*(?:#invoke:flag\s*\|\s*)?fbaicon\s*\|\s*([A-Z]{3})\s*(?:\|[^{}]*)?\}\}")
_VENUE_TIME = re.compile(r"\(\s*(\d{1,2}:\d{2})\s*\[\[\s*UTC\s*([+\-−±]\s*\d{1,2}(?::?\d{2})?)")


@dataclass(frozen=True)
class CheckPage:
    competitions: tuple[str, ...]  # openfootball competitions the page covers
    season: str
    page: WikiPage


def _pages() -> tuple[CheckPage, ...]:
    out: list[CheckPage] = []

    def add(comps: tuple[str, ...], season: str, slug: str, title: str) -> None:
        key = f"{comps[0].lower().rstrip('q')}/{season}/{slug}"
        out.append(CheckPage(comps, season, WikiPage(key, title)))

    for y in range(2018, 2026):
        season = f"{y % 100:02d}{(y + 1) % 100:02d}"
        label = f"{y}–{(y + 1) % 100:02d}"
        first = "league phase" if y >= 2024 else "group stage"
        slug = first.replace(" ", "-")
        add(("UCL",), season, slug, f"{label} UEFA Champions League {first}")
        add(("UCL",), season, "knockout-phase", f"{label} UEFA Champions League knockout phase")
        if y >= 2020:
            add(("UEL",), season, slug, f"{label} UEFA Europa League {first}")
            add(("UEL",), season, "knockout-phase", f"{label} UEFA Europa League knockout phase")
        if y >= 2021:
            conf = "UEFA Conference League" if y >= 2024 else "UEFA Europa Conference League"
            add(("UECL",), season, slug, f"{label} {conf} {first}")
            add(("UECL",), season, "knockout-phase", f"{label} {conf} knockout phase")
        if y >= 2024:
            qual = "qualifying phase and play-off round"
            add(("UCLQ",), season, "qualifying", f"{label} UEFA Champions League {qual}")
            add(("UELQ",), season, "qualifying", f"{label} UEFA Europa League {qual}")
            add(
                ("UECLQ",),
                season,
                "qualifying-q1-q2",
                f"{label} UEFA Conference League qualifying (first and second round matches)",
            )
            add(
                ("UECLQ",),
                season,
                "qualifying-q3-po",
                f"{label} UEFA Conference League qualifying (third and play-off round matches)",
            )
    return tuple(out)


CHECK_PAGES = _pages()


@dataclass
class WikiBox:
    listed_date: date
    kickoff_utc: datetime | None
    venue_time_note: str | None
    venue_local_time: str | None  # e.g. "19:55 UTC+03:00", where the box gives it
    home: str
    home_country: str | None
    away: str
    away_country: str | None
    score: tuple[int, int] | None  # as listed (after extra time if any)
    extra_time: bool
    penalties: bool
    regulation: tuple[int, int] | None  # None when extra time and the goal list is incomplete
    stadium_country: str | None
    stadium: str
    ref: str


def _iso(code: str | None) -> str | None:
    if code is None:
        return None
    iso = FIFA_TO_ISO.get(code) or OF_COUNTRIES.get(code) or ("MCO" if code == "MON" else None)
    return None if iso is None else LEAGUE_COUNTRY.get(iso, iso)


def _team(value: str) -> tuple[str, str | None]:
    name, code, _ = parse_team(value)
    if code is None and (m := _FLAG.search(value)):
        code = m.group(1)
    return name, code


def wiki_boxes(wikitext: str, ref: str) -> list[WikiBox]:
    boxes = []
    for box in iter_boxes(wikitext):
        p = box.params
        dm = _START_DATE.search(p.get("date", ""))
        if dm is None:
            continue
        listed = date(int(dm.group(1)), int(dm.group(2)), int(dm.group(3)))
        kickoff, note = kickoff_from_box(listed, p.get("time", ""))
        home, home_code = _team(p.get("team1", ""))
        away, away_code = _team(p.get("team2", ""))
        venue = _VENUE_TIME.search(p.get("time", ""))
        score_raw = p.get("score", "")
        score = parse_score(score_raw)
        extra_time = p.get("aet", "").strip().lower() in ("y", "yes", "1", "true") or bool(
            re.search(r"\{\{\s*aet\s*\}\}|a\.e\.t\.", score_raw, re.I)
        )
        penalties = bool(p.get("penaltyscore", "").strip()) or bool(
            re.search(r"\{\{\s*pso\b", score_raw, re.I)
        )
        regulation = score
        if score is not None and extra_time:
            listed_goals = (
                len(goal_minutes(p.get("goals1", ""))),
                len(goal_minutes(p.get("goals2", ""))),
            )
            regulation = (
                (regulation_goals(p.get("goals1", "")), regulation_goals(p.get("goals2", "")))
                if listed_goals == score
                else None
            )
        section = box.sections[-1] if box.sections else ""
        boxes.append(
            WikiBox(
                listed_date=listed,
                kickoff_utc=kickoff,
                venue_time_note=note,
                venue_local_time=f"{venue.group(1)} UTC{venue.group(2)}" if venue else None,
                home=home,
                home_country=_iso(home_code),
                away=away,
                away_country=_iso(away_code),
                score=score,
                extra_time=extra_time,
                penalties=penalties,
                regulation=regulation,
                stadium_country=stadium_country(p.get("stadium", "")),
                stadium=plain_text(p.get("stadium", "")),
                ref=f"{ref}#{section} (line {box.line})",
            )
        )
    return boxes


_STOP = {"fc", "cf", "afc", "sc", "sk", "fk", "ac", "as", "ss", "ssc", "kv", "nk", "pfc", "cd"}


def _norm(name: str) -> str:
    s = unicodedata.normalize("NFKD", re.sub(r"\s*\([A-Z]{3}\)$", "", name))
    s = re.sub(r"[^a-z0-9 ]", " ", s.encode("ascii", "ignore").decode().lower())
    return " ".join(t for t in s.split() if t not in _STOP)


def _similarity(of: OFMatch, box: WikiBox) -> float:
    def sim(a: str, b: str) -> float:
        x, y = _norm(a), _norm(b)
        if not x or not y:
            return 0.0
        if set(x.split()) & set(y.split()):
            return 1.0 + difflib.SequenceMatcher(None, x, y).ratio()
        return difflib.SequenceMatcher(None, x, y).ratio()

    return sim(of.home_name, box.home) + sim(of.away_name, box.away)


@dataclass
class Comparison:
    of: OFMatch
    box: WikiBox

    @property
    def kickoff_agrees(self) -> bool | None:
        if self.of.kickoff_utc is None or self.box.kickoff_utc is None:
            return None
        return self.of.kickoff_utc == self.box.kickoff_utc

    @property
    def regulation_agrees(self) -> bool | None:
        if self.of.status != "played" or self.box.regulation is None:
            return None
        return (self.of.home_goals, self.of.away_goals) == self.box.regulation

    @property
    def final_agrees(self) -> bool | None:
        if self.of.status != "played" or self.box.score is None:
            return None
        listed = self.of.aet or (self.of.home_goals, self.of.away_goals)
        return listed == self.box.score

    @property
    def flags_agree(self) -> bool:
        return (self.of.extra_time, self.of.penalties) == (
            self.box.extra_time,
            self.box.penalties,
        )


@dataclass
class CrossCheckResult:
    comparisons: list[Comparison] = field(default_factory=list)
    unmatched_of: list[OFMatch] = field(default_factory=list)
    unmatched_boxes: list[WikiBox] = field(default_factory=list)
    pages: dict[str, str] = field(default_factory=dict)  # logical key -> "rev N" | "missing"

    def summary(self) -> dict:
        def tally(attr: str) -> dict:
            values = [getattr(c, attr) for c in self.comparisons]
            return {
                "agree": sum(v is True for v in values),
                "disagree": sum(v is False for v in values),
                "not_comparable": sum(v is None for v in values),
            }

        return {
            "matched": len(self.comparisons),
            "kickoff": tally("kickoff_agrees"),
            "regulation_score": tally("regulation_agrees"),
            "final_score": tally("final_agrees"),
            "flags": tally("flags_agree"),
            "venue_time_notes": sum(1 for c in self.comparisons if c.box.venue_time_note),
            "unmatched_openfootball": len(self.unmatched_of),
            "unmatched_wikipedia": len(self.unmatched_boxes),
            "pages": self.pages,
        }

    def disagreements(self) -> list[Comparison]:
        return [
            c
            for c in self.comparisons
            if False in (c.kickoff_agrees, c.regulation_agrees, c.final_agrees, c.flags_agree)
        ]


def pair_boxes(of_matches: list[OFMatch], boxes: list[WikiBox]) -> CrossCheckResult:
    """Pair each box with an openfootball match: same date (or one day either side), same two
    countries, best name similarity."""
    out = CrossCheckResult()
    free = list(of_matches)
    for box in boxes:
        best, best_score = None, -1.0
        for shift in (0, -1, 1):
            day = box.listed_date + timedelta(days=shift)
            for m in free:
                if m.match_date != day:
                    continue
                countries = (_iso(m.home_code), _iso(m.away_code))
                if countries != (box.home_country, box.away_country):
                    continue
                score = _similarity(m, box) - abs(shift)
                if score > best_score:
                    best, best_score = m, score
            if best is not None:
                break
        if best is None:
            out.unmatched_boxes.append(box)
            continue
        free.remove(best)
        out.comparisons.append(Comparison(best, box))
    out.unmatched_of = free
    return out


def crosscheck(
    session: Session,
    raw_dir: Path,
    of_by_comp_season: dict[tuple[str, str], list[OFMatch]],
    pages: tuple[CheckPage, ...] = CHECK_PAGES,
) -> CrossCheckResult:
    """Compare every stored check page with the openfootball matches it covers."""
    total = CrossCheckResult()
    covered: set[tuple[str, str]] = set()
    boxes_by: dict[tuple[str, str], list[WikiBox]] = {}
    for spec in pages:
        raw = latest_revision(session, SOURCE, spec.page.logical_key)
        if raw is None:
            total.pages[spec.page.logical_key] = "missing"
            continue
        body = json.loads(read_raw(raw_dir, raw))
        wikitext = body.get("parse", {}).get("wikitext", "")
        revid = body.get("parse", {}).get("revid")
        total.pages[spec.page.logical_key] = f"rev {revid}"
        if not times_are_cet(wikitext):
            total.pages[spec.page.logical_key] += " (times not stated as CET/CEST: skipped)"
            continue
        for comp in spec.competitions:
            covered.add((comp, spec.season))
            boxes_by.setdefault((comp, spec.season), []).extend(
                wiki_boxes(wikitext, f"{spec.page.title} (rev {revid})")
            )
    for key in sorted(covered):
        result = pair_boxes(of_by_comp_season.get(key, []), boxes_by.get(key, []))
        total.comparisons += result.comparisons
        total.unmatched_of += result.unmatched_of
        total.unmatched_boxes += result.unmatched_boxes
    return total


CSV_COLUMNS = [
    "competition",
    "season",
    "stage",
    "date",
    "openfootball",
    "wikipedia",
    "of_kickoff_utc",
    "wiki_kickoff_utc",
    "of_regulation",
    "wiki_regulation",
    "of_final",
    "wiki_final",
    "of_flags",
    "wiki_flags",
    "kickoff_agrees",
    "regulation_agrees",
    "final_agrees",
    "flags_agree",
    "venue_time_note",
    "venue_local_time",
    "stadium",
    "of_ref",
    "wiki_ref",
]


def write_report(path: Path, result: CrossCheckResult) -> None:
    def fmt(v) -> str:
        return "" if v is None else str(v)

    path.parent.mkdir(parents=True, exist_ok=True)
    with path.open("w", newline="", encoding="utf-8") as f:
        w = csv.DictWriter(f, fieldnames=CSV_COLUMNS)
        w.writeheader()
        for c in result.comparisons:
            m, b = c.of, c.box
            w.writerow(
                {
                    "competition": m.competition,
                    "season": m.season,
                    "stage": m.stage,
                    "date": m.match_date.isoformat(),
                    "openfootball": f"{m.home_name} v {m.away_name}",
                    "wikipedia": f"{b.home} v {b.away}",
                    "of_kickoff_utc": fmt(m.kickoff_utc and m.kickoff_utc.isoformat()),
                    "wiki_kickoff_utc": fmt(b.kickoff_utc and b.kickoff_utc.isoformat()),
                    "of_regulation": fmt(
                        None if m.home_goals is None else f"{m.home_goals}-{m.away_goals}"
                    ),
                    "wiki_regulation": fmt(b.regulation and f"{b.regulation[0]}-{b.regulation[1]}"),
                    "of_final": fmt(
                        (m.aet and f"{m.aet[0]}-{m.aet[1]}")
                        or (None if m.home_goals is None else f"{m.home_goals}-{m.away_goals}")
                    ),
                    "wiki_final": fmt(b.score and f"{b.score[0]}-{b.score[1]}"),
                    "of_flags": f"et={int(m.extra_time)} pen={int(m.penalties)}",
                    "wiki_flags": f"et={int(b.extra_time)} pen={int(b.penalties)}",
                    "kickoff_agrees": fmt(c.kickoff_agrees),
                    "regulation_agrees": fmt(c.regulation_agrees),
                    "final_agrees": fmt(c.final_agrees),
                    "flags_agree": fmt(c.flags_agree),
                    "venue_time_note": fmt(b.venue_time_note),
                    "venue_local_time": fmt(b.venue_local_time),
                    "stadium": b.stadium,
                    "of_ref": f"{m.season}/{m.competition} line {m.line}",
                    "wiki_ref": b.ref,
                }
            )


def disagreement_counts(result: CrossCheckResult) -> Counter:
    c: Counter = Counter()
    for x in result.disagreements():
        for name in ("kickoff_agrees", "regulation_agrees", "final_agrees", "flags_agree"):
            if getattr(x, name) is False:
                c[name] += 1
    return c


def run_crosscheck(engine, settings, *, fetch: bool = False, out_csv: Path | None = None) -> dict:
    """Parse the latest stored openfootball files, compare them with the stored Wikipedia
    check pages (fetching them first when `fetch`), write the CSV and return a summary."""
    from footy.europe.build_2627 import fetch_wikipedia
    from footy.europe.euro_load import latest_openfootball
    from footy.europe.openfootball import parse_openfootball

    detail: dict = {}
    if fetch:
        fetched = fetch_wikipedia(engine, settings.raw_dir, specs=CHECK_PAGES)
        detail["fetch"] = dict(Counter(r.outcome for r in fetched))
    by_key: dict[tuple[str, str], list[OFMatch]] = {}
    with Session(engine) as s:
        for raw in latest_openfootball(s):
            season, file_name = raw.logical_key.split("/")
            parsed = parse_openfootball(read_raw(settings.raw_dir, raw), file_name, season)
            by_key[(parsed.competition, season)] = parsed.matches
        result = crosscheck(s, settings.raw_dir, by_key)
    path = out_csv or settings.data_dir / "europe" / "openfootball_crosscheck.csv"
    write_report(path, result)
    detail |= result.summary() | {
        "disagreements": dict(disagreement_counts(result)),
        "csv": str(path),
    }
    return detail
