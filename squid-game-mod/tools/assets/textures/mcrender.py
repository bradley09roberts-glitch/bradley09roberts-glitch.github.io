"""A tiny software renderer for Minecraft block models, used for isometric previews.

It mirrors the vanilla 1.21.1 baking rules closely enough to judge textures and multi-cube
models without launching the game:

* parent resolution / texture variable resolution (``#name``),
* ``FaceInfo`` vertex order + ``BlockFaceUV`` per-vertex UV assignment (incl. face ``rotation``),
* element ``rotation`` (origin / axis / angle, right-handed like JOML) and blockstate ``x`` / ``y``
  rotation (``rotateYXZ(-y, -x)`` about the block centre),
* directional face shading (down .5, up 1, north/south .8, west/east .6),
* alpha cutout / translucency, ``cullface`` against opaque neighbours in a scene.

Known simplifications: ``uvlock`` is ignored, no ambient occlusion, animated textures show frame 0.
It also contains a player-model preview (``player_quads``) built from the decompiled
``ModelPart.Cube`` UV rules, used to verify the tracksuit overlay layout.
"""
from __future__ import annotations

import json
import math
from dataclasses import dataclass
from pathlib import Path

import numpy as np
from PIL import Image

DIRS = ["down", "up", "north", "south", "west", "east"]
DIR_VEC = {
    "down": (0, -1, 0), "up": (0, 1, 0), "north": (0, 0, -1),
    "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0),
}
SHADE = {"down": 0.5, "up": 1.0, "north": 0.8, "south": 0.8, "west": 0.6, "east": 0.6}

# vanilla FaceInfo vertex order, as (x, y, z) selectors
_FACE_VERTS = {
    "down": [("min", "min", "max"), ("min", "min", "min"), ("max", "min", "min"), ("max", "min", "max")],
    "up": [("min", "max", "min"), ("min", "max", "max"), ("max", "max", "max"), ("max", "max", "min")],
    "north": [("max", "max", "min"), ("max", "min", "min"), ("min", "min", "min"), ("min", "max", "min")],
    "south": [("min", "max", "max"), ("min", "min", "max"), ("max", "min", "max"), ("max", "max", "max")],
    "west": [("min", "max", "min"), ("min", "min", "min"), ("min", "min", "max"), ("min", "max", "max")],
    "east": [("max", "max", "max"), ("max", "min", "max"), ("max", "min", "min"), ("max", "max", "min")],
}


def _default_uv(face, f, t):
    if face == "down":
        return [f[0], 16 - t[2], t[0], 16 - f[2]]
    if face == "up":
        return [f[0], f[2], t[0], t[2]]
    if face == "north":
        return [16 - t[0], 16 - t[1], 16 - f[0], 16 - f[1]]
    if face == "south":
        return [f[0], 16 - t[1], t[0], 16 - f[1]]
    if face == "west":
        return [f[2], 16 - t[1], t[2], 16 - f[1]]
    return [16 - t[2], 16 - t[1], 16 - f[2], 16 - f[1]]  # east


def _rot_axis(v, axis, deg):
    """Right-handed rotation of vector(s) v (..,3) about a principal axis."""
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    x, y, z = v[..., 0], v[..., 1], v[..., 2]
    if axis == "x":
        return np.stack([x, y * c - z * s, y * s + z * c], axis=-1)
    if axis == "y":
        return np.stack([x * c + z * s, y, -x * s + z * c], axis=-1)
    return np.stack([x * c - y * s, x * s + y * c, z], axis=-1)



# ------------------------------------------------------------------------------ built-in vanilla parents
# Minimal stand-ins for the vanilla parent models this mod's models depend on, so previews and the validator work
# without a vanilla asset dump.  Geometry / UVs follow the vanilla files (cube, slabs, stairs).
def _cube_faces():
    return {d: {"texture": f"#{d}", "cullface": d} for d in DIRS}


def _stair_el(frm, to, faces):
    return {"from": frm, "to": to, "faces": faces}


