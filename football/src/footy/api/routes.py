"""Read-only API for the website: predictions, past matches, backtests, performance.

Everything returned is read from saved records (predictions, settlements, ledgers, manifests)
or the imported match data. Nothing is computed afresh for display, so the site can only
show numbers that already exist in a record.

Every timestamp is sent as ISO 8601 in UTC with an explicit offset (`+00:00`), per D-005.
Bookmakers' prices are never sent (D-013, D-024): a model + market record exposes only the
de-vigged consensus probabilities it used and where the prices came from.
"""

import json
from collections.abc import Callable, Iterable
from datetime import UTC, date, datetime, timedelta
from pathlib import Path

import pandas as pd
from fastapi import APIRouter, HTTPException, Query
from fastapi.responses import FileResponse
from sqlalchemy import Engine, select, text
from sqlalchemy.orm import Session

from footy import models_of_record
from footy.config import Settings
from footy.models import Prediction, PredictionSettlement, Team
from footy.models_of_record import Period
from footy.reference import DIVISIONS, REFERENCE_DIR

COMPETITION_NAMES = {
    **{c: d.name for c, d in DIVISIONS.items()},
    "UCL": "Champions League",
    "UEL": "Europa League",
    "UECL": "Conference League",
}
UEFA_COMPETITIONS = ("UCL", "UEL", "UECL")
PRIMARY_MODELS_PATH = REFERENCE_DIR / "primary_models.json"

MODEL_AND_MARKET = "model_and_market"
MODEL_ONLY = "model_only"
BASES = (MODEL_AND_MARKET, MODEL_ONLY)


# ---------------------------------------------------------------------------------------------
# Timestamps


def iso_utc(dt: datetime | None, timespec: str = "auto") -> str | None:
    """ISO 8601 in UTC with a `+00:00` offset. A naive value is stored UTC (D-005)."""
    if dt is None:
        return None
    if dt.tzinfo is None:
        dt = dt.replace(tzinfo=UTC)
    return dt.astimezone(UTC).isoformat(timespec=timespec)


def iso_utc_text(value: object) -> str | None:
    """A saved timestamp string (e.g. "2026-10-21 03:06:09+00:00") in the same form as
    `iso_utc`. Anything that isn't a parseable timestamp is returned unchanged."""
    if value is None:
        return None
    if not isinstance(value, str):
        return str(value)
    try:
        return iso_utc(datetime.fromisoformat(value.strip()))
    except ValueError:
        return value


# ---------------------------------------------------------------------------------------------
# Model of record


def model_periods(path: Path | None = None) -> dict[str, list[Period]]:
    """Every group's models of record with their effective-from times (footy.models_of_record).

    reference/primary_models.json gives each group ("domestic", "uefa") a list of periods,
    e.g. `[{"from_utc": null, "models": ["dixon_coles"]}, {"from_utc": "2026-10-24T11:00:00Z",
    "models": ["market_tilt_dc", "dixon_coles"]}]`; a plain name or a plain list of names is
    one period from the start. A fixture takes the latest period starting at or before its
    kickoff; within it, the record of record is the first listed model with a genuine
    pre-match record for the fixture."""
    return models_of_record.load(path or PRIMARY_MODELS_PATH)


def primary_models(path: Path | None = None, at: datetime | None = None) -> dict[str, list[str]]:
    """The ordered model list in effect at `at` (default now) per group. A group whose every
    period starts later is left out."""
    at = at or datetime.now(UTC)
    out = {g: models_of_record.models_at(ps, at) for g, ps in model_periods(path).items()}
    return {g: models for g, models in out.items() if models}


def competition_group(competition: str) -> str:
    return "uefa" if competition in UEFA_COMPETITIONS else "domestic"


def _as_periods(primary: dict) -> dict[str, list[Period]]:
    """Periods per group; a plain {group: [names]} mapping is one period from the start."""
    return {
        g: v if v and all(isinstance(p, Period) for p in v) else [Period(None, tuple(v))]
        for g, v in primary.items()
    }


