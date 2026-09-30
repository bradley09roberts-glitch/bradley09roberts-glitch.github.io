"""Points of interest exported by the site builders (single source of truth for story coordinates).

Every story-relevant position (interaction targets, blueprint parts, NPC anchors, detection volumes)
is registered here in WORLD coordinates by the code that builds the place, and consumed by the data
pack generator. The resulting table is also written to tools/generated/poi.json for review and tests.
"""
from __future__ import annotations

import json

from .paths import TOOLS

POI: dict[str, dict] = {}


def add(name: str, x: float, y: float, z: float, **extra) -> dict:
    if name in POI:
        raise KeyError(f"duplicate POI {name}")
    entry = {"x": x, "y": y, "z": z}
    entry.update(extra)
    POI[name] = entry
    return entry


def get(name: str) -> dict:
    return POI[name]


def box(name: str, x1: int, y1: int, z1: int, x2: int, y2: int, z2: int, **extra) -> dict:
    """Axis-aligned detection volume (inclusive block coordinates)."""
    return add(name, (x1 + x2) / 2, (y1 + y2) / 2, (z1 + z2) / 2,
               box=[min(x1, x2), min(y1, y2), min(z1, z2), max(x1, x2), max(y1, y2), max(z1, z2)], **extra)


def write() -> None:
    out = TOOLS / "generated" / "poi.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(POI, indent=1, sort_keys=True) + "\n")
