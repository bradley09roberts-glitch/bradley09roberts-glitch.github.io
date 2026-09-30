"""Higher-level building helpers on top of structure.Build (Vell architectural style).

Directions: north = -z, south = +z, east = +x, west = -x (Minecraft convention).
"""
from __future__ import annotations

import random

from .structure import Build, parse

DIRS = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}
OPP = {"north": "south", "south": "north", "east": "west", "west": "east"}
CW = {"north": "east", "east": "south", "south": "west", "west": "north"}


def stairs(mat: str, facing: str, half: str = "bottom", shape: str = "straight") -> str:
    return f"{mat}_stairs[facing={facing},half={half},shape={shape}]"


def slab(mat: str, typ: str = "bottom") -> str:
    return f"{mat}_slab[type={typ}]"


def gable_roof(b: Build, x0: int, z0: int, x1: int, z1: int, y: int, mat: str, ridge_axis: str = "x",
               overhang: int = 1, fill_gable: str | None = None, ridge_block: str | None = None) -> int:
    """Pitched roof over the rectangle [x0..x1]x[z0..z1] starting at height y. Returns ridge height."""
    if ridge_axis == "x":
        lo, hi = z0 - overhang, z1 + overhang
        a0, a1 = x0 - overhang, x1 + overhang
    else:
        lo, hi = x0 - overhang, x1 + overhang
        a0, a1 = z0 - overhang, z1 + overhang
    level = 0
    while lo <= hi:
        yy = y + level
        for a in range(a0, a1 + 1):
            if lo == hi:
                cell = slab(mat) if ridge_block is None else ridge_block
                _roof_set(b, ridge_axis, a, lo, yy, cell)
            elif lo + 1 == hi:
                f_lo = "south" if ridge_axis == "x" else "east"
                f_hi = "north" if ridge_axis == "x" else "west"
                _roof_set(b, ridge_axis, a, lo, yy, stairs(mat, f_lo))
                _roof_set(b, ridge_axis, a, hi, yy, stairs(mat, f_hi))
            else:
                f_lo = "south" if ridge_axis == "x" else "east"
                f_hi = "north" if ridge_axis == "x" else "west"
                _roof_set(b, ridge_axis, a, lo, yy, stairs(mat, f_lo))
                _roof_set(b, ridge_axis, a, hi, yy, stairs(mat, f_hi))
        # gable ends (fill below the roof line inside the walls)
        if fill_gable and lo + 1 < hi:
            for c in range(lo + 1, hi):
                if ridge_axis == "x":
                    if z0 <= c <= z1:
                        for gx in (x0, x1):
                            if b.inside(gx, yy, c):
                                b.set(gx, yy, c, fill_gable)
                else:
                    if x0 <= c <= x1:
                        for gz in (z0, z1):
                            if b.inside(c, yy, gz):
                                b.set(c, yy, gz, fill_gable)
        lo += 1
        hi -= 1
        level += 1
    return y + level - 1


def _roof_set(b: Build, axis: str, a: int, c: int, y: int, block: str) -> None:
    x, z = (a, c) if axis == "x" else (c, a)
    if b.inside(x, y, z):
        b.set(x, y, z, block)


def door(b: Build, x: int, y: int, z: int, facing: str, mat: str = "spruce", hinge: str = "left") -> None:
    b.set(x, y, z, f"{mat}_door[facing={facing},half=lower,hinge={hinge},open=false]")
    b.set(x, y + 1, z, f"{mat}_door[facing={facing},half=upper,hinge={hinge},open=false]")


def bed(b: Build, x: int, y: int, z: int, facing: str, color: str = "red") -> None:
    dx, dz = DIRS[facing]
    b.set(x, y, z, f"{color}_bed[facing={facing},part=foot,occupied=false]")
    b.set(x + dx, y, z + dz, f"{color}_bed[facing={facing},part=head,occupied=false]")


