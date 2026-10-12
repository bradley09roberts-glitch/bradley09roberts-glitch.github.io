"""Journey assets: the four Den Den Mushi sprites and recipes, the snail glyphs of the chat font and the nautical
background of the journey advancement tab.

Ids come from journey/JourneyRegistry.java (see source.item_ids); gen_assets merges recipes(), writes textures() and
asset_files() (the bitmap font)."""
import math

from .canvas import Canvas, C, mix
from .sprite import Grid, Fixed, Force, render, OD, OL, DK, MD, LT, HL
from .palette import *
from .items import item
from .data import NS, shapeless

# ============================================================================================ ramps
SNAIL_GREEN = ['#10260c', '#2a4c1c', '#4a7a30', '#6ea444', '#96c860', '#cfeea0']
SHELL_CREAM = ['#3a3020', '#7a6a4a', '#b8a67c', '#ddd0a8', '#f0e8cc', '#fffaea']
SNAIL_TAN = ['#2a1606', '#5a3412', '#8c5a24', '#bc8640', '#dcac64', '#f4d6a0']
SHELL_GOLD = GOLD
SNAIL_PINK = ['#3a0c1c', '#6e2440', '#a8466a', '#d06e92', '#ec9cb8', '#ffd2e2']
SHELL_PEACH = ['#4a2414', '#8a4a2e', '#c4785a', '#e6a084', '#f6c8b0', '#fff0e6']
SNAIL_BLACK = ['#050506', '#121218', '#1e1e28', '#2c2c3a', '#40405a', '#62627e']
SHELL_BLACK = ['#030304', '#0c0c10', '#16161c', '#22222c', '#34343f', '#50505e']
HANDSET = ['#0c0c10', '#2a2a32', '#4a4a54', '#6a6a76', '#8e8e9a', '#b8b8c4']
BLACK_HANDSET = ['#050508', '#141418', '#24242c', '#383842', '#50505c', '#74748a']


def spiral(g, cx, cy, turns=1.5, r0=0.5, r1=4.1, key='p'):
    """An Archimedean spiral over the pixels already holding the shell ('s')."""
    span = turns * 2 * math.pi
    for y in range(16):
        for x in range(16):
            if g.at(x, y) != 's':
                continue
            dx, dy = x + 0.5 - cx, y + 0.5 - cy
            r, th = math.hypot(dx, dy), math.atan2(dy, dx) % (2 * math.pi)
            for n in range(int(turns) + 1):
                t = th + n * 2 * math.pi
                if t <= span and abs(r - (r0 + (r1 - r0) * t / span)) < 0.5:
                    g.put(x, y, key)
                    break


def snail(body, shell, spiral_col, handset=None, baby=False, eye='#101010', lips='#d04050'):
    """A Den Den Mushi side-on: the head with its eye stalks and lips on the left, the spiral shell on its back,
    the receiver resting on top of the shell."""
    g = Grid()
    if baby:
        g.ellipse(8.6, 13.6, 6.2, 1.8, 'b')                # foot
        g.seg(4.4, 12.4, 4.2, 9.6, 1.6, 'b')              # neck
        g.disc(4.4, 9.0, 1.9, 'b')                        # head
        g.seg(3.6, 7.6, 3.0, 5.4, 0.5, 'b')               # eye stalks
        g.seg(5.0, 7.6, 5.6, 5.4, 0.5, 'b')
        g.put(3, 5, 'w')
        g.put(6, 5, 'w')
        g.put(3, 4, 'k')
        g.put(6, 4, 'k')
        g.put(2, 9, 'l')
        g.disc(10.4, 10.2, 3.6, 's')                      # shell
        spiral(g, 10.4, 10.2, turns=1.25, r0=0.3, r1=2.9)
    else:
        g.ellipse(8.0, 13.3, 7.6, 2.1, 'b')
        g.seg(3.0, 12.0, 3.0, 6.4, 2.1, 'b')
        g.disc(3.1, 5.6, 2.4, 'b')
        g.seg(2.0, 3.4, 1.2, 1.4, 0.5, 'b')
        g.seg(4.2, 3.4, 5.0, 1.4, 0.5, 'b')
        for ex in (1, 5):
            g.put(ex, 1, 'w')
            g.put(ex, 0, 'k')
        g.put(0, 6, 'l')                                  # the lips that speak the caller's words
        g.put(0, 7, 'l')
        g.put(1, 7, 'L')
        g.disc(10.4, 8.4, 4.9, 's')
        spiral(g, 10.4, 8.4)
        if handset:
            g.seg(7.0, 2.0, 14.0, 2.0, 0.6, 'h')          # the receiver across the top of the shell
            g.rect(7, 3, 8, 3, 'h')
            g.rect(13, 3, 14, 3, 'h')
            g.put(15, 4, 'c')                              # its curly cord
            g.put(15, 6, 'c')
    mats = {'b': M(body), 's': M(shell, shading='sphere'), 'p': Fixed(spiral_col), 'w': Fixed('#f4f4f0'), 'k': Fixed(eye),
            'l': Fixed(lips), 'L': Fixed(mix(lips, '#000000', 0.45)), 'h': M(handset or HANDSET), 'c': Fixed('#3a3a42')}
    return render(g, mats)


