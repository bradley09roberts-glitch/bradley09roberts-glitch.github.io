"""Models of record with effective-from times (DECISIONS.md D-026).

`reference/primary_models.json` names the model of record per competition group ("domestic",
"uefa"). Each group is a list of periods, oldest first:

    "uefa": [
      {"from_utc": null, "models": ["pan_euro_mix"]},
      {"from_utc": "2026-10-21T04:30:00Z", "models": ["pan_euro_mix_mxg"]}
    ]

A fixture takes the latest period whose `from_utc` is at or before its kickoff (00:00 UK on its
date when the kickoff is unknown). Within that period the record of record is the first listed
model with a genuine pre-match record for the fixture. `from_utc: null` (first period only)
means "from the start". The older forms still work: a plain model name or a plain list of
names is one period from the start.

A new period is only ever appended, from now or later (`append_period`, CLI
`footy set-model-of-record`). A fixture that has already kicked off therefore keeps the period
it was played under, so switching the model of record never rewrites a settled record.
"""

import json
import math
import os
from dataclasses import dataclass
from datetime import UTC, date, datetime, time, timedelta
from pathlib import Path
from zoneinfo import ZoneInfo

UK = ZoneInfo("Europe/London")
GROUPS = ("domestic", "uefa")
PERIOD_KEYS = {"from_utc", "models", "note"}


@dataclass(frozen=True)
class Period:
    from_utc: datetime | None  # None: from the start (first period only)
    models: tuple[str, ...]
    note: str | None = None

    def as_json(self) -> dict:
        out: dict = {"from_utc": format_utc(self.from_utc), "models": list(self.models)}
        if self.note:
            out["note"] = self.note
        return out


def format_utc(dt: datetime | None) -> str | None:
    """UTC with a Z, e.g. "2026-10-21T04:30:00Z"."""
    if dt is None:
        return None
    return dt.astimezone(UTC).isoformat().replace("+00:00", "Z")


def parse_utc(value: object, where: str) -> datetime:
    """An ISO 8601 time with an explicit offset (`Z` or `+00:00`), as an aware UTC datetime.
    A time without an offset is refused: it would be ambiguous (D-005)."""
    if not isinstance(value, str) or not value.strip():
        raise ValueError(f"{where}: from_utc must be an ISO 8601 time such as 2026-10-24T11:00:00Z")
    try:
        dt = datetime.fromisoformat(value.strip())
    except ValueError:
        raise ValueError(f"{where}: unreadable from_utc {value!r}") from None
    if dt.tzinfo is None or dt.utcoffset() is None:
        raise ValueError(f"{where}: from_utc {value!r} has no UTC offset (add Z)")
    return dt.astimezone(UTC)


def _names(value: object, where: str) -> tuple[str, ...]:
    names = [value] if isinstance(value, str) else value
    # An empty list would leave the group with no record of record at all, silently.
    if (
        not isinstance(names, list)
        or not names
        or not all(isinstance(n, str) and n.strip() == n and n for n in names)
    ):
        raise ValueError(f"{where} must be a model name or a non-empty list of names")
    return tuple(dict.fromkeys(names))


def parse_group(value: object, where: str) -> list[Period]:
    """One group's value: a model name, a list of names (one period from the start) or a
    list of periods. Raises ValueError naming `where` for anything malformed."""
    if isinstance(value, list) and value and all(isinstance(p, dict) for p in value):
        periods: list[Period] = []
        for i, p in enumerate(value):
            at = f"{where} period {i + 1}"
            unknown = set(p) - PERIOD_KEYS
            if unknown or "from_utc" not in p or "models" not in p:
                raise ValueError(
                    f"{at} must have from_utc and models (and optionally note); "
                    f"unknown keys: {sorted(unknown)}"
                )
            if p["from_utc"] is None and i > 0:
                raise ValueError(f"{at}: only the first period may start from null")
            start = None if p["from_utc"] is None else parse_utc(p["from_utc"], at)
            note = p.get("note")
            if note is not None and not isinstance(note, str):
                raise ValueError(f"{at}: note must be text")
            if periods and start is not None and periods[-1].from_utc is not None:
                if start <= periods[-1].from_utc:
                    raise ValueError(f"{at}: periods must be in strictly increasing from_utc")
            periods.append(Period(start, _names(p["models"], f"{at} models"), note or None))
        return periods
    if isinstance(value, list) and any(isinstance(p, dict) for p in value):
        raise ValueError(f"{where} mixes periods and model names")
    return [Period(None, _names(value, where))]


