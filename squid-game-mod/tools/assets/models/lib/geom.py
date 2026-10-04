"""Bedrock ``format_version 1.12.0`` geometry builder for GeckoLib 4.9.x.

Authoring space ("file space", exactly what ends up in the JSON):

* units are model pixels (1 px = 1/16 block), y up, origin at the feet centre;
* the model FRONT is -Z (the "north" face of a cube);
* file +X is the entity's LEFT (Bedrock convention: the right arm lives at x < 0).
  GeckoLib negates X when it bakes the model, so this is only a file-format detail;
* cubes are ``origin`` (min corner) + ``size``; box-UV cubes carry ``uv: [u, v]`` and
  use the vanilla skin layout (see :func:`box_faces`); thin decals use per-face UV.

GeckoLib floors the size used for box-UV, so nominal cube sizes are kept integral
here and sub-pixel adjustments are done with ``inflate``.
"""
from __future__ import annotations

import json
import math
from typing import Dict, Iterable, List, Optional, Sequence, Tuple

from . import jsonfmt

Vec3 = Tuple[float, float, float]


def _num(v: float):
    """Trim float noise so the JSON stays readable and deterministic."""
    r = round(float(v), 4)
    if r == int(r):
        return int(r)
    return r


def _vec(v: Sequence[float]) -> List:
    return [_num(x) for x in v]


# ----------------------------------------------------------------------------------
# UV helpers
# ----------------------------------------------------------------------------------
def box_footprint(sx: int, sy: int, sz: int) -> Tuple[int, int]:
    """Texture area (w, h) taken by a box-UV cube."""
    return 2 * (sx + sz), sy + sz


def box_faces(u: int, v: int, sx: int, sy: int, sz: int) -> Dict[str, Tuple[int, int, int, int]]:
    """Pixel rectangles ``(x, y, w, h)`` of the six faces of a box-UV cube.

    Orientation of each rectangle, derived from GeckoLib's ``BakedModelFactory``:

    * ``north`` (front): viewed from the front, left edge = entity's RIGHT side.
    * ``south`` (back): viewed from behind, left edge = entity's LEFT side.
    * ``east``  (entity's right side): left edge = back, right edge = front.
    * ``west``  (entity's left side): left edge = front, right edge = back.
    * ``up`` / ``down``: top edge = back, bottom edge = front, left edge = entity's RIGHT.
    """
    return {
        "east": (u, v + sz, sz, sy),
        "north": (u + sz, v + sz, sx, sy),
        "west": (u + sz + sx, v + sz, sz, sy),
        "south": (u + 2 * sz + sx, v + sz, sx, sy),
        "up": (u + sz, v, sx, sz),
        "down": (u + sz + sx, v, sx, sz),
    }


class UVPacker:
    """Deterministic shelf packer for box-UV footprints and plain rectangles."""

    def __init__(self, width: int, height: int):
        self.w = width
        self.h = height
        self.regions: Dict[str, Tuple[int, int, int, int]] = {}
        self._shelves: List[List[int]] = []  # [y, height, x_cursor]
        self._next_y = 0

    def alloc(self, name: str, w: int, h: int) -> Tuple[int, int]:
        if name in self.regions:
            raise ValueError(f"region {name!r} allocated twice")
        for shelf in self._shelves:
            y, sh, x = shelf
            if h <= sh and x + w <= self.w:
                shelf[2] = x + w
                self.regions[name] = (x, y, w, h)
                return x, y
        if self._next_y + h > self.h:
            raise ValueError(f"texture {self.w}x{self.h} is full while placing {name!r} ({w}x{h})")
        y = self._next_y
        self._shelves.append([y, h, w])
        self._next_y += h
        self.regions[name] = (0, y, w, h)
        return 0, y

    def box(self, name: str, sx: int, sy: int, sz: int) -> Tuple[int, int]:
        w, h = box_footprint(sx, sy, sz)
        return self.alloc(name, w, h)

    def used_fraction(self) -> float:
        return sum(w * h for (_, _, w, h) in self.regions.values()) / float(self.w * self.h)