def timber_walls(b: Build, x0: int, z0: int, x1: int, z1: int, y0: int, y1: int, frame: str, infill: str,
                 base: str | None = None, post_every: int = 3) -> None:
    """Timber-framed walls: posts at corners and every few blocks, horizontal beams at top, infill between."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            for z in (z0, z1):
                b.set(x, y, z, _wall_block(x - x0, y - y0, y1 - y0, frame, infill, base, post_every, x in (x0, x1)))
        for z in range(z0 + 1, z1):
            for x in (x0, x1):
                b.set(x, y, z, _wall_block(z - z0, y - y0, y1 - y0, frame, infill, base, post_every, False))


def _wall_block(i: int, dy: int, h: int, frame: str, infill: str, base: str | None, every: int, corner: bool) -> str:
    if base and dy == 0:
        return base
    if corner or i % every == 0:
        return f"{frame}[axis=y]" if "log" in frame or "wood" in frame else frame
    if dy == h:
        return f"{frame}[axis=x]" if "log" in frame else frame
    return infill


def window(b: Build, x: int, y: int, z: int, block: str = "glass_pane", height: int = 1) -> None:
    for dy in range(height):
        b.set(x, y + dy, z, block)


def chimney(b: Build, x: int, z: int, y0: int, y1: int, block: str = "bricks", top: str = "campfire[lit=false,signal_fire=false]") -> None:
    for y in range(y0, y1 + 1):
        b.set(x, y, z, block)
    if top:
        b.set(x, y1 + 1, z, top)


def floor(b: Build, x0: int, z0: int, x1: int, z1: int, y: int, block: str) -> None:
    b.fill(x0, y, z0, x1, y, z1, block)


def clear_interior(b: Build, x0: int, z0: int, x1: int, z1: int, y0: int, y1: int) -> None:
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            for y in range(y0, y1 + 1):
                if b.get(x, y, z) is None:
                    b.set(x, y, z, "air")


def scatter(b: Build, rng: random.Random, x0: int, z0: int, x1: int, z1: int, y: int, choices: list, density: float) -> None:
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if rng.random() < density and b.get(x, y, z) is None and b.get(x, y - 1, z) is not None:
                b.set(x, y, z, rng.choice(choices))


# -- connection post-pass ----------------------------------------------------------------------

_FULL_EXCLUDE = ("stairs", "slab", "fence", "wall", "pane", "bars", "door", "trapdoor", "lantern", "torch", "chain",
                 "sign", "banner", "carpet", "bed", "rail", "lever", "button", "pressure_plate", "flower", "sapling",
                 "grass", "fern", "bush", "leaves", "air", "water", "lava", "campfire", "chest", "barrel", "lectern",
                 "bell", "candle", "pot", "head", "skull", "anvil", "rod", "ladder", "vine", "moss_carpet", "snow",
                 "path", "farmland", "grindstone", "bulb", "heart", "dripleaf", "petals", "cake", "table", "stand",
                 "cauldron", "composter", "hopper", "item_frame", "scaffolding", "chiseled_bookshelf", "shelf")


def _is_full(bs) -> bool:
    if bs is None:
        return False
    short = bs.name.split(":")[1]
    return not any(w in short for w in _FULL_EXCLUDE)


def _kind(bs) -> str | None:
    if bs is None:
        return None
    short = bs.name.split(":")[1]
    if short.endswith("_fence"):
        return "fence"
    if short.endswith("_fence_gate"):
        return "gate"
    if short.endswith("_wall"):
        return "wall"
    if short.endswith("_pane") or short == "iron_bars" or short.endswith("_bars"):
        return "pane"
    return None


def autoconnect(b: Build) -> None:
    """Set fence / wall / pane connection properties from neighbours (templates don't update shapes)."""
    for (x, y, z), (bs, tag) in list(b.blocks.items()):
        k = _kind(bs)
        if k in (None, "gate"):
            continue
        conns = {}
        for d, (dx, dz) in DIRS.items():
            nb = b.get(x + dx, y, z + dz)
            nk = _kind(nb)
            connect = False
            if k == "fence":
                connect = nk in ("fence", "gate") and (nb.name == bs.name or "nether" not in nb.name) or _is_full(nb)
            elif k == "wall":
                connect = nk in ("wall", "pane", "gate") or _is_full(nb)
            elif k == "pane":
                connect = nk in ("pane", "wall") or _is_full(nb) or (nb is not None and "glass" in nb.name)
            conns[d] = connect
        props = {}
        if k == "wall":
            above = b.get(x, y + 1, z)
            for d in DIRS:
                props[d] = ("tall" if (above is not None and _is_full(above)) else "low") if conns[d] else "none"
            straight = (conns["north"] and conns["south"] and not conns["east"] and not conns["west"]) or \
                       (conns["east"] and conns["west"] and not conns["north"] and not conns["south"])
            props["up"] = "false" if straight and not (above is not None and above.name != "minecraft:air") else "true"
        else:
            for d in DIRS:
                props[d] = "true" if conns[d] else "false"
        b.blocks[(x, y, z)] = (parse(str(bs.with_(**props))), tag)
