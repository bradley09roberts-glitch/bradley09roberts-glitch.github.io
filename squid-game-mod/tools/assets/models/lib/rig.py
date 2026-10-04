"""Faithful Python port of how GeckoLib 4.9.3 bakes, animates and transforms a model.

Everything here is derived from reading the GeckoLib sources:

* ``loading/object/BakedModelFactory`` : bone pivots ``(-x, y, z)``, bone/cube rotations
  ``(-rx, -ry, +rz)`` radians, cube origin ``(-(ox+sx), oy, oz)``, vertex order, UV rules,
  ``mirror`` handling, flooring of box-UV sizes.
* ``loading/json/typeadapter/BakedAnimationsAdapter`` : keyframe stacks - rotation
  constants are stored as ``(-x, -y, +z)`` radians; the easing of a key governs the
  segment that arrives at it; first key may sit after t=0 (constant before it).
* ``animation/AnimationController`` + ``EasingType`` : key lookup and easing maths.
* ``renderer/GeoEntityRenderer.renderRecursively`` + ``util/RenderUtil`` : per-bone matrix
  ``translate(-posX, posY, posZ) * translate(pivot) * Rz*Ry*Rx * scale * translate(-pivot)``
  and per-cube ``translate(cpivot) * Rz*Ry*Rx * translate(-cpivot)``.

Baked model space (what this module works in, in PIXELS = 1/16 block):
    +x = the entity's RIGHT, +y = up, +z = BACKWARD (front is -z).
"""
from __future__ import annotations

import json
import math
from dataclasses import dataclass, field
from typing import Dict, List, Optional, Sequence, Tuple

import numpy as np

DEG = math.pi / 180.0


# ----------------------------------------------------------------------------------
# small matrix helpers
# ----------------------------------------------------------------------------------
def T(x: float, y: float, z: float) -> np.ndarray:
    m = np.eye(4)
    m[0, 3], m[1, 3], m[2, 3] = x, y, z
    return m


def S(x: float, y: float, z: float) -> np.ndarray:
    m = np.eye(4)
    m[0, 0], m[1, 1], m[2, 2] = x, y, z
    return m


def Rx(a: float) -> np.ndarray:
    c, s = math.cos(a), math.sin(a)
    m = np.eye(4)
    m[1, 1], m[1, 2], m[2, 1], m[2, 2] = c, -s, s, c
    return m


def Ry(a: float) -> np.ndarray:
    c, s = math.cos(a), math.sin(a)
    m = np.eye(4)
    m[0, 0], m[0, 2], m[2, 0], m[2, 2] = c, s, -s, c
    return m


def Rz(a: float) -> np.ndarray:
    c, s = math.cos(a), math.sin(a)
    m = np.eye(4)
    m[0, 0], m[0, 1], m[1, 0], m[1, 1] = c, -s, s, c
    return m


def rot_zyx(rx: float, ry: float, rz: float) -> np.ndarray:
    """JOML ``Quaternionf.rotationZYX(z, y, x)`` == Rz * Ry * Rx (X applied first)."""
    return Rz(rz) @ Ry(ry) @ Rx(rx)


# ----------------------------------------------------------------------------------
# Baked model
# ----------------------------------------------------------------------------------
@dataclass
class Quad:
    verts: np.ndarray  # (4,3) baked px
    uvs: np.ndarray    # (4,2) normalised
    normal: np.ndarray  # (3,)
    face: str


@dataclass
class BCube:
    quads: List[Quad]
    pivot: np.ndarray      # baked px (x already negated)
    rot: Tuple[float, float, float]  # radians, baked (-rx,-ry,rz)
    size: Tuple[float, float, float]
    has_rot: bool


@dataclass
class RigBone:
    name: str
    parent: Optional[str]
    pivot: np.ndarray      # baked px
    rot0: np.ndarray       # baked rad
    cubes: List[BCube] = field(default_factory=list)
    children: List[str] = field(default_factory=list)


_NORMALS = {
    "north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0),
    "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0),
}
_DIR_ORDER = ["west", "east", "north", "south", "up", "down"]  # BakedModelFactory.buildQuads order


def _vertex_set(x0, x1, y0, y1, z0, z1):
    return {
        "bottomLeftBack": (x0, y0, z0), "bottomRightBack": (x0, y0, z1),
        "topLeftBack": (x0, y1, z0), "topRightBack": (x0, y1, z1),
        "topLeftFront": (x1, y1, z0), "topRightFront": (x1, y1, z1),
        "bottomLeftFront": (x1, y0, z0), "bottomRightFront": (x1, y0, z1),
    }


