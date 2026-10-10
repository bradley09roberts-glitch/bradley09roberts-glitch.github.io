#!/usr/bin/env python3
"""Adds new skin PNGs to the skin list, so a big batch of skins needs no hand-editing.

Usage:  python3 tools/add_skins.py          (then rebuild the mod with ./gradlew build)

1. Put 64x64 player skin PNGs in
       src/main/resources/assets/hardcorefriends/textures/entity/people/wide/   (classic arms, 4 pixels wide)
       src/main/resources/assets/hardcorefriends/textures/entity/people/slim/   (slim arms, 3 pixels wide)
   File names: lower case letters, digits and underscores, ending in .png. Start a name with child_ for a skin only
   children wear, adult_ for one only grown-ups wear; anything else may be worn by anyone.
2. Run this script. Every PNG not yet in skins.json gets an entry with the next free number from 1000 up. Skins
   already listed keep their numbers (worlds remember skins by number, so numbers never change).

Tags: the first word of the name after adult_/child_ becomes the skin's tag when it is a trade or a place the mod
knows (see TRADES and PLACES below), e.g. adult_baker_rosa.png is tagged "baker" and adult_dark_forest_greta.png
"dark_forest". People are more likely to wear a skin whose tags match their trade or the land they come from. A
skin with no known word is untagged and worn by anyone of the right age.

Files that are not 64x64 PNGs, or whose names have other characters, are reported and skipped.
"""
from __future__ import annotations

import json
import re
import struct
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "src" / "main" / "resources" / "assets" / "hardcorefriends"
SKINS_JSON = ASSETS / "skins.json"
FOLDERS = {"wide": ASSETS / "textures" / "entity" / "people" / "wide",
           "slim": ASSETS / "textures" / "entity" / "people" / "slim"}
FIRST_ID = 1000
NAME = re.compile(r"^[a-z0-9_]+\.png$")
# Kept in step with people/Skins.java (TRADE_TAGS and the place tags).
TRADES = {"baker", "butcher", "fisher", "shepherd", "beekeeper", "mason", "carpenter", "blacksmith", "tailor",
          "teacher", "doctor", "shopkeeper", "innkeeper", "farmer", "guard", "farmhand", "hunter", "miner", "scholar",
          "traveller", "wanderer", "weaver", "bard"}
PLACES = {"desert", "snowy", "jungle", "swamp", "savanna", "dark_forest"}


def tags_for(file_name: str) -> list[str]:
    words = file_name[:-len(".png")].split("_")
    if words[0] in ("adult", "child"):
        words = words[1:]
    for size in (2, 1):
        word = "_".join(words[:size])
        if len(words) > size and (word in TRADES or word in PLACES):
            return [word]
    return []


def png_size(path: Path) -> tuple[int, int] | None:
    data = path.read_bytes()[:24]
    if len(data) < 24 or data[:8] != b"\x89PNG\r\n\x1a\n" or data[12:16] != b"IHDR":
        return None
    return struct.unpack(">II", data[16:24])


def main() -> int:
    doc = json.loads(SKINS_JSON.read_text(encoding="utf-8"))
    skins = doc["skins"]
    known = {s["texture"] for s in skins}
    used = {int(s["id"]) for s in skins}
    next_id = max([FIRST_ID - 1] + [i for i in used if i >= FIRST_ID]) + 1
    added = 0
    for model, folder in FOLDERS.items():
        if not folder.is_dir():
            continue
        for png in sorted(folder.glob("*.png")):
            texture = f"hardcorefriends:textures/entity/people/{model}/{png.name}"
            if texture in known:
                continue
            if not NAME.match(png.name):
                print(f"skipped {png.name}: use only lower case letters, digits and underscores")
                continue
            if png_size(png) != (64, 64):
                print(f"skipped {png.name}: not a 64x64 PNG")
                continue
            wear = "child" if png.name.startswith("child_") else "adult" if png.name.startswith("adult_") else "any"
            while next_id in used:
                next_id += 1
            entry = {"id": next_id, "texture": texture, "model": model, "for": wear}
            tags = tags_for(png.name)
            if tags:
                entry["tags"] = tags
            skins.append(entry)
            used.add(next_id)
            print(f"added {next_id}: {model}/{png.name} ({wear}{', ' + ', '.join(tags) if tags else ''})")
            next_id += 1
            added += 1
    if added:
        rows = ",\n".join("\t\t" + json.dumps(s, ensure_ascii=False) for s in skins)
        text = "{\n\t\"_help\": " + json.dumps(doc.get("_help", ""), ensure_ascii=False) + ",\n\t\"skins\": [\n" + rows + "\n\t]\n}\n"
        json.loads(text)  # never write a broken file
        SKINS_JSON.write_text(text, encoding="utf-8")
    print(f"{added} new skin(s); {len(skins)} in all. Rebuild the mod to use them.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
