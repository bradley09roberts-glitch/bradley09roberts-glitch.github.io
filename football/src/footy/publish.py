"""Publishing and settling predictions (M7).

A published prediction is written twice: to the append-only `predictions` table, and as one
JSON line appended to records/predictions/<UK date of creation>.jsonl, which is committed to
git. The git history then shows each prediction existed before kickoff. Nothing is ever
updated or deleted: corrections and new editions are new rows that name what they supersede
and why.

The records files are the master copy. `fd-update` and `euro-update` rebuild the match data
but not the predictions: a new database gets them back with `import_records` (`footy
import-records`), which keeps every record's id, checks each line's sha256 and settles them
again. Until then `publish` refuses to write (`check_records`): a database that lacks records
the files hold would hand out their ids again and record their fixtures a second time.

A fixture's identity: in a league where a pairing meets once a season (`max_meetings` = 1,
e.g. the core ten) it is the pairing in the season, whatever its date, because a date can
change after records are made (a placeholder date, a postponement, a date moved for
television). A second record of the same pairing by the same model is never made, and a new
model's record takes the `fixture_ref` of the pairing's first record, so every model's
record of the match stays under one reference. Elsewhere (UEFA competitions, leagues where a
pairing can meet more than once) the identity is the reference, which includes the date.

The two stay in step even if the process is killed at any moment (the container restarts
without warning). `publish` takes the database's write lock before it reads anything, so
publishers take turns; it saves every row, then writes a pending note (the lines and where
they go), appends the lines in one write, commits, and deletes the note. A note left behind
by a killed run is finished by the next `publish` or `recover_pending` (under the same lock,
before anything else is appended): if the rows were committed it is only deleted; if not,
the lines are completed in the records file and the rows saved from them, exactly as
written, so a rerun neither loses nor repeats a record.
"""

import hashlib
import json
import os
import uuid
from dataclasses import dataclass, field
from datetime import UTC, date, datetime
from pathlib import Path
from zoneinfo import ZoneInfo

import numpy as np
from sqlalchemy import Engine, select, text
from sqlalchemy.orm import Session

from footy.config import PROJECT_ROOT
from footy.ingest.fd_parse import model_cutoff
from footy.modeling.grid import TOTAL_LINES, MarketProbs
from footy.models import Prediction, PredictionSettlement, Team, utcnow
from footy.reference import DIVISIONS

RECORDS_DIR = PROJECT_ROOT / "records" / "predictions"
UK = ZoneInfo("Europe/London")


class PublishError(RuntimeError):
    pass


@dataclass
class FixtureIn:
    competition: str
    season: str
    match_date: date
    kickoff_utc: datetime | None
    home_team: str
    away_team: str
    features: dict = field(default_factory=dict)
    missing_fields: list[str] = field(default_factory=list)

    @property
    def ref(self) -> str:
        return (
            f"{self.competition}:{self.season}:{self.match_date}:{self.home_team}:{self.away_team}"
        )

    @property
    def key(self) -> str:
        """The fixture's identity (see the module docstring): the pairing in the season where
        a pairing meets once a season, otherwise the reference."""
        return fixture_key(
            self.competition, self.season, self.match_date, self.home_team, self.away_team
        )


def single_meeting(competition: str) -> bool:
    """True for a league where a home team hosts an away team once a season (the core ten
    and the other `max_meetings` = 1 leagues); False for UEFA competitions and leagues where
    a pairing can meet more than once (D-022)."""
    d = DIVISIONS.get(competition)
    return d is not None and d.max_meetings == 1


def fixture_key(competition: str, season: str, match_date, home: str, away: str) -> str:
    """A fixture's identity: `competition:season:*:home:away` in a single-meeting league
    (its date can change), otherwise its reference `competition:season:date:home:away`."""
    if single_meeting(competition):
        return f"{competition}:{season}:*:{home}:{away}"
    return f"{competition}:{season}:{match_date}:{home}:{away}"


