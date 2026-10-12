"""Projectile billboards (32x32, glowing, transparent), race_parts.png (64x64 tint-ready greyscale) and mob effect icons."""
import math

from .canvas import Canvas, C, mix, with_alpha, shade, hash01
from .sprite import Grid, Mat, Fixed, Force, render, grid_from, OD, OL, DK, MD, LT, HL
from . import items as item_sprites

PROJECTILES = {}
EFFECTS = {}
N = 32
CX = CY = 16.0


def projectile(name):
    def deco(fn):
        PROJECTILES[name] = fn
        return fn
    return deco


def effect(name):
    def deco(fn):
        EFFECTS[name] = fn
        return fn
    return deco


# ============================================================================================ helpers
def banded(c, cx, cy, r, ramp, sx=1.0, sy=1.0, add=False):
    """Concentric bands of colour (pixel-art glow): ramp[0] at the centre ... ramp[-1] at radius r."""
    for y in range(c.h):
        for x in range(c.w):
            d = math.hypot((x + 0.5 - cx) / sx, (y + 0.5 - cy) / sy) / r
            if d < 1.0:
                col = C(ramp[min(len(ramp) - 1, int(d * len(ramp)))])
                c.blend(x, y, col)


def glow_around(c, color, radius=2.5, strength=150, steps=3):
    """Halo outside the current opaque shape (banded alpha)."""
    src = [p[3] for p in c.px]
    out = []
    for y in range(c.h):
        for x in range(c.w):
            if src[y * c.w + x] > 200:
                continue
            best = 99.0
            r = int(math.ceil(radius))
            for dy in range(-r, r + 1):
                for dx in range(-r, r + 1):
                    xx, yy = x + dx, y + dy
                    if 0 <= xx < c.w and 0 <= yy < c.h and src[yy * c.w + xx] > 200:
                        best = min(best, math.hypot(dx, dy))
            if best <= radius:
                band = min(steps - 1, int((best - 0.01) / radius * steps))
                a = int(strength * (1.0 - band / float(steps)))
                out.append((x, y, a))
    col = C(color)
    for x, y, a in out:
        c.blend(x, y, (col[0], col[1], col[2], a))


def from_grid(rows, mats, scale=2, size=N):
    g = grid_from(rows)
    small = render(g, mats)
    big = small.scaled(scale)
    out = Canvas(size, size)
    out.paste(big, (size - big.w) // 2, (size - big.h) // 2)
    return out


def crescent(c, r_out, r_in, off, ramp, angle=-45, thick_glow=None):
    """Crescent: inside the outer disc, outside an offset inner disc; banded by distance to the outer rim."""
    a = math.radians(angle)
    ox, oy = CX + math.cos(a) * off, CY + math.sin(a) * off
    for y in range(N):
        for x in range(N):
            px, py = x + 0.5, y + 0.5
            d1 = math.hypot(px - CX, py - CY)
            d2 = math.hypot(px - ox, py - oy)
            if d1 <= r_out and d2 >= r_in:
                t = (r_out - d1) / max(1e-6, (r_out - (r_in - off)))
                t = max(0.0, min(0.999, t * 1.6))
                c.blend(x, y, C(ramp[int(t * len(ramp))]))


def bolt_path(points, c, core, glow, hw=0.8, glow_r=3.2):
    def seg_dist(px, py, ax, ay, bx, by):
        dx, dy = bx - ax, by - ay
        t = max(0.0, min(1.0, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy or 1)))
        return math.hypot(px - (ax + t * dx), py - (ay + t * dy))
    for y in range(N):
        for x in range(N):
            d = min(seg_dist(x + 0.5, y + 0.5, *points[i], *points[i + 1]) for i in range(len(points) - 1))
            if d <= hw:
                c.blend(x, y, core)
            elif d <= glow_r:
                band = int((d - hw) / (glow_r - hw) * 3)
                g = C(glow)
                c.blend(x, y, (g[0], g[1], g[2], [200, 120, 50][min(2, band)]))


FIRE = ['#fffbe0', '#fff2a0', '#ffd24a', '#ff9a2a', '#f05a1a', '#c02a10c0', '#8a140880']


