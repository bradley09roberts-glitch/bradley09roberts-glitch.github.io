"""Isometric preview renderer for structure templates (QA only; never shipped in the mod).

Colours come from the real 26.2 block textures (average colour of the relevant face texture,
biome-tinted where vanilla tints). Shapes are approximate: full cubes, slabs, thin posts, flat plants.
"""
from __future__ import annotations

import io
import json
from functools import lru_cache

from PIL import Image, ImageDraw

from . import vanilla

TINT_GRASS = (124, 189, 107)
TINT_FOLIAGE = (89, 174, 48)
TINT_PALE = (139, 146, 133)

PLANT_WORDS = ("flower", "tulip", "poppy", "dandelion", "orchid", "allium", "bluet", "daisy", "cornflower", "lily_of_the_valley",
               "short_grass", "tall_grass", "fern", "sapling", "bush", "eyeblossom", "roots", "sugar_cane", "wheat", "carrots",
               "potatoes", "beetroots", "sweet_berry", "dead_bush", "seagrass", "kelp", "vine", "hanging_moss", "moss_carpet",
               "pink_petals", "leaf_litter", "wildflowers", "firefly_bush", "torchflower", "pitcher")
THIN_WORDS = ("fence", "wall", "pane", "bars", "chain", "lantern", "torch", "rod", "candle", "rail", "ladder", "lever", "button",
              "sign", "banner", "bell", "flower_pot", "head", "skull", "chain", "grate")


