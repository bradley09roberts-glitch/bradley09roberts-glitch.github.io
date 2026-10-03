"""3D armor models: geometry and textures for every TerraCraft armor set.

Each set is a list of cubes attached to the parts of the player model (head, body, arms, legs), in the same units
as Minecraft's HumanoidModel (1 unit = 1 texel = 1/16 block). This script packs every cube's box UV into one
texture per set, paints the texture from the cube's material and pattern, and writes:

    assets/terracraft/models/armor/<set>.json                   cubes, UVs and texture size (read by ArmorModels.java)
    textures/entity/equipment/humanoid[_leggings]/<set>.png     the painted texture (same image in both folders)

Shapes follow the Terraria sets: domed helmets with nasal guards and visors for the ore sets, crowns for gold and
platinum, the Jungle Hat's wide leafy brim, Molten's horns, the Meteor bubble helmet, Shadow's spikes and the
Crimson bone mask. All art is original and generated here.
"""
import json
import math
import os
import random

from pixelart import Canvas, hexc, shade

# part -> (pivot of the part in model space); cubes are given relative to their part like HumanoidModel.addBox
PARTS = ('head', 'body', 'right_arm', 'left_arm', 'right_leg', 'left_leg')
MIRROR = {'right_arm': 'left_arm', 'right_leg': 'left_leg'}


class Cube:
    def __init__(self, slot, part, origin, size, mat='main', pattern='plate', inflate=0.0, faces=None, pivot=None, rot=None):
        self.slot, self.part = slot, part
        self.origin, self.size = list(origin), [int(s) for s in size]
        self.mat, self.pattern, self.inflate = mat, pattern, inflate
        self.faces = faces or {}
        self.pivot, self.rot = pivot, rot
        self.uv = None

    def mirrored(self):
        part = MIRROR[self.part]
        x, y, z = self.origin
        w = self.size[0]
        pivot = rot = None
        if self.pivot:
            pivot = [-self.pivot[0], self.pivot[1], self.pivot[2]]
            x = -(x + w)
        else:
            x = -(x + w)
        if self.rot:
            rot = [self.rot[0], -self.rot[1], -self.rot[2]]
        faces = {({'east': 'west', 'west': 'east'}.get(k, k)): v for k, v in self.faces.items()}
        return Cube(self.slot, part, [x, y, z], self.size, self.mat, self.pattern, self.inflate, faces, pivot, rot)


