"""Database schema.

Raw inputs (`raw_files`), forward-captured odds (`odds_snapshots`, `capture_events`) and the
correction log are append-only: database triggers reject UPDATE and DELETE on them (see
migration 0002). Derived tables (`matches`, `fd_odds`) can always be rebuilt from raw files.
"""

from datetime import UTC, date, datetime

from sqlalchemy import (
    Boolean,
    Date,
    DateTime,
    Float,
    ForeignKey,
    Integer,
    String,
    Text,
    TypeDecorator,
    UniqueConstraint,
)
from sqlalchemy.orm import Mapped, mapped_column

from footy.db import Base

APPEND_ONLY_TABLES = (
    "raw_files",
    "data_corrections",
    "odds_snapshots",
    "capture_events",
    "predictions",
    "prediction_settlements",
)


class UTCDateTime(TypeDecorator):
    """Timezone-aware UTC in Python; naive UTC in the database. Rejects naive datetimes."""

    impl = DateTime
    cache_ok = True

    def process_bind_param(self, value, dialect):
        if value is None:
            return None
        if value.tzinfo is None:
            raise ValueError(f"naive datetime not allowed: {value!r}")
        return value.astimezone(UTC).replace(tzinfo=None)

    def process_result_value(self, value, dialect):
        return None if value is None else value.replace(tzinfo=UTC)


class JobRun(Base):
    __tablename__ = "job_runs"

    id: Mapped[int] = mapped_column(primary_key=True)
    job: Mapped[str] = mapped_column(String(64))
    started_at: Mapped[datetime] = mapped_column(UTCDateTime)
    finished_at: Mapped[datetime | None] = mapped_column(UTCDateTime)
    status: Mapped[str] = mapped_column(String(16))  # running | ok | partial | failed
    detail: Mapped[str | None] = mapped_column(Text)  # JSON summary
    error: Mapped[str | None] = mapped_column(Text)


class RawFile(Base):
    """One stored revision of a downloaded file or API response. Never changed or deleted."""

    __tablename__ = "raw_files"
    __table_args__ = (
        UniqueConstraint("source", "logical_key", "revision"),
        UniqueConstraint("source", "logical_key", "sha256"),
    )

    id: Mapped[int] = mapped_column(primary_key=True)
    source: Mapped[str] = mapped_column(String(32))
    logical_key: Mapped[str] = mapped_column(String(255))  # e.g. "2526/E0"
    revision: Mapped[int] = mapped_column(Integer)
    url: Mapped[str | None] = mapped_column(Text)
    sha256: Mapped[str] = mapped_column(String(64))
    size_bytes: Mapped[int] = mapped_column(Integer)
    retrieved_at: Mapped[datetime] = mapped_column(UTCDateTime)
    storage_path: Mapped[str] = mapped_column(Text)  # relative to the raw store
    http_last_modified: Mapped[str | None] = mapped_column(String(64))
    http_etag: Mapped[str | None] = mapped_column(String(255))


class RawImport(Base):
    """Which raw revisions have been parsed into the derived tables, and by which parser."""

    __tablename__ = "raw_imports"

    raw_file_id: Mapped[int] = mapped_column(ForeignKey("raw_files.id"), primary_key=True)
    parser_version: Mapped[str] = mapped_column(String(16), primary_key=True)
    imported_at: Mapped[datetime] = mapped_column(UTCDateTime)
    job_run_id: Mapped[int | None] = mapped_column(ForeignKey("job_runs.id"))
    rows_imported: Mapped[int] = mapped_column(Integer)
    rows_rejected: Mapped[int] = mapped_column(Integer)


class FetchLog(Base):
    """Every download attempt, including failures and cache hits."""

    __tablename__ = "fetch_log"

    id: Mapped[int] = mapped_column(primary_key=True)
    job_run_id: Mapped[int | None] = mapped_column(ForeignKey("job_runs.id"))
    source: Mapped[str] = mapped_column(String(32))
    logical_key: Mapped[str] = mapped_column(String(255))
    url: Mapped[str | None] = mapped_column(Text)
    requested_at: Mapped[datetime] = mapped_column(UTCDateTime)
    http_status: Mapped[int | None] = mapped_column(Integer)
    # new_revision | unchanged | not_modified | cached | not_found | error
    outcome: Mapped[str] = mapped_column(String(16))
    raw_file_id: Mapped[int | None] = mapped_column(ForeignKey("raw_files.id"))
    error: Mapped[str | None] = mapped_column(Text)


class Division(Base):
    __tablename__ = "divisions"

    code: Mapped[str] = mapped_column(String(8), primary_key=True)  # football-data code
    name: Mapped[str] = mapped_column(String(64))
    country: Mapped[str] = mapped_column(String(3))
    tier: Mapped[int] = mapped_column(Integer)


