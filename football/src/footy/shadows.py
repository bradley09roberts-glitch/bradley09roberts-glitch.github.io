"""Shadow records: other models' own genuine pre-match records (docs/shadow-records.md).

`reference/shadow_models.json` lists, per group ("domestic", "uefa"), the models that publish
beside the models of record so that each builds a live track record: the machine-learning
learners (D-028), the model lab's other challengers, the baselines and earlier models of
record. Each entry has a role and a one-line description for the website.

`run` publishes them by the same code paths and rules as `footy predict-domestic` and
`footy predict-euro` (`footy.live`): the same decision time, training window and price
rules, so a model + market forecast publishes only fixtures with usable prices. Before
fitting, it leaves out every fixture that has kicked off or that the model has already
recorded (so a record is never duplicated and a model with nothing new isn't fitted), and a
fixture whose kick-off time isn't known yet unless it already has a record without one (see
`run_one`). One model failing doesn't stop the others, nor one league the other leagues;
each model gets a summary (fixtures, published, skipped and why, seconds, and any error).

A shadow record is an ordinary genuine pre-match record under the model's own name; its notes
say it is a shadow record and its role. It never becomes the record of record: a model that
`primary_models.json` lists for now or later is refused here, and this module never writes
that file.
"""

import json
import time
import traceback
from collections import Counter
from collections.abc import Callable, Iterable
from dataclasses import dataclass, field
from datetime import UTC, datetime
from pathlib import Path

from sqlalchemy import Engine

from footy import models_of_record
from footy.reference import REFERENCE_DIR

SHADOW_MODELS_PATH = REFERENCE_DIR / "shadow_models.json"
PRIMARY_MODELS_PATH = REFERENCE_DIR / "primary_models.json"
GROUPS = models_of_record.GROUPS
DOMESTIC_DIVISIONS = ("E0", "SP1", "I1", "D1", "F1", "E1")
UEFA_COMPS = ("UCL", "UEL", "UECL")
# role: (how a record's notes name it, the DECISIONS.md entry they cite by default)
ROLES = {
    "machine_learning": ("machine-learning challenger", "D-028"),
    "challenger": ("challenger", "D-027"),
    "baseline": ("baseline", "D-025"),
    "earlier_record": ("earlier model of record", "D-026"),
}
# `label` (optional): a short display name for the website (footy.model_catalogue reads it)
ENTRY_KEYS = {"model", "role", "description", "decision", "label"}
MAX_DESCRIPTION = 200


@dataclass(frozen=True)
class Shadow:
    group: str
    model: str
    role: str
    description: str
    decision: str | None = None

    @property
    def notes(self) -> str:
        """The notes every record of this shadow model carries."""
        label, default = ROLES[self.role]
        return (
            f"Shadow record ({label}, {self.decision or default}); not the model of record. "
            f"{self.description}"
        )


def load(path: Path = SHADOW_MODELS_PATH) -> dict[str, list[Shadow]]:
    """Every group's shadow models, in the file's order. Raises ValueError for a malformed
    file: an unknown group or role, a missing or multi-line description, a repeated name."""
    data = json.loads(path.read_text())
    if not isinstance(data, dict):
        raise ValueError(f"{path.name}: expected an object of groups")
    out: dict[str, list[Shadow]] = {}
    for group, entries in data.items():
        if group.startswith("_"):
            continue
        if group not in GROUPS:
            raise ValueError(f"{path.name}: unknown group {group!r}")
        if not isinstance(entries, list):
            raise ValueError(f"{path.name}: {group!r} must be a list of models")
        seen: set[str] = set()
        shadows = []
        for i, e in enumerate(entries):
            at = f"{path.name}: {group} entry {i + 1}"
            if not isinstance(e, dict) or set(e) - ENTRY_KEYS or not {"model", "role"} <= set(e):
                raise ValueError(
                    f"{at} must have model, role and description (and optionally decision, label)"
                )
            name, role, desc = e["model"], e["role"], e.get("description")
            if not isinstance(name, str) or not name or name.strip() != name:
                raise ValueError(f"{at}: bad model name {name!r}")
            if name in seen:
                raise ValueError(f"{at}: {name} is listed twice")
            if role not in ROLES:
                raise ValueError(f"{at}: unknown role {role!r}; expected one of {', '.join(ROLES)}")
            if (
                not isinstance(desc, str)
                or not desc.strip()
                or "\n" in desc
                or len(desc) > MAX_DESCRIPTION
            ):
                raise ValueError(f"{at}: description must be one line of 1-{MAX_DESCRIPTION} chars")
            decision = e.get("decision")
            if decision is not None and not (isinstance(decision, str) and decision.strip()):
                raise ValueError(f"{at}: decision must be text such as D-026")
            seen.add(name)
            shadows.append(Shadow(group, name, role, desc.strip(), decision))
        out[group] = shadows
    return out