class Armor:
    def __init__(self, name, mats):
        self.name = name
        self.mats = {k: hexc(v) if isinstance(v, str) else v for k, v in mats.items()}
        self.cubes = []

    def add(self, slot, part, origin, size, **kw):
        cube = Cube(slot, part, origin, size, **kw)
        self.cubes.append(cube)
        return cube

    def pair(self, slot, part, origin, size, **kw):
        """A cube on the right arm/leg and its mirror image on the left one."""
        cube = self.add(slot, part, origin, size, **kw)
        self.cubes.append(cube.mirrored())

    # ---- shared pieces
    def helmet_shell(self, pattern='plate', front='open', inflate=1.0, mat='main'):
        self.add('head', 'head', (-4, -8, -4), (8, 8, 8), mat=mat, pattern=pattern, inflate=inflate,
                 faces={'north': front, 'down': 'none'})

    def chest_base(self, pattern='plate', sleeve='plate', sleeve_len=12, inflate=1.0, mat='main', sleeve_mat='main'):
        self.add('chest', 'body', (-4, 0, -2), (8, 12, 4), mat=mat, pattern=pattern, inflate=inflate, faces={'up': 'collar', 'down': 'none'})
        if sleeve_len:
            self.pair('chest', 'right_arm', (-3, -2, -2), (4, sleeve_len, 4), mat=sleeve_mat, pattern=sleeve, inflate=inflate,
                      faces={'down': 'none' if sleeve_len < 12 else 'fill'})

    def legs_base(self, pattern='plate', mat='main', belt_mat='trim', belt_pattern='belt'):
        self.add('legs', 'body', (-4, 8, -2), (8, 4, 4), mat=belt_mat, pattern=belt_pattern, inflate=0.55, faces={'up': 'none', 'down': 'none'})
        self.pair('legs', 'right_leg', (-2, 0, -2), (4, 12, 4), mat=mat, pattern=pattern, inflate=0.5, faces={'up': 'none'})

    def boots(self, mat='main', pattern='plate', toe=True):
        self.pair('legs', 'right_leg', (-2, 8, -2), (4, 4, 4), mat=mat, pattern=pattern, inflate=0.85, faces={'up': 'none'})
        if toe:
            self.pair('legs', 'right_leg', (-2.5, 10.2, -3.9), (5, 2, 2), mat=mat, pattern=pattern)

    def knee(self, mat='main', pattern='plate', y=3.5):
        self.pair('legs', 'right_leg', (-2.5, y, -3.1), (5, 3, 1), mat=mat, pattern=pattern)

    def pauldron(self, mat='main', pattern='plate', big=False):
        if big:
            self.pair('chest', 'right_arm', (-4.6, -3.6, -3.3), (6, 4, 7), mat=mat, pattern=pattern)
            self.pair('chest', 'right_arm', (-4.9, -0.6, -3.1), (5, 2, 6), mat=mat, pattern=pattern)
        else:
            self.pair('chest', 'right_arm', (-4.2, -3.3, -2.9), (5, 3, 6), mat=mat, pattern=pattern)

    def bracer(self, mat='main', pattern='plate'):
        self.pair('chest', 'right_arm', (-3, 5, -2), (4, 4, 4), mat=mat, pattern=pattern, inflate=1.2, faces={'up': 'none', 'down': 'none'})

    def breastplate(self, mat='main', pattern='plate', back=True, h=7):
        self.add('chest', 'body', (-4, -0.6, -3.7), (8, h, 1), mat=mat, pattern=pattern)
        if back:
            self.add('chest', 'body', (-4, -0.6, 2.7), (8, h, 1), mat=mat, pattern=pattern)

    def belt(self, mat='trim'):
        self.add('chest', 'body', (-4, 10, -2), (8, 2, 4), mat=mat, pattern='belt', inflate=1.25, faces={'up': 'none', 'down': 'none'})


# ----------------------------------------------------------------------------------------- the sets
def ore_set(name, metal, style):
    m = hexc(metal)
    a = Armor(name, {'main': m, 'chain': shade(m, 0.72), 'trim': '#5A4030', 'dark': '#16141C', 'accent': shade(m, 1.35),
                     'gem': '#E03040' if style in ('crown',) else '#40A0F0'})
    front = {'nasal': 'open', 'cheeks': 'open', 'slit': 'slit', 'ridge': 'slit', 'tvisor': 'tvisor', 'fins': 'tvisor', 'crown': 'tvisor',
             'winged': 'slit'}[style]
    a.helmet_shell('plate', front)
    if style == 'nasal':
        a.add('head', 'head', (-0.5, -5.5, -5.6), (1, 4, 1), mat='accent')
        a.add('head', 'head', (-5.2, -6, -5.2), (10, 1, 10), mat='accent', inflate=0.2, faces={'up': 'none', 'down': 'none'})
    if style == 'cheeks':
        a.add('head', 'head', (-5.3, -4, -5.3), (2, 4, 3), mat='main')
        a.add('head', 'head', (3.3, -4, -5.3), (2, 4, 3), mat='main')
        a.add('head', 'head', (-0.5, -10, -4), (1, 2, 8), mat='accent')
    if style in ('ridge', 'tvisor'):
        a.add('head', 'head', (-0.5, -10.2, -4.5), (1, 2, 9), mat='accent')
    if style == 'fins':
        a.add('head', 'head', (-0.5, -11.5, -3), (1, 3, 8), mat='accent', pattern='plate')
        a.add('head', 'head', (-5.6, -7, -1), (1, 3, 4), mat='accent')
        a.add('head', 'head', (4.6, -7, -1), (1, 3, 4), mat='accent')
    if style == 'crown':
        a.add('head', 'head', (-5, -10, -5), (10, 1, 10), mat='accent', faces={'down': 'none', 'up': 'none'})
        for x in (-4.5, -0.5, 3.5):
            a.add('head', 'head', (x, -12, -5.3), (1, 2, 1), mat='accent')
        a.add('head', 'head', (-0.5, -9.6, -5.6), (1, 1, 1), mat='gem', pattern='gem')
    if style == 'winged':
        a.add('head', 'head', (-5, -10, -5), (10, 1, 10), mat='accent', faces={'down': 'none', 'up': 'none'})
        a.add('head', 'head', (-6, -11, -1), (1, 4, 4), mat='accent', pivot=[-5, -8, 0], rot=[0, 0, -0.35])
        a.add('head', 'head', (5, -11, -1), (1, 4, 4), mat='accent', pivot=[5, -8, 0], rot=[0, 0, 0.35])
        a.add('head', 'head', (-0.5, -9.6, -5.6), (1, 1, 1), mat='gem', pattern='gem')
    # chainmail under a breastplate, pauldrons and bracers
    a.chest_base('chain', 'chain', mat='chain', sleeve_mat='chain')
    a.breastplate()
    a.pauldron(big=style in ('crown', 'winged', 'fins'))
    a.bracer()
    a.belt()
    # greaves: chain legs, knee cops, plated boots; tassets for the fancier sets
    a.legs_base('chain', mat='chain')
    a.knee()
    a.boots()
    if style in ('crown', 'winged', 'fins', 'tvisor'):
        a.add('legs', 'body', (-4, 10.5, -3.3), (8, 3, 1), mat='main')
    return a


