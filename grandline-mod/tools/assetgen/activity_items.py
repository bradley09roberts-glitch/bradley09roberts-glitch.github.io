"""Sea activity item sprites (treasure maps, sea catches, dishes) and their recipes, tags and loot modifier data.

Ids come from activity/ActivityItems.java (see source.item_ids); gen_assets merges recipes() and writes data_files()."""
import math

from .sprite import Grid, Mat, Fixed, Force, render, OD, OL, DK, MD, LT, HL
from .palette import *
from .items import item, art
from .data import NS, shapeless, cooking

# ============================================================================================ ramps
SILVER = ['#1a2230', '#3c4a5e', '#7a8ca2', '#a9b9cc', '#d2dde8', '#f4f8fc']
SARDINE_BACK = ['#06142a', '#0f2a52', '#1d4a86', '#2f6aae', '#4f8ccc', '#86b6e6']
BRONZE_FISH = ['#2a1606', '#5a3410', '#8e5a1e', '#c08434', '#e0aa52', '#f6d486']
PINK_FIN = ['#3a0a16', '#6e1a30', '#a8344e', '#d45c72', '#ee8a9a', '#ffc4cc']
TUNA_BACK = ['#060c2a', '#101e5a', '#1c3492', '#2e52c0', '#4a7ae0', '#8cb0f6']
GREEN_TEAL = ['#06201a', '#0f3e32', '#1c6450', '#2e8a6c', '#4eb08c', '#8ad8b8']
CARP_GOLD = ['#3a1604', '#6e2e08', '#a8520e', '#d87a1c', '#f2a83a', '#ffd67a']
CRIMSON = ['#2a0408', '#560a14', '#8a1422', '#bc2432', '#e04a50', '#ff8a86']
NAVY = ['#04060e', '#0c1426', '#16243e', '#22385a', '#34507a', '#5a78a2']
ABYSS = ['#020208', '#0a0a1c', '#14142e', '#222246', '#36366a', '#5a5a96']
GHOST = ['#2a2440', '#5a5278', '#9088b2', '#bab4d6', '#dcd8ee', '#f8f6ff']
CLOUD = ['#5a6478', '#98a2b6', '#c8d0de', '#e2e8f0', '#f2f6fa', '#ffffff']
SKY = ['#0c2a4a', '#1c4a7a', '#2e6aa6', '#4a8ccc', '#74b2e6', '#acd8fa']
SERPENT = ['#04180e', '#0a3220', '#145234', '#227a4c', '#3aa468', '#7ad89a']
SPINE_RED = ['#2a0604', '#580e08', '#8c1a10', '#c02c1a', '#e8502c', '#ff9060']
GRILL = ['#1a0c04', '#3e1e0a', '#6a3a16', '#985a26', '#c08040', '#e4b070']
ROAST = ['#1c0806', '#40140c', '#6e2a16', '#9c4424', '#c46a3a', '#e8a070']
BOWL_WOOD = ['#1e1206', '#3e2810', '#5e3e1a', '#805a28', '#a07838', '#c49c58']
CREAM = ['#4a3e2a', '#8a7a58', '#c4b48a', '#e2d4ac', '#f2e8cc', '#fffaea']
CURRY = ['#3a1004', '#702208', '#a83c0e', '#d8601a', '#f08a2e', '#ffc066']
RICE = ['#5a5a54', '#9a9a92', '#cacac2', '#e6e6de', '#f6f6f0', '#ffffff']
CHOWDER = ['#1a2a3a', '#3a5268', '#6a8aa4', '#9ab6cc', '#c4dae8', '#eef8ff']
HOTPOT_BROTH = ['#1a1406', '#3a2e0e', '#5e4c1a', '#82702c', '#a89444', '#ccbc6e']
IRON_POT = ['#08080a', '#18181c', '#2a2a30', '#3e3e46', '#56565e', '#7a7a84']
PLATE = ['#3a3c44', '#7c808a', '#bcc0c8', '#dcdfe4', '#eff1f4', '#ffffff']
SALMON_PINK = ['#4a1410', '#8a2c22', '#c4503c', '#e8785a', '#f8a080', '#ffd0b8']
KELP = ['#061a06', '#0e320c', '#1a5016', '#2a7222', '#449a34', '#7ac85a']
BREAD = ['#2e1a08', '#5e3812', '#8e5a20', '#bc8236', '#dcaa5a', '#f4d090']
MAP_PAPER = ['#3a2c16', '#76603a', '#b49c6a', '#d4bf8c', '#e8d8aa', '#f8eccc']