def pre_match_deadline(fx: FixtureIn) -> datetime:
    """The kickoff, or when the kickoff time isn't known, 00:00 UK on the first day of the
    fixture's round: the Friday for a Friday-to-Monday date, the Tuesday for a Tuesday-to-
    Thursday date (the D-012 collection day, `fd_parse.model_cutoff`). A date without a time
    can be a placeholder (openfootball lists a whole round on its Sunday until the times are
    announced, e.g. La Liga matchday 10 on 25 Oct 2026), so the deadline can't wait for the
    day itself: a game moved to the Friday would already have been played."""
    if fx.kickoff_utc is not None:
        return fx.kickoff_utc
    return model_cutoff(fx.match_date)


def market_dict(mp: MarketProbs, j: int) -> dict:
    out = {}
    if mp.p1x2 is not None:
        out["1X2"] = {
            "H": float(mp.p1x2[j, 0]),
            "D": float(mp.p1x2[j, 1]),
            "A": float(mp.p1x2[j, 2]),
        }
    for line in TOTAL_LINES:
        if line in mp.p_over:
            p = float(mp.p_over[line][j])
            out[f"OU_{line:g}"] = {"O": p, "U": 1 - p}
    if mp.p_btts is not None:
        p = float(mp.p_btts[j])
        out["BTTS"] = {"Y": p, "N": 1 - p}
    if mp.lam_h is not None:
        out["expected_goals"] = {"home": float(mp.lam_h[j]), "away": float(mp.lam_a[j])}
    if mp.grid is not None:
        g = mp.grid[j]
        top = np.dstack(np.unravel_index(np.argsort(g.ravel())[::-1][:6], g.shape))[0]
        out["top_scores"] = [{"score": f"{int(h)}-{int(a)}", "p": float(g[h, a])} for h, a in top]
    return out


def selections(probs: dict) -> dict:
    """The ordinary pick per market: the most likely outcome (draws included for 1X2)."""
    out = {}
    for market, p in probs.items():
        if market in ("expected_goals", "top_scores"):
            continue
        best = max(p, key=p.get)
        out[market] = {"pick": best, "p": p[best]}
    return out


def publish(
    engine: Engine,
    fixtures: list[FixtureIn],
    mp: MarketProbs,
    *,
    model_name: str,
    model_version: str,
    training_cutoff_utc: datetime,
    data_freshness: dict,
    edition: str = "early",
    record_type: str = "genuine_prematch",
    notes: str | None = None,
    now: datetime | None = None,
    records_dir: Path = RECORDS_DIR,
) -> list[int]:
    """Save predictions. A genuine pre-match record must be made before kickoff; an existing
    record for the same fixture (`FixtureIn.key`), edition and model is never duplicated: its
    id is returned instead. A new record of a single-meeting pairing that already has records
    under another date takes their `fixture_ref` (see the module docstring); its own date and
    kickoff are saved as they are now known. Without a fixed `now` the records are stamped
    once the write lock is held, just before they are written. Refuses (PublishError) while
    the records files hold records the database lacks (`check_records`)."""
    ids = []
    new: list[Prediction] = []
    with Session(engine) as s:
        _hold_write_lock(s)
        now = now or utcnow()
        # every fixture is checked before anything is written
        if record_type == "genuine_prematch":
            for fx in fixtures:
                if now >= pre_match_deadline(fx):
                    raise PublishError(f"{fx.ref}: kickoff has passed; cannot publish as pre-match")
        recovered = _recover(s, records_dir)
        _check_records_in(s, records_dir)
        for j, fx in enumerate(fixtures):
            ref, existing = _identify(s, fx, edition, model_name, record_type)
            if existing is not None:
                ids.append(existing)
                continue
            probs = market_dict(mp, j)
            row = Prediction(
                fixture_ref=ref,
                competition=fx.competition,
                season=fx.season,
                kickoff_utc=fx.kickoff_utc,
                match_date=fx.match_date,
                home_team=fx.home_team,
                away_team=fx.away_team,
                edition=edition,
                record_type=record_type,
                model_name=model_name,
                model_version=model_version,
                training_cutoff_utc=training_cutoff_utc,
                created_at_utc=now,
                probabilities=json.dumps(probs, sort_keys=True),
                selections=json.dumps(selections(probs), sort_keys=True),
                features=json.dumps(fx.features, sort_keys=True, default=str),
                data_freshness=json.dumps(data_freshness, sort_keys=True, default=str),
                missing_fields=json.dumps(fx.missing_fields),
                notes=notes,
            )
            s.add(row)
            s.flush()
            ids.append(row.id)
            new.append(row)
        # every row is saved (flushed) before a line is written, so a fixture that fails to
        # save leaves no line behind
        note = _append_records(records_dir, [_record_line(r, now) for r in new], now)
        s.commit()
    for path in [*recovered, *([note] if note else [])]:
        path.unlink(missing_ok=True)
    return ids


