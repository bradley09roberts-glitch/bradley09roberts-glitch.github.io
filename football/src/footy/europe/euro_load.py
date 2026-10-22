"""Load UEFA club competition matches into `euro_matches` (DECISIONS.md D-023).

Sources:
- **openfootball** (CC0), 2018/19 to 2025/26: the files of a local clone of
  openfootball/champions-league are stored in the raw store (source "openfootball", key
  "<season>/<file>", e.g. "1819/cl.txt") and the latest stored revision of each is parsed
  (`footy.europe.openfootball`). Without a clone, the stored revisions are used as they are.
- **Wikipedia**, 2025/26 Europa League and Conference League (which openfootball lacks): the
  stored league-phase, knockout-phase and final articles (`footy.europe.wikipedia_seasons`).
- **Wikipedia**, 2026/27: `data/europe/uefa_2627.csv`, rebuilt first from the stored pages by
  `footy.europe.build_2627.build_all` (optionally after re-fetching them). Each row's
  `raw_file_id` is the stored page revision named in its `source_ref`.

Team names resolve only through reference/team_aliases.csv (sources "openfootball" and
"wikipedia"); a name that is not there is reported and its match left out.

Idempotent: re-running over the same raw revisions changes nothing. Rows are matched on the
table's unique key (competition, season, date, home, away). A row that a (source, competition,
season) no longer produces is deleted, since a rescheduled fixture comes back under a new date,
but never while any parse, build or mapping error is outstanding (a row the build dropped is
not a row the source dropped; the job is then `partial`), and a row with a result (not
"scheduled") only with a `data_corrections` entry (field "deleted"). A changed result from a
newer raw revision is written to `data_corrections` (entity "euro_match").

A result entered by `footy euro-import-results` (source "manual", DECISIONS.md D-029) sits on
the same row. A source that later gives the same result takes the row over (confirmed); one
that gives another result corrects it, with a `data_corrections` entry and a
`manual_result_disagrees` error (job `partial`); one that has no result yet leaves it alone.
The loaders never delete a manual row.

At the end the whole table goes through `footy.europe.euro_integrity`; any error makes the job
`partial`.
"""

import csv
import json
import os
import subprocess
from collections import Counter, defaultdict
from dataclasses import dataclass, field
from datetime import date, datetime, timedelta
from pathlib import Path

from sqlalchemy import Engine, delete, select
from sqlalchemy.orm import Session

from footy.config import PROJECT_ROOT, Settings
from footy.europe.openfootball import FILES, PARSER_VERSION, SOURCE, OFParse, parse_openfootball
from footy.ingest.fd_import import _replace_issues, alias_map, sync_reference
from footy.ingest.fd_parse import Issue
from footy.ingest.raw_store import latest_revision, read_raw, store_raw
from footy.models import DataCorrection, EuroMatch, JobRun, RawFile, RawImport, utcnow

WIKI_SOURCE = "wikipedia"
WIKI_SEASON = "2627"
MANUAL_SOURCE = "manual"  # footy.europe.manual_results (DECISIONS.md D-029)
# A job still "running" after this long was killed (container restart): no job takes an hour.
STALE_RUN_AFTER = timedelta(hours=6)
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
    kept_rows: int = 0  # stale rows not deleted (load errors, or no correction possible)
    manual_confirmed: int = 0  # manual results the source now gives too
    manual_corrected: int = 0  # manual results the source gives differently (flagged)
    manual_kept: int = 0  # manual results the source has no result for yet
    issues: Counter = field(default_factory=Counter)
    disagreements: list[str] = field(default_factory=list)

    def as_dict(self) -> dict:
        out = {
            "inserted": self.inserted,
            "updated": self.updated,
            "relinked": self.relinked,
            "unchanged": self.unchanged,
            "deleted": self.deleted,
            "corrections": self.corrections,
            "skipped_rows": self.skipped_rows,
            "issues": dict(sorted(self.issues.items())),
        }
        for name in ("kept_rows", "manual_confirmed", "manual_corrected", "manual_kept"):
            if getattr(self, name):
                out[name] = getattr(self, name)
        if self.disagreements:
            out["manual_disagreements"] = self.disagreements
        return out


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


