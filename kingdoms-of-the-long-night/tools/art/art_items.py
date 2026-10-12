"""16x16 item icons (transparent background, vanilla-style shading + outlines)."""
from __future__ import annotations

import math

from art_pixel import Canvas, C, mix

# ---------------------------------------------------------------- palettes (outline, dark, mid, light, highlight)
COPPER = [C(c) for c in ('#4a2214', '#8a4424', '#b8642e', '#dc8a48', '#f6be80')]
SILVER = [C(c) for c in ('#363a44', '#787e8c', '#a6acb8', '#cdd3dc', '#f4f7fb')]
GOLD = [C(c) for c in ('#553608', '#a46e14', '#d6a22a', '#f0c84a', '#fff2a8')]
BRONZE = [C(c) for c in ('#3a200e', '#744420', '#a2642c', '#c68640', '#e8b46c')]
PARCH = [C(c) for c in ('#5e4a30', '#a68c5c', '#cdb684', '#e4d3a6', '#f6ecca')]
NIGHTMAP = [C(c) for c in ('#0e1430', '#1c2856', '#2a3c7a', '#3e56a0', '#6a86c8')]
CHALK = [C(c) for c in ('#5e5a52', '#9e9a8e', '#cfcbbf', '#e9e6dc', '#fbfaf5')]
IRON = [C(c) for c in ('#18181c', '#34363c', '#545760', '#7c808a', '#aab0ba')]
WOOD = [C(c) for c in ('#2e1c10', '#5a3a20', '#7a522e', '#98693c', '#b4834e')]
LEATHER = [C(c) for c in ('#2a140c', '#5a2c1a', '#7a4026', '#9a5634', '#b67048')]
WAX = [C(c) for c in ('#3e0c0a', '#7a1a14', '#a82a22', '#cc4636', '#ea7a62')]
RIBBON_BLUE = [C(c) for c in ('#1c2a4e', '#2e4a86', '#4a6eb4', '#7a9ad6', '#b2c8ee')]
EMBER = {'e': C('#8a2a12'), 'E': C('#e0681c'), 'F': C('#ffb03a'), 'W': C('#fff0a0')}
CRYSTAL = [C(c) for c in ('#07040b', '#130c1c', '#1f1530', '#33234c', '#523c7a', '#8a70bc')]
BLIGHT = [C(c) for c in ('#221e1b', '#38322c', '#5a5246', '#6e6656', '#8a8270', '#a89e88')]
INK = C('#3a2c22')


def outline(cv, col):
    cv.outline(col, diagonal=False)


# ================================================================ coins
def coin(cv, cx, cy, r, pal, emblem=(), thick=1):
    """Coin face (disc) with a visible edge below, rim lighting and a stamped emblem."""
    for y in range(16):
        for x in range(16):
            px, py = x + 0.5, y + 0.5
            if (px - cx) ** 2 + (py - cy - thick) ** 2 <= r * r:
                cv.set(x, y, pal[1])
    for y in range(16):
        for x in range(16):
            px, py = x + 0.5, y + 0.5
            d = math.hypot(px - cx, py - cy)
            if d <= r:
                lit = (px - cx) + (py - cy) < 0
                if d > r - 1.0:
                    cv.set(x, y, pal[3] if lit else pal[2])
                elif d > r - 2.0:
                    cv.set(x, y, pal[2] if lit else pal[3])
                else:
                    cv.set(x, y, pal[2])
    ew = len(emblem[0]) if emblem else 0
    eh = len(emblem)
    ox, oy = int(round(cx - ew / 2)), int(round(cy - eh / 2))
    for y, row in enumerate(emblem):
        for x, ch in enumerate(row):
            if ch == 'x':
                cv.set(ox + x, oy + y, pal[1])
            elif ch == 'h':
                cv.set(ox + x, oy + y, pal[4])
            elif ch == 'l':
                cv.set(ox + x, oy + y, pal[3])
    # specular glint
    gx, gy = int(cx - r * 0.55), int(cy - r * 0.55)
    cv.set(gx, gy, pal[4])
    cv.set(gx + 1, gy, pal[4])
    cv.set(gx, gy + 1, pal[4])
    outline(cv, pal[0])


