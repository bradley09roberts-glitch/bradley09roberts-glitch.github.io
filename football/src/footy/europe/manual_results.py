"""Manual UEFA results: `footy euro-import-results <file>` (DECISIONS.md D-029).

While Wikipedia refuses this machine, UEFA results can still be settled from final scores a
person has read in news reports. The input is a JSON file like
`comparisons/results_2026-10-21.json`:

    {"results": [
      {"fixture_ref": "UCL:2627:2026-10-21:ita-como:eng-man-united",
       "home_goals": 4, "away_goals": 1,
       "sources": [{"publisher": "Sky Sports", "url": "https://www.skysports.com/..."},
                   {"publisher": "TNT Sports", "url": "https://www.tntsports.co.uk/..."}]},
      {"competition": "UEL", "match_date": "2026-10-22", "home_team": "...", "away_team": "...",
       "home_goals": 1, "away_goals": 0, "sources": [...]}
    ]}

Teams are team keys, or names with exactly one team in `team_aliases`. `extra_time` and
`penalties` (booleans) may be given; the goals are always the score after 90 minutes plus
stoppage time. A source may carry its own `home_goals`/`away_goals`, which must then agree.

Rules:
- At least two independent publishers per score: different publisher names on different web
  domains. UEFA.com is never a source (its terms, D-023).
- The match must exist in `euro_matches` (the import never creates fixtures), not be
  postponed, cancelled or awarded, and have kicked off at least two hours earlier.
- The file is stored in the raw store first (source "manual", key "euro-results/<file name>"),
  and the row records it: `source` "manual", `raw_file_id` that revision, `source_ref` a JSON
  object with the file and every publisher and URL.
- A row with no result yet (scheduled, or played without a regulation score) takes the score.
  A row with the same result already changes nothing. A row with another result is refused,
  unless `--correct` is given; the change is then written to `data_corrections` first.
- A later Wikipedia load confirms the manual result (the row becomes Wikipedia's again) or
  corrects it with a `data_corrections` entry and a `manual_result_disagrees` error
  (`footy.europe.euro_load`).

Entries for other competitions (league games) are skipped: their results come from
football-data. `--dry-run` writes nothing.
"""

import json
import re
from collections import Counter
from dataclasses import dataclass, field
from datetime import UTC, date, datetime, time, timedelta
from pathlib import Path
from urllib.parse import urlsplit

from sqlalchemy import Engine, select
from sqlalchemy.orm import Session

from footy.ingest.raw_store import store_raw
from footy.models import DataCorrection, EuroMatch, Team, TeamAlias, utcnow

SOURCE = "manual"
RAW_KEY_PREFIX = "euro-results"
COMPETITIONS = ("UCL", "UEL", "UECL", "UCLQ", "UELQ", "UECLQ")
MIN_PUBLISHERS = 2
# A match is over at least this long after kick-off (two halves, half-time and stoppages).
FINISHED_AFTER = timedelta(hours=2)
FORBIDDEN_DOMAINS = ("uefa.com",)  # terms 6.2, DECISIONS.md D-023
NOT_PLAYED = ("postponed", "cancelled", "awarded")
# Second-level labels under which the registered domain is three labels (bbc.co.uk).
_SECOND_LEVEL = {"co", "com", "org", "net", "gov", "ac", "edu"}


class ManualImportError(ValueError):
    """The file as a whole can't be read."""


@dataclass
class EntryResult:
    index: int
    label: str
    action: str  # applied | corrected | unchanged | skipped | refused
    detail: str = ""
    euro_match_id: int | None = None
    publishers: list[str] = field(default_factory=list)

    def as_dict(self) -> dict:
        out = {"entry": self.index, "match": self.label, "action": self.action}
        if self.detail:
            out["detail"] = self.detail
        if self.euro_match_id is not None:
            out["euro_match_id"] = self.euro_match_id
        if self.publishers:
            out["publishers"] = self.publishers
        return out


@dataclass
class ImportReport:
    file: str
    raw_file_id: int | None
    dry_run: bool
    entries: list[EntryResult]

    @property
    def refused(self) -> list[EntryResult]:
        return [e for e in self.entries if e.action == "refused"]

    def as_dict(self) -> dict:
        return {
            "file": self.file,
            "raw_file_id": self.raw_file_id,
            "dry_run": self.dry_run,
            "counts": dict(sorted(Counter(e.action for e in self.entries).items())),
            "entries": [e.as_dict() for e in self.entries],
        }


def registered_domain(url: str) -> str:
    """ "skysports.com" for https://www.skysports.com/..., "bbc.co.uk" for news.bbc.co.uk."""
    host = (urlsplit(url).hostname or "").lower().rstrip(".")
    labels = host.split(".")
    if len(labels) >= 3 and labels[-2] in _SECOND_LEVEL and len(labels[-1]) == 2:
        return ".".join(labels[-3:])
    return ".".join(labels[-2:])


