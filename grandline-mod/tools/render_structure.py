#!/usr/bin/env python3
"""Offline structure renderer for the structure audit: textured isometric views and floor cut-aways of the JSON
dumps written by the dev server (GL_SELFTEST=structures -> run/structdump/*.json).

    python3 -I tools/render_structure.py run/structdump/tavern__village.json -o out.png
    python3 -I tools/render_structure.py run/structdump -o renders/            # every dump in a directory
    options: --views 0,2 (0..3, camera from SE/SW/NW/NE)  --scale 10 (pixels per block edge)  --no-plans

Blocks are drawn from their real block-state and model JSON (vanilla assets from the Minecraft client jar, the
mod's from src/main/resources), with Minecraft's face shading. Entity-rendered blocks (chests, beds, banners, signs)
and fluids are approximated with boxes. This is a geometry review aid, not a screenshot of the game.
Needs Pillow and numpy.
"""
import argparse
import glob
import json
import math
import os
import sys
import zipfile
from functools import lru_cache

from PIL import Image, ImageDraw

HERE = os.path.dirname(os.path.abspath(__file__))
MOD_ASSETS = os.path.join(os.path.dirname(HERE), 'src', 'main', 'resources', 'assets')
CLIENT_JAR = os.environ.get('MC_CLIENT_JAR', '/root/.gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar')

_jar = None


def _read(path):
    """assets-relative path like 'minecraft/models/block/stone.json'."""
    global _jar
    p = os.path.join(MOD_ASSETS, path)
    if os.path.exists(p):
        with open(p, 'rb') as f:
            return f.read()
    if _jar is None:
        _jar = zipfile.ZipFile(CLIENT_JAR)
    try:
        return _jar.read('assets/' + path)
    except KeyError:
        return None


def split_id(rid, default_ns='minecraft'):
    if ':' in rid:
        ns, p = rid.split(':', 1)
    else:
        ns, p = default_ns, rid
    return ns, p


@lru_cache(maxsize=None)
def load_json(path):
    b = _read(path)
    return json.loads(b) if b else None


@lru_cache(maxsize=None)
def texture(rid):
    ns, p = split_id(rid)
    b = _read(f'{ns}/textures/{p}.png')
    if b is None:
        im = Image.new('RGBA', (16, 16), (255, 0, 255, 255))
        return im
    import io
    im = Image.open(io.BytesIO(b)).convert('RGBA')
    w, h = im.size
    if h > w:  # animated strip: first frame
        im = im.crop((0, 0, w, w))
    return im


@lru_cache(maxsize=None)
def opaque_texture(rid):
    im = texture(rid)
    return im.getextrema()[3][0] == 255


# ------------------------------------------------------------------ block states and models

def parse_state(s):
    if '[' in s:
        name, props = s[:-1].split('[', 1)
        d = dict(kv.split('=', 1) for kv in props.split(',') if kv)
    else:
        name, d = s, {}
    return name, d


def _matches(cond, props):
    if 'OR' in cond:
        return any(_matches(c, props) for c in cond['OR'])
    if 'AND' in cond:
        return all(_matches(c, props) for c in cond['AND'])
    for k, v in cond.items():
        if str(props.get(k)) not in str(v).split('|'):
            return False
    return True


@lru_cache(maxsize=None)
def resolve_model(rid):
    """-> (textures dict, elements list) with the parent chain merged."""
    ns, p = split_id(rid)
    m = load_json(f'{ns}/models/{p}.json')
    if m is None:
        return {}, None, None
    tex, elements, special = {}, None, None
    if 'parent' in m:
        if m['parent'].startswith('builtin/'):
            special = m['parent']
        else:
            ptex, pel, special = resolve_model(m['parent'])
            tex.update(ptex)
            elements = pel
    tex.update(m.get('textures', {}))
    if 'elements' in m:
        elements = m['elements']
    return tex, elements, special


def resolve_tex(tex, ref, depth=0):
    while ref.startswith('#') and depth < 10:
        ref = tex.get(ref[1:], '')
        depth += 1
    return ref or 'minecraft:block/stone'


