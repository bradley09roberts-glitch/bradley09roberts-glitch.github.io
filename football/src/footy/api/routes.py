"""Read-only API for the website: predictions, past matches, backtests, performance.

Everything returned is read from saved records (predictions, settlements, ledgers, manifests)
or the imported match data. Nothing is computed afresh for display, so the site can only
show numbers that already exist in a record.
"""

import json
from collections.abc import Callable
from datetime import UTC, date, datetime, timedelta
from pathlib import Path

import pandas as pd
from fastapi import APIRouter, HTTPException, Query
from fastapi.responses import FileResponse
from sqlalchemy import Engine, select, text
from sqlalchemy.orm import Session

from footy.config import Settings
from footy.models import Prediction, PredictionSettlement, Team
from footy.reference import DIVISIONS

COMPETITION_NAMES = {
    **{c: d.name for c, d in DIVISIONS.items()},
    "UCL": "Champions League",
    "UEL": "Europa League",
    "UECL": "Conference League",
}


def make_router(engine_fn: Callable[[], Engine], settings: Settings) -> APIRouter:
    r = APIRouter(prefix="/api")

    def teams(s: Session) -> dict[str, str]:
        return {t.team_key: t.name for t in s.scalars(select(Team))}

    def settlements(s: Session) -> dict[int, PredictionSettlement]:
        return {x.prediction_id: x for x in s.scalars(select(PredictionSettlement))}

    def card(p: Prediction, names: dict, st: PredictionSettlement | None) -> dict:
        probs = json.loads(p.probabilities)
        return {
            "id": p.id,
            "fixture_ref": p.fixture_ref,
            "competition": p.competition,
            "competition_name": COMPETITION_NAMES.get(p.competition, p.competition),
            "season": p.season,
            "match_date": p.match_date.isoformat(),
            "kickoff_utc": p.kickoff_utc.isoformat() if p.kickoff_utc else None,
            "home_team": p.home_team,
            "away_team": p.away_team,
            "home_name": names.get(p.home_team, p.home_team),
            "away_name": names.get(p.away_team, p.away_team),
            "edition": p.edition,
            "record_type": p.record_type,
            "model_name": p.model_name,
            "model_version": p.model_version,
            "created_at_utc": p.created_at_utc.isoformat(),
            "training_cutoff_utc": p.training_cutoff_utc.isoformat(),
            "probabilities": probs,
            "selections": json.loads(p.selections),
            "data_freshness": json.loads(p.data_freshness),
            "missing_fields": json.loads(p.missing_fields),
            "notes": p.notes,
            "settlement": None
            if st is None
            else {
                "home_goals": st.home_goals,
                "away_goals": st.away_goals,
                "status": st.status,
                "outcomes": json.loads(st.outcomes),
                "settled_at_utc": st.settled_at_utc.isoformat(),
            },
        }

    @r.get("/fixtures")
    def fixtures(days: int = Query(8, ge=1, le=30)) -> dict:
        """Upcoming fixtures with their saved predictions (latest edition per fixture/model)."""
        now = datetime.now(UTC)
        today = now.date()
        with Session(engine_fn()) as s:
            names, st = teams(s), settlements(s)
            rows = s.scalars(
                select(Prediction)
                .where(Prediction.match_date >= today - timedelta(days=1))
                .where(Prediction.match_date <= today + timedelta(days=days))
                .where(Prediction.record_type == "genuine_prematch")
                .order_by(Prediction.kickoff_utc, Prediction.id)
            ).all()
            latest: dict[tuple, Prediction] = {}
            for p in rows:
                latest[(p.fixture_ref, p.model_name)] = p
            cards = [card(p, names, st.get(p.id)) for p in latest.values()]
        groups: dict[str, list] = {}
        for c in sorted(cards, key=lambda c: (c["kickoff_utc"] or c["match_date"], c["id"])):
            groups.setdefault(c["competition"], []).append(c)
        return {
            "generated_at_utc": now.isoformat(timespec="seconds"),
            "competitions": [
                {"code": k, "name": COMPETITION_NAMES.get(k, k), "fixtures": v}
                for k, v in groups.items()
            ],
        }

    @r.get("/predictions/{pid}")
    def prediction(pid: int) -> dict:
        with Session(engine_fn()) as s:
            p = s.get(Prediction, pid)
            if p is None:
                raise HTTPException(404, "no such prediction")
            names, st = teams(s), settlements(s)
            out = card(p, names, st.get(p.id))
            out["features"] = json.loads(p.features)
            editions = s.scalars(
                select(Prediction)
                .where(Prediction.fixture_ref == p.fixture_ref)
                .order_by(Prediction.id)
            ).all()
            out["editions"] = [
                {
                    "id": e.id,
                    "edition": e.edition,
                    "model_name": e.model_name,
                    "created_at_utc": e.created_at_utc.isoformat(),
                    "record_type": e.record_type,
                    "reason": e.reason,
                }
                for e in editions
            ]
            out["recent"] = {
                "home": recent_form(s, p.home_team, p.training_cutoff_utc, names),
                "away": recent_form(s, p.away_team, p.training_cutoff_utc, names),
            }
        out["explanation"] = explain(out)
        return out

    @r.get("/past")
    def past(
        competition: str | None = None,
        record_type: str = "genuine_prematch",
        limit: int = Query(200, ge=1, le=2000),
    ) -> dict:
        """Settled predictions, newest first. Genuine pre-match and retrospective records are
        never mixed in one response."""
        with Session(engine_fn()) as s:
            names, st = teams(s), settlements(s)
            q = select(Prediction).where(Prediction.record_type == record_type)
            if competition:
                q = q.where(Prediction.competition == competition)
            rows = s.scalars(q.order_by(Prediction.match_date.desc(), Prediction.id.desc())).all()
            cards = [card(p, names, st.get(p.id)) for p in rows if p.id in st][:limit]
        return {"record_type": record_type, "matches": cards}

    @r.get("/performance")
    def performance() -> dict:
        """The genuine pre-match record: settled predictions only, per market."""
        with Session(engine_fn()) as s:
            st = settlements(s)
            rows = s.scalars(
                select(Prediction).where(Prediction.record_type == "genuine_prematch")
            ).all()
        per: dict[str, dict] = {}
        pending = 0
        for p in rows:
            x = st.get(p.id)
            if x is None:
                pending += 1
                continue
            for market, o in json.loads(x.outcomes).items():
                m = per.setdefault(market, {"n": 0, "correct": 0, "sum_p_actual": 0.0})
                m["n"] += 1
                m["correct"] += int(o["correct"])
                m["sum_p_actual"] += o["p_actual"]
        for m in per.values():
            m["accuracy"] = m["correct"] / m["n"] if m["n"] else None
            m["mean_p_actual"] = m.pop("sum_p_actual") / m["n"] if m["n"] else None
        return {
            "published": len(rows),
            "settled": len(rows) - pending,
            "pending": pending,
            "markets": per,
        }

    @r.get("/backtests")
    def backtests() -> dict:
        out = []
        for root in (settings.data_dir / "backtests", settings.data_dir / "backtests_euro"):
            for d in sorted(root.glob("*")) if root.exists() else []:
                mf = d / "manifest.json"
                if mf.exists():
                    m = json.loads(mf.read_text())
                    out.append(
                        {
                            "run_id": d.name,
                            "kind": root.name,
                            **{
                                k: m.get(k)
                                for k in (
                                    "model",
                                    "split",
                                    "seasons",
                                    "leagues",
                                    "created_at_utc",
                                    "headline",
                                )
                            },
                        }
                    )
        return {"runs": out}

    def _run_dir(run_id: str) -> Path:
        for root in (settings.data_dir / "backtests", settings.data_dir / "backtests_euro"):
            d = root / run_id
            if d.is_dir() and d.resolve().parent == root.resolve():
                return d
        raise HTTPException(404, "no such run")

    @r.get("/backtests/{run_id}/summary")
    def backtest_summary(run_id: str, league: str | None = None, season: str | None = None) -> dict:
        from footy.modeling.grid import TOTAL_LINES
        from footy.modeling.ledger import headline
        from footy.modeling.metrics import calibration_table

        led = pd.read_csv(_run_dir(run_id) / "ledger.csv", float_precision="round_trip")
        if league:
            led = led[led.league == league] if "league" in led else led[led.comp == league]
        if season:
            led = led[led.season.astype(str) == season]
        pred = led[led.status == "predicted"]
        cal = {}
        if "p_H" in pred:
            cal["1X2_home"] = calibration_table(
                pred.p_H, (pred.home_goals > pred.away_goals).astype(int)
            )
            cal["1X2_draw"] = calibration_table(
                pred.p_D, (pred.home_goals == pred.away_goals).astype(int)
            )
            cal["1X2_away"] = calibration_table(
                pred.p_A, (pred.home_goals < pred.away_goals).astype(int)
            )
        for k in TOTAL_LINES:
            col = f"p_over_{k:g}"
            if col in pred:
                cal[f"OU_{k:g}"] = calibration_table(
                    pred[col], ((pred.home_goals + pred.away_goals) > k).astype(int)
                )
        if "p_btts" in pred:
            cal["BTTS"] = calibration_table(
                pred.p_btts, ((pred.home_goals > 0) & (pred.away_goals > 0)).astype(int)
            )
        return {"run_id": run_id, "headline": headline(led, with_ci=True), "calibration": cal}

    @r.get("/backtests/{run_id}/ledger.{fmt}")
    def backtest_ledger(run_id: str, fmt: str):
        d = _run_dir(run_id)
        if fmt not in ("csv", "json"):
            raise HTTPException(404, "csv or json")
        path = d / f"ledger.{fmt}"
        if not path.exists():
            raise HTTPException(404, "not exported")
        return FileResponse(path, filename=f"{run_id}.{fmt}")

    @r.get("/reports/{name}")
    def report(name: str) -> dict:
        """Saved JSON reports (model selection, benchmark gate, profit simulation)."""
        root = settings.data_dir / "reports"
        path = root / f"{name}.json"
        if not path.exists() or path.resolve().parent != root.resolve():
            raise HTTPException(404, "no such report")
        return json.loads(path.read_text())

    return r


