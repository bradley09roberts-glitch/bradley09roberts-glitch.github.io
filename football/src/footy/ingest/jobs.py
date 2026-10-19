"""Job run log: every refresh is recorded with its outcome, including failures."""

import json
import traceback
from collections.abc import Iterator
from contextlib import contextmanager
from typing import Any

from sqlalchemy import Engine
from sqlalchemy.orm import Session, sessionmaker

from footy.models import JobRun, utcnow


class JobContext:
    def __init__(self, run_id: int):
        self.run_id = run_id
        self.detail: dict[str, Any] = {}
        self.partial = False  # set when some items failed but the job carried on


@contextmanager
def job_run(engine: Engine, job: str) -> Iterator[JobContext]:
    """Record a job in `job_runs`. The run row is committed separately from the job's work,
    so a failure is still logged even when the job's own transaction rolls back."""
    factory = sessionmaker(bind=engine)
    with factory() as s:
        run = JobRun(job=job, started_at=utcnow(), status="running")
        s.add(run)
        s.commit()
        ctx = JobContext(run.id)
    status, error = "ok", None
    try:
        yield ctx
        if ctx.partial:
            status = "partial"
    except BaseException as exc:
        status = "failed"
        error = "".join(traceback.format_exception_only(type(exc), exc)).strip()
        raise
    finally:
        with factory() as s:
            run = s.get(JobRun, ctx.run_id)
            run.finished_at = utcnow()
            run.status = status
            run.error = error
            run.detail = json.dumps(ctx.detail, default=str, sort_keys=True)
            s.commit()


def recent_runs(session: Session, limit: int = 50) -> list[JobRun]:
    return list(session.query(JobRun).order_by(JobRun.id.desc()).limit(limit))