_SLAB_BOTTOM = _stair_el([0, 0, 0], [16, 8, 16], {
    "down": {"uv": [0, 0, 16, 16], "texture": "#bottom", "cullface": "down"},
    "up": {"uv": [0, 0, 16, 16], "texture": "#top"},
    "north": {"uv": [0, 8, 16, 16], "texture": "#side", "cullface": "north"},
    "south": {"uv": [0, 8, 16, 16], "texture": "#side", "cullface": "south"},
    "west": {"uv": [0, 8, 16, 16], "texture": "#side", "cullface": "west"},
    "east": {"uv": [0, 8, 16, 16], "texture": "#side", "cullface": "east"}})

BUILTIN_MODELS: dict[str, dict] = {
    "block/block": {"display": {}},
    "block/cube": {"parent": "minecraft:block/block", "elements": [
        {"from": [0, 0, 0], "to": [16, 16, 16], "faces": _cube_faces()}]},
    "block/cube_all": {"parent": "minecraft:block/cube", "textures": {
        "particle": "#all", **{d: "#all" for d in DIRS}}},
    "block/cube_column": {"parent": "minecraft:block/cube", "textures": {
        "particle": "#side", "down": "#end", "up": "#end", "north": "#side", "east": "#side", "south": "#side",
        "west": "#side"}},
    "block/cube_bottom_top": {"parent": "minecraft:block/cube", "textures": {
        "particle": "#side", "down": "#bottom", "up": "#top", "north": "#side", "east": "#side", "south": "#side",
        "west": "#side"}},
    "block/slab": {"parent": "minecraft:block/block", "textures": {"particle": "#side"}, "elements": [_SLAB_BOTTOM]},
    "block/slab_top": {"parent": "minecraft:block/block", "textures": {"particle": "#side"}, "elements": [
        _stair_el([0, 8, 0], [16, 16, 16], {
            "down": {"uv": [0, 0, 16, 16], "texture": "#bottom"},
            "up": {"uv": [0, 0, 16, 16], "texture": "#top", "cullface": "up"},
            "north": {"uv": [0, 0, 16, 8], "texture": "#side", "cullface": "north"},
            "south": {"uv": [0, 0, 16, 8], "texture": "#side", "cullface": "south"},
            "west": {"uv": [0, 0, 16, 8], "texture": "#side", "cullface": "west"},
            "east": {"uv": [0, 0, 16, 8], "texture": "#side", "cullface": "east"}})]},
    "block/stairs": {"parent": "minecraft:block/block", "textures": {"particle": "#side"}, "elements": [
        _SLAB_BOTTOM,
        _stair_el([8, 8, 0], [16, 16, 16], {
            "up": {"uv": [8, 0, 16, 16], "texture": "#top", "cullface": "up"},
            "north": {"uv": [0, 0, 8, 8], "texture": "#side", "cullface": "north"},
            "south": {"uv": [8, 0, 16, 8], "texture": "#side", "cullface": "south"},
            "west": {"uv": [0, 0, 16, 8], "texture": "#side"},
            "east": {"uv": [0, 0, 16, 8], "texture": "#side", "cullface": "east"}})]},
    "block/inner_stairs": {"parent": "minecraft:block/block", "textures": {"particle": "#side"}, "elements": [
        _SLAB_BOTTOM,
        _stair_el([8, 8, 0], [16, 16, 16], {
            "up": {"uv": [8, 0, 16, 16], "texture": "#top", "cullface": "up"},
            "north": {"uv": [0, 0, 8, 8], "texture": "#side", "cullface": "north"},
            "south": {"uv": [8, 0, 16, 8], "texture": "#side", "cullface": "south"},
            "west": {"uv": [0, 0, 16, 8], "texture": "#side"},
            "east": {"uv": [0, 0, 16, 8], "texture": "#side", "cullface": "east"}}),
        _stair_el([0, 8, 8], [8, 16, 16], {
            "up": {"uv": [0, 8, 8, 16], "texture": "#top", "cullface": "up"},
            "north": {"uv": [8, 0, 16, 8], "texture": "#side"},
            "south": {"uv": [0, 0, 8, 8], "texture": "#side", "cullface": "south"},
            "west": {"uv": [8, 0, 16, 8], "texture": "#side", "cullface": "west"}})]},
    "block/outer_stairs": {"parent": "minecraft:block/block", "textures": {"particle": "#side"}, "elements": [
        _SLAB_BOTTOM,
        _stair_el([8, 8, 8], [16, 16, 16], {
            "up": {"uv": [8, 8, 16, 16], "texture": "#top", "cullface": "up"},
            "north": {"uv": [0, 0, 8, 8], "texture": "#side"},
            "south": {"uv": [8, 0, 16, 8], "texture": "#side", "cullface": "south"},
            "west": {"uv": [8, 0, 16, 8], "texture": "#side"},
            "east": {"uv": [0, 0, 8, 8], "texture": "#side", "cullface": "east"}})]},
}


