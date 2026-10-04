"""GeckoLib 4.9.3 faithful model / animation toolkit (pure Python + numpy).

Everything in this file is a port of what the real library does when it loads
and renders a ``*.geo.json`` / ``*.animation.json`` pair.  Sources read:

  loading/object/BakedModelFactory.java   (bake: x negation, UV / vertex order)
  cache/object/GeoBone.java, GeoCube.java, GeoQuad.java
  util/RenderUtil.java                    (matrix order: prepMatrixForBone ...)
  renderer/GeoRenderer.java               (renderRecursively / renderCube)
  loading/json/typeadapter/BakedAnimationsAdapter.java  (keyframe parsing, rot signs)
  animation/AnimationController.java      (keyframe lookup)
  animation/AnimationProcessor.java       (value + initial snapshot rotation)
  animation/EasingType.java               (easing functions)

Derived conventions (all verified against the sources, see README.md):

  baked pivot     = (-px, py, pz)             (x is mirrored when a model is baked)
  baked cube org  = (-(ox + sx), oy, oz)      cube x extent [-(ox+sx), -ox]
  bone rotation   = (-rx, -ry, +rz) radians   from the geo "rotation" AND from the
                                              animation keyframes (degrees in JSON)
  bone matrix     = T(-pos.x, pos.y, pos.z)/16 * T(P/16) * Rz * Ry * Rx * S * T(-P/16)
  cube matrix     = T(cp/16) * Rz * Ry * Rx * T(-cp/16)       (cp = (-cpx, cpy, cpz))
  animated rot    = keyframe value (after sign flip) + initial bone rotation
  animated pos    = keyframe value (absolute, replaces the offset)  px / 16 blocks
  keyframe easing = the easing of keyframe N describes the segment N-1 -> N
"""
from __future__ import annotations

import json
import math
from dataclasses import dataclass, field

import numpy as np

# --------------------------------------------------------------------------------------
# easing (EasingType.java)
# --------------------------------------------------------------------------------------


def _ease_in(f):
    return f


def _ease_out(f):
    return lambda t: 1 - f(1 - t)


def _ease_in_out(f):
    def g(t):
        if t < 0.5:
            return f(t * 2) / 2
        return 1 - f((1 - t) * 2) / 2

    return g


def _sine(n):
    return 1 - math.cos(n * math.pi / 2)


def _quad(n):
    return n * n


def _cubic(n):
    return n * n * n


def _pow(k):
    return lambda n: n ** k


def _expo(n):
    return 2 ** (10 * (n - 1))


def _circ(n):
    return 1 - math.sqrt(max(0.0, 1 - n * n))


def _back(arg):
    n2 = 1.70158 if arg is None else arg * 1.70158
    return lambda t: t * t * ((n2 + 1) * t - n2)


def _elastic(arg):
    n2 = 1 if arg is None else arg
    return lambda t: 1 - math.pow(math.cos(t * math.pi / 2), 3) * math.cos(t * n2 * math.pi)


def _bounce(arg):
    n2 = 0.5 if arg is None else arg
    one = lambda x: 121 / 16 * x * x
    two = lambda x: 121 / 4 * n2 * (x - 6 / 11) ** 2 + 1 - n2
    three = lambda x: 121 * n2 * n2 * (x - 9 / 11) ** 2 + 1 - n2 * n2
    four = lambda x: 484 * n2 ** 3 * (x - 10.5 / 11) ** 2 + 1 - n2 ** 3
    return lambda t: min(min(one(t), two(t)), min(three(t), four(t)))


def _catmull_inner(n):
    return 0.5 * (2 * (n + 1) + 2 + (2 * n - 5 * (n + 1) + 4 * (n + 2) - (n + 3)) + (3 * (n + 1) - n - 3 * (n + 2) + (n + 3)))


