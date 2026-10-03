"""Custom 3D models for creatures that need their own shape (no more reskinned bats and mouths).

Each creature is a tree of parts (pivot + cubes, Minecraft model units: y points down, the ground is y=24, the
front is -z) plus animation roles. This script packs the cubes' box UVs, paints the texture with the same painter
as the armor (tools/armor_models.py) and writes:

    assets/terracraft/models/creature/<name>.json     parts, cubes, UVs, animations (read by JsonCreatureModel)
    textures/entity/model/<name>.png                  the painted texture

Animation roles (JsonCreatureModel): head (looks around), leg_r/leg_l and arm_r/arm_l (walk swing), wing_r/wing_l
(flap), tail and wiggle (sway), jaw (chomp), bob (hover), spider (leg scuttle), flicker (flames).
"""
import json
import os
import random

import armor_models as am
from pixelart import Canvas, hexc


class Creature:
    def __init__(self, name, mats):
        self.name = name
        self.mats = {k: hexc(v) if isinstance(v, str) else v for k, v in mats.items()}
        self.parts = []      # (name, parent, pivot, rotation)
        self.cubes = []      # am.Cube with .part
        self.anims = []

    def part(self, name, parent=None, pivot=(0, 0, 0), rot=(0, 0, 0)):
        self.parts.append((name, parent, list(pivot), list(rot)))
        return name

    def box(self, part, origin, size, mat='main', pattern='skin', faces=None, inflate=0.0, edge=None):
        cube = am.Cube(None, part, origin, size, mat=mat, pattern=pattern, inflate=inflate, faces=faces, edge=edge)
        self.cubes.append(cube)
        return cube

    def anim(self, part, role, amp=1.0, speed=1.0, phase=0.0):
        self.anims.append({'part': part, 'role': role, 'amp': amp, 'speed': speed, 'phase': phase})


def mirror_x(origin, size):
    return [-(origin[0] + size[0]), origin[1], origin[2]]


# ----------------------------------------------------------------------------------------- Underworld
def imp():
    """Fire Imp: a small hunched red devil with big pointed ears, little horns and a tail, casting fireballs."""
    c = Creature('imp', {'main': '#D8505A', 'dark': '#2A0A10', 'horn': '#3A2020', 'cloth': '#3A1A2A', 'eye': '#FFD040',
                         'teeth': '#F0E8D8', 'glow': '#FF9020'})
    c.part('body', pivot=(0, 15, 0), rot=(0.3, 0, 0))
    c.box('body', (-3, -7, -2), (6, 7, 4))
    c.box('body', (-3, -2, -2), (6, 3, 4), mat='cloth', pattern='cloth', inflate=0.3)
    c.part('head', 'body', pivot=(0, -7, -0.5), rot=(-0.3, 0, 0))
    c.box('head', (-3.5, -7, -3.5), (7, 7, 7), faces={'north': 'face_imp'})
    for side in (-1, 1):
        ear = (-6.5, -5, -0.5) if side < 0 else (3.5, -5, -0.5)
        c.box('head', ear, (3, 2, 1))
        c.box('head', ((-6.5 if side < 0 else 5.5), -6, -0.5), (1, 1, 1))
        c.box('head', ((-3 if side < 0 else 2), -9, -1), (1, 2, 1), mat='horn', pattern='horn')
    for side, name in ((-1, 'arm_r'), (1, 'arm_l')):
        c.part(name, 'body', pivot=(3.8 * side, -6, 0), rot=(-0.9, 0, 0))
        c.box(name, (-1 if side > 0 else -1, 0, -1), (2, 7, 2))
        c.anim(name, 'cast', 0.25, 0.25, 0 if side < 0 else 3.14)
    c.box('arm_r', (-1.5, 6.5, -1.5), (3, 3, 3), mat='glow', pattern='fire')   # fireball in hand
    for side, name in ((-1, 'leg_r'), (1, 'leg_l')):
        c.part(name, pivot=(1.6 * side, 16, 0.5))
        c.box(name, (-1.5, 0, -1.5), (3, 6, 3))
        c.box(name, (-1.5, 6, -2.5), (3, 2, 4), mat='dark', pattern='skin')
        c.anim(name, name)
    c.part('tail', 'body', pivot=(0, -1, 2), rot=(0.9, 0, 0))
    c.box('tail', (-0.5, 0, 0), (1, 1, 6))
    c.box('tail', (-1, -0.5, 5.5), (2, 2, 2), mat='horn', pattern='horn')
    c.anim('tail', 'tail', 0.4, 0.2)
    c.anim('head', 'head')
    return c