def _publisher_key(name: str) -> str:
    return re.sub(r"[^a-z0-9]", "", name.casefold())


def check_sources(entry: dict, score: tuple[int, int]) -> tuple[list[dict], list[str]]:
    """(the sources that count, problems). Problems make the entry refused."""
    problems: list[str] = []
    counted: list[dict] = []
    publishers: set[str] = set()
    domains: set[str] = set()
    sources = entry.get("sources")
    if not isinstance(sources, list):
        return [], ["no sources list"]
    for src in sources:
        if not isinstance(src, dict):
            problems.append(f"source is not an object: {src!r}")
            continue
        publisher = str(src.get("publisher") or "").strip()
        url = str(src.get("url") or "").strip()
        parts = urlsplit(url)
        if not publisher or parts.scheme not in ("http", "https") or not parts.hostname:
            problems.append(f"source needs a publisher and an http(s) URL: {src!r}")
            continue
        domain = registered_domain(url)
        if domain in FORBIDDEN_DOMAINS:
            problems.append(f"{publisher}: {domain} may not be used (terms 6.2, D-023)")
            continue
        own = (src.get("home_goals"), src.get("away_goals"))
        if own != (None, None) and own != score:
            problems.append(f"{publisher} gives {own[0]}-{own[1]}, not {score[0]}-{score[1]}")
            continue
        counted.append({"publisher": publisher, "url": url})
        publishers.add(_publisher_key(publisher))
        domains.add(domain)
    independent = min(len(publishers), len(domains))
    if independent < MIN_PUBLISHERS:
        problems.append(
            f"{independent} independent publisher(s) (different names on different domains); "
            f"at least {MIN_PUBLISHERS} needed"
        )
    return counted, problems


def season_of(day: date) -> str:
    """UEFA seasons start with the qualifiers in June: 2026-10-21 -> "2627"."""
    year = day.year if day.month >= 6 else day.year - 1
    return f"{year % 100:02d}{(year + 1) % 100:02d}"


def _team_key(session: Session, value: str) -> str | None:
    if session.get(Team, value) is not None:
        return value
    keys = set(session.scalars(select(TeamAlias.team_key).where(TeamAlias.alias == value)))
    return keys.pop() if len(keys) == 1 else None


def identify(session: Session, entry: dict) -> tuple[tuple[str, str, date, str, str] | None, str]:
    """((competition, season, date, home key, away key), "") or (None, why not)."""
    ref = entry.get("fixture_ref")
    if ref:
        parts = str(ref).split(":")
        if len(parts) != 5:
            return None, f"fixture_ref {ref!r} is not competition:season:date:home:away"
        comp, season, day, home, away = parts
    else:
        comp = str(entry.get("competition") or "")
        day = str(entry.get("match_date") or entry.get("date") or "")
        home = str(entry.get("home_team") or "")
        away = str(entry.get("away_team") or "")
        season = str(entry.get("season") or "")
        if not (comp and day and home and away):
            return None, "needs fixture_ref, or competition, match_date, home_team and away_team"
    try:
        match_date = date.fromisoformat(day)
    except ValueError:
        return None, f"date {day!r} is not YYYY-MM-DD"
    if comp not in COMPETITIONS:
        return (comp, season, match_date, home, away), "not a UEFA competition"
    home_key, away_key = _team_key(session, home), _team_key(session, away)
    if home_key is None or away_key is None:
        unknown = [t for t, k in ((home, home_key), (away, away_key)) if k is None]
        return None, f"unknown or ambiguous team {', '.join(unknown)}"
    return (comp, season or season_of(match_date), match_date, home_key, away_key), ""


def _score(entry: dict) -> tuple[int, int] | None:
    hg, ag = entry.get("home_goals"), entry.get("away_goals")
    ok = all(isinstance(g, int) and not isinstance(g, bool) and 0 <= g < 30 for g in (hg, ag))
    return (hg, ag) if ok else None