def copper_penny():
    cv = Canvas(16, 16)
    coin(cv, 8, 8.5, 4.1, COPPER, [".x.", "xlx", ".x."])
    return cv


def silver_mark():
    cv = Canvas(16, 16)
    coin(cv, 8, 8, 5.1, SILVER, ["x.x.x", "xxxxx", ".xlx.", ".x.x."])
    return cv


def gold_crown():
    cv = Canvas(16, 16)
    coin(cv, 8, 7.6, 6.1, GOLD, ["h.h.h", "xhxhx", "xxxxx"])
    return cv


# ================================================================ scrolls (capsules)
def capsule(cv, p0, p1, r, pal, cap_pal=None, cap_end=True):
    """Rolled cylinder from p0 to p1, lit from the upper-left; returns (axis, length)."""
    (x0, y0), (x1, y1) = p0, p1
    L = math.hypot(x1 - x0, y1 - y0)
    ax, ay = (x1 - x0) / L, (y1 - y0) / L
    nx, ny = -ay, ax                     # perpendicular
    if nx + ny > 0:                      # make "negative v" face the upper-left (lit)
        nx, ny = -nx, -ny
    for y in range(16):
        for x in range(16):
            px, py = x + 0.5 - x0, y + 0.5 - y0
            t = px * ax + py * ay
            v = px * nx + py * ny        # >0 = upper-left side
            if -0.2 <= t <= L + 0.2 and abs(v) <= r:
                k = (v + r) / (2 * r)    # 0 = shadow side, 1 = lit side
                idx = 1 if k < 0.25 else 2 if k < 0.6 else 3 if k < 0.85 else 4
                cv.set(x, y, pal[idx])
    if cap_end:
        cp = cap_pal or pal
        for y in range(16):
            for x in range(16):
                d = math.hypot(x + 0.5 - x1, y + 0.5 - y1)
                if d <= r + 0.15:
                    cv.set(x, y, cp[3] if d > r - 0.9 else cp[1])
        cv.set(int(x1), int(y1), cp[0])
    return (ax, ay), L


def band_across(cv, p0, p1, t_frac, half, col, r):
    (x0, y0), (x1, y1) = p0, p1
    L = math.hypot(x1 - x0, y1 - y0)
    ax, ay = (x1 - x0) / L, (y1 - y0) / L
    nx, ny = -ay, ax
    for y in range(16):
        for x in range(16):
            px, py = x + 0.5 - x0, y + 0.5 - y0
            t = px * ax + py * ay
            v = px * nx + py * ny
            if abs(t - t_frac * L) <= half and abs(v) <= r + 0.3:
                cv.set(x, y, col(v) if callable(col) else col)


def wax_seal(cv, cx, cy, r=2.2, pal=WAX, emblem=None):
    for y in range(16):
        for x in range(16):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            if d <= r:
                lit = (x + 0.5 - cx) + (y + 0.5 - cy) < -0.5
                cv.set(x, y, pal[3] if lit else (pal[1] if d > r - 0.8 else pal[2]))
    cv.set(int(cx - 1), int(cy - 1), pal[4])
    if emblem:
        for x, y, c in emblem:
            cv.set(x, y, c)


def land_deed():
    cv = Canvas(16, 16)
    p0, p1 = (2.5, 13.0), (13.0, 2.5)
    capsule(cv, p0, p1, 2.3, PARCH)
    band_across(cv, p0, p1, 0.45, 0.8, lambda v: WAX[3] if v > 0 else WAX[1], 2.3)
    # ribbon tails + hanging seal
    for x, y in ((8, 9), (8, 10), (9, 10)):
        cv.set(x, y, WAX[1])
    wax_seal(cv, 10.5, 11.5, 2.2)
    cv.set(10, 11, WAX[1])
    cv.set(11, 12, WAX[1])
    outline(cv, PARCH[0])
    return cv