def demon(name='demon', voodoo=False):
    """Demon: a red, horned, winged humanoid with clawed hands and a pointed tail; the Voodoo Demon carries the Guide doll."""
    skin = '#C85A5A' if voodoo else '#D86A6A'
    c = Creature(name, {'main': skin, 'dark': '#2A0A10', 'horn': '#E8DCC8', 'wing': '#5A4A4A' if not voodoo else '#4A3A3A',
                        'eye': '#FFE040', 'teeth': '#F0E8D8', 'doll': '#8A6A4A', 'doll_head': '#E8B890'})
    c.part('body', pivot=(0, 8, 0), rot=(0.15, 0, 0))
    c.box('body', (-3.5, 0, -2), (7, 8, 4))
    c.box('body', (-2.5, 1, -2.4), (5, 6, 1), pattern='bands')     # belly
    c.part('head', 'body', pivot=(0, 0, -0.5))
    c.box('head', (-3, -6, -3), (6, 6, 6), faces={'north': 'face_demon'})
    for side in (-1, 1):
        hx = -3.5 if side < 0 else 2.5
        c.box('head', (hx, -8, -1), (1, 3, 1), mat='horn', pattern='horn')
        c.box('head', (hx + 0.5 * side * 2, -10, 0), (1, 2, 1), mat='horn', pattern='horn')
        c.box('head', ((-4.5 if side < 0 else 3.5), -4, -0.5), (1, 2, 1))       # pointed ears
    for side, nm in ((-1, 'arm_r'), (1, 'arm_l')):
        c.part(nm, 'body', pivot=(4.5 * side, 1, 0), rot=(-0.4, 0, 0.15 * -side))
        c.box(nm, (-1.5, 0, -1.5), (3, 7, 3))
        c.box(nm, (-1.5, 7, -2), (3, 2, 1), mat='horn', pattern='horn')        # claws
        c.anim(nm, 'cast', 0.3, 0.3, 0 if side < 0 else 3.14)
    if voodoo:
        c.box('arm_r', (-1.5, 8, -3), (3, 2, 2), mat='doll', pattern='cloth')    # the Guide Voodoo Doll
        c.box('arm_r', (-1, 6.5, -2.5), (2, 2, 2), mat='doll_head', pattern='skin')
    for side, nm in ((-1, 'leg_r'), (1, 'leg_l')):
        c.part(nm, 'body', pivot=(1.8 * side, 8, 0), rot=(0.3, 0, 0))
        c.box(nm, (-1.5, 0, -1.5), (3, 5, 3))
        c.box(nm, (-1.5, 4, -0.5), (3, 4, 2))
        c.anim(nm, 'dangle', 0.25, 0.15, 0 if side < 0 else 3.14)
    for side, nm in ((-1, 'wing_r'), (1, 'wing_l')):
        c.part(nm, 'body', pivot=(2 * side, 1, 2), rot=(0, -0.3 * side, 0))
        origin = (-20, -9, 0) if side < 0 else (0, -9, 0)
        c.box(nm, origin, (20, 15, 0), mat='wing', pattern='membrane', faces={'north': 'batwing_r' if side < 0 else 'batwing_l',
                                                                               'south': 'batwing_l' if side < 0 else 'batwing_r'})
        c.anim(nm, nm, 0.7, 0.45)
    c.part('tail', 'body', pivot=(0, 7, 2), rot=(0.7, 0, 0))
    c.box('tail', (-0.5, 0, 0), (1, 1, 8))
    c.box('tail', (-1, -0.5, 7.5), (2, 2, 2), mat='horn', pattern='horn')
    c.anim('tail', 'tail', 0.35, 0.18)
    c.anim('body', 'bob', 1.2, 0.12)
    c.anim('head', 'head')
    return c


# ----------------------------------------------------------------------------------------- Corruption / Crimson
def eater_of_souls():
    """Eater of Souls: a flying, segmented worm-thing led by a head ringed with four mandibles."""
    c = Creature('eater_of_souls', {'main': '#6A5A6A', 'dark': '#1A1018', 'accent': '#7AA040', 'teeth': '#C8D890', 'eye': '#B8E060'})
    c.part('head', pivot=(0, 18, -2))
    c.box('head', (-3, -3, -4), (6, 6, 5), pattern='scale', faces={'north': 'face_eos'})
    c.box('head', (-3.5, -3.5, -1), (7, 7, 1), mat='accent', pattern='plate')   # green collar
    for i, (x, y) in enumerate(((-2.5, -2.5), (1.5, -2.5), (-2.5, 1.5), (1.5, 1.5))):
        nm = f'mandible_{i}'
        c.part(nm, 'head', pivot=(x + 0.5, y + 0.5, -4), rot=(0.3 * (1 if y > 0 else -1), 0.3 * (1 if x > 0 else -1), 0))
        c.box(nm, (-0.5, -0.5, -4), (1, 1, 4), mat='teeth', pattern='horn')
        c.anim(nm, 'jaw', 0.25, 0.5, i * 1.2)
    prev, z = 'head', 1
    for i, size in enumerate((5, 4, 3, 2)):
        nm = f'segment_{i}'
        c.part(nm, prev, pivot=(0, 0, z))
        c.box(nm, (-size / 2, -size / 2, 0), (size, size, 4), pattern='scale')
        c.box(nm, (-0.5, -size / 2 - 1.5, 1), (1, 2, 1), mat='accent', pattern='horn')   # back spine
        c.anim(nm, 'wiggle', 0.25, 0.3, i * 0.9)
        prev, z = nm, 4
    c.anim('head', 'bob', 0.8, 0.15)
    return c


