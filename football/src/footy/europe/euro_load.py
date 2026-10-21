"""Load UEFA club competition matches into `euro_matches` (DECISIONS.md D-023).

Sources:
- **openfootball** (CC0), 2018/19 to 2025/26: the files of a local clone of
  openfootball/champions-league are stored in the raw store (source "openfootball", key
  "<season>/<file>", e.g. "1819/cl.txt") and the latest stored revision of each is parsed
  (`footy.europe.openfootball`). Without a clone, the stored revisions are used as they are.
- **Wikipedia**, 2026/27: `data/europe/uefa_2627.csv`, rebuilt first from the stored pages by
  `footy.europe.build_2627.build_all` (optionally after re-fetching them). Each row's
  `raw_file_id` is the stored page revision named in its `source_ref`.

Team names resolve only through reference/team_aliases.csv (sources "openfootball" and
"wikipedia"); a name that is not there is reported and its match left out.

Idempotent: re-running over the same raw revisions changes nothing. Rows are matched on the
table's unique key (competition, season, date, home, away). A row that a (source, competition,
season) no longer produces is deleted, since a rescheduled fixture comes back under a new date;
nothing is deleted from a file that had parse errors. A changed result (a row that was not
"scheduled") from a newer raw revision is written to `data_corrections` (entity "euro_match").
"""

import csv
import json
import os
import subprocess
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from datetime import date, datetime
from pathlib import Path

from sqlalchemy import Engine, delete, select
from sqlalchemy.orm import Session

from footy.config import PROJECT_ROOT, Settings
from footy.europe.openfootball import FILES, PARSER_VERSION, SOURCE, OFParse, parse_openfootball
from footy.ingest.fd_import import _replace_issues, alias_map, sync_reference
from footy.ingest.fd_parse import Issue
from footy.ingest.raw_store import latest_revision, read_raw, store_raw
from footy.models import DataCorrection, EuroMatch, RawFile, RawImport, utcnow

WIKI_SOURCE = "wikipedia"
WIKI_SEASON = "2627"
FIRST_SEASON = "1819"
OF_REPO_URL = "https://raw.githubusercontent.com/openfootball/champions-league"
DEFAULT_OF_DIR = PROJECT_ROOT.parent.parent / "openfootball" / "champions-league"
VALUE_FIELDS = [
    "stage",
    "kickoff_utc",
    "home_goals",
    "away_goals",
    "extra_time",
    "penalties",
    "neutral",
    "status",
    "source_ref",
    "raw_file_id",
    "model_exclusion",
]
# Fields whose change between raw revisions is a data correction (source_ref and raw_file_id
# change with every revision).
CORRECTION_FIELDS = [f for f in VALUE_FIELDS if f not in ("source_ref", "raw_file_id")]


@dataclass
class EuroRowIn:
    competition: str
    season: str
    stage: str
    match_date: date
    kickoff_utc: datetime | None
    home_team: str
    away_team: str
    home_goals: int | None
    away_goals: int | None
    extra_time: bool
    penalties: bool
    neutral: bool
    status: str
    source: str
    source_ref: str
    raw_file_id: int | None
    model_exclusion: str | None

    def key(self) -> tuple:
        return (self.match_date, self.home_team, self.away_team)

    def values(self) -> dict:
        return {f: getattr(self, f) for f in VALUE_FIELDS}


@dataclass
class LoadStats:
    inserted: int = 0
    updated: int = 0  # a value changed (score, kick-off, flags, status, stage, ...)
    relinked: int = 0  # only the raw revision or source reference changed
    unchanged: int = 0
    deleted: int = 0
    corrections: int = 0
    skipped_rows: int = 0
    issues: Counter = field(default_factory=Counter)

    def as_dict(self) -> dict:
        return {
            "inserted": self.inserted,
            "updated": self.updated,
            "relinked": self.relinked,
            "unchanged": self.unchanged,
            "deleted": self.deleted,
            "corrections": self.corrections,
            "skipped_rows": self.skipped_rows,
            "issues": dict(sorted(self.issues.items())),
        }


