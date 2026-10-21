"""Publishing and settling predictions (M7).

A published prediction is written twice: to the append-only `predictions` table, and as one
JSON line appended to records/predictions/<UK date of creation>.jsonl, which is committed to
git. The git history then shows each prediction existed before kickoff; the database can be
rebuilt from those files. Nothing is ever updated or deleted: corrections and new editions
are new rows that name what they supersede and why.
"""

import hashlib
import json
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
    record for the same fixture, edition and model is never duplicated."""
    now = now or utcnow()
    ids = []
    with Session(engine) as s:
        for j, fx in enumerate(fixtures):
            if record_type == "genuine_prematch" and now >= pre_match_deadline(fx):
                raise PublishError(f"{fx.ref}: kickoff has passed; cannot publish as pre-match")
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
            _append_record(records_dir, row, now)
        s.commit()
    return ids


def _append_record(records_dir: Path, row: Prediction, now: datetime) -> None:
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
    records_dir.mkdir(parents=True, exist_ok=True)
    path = records_dir / f"{now.astimezone(UK).date().isoformat()}.jsonl"
    with path.open("a", encoding="utf-8") as f:
        f.write(json.dumps({"sha256": payload_sha, **payload}, sort_keys=True) + "\n")


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
