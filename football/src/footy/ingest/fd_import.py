"""Load parsed football-data files into `matches` and `fd_odds`.

Idempotent: re-importing the same raw revision changes nothing. When a newer revision of a
file changes a stored value, the change is applied and written to `data_corrections`.
Main-layout files hold one division-season each; extra-league files hold every season of
one league and are imported season by season with the same rules (D-022).
"""

from collections import Counter
from dataclasses import dataclass
from pathlib import Path

from sqlalchemy import delete, func, insert, select
from sqlalchemy.orm import Session

from footy.ingest.fd_download import EXTRA_SOURCE, SOURCE
from footy.ingest.fd_extra_parse import EXTRA_PARSER_VERSION, parse_fd_extra_csv
from footy.ingest.fd_parse import PARSER_VERSION, Issue, ParsedMatch, parse_fd_csv
from footy.ingest.raw_store import read_raw
from footy.models import (
    DataCorrection,
    Division,
    FdOdds,
    ImportIssue,
    Match,
    RawFile,
    RawImport,
    Team,
    TeamAlias,
    utcnow,
)
from footy.reference import DIVISIONS, TOP_DIVISIONS, load_aliases, load_teams
from footy.seasons import season_label

MATCH_FIELDS = [
    "match_date",
    "kickoff_local",
    "kickoff_utc",
    "home_goals",
    "away_goals",
    "result",
    "ht_home_goals",
    "ht_away_goals",
    "ht_result",
    "referee",
    "fd_home_xg",
    "fd_away_xg",
    "model_exclusion",
    "home_shots",
    "away_shots",
    "home_sot",
    "away_sot",
    "home_corners",
    "away_corners",
    "home_fouls",
    "away_fouls",
    "home_yellow",
    "away_yellow",
    "home_red",
    "away_red",
    "source_row",
]
ODDS_FIELDS = ["price", "line", "quote_time_utc", "quote_time_basis", "source_column"]


class UnmappedTeams(Exception):
    def __init__(self, names: set[str]):
        super().__init__(f"team names not in reference/team_aliases.csv: {sorted(names)}")
        self.names = names


@dataclass
class ImportStats:
    raw_file_id: int
    logical_key: str
    skipped: bool = False
    matches_inserted: int = 0
    matches_updated: int = 0
    odds_inserted: int = 0
    odds_updated: int = 0
    odds_deleted: int = 0
    corrections: int = 0
    rows_rejected: int = 0
    issues: int = 0


def sync_reference(session: Session, teams=None, aliases=None) -> None:
    """Make divisions, teams and aliases in the database match the committed reference files
    (or the given rows, for tests)."""
    for d in DIVISIONS.values():
        session.merge(Division(code=d.code, name=d.name, country=d.country, tier=d.tier))
    for t in teams if teams is not None else load_teams():
        session.merge(Team(team_key=t.team_key, name=t.name, country=t.country))
    session.flush()
    session.execute(delete(TeamAlias))
    for a in aliases if aliases is not None else load_aliases():
        session.add(TeamAlias(source=a.source, alias=a.alias, team_key=a.team_key))
    session.flush()


def alias_map(session: Session, source: str = SOURCE) -> dict[str, str]:
    rows = session.execute(
        select(TeamAlias.alias, TeamAlias.team_key).where(TeamAlias.source == source)
    )
    return dict(rows.all())


def import_raw_file(
    session: Session, raw_dir: Path, raw: RawFile, *, job_run_id: int | None, reimport=False
) -> ImportStats:
    season, division = raw.logical_key.split("/")
    stats = ImportStats(raw.id, raw.logical_key)
    done = session.get(RawImport, (raw.id, PARSER_VERSION))
    if done is not None and not reimport:
        stats.skipped = True
        return stats

    parsed = parse_fd_csv(
        read_raw(raw_dir, raw),
        division,
        with_odds=division in TOP_DIVISIONS,
        repeats_expected=DIVISIONS[division].scope != "core",
    )
    aliases = alias_map(session)
    _check_mapped(session, raw.id, parsed.matches, aliases)

    now = utcnow()
    odds_by_match, found = _upsert_matches(
        session, raw.id, division, season, parsed.matches, aliases, now, stats
    )
    _upsert_odds(session, raw.id, odds_by_match, now, stats)

    issues = list(parsed.issues) + found
    stats.issues = _replace_issues(session, raw.id, issues)
    stats.rows_rejected = _rows_rejected(issues)
    if done is None:
        session.add(
            RawImport(
                raw_file_id=raw.id,
                parser_version=PARSER_VERSION,
                imported_at=now,
                job_run_id=job_run_id,
                rows_imported=len(parsed.matches) - _rows_rejected(found),
                rows_rejected=stats.rows_rejected,
            )
        )
    session.flush()
    return stats


