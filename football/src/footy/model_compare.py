"""Every model's genuine pre-match records, side by side.

Two views, both read only from saved records and their settlements (nothing is re-predicted):

- `fixture_models`: one match, one row per model with a genuine pre-match record for it (its
  first edition, later editions listed), the record of record first (D-026);
- `leaderboard`: per competition group, every model with settled records: how often its match
  result pick was right (with a 95% Wilson interval), its mean ranked probability score (RPS)
  and its mean log losses, and a paired comparison with each match's record of record on the
  matches both recorded.

Genuine pre-match records only: each was saved before kickoff and is never changed. Backtest
predictions (computed after the fact) are in footy.backtest_explorer and never mixed in here.
A purely market-derived record (footy.model_catalogue.is_market_only) is left out: its
probabilities would be a price in disguise (D-013, D-024). Model + market records are model
output and are shown, marked by `basis`.
"""

from __future__ import annotations

import json
import math
from collections.abc import Iterable, Mapping
from dataclasses import dataclass
from datetime import datetime

import numpy as np
from sqlalchemy import select
from sqlalchemy.orm import Session

from footy import model_catalogue as cat
from footy import models_of_record
from footy.modeling.hit_rates import FAMILY_NAMES, wilson
from footy.modeling.metrics import EPS, outcome_index, rps
from footy.models import Prediction, PredictionSettlement

KIND = "genuine_prematch"
SMALL_SAMPLE = 100  # settled matches below which a model's numbers are flagged
SHOWN_MARKETS = ("1X2", "OU_1.5", "OU_2.5", "OU_3.5", "BTTS")
LEADERBOARD_LOG_LOSS = ("OU_2.5", "BTTS")
# RPS differences this small are rounding (two records with the same 1X2 probabilities, such as
# two model + market records tilted onto the same prices), so the match counts as level.
LEVEL = 1e-9

FIXTURE_EXPLANATION = (
    "Every model's genuine pre-match record for this match: each was saved before kickoff and "
    "has not been changed since. The first edition each model published is shown; any later "
    "editions are listed. Once the match is settled, each market shows whether the model's "
    "pick was right, and the match's ranked probability score (RPS, lower is better) and log "
    "losses are given."
)
LEADERBOARD_EXPLANATION = (
    "Genuine pre-match records only: each saved before kickoff and settled on the final "
    "score. Each match counts once per model, with that model's first edition. Results right "
    "is how often the model's match result pick (home, draw or away) happened, with a 95% "
    "interval; RPS and log loss are lower when better. The comparison with the model of record "
    "uses only the matches where both have a settled record. Under 100 settled matches, the "
    "numbers can move a lot with a few results."
)
RECORD_DESCRIPTION = (
    "Each match's record of record (DECISIONS.md D-026): the first listed model of record "
    "with a record for it, first edition, as the Performance page counts it."
)


# ---------------------------------------------------------------------------------------------
# One settled record


def _num(v: object) -> float | None:
    return float(v) if isinstance(v, int | float) and not isinstance(v, bool) else None


def match_rps(probs: Mapping, home_goals: int, away_goals: int) -> float | None:
    """The ranked probability score of a record's 1X2 probabilities on the final score."""
    x = probs.get("1X2") if isinstance(probs, Mapping) else None
    if not isinstance(x, Mapping):
        return None
    p = [_num(x.get(k)) for k in ("H", "D", "A")]
    if any(v is None for v in p):
        return None
    return float(rps(np.array([p]), outcome_index([home_goals], [away_goals]))[0])


def log_loss(p_actual: object) -> float | None:
    p = _num(p_actual)
    return None if p is None else -math.log(min(max(p, EPS), 1.0))


@dataclass(frozen=True)
class Scored:
    """A settled record's scores: per market the pick, the outcome, whether the pick was right
    and the probability given to what happened; the RPS and the log losses."""

    markets: dict[str, dict]
    rps: float | None
    log_loss: dict[str, float]

    @property
    def result_right(self) -> bool | None:
        m = self.markets.get("1X2")
        return None if m is None else bool(m["correct"])

    def as_json(self) -> dict:
        return {"markets": self.markets, "rps": self.rps, "log_loss": self.log_loss}