# ------------------------------------------------------------------------------ resolver
class Resolver:
    def __init__(self, squid_root: Path, vanilla_root: Path | None):
        self.roots = {"squidgame": Path(squid_root), "minecraft": Path(vanilla_root) if vanilla_root else None}
        self._models: dict[str, dict] = {}
        self._tex: dict[str, np.ndarray] = {}

    @staticmethod
    def split(ref: str) -> tuple[str, str]:
        if ":" in ref:
            ns, p = ref.split(":", 1)
            return ns, p
        return "minecraft", ref

    def _file(self, ref: str, sub: str, ext: str) -> Path | None:
        ns, p = self.split(ref)
        root = self.roots.get(ns)
        if root is None:
            return None
        f = root / sub / (p + ext)
        return f if f.exists() else None

    def model(self, ref: str) -> dict:
        if ref in self._models:
            return self._models[ref]
        f = self._file(ref, "models", ".json")
        ns, path = self.split(ref)
        if f is not None:
            data = json.loads(f.read_text(encoding="utf-8"))
        elif ns == "minecraft" and path in BUILTIN_MODELS:
            data = BUILTIN_MODELS[path]
        else:
            raise FileNotFoundError(f"model {ref}")
        parent = data.get("parent")
        if parent and not parent.startswith("builtin/"):
            base = self.model(parent)
        else:
            base = {"textures": {}, "elements": None, "display": {}}
        res = {
            "textures": {**base["textures"], **data.get("textures", {})},
            "elements": data["elements"] if "elements" in data else base["elements"],
            "display": {**base["display"], **data.get("display", {})},
            "parent": parent,
        }
        self._models[ref] = res
        return res

    def texture(self, ref: str) -> np.ndarray | None:
        if ref in self._tex:
            return self._tex[ref]
        f = self._file(ref, "textures", ".png")
        arr = None if f is None else np.array(Image.open(f).convert("RGBA"))
        self._tex[ref] = arr
        return arr

    def blockstate(self, block: str) -> dict:
        f = self._file(block, "blockstates", ".json")
        if f is None:
            raise FileNotFoundError(f"blockstate {block}")
        return json.loads(f.read_text(encoding="utf-8"))


# ------------------------------------------------------------------------------ quads
@dataclass
class Quad:
    verts: np.ndarray        # (4,3) world-ish coordinates (block units)
    uvs: np.ndarray          # (4,2) normalised 0..1 (relative to texture frame)
    tex: np.ndarray          # RGBA texture
    shade: float
    cull: str | None = None
    translucent: bool = False
    layer: int = 0           # draw order tie-breaker


def _tex_translucent(tex: np.ndarray) -> bool:
    a = tex[..., 3]
    return bool(((a > 0) & (a < 255)).any())


def _resolve_tex(model: dict, name: str) -> str | None:
    seen = 0
    while name and name.startswith("#"):
        name = model["textures"].get(name[1:])
        seen += 1
        if seen > 20:
            return None
    return name