def import_results(
    engine: Engine,
    raw_dir: Path,
    path: Path,
    *,
    correct: bool = False,
    dry_run: bool = False,
    now: datetime | None = None,
) -> ImportReport:
    """Apply the file's UEFA results to `euro_matches` (see the module docstring)."""
    now = now or utcnow()
    content = path.read_bytes()
    try:
        data = json.loads(content)
    except ValueError as exc:
        raise ManualImportError(f"{path} is not JSON: {exc}") from exc
    entries = data.get("results") if isinstance(data, dict) else data
    if not isinstance(entries, list):
        raise ManualImportError(f"{path}: expected a list of results, or {{'results': [...]}}")

    report = ImportReport(str(path), None, dry_run, [])
    with Session(engine) as s:
        raw_id = None
        if not dry_run:
            raw = store_raw(
                s,
                raw_dir,
                source=SOURCE,
                logical_key=f"{RAW_KEY_PREFIX}/{path.name}",
                content=content,
                retrieved_at=now,
                url=f"file:{path.name}",
                suffix=path.suffix,
            ).raw
            raw_id = report.raw_file_id = raw.id
        for i, entry in enumerate(entries, 1):
            if not isinstance(entry, dict):
                report.entries.append(EntryResult(i, repr(entry)[:60], "refused", "not an object"))
                continue
            report.entries.append(_apply(s, i, entry, raw_id, path, correct, now))
        if not dry_run:
            s.commit()
    return report


def _apply(
    s: Session,
    i: int,
    entry: dict,
    raw_id: int | None,
    path: Path,
    correct: bool,
    now: datetime,
) -> EntryResult:
    label = str(entry.get("match") or entry.get("fixture_ref") or f"entry {i}")
    key, why = identify(s, entry)
    if why == "not a UEFA competition":
        return EntryResult(
            i,
            label,
            "skipped",
            f"{key[0]}: not a UEFA match (league results come from football-data)",
        )
    if key is None:
        return EntryResult(i, label, "refused", why)
    score = _score(entry)
    if score is None:
        return EntryResult(i, label, "refused", "home_goals and away_goals must be 0 to 29")
    sources, problems = check_sources(entry, score)
    publishers = [x["publisher"] for x in sources]
    if problems:
        return EntryResult(i, label, "refused", "; ".join(problems), publishers=publishers)
    comp, season, match_date, home, away = key
    row = s.scalar(
        select(EuroMatch).where(
            EuroMatch.competition == comp,
            EuroMatch.season == season,
            EuroMatch.match_date == match_date,
            EuroMatch.home_team == home,
            EuroMatch.away_team == away,
        )
    )
    if row is None:
        return EntryResult(i, label, "refused", "no such match in euro_matches")
    result = EntryResult(i, label, "refused", euro_match_id=row.id, publishers=publishers)
    extra_time, penalties = bool(entry.get("extra_time")), bool(entry.get("penalties"))
    if penalties and not extra_time:
        result.detail = "penalties without extra time"
        return result
    if extra_time and row.stage.startswith(("League", "Group")):
        result.detail = f"extra time in {row.stage}"
        return result
    if row.status in NOT_PLAYED:
        result.detail = f"the match is {row.status}"
        return result
    start = row.kickoff_utc or datetime.combine(row.match_date, time(23, 59), tzinfo=UTC)
    if now < start + FINISHED_AFTER:
        result.detail = f"kick-off {start:%Y-%m-%d %H:%M} UTC: not finished at {now:%H:%M}"
        return result

    new = {
        "status": "played",
        "home_goals": score[0],
        "away_goals": score[1],
        "extra_time": extra_time,
        "penalties": penalties,
    }
    has_result = row.status == "played" and None not in (row.home_goals, row.away_goals)
    if has_result:
        changed = [f for f, v in new.items() if getattr(row, f) != v]
        if not changed:
            result.action, result.detail = (
                "unchanged",
                f"already {score[0]}-{score[1]} ({row.source})",
            )
            return result
        old = f"{row.home_goals}-{row.away_goals} from {row.source}"
        if not correct:
            result.detail = (
                f"stored {old}, file {score[0]}-{score[1]}: refused (a correction needs "
                "--correct, and is written to data_corrections)"
            )
            return result
        if row.raw_file_id is None:
            result.detail = f"stored {old} has no raw revision to record the correction against"
            return result
        result.action, result.detail = "corrected", f"{old} -> {score[0]}-{score[1]}"
        if raw_id is not None:
            for name in changed:
                before, after = getattr(row, name), new[name]
                s.add(
                    DataCorrection(
                        entity="euro_match",
                        entity_id=row.id,
                        field=name,
                        old_value=None if before is None else str(before),
                        new_value=str(after),
                        old_raw_file_id=row.raw_file_id,
                        new_raw_file_id=raw_id,
                        detected_at=now,
                    )
                )
    else:
        result.action, result.detail = "applied", f"{score[0]}-{score[1]} (was {row.status})"
    if raw_id is None:  # dry run
        return result
    for name, value in new.items():
        setattr(row, name, value)
    row.source = SOURCE
    row.raw_file_id = raw_id
    row.model_exclusion = None
    row.source_ref = json.dumps(
        {"import": path.name, "raw_file_id": raw_id, "sources": sources},
        ensure_ascii=False,
        sort_keys=True,
    )
    s.flush()
    return result