def crimera():
    """Crimera: a floating lump of flesh, all teeth, with little eyes on top and dangling tendrils."""
    c = Creature('crimera', {'main': '#B04040', 'dark': '#2A0608', 'accent': '#E07070', 'teeth': '#F0E0C8', 'eye': '#FFE060'})
    c.part('body', pivot=(0, 17, 0))
    c.box('body', (-3.5, -3.5, -3.5), (7, 7, 7), pattern='flesh', faces={'north': 'face_maw'})
    c.box('body', (-2.5, -4.5, -2.5), (5, 1, 5), mat='accent', pattern='flesh')
    for x in (-2.5, 1.5):
        c.box('body', (x, -5.5, -2.5), (1, 1, 1), mat='eye', pattern='gem')
    for i, (x, z) in enumerate(((-2.5, -1.5), (1.5, -1.5), (-2.5, 1.5), (1.5, 1.5))):
        nm = f'tendril_{i}'
        c.part(nm, 'body', pivot=(x + 0.5, 3.5, z))
        c.box(nm, (-0.5, 0, -0.5), (1, 4, 1), mat='accent', pattern='flesh')
        c.box(nm, (-0.5, 3.5, -1), (1, 2, 1), mat='main', pattern='flesh')
        c.anim(nm, 'dangle', 0.35, 0.25, i * 1.5)
    c.anim('body', 'bob', 0.8, 0.15)
    return c


def face_monster():
    """Face Monster: a hunched Crimson ghoul whose head is mostly a huge gaping face, with long dragging arms."""
    c = Creature('face_monster', {'main': '#B86A4A', 'dark': '#2A0A08', 'cloth': '#5A3A2A', 'teeth': '#F0E8D0', 'eye': '#FFE060',
                                  'accent': '#8A3A2A'})
    c.part('body', pivot=(0, 12, 0), rot=(0.45, 0, 0))
    c.box('body', (-4, -10, -2.5), (8, 10, 5), pattern='flesh')
    c.box('body', (-4, -3, -2.5), (8, 3, 5), mat='cloth', pattern='cloth', inflate=0.3)
    c.part('head', 'body', pivot=(0, -10, -1), rot=(-0.45, 0, 0))
    c.box('head', (-4.5, -9, -5), (9, 9, 8), pattern='flesh', faces={'north': 'face_fm'})
    for side, nm in ((-1, 'arm_r'), (1, 'arm_l')):
        c.part(nm, 'body', pivot=(5 * side, -9, 0), rot=(-0.5, 0, 0))
        c.box(nm, (-1.5, 0, -1.5), (3, 14, 3), pattern='flesh')
        c.box(nm, (-2, 13, -2), (4, 2, 3), mat='accent', pattern='flesh')
        c.anim(nm, 'arm_r' if side < 0 else 'arm_l', 0.6)
    for side, nm in ((-1, 'leg_r'), (1, 'leg_l')):
        c.part(nm, pivot=(2 * side, 12, 0))
        c.box(nm, (-2, 0, -2), (4, 12, 4), mat='cloth', pattern='cloth')
        c.anim(nm, nm)
    c.anim('head', 'head')
    return c


def blood_crawler():
    """Blood Crawler: a spindly Crimson spider with a swollen pale abdomen and eight long legs."""
    c = Creature('blood_crawler', {'main': '#8A2A24', 'abdomen': '#E09070', 'dark': '#1A0404', 'teeth': '#E8D8C0', 'eye': '#FF3020',
                                   'leg': '#6A2018'})
    c.part('body', pivot=(0, 18, 0))
    c.box('body', (-3, -2.5, -4), (6, 4, 6), pattern='flesh')
    c.part('abdomen', 'body', pivot=(0, -1, 2), rot=(-0.2, 0, 0))
    c.box('abdomen', (-4, -4, 0), (8, 7, 9), mat='abdomen', pattern='flesh')
    c.part('head', 'body', pivot=(0, -0.5, -4))
    c.box('head', (-2.5, -2, -3), (5, 4, 3), faces={'north': 'face_spider'})
    for side in (-1, 1):
        c.box('head', ((-2 if side < 0 else 1), 1.5, -4), (1, 2, 1), mat='teeth', pattern='horn')
    for i, z in enumerate((-3, -1, 1, 3)):
        for side in (-1, 1):
            nm = f'leg_{i}_{"r" if side < 0 else "l"}'
            spread = (i - 1.5) * 0.35
            c.part(nm, 'body', pivot=(3 * side, -0.5, z - 1), rot=(0, spread * side, -0.6 * side))
            upper = (-8, -0.5, -0.5) if side < 0 else (0, -0.5, -0.5)
            c.box(nm, upper, (8, 1, 1), mat='leg', pattern='horn')
            low = nm + '_low'
            c.part(low, nm, pivot=(8 * side, 0, 0), rot=(0, 0, 1.5 * side))
            c.box(low, ((-9, -0.5, -0.5) if side < 0 else (0, -0.5, -0.5)), (9, 1, 1), mat='leg', pattern='horn')
            c.anim(nm, 'spider', 0.35, 0.6, i * 1.6 + (0 if side < 0 else 3.14))
    return c


