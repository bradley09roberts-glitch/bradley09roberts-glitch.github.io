"""football-data refresh: download (if enabled), take in hand-dropped files, then import.

Covers both layouts: per-season main-layout files (the core ten divisions and the European
main-layout leagues) and the one-file-per-league "extra leagues" (DECISIONS.md D-022).
"""

import re
from collections import Counter
from datetime import date

from sqlalchemy import Engine
from sqlalchemy.orm import Session

from footy.config import Settings
from footy.ingest.fd_download import (
    EXTRA_SOURCE,
    SOURCE,
    PoliteClient,
    extra_logical_key,
    fetch_extra_league_file,
    fetch_season_file,
    logical_key,
)
from footy.ingest.jobs import JobContext
from footy.ingest.raw_store import store_raw
from footy.models import FetchLog, utcnow
from footy.reference import (
    CORE_DIVISIONS,
    DIVISIONS,
    EUROPE_DIVISIONS,
    EXTRA_LAYOUT_DIVISIONS,
    MAIN_LAYOUT_DIVISIONS,
)
from footy.seasons import seasons_through

INBOX_NAME = re.compile(r"^(?P<season>\d{4})_(?P<division>[A-Z0-9]+)\.csv$")
# Hand-downloaded extra-league files: new_<CODE>.csv (e.g. new_AUT.csv).
INBOX_EXTRA_NAME = re.compile(r"^new_(?P<code>[A-Z]+)\.csv$")
SCOPES = {"all": list(DIVISIONS), "core": CORE_DIVISIONS, "europe": EUROPE_DIVISIONS}


def download(
    engine: Engine,
    settings: Settings,
    ctx: JobContext,
    *,
    today: date,
    divisions: list[str] | None = None,
    seasons: list[str] | None = None,
    force: bool = False,
    client: PoliteClient | None = None,
) -> Counter:
    divisions = [d for d in (divisions or list(DIVISIONS)) if d in MAIN_LAYOUT_DIVISIONS]
    seasons = seasons or seasons_through(today)
    client = client or PoliteClient()
    outcomes: Counter = Counter()
    for season in seasons:
        for division in divisions:
            # One transaction per file, so a failure part-way keeps what already succeeded.
            with Session(engine) as s:
                res = fetch_season_file(
                    s,
                    client,
                    settings.raw_dir,
                    season=season,
                    division=division,
                    today=today,
                    job_run_id=ctx.run_id,
                    force=force,
                )
                s.commit()
            outcomes[res.outcome] += 1
            if res.outcome in ("error", "not_found"):
                ctx.partial = True
                ctx.detail.setdefault("download_problems", []).append(
                    {"file": res.logical_key, "outcome": res.outcome, "error": res.error}
                )
    ctx.detail["download"] = dict(outcomes)
    return outcomes


def download_extra(
    engine: Engine,
    settings: Settings,
    ctx: JobContext,
    *,
    divisions: list[str] | None = None,
    force: bool = False,
    client: PoliteClient | None = None,
) -> Counter:
    """Fetch the extra-league files (one per league, every season) through the same client,
    so the politeness gap holds across both layouts."""
    codes = [d for d in (divisions or list(DIVISIONS)) if d in EXTRA_LAYOUT_DIVISIONS]
    client = client or PoliteClient()
    outcomes: Counter = Counter()
    for code in codes:
        with Session(engine) as s:
            res = fetch_extra_league_file(
                s, client, settings.raw_dir, code=code, job_run_id=ctx.run_id, force=force
            )
            s.commit()
        outcomes[res.outcome] += 1
        if res.outcome in ("error", "not_found"):
            ctx.partial = True
            ctx.detail.setdefault("download_problems", []).append(
                {"file": res.logical_key, "outcome": res.outcome, "error": res.error}
            )
    ctx.detail["download_extra"] = dict(outcomes)
    return outcomes


