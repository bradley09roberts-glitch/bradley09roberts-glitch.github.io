"""Publishing and settling predictions (M7).

A published prediction is written twice: to the append-only `predictions` table, and as one
JSON line appended to records/predictions/<UK date of creation>.jsonl, which is committed to
git. The git history then shows each prediction existed before kickoff; the database can be
rebuilt from those files. Nothing is ever updated or deleted: corrections and new editions
are new rows that name what they supersede and why.

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
from footy.modeling.grid import TOTAL_LINES, MarketProbs
from footy.models import Prediction, PredictionSettlement, utcnow
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


def pre_match_deadline(fx: FixtureIn) -> datetime:
    """The kickoff, or when the kickoff time isn't announced yet, 00:00 UK on the match day."""
    if fx.kickoff_utc is not None:
        return fx.kickoff_utc
    return datetime(
        fx.match_date.year, fx.match_date.month, fx.match_date.day, tzinfo=UK
    ).astimezone(UTC)


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
    record for the same fixture, edition and model is never duplicated. Without a fixed `now`
    the records are stamped once the write lock is held, just before they are written."""
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
        for j, fx in enumerate(fixtures):
            existing = s.scalar(
                select(Prediction.id).where(
                    Prediction.fixture_ref == fx.ref,
                    Prediction.edition == edition,
                    Prediction.model_name == model_name,
                    Prediction.record_type == record_type,
                )
            )
            if existing is not None:
                ids.append(existing)
                continue
            probs = market_dict(mp, j)
            row = Prediction(
                fixture_ref=fx.ref,
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


def recorded_refs(
    engine: Engine,
    model_name: str,
    *,
    edition: str = "early",
    record_type: str = "genuine_prematch",
) -> set[str]:
    """The fixtures that already have a record of this model, edition and type: the ones
    `publish` would not record again."""
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


def settle(engine: Engine, now: datetime | None = None) -> dict:
    """Settle every unsettled prediction whose match now has a final regulation-time score."""
    now = now or utcnow()
    done = 0
    with Session(engine) as s:
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
                    source="euro_matches" if p.competition in ("UCL", "UEL", "UECL") else "matches",
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