def apply_wikipedia_checks(session: Session, raw_dir: Path, parsed_by_raw: dict) -> list:
    """Compare the parsed files with the stored Wikipedia check pages, take Wikipedia's
    regulation score, flags or date where the two disagree and Wikipedia's box is consistent
    (footy.europe.of_crosscheck), then re-run the tie checks of the files changed."""
    from footy.europe.of_crosscheck import apply_corrections, crosscheck
    from footy.europe.openfootball import OFIssue, check_ties

    by_key = {(p.competition, p.season): p.matches for p in parsed_by_raw.values()}
    result = crosscheck(session, raw_dir, by_key)
    corrections = []
    for parsed in parsed_by_raw.values():
        fixed = apply_corrections(parsed.matches, result.comparisons)
        for c in fixed:
            parsed.issues.append(OFIssue("warning", "corrected_from_wikipedia", c.detail, c.line))
        if fixed:
            check_ties(parsed)
        corrections += fixed
    return corrections


def openfootball_rows(
    raw: RawFile, parsed: OFParse, aliases: dict[str, str]
) -> tuple[list[EuroRowIn], list[Issue]]:
    issues = [Issue(i.line, i.severity, i.code, i.detail) for i in parsed.issues]
    corrected = {i.line for i in parsed.issues if i.code == "corrected_from_wikipedia"}
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
                source_ref=f"{folder}/{file_name} line {m.line}"
                + ("; corrected from Wikipedia (see import_issues)" if m.line in corrected else ""),
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
            elif r["status"] in ("awarded", "cancelled"):  # as openfootball's (D-023)
                exclusion = r["status"]
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
    """Make the stored rows of one (source, competition, season) equal to `rows`.

    Rows another source owns for the same (competition, season) are left alone, except a
    manual result (`MANUAL_SOURCE`) on a key `rows` also has: see `_meet_manual`."""
    now = now or utcnow()
    existing: dict[tuple, EuroMatch] = {}
    manual: dict[tuple, EuroMatch] = {}
    for m in session.scalars(
        select(EuroMatch).where(
            EuroMatch.source.in_((source, MANUAL_SOURCE)),
            EuroMatch.competition == competition,
            EuroMatch.season == season,
        )
    ):
        key = (m.match_date, m.home_team, m.away_team)
        (existing if m.source == source else manual)[key] = m
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
        if current is None and key in manual:
            issues += _meet_manual(session, source, manual[key], row, stats, now)
            continue
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
    if stale and not allow_delete:
        stats.kept_rows += len(stale)
        issues.append(
            Issue(0, "warning", "stale_rows_kept", f"{len(stale)} stored rows not in this load")
        )
        stale = []
    doomed = []
    for m in stale:
        if m.status == "scheduled":
            doomed.append(m.id)
        elif _record_deletion(session, m, now):
            doomed.append(m.id)
            stats.corrections += 1
        else:
            stats.kept_rows += 1
            issues.append(
                Issue(0, "error", "result_row_kept", f"#{m.id} {_describe(m)}: no raw revision")
            )
    if doomed:
        session.execute(delete(EuroMatch).where(EuroMatch.id.in_(doomed)))
        stats.deleted += len(doomed)
    session.flush()
    return issues


def _describe(m: EuroMatch) -> str:
    score = "" if m.home_goals is None else f" {m.home_goals}-{m.away_goals}"
    return (
        f"{m.competition} {m.season} {m.stage} {m.match_date} {m.home_team} v {m.away_team} "
        f"{m.status}{score}"
    )


def _record_deletion(session: Session, m: EuroMatch, now: datetime) -> bool:
    """Write the `data_corrections` entry for deleting a row that has a result; False when it
    can't be written (no raw revision to name), in which case the row must stay."""
    if m.raw_file_id is None:
        return False
    raw = session.get(RawFile, m.raw_file_id)
    newest = latest_revision(session, raw.source, raw.logical_key) if raw else None
    session.add(
        DataCorrection(
            entity="euro_match",
            entity_id=m.id,
            field="deleted",
            old_value=_describe(m),
            new_value=None,
            old_raw_file_id=m.raw_file_id,
            new_raw_file_id=newest.id if newest else m.raw_file_id,
            detected_at=now,
        )
    )
    return True


