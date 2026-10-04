"""sgsynth.registry - tiny event registry used by the ev_*.py modules.

``@sound("door.lock", "Door locks")`` registers a function ``fn(variant, rng) -> samples`` for one
event (rendered once per variant).  ``@sound_group`` registers one function that renders several
events at once (the ten doll syllables are loudness-matched together).
"""
from __future__ import annotations

from dataclasses import dataclass
from typing import Callable, List

from .contract import CONTRACT


@dataclass
class Event:
    id: str
    subtitle: str
    files: List[str]          # relative to sounds/, no extension, e.g. "marble/click_1"
    stream: bool = False


@dataclass
class Unit:
    name: str
    events: List[Event]
    fn: Callable
    grouped: bool = False     # True: fn() -> list of arrays, one per file (flattened in event order)
    variants: int = 1


UNITS: List[Unit] = []


def _default_files(event_id: str, variants: int, files):
    if files:
        return list(files)
    group, name = event_id.split(".", 1)
    if variants == 1:
        return [f"{group}/{name}"]
    return [f"{group}/{name}_{i + 1}" for i in range(variants)]


def sound(event_id: str, subtitle: str, variants: int = 1, stream: bool = False, files=None):
    if event_id not in CONTRACT:
        raise KeyError(f"{event_id} is not in the contract")

    def deco(fn):
        ev = Event(event_id, subtitle, _default_files(event_id, variants, files), stream)
        UNITS.append(Unit(event_id, [ev], fn, False, variants))
        return fn
    return deco


def sound_group(name: str, events: list):
    """events: list of (event_id, subtitle, file) - fn() must return one array per event."""
    def deco(fn):
        evs = []
        for eid, sub, f in events:
            if eid not in CONTRACT:
                raise KeyError(f"{eid} is not in the contract")
            evs.append(Event(eid, sub, [f], False))
        UNITS.append(Unit(name, evs, fn, True))
        return fn
    return deco
