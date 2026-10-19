"""football-data refresh: download (if enabled), take in hand-dropped files, then import."""

import re
from collections import Counter
from datetime import date

from sqlalchemy import Engine
from sqlalchemy.orm import Session

from footy.config import Settings
from footy.ingest.fd_download import SOURCE, PoliteClient, fetch_season_file, logical_key
from footy.ingest.jobs import JobContext
from footy.ingest.raw_store import store_raw
from footy.models import FetchLog, utcnow
from footy.reference import DIVISIONS
from footy.seasons import seasons_through

INBOX_NAME = re.compile(r"^(?P<season>\d{4})_(?P<division>[A-Z0-9]+)\.csv$")


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
    divisions = divisions or list(DIVISIONS)
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


def take_inbox(engine: Engine, settings: Settings, ctx: JobContext) -> Counter:
    """Store hand-downloaded files from the inbox as raw revisions. Files are left in place."""
    outcomes: Counter = Counter()
    inbox = settings.fd_inbox_dir
    if not inbox.is_dir():
        return outcomes
    for path in sorted(inbox.glob("*.csv")):
        m = INBOX_NAME.match(path.name)
        if not m or m["division"] not in DIVISIONS:
            outcomes["ignored"] += 1
            ctx.detail.setdefault("inbox_ignored", []).append(path.name)
            continue
        key = logical_key(m["season"], m["division"])
        with Session(engine) as s:
            stored = store_raw(
                s,
                settings.raw_dir,
                source=SOURCE,
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
                    source=SOURCE,
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
) -> dict:
    """The whole refresh as one logged job. Returns the job's detail summary.

    `reference` = (teams, aliases) replaces the committed reference tables (tests only)."""
    from footy.ingest.fd_import import (
        UnmappedTeams,
        import_raw_file,
        pending_raw_files,
        summarise,
        sync_reference,
    )
    from footy.ingest.fd_parse import Issue
    from footy.ingest.jobs import job_run

    with job_run(engine, "fd_update") as ctx:
        if settings.fd_download and allow_download:
            download(
                engine,
                settings,
                ctx,
                today=today,
                divisions=divisions,
                seasons=seasons,
                force=force,
                client=client,
            )
        else:
            ctx.detail["download"] = "disabled"
        take_inbox(engine, settings, ctx)

        with Session(engine) as s:
            sync_reference(s, *(reference or (None, None)))
            s.commit()
            todo = pending_raw_files(s, reimport=reimport)
            todo_ids = [r.id for r in todo]

        stats = []
        for raw_id in todo_ids:
            with Session(engine) as s:
                from footy.models import RawFile

                raw = s.get(RawFile, raw_id)
                season, division = raw.logical_key.split("/")
                if (divisions and division not in divisions) or (seasons and season not in seasons):
                    continue
                try:
                    stats.append(
                        import_raw_file(
                            s, settings.raw_dir, raw, job_run_id=ctx.run_id, reimport=reimport
                        )
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