# ============================================================================================ fish geometry
class Fish:
    """A fish drawn along a tilted axis: head at the lower left, tail at the upper right (like the vanilla cod).

    u runs from the snout (-rx) to the tail root (+rx); v is the perpendicular offset (negative = back)."""

    def __init__(self, cx=7.6, cy=8.6, rx=5.4, ry=2.8, tilt=28, head=1.15, tail=3.2, fork=0.55, tail_len=3.4):
        self.cx, self.cy, self.rx, self.ry = cx, cy, rx, ry
        self.th = math.radians(tilt)
        self.head = head          # >1: blunter, fuller head
        self.tail, self.fork, self.tail_len = tail, fork, tail_len
        self.g = Grid()

    def uv(self, x, y):
        dx, dy = x + 0.5 - self.cx, y + 0.5 - self.cy
        c, s = math.cos(self.th), math.sin(self.th)
        return dx * c - dy * s, dx * s + dy * c

    def xy(self, u, v):
        c, s = math.cos(self.th), math.sin(self.th)
        return self.cx + u * c + v * s - 0.5, self.cy - u * s + v * c - 0.5

    def half(self, u):
        """Body half-height at u (fuller toward the head)."""
        t = u / self.rx
        if abs(t) >= 1:
            return 0.0
        k = math.sqrt(1 - t * t)
        if t < 0:
            k = k ** (1 / self.head)
        return self.ry * k

    def body(self, k='b'):
        for y in range(16):
            for x in range(16):
                u, v = self.uv(x, y)
                if abs(v) <= self.half(u) + 0.15:
                    self.g.put(x, y, k)
        return self

    def tailfin(self, k='t'):
        u0 = self.rx * 0.78
        for y in range(16):
            for x in range(16):
                u, v = self.uv(x, y)
                if u < u0 or u > u0 + self.tail_len:
                    continue
                f = (u - u0) / self.tail_len
                spread = 0.6 + f * self.tail
                if abs(v) > spread:
                    continue
                if self.fork > 0 and f > 0.55 and abs(v) < self.fork * self.tail * (f - 0.45):
                    continue
                if self.g.at(x, y) is None:
                    self.g.put(x, y, k)
        return self

    def dorsal(self, u0, u1, height, k='f', below=False):
        for y in range(16):
            for x in range(16):
                u, v = self.uv(x, y)
                if not (u0 <= u <= u1):
                    continue
                h = self.half(u)
                f = (u - u0) / max(0.01, u1 - u0)
                top = height * (1 - abs(f - 0.35) / 0.65 * 0.7)
                if below:
                    if h < v <= h + top and self.g.at(x, y) is None:
                        self.g.put(x, y, k)
                elif -h - top <= v < -h and self.g.at(x, y) is None:
                    self.g.put(x, y, k)
        return self

    def region(self, fn, k, only=('b',)):
        for y in range(16):
            for x in range(16):
                if self.g.at(x, y) in only:
                    u, v = self.uv(x, y)
                    if fn(u, v, self.half(u)):
                        self.g.put(x, y, k)
        return self

    def at(self, u, v, k):
        x, y = self.xy(u, v)
        self.g.put(round(x), round(y), k)
        return self

    def eye(self, u=None, v=None):
        u = -self.rx * 0.62 if u is None else u
        v = -self.ry * 0.18 if v is None else v
        return self.at(u, v, 'e')


EYE = Fixed('#0a0a0e')


def fish_render(f, mats):
    m = {'e': EYE}
    m.update(mats)
    return render(f.g, m)