def import_extra_raw_file(
    session: Session, raw_dir: Path, raw: RawFile, *, job_run_id: int | None, reimport=False
) -> ImportStats:
    """Import one revision of an extra-league file (every season of one league)."""
    division = raw.logical_key.split("/")[1]
    stats = ImportStats(raw.id, raw.logical_key)
    done = session.get(RawImport, (raw.id, EXTRA_PARSER_VERSION))
    if done is not None and not reimport:
        stats.skipped = True
        return stats

    parsed = parse_fd_extra_csv(read_raw(raw_dir, raw), division)
    aliases = alias_map(session)
    all_matches = [m for ms in parsed.seasons.values() for m in ms]
    _check_mapped(session, raw.id, all_matches, aliases)

    now = utcnow()
    issues = list(parsed.issues)
    stored_seasons = set(
        session.scalars(select(Match.season).where(Match.division == division).distinct())
    )
    for season in sorted(stored_seasons | set(parsed.seasons)):
        _, found = _upsert_matches(
            session,
            raw.id,
            division,
            season,
            parsed.seasons.get(season, []),
            aliases,
            now,
            stats,
            prefix=f"{season_label(season)}: ",
        )
        issues += found
    stats.issues = _replace_issues(session, raw.id, issues)
    stats.rows_rejected = _rows_rejected(issues)
    if done is None:
        session.add(
            RawImport(
                raw_file_id=raw.id,
                parser_version=EXTRA_PARSER_VERSION,
                imported_at=now,
                job_run_id=job_run_id,
                rows_imported=len(all_matches) - _rows_rejected(issues[len(parsed.issues) :]),
                rows_rejected=stats.rows_rejected,
            )
        )
    session.flush()
    return stats


def _rows_rejected(issues: list[Issue]) -> int:
    return sum(
        1
        for i in issues
        if i.severity == "error" and i.source_row > 0 and i.code in REJECTING_CODES
    )


def _check_mapped(session, raw_id: int, matches: list[ParsedMatch], aliases: dict) -> None:
    unmapped = {name for m in matches for name in (m.home_name, m.away_name) if name not in aliases}
    if unmapped:
        _record_issues(
            session, raw_id, [Issue(0, "error", "unmapped_teams", ", ".join(sorted(unmapped)))]
        )
        raise UnmappedTeams(unmapped)