def flame_licks(c, cx, cy, r, n=9, seed=1, ramp=FIRE, length=5.0):
    """Tongues of flame radiating from a ball."""
    for y in range(N):
        for x in range(N):
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            d = math.hypot(dx, dy)
            if d < r or d > r + length:
                continue
            ang = math.atan2(dy, dx)
            wob = 0.5 + 0.5 * math.cos(ang * n + seed)
            reach = r + length * (0.35 + 0.65 * wob) * (0.7 + 0.3 * hash01(int(ang * 10), seed, 7))
            if d <= reach:
                t = (d - r) / max(0.01, reach - r)
                idx = min(len(ramp) - 1, 3 + int(t * (len(ramp) - 3)))
                c.blend(x, y, C(ramp[idx]))


# ============================================================================================ fire
FIST = [
    '................',
    '................',
    '...oo.oo.oo.oo..',
    '..ohhohhohhohho.',
    '..ohHohHohHohHo.',
    '..ohhhhhhhhhhho.',
    '..ohhhhhhhhhhho.',
    '..otttttthhhhho.',
    '..oTTTTTtthhhho.',
    '...otttttthhhho.',
    '....ohhhhhhhho..',
    '.....owwwwwwo...',
    '.....owwwwwwo...',
    '......oooooo....',
    '................',
    '................',
]


@projectile('fire_fist')
def _fire_fist():
    c = Canvas(N, N)
    banded(c, CX, CY, 15.5, ['#ffd24a60', '#ff9a2a60', '#f05a1a50', '#c02a1040'])
    flame_licks(c, CX, CY, 9.5, n=7, seed=2, length=6.5)
    fist = from_grid(FIST, {'o': Fixed('#c02a10'), 'h': Fixed('#ffb030'), 'H': Fixed('#fff4c0'), 't': Fixed('#ffd060'),
                            'T': Fixed('#fff0b0'), 'w': Fixed('#ff7a20')})
    c.paste(fist, 0, 1)
    return c


@projectile('fire_bullet')
def _fire_bullet():
    c = Canvas(N, N)
    banded(c, CX, CY, 11, ['#ffb03070', '#f0601a50', '#c02a1030'])
    flame_licks(c, CX, CY, 5.5, n=6, seed=4, length=4.5)
    banded(c, CX, CY, 6, ['#ffffff', '#fff6c0', '#ffe070', '#ffb030'])
    return c


@projectile('flame_emperor')
def _flame_emperor():
    c = Canvas(N, N)
    # corona rays
    for y in range(N):
        for x in range(N):
            dx, dy = x + 0.5 - CX, y + 0.5 - CY
            d = math.hypot(dx, dy)
            ang = math.atan2(dy, dx)
            ray = abs(math.cos(ang * 8)) ** 6
            if 11 <= d <= 11 + 5 * ray:
                c.blend(x, y, C('#ff8a20c0') if d < 13.5 else C('#e0401880'))
    flame_licks(c, CX, CY, 10.5, n=13, seed=9, length=3.5)
    banded(c, CX, CY, 11.5, ['#ffffff', '#fffbe0', '#fff2a0', '#ffe070', '#ffc040', '#ff9a2a', '#f06a1a'])
    for (x, y) in ((12, 11), (19, 14), (14, 19), (18, 10)):  # sunspots / flares
        c.set(x, y, '#fff2a0')
    return c


@projectile('fire_bird')
def _fire_bird():
    c = Canvas(N, N)
    bird = [
        '................',
        '.yy..........yy.',
        '.yoy...WW...yoy.',
        '..yoo..hh..ooy..',
        '..yooo.hh.oooy..',
        '...yoooh.hoooy..',
        '...yyoohhhooyy..',
        '....yyohWhoyy...',
        '.....yyhhhyy....',
        '......yohoy.....',
        '......yooy......',
        '.....yo..oy.....',
        '.....y....y.....',
        '................',
        '................',
        '................',
    ]
    b = from_grid(bird, {'y': Fixed('#ffd24a'), 'o': Fixed('#ff8a20'), 'h': Fixed('#fff2a0'), 'W': Fixed('#ffffff')})
    c.paste(b, 0, 2)
    glow_around(c, '#ff6a1a', radius=2.6, strength=170)
    return c