def score(probs: Mapping, st: PredictionSettlement | None) -> Scored | None:
    """None unless the record is settled with a final score (a void or postponed match has no
    scores)."""
    if st is None or st.status != "settled" or st.home_goals is None or st.away_goals is None:
        return None
    outcomes = json.loads(st.outcomes) if st.outcomes else {}
    markets: dict[str, dict] = {}
    losses: dict[str, float] = {}
    for m in SHOWN_MARKETS:
        o = outcomes.get(m)
        if not isinstance(o, Mapping) or "correct" not in o:
            continue
        markets[m] = {
            "pick": o.get("pick"),
            "actual": o.get("actual"),
            "correct": bool(o["correct"]),
            "p_actual": _num(o.get("p_actual")),
        }
        ll = log_loss(o.get("p_actual"))
        if ll is not None:
            losses[m] = ll
    return Scored(markets, match_rps(probs, st.home_goals, st.away_goals), losses)


def shown_probabilities(probs: Mapping) -> dict:
    """The markets the comparison shows: 1X2, O/U 1.5, 2.5 and 3.5, BTTS, expected goals and
    the single most likely score."""
    out = {m: probs[m] for m in SHOWN_MARKETS if isinstance(probs.get(m), Mapping)}
    if isinstance(probs.get("expected_goals"), Mapping):
        out["expected_goals"] = probs["expected_goals"]
    top = probs.get("top_scores")
    out["top_score"] = top[0] if isinstance(top, list) and top else None
    return out


# ---------------------------------------------------------------------------------------------
# Shared helpers


def _features(p: Prediction) -> dict:
    try:
        f = json.loads(p.features) if p.features else {}
    except ValueError:
        return {}
    return f if isinstance(f, dict) else {}


def settlements_for(s: Session, ids: Iterable[int]) -> dict[int, PredictionSettlement]:
    """The latest settlement of each record (settlements are append-only)."""
    ids = list(ids)
    out: dict[int, PredictionSettlement] = {}
    for i in range(0, len(ids), 500):
        q = (
            select(PredictionSettlement)
            .where(PredictionSettlement.prediction_id.in_(ids[i : i + 500]))
            .order_by(PredictionSettlement.id)
        )
        for x in s.scalars(q):
            out[x.prediction_id] = x
    return out


def earlier_models(periods: Mapping, group: str, now: datetime) -> set[str]:
    """Models named in a period of the group other than the one in force now."""
    current = set(models_of_record.models_at(periods.get(group, []), now))
    return {m for p in periods.get(group, []) for m in p.models} - current


def fixture_role(
    model: str,
    record: str | None,
    order: Iterable[str],
    shadow_group: Mapping,
    earlier: Iterable[str],
) -> str:
    """A model's role on one fixture: `model_of_record` for the fixture's record of record
    (`record`), `fallback_of_record` for another model of the list that applies to it
    (`order`), else the role shadow_models.json gives (`shadow_group`: that group's entries),
    else `earlier_record` for a model of record of an earlier period, else `other`."""
    if model == record:
        return cat.MODEL_OF_RECORD
    if model in set(order):
        return cat.FALLBACK_OF_RECORD
    entry = shadow_group.get(model)
    if entry and entry.get("role") in cat.SHADOW_ROLES:
        return str(entry["role"])
    return cat.EARLIER_RECORD if model in set(earlier) else cat.OTHER


# ---------------------------------------------------------------------------------------------
# Every model for one fixture