def _identify(
    s: Session, fx: FixtureIn, edition: str, model_name: str, record_type: str
) -> tuple[str, int | None]:
    """The `fixture_ref` a new record of `fx` takes, and the id of this model's existing record
    of the fixture (edition and type), if it has one. In a single-meeting league the fixture
    is the pairing in the season (any date) and the reference is its first record's."""
    if not single_meeting(fx.competition):
        existing = s.scalar(
            select(Prediction.id).where(
                Prediction.fixture_ref == fx.ref,
                Prediction.edition == edition,
                Prediction.model_name == model_name,
                Prediction.record_type == record_type,
            )
        )
        return fx.ref, existing
    rows = s.execute(
        select(
            Prediction.id,
            Prediction.fixture_ref,
            Prediction.edition,
            Prediction.model_name,
            Prediction.record_type,
        )
        .where(
            Prediction.competition == fx.competition,
            Prediction.season == fx.season,
            Prediction.home_team == fx.home_team,
            Prediction.away_team == fx.away_team,
        )
        .order_by(Prediction.id)
    ).all()
    ref = rows[0].fixture_ref if rows else fx.ref
    mine = (
        r.id
        for r in rows
        if (r.edition, r.model_name, r.record_type) == (edition, model_name, record_type)
    )
    return ref, next(mine, None)


def recorded_refs(
    engine: Engine,
    model_name: str,
    *,
    edition: str = "early",
    record_type: str = "genuine_prematch",
) -> set[str]:
    """The fixtures that already have a record of this model, edition and type (their
    references)."""
    with Session(engine) as s:
        return set(
            s.scalars(
                select(Prediction.fixture_ref).where(
                    Prediction.model_name == model_name,
                    Prediction.edition == edition,
                    Prediction.record_type == record_type,
                )
            )
        )


def recorded_fixtures(
    engine: Engine,
    model_name: str,
    *,
    edition: str = "early",
    record_type: str = "genuine_prematch",
) -> set[str]:
    """The fixtures `publish` would not record again for this model, edition and type: their
    references and their identities (`FixtureIn.key`), so a fixture whose date has changed
    since its record is recognised by `fx.key in ...`."""
    with Session(engine) as s:
        rows = s.execute(
            select(
                Prediction.fixture_ref,
                Prediction.competition,
                Prediction.season,
                Prediction.match_date,
                Prediction.home_team,
                Prediction.away_team,
            ).where(
                Prediction.model_name == model_name,
                Prediction.edition == edition,
                Prediction.record_type == record_type,
            )
        ).all()
    return {r.fixture_ref for r in rows} | {fixture_key(*r[1:]) for r in rows}


PENDING_PREFIX = ".publish-pending-"


def _hold_write_lock(s: Session) -> None:
    """Take the database's write lock before reading anything (SQLite: BEGIN IMMEDIATE; it
    waits up to the busy timeout). Publishers then take turns: none can record a fixture
    between another's check and insert, append to the records file at the same time, or be
    half-way through while another finishes a killed run's note."""
    if s.get_bind().dialect.name == "sqlite":
        s.execute(text("BEGIN IMMEDIATE"))


def _record_line(row: Prediction, now: datetime) -> str:
    payload = {
        "id": row.id,
        "fixture_ref": row.fixture_ref,
        "competition": row.competition,
        "season": row.season,
        "match_date": row.match_date.isoformat(),
        "kickoff_utc": row.kickoff_utc.isoformat() if row.kickoff_utc else None,
        "home_team": row.home_team,
        "away_team": row.away_team,
        "edition": row.edition,
        "record_type": row.record_type,
        "model_name": row.model_name,
        "model_version": row.model_version,
        "training_cutoff_utc": row.training_cutoff_utc.isoformat(),
        "created_at_utc": now.isoformat(timespec="seconds"),
        "probabilities": json.loads(row.probabilities),
        "selections": json.loads(row.selections),
        "features": json.loads(row.features),
        "data_freshness": json.loads(row.data_freshness),
        "missing_fields": json.loads(row.missing_fields),
        "notes": row.notes,
    }
    line = json.dumps(payload, sort_keys=True)
    payload_sha = hashlib.sha256(line.encode()).hexdigest()
    return json.dumps({"sha256": payload_sha, **payload}, sort_keys=True) + "\n"