def record_of_record(
    fixture_ref: str,
    competition: str,
    genuine_models: Iterable[str],
    primary: dict,
    when: datetime | None = None,
) -> str | None:
    """The model whose record is the record of record for one fixture: the first model, in
    the list of the period that applies at `when` (the fixture's kickoff; default now), with a
    genuine pre-match record for it (None if no listed model has)."""
    have = set(genuine_models)
    periods = _as_periods(primary).get(competition_group(competition), [])
    order = models_of_record.models_at(periods, when or datetime.now(UTC))
    return next((m for m in order if m in have), None)


class RecordsOfRecord:
    """Which model is the record of record for each fixture (one query per request).

    The period that applies to a fixture is decided by the earliest kickoff saved with any of
    its genuine pre-match records (00:00 UK on its date for a record that has none). A kickoff
    brought forward after the first record is therefore honoured: a fixture that kicked off
    before a switch never takes the new period. A genuine record can only be saved before its
    own kickoff, so once the fixture has kicked off no new record can move that time."""

    def __init__(self, s: Session, primary: dict):
        self.periods = _as_periods(primary)
        self.genuine: dict[str, set[str]] = {}
        self.when: dict[str, datetime] = {}
        rows = s.execute(
            select(
                Prediction.fixture_ref,
                Prediction.model_name,
                Prediction.kickoff_utc,
                Prediction.match_date,
            ).where(Prediction.record_type == "genuine_prematch")
        ).all()
        for ref, model, kickoff, day in rows:
            self.genuine.setdefault(ref, set()).add(model)
            t = models_of_record.fixture_time(kickoff, day)
            self.when[ref] = min(self.when.get(ref, t), t)

    def order_for(self, fixture_ref: str, competition: str) -> list[str]:
        """The ordered model list of the period that applies to this fixture."""
        when = self.when.get(fixture_ref)
        periods = self.periods.get(competition_group(competition), [])
        return models_of_record.models_at(periods, when) if when is not None else []

    def model_for(self, fixture_ref: str, competition: str) -> str | None:
        have = self.genuine.get(fixture_ref, set())
        return next((m for m in self.order_for(fixture_ref, competition) if m in have), None)

    def is_primary(self, p: Prediction) -> bool:
        """True only for the genuine pre-match record(s) of the fixture's record-of-record
        model (each of its editions)."""
        return p.record_type == "genuine_prematch" and p.model_name == self.model_for(
            p.fixture_ref, p.competition
        )


# ---------------------------------------------------------------------------------------------
# Model + market records


def basis_of(features: dict) -> str:
    """MODEL_AND_MARKET when the record used bookmakers' prices, otherwise MODEL_ONLY."""
    return MODEL_AND_MARKET if isinstance(features.get("market_prices_used"), dict) else MODEL_ONLY


def _params(features: dict) -> dict:
    p = features.get("model_params")
    return p if isinstance(p, dict) else {}


def _prob(v: object) -> float | None:
    return float(v) if isinstance(v, int | float) and not isinstance(v, bool) else None


# Provenance fields that may be shown. Anything else in the saved provenance is left out, so
# nothing price-like can reach the API by accident.
PROVENANCE_FIELDS = ("source", "raw_sha256", "raw_file_id", "retrieved_at")


# The fields of a saved `market_prices_used` that the API passes on: derived probabilities,
# the books' names and how many each consensus averaged, the quote time and the provenance.
# Nothing else, so a price can't leak.
MARKET_FIELDS = (
    "p_H",
    "p_D",
    "p_A",
    "p_over_2.5",
    "books",
    "n_books_1x2",
    "n_books_ou",
    "quote_time_utc",
    "provenance",
)


def _provenance(prov: object) -> dict:
    out = {k: prov[k] for k in PROVENANCE_FIELDS if k in prov} if isinstance(prov, dict) else {}
    if "retrieved_at" in out:
        out["retrieved_at"] = iso_utc_text(out["retrieved_at"])
    return out


def public_features(features: dict) -> dict:
    """The saved feature snapshot as the API sends it. A model + market record's
    `market_prices_used` keeps only MARKET_FIELDS, with its timestamps in ISO 8601 form."""
    m = features.get("market_prices_used")
    if not isinstance(m, dict):
        return features
    kept = {k: m[k] for k in MARKET_FIELDS if k in m}
    if "quote_time_utc" in kept:
        kept["quote_time_utc"] = iso_utc_text(kept["quote_time_utc"])
    if "provenance" in kept:
        kept["provenance"] = _provenance(kept["provenance"])
    return {**features, "market_prices_used": kept}