def fixture_models(
    s: Session,
    fixture_ref: str,
    ror,
    periods: Mapping,
    shadow: Mapping,
    names: Mapping[str, str],
    media: Mapping[str, dict],
    now: datetime,
) -> dict | None:
    """The `/api/fixtures/{fixture_ref}/models` body, or None if the fixture has no genuine
    pre-match record. `ror` is footy.api.routes.RecordsOfRecord, `periods` the models of
    record (footy.models_of_record.load), `shadow` load_shadow_models(), `media` team key ->
    media summary for both teams."""
    from footy.api.routes import COMPETITION_NAMES, basis_of, competition_group, iso_utc

    rows = s.scalars(
        select(Prediction)
        .where(Prediction.fixture_ref == fixture_ref)
        .where(Prediction.record_type == KIND)
        .order_by(Prediction.created_at_utc, Prediction.id)
    ).all()
    rows = [p for p in rows if not cat.is_market_only(p.model_name, _features(p))]
    if not rows:
        return None
    st = settlements_for(s, (p.id for p in rows))
    latest = rows[-1]
    competition = latest.competition
    group = competition_group(competition)
    record = ror.model_for(fixture_ref, competition)
    order = ror.order_for(fixture_ref, competition)
    # Models of record of an earlier period only (not of the one in force now).
    earlier = earlier_models(periods, group, now)
    g_shadow = shadow.get(group, {})

    by_model: dict[str, list[Prediction]] = {}
    for p in rows:
        by_model.setdefault(p.model_name, []).append(p)

    def role(model: str) -> str:
        return fixture_role(model, record, order, g_shadow, earlier)

    def before_prices(p: Prediction, features: dict, probs: Mapping) -> bool:
        """True only for a model + market record whose expected goals are its base model's
        own: equal to that model's record for the fixture (the one the match page compares
        with, footy.api.routes.counterpart). Records since 21 Oct 2026 take them from the
        tilted grid (footy.modeling.lab.market_blend), and with no base record to compare,
        nothing is claimed."""
        from footy.api.routes import MODEL_AND_MARKET, counterpart

        if basis_of(features) != MODEL_AND_MARKET:
            return False
        other = counterpart(p, features, rows)
        mine = probs.get("expected_goals")
        theirs = None if other is None else json.loads(other.probabilities).get("expected_goals")
        if not isinstance(mine, Mapping) or not isinstance(theirs, Mapping):
            return False
        return all(
            (a := _num(mine.get(k))) is not None
            and (b := _num(theirs.get(k))) is not None
            and abs(a - b) < 1e-9
            for k in ("home", "away")
        )

    out_models = []
    for model, recs in by_model.items():
        first = recs[0]
        probs = json.loads(first.probabilities)
        features = _features(first)
        basis = basis_of(features)
        scored = score(probs, st.get(first.id))
        entry = g_shadow.get(model)
        r = role(model)
        out_models.append(
            {
                "model": model,
                "label": cat.label_of(model, entry),
                "role": r,
                "description": cat.description_of(model, entry),
                "basis": basis,
                "is_record_of_record": model == record,
                "prediction_id": first.id,
                "edition": first.edition,
                "model_version": first.model_version,
                "published_at_utc": iso_utc(first.created_at_utc),
                "later_editions": [
                    {
                        "prediction_id": e.id,
                        "edition": e.edition,
                        "published_at_utc": iso_utc(e.created_at_utc),
                    }
                    for e in recs[1:]
                ],
                "probabilities": shown_probabilities(probs),
                # Its expected goals are its base model's own, before the prices (checked
                # against that model's record; see before_prices).
                "expected_goals_before_prices": before_prices(first, features, probs),
                "settlement": None if scored is None else scored.as_json(),
            }
        )

    def rank(m: dict) -> tuple:
        listed = order.index(m["model"]) if m["model"] in order else len(order)
        return (
            not m["is_record_of_record"],
            listed,
            cat.ROLE_ORDER.get(m["role"], len(cat.ROLE_ORDER)),
            m["published_at_utc"] or "",
            m["model"],
        )

    out_models.sort(key=rank)
    settled = [st[p.id] for p in rows if p.id in st and st[p.id].status == "settled"]
    final = max(settled, key=lambda x: (x.settled_at_utc, x.id)) if settled else None
    kickoff = next((p.kickoff_utc for p in reversed(rows) if p.kickoff_utc is not None), None)
    return {
        "kind": KIND,
        "explanation": FIXTURE_EXPLANATION,
        "generated_at_utc": iso_utc(now, "seconds"),
        "fixture_ref": fixture_ref,
        "competition": competition,
        "competition_name": COMPETITION_NAMES.get(competition, competition),
        "group": group,
        "season": latest.season,
        "match_date": latest.match_date.isoformat(),
        "kickoff_utc": iso_utc(kickoff),
        "home_team": latest.home_team,
        "away_team": latest.away_team,
        "home_name": names.get(latest.home_team, latest.home_team),
        "away_name": names.get(latest.away_team, latest.away_team),
        "home_media": media.get(latest.home_team),
        "away_media": media.get(latest.away_team),
        "record_of_record": record,
        "result": None
        if final is None
        else {
            "home_goals": final.home_goals,
            "away_goals": final.away_goals,
            "settled_at_utc": iso_utc(final.settled_at_utc),
        },
        "roles": cat.ROLE_LABELS,
        "models": out_models,
    }


# ---------------------------------------------------------------------------------------------
# Live leaderboard


def _rate(hits: list[bool]) -> dict:
    n, correct = len(hits), sum(hits)
    ci = wilson(correct, n)
    return {
        "n": n,
        "correct": correct,
        "hit_rate": correct / n if n else None,
        "ci95": list(ci) if ci else None,
    }


def _mean(values: list[float]) -> float | None:
    return sum(values) / len(values) if values else None


def _block(scores: list[Scored]) -> dict:
    """Results right, mean RPS and mean log losses over these settled records."""
    hits = [x.result_right for x in scores if x.result_right is not None]
    return {
        "settled": len(scores),
        "results_right": _rate([bool(h) for h in hits]),
        "mean_rps": _mean([x.rps for x in scores if x.rps is not None]),
        "mean_log_loss": {
            m: _mean([x.log_loss[m] for x in scores if m in x.log_loss])
            for m in LEADERBOARD_LOG_LOSS
        },
        "small_sample": len(scores) < SMALL_SAMPLE,
    }


