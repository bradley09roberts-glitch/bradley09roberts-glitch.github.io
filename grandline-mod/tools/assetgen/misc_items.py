"""Non-weapon item sprites: devil fruit (two tintable layers), navigation tools, ammo, food, papers."""
import math

from .sprite import Grid, Mat, Fixed, Force, render, OD, OL, DK, MD, LT, HL
from .palette import *
from .items import item, art, LAYERED, HOLE

# ============================================================================================ devil fruit
# Both layers are light greyscale: the client tints layer0 with the fruit colour and layer1 with its swirl colour.
FRUIT_BODY = ['#3a3a3a', '#5e5e5e', '#9e9e9e', '#cbcbcb', '#ebebeb', '#ffffff']
FRUIT_SWIRL = ['#505050', '#787878', '#acacac', '#d2d2d2', '#efefef', '#ffffff']
STEM = ['#383838', '#585858', '#7a7a7a', '#9a9a9a', '#b8b8b8', '#d2d2d2']


def fruit_body_grid():
    g = Grid()
    g.ellipse(8.0, 9.4, 6.9, 6.1, 'b')
    for x, y in ((7, 3), (8, 3)):
        g.put(x, y, None)       # dimple where the stem sits
    return g


SWIRLS = [
    '................',
    '................',
    '................',
    '................',
    '.....ss.........',
    '...ss..s..sss...',
    '..s..s.s.s...s..',
    '..s.s..s.s.s.s..',
    '..s..ss..s..s...',
    '...s......s.....',
    '....sss..s..sss.',
    '...s...s.s.s...s',
    '...s.s.s..s..s.s',
    '...s..s......s..',
    '....s.....sss...',
    '................',
]
STEM_ART = [
    '.........tt.....',
    '........t..t....',
    '.......tt..t....',
    '.......t........',
]