def snapper(name, outer, inner, size):
    """Man Eater / Snatcher: a carnivorous plant head on a vine, its jaws snapping open and shut."""
    c = Creature(name, {'main': outer, 'inner': inner, 'dark': '#1A0A10', 'teeth': '#F0F0E0', 'vine': '#3A6A28', 'leaf': '#5AA03A'})
    h = size
    c.part('head', pivot=(0, 18, 0))
    c.part('upper', 'head', pivot=(0, 0, h / 2))
    c.box('upper', (-h / 2, -h / 2, -h), (h, h / 2, h), pattern='leaf', faces={'down': 'teeth_down'})
    c.part('lower', 'head', pivot=(0, 0, h / 2))
    c.box('lower', (-h / 2 + 0.5, 0, -h + 0.5), (h - 1, h / 2 - 1, h - 1), mat='inner', pattern='flesh', faces={'up': 'teeth_up'})
    c.anim('upper', 'jaw', -0.35, 0.35)
    c.anim('lower', 'jaw', 0.35, 0.35)
    for side in (-1, 1):   # leaves around the back of the head
        c.box('head', ((-h / 2 - 2) if side < 0 else h / 2, -2, h / 2 - 2), (2, 4, 3), mat='leaf', pattern='leaf')
    prev, z = 'head', h / 2
    for i in range(4):
        nm = f'vine_{i}'
        c.part(nm, prev, pivot=(0, 0, z), rot=(-0.3, 0, 0))
        c.box(nm, (-1, -1, 0), (2, 2, 5), mat='vine', pattern='leaf')
        c.anim(nm, 'wiggle', 0.2, 0.25, i * 0.8)
        prev, z = nm, 5
    c.anim('head', 'bob', 0.5, 0.2)
    return c


# ----------------------------------------------------------------------------------------- Meteorite
def meteor_head():
    """Meteor Head: a floating rock skull with glowing eyes, trailing flames."""
    c = Creature('meteor_head', {'main': '#6A5060', 'dark': '#1A0A10', 'eye': '#FFB030', 'teeth': '#3A2A30', 'glow': '#FFD060',
                                 'fire': '#FF6A20', 'accent': '#8A6A70'})
    c.part('head', pivot=(0, 18, 0))
    c.box('head', (-4, -4, -4), (8, 8, 8), pattern='rock', faces={'north': 'face_meteor'})
    c.box('head', (-3, -5, -2), (2, 1, 2), mat='accent', pattern='rock')
    c.box('head', (1, -5, 0), (2, 1, 3), mat='accent', pattern='rock')
    for i, (y, s, z) in enumerate(((-2, 6, 4), (-1, 5, 7), (0, 3, 10))):
        nm = f'flame_{i}'
        c.part(nm, 'head', pivot=(0, y, z))
        c.box(nm, (-s / 2, -s / 2, 0), (s, s, 4), mat='fire', pattern='fire')
        c.anim(nm, 'flicker', 0.15, 0.9, i * 1.3)
    c.anim('head', 'bob', 0.6, 0.2)
    return c


# ----------------------------------------------------------------------------------------- Hardmode (Stage 5)
def wings(c, parent, pivot_y, size, mat='wing', pattern='membrane', bat=False, z=1.5, amp=0.6, speed=0.6):
    w, h = size
    for side, nm in ((-1, 'wing_r'), (1, 'wing_l')):
        c.part(nm, parent, pivot=(1 * side, pivot_y, z), rot=(0, -0.35 * side, 0))
        origin = (-w, -h * 0.6, 0) if side < 0 else (0, -h * 0.6, 0)
        faces = None
        if bat:
            faces = {'north': 'batwing_r' if side < 0 else 'batwing_l', 'south': 'batwing_l' if side < 0 else 'batwing_r'}
        c.box(nm, origin, (w, h, 0), mat=mat, pattern=pattern, faces=faces)
        c.anim(nm, nm, amp, speed)


