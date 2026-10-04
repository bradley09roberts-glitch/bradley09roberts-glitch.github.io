"""Small model-builder DSL: bones, cubes with per-face UV into a packed texture atlas.

Texture regions are declared first (name, w, h), packed deterministically, then painted;
cube faces reference regions symbolically and are resolved to pixel UVs on export.

Face spec forms (for ``cube(..., faces={...})``):
    "region"                      whole region
    ("region", x, y, w, h)        sub-rectangle of the region (pixels, relative to it)
    ("region", x, y, w, h, rot)   same, with a uv_rotation (0/90/180/270)
    None / missing                face is not emitted (hidden face, saves texture + quads)
"""
from __future__ import annotations

import json
from dataclasses import dataclass

import numpy as np

FACES = ("north", "south", "east", "west", "up", "down")


@dataclass
class Region:
    name: str
    w: int
    h: int
    x: int = -1
    y: int = -1


class Atlas:
    def __init__(self, width=256, height=256, gap=1):
        self.w, self.h, self.gap = width, height, gap
        self.regions: dict[str, Region] = {}
        self._order: list[str] = []
        self.pix = np.zeros((height, width, 4), dtype=np.float64)   # straight alpha, 0..1
        self.glow = np.zeros((height, width), dtype=bool)
        self.packed = False

    def add(self, name: str, w: int, h: int) -> Region:
        if name in self.regions:
            raise KeyError("duplicate region " + name)
        r = Region(name, int(w), int(h))
        self.regions[name] = r
        self._order.append(name)
        return r

    def pack(self):
        """Shelf packer, tallest first; deterministic (ties broken by declaration order)."""
        idx = {n: i for i, n in enumerate(self._order)}
        regs = sorted(self.regions.values(), key=lambda r: (-r.h, -r.w, idx[r.name]))
        x = y = shelf_h = 0
        for r in regs:
            if x + r.w > self.w:
                x = 0
                y += shelf_h + self.gap
                shelf_h = 0
            if r.w > self.w:
                raise ValueError("region too wide: " + r.name)
            r.x, r.y = x, y
            x += r.w + self.gap
            shelf_h = max(shelf_h, r.h)
        used = y + shelf_h
        if used > self.h:
            raise ValueError("atlas overflow: needs %d rows of %d" % (used, self.h))
        self.used_rows = used
        self.packed = True

    def view(self, name: str) -> np.ndarray:
        r = self.regions[name]
        return self.pix[r.y:r.y + r.h, r.x:r.x + r.w]

    def mark_glow(self, name: str, mask: np.ndarray | None = None):
        """Mark texels of a region as emissive (all opaque texels, or those where mask is True)."""
        r = self.regions[name]
        sub = self.glow[r.y:r.y + r.h, r.x:r.x + r.w]
        opaque = self.pix[r.y:r.y + r.h, r.x:r.x + r.w, 3] > 0.5
        sub |= (opaque if mask is None else (opaque & mask))

    def to_rgba8(self) -> np.ndarray:
        a = np.clip(self.pix, 0, 1)
        out = np.zeros(a.shape, dtype=np.uint8)
        out[..., :3] = (a[..., :3] * 255 + 0.5).astype(np.uint8)
        out[..., 3] = np.where(a[..., 3] >= 0.5, 255, 0).astype(np.uint8)
        out[out[..., 3] == 0] = 0     # fully transparent texels are (0,0,0,0)
        return out

    def glowmask_rgba8(self) -> np.ndarray:
        """GeckoLib: any texel with non-zero ABGR int is a glow marker; alpha is the glow alpha.
        Everything else must be exactly (0,0,0,0)."""
        base = self.to_rgba8()
        out = np.zeros_like(base)
        m = self.glow & (base[..., 3] > 0)
        out[m] = base[m]
        out[..., 3] = np.where(m, 255, 0)
        return out


def _num(v):
    f = round(float(v), 4)
    return int(f) if f == int(f) else f