# ============================================================================================ treasure maps
@item('treasure_map')
def _treasure_map():
    g = art([
        '................',
        '..rr........rr..',
        '.rRRpppppppppRr.',
        '.rRpppppppppppr.',
        '..ppppwwwppppp..',
        '..pppwgggwwppp..',
        '..ppwggggggwpp..',
        '..ppwgggxgggwp..',
        '..ppwggxgxggwp..',
        '..pppwgggxggwp..',
        '..pppwwggggwpp..',
        '..ppppppwwwppp..',
        '..pkpkpkpppppp..',
        '.rpppppppppppRr.',
        '.rRRpppppppppRr.',
        '..rr........rr..',
    ])
    paper = M(MAP_PAPER, pattern=lambda x, y, i: DK if i == MD and (x * 5 + y * 3) % 13 == 0 else i)
    roll = M(['#2a1e0c', '#5c4424', '#94784a', '#b89c6a', '#d6c090', '#ece0b8'])
    return render(g, {'p': paper, 'r': roll, 'R': Force(roll, LT), 'w': Fixed('#6f9ab0'), 'g': Fixed('#7d9a4c'),
                      'x': Fixed('#c4201c'), 'k': Fixed('#5a3c1e')})


@item('torn_map')
def _torn_map():
    g = art([
        '................',
        '..rr............',
        '.rRRpppppp......',
        '.rRpppppppp.....',
        '..ppppwwwpp.....',
        '..pppwgggwpp....',
        '..ppwggggggp....',
        '..ppwggggxp.....',
        '..ppwggxgx......',
        '..pppwgggpp.....',
        '..pppwwgggpp....',
        '..pppppppwp.....',
        '..pkpkpkppp.....',
        '.rpppppppppp....',
        '.rRRpppppppp....',
        '..rr............',
    ])
    paper = M(MAP_PAPER, pattern=lambda x, y, i: DK if i == MD and (x * 5 + y * 3) % 13 == 0 else i)
    roll = M(['#2a1e0c', '#5c4424', '#94784a', '#b89c6a', '#d6c090', '#ece0b8'])
    return render(g, {'p': paper, 'r': roll, 'R': Force(roll, LT), 'w': Fixed('#6f9ab0'), 'g': Fixed('#7d9a4c'),
                      'x': Fixed('#c4201c'), 'k': Fixed('#5a3c1e')})


# ============================================================================================ sea catches
@item('blue_sardine')
def _sardine():
    f = Fish(cx=7.6, cy=8.4, rx=5.6, ry=2.3, head=1.05, tail=2.4, tail_len=2.8, tilt=38)
    f.body().tailfin().dorsal(-0.5, 1.5, 1.1)
    f.region(lambda u, v, h: v < -h * 0.15, 'k')
    for u in (-2.4, -1.0, 0.4, 1.8):
        f.at(u, -0.2, 's')
    f.eye()
    back = M(SARDINE_BACK)
    return fish_render(f, {'b': M(SILVER), 'k': back, 's': Force(back, OD), 't': M(SARDINE_BACK), 'f': M(SARDINE_BACK, outline=False)})


@item('harbor_bream')
def _bream():
    f = Fish(cx=7.4, cy=8.6, rx=5.2, ry=3.7, head=1.25, tail=2.8, tail_len=2.8, tilt=36)
    f.body().tailfin().dorsal(-2.5, 2.8, 1.6).dorsal(-0.5, 2.2, 1.2, below=True)
    f.region(lambda u, v, h: v > h * 0.35, 'w')
    f.region(lambda u, v, h: abs(u + 2.4) < 0.5 and abs(v) < h * 0.8, 'g')
    f.eye()
    body = M(BRONZE_FISH)
    return fish_render(f, {'b': body, 'w': M(['#3a2a10', '#7a6030', '#c4a464', '#e2cc94', '#f2e2b8', '#fff6dc']), 'g': Force(body, DK),
                           't': M(PINK_FIN), 'f': M(PINK_FIN, outline=False)})


@item('rainbow_tuna')
def _tuna():
    f = Fish(cx=7.2, cy=8.8, rx=6.0, ry=3.2, head=1.2, tail=3.4, fork=0.8, tail_len=3.0, tilt=38)
    f.body().tailfin().dorsal(-1.0, 0.8, 1.4).dorsal(-0.6, 0.8, 1.2, below=True)
    f.region(lambda u, v, h: v < -h * 0.35, 'k')
    f.region(lambda u, v, h: -h * 0.35 <= v < -h * 0.05, 'g')
    f.region(lambda u, v, h: -h * 0.05 <= v < h * 0.25, 'y')
    f.region(lambda u, v, h: h * 0.25 <= v < h * 0.55, 'o')
    f.region(lambda u, v, h: v >= h * 0.55, 'p')
    for u in (2.6, 3.4, 4.2):
        f.at(u, -f.half(u) - 0.6, 'Y')
    f.eye()
    return fish_render(f, {'b': M(TUNA_BACK), 'k': M(TUNA_BACK), 'g': Fixed('#38b07a'), 'y': Fixed('#f2d23a'), 'o': Fixed('#f08a32'),
                           'p': Fixed('#e86a9a'), 't': M(TUNA_BACK), 'f': M(['#3a2a04', '#7a5a08', '#b88a10', '#e8b820', '#f8d84a', '#fff09a'], outline=False),
                           'Y': Fixed('#f8d84a')})


