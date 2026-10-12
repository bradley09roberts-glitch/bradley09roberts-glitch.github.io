"""16x16 item sprites. Weapons are laid out along the vanilla tool diagonal (handle bottom-left, tip top-right) using
axis-space helpers; everything else is hand-authored ASCII art rendered through material ramps."""
import math

from .canvas import Canvas, C, mix
from .sprite import Grid, Mat, Fixed, Force, render, grid_from, OD, OL, DK, MD, LT, HL
from .palette import *

ITEMS = {}          # id -> fn() -> Canvas
LAYERED = {}        # id -> fn() -> [Canvas, Canvas]
HOLE = Fixed((0, 0, 0, 0))


def item(name):
    def deco(fn):
        ITEMS[name] = fn
        return fn
    return deco


def art(rows):
    rows = list(rows)
    assert len(rows) == 16, (len(rows), rows)
    for r in rows:
        assert len(r) == 16, (len(r), r)
    return grid_from(rows, 16, 16)


# ============================================================================================ helpers
def tip(lo, hi, b1, n, mode='lo'):
    """dlo/dhi callables that taper a blade over its last n axis units ('lo' edge rises, 'hi' falls, 'both')."""
    def f_lo(p):
        if mode in ('lo', 'both') and p > b1 - n:
            k = (p - (b1 - n)) / n
            return lo + math.ceil(k * (hi - lo) * (0.5 if mode == 'both' else 1.0) - 1e-9)
        return lo

    def f_hi(p):
        if mode in ('hi', 'both') and p > b1 - n:
            k = (p - (b1 - n)) / n
            return hi - math.ceil(k * (hi - lo) * (0.5 if mode == 'both' else 1.0) - 1e-9)
        return hi
    return f_lo, f_hi


def curved(f, c, b0, b1, power=2.0):
    """Shift a dlo/dhi function by c * t^power (t: 0 at b0 -> 1 at b1); +c bends toward the bottom-right."""
    def g(p):
        base = f(p) if callable(f) else f
        t = max(0.0, min(1.0, (p - b0) / float(b1 - b0)))
        return base + round(c * t ** power)
    return g


def hamon_wave(a=HL, b=LT, period=3, red=None):
    """Katana temper line on the lit edge: alternating highlight values (or a coloured wave)."""
    def fn(x, y, i):
        if i == HL:
            k = (x + y) % period
            if red is not None:
                return red if k == 0 else a
            return a if k else b
        return i
    return fn


def diamond_wrap(color, period=4):
    """Tsuka-ito: diamonds of same-ray skin showing through the handle wrap."""
    def fn(x, y, i):
        if i in (OD, OL):
            return i
        p2 = (x - y + 15)
        return color if p2 % period in (0, period - 1) else i
    return fn


def katana_grid(handle=(1, 4), hdh=2, blade=(6, 14.5), lo=-1, hi=2, tipn=2.0, tsuba=(4.5, 5.0, -3, 4), habaki=(5.5, 5.5),
                kashira=(0, 0.5), curve=0, blade_key='b'):
    g = Grid()
    g.band(kashira[0], kashira[1], -1, hdh, 'k')
    g.band(handle[0], handle[1], -1, hdh, 'w')
    flo, fhi = tip(lo, hi, blade[1], tipn, 'lo')
    if curve:
        flo, fhi = curved(flo, curve, blade[0], blade[1]), curved(fhi, curve, blade[0], blade[1])
    g.band(blade[0], blade[1], flo, fhi, blade_key)
    if tsuba:
        g.band(tsuba[0], tsuba[1], tsuba[2], tsuba[3], 't')
    if habaki:
        g.band(habaki[0], habaki[1], lo, hi, 'h')
    return g


WRAP_BLACK = ['#08080d', '#181822', '#22222e', '#2e2e40', '#3e3e56', '#565670']
WRAP_BLUE = ['#060a1c', '#101a3c', '#18275a', '#22377c', '#2f4c9e', '#4a6cc0']
WRAP_PURPLE = ['#10061a', '#2a0f3a', '#3e1a55', '#56287a', '#7a3ea0', '#a05cc8']
WRAP_RED = ['#1a0404', '#3e0a0a', '#5e1010', '#861a1a', '#aa2a26', '#d0483e']
WRAP_WHITE = ['#2c2e36', '#70747e', '#b4b8c0', '#dadde2', '#f0f2f4', '#ffffff']
WRAP_MAROON = ['#140406', '#300a10', '#4a1018', '#661824', '#842634', '#a43a48']
WRAP_TEAL = ['#041214', '#0a2a2e', '#124045', '#1c5a60', '#2a7a80', '#46a0a4']


def katana_mats(blade=STEEL, wrap=WRAP_BLACK, diamonds='#d8d0bc', tsuba=DARK_IRON, habaki=BRASS, kashira=DARK_IRON,
                hamon=None):
    return {
        'k': M(kashira),
        'w': M(wrap, pattern=diamond_wrap(diamonds) if diamonds else None),
        'b': M(blade, pattern=hamon or hamon_wave()),
        't': M(tsuba),
        'h': M(habaki),
    }