# --- openfootball -----------------------------------------------------------------------


def season_dir_to_key(name: str) -> str | None:
    """openfootball's "2018-19" -> "1819"."""
    if len(name) == 7 and name[4] == "-" and name[:4].isdigit() and name[5:].isdigit():
        return name[2:4] + name[5:]
    return None


def key_to_season_dir(season: str) -> str:
    return f"20{season[:2]}-{season[2:]}"


def git_commit(root: Path) -> str | None:
    try:
        out = subprocess.run(
            ["git", "-C", str(root), "rev-parse", "HEAD"],
            capture_output=True,
            text=True,
            check=False,
            timeout=30,
        )
    except (OSError, subprocess.SubprocessError):
        return None
    commit = out.stdout.strip()
    return commit if out.returncode == 0 and len(commit) == 40 else None


def store_openfootball(
    session: Session, raw_dir: Path, root: Path, *, first_season: str = FIRST_SEASON
) -> dict[str, str]:
    """Store every competition file of every season from `first_season` on. Returns
    {logical key: "new_revision" | "unchanged"}."""
    commit = git_commit(root) or "master"
    outcomes: dict[str, str] = {}
    for season_path in sorted(p for p in root.iterdir() if p.is_dir()):
        season = season_dir_to_key(season_path.name)
        if season is None or season < first_season:
            continue
        for file_name in FILES:
            path = season_path / file_name
            if not path.is_file():
                continue
            key = f"{season}/{file_name}"
            stored = store_raw(
                session,
                raw_dir,
                source=SOURCE,
                logical_key=key,
                content=path.read_bytes(),
                retrieved_at=utcnow(),
                url=f"{OF_REPO_URL}/{commit}/{season_path.name}/{file_name}",
                suffix=".txt",
            )
            outcomes[key] = "new_revision" if stored.created else "unchanged"
    return outcomes


def latest_openfootball(session: Session) -> list[RawFile]:
    keys = session.scalars(
        select(RawFile.logical_key).where(RawFile.source == SOURCE).distinct()
    ).all()
    return [latest_revision(session, SOURCE, k) for k in sorted(keys)]


def openfootball_rows(
    raw: RawFile, parsed: OFParse, aliases: dict[str, str]
) -> tuple[list[EuroRowIn], list[Issue]]:
    issues = [Issue(i.line, i.severity, i.code, i.detail) for i in parsed.issues]
    rows = []
    folder = key_to_season_dir(parsed.season)
    file_name = raw.logical_key.split("/")[1]
    for m in parsed.matches:
        missing = [n for n in (m.home_name, m.away_name) if n not in aliases]
        if missing:
            issues.append(Issue(m.line, "error", "unmapped_team", ", ".join(missing)))
            continue
        rows.append(
            EuroRowIn(
                competition=m.competition,
                season=m.season,
                stage=m.stage,
                match_date=m.match_date,
                kickoff_utc=m.kickoff_utc,
                home_team=aliases[m.home_name],
                away_team=aliases[m.away_name],
                home_goals=m.home_goals,
                away_goals=m.away_goals,
                extra_time=m.extra_time,
                penalties=m.penalties,
                neutral=m.neutral,
                status=m.status,
                source=SOURCE,
                source_ref=f"{folder}/{file_name} line {m.line}",
                raw_file_id=raw.id,
                model_exclusion=m.model_exclusion,
            )
        )
    return rows, issues


# --- Wikipedia (2026/27 CSV) ------------------------------------------------------------


def wiki_revision_index(session: Session, raw_dir: Path) -> dict[int, int]:
    """{Wikipedia revision id: raw_files.id} for every stored Wikipedia page."""
    index: dict[int, int] = {}
    for raw in session.scalars(select(RawFile).where(RawFile.source == WIKI_SOURCE)):
        try:
            revid = json.loads(read_raw(raw_dir, raw)).get("parse", {}).get("revid")
        except (ValueError, OSError, RuntimeError):
            continue
        if isinstance(revid, int):
            index[revid] = raw.id
    return index


