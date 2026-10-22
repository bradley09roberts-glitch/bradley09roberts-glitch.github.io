"""Read-only API for the website: predictions, past matches, backtests, performance.

Everything returned is read from saved records (predictions, settlements, ledgers, manifests)
or the imported match data. Nothing is computed afresh for display, so the site can only
show numbers that already exist in a record.

Every timestamp is sent as ISO 8601 in UTC with an explicit offset (`+00:00`), per D-005.
Bookmakers' prices are never sent (D-013, D-024): a model + market record exposes only the
de-vigged consensus probabilities it used and where the prices came from.
"""

import json
import re
from collections.abc import Callable, Iterable
from datetime import UTC, date, datetime, timedelta
from pathlib import Path

import pandas as pd
from fastapi import APIRouter, HTTPException, Query
from fastapi.responses import FileResponse
from sqlalchemy import Engine, func, select, text
from sqlalchemy.orm import Session

from footy import model_catalogue as cat
from footy import models_of_record
from footy.config import Settings
from footy.media.service import media_summaries
from footy.models import FetchLog, Prediction, PredictionSettlement, Team
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
    own kickoff, so once the fixture has kicked off no new record can move that time.

    `schedule` holds each fixture's date and kickoff as last known, for display: the latest
    genuine record of the fixture that has a kickoff, else the played match where the pairing
    meets once a season (a record dated by a placeholder, such as La Liga matchday 10 of
    2026/27 listed on its Sunday, is shown on the day the match was played)."""

    def __init__(self, s: Session, primary: dict):
        self.periods = _as_periods(primary)
        self.genuine: dict[str, set[str]] = {}
        self.when: dict[str, datetime] = {}
        self.schedule: dict[str, tuple[date, datetime | None]] = {}
        rows = s.execute(
            select(
                Prediction.fixture_ref,
                Prediction.model_name,
                Prediction.kickoff_utc,
                Prediction.match_date,
            )
            .where(Prediction.record_type == "genuine_prematch")
            .order_by(Prediction.id)
        ).all()
        for ref, model, kickoff, day in rows:
            self.genuine.setdefault(ref, set()).add(model)
            t = models_of_record.fixture_time(kickoff, day)
            self.when[ref] = min(self.when.get(ref, t), t)
            if kickoff is not None:
                self.schedule[ref] = (day, kickoff)
        undated = set(self.genuine) - set(self.schedule)
        if undated:
            for ref, day, kickoff in s.execute(
                text(
                    "SELECT DISTINCT p.fixture_ref, m.match_date, m.kickoff_utc FROM predictions p "
                    "JOIN matches m ON m.division = p.competition AND m.season = p.season "
                    "AND m.home_team = p.home_team AND m.away_team = p.away_team "
                    "WHERE p.kickoff_utc IS NULL AND p.record_type = 'genuine_prematch'"
                )
            ):
                division = DIVISIONS.get(ref.split(":", 1)[0])
                if ref in undated and division is not None and division.max_meetings == 1:
                    k = None if kickoff is None else datetime.fromisoformat(str(kickoff))
                    self.schedule[ref] = (date.fromisoformat(str(day)[:10]), k)

    def schedule_of(self, p: Prediction) -> tuple[date, datetime | None]:
        """The date and kickoff to show for a record's fixture (see the class docstring)."""
        return self.schedule.get(p.fixture_ref, (p.match_date, p.kickoff_utc))

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
    "model_label",
    "model_version",
    "created_at_utc",
    "probabilities",
    "selections",
)


