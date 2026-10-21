from datetime import date

import pytest
from sqlalchemy import func, select, text
from sqlalchemy.exc import DatabaseError
from sqlalchemy.orm import Session

from fdfixtures import REFERENCE, ROWS, csv_bytes, replace_field
from footy.ingest.fd_update import run_fd_update
from footy.ingest.jobs import job_run
from footy.models import (
    DataCorrection,
    FdOdds,
    ImportIssue,
    JobRun,
    Match,
    RawFile,
)

TODAY = date(2025, 12, 10)


def drop(settings, content: bytes, name: str = "2526_E0.csv") -> None:
    settings.fd_inbox_dir.mkdir(parents=True, exist_ok=True)
    (settings.fd_inbox_dir / name).write_bytes(content)


def update(engine, settings, **kw):
    return run_fd_update(
        engine, settings, today=TODAY, allow_download=False, reference=REFERENCE, **kw
    )


def counts(engine) -> dict:
    with Session(engine) as s:
        return {
            m.__tablename__: s.scalar(select(func.count()).select_from(m))
            for m in (Match, FdOdds, RawFile, DataCorrection, ImportIssue)
        }


def test_import_then_rerun_creates_no_duplicates(migrated_engine, settings):
    drop(settings, csv_bytes())
    detail = update(migrated_engine, settings)
    assert detail["import"]["matches_inserted"] == 3
    assert detail["import"]["odds_inserted"] == 30
    first = counts(migrated_engine)

    update(migrated_engine, settings)  # same file again
    update(migrated_engine, settings, reimport=True)  # forced re-parse
    assert counts(migrated_engine) == first
    assert first["raw_files"] == 1 and first["data_corrections"] == 0


def test_new_revision_applies_and_logs_corrections(migrated_engine, settings):
    drop(settings, csv_bytes())
    update(migrated_engine, settings)
    with Session(migrated_engine) as s:
        old_path = s.scalar(select(RawFile.storage_path))
    old_bytes = (settings.raw_dir / old_path).read_bytes()

    changed = replace_field(replace_field(ROWS[0], "FTHG", "3"), "B365H", "")
    drop(settings, csv_bytes([changed, *ROWS[1:]]))
    detail = update(migrated_engine, settings)
    assert detail["import"]["corrections"] == 2

    with Session(migrated_engine) as s:
        assert s.scalar(select(func.count()).select_from(RawFile)) == 2
        m = s.scalar(select(Match).where(Match.home_team == "eng-testtown"))
        assert m.home_goals == 3 and m.first_raw_file_id != m.last_raw_file_id
        corr = {
            (c.entity, c.field, c.old_value, c.new_value) for c in s.scalars(select(DataCorrection))
        }
        assert corr == {("match", "home_goals", "2", "3"), ("fd_odds", "price", "2.1", None)}
        assert not s.scalar(
            select(FdOdds).where(FdOdds.match_id == m.id, FdOdds.source_column == "B365H")
        )
    # The first revision is still on disk, byte for byte.
    assert (settings.raw_dir / old_path).read_bytes() == old_bytes


def test_unmapped_team_fails_the_file_and_is_logged(migrated_engine, settings):
    drop(settings, csv_bytes([replace_field(ROWS[0], "HomeTeam", "Nowhere United")]))
    detail = update(migrated_engine, settings)
    assert detail["import_failures"][0]["file"] == "2526/E0"
    with Session(migrated_engine) as s:
        assert s.scalar(select(func.count()).select_from(Match)) == 0
        issue = s.scalar(select(ImportIssue))
        assert issue.code == "unmapped_teams" and "Nowhere United" in issue.detail
        run = s.scalar(select(JobRun).order_by(JobRun.id.desc()))
        assert run.status == "partial"


def test_inbox_ignores_badly_named_files(migrated_engine, settings):
    drop(settings, csv_bytes(), name="premier-league.csv")
    detail = update(migrated_engine, settings)
    assert detail["inbox_ignored"] == ["premier-league.csv"]
    assert counts(migrated_engine)["raw_files"] == 0


SEED_ROWS = {
    "odds_snapshots": (
        "INSERT INTO odds_snapshots (provider, provider_fixture_id, target, scheduled_for_utc, "
        "captured_at_utc, kickoff_utc_at_capture, bookmaker, market_key, line, selection, price, "
        "raw_file_id) VALUES ('test', '1', 'T-2h', '2025-12-01 13:00:00', '2025-12-01 13:01:00', "
        "'2025-12-01 15:00:00', 'b', 'BTTS', NULL, 'Y', 1.9, :raw)"
    ),
    "capture_events": (
        "INSERT INTO capture_events (provider, provider_fixture_id, target, scheduled_for_utc, "
        "attempted_at_utc, status, n_quotes, lateness_seconds) VALUES ('test', '1', 'T-2h', "
        "'2025-12-01 13:00:00', '2025-12-01 13:01:00', 'captured', 1, 60)"
    ),
    "data_corrections": (
        "INSERT INTO data_corrections (entity, entity_id, field, old_value, new_value, "
        "old_raw_file_id, new_raw_file_id, detected_at) VALUES ('match', 1, 'home_goals', '1', "
        "'2', :raw, :raw, '2025-12-01 13:00:00')"
    ),
}


