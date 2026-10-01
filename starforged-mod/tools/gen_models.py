#!/usr/bin/env python3
"""
Starforged entity model generator.

Each creature is described once (parts, cubes, pivots, materials). This script:
  1. packs every cube's box-UV footprint into the texture sheet,
  2. writes src/main/java/com/starforged/client/model/ModelGeometry.java (LayerDefinitions),
  3. paints assets/starforged/textures/entity/<id>.png and <id>_glow.png (emissive layer).

Model space follows Minecraft conventions: +Y is down, the creature faces -Z (north), y=24 is the ground.
"""
import math
import os
import random
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA_OUT = os.path.join(ROOT, "src/main/java/com/starforged/client/model/ModelGeometry.java")
TEX_OUT = os.path.join(ROOT, "src/main/resources/assets/starforged/textures/entity")


# ----------------------------------------------------------------------------------------------------------------
# Materials: how a cube face gets painted.
# ----------------------------------------------------------------------------------------------------------------

def hexc(s):
    s = s.lstrip("#")
    return tuple(int(s[i:i + 2], 16) for i in (0, 2, 4))


def mix(a, b, t):
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(3))


def clamp(c):
    return tuple(max(0, min(255, int(v))) for v in c)


def shade(c, f):
    return clamp(tuple(v * f for v in c))


MATERIALS = {
    # name: dict(base, dark, light, pattern, glow)
    "mite_shell": dict(base="#3b2f63", dark="#1d1636", light="#6a58a8", pattern="plates"),
    "mite_leg": dict(base="#2a2147", dark="#140f26", light="#4d3f80", pattern="noise"),
    "mite_crystal": dict(base="#69e6ff", dark="#2a8fd1", light="#e0fbff", pattern="crystal", glow=True),
    "mite_eye": dict(base="#ffe36b", dark="#ffb02e", light="#fff8d0", pattern="flat", glow=True),

    "stalker_skin": dict(base="#16121f", dark="#07060c", light="#2c2440", pattern="veins", vein="#5b2a9e"),
    "stalker_head": dict(base="#120f1a", dark="#050408", light="#2a2238", pattern="noise"),
    "stalker_claw": dict(base="#c9c2d9", dark="#6c6480", light="#f2eeff", pattern="metal"),

    "golem_stone": dict(base="#4a5a78", dark="#28324a", light="#7387ad", pattern="bricks"),
    "golem_dark": dict(base="#2f3a52", dark="#171d2b", light="#4c5a7a", pattern="noise"),
    "golem_crystal": dict(base="#8c6cff", dark="#4b2fc4", light="#e6dcff", pattern="crystal", glow=True),
    "golem_core": dict(base="#7ff0ff", dark="#2bb0e0", light="#ffffff", pattern="flat", glow=True),

    "chest_wood": dict(base="#a0703c", dark="#5c3a1a", light="#c9935a", pattern="planks"),
    "chest_trim": dict(base="#3d2a18", dark="#22170c", light="#5a4027", pattern="noise"),
    "chest_latch": dict(base="#9aa0a8", dark="#585d66", light="#dfe3e8", pattern="metal"),
    "mouth": dict(base="#8a1c2e", dark="#4a0b16", light="#c23b52", pattern="noise"),
    "tongue": dict(base="#d6455f", dark="#8e1f33", light="#f37f94", pattern="noise"),
    "tooth": dict(base="#f2ead6", dark="#b8ad92", light="#ffffff", pattern="flat"),

    "robe": dict(base="#2a3266", dark="#141938", light="#4a56a3", pattern="robe", trim="#d8b24a"),
    "robe_dark": dict(base="#151a3a", dark="#080b1d", light="#2b3470", pattern="noise"),
    "wraith_face": dict(base="#05060c", dark="#000000", light="#0d1020", pattern="flat"),
    "wraith_hand": dict(base="#9fb6e8", dark="#5a6fa3", light="#dce8ff", pattern="noise"),
    "staff_wood": dict(base="#4a3628", dark="#2a1c12", light="#6e533e", pattern="noise"),
    "staff_lens": dict(base="#8ff7ff", dark="#2ab9d4", light="#ffffff", pattern="crystal", glow=True),

    "starling_body": dict(base="#ffd95a", dark="#f0a020", light="#fff6c4", pattern="star", glow=True),
    "starling_wing": dict(base="#bfe9ff", dark="#6ab8e6", light="#ffffff", pattern="crystal", glow=True),

    "ray_skin": dict(base="#2a2470", dark="#120f3a", light="#5146b8", pattern="nebula"),
    "ray_belly": dict(base="#8c86c9", dark="#57519a", light="#c3bff0", pattern="noise"),
    "ray_fin": dict(base="#3a2f8f", dark="#1b1552", light="#6c5ed6", pattern="nebula"),

    "sov_armor": dict(base="#2b2346", dark="#120e22", light="#4d3f7d", pattern="plates"),
    "sov_gold": dict(base="#d9a93a", dark="#8a5f16", light="#ffe9a3", pattern="metal"),
    "sov_robe": dict(base="#1b1433", dark="#09060f", light="#3a2a68", pattern="stars"),
    "sov_face": dict(base="#e8e0f5", dark="#9b8fb5", light="#ffffff", pattern="mask"),
    "sov_core": dict(base="#000000", dark="#000000", light="#1a0a2a", pattern="corona", glow=True),
    "sov_gem": dict(base="#ff5ae0", dark="#a01f8c", light="#ffd0f6", pattern="crystal", glow=True),
    "sov_halo": dict(base="#ffe39a", dark="#e0a640", light="#ffffff", pattern="flat", glow=True),
    "sov_hand": dict(base="#3a2d5e", dark="#1a1330", light="#6650a3", pattern="plates", rune="#c58bff"),

    "crystal_purple": dict(base="#b46cff", dark="#5a22b8", light="#f2e0ff", pattern="crystal", glow=True),

    # --- Sunforged ---
    "imp_skin": dict(base="#3a1612", dark="#1a0806", light="#5e2418", pattern="veins", vein="#ff7a1a"),
    "imp_horn": dict(base="#2a2420", dark="#120e0c", light="#5a4c40", pattern="metal"),
    "imp_wing": dict(base="#c8481a", dark="#6e1e0a", light="#ff9a3a", pattern="feather"),
    "ember_glow": dict(base="#ffb030", dark="#ff6a10", light="#fff2a0", pattern="crystal", glow=True),
    "crawler_rock": dict(base="#2c2622", dark="#141110", light="#4a403a", pattern="veins", vein="#ff6a10"),
    "crawler_belly": dict(base="#8a3a14", dark="#4a1a08", light="#c25a24", pattern="noise"),
    "hound_coal": dict(base="#24201e", dark="#0e0c0b", light="#3e3632", pattern="veins", vein="#ff8a20"),
    "hound_flame": dict(base="#ff8a1a", dark="#e04a0a", light="#ffe08a", pattern="feather", glow=True),
    "ash_plate": dict(base="#5e5a56", dark="#2e2c2a", light="#8a8580", pattern="plates"),
    "ash_dark": dict(base="#2a2826", dark="#121110", light="#423e3a", pattern="noise"),
    "knight_gold": dict(base="#a88a3a", dark="#5e4a18", light="#d8bc6a", pattern="metal"),
    "knight_blade": dict(base="#3a3634", dark="#1a1816", light="#5a5652", pattern="edged", edge="#ff8a20"),
    "shield_face": dict(base="#4a4642", dark="#24221f", light="#6e6964", pattern="plates"),
    "phoenix_body": dict(base="#e0441a", dark="#8a1e0a", light="#ff9a3a", pattern="feather"),
    "phoenix_wing": dict(base="#ff6a1a", dark="#c22a0a", light="#ffd060", pattern="feather", glow=True),
    "phoenix_tail": dict(base="#ffb030", dark="#ff4a10", light="#fff6c0", pattern="feather", glow=True),
    "phoenix_gold": dict(base="#e8b040", dark="#8a5e14", light="#fff0a0", pattern="metal"),
    "warden_plate": dict(base="#c89a3a", dark="#6e4e14", light="#f0d080", pattern="plates"),
    "warden_stone": dict(base="#b8844a", dark="#6a4420", light="#e0b47a", pattern="glyph", vein="#ffd060"),
    "warden_dark": dict(base="#4a2e1a", dark="#24160c", light="#6e4a2e", pattern="noise"),
    "warden_core": dict(base="#fff2b0", dark="#ffb030", light="#ffffff", pattern="star", glow=True),
    "warden_ray": dict(base="#ffd060", dark="#ff9a20", light="#fff6c0", pattern="flat", glow=True),
    "warden_blade": dict(base="#fff0c0", dark="#ffb040", light="#ffffff", pattern="edged", edge="#ffffff", glow=True),
    "pylon_stone": dict(base="#b8844a", dark="#6a4420", light="#e0b47a", pattern="glyph", vein="#ffd060"),
    "pylon_crystal": dict(base="#ffd060", dark="#ff8a20", light="#ffffff", pattern="crystal", glow=True),
    "crystal_core": dict(base="#ffffff", dark="#e0c8ff", light="#ffffff", pattern="flat", glow=True),
}