def leaderboard(
    s: Session,
    group: str,
    ror,
    periods: Mapping,
    shadow: Mapping,
    now: datetime,
) -> dict:
    """The `/api/model-leaderboard?group=` body."""
    from footy.api.routes import basis_of, competition_group, iso_utc

    rows = [
        p
        for p in s.scalars(
            select(Prediction)
            .where(Prediction.record_type == KIND)
            .order_by(Prediction.created_at_utc, Prediction.id)
        )
        if competition_group(p.competition) == group
        and not cat.is_market_only(p.model_name, _features(p))
    ]
    st = settlements_for(s, (p.id for p in rows))
    first: dict[tuple[str, str], Prediction] = {}
    for p in rows:
        first.setdefault((p.fixture_ref, p.model_name), p)
    scored: dict[tuple[str, str], Scored | None] = {
        k: score(json.loads(p.probabilities), st.get(p.id)) for k, p in first.items()
    }
    record_of: dict[str, str | None] = {}
    for (ref, _model), p in first.items():
        if ref not in record_of:
            record_of[ref] = ror.model_for(ref, p.competition)

    current = models_of_record.models_at(periods.get(group, []), now)
    earlier = earlier_models(periods, group, now)
    g_shadow = shadow.get(group, {})
    models: dict[str, dict] = {}
    for (ref, model), p in first.items():
        m = models.setdefault(
            model, {"published": 0, "void": 0, "scores": [], "bases": set(), "of_record": 0}
        )
        m["published"] += 1
        m["bases"].add(basis_of(_features(p)))
        m["of_record"] += record_of.get(ref) == model
        x = scored[(ref, model)]
        if x is not None:
            m["scores"].append((ref, x))
        elif p.id in st:
            m["void"] += 1

    record_scores = [
        x for ref, rec in record_of.items() if rec and (x := scored.get((ref, rec))) is not None
    ]
    out, waiting = [], []
    for model, m in models.items():
        entry = g_shadow.get(model)
        info = {
            "model": model,
            "label": cat.label_of(model, entry),
            "role": cat.role_of(model, current, earlier, entry),
            "basis": next(iter(m["bases"])) if len(m["bases"]) == 1 else "mixed",
            "description": cat.description_of(model, entry),
            "published": m["published"],
        }
        if not m["scores"]:
            waiting.append(info)
            continue
        diffs = []
        for ref, x in m["scores"]:
            rec = record_of.get(ref)
            other = scored.get((ref, rec)) if rec and rec != model else None
            if other is not None and x.rps is not None and other.rps is not None:
                diffs.append(x.rps - other.rps)
        out.append(
            {
                **info,
                "pending": m["published"] - len(m["scores"]) - m["void"],
                "void": m["void"],
                "records_of_record": m["of_record"],
                **_block([x for _, x in m["scores"]]),
                "vs_record": {
                    "n": len(diffs),
                    # model minus the match's record of record: negative means this model's
                    # forecasts scored better on those matches
                    "mean_rps_difference": _mean(diffs),
                    "better": sum(d < -LEVEL for d in diffs),
                    "worse": sum(d > LEVEL for d in diffs),
                    "level": sum(abs(d) <= LEVEL for d in diffs),
                    "small_sample": len(diffs) < SMALL_SAMPLE,
                },
            }
        )
    out.sort(
        key=lambda r: (
            r["mean_rps"] if r["mean_rps"] is not None else math.inf,
            -r["settled"],
            r["model"],
        )
    )
    waiting.sort(
        key=lambda r: (cat.ROLE_ORDER.get(r["role"], len(cat.ROLE_ORDER)), -r["published"])
    )
    last = max(
        (st[p.id].settled_at_utc for p in first.values() if p.id in st),
        default=None,
    )
    return {
        "kind": KIND,
        "explanation": LEADERBOARD_EXPLANATION,
        "generated_at_utc": iso_utc(now, "seconds"),
        "group": group,
        "group_name": FAMILY_NAMES.get(group, group),
        "small_sample_below": SMALL_SAMPLE,
        "models_of_record_now": current,
        "roles": cat.ROLE_LABELS,
        "last_settled_at_utc": iso_utc(last),
        "record": {
            "label": "Model of record",
            "description": RECORD_DESCRIPTION,
            "published": sum(1 for rec in record_of.values() if rec),
            **_block(record_scores),
        },
        "models": out,
        "waiting": waiting,
    }