def _quad_vertices(vs, direction: str, box_uv: bool, mirror: bool):
    def q(*names):
        return [vs[n] for n in names]
    west = q("topRightBack", "topLeftBack", "bottomLeftBack", "bottomRightBack")
    east = q("topLeftFront", "topRightFront", "bottomRightFront", "bottomLeftFront")
    north = q("topLeftBack", "topLeftFront", "bottomLeftFront", "bottomLeftBack")
    south = q("topRightFront", "topRightBack", "bottomRightBack", "bottomRightFront")
    up = q("topRightBack", "topRightFront", "topLeftFront", "topLeftBack")
    down = q("bottomLeftBack", "bottomLeftFront", "bottomRightFront", "bottomRightBack")
    if direction == "west":
        return east if mirror else west
    if direction == "east":
        return west if mirror else east
    if direction == "north":
        return north
    if direction == "south":
        return south
    if direction == "up":
        return down if (mirror and not box_uv) else up
    return up if (mirror and not box_uv) else down


def _rotate_uvs(rotation: int, u, v, uw, vh):
    rotation = (rotation % 360) // 90
    if rotation == 0:
        return [u, v, uw, v, uw, vh, u, vh]
    if rotation == 1:
        return [uw, v, uw, vh, u, vh, u, v]
    if rotation == 2:
        return [uw, vh, u, vh, u, v, uw, v]
    return [u, vh, u, v, uw, v, uw, vh]


def _build_quad(verts, uv, uv_size, uv_rot, tw, th, mirror, direction) -> Quad:
    u, v = uv
    uw = (u + uv_size[0]) / tw
    vh = (v + uv_size[1]) / th
    u = u / tw
    v = v / th
    normal = np.array(_NORMALS[direction], dtype=float)
    if not mirror:
        u, uw = uw, u
    else:
        normal = normal * np.array([-1.0, 1.0, 1.0])
    uvs = _rotate_uvs(uv_rot, u, v, uw, vh)
    return Quad(np.array(verts, dtype=float), np.array(uvs, dtype=float).reshape(4, 2), normal, direction)


def bake_cube(cj: dict, tw: float, th: float, bone_inflate: float = 0.0) -> BCube:
    size = np.array(cj["size"], dtype=float)
    origin = np.array(cj["origin"], dtype=float)
    mirror = bool(cj.get("mirror", False))
    inflate = float(cj["inflate"]) if cj.get("inflate") is not None else bone_inflate
    pivot = np.array(cj.get("pivot", [0, 0, 0]), dtype=float) * np.array([-1, 1, 1])
    r = cj.get("rotation", [0, 0, 0])
    rot = (-r[0] * DEG, -r[1] * DEG, r[2] * DEG)
    x0 = -(origin[0] + size[0]) - inflate
    x1 = -origin[0] + inflate
    y0 = origin[1] - inflate
    y1 = origin[1] + size[1] + inflate
    z0 = origin[2] - inflate
    z1 = origin[2] + size[2] + inflate
    vs = _vertex_set(x0, x1, y0, y1, z0, z1)
    uvj = cj["uv"]
    quads: List[Quad] = []
    if isinstance(uvj, list):
        u, v = uvj
        sx, sy, sz = math.floor(size[0]), math.floor(size[1]), math.floor(size[2])
        rects = {
            "west": ((u + sz + sx, v + sz), (sz, sy)),
            "east": ((u, v + sz), (sz, sy)),
            "north": ((u + sz, v + sz), (sx, sy)),
            "south": ((u + sz + sx + sz, v + sz), (sx, sy)),
            "up": ((u + sz, v), (sx, sz)),
            "down": ((u + sz + sx, v + sz), (sx, -sz)),
        }
        for d in _DIR_ORDER:
            (uu, vv), sz2 = rects[d]
            quads.append(_build_quad(_quad_vertices(vs, d, True, mirror), (uu, vv), sz2, 0, tw, th, mirror, d))
    else:
        for d in _DIR_ORDER:
            f = uvj.get(d)
            if f is None:
                continue
            quads.append(_build_quad(_quad_vertices(vs, d, False, mirror), f["uv"], f["uv_size"],
                                     int(f.get("uv_rotation", 0)), tw, th, mirror, d))
    has_rot = any(abs(a) > 1e-12 for a in rot)
    return BCube(quads, pivot, rot, tuple(size), has_rot)