def star_chart():
    cv = Canvas(16, 16)
    p0, p1 = (2.5, 13.0), (13.0, 2.5)
    capsule(cv, p0, p1, 2.6, NIGHTMAP, cap_pal=[NIGHTMAP[0], NIGHTMAP[1], NIGHTMAP[2], C('#c8d4ee'), C('#ffffff')])
    for x, y, c in ((4, 10, '#fff6c0'), (7, 9, '#f0d878'), (6, 7, '#ffffff'), (9, 5, '#f0d878'),
                    (10, 7, '#fff6c0'), (3, 12, '#c8d0f0'), (11, 4, '#ffffff')):
        if cv.opaque(x, y):
            cv.set(x, y, C(c))
    band_across(cv, p0, p1, 0.55, 0.6, lambda v: GOLD[3] if v > 0 else GOLD[1], 2.6)
    cv.set(10, 10, GOLD[2])
    cv.set(11, 11, GOLD[1])
    cv.set(10, 11, GOLD[3])
    outline(cv, NIGHTMAP[0])
    return cv


def ward_chalk():
    cv = Canvas(16, 16)
    p0, p1 = (3.0, 13.0), (12.0, 4.0)
    capsule(cv, p0, p1, 1.6, CHALK, cap_end=False)
    for x, y in ((12, 4), (11, 3)):           # worn, flattened tip
        cv.set(x, y, CHALK[4])
    outline(cv, CHALK[0])
    for x, y in ((14, 2), (13, 1), (14, 5)):   # chalk dust
        cv.set(x, y, CHALK[3])
    return cv


# ================================================================ ASCII icons
def ascii_icon(rows, pal):
    cv = Canvas(16, 16)
    cv.ascii(rows, pal)
    return cv


def settlement_ledger():
    rows = [
        "................",
        "..oooooooooooo..",
        ".oSGHHHHHHHHGpo.",
        ".oSLLLLLLLLLLpo.",
        ".oHLLLLLLLLLLqo.",
        ".oSLLwwwwwwLLpo.",
        ".oSLLwiiiiwLLqo.",
        ".oSLLwwwwwwLLpo.",
        ".oSssssssssssgo.",
        ".oSLLLLLLLLLLqo.",
        ".oHLLLLLLLLLLpo.",
        ".oSLLLLLLLLLLqo.",
        ".oSGllllllllGpo.",
        ".ooqqqqqqqqqqqo.",
        "..ooooooooooooo.",
        "................",
    ]
    pal = {'o': LEATHER[0], 'S': LEATHER[1], 'L': LEATHER[2], 'l': LEATHER[1], 'H': LEATHER[3],
           'G': GOLD[2], 'g': GOLD[3], 'p': PARCH[4], 'q': PARCH[2], 'w': PARCH[3],
           'i': mix(INK, PARCH[3], 0.25), 's': C('#2e1a10')}
    cv = ascii_icon(rows, pal)
    for x in range(4, 12):                # leather grain / wear
        if x % 3 == 0:
            cv.set(x, 3, LEATHER[3])
    cv.set(10, 10, LEATHER[1])
    cv.set(5, 11, LEATHER[3])
    return cv


def letter():
    cv = Canvas(16, 16)
    P = PARCH
    cv.rect(1, 4, 14, 12, P[3])
    for y in range(4, 9):                  # top flap
        a, b = 2 + (y - 4), 13 - (y - 4)
        cv.hline(a, b, y, P[4])
        cv.set(a - 1 if a > 2 else a, y, P[2])
        cv.set(a, y, P[2])
        cv.set(b, y, P[2])
    cv.line(2, 12, 6, 9, P[2])             # lower folds
    cv.line(13, 12, 9, 9, P[2])
    cv.hline(2, 13, 12, P[2])
    outline(cv, P[0])
    wax_seal(cv, 8.0, 9.0, 2.3)
    cv.set(7, 8, WAX[4])
    return cv