# ============================================================================================ ice / lightning / water
@projectile('ice_spear')
def _ice_spear():
    c = Canvas(N, N)
    # elongated diamond along the diagonal
    ax, ay, bx, by = 4.0, 28.0, 28.5, 3.5
    L = math.hypot(bx - ax, by - ay)
    ux, uy = (bx - ax) / L, (by - ay) / L
    for y in range(N):
        for x in range(N):
            px, py = x + 0.5 - ax, y + 0.5 - ay
            t = (px * ux + py * uy) / L
            s_ = -px * uy + py * ux
            if not (0 <= t <= 1):
                continue
            w = 3.6 * (t / 0.62 if t < 0.62 else (1 - t) / 0.38)
            w = max(0.6, w) if t < 0.97 else 0.4
            if abs(s_) <= w:
                if abs(s_) > w - 0.9:
                    col = '#3a8ac8'
                elif s_ < 0:
                    col = '#eafcff' if abs(s_) < w * 0.45 else '#bfefff'
                else:
                    col = '#7fd0f0'
                if abs(s_) < 0.5 and 0.25 < t < 0.9:
                    col = '#ffffff'
                c.set(x, y, col)
    glow_around(c, '#9ae8ff', radius=2.2, strength=140)
    for x, y in ((9, 6), (24, 22), (6, 18)):
        c.set(x, y, '#e8fbff')
    return c


@projectile('lightning_bolt')
def _lightning():
    c = Canvas(N, N)
    banded(c, CX, CY, 15, ['#c8b0ff30', '#8a6aff20'])
    main = [(3, 4), (12, 11), (9, 15), (19, 19), (16, 22), (28, 29)]
    bolt_path(main, c, C('#ffffff'), '#a88aff', hw=0.9, glow_r=3.4)
    bolt_path([(12, 11), (19, 8), (23, 9)], c, C('#fff6b0'), '#8a6aff', hw=0.5, glow_r=2.4)
    bolt_path([(19, 19), (24, 16), (28, 17)], c, C('#fff6b0'), '#8a6aff', hw=0.5, glow_r=2.4)
    bolt_path([(9, 15), (5, 20)], c, C('#fff6b0'), '#8a6aff', hw=0.5, glow_r=2.2)
    return c


@projectile('water_shot')
def _water():
    c = Canvas(N, N)
    banded(c, CX, CY, 14, ['#8ad0ff50', '#4a9ae030'])
    for i in range(8):  # droplets
        a = i * math.pi / 4 + 0.3
        x, y = CX + math.cos(a) * 12.0, CY + math.sin(a) * 12.0
        banded(c, x, y, 1.8, ['#d8f4ff', '#5ab0f0'])
    banded(c, CX, CY, 9.5, ['#2a6ad0', '#2a6ad0', '#3a86e8', '#4aa0f4', '#2a70d8', '#1a4aa8'], sx=1.0, sy=1.0)
    banded(c, CX - 2.5, CY - 2.5, 4.5, ['#ffffff', '#d8f4ff', '#9ad6ff', '#6ab8ff80'])
    c.set(20, 20, '#9ad6ff')
    c.set(21, 19, '#9ad6ff')
    return c


# ============================================================================================ wind / slashes
@projectile('wind_blade')
def _wind_blade():
    c = Canvas(N, N)
    crescent(c, 14.5, 12.0, 4.5, ['#f0fff0', '#ccf8cc', '#a8eaa8', '#7ed88ad0', '#5ac07090', '#3aa05850'], angle=-45)
    return c


@projectile('air_blade')
def _air_blade():
    c = Canvas(N, N)
    crescent(c, 13.0, 10.5, 4.0, ['#ffffff', '#f4f8ff', '#e0eaf8', '#c8d6eec0', '#aabcdc80', '#90a4c840'], angle=-45)
    return c


@projectile('air_blade_big')
def _air_blade_big():
    c = Canvas(N, N)
    crescent(c, 15.5, 12.0, 5.0, ['#ffffff', '#f6f9ff', '#e4ecfa', '#ccd8f0c0', '#b0c0e080', '#94a8cc40'], angle=-45)
    crescent(c, 10.5, 8.5, 3.0, ['#ffffffd0', '#e8f0ffa0', '#c8d8f060'], angle=-45)
    return c


@projectile('crescent')
def _crescent():
    c = Canvas(N, N)
    crescent(c, 14.0, 11.5, 4.0, ['#ffffff', '#e8f4ff', '#bcd8ff', '#8ab4f0c0', '#5a88d880', '#3a64c040'], angle=-45)
    return c


@projectile('tempest')
def _tempest():
    c = Canvas(N, N)
    for y in range(N):
        for x in range(N):
            dx, dy = x + 0.5 - CX, y + 0.5 - CY
            r = math.hypot(dx, dy)
            if r > 15.5:
                continue
            th = math.atan2(dy, dx)
            arm = (th + r * 0.38) % (2 * math.pi / 3)
            v = abs(arm - math.pi / 3) / (math.pi / 3)  # 0 at arm centre
            if v < 0.35:
                k = int(r / 15.5 * 4)
                c.blend(x, y, C(['#ffffff', '#e0ffe8', '#a8f0bc', '#6ad08ac0', '#3aa86a80'][min(4, k + (1 if v > 0.2 else 0))]))
            elif r < 15:
                c.blend(x, y, C('#6ad08a30'))
    banded(c, CX, CY, 3.5, ['#ffffff', '#e0ffe8'])
    return c


