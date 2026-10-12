"""Small toolkit for code-generated GeckoLib assets: painted texture atlases, Bedrock geometry with per-face UVs,
and procedural keyframe animations. Used by gen_creatures.py.

Conventions (GeckoLib / Blockbench file space): 16 units = 1 block at scale 1, ground at y = 0, the creature faces -Z.
Rotations in degrees. For a bone pivot, +X rotation pitches a forward-pointing part nose-down and swings a hanging
limb backward; +Z rotation lowers a wing pointing toward +X (the creature's left)."""
import json, math, os, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from pngw import write_png  # noqa: E402


# ------------------------------------------------------------------ colour helpers

def hexc(h):
    return ((h >> 16) & 255, (h >> 8) & 255, h & 255)


def mix(a, b, t):
    t = 0.0 if t < 0 else 1.0 if t > 1 else t
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(3))


def shade(c, f):
    return tuple(c[i] * f for i in range(3))


def smooth(e0, e1, x):
    if e0 == e1:
        return 0.0 if x < e0 else 1.0
    t = (x - e0) / (e1 - e0)
    t = 0.0 if t < 0 else 1.0 if t > 1 else t
    return t * t * (3 - 2 * t)


def _h(ix, iy, seed):
    n = (ix * 374761393 + iy * 668265263 + seed * 2147483647) & 0xFFFFFFFF
    n = ((n ^ (n >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((n ^ (n >> 16)) & 0xFFFF) / 65535.0


def vnoise(x, y, cell, w, h, seed=0):
    """Tileable value noise over a w*h swatch (cell size in pixels, wraps at the swatch size)."""
    nx, ny = max(1, round(w / cell)), max(1, round(h / cell))
    fx, fy = x / w * nx, y / h * ny
    ix, iy = int(math.floor(fx)), int(math.floor(fy))
    tx, ty = fx - ix, fy - iy
    tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
    a = _h(ix % nx, iy % ny, seed)
    b = _h((ix + 1) % nx, iy % ny, seed)
    c = _h(ix % nx, (iy + 1) % ny, seed)
    d = _h((ix + 1) % nx, (iy + 1) % ny, seed)
    return a + (b - a) * tx + (c - a) * ty + (a - b - c + d) * tx * ty


def fbm(x, y, w, h, seed=0, cells=(8, 4, 2)):
    v, amp, tot = 0.0, 1.0, 0.0
    for c in cells:
        v += vnoise(x, y, c, w, h, seed) * amp
        tot += amp
        amp *= 0.55
        seed += 17
    return v / tot


# ------------------------------------------------------------------ patterns (return a brightness factor ~1.0)

def pat_scales(cw, ch, depth=0.32):
    def f(u, v, w, h):
        row = int(v // ch)
        off = (row % 2) * cw / 2
        lx = ((u + off) % cw) - cw / 2
        ly = (v % ch)
        d = math.sqrt((lx / (cw / 2)) ** 2 + ((ly - ch * 0.15) / ch) ** 2)
        rim = smooth(0.72, 1.0, d)
        return 1.10 - depth * rim - 0.12 * (ly / ch)
    return f


def pat_fur(strand=5, depth=0.22, seed=3):
    def f(u, v, w, h):
        n = vnoise(u * 3.0 % w, v * 0.5 % h, strand, w, h, seed)
        n2 = vnoise(u, v, 2, w, h, seed + 9)
        return 1.0 + (n - 0.5) * depth * 2 + (n2 - 0.5) * 0.1
    return f


def pat_feathers(fw, fh, depth=0.35):
    def f(u, v, w, h):
        row = int(v // fh)
        off = (row % 2) * fw / 2
        lx = ((u + off) % fw) / fw - 0.5
        ly = (v % fh) / fh
        edge = 0.55 + 0.45 * math.cos(lx * math.pi)
        rim = smooth(edge - 0.18, edge, ly)
        shaft = 1.0 - 0.18 * smooth(0.06, 0.0, abs(lx)) * (ly < edge)
        return (1.08 - depth * rim - 0.1 * ly) * shaft
    return f


def pat_plates(pw, depth=0.3, axis='v'):
    def f(u, v, w, h):
        t = (v if axis == 'v' else u) % pw / pw
        return 1.06 - depth * smooth(0.82, 1.0, t) - 0.08 * t
    return f


def pat_wrinkles(spacing=4, depth=0.18, seed=5):
    def f(u, v, w, h):
        y = v + (vnoise(u, v, 6, w, h, seed) - 0.5) * 3
        k = abs(math.sin(y / spacing * math.pi))
        return 1.04 - depth * smooth(0.25, 0.0, k) - (vnoise(u, v, 3, w, h, seed + 3) - 0.5) * 0.14
    return f


def pat_mottle(cell=6, depth=0.25, seed=11):
    def f(u, v, w, h):
        return 1.0 + (fbm(u, v, w, h, seed, (cell, cell / 2, 2)) - 0.5) * depth * 2
    return f


def pat_combine(*ps):
    def f(u, v, w, h):
        r = 1.0
        for p in ps:
            r *= p(u, v, w, h)
        return r
    return f


# ------------------------------------------------------------------ painters (return (r,g,b,a))

def p_gradient(top, bottom, pattern=None, power=1.0, noise=0.08, seed=1, rim=0.12):
    top, bottom = hexc(top) if isinstance(top, int) else top, hexc(bottom) if isinstance(bottom, int) else bottom

    def f(u, v, w, h):
        t = (v + 0.5) / h
        c = mix(top, bottom, t ** power)
        k = pattern(u, v, w, h) if pattern else 1.0
        k *= 1.0 + (vnoise(u, v, 3, w, h, seed) - 0.5) * noise * 2
        k *= 1.0 - rim * (smooth(0.12, 0.0, t) + smooth(0.88, 1.0, t))
        return shade(c, k) + (255,)
    return f


def p_flat(color, pattern=None, noise=0.08, seed=2):
    color = hexc(color) if isinstance(color, int) else color

    def f(u, v, w, h):
        k = pattern(u, v, w, h) if pattern else 1.0
        k *= 1.0 + (vnoise(u, v, 3, w, h, seed) - 0.5) * noise * 2
        return shade(color, k) + (255,)
    return f


def p_spots(base_painter, spot, cell, radius, ring=None, seed=7, jitter=0.35):
    """Spots/rosettes on a jittered grid laid over another painter."""
    spot = hexc(spot)
    ring = hexc(ring) if ring is not None else None

    def f(u, v, w, h):
        r, g, b, a = base_painter(u, v, w, h)
        nx, ny = max(1, round(w / cell)), max(1, round(h / cell))
        cx, cy = u / w * nx, v / h * ny
        best = 9.0
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                ix, iy = int(math.floor(cx)) + dx, int(math.floor(cy)) + dy
                px = ix + 0.5 + (_h(ix % nx, iy % ny, seed) - 0.5) * jitter * 2
                py = iy + 0.5 + (_h(ix % nx, iy % ny, seed + 1) - 0.5) * jitter * 2
                rr = radius * (0.75 + 0.5 * _h(ix % nx, iy % ny, seed + 2))
                d = math.hypot(cx - px, cy - py) / rr
                best = min(best, d)
        c = (r, g, b)
        if ring is not None:
            if 0.55 < best < 1.0:
                c = mix(c, ring, 0.85)
            elif best <= 0.55:
                c = mix(c, spot, 0.55)
        elif best < 1.0:
            c = mix(c, spot, 0.85 * smooth(1.0, 0.75, best))
        return c + (a,)
    return f


def p_stripes(base_painter, color, freq, width=0.3, seed=4, wobble=2.0, vertical=True):
    color = hexc(color)

    def f(u, v, w, h):
        r, g, b, a = base_painter(u, v, w, h)
        x = (u if vertical else v) + (vnoise(u, v, 5, w, h, seed) - 0.5) * wobble * 2
        k = (math.sin(x / (w if vertical else h) * freq * 2 * math.pi) + 1) / 2
        t = smooth(1 - width, 1 - width + 0.12, k)
        return mix((r, g, b), color, t * 0.9) + (a,)
    return f


def p_eye(iris, pupil=0x101010, slit=True, sclera=None, glow=False):
    iris, pupil = hexc(iris), hexc(pupil)
    sclera = hexc(sclera) if sclera is not None else None

    def f(u, v, w, h):
        x, y = (u + 0.5) / w * 2 - 1, (v + 0.5) / h * 2 - 1
        d = math.hypot(x, y)
        if sclera is not None and d > 0.78:
            return sclera + (255,)
        c = mix(shade(iris, 1.25), shade(iris, 0.75), (y + 1) / 2)
        if (abs(x) < 0.22 and abs(y) < 0.9) if slit else d < 0.42:
            c = pupil
        if -0.7 < x < -0.35 and -0.7 < y < -0.35:
            c = (250, 250, 240)
        return tuple(c) + (255,)
    return f


def p_horn(base, tip, rings=5, seed=6):
    base, tip = hexc(base), hexc(tip)

    def f(u, v, w, h):
        t = (v + 0.5) / h
        c = mix(base, tip, t)
        k = 1.0 - 0.18 * smooth(0.75, 1.0, abs(math.sin(t * rings * math.pi)))
        k *= 1.0 + (vnoise(u, v, 2, w, h, seed) - 0.5) * 0.12
        k *= 0.85 + 0.25 * math.sin((u + 0.5) / w * math.pi)
        return shade(c, k) + (255,)
    return f


def p_teeth(color=0xF2EAD0):
    color = hexc(color)

    def f(u, v, w, h):
        t = (v + 0.5) / h
        return shade(color, 1.05 - 0.25 * t) + (255,)
    return f


def p_membrane(color, vein, seed=8):
    color, vein = hexc(color), hexc(vein)

    def f(u, v, w, h):
        k = abs(math.sin((u + (vnoise(u, v, 6, w, h, seed) - 0.5) * 6) / w * 3 * math.pi))
        c = mix(vein, color, smooth(0.0, 0.18, k))
        c = shade(c, 0.92 + 0.16 * (vnoise(u, v, 4, w, h, seed + 1)))
        return c + (255,)
    return f


def p_flame(colors, seed=9):
    cs = [hexc(c) for c in colors]

    def f(u, v, w, h):
        t = (v + 0.5) / h + (vnoise(u, v, 4, w, h, seed) - 0.5) * 0.35
        t = 0 if t < 0 else 0.999 if t >= 1 else t
        seg = t * (len(cs) - 1)
        i = int(seg)
        c = mix(cs[i], cs[min(i + 1, len(cs) - 1)], seg - i)
        return shade(c, 0.9 + 0.2 * vnoise(u, v, 2, w, h, seed + 3)) + (255,)
    return f


def p_suckers(base, ring, cell=6, seed=10):
    base, ring = hexc(base), hexc(ring)

    def f(u, v, w, h):
        lx = (u % cell) - cell / 2 + 0.5
        ly = (v % cell) - cell / 2 + 0.5
        d = math.hypot(lx, ly) / (cell / 2)
        c = base
        if 0.45 < d < 0.85:
            c = ring
        elif d <= 0.45:
            c = shade(ring, 0.75)
        return shade(c, 0.92 + 0.16 * vnoise(u, v, 3, w, h, seed)) + (255,)
    return f


def p_solid(color, a=255):
    color = hexc(color)
    return lambda u, v, w, h: color + (a,)


# ------------------------------------------------------------------ texture atlas

class Mat:
    def __init__(self, x, y, w, h, vfull, fit):
        self.x, self.y, self.w, self.h, self.vfull, self.fit = x, y, w, h, vfull, fit


class Atlas:
    def __init__(self, w, h):
        self.w, self.h = w, h
        self.px = [[(0, 0, 0, 0)] * w for _ in range(h)]
        self.glow = None
        self.mats = {}
        self.cx = self.cy = self.row = 0

    def _alloc(self, w, h):
        if self.cx + w > self.w:
            self.cx, self.cy, self.row = 0, self.cy + self.row, 0
        if self.cy + h > self.h:
            raise ValueError('atlas full (%dx%d) allocating %dx%d' % (self.w, self.h, w, h))
        x, y = self.cx, self.cy
        self.cx += w
        self.row = max(self.row, h)
        return x, y

    def add(self, name, w, h, painter, vfull=False, fit=False, glow=None):
        x, y = self._alloc(w, h)
        for j in range(h):
            row = self.px[y + j]
            for i in range(w):
                c = painter(i, j, w, h)
                row[x + i] = tuple(max(0, min(255, int(round(k)))) for k in c)
        if glow is not None:
            if self.glow is None:
                self.glow = [[(0, 0, 0, 0)] * self.w for _ in range(self.h)]
            for j in range(h):
                for i in range(w):
                    g = glow(i, j, w, h) if callable(glow) else (glow and self.px[y + j][x + i])
                    if g:
                        self.glow[y + j][x + i] = g if isinstance(g, tuple) else self.px[y + j][x + i]
        self.mats[name] = Mat(x, y, w, h, vfull, fit)
        return self.mats[name]

    def save(self, path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        write_png(path, self.w, self.h, self.px)
        if self.glow is not None:
            write_png(path[:-4] + '_glowmask.png', self.w, self.h, self.glow)


# ------------------------------------------------------------------ geometry

FACES = ('north', 'south', 'east', 'west', 'up', 'down')


class Model:
    def __init__(self, ident, tex_w, tex_h, density=1.0):
        self.ident = ident
        self.atlas = Atlas(tex_w, tex_h)
        self.density = density
        self.bones = []
        self.bmap = {}
        self.salt = 0

    def mat(self, name, w, h, painter, vfull=False, fit=False, glow=None):
        return self.atlas.add(name, w, h, painter, vfull, fit, glow)

    def bone(self, name, parent=None, pivot=(0, 0, 0), rot=None):
        if name in self.bmap:
            raise ValueError('duplicate bone ' + name)
        b = {'name': name, 'pivot': [round(p, 3) for p in pivot]}
        if parent:
            if parent not in self.bmap:
                raise ValueError('unknown parent ' + parent)
            b['parent'] = parent
        if rot:
            b['rotation'] = [round(r, 3) for r in rot]
        b['cubes'] = []
        self.bones.append(b)
        self.bmap[name] = b
        return name

    def _uv(self, mname, fw, fh):
        m = self.atlas.mats[mname]
        if m.fit:
            return [m.x, m.y], [m.w, m.h]
        tw = min(m.w, max(1.0, fw * self.density))
        th = m.h if m.vfull else min(m.h, max(1.0, fh * self.density))
        self.salt += 1
        slack_x, slack_y = int(m.w - tw), int(m.h - th)
        ox = (self.salt * 7919) % (slack_x + 1) if slack_x > 0 else 0
        oy = (self.salt * 104729) % (slack_y + 1) if slack_y > 0 else 0
        return [round(m.x + ox, 3), round(m.y + oy, 3)], [round(tw, 3), round(th, 3)]

    def box(self, bone, origin, size, mats, inflate=0.0, rot=None, pivot=None):
        """mats: material name, or dict with keys all/side/up/down/north/south/east/west."""
        if isinstance(mats, str):
            mats = {'all': mats}
        sx, sy, sz = size
        dims = {'north': (sx, sy), 'south': (sx, sy), 'east': (sz, sy), 'west': (sz, sy), 'up': (sx, sz), 'down': (sx, sz)}
        uv = {}
        for f in FACES:
            m = mats.get(f) or (mats.get('side') if f in ('north', 'south', 'east', 'west') else None) or mats.get('all')
            if m is None:
                continue
            u, s = self._uv(m, *dims[f])
            uv[f] = {'uv': u, 'uv_size': s}
        c = {'origin': [round(o, 3) for o in origin], 'size': [round(v, 3) for v in size], 'uv': uv}
        if inflate:
            c['inflate'] = inflate
        if rot:
            c['rotation'] = [round(r, 3) for r in rot]
            c['pivot'] = [round(p, 3) for p in (pivot if pivot else (origin[0] + sx / 2, origin[1] + sy / 2, origin[2] + sz / 2))]
        self.bmap[bone]['cubes'].append(c)

    def cbox(self, bone, center, size, mats, **kw):
        """Box given by its centre."""
        self.box(bone, (center[0] - size[0] / 2, center[1] - size[1] / 2, center[2] - size[2] / 2), size, mats, **kw)

    def write(self, geo_path, tex_path):
        bones = []
        for b in self.bones:
            b = dict(b)
            if not b['cubes']:
                del b['cubes']
            bones.append(b)
        out = {'format_version': '1.12.0', 'minecraft:geometry': [{
            'description': {'identifier': 'geometry.' + self.ident, 'texture_width': self.atlas.w, 'texture_height': self.atlas.h,
                            'visible_bounds_width': 8, 'visible_bounds_height': 6, 'visible_bounds_offset': [0, 2, 0]},
            'bones': bones}]}
        os.makedirs(os.path.dirname(geo_path), exist_ok=True)
        with open(geo_path, 'w') as f:
            json.dump(out, f, separators=(',', ':'))
        self.atlas.save(tex_path)


# ------------------------------------------------------------------ animations

def _key(t):
    s = ('%.4f' % t).rstrip('0')
    return s + '0' if s.endswith('.') else s


def frames(fn, length, fps=12):
    """Sample fn(t) -> (x, y, z) from 0 to length into a keyframe dict."""
    n = max(2, int(round(length * fps)))
    out = {}
    for i in range(n + 1):
        t = length * i / n
        v = fn(t)
        out[_key(t)] = [round(float(c), 3) for c in v]
    return out


class Anims:
    def __init__(self, model_id):
        self.model_id = model_id
        self.anims = {}

    def add(self, name, length, bones, loop=True):
        """bones: {bone: {'rotation': fn or dict, 'position': fn or dict}} with fn(t) -> (x,y,z)."""
        out = {}
        for b, ch in bones.items():
            o = {}
            for kind in ('rotation', 'position', 'scale'):
                if kind in ch:
                    v = ch[kind]
                    o[kind] = frames(v, length) if callable(v) else v
            out[b] = o
        self.anims['animation.%s.%s' % (self.model_id, name)] = {'loop': loop, 'animation_length': length, 'bones': out}

    def write(self, path):
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, 'w') as f:
            json.dump({'format_version': '1.8.0', 'animations': self.anims}, f, separators=(',', ':'))


def wave(t, length, cycles=1.0, phase=0.0):
    return math.sin((t / length * cycles + phase) * 2 * math.pi)
