"""3D armor models: geometry and textures for every TerraCraft armor set.

Each set is a list of cubes attached to the parts of the player model (head, body, arms, legs), in the same units
as Minecraft's HumanoidModel (1 unit = 1 texel = 1/16 block). This script packs every cube's box UV into one
texture per set, paints the texture from the cube's material and pattern, and writes:

    assets/terracraft/models/armor/<set>.json                   cubes, UVs and texture size (read by ArmorModels.java)
    textures/entity/equipment/humanoid[_leggings]/<set>.png     the painted texture (same image in both folders)

Shapes follow the Terraria sets: open-faced helmets for most sets (Copper/Tin crests, Iron's kettle hat, Silver and
Gold wings, Platinum horns, the Shadow hood, Crimson horns, the flowered Jungle Hat), closed ones for Lead, Tungsten,
the Meteor visor helmet and Molten's stone mask, and plated chests (breast, belly and back plates, pauldrons,
bracers, belts, gems) and greaves (thigh plates, knee cops, shin guards, boots, tassets). Original art only.
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
    def __init__(self, slot, part, origin, size, mat='main', pattern='plate', inflate=0.0, faces=None, pivot=None, rot=None, edge=None):
        self.slot, self.part = slot, part
        self.origin, self.size = list(origin), [int(s) for s in size]
        self.mat, self.pattern, self.inflate = mat, pattern, inflate
        self.faces = faces or {}
        self.pivot, self.rot = pivot, rot
        self.edge = edge
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
        return Cube(self.slot, part, [x, y, z], self.size, self.mat, self.pattern, self.inflate, faces, pivot, rot, self.edge)


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
# Shapes follow Terraria's armor sprites: most helmets leave the face open (only Lead, Tungsten and Molten cover
# it), and every chest/leg piece carries plates, trims, belts and greaves rather than a plain colour.

def open_helmet(a, pattern='plate', mat='main', inflate=1.0, edge=None):
    a.add('head', 'head', (-4, -8, -4), (8, 8, 8), mat=mat, pattern=pattern, inflate=inflate, edge=edge,
          faces={'north': 'open', 'down': 'none'})


def closed_helmet(a, front, pattern='plate', mat='main', inflate=1.0, edge=None):
    a.add('head', 'head', (-4, -8, -4), (8, 8, 8), mat=mat, pattern=pattern, inflate=inflate, edge=edge,
          faces={'north': front, 'down': 'none'})


def brim(a, mat='main', pattern='plate', size=12, y=-5.5):
    h = size / 2
    a.add('head', 'head', (-h, y, -h), (size, 1, size), mat=mat, pattern=pattern, edge='dark_edge')


def crest(a, mat='accent', pattern='plate', n=3, tall=4):
    """A fan of spikes over the crown, swept back (Copper/Tin)."""
    for i in range(n):
        z = -3 + i * 3
        a.add('head', 'head', (-0.5, -9 - tall, z - 1), (1, tall, 2), mat=mat, pattern=pattern, pivot=[0, -9, z], rot=[-0.35 - i * 0.12, 0, 0])


def helmet_wings(a, mat='accent', big=True, lift=0.35):
    """Feathered wings on both sides of the helmet (Gold/Silver)."""
    w, h = (1, 6) if big else (1, 4)
    for side in (-1, 1):
        x = -5.6 if side < 0 else 4.6
        a.add('head', 'head', (x, -8 - h + 2, -1), (w, h, 5), mat=mat, pattern='feather', pivot=[x + 0.5, -6, 0], rot=[-0.3, 0, lift * side])


def torso(a, base='chain', plate='plate', mat='main', base_mat='chain', edge='accent', gem=None, sleeves=12, pads='normal',
          abs_plates=True, bracers=True, belt='trim'):
    a.add('chest', 'body', (-4, 0, -2), (8, 12, 4), mat=base_mat, pattern=base, inflate=1.0, faces={'up': 'collar', 'down': 'none'})
    if sleeves:
        a.pair('chest', 'right_arm', (-3, -2, -2), (4, sleeves, 4), mat=base_mat, pattern=base, inflate=1.0,
               faces={'down': 'none' if sleeves < 12 else 'fill'})
    # breastplate (front and back) with a trimmed edge
    a.add('chest', 'body', (-4, -0.6, -3.8), (8, 6, 1), mat=mat, pattern=plate, edge=edge)
    a.add('chest', 'body', (-4, -0.6, 2.8), (8, 7, 1), mat=mat, pattern=plate, edge=edge)
    if abs_plates:   # segmented belly plates under the breastplate
        a.add('chest', 'body', (-3.5, 5.4, -3.6), (7, 2, 1), mat=mat, pattern='bands', edge=edge)
        a.add('chest', 'body', (-3, 7.4, -3.5), (6, 2, 1), mat=mat, pattern='bands', edge=edge)
    if gem:
        a.add('chest', 'body', (-1, 1.5, -4.4), (2, 2, 1), mat=gem, pattern='gem')
    if pads == 'normal':
        a.pair('chest', 'right_arm', (-4.2, -3.3, -2.9), (5, 3, 6), mat=mat, pattern=plate, edge=edge)
    elif pads == 'big':
        a.pair('chest', 'right_arm', (-4.6, -3.8, -3.3), (6, 4, 7), mat=mat, pattern=plate, edge=edge)
        a.pair('chest', 'right_arm', (-4.9, -0.4, -3.1), (5, 2, 6), mat=mat, pattern='bands', edge=edge)
    if bracers:
        a.pair('chest', 'right_arm', (-3, 5, -2), (4, 5, 4), mat=mat, pattern=plate, inflate=1.25, edge=edge, faces={'up': 'none', 'down': 'none'})
    if belt:
        a.add('chest', 'body', (-4, 10, -2), (8, 2, 4), mat=belt, pattern='belt', inflate=1.25, faces={'up': 'none', 'down': 'none'})
        a.add('chest', 'body', (-1, 9.6, -4.4), (2, 2, 1), mat='buckle', pattern='gem')


def greaves(a, base='chain', plate='plate', mat='main', base_mat='chain', edge='accent', tassets=True, boot_mat=None, belt='trim'):
    a.add('legs', 'body', (-4, 8, -2), (8, 4, 4), mat=belt, pattern='belt', inflate=0.55, faces={'up': 'none', 'down': 'none'})
    a.pair('legs', 'right_leg', (-2, 0, -2), (4, 12, 4), mat=base_mat, pattern=base, inflate=0.5, faces={'up': 'none'})
    a.pair('legs', 'right_leg', (-2.5, 0.5, -3.0), (5, 4, 1), mat=mat, pattern=plate, edge=edge)      # thigh plate
    a.pair('legs', 'right_leg', (-2.5, 4.3, -3.4), (5, 2, 1), mat=edge, pattern='plate')               # knee cop
    a.pair('legs', 'right_leg', (-2.5, 6.3, -3.0), (5, 3, 1), mat=mat, pattern=plate, edge=edge)       # shin guard
    a.pair('legs', 'right_leg', (-2, 8.6, -2), (4, 3, 4), mat=boot_mat or mat, pattern=plate, inflate=0.9, edge=edge, faces={'up': 'none'})
    a.pair('legs', 'right_leg', (-2.5, 10.4, -3.9), (5, 2, 2), mat=boot_mat or mat, pattern=plate)
    if tassets:
        a.add('legs', 'body', (-4, 10.4, -3.3), (8, 3, 1), mat=mat, pattern='bands', edge=edge)
        a.add('legs', 'body', (-4, 10.4, 2.3), (8, 3, 1), mat=mat, pattern='bands', edge=edge)


def ore(name, metal, **extra):
    m = hexc(metal)
    mats = {'main': m, 'chain': shade(m, 0.7), 'trim': '#5A4030', 'dark': '#16141C', 'accent': shade(m, 1.3),
            'dark_edge': shade(m, 0.55), 'buckle': '#D8B040'}
    mats.update(extra)
    return Armor(name, mats)


def wood_set():
    a = Armor('wood', {'main': '#8C5A32', 'chain': '#6A4426', 'trim': '#4E3220', 'dark': '#2A1A10', 'accent': '#A8784A',
                       'dark_edge': '#5A3A20', 'buckle': '#8A8A90'})
    open_helmet(a, 'wood', edge='trim')
    a.add('head', 'head', (-5, -6, -5), (10, 1, 10), mat='accent', pattern='wood', edge='trim', faces={'up': 'none', 'down': 'none'})
    torso(a, base='cloth', plate='wood', base_mat='trim', edge='trim', sleeves=6, pads='normal', bracers=False)
    greaves(a, base='cloth', plate='wood', base_mat='trim', edge='trim', tassets=False, boot_mat='trim')
    return a


def copper_set():
    a = ore('copper', '#C8783C')
    open_helmet(a)
    crest(a, 'accent')
    torso(a, gem=None, pads='normal')
    greaves(a, tassets=False)
    return a


def tin_set():
    a = ore('tin', '#B4A88C')
    open_helmet(a)
    crest(a, 'accent', n=2, tall=3)
    a.add('head', 'head', (-5.2, -4, -5.2), (2, 4, 3), mat='main', edge='accent')   # cheek guards
    a.add('head', 'head', (3.2, -4, -5.2), (2, 4, 3), mat='main', edge='accent')
    torso(a, pads='normal')
    greaves(a, tassets=False)
    return a


def iron_set():
    a = ore('iron', '#A0A0A8')
    open_helmet(a)
    brim(a, 'main')                 # kettle hat
    torso(a, pads='normal')
    greaves(a)
    return a


def lead_set():
    a = ore('lead', '#5A6478')
    closed_helmet(a, 'slit')
    brim(a, 'main', size=11, y=-6)
    a.add('head', 'head', (-0.5, -10, -4.5), (1, 2, 9), mat='accent')
    torso(a, pads='big')
    greaves(a)
    return a


def silver_set():
    a = ore('silver', '#D8DCE2', accent='#E0B840', gem='#E04040')
    open_helmet(a)
    helmet_wings(a, 'accent', big=False)
    a.add('head', 'head', (-0.5, -9.4, -5.4), (1, 2, 1), mat='gem', pattern='gem')
    torso(a, gem='gem', pads='big')
    greaves(a)
    return a


def tungsten_set():
    a = ore('tungsten', '#A8C0A0', accent='#8A60C0', gem='#B080F0')
    closed_helmet(a, 'visor')
    a.add('head', 'head', (-0.5, -11.5, -3), (1, 3, 8), mat='main', edge='accent')
    a.add('head', 'head', (-0.5, -8.6, -5.5), (1, 1, 1), mat='gem', pattern='gem')
    torso(a, gem='gem', pads='big')
    greaves(a)
    return a


def gold_set():
    a = ore('gold', '#E6BE3C', accent='#FFE070', gem='#E02838')
    open_helmet(a)
    helmet_wings(a, 'accent', big=True, lift=0.45)
    a.add('head', 'head', (-0.5, -9.4, -5.4), (1, 2, 1), mat='gem', pattern='gem')
    torso(a, gem='gem', pads='big')
    greaves(a)
    return a


def platinum_set():
    a = ore('platinum', '#C4D4E8', accent='#E8F0FA', gem='#E02838', trim='#A02030')
    open_helmet(a)
    for side in (-1, 1):   # swept horns
        x = -6 if side < 0 else 5
        a.add('head', 'head', (x, -12, -1), (1, 5, 2), mat='accent', pivot=[x + 0.5, -7, 0], rot=[-0.4, 0, 0.5 * side])
    a.add('head', 'head', (-0.5, -9.4, -5.4), (1, 2, 1), mat='gem', pattern='gem')
    torso(a, gem='gem', pads='big', belt='trim')
    greaves(a, belt='trim')
    return a


def shadow_set():
    a = Armor('shadow', {'main': '#3C2C5C', 'chain': '#2A1E40', 'trim': '#5C4488', 'dark': '#0C0814', 'accent': '#9A70E0',
                         'glow': '#D070FF', 'dark_edge': '#1A1028', 'buckle': '#C070FF'})
    open_helmet(a, 'scale')
    for i, z in enumerate((-3, 0, 3)):
        a.add('head', 'head', (-0.5, -12 + i, z - 1), (1, 4, 2), mat='accent', pattern='scale', pivot=[0, -9, z], rot=[-0.5, 0, 0])
    a.add('head', 'head', (-4.5, -9.2, -5.6), (9, 2, 2), mat='main', pattern='scale', edge='accent')   # hood brow
    a.add('head', 'head', (-6, -8, -2), (1, 2, 5), mat='accent', pattern='scale', pivot=[-5, -7, 0], rot=[0, 0.3, 0.3])
    a.add('head', 'head', (5, -8, -2), (1, 2, 5), mat='accent', pattern='scale', pivot=[5, -7, 0], rot=[0, -0.3, -0.3])
    torso(a, base='scale', plate='scale', base_mat='chain', gem='glow', pads='big', belt='chain')
    a.pair('chest', 'right_arm', (-5, -6, -0.5), (1, 3, 1), mat='accent', pattern='scale', pivot=[-3, -3, 0], rot=[0, 0, -0.5])
    greaves(a, base='scale', plate='scale', belt='chain')
    a.pair('legs', 'right_leg', (-0.5, 2.5, -4.6), (1, 3, 1), mat='accent', pattern='scale', pivot=[0, 4.5, -3.2], rot=[-0.6, 0, 0])
    return a


def crimson_set():
    a = Armor('crimson', {'main': '#5A1A22', 'chain': '#3A1016', 'trim': '#E6DCC4', 'dark': '#1C0608', 'accent': '#C8404A',
                          'glow': '#FFD040', 'dark_edge': '#2A0A0E', 'buckle': '#E6DCC4', 'horn': '#D84050'})
    open_helmet(a, 'flesh')
    for side in (-1, 1):   # curved red horns
        x = -5.5 if side < 0 else 4.5
        a.add('head', 'head', (x, -11, -1), (1, 4, 2), mat='horn', pattern='horn', pivot=[x + 0.5, -7, 0], rot=[-0.2, 0, 0.45 * side])
        a.add('head', 'head', (x + 2 * side, -14, -0.5), (1, 3, 1), mat='horn', pattern='horn', pivot=[x + 0.5, -7, 0], rot=[-0.2, 0, 0.75 * side])
    torso(a, base='flesh', plate='flesh', base_mat='chain', edge='accent', pads='normal', belt='chain')
    for y in (0.8, 2.6, 4.4):   # ribs over the breastplate
        a.add('chest', 'body', (-3.5, y, -4.1), (7, 1, 1), mat='trim', pattern='bone')
    greaves(a, base='flesh', plate='flesh', edge='accent', belt='chain')
    return a


def jungle_set():
    a = Armor('jungle', {'main': '#5A9A34', 'chain': '#3A6A2A', 'trim': '#7A5A30', 'dark': '#1A2A10', 'accent': '#9AD050',
                         'flower': '#E04060', 'gem': '#F0D040', 'dark_edge': '#2A4A18', 'buckle': '#C8A050'})
    # Jungle Hat: a leafy dome with a floppy brim and red flowers on top, face open
    a.add('head', 'head', (-4.5, -9.5, -4.5), (9, 4, 9), mat='main', pattern='leaf', faces={'down': 'none'})
    a.add('head', 'head', (-6.5, -6.5, -6.5), (13, 1, 13), mat='accent', pattern='leaf')
    a.add('head', 'head', (-4.5, -6, 2), (9, 5, 3), mat='main', pattern='leaf')   # leaves over the back of the neck
    for x, z in ((-2.5, -2), (1, -1), (-0.5, 1.5)):
        a.add('head', 'head', (x, -11.5, z), (2, 2, 2), mat='flower', pattern='flower')
    torso(a, base='leaf', plate='leaf', base_mat='main', mat='accent', edge='trim', sleeves=6, pads='none', abs_plates=False, bracers=False, belt='trim')
    a.add('chest', 'body', (-3.6, -0.4, -3.9), (2, 11, 1), mat='trim', pattern='belt', pivot=[0, 0, -3.5], rot=[0, 0, -0.5])  # sash
    a.pair('chest', 'right_arm', (-4, -3, -3), (5, 2, 6), mat='accent', pattern='leaf')
    a.add('legs', 'body', (-4, 8, -2), (8, 4, 4), mat='trim', pattern='belt', inflate=0.55, faces={'up': 'none', 'down': 'none'})
    a.pair('legs', 'right_leg', (-2, 0, -2), (4, 12, 4), mat='chain', pattern='cloth', inflate=0.5, faces={'up': 'none'})
    a.pair('legs', 'right_leg', (-2, 6, -2), (4, 4, 4), mat='trim', pattern='wrap', inflate=0.7, faces={'up': 'none', 'down': 'none'})
    a.pair('legs', 'right_leg', (-2.5, 0.5, -3.0), (5, 4, 1), mat='accent', pattern='leaf')
    a.pair('legs', 'right_leg', (-2, 9, -2), (4, 3, 4), mat='trim', pattern='cloth', inflate=0.8, faces={'up': 'none'})
    a.add('legs', 'body', (-4.5, 10, -3.2), (9, 4, 1), mat='accent', pattern='leaf')
    return a


def molten_set():
    # Terraria's Molten armor: grey volcanic stone shot through with glowing lava cracks; the helmet is a stone mask
    a = Armor('molten', {'main': '#7A7068', 'chain': '#4A4440', 'trim': '#5A3020', 'dark': '#1A1210', 'accent': '#9A8E84',
                         'glow': '#FF8A20', 'horn': '#4A4440', 'dark_edge': '#3A3430', 'buckle': '#FF8A20'})
    closed_helmet(a, 'mask', 'lava')
    a.add('head', 'head', (-4.5, -10, -4.5), (9, 2, 9), mat='main', pattern='lava', edge='dark_edge')
    for side in (-1, 1):
        x = -6.5 if side < 0 else 5.5
        a.add('head', 'head', (x, -12, -1), (1, 5, 2), mat='horn', pattern='lava', pivot=[x + 0.5, -7, 0], rot=[-0.25, 0, 0.35 * side])
    torso(a, base='lava', plate='lava', base_mat='chain', edge='dark_edge', gem='glow', pads='big', belt='chain')
    greaves(a, base='lava', plate='lava', base_mat='chain', edge='dark_edge', belt='chain')
    return a


def meteor_set():
    # Meteor armor: dark purple-blue plates with fiery orange flames; the helmet is closed, with a glass visor
    a = Armor('meteor', {'main': '#4A3A70', 'chain': '#2E2448', 'trim': '#6A5A90', 'dark': '#16101A', 'accent': '#E86A20',
                         'glow': '#FFC040', 'fire': '#FF7A20', 'glass': '#3A70C0', 'dark_edge': '#221A36', 'buckle': '#FFC040'})
    closed_helmet(a, 'glass', 'rock', inflate=1.5, edge='accent')
    a.add('head', 'head', (-3.5, -11, -3.5), (7, 1, 7), mat='main', pattern='rock')
    a.add('head', 'head', (-0.5, -13, -2), (1, 3, 6), mat='fire', pattern='fire')
    a.add('head', 'head', (-6.5, -6, -2), (1, 3, 4), mat='trim', pattern='plate')
    a.add('head', 'head', (5.5, -6, -2), (1, 3, 4), mat='trim', pattern='plate')
    torso(a, base='rock', plate='rock', base_mat='chain', edge='accent', gem='glow', pads='big', belt='chain')
    greaves(a, base='rock', plate='rock', base_mat='chain', edge='accent', belt='chain')
    a.pair('legs', 'right_leg', (-2.5, 9.5, -2.5), (5, 1, 5), mat='fire', pattern='fire')
    return a


def all_sets():
    return [wood_set(), copper_set(), tin_set(), iron_set(), lead_set(), silver_set(), tungsten_set(), gold_set(), platinum_set(),
            shadow_set(), crimson_set(), jungle_set(), molten_set(), meteor_set()]


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
                if cube.edge and face not in ('up', 'down') and (y == 0 or y == fh - 1) and fh > 2:
                    trim = ramp(armor.mats[cube.edge])          # trim along the top and bottom of plates
                    col = trim[3] if y == 0 else trim[1]
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
    if pattern == 'bands':
        # overlapping horizontal plates: light top lip, shadowed bottom
        return p[3] if y % 2 == 0 else (p[1] if r > 0.2 else p[2])
    if pattern == 'feather':
        if y % 2 == 0 and x % 2 == 0:
            return p[1]
        return p[3] if (x + y) % 3 == 0 else p[2]
    if pattern == 'fire':
        t = y / max(1, h - 1)
        return armor.mats.get('glow', p[4]) if t > 0.6 or r < 0.2 else (p[3] if t > 0.3 else p[2])
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
    elif kind == 'visor':
        # closed face with a wide eye opening and breathing holes
        for x in range(1, w - 1):
            c.set(x0 + x, y0 + 3, dark)
            c.set(x0 + x, y0 + 4, dark)
        for x in range(2, w - 2, 2):
            c.set(x0 + x, y0 + 6, dark)
    elif kind == 'mask':
        # Molten's stone face: dark glowing eye holes, a nose ridge and a grim mouth
        for ex in (1, w - 4):
            for dx in range(3):
                c.set(x0 + ex + dx, y0 + 3, dark)
            c.set(x0 + ex + 1, y0 + 3, glow)
        c.set(x0 + w // 2 - 1, y0 + 4, p[3]); c.set(x0 + w // 2, y0 + 4, p[3])
        for x in range(2, w - 2):
            c.set(x0 + x, y0 + 6, dark)
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