def market_used(features: dict) -> dict | None:
    """What a model + market record took from the bookmakers: the de-vigged consensus
    probabilities, the books, when the prices were collected and where they came from.
    Never the prices themselves. None for a model-only record."""
    m = features.get("market_prices_used")
    if not isinstance(m, dict):
        return None
    probs: dict[str, dict[str, float]] = {}
    h, d, a = (_prob(m.get(k)) for k in ("p_H", "p_D", "p_A"))
    if h is not None and d is not None and a is not None:
        probs["1X2"] = {"H": h, "D": d, "A": a}
    over = _prob(m.get("p_over_2.5"))
    if over is not None:
        probs["OU_2.5"] = {"O": over, "U": 1 - over}
    params = _params(features)
    weights = {
        market: w
        for market, key in (("1X2", "w_1x2"), ("OU_2.5", "w_ou"))
        if (w := _prob(params.get(key))) is not None
    }
    counts = {
        market: int(n)
        for market, key in (("1X2", "n_books_1x2"), ("OU_2.5", "n_books_ou"))
        if isinstance(n := m.get(key), int) and not isinstance(n, bool)
    }
    return {
        "probabilities": probs,
        "books": [str(b) for b in m.get("books") or []],
        "n_books": counts,  # books averaged per market; empty for older records
        "quote_time_utc": iso_utc_text(m.get("quote_time_utc")),
        "provenance": _provenance(m.get("provenance")),
        "devig": params.get("devig"),
        "phase": params.get("phase"),
        "weights": weights,
        "base_model": params.get("base"),
    }


def value_selection(basis: str) -> dict | None:
    """Placeholder for a value selection, only on records made with real bookmakers' prices.
    No selection rule is published yet, so it never names a bet."""
    if basis != MODEL_AND_MARKET:
        return None
    return {
        "status": "not_published",
        "selections": [],
        "note": "Bookmakers' prices were used for this record, but no value-selection rule "
        "is published yet, so there is no selection.",
    }


# What the detail endpoint shows of the counterpart record (it has its own page for the rest).
COUNTERPART_FIELDS = (
    "id",
    "is_primary",
    "basis",
    "edition",
    "record_type",
    "model_name",
    "model_version",
    "created_at_utc",
    "probabilities",
    "selections",
)


def counterpart(p: Prediction, features: dict, records: list[Prediction]) -> Prediction | None:
    """The same fixture's record with the other basis: for a model + market record, its
    base model's own record; for a model-only record, a model + market record built on it.
    Same record type; the same edition if there is one, otherwise the latest."""

    def feats(r: Prediction) -> dict:
        return json.loads(r.features) if r.features else {}

    if basis_of(features) == MODEL_AND_MARKET:
        base = _params(features).get("base")
        cands = [
            r
            for r in records
            if r.id != p.id
            and r.record_type == p.record_type
            and r.model_name == base
            and basis_of(feats(r)) == MODEL_ONLY
        ]
    else:
        cands = []
        for r in records:
            f = feats(r)
            if (
                r.id != p.id
                and r.record_type == p.record_type
                and basis_of(f) == MODEL_AND_MARKET
                and _params(f).get("base") == p.model_name
            ):
                cands.append(r)
    if not cands:
        return None
    same = [r for r in cands if r.edition == p.edition]
    return max(same or cands, key=lambda r: r.id)