def pixie():
    """Pixie: a tiny glowing fairy with a pastel dress and two pairs of shimmering wings."""
    c = Creature('pixie', {'main': '#F8E0B0', 'dress': '#F070C0', 'hair': '#F8E060', 'wing': '#A8E8FF', 'dark': '#3A1A30',
                           'eye': '#FFFFFF', 'glow': '#FFF8C0'})
    c.part('body', pivot=(0, 14, 0))
    c.box('body', (-1.5, 0, -1), (3, 4, 2), mat='dress', pattern='cloth')
    c.box('body', (-2, 3, -1.5), (4, 2, 3), mat='dress', pattern='cloth')          # flared skirt
    c.part('head', 'body', pivot=(0, 0, 0))
    c.box('head', (-2, -4, -2), (4, 4, 4), faces={'north': 'face_cute'})
    c.box('head', (-2.3, -4.5, -1), (4.6, 2, 3.5), mat='hair', pattern='cloth')
    c.box('head', (-0.5, -5.5, 0), (1, 1, 1), mat='glow', pattern='glow')
    wings(c, 'body', 1, (6, 5), amp=0.9, speed=1.6)
    c.part('wing2_r', 'body', pivot=(-1, 2.5, 1.2), rot=(0, 0.3, 0.5))
    c.box('wing2_r', (-4, -1.5, 0), (4, 3, 0), mat='wing', pattern='membrane')
    c.anim('wing2_r', 'wing_r', 0.7, 1.6, 0.6)
    c.part('wing2_l', 'body', pivot=(1, 2.5, 1.2), rot=(0, -0.3, -0.5))
    c.box('wing2_l', (0, -1.5, 0), (4, 3, 0), mat='wing', pattern='membrane')
    c.anim('wing2_l', 'wing_l', 0.7, 1.6, 0.6)
    for side, nm in ((-1, 'arm_r'), (1, 'arm_l')):
        c.part(nm, 'body', pivot=(2 * side, 0.5, 0))
        c.box(nm, (-0.5, 0, -0.5), (1, 3, 1))
        c.anim(nm, 'dangle', 0.3, 0.4, 0 if side < 0 else 3.14)
    c.anim('body', 'bob', 1.0, 0.25)
    c.anim('head', 'head')
    return c


def unicorn():
    """Unicorn: a white horse with a pastel mane and tail and a golden spiral horn; it charges headlong."""
    c = Creature('unicorn', {'main': '#F4F2FA', 'mane': '#E890D8', 'mane2': '#90C8F8', 'horn': '#F0D070', 'hoof': '#9A90B0',
                             'dark': '#2A2038', 'eye': '#4A70D0', 'blaze': '#FFFFFF'})
    c.part('body', pivot=(0, 12, 0))
    c.box('body', (-4, -4, -8), (8, 7, 16))
    c.part('neck', 'body', pivot=(0, -2, -6.5), rot=(0.5, 0, 0))
    c.box('neck', (-2, -9, -2.5), (4, 10, 5))
    c.box('neck', (-1, -10, 1.5), (2, 10, 2), mat='mane', pattern='cloth')
    c.box('neck', (-1.01, -6, 1.6), (2, 5, 2), mat='mane2', pattern='cloth')
    c.part('head', 'neck', pivot=(0, -8, 0), rot=(-0.5, 0, 0))
    c.box('head', (-2.5, -3, -9), (5, 5, 9), faces={'north': 'face_horse'})
    for side in (-1, 1):
        c.box('head', ((-2.7 if side < 0 else 1.7), -2, -6), (1, 1, 1), mat='dark', pattern='skin')     # eyes on the sides
        c.box('head', ((-2 if side < 0 else 1), -5, -1), (1, 2, 1))                                      # ears
    c.part('horn', 'head', pivot=(0, -3, -5), rot=(0.55, 0, 0))
    c.box('horn', (-0.75, -3, -0.75), (1.5, 3, 1.5), mat='horn', pattern='bands')
    c.box('horn', (-0.5, -6, -0.5), (1, 3, 1), mat='horn', pattern='bands')
    c.part('tail', 'body', pivot=(0, -3, 8), rot=(0.6, 0, 0))
    c.box('tail', (-1.5, 0, -1), (3, 9, 2), mat='mane', pattern='cloth')
    c.box('tail', (-1, 4, -0.8), (2, 6, 2), mat='mane2', pattern='cloth')
    c.anim('tail', 'tail', 0.3, 0.2)
    for nm, x, z, role in (('leg_fr', -2.5, -6, 'leg_r'), ('leg_fl', 2.5, -6, 'leg_l'), ('leg_br', -2.5, 6, 'leg_l'), ('leg_bl', 2.5, 6, 'leg_r')):
        c.part(nm, pivot=(x, 15, z))
        c.box(nm, (-1.5, 0, -1.5), (3, 7, 3))
        c.box(nm, (-1.5, 7, -1.5), (3, 2, 3), mat='hoof', pattern='plate', inflate=0.1)
        c.anim(nm, role)
    c.anim('neck', 'head', 0.3)
    return c


def gastropod():
    """Gastropod: a floating pink snail of the Hallow with a spiral shell and stalk eyes; it fires pink lasers."""
    c = Creature('gastropod', {'main': '#F8B0D8', 'shell': '#C870D0', 'shell2': '#F8E0F8', 'dark': '#3A1030', 'eye': '#FFFFFF',
                               'glow': '#FF70C0'})
    c.part('body', pivot=(0, 15, 0))
    c.box('body', (-2.5, 0, -6), (5, 4, 12))
    c.part('shell', 'body', pivot=(0, 0, 1))
    c.box('shell', (-3.5, -7, -3.5), (7, 7, 7), mat='shell', pattern='bands')
    c.box('shell', (-3.6, -5.5, -2), (7.2, 4, 4), mat='shell2', pattern='bands')
    c.part('head', 'body', pivot=(0, 1, -6))
    c.box('head', (-2.5, -2, -3), (5, 5, 3), faces={'north': 'face_cute'})
    for side in (-1, 1):
        nm = 'stalk_r' if side < 0 else 'stalk_l'
        c.part(nm, 'head', pivot=(1.3 * side, -2, -2), rot=(-0.3, 0, 0.25 * side))
        c.box(nm, (-0.5, -4, -0.5), (1, 4, 1))
        c.box(nm, (-1, -5.5, -1), (2, 2, 2), mat='glow', pattern='glow')
        c.anim(nm, 'dangle', 0.2, 0.3, 0 if side < 0 else 2)
    c.anim('body', 'bob', 1.2, 0.15)
    return c


