"""16x16 block textures (plus the 16x64 animated ward stone)."""
from __future__ import annotations

import random

from art_pixel import Canvas, C, blob, mix

# ---------------------------------------------------------------- palettes (dark -> light)
HEARTH = [C(c) for c in ('#2e2826', '#46403b', '#5d5650', '#756c64', '#8f857b', '#a89d90')]
GRAVE = [C(c) for c in ('#3d4042', '#55595b', '#6c7072', '#83878a', '#9a9ea0', '#b4b7b8')]
FIELD = [C(c) for c in ('#3b3c36', '#53554c', '#6a6c61', '#808276', '#97998b', '#adae9f')]
WARD = [C(c) for c in ('#15151b', '#202029', '#2b2b36', '#363644', '#434354', '#535366')]
MOSS = [C(c) for c in ('#2c3f1e', '#3e5826', '#55712f', '#6e8a3a', '#8aa24a')]
LICHEN = [C('#9a9a6a'), C('#b8b47e')]
EMBER = {'r': C('#5e1e0e'), 'e': C('#a8381a'), 'E': C('#e8701e'), 'F': C('#ffb440'), 'W': C('#fff0a8')}
OAK = [C(c) for c in ('#3a2616', '#563a22', '#6e4b2d', '#865d38', '#9c7045', '#b08254')]
DARKWOOD = [C(c) for c in ('#24170e', '#352315', '#47301d', '#5a3e26', '#6c4c30', '#7e5a3a')]
IRON = [C(c) for c in ('#1d1d22', '#34353b', '#4d4f56', '#6a6d75', '#9196a0', '#c2c7cf')]
BRASS = [C(c) for c in ('#5a3f16', '#8a6424', '#b88a34', '#e0b850', '#f4dc8a')]
PARCH = [C(c) for c in ('#8c7650', '#b49c6c', '#d2bc88', '#e6d4a4', '#f4e8c4')]
INK = C('#3a2c24')
SILVER = [C('#6e747e'), C('#a6adb8'), C('#d6dce4'), C('#f4f7fa')]
GLOW = [C(c) for c in ('#1f3a5c', '#2f6a9a', '#58a8d8', '#9edcf4', '#e4fbff')]
RED_CLOTH = [C(c) for c in ('#5a1c18', '#7a2a22', '#983a2c', '#b44e3a', '#c86a50')]
CREAM_CLOTH = [C(c) for c in ('#9a8a6a', '#b8a882', '#d2c4a0', '#e6dabc', '#f4ecd6')]
BLIGHT = [C(c) for c in ('#1c1a19', '#2c2926', '#3e3a35', '#544e46', '#6c665b', '#888070')]
BLIGHT_ACC = [C('#4a3a4e'), C('#5e4a62'), C('#6e6a44')]


# ================================================================ material helpers
def speckle(cv, rng, pal, x0, y0, x1, y1, n=10, mask=None, base_i=2):
    """Hand-style stone texture: small 1-3px clusters one step lighter/darker."""
    for _ in range(n):
        x, y = rng.randint(x0, x1), rng.randint(y0, y1)
        tone = pal[base_i - 1] if rng.random() < 0.55 else pal[base_i + 1]
        length = rng.choice((1, 2, 2, 3))
        for _ in range(length):
            if (mask is None or mask(x, y)) and x0 <= x <= x1 and y0 <= y <= y1:
                cv.set(x, y, tone)
            if rng.random() < 0.6:
                x += rng.choice((-1, 1))
            else:
                y += rng.choice((-1, 1))


def pits(cv, rng, pal, x0, y0, x1, y1, n=3):
    """Weathering pits: a dark pixel with a lit lower-right lip."""
    for _ in range(n):
        x, y = rng.randint(x0, x1 - 1), rng.randint(y0, y1 - 1)
        cv.set(x, y, pal[0])
        cv.set(x + 1, y + 1, pal[3])