def make_router(engine_fn: Callable[[], Engine], settings: Settings) -> APIRouter:
    r = APIRouter(prefix="/api")

    def teams(s: Session) -> dict[str, str]:
        return {t.team_key: t.name for t in s.scalars(select(Team))}

    def settlements(s: Session) -> dict[int, PredictionSettlement]:
        return {x.prediction_id: x for x in s.scalars(select(PredictionSettlement))}

    def card(
        p: Prediction, names: dict, st: PredictionSettlement | None, ror: RecordsOfRecord
    ) -> dict:
        probs = json.loads(p.probabilities)
        features = json.loads(p.features) if p.features else {}
        basis = basis_of(features)
        out = {
            "id": p.id,
            "is_primary": ror.is_primary(p),
            "basis": basis,
            "fixture_ref": p.fixture_ref,
            "competition": p.competition,
            "competition_name": COMPETITION_NAMES.get(p.competition, p.competition),
            "season": p.season,
            "match_date": p.match_date.isoformat(),
            "kickoff_utc": iso_utc(p.kickoff_utc),
            "home_team": p.home_team,
            "away_team": p.away_team,
            "home_name": names.get(p.home_team, p.home_team),
            "away_name": names.get(p.away_team, p.away_team),
            "edition": p.edition,
            "record_type": p.record_type,
            "model_name": p.model_name,
            "model_version": p.model_version,
            "created_at_utc": iso_utc(p.created_at_utc),
            "training_cutoff_utc": iso_utc(p.training_cutoff_utc),
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
                "settled_at_utc": iso_utc(st.settled_at_utc),
            },
        }
        vs = value_selection(basis)
        if vs is not None:  # only where real prices were used; never a made-up selection
            out["value_selection"] = vs
        return out

    @r.get("/fixtures")
    def fixtures(days: int = Query(8, ge=1, le=30)) -> dict:
        """Upcoming fixtures with their saved predictions (latest edition per fixture/model).
        Each card says whether it is the fixture's record of record (`is_primary`) and
        whether it used bookmakers' prices (`basis`)."""
        now = datetime.now(UTC)
        today = now.date()
        with Session(engine_fn()) as s:
            names, st = teams(s), settlements(s)
            ror = RecordsOfRecord(s, model_periods())
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
            cards = [card(p, names, st.get(p.id), ror) for p in latest.values()]
        groups: dict[str, list] = {}
        for c in sorted(cards, key=lambda c: (c["kickoff_utc"] or c["match_date"], c["id"])):
            groups.setdefault(c["competition"], []).append(c)
        return {
            "generated_at_utc": iso_utc(now, "seconds"),
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
            ror = RecordsOfRecord(s, model_periods())
            out = card(p, names, st.get(p.id), ror)
            features = json.loads(p.features) if p.features else {}
            out["features"] = public_features(features)
            out["market"] = market_used(features)
            records = s.scalars(
                select(Prediction)
                .where(Prediction.fixture_ref == p.fixture_ref)
                .order_by(Prediction.id)
            ).all()
            out["editions"] = [
                {
                    "id": e.id,
                    "edition": e.edition,
                    "model_name": e.model_name,
                    "basis": basis_of(json.loads(e.features) if e.features else {}),
                    "is_primary": ror.is_primary(e),
                    "created_at_utc": iso_utc(e.created_at_utc),
                    "record_type": e.record_type,
                    "reason": e.reason,
                }
                for e in records
            ]
            other = counterpart(p, features, list(records))
            if other is None:
                out["counterpart"] = None
            else:
                full = card(other, names, st.get(other.id), ror)
                out["counterpart"] = {k: full[k] for k in COUNTERPART_FIELDS}
            out["recent"] = {
                "home": recent_form(s, p.home_team, p.training_cutoff_utc, names),
                "away": recent_form(s, p.away_team, p.training_cutoff_utc, names),
            }
            from footy.teams import match_page  # here: footy.teams imports this module

            out["teams"] = match_page(s, p, names)
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
            ror = RecordsOfRecord(s, model_periods())
            q = select(Prediction).where(Prediction.record_type == record_type)
            if competition:
                q = q.where(Prediction.competition == competition)
            rows = s.scalars(q.order_by(Prediction.match_date.desc(), Prediction.id.desc())).all()
            cards = [card(p, names, st.get(p.id), ror) for p in rows if p.id in st][:limit]
        return {"record_type": record_type, "matches": cards}

    @r.get("/performance")
    def performance(primary_only: bool = True) -> dict:
        """The genuine pre-match record: settled predictions only, per market.

        Headline: by default each fixture counts once, with the first edition of its record
        of record (the first model, in the primary_models.json period that applies at its
        kickoff, with a genuine pre-match record for it); primary_only=false counts every
        genuine pre-match record. A period appended later starts from its own time, so it
        never changes a fixture already played.

        `by_basis` reports model + market and model-only records separately. By default each
        fixture counts once per basis: the first listed model (same period) with a record of
        that basis, or else the earliest such record, first edition. `paired` repeats both on
        the fixtures that have both, for a like-for-like comparison.

        `primary_models` is the list in effect now; `primary_model_periods` gives every
        period with its start.

        There is no profit here: profit is reported only from real historical prices, by the
        profit simulation."""
        periods = model_periods()
        now = datetime.now(UTC)
        primary = {g: m for g, ps in periods.items() if (m := models_of_record.models_at(ps, now))}
        with Session(engine_fn()) as s:
            st = settlements(s)
            ror = RecordsOfRecord(s, periods)
            rows = s.scalars(
                select(Prediction)
                .where(Prediction.record_type == "genuine_prematch")
                .order_by(Prediction.id)
            ).all()
        basis = {p.id: basis_of(json.loads(p.features) if p.features else {}) for p in rows}
        by_fixture: dict[str, list[Prediction]] = {}
        for p in rows:
            by_fixture.setdefault(p.fixture_ref, []).append(p)
        one_per_fixture: dict[str, dict[str, Prediction]] = {b: {} for b in BASES}
        for ref, recs in by_fixture.items():
            order = ror.order_for(ref, recs[0].competition)

            def rank(x: Prediction, order: list[str] = order) -> tuple[int, int]:
                listed = order.index(x.model_name) if x.model_name in order else len(order)
                return listed, x.id

            for b in BASES:
                cands = [x for x in recs if basis[x.id] == b]
                if cands:
                    one_per_fixture[b][ref] = min(cands, key=rank)
        if primary_only:
            first: dict[str, Prediction] = {}
            for p in rows:
                if ror.is_primary(p) and p.fixture_ref not in first:
                    first[p.fixture_ref] = p
            headline_rows = list(first.values())
            split = {b: list(one_per_fixture[b].values()) for b in BASES}
        else:
            headline_rows = list(rows)
            split = {b: [p for p in rows if basis[p.id] == b] for b in BASES}
        both = [ref for ref in by_fixture if all(ref in one_per_fixture[b] for b in BASES)]
        return {
            "primary_only": primary_only,
            "primary_models": primary,
            "primary_model_periods": {
                g: [{**p.as_json(), "from_utc": iso_utc(p.from_utc)} for p in ps]
                for g, ps in periods.items()
            },
            **summarise(headline_rows, st),
            "by_basis": {
                b: {**summarise(split[b], st), "models": sorted({p.model_name for p in split[b]})}
                for b in BASES
            },
            "paired": {
                "fixtures": len(both),
                **{b: summarise([one_per_fixture[b][ref] for ref in both], st) for b in BASES},
            },
        }

    @r.get("/backtests")
    def backtests() -> dict:
        out = []
        for root in (settings.data_dir / "backtests", settings.data_dir / "backtests_euro"):
            for d in sorted(root.glob("*")) if root.exists() else []:
                mf = d / "manifest.json"
                if mf.exists():
                    m = json.loads(mf.read_text())
                    m["created_at_utc"] = iso_utc_text(m.get("created_at_utc"))
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

    @r.get("/hit-rates")
    def hit_rates() -> dict:
        """How often the pick was right, per league or competition and market
        (footy.modeling.hit_rates).

        `backtest`: each model of record's test run (2024/25 + 2025/26), counted from its saved
        ledger, with the frequency baseline's hit rate on the same fixtures and 95% Wilson
        intervals; confidence buckets per row. `missing` lists a model of record whose test
        ledger isn't saved. `prematch`: the settled genuine pre-match record, each fixture
        once with the first edition of its record of record (as /api/performance counts it);
        empty until something is settled."""
        from footy.modeling import hit_rates as hr

        now = datetime.now(UTC)
        tables, missing = hr.backtest_tables(settings.data_dir)
        for t in tables:
            for k in ("run_created_at_utc", "baseline_run_created_at_utc"):
                t[k] = iso_utc_text(t[k])
        with Session(engine_fn()) as s:
            st = settlements(s)
            ror = RecordsOfRecord(s, model_periods())
            rows = s.scalars(
                select(Prediction)
                .where(Prediction.record_type == "genuine_prematch")
                .order_by(Prediction.id)
            ).all()
        first: dict[str, Prediction] = {}
        for p in rows:
            if ror.is_primary(p) and p.fixture_ref not in first:
                first[p.fixture_ref] = p
        settled = [
            hr.SettledRecord(
                family=competition_group(p.competition),
                model=p.model_name,
                basis=basis_of(json.loads(p.features) if p.features else {}),
                competition=p.competition,
                outcomes=json.loads(st[p.id].outcomes),
            )
            for p in first.values()
            if p.id in st
        ]
        last = max((st[p.id].settled_at_utc for p in first.values() if p.id in st), default=None)
        return {
            "generated_at_utc": iso_utc(now, "seconds"),
            "markets": [{"key": k, "label": v} for k, v in hr.MARKET_LABELS.items()],
            "confidence_thresholds": {
                m: list(thresholds) for m, (_, thresholds) in hr.CONFIDENCE.items()
            },
            "backtest": {"tables": tables, "missing": missing},
            "prematch": {
                "published": len(first),
                "settled": len(settled),
                "last_settled_at_utc": iso_utc(last),
                "tables": hr.prematch_tables(settled),
            },
        }

    @r.get("/reports/{name}")
    def report(name: str) -> dict:
        """Saved JSON reports (model selection, benchmark gate, profit simulation)."""
        root = settings.data_dir / "reports"
        path = root / f"{name}.json"
        if not path.exists() or path.resolve().parent != root.resolve():
            raise HTTPException(404, "no such report")
        return json.loads(path.read_text())

    from footy.api.teams_routes import make_teams_router  # here: it imports this module

    r.include_router(make_teams_router(engine_fn))
    return r


def summarise(rows: list[Prediction], st: dict[int, PredictionSettlement]) -> dict:
    """Published, settled and pending counts and per-market accuracy for these records."""
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
    last = max((st[p.id].settled_at_utc for p in rows if p.id in st), default=None)
    return {
        "published": len(rows),
        "settled": len(rows) - pending,
        "pending": pending,
        "last_settled_at_utc": iso_utc(last),
        "markets": per,
    }


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
    blended = c.get("basis") == MODEL_AND_MARKET
    parts = []
    xg = p.get("expected_goals")
    if xg:
        # A model + market record keeps its base model's expected goals: the tilt moves the
        # score grid to the prices, not these averages (footy.modeling.lab.market_blend).
        lead = (
            "Before the bookmakers' prices were blended in, the model expected"
            if blended
            else "The model expects"
        )
        parts.append(
            f"{lead} {c['home_name']} to score {xg['home']:.2f} goals and "
            f"{c['away_name']} {xg['away']:.2f}."
        )
    if "1X2" in p:
        x = p["1X2"]
        lead = "With the prices blended in: home win" if blended else "Home win"
        parts.append(f"{lead} {x['H']:.0%}, draw {x['D']:.0%}, away win {x['A']:.0%}.")
    if p.get("top_scores"):
        t = p["top_scores"][0]
        parts.append(f"The single most likely score is {t['score']} ({t['p']:.0%}).")
    if "OU_2.5" in p and "BTTS" in p:
        parts.append(
            f"Over 2.5 goals {p['OU_2.5']['O']:.0%}; both teams to score {p['BTTS']['Y']:.0%}."
        )
    m = c.get("market")
    if blended and m:
        w = m.get("weights") or {}
        if w and all(v == 1 for v in w.values()):
            how = "are the bookmakers' consensus"
        elif w:
            label = {"1X2": "match result", "OU_2.5": "over/under 2.5"}
            how = (
                "blend the bookmakers' consensus with the model (prices weighted "
                + ", ".join(f"{v:.0%} for the {label.get(k, k)}" for k, v in w.items())
                + ")"
            )
        else:
            how = "use the bookmakers' consensus"
        parts.append(
            f"The match result and over/under 2.5 goals {how}; the other goal lines, both teams "
            "to score and the scores come from the model's score grid, moved to agree with them."
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