@item('needlenose_gar')
def _gar():
    f = Fish(cx=8.8, cy=7.4, rx=5.4, ry=2.0, head=1.0, tail=2.0, fork=0.0, tail_len=2.4, tilt=40)
    f.body().tailfin().dorsal(2.0, 3.6, 1.0).dorsal(2.0, 3.4, 0.9, below=True)
    # the needle snout
    for k in range(5):
        f.at(-f.rx - 0.6 - k * 0.9, 0.1, 'n')
    f.region(lambda u, v, h: v > h * 0.3, 'w')
    for u in (-2.6, -0.6, 1.2, 2.8):
        f.at(u, -0.4, 's')
    f.eye(u=-f.rx * 0.7)
    body = M(GREEN_TEAL)
    return fish_render(f, {'b': body, 'w': M(SILVER), 's': Force(body, OD), 'n': M(GREEN_TEAL, outline=False, shading='flat'),
                           't': M(GREEN_TEAL), 'f': M(GREEN_TEAL, outline=False)})


@item('behemoth_carp')
def _carp():
    f = Fish(cx=7.2, cy=8.8, rx=6.2, ry=4.3, head=1.3, tail=3.0, fork=0.35, tail_len=2.6, tilt=24)
    f.body().tailfin().dorsal(-2.5, 3.0, 1.2)
    # scales: a lattice of darker crescents
    f.region(lambda u, v, h: (int((u + 10) * 0.9) + int((v + 10) * 0.9)) % 2 == 0 and abs(v) < h - 0.6, 'S')
    f.region(lambda u, v, h: v > h * 0.45, 'w', only=('b', 'S'))
    f.eye(u=-f.rx * 0.6, v=-f.ry * 0.25)
    # barbels at the mouth
    f.at(-f.rx - 0.4, 0.9, 'B')
    f.at(-f.rx - 1.3, 1.6, 'B')
    gold = M(CARP_GOLD)
    return fish_render(f, {'b': gold, 'S': Force(gold, DK), 'w': M(['#4a2a0a', '#8a5a1e', '#c89a4a', '#e8c47a', '#f6dca4', '#fff2d0']),
                           't': M(CRIMSON), 'f': M(CRIMSON, outline=False), 'B': Fixed('#7a3a0e')})


@item('crimson_marlin')
def _marlin():
    f = Fish(cx=8.6, cy=8.0, rx=5.0, ry=2.5, head=1.1, tail=3.6, fork=0.9, tail_len=2.8, tilt=38)
    f.body().tailfin().dorsal(-3.2, 1.6, 3.2)
    for k in range(5):
        f.at(-f.rx - 0.5 - k * 0.95, 0.35, 'n')
    f.region(lambda u, v, h: v > h * 0.1, 'w')
    for u in (-1.6, -0.2, 1.2):
        f.region(lambda uu, v, h, u=u: abs(uu - u) < 0.35, 'B', only=('b',))
    f.eye(u=-f.rx * 0.7)
    red = M(CRIMSON)
    return fish_render(f, {'b': red, 'w': M(SILVER), 'B': Force(red, LT), 'n': M(['#1a0a0a', '#3a1a1a', '#6a3030', '#8a4a4a', '#aa6a6a', '#cc9a9a'], outline=False, shading='flat'),
                           't': M(CRIMSON), 'f': M(['#20060a', '#4a0e16', '#7a1a26', '#a82c3a', '#cc4a56', '#ee7a80'], outline=False)})