def _fsync_dir(path: Path) -> None:
    fd = os.open(path, os.O_RDONLY)
    try:
        os.fsync(fd)
    finally:
        os.close(fd)


def _append_records(records_dir: Path, lines: list[str], now: datetime) -> Path | None:
    """Write the pending note, then append `lines` to the day's records file in one write,
    both forced to disk. Returns the note (deleted once the rows are committed)."""
    if not lines:
        return None
    records_dir.mkdir(parents=True, exist_ok=True)
    path = records_dir / f"{now.astimezone(UK).date().isoformat()}.jsonl"
    offset = path.stat().st_size if path.exists() else 0
    note = records_dir / f"{PENDING_PREFIX}{uuid.uuid4().hex}.json"
    tmp = note.with_name(note.name + ".tmp")
    with tmp.open("w", encoding="utf-8") as f:
        json.dump({"file": path.name, "offset": offset, "lines": lines}, f)
        f.flush()
        os.fsync(f.fileno())
    os.replace(tmp, note)  # a note is either whole or absent
    _fsync_dir(records_dir)
    with path.open("a", encoding="utf-8") as f:
        f.write("".join(lines))
        f.flush()
        os.fsync(f.fileno())
    return note


def _row_from_line(p: dict) -> Prediction:
    """The predictions row a records-file line was written from."""

    def when(v: str | None) -> datetime | None:
        return None if v is None else datetime.fromisoformat(v)

    return Prediction(
        id=p["id"],
        fixture_ref=p["fixture_ref"],
        competition=p["competition"],
        season=p["season"],
        kickoff_utc=when(p["kickoff_utc"]),
        match_date=date.fromisoformat(p["match_date"]),
        home_team=p["home_team"],
        away_team=p["away_team"],
        edition=p["edition"],
        record_type=p["record_type"],
        model_name=p["model_name"],
        model_version=p["model_version"],
        training_cutoff_utc=when(p["training_cutoff_utc"]),
        created_at_utc=when(p["created_at_utc"]),
        probabilities=json.dumps(p["probabilities"], sort_keys=True),
        selections=json.dumps(p["selections"], sort_keys=True),
        features=json.dumps(p["features"], sort_keys=True, default=str),
        data_freshness=json.dumps(p["data_freshness"], sort_keys=True, default=str),
        missing_fields=json.dumps(p["missing_fields"]),
        notes=p["notes"],
    )


def _recover(s: Session, records_dir: Path) -> list[Path]:
    """Finish every publish a killed run left half-done (its pending note), inside `s`, which
    holds the write lock. Returns the notes to delete once `s` is committed. Raises
    PublishError where the records file or the table doesn't match what the note says."""
    if not records_dir.is_dir():
        return []
    for stale in records_dir.glob(f"{PENDING_PREFIX}*.json.tmp"):
        stale.unlink(missing_ok=True)  # never renamed: nothing was appended after it
    notes = []
    for note in records_dir.glob(f"{PENDING_PREFIX}*.json"):
        data = json.loads(note.read_text(encoding="utf-8"))
        notes.append((data["file"], data["offset"], note, data["lines"]))
    done = []
    for name, offset, note, lines in sorted(notes):
        payloads = [json.loads(line) for line in lines]
        saved = dict(
            s.execute(
                select(Prediction.id, Prediction.fixture_ref).where(
                    Prediction.id.in_([p["id"] for p in payloads])
                )
            ).all()
        )
        if all(saved.get(p["id"]) == p["fixture_ref"] for p in payloads):
            done.append(note)  # committed: the lines were written before the commit
            continue
        if saved:
            raise PublishError(f"{note}: some of its ids now belong to other records")
        block = "".join(lines).encode("utf-8")
        path = records_dir / name
        with path.open("a+b") as f:
            f.seek(0)
            have = f.read()
            tail = have[offset:]
            if tail[: len(block)] != block:
                if len(have) < offset or not block.startswith(tail):
                    raise PublishError(f"{note}: {name} doesn't hold what it says from {offset}")
                f.truncate(offset)  # a write cut short: written again whole
                f.seek(offset)
                f.write(block)
                f.flush()
                os.fsync(f.fileno())
        for p in payloads:
            s.add(_row_from_line(p))
        s.flush()
        done.append(note)
    return done


