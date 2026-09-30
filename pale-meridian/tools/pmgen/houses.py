"""Parametric Vell-style buildings (cottages, halls, towers) built into a structure.Build."""
from __future__ import annotations

import json
import random
from dataclasses import dataclass

from .buildkit import bed, chimney, door, gable_roof, stairs, timber_walls, window
from .paths import TOOLS
from .structure import Build, container_nbt, item_stack_nbt, sign_nbt

_HEIGHTS = None


def heights() -> dict:
    global _HEIGHTS
    if _HEIGHTS is None:
        _HEIGHTS = json.loads((TOOLS / "generated" / "heightmaps.json").read_text())
    return _HEIGHTS


def ground_at(site: str, wx: int, wz: int) -> int:
    """Design ground height (first air block) at a world column, from the exported heightmap."""
    h = heights()[site]
    dx, dz = wx - h["x0"], wz - h["z0"]
    dx = max(0, min(h["w"] - 1, dx))
    dz = max(0, min(h["w"] - 1, dz))
    return h["ground"][dz * h["w"] + dx]


@dataclass
class Style:
    base: str = "cobblestone"
    lower: str = "stone_bricks"
    frame: str = "stripped_dark_oak_log"
    infill: str = "calcite"
    roof: str = "dark_oak"
    floor: str = "spruce_planks"
    door: str = "spruce"
    glass: str = "glass_pane"
    trim: str = "spruce"


STYLES = {
    "plain": Style(),
    "warm": Style(frame="stripped_spruce_log", infill="white_terracotta", roof="spruce", floor="oak_planks", door="oak"),
    "stone": Style(lower="mossy_stone_bricks", frame="stripped_dark_oak_log", infill="stone_bricks", roof="deepslate_tile", floor="spruce_planks"),
    "pale": Style(frame="stripped_pale_oak_log", infill="calcite", roof="pale_oak", floor="pale_oak_planks", door="pale_oak", trim="pale_oak"),
    "brick": Style(lower="bricks", frame="stripped_dark_oak_log", infill="bricks", roof="deepslate_tile", floor="spruce_planks"),
}


def cottage(b: Build, x0: int, z0: int, w: int, d: int, fl: int, door_side: str, style: Style, rng: random.Random,
            role: str = "home", storeys: int = 1, ridge: str | None = None, chimney_side: str = "west",
            furnish: bool = True, loot: list | None = None) -> dict:
    """A timber-framed cottage. Returns key interior positions."""
    x1, z1 = x0 + w - 1, z0 + d - 1
    wall_top = fl + 3 * storeys + (0 if storeys == 1 else 0)
    # plinth + floor
    b.fill(x0 - 1, fl - 2, z0 - 1, x1 + 1, fl - 2, z1 + 1, style.base)
    b.fill(x0, fl - 1, z0, x1, fl - 1, z1, style.floor)
    b.fill(x0 - 1, fl - 1, z0 - 1, x1 + 1, fl - 1, z0 - 1, style.base)
    b.fill(x0 - 1, fl - 1, z1 + 1, x1 + 1, fl - 1, z1 + 1, style.base)
    b.fill(x0 - 1, fl - 1, z0, x0 - 1, fl - 1, z1, style.base)
    b.fill(x1 + 1, fl - 1, z0, x1 + 1, fl - 1, z1, style.base)
    timber_walls(b, x0, z0, x1, z1, fl, wall_top, style.frame, style.infill, base=style.lower, post_every=4)
    b.fill(x0 + 1, fl, z0 + 1, x1 - 1, wall_top, z1 - 1, "air")
    if storeys > 1:
        b.fill(x0 + 1, fl + 3, z0 + 1, x1 - 1, fl + 3, z1 - 1, style.floor)
        b.fill(x0 + 1, fl + 4, z0 + 1, x1 - 1, wall_top, z1 - 1, "air")
    axis = ridge or ("x" if w >= d else "z")
    rtop = gable_roof(b, x0, z0, x1, z1, wall_top + 1, style.roof, ridge_axis=axis, overhang=1, fill_gable=style.infill)
    # clear the attic under the roof
    for y in range(wall_top + 1, rtop):
        pass
    # door
    mid_x, mid_z = (x0 + x1) // 2, (z0 + z1) // 2
    facing = {"south": "north", "north": "south", "east": "west", "west": "east"}[door_side]
    if door_side == "south":
        dx, dz = mid_x, z1
    elif door_side == "north":
        dx, dz = mid_x, z0
    elif door_side == "east":
        dx, dz = x1, mid_z
    else:
        dx, dz = x0, mid_z
    door(b, dx, fl, dz, facing, style.door)
    b.set(dx, fl + 2, dz, style.frame + ("[axis=y]" if "log" in style.frame else ""))
    # windows (skip the door column)
    for x in range(x0 + 2, x1 - 1, 3):
        for z in (z0, z1):
            if (x, z) != (dx, dz) and abs(x - dx) > 0:
                window(b, x, fl + 1, z, style.glass)
                if storeys > 1:
                    window(b, x, fl + 4, z, style.glass)
    for z in range(z0 + 2, z1 - 1, 3):
        for x in (x0, x1):
            if (x, z) != (dx, dz) and abs(z - dz) > 0:
                window(b, x, fl + 1, z, style.glass)
    # chimney
    cx = x0 - 1 if chimney_side == "west" else x1 + 1
    chimney(b, cx, mid_z + (1 if door_side in ("west", "east") else 0), fl - 1, rtop + 1, "bricks" if style.lower != "bricks" else "stone_bricks")
    inside = {"door": (dx, dz), "x0": x0 + 1, "z0": z0 + 1, "x1": x1 - 1, "z1": z1 - 1, "fl": fl, "roof_top": rtop}
    if furnish:
        _furnish(b, inside, role, rng, style, loot)
    return inside