def registered(group: str) -> dict[str, Callable]:
    """The factories a group's shadow models are made from, by name."""
    if group == "uefa":
        from footy.modeling.euro_registry import EURO_MODELS, euro_factory

        return {name: euro_factory(name) for name in EURO_MODELS}
    from footy.modeling.registry import candidates

    return candidates()


def models_of_record_from(now: datetime, path: Path = PRIMARY_MODELS_PATH) -> dict[str, set[str]]:
    """Per group, every model `primary_models.json` lists for now or later: the period in
    effect now and any that start after it."""
    out: dict[str, set[str]] = {}
    for group, periods in models_of_record.load(path).items():
        names: set[str] = set()
        current = models_of_record.period_at(periods, now)
        for p in periods:
            if p is current or (p.from_utc is not None and p.from_utc > now):
                names |= set(p.models)
        out[group] = names
    return out


@dataclass
class Outcome:
    """One shadow model's run."""

    group: str
    model: str
    role: str
    dry_run: bool
    fixtures: int = 0  # fixtures in the window (published, would be, or skipped)
    published: int = 0  # records published (a dry run: records it would publish)
    skipped: dict[str, int] = field(default_factory=dict)  # reason -> fixtures
    seconds: float = 0.0
    error: str | None = None
    records: list[dict] = field(default_factory=list)  # what footy.live returned
    skips: list[dict] = field(default_factory=list)  # every skipped fixture and why

    def as_json(self) -> dict:
        return {
            "group": self.group,
            "model": self.model,
            "role": self.role,
            "dry_run": self.dry_run,
            "fixtures": self.fixtures,
            "published" if not self.dry_run else "would_publish": self.published,
            "skipped": self.skipped,
            "seconds": self.seconds,
            "error": self.error,
            "records": self.records,
            "skips": self.skips,
        }

    def line(self) -> str:
        verb = "would publish" if self.dry_run else "published"
        head = f"{self.group:8} {self.model:36} {self.role:16}"
        if self.error and not self.fixtures:
            return f"{head} FAILED after {self.seconds:.1f}s: {self.error}"
        why = "; ".join(f"{n} {r}" for r, n in sorted(self.skipped.items(), key=lambda x: -x[1]))
        return (
            f"{head} fixtures {self.fixtures:3}  {verb} {self.published:3}  "
            f"skipped {sum(self.skipped.values()):3}  {self.seconds:7.1f}s"
            + (f"  ({why})" if why else "")
            + (f"  FAILED: {self.error}" if self.error else "")
        )


def undated_refs(engine: Engine) -> set[str]:
    """Fixtures with a genuine pre-match record (any model) that has no kick-off time."""
    from sqlalchemy import select
    from sqlalchemy.orm import Session

    from footy.models import Prediction

    with Session(engine) as s:
        return set(
            s.scalars(
                select(Prediction.fixture_ref).where(
                    Prediction.record_type == "genuine_prematch",
                    Prediction.kickoff_utc.is_(None),
                )
            )
        )


def _error(exc: BaseException) -> str:
    return "".join(traceback.format_exception_only(type(exc), exc)).strip()


def _predict(group: str):
    from footy import live

    return live.predict_euro if group == "uefa" else live.predict_domestic