def wood_set():
    a = Armor('wood', {'main': '#8C5A32', 'trim': '#4E3220', 'dark': '#2A1A10', 'accent': '#A8784A'})
    a.helmet_shell('wood', 'open')
    a.add('head', 'head', (-5.5, -5.5, -5.5), (11, 1, 11), mat='accent', pattern='wood', faces={'up': 'none', 'down': 'none'})
    a.chest_base('wood', sleeve_len=0)
    a.breastplate('accent', 'wood', h=6)
    a.pair('chest', 'right_arm', (-3, -2, -2), (4, 4, 4), mat='main', pattern='wood', inflate=1.0, faces={'down': 'none'})
    a.belt()
    a.legs_base('wood')
    a.knee('accent', 'wood')
    a.boots('trim', 'cloth', toe=False)
    return a


def shadow_set():
    a = Armor('shadow', {'main': '#3C2C5C', 'chain': '#2A1E40', 'trim': '#5C4488', 'dark': '#0C0814', 'accent': '#7A5CC0', 'glow': '#D070FF'})
    a.helmet_shell('scale', 'eyes')
    for i, z in enumerate((-3, 0, 3)):
        a.add('head', 'head', (-0.5, -12 + i, z - 1), (1, 4, 2), mat='accent', pattern='scale', pivot=[0, -9, z], rot=[-0.5, 0, 0])
    a.add('head', 'head', (-6, -8, -2), (1, 2, 5), mat='accent', pattern='scale', pivot=[-5, -7, 0], rot=[0, 0.3, 0.3])
    a.add('head', 'head', (5, -8, -2), (1, 2, 5), mat='accent', pattern='scale', pivot=[5, -7, 0], rot=[0, -0.3, -0.3])
    a.chest_base('scale', 'scale', mat='main', sleeve_mat='chain')
    a.breastplate('accent', 'scale', back=False, h=6)
    a.pauldron('accent', 'scale', big=True)
    a.pair('chest', 'right_arm', (-5, -6, -0.5), (1, 3, 1), mat='trim', pattern='scale', pivot=[-3, -3, 0], rot=[0, 0, -0.5])
    a.pair('chest', 'right_arm', (-3.5, -6.5, -2.5), (1, 3, 1), mat='trim', pattern='scale', pivot=[-2, -3, -2], rot=[0, 0, -0.3])
    a.bracer('accent', 'scale')
    a.belt('chain')
    a.legs_base('scale', belt_mat='chain')
    a.knee('accent', 'scale')
    a.pair('legs', 'right_leg', (-0.5, 1.5, -4.5), (1, 3, 1), mat='trim', pattern='scale', pivot=[0, 4, -3], rot=[-0.6, 0, 0])
    a.boots('accent', 'scale')
    return a


