"""16x16 textures for the architectural building kit (plasters, timber framing, roof tiles, dressed stone, shutters,
windows, awnings, cargo and dock props, lanterns, furniture). Stylised, slightly painterly pixel art at vanilla
density: readable material shapes, a few controlled shade levels, hue-shifted ramps, no per-pixel noise.
Every texture tiles; deterministic output."""
import math

from .canvas import Canvas, C, mix, shade, hash01, value_noise, with_alpha, CLEAR

N = 16


def tnoise(x, y, seed, cell=4, octaves=2):
    """Tileable smooth noise over a 16x16 tile (cell must divide 16)."""
    p = N // cell
    tot, amp, norm, f = 0.0, 1.0, 0.0, 1
    for o in range(octaves):
        tot += value_noise(x / cell * f, y / cell * f, seed + o * 57, p * f) * amp
        norm += amp
        amp *= 0.5
        f *= 2
    return tot / norm


def levels(base, steps=(-0.55, 0.0, 0.35)):
    return [shade(base, k) if k else C(base) for k in steps]


def quantize(v, cuts):
    i = 0
    for c in cuts:
        if v > c:
            i += 1
    return i


def fill(c):
    cv = Canvas(N, N)
    for y in range(N):
        for x in range(N):
            cv.set(x, y, c)
    return cv


# ------------------------------------------------------------------------------------------- plaster
def plaster(base, seed):
    """Lime render: soft blotches in three close tones, a few pocks and hairline trowel marks."""
    pal = [shade(base, -0.36), shade(base, -0.11), C(base), shade(base, 0.14)]
    cv = Canvas(N, N)
    for y in range(N):
        for x in range(N):
            v = tnoise(x, y, seed, 8, 2) * 0.5 + tnoise(x, y, seed + 9, 4, 2) * 0.5
            i = 1 + quantize(v, (0.36, 0.64))
            if hash01(x, y, seed + 3) < 0.025:
                i = 0
            cv.set(x, y, pal[min(3, i)])
    # two short trowel arcs
    for (sx, sy, ln) in ((2 + seed % 5, 4 + seed % 3, 4), (9 - seed % 4, 11, 3)):
        for k in range(ln):
            cv.set((sx + k) % N, (sy + (1 if k == ln - 1 else 0)) % N, pal[3])
    return cv


# ------------------------------------------------------------------------------------------- wood
def wood_grain(cv, x0, y0, x1, y1, base, seed, vertical):
    pal = [shade(base, -0.9), shade(base, -0.4), C(base), shade(base, 0.35)]
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            u, v = (x, y) if vertical else (y, x)
            g = math.sin(u * 1.7 + 2.2 * tnoise(v, u, seed, 8, 1) * 3) * 0.5 + 0.5
            i = 1 + quantize(g + (hash01(x, y, seed) - 0.5) * 0.25, (0.28, 0.82))
            cv.set(x, y, pal[i])


def beam_side(base, seed):
    cv = Canvas(N, N)
    wood_grain(cv, 0, 0, 15, 15, base, seed, True)
    dark, light = shade(base, -1.2), shade(base, 0.5)
    for y in range(N):
        cv.set(0, y, dark)
        cv.set(15, y, shade(base, -0.8))
        cv.set(1, y, light if y % 7 else C(base))
    # a knot and a check (crack)
    cv.set(6, 5, shade(base, -1.1))
    cv.set(7, 5, shade(base, -0.7))
    for y in range(9, 14):
        cv.set(10, y, shade(base, -0.9))
    return cv


def beam_end(base, seed):
    cv = Canvas(N, N)
    lite = shade(base, 0.45)
    for y in range(N):
        for x in range(N):
            r = max(abs(x - 7.5), abs(y - 7.5))
            ring = int(r) % 3 == 0
            cv.set(x, y, shade(base, -0.25) if ring else lite)
    for i in range(N):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            cv.set(x, y, shade(base, -1.2))
    cv.set(7, 7, shade(base, -0.8))
    cv.set(8, 8, shade(base, -0.6))
    return cv