def _meet_manual(
    session: Session, source: str, current: EuroMatch, row: EuroRowIn, stats: LoadStats, now
) -> list[Issue]:
    """A source row on the key of a manual result (D-029).

    - No result in the source yet (scheduled, or played without a regulation score): the manual
      row stays as it is.
    - The same result: the source takes the row over (its kick-off, stage, reference and raw
      revision), so the result is now the source's.
    - Another result: the source's result replaces the manual one, each changed field with a
      `data_corrections` entry, and the disagreement is reported as an error for a person to
      look at (a settlement made from the manual score is not changed by this)."""
    has_result = row.status != "scheduled" and not (
        row.status == "played" and (row.home_goals is None or row.away_goals is None)
    )
    if not has_result:
        stats.manual_kept += 1
        return []
    fields = ("status", "home_goals", "away_goals", "extra_time", "penalties")
    changed = [f for f in fields if getattr(current, f) != getattr(row, f)]
    issues: list[Issue] = []
    if changed:
        if row.raw_file_id is None or current.raw_file_id is None:
            stats.manual_kept += 1
            return [
                Issue(
                    0,
                    "error",
                    "manual_result_disagrees",
                    f"#{current.id} {_describe(current)}: {source} has "
                    f"{row.status} {row.home_goals}-{row.away_goals}, not applied "
                    "(no raw revision to record the correction against)",
                )
            ]
        for name in changed:
            old, new = getattr(current, name), getattr(row, name)
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
        text = (
            f"#{current.id} {_describe(current)} (manual) but {source} has {row.status} "
            f"{row.home_goals}-{row.away_goals}: {source}'s result taken"
        )
        stats.disagreements.append(text)
        issues.append(Issue(0, "error", "manual_result_disagrees", text))
        stats.manual_corrected += 1
    else:
        stats.manual_confirmed += 1
    current.source = source
    for name, new in row.values().items():
        setattr(current, name, new)
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
    interrupted = mark_interrupted_runs(engine)
    # One client for every fetch of the job, so that one refusal stops them all.
    client = _wiki_client() if fetch_wikipedia else None
    with job_run(engine, "euro_update") as ctx:
        if interrupted:
            ctx.detail["interrupted_runs"] = interrupted
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
            parsed_by_raw = {}
            for raw in raws:
                season, file_name = raw.logical_key.split("/")
                content = read_raw(settings.raw_dir, raw)
                parsed_by_raw[raw.id] = parse_openfootball(content, file_name, season)
            corrected = apply_wikipedia_checks(s, settings.raw_dir, parsed_by_raw)
        ctx.detail["openfootball_corrections"] = {
            "by_field": dict(Counter(f for c in corrected for f in c.fields)),
            "scores_and_dates": [
                f"{c.competition} {c.season} line {c.line}: {c.detail}"
                for c in corrected
                if set(c.fields) - {"neutral"}
            ],
        }
        for raw in raws:
            with Session(engine) as s:
                raw = s.get(RawFile, raw.id)
                season, file_name = raw.logical_key.split("/")
                parsed = parsed_by_raw[raw.id]
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
                if any(i.severity == "error" for i in issues):
                    ctx.partial = True
        ctx.detail["openfootball"] = of_stats.as_dict() | {"files": len(raws)}

        # 2. Seasons openfootball lacks (2025/26 UEL and UECL), from stored Wikipedia pages.
        ctx.detail["wikipedia_gap"] = _load_gap_seasons(engine, settings, ctx, client)

        # 3. Wikipedia 2026/27: optionally re-fetch, rebuild the CSV, load it.
        ctx.detail["wikipedia"] = _load_wikipedia(
            engine, settings, ctx, csv_path, rebuild_2627, client, fetch_uefa
        )

        with Session(engine) as s:
            ctx.detail["table"] = summarise_table(s)
            ctx.detail["integrity"] = integrity_summary(s)
        if ctx.detail["integrity"]["errors"]:
            ctx.partial = True
        return ctx.detail