def counterpart(
    p: Prediction, features: dict, records: list[Prediction], order: list[str] | None = None
) -> Prediction | None:
    """The same fixture's record with the other basis: for a model + market record, its
    base model's own record; for a model-only record, a model + market record built on it.
    Same record type; the same edition if there is one, otherwise the latest.

    A model + market record whose base model saved no record for the fixture (a learner
    names no `base`) falls back to the fixture's pure record of record: the first model in
    `order` (the fixture's list of models of record) with a model-only record, else the
    earliest model-only record, as /api/performance picks one per basis
    (`counterpart_relation` says which)."""

    def feats(r: Prediction) -> dict:
        return json.loads(r.features) if r.features else {}

    if basis_of(features) == MODEL_AND_MARKET:
        base = _params(features).get("base")
        pure = [
            r
            for r in records
            if r.id != p.id and r.record_type == p.record_type and basis_of(feats(r)) == MODEL_ONLY
        ]
        cands = [r for r in pure if r.model_name == base]
        if not cands and pure:
            ranked = order or []

            def rank(r: Prediction) -> tuple[int, int]:
                listed = ranked.index(r.model_name) if r.model_name in ranked else len(ranked)
                return listed, r.id

            model = min(pure, key=rank).model_name
            cands = [r for r in pure if r.model_name == model]
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


def counterpart_relation(features: dict, other: Prediction) -> str:
    """How `counterpart` chose `other` for a record with these features: "base" (a model +
    market record's own base model), "built_on" (a model + market record built on this
    model-only record) or "pure_record_of_record" (the fixture's pure record of record,
    standing in where the base model saved no record)."""
    if basis_of(features) != MODEL_AND_MARKET:
        return "built_on"
    return "base" if other.model_name == _params(features).get("base") else "pure_record_of_record"


# Two probabilities this close are the same figure (a record's own figures beside the
# consensus it saved, both from one computation).
SAME_FIGURE = 1e-9


def consensus_markets(probabilities: dict, features: dict) -> list[str]:
    """The markets ("1X2", "OU_2.5") whose figures in a model + market record are the
    bookmakers' de-vigged consensus it saved: the prices were given all the weight (a tilt
    with weight 1.0), so the model changed nothing there and its pick is the bookmakers'
    favourite. Empty for a model-only record or a genuine blend."""
    m = features.get("market_prices_used")
    if not isinstance(m, dict):
        return []
    out = []
    x = probabilities.get("1X2")
    h, d, a = (_prob(m.get(k)) for k in ("p_H", "p_D", "p_A"))
    if isinstance(x, dict) and None not in (h, d, a):
        if all(
            isinstance(x.get(k), int | float) and abs(x[k] - v) <= SAME_FIGURE
            for k, v in (("H", h), ("D", d), ("A", a))
        ):
            out.append("1X2")
    ou = probabilities.get("OU_2.5")
    over = _prob(m.get("p_over_2.5"))
    if isinstance(ou, dict) and over is not None and isinstance(ou.get("O"), int | float):
        if abs(ou["O"] - over) <= SAME_FIGURE:
            out.append("OU_2.5")
    return out


# How far a record's own date may be from its fixture's last known date and still be read by
# /api/fixtures (a placeholder date in the same round, or a postponement of a week or two).
RE_DATED_MARGIN = timedelta(days=14)


def fixture_cards(
    fixture_ref: str, recs: list[Prediction], ror: RecordsOfRecord
) -> list[Prediction]:
    """The records /api/fixtures carries for one fixture, from `recs` (the latest edition of
    each model's genuine pre-match record): the record of record, then the record with the
    other basis built with it (`counterpart`), if there is one. If no listed model has a record
    for the fixture, its first published record stands in (never marked primary)."""
    if not recs:
        return []
    record = ror.model_for(fixture_ref, recs[0].competition)
    lead = next((p for p in recs if p.model_name == record), None)
    if lead is None:
        lead = min(recs, key=lambda p: (p.created_at_utc, p.id))
    features = json.loads(lead.features) if lead.features else {}
    other = counterpart(lead, features, recs, ror.order_for(fixture_ref, lead.competition))
    return [lead] if other is None else [lead, other]


LEDGER_WITHHELD = (
    "Summary only: this run's match-result and over 2.5 goals figures are, match by match, "
    "the bookmakers' consensus with the margin removed, so its ledger is not sent (the "
    "backtest explorer withholds the same figures). Its summaries are on the lab's pages."
)