@pytest.mark.parametrize(
    "table", ["raw_files", "data_corrections", "odds_snapshots", "capture_events"]
)
def test_append_only_tables_reject_update_and_delete(migrated_engine, settings, table):
    drop(settings, csv_bytes())
    update(migrated_engine, settings)
    with migrated_engine.begin() as conn:
        raw_id = conn.execute(text("SELECT min(id) FROM raw_files")).scalar_one()
        if table in SEED_ROWS:
            conn.execute(text(SEED_ROWS[table]), {"raw": raw_id})
    with migrated_engine.connect() as conn:
        assert conn.execute(text(f"SELECT count(*) FROM {table}")).scalar_one() >= 1
        for stmt in (f"UPDATE {table} SET id = id", f"DELETE FROM {table}"):
            with pytest.raises(DatabaseError, match="append-only"):
                conn.execute(text(stmt))


def test_failed_job_is_recorded(migrated_engine):
    with pytest.raises(RuntimeError):
        with job_run(migrated_engine, "boom"):
            raise RuntimeError("source unreachable")
    with Session(migrated_engine) as s:
        run = s.scalar(select(JobRun).where(JobRun.job == "boom"))
        assert run.status == "failed" and "source unreachable" in run.error
        assert run.finished_at is not None


def sc0_rows_with_repeats() -> list[str]:
    """SYNTHETIC: the three E0 fixture rows as SC0, then the first pairing three more times
    on later dates (SC0 allows three meetings, so the last is held back)."""
    rows = [replace_field(r, "Div", "SC0") for r in ROWS]
    later = [replace_field(rows[0], "Date", d) for d in ("14/02/2026", "18/04/2026", "16/05/2026")]
    return [*rows, *later]


def test_european_main_layout_division_has_results_but_no_prices(migrated_engine, settings):
    drop(settings, csv_bytes(sc0_rows_with_repeats()), name="2526_SC0.csv")
    detail = update(migrated_engine, settings)
    assert detail["import"]["matches_inserted"] == 5
    assert detail["import"]["odds_inserted"] == 0
    assert detail["import"]["rows_rejected"] == 1
    with Session(migrated_engine) as s:
        assert {m.division for m in s.scalars(select(Match))} == {"SC0"}
        pairing = s.scalars(
            select(Match).where(Match.home_team == "eng-testtown").order_by(Match.meeting)
        ).all()
        assert [(m.meeting, m.source_row, str(m.match_date)) for m in pairing] == [
            (1, 1, "2025-09-13"),
            (2, 4, "2026-02-14"),
            (3, 5, "2026-04-18"),
        ]
        issue = s.scalar(select(ImportIssue).where(ImportIssue.code == "repeat_pairing"))
        assert issue.source_row == 6 and issue.severity == "error"
        assert "would be meeting 4 and SC0 allows 3" in issue.detail


def test_repeat_meetings_reimport_creates_no_duplicates(migrated_engine, settings):
    drop(settings, csv_bytes(sc0_rows_with_repeats()), name="2526_SC0.csv")
    drop(settings, csv_bytes())  # a core division alongside
    update(migrated_engine, settings)
    first = counts(migrated_engine)
    with Session(migrated_engine) as s:
        before = sorted(
            (m.id, m.division, m.home_team, m.away_team, m.meeting, m.home_goals)
            for m in s.scalars(select(Match))
        )
        assert {m.meeting for m in s.scalars(select(Match).where(Match.division == "E0"))} == {1}
    update(migrated_engine, settings)  # same files again
    update(migrated_engine, settings, reimport=True)  # forced re-parse
    assert counts(migrated_engine) == first
    assert first["matches"] == 8 and first["data_corrections"] == 0
    with Session(migrated_engine) as s:
        after = sorted(
            (m.id, m.division, m.home_team, m.away_team, m.meeting, m.home_goals)
            for m in s.scalars(select(Match))
        )
    assert after == before


def test_core_division_keeps_one_row_per_pairing(migrated_engine, settings):
    """The core ten allow one meeting: a second row of a pairing on another date is still a
    rejected duplicate (D-017), whatever the European leagues allow."""
    again = replace_field(ROWS[0], "Date", "14/02/2026")
    drop(settings, csv_bytes([*ROWS, again]))
    detail = update(migrated_engine, settings)
    assert detail["import"]["matches_inserted"] == 3 and detail["import"]["rows_rejected"] == 1
    with Session(migrated_engine) as s:
        assert {m.meeting for m in s.scalars(select(Match))} == {1}
        issue = s.scalar(select(ImportIssue).where(ImportIssue.code == "duplicate_fixture"))
        assert (issue.source_row, issue.detail) == (4, "same pairing as row 1")