@item('thunder_eel')
def _eel():
    g = Grid()
    pts = [(2.0, 13.0), (4.0, 11.5), (6.0, 11.8), (8.0, 10.0), (9.0, 7.6), (11.0, 6.2), (13.0, 5.6), (14.2, 3.6)]
    for (ax, ay), (bx, by) in zip(pts, pts[1:]):
        g.seg(ax, ay, bx, by, 1.55, 'b')
    g.disc(2.4, 12.4, 2.3, 'b')
    # lightning stripe along the back
    for x, y in ((3, 11), (5, 10), (6, 10), (7, 9), (8, 8), (9, 6), (10, 6), (11, 5), (12, 5), (13, 4)):
        if g.at(x, y):
            g.put(x, y, 'z')
    g.put(1, 12, 'e')
    g.put(14, 2, 'f')
    g.put(15, 3, 'f')
    navy = M(NAVY)
    return render(g, {'b': navy, 'z': Fixed('#f6e04a'), 'e': Fixed('#f6e04a'), 'f': M(NAVY, outline=False)})


@item('abyss_lanternfish')
def _lantern():
    f = Fish(cx=8.8, cy=9.0, rx=5.0, ry=3.6, head=1.6, tail=2.6, fork=0.4, tail_len=2.4, tilt=30)
    f.body().tailfin().dorsal(-0.5, 2.0, 1.0)
    # gaping jaw with needle teeth
    f.region(lambda u, v, h: u < -f.rx * 0.55 and abs(v - h * 0.25) < 0.55, 'm')
    f.region(lambda u, v, h: u < -f.rx * 0.6 and abs(v - h * 0.25) < 0.55 and int(u * 2) % 2 == 0, 'T', only=('m',))
    f.eye(u=-f.rx * 0.45, v=-f.ry * 0.4)
    # the lure: a stalk from the brow to a glowing bulb
    g = f.g
    u0 = -f.rx * 0.45
    h0 = f.half(u0)
    for k, (du, dv) in enumerate(((0.0, 0.8), (-0.4, 1.7), (-1.1, 2.5), (-2.0, 3.0), (-2.9, 3.1))):
        x, y = f.xy(u0 + du, -h0 - dv)
        if g.at(round(x), round(y)) is None:
            g.put(round(x), round(y), 's')
    bx, by = f.xy(u0 - 3.8, -h0 - 2.7)
    bx, by = round(bx), round(by)
    for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        if g.at(bx + dx, by + dy) is None:
            g.put(bx + dx, by + dy, 'l')
    g.put(bx, by, 'L')
    f.region(lambda u, v, h: (int(u * 3) + int(v * 3)) % 7 == 0 and v > 0, 'p')
    return fish_render(f, {'b': M(ABYSS), 'm': Fixed('#300810'), 'T': Fixed('#e8e4d8'), 's': Fixed('#3a3a5a'), 'L': Fixed('#e8fffc'),
                           'l': Fixed('#5af0e6'), 'p': Fixed('#4ad8d0'), 't': M(ABYSS), 'f': M(ABYSS, outline=False)})


@item('ghost_ray')
def _ray():
    g = Grid()
    for y in range(16):
        for x in range(16):
            dx, dy = x + 0.5 - 7.0, y + 0.5 - 7.5
            # a kite: wide wings, pointed snout
            if abs(dx) / 6.6 + abs(dy) / 4.2 <= 1.0 and dy < 3.6:
                g.put(x, y, 'b')
    g.seg(7.0, 10.5, 13.5, 14.8, 0.55, 't')
    g.put(5, 6, 'e')
    g.put(9, 6, 'e')
    for x, y in ((4, 8), (7, 9), (10, 8), (6, 5), (8, 5)):
        if g.at(x, y) == 'b':
            g.put(x, y, 'd')
    ghost = M(GHOST)
    return render(g, {'b': ghost, 'd': Force(ghost, DK), 't': M(GHOST, outline=False, shading='flat'), 'e': Fixed('#5a2a8a')})