def planks(base, seed, gap_dark=1.4, nails=True, weathered=False):
    """Four horizontal boards with staggered butt joints, grain, dark gaps and nail heads."""
    cv = Canvas(N, N)
    gap = shade(base, -gap_dark)
    for b in range(4):
        y0 = b * 4
        bb = shade(base, ((b * 37 + seed) % 5 - 2) * 0.08)
        if weathered:
            bb = mix(bb, '#9a958a', 0.18 * ((b + seed) % 3))
        wood_grain(cv, 0, y0, 15, y0 + 2, bb, seed + b * 11, False)
        for x in range(N):
            cv.set(x, y0 + 3, gap)
        j = (b * 7 + seed * 3) % 16
        for y in range(y0, y0 + 3):
            cv.set(j, y, gap)
        cv.set((j + 1) % 16, y0, shade(bb, 0.6))
        if nails:
            cv.set((j + 2) % 16, y0 + 1, shade(base, -1.6))
            cv.set((j - 2) % 16, y0 + 1, shade(base, -1.6))
        for x in range(N):  # top edge highlight
            if (x + b) % 5:
                cv.set(x, y0, shade(cv.get(x, y0), 0.25))
    return cv


# ------------------------------------------------------------------------------------------- timber framing
def timber_frame(kind, infill='#e4d6b8', timber='#4b3424', seed=5):
    cv = plaster(infill, seed)
    tl, tm, td = shade(timber, 0.45), C(timber), shade(timber, -0.6)

    def beam(px):
        for (x, y) in px:
            cv.set(x % N, y % N, tm)

    pts = set()
    for i in range(N):
        pts.update({(i, 0), (i, 15), (0, i), (15, i)})
    if kind == 'cross':
        for i in range(N):
            pts.update({(i, i), (i, 15 - i), (min(15, i + 1), i), (max(0, 14 - i), i)})
    elif kind == 'diagonal':
        for i in range(N):
            pts.update({(i, 15 - i), (min(15, i + 1), 15 - i)})
    elif kind == 'studs':
        for i in range(N):
            pts.update({(7, i), (8, i)})
        for x in range(N):
            pts.add((x, 8))
    beam(pts)
    # shading: light top/left edge, dark bottom/right edge of each beam pixel run
    for (x, y) in pts:
        if (x, y - 1) not in pts and y > 0:
            cv.set(x, y, tl)
        if (x + 1, y) not in pts and x < 15 and (x, y + 1) not in pts:
            cv.set(x, y, td)
    for i in range(N):
        cv.set(i, 15, td)
        cv.set(15, i, td)
        cv.set(i, 0, tl)
        cv.set(0, i, tm)
    # pegs
    for (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14)):
        cv.set(x, y, shade(timber, -1.1))
    return cv


# ------------------------------------------------------------------------------------------- roofs
def barrel_tiles(base, seed, glaze=False, cap=False):
    """Clay barrel tiles: 4-px ribs in courses of 8 px, shadow under each course overlap."""
    cv = Canvas(N, N)
    for y in range(N):
        for x in range(N):
            col = x // 4
            course = y // 8
            hv = ((col * 13 + course * 7 + seed) % 5 - 2) * 0.10
            b = shade(base, hv)
            u = x % 4
            k = [-0.35, 0.45 if glaze else 0.3, 0.05, -0.75][u]
            yy = y % 8
            if yy == 7:
                k = -1.25  # shadow line under the overlap
            elif yy == 0:
                k += 0.35
            c = shade(b, k)
            if glaze and u == 1 and yy in (1, 2):
                c = shade(b, 1.4)
            cv.set(x, y, c)
    if cap:  # round end caps (Japanese kawara) at the bottom of each course
        for course in range(2):
            for col in range(4):
                cv.set(col * 4 + 1, course * 8 + 6, shade(base, 1.2))
                cv.set(col * 4 + 2, course * 8 + 6, shade(base, 0.6))
    return cv


