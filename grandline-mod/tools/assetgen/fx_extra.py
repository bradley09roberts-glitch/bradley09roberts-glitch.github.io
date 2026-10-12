"""Projectile billboards for Devil Fruit waves 2-3 and race powers (32x32, glowing, transparent).

Built from a handful of parametrised shapes (orb, star, lance, crescent, rock, fist, glob, beam, shard) plus a few
hand-drawn pixel grids, in the same banded pixel-art glow style as fx.py.
"""
import math

from .canvas import Canvas, C, hash01
from .sprite import Fixed
from .fx import projectile, banded, glow_around, from_grid, crescent, bolt_path, flame_licks, FIST, N, CX, CY


# ============================================================================================ shapes
def orb(core, halo=None, r=8.5, halo_r=14.0, sparkle=None):
    c = Canvas(N, N)
    if halo:
        banded(c, CX, CY, halo_r, halo)
    banded(c, CX, CY, r, core)
    if sparkle:
        c.set(13, 13, sparkle)
        c.set(14, 12, sparkle)
    return c


def star(points, r_out, r_in, ramp, glow, rot=-math.pi / 2):
    c = Canvas(N, N)
    for y in range(N):
        for x in range(N):
            dx, dy = x + 0.5 - CX, y + 0.5 - CY
            d = math.hypot(dx, dy)
            a = (math.atan2(dy, dx) - rot) % (2 * math.pi / points)
            t = abs(a / (2 * math.pi / points) - 0.5) * 2  # 1 at a tip, 0 between tips
            edge = r_in + (r_out - r_in) * t ** 1.6
            if d <= edge:
                c.blend(x, y, C(ramp[min(len(ramp) - 1, int(d / edge * len(ramp)))]))
    glow_around(c, glow, radius=2.4, strength=150)
    return c


def lance(edge, light, mid, glow, width=3.0, spiral=None, tip=0.62, ax=4.0, ay=28.0, bx=28.5, by=3.5):
    """Elongated diamond along the diagonal; optional spiral stripes."""
    c = Canvas(N, N)
    L = math.hypot(bx - ax, by - ay)
    ux, uy = (bx - ax) / L, (by - ay) / L
    for y in range(N):
        for x in range(N):
            px, py = x + 0.5 - ax, y + 0.5 - ay
            t = (px * ux + py * uy) / L
            s = -px * uy + py * ux
            if not (0 <= t <= 1):
                continue
            w = width * (t / tip if t < tip else (1 - t) / (1 - tip))
            w = max(0.6, w) if t < 0.97 else 0.4
            if abs(s) <= w:
                col = edge if abs(s) > w - 0.9 else (light if s < 0 else mid)
                if spiral and int((t * 14 + s * 0.8) % 2) == 0 and abs(s) <= w - 0.9:
                    col = spiral
                c.set(x, y, col)
    glow_around(c, glow, radius=2.2, strength=140)
    return c


def blade(ramp, glow, angle=-45, r_out=13.5, r_in=11.0, off=5.0):
    c = Canvas(N, N)
    crescent(c, r_out, r_in, off, ramp, angle=angle)
    glow_around(c, glow, radius=2.0, strength=120)
    return c


