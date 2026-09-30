"""World clocks and timelines: the global Pall haze and encounter 'surge' pulses (26.1+ world clocks)."""
from __future__ import annotations

from ..jsonio import write_json
from ..paths import PM_DATA, DATA
from . import tag

# Pall clock milestones (ticks). Functions jump the paused clock to these values.
PALL_LEVELS = {
    "start": 0,
    "landing": 1000,
    "hollin": 2000,
    "aldercross": 3000,
    "glassworks": 4000,
    "ending_true": 5000,
    "ending_blank": 6000,
}


def generate() -> None:
    write_json(PM_DATA / "world_clock" / "pall.json", {})
    write_json(PM_DATA / "world_clock" / "surge.json", {})
    # A world-wide haze that caps fog distance and thins as districts are restored (Pall biomes keep
    # their own, much thicker fog because 'minimum' never raises a value).
    write_json(PM_DATA / "timeline" / "pall.json", {
        "clock": "palemeridian:pall",
        "tracks": {
            "minecraft:visual/fog_end_distance": {
                "modifier": "minimum",
                "keyframes": [
                    {"ticks": 0, "value": 150.0},
                    {"ticks": 1000, "value": 176.0},
                    {"ticks": 2000, "value": 224.0},
                    {"ticks": 3000, "value": 288.0},
                    {"ticks": 4000, "value": 384.0},
                    {"ticks": 5000, "value": 2048.0},
                    {"ticks": 5999, "value": 2048.0},
                    {"ticks": 6000, "value": 640.0},
                ],
            },
            "minecraft:visual/sky_fog_end_distance": {
                "modifier": "minimum",
                "keyframes": [
                    {"ticks": 0, "value": 140.0},
                    {"ticks": 2000, "value": 200.0},
                    {"ticks": 4000, "value": 320.0},
                    {"ticks": 5000, "value": 2048.0},
                    {"ticks": 5999, "value": 2048.0},
                    {"ticks": 6000, "value": 560.0},
                ],
            },
        },
    })
    # Encounter pulse: while the surge clock runs, fog thickens and reddens in a slow 4-second breath.
    write_json(PM_DATA / "timeline" / "surge.json", {
        "clock": "palemeridian:surge",
        "period_ticks": 80,
        "tracks": {
            "minecraft:visual/fog_end_distance": {
                "modifier": "multiply",
                "keyframes": [{"ticks": 0, "value": 1.0}, {"ticks": 40, "value": 0.55}, {"ticks": 79, "value": 1.0}],
            },
            "minecraft:visual/fog_color": {
                "modifier": "multiply",
                "keyframes": [{"ticks": 0, "value": "#ffffff"}, {"ticks": 40, "value": "#e8b8b0"}, {"ticks": 79, "value": "#ffffff"}],
            },
        },
    })
    tag("timeline", "minecraft", "in_overworld", ["palemeridian:pall", "palemeridian:surge"])