@lru_cache(maxsize=None)
def models_for(state):
    """List of (model id, x, y) for a serialized block state."""
    name, props = parse_state(state)
    ns, p = split_id(name)
    bs = load_json(f'{ns}/blockstates/{p}.json')
    if bs is None:
        return []
    out = []

    def add(v):
        if isinstance(v, list):
            v = v[0]
        out.append((v['model'], v.get('x', 0), v.get('y', 0)))
    if 'variants' in bs:
        best = None
        for key, v in bs['variants'].items():
            if key == '':
                best = best or v
                continue
            conds = dict(kv.split('=', 1) for kv in key.split(','))
            if all(props.get(k) == val for k, val in conds.items()):
                best = v
                break
        if best is not None:
            add(best)
    for part in bs.get('multipart', []):
        if 'when' not in part or _matches(part['when'], props):
            add(part['apply'])
    return out


# ------------------------------------------------------------------ geometry

DIRS = {'up': (0, 1, 0), 'down': (0, -1, 0), 'north': (0, 0, -1), 'south': (0, 0, 1), 'west': (-1, 0, 0), 'east': (1, 0, 0)}
SHADE = {'up': 1.0, 'down': 0.5, 'north': 0.8, 'south': 0.8, 'west': 0.6, 'east': 0.6}


def face_corners(d, f, t):
    x1, y1, z1 = f
    x2, y2, z2 = t
    return {
        'up': ((x1, y2, z1), (x2, y2, z1), (x1, y2, z2)),
        'down': ((x1, y1, z2), (x2, y1, z2), (x1, y1, z1)),
        'north': ((x2, y2, z1), (x1, y2, z1), (x2, y1, z1)),
        'south': ((x1, y2, z2), (x2, y2, z2), (x1, y1, z2)),
        'west': ((x1, y2, z1), (x1, y2, z2), (x1, y1, z1)),
        'east': ((x2, y2, z2), (x2, y2, z1), (x2, y1, z2)),
    }[d]


def default_uv(d, f, t):
    x1, y1, z1 = f
    x2, y2, z2 = t
    return {
        'up': (x1, z1, x2, z2), 'down': (x1, 16 - z2, x2, 16 - z1),
        'north': (16 - x2, 16 - y2, 16 - x1, 16 - y1), 'south': (x1, 16 - y2, x2, 16 - y1),
        'west': (z1, 16 - y2, z2, 16 - y1), 'east': (16 - z2, 16 - y2, 16 - z1, 16 - y1),
    }[d]


def rot_elem(p, r):
    """Element rotation {origin, axis, angle}."""
    if not r:
        return p
    ox, oy, oz = r['origin']
    a = math.radians(r['angle'])
    c, s = math.cos(a), math.sin(a)
    x, y, z = p[0] - ox, p[1] - oy, p[2] - oz
    if r['axis'] == 'y':
        x, z = x * c + z * s, -x * s + z * c
    elif r['axis'] == 'x':
        y, z = y * c - z * s, y * s + z * c
    else:
        x, y = x * c - y * s, x * s + y * c
    if r.get('rescale'):
        k = 1 / max(abs(c), 1e-6) if abs(r['angle']) == 45 else 1
        if r['axis'] == 'y':
            x, z = x * k, z * k
        elif r['axis'] == 'x':
            y, z = y * k, z * k
        else:
            x, y = x * k, y * k
    return (x + ox, y + oy, z + oz)