# registry keys are lower case, exactly like EasingType.EASING_TYPES
EASINGS = {
    "linear": lambda arg: _ease_in(lambda t: t),
    "none": lambda arg: _ease_in(lambda t: t),
    "easeinsine": lambda arg: _ease_in(_sine),
    "easeoutsine": lambda arg: _ease_out(_sine),
    "easeinoutsine": lambda arg: _ease_in_out(_sine),
    "easeinquad": lambda arg: _ease_in(_quad),
    "easeoutquad": lambda arg: _ease_out(_quad),
    "easeinoutquad": lambda arg: _ease_in_out(_quad),
    "easeincubic": lambda arg: _ease_in(_cubic),
    "easeoutcubic": lambda arg: _ease_out(_cubic),
    "easeinoutcubic": lambda arg: _ease_in_out(_cubic),
    "easeinquart": lambda arg: _ease_in(_pow(4)),
    "easeoutquart": lambda arg: _ease_out(_pow(4)),
    "easeinoutquart": lambda arg: _ease_in_out(_pow(4)),
    "easeinquint": lambda arg: _ease_in(_pow(5)),
    "easeoutquint": lambda arg: _ease_out(_pow(5)),
    "easeinoutquint": lambda arg: _ease_in_out(_pow(5)),
    "easeinexpo": lambda arg: _ease_in(_expo),
    "easeoutexpo": lambda arg: _ease_out(_expo),
    "easeinoutexpo": lambda arg: _ease_in_out(_expo),
    "easeincirc": lambda arg: _ease_in(_circ),
    "easeoutcirc": lambda arg: _ease_out(_circ),
    "easeinoutcirc": lambda arg: _ease_in_out(_circ),
    "easeinback": lambda arg: _ease_in(_back(arg)),
    "easeoutback": lambda arg: _ease_out(_back(arg)),
    "easeinoutback": lambda arg: _ease_in_out(_back(arg)),
    "easeinelastic": lambda arg: _ease_in(_elastic(arg)),
    "easeoutelastic": lambda arg: _ease_out(_elastic(arg)),
    "easeinoutelastic": lambda arg: _ease_in_out(_elastic(arg)),
    "easeinbounce": lambda arg: _ease_in(_bounce(arg)),
    "easeoutbounce": lambda arg: _ease_out(_bounce(arg)),
    "easeinoutbounce": lambda arg: _ease_in_out(_bounce(arg)),
}
# not ported on purpose (we never author them): "step" (odd 2-value behaviour) and
# "catmullrom" (needs neighbour keyframes).  The validator rejects them.


# --------------------------------------------------------------------------------------
# animation parsing (BakedAnimationsAdapter.java)
# --------------------------------------------------------------------------------------


@dataclass
class Keyframe:
    length: float        # ticks
    start: float
    end: float
    easing: str = "linear"
    args: list = field(default_factory=list)


@dataclass
class BoneAnim:
    name: str
    rot: tuple      # (xs, ys, zs) lists of Keyframe, already sign-converted (radians)
    pos: tuple
    scale: tuple


@dataclass
class Anim:
    name: str
    length_ticks: float
    loop: object
    bones: dict
    raw_length_sec: float = 0.0


def _read_timestamp(key: str) -> float:
    try:
        return float(key)
    except ValueError:
        return 0.0


def _get_keyframes(el):
    """List of (time_sec, element) exactly like BakedAnimationsAdapter.getKeyframes."""
    if el is None:
        return []
    if isinstance(el, (int, float)):
        el = [el, el, el]
    if isinstance(el, list):
        return [(0.0, el)]
    if isinstance(el, dict):
        if "vector" in el:
            return [(0.0, el)]
        out = []
        for k, v in el.items():
            ts = _read_timestamp(k)
            if ts == 0 and out:
                raise ValueError("multiple starting keyframes: %r" % k)
            if isinstance(v, dict) and "vector" not in v:
                # bedrock pre/post
                added = False
                if "pre" in v:
                    added = True
                    out.append((ts if ts == 0 else ts - 0.001, _bedrock_vec(v["pre"])))
                if "post" in v:
                    vals = _bedrock_vec(v["post"])
                    if "lerp_mode" in v:
                        out.append((ts, {"vector": vals, "easing": v["lerp_mode"]}))
                    else:
                        out.append((ts, vals))
                    continue
                if not added:
                    raise ValueError("invalid keyframe %r" % (v,))
                continue
            out.append((ts, v))
        return out
    raise ValueError("invalid keyframe container %r" % (el,))