def recover_pending(engine: Engine, records_dir: Path = RECORDS_DIR) -> int:
    """Finish any publish a killed run left half-done (see the module docstring); returns how
    many were finished. `publish` does this itself before it writes."""
    if not records_dir.is_dir() or not any(records_dir.glob(f"{PENDING_PREFIX}*")):
        return 0
    with Session(engine) as s:
        _hold_write_lock(s)
        done = _recover(s, records_dir)
        s.commit()
    for note in done:
        note.unlink(missing_ok=True)
    return len(done)


@dataclass(frozen=True)
class RecordLine:
    """One line of a records file: where it is and what it records."""

    file: str
    line_no: int
    id: int
    fixture_ref: str
    model_name: str


# (size, mtime) -> the lines of a records file, so a publish doesn't re-read unchanged files
_SCANNED: dict[Path, tuple[tuple[int, int], list[RecordLine]]] = {}


def _scan_records(records_dir: Path) -> list[RecordLine]:
    """Every line of every records file (records_dir/*.jsonl), in file and line order."""
    out: list[RecordLine] = []
    if not records_dir.is_dir():
        return out
    for path in sorted(records_dir.glob("*.jsonl")):
        st = path.stat()
        sig = (st.st_size, st.st_mtime_ns)
        hit = _SCANNED.get(path)
        if hit is None or hit[0] != sig:
            lines = []
            for n, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
                if line.strip():
                    p = json.loads(line)
                    lines.append(
                        RecordLine(path.name, n, p["id"], p["fixture_ref"], p["model_name"])
                    )
            hit = (sig, lines)
            _SCANNED[path] = hit
        out.extend(hit[1])
    return out


IMPORT_HINT = "run `uv run footy import-records` (after `fd-update` and `euro-update`)"


def _records_problems(s: Session, records_dir: Path) -> list[str]:
    """Why the database doesn't hold every record the records files hold (empty if it does):
    an id or fixture_ref it lacks, an id it holds for a different record, or a smaller
    largest id (it would hand that id out again)."""
    lines = _scan_records(records_dir)
    if not lines:
        return []
    db = {
        r.id: (r.fixture_ref, r.model_name)
        for r in s.execute(select(Prediction.id, Prediction.fixture_ref, Prediction.model_name))
    }
    problems = []
    missing = [x for x in lines if x.id not in db]
    if missing:
        ids = sorted({x.id for x in missing})
        problems.append(
            f"{len(ids)} record id(s) in the records files are not in the database "
            f"(e.g. {ids[:5]}; {missing[0].file} line {missing[0].line_no})"
        )
    clash = [x for x in lines if x.id in db and db[x.id] != (x.fixture_ref, x.model_name)]
    if clash:
        x = clash[0]
        problems.append(
            f"{len(clash)} record id(s) belong to different records in the database and the "
            f"records files (e.g. id {x.id}: {x.model_name} {x.fixture_ref} in {x.file} line "
            f"{x.line_no}, {db[x.id][1]} {db[x.id][0]} in the database)"
        )
    refs = {x.fixture_ref for x in lines} - {ref for ref, _ in db.values()}
    if refs:
        problems.append(
            f"{len(refs)} fixture_ref(s) in the records files are not in the database "
            f"(e.g. {sorted(refs)[:3]})"
        )
    top = max(x.id for x in lines)
    if top > max(db, default=0):
        problems.append(
            f"the records files' largest id {top} is above the database's {max(db, default=0)}"
        )
    return problems


def _check_records_in(s: Session, records_dir: Path) -> None:
    problems = _records_problems(s, records_dir)
    if problems:
        raise PublishError(
            "refused: the database doesn't hold every published record, so a new record could "
            f"reuse an id or record a fixture twice: {'; '.join(problems)}. {IMPORT_HINT}"
        )