def _wiki_client():
    from footy.europe.wiki_fetch import WikiClient

    return WikiClient()


def integrity_summary(session: Session, now: datetime | None = None) -> dict:
    """`footy.europe.euro_integrity` over the whole table: counts per check, and the errors."""
    from footy.europe.euro_integrity import ERROR, WARNING, check_database

    report = check_database(session, now)
    errors = [f for f in report.findings if f.severity == ERROR]
    return {
        "errors": len(errors),
        "warnings": sum(f.severity == WARNING for f in report.findings),
        "checks": {f"{sev} {check}": n for (sev, check), n in sorted(report.counts().items())},
        "error_findings": [f"{f.check}: {f.detail}" for f in errors[:20]],
    }


def mark_interrupted_runs(
    engine: Engine, *, older_than: timedelta = STALE_RUN_AFTER, now: datetime | None = None
) -> list[int]:
    """Mark job runs (of any job) still "running" long after they started as "interrupted":
    their process was killed (the container restarts every hour or two) before `job_run` could
    record an outcome. Returns their ids."""
    now = now or utcnow()
    with Session(engine) as s:
        runs = s.scalars(
            select(JobRun).where(JobRun.status == "running", JobRun.started_at < now - older_than)
        ).all()
        for run in runs:
            run.status = "interrupted"
            hours = older_than.total_seconds() / 3600
            run.error = (
                f"still 'running' at {now:%Y-%m-%d %H:%M} UTC, over {hours:g} h after it "
                "started: the process was killed before it could record an outcome"
            )
        s.commit()
        return [r.id for r in runs]


def _load_gap_seasons(engine, settings, ctx, client) -> dict:
    """2025/26 Europa League and Conference League from Wikipedia (footy.europe.wikipedia_seasons).
    With a `client`, pages not yet in the raw store are fetched first (completed seasons are
    fetched once)."""
    from footy.europe.wikipedia_seasons import GAP_PAGES, gap_matches

    detail: dict = {}
    if client is not None:
        from footy.europe.build_2627 import fetch_wikipedia as fetch_pages

        with Session(engine) as s:
            todo = [
                p for p in GAP_PAGES if latest_revision(s, WIKI_SOURCE, p.page.logical_key) is None
            ]
        if todo:
            fetched = fetch_pages(
                engine, settings.raw_dir, client=client, specs=todo, job_run_id=ctx.run_id
            )
            detail["fetch"] = dict(Counter(r.outcome for r in fetched))
            failed = [r for r in fetched if r.outcome in ("error", "skipped")]
            if failed:
                detail["fetch_errors"] = [f"{r.logical_key}: {r.error}" for r in failed]
                ctx.partial = True
    stats = LoadStats()
    with Session(engine) as s:
        result = gap_matches(s, settings.raw_dir)
        aliases = alias_map(s, WIKI_SOURCE)
        by_raw: dict[int, list[Issue]] = defaultdict(list)
        for raw_id, severity, code, text in result.issues:
            stats.issues[code] += 1
            if raw_id is not None:
                by_raw[raw_id].append(Issue(0, severity, code, text))
        groups: dict[tuple[str, str], list[EuroRowIn]] = defaultdict(list)
        for m in result.matches:
            missing = [a for a in (m.home_alias, m.away_alias) if a not in aliases]
            if missing:
                by_raw[m.raw_file_id].append(Issue(0, "error", "unmapped_team", ", ".join(missing)))
                stats.issues["unmapped_team"] += 1
                stats.skipped_rows += 1
                continue
            groups[(m.competition, m.season)].append(
                EuroRowIn(
                    competition=m.competition,
                    season=m.season,
                    stage=m.stage,
                    match_date=m.match_date,
                    kickoff_utc=m.kickoff_utc,
                    home_team=aliases[m.home_alias],
                    away_team=aliases[m.away_alias],
                    home_goals=m.home_goals,
                    away_goals=m.away_goals,
                    extra_time=m.extra_time,
                    penalties=m.penalties,
                    neutral=m.neutral,
                    status=m.status,
                    source=WIKI_SOURCE,
                    source_ref=m.source_ref,
                    raw_file_id=m.raw_file_id,
                    model_exclusion=m.model_exclusion,
                )
            )
        has_errors = any(i.severity == "error" for issues in by_raw.values() for i in issues)
        has_errors = has_errors or any(sev == "error" for _, sev, _, _ in result.issues)
        complete = "missing" not in result.pages.values()
        for (comp, season), rows in sorted(groups.items()):
            extra = upsert_group(
                s, WIKI_SOURCE, comp, season, rows, stats, allow_delete=complete and not has_errors
            )
            stats.issues.update(i.code for i in extra)
            has_errors = has_errors or any(i.severity == "error" for i in extra)
        raw_ids = {m.raw_file_id for m in result.matches} | set(by_raw)
        for raw_id in sorted(raw_ids):
            _replace_issues(s, raw_id, by_raw.get(raw_id, []))
        s.commit()
    if has_errors:
        ctx.partial = True
    detail |= stats.as_dict() | {"pages": result.pages, "rows": len(result.matches)}
    return detail