def _bedrock_vec(k):
    if isinstance(k, list):
        return k
    if "vector" in k:
        return k["vector"]
    if "pre" in k:
        return k["pre"]
    return k["post"]


def _build_stack(entries, is_rot):
    if not entries:
        return ([], [], [])
    frames = ([], [], [])
    prev = None
    prev_time = 0.0
    for t, el in entries:
        vec = el if isinstance(el, list) else el["vector"]
        easing = "linear"
        args = []
        if isinstance(el, dict):
            if "easing" in el:
                easing = str(el["easing"]).lower()
            if "easingArgs" in el:
                args = [float(a) for a in el["easingArgs"]]
        vals = []
        for axis in range(3):
            v = float(vec[axis])
            if is_rot:
                v = math.radians(-v if axis < 2 else v)   # constants: (-x, -y, +z)
            vals.append(v)
        dt = t - prev_time
        for axis in range(3):
            start = vals[axis] if prev is None else prev[axis]
            frames[axis].append(Keyframe(dt * 20, start, vals[axis], easing, args))
        prev = vals
        prev_time = t
    return frames


def parse_animations(doc) -> dict:
    """doc: parsed animation json (dict with 'animations')."""
    out = {}
    for name, a in doc["animations"].items():
        length = a.get("animation_length")
        loop = a.get("loop", False)
        bones = {}
        for bname, b in a.get("bones", {}).items():
            rot = _build_stack(_get_keyframes(b.get("rotation")), True)
            pos = _build_stack(_get_keyframes(b.get("position")), False)
            scl = _build_stack(_get_keyframes(b.get("scale")), False)
            bones[bname] = BoneAnim(bname, rot, pos, scl)
        if length is None:
            ln = 0.0
            for ba in bones.values():
                for stack in (ba.rot, ba.pos, ba.scale):
                    for fr in stack:
                        ln = max(ln, sum(k.length for k in fr))
            length_ticks = ln if ln else float("inf")
        else:
            length_ticks = float(length) * 20.0
        out[name] = Anim(name, length_ticks, loop, bones, float(length) if length is not None else 0.0)
    return out


def _sample_axis(frames, tick):
    """AnimationController.getCurrentKeyFrameLocation + EasingType.apply."""
    total = 0.0
    chosen = None
    local = tick
    for f in frames:
        total += f.length
        if total > tick:
            chosen = f
            local = tick - (total - f.length)
            break
    if chosen is None:
        chosen = frames[-1]
        local = tick
    if local >= chosen.length:
        return chosen.end
    fn = EASINGS.get(chosen.easing, EASINGS["linear"])(chosen.args[0] if chosen.args else None)
    t = local / chosen.length
    return chosen.start + (chosen.end - chosen.start) * fn(t)


def sample_stack(stack, tick):
    if not stack[0]:
        return None
    return np.array([_sample_axis(stack[i], tick) for i in range(3)])


# --------------------------------------------------------------------------------------
# geometry baking (BakedModelFactory.Builtin)
# --------------------------------------------------------------------------------------

DIRS = ("west", "east", "north", "south", "up", "down")  # buildQuads order
NORMALS = {
    "west": (-1.0, 0.0, 0.0), "east": (1.0, 0.0, 0.0), "north": (0.0, 0.0, -1.0),
    "south": (0.0, 0.0, 1.0), "up": (0.0, 1.0, 0.0), "down": (0.0, -1.0, 0.0),
}


@dataclass
class BQuad:
    verts: np.ndarray   # (4,3) blocks (baked space, before bone/cube transforms)
    uv: np.ndarray      # (4,2) normalised
    normal: np.ndarray
    direction: str
    uv_rect: tuple      # (u0, v0, u1, v1) in texels (for clamped sampling)


