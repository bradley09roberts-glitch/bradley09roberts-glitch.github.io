#!/usr/bin/env python3
"""Build a *local, personal* resource pack that swaps TerraCraft's original art for real Terraria sprites.

TerraCraft itself only ships original art (Terraria's sprites belong to Re-Logic and must not be
redistributed). This script lets someone who owns Terraria make a resource pack for their own use:

    # download each sprite from the Terraria wiki (needs internet)
    python tools/terraria_resource_pack.py --wiki

    # or use a folder of PNGs you exported yourself, named like the item ("Copper_Broadsword.png",
    # "Copper Broadsword.png" and "copper_broadsword.png" all work)
    python tools/terraria_resource_pack.py --folder path/to/sprites

The result is `TerraCraft-Terraria-Sprites.zip`. Put it in `.minecraft/resourcepacks/`, enable it above
the default pack, and do not share it.

The list of items comes from the mod's own textures and English names, so new items are picked up
automatically. `NAME_OVERRIDES` handles items whose Terraria name differs from the TerraCraft one and
`VANILLA_ITEMS` re-skins the vanilla items TerraCraft reuses (copper/iron/gold bars, wooden arrows).
"""
import argparse
import io
import json
import sys
import time
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path

try:
    from PIL import Image
except ImportError:
    sys.exit("This tool needs Pillow: pip install pillow")

ROOT = Path(__file__).resolve().parents[1]
ASSETS = ROOT / "src/main/resources/assets/terracraft"
LANG = ASSETS / "lang/en_us.json"
WIKI = "https://terraria.wiki.gg/wiki/Special:FilePath/"
RESOURCE_PACK_FORMAT = 88

# TerraCraft item id -> Terraria name (None = no Terraria equivalent, keep TerraCraft art).
NAME_OVERRIDES = {
    "dev_tablet": None,
    "raw_tin": "Tin Ore", "raw_lead": "Lead Ore", "raw_silver": "Silver Ore",
    "raw_tungsten": "Tungsten Ore", "raw_platinum": "Platinum Ore",
    "wood_helmet": "Wood Helmet", "wood_breastplate": "Wood Breastplate", "wood_greaves": "Wood Greaves",
}

# Vanilla Minecraft textures TerraCraft uses as Terraria items.
VANILLA_ITEMS = {
    "copper_ingot": "Copper Bar",
    "iron_ingot": "Iron Bar",
    "gold_ingot": "Gold Bar",
    "arrow": "Wooden Arrow",
    "raw_copper": "Copper Ore",
    "raw_iron": "Iron Ore",
    "raw_gold": "Gold Ore",
}


def item_names():
    lang = json.loads(LANG.read_text(encoding="utf-8"))
    names = {}
    for texture in sorted((ASSETS / "textures/item").glob("*.png")):
        item = texture.stem
        if item in NAME_OVERRIDES:
            name = NAME_OVERRIDES[item]
        else:
            name = lang.get(f"item.terracraft.{item}") or lang.get(f"block.terracraft.{item}")
        if name:
            names[("terracraft", item)] = name
    for item, name in VANILLA_ITEMS.items():
        names[("minecraft", item)] = name
    return names


def mob_names():
    """Enemy/NPC/boss sprites: textures/entity/mob/<id>.png with an <id>.json frame description."""
    lang = json.loads(LANG.read_text(encoding="utf-8"))
    names = {}
    for meta in sorted((ASSETS / "textures/entity/mob").glob("*.json")):
        name = lang.get(f"entity.terracraft.{meta.stem}")
        if name:
            names[meta.stem] = (name, json.loads(meta.read_text(encoding="utf-8")))
    return names