def recent_form(s: Session, team: str, before: datetime, names: dict, n: int = 6) -> list[dict]:
    """The team's last results before the prediction's training cutoff (no hindsight)."""
    day = before.date() if isinstance(before, datetime) else before
    q = text(
        "SELECT match_date, division AS comp, home_team, away_team, home_goals, away_goals "
        "FROM matches WHERE (home_team = :t OR away_team = :t) AND match_date < :d "
        "UNION ALL SELECT match_date, competition, home_team, away_team, home_goals, away_goals "
        "FROM euro_matches WHERE (home_team = :t OR away_team = :t) AND match_date < :d "
        "AND status = 'played' "
        "ORDER BY match_date DESC LIMIT :n"
    )
    out = []
    for d, comp, h, a, hg, ag in s.execute(q, {"t": team, "d": day, "n": n}).all():
        home = h == team
        gf, ga = (hg, ag) if home else (ag, hg)
        out.append(
            {
                "date": str(d),
                "competition": comp,
                "venue": "H" if home else "A",
                "opponent": names.get(a if home else h, a if home else h),
                "score": f"{hg}-{ag}",
                "result": "W" if gf > ga else "D" if gf == ga else "L",
            }
        )
    return out


def explain(c: dict) -> str:
    """Plain-English summary built only from the saved record's numbers."""
    p = c["probabilities"]
    parts = []
    xg = p.get("expected_goals")
    if xg:
        parts.append(
            f"The model expects {c['home_name']} to score {xg['home']:.2f} goals and "
            f"{c['away_name']} {xg['away']:.2f}."
        )
    if "1X2" in p:
        x = p["1X2"]
        parts.append(f"Home win {x['H']:.0%}, draw {x['D']:.0%}, away win {x['A']:.0%}.")
    if p.get("top_scores"):
        t = p["top_scores"][0]
        parts.append(f"The single most likely score is {t['score']} ({t['p']:.0%}).")
    if "OU_2.5" in p and "BTTS" in p:
        parts.append(
            f"Over 2.5 goals {p['OU_2.5']['O']:.0%}; both teams to score {p['BTTS']['Y']:.0%}."
        )
    f = c.get("features") or {}
    if f.get("home_matches_in_window") is not None:
        parts.append(
            f"Based on {f['home_matches_in_window']} and {f['away_matches_in_window']} recent "
            "matches for the two sides; no lineup or injury information is used in this edition."
        )
    return " ".join(parts)


def parse_date(s: str) -> date:
    return date.fromisoformat(s)