def bake(resolver: Resolver, model_ref: str, x: int = 0, y: int = 0, offset=(0, 0, 0), layer: int = 0) -> list[Quad]:
    model = resolver.model(model_ref)
    quads: list[Quad] = []
    for el in model["elements"] or []:
        f = np.array(el["from"], dtype=float)
        t = np.array(el["to"], dtype=float)
        rot = el.get("rotation")
        shade_on = el.get("shade", True)
        for face, fd in el["faces"].items():
            texref = _resolve_tex(model, fd["texture"])
            tex = resolver.texture(texref) if texref else None
            if tex is None:
                tex = np.zeros((16, 16, 4), np.uint8)
                tex[..., 0] = 255
                tex[..., 2] = 255
                tex[..., 3] = 255
            uv = list(fd.get("uv") or _default_uv(face, f, t))
            frot = fd.get("rotation", 0)
            vs = []
            for sel in _FACE_VERTS[face]:
                vs.append([f[0] if sel[0] == "min" else t[0],
                           f[1] if sel[1] == "min" else t[1],
                           f[2] if sel[2] == "min" else t[2]])
            vs = np.array(vs, dtype=float)
            if rot:
                origin = np.array(rot["origin"], dtype=float)
                vs = _rot_axis(vs - origin, rot["axis"], rot["angle"]) + origin
                if rot.get("rescale"):
                    k = 1.0 / math.cos(math.radians(abs(rot["angle"])))
                    other = {"x": (0, 1, 1), "y": (1, 0, 1), "z": (1, 1, 0)}[rot["axis"]]
                    vs = (vs - origin) * np.where(np.array(other) > 0, k, 1.0) + origin
            # blockstate rotation: rotateYXZ(-y, -x) about the centre
            if x or y:
                c = np.array([8.0, 8.0, 8.0])
                vs = vs - c
                if x:
                    vs = _rot_axis(vs, "x", -x)
                if y:
                    vs = _rot_axis(vs, "y", -y)
                vs = vs + c
            # per-vertex UVs (BlockFaceUV)
            uvs = []
            for i in range(4):
                j = (i + frot // 90) % 4
                u = uv[2] if j not in (0, 1) else uv[0]
                v = uv[3] if j not in (0, 3) else uv[1]
                uvs.append([u / 16.0, v / 16.0])
            # final facing for shading (calculateFacing)
            n = np.cross(vs[2] - vs[1], vs[0] - vs[1])
            ln = np.linalg.norm(n)
            direction = face
            if ln > 1e-9:
                n = n / ln
                best, bd = 0.0, None
                for d in DIRS:
                    g = float(np.dot(n, DIR_VEC[d]))
                    if g >= 0 and g > best:
                        best, bd = g, d
                direction = bd or face
            cull = fd.get("cullface")
            if cull and (x or y):
                vec = np.array(DIR_VEC[cull], dtype=float)
                if x:
                    vec = _rot_axis(vec, "x", -x)
                if y:
                    vec = _rot_axis(vec, "y", -y)
                cull = min(DIRS, key=lambda d: np.linalg.norm(vec - np.array(DIR_VEC[d])))
            q = Quad(vs + np.array(offset, dtype=float), np.array(uvs), tex,
                     SHADE[direction] if shade_on else 1.0, cull, _tex_translucent(tex), layer)
            quads.append(q)
    return quads


# ------------------------------------------------------------------------------ scene
class Scene:
    """A set of blocks at integer positions rendered through their blockstates."""

    def __init__(self, resolver: Resolver):
        self.r = resolver
        self.blocks: dict[tuple[int, int, int], tuple[str, dict]] = {}

    def set(self, pos, block: str, **props):
        self.blocks[tuple(pos)] = (block, {k: str(v) for k, v in props.items()})

    def _variant(self, block: str, props: dict, pos):
        bs = self.r.blockstate(block)
        for key, val in bs.get("variants", {}).items():
            conds = [c.split("=") for c in key.split(",")] if key else []
            if all(props.get(k) == v for k, v in conds):
                if isinstance(val, list):
                    val = val[(pos[0] * 31 + pos[1] * 17 + pos[2] * 7) % len(val)]
                return val
        raise KeyError(f"no variant for {block} {props}")

    def _is_opaque_cube(self, pos) -> bool:
        if pos not in self.blocks:
            return False
        block, props = self.blocks[pos]
        v = self._variant(block, props, pos)
        m = self.r.model(v["model"])
        els = m["elements"]
        if not els or len(els) != 1:
            return False
        e = els[0]
        if e["from"] != [0, 0, 0] or e["to"] != [16, 16, 16] or e.get("rotation"):
            return False
        for fd in e["faces"].values():
            tex = self.r.texture(_resolve_tex(m, fd["texture"]) or "")
            if tex is None or (tex[..., 3] < 255).any():
                return False
        return len(e["faces"]) == 6

    def quads(self) -> list[Quad]:
        out: list[Quad] = []
        for pos, (block, props) in sorted(self.blocks.items()):
            v = self._variant(block, props, pos)
            off = (pos[0] * 16, pos[1] * 16, pos[2] * 16)
            for q in bake(self.r, v["model"], v.get("x", 0), v.get("y", 0), off):
                if q.cull:
                    d = DIR_VEC[q.cull]
                    npos = (pos[0] + d[0], pos[1] + d[1], pos[2] + d[2])
                    if self._is_opaque_cube(npos):
                        continue
                out.append(q)
        return out


# ------------------------------------------------------------------------------ rasteriser
def _project(verts: np.ndarray, center, yaw: float, pitch: float):
    q = verts - np.array(center, dtype=float)
    q = _rot_axis(q, "y", yaw)
    q = _rot_axis(q, "x", pitch)
    return q[:, 0], q[:, 1], q[:, 2]  # screen x (right), y (up), depth (toward viewer)


def render(quads: list[Quad], yaw: float = 225, pitch: float = 30, scale: float = 6.0,
           center=(8, 8, 8), margin: int = 10, bg=(0, 0, 0, 0), ssaa: int = 1,
           min_size: tuple[int, int] = (0, 0), fit: bool = True, light: float = 1.0) -> np.ndarray:
    """Render quads orthographically; returns an RGBA uint8 array."""
    if not quads:
        return np.zeros((8, 8, 4), np.uint8)
    S = scale * ssaa
    allv = np.concatenate([q.verts for q in quads])
    sx, sy, _ = _project(allv, center, yaw, pitch)
    minx, maxx = sx.min() * S, sx.max() * S
    miny, maxy = (-sy).min() * S, (-sy).max() * S
    W = int(math.ceil(maxx - minx)) + 2 * margin * ssaa
    H = int(math.ceil(maxy - miny)) + 2 * margin * ssaa
    W = max(W, min_size[0] * ssaa)
    H = max(H, min_size[1] * ssaa)
    ox = (W - (maxx - minx)) / 2 - minx
    oy = (H - (maxy - miny)) / 2 - miny

    color = np.zeros((H, W, 4), dtype=np.float64)
    zbuf = np.full((H, W), -1e18)

    def tri(p, z, uv, tex, shade_f, translucent, write_depth, test_only_opaque_z):
        x0 = int(max(0, math.floor(p[:, 0].min())))
        x1 = int(min(W, math.ceil(p[:, 0].max()) + 1))
        y0 = int(max(0, math.floor(p[:, 1].min())))
        y1 = int(min(H, math.ceil(p[:, 1].max()) + 1))
        if x1 <= x0 or y1 <= y0:
            return
        xs, ys = np.meshgrid(np.arange(x0, x1) + 0.5, np.arange(y0, y1) + 0.5)
        (ax, ay), (bx, by), (cx, cy) = p
        den = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
        if abs(den) < 1e-9:
            return
        w0 = ((by - cy) * (xs - cx) + (cx - bx) * (ys - cy)) / den
        w1 = ((cy - ay) * (xs - cx) + (ax - cx) * (ys - cy)) / den
        w2 = 1 - w0 - w1
        eps = -1e-6
        inside = (w0 >= eps) & (w1 >= eps) & (w2 >= eps)
        if not inside.any():
            return
        depth = w0 * z[0] + w1 * z[1] + w2 * z[2]
        zb = zbuf[y0:y1, x0:x1]
        inside &= depth >= zb - 1e-6
        if not inside.any():
            return
        u = w0 * uv[0, 0] + w1 * uv[1, 0] + w2 * uv[2, 0]
        v = w0 * uv[0, 1] + w1 * uv[1, 1] + w2 * uv[2, 1]
        tw = tex.shape[1]
        th = tex.shape[0]
        # tiny nudge so exact texel boundaries stay deterministic
        tx = np.clip(np.floor(u * tw + 1e-6).astype(int), 0, tw - 1)
        ty = np.clip(np.floor(v * tw + 1e-6).astype(int), 0, th - 1)
        texel = tex[ty, tx].astype(np.float64)
        alpha = texel[..., 3] / 255.0
        vis = inside & (alpha > 0)
        if not vis.any():
            return
        rgb = texel[..., :3] * shade_f * light
        cb = color[y0:y1, x0:x1]
        if translucent:
            a = alpha[..., None]
            dst = cb
            da = dst[..., 3:4] / 255.0
            oa = a + da * (1 - a)
            safe = np.where(oa > 0, oa, 1)
            new_rgb = (rgb * a + dst[..., :3] * da * (1 - a)) / safe
            new = np.concatenate([new_rgb, oa * 255.0], axis=-1)
            cb[vis] = new[vis]
        else:
            solid = vis & (alpha >= 0.999)
            cb[solid, :3] = rgb[solid]
            cb[solid, 3] = 255.0
            if write_depth:
                zb[solid] = depth[solid]
            # cutout partials (alpha between 0 and 1 but treated as translucent-lite)
            part = vis & ~solid
            if part.any():
                a = alpha[..., None]
                da = cb[..., 3:4] / 255.0
                oa = a + da * (1 - a)
                safe = np.where(oa > 0, oa, 1)
                new_rgb = (rgb * a + cb[..., :3] * da * (1 - a)) / safe
                cb[part, :3] = new_rgb[part]
                cb[part, 3] = (oa * 255.0)[..., 0][part]

    def draw(q: Quad, translucent_pass: bool):
        sx_, sy_, sz_ = _project(q.verts, center, yaw, pitch)
        p = np.stack([sx_ * S + ox, -sy_ * S + oy], axis=1)
        for idx in ((0, 1, 2), (0, 2, 3)):
            tri(p[list(idx)], sz_[list(idx)], q.uvs[list(idx)], q.tex, q.shade,
                translucent_pass, not translucent_pass, False)

    opaque = [q for q in quads if not q.translucent]
    trans = [q for q in quads if q.translucent]
    for q in sorted(opaque, key=lambda q: q.layer):
        draw(q, False)

    def mean_depth(q):
        return float(_project(q.verts, center, yaw, pitch)[2].mean())

    for q in sorted(trans, key=mean_depth):
        draw(q, True)

    out = np.zeros((H, W, 4), np.uint8)
    arr = color
    if ssaa > 1:
        a = arr[..., 3:4] / 255.0
        pm = np.concatenate([arr[..., :3] * a, arr[..., 3:4]], axis=-1)
        h2, w2 = H // ssaa * ssaa, W // ssaa * ssaa
        pm = pm[:h2, :w2].reshape(h2 // ssaa, ssaa, w2 // ssaa, ssaa, 4).mean(axis=(1, 3))
        al = pm[..., 3:4] / 255.0
        rgbv = np.where(al > 0, pm[..., :3] / np.maximum(al, 1e-6), 0)
        arr = np.concatenate([rgbv, pm[..., 3:4]], axis=-1)
    out = np.clip(np.round(arr), 0, 255).astype(np.uint8)
    if bg is not None and bg[3] > 0:
        base = np.zeros(out.shape, np.float64)
        base[..., :3] = bg[:3]
        base[..., 3] = 255
        a = out[..., 3:4] / 255.0
        res = out[..., :3] * a + base[..., :3] * (1 - a)
        out = np.concatenate([res, np.full(out.shape[:2] + (1,), 255.0)], axis=-1).astype(np.uint8)
    return out


# ------------------------------------------------------------------------------ player model
def _cube_quads(tex, u0, v0, size, origin, pivot, inflate, layer, tex_w=64, tex_h=64):
    """Port of ModelPart.Cube; returns quads in a y-up world (x=x, y=-y, z=-z of model space)."""
    wd, ht, dp = size
    ox, oy, oz = origin
    f, g, h = ox - inflate, oy - inflate, oz - inflate
    s, t, u = ox + wd + inflate, oy + ht + inflate, oz + dp + inflate
    V = {
        1: (f, g, h), 2: (s, g, h), 3: (s, t, h), 4: (f, t, h),
        5: (f, g, u), 6: (s, g, u), 7: (s, t, u), 8: (f, t, u),
    }
    w_ = u0
    x_ = u0 + dp
    y_ = u0 + dp + wd
    z_ = u0 + dp + 2 * wd
    aa = u0 + 2 * dp + wd
    ab = u0 + 2 * dp + 2 * wd
    ac = v0
    ad = v0 + dp
    ae = v0 + dp + ht
    polys = [
        ("down", [6, 5, 1, 2], x_, ac, y_, ad),
        ("up", [3, 4, 8, 7], y_, ad, z_, ac),
        ("west", [1, 5, 8, 4], w_, ad, x_, ae),
        ("north", [2, 1, 4, 3], x_, ad, y_, ae),
        ("east", [6, 2, 3, 7], y_, ad, aa, ae),
        ("south", [5, 6, 7, 8], aa, ad, ab, ae),
    ]
    quads = []
    for name, vi, f1, g1, h1, i1 in polys:
        uvs = [(h1 / tex_w, g1 / tex_h), (f1 / tex_w, g1 / tex_h), (f1 / tex_w, i1 / tex_h), (h1 / tex_w, i1 / tex_h)]
        vs = []
        for k in vi:
            mx, my, mz = V[k]
            mx, my, mz = mx + pivot[0], my + pivot[1], mz + pivot[2]
            vs.append((mx, -my, -mz))
        # shade by (rendered) normal direction in world space
        vs = np.array(vs, dtype=float)
        n = np.cross(vs[2] - vs[1], vs[0] - vs[1])
        n = n / (np.linalg.norm(n) + 1e-9)
        # winding of the model polygons is for y-down space; just choose outward by dot with centre
        cen = vs.mean(axis=0)
        quads.append((vs, np.array(uvs), name, cen))
    return quads


def player_quads(skin: np.ndarray, overlay: np.ndarray | None, slim: bool = False, show_hat: bool = False):
    """Quads for a player: base skin layer, tracksuit overlay on base parts, and second layer parts."""
    quads: list[Quad] = []
    # (name, u0, v0, size, origin, pivot, inflate)
    arm_w = 3 if slim else 4
    parts = {
        "head": ((0, 0), (8, 8, 8), (-4, -8, -4), (0, 0, 0)),
        "body": ((16, 16), (8, 12, 4), (-4, 0, -2), (0, 0, 0)),
        "right_arm": ((40, 16), (arm_w, 12, 4), ((-2 if slim else -3), -2, -2), (-5, 2.5 if slim else 2, 0)),
        "left_arm": ((32, 48), (arm_w, 12, 4), (-1, -2, -2), (5, 2.5 if slim else 2, 0)),
        "right_leg": ((0, 16), (4, 12, 4), (-2, 0, -2), (-1.9, 12, 0)),
        "left_leg": ((16, 48), (4, 12, 4), (-2, 0, -2), (1.9, 12, 0)),
    }
    parts2 = {
        "hat": ((32, 0), (8, 8, 8), (-4, -8, -4), (0, 0, 0), 0.5),
        "jacket": ((16, 32), (8, 12, 4), (-4, 0, -2), (0, 0, 0), 0.25),
        "right_sleeve": ((40, 32), (arm_w, 12, 4), ((-2 if slim else -3), -2, -2), (-5, 2.5 if slim else 2, 0), 0.25),
        "left_sleeve": ((48, 48), (arm_w, 12, 4), (-1, -2, -2), (5, 2.5 if slim else 2, 0), 0.25),
        "right_pants": ((0, 32), (4, 12, 4), (-2, 0, -2), (-1.9, 12, 0), 0.25),
        "left_pants": ((0, 48), (4, 12, 4), (-2, 0, -2), (1.9, 12, 0), 0.25),
    }

    def add(tex, parts_def, layer, base_layer):
        for name, spec in parts_def.items():
            if base_layer:
                (u0, v0), size, origin, pivot = spec
                infl = 0.0
            else:
                (u0, v0), size, origin, pivot, infl = spec
                if name == "hat" and not show_hat:
                    continue
            for vs, uvs, dname, cen in _cube_quads(tex, u0, v0, size, origin, pivot, infl, layer):
                # shade from true outward normal: use face name (model space) mapped to world
                shade_dir = {"down": "up", "up": "down", "north": "south", "south": "north",
                             "west": "west", "east": "east"}[dname]
                quads.append(Quad(vs, uvs, tex, SHADE[shade_dir], None, _tex_translucent(tex), layer))

    add(skin, parts, 0, True)
    add(skin, parts2, 1, False)
    if overlay is not None:
        add(overlay, parts, 2, True)
        add(overlay, parts2, 3, False)
    return quads