@projectile('haki_wave')
def _haki():
    c = Canvas(N, N)
    for y in range(N):
        for x in range(N):
            r = math.hypot(x + 0.5 - CX, y + 0.5 - CY)
            if 8.5 <= r <= 13.5:
                band = abs(r - 11.0)
                col = '#0a0610' if band < 1.2 else '#2a1040' if band < 2.0 else '#6a2aa0a0'
                c.blend(x, y, C(col))
            elif 13.5 < r <= 15.5:
                c.blend(x, y, C('#8a3ad050'))
            elif 6.5 <= r < 8.5:
                c.blend(x, y, C('#5a2a8a70'))
    # crimson crackles
    for a0 in (0.4, 2.2, 4.1):
        pts = []
        for i in range(4):
            a = a0 + i * 0.22
            rr = 9 + i * 2.1
            pts.append((CX + math.cos(a + (0.15 if i % 2 else -0.1)) * rr, CY + math.sin(a + (0.15 if i % 2 else -0.1)) * rr))
        bolt_path(pts, c, C('#ff4a5a'), '#a01a3a', hw=0.45, glow_r=1.4)
    return c


@projectile('impact_wave')
def _impact():
    c = Canvas(N, N)
    for y in range(N):
        for x in range(N):
            r = math.hypot(x + 0.5 - CX, y + 0.5 - CY)
            for rr, col in ((14.0, '#fff4dcc0'), (10.0, '#ffe8b8e0'), (6.0, '#ffffffff')):
                if abs(r - rr) < 0.9:
                    c.blend(x, y, C(col))
                elif abs(r - rr) < 1.9:
                    c.blend(x, y, C('#ffd89050'))
    banded(c, CX, CY, 3, ['#ffffff', '#fff4dc'])
    return c


@projectile('weather_ball')
def _weather_ball():
    c = Canvas(N, N)
    banded(c, CX, CY, 11.5, ['#bfe8ff40', '#bfe8ff40', '#a8dcff50', '#90d0ff70', '#78c0f8b0', '#5aa8f0e0'])
    for y in range(N):  # highlight arc
        for x in range(N):
            dx, dy = x + 0.5 - CX, y + 0.5 - CY
            r = math.hypot(dx, dy)
            if 7.5 <= r <= 9.0 and dx < -1 and dy < -1:
                c.blend(x, y, C('#ffffffe0'))
    c.set(11, 9, '#ffffff')
    c.set(20, 21, '#e0f4ff')
    # tiny storm cloud inside
    for x, y in ((14, 16), (15, 15), (16, 15), (17, 16), (18, 16), (13, 17), (14, 17), (15, 17), (16, 17), (17, 17), (18, 17)):
        c.blend(x, y, C('#f4f8ffd0'))
    return c


@projectile('energy')
def _energy():
    c = Canvas(N, N)
    banded(c, CX, CY, 13, ['#ffffff', '#f0fdff', '#c8f6ff', '#8ae8ffd0', '#5ad0f890', '#3aa8e850', '#2a88d020'])
    return c


# ============================================================================================ physical
@projectile('flying_fist')
def _flying_fist():
    c = Canvas(N, N)
    for i, (y, x0, x1) in enumerate(((9, 1, 6), (15, 0, 5), (21, 1, 6))):  # motion lines
        for x in range(x0, x1):
            c.blend(x, y, C('#ffffffa0'))
    fist = from_grid(FIST, {'o': Fixed('#7a4a2a'), 'h': Fixed('#e8b890'), 'H': Fixed('#fadcc0'), 't': Fixed('#dcaa80'),
                            'T': Fixed('#f0c8a4'), 'w': Fixed('#3a5a9a')})
    c.paste(fist, 2, 1)
    return c


