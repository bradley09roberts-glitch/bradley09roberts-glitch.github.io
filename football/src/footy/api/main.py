"""FastAPI application. Run with: uv run footy serve"""

import json
from datetime import UTC, datetime

from fastapi import FastAPI, Query
from sqlalchemy import Engine, func, select
from sqlalchemy.orm import Session

from footy import __version__
from footy.config import Settings, get_settings
from footy.db import check_connection, current_revision, get_engine, head_revision
from footy.models import ImportIssue, JobRun, Match, RawFile


def create_app(settings: Settings | None = None, engine: Engine | None = None) -> FastAPI:
    settings = settings or get_settings()
    app = FastAPI(title="Football model API", version=__version__)

    def _engine() -> Engine:
        return engine or get_engine()

    @app.get("/api/health")
    def health() -> dict:
        """Liveness plus the facts a reviewer needs before trusting anything else.

        No metrics or predictions are reported here; only what has been imported and when.
        """
        db_ok = False
        revision = None
        try:
            db_ok = check_connection(_engine())
            revision = current_revision(_engine())
        except Exception:  # surfaced as database: "error", never hidden
            db_ok = False
        head = head_revision()
        data = {"matches": 0, "divisions": 0, "latest_match_date": None, "last_refresh": None}
        if db_ok and revision == head:
            with Session(_engine()) as s:
                data["matches"] = s.scalar(select(func.count()).select_from(Match)) or 0
                data["divisions"] = s.scalar(select(func.count(func.distinct(Match.division))))
                latest = s.scalar(select(func.max(Match.match_date)))
                data["latest_match_date"] = latest.isoformat() if latest else None
                run = s.scalar(
                    select(JobRun).where(JobRun.job == "fd_update").order_by(JobRun.id.desc())
                )
                if run is not None:
                    data["last_refresh"] = {
                        "status": run.status,
                        "started_at": _iso(run.started_at),
                        "finished_at": _iso(run.finished_at),
                    }
        return {
            "status": "ok" if db_ok and revision == head else "degraded",
            "app_version": __version__,
            "server_time_utc": datetime.now(UTC).isoformat(timespec="seconds"),
            "display_timezone": settings.display_timezone,
            "database": "ok" if db_ok else "error",
            "schema_revision": revision,
            "schema_head": head,
            "live_provider": settings.live_provider,
            "live_provider_configured": settings.live_provider_configured(),
            "milestone": "M1",
            "data_imported": data["matches"] > 0,
            "data": data,
        }

    @app.get("/api/coverage")
    def coverage() -> dict:
        from footy.coverage import coverage_rows

        return {"rows": coverage_rows(_engine())}

    @app.get("/api/refresh-history")
    def refresh_history(limit: int = Query(50, ge=1, le=500)) -> dict:
        """Every job run, newest first, failures included."""
        with Session(_engine()) as s:
            runs = s.scalars(select(JobRun).order_by(JobRun.id.desc()).limit(limit))
            return {
                "runs": [
                    {
                        "id": r.id,
                        "job": r.job,
                        "status": r.status,
                        "started_at": _iso(r.started_at),
                        "finished_at": _iso(r.finished_at),
                        "error": r.error,
                        "detail": json.loads(r.detail) if r.detail else None,
                    }
                    for r in runs
                ]
            }

    @app.get("/api/import-issues")
    def import_issues() -> dict:
        """Rows and values rejected or flagged at import, by reason, with where they occurred."""
        with Session(_engine()) as s:
            rows = s.execute(
                select(
                    ImportIssue.severity,
                    ImportIssue.code,
                    RawFile.logical_key,
                    ImportIssue.source_row,
                    ImportIssue.detail,
                )
                .join(RawFile, RawFile.id == ImportIssue.raw_file_id)
                .order_by(ImportIssue.severity, ImportIssue.code, RawFile.logical_key)
            ).all()
        summary: dict[tuple[str, str], int] = {}
        for sev, code, *_ in rows:
            summary[(sev, code)] = summary.get((sev, code), 0) + 1
        return {
            "summary": [
                {"severity": sev, "code": code, "count": n} for (sev, code), n in summary.items()
            ],
            "issues": [
                {"severity": sev, "code": code, "file": key, "row": row, "detail": detail}
                for sev, code, key, row, detail in rows
            ],
        }

    from footy.api.routes import make_router

    app.include_router(make_router(_engine, settings))
    return app


def _iso(dt: datetime | None) -> str | None:
    return dt.isoformat(timespec="seconds") if dt else None


app = create_app()