@dataclass
class BCube:
    pivot: np.ndarray   # px, baked
    rot: np.ndarray     # rad, baked (x, y, z)
    quads: list
    size: tuple


@dataclass
class BBone:
    name: str
    parent: object
    pivot: np.ndarray   # px, baked
    rot: np.ndarray     # rad, baked
    cubes: list = field(default_factory=list)
    children: list = field(default_factory=list)
    never_render: bool = False


@dataclass
class BakedModel:
    bones: dict
    roots: list
    tex_w: float
    tex_h: float
    identifier: str = ""


def _vertex_set(origin, vsize, infl):
    ox, oy, oz = origin
    sx, sy, sz = vsize
    f = lambda x, y, z: np.array([x, y, z], dtype=float)
    return {
        "bottomLeftBack": f(ox - infl, oy - infl, oz - infl),
        "bottomRightBack": f(ox - infl, oy - infl, oz + sz + infl),
        "topLeftBack": f(ox - infl, oy + sy + infl, oz - infl),
        "topRightBack": f(ox - infl, oy + sy + infl, oz + sz + infl),
        "topLeftFront": f(ox + sx + infl, oy + sy + infl, oz - infl),
        "topRightFront": f(ox + sx + infl, oy + sy + infl, oz + sz + infl),
        "bottomLeftFront": f(ox + sx + infl, oy - infl, oz - infl),
        "bottomRightFront": f(ox + sx + infl, oy - infl, oz + sz + infl),
    }


def _quad_vertices(vs, direction, box_uv, mirror):
    g = vs.__getitem__
    q = {
        "west": [g("topRightBack"), g("topLeftBack"), g("bottomLeftBack"), g("bottomRightBack")],
        "east": [g("topLeftFront"), g("topRightFront"), g("bottomRightFront"), g("bottomLeftFront")],
        "north": [g("topLeftBack"), g("topLeftFront"), g("bottomLeftFront"), g("bottomLeftBack")],
        "south": [g("topRightFront"), g("topRightBack"), g("bottomRightBack"), g("bottomRightFront")],
        "up": [g("topRightBack"), g("topRightFront"), g("topLeftFront"), g("topLeftBack")],
        "down": [g("bottomLeftBack"), g("bottomLeftFront"), g("bottomRightFront"), g("bottomRightBack")],
    }
    if direction == "west":
        key = "east" if mirror else "west"
    elif direction == "east":
        key = "west" if mirror else "east"
    elif direction == "up":
        key = "down" if (mirror and not box_uv) else "up"
    elif direction == "down":
        key = "up" if (mirror and not box_uv) else "down"
    else:
        key = direction
    return [v.copy() for v in q[key]]


def _rotate_uvs(rot, u, v, uw, vh):
    # FaceUV.Rotation.rotateUvs
    if rot == 0:
        return [u, v, uw, v, uw, vh, u, vh]
    if rot == 90:
        return [uw, v, uw, vh, u, vh, u, v]
    if rot == 180:
        return [uw, vh, u, vh, u, v, uw, v]
    return [u, vh, u, v, uw, v, uw, vh]


def _build_quad(verts, u, v, usize, vsize, uv_rot, tw, th, mirror, direction):
    # GeoQuad.build
    u0t, v0t = u, v
    uw = (u + usize) / tw
    vh = (v + vsize) / th
    u = u / tw
    v = v / th
    normal = np.array(NORMALS[direction], dtype=float)
    if not mirror:
        u, uw = uw, u
    else:
        normal = normal * np.array([-1.0, 1.0, 1.0])
    uvs = _rotate_uvs(uv_rot, u, v, uw, vh)
    uvarr = np.array(uvs, dtype=float).reshape(4, 2)
    return BQuad(np.array(verts), uvarr, normal, direction,
                 (min(u0t, u0t + usize), min(v0t, v0t + vsize), max(u0t, u0t + usize), max(v0t, v0t + vsize)))