def _furnish(b: Build, r: dict, role: str, rng: random.Random, style: Style, loot: list | None) -> None:
    x0, z0, x1, z1, fl = r["x0"], r["z0"], r["x1"], r["z1"], r["fl"]
    dx, dz = r["door"]
    free = lambda x, z: b.get(x, fl, z) is not None and b.get(x, fl, z).name == "minecraft:air" and (abs(x - dx) + abs(z - dz) > 1)
    # lantern hanging from the ceiling
    cx, cz = (x0 + x1) // 2, (z0 + z1) // 2
    top = fl + 3
    if b.get(cx, top, cz) is not None and b.get(cx, top, cz).name == "minecraft:air":
        b.set(cx, top, cz, "spruce_planks")
    b.set(cx, top - 1, cz, "lantern[hanging=true]")
    corners = [(x0, z0), (x1, z0), (x0, z1), (x1, z1)]
    rng.shuffle(corners)
    if role in ("home", "rest"):
        c = corners.pop()
        if free(*c) and role == "home":
            facing = "south" if c[1] == z0 else "north"
            nz = c[1] + (1 if facing == "south" else -1)
            if free(c[0], nz):
                bed(b, c[0], fl, nz, "north" if facing == "south" else "south", rng.choice(["red", "blue", "brown", "light_gray", "green"]))
        c = corners.pop()
        if free(*c):
            b.set(c[0], fl, c[1], "chest[facing=south,type=single,waterlogged=false]" if c[1] == z0 else "chest[facing=north,type=single,waterlogged=false]",
                  container_nbt("minecraft:chest", loot or [], loot_table=None if loot else "palemeridian:chests/cottage"))
        c = corners.pop()
        if free(*c):
            b.set(c[0], fl, c[1], "crafting_table" if role == "rest" else rng.choice(["barrel[facing=up,open=false]", "bookshelf", "composter[level=0]"]))
        # table + chairs
        tx, tz = cx, cz + 1 if cz + 1 <= z1 else cz
        if free(tx, tz):
            b.set(tx, fl, tz, style.trim + "_fence")
            b.set(tx, fl + 1, tz, "white_carpet")
            if free(tx + 1, tz):
                b.set(tx + 1, fl, tz, stairs(style.trim, "east"))
            if free(tx - 1, tz):
                b.set(tx - 1, fl, tz, stairs(style.trim, "west"))
        c = corners.pop()
        if free(*c):
            b.set(c[0], fl, c[1], "flower_pot" if role == "rest" else "potted_" + rng.choice(["poppy", "dandelion", "fern", "allium", "azure_bluet"]))
    elif role == "shop":
        for x in range(x0 + 1, x1):
            if free(x, z0 + 2):
                b.set(x, fl, z0 + 2, stairs(style.trim, "north", "top"))
        b.set(x0, fl, z0, "barrel[facing=up,open=false]", container_nbt("minecraft:barrel", [], "palemeridian:chests/pantry"))
        b.set(x1, fl, z0, "barrel[facing=up,open=false]", container_nbt("minecraft:barrel", [], "palemeridian:chests/pantry"))


def tree(b: Build, x: int, y: int, z: int, rng: random.Random, kind: str = "oak", height: int = 5) -> None:
    log = f"{kind}_log[axis=y]"
    leaves = f"{kind}_leaves[distance=1,persistent=true,waterlogged=false]"
    for i in range(height):
        b.set(x, y + i, z, log)
    for dy in range(height - 2, height + 2):
        r = 2 if dy < height else 1
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                if abs(dx) + abs(dz) > r + 1 or (dx == 0 and dz == 0 and dy < height):
                    continue
                if rng.random() < 0.9 and b.inside(x + dx, y + dy, z + dz) and b.get(x + dx, y + dy, z + dz) is None:
                    b.set(x + dx, y + dy, z + dz, leaves)


def street_lamp(b: Build, x: int, fl: int, z: int, lit: bool = False) -> tuple:
    b.set(x, fl, z, "stone_brick_wall")
    b.set(x, fl + 1, z, "dark_oak_fence")
    b.set(x, fl + 2, z, "dark_oak_fence")
    b.set(x, fl + 3, z, f"waxed_exposed_copper_bulb[lit={'true' if lit else 'false'},powered=false]")
    b.set(x, fl + 4, z, "stone_brick_slab[type=bottom]")
    return (x, fl + 3, z)


def fence_line(b: Build, pts: list, fl: int, block: str = "spruce_fence") -> None:
    for (x0, z0), (x1, z1) in zip(pts, pts[1:]):
        n = max(abs(x1 - x0), abs(z1 - z0))
        for i in range(n + 1):
            x = x0 + round((x1 - x0) * i / max(1, n))
            z = z0 + round((z1 - z0) * i / max(1, n))
            if b.get(x, fl, z) is None or b.get(x, fl, z).name == "minecraft:air":
                b.set(x, fl, z, block)


def sign(b: Build, x: int, y: int, z: int, facing: str, lines: list, wood: str = "spruce", color: str = "black", glowing: bool = False) -> None:
    b.set(x, y, z, f"{wood}_wall_sign[facing={facing},waterlogged=false]", sign_nbt(lines, kind="minecraft:sign", color=color, glowing=glowing))