def to_sprite_sheet(data, own_meta):
    """Turns a (possibly animated) sprite into a vertical frame strip, keeping TerraCraft's flags."""
    image = Image.open(io.BytesIO(data))
    frames, durations = [], []
    for index in range(getattr(image, "n_frames", 1)):
        image.seek(index)
        frames.append(image.convert("RGBA"))
        durations.append(image.info.get("duration", 100) or 100)
    box = None
    for frame in frames:
        b = frame.getbbox()
        if b:
            box = b if box is None else (min(box[0], b[0]), min(box[1], b[1]), max(box[2], b[2]), max(box[3], b[3]))
    if box:
        frames = [f.crop(box) for f in frames]
    w, h = frames[0].size
    sheet = Image.new("RGBA", (w, h * len(frames)), (0, 0, 0, 0))
    for i, frame in enumerate(frames):
        sheet.paste(frame, (0, i * h))
    out = io.BytesIO()
    sheet.save(out, "PNG")
    meta = dict(own_meta)
    meta["frames"] = len(frames)
    meta["frame_time"] = max(1, round(sum(durations) / len(durations) / 50))
    return out.getvalue(), meta


def fetch_wiki(name):
    url = WIKI + urllib.parse.quote(name.replace(" ", "_") + ".png")
    request = urllib.request.Request(url, headers={"User-Agent": "TerraCraft-personal-resource-pack/1.0"})
    try:
        with urllib.request.urlopen(request, timeout=20) as response:
            return response.read()
    except Exception as error:  # noqa: BLE001 - report and continue
        print(f"  ! {name}: {error}")
        return None


def find_local(folder, name):
    candidates = [name, name.replace(" ", "_"), name.lower().replace(" ", "_").replace("'", "")]
    for candidate in candidates:
        path = folder / f"{candidate}.png"
        if path.exists():
            return path.read_bytes()
    return None


def to_item_texture(data):
    """First frame, padded onto a transparent square canvas so Minecraft does not stretch it."""
    image = Image.open(io.BytesIO(data))
    image.seek(0)
    image = image.convert("RGBA")
    box = image.getbbox()
    if box:
        image = image.crop(box)
    side = max(image.width, image.height, 16)
    canvas = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    canvas.paste(image, ((side - image.width) // 2, (side - image.height) // 2))
    out = io.BytesIO()
    canvas.save(out, "PNG")
    return out.getvalue()


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    source = parser.add_mutually_exclusive_group(required=True)
    source.add_argument("--wiki", action="store_true", help="download sprites from terraria.wiki.gg")
    source.add_argument("--folder", type=Path, help="folder of PNG sprites named after the items")
    parser.add_argument("--out", type=Path, default=Path("TerraCraft-Terraria-Sprites.zip"))
    args = parser.parse_args()

    names = item_names()
    done, missing = 0, []
    with zipfile.ZipFile(args.out, "w", zipfile.ZIP_DEFLATED) as pack:
        pack.writestr("pack.mcmeta", json.dumps({"pack": {
            "description": "TerraCraft with Terraria sprites (personal use only)",
            "min_format": RESOURCE_PACK_FORMAT, "max_format": RESOURCE_PACK_FORMAT}}, indent=2))
        for (namespace, item), name in names.items():
            data = fetch_wiki(name) if args.wiki else find_local(args.folder, name)
            if args.wiki:
                time.sleep(0.2)  # be polite to the wiki
            if not data:
                missing.append(name)
                continue
            try:
                pack.writestr(f"assets/{namespace}/textures/item/{item}.png", to_item_texture(data))
                done += 1
                print(f"  {namespace}:{item} <- {name}")
            except Exception as error:  # noqa: BLE001
                print(f"  ! {name}: {error}")
                missing.append(name)
        for mob, (name, meta) in mob_names().items():
            data = fetch_wiki(name) if args.wiki else find_local(args.folder, name)
            if args.wiki:
                time.sleep(0.2)
            if not data:
                missing.append(name)
                continue
            try:
                sheet, sheet_meta = to_sprite_sheet(data, meta)
                pack.writestr(f"assets/terracraft/textures/entity/mob/{mob}.png", sheet)
                pack.writestr(f"assets/terracraft/textures/entity/mob/{mob}.json", json.dumps(sheet_meta))
                done += 1
                print(f"  enemy {mob} <- {name} ({sheet_meta['frames']} frames)")
            except Exception as error:  # noqa: BLE001
                print(f"  ! {name}: {error}")
                missing.append(name)
    print(f"\nWrote {args.out} with {done} textures.")
    if missing:
        print("Not found (TerraCraft art kept): " + ", ".join(missing))


if __name__ == "__main__":
    main()
