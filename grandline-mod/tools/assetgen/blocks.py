"""16x16 block textures: seastone, seastone bricks, seastone ore, poneglyph (side/top), fruit bloom (cross plant)."""
import math

from .canvas import Canvas, C, mix, fbm, hash01, shade
from .sprite import Grid, Mat, Fixed, Force, render, OD, OL, DK, MD, LT, HL

BLOCKS = {}


def block(name):
    def deco(fn):
        BLOCKS[name] = fn
        return fn
    return deco


def stone_levels(seed, sx=4.5, sy=3.2, levels=5, bias=0.0):
    """Tileable clustered stone pattern quantised to a few shade levels (vanilla-like, not per-pixel noise)."""
    out = [[0] * 16 for _ in range(16)]

    # tile by blending with wrapped copies (cheap seamless trick)
    def sample(x, y):
        a = fbm(x / sx, y / sy, seed, 2)
        b = fbm((x + 16) / sx, y / sy, seed, 2)
        c = fbm(x / sx, (y + 16) / sy, seed, 2)
        d = fbm((x + 16) / sx, (y + 16) / sy, seed, 2)
        tx, ty = x / 16.0, y / 16.0
        top = b * (1 - tx) + a * tx
        bot = d * (1 - tx) + c * tx
        return bot * (1 - ty) + top * ty
    raw = [[sample(x, y) + (hash01(x, y, seed + 7) - 0.5) * 0.10 + bias for x in range(16)] for y in range(16)]
    flat = sorted(v for r in raw for v in r)
    cuts = [flat[int(len(flat) * k / levels)] for k in range(1, levels)]
    for y in range(16):
        for x in range(16):
            v = raw[y][x]
            out[y][x] = sum(1 for c in cuts if v > c)
    return out


SEASTONE = ['#1f3336', '#253c3f', '#2b4447', '#314c4f', '#385558']
SEASTONE_VEIN = ['#3f6a6c', '#4c7e80', '#5f9496']


def wave_y(x, phase, amp, base):
    return base + amp * math.sin((x / 16.0) * 2 * math.pi + phase)


def seastone_canvas(seed=11, veins=True):
    lv = stone_levels(seed, sx=5.5, sy=3.6, levels=5)
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            c.set(x, y, SEASTONE[lv[y][x]])
    if veins:
        # two faint, continuous sea-wave veins (period 16 so the texture tiles)
        for phase, base, amp, k in ((0.6, 4.4, 1.5, 0), (3.6, 11.4, 1.3, 1)):
            prev = None
            for x in range(16):
                y = int(round(wave_y(x, phase, amp, base))) % 16
                col = SEASTONE_VEIN[1] if (x + k * 3) % 5 else SEASTONE_VEIN[2]
                c.set(x, y, col)
                if prev is not None and abs(prev - y) > 1:
                    c.set(x, (y + (1 if prev > y else -1)) % 16, SEASTONE_VEIN[0])
                if (x + k) % 4 == 0:
                    c.set(x, (y + 1) % 16, SEASTONE_VEIN[0])
                prev = y
    return c


@block('seastone')
def _seastone():
    return seastone_canvas()


@block('seastone_bricks')
def _seastone_bricks():
    base = seastone_canvas(seed=23, veins=False)
    c = Canvas(16, 16)
    mortar = C('#101a1c')
    lo, hi = C('#203033'), C('#56787a')
    for y in range(16):
        for x in range(16):
            row = 0 if y < 8 else 1
            yy = y % 8
            joint = 15 if row == 0 else 7
            col = base.get(x, y)
            # mortar lines
            if yy == 7 or x == joint:
                c.set(x, y, mortar)
                continue
            # brick bevel: light on top/left edges, dark on bottom/right edges
            left_edge = (x == 0 and row == 0) or (row == 1 and x in (0, 8))
            right_edge = x == joint - 1 or (row == 1 and x == 15)
            if yy == 0 or left_edge:
                col = mix(col, hi, 0.45)
            elif yy == 6 or right_edge:
                col = mix(col, lo, 0.55)
            c.set(x, y, col)
    # a faint wave vein across the lower-left brick
    for x in range(1, 7):
        y = 8 + int(round(wave_y(x * 2.2, 1.0, 1.2, 3.2)))
        if c.get(x, y) != mortar:
            c.set(x, y, SEASTONE_VEIN[0])
    for x in range(2, 13):
        y = int(round(wave_y(x * 1.4, 2.2, 1.0, 3.0)))
        if c.get(x, y) != mortar and x % 5 != 0:
            c.set(x, y, mix(c.get(x, y), SEASTONE_VEIN[1], 0.6))
    return c


STONE = ['#6c6c6e', '#767678', '#808083', '#8c8c8e', '#979799']