def run_one(
    engine: Engine,
    shadow: Shadow,
    factory: Callable,
    *,
    days: int,
    divisions: Iterable[str] = DOMESTIC_DIVISIONS,
    comps: Iterable[str] = UEFA_COMPS,
    dry_run: bool = False,
    now: datetime | None = None,
    recorded: set[str] | None = None,
    undated: set[str] | None = None,
) -> Outcome:
    """Publish one shadow model's records (or, dry, say what it would publish). `now` fixes
    the time (tests); live runs leave it out so footy.live checks the clock itself.
    `recorded`: the fixtures the model has already recorded; `undated`: the fixtures that
    already have a genuine record without a kick-off time (both read from the predictions
    table by default). Exceptions are caught and reported in the outcome.

    A fixture whose kick-off time isn't known yet is left out unless it is in `undated`. A
    record without a kick-off time dates its fixture from 00:00 UK (`RecordsOfRecord` takes
    the earliest time of a fixture's genuine records), so a shadow record could otherwise
    move the fixture into an earlier model-of-record period; where a record without the time
    already exists, the fixture is dated from 00:00 UK anyway."""
    from footy.publish import recorded_refs

    out = Outcome(shadow.group, shadow.model, shadow.role, dry_run)
    t0 = time.perf_counter()
    skipped: list[dict] = []
    records: list[dict] = []
    errors = []
    try:
        exclude = recorded if recorded is not None else recorded_refs(engine, shadow.model)
        kw = dict(
            now=now,
            days=days,
            dry_run=dry_run,
            notes=shadow.notes,
            exclude=exclude,
            skipped=skipped,
            undated_ok=undated if undated is not None else undated_refs(engine),
        )
        if shadow.group == "uefa":
            records = _predict("uefa")(engine, factory, shadow.model, tuple(comps), **kw)
        else:
            # one league at a time, so a league that fails (a schedule that can't be read,
            # say) costs only its own fixtures, not every league after it
            for div in divisions:
                try:
                    records += _predict("domestic")(engine, factory, shadow.model, [div], **kw)
                except Exception as exc:
                    errors.append(f"{div}: {_error(exc)}")
    except Exception as exc:  # one model failing must not stop the others
        errors.append(_error(exc))
    out.error = "; ".join(errors) or None
    out.seconds = round(time.perf_counter() - t0, 1)
    # a dry run of a model + market forecast lists every fixture; the ones without usable
    # prices are also in `skipped`, and would not be published
    left_out = {s["fixture"] for s in skipped}
    out.records = records
    out.skips = skipped
    out.published = sum(1 for r in records if r["fixture"] not in left_out)
    out.skipped = dict(Counter(s["reason"] for s in skipped))
    out.fixtures = len({r["fixture"] for r in records} | left_out)
    return out


def run(
    engine: Engine,
    groups: Iterable[str],
    *,
    days: int,
    divisions: Iterable[str] = DOMESTIC_DIVISIONS,
    comps: Iterable[str] = UEFA_COMPS,
    models: Iterable[str] | None = None,
    dry_run: bool = False,
    now: datetime | None = None,
    shadows: dict[str, list[Shadow]] | None = None,
    primary_path: Path = PRIMARY_MODELS_PATH,
    factories: dict[str, dict[str, Callable]] | None = None,
    report: Callable[[Outcome], None] | None = None,
) -> list[Outcome]:
    """Run every listed shadow model of `groups` (only `models`, if given), one after the
    other; `report` is called with each outcome as soon as it is known."""
    shadows = shadows if shadows is not None else load()
    wanted = set(models) if models is not None else None
    of_record = models_of_record_from(now or datetime.now(UTC), primary_path)
    outcomes = []
    for group in groups:
        reg = factories[group] if factories is not None else registered(group)
        for sh in shadows.get(group, []):
            if wanted is not None and sh.model not in wanted:
                continue
            if sh.model in of_record.get(group, set()):
                o = Outcome(group, sh.model, sh.role, dry_run)
                o.error = (
                    f"{sh.model} is a {group} model of record (primary_models.json) and "
                    "publishes through its own job, not as a shadow"
                )
            elif sh.model not in reg:
                o = Outcome(group, sh.model, sh.role, dry_run)
                o.error = f"{sh.model} is not a registered {group} model"
            else:
                o = run_one(
                    engine,
                    sh,
                    reg[sh.model],
                    days=days,
                    divisions=divisions,
                    comps=comps,
                    dry_run=dry_run,
                    now=now,
                )
            outcomes.append(o)
            if report is not None:
                report(o)
    return outcomes