def _load_wikipedia(engine, settings, ctx, csv_path, rebuild, client, fetch_uefa) -> dict:
    detail: dict = {}
    fetch = client is not None
    if fetch or fetch_uefa:
        from footy.europe.build_2627 import fetch_uefa_pages, fetch_wikipedia
        from footy.europe.wikipedia_2627 import build_uefa_2627

        if fetch:
            fetched = fetch_wikipedia(
                engine, settings.raw_dir, client=client, job_run_id=ctx.run_id
            )
            detail["fetch_wikipedia"] = dict(Counter(r.outcome for r in fetched))
            failed = [r for r in fetched if r.outcome in ("error", "skipped")]
            if failed:  # the job carries on with the stored pages, but says so
                detail["fetch_errors"] = [
                    f"{r.logical_key}: {(r.error or '')[:300]}" for r in failed
                ]
                ctx.partial = True
        if fetch_uefa:
            with Session(engine) as s:
                rows = build_uefa_2627(s, settings.raw_dir).rows
            fetched = fetch_uefa_pages(engine, settings.raw_dir, rows)
            detail["fetch_uefa"] = dict(Counter(r.outcome for r in fetched))
    # Any build-stage error (a box or page the parser could not read, a missing page, a
    # failed rebuild) means the CSV may lack rows the source still has: nothing is deleted.
    from footy.europe.build_2627 import read_build_errors

    build_errors = False
    if rebuild:
        from footy.europe.build_2627 import build_all

        try:
            with Session(engine) as s:
                built = build_all(s, settings.raw_dir, settings.data_dir)
            detail["rebuilt_rows"] = len(built.wikipedia.rows)
            detail["rebuild_issues"] = dict(Counter(i.code for i in built.wikipedia.issues))
            if problems := built.problems():
                build_errors = True
                detail["rebuild_errors"] = problems[:20]
        except Exception as exc:  # the stored CSV is still loaded; the failure is reported
            detail["rebuild_error"] = f"{type(exc).__name__}: {exc}"
            build_errors = True
    # A CSV that an earlier build with errors wrote (loaded as it is, or because this rebuild
    # failed) lacks the same rows: its marker says so (footy.europe.build_2627).
    if not build_errors and (earlier := read_build_errors(csv_path)) is not None:
        build_errors = True
        detail["csv_build_errors"] = earlier[:20]
    if build_errors:
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
        has_errors = build_errors or any(i.severity == "error" for _, i in pairs)
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
    if has_errors or any(i.severity == "error" for i in extra):
        ctx.partial = True
    linked = sum(1 for r in rows if r.raw_file_id is not None)
    detail |= stats.as_dict() | {"csv": str(csv_path), "rows": len(rows), "raw_linked": linked}
    return detail