@item('cloud_skipper')
def _skipper():
    f = Fish(cx=8.0, cy=9.4, rx=5.2, ry=2.6, head=1.2, tail=2.8, fork=0.5, tail_len=2.6, tilt=36)
    f.body().tailfin()
    f.region(lambda u, v, h: v < -h * 0.2, 'k')
    f.eye()
    # cloud-puff fins along the back (and one under the belly)
    g = f.g
    puffs = [(-1.6, -1, 1.7), (0.4, -1, 1.9), (2.2, -1, 1.5), (-0.4, 1, 1.4)]
    for u, side, r in puffs:
        cx, cy = f.xy(u, side * (f.half(u) + r * 0.7))
        cx, cy = cx + 0.5, cy + 0.5
        for y in range(16):
            for x in range(16):
                if (x + 0.5 - cx) ** 2 + (y + 0.5 - cy) ** 2 <= r * r and g.at(x, y) is None:
                    g.put(x, y, 'c')
    return fish_render(f, {'b': M(CLOUD), 'k': M(SKY), 't': M(CLOUD), 'c': M(CLOUD, shading='sphere')})


@item('sea_king_fry')
def _fry():
    g = Grid()
    pts = [(3.0, 11.0), (5.4, 12.2), (8.0, 11.2), (9.6, 8.6), (11.6, 6.6), (14.0, 6.4)]
    for i, ((ax, ay), (bx, by)) in enumerate(zip(pts, pts[1:])):
        g.seg(ax, ay, bx, by, 1.6 - i * 0.22, 'b')
    g.disc(3.2, 10.2, 2.4, 'b')                     # head
    g.put(1, 10, 'b')
    g.put(0, 10, 'b')
    for x, y in ((3, 7), (4, 7), (2, 7)):           # horn
        g.put(x, y, 'h')
    g.put(4, 6, 'h')
    for x, y in ((6, 10), (8, 9), (10, 6), (12, 5)):  # dorsal spines
        g.put(x, y, 's')
        g.put(x, y - 1, 's')
    g.put(15, 5, 't')
    g.put(15, 7, 't')
    g.put(14, 5, 't')
    g.put(2, 9, 'e')
    g.put(1, 11, 'm')
    for x, y in ((5, 12), (7, 11), (9, 9)):
        if g.at(x, y) == 'b':
            g.put(x, y, 'w')
    green = M(SERPENT)
    return render(g, {'b': green, 'w': Force(green, HL), 'h': M(BONE, outline=False), 's': M(SPINE_RED, outline=False),
                      't': M(SPINE_RED, outline=False), 'e': Fixed('#f8d040'), 'm': Fixed('#2a0606')})


# ============================================================================================ dishes
def bowl(fill, extra=None, pot=False):
    """A wooden bowl (or an iron pot) seen from the side, its contents mounded on top."""
    g = Grid()
    g.ellipse(8.0, 8.6, 6.8, 2.2, 'f')              # surface of the contents
    for y in range(9, 15):
        w = 6.8 - (y - 9) ** 1.6 * 0.42
        for x in range(16):
            if abs(x + 0.5 - 8.0) <= w:
                g.put(x, y, 'w')
    g.rect(5, 14, 10, 14, 'w')
    g.put(1, 9, 'w')
    g.put(14, 9, 'w')
    if pot:
        g.put(0, 9, 'H')
        g.put(15, 9, 'H')
        g.put(0, 10, 'H')
        g.put(15, 10, 'H')
    if extra:
        extra(g)
    mats = {'f': fill, 'w': M(IRON_POT if pot else BOWL_WOOD), 'H': Fixed('#2a2a30')}
    return g, mats


@item('grilled_fish')
def _grilled_fish():
    f = Fish(cx=8.0, cy=8.4, rx=4.8, ry=2.3, head=1.1, tail=2.2, tail_len=2.4, tilt=40)
    f.body().tailfin()
    f.region(lambda u, v, h: (int((u + 10) * 1.2)) % 2 == 0 and abs(v) < h - 0.5, 'c')
    f.eye()
    g = f.g
    g.seg(0.5, 15.0, 4.2, 11.6, 0.55, 's')          # skewer through the fish
    g.seg(11.6, 4.6, 14.8, 1.2, 0.55, 's')
    grill = M(GRILL)
    return render(g, {'b': grill, 'c': Force(grill, OD), 't': M(GRILL), 'e': Fixed('#f0e8d0'),
                      's': M(LIGHT_WOOD, outline=False, shading='flat')})