def founding_charter():
    cv = Canvas(16, 16)
    P = PARCH
    cv.rect(4, 3, 11, 12, P[3])            # sheet
    cv.vline(4, 3, 12, P[4])
    cv.vline(11, 3, 12, P[2])
    for y in (5, 7, 9):                    # text
        cv.hline(5, 10 if y != 9 else 8, y, mix(INK, P[3], 0.3))
    for y, idx in ((1, 4), (2, 3), (3, 2)):          # top roll
        cv.hline(3, 12, y, P[idx])
    cv.set(2, 2, P[1])
    cv.set(13, 2, P[1])
    for y, idx in ((12, 4), (13, 3), (14, 2)):       # bottom roll
        cv.hline(3, 12, y, P[idx])
    cv.set(2, 13, P[1])
    cv.set(13, 13, P[1])
    outline(cv, P[0])
    # blue ribbon + red wax with a tiny gold crown
    for x, y in ((9, 10), (8, 11), (10, 11), (8, 12), (10, 12), (7, 13), (11, 13), (7, 14), (11, 14)):
        cv.set(x, y, RIBBON_BLUE[2] if x < 9 else RIBBON_BLUE[1])
    cv.set(6, 15, RIBBON_BLUE[1])
    cv.set(12, 15, RIBBON_BLUE[1])
    wax_seal(cv, 9.5, 10.5, 2.3)
    crown = [(8, 9, GOLD[3]), (9, 10, GOLD[2]), (10, 9, GOLD[3]), (8, 10, GOLD[3]), (10, 10, GOLD[2]),
             (9, 9, WAX[2])]
    for x, y, c in crown:
        cv.set(x, y, c)
    cv.set(8, 11, GOLD[2])
    cv.set(9, 11, GOLD[3])
    cv.set(10, 11, GOLD[2])
    return cv


def wedding_token():
    cv = Canvas(16, 16)
    R = RIBBON_BLUE
    for i in range(1, 15):                   # ribbon threaded diagonally behind the rings
        cv.set(i, 15 - i, R[3])
        cv.set(i, 16 - i, R[1])
    cv.set(0, 15, R[2])
    cv.set(15, 0, R[2])
    cv.set(14, 0, R[3])

    def ring(cx, cy, keep=None):
        for y in range(16):
            for x in range(16):
                d = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
                if 1.9 <= d <= 3.35 and (keep is None or keep(x, y)):
                    lit = (x + 0.5 - cx) + (y + 0.5 - cy) < 0
                    if lit:
                        cv.set(x, y, GOLD[4] if d < 2.5 else GOLD[3])
                    else:
                        cv.set(x, y, GOLD[1] if d > 2.7 else GOLD[2])
    ring(6.0, 6.5)
    ring(10.0, 9.5)                                         # ring 2 over ring 1 on the right...
    ring(6.0, 6.5, lambda x, y: x < 8 and y >= 7)           # ...ring 1 over ring 2 on the left
    outline(cv, C('#2e2412'))
    return cv


def trade_seal():
    cv = Canvas(16, 16)
    for x, y, c in ((5, 12, 3), (5, 13, 2), (4, 14, 2), (4, 15, 1), (10, 12, 2), (10, 13, 1), (11, 14, 1),
                    (11, 15, 0), (6, 13, 2), (9, 13, 1)):
        cv.set(x, y, WAX[c + 1])            # red ribbon tails behind the seal
    coin(cv, 8, 7.4, 6.1, BRONZE, [], thick=1)
    ship = ["....s....",
            "....ss...",
            "....sss..",
            "....ssss.",
            "....s....",
            "hhhhhhhhh",
            ".hhhhhhh.",
            "..xxxxx.."]
    for y, row in enumerate(ship):
        for x, ch in enumerate(row):
            if ch == '.':
                continue
            col = {'s': BRONZE[4], 'h': BRONZE[3], 'x': BRONZE[1]}[ch]
            cv.set(4 + x, 3 + y, col)
    for x, y in ((9, 4), (10, 5), (11, 6), (12, 6)):
        if cv.get(x, y) == BRONZE[2]:
            cv.set(x, y, BRONZE[1])
    return cv