def _bake_cube(c, tw, th, bone_inflate):
    mirror = bool(c.get("mirror", False))
    inflate = c["inflate"] / 16.0 if c.get("inflate") is not None else ((bone_inflate or 0) / 16.0)
    size = np.array(c["size"], dtype=float)
    origin = np.array(c["origin"], dtype=float)
    rotation = np.array(c.get("rotation", [0, 0, 0]), dtype=float)
    pivot = np.array(c.get("pivot", [0, 0, 0]), dtype=float)
    borigin = np.array([-(origin[0] + size[0]) / 16.0, origin[1] / 16.0, origin[2] / 16.0])
    vsize = size / 16.0
    pivot = pivot * np.array([-1.0, 1.0, 1.0])
    rot = np.array([math.radians(-rotation[0]), math.radians(-rotation[1]), math.radians(rotation[2])])
    vs = _vertex_set(borigin, vsize, inflate)
    uv = c["uv"]
    quads = []
    box_uv = isinstance(uv, list)
    for d in DIRS:
        if not box_uv:
            fuv = uv.get(d)
            if fuv is None:
                quads.append(None)
                continue
            verts = _quad_vertices(vs, d, False, mirror or c.get("mirror") is True)
            quads.append(_build_quad(verts, fuv["uv"][0], fuv["uv"][1], fuv["uv_size"][0], fuv["uv_size"][1],
                                     int(fuv.get("uv_rotation", 0)) % 360, tw, th, mirror, d))
            continue
        # box uv
        ux, uy = uv
        fx, fy, fz = (math.floor(size[0]), math.floor(size[1]), math.floor(size[2]))
        if d == "west":
            data = ((ux + fz + fx, uy + fz), (fz, fy))
        elif d == "east":
            data = ((ux, uy + fz), (fz, fy))
        elif d == "north":
            data = ((ux + fz, uy + fz), (fx, fy))
        elif d == "south":
            data = ((ux + fz + fx + fz, uy + fz), (fx, fy))
        elif d == "up":
            data = ((ux + fz, uy), (fx, fz))
        else:
            data = ((ux + fz + fx, uy + fz), (fx, -fz))
        verts = _quad_vertices(vs, d, True, mirror or c.get("mirror") is True)
        quads.append(_build_quad(verts, data[0][0], data[0][1], data[1][0], data[1][1], 0, tw, th, mirror, d))
    return BCube(pivot, rot, quads, tuple(size))


def bake(doc) -> BakedModel:
    """doc: parsed geo json.  Mirrors GeometryTree.fromModel + BakedModelFactory.Builtin."""
    geo = doc["minecraft:geometry"][0]
    desc = geo["description"]
    tw = float(desc["texture_width"])
    th = float(desc["texture_height"])
    bones = {}
    order = []
    for b in geo["bones"]:
        rot = np.array(b.get("rotation", [0, 0, 0]), dtype=float)
        piv = np.array(b.get("pivot", [0, 0, 0]), dtype=float)
        bb = BBone(b["name"], b.get("parent"),
                   np.array([-piv[0], piv[1], piv[2]]),
                   np.array([math.radians(-rot[0]), math.radians(-rot[1]), math.radians(rot[2])]),
                   never_render=bool(b.get("neverRender", False)))
        for c in b.get("cubes", []):
            bb.cubes.append(_bake_cube(c, tw, th, b.get("inflate")))
        bones[bb.name] = bb
        order.append(bb)
    roots = []
    for bb in order:
        if bb.parent is None:
            roots.append(bb)
        else:
            if bb.parent == bb.name:
                raise ValueError("bone is its own parent: " + bb.name)
            if bb.parent not in bones:
                raise ValueError("bone %s has undefined parent %s" % (bb.name, bb.parent))
            bones[bb.parent].children.append(bb)
    return BakedModel(bones, roots, tw, th, desc.get("identifier", ""))


# --------------------------------------------------------------------------------------
# pose + matrices (RenderUtil / GeoRenderer)
# --------------------------------------------------------------------------------------


def _T(v):
    m = np.eye(4)
    m[:3, 3] = v
    return m


def _Rx(a):
    c, s = math.cos(a), math.sin(a)
    m = np.eye(4)
    m[1, 1], m[1, 2], m[2, 1], m[2, 2] = c, -s, s, c
    return m


