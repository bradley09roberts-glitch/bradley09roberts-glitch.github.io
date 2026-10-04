"""ModelBuilder: bones + cubes + automatic UV allocation in one place."""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import Dict, List, Optional, Sequence, Tuple

from .geom import Bone, Cube, Geometry, UVPacker
from .paint import BoxPaint, Canvas, Face


@dataclass
class Part:
    name: str
    bone: str
    kind: str                      # "box" | "rect"
    size: Tuple[float, float, float]
    origin: Tuple[float, float, float]
    kw: dict = field(default_factory=dict)
    rect: Tuple[int, int] = (0, 0)  # for decals: texture size in px
    uv: Tuple[int, int] = (0, 0)
    face: str = "north"
    cube: Optional[Cube] = None


class ModelBuilder:
    def __init__(self, identifier: str, tex_w: int, tex_h: int, bounds=(3.0, 3.0, (0.0, 1.0, 0.0))):
        self.geo = Geometry(identifier, tex_w, tex_h, bounds)
        self.packer = UVPacker(tex_w, tex_h)
        self.parts: Dict[str, Part] = {}
        self._order: List[Part] = []

    # -- structure -------------------------------------------------------------------
    def bone(self, name: str, parent: Optional[str] = None, pivot: Sequence[float] = (0, 0, 0)) -> Bone:
        return self.geo.bone(name, parent, pivot)

    def box(self, bone: str, name: str, origin: Sequence[float], size: Sequence[int],
            inflate: Optional[float] = None, pivot=None, rotation=None, mirror: bool = False) -> Part:
        if name in self.parts:
            raise ValueError("duplicate part " + name)
        p = Part(name, bone, "box", tuple(size), tuple(origin),
                 dict(inflate=inflate, pivot=pivot, rotation=rotation, mirror=mirror))
        self.parts[name] = p
        self._order.append(p)
        return p

    def decal(self, bone: str, name: str, origin: Sequence[float], plane: Tuple[float, float],
              tex_size: Tuple[int, int], face: str = "north", pivot=None, rotation=None) -> Part:
        """A flat (zero-thickness) quad facing ``face`` (north = toward the front, -Z).

        ``plane`` is the quad's extent in model px (x,y for north/south); ``tex_size`` is the
        texture area it samples."""
        if name in self.parts:
            raise ValueError("duplicate part " + name)
        if face in ("north", "south"):
            size = (plane[0], plane[1], 0.0)
        else:
            raise ValueError("only north/south decals supported")
        p = Part(name, bone, "rect", size, tuple(origin), dict(pivot=pivot, rotation=rotation),
                 rect=tex_size, face=face)
        self.parts[name] = p
        self._order.append(p)
        return p

    # -- UV allocation & cube creation ---------------------------------------------------
    def finalize(self) -> None:
        def area_key(p: Part):
            if p.kind == "box":
                sx, sy, sz = (int(round(s)) for s in p.size)
                w, h = 2 * (sx + sz), sy + sz
            else:
                w, h = p.rect
            return (-h, -w, p.name)
        for p in sorted(self._order, key=area_key):
            if p.kind == "box":
                sx, sy, sz = (int(round(s)) for s in p.size)
                p.uv = self.packer.box(p.name, sx, sy, sz)
            else:
                p.uv = self.packer.alloc(p.name, *p.rect)
        for p in self._order:
            bone = self.geo.bones[p.bone]
            if p.kind == "box":
                c = Cube(p.origin, p.size, p.uv, name=p.name, **p.kw)
            else:
                u, v = p.uv
                c = Cube(p.origin, p.size, {p.face: (u, v, p.rect[0], p.rect[1])}, name=p.name, **p.kw)
            p.cube = c
            bone.add(c)

    # -- painting access -----------------------------------------------------------------------
    def paint_box(self, cv: Canvas, name: str) -> BoxPaint:
        p = self.parts[name]
        assert p.kind == "box"
        sx, sy, sz = (int(round(s)) for s in p.size)
        return BoxPaint(cv, p.uv[0], p.uv[1], sx, sy, sz, name)

    def paint_rect(self, cv: Canvas, name: str) -> Face:
        p = self.parts[name]
        assert p.kind == "rect"
        return Face(cv, p.uv[0], p.uv[1], p.rect[0], p.rect[1], name)