class Team(Base):
    __tablename__ = "teams"

    team_key: Mapped[str] = mapped_column(String(64), primary_key=True)
    name: Mapped[str] = mapped_column(String(64))
    country: Mapped[str] = mapped_column(String(3))


class TeamAlias(Base):
    __tablename__ = "team_aliases"

    source: Mapped[str] = mapped_column(String(32), primary_key=True)
    alias: Mapped[str] = mapped_column(String(128), primary_key=True)
    team_key: Mapped[str] = mapped_column(ForeignKey("teams.team_key"))


class Match(Base):
    """A played league match: regulation-time result and statistics from football-data."""

    __tablename__ = "matches"
    __table_args__ = (UniqueConstraint("division", "season", "home_team", "away_team"),)

    id: Mapped[int] = mapped_column(primary_key=True)
    division: Mapped[str] = mapped_column(ForeignKey("divisions.code"))
    season: Mapped[str] = mapped_column(String(4))  # "2526" = 2025/26
    match_date: Mapped[date] = mapped_column(Date, index=True)  # UK local date
    kickoff_local: Mapped[str | None] = mapped_column(String(5))  # "HH:MM" UK local time
    kickoff_utc: Mapped[datetime | None] = mapped_column(UTCDateTime)
    home_team: Mapped[str] = mapped_column(ForeignKey("teams.team_key"))
    away_team: Mapped[str] = mapped_column(ForeignKey("teams.team_key"))
    home_goals: Mapped[int] = mapped_column(Integer)
    away_goals: Mapped[int] = mapped_column(Integer)
    result: Mapped[str] = mapped_column(String(1))  # H | D | A
    ht_home_goals: Mapped[int | None] = mapped_column(Integer)
    ht_away_goals: Mapped[int | None] = mapped_column(Integer)
    ht_result: Mapped[str | None] = mapped_column(String(1))
    home_shots: Mapped[int | None] = mapped_column(Integer)
    away_shots: Mapped[int | None] = mapped_column(Integer)
    home_sot: Mapped[int | None] = mapped_column(Integer)
    away_sot: Mapped[int | None] = mapped_column(Integer)
    home_corners: Mapped[int | None] = mapped_column(Integer)
    away_corners: Mapped[int | None] = mapped_column(Integer)
    home_fouls: Mapped[int | None] = mapped_column(Integer)
    away_fouls: Mapped[int | None] = mapped_column(Integer)
    home_yellow: Mapped[int | None] = mapped_column(Integer)
    away_yellow: Mapped[int | None] = mapped_column(Integer)
    home_red: Mapped[int | None] = mapped_column(Integer)
    away_red: Mapped[int | None] = mapped_column(Integer)
    referee: Mapped[str | None] = mapped_column(String(64))
    # Undocumented football-data columns (2026/27 onwards). Stored, never used by models.
    fd_home_xg: Mapped[float | None] = mapped_column(Float)
    fd_away_xg: Mapped[float | None] = mapped_column(Float)
    # Set when the score is not evidence of football played (e.g. awarded results); such
    # matches are excluded from model fitting and evaluation, with this as the reason.
    model_exclusion: Mapped[str | None] = mapped_column(String(32))
    source_row: Mapped[int] = mapped_column(Integer)  # 1-based data row in the raw file
    first_raw_file_id: Mapped[int] = mapped_column(ForeignKey("raw_files.id"))
    last_raw_file_id: Mapped[int] = mapped_column(ForeignKey("raw_files.id"))


class FdOdds(Base):
    """Historical prices from football-data. `quote_time_utc` follows DECISIONS.md D-012."""

    __tablename__ = "fd_odds"
    __table_args__ = (
        UniqueConstraint("match_id", "bookmaker", "market_key", "selection", "phase"),
    )

    id: Mapped[int] = mapped_column(primary_key=True)
    match_id: Mapped[int] = mapped_column(ForeignKey("matches.id"), index=True)
    bookmaker: Mapped[str] = mapped_column(String(8))  # B365 | PS | BFE | Avg
    market_key: Mapped[str] = mapped_column(String(16))  # 1X2 | OU_2.5
    line: Mapped[float | None] = mapped_column(Float)
    selection: Mapped[str] = mapped_column(String(4))  # H D A | O U
    phase: Mapped[str] = mapped_column(String(12))  # pre_closing | closing
    price: Mapped[float] = mapped_column(Float)
    quote_time_utc: Mapped[datetime | None] = mapped_column(UTCDateTime)
    quote_time_basis: Mapped[str] = mapped_column(String(32))
    source_column: Mapped[str] = mapped_column(String(16))
    raw_file_id: Mapped[int] = mapped_column(ForeignKey("raw_files.id"))