@block('seastone_ore')
def _seastone_ore():
    lv = stone_levels(5, levels=5)
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            c.set(x, y, STONE[lv[y][x]])
    crystals = [
        # (x, y, pattern rows) with k=dark rim, c=crystal, h=highlight, s=shade
        (2, 2, ['.kk.', 'kchk', 'kcck', '.ks.']),
        (9, 1, ['..k.', '.kck', 'kchk', 'kcsk', '.kk.']),
        (5, 8, ['.kk..', 'kcck.', 'khcsk', '.kcsk', '..kk.']),
        (11, 10, ['.kk', 'khk', 'kck', '.k.']),
        (1, 12, ['.k.', 'khk', '.k.']),
    ]
    pal = {'k': C('#1c3236'), 'c': C('#3f8e92'), 'h': C('#a8f0e8'), 's': C('#2a6268')}
    for ox, oy, rows in crystals:
        for yy, row in enumerate(rows):
            for xx, ch in enumerate(row):
                if ch != '.':
                    c.set((ox + xx) % 16, (oy + yy) % 16, pal[ch])
    return c


GLYPHS = [
    ['###', '#.#', '##.'],
    ['#.#', '.#.', '#.#'],
    ['##.', '.##', '..#'],
    ['.#.', '###', '.#.'],
    ['#..', '###', '..#'],
    ['###', '..#', '.##'],
    ['#.#', '###', '#..'],
    ['.##', '#..', '.##'],
]


def poneglyph_base(seed):
    lv = stone_levels(seed, sx=5, sy=5, levels=4)
    pal = ['#121115', '#17161b', '#1c1a21', '#221f28']
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            c.set(x, y, pal[lv[y][x]])
    return c


@block('poneglyph')
def _poneglyph_side():
    c = poneglyph_base(41)
    red, red_dim = C('#5c1818'), C('#3e1214')
    n = 0
    for gy in range(3):
        for gx in range(3):
            glyph = GLYPHS[(gx * 5 + gy * 3 + 1) % len(GLYPHS)]
            ox, oy = 2 + gx * 4 + (gy % 2), 2 + gy * 5
            for yy, row in enumerate(glyph):
                for xx, ch in enumerate(row):
                    if ch == '#' and ox + xx < 15:
                        c.set(ox + xx, oy + yy, red if hash01(ox + xx, oy + yy, 3) < 0.7 else red_dim)
            n += 1
    # carved frame
    for i in range(16):
        for (x, y) in ((i, 0), (0, i)):
            c.set(x, y, mix(c.get(x, y), C('#2c2834'), 0.6))
        for (x, y) in ((i, 15), (15, i)):
            c.set(x, y, C('#0a090c'))
    return c


@block('poneglyph_top')
def _poneglyph_top():
    c = poneglyph_base(77)
    for i in range(16):
        for (x, y) in ((i, 0), (0, i)):
            c.set(x, y, C('#2c2834'))
        for (x, y) in ((i, 15), (15, i)):
            c.set(x, y, C('#0a090c'))
    for i in range(3, 13):
        c.set(i, 3, C('#3a1414'))
        c.set(i, 12, C('#3a1414'))
        c.set(3, i, C('#3a1414'))
        c.set(12, i, C('#3a1414'))
    return c


@block('fruit_bloom')
def _fruit_bloom():
    g = Grid()
    g.ascii([
        '................',
        '.....pppppp.....',
        '...pppppppppp...',
        '..pppppppppppp..',
        '..pppppppppppp..',
        '.ppppppppppppppp',
        '..pppppppppppp..',
        '...pppppppppp...',
        '.....ppssppp....',
        '.......ss.......',
        '..lll..ss..lll..',
        '.lllll.ss.lllll.',
        '..llllsssslll...',
        '.......ss.......',
        '......sss.......',
        '......s.s.......',
    ], keep='.')
    # swirl marks on the blossom
    for x, y in ((4, 3), (5, 3), (6, 4), (5, 5), (4, 5), (9, 2), (10, 2), (11, 3), (10, 4), (9, 4), (8, 6), (7, 6), (12, 5),
                 (13, 5), (12, 6), (3, 6)):
        if g.at(x, y) == 'p':
            g.put(x, y, 'w')
    for x, y in ((7, 0), (8, 0)):
        g.put(x, y, 'b')
    petal = Mat(None, ramp=['#1e0630', '#3e0e5c', '#64208c', '#8a3cb4', '#ac64d4', '#dcaaf6'], shading='sphere', group='bloom')
    swirl = Mat(None, ramp=['#3a1858', '#7a48a6', '#c08ae8', '#e4c4ff', '#f6e6ff', '#ffffff'], shading='flat', outline=False, group='bloom')
    stem = Mat(None, ramp=['#0c2408', '#1a4210', '#2a6418', '#3c8624', '#58a836', '#86d05a'], outline=False)
    leaf = Mat(None, ramp=['#0a2a0c', '#18501a', '#2a7a28', '#3e9a36', '#62bc4e', '#9ae07a'])
    return render(g, {'p': petal, 'w': swirl, 's': stem, 'l': leaf, 'b': Mat(None, ramp=['#0c2408', '#1a4210', '#2a6418', '#3c8624', '#58a836', '#86d05a'], outline=False)})