def _upsert_matches(
    session, raw_id: int, division: str, season: str, parsed, aliases, now, stats, prefix=""
) -> tuple[list[tuple[int, ParsedMatch]], list[Issue]]:
    """Insert or update one division-season's matches from one raw revision. Returns the
    (match id, parsed match) pairs for the odds step, and the issues found here: a pairing
    that recurs once names are mapped to teams (two spellings of one club in a season), and
    a warning for each stored match that this revision no longer contains."""
    existing = {
        (m.home_team, m.away_team): m
        for m in session.scalars(
            select(Match).where(Match.division == division, Match.season == season)
        )
    }
    seen_ids: set[int] = set()
    odds_by_match: list[tuple[int, ParsedMatch]] = []
    first: dict[tuple[str, str], ParsedMatch] = {}
    issues: list[Issue] = []

    for pm in parsed:
        home, away = aliases[pm.home_name], aliases[pm.away_name]
        if (home, away) in first:
            prev = first[(home, away)]
            same_day = prev.match_date == pm.match_date
            issues.append(
                Issue(
                    pm.source_row,
                    "error",
                    "duplicate_fixture" if same_day else "repeat_pairing",
                    f"{prefix}{pm.home_name} v {pm.away_name} on {pm.match_date} is the same "
                    f"pairing of teams as row {prev.source_row} ({prev.match_date}); held back, "
                    "one row per pairing per season in matches",
                )
            )
            continue
        first[(home, away)] = pm
        values = _match_values(pm)
        current = existing.get((home, away))
        if current is None:
            current = Match(
                division=division,
                season=season,
                home_team=home,
                away_team=away,
                first_raw_file_id=raw_id,
                last_raw_file_id=raw_id,
                **values,
            )
            session.add(current)
            session.flush()
            stats.matches_inserted += 1
        else:
            changed = _apply_changes(
                session, "match", current, values, current.last_raw_file_id, raw_id, now, stats
            )
            if changed or current.last_raw_file_id != raw_id:
                current.last_raw_file_id = raw_id
                stats.matches_updated += int(changed)
        seen_ids.add(current.id)
        odds_by_match.append((current.id, pm))

    issues += [
        Issue(0, "warning", "match_missing_from_revision", f"{home} v {away} (match {m.id})")
        for (home, away), m in existing.items()
        if m.id not in seen_ids
    ]
    return odds_by_match, issues


# Row-level error codes that drop the whole row (others drop a single value).
REJECTING_CODES = {
    "wrong_division",
    "wrong_country",
    "missing_team",
    "bad_date",
    "bad_season",
    "no_result",
    "result_mismatch",
    "duplicate_fixture",
    "repeat_pairing",
}


def _match_values(pm: ParsedMatch) -> dict:
    v = {
        "match_date": pm.match_date,
        "kickoff_local": pm.kickoff_local,
        "kickoff_utc": pm.kickoff_utc,
        "home_goals": pm.home_goals,
        "away_goals": pm.away_goals,
        "result": pm.result,
        "ht_home_goals": pm.ht_home_goals,
        "ht_away_goals": pm.ht_away_goals,
        "ht_result": pm.ht_result,
        "referee": pm.referee,
        "fd_home_xg": pm.fd_home_xg,
        "fd_away_xg": pm.fd_away_xg,
        "model_exclusion": pm.model_exclusion,
        "source_row": pm.source_row,
    }
    v.update(pm.stats)
    return v


def _apply_changes(session, entity, obj, values, old_raw_id, new_raw_id, now, stats) -> bool:
    changed = False
    for field, new in values.items():
        old = getattr(obj, field)
        if old == new:
            continue
        changed = True
        setattr(obj, field, new)
        # A different raw revision means the source changed; the same revision re-parsed with a
        # new parser version is a code change and is not a data correction.
        if field != "source_row" and old_raw_id != new_raw_id:
            session.add(
                DataCorrection(
                    entity=entity,
                    entity_id=obj.id,
                    field=field,
                    old_value=None if old is None else str(old),
                    new_value=None if new is None else str(new),
                    old_raw_file_id=old_raw_id,
                    new_raw_file_id=new_raw_id,
                    detected_at=now,
                )
            )
            stats.corrections += 1
    return changed


def _upsert_odds(session, raw_id, odds_by_match, now, stats) -> None:
    match_ids = [mid for mid, _ in odds_by_match]
    existing: dict[tuple, FdOdds] = {}
    for chunk in _chunks(match_ids, 500):
        for o in session.scalars(select(FdOdds).where(FdOdds.match_id.in_(chunk))):
            existing[(o.match_id, o.bookmaker, o.market_key, o.selection, o.phase)] = o
    new_rows = []
    present: set[tuple] = set()
    for mid, pm in odds_by_match:
        for po in pm.odds:
            key = (mid, po.bookmaker, po.market_key, po.selection, po.phase)
            present.add(key)
            values = {f: getattr(po, f) for f in ODDS_FIELDS}
            current = existing.get(key)
            if current is None:
                new_rows.append(
                    {
                        "match_id": mid,
                        "bookmaker": po.bookmaker,
                        "market_key": po.market_key,
                        "selection": po.selection,
                        "phase": po.phase,
                        "raw_file_id": raw_id,
                        **values,
                    }
                )
            else:
                if _apply_changes(
                    session, "fd_odds", current, values, current.raw_file_id, raw_id, now, stats
                ):
                    stats.odds_updated += 1
                current.raw_file_id = raw_id
    # A price present before but blank in this revision: log it as corrected to nothing.
    for key, o in existing.items():
        if key in present:
            continue
        if o.raw_file_id != raw_id:
            session.add(
                DataCorrection(
                    entity="fd_odds",
                    entity_id=o.id,
                    field="price",
                    old_value=str(o.price),
                    new_value=None,
                    old_raw_file_id=o.raw_file_id,
                    new_raw_file_id=raw_id,
                    detected_at=now,
                )
            )
            stats.corrections += 1
        session.delete(o)
        stats.odds_deleted += 1
    if new_rows:
        for chunk in _chunks(new_rows, 5000):
            session.execute(insert(FdOdds), chunk)
        stats.odds_inserted += len(new_rows)