def ledger_withheld(run_dir: Path) -> bool:
    """True for a backtest run whose ledger may not be sent match by match: its model is one
    the backtest explorer sends as summaries only (footy.backtest_explorer.PRICE_IDENTICAL,
    the same rule), named by the run's manifest or, without one, its directory."""
    from footy.backtest_explorer import PRICE_IDENTICAL

    model = run_dir.name.split("__", 1)[0]
    mf = run_dir / "manifest.json"
    if mf.exists():
        try:
            model = str(json.loads(mf.read_text()).get("model") or model)
        except ValueError:
            pass
    return model in PRICE_IDENTICAL or run_dir.name.split("__", 1)[0] in PRICE_IDENTICAL


# /api/past: one card per fixture (the counted record) or every model's.
PAST_RECORD = "record"
PAST_ALL = "all"
# Fields of a card the past list never shows: each record's page has them. Leaving them out
# keeps the list (and the phone copy, docs/snapshot.md) small.
PAST_CARD_DROPPED = (
    "probabilities",
    "notes",
    "data_freshness",
    "missing_fields",
    "training_cutoff_utc",
    "model_version",
    "value_selection",
)


def counted_record(records: list[Prediction], ror: RecordsOfRecord) -> Prediction | None:
    """The one record of a fixture that its tally counts, from all its records of one type.
    Genuine pre-match: the first edition of its record of record, as /api/performance counts
    it (None if no listed model has a record for the fixture). Any other type: the first
    record of the first listed model, else the earliest record."""
    if not records:
        return None
    if records[0].record_type == "genuine_prematch":
        primary = [p for p in records if ror.is_primary(p)]
        return min(primary, key=lambda p: p.id) if primary else None
    order = ror.order_for(records[0].fixture_ref, records[0].competition)

    def rank(p: Prediction) -> tuple[int, int]:
        return (order.index(p.model_name) if p.model_name in order else len(order)), p.id

    return min(records, key=rank)


def record_standing(
    p: Prediction, records: list[Prediction], ror: RecordsOfRecord, shadow: dict
) -> dict:
    """Which model a record is and where it stands, for its match page: `model_role` (for a
    genuine pre-match record, its role on this fixture as the every-model list gives it,
    footy.model_compare.fixture_role; otherwise its standing role) and `record_of_record` (the
    fixture's record of record: model, label and the id of its latest genuine pre-match
    edition; None for a retrospective record, or if no listed model has a record for it).
    `records` are the fixture's records."""
    from footy import model_compare  # here: footy.model_compare imports this module

    now = datetime.now(UTC)
    group = competition_group(p.competition)
    g_shadow = shadow.get(group, {})
    earlier = model_compare.earlier_models(ror.periods, group, now)
    record = None
    if p.record_type == "genuine_prematch":
        record = ror.model_for(p.fixture_ref, p.competition)
        order = ror.order_for(p.fixture_ref, p.competition)
        role = model_compare.fixture_role(p.model_name, record, order, g_shadow, earlier)
    else:
        current = models_of_record.models_at(ror.periods.get(group, []), now)
        role = cat.role_of(p.model_name, current, earlier, g_shadow.get(p.model_name))
    editions = [
        r for r in records if r.model_name == record and r.record_type == "genuine_prematch"
    ]
    return {
        "model_role": role,
        "record_of_record": None
        if record is None or not editions
        else {
            "model": record,
            "label": cat.label_of(record, g_shadow.get(record)),
            "prediction_id": max(r.id for r in editions),
        },
    }


