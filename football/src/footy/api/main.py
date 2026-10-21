"""FastAPI application. Run with: uv run footy serve"""

import json
from datetime import UTC, datetime

from fastapi import FastAPI, Query
from sqlalchemy import Engine, func, select
from sqlalchemy.orm import Session

from footy import __version__
from footy.api.routes import iso_utc, iso_utc_text
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
                last_jobs = {}
                for job in ("predict", "settle", "odds_capture", "euro_update"):
                    jr = s.scalar(
                        select(JobRun).where(JobRun.job == job).order_by(JobRun.id.desc())
                    )
                    last_jobs[job] = (
                        None
                        if jr is None
                        else {
                            "status": jr.status,
                            "started_at": _iso(jr.started_at),
                            "finished_at": _iso(jr.finished_at),
                        }
                    )
                data["last_jobs"] = last_jobs
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
            "server_time_utc": _iso(datetime.now(UTC)),
            "display_timezone": settings.display_timezone,
            "database": "ok" if db_ok else "error",
            "schema_revision": revision,
            "schema_head": head,
            "live_provider": settings.live_provider,
            "live_provider_configured": settings.live_provider_configured(),
            "milestone": "M7",
            "data_imported": data["matches"] > 0,
            "data": data,
        }

    @app.get("/api/coverage")
    def coverage() -> dict:
        from footy.coverage import coverage_rows

        rows = coverage_rows(_engine())
        for row in rows:  # stored naive UTC; sent with its offset (D-005)
            if row.get("retrieved_at"):
                row["retrieved_at"] = iso_utc_text(row["retrieved_at"])
        return {"rows": rows}

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
    def import_issues(per_code: int = Query(0, ge=0, le=5000)) -> dict:
        """Rows and values rejected or flagged at import, by reason, with where they occurred.
        A rejected price cell is named by its column only; its value is withheld (D-013)."""
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
            "issues": _limit_per_code(
                [
                    {
                        "severity": sev,
                        "code": code,
                        "file": key,
                        "row": row,
                        "detail": public_issue_detail(code, detail),
                    }
                    for sev, code, key, row, detail in rows
                ],
                per_code,
            ),
        }

    from footy.api.routes import make_router

    app.include_router(make_router(_engine, settings))
    return app


# Issues about one price cell, saved as `<column>=<raw cell>` (e.g. "B365H='0'"). The raw cell
# is a price from the source file, even if a faulty one, and prices are never served
# (D-013, D-024): the column name and the reason stay, the value doesn't.
PRICE_CELL_CODES = {"invalid_price": "not a valid price"}


def public_issue_detail(code: str, detail: str | None) -> str | None:
    """An import issue's detail as the API sends it: a price cell's value is withheld."""
    if code not in PRICE_CELL_CODES or detail is None:
        return detail
    column, sep, _value = detail.partition("=")
    reason = PRICE_CELL_CODES[code]
    return f"{column.strip()}: {reason} (value withheld)" if sep else f"{reason} (value withheld)"


def _limit_per_code(issues: list[dict], per_code: int) -> list[dict]:
    """per_code = 0 returns every issue; otherwise at most that many examples per reason."""
    if not per_code:
        return issues
    seen: dict[tuple, int] = {}
    out = []
    for i in issues:
        k = (i["severity"], i["code"])
        seen[k] = seen.get(k, 0) + 1
        if seen[k] <= per_code:
            out.append(i)
    return out


def _iso(dt: datetime | None) -> str | None:
    """ISO 8601 in UTC with a `+00:00` offset, to the second."""
    return iso_utc(dt, "seconds")


app = create_app()