def _Ry(a):
    c, s = math.cos(a), math.sin(a)
    m = np.eye(4)
    m[0, 0], m[0, 2], m[2, 0], m[2, 2] = c, s, -s, c
    return m


def _Rz(a):
    c, s = math.cos(a), math.sin(a)
    m = np.eye(4)
    m[0, 0], m[0, 1], m[1, 0], m[1, 1] = c, -s, s, c
    return m


def rot_zyx(x, y, z):
    """quat.rotationZYX(z, y, x): R = Rz * Ry * Rx (extrinsic X, then Y, then Z)."""
    return _Rz(z) @ _Ry(y) @ _Rx(x)


class Pose:
    """Per-bone animated state: rot (rad, baked sign), pos (px, JSON sign), scale."""

    def __init__(self, model: BakedModel):
        self.rot = {n: b.rot.copy() for n, b in model.bones.items()}
        self.pos = {n: np.zeros(3) for n in model.bones}
        self.scale = {n: np.ones(3) for n in model.bones}
        self.hidden = set()


def pose_at(model: BakedModel, anim: Anim | None, t_sec: float, loop_wrap: bool = True) -> Pose:
    pose = Pose(model)
    if anim is None:
        return pose
    tick = t_sec * 20.0
    if loop_wrap and anim.loop is True and anim.length_ticks > 0:
        tick = tick % anim.length_ticks
    for bname, ba in anim.bones.items():
        if bname not in model.bones:
            continue
        r = sample_stack(ba.rot, tick)
        if r is not None:
            pose.rot[bname] = r + model.bones[bname].rot
        p = sample_stack(ba.pos, tick)
        if p is not None:
            pose.pos[bname] = p
        s = sample_stack(ba.scale, tick)
        if s is not None:
            pose.scale[bname] = s
    return pose


def bone_matrix(bone: BBone, pose: Pose) -> np.ndarray:
    p = pose.pos[bone.name]
    r = pose.rot[bone.name]
    s = pose.scale[bone.name]
    S = np.diag([s[0], s[1], s[2], 1.0])
    return (_T(np.array([-p[0], p[1], p[2]]) / 16.0) @ _T(bone.pivot / 16.0)
            @ rot_zyx(r[0], r[1], r[2]) @ S @ _T(-bone.pivot / 16.0))


def cube_matrix(cube: BCube) -> np.ndarray:
    return _T(cube.pivot / 16.0) @ rot_zyx(cube.rot[0], cube.rot[1], cube.rot[2]) @ _T(-cube.pivot / 16.0)


def world_quads(model: BakedModel, pose: Pose):
    """Yield (bone_name, verts(4,3) in blocks, uv(4,2), normal(3), quad) for every visible quad."""
    out = []

    def rec(bone: BBone, parent_m):
        m = parent_m @ bone_matrix(bone, pose)
        hidden = bone.name in pose.hidden
        if not hidden:
            for cube in bone.cubes:
                cm = m @ cube_matrix(cube)
                nm = np.linalg.inv(cm[:3, :3]).T
                for q in cube.quads:
                    if q is None:
                        continue
                    v = np.c_[q.verts, np.ones(4)] @ cm.T
                    n = nm @ q.normal
                    ln = np.linalg.norm(n)
                    out.append((bone.name, v[:, :3], q.uv, n / ln if ln else n, q))
        if hidden:
            return  # setHidden also hides children
        for ch in bone.children:
            rec(ch, m)

    for r in model.roots:
        rec(r, np.eye(4))
    return out


def bone_world_matrices(model: BakedModel, pose: Pose) -> dict:
    res = {}

    def rec(bone, pm):
        m = pm @ bone_matrix(bone, pose)
        res[bone.name] = m
        for ch in bone.children:
            rec(ch, m)

    for r in model.roots:
        rec(r, np.eye(4))
    return res


def load_json(path):
    with open(path, "r", encoding="utf-8") as fh:
        return json.load(fh)