def _fruit_layers():
    body = fruit_body_grid()
    l0 = render(body, {'b': Mat(None, ramp=FRUIT_BODY, shading='sphere', center=(6.4, 7.8), radius=7.4)})
    sw = Grid()
    sw.ascii(SWIRLS, keep='.')
    # keep swirls on the body, inside its outline
    for y in range(16):
        for x in range(16):
            if not sw.at(x, y):
                continue
            inner = body.at(x, y) and all(body.at(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
            if not inner:
                sw.put(x, y, None)
    sw.ascii(STEM_ART, keep='.')
    swirl = Mat(None, ramp=FRUIT_SWIRL, outline=False, shading='sphere', center=(6.4, 7.8), radius=7.4)
    stem = Mat(None, ramp=STEM, outline=False, thin_mid=MD)
    l1 = render(sw, {'s': swirl, 't': stem})
    return [l0, l1]


LAYERED['devil_fruit'] = _fruit_layers


# ============================================================================================ navigation
@item('log_pose')
def _log_pose():
    g = Grid()
    g.ellipse(8.0, 11.3, 7.5, 3.9, 'l', ring=0.42)
    g.rect(5, 8, 10, 9, 'c')
    g.disc(8.0, 5.0, 4.7, 'g')
    g.line(9, 2, 7, 7, 'n')
    g.put(9, 2, 'N')
    g.put(9, 3, 'N')
    g.put(8, 5, 'p')
    stitch = lambda x, y, i: LT if i == MD and x % 2 == 0 else i
    m = {'l': M(LEATHER, pattern=stitch), 'c': M(BRASS), 'g': M(GLASS, shading='sphere'), 'n': Fixed('#3a1a1a'),
         'N': Fixed('#d83030'), 'p': Fixed('#f0c860')}
    return render(g, m)


@item('eternal_pose')
def _eternal_pose():
    g = art([
        '.....kkkkk......',
        '...wwwwwwwwww...',
        '...WWWWWWWWWW...',
        '...p..ggg...p...',
        '...p.ggggg..p...',
        '....pgggggg.p...',
        '....pgggggg.p...',
        '....pgggggg.p...',
        '....pgggggg.p...',
        '...p.ggggg..p...',
        '...p..ggg...p...',
        '..wwwwwwwwwwww..',
        '..wwwwwwwwwwww..',
        '..WWWLLLLLWWWW..',
        '.....LLLLL......',
        '................',
    ])
    # hourglass pillars: pinch the right pillar inward like the left one
    for y in (5, 6, 7, 8):
        g.put(12, y, None)
        g.put(11, y, 'p')
    g.line(9, 4, 7, 8, 'n')
    g.put(9, 4, 'N')
    g.put(8, 6, 'P')
    wood = M(WOOD)
    m = {'k': M(BRASS), 'w': wood, 'W': Force(wood, DK), 'p': M(DARK_WOOD), 'g': M(GLASS, shading='sphere'),
         'n': Fixed('#3a1a1a'), 'N': Fixed('#d83030'), 'P': Fixed('#f0c860'), 'L': M(PAPER, shading='flat')}
    return render(g, m)


@item('vivre_card')
def _vivre():
    g = art([
        '................',
        '................',
        '....pp.ppp.pp...',
        '...pppppppppppp.',
        '..ppppppppppppp.',
        '..pppppppppppp..',
        '..ppppppppppppp.',
        '..ppppppppppppp.',
        '..pppppppppppp..',
        '..ppppppppppppp.',
        '..ppppppppppppp.',
        '..pppppppppppp..',
        '..ppppppppppppp.',
        '...pppppppppppp.',
        '....pp.pppp.pp..',
        '................',
    ])
    fold = lambda x, y, i: DK if i == MD and x == 8 else i
    return render(g, {'p': M(WHITE_PAPER, pattern=fold)})


@item('sea_chart')
def _sea_chart():
    g = art([
        '................',
        '................',
        '.rr..........rr.',
        'rrrmmmmmmmmmmrrr',
        'rrrmsssssssssrrr',
        'rrrmssiisssssrrr',
        'rrrmsiiiisdssrrr',
        'rrrmssiisssdsrrr',
        'rrrmsssssssxsrrr',
        'rrrmssdssiiisrrr',
        'rrrmsssdsiiisrrr',
        'rrrmsssssssssrrr',
        'rrrmmmmmmmmmmrrr',
        '.rr..........rr.',
        '................',
        '................',
    ])
    m = {'r': M(['#2e2414', '#5e4a2a', '#9a8458', '#bba878', '#d8c89c', '#efe4c4']), 'm': M(PAPER, outline=False, shading='flat'),
         's': Fixed('#8fb8c0'), 'i': Fixed('#7a9a48'), 'd': Fixed('#b03028'), 'x': Fixed('#c8201c')}
    return render(g, m)


@item('seastone_cuffs')
def _cuffs():
    ring = [
        '..ccc..',
        '.ccccc.',
        'cc...cc',
        'cc...cc',
        'cc...cc',
        '.ccccc.',
        '..ccc..',
    ]
    g = Grid()
    g.ascii(ring, ox=8, oy=1, keep='.')
    g.ascii(ring, ox=1, oy=8, keep='.')
    for ox, oy in ((8, 1), (1, 8)):
        for y in range(2, 5):
            for x in range(2, 5):
                g.put(ox + x, oy + y, 'o')
    g.put(7, 7, 'l')
    g.put(8, 8, 'L')
    g.put(6, 8, 'L')
    g.put(8, 6, 'L')
    return render(g, {'c': M(SEASTONE), 'o': HOLE, 'l': M(IRON, outline=False, shading='flat'), 'L': Fixed('#3a3e44')})


@item('style_scroll')
def _style_scroll():
    g = Grid()
    g.band(2.5, 12.0, -2, 2, 'p')
    g.band(1.5, 2.0, -1, 1, 'k')
    g.band(12.5, 13.5, -1, 1, 'k')
    g.axis_ellipse(12.0, 0, 0.6, 2.3, 'e')
    g.band(7.0, 7.5, -2, 2, 'c')
    for x, y in ((8, 10), (9, 11), (8, 11), (9, 12), (10, 12)):
        g.put(x, y, 't')
    lines = lambda x, y, i: DK if i == MD and (x - y) % 5 == 0 else i
    paper = M(PAPER, pattern=lines)
    return render(g, {'p': paper, 'k': M(['#100404', '#2a0808', '#460e0e', '#6a1616', '#8e2420', '#b23a30']), 'e': Force(paper, LT),
                      'c': M(CLOTH_RED), 't': M(CLOTH_RED, outline=False)})


# A boss's Haki notes (boss fights): the same scroll in black lacquer and violet.
HAKI_PAPER = ['#1e1428', '#3a2a4a', '#6a5a7a', '#8a7a9a', '#b0a4c0', '#d8d0e4']
HAKI_LACQUER = ['#050508', '#101018', '#1c1c28', '#2c2c3c', '#40405a', '#5a5a7a']
HAKI_VIOLET = ['#14061e', '#2a0c3e', '#4a1a6a', '#6a2a9a', '#9a4ac8', '#c88af0']


@item('haki_scroll')
def _haki_scroll():
    g = Grid()
    g.band(2.5, 12.0, -2, 2, 'p')
    g.band(1.5, 2.0, -1, 1, 'k')
    g.band(12.5, 13.5, -1, 1, 'k')
    g.axis_ellipse(12.0, 0, 0.6, 2.3, 'e')
    g.band(7.0, 7.5, -2, 2, 'c')
    for x, y in ((8, 10), (9, 11), (8, 11), (9, 12), (10, 12)):
        g.put(x, y, 't')
    lines = lambda x, y, i: DK if i == MD and (x - y) % 4 == 0 else i
    paper = M(HAKI_PAPER, pattern=lines)
    return render(g, {'p': paper, 'k': M(HAKI_LACQUER), 'e': Force(paper, LT), 'c': M(HAKI_VIOLET), 't': M(HAKI_VIOLET, outline=False)})


# ============================================================================================ ammo & materials
@item('bullet')
def _bullet():
    g = art([
        '................',
        '................',
        '....ll..........',
        '...llll.........',
        '...llll...ll....',
        '...llll..llll...',
        '...bbbb..llll...',
        '...bbbb..llll...',
        '...bbbb..bbbb...',
        '...bbbb..bbbb...',
        '...bbbb..bbbb...',
        '...bbbb..bbbb...',
        '..RRRRRR.bbbb...',
        '.........bbbb...',
        '........RRRRRR..',
        '................',
    ])
    lead = ['#141618', '#34383e', '#54595f', '#767c84', '#9aa0a8', '#c8ced4']
    return render(g, {'l': M(lead), 'b': M(BRASS), 'R': M(BRONZE)})


@item('cannonball')
def _cannonball():
    g = Grid()
    g.disc(8.0, 8.5, 6.4, 'b')
    g.put(5, 5, 's')
    g.put(6, 4, 's')
    g.put(5, 4, 'S')
    m = M(['#050506', '#141518', '#24262b', '#3a3d44', '#565a63', '#7c818c'], shading='sphere')
    return render(g, {'b': m, 's': Force(m, HL), 'S': Fixed('#b4bac4')})


@item('pellet')
def _pellet():
    g = Grid()
    g.disc(5.0, 10.5, 2.6, 'a')
    g.disc(11.0, 11.0, 2.6, 'b')
    g.disc(8.5, 6.0, 2.6, 'c')
    clay = ['#2a2018', '#55463a', '#86735f', '#a8937c', '#c8b49c', '#e8dcc8']
    return render(g, {'a': M(clay, shading='sphere'), 'b': M(clay, shading='sphere'), 'c': M(clay, shading='sphere')})


@item('pop_green')
def _pop_green():
    g = Grid()
    c, s_ = math.cos(math.radians(35)), math.sin(math.radians(35))
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - 8.0, y + 0.5 - 9.8
            u, v = dx * c + dy * s_, -dx * s_ + dy * c
            if (u / 4.6) ** 2 + (v / 5.8) ** 2 <= 1:
                g.put(x, y, 'g')
    g.put(9, 11, 'h')
    g.put(10, 10, 'h')
    g.put(9, 10, 'h')
    g.ascii([
        '......ll........',
        '.....llls.LL....',
        '.......sLLL.....',
        '.......s........',
    ], keep='.')
    seed = ['#0a1e08', '#1a3e12', '#2e6a1e', '#46962c', '#6ec044', '#b4ec80']
    leaf = ['#0e2a0a', '#24561a', '#3a8a28', '#5ab43a', '#88d860', '#c4f4a0']
    green = M(seed, shading='sphere')
    return render(g, {'g': green, 'h': Force(green, DK), 's': M(seed, outline=False, shading='flat'),
                      'l': M(leaf, outline=False), 'L': M(leaf, outline=False)})


@item('seastone_shard')
def _shard():
    g = art([
        '................',
        '........c.......',
        '.......ccC......',
        '.......ccCC.....',
        '...c...ccCC.....',
        '..ccC..ccCC..C..',
        '..ccCC.ccCC.cCC.',
        '..ccCC.ccCC.cCC.',
        '...cCCcccCCccCC.',
        '...ccCcccCCccC..',
        '....cCccccCcCC..',
        '....ccccccCCCC..',
        '....kkkkkkkkkk..',
        '................',
        '................',
        '................',
    ]).shift(0, 1)
    lit = M(['#06181c', '#163a42', '#3a7880', '#5aa2a6', '#8cd2d0', '#e0fff8'], group='crystal')
    shade = M(['#06181c', '#10303a', '#1e4c56', '#2e6870', '#468a90', '#7cb8b8'], group='crystal')
    return render(g, {'c': lit, 'C': shade, 'k': M(SEASTONE)})


# ============================================================================================ food
@item('meat_on_bone')
def _meat():
    g = Grid()
    g.band(0.5, 14.5, -1, 1, 'b')
    for pc in (0.9, 14.1):
        g.axis_disc(pc, -2, 1.25, 'b')
        g.axis_disc(pc, 2, 1.25, 'b')
    g.axis_ellipse(7.5, 0.5, 4.3, 7.4, 'm')
    for x, y in ((6, 6), (9, 9), (5, 9), (8, 5)):
        if g.at(x, y) == 'm':
            g.put(x, y, 'M')
    meat = M(['#1e0a04', '#46180a', '#7a3214', '#a8501e', '#c8743a', '#e8a868'], shading='sphere')
    return render(g, {'b': M(BONE), 'm': meat, 'M': Force(meat, DK)})


@item('sea_king_steak')
def _steak():
    g = Grid()
    g.ellipse(8.0, 8.8, 7.3, 5.6, 'm')
    for y in range(16):
        for x in range(16):
            if g.at(x, y) == 'm':
                v = ((x + 0.5 - 8.0) / 7.3) ** 2 + ((y + 0.5 - 8.8) / 5.6) ** 2
                if v > 0.55 and (y + 0.5) < 8.8 - 0.25 * (x - 8):
                    g.put(x, y, 's')
    for x, y in ((5, 8), (6, 9), (7, 9), (9, 11), (10, 11), (11, 10), (12, 8)):
        if g.at(x, y) == 'm':
            g.put(x, y, 'f')
    meat = M(['#2a0608', '#5a1218', '#8e2430', '#c04656', '#de7480', '#f6b4b8'],
             pattern=lambda x, y, i: DK if i in (MD, LT) and (x - y) % 5 == 0 and 4 <= x <= 13 else i)
    skin = M(['#031612', '#0a3428', '#145a46', '#1f8264', '#36a884', '#7ad8b8'],
             pattern=lambda x, y, i: HL if i == MD and (x + y) % 3 == 0 else i)
    fat = Force(M(['#3a2a2a', '#7a6262', '#c4a8a8', '#ecd8d4', '#f8ecea', '#ffffff']), MD)
    return render(g, {'m': meat, 's': skin, 'f': fat})


# ============================================================================================ papers
@item('wanted_poster')
def _wanted():
    g = art([
        '................',
        '..pppppppppppp..',
        '..pTpTpTTpTpTp..',
        '..pTTTpTpTTTpp..',
        '..pppppppppppp..',
        '..ppFFFFFFFFpp..',
        '..ppFaahhaaFpp..',
        '..ppFahffhaFpp..',
        '..ppFaffffaFpp..',
        '..ppFabbbbaFpp..',
        '..ppFFFFFFFFpp..',
        '..ppDDpDDpDDpp..',
        '..pppppppppppp..',
        '..pBpNNpNNpNNp..',
        '..pppppppppppp..',
        '................',
    ])
    paper = M(PAPER, pattern=lambda x, y, i: DK if i == MD and (x * 7 + y * 3) % 11 == 0 else i)
    ink = '#3a2412'
    return render(g, {'p': paper, 'T': Fixed(ink), 'F': Fixed('#5a3c20'), 'a': Fixed('#cdb48a'), 'h': Fixed('#2e2014'),
                      'f': Fixed('#e0b088'), 'b': Fixed('#8a5a38'), 'D': Fixed('#6a4a2a'), 'B': Fixed(ink), 'N': Fixed(ink)})


@item('poneglyph_rubbing')
def _rubbing():
    g = art([
        '................',
        '..pppppppppppp..',
        '..pkkkkkkkkkkp..',
        '..pkgkkgkgkgkp..',
        '..pkggkgkgggkp..',
        '..pkkkkkkkkkkp..',
        '..pkgkgkkggkkp..',
        '..pkgggkkgkgkp..',
        '..pkkkkkkkkkkp..',
        '..pkggkgkgkgkp..',
        '..pkkgkggkggkp..',
        '..pkkkkkkkkkkp..',
        '..pkrkkkkkkrkp..',
        '..pkkkkkkkkkkp..',
        '..pppppppppppp..',
        '................',
    ])
    return render(g, {'p': M(PAPER), 'k': Fixed('#1e1c22'), 'g': Fixed('#6e6a78'), 'r': Fixed('#8a2a2a')})


@item('beli_pouch')
def _beli_pouch():
    g = art([
        '................',
        '......kkkk......',
        '.....kSSSSk.....',
        '......kccK......',
        '.....pppppp.....',
        '....pppppppp....',
        '...pppppppppp...',
        '...ppppGGpppp...',
        '..pppppGpGpppp..',
        '..ppppGGGGpppp..',
        '..pppppGpGpppp..',
        '..ppppGGGGpppp..',
        '...pppppGpppp...',
        '....pppppppp....',
        '.....pppppp.....',
        '................',
    ])
    leather = M(['#3a2412', '#5a3a1e', '#7a5028', '#946436', '#b07c46', '#cc9a5e'])
    return render(g, {'p': leather, 'k': Fixed('#3a2412'), 'S': Fixed('#7a5028'), 'c': Fixed('#d8b04a'), 'K': Fixed('#3a2412'),
                      'G': Fixed('#f2d25a')})