def _revid(source_ref: str) -> int | None:
    marker = "(rev "
    i = source_ref.rfind(marker)
    if i < 0:
        return None
    digits = source_ref[i + len(marker) :].split(",")[0].split(")")[0].strip()
    return int(digits) if digits.isdigit() else None


def _flag(value: str) -> bool:
    return value.strip() in ("1", "true", "True")


def _int_or_none(value: str) -> int | None:
    return int(value) if value.strip() != "" else None


def wikipedia_rows(
    csv_path: Path, aliases: dict[str, str], revisions: dict[int, int]
) -> tuple[list[EuroRowIn], list[tuple[int | None, Issue]]]:
    """Rows of the 2026/27 CSV, and issues as (raw_file_id, issue) pairs (the CSV row number
    is the issue's row)."""
    rows, issues = [], []
    with csv_path.open(newline="", encoding="utf-8") as f:
        for n, r in enumerate(csv.DictReader(f), 1):
            raw_id = revisions.get(_revid(r["source_ref"]) or -1)
            names = (
                f"{r['home_name']} ({r['home_country']})",
                f"{r['away_name']} ({r['away_country']})",
            )
            missing = [x for x in names if x not in aliases]
            if missing:
                issues.append((raw_id, Issue(n, "error", "unmapped_team", ", ".join(missing))))
                continue
            if raw_id is None:
                issues.append((None, Issue(n, "warning", "raw_page_not_found", r["source_ref"])))
            home_goals, away_goals = _int_or_none(r["home_goals"]), _int_or_none(r["away_goals"])
            exclusion = None
            if r["status"] == "played" and (home_goals is None or away_goals is None):
                exclusion = "no_regulation_score"
            rows.append(
                EuroRowIn(
                    competition=r["competition"],
                    season=r["season"],
                    stage=r["stage"],
                    match_date=date.fromisoformat(r["match_date"]),
                    kickoff_utc=datetime.fromisoformat(r["kickoff_utc"])
                    if r["kickoff_utc"]
                    else None,
                    home_team=aliases[names[0]],
                    away_team=aliases[names[1]],
                    home_goals=home_goals,
                    away_goals=away_goals,
                    extra_time=_flag(r["extra_time"]),
                    penalties=_flag(r["penalties"]),
                    neutral=_flag(r["neutral"]),
                    status=r["status"],
                    source=r["source"] or WIKI_SOURCE,
                    source_ref=r["source_ref"],
                    raw_file_id=raw_id,
                    model_exclusion=exclusion,
                )
            )
    return rows, issues


# --- upsert -----------------------------------------------------------------------------


def upsert_group(
    session: Session,
    source: str,
    competition: str,
    season: str,
    rows: list[EuroRowIn],
    stats: LoadStats,
    *,
    allow_delete: bool = True,
    now: datetime | None = None,
) -> list[Issue]:
    """Make the stored rows of one (source, competition, season) equal to `rows`."""
    now = now or utcnow()
    existing = {
        (m.match_date, m.home_team, m.away_team): m
        for m in session.scalars(
            select(EuroMatch).where(
                EuroMatch.source == source,
                EuroMatch.competition == competition,
                EuroMatch.season == season,
            )
        )
    }
    seen: set[tuple] = set()
    issues: list[Issue] = []
    for row in rows:
        key = row.key()
        if key in seen:
            issues.append(Issue(0, "error", "duplicate_fixture", f"{row.source_ref}"))
            stats.skipped_rows += 1
            continue
        seen.add(key)
        current = existing.get(key)
        if current is None:
            session.add(
                EuroMatch(
                    competition=competition,
                    season=season,
                    match_date=row.match_date,
                    home_team=row.home_team,
                    away_team=row.away_team,
                    source=source,
                    **row.values(),
                )
            )
            stats.inserted += 1
            continue
        changed = relinked = False
        for name, new in row.values().items():
            old = getattr(current, name)
            if old == new:
                continue
            if name not in CORRECTION_FIELDS:
                relinked = True
                continue
            changed = True
            if (
                current.status != "scheduled"
                and current.raw_file_id is not None
                and row.raw_file_id is not None
                and current.raw_file_id != row.raw_file_id
            ):
                session.add(
                    DataCorrection(
                        entity="euro_match",
                        entity_id=current.id,
                        field=name,
                        old_value=None if old is None else str(old),
                        new_value=None if new is None else str(new),
                        old_raw_file_id=current.raw_file_id,
                        new_raw_file_id=row.raw_file_id,
                        detected_at=now,
                    )
                )
                stats.corrections += 1
        if changed or relinked:
            for name, new in row.values().items():
                setattr(current, name, new)
        if changed:
            stats.updated += 1
        elif relinked:
            stats.relinked += 1
        else:
            stats.unchanged += 1
    stale = [m for k, m in existing.items() if k not in seen]
    if stale and allow_delete:
        session.execute(delete(EuroMatch).where(EuroMatch.id.in_([m.id for m in stale])))
        stats.deleted += len(stale)
    elif stale:
        issues.append(
            Issue(0, "warning", "stale_rows_kept", f"{len(stale)} stored rows not in this load")
        )
    session.flush()
    return issues