class Rig:
    """Baked model + the GeckoLib transform stack."""

    def __init__(self, geo_json: dict):
        g = geo_json["minecraft:geometry"][0]
        desc = g["description"]
        self.identifier = desc.get("identifier", "")
        self.tw = float(desc["texture_width"])
        self.th = float(desc["texture_height"])
        self.bones: Dict[str, RigBone] = {}
        self.order: List[str] = []
        for b in g["bones"]:
            rot = b.get("rotation", [0, 0, 0])
            piv = b["pivot"]
            rb = RigBone(b["name"], b.get("parent"),
                         np.array([-piv[0], piv[1], piv[2]], dtype=float),
                         np.array([-rot[0] * DEG, -rot[1] * DEG, rot[2] * DEG]))
            infl = float(b["inflate"]) if b.get("inflate") is not None else 0.0
            for c in b.get("cubes", []):
                rb.cubes.append(bake_cube(c, self.tw, self.th, infl))
            if rb.name in self.bones:
                raise ValueError("duplicate bone " + rb.name)
            self.bones[rb.name] = rb
        for rb in self.bones.values():
            if rb.parent is not None:
                if rb.parent not in self.bones:
                    raise ValueError(f"bone {rb.name}: unknown parent {rb.parent}")
                self.bones[rb.parent].children.append(rb.name)
        # parent-first order
        seen = set()

        def visit(n):
            if n in seen:
                return
            p = self.bones[n].parent
            if p:
                visit(p)
            seen.add(n)
            self.order.append(n)
        for n in self.bones:
            visit(n)

    @classmethod
    def load(cls, path: str) -> "Rig":
        with open(path) as f:
            return cls(json.load(f))

    # -- transforms ------------------------------------------------------------------
    def local_matrix(self, n: str, st: Optional[dict] = None) -> np.ndarray:
        """Bone-local part of the transform stack (without the parent matrix)."""
        b = self.bones[n]
        st = st or {}
        rot = b.rot0 + np.array(st["rot"]) if "rot" in st else b.rot0
        pos = st.get("pos", (0.0, 0.0, 0.0))
        sc = st.get("scale", (1.0, 1.0, 1.0))
        return (T(-pos[0], pos[1], pos[2]) @ T(*b.pivot) @ rot_zyx(rot[0], rot[1], rot[2])
                @ S(*sc) @ T(*(-b.pivot)))

    def path_to(self, name: str, ancestor: str) -> List[str]:
        """Bones from ``ancestor`` (inclusive) down to ``name`` (inclusive)."""
        path = [name]
        while path[-1] != ancestor:
            p = self.bones[path[-1]].parent
            if p is None:
                raise ValueError(f"{ancestor} is not an ancestor of {name}")
            path.append(p)
        return path[::-1]

    def world_matrices(self, state: Optional[dict] = None) -> Dict[str, np.ndarray]:
        """state: bone -> {"rot": (x,y,z) baked rad ANIMATION value, "pos": file px, "scale": ...}.

        Returns the matrix applied to the bone's (absolute, baked) cube vertices."""
        state = state or {}
        mats: Dict[str, np.ndarray] = {}
        for n in self.order:
            b = self.bones[n]
            st = state.get(n, {})
            rot = b.rot0.copy()
            if "rot" in st:
                rot = rot + np.array(st["rot"])
            pos = st.get("pos", (0.0, 0.0, 0.0))
            sc = st.get("scale", (1.0, 1.0, 1.0))
            m = (T(-pos[0], pos[1], pos[2])
                 @ T(*b.pivot)
                 @ rot_zyx(rot[0], rot[1], rot[2])
                 @ S(*sc)
                 @ T(*(-b.pivot)))
            if b.parent:
                m = mats[b.parent] @ m
            mats[n] = m
        return mats

    def cube_matrix(self, cube: BCube) -> np.ndarray:
        if not cube.has_rot:
            return np.eye(4)
        return T(*cube.pivot) @ rot_zyx(*cube.rot) @ T(*(-cube.pivot))

    def point(self, bone: str, file_pt: Sequence[float], mats: Dict[str, np.ndarray]) -> np.ndarray:
        """Baked-space position of a point given in FILE-space bind-pose coordinates, riding ``bone``."""
        p = np.array([-file_pt[0], file_pt[1], file_pt[2], 1.0])
        return (mats[bone] @ p)[:3]

    def descendants(self, name: str) -> List[str]:
        out = []
        stack = list(self.bones[name].children)
        while stack:
            n = stack.pop()
            out.append(n)
            stack.extend(self.bones[n].children)
        return out

    def vertices_world(self, mats: Dict[str, np.ndarray], hidden=(), skip=()):
        """Yield (bone, cube, (Q,4,3) world vertices) for visible cubes."""
        hid = set(hidden)
        for h in list(hid):
            hid.update(self.descendants(h))
        for n in self.order:
            if n in hid or n in skip:
                continue
            b = self.bones[n]
            for c in b.cubes:
                m = mats[n] @ self.cube_matrix(c)
                v = np.concatenate([q.verts[None] for q in c.quads], axis=0)  # (Q,4,3)
                vh = np.concatenate([v, np.ones((v.shape[0], 4, 1))], axis=2)
                yield n, c, (vh @ m.T)[..., :3]