def check_records(engine: Engine, records_dir: Path = RECORDS_DIR) -> None:
    """Raise PublishError if the records files hold a record the database lacks (an id or
    fixture_ref it doesn't have, an id it holds for another record, or a larger id): the
    check `publish` makes before it writes, for a command to make before it starts."""
    with Session(engine) as s:
        _check_records_in(s, records_dir)


def _line_payload(line: str, where: str) -> dict:
    """A records-file line's record, after checking its sha256 (PublishError if it fails)."""
    rec = json.loads(line)
    sha = rec.pop("sha256", None)
    got = hashlib.sha256(json.dumps(rec, sort_keys=True).encode()).hexdigest()
    if sha != got:
        raise PublishError(f"{where}: sha256 doesn't match the record (saved {sha}, got {got})")
    return rec


def _advance_sequence(s: Session) -> None:
    """Make the next new record's id follow the largest one. SQLite gives an INTEGER PRIMARY
    KEY without AUTOINCREMENT max(id) + 1 by itself; PostgreSQL's sequence must be moved."""
    if s.get_bind().dialect.name == "postgresql":
        s.execute(
            text(
                "SELECT setval(pg_get_serial_sequence('predictions', 'id'), "
                "(SELECT max(id) FROM predictions))"
            )
        )


def import_records(engine: Engine, records_dir: Path = RECORDS_DIR) -> dict:
    """Load every record of the records files into the predictions table, keeping its id
    (after a rebuilt or lost database). Every line's sha256 is checked first and nothing is
    written unless all are sound. A record the database already holds must be the same
    record (id, fixture, model, creation time); anything else is refused. A killed
    publish's note is finished first. Run `settle` afterwards (the CLI command does)."""
    with Session(engine) as s:
        _hold_write_lock(s)
        recovered = _recover(s, records_dir)
        s.flush()
        records: list[tuple[str, dict]] = []
        files = sorted(records_dir.glob("*.jsonl")) if records_dir.is_dir() else []
        for path in files:
            for n, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
                if line.strip():
                    where = f"{path.name} line {n}"
                    records.append((where, _line_payload(line, where)))
        seen: dict[int, str] = {}
        for where, rec in records:
            if rec["id"] in seen:
                raise PublishError(f"{where}: id {rec['id']} is also on {seen[rec['id']]}")
            seen[rec["id"]] = where
        have = {
            r.id: r
            for r in s.execute(
                select(
                    Prediction.id,
                    Prediction.fixture_ref,
                    Prediction.model_name,
                    Prediction.created_at_utc,
                )
            )
        }
        teams = set(s.scalars(select(Team.team_key)))
        lacking = sorted({t for _, r in records for t in (r["home_team"], r["away_team"])} - teams)
        if lacking:
            raise PublishError(
                f"{len(lacking)} team(s) the records name are not in the database "
                f"(e.g. {lacking[:5]}): run `fd-update` and `euro-update` first"
            )
        added = present = 0
        for where, rec in records:
            old = have.get(rec["id"])
            if old is None:
                s.add(_row_from_line(rec))
                added += 1
                continue
            created = datetime.fromisoformat(rec["created_at_utc"])
            old_created = old.created_at_utc
            if old_created.tzinfo is None:
                old_created = old_created.replace(tzinfo=UTC)
            if (old.fixture_ref, old.model_name) != (rec["fixture_ref"], rec["model_name"]) or (
                old_created.replace(microsecond=0) != created
            ):
                raise PublishError(
                    f"{where}: id {rec['id']} is a different record in the database "
                    f"({old.model_name} {old.fixture_ref} made {old.created_at_utc}); "
                    "nothing imported"
                )
            present += 1
        s.flush()
        _advance_sequence(s)
        s.commit()
    for note in recovered:
        note.unlink(missing_ok=True)
    return {
        "files": len(files),
        "records": len(records),
        "imported": added,
        "already_present": present,
        "largest_id": max(seen, default=None),
    }


def latest_settlements(s: Session, ids: list[int] | None = None) -> dict[int, PredictionSettlement]:
    """Each prediction's latest settlement (the highest settlement id wins), optionally only
    for `ids`. Settlements are append-only, so a correction is a later row."""
    q = select(PredictionSettlement).order_by(PredictionSettlement.id)
    if ids is not None:
        q = q.where(PredictionSettlement.prediction_id.in_(ids))
    return {x.prediction_id: x for x in s.scalars(q)}