def crimson_set():
    a = Armor('crimson', {'main': '#8E2630', 'chain': '#5E1820', 'trim': '#E6DCC4', 'dark': '#1C0608', 'accent': '#B8404A', 'glow': '#FFD040'})
    a.helmet_shell('flesh', 'skull')
    a.add('head', 'head', (-4.5, -6, -5.6), (9, 6, 1), mat='trim', pattern='bone', faces={'north': 'skull'})
    a.add('head', 'head', (-6, -10, -1), (1, 4, 1), mat='trim', pattern='bone', pivot=[-4.5, -7, 0], rot=[0, 0, -0.5])
    a.add('head', 'head', (5, -10, -1), (1, 4, 1), mat='trim', pattern='bone', pivot=[4.5, -7, 0], rot=[0, 0, 0.5])
    a.chest_base('flesh', 'flesh')
    for y in (1, 3.5, 6):
        a.add('chest', 'body', (-3.5, y, -3.6), (7, 1, 1), mat='trim', pattern='bone')
    a.add('chest', 'body', (-0.5, 0, -3.8), (1, 8, 1), mat='trim', pattern='bone')
    a.add('chest', 'body', (-0.5, -0.5, 2.8), (1, 11, 1), mat='trim', pattern='bone')
    a.pauldron('trim', 'bone')
    a.pair('chest', 'right_arm', (-4, -5, -0.5), (1, 2, 1), mat='trim', pattern='bone')
    a.belt('chain')
    a.legs_base('flesh', belt_mat='chain')
    a.knee('trim', 'bone')
    a.boots('chain', 'flesh')
    return a


def jungle_set():
    a = Armor('jungle', {'main': '#4E8C34', 'chain': '#3A6A2A', 'trim': '#7A5A30', 'dark': '#1A2A10', 'accent': '#8CC850',
                         'flower': '#E070B0', 'gem': '#F0D040'})
    # Jungle Hat: a leafy dome with a wide floppy brim, the tip falling back, and a flower
    a.add('head', 'head', (-4.5, -9.5, -4.5), (9, 4, 9), mat='main', pattern='leaf', faces={'down': 'none'})
    a.add('head', 'head', (-7, -6.5, -7), (14, 1, 14), mat='accent', pattern='leaf')
    a.add('head', 'head', (-2.5, -11.5, -1.5), (5, 2, 5), mat='main', pattern='leaf', pivot=[0, -9.5, 0], rot=[-0.5, 0, 0])
    a.add('head', 'head', (-1.5, -12, 2.5), (3, 1, 4), mat='accent', pattern='leaf', pivot=[0, -9.5, 0], rot=[-1.0, 0, 0])
    a.add('head', 'head', (3.5, -9, -5.2), (2, 2, 2), mat='flower', pattern='flower')
    a.add('head', 'head', (-4.5, -6.5, -4.5), (9, 1, 9), mat='trim', pattern='belt', faces={'up': 'none', 'down': 'none'})
    # leafy shirt with short sleeves and a vine sash
    a.chest_base('leaf', 'leaf', sleeve_len=6, mat='main')
    a.pair('chest', 'right_arm', (-4, -3, -3), (5, 2, 6), mat='accent', pattern='leaf')
    a.add('chest', 'body', (-4, 9, -2), (8, 4, 4), mat='accent', pattern='leaf', inflate=1.3, faces={'up': 'none', 'down': 'none'})
    a.add('chest', 'body', (-1, 2, -3.6), (2, 2, 1), mat='flower', pattern='flower')
    # pants with a leaf skirt and wrapped shins
    a.legs_base('cloth', mat='chain', belt_mat='accent', belt_pattern='leaf')
    a.pair('legs', 'right_leg', (-2, 6, -2), (4, 4, 4), mat='trim', pattern='wrap', inflate=0.7, faces={'up': 'none', 'down': 'none'})
    a.boots('trim', 'cloth', toe=False)
    return a