def make_router(engine_fn: Callable[[], Engine], settings: Settings) -> APIRouter:
    r = APIRouter(prefix="/api")

    def teams(s: Session) -> dict[str, str]:
        return {t.team_key: t.name for t in s.scalars(select(Team))}

    def settlements(s: Session) -> dict[int, PredictionSettlement]:
        """Each prediction's latest settlement (footy.publish.latest_settlements)."""
        from footy.publish import latest_settlements

        return latest_settlements(s)

    def badge_letters(s: Session) -> dict[str, str]:
        """Every team's badge letters, different for teams that meet (TeamsIndex.shorts)."""
        from footy.teams import teams_index  # here: footy.teams imports this module

        return teams_index(s).shorts

    def team_media(s: Session, rows: Iterable[Prediction], names: dict) -> dict[str, dict]:
        """The media summary (footy.media.service.media_summary) of every team in `rows`."""
        keys = {k for p in rows for k in (p.home_team, p.away_team)}
        named = {k: names.get(k, k) for k in sorted(keys)}
        return media_summaries(named, settings, badge_letters(s))

    def card(
        p: Prediction,
        names: dict,
        st: PredictionSettlement | None,
        ror: RecordsOfRecord,
        media: dict[str, dict] | None = None,
        shadow: dict | None = None,
    ) -> dict:
        """One record as a card. With `media` (team key -> media summary), the card also
        carries `home_media` and `away_media` (the fixture and past lists send them; the detail
        sends them in its `teams` block instead, keeping its top-level keys). `shadow`
        (model_catalogue.load_shadow_models, read once per request) names the model."""
        probs = json.loads(p.probabilities)
        features = json.loads(p.features) if p.features else {}
        basis = basis_of(features)
        entry = (shadow or {}).get(competition_group(p.competition), {}).get(p.model_name)
        # the fixture's date and kickoff as last known; the record's own when they differ
        day, kickoff = ror.schedule_of(p)
        moved = (day.isoformat(), iso_utc(kickoff)) != (
            p.match_date.isoformat(),
            iso_utc(p.kickoff_utc),
        )
        out = {
            "id": p.id,
            "is_primary": ror.is_primary(p),
            "basis": basis,
            "fixture_ref": p.fixture_ref,
            "competition": p.competition,
            "competition_name": COMPETITION_NAMES.get(p.competition, p.competition),
            "season": p.season,
            "match_date": day.isoformat(),
            "kickoff_utc": iso_utc(kickoff),
            **(
                {
                    "recorded_match_date": p.match_date.isoformat(),
                    "recorded_kickoff_utc": iso_utc(p.kickoff_utc),
                }
                if moved
                else {}
            ),
            "home_team": p.home_team,
            "away_team": p.away_team,
            "home_name": names.get(p.home_team, p.home_team),
            "away_name": names.get(p.away_team, p.away_team),
            **(
                {"home_media": media[p.home_team], "away_media": media[p.away_team]}
                if media is not None
                else {}
            ),
            "edition": p.edition,
            "record_type": p.record_type,
            "model_name": p.model_name,
            "model_label": cat.label_of(p.model_name, entry),
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
            # Where its figures are the bookmakers' consensus itself, the page says so.
            out["consensus_markets"] = consensus_markets(probs, features)
        return out

    @r.get("/fixtures")
    def fixtures(days: int = Query(8, ge=1, le=30)) -> dict:
        """Upcoming fixtures with their saved predictions: per fixture, the record of record
        (`is_primary`) and the record with the other basis built with it (a model + market
        record and its pure model's own, `counterpart`), each the latest edition of its model.
        Every other model's record is on the match page's every-model list
        (`/api/fixtures/{ref}/models`); `models_for_fixture` counts the models with a record.
        `basis` says whether a card used bookmakers' prices."""
        now = datetime.now(UTC)
        today = now.date()
        first, last = today - timedelta(days=1), today + timedelta(days=days)
        with Session(engine_fn()) as s:
            names, st = teams(s), settlements(s)
            ror = RecordsOfRecord(s, model_periods())
            shadow = cat.load_shadow_models()
            # A fixture is in the window by the date it is last known by (`RecordsOfRecord.
            # schedule`), not each record's own: a record dated by a placeholder (audit M1)
            # can be days off the real date. Records are read with a margin either side.
            rows = s.scalars(
                select(Prediction)
                .where(Prediction.match_date >= first - RE_DATED_MARGIN)
                .where(Prediction.match_date <= last + RE_DATED_MARGIN)
                .where(Prediction.record_type == "genuine_prematch")
                .order_by(Prediction.kickoff_utc, Prediction.id)
            ).all()
            known: dict[str, Prediction] = {}  # each fixture's latest record
            for p in rows:
                if p.fixture_ref not in known or p.id > known[p.fixture_ref].id:
                    known[p.fixture_ref] = p
            rows = [p for p in rows if first <= ror.schedule_of(known[p.fixture_ref])[0] <= last]
            latest: dict[tuple, Prediction] = {}
            for p in rows:
                latest[(p.fixture_ref, p.model_name)] = p
            by_ref: dict[str, list[Prediction]] = {}
            for p in latest.values():
                features = json.loads(p.features) if p.features else {}
                if not cat.is_market_only(p.model_name, features):  # never shown (D-013)
                    by_ref.setdefault(p.fixture_ref, []).append(p)
            kept: list[tuple[Prediction, int]] = []
            for ref, recs in by_ref.items():
                kept.extend((p, len(recs)) for p in fixture_cards(ref, recs, ror))
            media = team_media(s, (p for p, _ in kept), names)
            cards = [
                {
                    **card(p, names, st.get(p.id), ror, media, shadow),
                    "models_for_fixture": n,
                }
                for p, n in kept
            ]
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
            shadow = cat.load_shadow_models()
            out = card(p, names, st.get(p.id), ror, shadow=shadow)
            features = json.loads(p.features) if p.features else {}
            out["features"] = public_features(features)
            out["market"] = market_used(features)
            records = s.scalars(
                select(Prediction)
                .where(Prediction.fixture_ref == p.fixture_ref)
                .order_by(Prediction.id)
            ).all()
            out.update(record_standing(p, records, ror, shadow))
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
            other = counterpart(
                p, features, list(records), ror.order_for(p.fixture_ref, p.competition)
            )
            if other is None:
                out["counterpart"] = None
            else:
                full = card(other, names, st.get(other.id), ror, shadow=shadow)
                out["counterpart"] = {
                    **{k: full[k] for k in COUNTERPART_FIELDS},
                    **{k: full[k] for k in ("consensus_markets",) if k in full},
                    "relation": counterpart_relation(features, other),
                }
            out["recent"] = {
                "home": recent_form(s, p.home_team, p.training_cutoff_utc, names),
                "away": recent_form(s, p.away_team, p.training_cutoff_utc, names),
            }
            from footy.teams import match_page  # here: footy.teams imports this module

            out["teams"] = match_page(s, p, names)
            recent = [r for rows in out["recent"].values() for r in rows]
            media = media_summaries(
                {
                    k: names.get(k, k)
                    for k in sorted(
                        {p.home_team, p.away_team} | {r["opponent_team"] for r in recent}
                    )
                },
                settings,
                badge_letters(s),
            )
            for r in recent:
                r["opponent_media"] = media[r["opponent_team"]]
            for side in ("home", "away"):  # the team objects: media after the name
                t = out["teams"][side]
                out["teams"][side] = {
                    "team": t["team"],
                    "name": t["name"],
                    "media": media[t["team"]],
                    **t,
                }
        out["explanation"] = explain(out)
        return out

    @r.get("/past")
    def past(
        competition: str | None = None,
        record_type: str = "genuine_prematch",
        models: str = Query(PAST_RECORD, pattern=f"^({PAST_RECORD}|{PAST_ALL})$"),
        limit: int = Query(200, ge=1, le=2000),
    ) -> dict:
        """Settled predictions, newest fixture first. Genuine pre-match and retrospective
        records are never mixed in one response.

        `models=record` (the default): one card per fixture, the record /api/performance
        counts for it (`counted_record`), so the two pages agree; a fixture none of the listed
        models has a genuine record for is left out, as Performance leaves it out.
        `models=all`: every model's settled record, each fixture's counted card first, then
        the others in the order of the models of record and by model. `limit` caps fixtures,
        not cards; `truncated` says older fixtures were left out. `counted` marks the card
        Performance counts; `models_for_fixture` counts the models with a settled record.

        A record made of bookmakers' prices alone is never listed (D-013). Cards are the
        list's own (PAST_CARD_DROPPED: the fields the list never shows are left out); each
        record's page has the rest."""
        with Session(engine_fn()) as s:
            names, st = teams(s), settlements(s)
            ror = RecordsOfRecord(s, model_periods())
            shadow = cat.load_shadow_models()
            q = select(Prediction).where(Prediction.record_type == record_type)
            if competition:
                q = q.where(Prediction.competition == competition)
            rows = s.scalars(q.order_by(Prediction.match_date.desc(), Prediction.id.desc())).all()
            every: dict[str, list[Prediction]] = {}
            for p in rows:  # newest fixture first
                every.setdefault(p.fixture_ref, []).append(p)
            picked: list[tuple[Prediction, Prediction | None, int]] = []
            fixtures = 0
            truncated = False
            for ref, all_recs in every.items():
                # Chosen among every record of the fixture, settled or not, as Performance does.
                counted = counted_record(all_recs, ror)
                recs = [
                    p
                    for p in all_recs
                    if p.id in st
                    and not cat.is_market_only(
                        p.model_name, json.loads(p.features) if p.features else {}
                    )
                ]
                if counted not in recs:
                    counted = None
                if not recs or (models == PAST_RECORD and counted is None):
                    continue
                if fixtures >= limit:
                    truncated = True
                    break
                fixtures += 1
                n_models = len({p.model_name for p in recs})
                if models == PAST_RECORD:
                    picked.append((counted, counted, n_models))
                    continue
                order = ror.order_for(ref, recs[0].competition)

                def rank(x: Prediction, order: list[str] = order, lead=counted) -> tuple:
                    listed = order.index(x.model_name) if x.model_name in order else len(order)
                    return (x is not lead, listed, x.model_name, x.id)

                picked.extend((p, counted, n_models) for p in sorted(recs, key=rank))
            media = team_media(s, (p for p, _, _ in picked), names)
            cards = [
                {
                    **{
                        k: v
                        for k, v in card(p, names, st.get(p.id), ror, media, shadow).items()
                        if k not in PAST_CARD_DROPPED
                    },
                    "counted": p is lead,
                    "models_for_fixture": n,
                }
                for p, lead, n in picked
            ]
        return {
            "record_type": record_type,
            "models": models,
            "fixtures": fixtures,
            "truncated": truncated,
            "matches": cards,
        }

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
        """Every saved backtest run. `per_match` is false for a run whose ledger is withheld
        match by match (`ledger_withheld`): its summaries are sent, its ledger file is not."""
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
                            "per_match": not ledger_withheld(d),
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
        """A run's whole ledger, match by match. Refused (403) for a run whose match-result
        and over 2.5 figures are the bookmakers' consensus match by match (`ledger_withheld`,
        the explorer's rule): they would be a price in disguise."""
        d = _run_dir(run_id)
        if fmt not in ("csv", "json"):
            raise HTTPException(404, "csv or json")
        if ledger_withheld(d):
            raise HTTPException(403, LEDGER_WITHHELD)
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
        from footy.modeling.favourite import add_favourites

        now = datetime.now(UTC)
        tables, missing = hr.backtest_tables(settings.data_dir)
        for t in tables:
            for k in ("run_created_at_utc", "baseline_run_created_at_utc"):
                t[k] = iso_utc_text(t[k])
        with Session(engine_fn()) as s:
            # League games: the bookmakers' favourite on the same fixtures (hit rates only).
            add_favourites(s, settings.data_dir, tables)
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

    @r.get("/fetch-status")
    def fetch_status() -> dict:
        """How each source's downloads are going, from the fetch log (every attempt, failures
        included), so a failing source shows even while the jobs that use it end "ok"."""
        with Session(engine_fn()) as s:
            sources = fetch_sources(s)
        return {"generated_at_utc": iso_utc(datetime.now(UTC), "seconds"), "sources": sources}

    @r.get("/reports/{name}")
    def report(name: str) -> dict:
        """Saved JSON reports (model selection, benchmark gate, profit simulation)."""
        root = settings.data_dir / "reports"
        path = root / f"{name}.json"
        if not path.exists() or path.resolve().parent != root.resolve():
            raise HTTPException(404, "no such report")
        return json.loads(path.read_text())

    @r.get("/ml/summary")
    def ml_summary() -> dict:
        """D-028 for the machine-learning panel: each candidate, its validation and test
        composites against its target's, what happened to it and the verdict, read from
        /api/reports/ml_selection (footy.ml_decision). The month-by-month learning curve is
        /api/reports/ml_learning_curve."""
        from footy.ml_decision import ml_summary as build

        out = build(settings.data_dir / "reports")
        if out is None:
            raise HTTPException(404, "no such report")
        return out

    from footy.api.teams_routes import make_teams_router  # here: it imports this module

    r.include_router(make_teams_router(engine_fn, settings))
    from footy.api.models_routes import make_models_router  # here: it imports this module

    r.include_router(make_models_router(engine_fn, settings))
    return r


# Fetch log outcomes that are failures (footy.models.FetchLog.outcome); anything else got an
# answer (a new revision, unchanged, not modified, cached, or not found).
FETCH_FAILED = ("error",)
FETCH_ERROR_CHARS = 300
_URL_QUERY = re.compile(r"""(https?://[^\s'"?#]+)\?[^\s'"]*""")


def public_fetch_error(error: str | None) -> str | None:
    """A fetch error as the API sends it: a URL's query string (where a key could be) cut,
    and the text cut to FETCH_ERROR_CHARS characters."""
    if not error:
        return error
    text = _URL_QUERY.sub(r"\1?…", error.strip())
    return text if len(text) <= FETCH_ERROR_CHARS else text[: FETCH_ERROR_CHARS - 1] + "…"


def fetch_sources(s: Session) -> list[dict]:
    """Per source in the fetch log: attempts, the latest attempt and the latest that got an
    answer, and the latest failure (when, HTTP status, which file, the error). `failing` when
    the latest attempt failed; `failures_since_ok` counts the failed attempts since the last
    answer (since the first attempt if none) and `failing_since` is the first of them. Failing
    sources first, then the most recently tried."""

    def attempt(row: FetchLog | None) -> dict | None:
        if row is None:
            return None
        return {
            "at_utc": iso_utc(row.requested_at, "seconds"),
            "outcome": row.outcome,
            "http_status": row.http_status,
            "key": row.logical_key,
            "error": public_fetch_error(row.error),
            "job_run_id": row.job_run_id,
        }

    failed = FetchLog.outcome.in_(FETCH_FAILED)
    newest = (FetchLog.requested_at.desc(), FetchLog.id.desc())

    def latest(*where) -> FetchLog | None:
        return s.scalars(select(FetchLog).where(*where).order_by(*newest).limit(1)).first()

    out = []
    for source, n in s.execute(select(FetchLog.source, func.count()).group_by(FetchLog.source)):
        mine = FetchLog.source == source
        last, ok, err = latest(mine), latest(mine, ~failed), latest(mine, failed)
        since = [mine, failed]
        if ok is not None:
            since.append(FetchLog.requested_at > ok.requested_at)
        failures = s.scalar(select(func.count()).select_from(FetchLog).where(*since)) or 0
        first = s.scalars(
            select(FetchLog).where(*since).order_by(FetchLog.requested_at, FetchLog.id).limit(1)
        ).first()
        out.append(
            {
                "source": source,
                "attempts": int(n),
                "failing": last is not None and last.outcome in FETCH_FAILED,
                "failures_since_ok": int(failures),
                "failing_since_utc": iso_utc(first.requested_at, "seconds") if first else None,
                "last_attempt": attempt(last),
                "last_ok_utc": iso_utc(ok.requested_at, "seconds") if ok else None,
                "last_error": attempt(err),
            }
        )
    failing = [x for x in out if x["failing"]]
    rest = sorted(
        (x for x in out if not x["failing"]),
        key=lambda x: x["last_attempt"]["at_utc"] or "",
        reverse=True,
    )
    return sorted(failing, key=lambda x: x["last_attempt"]["at_utc"] or "", reverse=True) + rest


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
        opponent = a if home else h
        out.append(
            {
                "date": str(d),
                "competition": comp,
                "venue": "H" if home else "A",
                "opponent": names.get(opponent, opponent),
                "opponent_team": opponent,
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
