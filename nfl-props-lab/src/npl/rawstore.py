"""Immutable raw-file store and job-run bookkeeping.

Every retrieved file is written once under data/raw/<source>/<dataset>/ with its
SHA-256 in the name, registered in `raw_files`, and every retrieval (changed or not)
is logged in `raw_observations`. Re-importing identical content is a no-op apart from
the observation row, so imports are idempotent.
"""

from __future__ import annotations

import io
import json
import sqlite3
import traceback
from contextlib import contextmanager
from dataclasses import dataclass
from pathlib import Path

from . import db
from .config import DATA_HOME, RAW_DIR
from .util import iso, log, redact, sha256_bytes, utcnow


@dataclass
class StoredFile:
    id: int
    path: Path
    sha256: str
    retrieved_at: str
    changed: bool
    n_rows: int | None


def _parquet_meta(content: bytes) -> tuple[str | None, int | None]:
    try:
        import pyarrow.parquet as pq

        f = pq.ParquetFile(io.BytesIO(content))
        schema = [(n, str(t)) for n, t in zip(f.schema_arrow.names, f.schema_arrow.types, strict=True)]
        return sha256_bytes(json.dumps(schema).encode())[:16], f.metadata.num_rows
    except Exception:  # not parquet / unreadable: recorded as unknown, never guessed
        return None, None


def store(
    con: sqlite3.Connection,
    *,
    source: str,
    dataset: str,
    part: str,
    url: str,
    content: bytes,
    filename: str,
    content_type: str | None = None,
    upstream_last_modified: str | None = None,
    is_synthetic: bool = False,
    retrieved_at: str | None = None,
) -> StoredFile:
    digest = sha256_bytes(content)
    when = retrieved_at or iso(utcnow())
    with db.tx(con):
        row = con.execute(
            "SELECT id, path, retrieved_at, n_rows FROM raw_files "
            "WHERE source=? AND dataset=? AND part=? AND sha256=?",
            (source, dataset, part, digest),
        ).fetchone()
        if row:
            con.execute(
                "INSERT INTO raw_observations(raw_file_id, observed_at, changed) VALUES (?,?,0)",
                (row["id"], when),
            )
            return StoredFile(row["id"], DATA_HOME / row["path"], digest, row["retrieved_at"],
                              False, row["n_rows"])
        rel = Path("raw") / source / dataset / f"{when.replace(':', '')}_{digest[:12]}_{filename}"
        dest = DATA_HOME / rel
        dest.parent.mkdir(parents=True, exist_ok=True)
        tmp = dest.with_suffix(dest.suffix + ".part")
        tmp.write_bytes(content)
        tmp.replace(dest)
        fp, n_rows = _parquet_meta(content) if filename.endswith(".parquet") else (None, None)
        cur = con.execute(
            "INSERT INTO raw_files(source, dataset, part, url, retrieved_at, sha256, bytes, path,"
            " content_type, upstream_last_modified, schema_fingerprint, n_rows, is_synthetic)"
            " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)",
            (source, dataset, part, redact(url), when, digest, len(content), str(rel),
             content_type, upstream_last_modified, fp, n_rows, int(is_synthetic)),
        )
        con.execute(
            "INSERT INTO raw_observations(raw_file_id, observed_at, changed) VALUES (?,?,1)",
            (cur.lastrowid, when),
        )
        return StoredFile(cur.lastrowid, dest, digest, when, True, n_rows)


def latest(con: sqlite3.Connection, source: str, dataset: str, part: str,
           as_of: str | None = None) -> sqlite3.Row | None:
    """Most recent stored version, optionally as it stood at `as_of` (UTC ISO)."""
    q = ("SELECT * FROM raw_files WHERE source=? AND dataset=? AND part=? AND is_synthetic=0")
    args: list = [source, dataset, part]
    if as_of:
        q += " AND retrieved_at <= ?"
        args.append(as_of)
    q += " ORDER BY retrieved_at DESC, id DESC LIMIT 1"
    return con.execute(q, args).fetchone()


def last_observed(con: sqlite3.Connection, raw_file_id: int) -> str | None:
    r = con.execute(
        "SELECT MAX(observed_at) AS t FROM raw_observations WHERE raw_file_id=?", (raw_file_id,)
    ).fetchone()
    return r["t"] if r else None


def record_failure(con, *, source, dataset, part, url, status, error) -> None:
    con.execute(
        "INSERT INTO fetch_failures(source, dataset, part, url, attempted_at, status, error)"
        " VALUES (?,?,?,?,?,?,?)",
        (source, dataset, part, redact(url), iso(utcnow()), status, redact(str(error))[:2000]),
    )


@contextmanager
def job(con: sqlite3.Connection, name: str, args: dict | None = None):
    """Record a job run; failures are stored (and re-raised) so the UI can surface them."""
    cur = con.execute(
        "INSERT INTO job_runs(job, args, started_at, status) VALUES (?,?,?, 'running')",
        (name, json.dumps(args or {}, default=str), iso(utcnow())),
    )
    jid = cur.lastrowid
    state = {"status": "ok", "detail": {}}
    try:
        yield state
    except BaseException as e:
        con.execute(
            "UPDATE job_runs SET finished_at=?, status='failed', detail=? WHERE id=?",
            (iso(utcnow()), redact(json.dumps({"error": str(e),
                                               "trace": traceback.format_exc()[-3000:]})), jid),
        )
        log.error("job %s failed: %s", name, redact(str(e)))
        raise
    else:
        con.execute(
            "UPDATE job_runs SET finished_at=?, status=?, detail=? WHERE id=?",
            (iso(utcnow()), state["status"], json.dumps(state["detail"], default=str), jid),
        )


__all__ = ["store", "latest", "job", "record_failure", "StoredFile", "RAW_DIR"]