def item_projectile(item_id, scale=2):
    small = item_sprites.ITEMS[item_id]()
    c = Canvas(N, N)
    big = small.scaled(scale)
    c.paste(big, (N - big.w) // 2, (N - big.h) // 2)
    return c


@projectile('cannonball')
def _cannonball():
    c = item_projectile('cannonball')
    glow_around(c, '#d0d0d0', radius=1.5, strength=60, steps=2)
    return c


@projectile('bullet')
def _bullet():
    c = Canvas(N, N)
    banded(c, CX, CY, 7.5, ['#fff6d060', '#ffd89030'])
    banded(c, CX, CY, 4.2, ['#e8ecf0', '#b4bcc4', '#8a929c', '#5a626c', '#3a4048'])
    c.set(14, 14, '#ffffff')
    return c


@projectile('pellet')
def _pellet():
    c = Canvas(N, N)
    banded(c, CX, CY, 4.6, ['#f0e2c8', '#d0b898', '#a8906e', '#7a654c', '#4e3e2c'])
    c.set(14, 14, '#fff6e6')
    return c


@projectile('knife')
def _knife():
    return item_projectile('throwing_knife')


@projectile('spear')
def _spear():
    return item_projectile('spear')


@projectile('arrow')
def _arrow():
    rows = [
        '................',
        '.............hh.',
        '............hHhh',
        '...........shhh.',
        '..........s.hh..',
        '.........s......',
        '........s.......',
        '.......s........',
        '......s.........',
        '.....s..........',
        '..ffs...........',
        '.fFfs...........',
        '.fFFf...........',
        '..ff............',
        '................',
        '................',
    ]
    return from_grid(rows, {'h': Fixed('#9aa0a8'), 'H': Fixed('#e8ecf0'), 's': Fixed('#8a6a3a'), 'f': Fixed('#e8e8e8'),
                            'F': Fixed('#c03a2a')})


# ============================================================================================ race parts (64x64)
def _box_faces(u, v, w, h, d):
    return {
        'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d), 'right': (u, v + d, d, h),
        'front': (u + d, v + d, w, h), 'left': (u + d + w, v + d, d, h), 'back': (u + 2 * d + w, v + d, w, h),
    }


RACE_BOXES = {
    'head_fin': (0, 0, 1, 4, 9), 'ear': (20, 0, 3, 4, 1), 'third_eye': (28, 0, 2, 1, 1), 'horn': (34, 0, 1, 3, 1),
    'leaf_hair': (0, 14, 10, 3, 10), 'blowhole': (28, 4, 2, 1, 2), 'snout': (40, 0, 3, 3, 3), 'back_fin': (0, 28, 1, 8, 4),
    'tail': (12, 28, 2, 2, 9), 'wing': (0, 40, 9, 10, 1), 'coral': (34, 28, 2, 4, 2), 'tail_fin': (0, 52, 8, 2, 6),
    'plates': (34, 36, 9, 3, 5), 'neck': (40, 14, 3, 6, 3),
}
G = lambda v, a=255: (v, v, v, a)


def race_parts():
    c = Canvas(64, 64)

    def paint(box, fn):
        for face, (x0, y0, w, h) in _box_faces(*RACE_BOXES[box]).items():
            for y in range(h):
                for x in range(w):
                    col = fn(face, x, y, w, h)
                    if col is not None:
                        c.set(x0 + x, y0 + y, col)

    base = lambda face: {'top': 236, 'bottom': 170, 'front': 222, 'back': 200, 'right': 212, 'left': 212}[face]

    # fins: rays + bright leading edge
    def fin(face, x, y, w, h):
        v = base(face)
        if face in ('right', 'left'):
            if x % 2 == (0 if face == 'right' else 1):
                v -= 34
            if y == 0:
                v = min(255, v + 22)
        return G(v)
    paint('head_fin', fin)
    paint('back_fin', fin)

    def ear(face, x, y, w, h):
        v = base(face)
        if face == 'front' and x == 1 and 1 <= y <= 2:
            v -= 70
        if y == 0:
            v += 14
        return G(min(255, v))
    paint('ear', ear)
    paint('third_eye', lambda f, x, y, w, h: G(255) if f == 'front' else G(200))

    def horn(face, x, y, w, h):
        return G(min(255, base(face) - 40 + (2 - y) * 26 if face != 'top' else 250))
    paint('horn', horn)

    def leaf(face, x, y, w, h):
        if face in ('top', 'bottom'):
            v = base(face) - 10
            if (x + y) % 5 == 0 or x == y or x == 9 - y:
                v -= 40  # veins
            if (x * 3 + y) % 7 == 0:
                v += 22
            return G(max(60, min(255, v)))
        # jagged hanging leaf tips on the sides (cutout render type)
        if y == h - 1 and x % 3 == 1:
            return (0, 0, 0, 0)
        v = base(face) - 20 - (14 if x % 3 == 0 else 0)
        return G(v)
    paint('leaf_hair', leaf)

    def blowhole(face, x, y, w, h):
        if face == 'top':
            return G(70 if (x, y) in ((0, 1), (1, 1), (1, 0)) else 220)
        return G(base(face))
    paint('blowhole', blowhole)

    def snout(face, x, y, w, h):
        v = base(face)
        if face == 'front':
            if y == 0 and x == 1:
                return G(40)
            if y == 0:
                return G(70)
            if y == 2 and x == 1:
                return G(120)
        if face == 'top' and x == 1:
            v -= 18
        return G(v)
    paint('snout', snout)

    def tail(face, x, y, w, h):
        v = base(face)
        if face in ('right', 'left', 'top', 'bottom'):
            along = x if face in ('right', 'left') else y
            if along % 3 == 0:
                v -= 30
            if along >= 7:
                v = min(255, v + 25)
        return G(v)
    paint('tail', tail)

    def wing(face, x, y, w, h):
        if face in ('front', 'back'):
            # rows of feathers, scalloped lower edge
            if y == h - 1 and x % 2 == 1:
                return (0, 0, 0, 0)
            v = 236 - (y // 3) * 10
            if y % 3 == 2:
                v -= 36
            if (x + (y // 3)) % 3 == 0 and y % 3 != 2:
                v -= 14
            if face == 'back':
                v -= 14
            return G(v)
        return G(base(face) - 10)
    paint('wing', wing)

    def coral(face, x, y, w, h):
        v = base(face)
        if (x + y * 2) % 3 == 0:
            v -= 46
        return G(v)
    paint('coral', coral)

    def tail_fin(face, x, y, w, h):
        if face in ('top', 'bottom'):
            # rays from the attached edge; forked trailing edge (cutout)
            if y == h - 1 and x in (3, 4):
                return (0, 0, 0, 0)
            if y == h - 2 and x in (3, 4):
                return G(150)
            v = base(face)
            if x % 2 == 0:
                v -= 32
            if y >= h - 2:
                v += 12
            return G(min(255, v))
        return G(base(face) - 20)
    paint('tail_fin', tail_fin)

    def plates(face, x, y, w, h):
        v = base(face) - 26
        if face in ('front', 'back', 'top', 'bottom') and x % 3 == 2:
            return G(v - 60)  # gaps between plates
        if y == 0:
            v += 24
        if face in ('right', 'left') and x % 5 == 4:
            v -= 50
        return G(v)
    paint('plates', plates)

    def neck(face, x, y, w, h):
        v = base(face)
        if face not in ('top', 'bottom') and y % 2 == 1:
            v -= 24
        return G(v)
    paint('neck', neck)
    return c


# ============================================================================================ mob effects (18x18)
def _effect_from(rows, mats):
    g = grid_from(rows)
    small = render(g, mats)
    out = Canvas(18, 18)
    out.paste(small, 1, 1)
    return out


@effect('seastone_drain')
def _seastone_drain():
    rows = [
        '................',
        '......cccc......',
        '.....cc..cc.....',
        '.....c....c.....',
        '.....c....c.....',
        '.....cc..cc.....',
        '......cccc......',
        '.......l........',
        '........l.......',
        '.......l..d.....',
        '..........d.....',
        '.........ddd....',
        '........ddddd...',
        '........ddDdd...',
        '.........ddd....',
        '................',
    ]
    from .palette import SEASTONE, M
    return _effect_from(rows, {'c': M(SEASTONE), 'l': Fixed('#6a7a80'), 'd': M(['#06243a', '#0e3a5e', '#1a5a8e', '#2a7ec0', '#5aaae0', '#bfe8ff'], shading='sphere'),
                               'D': Fixed('#e8f8ff')})


@effect('knocked_out')
def _knocked_out():
    rows = [
        '................',
        '.....y..........',
        '....yYy.....w...',
        '.....y.....wWw..',
        '............w...',
        '..ssssssssss....',
        '.s..........s...',
        '.s..ssssss...s..',
        '.s.s......s..s..',
        '.s.s..ss..s..s..',
        '.s..s.....s..s..',
        '..s..sssss..s...',
        '...s.......s....',
        '....sssssss..y..',
        '............yYy.',
        '.............y..',
    ]
    return _effect_from(rows, {'s': Fixed('#d8d8e8'), 'y': Fixed('#f0c830'), 'Y': Fixed('#fff8c0'), 'w': Fixed('#e8e8ff'),
                               'W': Fixed('#ffffff')})