@item('den_den_mushi')
def _den_den_mushi():
    return snail(SNAIL_GREEN, SHELL_CREAM, '#7a6440', HANDSET)


@item('crew_den_den_mushi')
def _crew_den_den_mushi():
    return snail(SNAIL_TAN, SHELL_GOLD, '#7a4a08', HANDSET)


@item('baby_den_den_mushi')
def _baby_den_den_mushi():
    return snail(SNAIL_PINK, SHELL_PEACH, '#8a4430', baby=True)


@item('black_den_den_mushi')
def _black_den_den_mushi():
    return snail(SNAIL_BLACK, SHELL_BLACK, '#6a6a80', BLACK_HANDSET, eye='#e8c040', lips='#6a2a3a')


# ============================================================================================ chat glyphs
GLYPHS = [  # body, shell, spiral (one 8x8 glyph each: U+E000 personal, U+E001 crew, U+E002 baby, U+E003 black)
    ('#6ea444', '#ddd0a8', '#8a7652'),
    ('#bc8640', '#dcaa2a', '#8a5a10'),
    ('#d06e92', '#e6a084', '#a0583e'),
    ('#3a3a4a', '#1e1e26', '#8a8aa0'),
]
GLYPH = [  # b body, s shell, p spiral, e eye, h handset
    'e.e.....',
    '.b..hhhh',
    'bb.sssss',
    'bb.spps.',
    'bb.spsss',
    'bb.sssss',
    'bbbbbbbb',
    '.bbbbbb.',
]


def glyphs():
    cv = Canvas(8 * len(GLYPHS), 8)
    for gi, (body, shell, spiral) in enumerate(GLYPHS):
        cols = {'b': body, 's': shell, 'p': spiral, 'e': '#101010' if gi != 3 else '#e8c040', 'h': '#2a2a30' if gi != 3 else '#6a6a78'}
        for y, row in enumerate(GLYPH):
            for x, ch in enumerate(row):
                if ch in cols:
                    cv.set(gi * 8 + x, y, cols[ch])
    return cv


# ============================================================================================ advancement tab background
def sea_tile():
    """A 16x16 tile of open sea at night-blue: two swell lines with foam caps (seamless in x and y)."""
    deep, mid, swell, foam = C('#123a63'), C('#1a4d7e'), C('#2b6aa3'), C('#8cc0e4')
    cv = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            t = 0.5 + 0.5 * math.sin((x + y * 0.5) * math.pi / 8)
            cv.set(x, y, mix(deep, mid, t * 0.6))
    for row, phase in ((4, 0.0), (12, 8.0)):
        for x in range(16):
            yy = row + round(1.4 * math.sin((x + phase) * math.pi / 8))
            cv.set(x, yy % 16, swell)
            if (x + int(phase)) % 16 in (3, 4):
                cv.set(x, (yy - 1) % 16, foam)
            elif (x + int(phase)) % 16 in (2, 5):
                cv.set(x, (yy - 1) % 16, mix(swell, foam, 0.45))
    return cv


def textures():
    """Extra textures under assets/grandline/: {relative path: Canvas}."""
    return {
        'textures/gui/advancements/backgrounds/sea.png': sea_tile(),
        'textures/font/den_den.png': glyphs(),
    }


def asset_files():
    """Other assets this feature owns: {relative path under assets/grandline/: json}."""
    return {
        'font/den_den.json': {'providers': [{'type': 'bitmap', 'file': f'{NS}:font/den_den.png', 'ascent': 7, 'height': 8,
                                             'chars': [''.join(chr(0xE000 + i) for i in range(len(GLYPHS)))]}]},
    }


def recipes():
    return {
        'den_den_mushi': shapeless(['bone', 'kelp', 'kelp', 'copper_ingot', 'string'], 'den_den_mushi'),
        'crew_den_den_mushi': shapeless([f'{NS}:den_den_mushi', 'gold_ingot', 'copper_ingot'], 'crew_den_den_mushi'),
        'baby_den_den_mushi': shapeless(['bone', 'kelp'], 'baby_den_den_mushi', 2),
    }