def take_inbox(engine: Engine, settings: Settings, ctx: JobContext) -> Counter:
    """Store hand-downloaded files from the inbox as raw revisions. Files are left in place."""
    outcomes: Counter = Counter()
    inbox = settings.fd_inbox_dir
    if not inbox.is_dir():
        return outcomes
    for path in sorted(inbox.glob("*.csv")):
        m = INBOX_NAME.match(path.name)
        x = INBOX_EXTRA_NAME.match(path.name)
        if m and m["division"] in MAIN_LAYOUT_DIVISIONS:
            source, key = SOURCE, logical_key(m["season"], m["division"])
        elif x and x["code"] in EXTRA_LAYOUT_DIVISIONS:
            source, key = EXTRA_SOURCE, extra_logical_key(x["code"])
        else:
            outcomes["ignored"] += 1
            ctx.detail.setdefault("inbox_ignored", []).append(path.name)
            continue
        with Session(engine) as s:
            stored = store_raw(
                s,
                settings.raw_dir,
                source=source,
                logical_key=key,
                content=path.read_bytes(),
                retrieved_at=utcnow(),
                url=None,
                suffix=".csv",
            )
            outcome = "inbox_new" if stored.created else "inbox_same"
            s.add(
                FetchLog(
                    job_run_id=ctx.run_id,
                    source=source,
                    logical_key=key,
                    url=f"inbox:{path.name}",
                    requested_at=utcnow(),
                    outcome=outcome,
                    raw_file_id=stored.raw.id,
                )
            )
            s.commit()
        outcomes[outcome] += 1
    ctx.detail["inbox"] = dict(outcomes)
    return outcomes


def run_fd_update(
    engine: Engine,
    settings: Settings,
    *,
    today: date,
    divisions: list[str] | None = None,
    seasons: list[str] | None = None,
    force: bool = False,
    reimport: bool = False,
    allow_download: bool = True,
    client: PoliteClient | None = None,
    reference: tuple | None = None,
    scope: str = "all",
) -> dict:
    """The whole refresh as one logged job. Returns the job's detail summary.

    `scope` ("all", "core" or "europe") picks the divisions when `divisions` is not given.
    Extra-league files hold every season, so `seasons` does not narrow them.
    `reference` = (teams, aliases) replaces the committed reference tables (tests only)."""
    from footy.ingest.fd_import import (
        UnmappedTeams,
        import_extra_raw_file,
        import_raw_file,
        pending_extra_raw_files,
        pending_raw_files,
        summarise,
        sync_reference,
    )
    from footy.ingest.fd_parse import Issue
    from footy.ingest.jobs import job_run
    from footy.models import RawFile

    wanted = divisions or SCOPES[scope]
    with job_run(engine, "fd_update") as ctx:
        if settings.fd_download and allow_download:
            client = client or PoliteClient()
            download(
                engine,
                settings,
                ctx,
                today=today,
                divisions=wanted,
                seasons=seasons,
                force=force,
                client=client,
            )
            download_extra(engine, settings, ctx, divisions=wanted, force=force, client=client)
        else:
            ctx.detail["download"] = "disabled"
        take_inbox(engine, settings, ctx)

        with Session(engine) as s:
            sync_reference(s, *(reference or (None, None)))
            s.commit()
            todo = [(r.id, False) for r in pending_raw_files(s, reimport=reimport)]
            todo += [(r.id, True) for r in pending_extra_raw_files(s, reimport=reimport)]

        stats = []
        for raw_id, extra in todo:
            with Session(engine) as s:
                raw = s.get(RawFile, raw_id)
                if extra:
                    season, division = None, raw.logical_key.split("/")[1]
                else:
                    season, division = raw.logical_key.split("/")
                if division not in wanted or (season and seasons and season not in seasons):
                    continue
                importer = import_extra_raw_file if extra else import_raw_file
                try:
                    stats.append(
                        importer(s, settings.raw_dir, raw, job_run_id=ctx.run_id, reimport=reimport)
                    )
                    s.commit()
                except UnmappedTeams as exc:
                    s.rollback()
                    from footy.ingest.fd_import import _record_issues

                    _record_issues(s, raw_id, [Issue(0, "error", "unmapped_teams", str(exc))])
                    s.commit()
                    ctx.partial = True
                    ctx.detail.setdefault("import_failures", []).append(
                        {"file": raw.logical_key, "error": str(exc)}
                    )
        ctx.detail["import"] = summarise(stats)
    return ctx.detail
