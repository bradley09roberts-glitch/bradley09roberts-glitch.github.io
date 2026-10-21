"""raw_files: distinct names for its two unique keys

Migration 0002 gave both unique keys of `raw_files` the same name, `uq_raw_files_source`: the
naming convention in footy/db.py uses only the first column, and both keys start with
`source`. SQLite accepts the duplicate, but `alembic check` compares constraints by name and
kept one of the two at random, so it failed about one run in fifteen. The keys are now
`uq_raw_files_source_logical_key_revision` and `uq_raw_files_source_logical_key_sha256`,
named explicitly in footy/models.py too. Rows, columns and what the keys enforce don't change.

On SQLite the batch operation rebuilds `raw_files` (copy, drop, rename) from the explicit
table definition below rather than by reflection, which would merge the two same-named keys.
Nine tables reference `raw_files`, so foreign-key enforcement is paused for the rebuild and
checked with `PRAGMA foreign_key_check` before the migration commits (as in 0005). The
append-only triggers are dropped with the old table and created again.

Revision ID: 0006
Revises: 0005
Create Date: 2026-10-21 14:00:00.000000
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

revision: str = "0006"
down_revision: str | None = "0005"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None

OLD_NAME = "uq_raw_files_source"
REVISION_KEY = ("uq_raw_files_source_logical_key_revision", ["source", "logical_key", "revision"])
SHA_KEY = ("uq_raw_files_source_logical_key_sha256", ["source", "logical_key", "sha256"])
COLUMNS = [
    "id",
    "source",
    "logical_key",
    "revision",
    "url",
    "sha256",
    "size_bytes",
    "retrieved_at",
    "storage_path",
    "http_last_modified",
    "http_etag",
]


def _raw_files() -> sa.Table:
    """`raw_files` as created by migration 0002, without its unique keys."""
    return sa.Table(
        "raw_files",
        sa.MetaData(),
        sa.Column("id", sa.Integer(), nullable=False),
        sa.Column("source", sa.String(length=32), nullable=False),
        sa.Column("logical_key", sa.String(length=255), nullable=False),
        sa.Column("revision", sa.Integer(), nullable=False),
        sa.Column("url", sa.Text(), nullable=True),
        sa.Column("sha256", sa.String(length=64), nullable=False),
        sa.Column("size_bytes", sa.Integer(), nullable=False),
        sa.Column("retrieved_at", sa.DateTime(), nullable=False),
        sa.Column("storage_path", sa.Text(), nullable=False),
        sa.Column("http_last_modified", sa.String(length=64), nullable=True),
        sa.Column("http_etag", sa.String(length=255), nullable=True),
        sa.PrimaryKeyConstraint("id", name="pk_raw_files"),
    )


def _check_columns() -> None:
    """The rebuild copies the columns of `_raw_files()`; refuse if the table has others."""
    found = [c["name"] for c in sa.inspect(op.get_bind()).get_columns("raw_files")]
    if found != COLUMNS:
        raise RuntimeError(f"raw_files has columns {found}, expected {COLUMNS}")


def _foreign_keys_off() -> bool:
    """SQLite only: pause foreign-key enforcement so the batch rebuild can drop `raw_files`.
    Must run before any other statement of this migration (the pragma is ignored inside a
    transaction)."""
    bind = op.get_bind()
    if bind.dialect.name != "sqlite":
        return False
    op.execute("PRAGMA foreign_keys=OFF")
    if bind.execute(sa.text("PRAGMA foreign_keys")).scalar():
        raise RuntimeError("could not pause foreign keys: a transaction was already open")
    return True


def _foreign_keys_on() -> None:
    bad = op.get_bind().execute(sa.text("PRAGMA foreign_key_check")).all()
    if bad:
        raise RuntimeError(f"foreign key violations after rebuilding raw_files: {bad[:5]}")
    op.execute("PRAGMA foreign_keys=ON")


def _append_only_triggers() -> None:
    """The SQLite triggers of migration 0002; they went with the old table."""
    for action in ("UPDATE", "DELETE"):
        op.execute(
            f"CREATE TRIGGER raw_files_no_{action.lower()} BEFORE {action} ON raw_files "
            "BEGIN SELECT RAISE(ABORT, 'raw_files is append-only'); END"
        )


def upgrade() -> None:
    if _foreign_keys_off():
        _check_columns()
        with op.batch_alter_table(
            "raw_files", copy_from=_raw_files(), recreate="always"
        ) as batch_op:
            batch_op.create_unique_constraint(*REVISION_KEY)
            batch_op.create_unique_constraint(*SHA_KEY)
        _append_only_triggers()
        _foreign_keys_on()
    else:
        # A database other than SQLite can't hold two constraints of one name, so 0002 made
        # at most one there; the table and its triggers stay, only the keys change.
        with op.batch_alter_table("raw_files") as batch_op:
            batch_op.drop_constraint(OLD_NAME, type_="unique")
            batch_op.create_unique_constraint(*REVISION_KEY)
            batch_op.create_unique_constraint(*SHA_KEY)


def downgrade() -> None:
    if _foreign_keys_off():
        _check_columns()
        # Back to 0002's definition, both keys named uq_raw_files_source. Batch operations
        # hold constraints by name (a second create would replace the first), so the keys go
        # in as table arguments.
        with op.batch_alter_table(
            "raw_files",
            copy_from=_raw_files(),
            recreate="always",
            table_args=(
                sa.UniqueConstraint(*REVISION_KEY[1], name=OLD_NAME),
                sa.UniqueConstraint(*SHA_KEY[1], name=OLD_NAME),
            ),
        ):
            pass
        _append_only_triggers()
        _foreign_keys_on()
    else:
        with op.batch_alter_table("raw_files") as batch_op:
            batch_op.drop_constraint(SHA_KEY[0], type_="unique")
            batch_op.drop_constraint(REVISION_KEY[0], type_="unique")
            batch_op.create_unique_constraint(OLD_NAME, REVISION_KEY[1])