@item('sea_king_roast')
def _roast():
    g = Grid()
    g.ellipse(8.0, 9.0, 7.2, 5.4, 'm')
    for y in range(16):
        for x in range(16):
            if g.at(x, y) == 'm' and (x - y) % 4 == 0 and 3 <= x <= 13:
                g.put(x, y, 'c')
    for x, y in ((6, 4), (7, 4), (8, 4), (5, 5)):
        g.put(x, y, 'k')                            # crackling rind
    g.put(11, 3, 'h')
    g.put(12, 2, 'h')
    g.put(12, 3, 'H')
    roast = M(ROAST, shading='sphere')
    return render(g, {'m': roast, 'c': Force(roast, OD), 'k': M(['#100404', '#2a0c06', '#4a1a0c', '#6a2a14', '#8a3e1e', '#b0603a'], outline=False),
                      'h': M(KELP, outline=False), 'H': Fixed('#9ad85a')})


@item('fisherman_stew')
def _stew():
    def extra(g):
        for x, y in ((5, 8), (9, 7), (11, 9), (7, 9)):
            g.put(x, y, 'c')
        for x, y in ((4, 9), (10, 8)):
            g.put(x, y, 'o')
        g.put(8, 8, 'g')
    g, m = bowl(M(CREAM, outline=False), extra)
    m.update({'c': Fixed('#f6efe0'), 'o': Fixed('#e88a2e'), 'g': Fixed('#5aa040')})
    return render(g, m)


@item('abyss_chowder')
def _chowder():
    def extra(g):
        for x, y in ((5, 8), (9, 9), (11, 8), (7, 7)):
            g.put(x, y, 'l')
        g.put(4, 9, 'k')
        g.put(10, 7, 'k')
    g, m = bowl(M(CHOWDER, outline=False), extra)
    m.update({'l': Fixed('#5af0e6'), 'k': Fixed('#2a6a3a')})
    return render(g, m)


@item('saltfire_curry')
def _curry():
    def extra(g):
        g.ellipse(5.4, 7.8, 3.0, 1.8, 'r')          # rice mound
        for x, y in ((10, 8), (12, 9), (9, 9)):
            g.put(x, y, 'p')
        g.put(11, 7, 'c')
        for x, y in ((7, 3), (8, 2), (9, 4), (10, 3)):
            g.put(x, y, 's')                         # steam
    g, m = bowl(M(CURRY, outline=False), extra)
    m.update({'r': M(RICE, shading='sphere'), 'p': Fixed('#d82a1a'), 'c': Fixed('#f6e8c0'), 's': Fixed((235, 235, 235, 150))})
    return render(g, m)


@item('tidewater_sashimi')
def _sashimi():
    g = Grid()
    g.ellipse(8.0, 10.4, 7.4, 3.6, 'P')              # plate
    g.ellipse(8.0, 9.9, 5.8, 2.4, 'p')
    for i, cx in enumerate((4.6, 7.4, 10.2)):        # three slices, fanned
        for y in range(16):
            for x in range(16):
                dx, dy = x + 0.5 - cx, y + 0.5 - (8.4 + i * 0.3)
                if (dx / 1.5) ** 2 + (dy / 2.6) ** 2 <= 1:
                    g.put(x, y, 's' if (x + y) % 3 else 'S')
    g.seg(11.8, 10.6, 14.2, 7.4, 0.6, 'k')         # a curl of kelp
    g.put(3, 11, 'w')
    g.put(4, 11, 'w')
    salmon = M(SALMON_PINK)
    plate = M(PLATE)
    return render(g, {'P': plate, 'p': Force(plate, LT), 's': salmon, 'S': Force(salmon, HL), 'k': M(KELP, outline=False), 'w': Fixed('#7ac05a')})


@item('sea_king_hotpot')
def _hotpot():
    def extra(g):
        for x, y in ((5, 8), (10, 8)):
            g.put(x, y, 'm')
            g.put(x + 1, y, 'm')
        g.put(8, 7, 'u')
        g.put(8, 8, 'U')
        for x, y in ((6, 4), (7, 3), (9, 3), (10, 4)):
            g.put(x, y, 's')
    g, m = bowl(M(HOTPOT_BROTH, outline=False), extra, pot=True)
    m.update({'m': Fixed('#b04a3a'), 'u': Fixed('#c8a070'), 'U': Fixed('#e8dcc0'), 's': Fixed((235, 235, 235, 150))})
    return render(g, m)