def rot_state(p, rx, ry):
    x, y, z = p
    for _ in range((rx // 90) % 4):
        y, z = z, 16 - y
    for _ in range((ry // 90) % 4):
        x, z = 16 - z, x
    return (x, y, z)


def rot_dir(d, rx, ry):
    v = DIRS[d]
    x, y, z = v
    for _ in range((rx // 90) % 4):
        y, z = z, -y
    for _ in range((ry // 90) % 4):
        x, z = -z, x
    for k, vv in DIRS.items():
        if vv == (x, y, z):
            return k
    return d


# fallbacks for entity-rendered blocks and fluids: (from, to, texture, tint)
def special_boxes(name, props):
    n = name.split(':')[1]
    if n.endswith('_bed'):
        col = n[:-4]
        return [((0, 0, 0), (16, 9, 16), f'block/{col}_wool', None)]
    if n in ('chest', 'trapped_chest'):
        return [((1, 0, 1), (15, 14, 15), 'block/oak_planks', None)]
    if n == 'ender_chest':
        return [((1, 0, 1), (15, 14, 15), 'block/obsidian', None)]
    if n.endswith('wall_banner'):
        col = n[:-len('_wall_banner')]
        return [((1, 0, 14), (15, 16, 16), f'block/{col}_wool', None)]
    if n.endswith('_banner'):
        col = n[:-len('_banner')]
        return [((7, 0, 7), (9, 14, 9), 'block/oak_planks', None), ((1, 6, 7), (15, 30, 8), f'block/{col}_wool', None)]
    if n.endswith('_wall_sign') or n.endswith('wall_hanging_sign'):
        wood = n.split('_wall')[0]
        return [((0, 4, 14), (16, 12, 16), f'block/{wood}_planks', None)]
    if n.endswith('_hanging_sign'):
        wood = n[:-len('_hanging_sign')]
        return [((1, 0, 7), (15, 10, 9), f'block/{wood}_planks', None)]
    if n.endswith('_sign'):
        wood = n[:-len('_sign')]
        return [((7, 0, 7), (9, 9, 9), f'block/{wood}_log', None), ((0, 9, 7), (16, 17, 9), f'block/{wood}_planks', None)]
    if 'skull' in n or n.endswith('_head'):
        return [((4, 0, 4), (12, 8, 12), 'block/bone_block_side', None)]
    if n == 'bell':
        return [((4, 3, 4), (12, 12, 12), 'block/gold_block', None)]
    if n == 'decorated_pot':
        return [((1, 0, 1), (15, 16, 15), 'block/terracotta', None)]
    if n == 'conduit':
        return [((5, 5, 5), (11, 11, 11), 'block/prismarine', None)]
    return None


FLUID = {'minecraft:water': ('block/water_still', (63, 118, 228), 150), 'minecraft:lava': ('block/lava_still', None, 255),
         'minecraft:bubble_column': ('block/water_still', (63, 118, 228), 150)}


def tint_for(name):
    n = name.split(':')[1]
    if n in ('spruce_leaves',):
        return (97, 153, 97)
    if n in ('birch_leaves',):
        return (128, 167, 85)
    if 'leaves' in n or n in ('vine', 'lily_pad'):
        return (89, 160, 48)
    if 'water' in n:
        return (63, 118, 228)
    if n in ('melon_stem', 'pumpkin_stem', 'attached_melon_stem', 'attached_pumpkin_stem'):
        return (160, 170, 60)
    return (124, 189, 90)  # grass, ferns, sugar cane


@lru_cache(maxsize=None)
def faces_of(state):
    """All faces of a block state in block units (0..16): list of (dir, (TL, TR, BL) points, texture id, uv, uvrot,
    tint rgb or None, alpha, cullface dir or None)."""
    name, props = parse_state(state)
    faces = []
    sp = special_boxes(name, props)
    fl = FLUID.get(name)
    if sp is not None:
        facing = props.get('facing', 'north')
        ry = {'north': 0, 'east': 90, 'south': 180, 'west': 270}.get(facing, 0)
        if not name.endswith('wall_banner') and not name.endswith('wall_sign'):
            ry = 0
        for f, t, tex, tint in sp:
            for d in DIRS:
                pts = tuple(rot_state(p, 0, ry) for p in face_corners(d, f, t))
                faces.append((rot_dir(d, 0, ry), pts, 'minecraft:' + tex, default_uv(d, f, t), 0, tint, 255, None))
        return faces
    if fl is not None:
        tex, tint, alpha = fl
        top = 14
        for d in DIRS:
            pts = face_corners(d, (0, 0, 0), (16, top, 16))
            faces.append((d, pts, 'minecraft:' + tex, (0, 0, 16, 16), 0, tint, alpha, d if d != 'up' else 'fluidup'))
        return faces
    for mid, rx, ry in models_for(state):
        tex, elements, special = resolve_model(mid)
        if not elements:
            continue
        for el in elements:
            f, t = tuple(el['from']), tuple(el['to'])
            for d, fd in el.get('faces', {}).items():
                pts = tuple(rot_state(rot_elem(p, el.get('rotation')), rx, ry) for p in face_corners(d, f, t))
                uv = tuple(fd.get('uv', default_uv(d, f, t)))
                tint = tint_for(name) if 'tintindex' in fd else None
                cull = rot_dir(fd['cullface'], rx, ry) if 'cullface' in fd else None
                faces.append((rot_dir(d, rx, ry), pts, resolve_tex(tex, fd.get('texture', '')), uv, fd.get('rotation', 0), tint, 255, cull))
    if props.get('waterlogged') == 'true':
        tex, tint, alpha = FLUID['minecraft:water']
        for d in DIRS:
            faces.append((d, face_corners(d, (0, 0, 0), (16, 14, 16)), 'minecraft:' + tex, (0, 0, 16, 16), 0, tint, alpha, d if d != 'up' else 'fluidup'))
    return faces


@lru_cache(maxsize=None)
def is_full_opaque(state):
    name, props = parse_state(state)
    if name in FLUID or props.get('waterlogged') == 'true':
        return False
    if special_boxes(name, props) is not None:
        return False
    ms = models_for(state)
    if len(ms) != 1:
        return False
    tex, elements, special = resolve_model(ms[0][0])
    if not elements or len(elements) != 1:
        return False
    el = elements[0]
    if el['from'] != [0, 0, 0] or el['to'] != [16, 16, 16] or len(el.get('faces', {})) < 6:
        return False
    return all(opaque_texture(resolve_tex(tex, fd.get('texture', ''))) for fd in el['faces'].values())


# ------------------------------------------------------------------ projection

class View:
    """Isometric camera; view v rotates the world by v*90 degrees about the vertical so the camera always sits on +x'/+z'."""

    def __init__(self, v, s):
        self.v = v
        self.ax = s * 0.5 * math.sqrt(3) / 1.0 * 0.82  # x' -> right
        self.bz = s * 0.5 * 0.82                        # depth -> down
        self.cy = s * 0.82                               # up -> up

    def rot(self, x, z):
        for _ in range(self.v % 4):
            x, z = -z, x
        return x, z

    def proj(self, x, y, z):
        x, z = self.rot(x, z)
        return ((x - z) * self.ax, (x + z) * self.bz - y * self.cy)

    def depth(self, x, y, z):
        x, z = self.rot(x, z)
        return x + z + y * 1.0


def draw_face(img, pts2, tex, uv, uvrot, shade, tint, alpha, origin):
    (ax, ay), (bx, by), (cx, cy) = pts2
    det = (bx - ax) * (cy - ay) - (cx - ax) * (by - ay)
    if abs(det) < 0.05:
        return
    im = texture(tex)
    tw, th = im.size
    u1, v1, u2, v2 = uv
    corners = [(u1, v1), (u2, v1), (u2, v2), (u1, v2)]
    k = (uvrot // 90) % 4
    corners = corners[k:] + corners[:k]
    tl, tr, br, bl = corners
    T = [(tl[0] * tw / 16, tl[1] * th / 16), (tr[0] * tw / 16, tr[1] * th / 16), (bl[0] * tw / 16, bl[1] * th / 16)]
    # screen point -> texture point affine (solve with the three corners)
    ox, oy = origin
    P = [(ax + ox, ay + oy), (bx + ox, by + oy), (cx + ox, cy + oy)]
    import numpy as np
    M = np.array([[P[0][0], P[0][1], 1], [P[1][0], P[1][1], 1], [P[2][0], P[2][1], 1]], dtype=float)
    try:
        cu = np.linalg.solve(M, [T[0][0], T[1][0], T[2][0]])
        cv = np.linalg.solve(M, [T[0][1], T[1][1], T[2][1]])
    except np.linalg.LinAlgError:
        return
    # clamp sampling inside the texture
    warped = im.transform(img.size, Image.AFFINE, (cu[0], cu[1], cu[2], cv[0], cv[1], cv[2]), resample=Image.NEAREST)
    mask = Image.new('L', img.size, 0)
    d4 = (P[1][0] + P[2][0] - P[0][0], P[1][1] + P[2][1] - P[0][1])
    quad = [P[0], P[1], d4, P[2]]
    mx = sum(q[0] for q in quad) / 4
    my = sum(q[1] for q in quad) / 4
    grow = []
    for qx, qy in quad:  # grow by half a pixel so neighbouring faces meet without seams
        l = math.hypot(qx - mx, qy - my) or 1
        grow.append((qx + (qx - mx) / l * 0.6, qy + (qy - my) / l * 0.6))
    ImageDraw.Draw(mask).polygon(grow, fill=255)
    r, g, b, a = warped.split()
    if tint is not None:
        r = r.point(lambda x, t=tint[0]: x * t // 255)
        g = g.point(lambda x, t=tint[1]: x * t // 255)
        b = b.point(lambda x, t=tint[2]: x * t // 255)
    if shade != 1.0:
        r = r.point(lambda x, s=shade: int(x * s))
        g = g.point(lambda x, s=shade: int(x * s))
        b = b.point(lambda x, s=shade: int(x * s))
    from PIL import ImageChops
    a = ImageChops.multiply(a, mask)
    if alpha != 255:
        a = a.point(lambda x, al=alpha: x * al // 255)
    img.alpha_composite(Image.merge('RGBA', (r, g, b, a)))


_sprites = {}


def sprite(state, view, cullmask):
    key = (state, view.v, cullmask)
    sp = _sprites.get(key)
    if sp is not None:
        return sp
    pad = 2
    s = view.ax
    W = int(2 * (1 + pad) * view.ax) + 2
    H = int((2 + 2 * pad) * view.bz + (1 + 2 * pad) * view.cy) + 2
    ox, oy = W / 2, (1 + pad) * view.cy + pad * view.bz
    img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    vx, vz = view.rot(1, 1)
    todo = []
    for d, pts, tex, uv, uvrot, tint, alpha, cull in faces_of(state):
        n = DIRS[d]
        nx, nz = view.rot(n[0], n[2])
        if nx + nz + n[1] * 1.0 <= 0.01 and not (d == 'up'):
            continue
        if cull == 'fluidup':
            if cullmask & (1 << 6):
                continue
        elif cull is not None and cullmask & (1 << list(DIRS).index(cull)):
            continue
        p2 = [view.proj(p[0] / 16, p[1] / 16, p[2] / 16) for p in pts]
        cxm = sum(p[0] for p in pts) / 3 / 16
        cym = sum(p[1] for p in pts) / 3 / 16
        czm = sum(p[2] for p in pts) / 3 / 16
        todo.append((view.depth(cxm + n[0] * 0.01, cym + n[1] * 0.01, czm + n[2] * 0.01), p2, tex, uv, uvrot, SHADE[d], tint, alpha))
    todo.sort(key=lambda t: t[0])
    for dep, p2, tex, uv, uvrot, sh, tint, alpha in todo:
        draw_face(img, p2, tex, uv, uvrot, sh, tint, alpha, (ox, oy))
    bbox = img.getbbox()
    if bbox is None:
        sp = (None, 0, 0)
    else:
        sp = (img.crop(bbox), bbox[0] - ox, bbox[1] - oy)
    _sprites[key] = sp
    return sp


# ------------------------------------------------------------------ scenes

def load_dump(path):
    with open(path) as f:
        d = json.load(f)
    pal = d['palette']
    bl = d['blocks']
    blocks = {}
    for i in range(0, len(bl), 4):
        blocks[(bl[i], bl[i + 1], bl[i + 2])] = pal[bl[i + 3]]
    d['map'] = blocks
    return d


def add_ground(d):
    """Grass (or sea) under the footprint margin where nothing was built, so pieces are seen standing on something."""
    blocks = d['map']
    if not blocks:
        return
    xs = [k[0] for k in blocks]
    zs = [k[2] for k in blocks]
    x0, x1, z0, z1 = min(xs) - 3, max(xs) + 3, min(zs) - 3, max(zs) + 3
    if d['kind'] == 'town':
        return
    cols = {}
    for (x, y, z) in blocks:
        cols.setdefault((x, z), []).append(y)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if (x, -1, z) in blocks:
                continue
            ys = cols.get((x, z), [])
            if d.get('sea'):
                if not any(y <= -1 for y in ys):
                    blocks[(x, -1, z)] = 'minecraft:water'
            elif not any(y <= -1 for y in ys):
                blocks[(x, -1, z)] = 'minecraft:grass_block[snowy=false]'


NB = [(0, 1, 0), (0, -1, 0), (0, 0, -1), (0, 0, 1), (-1, 0, 0), (1, 0, 0)]  # order of DIRS


def render_iso(d, v, s, ycut=None):
    view = View(v, s)
    blocks = d['map']
    items = []
    for (x, y, z), st in blocks.items():
        if ycut is not None and y > ycut:
            continue
        mask = 0
        name = st.split('[')[0]
        for i, (dx, dy, dz) in enumerate(NB):
            nb = blocks.get((x + dx, y + dy, z + dz))
            if nb is None or (ycut is not None and y + dy > ycut):
                continue
            if is_full_opaque(nb) or nb.split('[')[0] == name:
                mask |= 1 << i
        up = blocks.get((x, y + 1, z))
        if up is not None and (up.split('[')[0] in FLUID or 'waterlogged=true' in up):
            mask |= 1 << 6
        # fully hidden: the three camera-facing neighbours are opaque cubes
        fx, fz = View(v, s).rot(1, 0)
        cam = [(0, 1, 0)]
        for dx, dz in ((1, 0), (0, 1)):
            # world direction whose rotated version is +x' / +z'
            for wx, wz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                rx, rz = view.rot(wx, wz)
                if (rx, rz) == (dx, dz):
                    cam.append((wx, 0, wz))
        if all((lambda nb: nb is not None and is_full_opaque(nb) and not (ycut is not None and y + c[1] > ycut))(blocks.get((x + c[0], y + c[1], z + c[2]))) for c in cam):
            continue
        items.append((view.depth(x + 0.5, y + 0.5, z + 0.5), x, y, z, st, mask))
    if not items:
        return None
    items.sort(key=lambda t: t[0])
    pts = [view.proj(x + a, y + b, z + c) for _, x, y, z, _, _ in items for a, b, c in ((0, 0, 0), (1, 1, 1), (1, 0, 0), (0, 0, 1), (0, 1, 1), (1, 1, 0))]
    minu = min(p[0] for p in pts) - 2 * s
    maxu = max(p[0] for p in pts) + 2 * s
    minv = min(p[1] for p in pts) - 2 * s
    maxv = max(p[1] for p in pts) + 2 * s
    W, H = int(maxu - minu) + 1, int(maxv - minv) + 1
    img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    for _, x, y, z, st, mask in items:
        sp, dx, dy = sprite(st, view, mask)
        if sp is None:
            continue
        u, w = view.proj(x, y, z)
        # sprite origin is the block's (0,0,0) corner
        img.alpha_composite(sp, (int(round(u - minu + dx)), int(round(w - minv + dy))))
    return img


def render_plan(d, y, s=6):
    """Top-down cut-away just above floor y: the highest block at or below y+2 per column, darker when lower."""
    blocks = d['map']
    xs = [k[0] for k in blocks]
    zs = [k[2] for k in blocks]
    x0, x1, z0, z1 = min(xs), max(xs), min(zs), max(zs)
    top = {}
    for (x, yy, z), st in blocks.items():
        if yy > y + 2 or yy < y - 6:
            continue
        if (x, z) not in top or yy > top[(x, z)][0]:
            top[(x, z)] = (yy, st)
    img = Image.new('RGBA', ((x1 - x0 + 1) * s, (z1 - z0 + 1) * s), (24, 26, 30, 255))
    for (x, z), (yy, st) in top.items():
        faces = [f for f in faces_of(st) if f[0] == 'up']
        if not faces:
            faces = faces_of(st)
        if not faces:
            continue
        f = max(faces, key=lambda f: max(p[1] for p in f[1]))
        im = texture(f[2]).resize((s, s), Image.NEAREST)
        if f[5] is not None:
            t = f[5]
            r, g, b, a = im.split()
            im = Image.merge('RGBA', (r.point(lambda v: v * t[0] // 255), g.point(lambda v: v * t[1] // 255), b.point(lambda v: v * t[2] // 255), a))
        k = 1.0 if yy >= y + 1 else 0.75 if yy == y else 0.5
        if yy >= y + 1 and yy <= y + 2:
            k = 1.15  # walls and furniture at standing height read brighter
        bg = Image.new('RGBA', (s, s), (24, 26, 30, 255))
        bg.alpha_composite(im)
        bg = bg.point(lambda v: min(255, int(v * k)))
        img.paste(bg, ((x - x0) * s, (z - z0) * s))
    dr = ImageDraw.Draw(img)
    for m in d.get('markers', []):
        mx, my, mz = m['pos']
        if abs(my - y) <= 1:
            c = (230, 40, 40) if m['kind'] == 'npc' else (60, 160, 255)
            px, pz = (mx - x0) * s + s // 2, (mz - z0) * s + s // 2
            dr.ellipse([px - s // 2, pz - s // 2, px + s // 2, pz + s // 2], outline=c, width=2)
    for c in d.get('chests', []):
        mx, my, mz = c['pos']
        if abs(my - y) <= 1:
            px, pz = (mx - x0) * s, (mz - z0) * s
            dr.rectangle([px, pz, px + s - 1, pz + s - 1], outline=(255, 210, 40), width=2)
    return img


def floors(d):
    """Floor levels worth a plan: y values where many cells are walkable (solid below, two air above)."""
    blocks = d['map']
    count = {}
    for (x, y, z), st in blocks.items():
        if is_full_opaque(st) or 'slab' in st or 'stairs' in st or 'planks' in st:
            if (x, y + 1, z) not in blocks and (x, y + 2, z) not in blocks:
                count[y + 1] = count.get(y + 1, 0) + 1
    if not count:
        return [0]
    best = sorted(count.items(), key=lambda t: -t[1])
    out = []
    for y, n in sorted(best[:12]):
        if n >= max(6, best[0][1] * 0.08) and all(abs(y - o) >= 3 for o in out):
            out.append(y)
    ys = [k[1] for k in blocks]
    return [y for y in out if min(ys) <= y <= max(ys)][:6] or [0]


def sheet(d, views, s, plans=True):
    add_ground(d)
    panels = []
    for v in views:
        im = render_iso(d, v, s)
        if im is not None:
            panels.append((f'view {v}', im))
    if plans and d['kind'] != 'town':
        for y in floors(d):
            panels.append((f'plan y={y}', render_plan(d, y, max(4, int(s * 0.6)))))
    if not panels:
        return None
    pad = 8
    W = sum(p[1].width for p in panels) + pad * (len(panels) + 1)
    H = max(p[1].height for p in panels) + pad * 2 + 18
    out = Image.new('RGB', (W, H), (40, 44, 52))
    dr = ImageDraw.Draw(out)
    x = pad
    for label, im in panels:
        out.paste(im, (x, pad + 18), im)
        dr.text((x, pad), label, fill=(220, 220, 220))
        x += im.width + pad
    dr.text((W - 8 * len(d['id']) - 8, pad), d['id'], fill=(255, 220, 120))
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('src')
    ap.add_argument('-o', '--out', required=True)
    ap.add_argument('--views', default='0,2')
    ap.add_argument('--scale', type=float, default=10)
    ap.add_argument('--no-plans', action='store_true')
    ap.add_argument('--match', default='')
    ap.add_argument('-j', '--jobs', type=int, default=1)
    a = ap.parse_args()
    views = [int(v) for v in a.views.split(',')]
    paths = sorted(glob.glob(os.path.join(a.src, '*.json'))) if os.path.isdir(a.src) else [a.src]
    paths = [p for p in paths if a.match in os.path.basename(p)]
    if a.jobs > 1 and len(paths) > 1:
        import multiprocessing
        with multiprocessing.Pool(a.jobs) as pool:
            pool.starmap(render_one, [(p, a, views, len(paths)) for p in paths])
        return
    for p in paths:
        render_one(p, a, views, len(paths))


def render_one(p, a, views, total):
    if True:
        d = load_dump(p)
        s = a.scale if d['kind'] != 'town' else min(a.scale, 4)
        img = sheet(d, views, s, not a.no_plans)
        if img is None:
            print('empty', p)
            return
        out = a.out
        if os.path.isdir(out) or total > 1:
            os.makedirs(out, exist_ok=True)
            out = os.path.join(out, os.path.basename(p)[:-5] + '.png')
        img.save(out)
        print(out, img.size)


if __name__ == '__main__':
    main()