# ----------------------------------------------------------------------------------
# Easing (port of EasingType)
# ----------------------------------------------------------------------------------
def _sine(n): return 1 - math.cos(n * math.pi / 2)
def _quad(n): return n * n
def _cubic(n): return n * n * n
def _pow(k): return lambda t: t ** k
def _exp(n): return 2 ** (10 * (n - 1))
def _circle(n): return 1 - math.sqrt(max(0.0, 1 - n * n))


def _back(n):
    n2 = 1.70158 if n is None else n * 1.70158
    return lambda t: t * t * ((n2 + 1) * t - n2)


def _elastic(n):
    n2 = 1.0 if n is None else n
    return lambda t: 1 - math.cos(t * math.pi / 2) ** 3 * math.cos(t * n2 * math.pi)


def _bounce(n):
    n2 = 0.5 if n is None else n
    one = lambda x: 121 / 16 * x * x
    two = lambda x: 121 / 4 * n2 * (x - 6 / 11) ** 2 + 1 - n2
    three = lambda x: 121 * n2 * n2 * (x - 9 / 11) ** 2 + 1 - n2 * n2
    four = lambda x: 484 * n2 ** 3 * (x - 10.5 / 11) ** 2 + 1 - n2 ** 3
    return lambda t: min(min(one(t), two(t)), min(three(t), four(t)))


def _ease_in(f): return f
def _ease_out(f): return lambda t: 1 - f(1 - t)


def _ease_in_out(f):
    def g(t):
        if t < 0.5:
            return f(t * 2) / 2
        return 1 - f((1 - t) * 2) / 2
    return g


def _step(n):
    n2 = 2 if n is None else n
    steps = int(n2)

    def g(t):
        if t < 0:
            return 0.0
        step_len = 1 / steps
        res = (steps - 1) * step_len
        if t > res:
            return res
        left, right = 0, steps - 1
        while right - left != 1:
            test = left + (right - left) // 2
            if t >= test * step_len:
                left = test
            else:
                right = test
        return left * step_len
    return g


def easing_transformer(name: str, arg: Optional[float]):
    n = name.lower()
    if n in ("linear", "none"):
        return _ease_in(lambda t: t)
    if n == "step":
        return _ease_in(_step(arg))
    base = {
        "sine": _sine, "quad": _quad, "cubic": _cubic, "quart": _pow(4), "quint": _pow(5),
        "expo": _exp, "circ": _circle,
    }
    fam = {
        "back": _back, "elastic": _elastic, "bounce": _bounce,
    }
    for mode, wrap in (("easein", _ease_in), ("easeout", _ease_out), ("easeinout", _ease_in_out)):
        if n.startswith(mode):
            key = n[len(mode):]
            if key in base:
                return wrap(base[key])
            if key in fam:
                return wrap(fam[key](arg))
    # catmullrom handled by caller; unknown -> linear (GeckoLib falls back to LINEAR)
    return lambda t: t


def _catmull_n(n):
    return 0.5 * (2 * (n + 1) + 2 + (2 * n - 5 * (n + 1) + 4 * (n + 2) - (n + 3))
                  + (3 * (n + 1) - n - 3 * (n + 2) + (n + 3)))


def _spline(d, p0, p1, p2, p3):
    return 0.5 * (2 * p1 + (p2 - p0) * d + (2 * p0 - 5 * p1 + 4 * p2 - p3) * d * d
                  + (3 * p1 - p0 - 3 * p2 + p3) * d * d * d)


# ----------------------------------------------------------------------------------
# Animation clip (port of BakedAnimationsAdapter + AnimationController sampling)
# ----------------------------------------------------------------------------------
@dataclass
class KF:
    length: float          # ticks
    start: float
    end: float
    easing: str
    args: List[float]