@item('pirates_feast')
def _feast():
    g = Grid()
    g.ellipse(8.0, 11.6, 7.6, 3.4, 'P')              # platter
    g.ellipse(8.0, 11.0, 6.4, 2.4, 'p')
    # meat on the bone (left)
    g.ellipse(5.2, 7.8, 3.2, 2.6, 'm')
    g.seg(7.6, 6.2, 10.0, 4.0, 0.7, 'o')
    g.disc(10.4, 3.6, 1.1, 'o')
    g.disc(9.6, 3.0, 0.9, 'o')
    # bread loaf (right)
    g.ellipse(11.4, 9.0, 2.8, 1.8, 'r')
    # a grilled fish across the front
    g.seg(3.0, 11.2, 11.0, 11.8, 0.9, 'f')
    g.put(12, 11, 'f')
    g.put(12, 12, 'f')
    g.put(13, 11, 'f')
    meat = M(['#1e0a04', '#46180a', '#7a3214', '#a8501e', '#c8743a', '#e8a868'], shading='sphere')
    plate = M(PLATE)
    return render(g, {'P': plate, 'p': Force(plate, LT), 'm': meat, 'o': M(BONE), 'r': M(BREAD, shading='sphere'), 'f': M(GRILL)})


# ============================================================================================ data
SEA_FISH = ['blue_sardine', 'harbor_bream', 'rainbow_tuna', 'needlenose_gar', 'behemoth_carp', 'crimson_marlin', 'thunder_eel',
            'abyss_lanternfish', 'ghost_ray', 'cloud_skipper']
GRAND_LINE_FISH = ['rainbow_tuna', 'needlenose_gar', 'behemoth_carp', 'crimson_marlin', 'thunder_eel']
DEEP_FISH = ['abyss_lanternfish', 'ghost_ray']


def _g(i):
    return f'{NS}:{i}'


def recipes():
    sea = '#' + _g('sea_fish')
    gl_fish = '#' + _g('grand_line_fish')
    deep = '#' + _g('deep_sea_fish')
    king = [_g('sea_king_steak'), _g('sea_king_fry')]
    r = {}
    for kind, t in (('smelting', 200), ('smoking', 100), ('campfire_cooking', 600)):
        r[f'grilled_fish_{kind}'] = cooking(kind, sea, 'grilled_fish', t, 0.35)
        r[f'sea_king_roast_{kind}'] = cooking(kind, _g('sea_king_steak'), 'sea_king_roast', t, 0.5)
    r['fisherman_stew'] = shapeless(['bowl', sea, sea, 'potato', 'carrot'], 'fisherman_stew')
    r['abyss_chowder'] = shapeless(['bowl', deep, deep, 'kelp', 'potato'], 'abyss_chowder')
    r['saltfire_curry'] = shapeless(['bowl', sea, _g('meat_on_bone'), 'potato', 'carrot', 'red_mushroom'], 'saltfire_curry')
    r['tidewater_sashimi'] = shapeless([gl_fish, gl_fish, 'kelp'], 'tidewater_sashimi')
    r['sea_king_hotpot'] = shapeless(['bowl', king, 'brown_mushroom', 'red_mushroom', 'potato', 'carrot'], 'sea_king_hotpot')
    r['pirates_feast'] = shapeless([_g('meat_on_bone'), _g('meat_on_bone'), _g('sea_king_roast'), _g('grilled_fish'), 'bread', 'apple'], 'pirates_feast')
    return r


def data_files():
    """Other data this feature owns: item tags and the fishing loot modifier. {path under data/: json}."""
    tag = lambda vals: {'replace': False, 'values': [_g(v) for v in vals]}
    return {
        'grandline/tags/item/sea_fish.json': tag(SEA_FISH),
        'grandline/tags/item/grand_line_fish.json': tag(GRAND_LINE_FISH),
        'grandline/tags/item/deep_sea_fish.json': tag(DEEP_FISH),
        'grandline/loot_modifiers/sea_fishing.json': {
            'type': _g('sea_fishing'),
            'conditions': [{'condition': 'neoforge:loot_table_id', 'loot_table_id': 'minecraft:gameplay/fishing'}]},
        'neoforge/loot_modifiers/global_loot_modifiers.json': {'replace': False, 'entries': [_g('sea_fishing')]},
    }