def hooded(name, mats, face='eyes', robe_len=12, wisps=False):
    """A robed, hooded figure (Chaos Elemental, Wraith): glowing eyes in the dark of the hood."""
    c = Creature(name, mats)
    c.part('body', pivot=(0, 4, 0))
    c.box('body', (-4, 0, -2), (8, 10, 4), pattern='cloth')
    c.box('body', (-4.5, 8, -2.5), (9, robe_len - 4, 5), pattern='cloth')
    if wisps:
        for i, x in enumerate((-3.5, -1, 1.5)):
            nm = f'wisp_{i}'
            c.part(nm, 'body', pivot=(x + 1, 8 + robe_len - 4, 0))
            c.box(nm, (-1, 0, -1.5), (2, 4, 3), pattern='cloth')
            c.anim(nm, 'dangle', 0.35, 0.3, i * 1.5)
    c.part('head', 'body', pivot=(0, 0, 0))
    c.box('head', (-3.5, -7, -3.5), (7, 7, 7), mat='dark', pattern='skin', faces={'north': face})
    c.box('head', (-4.5, -8.5, -4), (9, 9, 9), pattern='cloth', faces={'north': 'open', 'down': 'none'})
    c.box('head', (-1.5, -10, 2), (3, 3, 4), pattern='cloth')                       # hood point
    for side, nm in ((-1, 'arm_r'), (1, 'arm_l')):
        c.part(nm, 'body', pivot=(5 * side, 1.5, 0), rot=(-0.4, 0, 0.1 * -side))
        c.box(nm, (-1.5, -1, -1.5), (3, 9, 3), pattern='cloth')
        c.box(nm, (-1, 8, -1), (2, 2, 2), mat='hand', pattern='skin')
        c.anim(nm, 'dangle', 0.25, 0.3, 0 if side < 0 else 3.14)
    c.anim('body', 'bob', 1.0, 0.12)
    c.anim('head', 'head')
    return c


def chaos_elemental():
    return hooded('chaos_elemental', {'main': '#7A3AC8', 'dark': '#120820', 'glow': '#FF70FF', 'hand': '#C090F0', 'eye': '#FF70FF'})


def wraith():
    return hooded('wraith', {'main': '#2E2E3A', 'dark': '#040408', 'glow': '#D8F0FF', 'hand': '#8A8AA0', 'eye': '#D8F0FF'},
                  robe_len=10, wisps=True)


def corruptor():
    """Corruptor: a bloated purple flying head with a gaping green-ringed mouth, spikes and a short tail."""
    c = Creature('corruptor', {'main': '#6A5A80', 'dark': '#14081A', 'accent': '#90B848', 'teeth': '#D0E0A0', 'eye': '#C8F060',
                               'horn': '#3A2A48'})
    c.part('head', pivot=(0, 16, 0))
    c.box('head', (-5, -5, -5), (10, 10, 9), pattern='scale', faces={'north': 'face_eos'})
    for x, y in ((-3, -6), (2, -7), (-1, -7.5), (4, -4), (-5.5, -3)):
        c.box('head', (x, y, -1), (1.5, 2.5, 1.5), mat='horn', pattern='horn')
    prev, z = 'head', 4
    for i, size in enumerate((7, 5, 3)):
        nm = f'segment_{i}'
        c.part(nm, prev, pivot=(0, 0, z))
        c.box(nm, (-size / 2, -size / 2, 0), (size, size, 4), pattern='scale')
        c.anim(nm, 'wiggle', 0.25, 0.3, i * 0.9)
        prev, z = nm, 4
    c.anim('head', 'bob', 1.0, 0.15)
    return c


def slimer():
    """Slimer: a purple Corruption slime that sprouted bat wings."""
    c = Creature('slimer', {'main': '#8A62C0', 'dark': '#1A0A28', 'wing': '#4A3A5A', 'eye': '#F0F0FF'})
    c.part('body', pivot=(0, 15, 0))
    c.box('body', (-5, -4, -5), (10, 8, 10), pattern='glow', faces={'north': 'face_cute'})
    c.box('body', (-4, -5, -4), (8, 1, 8), pattern='glow')
    wings(c, 'body', -1, (10, 8), bat=True, z=0, amp=0.7, speed=0.6)
    c.anim('body', 'bob', 1.2, 0.2)
    return c