class ModelBuilder:
    def __init__(self, identifier: str, atlas: Atlas, visible=(4.0, 10.0, (0, 4.5, 0))):
        self.identifier = identifier
        self.atlas = atlas
        self.bones: list[dict] = []
        self._by_name: dict[str, dict] = {}
        self.visible = visible

    # ------------------------------------------------------------------ bones / cubes
    def bone(self, name, parent=None, pivot=(0, 0, 0), rotation=None, **extra):
        if name in self._by_name:
            raise KeyError("duplicate bone " + name)
        b = {"name": name, "_parent": parent, "_pivot": tuple(pivot), "_rot": tuple(rotation) if rotation else None,
             "_cubes": [], "_extra": extra}
        self.bones.append(b)
        self._by_name[name] = b
        return name

    def cube(self, bone, origin, size, faces, rotation=None, pivot=None, inflate=None, tag=None):
        c = {"origin": tuple(origin), "size": tuple(size), "faces": dict(faces),
             "rotation": tuple(rotation) if rotation else None,
             "pivot": tuple(pivot) if pivot else None, "inflate": inflate, "tag": tag}
        self._by_name[bone]["_cubes"].append(c)
        return c

    def box(self, bone, origin, size, all=None, sides=None, front=None, back=None, left=None, right=None,
            top=None, bottom=None, **kw):
        """Convenience wrapper around cube().

        ``all`` is the default for every face, ``sides`` overrides north/south/east/west, the named
        faces override individually (front=north, back=south, left=west = the entity's left = JSON +x,
        right=east = the entity's right).  Pass ``False`` to omit a face (hidden / never visible).
        """
        faces = {f: all for f in FACES}
        if sides is not None:
            for f in ("north", "south", "east", "west"):
                faces[f] = sides
        for f, v in (("north", front), ("south", back), ("west", left), ("east", right), ("up", top), ("down", bottom)):
            if v is not None:
                faces[f] = v
        for f in FACES:
            if faces[f] is False:
                faces[f] = None
        return self.cube(bone, origin, size, faces, **kw)

    # ------------------------------------------------------------------ export
    def _resolve(self, spec):
        if spec is None:
            return None
        if isinstance(spec, str):
            r = self.atlas.regions[spec]
            return {"uv": [r.x, r.y], "uv_size": [r.w, r.h]}
        name, sx, sy, sw, sh, *rest = spec
        r = self.atlas.regions[name]
        if sx < -1e-9 or sy < -1e-9 or sx + sw > r.w + 1e-9 or sy + sh > r.h + 1e-9:
            raise ValueError("sub-rect %r outside region %s (%dx%d)" % (spec, name, r.w, r.h))
        d = {"uv": [_num(r.x + sx), _num(r.y + sy)], "uv_size": [_num(sw), _num(sh)]}
        if rest and rest[0]:
            d["uv_rotation"] = int(rest[0])
        return d

    def to_json(self) -> dict:
        assert self.atlas.packed, "pack the atlas before exporting"
        bones = []
        for b in self.bones:
            jb = {"name": b["name"]}
            if b["_parent"]:
                jb["parent"] = b["_parent"]
            jb["pivot"] = [_num(v) for v in b["_pivot"]]
            if b["_rot"]:
                jb["rotation"] = [_num(v) for v in b["_rot"]]
            jb.update(b["_extra"])
            cubes = []
            for c in b["_cubes"]:
                jc = {"origin": [_num(v) for v in c["origin"]], "size": [_num(v) for v in c["size"]]}
                if c["pivot"] is not None:
                    jc["pivot"] = [_num(v) for v in c["pivot"]]
                if c["rotation"] is not None:
                    jc["rotation"] = [_num(v) for v in c["rotation"]]
                if c["inflate"]:
                    jc["inflate"] = _num(c["inflate"])
                uv = {}
                for f in FACES:
                    r = self._resolve(c["faces"].get(f))
                    if r is not None:
                        uv[f] = r
                jc["uv"] = uv
                cubes.append(jc)
            if cubes:
                jb["cubes"] = cubes
            bones.append(jb)
        w, h, off = self.visible
        return {
            "format_version": "1.12.0",
            "minecraft:geometry": [{
                "description": {
                    "identifier": self.identifier,
                    "texture_width": self.atlas.w,
                    "texture_height": self.atlas.h,
                    "visible_bounds_width": w,
                    "visible_bounds_height": h,
                    "visible_bounds_offset": list(off),
                },
                "bones": bones,
            }],
        }


def dump_json(obj, path, indent=None):
    with open(path, "w", encoding="utf-8", newline="\n") as fh:
        json.dump(obj, fh, indent=indent, separators=None if indent else (",", ":"))
        fh.write("\n")