def ember_relic():
    rows = [
        "................",
        ".......oo.......",
        "......o..o......",
        ".......oo.......",
        ".....oiiiio.....",
        "....oIIIIIIo....",
        "....I.w..w.I....",
        "....IewFFweI....",
        "....IFwWWwFI....",
        "....IEwWFwEI....",
        "....IewEEweI....",
        "....I.w..w.I....",
        "....oIIIIIIo....",
        ".....oiiiio.....",
        "......o..o......",
        "................",
    ]
    pal = {'o': IRON[0], 'I': IRON[2], 'i': IRON[3], 'w': C('#a8704a')}
    pal.update(EMBER)
    cv = ascii_icon(rows, pal)
    for x, y in ((3, 8), (12, 8), (7, 5), (8, 12)):   # warm glow on the frame
        if cv.opaque(x, y):
            cv.set(x, y, mix(cv.get(x, y), EMBER['E'], 0.4))
    cv.set(10, 4, EMBER['F'])
    cv.set(5, 3, mix(EMBER['E'], '#000000', 0.2))
    return cv


def night_shard():
    cv = Canvas(16, 16)
    p0, p1 = (3.0, 13.0), (13.5, 1.5)
    L = math.hypot(p1[0] - p0[0], p1[1] - p0[1])
    ax, ay = (p1[0] - p0[0]) / L, (p1[1] - p0[1]) / L
    nx, ny = ay, -ax
    if nx + ny > 0:
        nx, ny = -nx, -ny
    for y in range(16):
        for x in range(16):
            px, py = x + 0.5 - p0[0], y + 0.5 - p0[1]
            t = px * ax + py * ay
            v = px * nx + py * ny
            tf = t / L
            rmax = 2.6 if tf < 0.62 else 2.6 * (1 - tf) / 0.38
            if tf < 0.08:                    # broken, jagged base
                rmax = 2.6 if (x + y) % 2 else 1.8
            if 0 <= t <= L and abs(v) <= rmax:
                k = v / max(rmax, 0.01)       # +1 lit edge, -1 dark edge
                idx = 5 if k > 0.6 else 4 if k > 0.15 else 3 if k > -0.35 else 2 if k > -0.75 else 1
                cv.set(x, y, CRYSTAL[idx])
    for x, y, c in ((6, 9, '#e8e0ff'), (9, 6, '#b8a8e0'), (5, 11, '#9a88c8'), (11, 4, '#f4f0ff'),
                    (8, 8, '#7a68a8')):
        if cv.opaque(x, y):
            cv.set(x, y, C(c))
    outline(cv, CRYSTAL[0])
    return cv


def maker_mark_stamp():
    rows = [
        "................",
        "......oooo......",
        ".....oHhhwo.....",
        ".....ohhhwo.....",
        "......owwo......",
        ".......Hw.......",
        ".......Hw.......",
        ".......Hw.......",
        "...oooHhwwooo...",
        "..oHHhhhhhwwwo..",
        "..owwwwwwwwwwo..",
        "..oiiiiiiiiiio..",
        "..oIIIIMIIIIIo..",
        "..oIIIMMMIIIIo..",
        "..oDDDDMDDDDDo..",
        "...oooooooooo...",
    ]
    pal = {'o': IRON[0], 'H': WOOD[4], 'h': WOOD[3], 'w': WOOD[1], 'i': IRON[4], 'I': IRON[2],
           'M': IRON[4], 'D': IRON[1]}
    cv = ascii_icon(rows, pal)
    for y in range(5, 8):
        cv.set(6, y, IRON[0])
        cv.set(9, y, IRON[0])
    return cv