def face_pixels(mat, w, h, face, rng, cube_id):
    """Returns a w*h list of (rgb, alpha, glow) for one cube face."""
    m = MATERIALS[mat]
    base, dark, light = hexc(m["base"]), hexc(m["dark"]), hexc(m["light"])
    pat = m["pattern"]
    out = []
    # Directional lighting baked into the texture: top bright, bottom dark.
    face_mul = {"top": 1.12, "bottom": 0.72, "north": 1.0, "south": 0.88, "east": 0.94, "west": 0.94}[face]
    for y in range(h):
        row = []
        for x in range(w):
            n = rng.random()
            c = base
            glow = m.get("glow", False)
            if pat == "noise":
                c = mix(base, dark if n < 0.5 else light, abs(n - 0.5) * 0.7)
            elif pat == "flat":
                c = mix(base, light, 0.15 * n)
            elif pat == "plates":
                c = mix(base, dark if n < 0.5 else light, abs(n - 0.5) * 0.4)
                if y % 4 == 0 or (x + (y // 4) * 2) % 6 == 0:
                    c = mix(c, dark, 0.55)
                if y % 4 == 1:
                    c = mix(c, light, 0.25)
            elif pat == "bricks":
                c = mix(base, dark if n < 0.5 else light, abs(n - 0.5) * 0.5)
                off = 3 if (y // 4) % 2 else 0
                if y % 4 == 3 or (x + off) % 6 == 5:
                    c = mix(c, dark, 0.6)
            elif pat == "planks":
                c = mix(base, dark if n < 0.5 else light, abs(n - 0.5) * 0.35)
                if y % 4 == 3:
                    c = mix(c, dark, 0.6)
                if (x * 7 + y // 4 * 13) % 11 == 0 and y % 4 == 1:
                    c = mix(c, dark, 0.3)
            elif pat == "metal":
                t = y / max(1, h - 1)
                c = mix(light, dark, t * 0.8)
                if n > 0.92:
                    c = light
            elif pat == "crystal":
                t = ((x + y) % 5) / 4.0
                c = mix(light, dark, t * 0.75)
                if n > 0.9:
                    c = (255, 255, 255)
            elif pat == "veins":
                c = mix(base, dark, n * 0.4)
                if (x * 3 + y * 5 + int(n * 3)) % 9 == 0:
                    c = hexc(m["vein"])
                    glow = True
            elif pat == "robe":
                c = mix(base, dark if n < 0.5 else light, abs(n - 0.5) * 0.4)
                if x % 5 == 2:
                    c = mix(c, dark, 0.35)
                if y == h - 1 or y == h - 2:
                    c = hexc(m["trim"])
            elif pat == "star":
                cx, cy = (w - 1) / 2, (h - 1) / 2
                d = math.hypot(x - cx, y - cy) / max(1, max(w, h) / 2)
                c = mix(light, dark, min(1, d * 1.1))
            elif pat == "nebula":
                v = (math.sin(x * 0.45 + cube_id) + math.sin(y * 0.6 + x * 0.2) + 2) / 4
                c = mix(dark, mix(base, hexc("#7a3fbf"), v), 0.5 + v * 0.5)
                if n > 0.965:
                    c = (255, 255, 240)
                    glow = True
            elif pat == "stars":
                c = mix(base, dark, n * 0.5)
                if n > 0.96:
                    c = (255, 240, 200)
                    glow = True
            elif pat == "feather":
                t = y / max(1, h - 1)
                c = mix(light, dark, t * 0.85)
                if x % 2 == 1:
                    c = mix(c, dark, 0.18)
                if n > 0.93:
                    c = light
            elif pat == "edged":
                c = mix(base, dark if n < 0.5 else light, abs(n - 0.5) * 0.4)
                if x == 0 or x == w - 1:
                    c = hexc(m["edge"])
                    glow = True
            elif pat == "glyph":
                c = mix(base, dark if n < 0.5 else light, abs(n - 0.5) * 0.45)
                if y % 5 == 4:
                    c = mix(c, dark, 0.45)
                if (x * 7 + y * 3) % 11 == 0 and y % 5 == 2:
                    c = hexc(m["vein"])
                    glow = True
            elif pat == "mask":
                c = mix(base, dark, n * 0.25)
            elif pat == "corona":
                cx, cy = (w - 1) / 2, (h - 1) / 2
                d = math.hypot(x - cx, y - cy) / max(1, min(w, h) / 2)
                if d < 0.62:
                    c = (0, 0, 0)
                    glow = False
                elif d < 1.05:
                    c = mix(hexc("#fff4d6"), hexc("#c27bff"), (d - 0.62) / 0.43)
                    glow = True
                else:
                    c = hexc("#3a1660")
            if not m.get("glow", False) and pat not in ("corona",):
                c = shade(c, face_mul)
            # Rune lines for the Sovereign's gauntlets.
            if "rune" in m and face in ("north", "east", "west") and (x == w // 2 or y == h // 2) and (x + y) % 2 == 0:
                c = hexc(m["rune"])
                glow = True
            row.append((clamp(c), 255, glow))
        out.append(row)
    # Edge darkening for crisp silhouettes.
    if pat not in ("corona", "star", "crystal", "flat", "edged"):
        for y in range(h):
            for x in range(w):
                if x == 0 or y == 0 or x == w - 1 or y == h - 1:
                    c, a, g = out[y][x]
                    if not g:
                        out[y][x] = (shade(c, 0.78), a, g)
    return out


# ----------------------------------------------------------------------------------------------------------------
# Model description helpers
# ----------------------------------------------------------------------------------------------------------------

class Cube:
    def __init__(self, origin, size, mat, inflate=0.0, mirror=False, decals=None):
        self.origin = origin
        self.size = size
        self.mat = mat
        self.inflate = inflate
        self.mirror = mirror
        self.decals = decals or []  # (face, x, y, w, h, hexcolor, glow)
        self.uv = None


class Part:
    def __init__(self, name, pivot=(0, 0, 0), rot=(0, 0, 0), cubes=(), children=()):
        self.name = name
        self.pivot = pivot
        self.rot = rot
        self.cubes = list(cubes)
        self.children = list(children)


def C(origin, size, mat, **kw):
    return Cube(origin, size, mat, **kw)


def P(name, pivot=(0, 0, 0), rot=(0, 0, 0), cubes=(), children=()):
    return Part(name, pivot, rot, cubes, children)


def all_cubes(parts):
    for p in parts:
        for c in p.cubes:
            yield c
        yield from all_cubes(p.children)


def footprint(c):
    w, h, d = (int(math.ceil(v)) for v in c.size)
    return 2 * (d + w), d + h


def pack(parts, tex_w, tex_h):
    cubes = sorted(all_cubes(parts), key=lambda c: -footprint(c)[1])
    x = y = shelf = 0
    for c in cubes:
        fw, fh = footprint(c)
        if x + fw > tex_w:
            x = 0
            y += shelf
            shelf = 0
        if y + fh > tex_h:
            raise SystemExit(f"texture overflow at cube {c.size} ({tex_w}x{tex_h})")
        c.uv = (x, y)
        x += fw
        shelf = max(shelf, fh)


# ----------------------------------------------------------------------------------------------------------------
# Texture painting
# ----------------------------------------------------------------------------------------------------------------

def paint(name, parts, tex_w, tex_h, seed):
    rng = random.Random(seed)
    base = Image.new("RGBA", (tex_w, tex_h), (0, 0, 0, 0))
    glow = Image.new("RGBA", (tex_w, tex_h), (0, 0, 0, 0))
    bp, gp = base.load(), glow.load()
    for idx, c in enumerate(all_cubes(parts)):
        w, h, d = (int(math.ceil(v)) for v in c.size)
        u, v = c.uv
        regions = {
            "top": (u + d, v, w, d),
            "bottom": (u + d + w, v, w, d),
            "west": (u, v + d, d, h),
            "north": (u + d, v + d, w, h),
            "east": (u + d + w, v + d, d, h),
            "south": (u + 2 * d + w, v + d, w, h),
        }
        for face, (fx, fy, fw, fh) in regions.items():
            if fw <= 0 or fh <= 0:
                continue
            pix = face_pixels(c.mat, fw, fh, face, rng, idx)
            for yy in range(fh):
                for xx in range(fw):
                    col, a, g = pix[yy][xx]
                    bp[fx + xx, fy + yy] = col + (a,)
                    if g:
                        gp[fx + xx, fy + yy] = col + (255,)
        for (face, dx, dy, dw, dh, color, is_glow) in c.decals:
            fx, fy, fw, fh = regions[face]
            col = hexc(color)
            for yy in range(dh):
                for xx in range(dw):
                    px, py = fx + dx + xx, fy + dy + yy
                    if fx <= px < fx + fw and fy <= py < fy + fh:
                        bp[px, py] = col + (255,)
                        if is_glow:
                            gp[px, py] = col + (255,)
                        else:
                            gp[px, py] = (0, 0, 0, 0)
    os.makedirs(TEX_OUT, exist_ok=True)
    base.save(os.path.join(TEX_OUT, f"{name}.png"))
    glow.save(os.path.join(TEX_OUT, f"{name}_glow.png"))


# ----------------------------------------------------------------------------------------------------------------
# Java emission
# ----------------------------------------------------------------------------------------------------------------

def jf(v):
    s = f"{v:.4f}".rstrip("0").rstrip(".")
    if s in ("-0", ""):
        s = "0"
    return s + "F"


def emit_part(p, parent_var, lines, depth):
    var = f"p{len(lines)}_{p.name}"
    builder = "CubeListBuilder.create()"
    for c in p.cubes:
        builder += f".texOffs({c.uv[0]}, {c.uv[1]})"
        if c.mirror:
            builder += ".mirror()"
        o, s = c.origin, c.size
        if c.inflate:
            builder += f".addBox({jf(o[0])}, {jf(o[1])}, {jf(o[2])}, {jf(s[0])}, {jf(s[1])}, {jf(s[2])}, new CubeDeformation({jf(c.inflate)}))"
        else:
            builder += f".addBox({jf(o[0])}, {jf(o[1])}, {jf(o[2])}, {jf(s[0])}, {jf(s[1])}, {jf(s[2])})"
        if c.mirror:
            builder += ".mirror(false)"
    rx, ry, rz = (math.radians(a) for a in p.rot)
    pose = f"PartPose.offsetAndRotation({jf(p.pivot[0])}, {jf(p.pivot[1])}, {jf(p.pivot[2])}, {jf(rx)}, {jf(ry)}, {jf(rz)})"
    lines.append(f"        PartDefinition {var} = {parent_var}.addOrReplaceChild(\"{p.name}\", {builder}, {pose});")
    for child in p.children:
        emit_part(child, var, lines, depth + 1)


def emit_method(method, parts, tex_w, tex_h):
    lines = []
    for p in parts:
        emit_part(p, "root", lines, 0)
    body = "\n".join(lines)
    return f"""
    public static LayerDefinition {method}() {{
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
{body}
        return LayerDefinition.create(mesh, {tex_w}, {tex_h});
    }}
"""


# ----------------------------------------------------------------------------------------------------------------
# The creatures
# ----------------------------------------------------------------------------------------------------------------

def star_mite():
    legs = []
    for i, z in enumerate((-2.5, 0.5, 3.5)):
        for side, sx in (("right", -1), ("left", 1)):
            legs.append(P(f"leg_{side}_{i}", pivot=(sx * 3.0, 20.0, z), rot=(0, 0, sx * 35),
                          cubes=[C((0 if sx > 0 else -9, -1, -1), (9, 2, 2), "mite_leg")]))
    crystals = [
        P("crystal_a", pivot=(0, -2, 1), rot=(-15, 0, 10), cubes=[C((-1, -5, -1), (2, 5, 2), "mite_crystal")]),
        P("crystal_b", pivot=(-2, -2, 3), rot=(10, 0, -25), cubes=[C((-1, -4, -1), (2, 4, 2), "mite_crystal")]),
        P("crystal_c", pivot=(2, -2, 4), rot=(20, 0, 25), cubes=[C((-1, -3, -1), (2, 3, 2), "mite_crystal")]),
    ]
    return [
        P("head", pivot=(0, 20.5, -4), cubes=[C((-2.5, -2, -4), (5, 4, 4), "mite_shell",
                                                decals=[("north", 0, 1, 2, 1, "#ffe36b", True), ("north", 3, 1, 2, 1, "#ffe36b", True)])],
          children=[P("mandibles", pivot=(0, 1, -4), cubes=[C((-2, -0.5, -2), (4, 1, 2), "mite_leg")])]),
        P("body", pivot=(0, 20, 0), cubes=[C((-3, -2.5, -4), (6, 5, 6), "mite_shell")]),
        P("abdomen", pivot=(0, 19.5, 2), cubes=[C((-4, -2.5, 0), (8, 5, 8), "mite_shell")], children=crystals),
    ] + legs, 64, 64


def void_stalker():
    claw = lambda side: [P(f"claw_{side}_{i}", pivot=(x, 21, 0), cubes=[C((-0.5, 0, -0.5), (1, 5, 1), "stalker_claw")])
                         for i, x in enumerate((-1, 0, 1))]
    return [
        P("head", pivot=(0, -15, 0), cubes=[C((-3, -10, -3.5), (6, 10, 7), "stalker_head",
                                              decals=[("north", 1, 4, 1, 1, "#d27bff", True), ("north", 4, 4, 1, 1, "#d27bff", True),
                                                      ("north", 1, 5, 1, 1, "#7a2fd6", True), ("north", 4, 5, 1, 1, "#7a2fd6", True)])]),
        P("body", pivot=(0, -15, 0), cubes=[C((-4.5, 0, -2.5), (9, 15, 5), "stalker_skin")]),
        P("right_arm", pivot=(-6, -13, 0), cubes=[C((-1.5, -1.5, -1.5), (3, 22, 3), "stalker_skin")], children=claw("right")),
        P("left_arm", pivot=(6, -13, 0), cubes=[C((-1.5, -1.5, -1.5), (3, 22, 3), "stalker_skin")], children=claw("left")),
        P("right_leg", pivot=(-2.5, 0, 0), cubes=[C((-1.5, 0, -1.5), (3, 24, 3), "stalker_skin")]),
        P("left_leg", pivot=(2.5, 0, 0), cubes=[C((-1.5, 0, -1.5), (3, 24, 3), "stalker_skin")]),
    ], 64, 64


def astral_golem():
    back = [
        P("back_crystal_a", pivot=(-4, -8, 6), rot=(35, 0, -15), cubes=[C((-2, -9, -2), (4, 9, 4), "golem_crystal")]),
        P("back_crystal_b", pivot=(3, -6, 6), rot=(45, 0, 20), cubes=[C((-1.5, -7, -1.5), (3, 7, 3), "golem_crystal")]),
        P("back_crystal_c", pivot=(0, -1, 6), rot=(60, 0, 0), cubes=[C((-1.5, -6, -1.5), (3, 6, 3), "golem_crystal")]),
    ]
    shoulder = lambda side, sx: P(f"{side}_shoulder_crystal", pivot=(0, -2, 0), rot=(0, 0, sx * 20),
                                  cubes=[C((-1.5, -7, -1.5), (3, 7, 3), "golem_crystal")])
    return [
        P("body", pivot=(0, -8, 0), cubes=[C((-10, -10, -6), (20, 16, 12), "golem_stone"),
                                           C((-3, -6, -6.6), (6, 6, 1), "golem_core")], children=back),
        P("head", pivot=(0, -17, -3), cubes=[C((-4, -7, -5), (8, 8, 8), "golem_dark",
                                                decals=[("north", 1, 3, 2, 1, "#7ff0ff", True), ("north", 5, 3, 2, 1, "#7ff0ff", True)])]),
        P("right_arm", pivot=(-12, -15, 0), cubes=[C((-6, -2, -3.5), (7, 28, 7), "golem_stone")], children=[shoulder("right", -1)]),
        P("left_arm", pivot=(12, -15, 0), cubes=[C((-1, -2, -3.5), (7, 28, 7), "golem_stone")], children=[shoulder("left", 1)]),
        P("right_leg", pivot=(-5, 0, 0), cubes=[C((-4, -2, -4), (8, 26, 8), "golem_dark")]),
        P("left_leg", pivot=(5, 0, 0), cubes=[C((-4, -2, -4), (8, 26, 8), "golem_dark")]),
    ], 128, 128


def mimic():
    top_teeth = [C((-6 + i * 2.5, 0, -13.5), (1, 2, 1), "tooth") for i in range(5)] + \
                [C((-6.5, 0, -12 + i * 3), (1, 2, 1), "tooth") for i in range(4)] + \
                [C((5.5, 0, -12 + i * 3), (1, 2, 1), "tooth") for i in range(4)]
    bottom_teeth = [C((-5 + i * 2.5, -12, -6.5), (1, 2, 1), "tooth") for i in range(5)]
    return [
        P("base", pivot=(0, 24, 0), cubes=[C((-7, -10, -7), (14, 10, 14), "chest_wood"),
                                            C((-6, -10.05, -6), (12, 1, 12), "mouth")] + bottom_teeth,
          children=[P("tongue", pivot=(0, -10, 2), cubes=[C((-3, -1.2, -7), (6, 1, 8), "tongue")])]),
        P("lid", pivot=(0, 14, 7), cubes=[C((-7, -5, -14), (14, 5, 14), "chest_wood"),
                                           C((-6, -0.05, -13), (12, 0.1, 12), "mouth"),
                                           C((-1, -2, -15), (2, 4, 1), "chest_latch")] + top_teeth),
    ], 128, 64


def astral_wraith():
    return [
        P("hood", pivot=(0, -2, 0), cubes=[C((-4, -8, -4), (8, 8, 8), "robe"),
                                           C((-3, -6.5, -4.3), (6, 5, 1), "wraith_face",
                                             decals=[("north", 1, 2, 1, 1, "#8ff7ff", True), ("north", 4, 2, 1, 1, "#8ff7ff", True)])]),
        P("body", pivot=(0, -2, 0), cubes=[C((-4, 0, -2.5), (8, 11, 5), "robe")]),
        P("skirt", pivot=(0, 9, 0), cubes=[C((-5, 0, -3.5), (10, 10, 7), "robe_dark")],
          children=[P("tatters", pivot=(0, 10, 0), cubes=[C((-5, 0, -3.5), (10, 4, 7), "robe_dark")])]),
        P("right_arm", pivot=(-5, 0, 0), cubes=[C((-2, -1, -1.5), (3, 11, 3), "robe"), C((-1.5, 10, -1), (2, 2, 2), "wraith_hand")],
          children=[P("staff", pivot=(-0.5, 11, -1), rot=(-90, 0, 0), cubes=[C((-0.5, -10, -0.5), (1, 22, 1), "staff_wood")],
                      children=[P("lens", pivot=(0, -10, 0), cubes=[C((-1.5, -3, -1.5), (3, 3, 3), "staff_lens")])])]),
        P("left_arm", pivot=(5, 0, 0), cubes=[C((-1, -1, -1.5), (3, 11, 3), "robe"), C((-0.5, 10, -1), (2, 2, 2), "wraith_hand")]),
    ], 64, 64


def starling():
    points = [
        P("point_top", pivot=(0, -3, 0), rot=(0, 0, 0), cubes=[C((-1, -3, -1), (2, 3, 2), "starling_body")]),
        P("point_left", pivot=(3, -1, 0), rot=(0, 0, -72), cubes=[C((-1, -3, -1), (2, 3, 2), "starling_body")]),
        P("point_right", pivot=(-3, -1, 0), rot=(0, 0, 72), cubes=[C((-1, -3, -1), (2, 3, 2), "starling_body")]),
        P("point_bl", pivot=(-2, 3, 0), rot=(0, 0, 144), cubes=[C((-1, -3, -1), (2, 3, 2), "starling_body")]),
        P("point_br", pivot=(2, 3, 0), rot=(0, 0, -144), cubes=[C((-1, -3, -1), (2, 3, 2), "starling_body")]),
    ]
    wings = [
        P("right_wing", pivot=(-2, -1, 2.5), cubes=[C((-6, -0.5, 0), (6, 1, 4), "starling_wing")]),
        P("left_wing", pivot=(2, -1, 2.5), cubes=[C((0, -0.5, 0), (6, 1, 4), "starling_wing")]),
    ]
    return [
        P("core", pivot=(0, 18, 0), cubes=[C((-3, -3, -3), (6, 6, 6), "starling_body",
                                             decals=[("north", 1, 2, 1, 2, "#2a1a50", False), ("north", 4, 2, 1, 2, "#2a1a50", False),
                                                     ("north", 1, 2, 1, 1, "#ffffff", False), ("north", 4, 2, 1, 1, "#ffffff", False),
                                                     ("north", 2, 4, 2, 1, "#ff9a7a", True)])],
          children=points + wings),
    ], 32, 32


def nebula_ray():
    tail = P("tail1", pivot=(0, 0, 10), cubes=[C((-1.5, -1, 0), (3, 2, 10), "ray_skin")],
             children=[P("tail2", pivot=(0, 0, 10), cubes=[C((-1, -0.5, 0), (2, 1, 10), "ray_skin")],
                         children=[P("tail3", pivot=(0, 0, 10), cubes=[C((-0.5, -0.5, 0), (1, 1, 12), "ray_fin")])])])
    return [
        P("body", pivot=(0, 20, 0), cubes=[C((-8, -2, -10), (16, 4, 20), "ray_skin"),
                                           C((-7, 2, -9), (14, 1, 18), "ray_belly")],
          children=[
              P("right_wing", pivot=(-8, 0, -2), cubes=[C((-14, -1, -8), (14, 2, 16), "ray_skin")],
                children=[P("right_wing_tip", pivot=(-14, 0, 0), cubes=[C((-12, -0.5, -6), (12, 1, 10), "ray_fin")])]),
              P("left_wing", pivot=(8, 0, -2), cubes=[C((0, -1, -8), (14, 2, 16), "ray_skin")],
                children=[P("left_wing_tip", pivot=(14, 0, 0), cubes=[C((0, -0.5, -6), (12, 1, 10), "ray_fin")])]),
              P("right_horn", pivot=(-5, 0, -10), rot=(0, 15, 0), cubes=[C((-1, -1, -6), (2, 2, 6), "ray_fin",
                                                                            decals=[("east", 3, 0, 1, 1, "#9ff3ff", True)])]),
              P("left_horn", pivot=(5, 0, -10), rot=(0, -15, 0), cubes=[C((-1, -1, -6), (2, 2, 6), "ray_fin",
                                                                           decals=[("west", 2, 0, 1, 1, "#9ff3ff", True)])]),
              tail,
          ]),
    ], 128, 128


def eclipse_sovereign():
    crown = [P(f"crown_{i}", pivot=(x, -10, z), rot=(rx, 0, rz), cubes=[C((-1, -h, -1), (2, h, 2), "sov_gold")])
             for i, (x, z, h, rx, rz) in enumerate([(0, -4, 9, -10, 0), (-3.5, -3, 7, -8, -18), (3.5, -3, 7, -8, 18),
                                                    (-5, 1, 5, 0, -28), (5, 1, 5, 0, 28)])]
    crown.append(P("crown_gem", pivot=(0, -10, -4.5), cubes=[C((-1.5, -3, -1), (3, 3, 2), "sov_gem")]))
    halo = [P(f"halo_{i}", pivot=(math.cos(a) * 11, math.sin(a) * 11, 0), rot=(0, 0, math.degrees(a)),
              cubes=[C((-1, -3, -0.5), (2, 6, 1), "sov_halo")]) for i, a in enumerate([k * math.pi / 6 for k in range(12)])]
    fingers = lambda side: [P(f"{side}_finger_{i}", pivot=(x, 6, -3), cubes=[C((-1, 0, -1), (2, 6, 2), "sov_hand")])
                            for i, x in enumerate((-3, -1, 1, 3))]
    cape = [P(f"cape_{i}", pivot=(x, -14, 5), cubes=[C((-3, 0, 0), (6, 26, 1), "sov_robe")]) for i, x in enumerate((-6, 0, 6))]
    return [
        P("body", pivot=(0, -10, 0), children=[
            P("waist", pivot=(0, 0, 0), cubes=[C((-7, 0, -4.5), (14, 12, 9), "sov_robe")],
              children=[P("trail", pivot=(0, 12, 0), cubes=[C((-5, 0, -3.5), (10, 10, 7), "sov_robe")],
                          children=[P("trail_tip", pivot=(0, 10, 0), cubes=[C((-3, 0, -2), (6, 8, 4), "sov_robe")])])]),
            P("chest", pivot=(0, 0, 0), cubes=[C((-9, -16, -5.5), (18, 16, 11), "sov_armor"),
                                               C((-10, -16, -6), (20, 3, 12), "sov_gold")],
              children=[
                  P("core", pivot=(0, -9, -5.6), cubes=[C((-4, -4, -1), (8, 8, 1), "sov_core")]),
                  P("right_pauldron", pivot=(-10, -15, 0), rot=(0, 0, 12), cubes=[C((-7, -4, -6), (8, 6, 12), "sov_armor"),
                                                                                  C((-7.5, -4.5, -6.5), (8, 1, 13), "sov_gold")]),
                  P("left_pauldron", pivot=(10, -15, 0), rot=(0, 0, -12), cubes=[C((-1, -4, -6), (8, 6, 12), "sov_armor"),
                                                                                 C((-0.5, -4.5, -6.5), (8, 1, 13), "sov_gold")]),
                  P("head", pivot=(0, -16, 0), cubes=[C((-5, -10, -5), (10, 10, 10), "sov_armor"),
                                                      C((-4, -8, -5.3), (8, 7, 1), "sov_face",
                                                        decals=[("north", 1, 2, 2, 1, "#ff3ad8", True), ("north", 5, 2, 2, 1, "#ff3ad8", True),
                                                                ("north", 1, 3, 2, 1, "#7a0fd6", True), ("north", 5, 3, 2, 1, "#7a0fd6", True),
                                                                ("north", 3, 5, 2, 1, "#2a1a3a", False)])],
                    children=crown + [P("halo", pivot=(0, -6, 8), children=halo)]),
              ] + cape),
        ]),
        P("right_hand", pivot=(-22, -14, -2), cubes=[C((-5, -6, -4), (10, 12, 8), "sov_hand"),
                                                     C((-5.5, -6.5, -4.5), (11, 3, 9), "sov_gold")], children=fingers("right")),
        P("left_hand", pivot=(22, -14, -2), cubes=[C((-5, -6, -4), (10, 12, 8), "sov_hand"),
                                                   C((-5.5, -6.5, -4.5), (11, 3, 9), "sov_gold")], children=fingers("left")),
    ], 256, 128


def eclipse_crystal():
    return [
        P("crystal", pivot=(0, 8, 0), children=[
            P("outer", pivot=(0, 0, 0), rot=(45, 0, 45), cubes=[C((-5, -5, -5), (10, 10, 10), "crystal_purple")]),
            P("inner", pivot=(0, 0, 0), rot=(0, 45, 0), cubes=[C((-3, -8, -3), (6, 16, 6), "crystal_purple")]),
            P("heart", pivot=(0, 0, 0), cubes=[C((-2, -2, -2), (4, 4, 4), "crystal_core")]),
        ]),
    ], 64, 64


# ----------------------------------------------------------------------------------------------------------------
# Sunforged creatures
# ----------------------------------------------------------------------------------------------------------------

def cinder_imp():
    eyes = [("north", 1, 2, 1, 1, "#fff2a0", True), ("north", 4, 2, 1, 1, "#fff2a0", True),
            ("north", 1, 4, 4, 1, "#ff8a20", True), ("north", 2, 5, 2, 1, "#ff8a20", True)]
    horn = lambda side, sx: P(f"{side}_horn", pivot=(sx * 2.0, -6, -1), rot=(-20, 0, sx * 25), cubes=[C((-0.5, -4, -0.5), (1, 4, 1), "imp_horn")])
    return [
        P("head", pivot=(0, 12, 0), cubes=[C((-3, -6, -3), (6, 6, 6), "imp_skin", decals=eyes)], children=[horn("right", -1), horn("left", 1)]),
        P("body", pivot=(0, 12, 0), cubes=[C((-2.5, 0, -1.5), (5, 6, 3), "imp_skin", decals=[("north", 2, 2, 1, 1, "#ffb030", True)])]),
        P("right_arm", pivot=(-3.5, 12.5, 0), cubes=[C((-1, 0, -1), (2, 6, 2), "imp_skin")]),
        P("left_arm", pivot=(3.5, 12.5, 0), cubes=[C((-1, 0, -1), (2, 6, 2), "imp_skin")]),
        P("right_leg", pivot=(-1.3, 18, 0), cubes=[C((-1, 0, -1), (2, 5, 2), "imp_skin")]),
        P("left_leg", pivot=(1.3, 18, 0), cubes=[C((-1, 0, -1), (2, 5, 2), "imp_skin")]),
        P("right_wing", pivot=(-1.5, 13, 1.5), rot=(0, 30, 0), cubes=[C((-9, -4, 0), (9, 8, 1), "imp_wing")]),
        P("left_wing", pivot=(1.5, 13, 1.5), rot=(0, -30, 0), cubes=[C((0, -4, 0), (9, 8, 1), "imp_wing")]),
        P("tail", pivot=(0, 17, 1.5), rot=(40, 0, 0), cubes=[C((-0.5, -0.5, 0), (1, 1, 6), "imp_skin")],
          children=[P("tail_tip", pivot=(0, 0, 6), cubes=[C((-1, -1, 0), (2, 2, 2), "ember_glow")])]),
    ], 64, 64


def magma_crawler():
    eyes = [("west", 1, 0, 1, 1, "#ffe08a", True), ("east", 4, 0, 1, 1, "#ffe08a", True),
            ("top", 0, 1, 1, 1, "#ffb030", True), ("top", 5, 1, 1, 1, "#ffb030", True)]
    spines = [P(f"spine_{i}", pivot=(0, -2.5, z), rot=(-20, 0, 0), cubes=[C((-1, -h, -1), (2, h, 2), "ember_glow")])
              for i, (z, h) in enumerate([(-4, 4), (0, 5), (4, 3)])]
    leg = lambda name, x, z, sx: P(name, pivot=(x, 19, z), rot=(0, 0, sx * 30), cubes=[C((-1.5, 0, -1.5), (3, 5, 3), "crawler_rock")])
    return [
        P("body", pivot=(0, 19, 0), cubes=[C((-4, -2.5, -7), (8, 5, 14), "crawler_rock"), C((-3.5, 2.5, -6), (7, 0.5, 12), "crawler_belly")],
          children=spines),
        P("head", pivot=(0, 18.5, -7), cubes=[C((-3, -2, -6), (6, 3, 6), "crawler_rock", decals=eyes)],
          children=[P("jaw", pivot=(0, 1, -1), cubes=[C((-2.5, 0, -5), (5, 1.5, 5), "crawler_belly")])]),
        leg("right_front_leg", -4.5, -4, 1), leg("left_front_leg", 4.5, -4, -1),
        leg("right_hind_leg", -4.5, 4, 1), leg("left_hind_leg", 4.5, 4, -1),
        P("tail1", pivot=(0, 19, 7), cubes=[C((-2.5, -2, 0), (5, 4, 7), "crawler_rock")],
          children=[P("tail2", pivot=(0, 0, 7), cubes=[C((-1.5, -1.5, 0), (3, 3, 7), "crawler_rock")],
                      children=[P("tail3", pivot=(0, 0, 7), cubes=[C((-1, -1, 0), (2, 2, 6), "crawler_rock")])])]),
    ], 128, 64


def ember_hound():
    eyes = [("north", 1, 2, 1, 1, "#ffe08a", True), ("north", 4, 2, 1, 1, "#ffe08a", True)]
    ear = lambda side, sx: P(f"{side}_ear", pivot=(sx * 2.0, -3, -1), cubes=[C((-1, -3, -0.5), (2, 3, 1), "hound_coal")])
    leg = lambda name, x, z: P(name, pivot=(x, 17, z), cubes=[C((-1, 0, -1), (2, 7, 2), "hound_coal")])
    return [
        P("body", pivot=(0, 14, 2), cubes=[C((-3, -3, -6), (6, 6, 11), "hound_coal")]),
        P("mane", pivot=(0, 13, -3), cubes=[C((-4, -4, -3), (8, 8, 4), "hound_flame")]),
        P("head", pivot=(0, 12, -6), cubes=[C((-3, -3, -4), (6, 6, 4), "hound_coal", decals=eyes)],
          children=[P("snout", pivot=(0, 0.5, -4), cubes=[C((-1.5, -1, -3), (3, 3, 3), "hound_coal",
                                                              decals=[("north", 1, 0, 1, 1, "#0a0807", False)])]),
                    ear("right", -1), ear("left", 1)]),
        leg("right_front_leg", -1.8, -3), leg("left_front_leg", 1.8, -3),
        leg("right_hind_leg", -1.8, 5), leg("left_hind_leg", 1.8, 5),
        P("tail", pivot=(0, 12, 7), rot=(50, 0, 0), cubes=[C((-1, 0, -1), (2, 8, 2), "hound_flame")]),
    ], 64, 64


def ashen_knight():
    visor = [("north", 1, 4, 7, 1, "#ff8a20", True), ("north", 4, 5, 1, 2, "#ff8a20", True)]
    return [
        P("head", pivot=(0, 0, 0), cubes=[C((-4.5, -9, -4.5), (9, 9, 9), "ash_plate", decals=visor)],
          children=[P("plume", pivot=(0, -9, 0), cubes=[C((-0.5, -4, -4), (1, 4, 8), "hound_flame")])]),
        P("body", pivot=(0, 0, 0), cubes=[C((-4.5, 0, -2.5), (9, 12, 5), "ash_plate",
                                             decals=[("north", 3, 2, 3, 3, "#c89a3a", False), ("north", 4, 3, 1, 1, "#ff8a20", True)]),
                                           C((-4.6, 9, -2.6), (9.2, 1, 5.2), "knight_gold")]),
        P("right_arm", pivot=(-6, 2, 0), cubes=[C((-3, -2, -2.5), (5, 12, 5), "ash_plate"), C((-3.5, -3, -3), (6, 4, 6), "knight_gold")],
          children=[P("sword", pivot=(-0.5, 10, -1), rot=(-90, 0, 0), cubes=[C((-0.5, -2, -0.5), (1, 4, 1), "ash_dark")],
                      children=[P("guard", pivot=(0, -2, 0), cubes=[C((-2.5, -1, -1), (5, 1, 2), "knight_gold")]),
                                P("blade", pivot=(0, -3, 0), cubes=[C((-1, -17, -0.5), (2, 17, 1), "knight_blade")])])]),
        P("left_arm", pivot=(6, 2, 0), cubes=[C((-2, -2, -2.5), (5, 12, 5), "ash_plate"), C((-2.5, -3, -3), (6, 4, 6), "knight_gold")],
          children=[P("shield", pivot=(0.5, 7, -3.5), cubes=[C((-5, -8, -1), (10, 15, 1), "shield_face",
                                                               decals=[("north", 3, 4, 4, 4, "#c89a3a", False), ("north", 4, 5, 2, 2, "#ff8a20", True)])])]),
        P("right_leg", pivot=(-2.2, 12, 0), cubes=[C((-2.5, 0, -2.5), (5, 12, 5), "ash_dark")]),
        P("left_leg", pivot=(2.2, 12, 0), cubes=[C((-2.5, 0, -2.5), (5, 12, 5), "ash_dark")]),
    ], 128, 64


def solar_phoenix():
    plumes = [P(f"crest_{i}", pivot=(0, -4, 1 + i), rot=(-40 - i * 12, 0, 0), cubes=[C((-0.5, -5, -0.5), (1, 5, 1), "phoenix_tail")])
              for i in range(3)]
    tail = [P(f"tail_{i}", pivot=(0, 0, 0), rot=(0, yaw, 0), cubes=[C((-1, -0.5, 0), (2, 1, 15), "phoenix_tail")])
            for i, yaw in enumerate((-22, 0, 22))]
    wing = lambda side, sx: P(f"{side}_wing", pivot=(sx * 3.5, -2, -1), cubes=[C((-12 if sx < 0 else 0, -0.5, -4), (12, 1, 9), "phoenix_wing")],
                              children=[P(f"{side}_wing_tip", pivot=(sx * 12, 0, 0),
                                          cubes=[C((-12 if sx < 0 else 0, -0.5, -3), (12, 1, 8), "phoenix_tail")])])
    talon = lambda side, sx: P(f"{side}_talon", pivot=(sx * 1.5, 3.5, 1), rot=(45, 0, 0), cubes=[C((-0.5, 0, -0.5), (1, 4, 1), "phoenix_gold")])
    return [
        P("body", pivot=(0, 14, 0), cubes=[C((-3.5, -3.5, -6), (7, 7, 13), "phoenix_body")],
          children=[
              P("neck", pivot=(0, -2, -6), rot=(-35, 0, 0), cubes=[C((-2, -6, -2), (4, 6, 4), "phoenix_body")],
                children=[P("head", pivot=(0, -6, 0), rot=(35, 0, 0), cubes=[C((-2.5, -4, -3.5), (5, 4, 6), "phoenix_body",
                                                                               decals=[("west", 1, 1, 1, 1, "#fff6c0", True), ("east", 4, 1, 1, 1, "#fff6c0", True)])],
                            children=[P("beak", pivot=(0, -1.5, -3.5), cubes=[C((-1, -1, -3), (2, 2, 3), "phoenix_gold")])] + plumes)]),
              wing("right", -1), wing("left", 1),
              P("tail", pivot=(0, -1, 6.5), rot=(15, 0, 0), children=tail),
              talon("right", -1), talon("left", 1),
          ]),
    ], 128, 64


def sun_warden():
    rays = [P(f"ray_{i}", pivot=(math.cos(a) * 8.5, math.sin(a) * 8.5, 0), rot=(0, 0, math.degrees(a) + 90),
              cubes=[C((-1, -4 if i % 2 == 0 else -2.5, -0.5), (2, 8 if i % 2 == 0 else 5, 1), "warden_ray")])
            for i, a in enumerate([k * math.pi / 8 for k in range(16)])]
    visor = [("north", 1, 5, 8, 1, "#fff2a0", True), ("north", 4, 6, 2, 2, "#ffb030", True)]
    crown = [P(f"crown_{i}", pivot=(x, -11, z), rot=(rx, 0, rz), cubes=[C((-1, -h, -1), (2, h, 2), "warden_plate")])
             for i, (x, z, h, rx, rz) in enumerate([(0, -4, 6, -10, 0), (-3.5, -3.5, 4, -8, -18), (3.5, -3.5, 4, -8, 18)])]
    sword = P("sword", pivot=(0, 5, -1), rot=(-80, 0, 0), cubes=[C((-1, -3, -1), (2, 7, 2), "warden_dark")],
              children=[P("sword_guard", pivot=(0, -3, 0), cubes=[C((-5, -2, -1.5), (10, 2, 3), "warden_plate")]),
                        P("sword_blade", pivot=(0, -5, 0), cubes=[C((-2, -32, -0.5), (4, 32, 1), "warden_blade")])])
    return [
        P("waist", pivot=(0, 2, 0), cubes=[C((-8, -6, -5), (16, 6, 10), "warden_dark"), C((-8.5, -2, -5.5), (17, 2, 11), "warden_plate")],
          children=[P("torso", pivot=(0, -6, 0), cubes=[C((-10, -16, -6), (20, 16, 12), "warden_stone")],
                      children=[
                          P("core", pivot=(0, -9, -6.2), cubes=[C((-4, -4, -0.6), (8, 8, 1), "warden_core")]),
                          P("right_pauldron", pivot=(-12, -14, 0), rot=(0, 0, 10), cubes=[C((-5, -4, -6.5), (10, 7, 13), "warden_plate")]),
                          P("left_pauldron", pivot=(12, -14, 0), rot=(0, 0, -10), cubes=[C((-5, -4, -6.5), (10, 7, 13), "warden_plate")]),
                          P("head", pivot=(0, -16, 0), cubes=[C((-5, -11, -5), (10, 11, 10), "warden_plate", decals=visor)], children=crown),
                          P("halo", pivot=(0, -21, 8), children=[P("halo_disc", pivot=(0, 0, 0), cubes=[C((-6, -6, -0.5), (12, 12, 1), "warden_core")])] + rays),
                      ])]),
        P("right_arm", pivot=(-14, -12, 0), cubes=[C((-3.5, -2, -3.5), (7, 18, 7), "warden_stone")],
          children=[P("right_gauntlet", pivot=(0, 16, 0), cubes=[C((-4, 0, -4), (8, 7, 8), "warden_plate")], children=[sword])]),
        P("left_arm", pivot=(14, -12, 0), cubes=[C((-3.5, -2, -3.5), (7, 18, 7), "warden_stone")],
          children=[P("left_gauntlet", pivot=(0, 16, 0), cubes=[C((-4, 0, -4), (8, 7, 8), "warden_plate")])]),
        P("right_leg", pivot=(-5, 2, 0), cubes=[C((-4, 0, -4), (8, 22, 8), "warden_plate")]),
        P("left_leg", pivot=(5, 2, 0), cubes=[C((-4, 0, -4), (8, 22, 8), "warden_plate")]),
    ], 256, 128


def solar_pylon():
    shards = [P(f"shard_{i}", pivot=(math.cos(i * math.pi / 2) * 5, -2, math.sin(i * math.pi / 2) * 5), rot=(20, i * 90, 15),
                cubes=[C((-1, -2, -1), (2, 4, 2), "pylon_crystal")]) for i in range(4)]
    return [
        P("base", pivot=(0, 24, 0), cubes=[C((-5, -3, -5), (10, 3, 10), "pylon_stone")]),
        P("shaft", pivot=(0, 21, 0), rot=(0, 45, 0), cubes=[C((-2.5, -24, -2.5), (5, 24, 5), "pylon_stone")]),
        P("top", pivot=(0, -7, 0), children=[P("gem", pivot=(0, 0, 0), rot=(45, 0, 45), cubes=[C((-3, -3, -3), (6, 6, 6), "pylon_crystal")])] + shards),
    ], 64, 64


MODELS = [
    ("star_mite", "starMite", star_mite, 101),
    ("void_stalker", "voidStalker", void_stalker, 202),
    ("astral_golem", "astralGolem", astral_golem, 303),
    ("mimic", "mimic", mimic, 404),
    ("astral_wraith", "astralWraith", astral_wraith, 505),
    ("starling", "starling", starling, 606),
    ("nebula_ray", "nebulaRay", nebula_ray, 707),
    ("eclipse_sovereign", "eclipseSovereign", eclipse_sovereign, 808),
    ("eclipse_crystal", "eclipseCrystal", eclipse_crystal, 909),
    ("cinder_imp", "cinderImp", cinder_imp, 1001),
    ("magma_crawler", "magmaCrawler", magma_crawler, 1002),
    ("ember_hound", "emberHound", ember_hound, 1003),
    ("ashen_knight", "ashenKnight", ashen_knight, 1004),
    ("solar_phoenix", "solarPhoenix", solar_phoenix, 1005),
    ("sun_warden", "sunWarden", sun_warden, 1006),
    ("solar_pylon", "solarPylon", solar_pylon, 1007),
]


def main():
    methods = []
    for tex_name, method, fn, seed in MODELS:
        parts, tw, th = fn()
        pack(parts, tw, th)
        paint(tex_name, parts, tw, th, seed)
        methods.append(emit_method(method, parts, tw, th))
        print(f"{tex_name}: {sum(1 for _ in all_cubes(parts))} cubes, {tw}x{th}")
    java = """package com.starforged.client.model;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/**
 * GENERATED by tools/gen_models.py - do not edit by hand. Geometry for every Starforged creature;
 * the matching textures are painted by the same script so UVs always line up.
 */
@SuppressWarnings("unused")
public final class ModelGeometry {
    private ModelGeometry() {
    }
""" + "".join(methods) + "}\n"
    os.makedirs(os.path.dirname(JAVA_OUT), exist_ok=True)
    with open(JAVA_OUT, "w") as f:
        f.write(java)


if __name__ == "__main__":
    main()