def rock(base, dark, light, seed, r=10.0, glow=None, cracks=None, lumps=7):
    c = Canvas(N, N)
    for y in range(N):
        for x in range(N):
            dx, dy = x + 0.5 - CX, y + 0.5 - CY
            d = math.hypot(dx, dy)
            a = math.atan2(dy, dx)
            edge = r * (0.82 + 0.18 * math.cos(a * lumps + seed) + 0.08 * (hash01(int(a * 6), seed, 3) - 0.5))
            if d > edge:
                continue
            n = hash01(x // 2, y // 2, seed)
            col = light if (dx + dy < -4 and n > 0.3) else dark if (dx + dy > 5 or n < 0.18) else base
            if d > edge - 1.0:
                col = dark
            c.set(x, y, col)
            if cracks and abs(math.sin(a * 3 + seed) * d - 2) < 0.6 and d < edge - 1.5:
                c.set(x, y, cracks)
    if glow:
        glow_around(c, glow, radius=2.6, strength=160)
    return c


def fist(o, h, H, t, T, w, glow=None, flames=None):
    c = Canvas(N, N)
    if flames:
        banded(c, CX, CY, 15.5, flames[0])
        flame_licks(c, CX, CY, 9.5, n=7, seed=3, length=6.0, ramp=flames[1])
    f = from_grid(FIST, {'o': Fixed(o), 'h': Fixed(h), 'H': Fixed(H), 't': Fixed(t), 'T': Fixed(T), 'w': Fixed(w)})
    c.paste(f, 0, 1)
    if glow:
        glow_around(c, glow, radius=2.4, strength=150)
    return c


def glob(ramp, drip, glow, seed=1):
    """Sticky blob with drips trailing behind (down-left)."""
    c = Canvas(N, N)
    for i in range(4):
        a = math.pi * 0.75 + (i - 1.5) * 0.35
        L = 9 + 4 * hash01(i, seed, 2)
        x, y = CX + math.cos(a) * L, CY + math.sin(a) * L
        banded(c, x, y, 2.4 + hash01(i, seed) * 1.2, [drip, drip])
    banded(c, CX + 1, CY - 1, 9.0, ramp, sx=1.1, sy=0.95)
    c.set(13, 12, '#ffffffc0')
    c.set(14, 12, '#ffffff90')
    glow_around(c, glow, radius=1.8, strength=100, steps=2)
    return c


def beam(core, glow, width=1.4, glow_r=4.0, wave=0.0):
    c = Canvas(N, N)
    pts = []
    for i in range(9):
        t = i / 8
        x, y = 2 + t * 28, 29 - t * 26
        if wave:
            off = math.sin(t * math.pi * 3) * wave
            x, y = x + off * 0.7, y + off * 0.7
        pts.append((x, y))
    bolt_path(pts, c, C(core), glow, hw=width, glow_r=glow_r)
    return c


def shard(fill, light, edge, glow, pts=((16, 2), (25, 14), (18, 29), (8, 17))):
    c = Canvas(N, N)

    def inside(px, py):
        sign = None
        for i in range(len(pts)):
            ax, ay = pts[i]
            bx, by = pts[(i + 1) % len(pts)]
            cr = (bx - ax) * (py - ay) - (by - ay) * (px - ax)
            if sign is None:
                sign = cr > 0
            elif (cr > 0) != sign:
                return False
        return True
    for y in range(N):
        for x in range(N):
            if inside(x + 0.5, y + 0.5):
                near = any(not inside(x + 0.5 + dx, y + 0.5 + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
                c.set(x, y, edge if near else (light if x + y < 30 else fill))
    glow_around(c, glow, radius=2.0, strength=130)
    return c


def grid(rows, mats, glow=None, scale=2, glow_r=2.0):
    c = from_grid(rows, {k: Fixed(v) for k, v in mats.items()}, scale=scale)
    if glow:
        glow_around(c, glow, radius=glow_r, strength=130)
    return c


# ============================================================================================ fire / light / heat
@projectile('magma_fist')
def _magma_fist():
    return fist('#3a1408', '#c83a10', '#ffb040', '#8a2a0c', '#ff7a20', '#5a1a0a',
                flames=(['#ff6a1a50', '#c02a1040'], ['#fff2a0', '#ffd24a', '#ff9a2a', '#e0401a', '#a01a08c0', '#5a0a0480']))


@projectile('phoenix_flame')
def _phoenix_flame():
    c = orb(['#ffffff', '#e8fff8', '#a8f4ff', '#4ad8f0', '#ffd860', '#ffb030'], ['#5ad8f060', '#ffd24a40', '#ff9a2a20'], r=7.5)
    flame_licks(c, CX, CY, 7.5, n=8, seed=5, length=6.0, ramp=['#ffffff', '#e0fff8', '#a8f0ff', '#5ad0f0', '#ffd24a', '#ff9a2ac0', '#ff6a1a80'])
    return c


@projectile('prometheus_sun')
def _prometheus_sun():
    c = Canvas(N, N)
    flame_licks(c, CX, CY, 11.5, n=11, seed=7, length=3.8)
    banded(c, CX, CY, 12.0, ['#fffbe0', '#fff2a0', '#ffd24a', '#ffb030', '#ff8a20', '#e0501a'])
    for x, y, col in ((11, 12, '#3a1a0a'), (19, 12, '#3a1a0a'), (12, 18, '#5a2a0a'), (13, 19, '#5a2a0a'), (14, 19, '#5a2a0a'),
                      (15, 19, '#5a2a0a'), (16, 19, '#5a2a0a'), (17, 19, '#5a2a0a'), (18, 18, '#5a2a0a')):
        c.set(x, y, col)  # the sun has a grin
    return c


@projectile('light_bead')
def _light_bead():
    return orb(['#ffffff', '#fffde8', '#fff4a0', '#ffe060c0'], ['#fff4a060', '#ffe06030', '#ffd04018'], r=6.0, halo_r=13.0)


@projectile('wing_crescent')
def _wing_crescent():
    return blade(['#ffffff', '#fff4c8', '#ffd860', '#ffb030c0'], '#ffd24a', angle=-90)


# ============================================================================================ dark / gravity / void
@projectile('dark_debris')
def _dark_debris():
    c = Canvas(N, N)
    banded(c, CX, CY, 14.5, ['#2a0a3a90', '#1a052870', '#0a021440'])
    for i in range(6):
        a = i * 1.05 + 0.4
        x, y = CX + math.cos(a) * 9, CY + math.sin(a) * 9
        banded(c, x, y, 2.4, ['#5a4a6a', '#3a2a48', '#1a1024'])
    banded(c, CX, CY, 6.0, ['#000000', '#08020e', '#1a0628', '#3a0a5a'])
    return c


@projectile('negative_hollow')
def _negative_hollow():
    c = orb(['#f8f4ff', '#e8e0f0d0', '#c8b8e0a0', '#a890c870'], ['#c8b8e030', '#a890c818'], r=9.0)
    for x, y in ((12, 13), (19, 13)):
        for dx in range(2):
            for dy in range(3):
                c.set(x + dx, y + dy, '#3a2a4a')
    for x in range(13, 20):
        c.set(x, 19 + (1 if x in (13, 19) else 0), '#6a5a7a')  # a mournful mouth
    return c


@projectile('mini_hollow')
def _mini_hollow():
    c = orb(['#fcfaff', '#ece4f8c0', '#d0c4e890'], ['#d0c4e828'], r=6.0, halo_r=10.0)
    c.set(14, 14, '#4a3a5a')
    c.set(17, 14, '#4a3a5a')
    return c


@projectile('toku_hollow')
def _toku_hollow():
    c = orb(['#fff0f8', '#f8d8eac0', '#e8b0d090'], ['#f0a0c830', '#d080b018'], r=9.5)
    for x, y in ((12, 13), (19, 13)):
        c.set(x, y, '#5a2a4a')
        c.set(x, y + 1, '#5a2a4a')
    for x in range(14, 18):
        c.set(x, 18, '#c84a8a')
    return c


# ============================================================================================ stars / sky
@projectile('hoshi_star')
def _hoshi_star():
    return star(5, 13.5, 5.5, ['#ffffff', '#fff8c8', '#ffe880', '#ffd040', '#f0a820'], '#fff0a0')


@projectile('hoshi_meteor')
def _hoshi_meteor():
    c = Canvas(N, N)
    for i in range(12):  # tail
        t = i / 11
        banded(c, 6 + t * 10, 26 - t * 10, 2.0 + t * 2.0, ['#a8c8ff40', '#6a8aff20'])
    s = star(5, 8.5, 3.6, ['#ffffff', '#fff8c8', '#ffe880', '#ffd040'], '#fff0a0')
    c.paste(s, 4, -4)
    return c


@projectile('meteor')
def _meteor():
    c = Canvas(N, N)
    flame_licks(c, CX + 2, CY - 2, 9.0, n=5, seed=11, length=7.0)
    r = rock('#6a5a4a', '#3a2e24', '#8a7a68', 4, r=9.0, cracks='#ff8a20')
    c.paste(r, 2, -2)
    return c


@projectile('snow_shuriken')
def _snow_shuriken():
    c = star(4, 14.0, 4.0, ['#ffffff', '#f0faff', '#d0f0ff', '#a8e0f8', '#7ac8f0'], '#c8f0ff', rot=-math.pi / 4)
    banded(c, CX, CY, 2.2, ['#5aa8d8', '#5aa8d8'])
    return c


# ============================================================================================ water / sea / deep
@projectile('water_drill')
def _water_drill():
    return lance('#1a5ab0', '#bfe8ff', '#4a9ae8', '#6ac0ff', width=4.2, spiral='#e8f8ff', tip=0.3)


@projectile('race_coral_lance')
def _coral_lance():
    return lance('#8a2a3a', '#ffb8b0', '#ff6a6a', '#ff8a8a', width=3.2, spiral='#ffe0d8')


@projectile('abura_glob')
def _abura_glob():
    return glob(['#fff8c0', '#f0d860', '#c8a828', '#8a7010', '#5a4808'], '#a08a20c0', '#f0d86080', seed=3)


@projectile('mitsu_glob')
def _mitsu_glob():
    return glob(['#fff8d0', '#ffe080', '#ffc020', '#e09010', '#a86008'], '#e8a010c0', '#ffd04080', seed=5)


@projectile('mud_ball')
def _mud_ball():
    return glob(['#a8885a', '#8a6a40', '#6a4e2c', '#4e3820', '#3a2814'], '#5a4228c0', '#6a4e2c60', seed=7)


@projectile('kaeru_venom')
def _kaeru_venom():
    return glob(['#f0ffb0', '#c8f040', '#90d020', '#5a9a10', '#3a6a08'], '#7ac018c0', '#b0f04080', seed=9)


@projectile('paw_bubble')
def _paw_bubble():
    c = orb(['#ffffff20', '#e8f4ff30', '#c8e4ff40', '#a8d0ff70', '#e8f8ffd0'], None, r=12.0)
    for x, y, r in ((16, 19, 3.6), (11, 13, 1.8), (15, 11, 1.8), (19, 11, 1.8), (22, 14, 1.8)):
        banded(c, x, y, r, ['#fff0f8', '#f8c8e0', '#e0a0c0'])
    c.set(10, 8, '#ffffff')
    c.set(11, 8, '#ffffff')
    return c


# ============================================================================================ earth / stone / metal
@projectile('race_boulder')
def _race_boulder():
    return rock('#8a8478', '#5a554c', '#aaa498', 12, r=12.0)


@projectile('tsuchi_clod')
def _tsuchi_clod():
    return rock('#7a5a38', '#4e3820', '#9a7a52', 21, r=9.0, lumps=5)


@projectile('bomb_pellet')
def _bomb_pellet():
    c = Canvas(N, N)
    banded(c, CX, CY + 2, 8.0, ['#6a6a72', '#4a4a52', '#2e2e34', '#1a1a1e'])
    c.set(13, 15, '#a8a8b0')
    for i in range(5):
        c.set(18 + i // 2, 9 - i, '#8a6a3a')
    banded(c, 21, 4, 2.6, ['#ffffff', '#fff2a0', '#ff9a2a', '#ff5a1a80'])
    return c


@projectile('steel_crescent')
def _steel_crescent():
    return blade(['#ffffff', '#e8eef4', '#b8c4d0', '#8a96a4', '#5a6674'], '#d0e0f0')


@projectile('salt_shard')
def _salt_shard():
    return shard('#e8eef0', '#ffffff', '#a8b8c0', '#f0fcff')


@projectile('mirror_shard')
def _mirror_shard():
    c = shard('#a8d8e8', '#e8fcff', '#5a8a9a', '#d0f8ff', pts=((10, 3), (26, 9), (21, 28), (6, 20)))
    for i in range(6):
        c.set(12 + i, 8 + i, '#ffffff')
    return c


@projectile('mirror_beam')
def _mirror_beam():
    return beam('#ffffff', '#a8f0ff', width=1.6, glow_r=4.4)


@projectile('hari_needle')
def _hari_needle():
    return lance('#4a525a', '#f0f4f8', '#a8b0b8', '#c8d0d8', width=1.2, tip=0.85)


@projectile('kusuri_dart')
def _kusuri_dart():
    c = lance('#3a6a4a', '#d8ffe0', '#7ad08a', '#90f0a0', width=1.6, tip=0.8)
    banded(c, 7, 25, 3.0, ['#c8f0d0', '#90d0a0', '#5aa070'])  # the vial at the back
    return c


@projectile('wax_harpoon')
def _wax_harpoon():
    return lance('#a89870', '#fffcf0', '#f0e8d0', '#fff8e0', width=3.4, tip=0.75)


@projectile('wood_stake')
def _wood_stake():
    c = lance('#4a3018', '#b88a50', '#8a6030', '#a07a40', width=2.6, tip=0.86)
    for (x, y) in ((8, 22), (11, 19), (14, 16)):
        c.set(x, y, '#5a8a2a')  # a leaf or two still on it
        c.set(x + 1, y, '#7ab03a')
    return c


@projectile('kiri_lance')
def _kiri_lance():
    return lance('#1a1a1a', '#ffffff', '#d8dce0', '#e8f0f8', width=2.6, tip=0.9)


@projectile('rasen_lance')
def _rasen_lance():
    return lance('#2a4a7a', '#e8f4ff', '#7aa8e0', '#a8d0ff', width=3.8, spiral='#ffffff', tip=0.45)


# ============================================================================================ wind / smoke / sand / spores
@projectile('sand_blade')
def _sand_blade():
    c = blade(['#fff4d0', '#f0d8a0', '#d8b878', '#b89458c0', '#8a6a3880'], '#e8c890')
    for i in range(10):
        c.blend(int(4 + hash01(i, 1) * 24), int(4 + hash01(i, 2) * 24), C('#e8d0a0c0'))
    return c


@projectile('tempest_kick')
def _tempest_kick():
    return blade(['#ffffff', '#e8f8ff', '#c0e8f8c0', '#90d0f080'], '#c8f0ff', angle=-20, r_out=14.5, r_in=12.5, off=4.0)


@projectile('smoke_fist')
def _smoke_fist():
    c = Canvas(N, N)
    for i in range(8):
        a = i * 0.8
        banded(c, CX + math.cos(a) * 9, CY + math.sin(a) * 9, 4.5, ['#e8e8e8a0', '#c8c8c880', '#a0a0a040'])
    c.paste(fist('#7a7a7a', '#d8d8d8', '#ffffff', '#b8b8b8', '#ececec', '#9a9a9a'), 0, 0)
    return c


@projectile('mochi_fist')
def _mochi_fist():
    return fist('#a89078', '#fff8f0', '#ffffff', '#f0e4d4', '#fffaf4', '#e0d0bc', glow='#fff4e8')


@projectile('kabi_spore')
def _kabi_spore():
    c = Canvas(N, N)
    for i in range(9):
        x, y = 6 + hash01(i, 4) * 20, 6 + hash01(i, 5) * 20
        banded(c, x, y, 2.0 + hash01(i, 6) * 2.0, ['#d8f0a0', '#a8c860', '#7a9a38c0', '#5a7a2880'])
    banded(c, CX, CY, 4.5, ['#f0ffc0', '#c8e070', '#90b040'])
    return c


@projectile('race_feather')
def _race_feather():
    rows = [
        '................',
        '............hh..',
        '...........hHHh.',
        '..........hHwHh.',
        '.........hHwHh..',
        '........hHwHh...',
        '.......hHwHh....',
        '......hHwHh.....',
        '.....hHwHh......',
        '....hHwHh.......',
        '...hhwHh........',
        '...hwhh.........',
        '..qw............',
        '.q..............',
        '................',
        '................',
    ]
    return grid(rows, {'h': '#5a6a8a', 'H': '#c8d4e8', 'w': '#ffffff', 'q': '#3a4050'}, glow='#d0e0ff')


# ============================================================================================ toys / string / snake
@projectile('toy_soldier')
def _toy_soldier():
    rows = [
        '................',
        '......kkkk......',
        '......kkkk......',
        '......ssss......',
        '......sese......',
        '......ssss......',
        '.....rrrrrr.....',
        '....brrggrrb....',
        '....srrggrrs....',
        '......rrrr......',
        '......bbbb......',
        '......b..b......',
        '......b..b......',
        '.....kk..kk.....',
        '................',
        '................',
    ]
    return grid(rows, {'k': '#1a1a2a', 's': '#f0c8a0', 'e': '#1a1a1a', 'r': '#c82a2a', 'g': '#f0d040', 'b': '#2a3a8a'}, glow='#ffe0a0')


@projectile('toy_box')
def _toy_box():
    rows = [
        '................',
        '....yyyyyyyy....',
        '...yrrrrrrrry...',
        '...yrrbbbbrry...',
        '...yyyyyyyyyy...',
        '...rbbbbbbbbr...',
        '...rbyyyyyybr...',
        '...rbyrrrrybr...',
        '...rbyrggrybr...',
        '...rbyrggrybr...',
        '...rbyrrrrybr...',
        '...rbyyyyyybr...',
        '...rbbbbbbbbr...',
        '...rrrrrrrrrr...',
        '................',
        '................',
    ]
    return grid(rows, {'y': '#f0d040', 'r': '#c82a2a', 'b': '#2a5ab0', 'g': '#3aa040'}, glow='#fff0a0')


@projectile('string_bullet')
def _string_bullet():
    c = Canvas(N, N)
    for k in range(5):
        off = (k - 2) * 2.2
        bolt_path([(2 + off, 29 - off), (30 + off * 0.3, 3 - off * 0.3)], c, C('#ffffff'), '#e0e8ff', hw=0.35, glow_r=1.2)
    return c


@projectile('white_snake')
def _white_snake():
    c = beam('#ffffff', '#d8f0ff', width=2.0, glow_r=3.4, wave=3.0)
    for x, y in ((27, 5), (25, 5)):
        c.set(x, y, '#c82a2a')  # eyes
    return c


# ============================================================================================ Devil Fruits wave 4 (original)
@projectile('hai_cinder')
def _hai_cinder():
    """A smouldering clot of ash with ember cracks, trailing grey smoke."""
    c = Canvas(N, N)
    for i in range(7):  # smoke tail behind (down-left)
        t = i / 6
        banded(c, 8 + t * 6, 24 - t * 6, 3.5 - t * 1.2, ['#8a8a9070', '#5a5a6050'])
    r = rock('#5a5a60', '#3a3a40', '#7a7a80', 31, r=8.5, cracks='#ff7a2a', glow='#ff6a2a80', lumps=6)
    c.paste(r, 3, -3)
    return c


@projectile('yako_spark')
def _yako_spark():
    c = orb(['#ffffff', '#e0ffff', '#9af8ff', '#5af0ff', '#2ab0d8c0'], ['#5af0ff50', '#2a90c030', '#10305a18'], r=6.0, halo_r=13.0)
    for i in range(8):  # drifting plankton motes
        x, y = 5 + hash01(i, 41) * 22, 5 + hash01(i, 42) * 22
        c.blend(int(x), int(y), C('#9af8ffc0'))
    return c


@projectile('shinku_bullet')
def _shinku_bullet():
    """A sphere of nothing: a clear centre with a thin, dark-edged rim."""
    c = Canvas(N, N)
    for y in range(N):
        for x in range(N):
            d = math.hypot(x + 0.5 - CX, y + 0.5 - CY)
            if 9.0 <= d <= 10.6:
                c.blend(x, y, C('#2a2a3ee0'))
            elif 10.6 < d <= 12.5:
                c.blend(x, y, C('#dce6f080'))
            elif 6.5 <= d < 9.0:
                c.blend(x, y, C('#dce6f030'))
            elif d < 6.5:
                c.blend(x, y, C('#10101860'))
    for i in range(6):  # air streaks pulled inward
        a = i * math.pi / 3 + 0.3
        for k in range(3):
            c.blend(int(CX + math.cos(a) * (13.5 + k)), int(CY + math.sin(a) * (13.5 + k)), C('#ffffffa0'))
    return c


@projectile('kai_flame')
def _kai_flame():
    """A Flame Dial's burst: a spiral shell spitting flame."""
    c = Canvas(N, N)
    flame_licks(c, CX + 3, CY - 3, 7.0, n=7, seed=13, length=7.0)
    banded(c, CX + 3, CY - 3, 7.5, ['#fffbe0', '#fff2a0', '#ffd24a', '#ff9a2a'])
    for i in range(40):  # the shell's spiral at the back
        t = i / 39
        a = t * math.pi * 3.2
        rr = 1.0 + t * 6.0
        c.set(int(9 + math.cos(a) * rr * 0.8), int(23 + math.sin(a) * rr * 0.8), '#f0e2c8' if i % 3 else '#d8907a')
    return c


@projectile('fusen_balloon')
def _fusen_balloon():
    c = Canvas(N, N)
    banded(c, CX, CY - 3, 10.5, ['#ff8a9a', '#f05a70', '#e8405a', '#c02a44', '#8a1a30'], sx=0.9, sy=1.05)
    for x, y in ((12, 8), (13, 8), (12, 9), (13, 7)):
        c.set(x, y, '#ffe0e8')  # shine
    c.set(16, 24, '#c02a44')  # knot
    c.set(15, 25, '#c02a44')
    c.set(17, 25, '#c02a44')
    for i in range(6):  # string
        c.set(16 + (1 if i % 2 else 0), 26 + i, '#f4f4f4')
    glow_around(c, '#ffe04a', radius=1.8, strength=110, steps=2)
    return c


@projectile('shinju_pearl')
def _shinju_pearl():
    c = orb(['#ffffff', '#f8f4ff', '#f0e8f4', '#e0d0ec', '#c8b4e0', '#a890c8'], ['#c8b4e040', '#a8e0f020'], r=7.5, halo_r=11.0)
    for x, y, col in ((12, 11, '#ffffff'), (13, 11, '#ffffff'), (12, 12, '#ffffff'), (19, 19, '#f0c8e0c0'), (20, 18, '#a8e0f0c0')):
        c.set(x, y, col)
    return c


@projectile('kagi_key')
def _kagi_key():
    rows = [
        '................',
        '.........bbbb...',
        '........bBBBBb..',
        '........bB..Bb..',
        '........bB..Bb..',
        '........bBBBBb..',
        '.........bbBb...',
        '..........bBb...',
        '.........bBb....',
        '........bBb.....',
        '.......bBb......',
        '......bBbbb.....',
        '.....bBbBBb.....',
        '....bBb.bb......',
        '.....bbBb.......',
        '......bb........',
    ]
    return grid(rows, {'b': '#6a4a14', 'B': '#f0c850'}, glow='#ffe08a')


@projectile('hanabi_rocket')
def _hanabi_rocket():
    c = Canvas(N, N)
    for i in range(10):  # sparkling exhaust
        t = i / 9
        banded(c, 5 + t * 8, 27 - t * 8, 2.6 - t, ['#ffd24ac0', '#ff9a2a80'])
        c.set(int(4 + hash01(i, 51) * 10), int(18 + hash01(i, 52) * 10), '#ffffff')
    c.paste(lance('#8a1a2a', '#ff8a9a', '#e83a5a', '#ff6a8a', width=2.6, tip=0.72, ax=12.0, ay=20.0, bx=28.5, by=3.5), 0, 0)
    for k, col in enumerate(('#4ac8ff', '#ffd24a')):  # coloured bands on the tube
        for j in range(3):
            c.set(18 + k * 3 + j // 2, 14 - k * 3 - j // 2, col)
    return c


@projectile('taru_barrel')
def _taru_barrel():
    rows = [
        '................',
        '....oooooooo....',
        '...owwwwwwwwo...',
        '...oiiiiiiiio...',
        '..owwWwwwWwwwo..',
        '..owwWwwwWwwwo..',
        '..oiiiiiiiiiio..',
        '..owwWwwwWwwwo..',
        '..owwWwwwWwwwo..',
        '..oiiiiiiiiiio..',
        '..owwWwwwWwwwo..',
        '...owwwwwwwwo...',
        '...oiiiiiiiio...',
        '....oooooooo....',
        '................',
        '................',
    ]
    return grid(rows, {'o': '#3a2410', 'w': '#a0703a', 'W': '#7a5228', 'i': '#6a6a72'}, glow='#e0c090')


@projectile('taru_keg')
def _taru_keg():
    rows = [
        '..........fF....',
        '.........f..F...',
        '....oooooof.....',
        '...owwwwwwwo....',
        '..oiiiiiiiiio...',
        '..owwKKKKwwwo...',
        '..owwKKKKwwwo...',
        '..oiiiiiiiiio...',
        '..owwwwwwwwwo...',
        '..owwwwwwwwwo...',
        '..oiiiiiiiiio...',
        '...owwwwwwwo....',
        '....ooooooo.....',
        '................',
        '................',
        '................',
    ]
    c = grid(rows, {'o': '#2a1a0a', 'w': '#6a4a24', 'i': '#4a4a52', 'K': '#1a1a1a', 'f': '#c8a878', 'F': '#ffd24a'}, glow='#ff9a2a', glow_r=1.6)
    banded(c, 23, 2, 2.4, ['#ffffff', '#fff2a0', '#ff9a2a', '#ff5a1a80'])  # lit fuse
    return c


@projectile('kujira_bone')
def _kujira_bone():
    """A whale rib hurled like a harpoon, wrapped in ghost-light."""
    c = lance('#8a96a8', '#ffffff', '#d8e0e8', '#8aa8d8', width=2.2, tip=0.88)
    for (x, y) in ((6, 25), (5, 26), (7, 26), (6, 27)):
        c.set(x, y, '#e8eef4')  # the knobbed end of the rib
    return c


@projectile('raiju_ball')
def _raiju_ball():
    c = orb(['#ffffff', '#fff8c0', '#f2e14a', '#8aa0ff', '#3a5ad8'], ['#6a8aff60', '#3a5ad830', '#2a3a9018'], r=7.5)
    bolt_path([(3, 9), (8, 12), (6, 15), (11, 17)], c, C('#fff8a0'), '#f2e14a', hw=0.5, glow_r=1.6)
    bolt_path([(29, 22), (24, 20), (26, 17), (21, 15)], c, C('#fff8a0'), '#f2e14a', hw=0.5, glow_r=1.6)
    bolt_path([(18, 2), (17, 6), (20, 8)], c, C('#ffffff'), '#8aa0ff', hw=0.5, glow_r=1.4)
    return c


@projectile('sasori_spine')
def _sasori_spine():
    c = lance('#4a1a10', '#e8b878', '#a8602a', '#d8a060', width=1.4, tip=0.82)
    for (x, y) in ((25, 6), (26, 5), (27, 4), (24, 7)):
        c.set(x, y, '#9aff6a')  # venom at the point
    return c