def banner_shield():
    cv = Canvas(16, 16)
    spans = {1: (2, 13), 2: (2, 13), 3: (2, 13), 4: (2, 13), 5: (2, 13), 6: (2, 13), 7: (2, 13),
             8: (3, 12), 9: (3, 12), 10: (4, 11), 11: (4, 11), 12: (5, 10), 13: (6, 9), 14: (7, 8)}
    inside = {(x, y) for y, (a, b) in spans.items() for x in range(a, b + 1)}
    field = [C('#14203e'), C('#1e3060'), C('#2a4282')]
    for x, y in inside:
        cv.set(x, y, field[2] if x <= 4 else field[1] if x <= 10 else field[0])
    # iron rim
    for x, y in inside:
        if any((x + dx, y + dy) not in inside for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            lit = x < 8 and y < 8
            cv.set(x, y, IRON[4] if lit else IRON[2])
    for x, y in ((2, 1), (13, 1), (7, 14), (8, 14)):
        cv.set(x, y, IRON[3])
    # ember sun in chief
    sun = [".F.F.", "FWWWF", ".WEW.", "FWWWF", ".F.F."]
    pal = {'F': EMBER['F'], 'W': C('#ffd870'), 'E': EMBER['E']}
    cv.ascii(sun, pal, 5, 3)
    # silver chevron
    chev = [(4, 11), (5, 10), (6, 9), (7, 8), (8, 8), (9, 9), (10, 10), (11, 11),
            (5, 11), (6, 10), (7, 9), (8, 9), (9, 10), (10, 11)]
    for x, y in chev:
        if (x, y) in inside and cv.get(x, y) not in (IRON[4], IRON[2]):
            cv.set(x, y, SILVER[3] if x < 8 else SILVER[2])
    for x, y in ((4, 2), (11, 2), (4, 7), (11, 7)):   # rivets
        cv.set(x, y, IRON[3])
    outline(cv, IRON[0])
    return cv


WHEAT_SHEAF = {
    # 0/1 blackened grain checker, 2 lit grain tip, 3-5 ashen stalks (dark->light)
    0: [(3, 2), (4, 3), (3, 4), (4, 5), (3, 6), (8, 1), (9, 2), (8, 3), (9, 4), (8, 5),
        (12, 6), (13, 7), (14, 8), (13, 9)],
    1: [(4, 2), (3, 3), (4, 4), (3, 5), (4, 6), (9, 1), (8, 2), (9, 3), (8, 4), (9, 5),
        (13, 6), (12, 7), (13, 8), (14, 9)],
    2: [(3, 1), (9, 0), (12, 5), (14, 10)],
    3: [(4, 8), (8, 7), (7, 10), (10, 9), (3, 14), (5, 12), (6, 11)],
    4: [(4, 7), (5, 9), (5, 10), (9, 6), (8, 8), (7, 9), (6, 10), (11, 6), (11, 7), (10, 8), (9, 9),
        (8, 10), (4, 12), (4, 13), (6, 12), (5, 13)],
    5: [(2, 14), (4, 14), (3, 13)],
}


def blighted_wheat():
    cv = Canvas(16, 16)
    tone = {0: BLIGHT[0], 1: BLIGHT[2], 2: BLIGHT[4], 3: BLIGHT[3], 4: BLIGHT[4], 5: BLIGHT[5]}
    for k in (3, 4, 5, 2, 1, 0):
        for x, y in WHEAT_SHEAF[k]:
            cv.set(x, y, tone[k])
    for x, y in ((3, 3), (9, 2), (13, 8)):
        cv.set(x, y, C('#6a5470'))          # blight spores
    for x, y in ((4, 11), (5, 11), (6, 11), (7, 11)):
        cv.set(x, y, C('#7a6448'))          # twine tie
    outline(cv, C('#0c0a08'))
    return cv


ITEMS = {
    'copper_penny': copper_penny,
    'silver_mark': silver_mark,
    'gold_crown': gold_crown,
    'land_deed': land_deed,
    'trade_seal': trade_seal,
    'wedding_token': wedding_token,
    'founding_charter': founding_charter,
    'settlement_ledger': settlement_ledger,
    'letter': letter,
    'ward_chalk': ward_chalk,
    'blighted_wheat': blighted_wheat,
    'ember_relic': ember_relic,
    'night_shard': night_shard,
    'star_chart': star_chart,
    'maker_mark_stamp': maker_mark_stamp,
    'banner_shield': banner_shield,
}