# --- the job ----------------------------------------------------------------------------


def summarise_table(session: Session) -> dict:
    """Counts per competition and season, and the flags, as stored."""
    per: dict[str, Counter] = defaultdict(Counter)
    totals: Counter = Counter()
    for m in session.scalars(select(EuroMatch)):
        c = per[f"{m.competition} {m.season}"]
        c[m.status] += 1
        c["extra_time"] += int(m.extra_time)
        c["penalties"] += int(m.penalties)
        c["neutral"] += int(m.neutral)
        if m.model_exclusion:
            c[f"excluded:{m.model_exclusion}"] += 1
        totals[m.status] += 1
        totals["extra_time"] += int(m.extra_time)
        totals["penalties"] += int(m.penalties)
        totals["neutral"] += int(m.neutral)
        totals["excluded"] += int(m.model_exclusion is not None)
        totals["rows"] += 1
    return {
        "per_competition_season": {k: dict(sorted(v.items())) for k, v in sorted(per.items())},
        "totals": dict(sorted(totals.items())),
    }


def run_euro_update(
    engine: Engine,
    settings: Settings,
    *,
    openfootball_dir: Path | None = None,
    rebuild_2627: bool = True,
    fetch_wikipedia: bool = False,
    fetch_uefa: bool = False,
    wiki_csv: Path | None = None,
) -> dict:
    """The whole UEFA load as one logged job (`job_runs.job = "euro_update"`)."""
    from footy.ingest.jobs import job_run

    of_dir = openfootball_dir or Path(os.environ.get("FOOTY_OPENFOOTBALL_DIR", DEFAULT_OF_DIR))
    csv_path = wiki_csv or settings.data_dir / "europe" / "uefa_2627.csv"
    with job_run(engine, "euro_update") as ctx:
        with Session(engine) as s:
            sync_reference(s)
            s.commit()

        # 1. openfootball: store the clone's files, then parse the latest stored revisions.
        if of_dir.is_dir():
            with Session(engine) as s:
                ctx.detail["openfootball_store"] = dict(
                    Counter(store_openfootball(s, settings.raw_dir, of_dir).values())
                )
                ctx.detail["openfootball_commit"] = git_commit(of_dir)
                s.commit()
        else:
            ctx.detail["openfootball_store"] = f"no clone at {of_dir}; stored revisions used"
        of_stats = LoadStats()
        with Session(engine) as s:
            aliases = alias_map(s, SOURCE)
            raws = latest_openfootball(s)
        for raw in raws:
            with Session(engine) as s:
                raw = s.get(RawFile, raw.id)
                season, file_name = raw.logical_key.split("/")
                parsed = parse_openfootball(read_raw(settings.raw_dir, raw), file_name, season)
                rows, issues = openfootball_rows(raw, parsed, aliases)
                has_errors = any(i.severity == "error" for i in issues)
                issues += upsert_group(
                    s,
                    SOURCE,
                    parsed.competition,
                    season,
                    rows,
                    of_stats,
                    allow_delete=not has_errors,
                )
                of_stats.skipped_rows += len(parsed.matches) - len(rows)
                of_stats.issues.update(i.code for i in issues)
                _replace_issues(s, raw.id, issues)
                if s.get(RawImport, (raw.id, PARSER_VERSION)) is None:
                    s.add(
                        RawImport(
                            raw_file_id=raw.id,
                            parser_version=PARSER_VERSION,
                            imported_at=utcnow(),
                            job_run_id=ctx.run_id,
                            rows_imported=len(rows),
                            rows_rejected=len(parsed.matches) - len(rows),
                        )
                    )
                s.commit()
                if has_errors:
                    ctx.partial = True
        ctx.detail["openfootball"] = of_stats.as_dict() | {"files": len(raws)}

        # 2. Wikipedia 2026/27: optionally re-fetch, rebuild the CSV, load it.
        ctx.detail["wikipedia"] = _load_wikipedia(
            engine, settings, ctx, csv_path, rebuild_2627, fetch_wikipedia, fetch_uefa
        )

        with Session(engine) as s:
            ctx.detail["table"] = summarise_table(s)
        return ctx.detail