def stone_block(cv, rng, pal, x0, y0, x1, y1, n=None, bevel=True):
    cv.rect(x0, y0, x1, y1, pal[2])
    area = (x1 - x0 + 1) * (y1 - y0 + 1)
    speckle(cv, rng, pal, x0 + 1, y0 + 1, x1 - 1, y1 - 1, n if n is not None else max(2, area // 9))
    if bevel:
        cv.hline(x0, x1, y0, pal[3])
        cv.vline(x0, y0, y1, pal[3])
        cv.hline(x0, x1, y1, pal[1])
        cv.vline(x1, y0, y1, pal[1])
        cv.set(x0, y0, pal[4])
        cv.set(x1, y0, pal[2])
        cv.set(x0, y1, pal[2])


def crack(cv, pts, dark, lit=None):
    for i in range(len(pts) - 1):
        cv.line(*pts[i], *pts[i + 1], dark)
    if lit:
        for x, y in pts[1:]:
            if cv.inb(x + 1, y) and cv.get(x + 1, y) != dark:
                cv.set(x + 1, y, lit)


def moss(cv, rng, seeds, steps, bias=(0, 1), pal=MOSS, mask=None):
    m = blob(rng, cv.w, cv.h, seeds, steps, mask=mask, bias=bias)
    for y in range(cv.h):
        for x in range(cv.w):
            if m[y, x]:
                up = y > 0 and m[y - 1, x]
                down = y < cv.h - 1 and m[y + 1, x]
                if not up:
                    c = pal[3] if rng.random() < 0.7 else pal[4]
                elif not down:
                    c = pal[1]
                else:
                    c = pal[2] if rng.random() < 0.75 else pal[1]
                cv.set(x, y, c)
    return m


def planks(cv, rng, pal, x0, y0, x1, y1, horizontal=True, size=4, seams=True, base=2):
    """Vanilla-like planks: each plank has a lit edge, grain streaks and a dark gap."""
    if horizontal:
        for py in range(y0, y1 + 1, size):
            top, bot = py, min(py + size - 1, y1)
            cv.rect(x0, top, x1, bot, pal[base])
            cv.hline(x0, x1, bot, pal[base - 2])
            for _ in range((x1 - x0 + 1) // 3):
                gx, gy = rng.randint(x0, x1), rng.randint(top, max(top, bot - 1))
                ln = rng.randint(2, 5)
                tone = pal[base - 1] if rng.random() < 0.65 else pal[base + 1]
                cv.hline(gx, min(x1, gx + ln - 1), gy, tone)
            if seams:
                sx = rng.randint(x0 + 2, x1 - 2)
                cv.vline(sx, top, bot - 1, pal[base - 2])
                cv.vline(sx + 1, top, bot - 1, pal[base + 1])
            cv.hline(x0, x1, top, pal[base + 1])
    else:
        for px in range(x0, x1 + 1, size):
            lft, rgt = px, min(px + size - 1, x1)
            cv.rect(lft, y0, rgt, y1, pal[base])
            cv.vline(rgt, y0, y1, pal[base - 2])
            cv.vline(lft, y0, y1, pal[base + 1])
            for _ in range((y1 - y0 + 1) // 3):
                gx, gy = rng.randint(lft + 1, max(lft + 1, rgt - 1)), rng.randint(y0, y1)
                ln = rng.randint(2, 5)
                tone = pal[base - 1] if rng.random() < 0.65 else pal[base + 1]
                cv.vline(gx, gy, min(y1, gy + ln - 1), tone)
            if seams:
                sy = rng.randint(y0 + 2, y1 - 2)
                cv.hline(lft, rgt - 1, sy, pal[base - 2])
                cv.hline(lft, rgt - 1, sy + 1, pal[base + 1])


def iron_band(cv, x0, y0, x1, y1, rivets=(), pal=IRON):
    cv.rect(x0, y0, x1, y1, pal[2])
    if y1 - y0 >= x1 - x0:     # vertical band
        cv.vline(x0, y0, y1, pal[3])
        cv.vline(x1, y0, y1, pal[1])
    else:
        cv.hline(x0, x1, y0, pal[3])
        cv.hline(x0, x1, y1, pal[1])
    for rx, ry in rivets:
        cv.set(rx, ry, pal[4])
        cv.set(rx + 1, ry + 1, pal[0])


# ================================================================ hearthstone
RING12 = [
    "....EEEE....",
    "..EEeeeeFF..",
    ".Ee......FE.",
    ".e........F.",
    "Ee........FE",
    "e..........F",
    "e..........F",
    "Ee........EE",
    ".e........E.",
    ".Ee......EE.",
    "..eeEEEEEe..",
    "....eeee....",
]
FLAME = [
    "...F..",
    "..FF..",
    "..FEF.",
    ".FEWF.",
    ".FWWEF",
    "FEWWWF",
    "FEWWEF",
    ".FEEF.",
    "..ee..",
]


def warm_halo(cv, base_pal, glow_col, keys, strength=0.45):
    """Tint stone pixels that touch any ember pixel."""
    src = cv.copy()
    for y in range(cv.h):
        for x in range(cv.w):
            if src.get(x, y) in keys:
                continue
            near = any(src.inb(x + dx, y + dy) and src.get(x + dx, y + dy) in keys
                       for dx in (-1, 0, 1) for dy in (-1, 0, 1))
            if near:
                cv.set(x, y, mix(src.get(x, y), glow_col, strength))


def hearthstone_top():
    rng = random.Random(101)
    cv = Canvas(16, 16)
    stone_block(cv, rng, HEARTH, 0, 0, 15, 15, n=26)
    # inner carved field
    cv.frame(2, 2, 13, 13, HEARTH[1])
    cv.hline(3, 13, 14, HEARTH[3])
    cv.vline(14, 3, 14, HEARTH[3])
    cv.ascii(RING12, EMBER, 2, 2)
    cv.ascii(FLAME, EMBER, 5, 3)
    warm_halo(cv, HEARTH, C('#d06020'), {EMBER[k] for k in EMBER}, 0.38)
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):      # corner studs
        cv.set(x, y, HEARTH[4])
    return cv


def hearthstone_side():
    rng = random.Random(102)
    cv = Canvas(16, 16)
    # upper moulding
    cv.rect(0, 0, 15, 2, HEARTH[3])
    cv.hline(0, 15, 0, HEARTH[4])
    cv.hline(0, 15, 2, HEARTH[1])
    for x in range(1, 16, 4):
        cv.set(x, 1, HEARTH[2])
    # two courses of ashlar
    stone_block(cv, rng, HEARTH, 0, 3, 8, 7)
    stone_block(cv, rng, HEARTH, 9, 3, 15, 7)
    stone_block(cv, rng, HEARTH, 0, 8, 4, 12)
    stone_block(cv, rng, HEARTH, 5, 8, 15, 12)
    # carved ember rune (Kenaz, the torch) in the central block
    rune = ["...F",
            "..Fe",
            ".Fe.",
            "Fe..",
            ".Fe.",
            "..Fe"]
    cv.ascii(rune, EMBER, 7, 3 + 1)
    cv.ascii(["..F.", ".Fe.", "Fe..", ".Fe."], EMBER, 1, 8)
    warm_halo(cv, HEARTH, C('#c05a20'), {EMBER['F'], EMBER['e']}, 0.32)
    # plinth
    cv.rect(0, 13, 15, 15, HEARTH[2])
    cv.hline(0, 15, 13, HEARTH[4])
    cv.hline(0, 15, 15, HEARTH[0])
    speckle(cv, rng, HEARTH, 0, 14, 15, 14, 4)
    # ember-lit hairline crack
    crack(cv, [(12, 8), (11, 10), (12, 12)], EMBER['e'])
    cv.set(11, 10, EMBER['E'])
    return cv


# ================================================================ storehouse
def storehouse_top():
    rng = random.Random(201)
    cv = Canvas(16, 16)
    planks(cv, rng, OAK, 0, 0, 15, 15, horizontal=True, size=4)
    iron_band(cv, 0, 0, 15, 1, rivets=((2, 0), (13, 0)))
    iron_band(cv, 0, 14, 15, 15, rivets=((2, 14), (13, 14)))
    iron_band(cv, 3, 0, 4, 15, rivets=((3, 5), (3, 9)))
    iron_band(cv, 11, 0, 12, 15, rivets=((11, 5), (11, 9)))
    for x, y in ((3, 0), (11, 0), (3, 14), (11, 14)):
        cv.set(x, y, IRON[4])
    return cv


def storehouse_side():
    rng = random.Random(202)
    cv = Canvas(16, 16)
    planks(cv, rng, OAK, 0, 0, 15, 15, horizontal=True, size=4)
    iron_band(cv, 0, 0, 1, 15, rivets=((0, 3), (0, 11)))
    iron_band(cv, 14, 0, 15, 15, rivets=((14, 3), (14, 11)))
    iron_band(cv, 0, 7, 15, 8, rivets=((4, 7), (10, 7)))
    iron_band(cv, 0, 0, 15, 0)
    iron_band(cv, 0, 15, 15, 15)
    return cv


LEDGER_FRONT = [
    # open ledger on the counter shelf; rows 2..8 of the front face
    ".DQQQQQPPQQQQQD.",
    ".DpiqiiPpiiqipD.",
    ".DpqqqqPpqqqqpD.",
    ".DpiiqiPpiqiipD.",
    ".DpqqqqPrqqqqpD.",
    ".bbbbbbbrbbbbbb.",
]


def carve(cv, marks, dark, lit, mid=None):
    """Carved groove: dark pixels with a lit lower/right lip."""
    mset = set(marks)
    for x, y in marks:
        cv.set(x, y, dark)
    for x, y in marks:
        for nx, ny in ((x + 1, y), (x, y + 1)):
            if (nx, ny) not in mset and cv.inb(nx, ny):
                cv.set(nx, ny, lit)
    if mid:
        for x, y in marks:
            if (x - 1, y) not in mset and (x, y - 1) not in mset:
                cv.set(x, y, mid)


def marks_of(rows, ox, oy, ch='s'):
    return [(ox + x, oy + y) for y, r in enumerate(rows) for x, c in enumerate(r) if c == ch]


def storehouse_front():
    rng = random.Random(203)
    cv = Canvas(16, 16)
    planks(cv, rng, OAK, 0, 0, 15, 15, horizontal=True, size=4)
    # recessed shelf with the open ledger
    cv.rect(1, 1, 14, 8, DARKWOOD[1])
    cv.hline(1, 14, 1, DARKWOOD[0])
    ink = mix(INK, PARCH[3], 0.3)
    pal = {'D': DARKWOOD[1], 'Q': PARCH[4], 'q': PARCH[3], 'p': PARCH[2], 'P': PARCH[1],
           'i': ink, 'r': C('#9a2a22'), 'b': C('#5a2e22')}
    cv.ascii(LEDGER_FRONT, pal, 0, 2)
    cv.set(8, 8, C('#7a2018'))
    # iron frame
    iron_band(cv, 0, 0, 15, 0)
    iron_band(cv, 0, 9, 15, 9, rivets=((2, 9), (12, 9)))
    iron_band(cv, 0, 15, 15, 15)
    iron_band(cv, 0, 0, 0, 15)
    iron_band(cv, 15, 0, 15, 15)
    # lock plate with keyhole
    lock = [
        ".BBBB.",
        "BbbbbB",
        "Bb..bB",
        "Bbb.bB",
        "BbbbbB",
        ".BBBB.",
    ]
    cv.rect(5, 10, 10, 14, BRASS[2])
    cv.ascii(lock, {'B': BRASS[1], 'b': BRASS[3]}, 5, 10)
    cv.set(7, 12, IRON[0])
    cv.set(8, 12, IRON[0])
    cv.set(7, 13, IRON[0])
    cv.set(6, 10, BRASS[4])
    cv.set(10, 11, BRASS[0])
    cv.set(10, 12, BRASS[0])
    cv.set(10, 13, BRASS[0])
    cv.hline(6, 9, 15, IRON[1])
    return cv


# ================================================================ gravestone material
def gravestone():
    rng = random.Random(301)
    cv = Canvas(16, 16, GRAVE[2])
    # a few broad, soft weathering patches (wrapping, so faces tile cleanly)
    for _ in range(5):
        cx, cy = rng.randint(0, 15), rng.randint(0, 15)
        tone = GRAVE[3] if rng.random() < 0.6 else GRAVE[1]
        for _ in range(rng.randint(6, 10)):
            cv.set(cx % 16, cy % 16, tone)
            cx += rng.choice((-1, 0, 1))
            cy += rng.choice((-1, 0, 1))
    speckle(cv, rng, GRAVE, 0, 0, 15, 15, 7)
    pits(cv, rng, GRAVE, 1, 1, 14, 14, 3)
    crack(cv, [(3, 3), (4, 5), (4, 7)], GRAVE[0])
    cv.set(5, 7, GRAVE[3])
    for x, y in ((10, 3), (11, 3), (11, 4)):              # lichen
        cv.set(x, y, LICHEN[0])
    cv.set(10, 4, LICHEN[1])
    moss(cv, rng, [(2, 15), (8, 15), (13, 15)], 14, bias=(0, 1))
    return cv


# ================================================================ boundary stone
def boundary_stone_side():
    rng = random.Random(401)
    cv = Canvas(16, 16)
    stone_block(cv, rng, FIELD, 0, 0, 15, 15, n=22, bevel=False)
    cv.hline(0, 15, 0, FIELD[4])
    # carved claim sigil: a pennant on a staff planted in a mound
    sig = [
        "s.......",
        "ssss....",
        "ssssss..",
        "sssssss.",
        "ssss....",
        "s.......",
        "s.......",
        "s.......",
        "s.......",
        "ss......",
        "ssss....",
    ]
    pen = marks_of(sig, 5, 2)
    carve(cv, pen, FIELD[0], FIELD[4], FIELD[1])
    for x, y in ((6, 3), (7, 4), (8, 4)):
        cv.set(x, y, FIELD[1])
    pits(cv, rng, FIELD, 0, 0, 15, 15, 2)
    moss(cv, rng, [(0, 15), (3, 15), (9, 15), (15, 15), (15, 12)], 30, bias=(0, 1))
    moss(cv, rng, [(0, 1), (14, 1)], 7, bias=(0, 1))
    return cv


def boundary_stone_top():
    rng = random.Random(402)
    cv = Canvas(16, 16)
    stone_block(cv, rng, FIELD, 0, 0, 15, 15, n=24)
    # carved survey cross in a ring
    ring = [
        "...ssss...",
        "..s....s..",
        ".s..ss..s.",
        "s...ss...s",
        "s.ssssss.s",
        "s.ssssss.s",
        "s...ss...s",
        ".s..ss..s.",
        "..s....s..",
        "...ssss...",
    ]
    carve(cv, marks_of(ring, 3, 3), FIELD[0], FIELD[4], FIELD[1])
    moss(cv, rng, [(0, 0), (1, 0), (0, 4)], 14, bias=(1, 0))
    moss(cv, rng, [(15, 15), (12, 15), (15, 11)], 16, bias=(-1, 0))
    for x, y in ((12, 2), (3, 13)):
        cv.set(x, y, LICHEN[0])
    return cv


# ================================================================ ward stone
WARD_RUNES = [  # three stacked runes for the pillar side (6 wide)
    ["s....s",
     ".s..s.",
     "..ss..",
     ".s..s.",
     "s....s"],
    ["..s...",
     "..ss..",
     "..s.s.",
     "..ss..",
     "..s..."],
    ["ssssss",
     "..s...",
     "..s...",
     "..s...",
     "......"],
]


def _ward_base(rng, top=False):
    cv = Canvas(16, 16)
    if top:
        stone_block(cv, rng, WARD, 0, 0, 15, 15, n=22)
        cv.frame(1, 1, 14, 14, WARD[1])
        return cv
    # pillar: bevelled vertical edges, two stacked blocks
    stone_block(cv, rng, WARD, 0, 0, 15, 7, n=12)
    stone_block(cv, rng, WARD, 0, 8, 15, 15, n=12)
    cv.vline(1, 0, 15, WARD[4])
    cv.vline(14, 0, 15, WARD[1])
    cv.vline(0, 0, 15, WARD[2])
    cv.vline(15, 0, 15, WARD[0])
    cv.rect(4, 1, 11, 14, WARD[1])          # recessed rune panel
    cv.hline(4, 11, 1, WARD[0])
    cv.vline(4, 1, 14, WARD[0])
    cv.hline(5, 11, 14, WARD[3])
    cv.vline(11, 2, 14, WARD[2])
    speckle(cv, rng, WARD, 5, 2, 10, 13, 5, base_i=1)
    return cv


def _ward_side_runes(cv, core, edge, halo=None, halo_strength=0.5):
    marks = []
    for rune, oy in zip(WARD_RUNES, (2, 7, 11)):
        for y, r in enumerate(rune):
            for x, ch in enumerate(r):
                if ch == 's':
                    marks.append((5 + x, oy + y))
    marks = [(x, y) for x, y in marks if 2 <= y <= 13]
    if halo is not None:
        src = cv.copy()
        mset = set(marks)
        for y in range(16):
            for x in range(16):
                if (x, y) in mset:
                    continue
                if any((x + dx, y + dy) in mset for dx in (-1, 0, 1) for dy in (-1, 0, 1)):
                    cv.set(x, y, mix(src.get(x, y), halo, halo_strength))
    for x, y in marks:
        cv.set(x, y, core)
    for x, y in marks:
        if (x, y - 1) not in marks:
            cv.set(x, y, edge)
    return cv


def ward_stone_side(active=False, frame=0):
    rng = random.Random(501)
    cv = _ward_base(rng)
    if not active:
        return _ward_side_runes(cv, SILVER[1], SILVER[2])
    pulse = (0.35, 0.5, 0.65, 0.5)[frame % 4]
    core = (GLOW[3], GLOW[3], GLOW[4], GLOW[3])[frame % 4]
    edge = (GLOW[4], GLOW[4], GLOW[4], GLOW[4])[frame % 4]
    cv = _ward_side_runes(cv, core, edge, halo=GLOW[2], halo_strength=pulse)
    # faint motes drifting upward
    for i, (x, y) in enumerate(((3, 12), (12, 9), (2, 5), (13, 3))):
        yy = (y - frame * 2) % 16
        if i % 2 == frame % 2:
            cv.set(x, yy, mix(cv.get(x, yy), GLOW[3], 0.6))
    return cv


def ward_stone_side_active_strip():
    strip = Canvas(16, 64)
    for f in range(4):
        strip.paste(ward_stone_side(True, f), 0, f * 16)
    return strip


WARD_TOP_SIGIL = [
    "....ssss....",
    "..ss.ss.ss..",
    ".s...ss...s.",
    ".s........s.",
    "s....ss....s",
    "sss.s..s.sss",
    "sss.s..s.sss",
    "s....ss....s",
    ".s........s.",
    ".s...ss...s.",
    "..ss.ss.ss..",
    "....ssss....",
]


def ward_stone_top(active=False):
    rng = random.Random(502)
    cv = _ward_base(rng, top=True)
    marks = [(2 + x, 2 + y) for y, r in enumerate(WARD_TOP_SIGIL) for x, ch in enumerate(r) if ch == 's']
    mset = set(marks)
    if active:
        src = cv.copy()
        for y in range(16):
            for x in range(16):
                if (x, y) not in mset and any((x + dx, y + dy) in mset for dx in (-1, 0, 1) for dy in (-1, 0, 1)):
                    cv.set(x, y, mix(src.get(x, y), GLOW[2], 0.45))
    for x, y in marks:
        lit = (x + y) < 15
        if active:
            cv.set(x, y, GLOW[4] if lit else GLOW[3])
        else:
            cv.set(x, y, SILVER[2] if lit else SILVER[1])
    return cv


# ================================================================ notice board
def notice_board():
    rng = random.Random(601)
    cv = Canvas(16, 16)
    planks(cv, rng, OAK, 1, 1, 14, 14, horizontal=False, size=4, seams=False, base=3)
    # dark frame
    cv.frame(0, 0, 15, 15, DARKWOOD[2])
    cv.hline(0, 15, 0, DARKWOOD[4])
    cv.vline(0, 0, 15, DARKWOOD[3])
    cv.hline(0, 15, 15, DARKWOOD[0])
    cv.vline(15, 0, 15, DARKWOOD[1])
    for x, y in ((0, 0), (15, 0), (0, 15), (15, 15)):
        cv.set(x, y, IRON[2])
    notes = [
        # (x0, y0, x1, y1, pin colour, tone)
        (2, 2, 7, 8, C('#b03a2e'), 4),
        (9, 2, 13, 6, IRON[4], 3),
        (8, 8, 13, 13, C('#3a6ab0'), 4),
        (2, 10, 6, 13, C('#c09a30'), 3),
    ]
    for x0, y0, x1, y1, pin, tone in notes:
        cv.rect(x0, y0, x1, y1, PARCH[tone])
        cv.vline(x1, y0 + 1, y1, PARCH[tone - 2])     # shadowed edge
        cv.hline(x0 + 1, x1, y1, PARCH[tone - 2])
        cv.set(x1 + 1, y1, OAK[0])                      # drop shadow
        if y1 < 14:
            cv.hline(x0 + 1, x1 + 1, y1 + 1, OAK[1])
        for ly in range(y0 + 2, y1 - 0, 2):            # ink lines
            ln = rng.randint(2, max(2, x1 - x0 - 1))
            cv.hline(x0 + 1, x0 + ln, ly, mix(INK, PARCH[tone], 0.25))
        cx = (x0 + x1) // 2
        cv.set(cx, y0, pin)
        cv.set(cx, y0 + 1, mix(pin, '#000000', 0.45))
    cv.set(13, 2, PARCH[1])      # dog-eared corner
    cv.set(2, 13, PARCH[1])
    return cv


def notice_board_side():
    rng = random.Random(602)
    cv = Canvas(16, 16)
    planks(cv, rng, OAK, 0, 0, 15, 15, horizontal=False, size=4, base=3)
    return cv


# ================================================================ market stall
def _stripe_cloth(cv, x0, y0, x1, y1, offset=0):
    for x in range(x0, x1 + 1):
        k = (x + offset) % 8
        pal = RED_CLOTH if k < 4 else CREAM_CLOTH
        tone = (3, 2, 2, 1)[k % 4]
        cv.vline(x, y0, y1, pal[tone])


def market_stall_top():
    cv = Canvas(16, 16)
    _stripe_cloth(cv, 0, 0, 15, 15)
    # a sewn seam across the middle of the awning
    for x in range(16):
        col = cv.get(x, 8)
        pal = RED_CLOTH if col in RED_CLOTH else CREAM_CLOTH
        cv.set(x, 8, pal[max(0, pal.index(col) - 1)])
    cv.hline(0, 15, 0, C('#4a3a2a'))     # hem stitching on the edges
    cv.hline(0, 15, 15, C('#4a3a2a'))
    cv.set(5, 8, mix(cv.get(5, 8), '#5a4a30', 0.4))   # weather stain
    cv.set(6, 8, mix(cv.get(6, 8), '#5a4a30', 0.3))
    return cv


def market_stall_side():
    rng = random.Random(701)
    cv = Canvas(16, 16)
    planks(cv, rng, OAK, 0, 0, 15, 15, horizontal=True, size=4, base=3)
    # corner posts
    for x0 in (0, 13):
        cv.rect(x0, 0, x0 + 2, 15, DARKWOOD[3])
        cv.vline(x0, 0, 15, DARKWOOD[4])
        cv.vline(x0 + 2, 0, 15, DARKWOOD[1])
        cv.set(x0 + 1, 9, DARKWOOD[1])
        cv.set(x0 + 1, 10, DARKWOOD[2])
    # striped valance with scalloped edge
    _stripe_cloth(cv, 0, 0, 15, 3)
    scallop = ["XXXXXXXXXXXXXXXX",
               ".XX..XX..XX..XX."]
    for y, r in enumerate(scallop):
        for x, ch in enumerate(r):
            if ch == 'X':
                k = x % 8
                pal = RED_CLOTH if k < 4 else CREAM_CLOTH
                cv.set(x, 4 + y, pal[1])
    for x in range(16):
        cv.set(x, 6 if x % 4 in (1, 2) else 5, OAK[1])   # shadow under the valance
    cv.hline(0, 15, 0, C('#4a3a2a'))
    return cv


# ================================================================ blighted crop
# hand-placed pixels per growth stage: key -> list of (x, y)
# a/b blackened grain, c/d/e/f stalk & leaf tones, p/o blight accents
CROP_STAGES = {
    0: {
        'd': [(2, 15), (2, 14), (2, 13), (5, 15), (5, 14), (8, 15), (8, 14), (8, 13), (8, 12),
              (11, 15), (11, 14), (14, 15), (14, 14), (14, 13)],
        'e': [(3, 12), (4, 13), (9, 11), (12, 13), (13, 12)],
        'c': [(4, 14), (10, 12), (12, 13), (6, 15), (1, 14), (7, 14)],
        'f': [(5, 13)],
        'p': [(9, 12)],
    },
    1: {
        'd': [(1, y) for y in range(9, 16)] + [(4, y) for y in range(7, 16)] + [(7, y) for y in range(8, 16)]
             + [(10, y) for y in range(6, 16)] + [(13, y) for y in range(8, 16)],
        'e': [(2, 8), (3, 6), (8, 7), (11, 5), (12, 7), (5, 12), (8, 13), (9, 11), (14, 13), (4, 10), (10, 9),
              (1, 11), (13, 10)],
        'c': [(3, 9), (2, 7), (9, 8), (12, 6), (11, 8), (0, 13), (6, 13), (9, 14), (8, 12), (15, 14)],
        'f': [(3, 5), (11, 4)],
        'p': [(4, 8), (10, 12)],
        'o': [(7, 10)],
    },
    2: {
        # stalks
        'd': [(3, 6), (3, 7), (3, 9), (3, 10), (2, 11), (2, 12), (2, 13), (2, 14), (2, 15)]
             + [(6, y) for y in range(6, 16)]
             + [(11, 5), (11, 6), (11, 8), (11, 9), (12, 10), (12, 11), (12, 12), (12, 13), (12, 14), (12, 15)]
             + [(14, y) for y in range(7, 16)]
             + [(9, 15), (9, 14), (9, 13), (8, 12)],
        'e': [(3, 8), (6, 5), (7, 4), (11, 7), (14, 9), (2, 9), (5, 11), (10, 10), (15, 10), (7, 12)],
        'c': [(1, 10), (1, 11), (0, 12), (4, 12), (4, 13), (9, 11), (9, 12), (15, 11), (6, 9), (12, 12)],
        'f': [(8, 4)],
        # grain heads (checker of near-black tones)
        'a': [(2, 1), (3, 2), (2, 3), (3, 4), (8, 5), (9, 6), (8, 7), (10, 0), (11, 1), (10, 2), (11, 3),
              (13, 2), (14, 3), (13, 4), (14, 5)],
        'b': [(3, 1), (2, 2), (3, 3), (2, 4), (3, 5), (9, 5), (8, 6), (9, 7), (8, 8), (11, 0), (10, 1),
              (11, 2), (10, 3), (14, 2), (13, 3), (14, 4), (13, 5), (14, 6)],
        'p': [(2, 5), (9, 8), (10, 4), (13, 6)],
        'o': [(4, 2), (12, 1), (15, 4)],
    },
}


def blight_crop(stage):
    cv = Canvas(16, 16)
    pal = {'a': BLIGHT[0], 'b': BLIGHT[1], 'c': BLIGHT[2], 'd': BLIGHT[3], 'e': BLIGHT[4],
           'f': BLIGHT[5], 'p': BLIGHT_ACC[1], 'o': BLIGHT_ACC[2]}
    for key in ('d', 'c', 'e', 'f', 'a', 'b', 'p', 'o'):
        for x, y in CROP_STAGES[stage].get(key, ()):
            cv.set(x, y, pal[key])
    return cv


BLOCKS = {
    'hearthstone_top': hearthstone_top,
    'hearthstone_side': hearthstone_side,
    'storehouse_top': storehouse_top,
    'storehouse_side': storehouse_side,
    'storehouse_front': storehouse_front,
    'gravestone': gravestone,
    'boundary_stone_top': boundary_stone_top,
    'boundary_stone_side': boundary_stone_side,
    'ward_stone_side': lambda: ward_stone_side(False),
    'ward_stone_top': lambda: ward_stone_top(False),
    'ward_stone_side_active': ward_stone_side_active_strip,
    'ward_stone_top_active': lambda: ward_stone_top(True),
    'notice_board': notice_board,
    'notice_board_side': notice_board_side,
    'market_stall_top': market_stall_top,
    'market_stall_side': market_stall_side,
    'blight_crop_stage0': lambda: blight_crop(0),
    'blight_crop_stage1': lambda: blight_crop(1),
    'blight_crop_stage2': lambda: blight_crop(2),
}
ANIMATED = {'ward_stone_side_active': {'animation': {'frametime': 8}}}