class Clip:
    def __init__(self, name: str, aj: dict):
        self.name = name
        self.length_ticks = float(aj["animation_length"]) * 20.0 if "animation_length" in aj else None
        loop = aj.get("loop", False)
        if loop is True or loop == "loop":
            self.loop = "loop"
        elif loop == "hold_on_last_frame":
            self.loop = "hold"
        else:
            self.loop = "once"
        self.bones: Dict[str, Dict[str, Tuple[List[KF], List[KF], List[KF]]]] = {}
        for bname, bj in aj.get("bones", {}).items():
            self.bones[bname] = {
                "rot": self._stack(bj.get("rotation"), True),
                "pos": self._stack(bj.get("position"), False),
                "scale": self._stack(bj.get("scale"), False),
            }
        if self.length_ticks is None:
            self.length_ticks = max([sum(k.length for k in st[0]) for ch in self.bones.values()
                                     for st in ch.values() if st[0]] or [1.0])

    @property
    def length(self) -> float:
        return self.length_ticks / 20.0

    @staticmethod
    def _entries(el):
        """Port of BakedAnimationsAdapter.getKeyframes (only the forms this project writes)."""
        if el is None:
            return []
        if isinstance(el, (int, float)):
            return [(0.0, [el, el, el])]
        if isinstance(el, list):
            return [(0.0, el)]
        if isinstance(el, dict):
            if "vector" in el:
                return [(0.0, el)]
            out = []
            for k, v in el.items():
                ts = float(k)
                if ts == 0 and out:
                    raise ValueError("multiple starting keyframes at 0")
                out.append((ts, v))
            return out
        raise ValueError(el)

    def _stack(self, el, is_rot: bool):
        entries = self._entries(el)
        if not entries:
            return ([], [], [])
        frames = ([], [], [])
        prev_t = 0.0
        prev_vals = None
        for (t, v) in entries:
            vec = v["vector"] if isinstance(v, dict) else v
            vals = []
            for i in range(3):
                x = float(vec[i])
                if is_rot:
                    x = math.radians(-x) if i < 2 else math.radians(x)
                vals.append(x)
            easing = "linear"
            args: List[float] = []
            if isinstance(v, dict):
                easing = v.get("easing", "linear")
                args = [float(a) for a in v.get("easingArgs", [])]
            dt = t - prev_t
            for i in range(3):
                frames[i].append(KF(dt * 20.0, vals[i] if prev_vals is None else prev_vals[i], vals[i], easing, list(args)))
            prev_vals = vals
            prev_t = t
        # addSplineArgs
        for i in range(3):
            fl = frames[i]
            for j, f in enumerate(fl):
                if f.easing.lower() == "catmullrom":
                    f.args = [f.start if j < 2 else fl[j - 2].end,
                              f.end if j + 1 >= len(fl) else fl[j + 1].end]
        return frames

    def _sample_axis(self, frames: List[KF], tick: float) -> float:
        total = 0.0
        loc = None
        for f in frames:
            total += f.length
            if total > tick:
                loc = (f, tick - (total - f.length))
                break
        if loc is None:
            loc = (frames[-1], tick)
        f, cur = loc
        if cur >= f.length:
            return f.end
        x = cur / f.length
        if f.easing.lower() == "catmullrom":
            if len(f.args) < 2:
                t = _ease_in_out(_catmull_n)(x)
                return f.start + (f.end - f.start) * t
            return _spline(x, f.args[0], f.start, f.end, f.args[1])
        arg = f.args[0] if f.args else None
        t = easing_transformer(f.easing, arg)(x)
        return f.start + (f.end - f.start) * t

    def sample(self, t_seconds: float, loop: Optional[bool] = None) -> dict:
        """Bone state dict as used by :meth:`Rig.world_matrices` at time ``t_seconds``."""
        tick = t_seconds * 20.0
        L = self.length_ticks
        do_loop = (self.loop == "loop") if loop is None else loop
        if do_loop and L > 0:
            tick = tick % L
        state = {}
        for bname, ch in self.bones.items():
            st = {}
            for key in ("rot", "pos", "scale"):
                fr = ch[key]
                if fr[0]:
                    st[key] = tuple(self._sample_axis(fr[i], tick) for i in range(3))
            if st:
                state[bname] = st
        return state


class ClipLibrary:
    def __init__(self, path_or_json):
        if isinstance(path_or_json, str):
            with open(path_or_json) as f:
                data = json.load(f)
        else:
            data = path_or_json
        self.clips = {name: Clip(name, aj) for name, aj in data["animations"].items()}

    def __getitem__(self, name: str) -> Clip:
        return self.clips[name]

    def __contains__(self, name: str) -> bool:
        return name in self.clips