def load(path: Path) -> dict[str, list[Period]]:
    """Every group's periods. A missing file means no model of record anywhere."""
    if not path.exists():
        return {}
    data = json.loads(path.read_text())
    if not isinstance(data, dict):
        raise ValueError(f"{path.name}: expected an object of groups")
    return {
        group: parse_group(value, f"{path.name}: {group!r}")
        for group, value in data.items()
        if not group.startswith("_")
    }


def fixture_time(kickoff_utc: datetime | None, match_date: date) -> datetime:
    """The time that decides a fixture's period: its kickoff, or 00:00 UK on its date when
    the kickoff is unknown. A naive kickoff is stored UTC (D-005)."""
    if kickoff_utc is not None:
        k = kickoff_utc if kickoff_utc.tzinfo else kickoff_utc.replace(tzinfo=UTC)
        return k.astimezone(UTC)
    return datetime.combine(match_date, time(0), tzinfo=UK).astimezone(UTC)


def period_at(periods: list[Period], when: datetime) -> Period | None:
    """The latest period starting at or before `when` (None if every period starts later)."""
    found = None
    for p in periods:
        if p.from_utc is None or p.from_utc <= when:
            found = p
    return found


def models_at(periods: list[Period], when: datetime) -> list[str]:
    p = period_at(periods, when)
    return list(p.models) if p else []


def parse_from(value: str, now: datetime) -> datetime:
    """`now`, or an ISO 8601 time with an offset. "now" is rounded up to the whole second."""
    if value.strip().lower() == "now":
        return now.replace(microsecond=0) + timedelta(seconds=math.ceil(now.microsecond / 1e6))
    return parse_utc(value, "--from")


def append_period(
    path: Path,
    group: str,
    models: list[str],
    from_utc: datetime,
    *,
    note: str | None = None,
    now: datetime | None = None,
) -> list[Period]:
    """Append a period to `group` and save the file; returns the group's periods.

    Refused (ValueError, file untouched): an unknown group, a malformed file, a start before
    `now` (that would re-assign fixtures already played), a start not after the group's last
    period, or a malformed model list. A group in the plain form is first rewritten as one
    period from the start, which keeps its meaning."""
    now = (now or datetime.now(UTC)).astimezone(UTC)
    if group not in GROUPS:
        raise ValueError(f"unknown group {group!r}; expected one of {', '.join(GROUPS)}")
    if from_utc.tzinfo is None:
        raise ValueError("from_utc needs a UTC offset")
    from_utc = from_utc.astimezone(UTC)
    if from_utc < now:
        raise ValueError(
            f"from {format_utc(from_utc)} is before now ({format_utc(now)}): a model of record "
            "can only change from now on, so fixtures already played keep theirs (D-026)"
        )
    data = json.loads(path.read_text()) if path.exists() else {}
    existing = load(path) if path.exists() else {}
    periods = list(existing.get(group, []))
    last = periods[-1].from_utc if periods else None
    if last is not None and from_utc <= last:
        raise ValueError(
            f"from {format_utc(from_utc)} is not after the last {group} period "
            f"({format_utc(last)}); periods are only ever appended"
        )
    new = Period(from_utc, _names(list(models), "models"), note or None)
    periods.append(new)
    data[group] = [p.as_json() for p in periods]
    parse_group(data[group], f"{path.name}: {group!r}")  # the result must read back
    tmp = path.with_suffix(path.suffix + ".tmp")
    tmp.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n")
    os.replace(tmp, path)
    return periods