# ============================================================================================ katanas
@item('katana')
def _katana():
    g = katana_grid()
    return render(g, katana_mats())


@item('fine_katana')
def _fine_katana():
    g = katana_grid(tsuba=(4.5, 5.0, -4, 5))
    g.band(4.5, 5.0, -2, 3, 'T')  # inner ring of the ornate guard
    m = katana_mats(blade=BRIGHT_STEEL, wrap=WRAP_BLUE, diamonds='#e8e4d8', tsuba=GOLD, habaki=GOLD, kashira=GOLD)
    m['T'] = Force(m['t'], DK)
    return render(g, m)


@item('enma')
def _enma():
    g = katana_grid(tsuba=(4.5, 5.0, -4, 5), blade=(6, 15))
    g.put(3, 8, 't')
    g.put(7, 12, 't')
    purple_hamon = hamon_wave(red='#e0c8ff')
    m = katana_mats(blade=['#120c1a', '#3a3450', '#7a7a98', '#b0b0c8', '#d8d6ea', '#ffffff'], wrap=WRAP_PURPLE,
                    diamonds='#e6d2ff', tsuba=GOLD, habaki=GOLD, kashira=GOLD, hamon=purple_hamon)
    return render(g, m)


@item('ame_no_habakiri')
def _ame():
    g = katana_grid(tsuba=(4.5, 5.0, -4, 5))
    g.band(4.5, 5.0, -4, -4, 'R')
    g.band(4.5, 5.0, 5, 5, 'R')
    m = katana_mats(blade=BRIGHT_STEEL, wrap=WRAP_BLACK, diamonds='#c9a648', tsuba=DARK_IRON, habaki=GOLD, kashira=DARK_IRON)
    m['R'] = M(GOLD)
    return render(g, m)


@item('shusui')
def _shusui():
    g = katana_grid(tsuba=(4.5, 5.0, -4, 5), blade=(6, 15))
    # four-lobed guard: notch the guard to suggest the cross/flower shape
    for x, y in ((4, 9), (6, 11)):
        g.put(x, y, 'T')
    edge = lambda x, y, i: '#9a8fbf' if i == HL and (x + y) % 4 == 0 else ('#6c6488' if i == HL else i)
    m = katana_mats(blade=BLACK_BLADE, wrap=WRAP_MAROON, diamonds='#d8b0a0', tsuba=GOLD, habaki=GOLD, kashira=GOLD, hamon=edge)
    m['T'] = Force(m['t'], DK)
    return render(g, m)


@item('wado_ichimonji')
def _wado():
    g = katana_grid(tsuba=(4.5, 5.0, -3, 4))
    m = katana_mats(blade=BRIGHT_STEEL, wrap=WRAP_WHITE, diamonds='#3a3a48', tsuba=GOLD, habaki=GOLD, kashira=GOLD)
    return render(g, m)