def herpling():
    """Herpling: a hopping lump of Crimson flesh that is nearly all mouth, on two stubby legs."""
    c = Creature('herpling', {'main': '#B83A44', 'dark': '#200406', 'teeth': '#F0E0C8', 'eye': '#FFE060', 'inner': '#701820'})
    c.part('body', pivot=(0, 18, 0))
    c.box('body', (-5, -8, -5), (10, 9, 10), pattern='flesh', faces={'north': 'face_maw'})
    for x in (-3, 1):
        c.box('body', (x, -10, -2), (2, 2, 2), mat='inner', pattern='flesh')
    for side, nm in ((-1, 'leg_r'), (1, 'leg_l')):
        c.part(nm, pivot=(2.5 * side, 19, 0))
        c.box(nm, (-1.5, 0, -1.5), (3, 5, 3), pattern='flesh')
        c.anim(nm, nm)
    c.anim('body', 'bob', 0.5, 0.3)
    return c


def floaty_gross():
    """Floaty Gross: a pale, drooling Crimson ghoul drifting through walls with long dangling arms."""
    c = Creature('floaty_gross', {'main': '#D8B0A8', 'dark': '#2A0808', 'teeth': '#F0E8C8', 'eye': '#FF3020', 'flesh': '#A84048'})
    c.part('body', pivot=(0, 6, 0), rot=(0.2, 0, 0))
    c.box('body', (-4, 0, -2.5), (8, 9, 5), pattern='flesh')
    c.box('body', (-3, 9, -2), (6, 5, 4), mat='flesh', pattern='flesh')
    c.box('body', (-2, 14, -1.5), (4, 4, 3), mat='flesh', pattern='flesh')
    c.part('head', 'body', pivot=(0, 0, -0.5))
    c.box('head', (-4, -8, -4), (8, 8, 8), pattern='flesh', faces={'north': 'face_fm'})
    for side, nm in ((-1, 'arm_r'), (1, 'arm_l')):
        c.part(nm, 'body', pivot=(5 * side, 1, 0), rot=(-0.2, 0, 0))
        c.box(nm, (-1, 0, -1), (2, 14, 2), pattern='flesh')
        c.anim(nm, 'dangle', 0.3, 0.25, 0 if side < 0 else 3.14)
    c.anim('body', 'bob', 1.2, 0.12)
    c.anim('head', 'head')
    return c


def possessed_armor():
    """Possessed Armor: an empty suit of dark plate walking on its own, red light glowing inside the helm."""
    c = Creature('possessed_armor', {'main': '#5A5A6A', 'trim': '#8A7A50', 'dark': '#080808', 'glow': '#FF3030', 'chain': '#3A3A46'})
    for side, nm in ((-1, 'leg_r'), (1, 'leg_l')):
        c.part(nm, pivot=(2 * side, 12, 0))
        c.box(nm, (-2, 0, -2), (4, 12, 4), mat='chain', pattern='chain')
        c.box(nm, (-2.3, 0, -2.4), (4.6, 5, 1), pattern='plate', edge='trim')
        c.box(nm, (-2, 9, -2), (4, 3, 4), pattern='plate', inflate=0.3)
        c.anim(nm, nm)
    c.part('body', pivot=(0, 0, 0))
    c.box('body', (-4, 0, -2), (8, 12, 4), mat='chain', pattern='chain')
    c.box('body', (-4.3, 0, -2.6), (8.6, 7, 1), pattern='plate', edge='trim')
    c.box('body', (-4, 10, -2), (8, 2, 4), mat='trim', pattern='belt', inflate=0.3)
    c.part('head', 'body', pivot=(0, 0, 0))
    c.box('head', (-4, -8, -4), (8, 8, 8), pattern='plate', edge='trim', faces={'north': 'eyes'})
    c.box('head', (-0.5, -10, -4), (1, 2, 8), mat='trim', pattern='plate')
    for side, nm in ((-1, 'arm_r'), (1, 'arm_l')):
        c.part(nm, 'body', pivot=(5 * side, 2, 0))
        c.box(nm, (-1, -2, -2), (4, 12, 4) if side > 0 else (4, 12, 4), mat='chain', pattern='chain')
        c.box(nm, (-1.5, -3, -2.5), (5, 3, 5), pattern='plate', edge='trim')
        c.anim(nm, nm)
    c.anim('head', 'head')
    return c