# ----------------------------------------------------------------------------------
# Cubes / bones / geometry
# ----------------------------------------------------------------------------------
class Cube:
    """One cuboid.

    ``uv`` is either ``(u, v)`` (box UV, ``size`` must then be integral) or a dict
    ``{face: (u, v, w, h)}`` with faces among north/south/east/west/up/down (per-face UV,
    only the listed faces are emitted - used for flat decals).
    """

    def __init__(self, origin: Sequence[float], size: Sequence[float], uv,
                 inflate: Optional[float] = None, mirror: bool = False,
                 pivot: Optional[Sequence[float]] = None,
                 rotation: Optional[Sequence[float]] = None, name: str = ""):
        self.origin = tuple(float(x) for x in origin)
        self.size = tuple(float(x) for x in size)
        self.uv = uv
        self.inflate = inflate
        self.mirror = mirror
        self.pivot = None if pivot is None else tuple(float(x) for x in pivot)
        self.rotation = None if rotation is None else tuple(float(x) for x in rotation)
        self.name = name
        if not isinstance(uv, dict):
            for s in self.size:
                if abs(s - round(s)) > 1e-9:
                    raise ValueError(f"box-UV cube {name!r} needs integral size, got {self.size}")

    def to_json(self) -> dict:
        d: dict = {"origin": _vec(self.origin), "size": _vec(self.size)}
        if self.pivot is not None:
            d["pivot"] = _vec(self.pivot)
        if self.rotation is not None:
            d["rotation"] = _vec(self.rotation)
        if self.inflate:
            d["inflate"] = _num(self.inflate)
        if self.mirror:
            d["mirror"] = True
        if isinstance(self.uv, dict):
            faces = {}
            for face in ("north", "east", "south", "west", "up", "down"):
                if face in self.uv:
                    fu, fv, fw, fh = self.uv[face]
                    faces[face] = {"uv": [_num(fu), _num(fv)], "uv_size": [_num(fw), _num(fh)]}
            d["uv"] = faces
        else:
            d["uv"] = [int(self.uv[0]), int(self.uv[1])]
        return d


class Bone:
    def __init__(self, name: str, parent: Optional[str], pivot: Sequence[float]):
        self.name = name
        self.parent = parent
        self.pivot = tuple(float(x) for x in pivot)
        self.cubes: List[Cube] = []

    def add(self, cube: Cube) -> Cube:
        self.cubes.append(cube)
        return cube

    def to_json(self) -> dict:
        d: dict = {"name": self.name}
        if self.parent:
            d["parent"] = self.parent
        d["pivot"] = _vec(self.pivot)
        if self.cubes:
            d["cubes"] = [c.to_json() for c in self.cubes]
        return d


class Geometry:
    def __init__(self, identifier: str, tex_w: int, tex_h: int,
                 visible_bounds=(4.0, 4.0, (0.0, 1.0, 0.0))):
        self.identifier = identifier
        self.tex_w = tex_w
        self.tex_h = tex_h
        self.bounds = visible_bounds
        self.bones: Dict[str, Bone] = {}

    def bone(self, name: str, parent: Optional[str] = None, pivot: Sequence[float] = (0, 0, 0)) -> Bone:
        if name in self.bones:
            raise ValueError(f"duplicate bone {name!r}")
        if parent is not None and parent not in self.bones:
            raise ValueError(f"bone {name!r}: unknown parent {parent!r} (define parents first)")
        b = Bone(name, parent, pivot)
        self.bones[name] = b
        return b

    def descendants(self, name: str) -> List[str]:
        out = []
        for n, b in self.bones.items():
            p = b.parent
            while p:
                if p == name:
                    out.append(n)
                    break
                p = self.bones[p].parent
        return out

    def to_json(self) -> dict:
        bw, bh, off = self.bounds
        return {
            "format_version": "1.12.0",
            "minecraft:geometry": [{
                "description": {
                    "identifier": self.identifier,
                    "texture_width": self.tex_w,
                    "texture_height": self.tex_h,
                    "visible_bounds_width": _num(bw),
                    "visible_bounds_height": _num(bh),
                    "visible_bounds_offset": _vec(off),
                },
                "bones": [b.to_json() for b in self.bones.values()],
            }],
        }

    def write(self, path: str) -> None:
        jsonfmt.write(path, self.to_json())


def mirror_x(v: Sequence[float]) -> Vec3:
    """Mirror a file-space point across the sagittal plane (left <-> right)."""
    return (-v[0], v[1], v[2])


def mirrored_origin(origin: Sequence[float], size: Sequence[float]) -> Vec3:
    """Origin (min corner) of the mirror image of a cube."""
    return (-(origin[0] + size[0]), origin[1], origin[2])