@item('sandai_kitetsu')
def _kitetsu():
    g = katana_grid(tsuba=(4.5, 5.0, -3, 4))

    def red_wave(x, y, i):
        if i == HL:
            return '#e8333a' if (x + y + (x // 2)) % 3 == 0 else '#f4eeee'
        if i == MD:
            return '#c4565a' if (x * 3 + y) % 5 == 0 else i
        return i
    m = katana_mats(blade=STEEL, wrap=WRAP_RED, diamonds='#1a0a0a', tsuba=DARK_IRON, habaki=BRASS, kashira=DARK_IRON, hamon=red_wave)
    return render(g, m)


@item('yubashiri')
def _yubashiri():
    g = katana_grid(hi=1, hdh=1, tsuba=(4.5, 5.0, -3, 3), blade=(6, 15), tipn=1.5)
    m = katana_mats(blade=['#1a1e26', '#48525e', '#9aa8b8', '#cfd8e2', '#eef4fa', '#ffffff'], wrap=['#1e2a3a', '#3a5068', '#5a7898', '#7c9cbc', '#a4c2dc', '#d4e6f4'],
                    diamonds='#f4f8ff', tsuba=GOLD, habaki=GOLD, kashira=['#1e2a3a', '#3a5068', '#5a7898', '#7c9cbc', '#a4c2dc', '#d4e6f4'],
                    hamon=lambda x, y, i: i)
    return render(g, m)


@item('kurenai_shigure')
def _kurenai():
    g = katana_grid(tsuba=(4.5, 5.0, -4, 5))
    crimson_steel = ['#1c0c10', '#4a343a', '#857a80', '#b8b0b4', '#e0d8da', '#ffffff']

    def rain(x, y, i):
        # crimson temper line on the edge with scattered "rain drop" flecks
        if i == HL:
            return '#e8384e' if (x + 2 * y) % 4 == 0 else '#fbe6ea'
        if i == DK and (x * 7 + y * 3) % 5 == 0:
            return '#a01e30'
        return i
    m = katana_mats(blade=crimson_steel, wrap=WRAP_BLACK, diamonds='#c8303c', tsuba=['#1a0408', '#40101a', '#6a1a28', '#962838', '#bc3c4a', '#e0646e'],
                    habaki=GOLD, kashira=['#1a0408', '#40101a', '#6a1a28', '#962838', '#bc3c4a', '#e0646e'], hamon=rain)
    return render(g, m)


@item('nodachi')
def _nodachi():
    g = katana_grid(handle=(0.5, 3.0), kashira=(0, 0), tsuba=(3.5, 4.0, -3, 4), habaki=(4.5, 4.5), blade=(5, 15), tipn=1.5, curve=0)
    return render(g, katana_mats(blade=STEEL, wrap=WRAP_TEAL, diamonds='#d8d0bc', tsuba=DARK_IRON, habaki=BRASS))


@item('yoru')
def _yoru():
    g = Grid()
    g.band(0, 0.5, -1, 2, 'k')                     # gold pommel
    g.band(1, 3.5, -1, 2, 'w')                     # black grip
    flo, fhi = tip(-2, 3, 15, 2.5, 'both')
    g.band(5, 15, flo, fhi, 'b')                   # broad black blade
    g.band(4.0, 4.5, -6, 7, 'x')                   # long cross guard
    g.band(4.0, 4.5, -7, -6, 'X')
    g.band(4.0, 4.5, 7, 8, 'X')
    g.axis_disc(4.25, 0.5, 1.1, 'j')              # jewel at the crossing
    g.band(5.0, 5.5, -1, 2, 'x')

    def edge(x, y, i):
        p, d = g.pd(x, y)
        if i == HL or (i == LT):
            return '#b8b4cc' if (x + y) % 3 else '#e8e6f2'
        if d == 0 and p > 6:
            return '#5a5470'  # fuller groove
        return i
    m = {'k': M(GOLD), 'w': M(WRAP_BLACK, pattern=diamond_wrap('#a08850')), 'b': M(BLACK_BLADE, pattern=edge), 'x': M(GOLD),
         'X': M(GOLD), 'j': M(['#06200e', '#0e4a20', '#1a7a34', '#2eaa4c', '#5ad078', '#c0ffd0'], shading='sphere')}
    m['X'] = m['x']
    return render(g, m)


# ============================================================================================ cutlasses
def cutlass_grid(guard_dhi=5, blade_hi=3, curve=2):
    g = Grid()
    g.band(0, 0.5, -1, 1, 'k')                    # pommel
    g.band(1, 3.5, -1, 1, 'w')                    # grip
    lo = lambda p: -1
    hi = lambda p: 2 if p < 8 else blade_hi
    flo, fhi = tip(-1, blade_hi, 14.5, 2.5, 'hi')
    blo = curved(lambda p: -1 if p < 12.5 else flo(p), curve, 5, 14.5)
    bhi = curved(lambda p: min(hi(p), fhi(p)), curve, 5, 14.5)
    g.band(5, 14.5, blo, bhi, 'b')
    g.band(4.0, 4.5, -3, guard_dhi, 'g')          # guard plate
    # knuckle bow sweeping from the guard back to the pommel on the +d side
    g.band(0.5, 4.0, 2, 3, 'g')
    g.band(0.0, 0.5, 1, 3, 'g')
    return g


@item('cutlass')
def _cutlass():
    g = cutlass_grid()
    return render(g, {'k': M(BRASS), 'w': M(LEATHER), 'b': M(IRON, pattern=hamon_wave(HL, LT, 4)), 'g': M(BRASS)})


@item('fine_cutlass')
def _fine_cutlass():
    g = cutlass_grid()
    g.put(4, 11, 'r')
    return render(g, {'k': M(GOLD), 'w': M(RED_LEATHER), 'b': M(BRIGHT_STEEL, pattern=hamon_wave(HL, LT, 4)), 'g': M(GOLD),
                      'r': M(['#200004', '#500010', '#8a0a20', '#d01838', '#ff5a70', '#ffc0c8'], shading='flat')})


@item('abyssal_fang')
def _abyssal_fang():
    g = cutlass_grid(blade_hi=3, curve=2)
    # serrated spine: notch every other step on the lit (-d) side
    for y in range(16):
        for x in range(16):
            p, d = g.pd(x, y)
            if g.at(x, y) == 'b' and 7 <= p <= 12.5 and d <= -1 and int(p * 2) % 3 == 0:
                g.put(x, y, None)
    abyss = ['#02050c', '#0a1630', '#132a52', '#1c3f76', '#2a5a9a', '#4a86c4']

    def glow(x, y, i):
        p, d = g.pd(x, y)
        if i == DK or i == OD:
            return '#4ef0ff' if (x + y) % 2 else '#9afcff'
        return i
    return render(g, {'k': M(BONE), 'w': M(WRAP_TEAL), 'b': M(abyss, pattern=glow), 'g': M(['#04080e', '#121c28', '#1e2c3c', '#2c4054', '#405a72', '#6a8aa6'])})


# ============================================================================================ other blades
@item('rapier')
def _rapier():
    g = Grid()
    g.axis_disc(0.6, 0.5, 1.25, 'k')               # ball pommel
    g.band(1.5, 3.5, -1, 1, 'w')
    g.band(4.0, 4.5, -4, 4, 'g')                   # quillons
    g.band(1.0, 4.0, 3, 3, 'g')                    # swept knuckle guard
    g.band(1.0, 1.0, 1, 3, 'g')
    g.band(5.0, 15, -1, 1, 'b')
    g.band(4.5, 4.5, -1, 1, 'g')
    return render(g, {'k': M(GOLD, shading='sphere'), 'w': M(['#100808', '#2a1410', '#3e2018', '#583020', '#74462e', '#966040']),
                      'g': M(GOLD), 'b': M(BRIGHT_STEEL)})


@item('knife')
def _knife():
    g = Grid()
    g.band(2.0, 2.5, -1, 2, 'k')
    g.band(3.0, 6.5, -1, 2, 'w')
    g.band(7.0, 7.5, -2, 3, 'g')
    flo, fhi = tip(-1, 3, 13, 3.0, 'lo')
    g.band(8.0, 13, flo, fhi, 'b')
    rivets = lambda x, y, i: '#d8c8a0' if i == MD and (x - y) % 4 == 1 else i
    return render(g, {'k': M(DARK_IRON), 'w': M(WOOD, pattern=rivets), 'g': M(IRON), 'b': M(STEEL)})


@item('throwing_knife')
def _kunai():
    g = Grid()
    g.band(2.5, 6.0, -1, 1, 'w')
    g.band(6.5, 9.5, lambda p: -1 - int((p - 6.5) * 0.8), lambda p: 1 + int((p - 6.5) * 0.8), 'b')
    flo, fhi = tip(-3, 3, 13.5, 4.0, 'both')
    g.band(9.5, 13.5, flo, fhi, 'b')
    g.ascii([
        '.rr..',
        'r..r.',
        'r..rw',
        '.rr..',
    ], ox=0, oy=11, keep='.')
    g.put(1, 12, 'o'); g.put(2, 12, 'o'); g.put(1, 13, 'o'); g.put(2, 13, 'o')

    def cloth(x, y, i):
        if i in (OD, OL):
            return i
        return '#d8d0bc' if (x - y) % 4 in (0, 1) else i
    return render(g, {'r': M(DARK_IRON), 'o': HOLE, 'w': M(['#1a0606', '#3a0e0e', '#5a1616', '#7c2020', '#9a3030', '#ba4a40'], pattern=cloth),
                      'b': M(DARK_IRON)})


@item('dual_blades')
def _dual():
    a = Grid()
    a.band(1.0, 1.5, -1, 1, 'k')
    a.band(2.0, 4.0, -1, 1, 'w')
    a.band(4.5, 5.0, -3, 3, 'g')
    flo, fhi = tip(-1, 2, 13, 2, 'lo')
    a.band(5.5, 13, flo, fhi, 'b')
    b = a.flip_h()
    for y in range(16):
        for x in range(16):
            k = b.at(x, y)
            if k:
                b.put(x, y, k.upper())
    b.overlay(a.shift(0, 0))
    m = {'k': M(BRASS), 'w': M(LEATHER), 'g': M(BRASS), 'b': M(STEEL, pattern=hamon_wave(HL, LT, 4))}
    m2 = {'K': M(BRASS), 'W': M(['#100a14', '#26182e', '#382244', '#4c3060', '#64447c', '#80609a']), 'G': M(BRASS), 'B': M(STEEL)}
    m.update(m2)
    return render(b, m)


# ============================================================================================ polearms
def shaft(g, p0, p1, key='s', lo=-1, hi=1):
    g.band(p0, p1, lo, hi, key)


@item('spear')
def _spear():
    g = Grid()
    shaft(g, 0, 10.0)
    g.band(0, 0.5, -1, 1, 'c')
    g.band(10.0, 10.5, -1, 1, 'c')                # socket ring
    g.band(10.5, 11.0, -2, 2, 'c')
    flo, fhi = tip(-3, 3, 15, 2.5, 'both')
    g.band(11.5, 15, lambda p: max(flo(p), -3 if p > 12 else -2), lambda p: min(fhi(p), 3 if p > 12 else 2), 'h')
    mid = lambda x, y, i: '#4a5058' if g.pd(x, y)[1] == 0 and i not in (OD, OL) and g.pd(x, y)[0] > 12 else i
    return render(g, {'s': M(WOOD), 'c': M(DARK_IRON), 'h': M(IRON, pattern=mid)})


@item('steel_spear')
def _steel_spear():
    g = Grid()
    shaft(g, 0, 9.5)
    for p in (2.5, 5.0, 7.5):
        g.band(p, p, -1, 1, 'c')
    g.band(0, 0.5, -1, 1, 'c')
    g.band(9.5, 10.0, -2, 2, 'c')
    flo, fhi = tip(-3, 3, 15, 3.0, 'both')
    g.band(10.5, 15, flo, fhi, 'h')
    # red tassel hanging off the socket
    for x, y in ((6, 10), (7, 11), (6, 11), (7, 12), (8, 12), (6, 12)):
        if g.at(x, y) is None:
            g.put(x, y, 'r')
    mid = lambda x, y, i: '#5a6470' if g.pd(x, y)[1] == 0 and i not in (OD, OL) else i
    return render(g, {'s': M(DARK_WOOD), 'c': M(GOLD), 'h': M(BRIGHT_STEEL, pattern=mid), 'r': M(CLOTH_RED, outline=False)})


def naginata_grid():
    g = Grid()
    shaft(g, 0, 8.0)
    g.band(0, 0.5, -1, 1, 'c')
    g.ascii([
        '..............bb',
        '.............bbb',
        '............bbbb',
        '...........bbbb.',
        '..........bbbbb.',
        '.........bbbbb..',
        '........hbbbb...',
        '.......thbb.....',
        '......ttth......',
        '.......tt.......',
    ], keep='.')
    return g


@item('naginata')
def _naginata():
    g = naginata_grid()
    return render(g, {'s': M(['#140808', '#2e1210', '#461c16', '#64281e', '#843a2a', '#a6523c']), 'c': M(BRASS), 't': M(DARK_IRON),
                      'h': M(BRASS), 'b': M(STEEL, pattern=hamon_wave())})


@item('murakumogiri')
def _murakumogiri():
    g = naginata_grid()
    for p in (2.0, 4.5, 7.0):
        g.band(p, p, -1, 1, 'c')
    g.ascii(['...........b....', '..........b.....'], oy=2)  # broader blade back
    m = {'s': M(WRAP_BLACK), 'c': M(GOLD), 't': M(GOLD), 'h': M(GOLD), 'b': M(BRIGHT_STEEL, pattern=hamon_wave(HL, LT, 2))}
    return render(g, m)


@item('war_trident')
def _trident():
    g = Grid()
    shaft(g, 0, 9.0)
    g.band(0, 0.5, -1, 1, 'c')
    g.band(9.0, 10.0, -5, 5, 'h')                 # crossbar
    g.band(10.0, 15, -1, 1, 'h')                  # centre prong
    g.band(10.0, 13.5, -5, -4, 'h')               # side prongs
    g.band(10.0, 13.5, 4, 5, 'h')
    g.band(13.5, 14.0, -6, -4, 'h')               # barbs
    g.band(13.5, 14.0, 4, 6, 'h')
    g.band(14.5, 15, -1, 1, 'h')
    return render(g, {'s': M(DARK_WOOD), 'c': M(SEASTONE), 'h': M(['#0a1a1c', '#24423f', '#3f6e66', '#5f9a8c', '#8cc4b2', '#d0f0e2'])})


@item('bo_staff')
def _bo():
    g = Grid()
    shaft(g, 0, 15)
    g.band(0, 1.0, -1, 1, 'c')
    g.band(14.0, 15, -1, 1, 'c')
    g.band(6.5, 8.5, -1, 1, 'w')
    wrap = lambda x, y, i: '#e8dcc0' if i not in (OD, OL) and (x - y) % 2 == 0 else i
    return render(g, {'s': M(['#1c0a04', '#3e180a', '#5e2610', '#82381a', '#a44e28', '#c87040']), 'c': M(BRASS),
                      'w': M(['#141414', '#2a2a2a', '#3c3c3c', '#525252', '#6a6a6a', '#8a8a8a'], pattern=wrap)})


def club_grid():
    g = Grid()
    g.axis_ellipse(0.6, 0.5, 0.9, 2.2, 'k')        # pommel ring
    g.band(1.0, 4.0, -1, 1, 'w')
    g.band(4.0, 4.5, -2, 2, 'c')
    g.band(5.0, 14.0, lambda p: -2 - int((p - 5) / 3.2), lambda p: 2 + int((p - 5) / 3.2), 'b')
    g.band(14.5, 14.5, -3, 3, 'b')
    for y in range(16):
        for x in range(16):
            p, d = g.pd(x, y)
            if g.at(x, y) != 'b':
                continue
            # sparse staggered studs: two rows on the faces, offset rows on the wider head
            if p in (6, 9, 12) and d in (-2, 2):
                g.put(x, y, 'S')
            elif p in (7.5, 10.5, 13.5) and d in (-3, 3, 0) and (d != 0 or p == 7.5):
                g.put(x, y, 'S')
    return g


def facets(x, y, i):
    """Octagonal club: a lit facet line and a shadowed facet line running along the length."""
    if i in (OD, OL):
        return i
    d = x + y - 15
    if d == -1:
        return LT
    if d == 2:
        return DK
    return i


@item('kanabo')
def _kanabo():
    g = club_grid()
    m = {'k': M(DARK_IRON), 'w': M(['#1a0e06', '#3a2010', '#4e2c18', '#6a3e22', '#8a5430', '#aa7044'], pattern=diamond_wrap('#c8b48a')),
         'c': M(DARK_IRON), 'b': M(DARK_WOOD, pattern=facets), 'S': Force(M(IRON), LT)}
    return render(g, m)


@item('iron_kanabo')
def _iron_kanabo():
    g = club_grid()
    g.band(5.0, 5.5, -2, 2, 'c')
    m = {'k': M(GOLD), 'w': M(WRAP_RED, pattern=diamond_wrap('#e0c070')), 'c': M(GOLD), 'b': M(DARK_IRON, pattern=facets),
         'S': Force(M(BRIGHT_STEEL), LT)}
    return render(g, m)


@item('war_hammer')
def _hammer():
    g = Grid()
    shaft(g, 0, 11.0)
    g.band(0, 0.5, -1, 1, 'c')
    g.band(3.0, 5.0, -1, 1, 'w')
    g.band(10.5, 14.0, -5, 5, 'h')                # head block
    g.band(10.5, 14.0, -6, -6, 'h')
    g.band(11.0, 13.5, 6, 6, 'h')
    g.band(12.0, 12.5, -6, 6, 'r')                # banding
    g.band(14.5, 14.5, -2, 2, 'h')                # top spike
    return render(g, {'s': M(WOOD), 'c': M(DARK_IRON), 'w': M(LEATHER, pattern=lambda x, y, i: LT if i == MD and (x - y) % 2 else i),
                      'h': M(IRON), 'r': M(DARK_IRON)})


@item('tonfa')
def _tonfa():
    g = Grid()
    g.band(1.5, 14.5, -1, 2, 's')                 # main baton
    g.band(1.5, 2.0, -1, 2, 'c')
    g.band(14.0, 14.5, -1, 2, 'c')
    g.band(4.0, 5.0, 3, 7, 's')                   # side grip
    g.band(4.0, 5.0, 7, 8, 'k')                   # grip knob
    return render(g, {'s': M(DARK_WOOD), 'c': M(DARK_IRON), 'k': M(DARK_IRON)})


@item('seastone_jitte')
def _jitte():
    g = art([
        '..............rr',
        '.............rrr',
        '............rrr.',
        '...........rrr..',
        '..........rrr...',
        '.........rrr....',
        '........rrr..k..',
        '.......rrr..kk..',
        '......rrr..kk...',
        '.....rrr..kk....',
        '....crrkkkk.....',
        '...wwcc.........',
        '..www...........',
        '.www............',
        'oww.............',
        'oo..............',
    ])
    g.put(0, 13, 't'); g.put(1, 12, None)
    return render(g, {'o': M(SEASTONE), 'w': M(WRAP_RED, pattern=diamond_wrap('#1a0606')), 'c': M(GOLD), 'r': M(SEASTONE),
                      'k': M(SEASTONE), 't': M(CLOTH_RED, outline=False)})


@item('weather_staff')
def _clima():
    g = Grid()
    seg_mat = ['#071436', '#10285e', '#1a4a9a', '#2a6cd0', '#56a0f0', '#b4dcff']
    for a, b in ((0.5, 4.5), (5.5, 9.5), (10.5, 14.5)):
        g.band(a, b, -1, 1, 's')
    for p in (4.5, 5.0, 9.5, 10.0):
        g.band(p, p, -2, 2, 'j')
    g.axis_disc(15.0, 0, 1.3, 'o')
    g.axis_disc(0.0, 0, 1.3, 'o')
    return render(g, {'s': M(seg_mat, pattern=lambda x, y, i: HL if i == LT and (x + y) % 3 == 0 else i), 'j': M(WRAP_WHITE),
                      'o': M(['#2a1c04', '#6a4a10', '#b08020', '#f0c040', '#ffe080', '#fffadf'], shading='sphere')})


# ============================================================================================ hand-drawn weapons
@item('whip')
def _whip():
    g = Grid()
    g.ellipse(9.5, 7.0, 6.0, 4.6, 'l', ring=0.55)
    g.ellipse(9.5, 7.5, 3.6, 2.6, 'L', ring=0.3)
    g.seg(1.0, 14.0, 4.0, 11.0, 1.1, 'h')
    g.seg(4.0, 11.0, 5.5, 9.5, 0.7, 'h')
    g.put(0, 15, 'k')
    g.put(1, 15, 'k')
    g.put(0, 14, 'k')
    for x, y in ((14, 11), (15, 12), (15, 13), (14, 14), (13, 14)):
        g.put(x, y, 't')
    m_l = M(LEATHER, outline=False, thin_mid=LT)
    return render(g, {'l': m_l, 'L': Force(m_l, DK), 'h': M(DARK_WOOD), 'k': M(BRASS), 't': M(LEATHER, outline=False, shading='flat')})


@item('brass_knuckles')
def _knuckles():
    g = art([
        '................',
        '................',
        '................',
        '..bb.bb.bb.bb...',
        '.bbbbbbbbbbbbb..',
        '.b..b..b..b..b..',
        '.b..b..b..b..b..',
        '.bbbbbbbbbbbbb..',
        '..bbbbbbbbbbbb..',
        '...bb......bb...',
        '...bb......bb...',
        '....bbbbbbbb....',
        '................',
        '................',
        '................',
        '................',
    ])
    for x, y in [(2, 5), (3, 5), (2, 6), (3, 6), (5, 5), (6, 5), (5, 6), (6, 6), (8, 5), (9, 5), (8, 6), (9, 6), (11, 5), (12, 5),
                 (11, 6), (12, 6), (5, 9), (6, 9), (7, 9), (8, 9), (9, 9), (10, 9), (5, 10), (6, 10), (7, 10), (8, 10), (9, 10), (10, 10)]:
        g.put(x, y, 'o')
    g = g.shift(0, 1)
    return render(g, {'b': M(BRASS), 'o': HOLE})


@item('anchor_chain')
def _anchor():
    g = art([
        '.........rr.....',
        '........r..r....',
        '.......cr..r....',
        '......c..rr.....',
        '.....c.kkkkkkk..',
        '.....c.kkkkkkk..',
        '....c....ss.....',
        '....c....ss.....',
        '...c.....ss.....',
        '...c.f...ss...f.',
        '..c.fff..ss..fff',
        '..c..aa..ss..aa.',
        '.c....aa.ss.aa..',
        '.c.....aaaaaa...',
        'c.......aaaa....',
        'c...............',
    ])
    for x, y in [(8, 1), (11, 1), (8, 2), (11, 2), (9, 0), (10, 0), (9, 3), (10, 3)]:
        g.put(x, y, 'r')
    g.put(9, 1, 'o'); g.put(10, 1, 'o'); g.put(9, 2, 'o'); g.put(10, 2, 'o')
    chain = M(IRON, outline=False, shading='flat', pattern=lambda x, y, i: LT if (x + y) % 2 else OL)
    return render(g, {'r': M(DARK_IRON), 'o': HOLE, 'c': chain, 'k': M(DARK_WOOD), 's': M(DARK_IRON), 'a': M(DARK_IRON),
                      'f': M(DARK_IRON)})


@item('tidebreaker')
def _tidebreaker():
    g = art([
        '.........rr.....',
        '........r..r....',
        '.......cr..r....',
        '......c..rr.....',
        '.....c.kkkkkkkk.',
        '.....c.kkkkkkkk.',
        '....c...ssss....',
        '....c...sgss....',
        '...c....ssss....',
        '...cff..ssss..ff',
        '..cfff..ssss.fff',
        '..c.aaa.ssss.aaa',
        '.c...aaassssaaa.',
        '.c....aaaaaaaa..',
        'c......aaaaaa...',
        'c...............',
    ])
    for x, y in [(8, 1), (11, 1), (8, 2), (11, 2), (9, 0), (10, 0), (9, 3), (10, 3)]:
        g.put(x, y, 'r')
    g.put(9, 1, 'o'); g.put(10, 1, 'o'); g.put(9, 2, 'o'); g.put(10, 2, 'o')
    for x, y in [(8, 4), (15, 4), (8, 5), (15, 5)]:
        g.put(x, y, 'K')
    chain = M(GOLD, outline=False, shading='flat', pattern=lambda x, y, i: LT if (x + y) % 2 else OL)
    tide = ['#03141c', '#0c3442', '#16566a', '#227a90', '#3aa4b8', '#8ae0ea']
    wave = lambda x, y, i: HL if i == MD and (x + 2 * y) % 5 == 0 else i
    return render(g, {'r': M(GOLD), 'o': HOLE, 'c': chain, 'k': M(tide, pattern=wave), 'K': M(GOLD), 's': M(tide, pattern=wave),
                      'g': Force(M(GOLD), HL), 'a': M(tide, pattern=wave), 'f': M(GOLD)})


@item('flintlock')
def _flintlock():
    g = art([
        '................',
        '................',
        '................',
        '....hh..........',
        '.....hh.........',
        '....lllbbbbbbbbm',
        '...wwllbbbbbbbbm',
        '..wwwwwwwwwwwww.',
        '.wwwwwwtw.......',
        '.wwww..t.t......',
        'wwwww...t.......',
        'wwww............',
        'gggg............',
        '................',
        '................',
        '................',
    ]).shift(0, 1)
    return render(g, {'b': M(IRON), 'm': M(BRASS), 'w': M(WOOD), 'h': M(DARK_IRON), 'l': M(IRON), 't': M(BRASS, outline=False),
                      'g': M(BRASS)})


@item('musket')
def _musket():
    g = art([
        '...............m',
        '..............bm',
        '.............bbw',
        '............bbww',
        '...........bbww.',
        '..........bBww..',
        '.........bbww...',
        '........bbww....',
        '...hh..bbww.....',
        '....hhbbww......',
        '....llbwww......',
        '...wwwwwwt......',
        '..wwwwwwt.t.....',
        '.wwwwwww..t.....',
        'wwwwww..........',
        'gwww............',
    ])
    return render(g, {'b': M(IRON), 'B': M(BRASS), 'm': M(BRASS), 'w': M(WOOD), 'h': M(DARK_IRON), 'l': M(IRON),
                      't': M(BRASS, outline=False), 'g': M(DARK_IRON)})


@item('bazooka')
def _bazooka():
    g = Grid()
    g.band(1.5, 13.5, -3, 3, 't')                 # tube
    g.band(13.5, 15, -4, 4, 'm')                  # flared muzzle
    g.band(1.0, 2.0, -4, 4, 'e')                  # rear ring
    g.band(4.0, 4.5, -3, 3, 'e')
    g.band(10.0, 10.5, -3, 3, 'e')
    g.band(6.0, 7.0, 4, 8, 'h')                   # pistol grip
    g.band(8.5, 9.0, -6, -4, 's')                 # sight
    g.band(14.5, 15, -2, 2, None)                 # bore
    g.axis_ellipse(14.8, 0, 0.6, 2.1, 'o')
    olive = ['#0e1406', '#24300e', '#3a4a18', '#526626', '#6e843a', '#94aa5c']
    return render(g, {'t': M(olive), 'm': M(DARK_IRON), 'e': M(DARK_IRON), 'h': M(DARK_WOOD), 's': M(DARK_IRON),
                      'o': Fixed('#0a0a0a')})


@item('hand_cannon')
def _hand_cannon():
    g = art([
        '................',
        '................',
        '...F............',
        '...f.........rr.',
        '..cccrcccccccrrr',
        '.ccccrccccccccro',
        'kccccrccccccccro',
        'kccccrccccccccro',
        '.ccccrccccccccrr',
        '..cccrcccccccrrr',
        '....hh.......rr.',
        '....hhh.........',
        '.....hhh........',
        '......hhh.......',
        '.......hh.......',
        '................',
    ])
    return render(g, {'c': M(BRONZE), 'r': M(BRASS), 'k': M(BRONZE), 'o': Fixed('#140c06'), 'h': M(DARK_WOOD),
                      'f': M(ROPE, outline=False), 'F': Fixed('#ffb030')})


@item('slingshot')
def _slingshot():
    g = Grid()
    g.seg(1.0, 14.5, 6.5, 9.0, 0.95, 'w')         # handle
    g.seg(6.5, 9.0, 7.6, 4.0, 0.95, 'w')          # prong A
    g.seg(7.6, 4.0, 9.0, 1.5, 0.75, 'w')
    g.seg(6.5, 9.0, 11.0, 7.8, 0.95, 'w')         # prong B
    g.seg(11.0, 7.8, 13.6, 6.5, 0.75, 'w')
    g.line(9, 2, 10, 4, 'r')                      # rubber band with pouch
    g.line(13, 6, 11, 5, 'r')
    g.rect(10, 4, 11, 5, 'p')
    g.put(0, 15, 'k'); g.put(1, 14, 'k'); g.put(0, 14, 'k')
    return render(g, {'w': M(WOOD), 'r': M(['#1c0606', '#3a0c0c', '#5a1414', '#7a2020', '#9a3030', '#c05050'], outline=False, shading='flat'),
                      'p': M(LEATHER), 'k': M(LEATHER)})


@item('war_bow')
def _war_bow():
    g = Grid()
    cx, cy, r = 15.0, 15.0, 13.0
    for y in range(16):
        for x in range(16):
            dist = math.hypot(x + 0.5 - cx, y + 0.5 - cy)
            ang = math.degrees(math.atan2(y + 0.5 - cy, x + 0.5 - cx))
            if r - 1.4 <= dist <= r + 0.6 and -180 <= ang <= -90:
                g.put(x, y, 'l')
    g.line(1, 14, 14, 1, 's')                      # string
    for x, y in ((5, 5), (4, 6), (5, 4), (6, 4), (4, 5)):
        if g.at(x, y) == 'l':
            g.put(x, y, 'g')
    for x, y in ((1, 14), (1, 13), (2, 14), (14, 1), (13, 1), (14, 2)):
        g.put(x, y, 't')
    g.put(0, 13, 't'); g.put(13, 0, 't')
    return render(g, {'l': M(['#120604', '#2e1208', '#461c0c', '#602a14', '#7c3c20', '#9a5430']), 's': Fixed('#d8d0c0'),
                      'g': M(RED_LEATHER), 't': M(GOLD)})


@item('impact_dial')
def _dial():
    g = art([
        '................',
        '.......pp.......',
        '......pPPp......',
        '.....ssssss.....',
        '....ssssssss....',
        '...ssssssssss...',
        '..ssssssssssss..',
        '..ssssssssssss..',
        '..ssssssssssss..',
        '..ssssssssssss..',
        '...ssssssssssA..',
        '...ssssssssAAA..',
        '....ssssssAAA...',
        '.....ssssAAA....',
        '.......sss......',
        '................',
    ])
    # spiral groove
    spiral = [(7, 6), (8, 6), (9, 7), (9, 8), (8, 9), (7, 9), (6, 8), (6, 7), (5, 6), (5, 5), (6, 4), (8, 4), (9, 4), (10, 5),
              (11, 6), (11, 7), (11, 9), (10, 10), (9, 11), (8, 11), (6, 11), (5, 11), (4, 10), (3, 9), (3, 8), (3, 7)]
    for x, y in spiral:
        g.put(x, y, 'S')
    shell = ['#3a2a1c', '#7a5a3a', '#c09a6a', '#e4c79a', '#f4e2c0', '#fff8e6']
    m_s = M(shell, shading='sphere')
    return render(g, {'s': m_s, 'S': Force(m_s, OL), 'A': M(['#4a1a1a', '#8a3a3a', '#c86a6a', '#f0a0a0', '#ffd0d0', '#fff0f0'], outline=True),
                      'p': M(['#2a0a10', '#5a1a28', '#8a2a3a', '#c04050', '#e07080', '#ffb0c0']), 'P': Force(M(['#2a0a10', '#5a1a28', '#8a2a3a', '#c04050', '#e07080', '#ffb0c0']), HL)})