class DataCorrection(Base):
    """A value that changed between two revisions of the same source file."""

    __tablename__ = "data_corrections"

    id: Mapped[int] = mapped_column(primary_key=True)
    entity: Mapped[str] = mapped_column(String(16))  # match | fd_odds
    entity_id: Mapped[int] = mapped_column(Integer)
    field: Mapped[str] = mapped_column(String(64))
    old_value: Mapped[str | None] = mapped_column(Text)
    new_value: Mapped[str | None] = mapped_column(Text)
    old_raw_file_id: Mapped[int] = mapped_column(ForeignKey("raw_files.id"))
    new_raw_file_id: Mapped[int] = mapped_column(ForeignKey("raw_files.id"))
    detected_at: Mapped[datetime] = mapped_column(UTCDateTime)


class ImportIssue(Base):
    """A row or value that was rejected or flagged during import, with the exact reason."""

    __tablename__ = "import_issues"
    __table_args__ = (UniqueConstraint("raw_file_id", "source_row", "code", "detail"),)

    id: Mapped[int] = mapped_column(primary_key=True)
    raw_file_id: Mapped[int] = mapped_column(ForeignKey("raw_files.id"), index=True)
    source_row: Mapped[int] = mapped_column(Integer)  # 0 = whole file
    severity: Mapped[str] = mapped_column(String(8))  # error (row/value dropped) | warning
    code: Mapped[str] = mapped_column(String(48))
    detail: Mapped[str] = mapped_column(Text)
    created_at: Mapped[datetime] = mapped_column(UTCDateTime)


class ProviderFixture(Base):
    """Upcoming fixture as last seen at the live provider."""

    __tablename__ = "provider_fixtures"

    provider: Mapped[str] = mapped_column(String(32), primary_key=True)
    provider_fixture_id: Mapped[str] = mapped_column(String(64), primary_key=True)
    league_code: Mapped[str] = mapped_column(String(8))  # football-data division code
    kickoff_utc: Mapped[datetime] = mapped_column(UTCDateTime)
    home_name: Mapped[str] = mapped_column(String(128))
    away_name: Mapped[str] = mapped_column(String(128))
    home_team: Mapped[str | None] = mapped_column(ForeignKey("teams.team_key"))
    away_team: Mapped[str | None] = mapped_column(ForeignKey("teams.team_key"))
    status: Mapped[str] = mapped_column(String(32))
    first_seen_at: Mapped[datetime] = mapped_column(UTCDateTime)
    last_seen_at: Mapped[datetime] = mapped_column(UTCDateTime)


class OddsSnapshot(Base):
    """A forward-captured pre-match price. Append-only: the genuine ROI record."""

    __tablename__ = "odds_snapshots"

    id: Mapped[int] = mapped_column(primary_key=True)
    provider: Mapped[str] = mapped_column(String(32))
    provider_fixture_id: Mapped[str] = mapped_column(String(64), index=True)
    target: Mapped[str] = mapped_column(String(8))  # T-48h | T-24h | T-2h | T-15m
    scheduled_for_utc: Mapped[datetime] = mapped_column(UTCDateTime)
    captured_at_utc: Mapped[datetime] = mapped_column(UTCDateTime)
    kickoff_utc_at_capture: Mapped[datetime] = mapped_column(UTCDateTime)
    bookmaker: Mapped[str] = mapped_column(String(32))
    market_key: Mapped[str] = mapped_column(String(16))  # 1X2 | OU_1.5 | OU_2.5 | OU_3.5 | BTTS
    line: Mapped[float | None] = mapped_column(Float)
    selection: Mapped[str] = mapped_column(String(4))  # H D A | O U | Y N
    price: Mapped[float] = mapped_column(Float)
    quote_time_utc: Mapped[datetime | None] = mapped_column(UTCDateTime)  # provider's own
    raw_file_id: Mapped[int] = mapped_column(ForeignKey("raw_files.id"))


class CaptureEvent(Base):
    """Outcome of each scheduled snapshot attempt, so gaps and late captures stay visible."""

    __tablename__ = "capture_events"

    id: Mapped[int] = mapped_column(primary_key=True)
    provider: Mapped[str] = mapped_column(String(32))
    provider_fixture_id: Mapped[str] = mapped_column(String(64), index=True)
    target: Mapped[str] = mapped_column(String(8))
    scheduled_for_utc: Mapped[datetime] = mapped_column(UTCDateTime)
    attempted_at_utc: Mapped[datetime] = mapped_column(UTCDateTime)
    status: Mapped[str] = mapped_column(String(16))  # captured | no_odds | missed | error
    n_quotes: Mapped[int] = mapped_column(Integer)
    lateness_seconds: Mapped[int] = mapped_column(Integer)
    raw_file_id: Mapped[int | None] = mapped_column(ForeignKey("raw_files.id"))
    error: Mapped[str | None] = mapped_column(Text)