def _load_wikipedia(engine, settings, ctx, csv_path, rebuild, fetch, fetch_uefa) -> dict:
    detail: dict = {}
    if fetch or fetch_uefa:
        from footy.europe.build_2627 import fetch_uefa_pages, fetch_wikipedia
        from footy.europe.wikipedia_2627 import build_uefa_2627

        if fetch:
            fetched = fetch_wikipedia(engine, settings.raw_dir)
            detail["fetch_wikipedia"] = dict(Counter(r.outcome for r in fetched))
        if fetch_uefa:
            with Session(engine) as s:
                rows = build_uefa_2627(s, settings.raw_dir).rows
            fetched = fetch_uefa_pages(engine, settings.raw_dir, rows)
            detail["fetch_uefa"] = dict(Counter(r.outcome for r in fetched))
    if rebuild:
        from footy.europe.build_2627 import build_all

        try:
            with Session(engine) as s:
                built = build_all(s, settings.raw_dir, settings.data_dir)
            detail["rebuilt_rows"] = len(built.wikipedia.rows)
            detail["rebuild_issues"] = dict(Counter(i.code for i in built.wikipedia.issues))
        except Exception as exc:  # the stored CSV is still loaded; the failure is reported
            detail["rebuild_error"] = f"{type(exc).__name__}: {exc}"
            ctx.partial = True
    if not csv_path.is_file():
        detail["csv"] = f"missing: {csv_path}"
        ctx.partial = True
        return detail
    stats = LoadStats()
    with Session(engine) as s:
        aliases = alias_map(s, WIKI_SOURCE)
        revisions = wiki_revision_index(s, settings.raw_dir)
        rows, pairs = wikipedia_rows(csv_path, aliases, revisions)
        groups: dict[tuple[str, str], list[EuroRowIn]] = defaultdict(list)
        for r in rows:
            groups[(r.competition, r.season)].append(r)
        has_errors = any(i.severity == "error" for _, i in pairs)
        extra: list[Issue] = []
        for (comp, season), group in sorted(groups.items()):
            extra += upsert_group(
                s, WIKI_SOURCE, comp, season, group, stats, allow_delete=not has_errors
            )
        by_raw: dict[int, list[Issue]] = defaultdict(list)
        for raw_id, issue in pairs:
            if raw_id is not None:
                by_raw[raw_id].append(issue)
        for raw_id in sorted(set(revisions.values()) & {r.raw_file_id for r in rows} | set(by_raw)):
            _replace_issues(s, raw_id, by_raw.get(raw_id, []))
        s.commit()
    stats.skipped_rows += sum(1 for _, i in pairs if i.severity == "error")
    stats.issues.update(i.code for _, i in pairs)
    stats.issues.update(i.code for i in extra)
    if has_errors:
        ctx.partial = True
    linked = sum(1 for r in rows if r.raw_file_id is not None)
    detail |= stats.as_dict() | {"csv": str(csv_path), "rows": len(rows), "raw_linked": linked}
    return detail
