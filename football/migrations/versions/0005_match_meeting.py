"""match meeting: repeat meetings of a pairing in one season (D-022)

`matches` was unique on (division, season, home_team, away_team), so split and multi-round
leagues lost every later meeting of a pairing. `meeting` (1, 2, 3, ... in file order) joins
the unique key; existing rows are meeting 1.

On SQLite the batch operation rebuilds `matches` (copy, drop, rename). `fd_odds` references
it, so foreign-key enforcement is switched off for the rebuild and the result is checked
with `PRAGMA foreign_key_check` before the migration commits.

Downgrade deletes every row with meeting > 1 (and its prices) because the old key cannot
hold them. They are derived data: re-run `footy fd-update --no-download --reimport` on the
older code to rebuild `import_issues` for them.

Revision ID: 0005
Revises: 0004
Create Date: 2026-10-21 12:00:00.000000
"""

from collections.abc import Sequence

import sqlalchemy as sa
from alembic import op

revision: str = "0005"
down_revision: str | None = "0004"
branch_labels: str | Sequence[str] | None = None
depends_on: str | Sequence[str] | None = None

OLD_KEY = ["division", "season", "home_team", "away_team"]
NEW_KEY = [*OLD_KEY, "meeting"]
# Named by the convention in footy/db.py: uq_%(table_name)s_%(column_0_name)s.
KEY_NAME = "uq_matches_division"


def _foreign_keys_off() -> bool:
    """SQLite only: pause foreign-key enforcement so the batch rebuild can drop `matches`.
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
        raise RuntimeError(f"foreign key violations after rebuilding matches: {bad[:5]}")
    op.execute("PRAGMA foreign_keys=ON")


def upgrade() -> None:
    sqlite = _foreign_keys_off()
    with op.batch_alter_table("matches", schema=None) as batch_op:
        batch_op.add_column(
            sa.Column("meeting", sa.Integer(), server_default=sa.text("1"), nullable=False)
        )
        batch_op.drop_constraint(KEY_NAME, type_="unique")
        batch_op.create_unique_constraint(KEY_NAME, NEW_KEY)
    if sqlite:
        _foreign_keys_on()


def downgrade() -> None:
    sqlite = _foreign_keys_off()
    op.execute("DELETE FROM fd_odds WHERE match_id IN (SELECT id FROM matches WHERE meeting > 1)")
    op.execute("DELETE FROM matches WHERE meeting > 1")
    with op.batch_alter_table("matches", schema=None) as batch_op:
        batch_op.drop_constraint(KEY_NAME, type_="unique")
        batch_op.create_unique_constraint(KEY_NAME, OLD_KEY)
        batch_op.drop_column("meeting")
    if sqlite:
        _foreign_keys_on()
