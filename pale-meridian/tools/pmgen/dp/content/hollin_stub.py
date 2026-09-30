"""Provisional Hollin gate area (replaced by the full Hollin site in Chapter 1)."""
from ... import poi
from ..engine import R, Area


def register() -> None:
    if "hollin.gate" not in poi.POI:
        poi.add("hollin.gate", 112, 67, 150)
        R.area(Area("hollin_gate", [100, 60, 138, 124, 80, 162]))
