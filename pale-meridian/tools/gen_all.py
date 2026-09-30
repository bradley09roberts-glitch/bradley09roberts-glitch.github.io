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
from pmgen.dp import loot_tables  # noqa: E402
from pmgen.dp.content import bench, chapter1, chapter2, chapter3, chapter4, fen as fen_content, journal_pages, keepsakes, prologue  # noqa: E402
from pmgen.dp import encounters  # noqa: E402
from pmgen.jsonio import GENERATED  # noqa: E402
from pmgen.paths import LAYOUT, PM_DATA, PM_ASSETS, RES  # noqa: E402
from pmgen.sites import export_site  # noqa: E402
from pmgen.sites import aldercross, deepcut, fen, glassworks, hollin, landing, meridian  # noqa: E402


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
    export_site("hollin", hollin.build())
    export_site("aldercross", aldercross.build())
    export_site("glassworks", glassworks.build())
    export_site("deepcut", deepcut.build(), anchor=(24, 60, -384))
    export_site("meridian", meridian.build(), anchor=(-10, 60, 20))
    export_site("fen", fen.build())
    # content registration
    encounters.generate_shared()
    for reg in (prologue.register, chapter1.register, chapter2.register, keepsakes.register, chapter3.register, chapter4.register, fen_content.register, bench.register):
        reg()
    ui_pages.generate(recaps=journal_pages.recaps(), people=journal_pages.people(), eleven=keepsakes.eleven_journal())
    loot_tables.generate()
    engine.generate()
    from pmgen.dp import questdoc, testfixture
    questdoc.write()
    testfixture.write()
    items.generate()
    atmosphere.generate()
    poi.write()
    core.generate()
    art.generate()
    import json as _json
    from pmgen.dp import MACRO_SAMPLES
    from pmgen.paths import TOOLS
    (TOOLS / "generated" / "macro_samples.json").write_text(_json.dumps(MACRO_SAMPLES, indent=1, sort_keys=True) + "\n")
    print(f"generated/verified {len(GENERATED)} files")


if __name__ == "__main__":
    main()
