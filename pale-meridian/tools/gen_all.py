#!/usr/bin/env python3
"""Regenerates every generated file of the Pale Meridian mod from the sources in tools/.

Usage:  python3 tools/gen_all.py
Requires: Python 3.11+, Pillow, a Java 25 runtime on PATH (or PM_JAVA) for the vanilla reports.
Network: downloads the pinned Minecraft 26.2 jars once (SHA-1 verified) into tools/.cache.
"""
import shutil
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from pmgen import art, poi, worldgen  # noqa: E402
from pmgen.dp import atmosphere, core, engine, items  # noqa: E402
from pmgen.dp import ui_pages  # noqa: E402
from pmgen.dp.content import hollin_stub, prologue  # noqa: E402
from pmgen.jsonio import GENERATED  # noqa: E402
from pmgen.paths import LAYOUT, PM_DATA, PM_ASSETS, RES  # noqa: E402
from pmgen.sites import export_site  # noqa: E402
from pmgen.sites import landing  # noqa: E402


def clean_generated_dirs() -> None:
    """Generated trees are rebuilt from scratch so removed content never lingers."""
    for sub in ("function", "advancement", "dialog", "predicate", "structure", "worldgen", "tags", "loot_table",
                "timeline", "world_clock", "item_modifier", "recipe"):
        shutil.rmtree(PM_DATA / sub, ignore_errors=True)
    for sub in ("textures", "waypoint_style", "models", "items", "lang", "blockstates"):
        shutil.rmtree(PM_ASSETS / sub, ignore_errors=True)
    shutil.rmtree(RES / "data" / "minecraft", ignore_errors=True)


def main() -> None:
    clean_generated_dirs()
    shutil.copyfile(LAYOUT, RES / "palemeridian_layout.json")
    worldgen.generate()
    # sites (register POIs as they are built)
    export_site("landing", landing.build())
    # content registration
    prologue_stub_order = [hollin_stub.register, prologue.register]
    for reg in prologue_stub_order:
        reg()
    ui_pages.generate(
        recaps=[
            ("if score p.road pm.q matches 1..", ["You relit the Landing lamp and the fog drew back from the south rim.",
                                                  "The lamp road runs north into the Pall, toward the village of Hollin.",
                                                  "On the road you glimpsed a figure with a lantern. It vanished."]),
            ("if score p.letter pm.q matches 2", ["Tamsin Reed, your old mentor, wrote to you from inside the fog of Vell.",
                                                   "She asked you to come yourself, bring light, and relight the lamp at the Landing."]),
            ("", ["You have just arrived at the Landing, on the south rim of the Vale of Vell.",
                  "A letter waits for you on the noticeboard."]),
        ],
        people=[("if score p.letter pm.q matches 2", "Tamsin Reed", ["Your former mentor at the Chartered Survey. Clever, stubborn, funny when it's least appropriate.",
                                                                     "She went into the Pall a year ago and stopped writing. Then her letter came."])],
        eleven=[],
    )
    engine.generate()
    items.generate()
    atmosphere.generate()
    poi.write()
    core.generate()
    art.generate()
    print(f"generated/verified {len(GENERATED)} files")


if __name__ == "__main__":
    main()