def werewolf():
    """Werewolf: a hunched grey-brown wolf-man with a long snout, pointed ears, claws and a bushy tail."""
    c = Creature('werewolf', {'main': '#6A5848', 'dark': '#140C08', 'eye': '#FFD030', 'teeth': '#F0E8D8', 'belly': '#9A8A70',
                              'horn': '#2A2018', 'cloth': '#4A3A5A'})
    for side, nm in ((-1, 'leg_r'), (1, 'leg_l')):
        c.part(nm, pivot=(2.2 * side, 12, 1))
        c.box(nm, (-2, 0, -2), (4, 7, 4))
        c.box(nm, (-1.5, 6, -0.5), (3, 5, 3))
        c.box(nm, (-1.5, 10, -3), (3, 2, 4))
        c.anim(nm, nm)
    c.part('body', pivot=(0, 12, 0), rot=(0.45, 0, 0))
    c.box('body', (-4.5, -12, -2.5), (9, 12, 5))
    c.box('body', (-3, -10, -2.9), (6, 8, 1), mat='belly', pattern='skin')
    c.box('body', (-4.5, -2, -2.5), (9, 3, 5), mat='cloth', pattern='cloth', inflate=0.3)        # torn trousers
    c.part('head', 'body', pivot=(0, -12, -1), rot=(-0.45, 0, 0))
    c.box('head', (-3.5, -6, -4), (7, 6, 6), faces={'north': 'face_demon'})
    c.box('head', (-2, -3, -8), (4, 3, 4))                                           # snout
    c.box('head', (-1, -3.5, -8.3), (2, 1, 1), mat='dark', pattern='skin')           # nose
    for side in (-1, 1):
        c.box('head', ((-3 if side < 0 else 1.5), -8.5, -1), (1.5, 3, 1.5), mat='main')   # ears
        c.box('head', ((-1.6 if side < 0 else 1), -0.5, -8), (0.6, 1, 0.6), mat='teeth', pattern='horn')
    for side, nm in ((-1, 'arm_r'), (1, 'arm_l')):
        c.part(nm, 'body', pivot=(5.5 * side, -11, 0), rot=(-0.6, 0, 0))
        c.box(nm, (-1.5, 0, -1.5), (3, 11, 3))
        c.box(nm, (-1.5, 11, -2), (3, 2, 1), mat='horn', pattern='horn')
        c.anim(nm, nm)
    c.part('tail', 'body', pivot=(0, -1, 2.5), rot=(0.5, 0, 0))
    c.box('tail', (-1.5, 0, 0), (3, 3, 7))
    c.anim('tail', 'tail', 0.35, 0.2)
    c.anim('head', 'head')
    return c


def mimic():
    """Mimic: a gold chest that grew teeth, hopping after you with its lid snapping."""
    c = Creature('mimic', {'main': '#8A5A2A', 'trim': '#E0B040', 'dark': '#1A0806', 'teeth': '#F8F0E0', 'inner': '#A02030',
                           'eye': '#FFE060'})
    c.part('chest', pivot=(0, 24, 0))
    c.part('lower', 'chest', pivot=(0, -8, 6))
    c.box('lower', (-7, 0, -13), (14, 8, 13), pattern='wood', edge='trim', faces={'up': 'teeth_up'})
    c.box('lower', (-7.3, 0, -13.3), (1, 8, 1), mat='trim', pattern='plate')
    c.box('lower', (6.3, 0, -13.3), (1, 8, 1), mat='trim', pattern='plate')
    c.part('upper', 'chest', pivot=(0, -8, 6))
    c.box('upper', (-7, -5, -13), (14, 5, 13), pattern='wood', edge='trim', faces={'down': 'teeth_down'})
    c.box('upper', (-1, -3, -13.6), (2, 3, 1), mat='trim', pattern='gem')      # the lock
    c.anim('upper', 'jaw', -0.5, 0.35)
    for side, nm in ((-1, 'leg_r'), (1, 'leg_l')):
        c.part(nm, 'chest', pivot=(4 * side, -1, -0.5))
        c.box(nm, (-1, -0.5, -1), (2, 1.5, 2), mat='inner', pattern='flesh')
    return c


def hm_creatures():
    return [pixie(), unicorn(), gastropod(), chaos_elemental(), corruptor(), slimer(), herpling(), floaty_gross(), wraith(),
            possessed_armor(), werewolf(), mimic()]


def all_creatures():
    return [imp(), demon(), demon('voodoo_demon', voodoo=True), eater_of_souls(), crimera(), face_monster(), blood_crawler(),
            snapper('man_eater', '#4A9A30', '#E070A0', 9), snapper('snatcher', '#5AAA3A', '#F0A0C0', 7), meteor_head()] + hm_creatures()


def write(assets, tex):
    for creature in all_creatures():
        width, height = am.pack(creature.cubes, 128)
        c = Canvas(width, height)
        rnd = random.Random(creature.name)
        for cube in creature.cubes:
            am.paint(creature, c, cube, rnd)
        c.save(os.path.join(tex, 'entity/model', creature.name + '.png'))
        parts = []
        for name, parent, pivot, rot in creature.parts:
            cubes = []
            for cube in creature.cubes:
                if cube.part == name:
                    entry = {'origin': cube.origin, 'size': cube.size, 'uv': list(cube.uv)}
                    if cube.inflate:
                        entry['inflate'] = cube.inflate
                    cubes.append(entry)
            parts.append({'name': name, 'parent': parent, 'pivot': pivot, 'rotation': rot, 'cubes': cubes})
        data = {'texture_width': width, 'texture_height': height, 'parts': parts, 'anims': creature.anims}
        path = os.path.join(assets, 'models/creature', creature.name + '.json')
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, 'w') as f:
            json.dump(data, f, separators=(',', ':'))


def names():
    return [c.name for c in all_creatures()]


if __name__ == '__main__':
    root = os.path.join(os.path.dirname(__file__), '..', 'src/main/resources/assets/terracraft')
    write(root, os.path.join(root, 'textures'))