def molten_set():
    a = Armor('molten', {'main': '#3A1C14', 'chain': '#28120C', 'trim': '#6A2A14', 'dark': '#120604', 'accent': '#5A2618',
                         'glow': '#FF8A20', 'horn': '#2A1A16'})
    a.helmet_shell('lava', 'tvisor')
    # great curved horns: out from the temples, then up
    for side in (-1, 1):
        x0 = -7 if side < 0 else 4
        a.add('head', 'head', (x0, -8, -1.5), (3, 2, 3), mat='horn', pattern='horn')
        a.add('head', 'head', (-9 if side < 0 else 7, -12, -1), (2, 5, 2), mat='horn', pattern='horn',
              pivot=[-7.5 * 1 if side < 0 else 7.5, -8, 0], rot=[0, 0, 0.3 * side])
        a.add('head', 'head', (-9.5 if side < 0 else 8.5, -14.5, -0.5), (1, 3, 1), mat='glow', pattern='glow',
              pivot=[-7.5 if side < 0 else 7.5, -8, 0], rot=[0, 0, 0.45 * side])
    a.add('head', 'head', (-0.5, -10.5, -4.5), (1, 3, 9), mat='accent', pattern='lava')
    a.chest_base('lava', 'lava')
    a.breastplate('accent', 'lava')
    a.add('chest', 'body', (-1.5, 2, -4.4), (3, 3, 1), mat='glow', pattern='glow')
    a.pauldron('accent', 'lava', big=True)
    a.pair('chest', 'right_arm', (-5.5, -6.5, -0.5), (1, 3, 1), mat='horn', pattern='horn', pivot=[-3, -3.5, 0], rot=[0, 0, -0.45])
    a.pair('chest', 'right_arm', (-4.5, -6, -2.5), (1, 3, 1), mat='horn', pattern='horn', pivot=[-3, -3.5, -2], rot=[0, 0, -0.25])
    a.bracer('accent', 'lava')
    a.belt('trim')
    a.legs_base('lava', belt_mat='trim')
    a.knee('accent', 'lava')
    a.pair('legs', 'right_leg', (-0.5, 2.5, -4.6), (1, 2, 1), mat='horn', pattern='horn', pivot=[0, 4.5, -3.2], rot=[-0.6, 0, 0])
    a.boots('accent', 'lava')
    a.add('legs', 'body', (-4, 10.5, -3.3), (8, 3, 1), mat='accent', pattern='lava')
    return a


def meteor_set():
    a = Armor('meteor', {'main': '#5A4A60', 'chain': '#3E3244', 'trim': '#7A6880', 'dark': '#16101A', 'accent': '#8A5A48',
                         'glow': '#FF5A30', 'glass': '#3A70C0'})
    # rounded bubble helmet with a glass visor band and a fin
    a.helmet_shell('rock', 'glass', inflate=1.5)
    a.add('head', 'head', (-3.5, -11, -3.5), (7, 1, 7), mat='main', pattern='rock')
    a.add('head', 'head', (-0.5, -13, -2), (1, 3, 6), mat='glow', pattern='glow')
    a.add('head', 'head', (-6.5, -6, -2), (1, 3, 4), mat='trim', pattern='plate')
    a.add('head', 'head', (5.5, -6, -2), (1, 3, 4), mat='trim', pattern='plate')
    a.chest_base('rock', 'rock')
    a.breastplate('trim', 'rock')
    a.add('chest', 'body', (-1.5, 1.5, -4.5), (3, 3, 1), mat='glow', pattern='glow')
    a.pauldron('trim', 'rock', big=True)
    a.bracer('trim', 'rock')
    a.belt('chain')
    a.legs_base('rock', belt_mat='chain')
    a.knee('trim', 'rock')
    a.boots('trim', 'rock')
    return a


def all_sets():
    from_ore = {
        'copper': ('#C8783C', 'nasal'), 'tin': ('#B4A88C', 'cheeks'), 'iron': ('#A0A0A8', 'slit'), 'lead': ('#5A6478', 'ridge'),
        'silver': ('#D2D7DC', 'tvisor'), 'tungsten': ('#8CA088', 'fins'), 'gold': ('#E6BE3C', 'crown'), 'platinum': ('#BED2E6', 'winged'),
    }
    sets = [wood_set()]
    sets += [ore_set(n, c, s) for n, (c, s) in from_ore.items()]
    sets += [shadow_set(), crimson_set(), jungle_set(), molten_set(), meteor_set()]
    return sets


# ----------------------------------------------------------------------------------------- UV packing
def footprint(cube):
    w, h, d = cube.size
    return 2 * (d + w), d + h