def settle(engine: Engine, now: datetime | None = None) -> dict:
    """Settle every unsettled prediction whose match now has a final regulation-time score.
    Holds the write lock from the start (as `publish` does), so two runs at once take turns
    and the second finds nothing left to settle instead of settling the same records again."""
    now = now or utcnow()
    done = 0
    with Session(engine) as s:
        _hold_write_lock(s)
        settled = set(s.scalars(select(PredictionSettlement.prediction_id)))
        pending = [p for p in s.scalars(select(Prediction)) if p.id not in settled]
        for p in pending:
            score = _final_score(s, p)
            if score is None:
                continue
            hg, ag = score
            probs = json.loads(p.probabilities)
            sel = json.loads(p.selections)
            outcomes = {}
            res = "H" if hg > ag else "D" if hg == ag else "A"
            for market, pick in sel.items():
                if market == "1X2":
                    actual = res
                elif market.startswith("OU_"):
                    actual = "O" if hg + ag > float(market[3:]) else "U"
                elif market == "BTTS":
                    actual = "Y" if hg > 0 and ag > 0 else "N"
                else:
                    continue
                outcomes[market] = {
                    "pick": pick["pick"],
                    "actual": actual,
                    "correct": pick["pick"] == actual,
                    "p_actual": probs[market][actual],
                }
            s.add(
                PredictionSettlement(
                    prediction_id=p.id,
                    settled_at_utc=now,
                    home_goals=hg,
                    away_goals=ag,
                    status="settled",
                    outcomes=json.dumps(outcomes, sort_keys=True),
                    source=_settlement_source(s, p),
                )
            )
            done += 1
        s.commit()
    return {"settled": done}


def _final_score(s: Session, p: Prediction) -> tuple[int, int] | None:
    if p.competition in ("UCL", "UEL", "UECL"):
        q = text(
            "SELECT home_goals, away_goals FROM euro_matches WHERE competition = :c AND "
            "home_team = :h AND away_team = :a AND match_date = :d AND status = 'played'"
        )
        params = {"c": p.competition, "h": p.home_team, "a": p.away_team, "d": p.match_date}
        r = s.execute(q, params).first()
    else:
        r = _domestic_score(s, p)
    if r is None or r[0] is None:
        return None
    return int(r[0]), int(r[1])


def _settlement_source(s: Session, p: Prediction) -> str:
    """"matches", "euro_matches", or "euro_matches:manual" for a UEFA score entered by hand
    from news reports (`footy euro-import-results`, DECISIONS.md D-029): a later source may
    confirm or correct that row, so the settlement keeps where its score came from."""
    if p.competition not in ("UCL", "UEL", "UECL"):
        return "matches"
    source = s.execute(
        text(
            "SELECT source FROM euro_matches WHERE competition = :c AND home_team = :h AND "
            "away_team = :a AND match_date = :d AND status = 'played'"
        ),
        {"c": p.competition, "h": p.home_team, "a": p.away_team, "d": p.match_date},
    ).scalar()
    return "euro_matches:manual" if source == "manual" else "euro_matches"


def _domestic_score(s: Session, p: Prediction):
    """The stored match a domestic prediction is for. Where a pairing meets once a season
    (`max_meetings` = 1, e.g. the core ten) that is the pairing's row, whatever its date (a
    postponed match keeps its prediction). Where it can meet more than once (D-022), only
    the meeting on the predicted date settles it: the prediction stays unsettled rather
    than take another meeting's score."""
    division = DIVISIONS.get(p.competition)
    rows = s.execute(
        text(
            "SELECT home_goals, away_goals, match_date FROM matches WHERE division = :c AND "
            "season = :s AND home_team = :h AND away_team = :a ORDER BY meeting"
        ),
        {"c": p.competition, "s": p.season, "h": p.home_team, "a": p.away_team},
    ).all()
    if division is None or division.max_meetings == 1:
        return rows[0] if rows else None
    day = p.match_date.isoformat()
    return next((r for r in rows if str(r.match_date) == day), None)


def now_utc() -> datetime:
    return datetime.now(UTC)