def slate_tiles(base, seed):
    cv = Canvas(N, N)
    for y in range(N):
        row = y // 4
        off = 4 if row % 2 else 0
        for x in range(N):
            tile = ((x + off) // 8) % 2 + row * 2
            hv = ((tile * 29 + seed) % 7 - 3) * 0.07
            b = shade(base, hv)
            yy = y % 4
            xx = (x + off) % 8
            k = 0.0
            if yy == 3:
                k = -1.15
            elif yy == 0:
                k = 0.3
            if xx == 0 and yy < 3:
                k = -0.7
            if hash01(x, y, seed) < 0.06 and yy in (1, 2):
                k += 0.25
            cv.set(x, y, shade(b, k))
    return cv


def scale_tiles(base, seed):
    """Fish-scale shingles (Skypiea): rounded bottoms, staggered."""
    cv = Canvas(N, N)
    for y in range(N):
        for x in range(N):
            row = y // 4
            off = 2 if row % 2 else 0
            cx = ((x + off) // 4) * 4 + 1.5 - off
            dy = (y % 4)
            d = abs((x - cx + 8) % 4 - 1.5)
            edge = dy == 3 and d < 1.2 or dy == 2 and d >= 1.2
            hv = (((x + off) // 4 * 11 + row * 5 + seed) % 5 - 2) * 0.07
            b = shade(base, hv)
            k = -1.0 if edge else (0.35 if dy == 0 else 0.0)
            cv.set(x, y, shade(b, k))
    return cv


def thatch(base, seed):
    cv = Canvas(N, N)
    for y in range(N):
        for x in range(N):
            strand = math.sin(x * 2.1 + tnoise(x, y, seed, 4, 1) * 4) * 0.5 + 0.5
            yy = y % 8
            k = (strand - 0.5) * 0.9
            if yy >= 6:
                k -= 0.6 + (yy - 6) * 0.3
            if yy == 7 and hash01(x, y, seed) < 0.35:
                k = 0.2  # ragged straw ends
            cv.set(x, y, shade(base, k))
    return cv


# ------------------------------------------------------------------------------------------- stone
def ashlar(base, mortar, seed, h=4, w=8, chisel=True):
    cv = Canvas(N, N)
    for y in range(N):
        row = y // h
        off = (w // 2) if row % 2 else 0
        for x in range(N):
            sid = ((x + off) // w) % (N // w) + row * 7
            hv = ((sid * 31 + seed) % 5 - 2) * 0.06
            b = shade(base, hv)
            yy, xx = y % h, (x + off) % w
            if yy == h - 1 or xx == w - 1:
                cv.set(x, y, C(mortar))
                continue
            k = 0.0
            if yy == 0 or xx == 0:
                k = 0.3
            elif yy == h - 2 or xx == w - 2:
                k = -0.35
            if chisel and hash01(x, y, seed + sid) < 0.07:
                k -= 0.3
            cv.set(x, y, shade(b, k))
    return cv


def voronoi_stones(palette, mortar, seed, cells=3):
    pts = []
    step = N / cells
    for i in range(cells):
        for j in range(cells):
            pts.append((i * step + step * (0.25 + 0.5 * hash01(i, j, seed)), j * step + step * (0.25 + 0.5 * hash01(j, i, seed + 1)), (i * cells + j)))
    cv = Canvas(N, N)
    for y in range(N):
        for x in range(N):
            ds = []
            for px, py, idx in pts:
                for ox in (-N, 0, N):
                    for oy in (-N, 0, N):
                        ds.append((math.hypot(x - px - ox, y - py - oy), idx, px + ox, py + oy))
            ds.sort()
            d0, idx, px, py = ds[0]
            d1 = ds[1][0]
            if d1 - d0 < 1.1:
                cv.set(x, y, C(mortar))
                continue
            b = C(palette[idx % len(palette)])
            # light from the top-left on each rounded stone
            k = ((px - x) + (py - y)) / 6.0 * 0.5
            k = max(-0.6, min(0.5, k))
            if d1 - d0 < 2.1:
                k -= 0.35
            cv.set(x, y, shade(b, round(k * 4) / 4))
    return cv


def fluted(base, seed):
    cv = Canvas(N, N)
    pat = [-0.9, 0.35, 0.15, 0.0, -0.3, -0.55]
    for y in range(N):
        for x in range(N):
            k = pat[(x - 1) % 6] if 0 < x < 15 else -1.0
            cv.set(x, y, shade(base, k))
    return cv


def column_top(base):
    cv = Canvas(N, N)
    for y in range(N):
        for x in range(N):
            r = math.hypot(x - 7.5, y - 7.5)
            k = 0.35 if r < 5 else (-0.3 if r < 6.5 else 0.1)
            if x in (0, 15) or y in (0, 15):
                k = -0.8
            cv.set(x, y, shade(base, k))
    return cv


# ------------------------------------------------------------------------------------------- openings
def shutter(base, seed):
    cv = Canvas(N, N)
    frame, fd = shade(base, -0.5), shade(base, -1.2)
    for y in range(N):
        for x in range(N):
            if x in (0, 15) or y in (0, 15):
                cv.set(x, y, frame if x == 0 or y == 0 else fd)
            elif x in (1, 14) or y in (1, 14):
                cv.set(x, y, shade(base, -0.2))
            else:
                yy = y % 3
                cv.set(x, y, [shade(base, 0.45), C(base), shade(base, -0.85)][yy])
    for x in range(2, 14):  # middle rail
        cv.set(x, 7, frame)
        cv.set(x, 8, shade(base, 0.3))
    cv.set(1, 4, C('#2b2b2e'))
    cv.set(1, 11, C('#2b2b2e'))
    return cv


def lattice(wood, seed):
    cv = Canvas(N, N)
    w, wl, wd = C(wood), shade(wood, 0.4), shade(wood, -0.7)
    for y in range(N):
        for x in range(N):
            on = x in (0, 15) or y in (0, 15) or x % 5 == 0 or y % 5 == 0
            if on:
                cv.set(x, y, wl if (y % 5 == 0 and x % 5) else w)
            elif x % 5 == 1 and y % 5 == 1:
                cv.set(x, y, with_alpha(wd, 120))  # shadow corner reads as depth
            else:
                cv.set(x, y, CLEAR)
    return cv


def mullion(wood, glass='#bfe3f2'):
    cv = Canvas(N, N)
    for y in range(N):
        for x in range(N):
            if x in (0, 15) or y in (0, 15) or x in (7, 8) or y in (7, 8):
                k = 0.35 if (y in (0, 7) or x in (0, 7)) else -0.5
                cv.set(x, y, shade(wood, k))
            else:
                g = C(glass)
                a = 95
                if (x - y) % 7 == 0 and 2 <= x % 8 <= 4:
                    a, g = 170, shade(glass, 0.8)
                cv.set(x, y, with_alpha(g, a))
    return cv


def pane_edge(wood):
    cv = Canvas(N, N)
    for y in range(N):
        for x in range(N):
            cv.set(x, y, shade(wood, 0.2 if x % 2 else -0.2))
    return cv


# ------------------------------------------------------------------------------------------- canvas
def awning(col, seed):
    cv = Canvas(N, N)
    white = C('#efe8d8')
    for y in range(N):
        for x in range(N):
            stripe = (x // 4) % 2 == 0
            b = C(col) if stripe else white
            k = 0.0
            if x % 4 == 0:
                k = 0.25
            elif x % 4 == 3:
                k = -0.35
            if y % 8 == 7:
                k -= 0.25  # seam
            cv.set(x, y, shade(b, k))
    return cv


def awning_edge(col):
    """Scalloped valance (transparent below the scallops)."""
    cv = Canvas(N, N)
    white = C('#efe8d8')
    for y in range(N):
        for x in range(N):
            stripe = (x // 4) % 2 == 0
            b = C(col) if stripe else white
            dx = (x % 4) - 1.5
            bottom = 10 + int(round(2.2 * math.sqrt(max(0.0, 1 - (dx / 2.0) ** 2))))
            if y > bottom:
                cv.set(x, y, CLEAR)
            elif y == bottom:
                cv.set(x, y, shade(b, -0.7))
            else:
                cv.set(x, y, shade(b, 0.2 if y < 2 else (-0.15 if x % 4 == 3 else 0.0)))
    return cv


# ------------------------------------------------------------------------------------------- props
def crate_side(seed, stencil=True):
    base = C('#a97c4c')
    cv = planks(base, seed, nails=False)
    frame, fl, fd = C('#6d4c2e'), C('#8a6440'), C('#4e351f')
    for i in range(N):
        for (x, y) in ((i, 0), (i, 1), (i, 14), (i, 15), (0, i), (1, i), (14, i), (15, i)):
            cv.set(x, y, frame)
        cv.set(i, 0, fl)
        cv.set(0, i, fl)
        cv.set(i, 15, fd)
        cv.set(15, i, fd)
    for i in range(2, 14):
        cv.set(i, i, frame)
        cv.set(i + 1 if i < 13 else i, i, fl)
    for (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14)):
        cv.set(x, y, C('#3a3a3e'))
    if stencil:  # a small stencilled anchor mark
        ink = C('#3d2a1a')
        for (x, y) in ((11, 4), (11, 5), (11, 6), (11, 7), (10, 5), (12, 5), (10, 8), (12, 8), (9, 7), (13, 7)):
            cv.set(x, y, ink)
    return cv


def crate_top(seed):
    cv = planks(C('#a97c4c'), seed + 4, nails=True)
    frame = C('#6d4c2e')
    for i in range(N):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            cv.set(x, y, frame)
    return cv


def burlap(base, seed):
    cv = Canvas(N, N)
    for y in range(N):
        for x in range(N):
            k = 0.18 if (x + y) % 2 == 0 else -0.12
            if (x % 4 == 0) ^ (y % 4 == 0):
                k -= 0.12
            k += (tnoise(x, y, seed, 8, 1) - 0.5) * 0.6
            cv.set(x, y, shade(base, round(k * 5) / 5))
    return cv


def rope(base, seed):
    cv = Canvas(N, N)
    for y in range(N):
        for x in range(N):
            t = ((x + y * 2) % 6)
            k = [0.45, 0.2, 0.0, -0.25, -0.6, -1.0][t]
            cv.set(x, y, shade(base, k))
    return cv


def iron(base, seed):
    cv = Canvas(N, N)
    for y in range(N):
        for x in range(N):
            v = tnoise(x, y, seed, 8, 2)
            k = (v - 0.5) * 0.6
            if x in (3, 4):
                k += 0.5
            if x in (12, 13):
                k -= 0.4
            if hash01(x, y, seed) < 0.05:
                k = -0.8  # pitting
            cv.set(x, y, shade(base, round(k * 4) / 4))
    return cv


def net(seed):
    cv = Canvas(N, N)
    twine = C('#b49a6a')
    for y in range(N):
        for x in range(N):
            on = (x + y) % 5 == 0 or (x - y) % 5 == 0
            cv.set(x, y, shade(twine, 0.2 if (x + y) % 5 == 0 else -0.3) if on else CLEAR)
    for (x, y) in ((3, 9), (12, 4)):  # a float and a shell caught in it
        cv.set(x, y, C('#d9a441'))
        cv.set(x + 1, y, C('#b57d2a'))
    return cv


def shelf_front(seed):
    wood = C('#7a5434')
    cv = Canvas(N, N)
    for y in range(N):
        for x in range(N):
            cv.set(x, y, shade(wood, -1.0))
    goods = ['#a8473a', '#3f6aa0', '#d6b24a', '#5b8a3a', '#e6dccb', '#7d4f8c', '#c47a3a']
    for shelf_y in (6, 14):
        for x in range(N):
            cv.set(x, shelf_y, shade(wood, 0.3))
            cv.set(x, shelf_y + 1, wood)
        x = 1
        k = seed + shelf_y
        while x < 15:
            w = 1 + (k % 3 == 0)
            h = 3 + (k % 3)
            c = C(goods[k % len(goods)])
            for xx in range(x, min(15, x + w)):
                for yy in range(shelf_y - h, shelf_y):
                    cv.set(xx, yy, shade(c, 0.35 if xx == x else 0.0))
            x += w + (1 if k % 4 == 0 else 0)
            k = (k * 7 + 3) % 97
    for y in range(N):
        cv.set(0, y, wood)
        cv.set(15, y, shade(wood, -0.5))
    for x in range(N):
        cv.set(x, 0, wood)
    return cv


def paper_lantern(paper, seed):
    cv = Canvas(N, N)
    rib = shade(paper, -0.9)
    for y in range(N):
        for x in range(N):
            k = 0.35 if 5 <= x <= 9 else (0.0 if 3 <= x <= 12 else -0.35)
            c = shade(paper, k)
            if y % 3 == 0:
                c = rib
            cv.set(x, y, c)
    # a painted character band
    for (x, y) in ((7, 5), (8, 5), (7, 7), (8, 7), (6, 6), (9, 6), (7, 8)):
        cv.set(x, y, C('#1d1b1b'))
    return cv


def lantern_cap(seed):
    cv = Canvas(N, N)
    for y in range(N):
        for x in range(N):
            cv.set(x, y, shade('#2e2a28', 0.3 if (x + y) % 3 == 0 else 0.0))
    return cv


def brass_lantern(seed):
    cv = Canvas(N, N)
    brass = C('#b8913a')
    glow = C('#ffe08a')
    for y in range(N):
        for x in range(N):
            if x in (0, 1, 14, 15) or y in (0, 1, 14, 15) or x in (7, 8):
                k = 0.4 if (x in (0, 7) or y in (0, 1)) else -0.4
                cv.set(x, y, shade(brass, k))
            else:
                d = math.hypot(x - 7.5, y - 8)
                cv.set(x, y, shade(glow, 0.6 if d < 3 else (0.2 if d < 5 else -0.3)))
    return cv


def brass(seed):
    cv = Canvas(N, N)
    for y in range(N):
        for x in range(N):
            cv.set(x, y, shade('#b8913a', 0.4 if (x + y) % 5 == 0 else (-0.2 if (x * 3 + y) % 7 == 0 else 0.0)))
    return cv


def polished_wood(base, seed):
    cv = Canvas(N, N)
    wood_grain(cv, 0, 0, 15, 15, base, seed, False)
    for x in range(N):
        if x % 4 == 1:
            cv.set(x, 2, shade(base, 0.9))
    return cv


def counter_front(base, seed):
    cv = planks(base, seed, nails=False)
    frame = shade(base, -0.9)
    for i in range(N):
        for (x, y) in ((i, 0), (i, 15), (0, i), (15, i)):
            cv.set(x, y, frame)
        cv.set(i, 1, shade(base, 0.5))
    for y in range(3, 13):  # recessed panels
        cv.set(3, y, frame)
        cv.set(12, y, frame)
    for x in range(3, 13):
        cv.set(x, 3, frame)
        cv.set(x, 12, shade(base, 0.4))
    return cv