def pack(cubes, width=128):
    order = sorted(cubes, key=lambda c: -footprint(c)[1])
    x = y = row = 0
    for cube in order:
        fw, fh = footprint(cube)
        if x + fw > width:
            x, y, row = 0, y + row, 0
        cube.uv = (x, y)
        x += fw
        row = max(row, fh)
    height = 32
    while height < y + row:
        height *= 2
    return width, height


# ----------------------------------------------------------------------------------------- painting
def ramp(color):
    return [shade(color, 0.45), shade(color, 0.72), color, shade(color, 1.18), shade(color, 1.4)]


def faces_of(cube):
    u, v = cube.uv
    w, h, d = cube.size
    return {
        'up': (u + d, v, w, d), 'down': (u + d + w, v, w, d),
        'east': (u, v + d, d, h), 'north': (u + d, v + d, w, h), 'west': (u + d + w, v + d, d, h), 'south': (u + 2 * d + w, v + d, w, h),
    }


def paint(armor, c, cube, rnd):
    p = ramp(armor.mats[cube.mat])
    dark = armor.mats.get('dark', (20, 20, 20, 255))
    glow = armor.mats.get('glow', p[4])
    for face, (x0, y0, fw, fh) in faces_of(cube).items():
        special = cube.faces.get(face)
        if special == 'none':
            continue
        for x in range(fw):
            for y in range(fh):
                col = pattern_pixel(cube.pattern, p, x, y, fw, fh, rnd, armor, face)
                # bevel: light top edge, dark bottom/side edges
                if cube.pattern not in ('glow', 'gem', 'flower'):
                    if y == 0 and face not in ('up', 'down'):
                        col = shade(col, 1.18)
                    elif y == fh - 1 or x == 0 or x == fw - 1:
                        col = shade(col, 0.82)
                c.set(x0 + x, y0 + y, col)
        if special:
            front_detail(c, special, x0, y0, fw, fh, p, dark, glow, armor)