@lru_cache(maxsize=None)
def _texture_color(path: str):
    try:
        img = Image.open(io.BytesIO(vanilla.asset_bytes(path))).convert("RGBA")
    except KeyError:
        return None
    img = img.crop((0, 0, img.width, min(img.height, img.width)))
    px = [p for p in img.getdata() if p[3] > 30]
    if not px:
        return None
    n = len(px)
    return (sum(p[0] for p in px) // n, sum(p[1] for p in px) // n, sum(p[2] for p in px) // n)


@lru_cache(maxsize=None)
def _model(path: str) -> dict:
    try:
        return json.loads(vanilla.asset_bytes(f"assets/minecraft/models/{path}.json"))
    except KeyError:
        return {}


def _resolve_textures(model_id: str) -> dict:
    textures = {}
    mid = model_id
    for _ in range(12):
        path = mid.split(":")[-1]
        m = _model(path)
        if not m:
            break
        for k, v in m.get("textures", {}).items():
            if isinstance(v, dict):
                v = v.get("sprite") or v.get("texture") or next((x for x in v.values() if isinstance(x, str)), None)
            if isinstance(v, str):
                textures.setdefault(k, v)
        if "parent" not in m:
            break
        mid = m["parent"]
    # resolve #refs
    for _ in range(6):
        for k, v in list(textures.items()):
            if isinstance(v, str) and v.startswith("#"):
                textures[k] = textures.get(v[1:], v)
    return textures


@lru_cache(maxsize=None)
def _block_models(name: str) -> list:
    short = name.split(":")[-1]
    try:
        bs = json.loads(vanilla.asset_bytes(f"assets/minecraft/blockstates/{short}.json"))
    except KeyError:
        return []
    models = []
    if "variants" in bs:
        for v in bs["variants"].values():
            v = v[0] if isinstance(v, list) else v
            models.append(v["model"])
    elif "multipart" in bs:
        for part in bs["multipart"]:
            a = part["apply"]
            a = a[0] if isinstance(a, list) else a
            models.append(a["model"])
    return models


@lru_cache(maxsize=None)
def face_colors(name: str):
    """(top, side) RGB colours for a block name."""
    short = name.split(":")[-1]
    special = {"water": ((63, 118, 228), (63, 118, 228)), "lava": ((207, 92, 20), (207, 92, 20)),
               "air": None}
    if short in special:
        return special[short]
    models = _block_models(name)
    tex = _resolve_textures(models[0]) if models else {}
    def col(keys):
        for k in keys:
            t = tex.get(k)
            if t and not t.startswith("#"):
                c = _texture_color("assets/minecraft/textures/" + t.split(":")[-1] + ".png")
                if c:
                    return c
        return None
    top = col(["top", "end", "up", "all", "texture", "cross", "plant", "particle", "side"])
    side = col(["side", "all", "texture", "north", "front", "cross", "plant", "particle", "top"])
    if top is None:
        top = _texture_color(f"assets/minecraft/textures/block/{short}.png") or (160, 160, 160)
    if side is None:
        side = top
    if short in ("grass_block",):
        top = tuple(int(c * t / 255) for c, t in zip(_texture_color("assets/minecraft/textures/block/grass_block_top.png") or (150, 150, 150), TINT_GRASS))
    if "leaves" in short and "pale" not in short and "cherry" not in short and "azalea" not in short:
        top = side = tuple(int(c * t / 255) for c, t in zip(top, TINT_FOLIAGE))
    if short in ("short_grass", "tall_grass", "fern", "large_fern", "vine"):
        top = side = tuple(int(c * t / 255) for c, t in zip(top, TINT_GRASS))
    return top, side


def _shape(name: str, props: dict) -> str:
    short = name.split(":")[-1]
    if short.endswith("_slab"):
        return "slab_top" if props.get("type") == "top" else ("full" if props.get("type") == "double" else "slab")
    if short.endswith("_carpet") or short in ("snow", "pale_moss_carpet", "moss_carpet", "leaf_litter", "pink_petals", "lily_pad"):
        return "carpet"
    if short.endswith("_trapdoor"):
        return "carpet" if props.get("open") == "false" else "thin"
    if short.endswith("_door"):
        return "thin"
    if any(w in short for w in PLANT_WORDS) and "block" not in short and "leaves" not in short:
        return "plant"
    if any(w in short for w in THIN_WORDS) and "block" not in short:
        return "thin"
    if short.endswith("_stairs"):
        return "stairs"
    return "full"


def render(build, path, scale: int = 6, crop_air: bool = True, title: str | None = None, max_side: int = 2600) -> None:
    items = []
    for (x, y, z), (bs, _) in build.blocks.items():
        if bs.name == "minecraft:air" or bs.name == "minecraft:structure_void":
            continue
        items.append((x, y, z, bs))
    if not items:
        return
    sx, sy, sz = build.size_x, build.size_y, build.size_z
    # auto-scale down big builds
    while scale > 1 and (sx + sz) * scale * 2 > max_side:
        scale -= 1
    w = (sx + sz) * scale * 2 + 40
    h = (sx + sz) * scale + sy * scale * 2 + 60
    img = Image.new("RGB", (w, h), (30, 32, 36))
    d = ImageDraw.Draw(img)
    ox = sz * scale * 2 + 20
    oy = sy * scale * 2 + 30

    def proj(px, py, pz):
        # view from south-east: +x goes right-down, +z goes left-down
        return (ox + (px - pz) * scale * 2, oy + (px + pz) * scale - py * scale * 2)

    items.sort(key=lambda t: (t[0] + t[2], t[1], t[0]))
    for x, y, z, bs in items:
        props = dict(bs.props)
        cols = face_colors(bs.name)
        if cols is None:
            continue
        top, side = cols
        shape = _shape(bs.name, props)
        x0, x1, z0, z1, y0, y1 = x, x + 1, z, z + 1, y, y + 1
        if shape == "slab":
            y1 = y + 0.5
        elif shape == "slab_top":
            y0 = y + 0.5
        elif shape == "carpet":
            y1 = y + 0.12
        elif shape == "thin":
            x0, x1, z0, z1 = x + 0.38, x + 0.62, z + 0.38, z + 0.62
        elif shape == "plant":
            x0, x1, z0, z1, y1 = x + 0.3, x + 0.7, z + 0.3, z + 0.7, y + 0.7
        elif shape == "stairs":
            y1 = y + 0.8
        left = tuple(int(c * 0.72) for c in side)
        right = tuple(int(c * 0.86) for c in side)
        # top face
        d.polygon([proj(x0, y1, z0), proj(x1, y1, z0), proj(x1, y1, z1), proj(x0, y1, z1)], fill=top)
        # south face (z1), shown lower-left
        d.polygon([proj(x0, y1, z1), proj(x1, y1, z1), proj(x1, y0, z1), proj(x0, y0, z1)], fill=left)
        # east face (x1), shown lower-right
        d.polygon([proj(x1, y1, z0), proj(x1, y1, z1), proj(x1, y0, z1), proj(x1, y0, z0)], fill=right)
    if title:
        d.text((10, 8), title, fill=(230, 230, 230))
    img.save(path)


def render_top(build, path, scale: int = 4, title: str | None = None) -> None:
    """Plan view (top-down) of the highest non-air block per column."""
    sx, sz = build.size_x, build.size_z
    top = {}
    for (x, y, z), (bs, _) in build.blocks.items():
        if bs.name in ("minecraft:air", "minecraft:structure_void"):
            continue
        if (x, z) not in top or y > top[(x, z)][0]:
            top[(x, z)] = (y, bs)
    img = Image.new("RGB", (sx * scale, sz * scale + 16), (30, 32, 36))
    d = ImageDraw.Draw(img)
    maxy = max((v[0] for v in top.values()), default=1)
    for (x, z), (y, bs) in top.items():
        cols = face_colors(bs.name)
        if not cols:
            continue
        c = cols[0]
        f = 0.6 + 0.4 * (y / max(1, maxy))
        c = tuple(min(255, int(v * f)) for v in c)
        d.rectangle([x * scale, z * scale + 16, x * scale + scale - 1, z * scale + 16 + scale - 1], fill=c)
    if title:
        d.text((4, 2), title, fill=(230, 230, 230))
    img.save(path)