def _replace_issues(session, raw_id: int, issues: list[Issue]) -> int:
    """Issues describe what the current parser did with a file, so they are rewritten on
    each import of that file."""
    session.execute(delete(ImportIssue).where(ImportIssue.raw_file_id == raw_id))
    return _record_issues(session, raw_id, issues)


def _record_issues(session, raw_id: int, issues: list[Issue]) -> int:
    known = set(
        session.execute(
            select(ImportIssue.source_row, ImportIssue.code, ImportIssue.detail).where(
                ImportIssue.raw_file_id == raw_id
            )
        ).all()
    )
    now = utcnow()
    added = 0
    for i in issues:
        key = (i.source_row, i.code, i.detail)
        if key in known:
            continue
        known.add(key)
        session.add(
            ImportIssue(
                raw_file_id=raw_id,
                source_row=i.source_row,
                severity=i.severity,
                code=i.code,
                detail=i.detail,
                created_at=now,
            )
        )
        added += 1
    session.flush()
    return added


def pending_raw_files(session: Session, reimport: bool = False) -> list[RawFile]:
    """Revisions to import, oldest first per file so corrections replay in order.

    Normally: every football-data revision this parser version hasn't imported yet.
    With reimport: only the latest revision of each file (re-applying older revisions would
    flip values back and forth and log false corrections)."""
    q = select(RawFile).where(RawFile.source == SOURCE)
    if reimport:
        last: dict[str, RawFile] = {}
        for r in session.scalars(q.order_by(RawFile.logical_key, RawFile.revision)):
            last[r.logical_key] = r
        return list(last.values())
    done = select(RawImport.raw_file_id).where(RawImport.parser_version == PARSER_VERSION)
    q = q.where(RawFile.id.not_in(done))
    return list(session.scalars(q.order_by(RawFile.logical_key, RawFile.revision)))


def pending_extra_raw_files(session: Session, reimport: bool = False) -> list[RawFile]:
    """As `pending_raw_files`, for the extra-league files."""
    q = select(RawFile).where(RawFile.source == EXTRA_SOURCE)
    if reimport:
        last: dict[str, RawFile] = {}
        for r in session.scalars(q.order_by(RawFile.logical_key, RawFile.revision)):
            last[r.logical_key] = r
        return list(last.values())
    done = select(RawImport.raw_file_id).where(RawImport.parser_version == EXTRA_PARSER_VERSION)
    q = q.where(RawFile.id.not_in(done))
    return list(session.scalars(q.order_by(RawFile.logical_key, RawFile.revision)))


def match_count(session: Session) -> int:
    return session.scalar(select(func.count()).select_from(Match)) or 0


def summarise(stats: list[ImportStats]) -> dict:
    c = Counter()
    for s in stats:
        if s.skipped:
            c["files_skipped"] += 1
            continue
        c["files_imported"] += 1
        for f in (
            "matches_inserted",
            "matches_updated",
            "odds_inserted",
            "odds_updated",
            "odds_deleted",
            "corrections",
            "rows_rejected",
            "issues",
        ):
            c[f] += getattr(s, f)
    return dict(c)


def _chunks(seq, n):
    for i in range(0, len(seq), n):
        yield seq[i : i + n]