class EuroMatch(Base):
    """A UEFA club competition match (Champions League, Europa League, Conference League and
    their qualifiers). Scores are regulation time (90 minutes plus stoppage time); extra time
    and penalties are recorded only as flags."""

    __tablename__ = "euro_matches"
    __table_args__ = (
        UniqueConstraint("competition", "season", "match_date", "home_team", "away_team"),
    )

    id: Mapped[int] = mapped_column(primary_key=True)
    competition: Mapped[str] = mapped_column(String(8))  # UCL UEL UECL UCLQ UELQ UECLQ
    season: Mapped[str] = mapped_column(String(4))  # "2627"
    stage: Mapped[str] = mapped_column(String(32))  # e.g. "League MD3", "Round of 16 leg 1"
    match_date: Mapped[date] = mapped_column(Date, index=True)  # local date of the venue
    kickoff_utc: Mapped[datetime | None] = mapped_column(UTCDateTime)
    home_team: Mapped[str] = mapped_column(ForeignKey("teams.team_key"))
    away_team: Mapped[str] = mapped_column(ForeignKey("teams.team_key"))
    home_goals: Mapped[int | None] = mapped_column(Integer)  # regulation time
    away_goals: Mapped[int | None] = mapped_column(Integer)
    extra_time: Mapped[bool] = mapped_column(Boolean, default=False)
    penalties: Mapped[bool] = mapped_column(Boolean, default=False)
    neutral: Mapped[bool] = mapped_column(Boolean, default=False)  # e.g. finals
    status: Mapped[str] = mapped_column(String(16))  # played | scheduled | postponed
    source: Mapped[str] = mapped_column(String(32))  # openfootball | wikipedia
    source_ref: Mapped[str | None] = mapped_column(Text)  # file and line, or page and section
    raw_file_id: Mapped[int | None] = mapped_column(ForeignKey("raw_files.id"))
    model_exclusion: Mapped[str | None] = mapped_column(String(32))


class Prediction(Base):
    """A saved prediction. Append-only (database triggers): corrections and new editions are
    new rows that point at what they supersede."""

    __tablename__ = "predictions"

    id: Mapped[int] = mapped_column(primary_key=True)
    fixture_ref: Mapped[str] = mapped_column(String(128), index=True)
    competition: Mapped[str] = mapped_column(String(8))  # division code or UCL/UEL/UECL
    season: Mapped[str] = mapped_column(String(4))
    kickoff_utc: Mapped[datetime | None] = mapped_column(UTCDateTime)
    match_date: Mapped[date] = mapped_column(Date)
    home_team: Mapped[str] = mapped_column(ForeignKey("teams.team_key"))
    away_team: Mapped[str] = mapped_column(ForeignKey("teams.team_key"))
    edition: Mapped[str] = mapped_column(String(16))  # early | lineup
    # genuine_prematch | retrospective_backtest | exploratory | unavailable
    record_type: Mapped[str] = mapped_column(String(24))
    model_name: Mapped[str] = mapped_column(String(64))
    model_version: Mapped[str] = mapped_column(String(64))
    training_cutoff_utc: Mapped[datetime] = mapped_column(UTCDateTime)
    created_at_utc: Mapped[datetime] = mapped_column(UTCDateTime)
    probabilities: Mapped[str] = mapped_column(Text)  # JSON: every market
    selections: Mapped[str] = mapped_column(Text)  # JSON: pick per market
    features: Mapped[str] = mapped_column(Text)  # JSON: feature snapshot used
    data_freshness: Mapped[str] = mapped_column(Text)  # JSON
    missing_fields: Mapped[str] = mapped_column(Text)  # JSON list
    notes: Mapped[str | None] = mapped_column(Text)
    supersedes_id: Mapped[int | None] = mapped_column(ForeignKey("predictions.id"))
    reason: Mapped[str | None] = mapped_column(Text)


class PredictionSettlement(Base):
    """Result of a saved prediction once the match is final. Append-only."""

    __tablename__ = "prediction_settlements"

    id: Mapped[int] = mapped_column(primary_key=True)
    prediction_id: Mapped[int] = mapped_column(ForeignKey("predictions.id"), index=True)
    settled_at_utc: Mapped[datetime] = mapped_column(UTCDateTime)
    home_goals: Mapped[int | None] = mapped_column(Integer)
    away_goals: Mapped[int | None] = mapped_column(Integer)
    status: Mapped[str] = mapped_column(String(16))  # settled | void | postponed
    outcomes: Mapped[str] = mapped_column(Text)  # JSON: per market, correct/incorrect
    source: Mapped[str] = mapped_column(String(64))


def utcnow() -> datetime:
    return datetime.now(UTC)