def pattern_pixel(pattern, p, x, y, w, h, rnd, armor, face):
    r = rnd.random()
    if pattern == 'plate':
        if y % 4 == 3:
            return p[1]
        if y % 4 == 0:
            return p[3]
        if (x in (1, w - 2)) and y % 4 == 1 and w > 4:
            return p[4]          # rivets
        return p[2] if r > 0.12 else p[3]
    if pattern == 'chain':
        # small interlocking rings: a light dot on top of each ring, a dark gap below
        ring = (x + (y // 2) % 2) % 2
        if y % 2 == 0:
            return p[3] if ring == 0 else p[2]
        return p[1] if ring == 1 else shade(p[2], 0.9)
    if pattern == 'wood':
        if x % 4 == 0:
            return p[1]
        return p[2] if r > 0.25 else (p[3] if r > 0.1 else p[1])
    if pattern == 'scale':
        row = y // 2
        sx = (x + row) % 3
        if y % 2 == 1 and sx != 1:
            return p[0]
        return p[3] if sx == 1 else p[2]
    if pattern == 'leaf':
        if r < 0.18:
            return p[4]
        if r < 0.45:
            return p[3]
        if (x + y * 2) % 5 == 0:
            return p[1]
        return p[2]
    if pattern == 'cloth':
        return p[2] if r > 0.2 else p[1]
    if pattern == 'wrap':
        return p[3] if (x + y) % 3 == 0 else p[1]
    if pattern == 'belt':
        if y == 0 or y == h - 1:
            return p[1]
        if x == w // 2 and face in ('north',):
            return hexc('#D8B040')
        return p[2]
    if pattern == 'bone':
        return p[3] if r > 0.3 else p[2]
    if pattern == 'flesh':
        if r < 0.08:
            return armor.mats.get('glow', p[4]) if r < 0.015 else p[0]
        return p[2] if (x * 7 + y * 3) % 5 else p[1]
    if pattern == 'lava':
        crack = (x * 3 + y * 5 + (x * y) % 7) % 11 == 0 or (x + 2 * y) % 13 == 0
        if crack:
            return armor.mats['glow']
        return p[2] if r > 0.2 else p[1]
    if pattern == 'rock':
        if r < 0.06:
            return armor.mats['glow']
        if (x * 5 + y * 3) % 7 == 0:
            return p[1]
        return p[2] if r > 0.2 else p[3]
    if pattern == 'horn':
        return p[3] if (y % 3 == 0) else p[2]
    if pattern == 'glow':
        return shade(p[2], 1.25) if r > 0.4 else p[2]
    if pattern == 'gem':
        return p[3]
    if pattern == 'flower':
        return p[2] if (x + y) % 2 else armor.mats.get('gem', p[4])
    return p[2]


def front_detail(c, kind, x0, y0, w, h, p, dark, glow, armor):
    clear = (0, 0, 0, 0)
    if kind == 'open':
        # face opening: brow band on top, cheek bars on the sides
        for x in range(1, w - 1):
            for y in range(3, h):
                c.set(x0 + x, y0 + y, clear)
    elif kind == 'slit':
        for x in range(1, w - 1):
            c.set(x0 + x, y0 + 3, dark)
            c.set(x0 + x, y0 + 4, dark)
        for x in range(2, w - 2, 2):
            c.set(x0 + x, y0 + 6, dark)
    elif kind == 'tvisor':
        for x in range(1, w - 1):
            c.set(x0 + x, y0 + 3, dark)
        for y in range(3, h - 1):
            c.set(x0 + w // 2 - 1, y0 + y, dark)
            c.set(x0 + w // 2, y0 + y, dark)
    elif kind == 'eyes':
        for x in range(1, w - 1):
            for y in range(3, h - 1):
                c.set(x0 + x, y0 + y, dark)
        for ex in (2, w - 4):
            c.set(x0 + ex, y0 + 4, glow)
            c.set(x0 + ex + 1, y0 + 4, glow)
    elif kind == 'skull':
        bone = ramp(armor.mats['trim'])
        for x in range(w):
            for y in range(h):
                c.set(x0 + x, y0 + y, bone[3] if (x + y) % 5 else bone[2])
        for ex in (1, w - 4):
            for dx in range(3):
                for dy in range(2):
                    c.set(x0 + ex + dx, y0 + 1 + dy, dark)
            c.set(x0 + ex + 1, y0 + 1, glow)
        c.set(x0 + w // 2, y0 + 3, dark)
        for x in range(1, w - 1):
            c.set(x0 + x, y0 + h - 2, dark if x % 2 else bone[4])
    elif kind == 'glass':
        g = ramp(armor.mats['glass'])
        for x in range(1, w - 1):
            for y in range(2, 6):
                c.set(x0 + x, y0 + y, g[2] if y > 2 else g[3])
        c.set(x0 + 2, y0 + 3, g[4])
        c.set(x0 + 3, y0 + 3, g[4])
    elif kind == 'collar':
        for x in range(w):
            for y in range(h):
                if 1 <= y < h - 1 and 2 <= x < w - 2:
                    c.set(x0 + x, y0 + y, clear)


def write(assets, tex):
    for armor in all_sets():
        width, height = pack(armor.cubes)
        c = Canvas(width, height)
        rnd = random.Random(armor.name)
        for cube in armor.cubes:
            paint(armor, c, cube, rnd)
        for folder in ('humanoid', 'humanoid_leggings'):
            c.save(os.path.join(tex, f'entity/equipment/{folder}/{armor.name}.png'))
        data = {'texture_width': width, 'texture_height': height, 'cubes': []}
        for cube in armor.cubes:
            entry = {'slot': cube.slot, 'part': cube.part, 'origin': cube.origin, 'size': cube.size, 'uv': list(cube.uv)}
            if cube.inflate:
                entry['inflate'] = cube.inflate
            if cube.pivot:
                entry['pivot'] = cube.pivot
                entry['rotation'] = cube.rot
            data['cubes'].append(entry)
        path = os.path.join(assets, f'models/armor/{armor.name}.json')
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, 'w') as f:
            json.dump(data, f, separators=(',', ':'))
        with open(os.path.join(assets, f'equipment/{armor.name}.json'), 'w') as f:
            json.dump({'layers': {'humanoid': [{'texture': f'terracraft:{armor.name}'}],
                                  'humanoid_leggings': [{'texture': f'terracraft:{armor.name}'}]}}, f, indent=2)
            f.write('\n')


if __name__ == '__main__':
    root = os.path.join(os.path.dirname(__file__), '..', 'src/main/resources/assets/terracraft')
    write(root, os.path.join(root, 'textures'))
