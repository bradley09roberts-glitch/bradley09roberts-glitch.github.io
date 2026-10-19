"""Append-only store of raw downloads and API responses.

Each distinct content of a logical file becomes a new revision, stored content-addressed on
disk and recorded in `raw_files`. Identical content is never stored twice; nothing is ever
overwritten or deleted.
"""

import hashlib
import os
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path

from sqlalchemy import func, select
from sqlalchemy.orm import Session

from footy.models import RawFile


@dataclass(frozen=True)
class StoredRaw:
    raw: RawFile
    created: bool  # False when identical content was already stored


def sha256_hex(content: bytes) -> str:
    return hashlib.sha256(content).hexdigest()


def store_raw(
    session: Session,
    raw_dir: Path,
    *,
    source: str,
    logical_key: str,
    content: bytes,
    retrieved_at: datetime,
    url: str | None = None,
    suffix: str = "",
    http_last_modified: str | None = None,
    http_etag: str | None = None,
) -> StoredRaw:
    digest = sha256_hex(content)
    existing = session.scalar(
        select(RawFile).where(
            RawFile.source == source,
            RawFile.logical_key == logical_key,
            RawFile.sha256 == digest,
        )
    )
    if existing is not None:
        return StoredRaw(existing, created=False)

    latest = session.scalar(
        select(func.max(RawFile.revision)).where(
            RawFile.source == source, RawFile.logical_key == logical_key
        )
    )
    revision = (latest or 0) + 1
    rel_path = Path(source) / logical_key / f"r{revision:04d}_{digest[:12]}{suffix}"
    _write_once(raw_dir / rel_path, content)

    raw = RawFile(
        source=source,
        logical_key=logical_key,
        revision=revision,
        url=url,
        sha256=digest,
        size_bytes=len(content),
        retrieved_at=retrieved_at,
        storage_path=rel_path.as_posix(),
        http_last_modified=http_last_modified,
        http_etag=http_etag,
    )
    session.add(raw)
    session.flush()
    return StoredRaw(raw, created=True)


def read_raw(raw_dir: Path, raw: RawFile) -> bytes:
    content = (raw_dir / raw.storage_path).read_bytes()
    if sha256_hex(content) != raw.sha256:
        raise RuntimeError(f"raw file {raw.storage_path} does not match its recorded SHA-256")
    return content


def latest_revision(session: Session, source: str, logical_key: str) -> RawFile | None:
    return session.scalar(
        select(RawFile)
        .where(RawFile.source == source, RawFile.logical_key == logical_key)
        .order_by(RawFile.revision.desc())
        .limit(1)
    )


def _write_once(path: Path, content: bytes) -> None:
    if path.exists():
        if path.read_bytes() != content:
            raise RuntimeError(f"refusing to overwrite existing raw file {path}")
        return
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_name(path.name + ".tmp")
    tmp.write_bytes(content)
    os.replace(tmp, path)
