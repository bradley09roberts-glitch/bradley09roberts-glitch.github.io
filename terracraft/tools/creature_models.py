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
        self.variants = {}   # texture suffix -> material overrides (same model, e.g. Plantera's open mouth)

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


def snapper(name, outer, inner, size, vines=4):
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
    for i in range(vines):
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


# ----------------------------------------------------------------------------------------- Skeletron Prime's arms
def prime_arm(name, tool):
    """An arm of Skeletron Prime: a jointed steel limb ending in its weapon."""
    c = Creature(name, {'main': '#9A9AA8', 'dark': '#16161C', 'accent': '#6A6A78', 'glow': '#FF3030', 'blade': '#D8D8E0',
                        'teeth': '#E8E8F0', 'inner': '#3A3A44', 'fire': '#FF7030'})
    c.part('arm', pivot=(0, 14, 0))
    c.box('arm', (-1.5, -8, -1.5), (3, 8, 3), pattern='plate')
    c.box('arm', (-2, -1, -2), (4, 2, 4), mat='accent', pattern='bands')          # elbow joint
    c.part('tool', 'arm', pivot=(0, 0, 0))
    if tool == 'cannon':
        c.box('tool', (-2.5, 0, -2.5), (5, 8, 5), pattern='plate')
        c.box('tool', (-1.5, 7, -1.5), (3, 2, 3), mat='dark', pattern='skin')
        c.box('tool', (-3, 2, -3), (6, 1, 6), mat='accent', pattern='bands')
    elif tool == 'laser':
        c.box('tool', (-2, 0, -2), (4, 7, 4), pattern='plate')
        c.box('tool', (-1, 7, -1), (2, 3, 2), mat='glow', pattern='glow')
        c.box('tool', (-2.5, 4, -2.5), (5, 1, 5), mat='accent', pattern='bands')
    elif tool == 'saw':
        c.box('tool', (-1, 0, -1), (2, 4, 2), pattern='plate')
        c.part('blade', 'tool', pivot=(0, 8, 0))
        c.box('blade', (-0.5, -4.5, -4.5), (1, 9, 9), mat='blade', pattern='blade')
        c.box('blade', (-1, -1, -1), (2, 2, 2), mat='dark', pattern='skin')
        c.anim('blade', 'flicker', 0.05, 2.0)
        c.anim('tool', 'wiggle', 0.3, 1.5)
    else:   # vice: two clamping jaws
        c.box('tool', (-2.5, 0, -1.5), (5, 3, 3), pattern='plate')
        for side, nm in ((-1, 'jaw_r'), (1, 'jaw_l')):
            c.part(nm, 'tool', pivot=(1.5 * side, 3, 0))
            c.box(nm, (-0.75, 0, -1.5), (1.5, 6, 3), mat='accent', pattern='plate')
            c.box(nm, ((0 if side < 0 else -1), 5, -1.5), (1, 1, 3), mat='teeth', pattern='horn')
            c.anim(nm, 'jaw', 0.4 * side, 0.3)
    c.anim('arm', 'bob', 0.5, 0.15)
    return c


def mech_creatures():
    return [prime_arm('prime_cannon', 'cannon'), prime_arm('prime_saw', 'saw'), prime_arm('prime_vice', 'vice'), prime_arm('prime_laser', 'laser')]


# ----------------------------------------------------------------------------------------- Pirate Invasion
def parrot():
    """Parrot: a bright red macaw with blue and yellow wings, a hooked beak and a long tail; it swoops at the player."""
    c = Creature('parrot', {'main': '#D8302A', 'wing': '#2A6AD8', 'yellow': '#F0C830', 'beak': '#E8E0C8', 'dark': '#1A1A1A',
                            'tail': '#2A5AC0', 'green': '#3AA040'})
    c.part('body', pivot=(0, 16, 0), rot=(0.4, 0, 0))
    c.box('body', (-2, -3, -2.5), (4, 5, 5), pattern='feather')
    c.part('head', 'body', pivot=(0, -3, -1), rot=(-0.4, 0, 0))
    c.box('head', (-1.5, -3, -2), (3, 3, 3), pattern='feather')
    c.box('head', (-0.5, -2, -3.5), (1, 2, 2), mat='beak', pattern='horn')
    for side in (-1, 1):
        c.box('head', ((-1.6 if side < 0 else 1.1), -2.2, -1.4), (0.5, 0.8, 0.8), mat='dark', pattern='skin')
    wings(c, 'body', -2, (6, 4), mat='wing', pattern='feather', z=0.5, amp=0.9, speed=1.4)
    c.box('wing_r', (-6, -1.4, 0), (3, 2, 0), mat='yellow', pattern='feather')
    c.box('wing_l', (3, -1.4, 0), (3, 2, 0), mat='yellow', pattern='feather')
    c.part('tail', 'body', pivot=(0, 2, 2), rot=(0.7, 0, 0))
    c.box('tail', (-1, 0, -0.5), (2, 7, 1), mat='tail', pattern='feather')
    c.anim('tail', 'tail', 0.2, 0.4)
    c.anim('body', 'bob', 0.6, 0.3)
    c.anim('head', 'head')
    return c


def flying_dutchman():
    """The Flying Dutchman: a ghostly galleon of rotten green-black wood with tattered glowing sails, a raised stern
    castle, two masts with a skull flag and four cannons poking out of its sides. Long axis along z (it sails forward)."""
    c = Creature('flying_dutchman', {'main': '#2E3E38', 'dark': '#121A18', 'deck': '#4A5A48', 'sail': '#A8D8B8', 'glow': '#70FFC0',
                                     'metal': '#3A3A40', 'trim': '#8A7A40', 'flag': '#1A1A1A', 'bone': '#E8E0C8'})
    c.part('hull', pivot=(0, 24, 0))
    for z in (-33, -11, 11):
        c.box('hull', (-10, -14, z), (20, 14, 22), pattern='wood', edge='trim')
    c.box('hull', (-7, -12, -41), (14, 11, 8), pattern='wood', edge='trim')        # bow
    c.box('hull', (-3, -10, -47), (6, 7, 6), pattern='wood')                       # prow
    c.box('hull', (-1, -14, -52), (2, 2, 9), mat='trim', pattern='wood')           # bowsprit
    c.box('hull', (-10, -24, 22), (20, 10, 11), pattern='wood', edge='trim')       # stern castle
    for x in (-10, 9):
        c.box('hull', (x, -16, -33), (1, 2, 55), mat='deck', pattern='wood')       # side rails
    for z, x in ((-18, -14), (-18, 10), (14, -14), (14, 10)):
        c.box('hull', (x, -9, z), (4, 3, 3), mat='metal', pattern='plate')        # cannons
    for z in (-24, -6, 6):
        c.box('hull', (-10.5, -8, z), (21, 2, 2), mat='glow', pattern='glow')     # ghostly portholes
    c.box('hull', (-3, -22, 32.5), (6, 4, 1), mat='glow', pattern='glow')          # stern lanterns
    for name, z, height, sail_w, sail_h in (('mast_front', -14, 44, 30, 24), ('mast_back', 10, 38, 26, 20)):
        c.part(name, 'hull', pivot=(0, -14, z))
        c.box(name, (-1, -height, -1), (2, height, 2), mat='main', pattern='wood')
        c.box(name, (-sail_w / 2 - 1, -height + 6, -0.5), (sail_w + 2, 1, 1), mat='main', pattern='wood')   # yard
        sail = name + '_sail'
        c.part(sail, name, pivot=(0, -height + 7, 0.5))
        c.box(sail, (-sail_w / 2, 0, 0), (sail_w, sail_h, 0), mat='sail', pattern='membrane')
        c.anim(sail, 'wiggle', 0.06, 0.5)
    c.part('flag', 'mast_front', pivot=(0, -44, 0))
    c.box('flag', (0, -6, 0), (9, 6, 0), mat='flag', pattern='cloth')
    c.box('flag', (3, -4.5, -0.1), (2, 2, 0.2), mat='bone', pattern='bone')
    c.anim('flag', 'wiggle', 0.25, 1.2)
    c.anim('hull', 'bob', 0.6, 0.1)
    return c


# ----------------------------------------------------------------------------------------- Frost Legion
def snowman(name, hat, prop):
    """Frost Legion snowman: three stacked snowballs with coal eyes, a carrot nose, a scarf and stick arms, plus a hat
    and a weapon: Mister Stabby holds a knife, Snowman Gangsta wears a fedora and shades and carries a tommy gun,
    Snow Balla wears a beanie and holds a snowball."""
    c = Creature(name, {'main': '#F4F8FF', 'shade': '#C8D8F0', 'dark': '#1A1A20', 'carrot': '#F08A2A', 'stick': '#6A4A2A',
                        'scarf': '#C83030' if name != 'snow_balla' else '#3070C8', 'hat': '#2A2A30' if hat != 'beanie' else '#3070C8',
                        'band': '#C8A040', 'blade': '#D8D8E0', 'metal': '#3A3A44', 'wood': '#7A5A3A'})
    c.part('body', pivot=(0, 24, 0))
    c.box('body', (-5, -9, -5), (10, 9, 10), pattern='skin')                     # base ball
    c.part('torso', 'body', pivot=(0, -9, 0))
    c.box('torso', (-4, -8, -4), (8, 8, 8), pattern='skin')
    for y in (-6, -3):
        c.box('torso', (-0.5, y, -4.4), (1, 1, 0.5), mat='dark', pattern='skin')   # coal buttons
    c.box('torso', (-4.3, -1.5, -4.3), (8.6, 2, 8.6), mat='scarf', pattern='cloth')
    c.box('torso', (2, -0.5, -4.6), (2, 5, 1), mat='scarf', pattern='cloth')       # scarf tail
    c.part('head', 'torso', pivot=(0, -8, 0))
    c.box('head', (-3.5, -7, -3.5), (7, 7, 7), pattern='skin')
    for side in (-1, 1):
        c.box('head', ((-2.5 if side < 0 else 1.5), -5, -3.8), (1, 1, 0.5), mat='dark', pattern='skin')
    c.box('head', (-0.5, -4, -6), (1, 1, 3), mat='carrot', pattern='skin')
    for x in (-1.5, -0.5, 0.5):
        c.box('head', (x, -2, -3.7), (0.8, 0.8, 0.3), mat='dark', pattern='skin')
    if hat == 'fedora':
        c.box('head', (-5, -7.5, -5), (10, 1, 10), mat='hat', pattern='cloth')
        c.box('head', (-3.5, -10.5, -3.5), (7, 3, 7), mat='hat', pattern='cloth')
        c.box('head', (-3.6, -8.5, -3.6), (7.2, 1, 7.2), mat='band', pattern='cloth')
        c.box('head', (-3, -5.5, -4), (6, 1.5, 0.6), mat='dark', pattern='gem')    # sunglasses
    elif hat == 'beanie':
        c.box('head', (-3.8, -9, -3.8), (7.6, 3, 7.6), mat='hat', pattern='cloth')
        c.box('head', (-1, -10.5, -1), (2, 2, 2), mat='main', pattern='cloth')     # pompom
    else:   # Mister Stabby: a battered bucket
        c.box('head', (-3, -10, -3), (6, 4, 6), mat='metal', pattern='plate')
    for side, nm in ((-1, 'arm_r'), (1, 'arm_l')):
        c.part(nm, 'torso', pivot=(4 * side, -5, 0), rot=(0, 0, 0.6 * side))
        c.box(nm, ((-6 if side < 0 else 0), -0.5, -0.5), (6, 1, 1), mat='stick', pattern='wood')
        c.anim(nm, nm, 0.6)
    if prop == 'knife':
        c.box('arm_r', (-7, -0.5, -4), (1, 1, 4), mat='blade', pattern='blade')
    elif prop == 'gun':
        c.box('arm_r', (-8, -1, -6), (2, 2, 9), mat='metal', pattern='plate')
        c.box('arm_r', (-8, 1, -3), (2, 3, 2), mat='wood', pattern='wood')
        c.box('arm_r', (-8.2, 0.5, -1), (2.4, 2.4, 2.4), mat='metal', pattern='bands')     # drum magazine
    else:
        c.box('arm_r', (-8, -1.5, -1.5), (3, 3, 3), pattern='skin')                    # snowball
    c.anim('body', 'bob', 0.6, 0.35)
    c.anim('head', 'head')
    return c


def frost_creatures():
    return [snowman('mister_stabby', 'bucket', 'knife'), snowman('snowman_gangsta', 'fedora', 'gun'), snowman('snow_balla', 'beanie', 'snowball')]


def pirate_creatures():
    return [parrot(), flying_dutchman()]


# ----------------------------------------------------------------------------------------- Plantera (Stage 6)
def plantera():
    """Plantera: a huge pink flower bud with yellow-green spots, a ring of jungle leaves around its back and a thick
    vine stem. The front is a closed bud of four petals; the 'mouth' texture (second phase) shows the petals parted
    over a dark maw full of teeth."""
    c = Creature('plantera', {'main': '#E0508A', 'dark': '#7A1A40', 'spot': '#C8E050', 'leaf': '#3A9A30', 'vine': '#2E6A22',
                              'petal': '#F070A8', 'inner': '#E0508A', 'teeth': '#F0E8C8'})
    c.variants['mouth'] = {'inner': '#2A0610', 'petal': '#D84080'}
    c.part('head', pivot=(0, 12, 0))
    c.box('head', (-8, -8, -6), (16, 16, 14), pattern='skin', edge='dark')
    c.box('head', (-6, -6, -7), (12, 12, 1), mat='inner', pattern='flesh', faces={'north': 'teeth_down'})   # the mouth
    for (x, y) in ((-8.6, -3), (8, 2), (-4, -8.6), (3, 7.6), (-8.6, 4), (8, -5)):
        w = (1, 3, 3) if abs(x) > 8 else (3, 1, 3)
        c.box('head', (x, y, -1), w, mat='spot', pattern='gem')
    for nm, piv, size, origin, rot in (
            ('petal_top', (0, -7, -6), (14, 2, 6), (-7, 0, -6), (0.15, 0, 0)),
            ('petal_bottom', (0, 7, -6), (14, 2, 6), (-7, -2, -6), (-0.15, 0, 0)),
            ('petal_left', (-7, 0, -6), (2, 12, 6), (0, -6, -6), (0, -0.15, 0)),
            ('petal_right', (7, 0, -6), (2, 12, 6), (-2, -6, -6), (0, 0.15, 0))):
        c.part(nm, 'head', pivot=piv, rot=rot)
        c.box(nm, origin, size, mat='petal', pattern='skin', edge='dark')
    c.anim('petal_top', 'jaw', -0.25, 0.3)
    c.anim('petal_bottom', 'jaw', 0.25, 0.3)
    c.anim('petal_left', 'wiggle', 0.15, 0.3)
    c.anim('petal_right', 'wiggle', -0.15, 0.3, 3.14)
    for i, (x, y, rz) in enumerate(((-10, -4, 0.5), (10, -4, -0.5), (-9, 6, -0.4), (9, 6, 0.4), (0, -11, 0.0), (0, 11, 0.0))):
        nm = f'leaf_{i}'
        c.part(nm, 'head', pivot=(x * 0.6, y * 0.6, 6), rot=(0.5 if y < 0 else -0.5, 0, rz))
        c.box(nm, (-3, -1 if abs(x) < 1 else -3, 0), (6 if abs(x) < 1 else 6, 2 if abs(x) < 1 else 6, 7), mat='leaf', pattern='leaf')
        c.anim(nm, 'dangle', 0.08, 0.2, i)
    c.box('head', (-2.5, -2.5, 8), (5, 5, 6), mat='vine', pattern='leaf')
    c.anim('head', 'head')
    return c


def plantera_hook():
    """Plantera's Hook: a green thorny grabbing claw at the end of its vine."""
    c = Creature('plantera_hook', {'main': '#3A8A2A', 'dark': '#1A3A12', 'claw': '#C8D888', 'vine': '#2E6A22'})
    c.part('body', pivot=(0, 17, 0))
    c.box('body', (-3.5, -3.5, -2), (7, 7, 7), pattern='leaf', edge='dark')
    for i, (x, y) in enumerate(((-2.5, -2.5), (2.5, -2.5), (0, 3))):
        nm = f'claw_{i}'
        c.part(nm, 'body', pivot=(x, y, -2), rot=(0.25 * (1 if y < 0 else -1), 0.25 * (-1 if x < 0 else 1 if x > 0 else 0), 0))
        c.box(nm, (-1, -1, -6), (2, 2, 6), mat='claw', pattern='horn')
    c.box('body', (-1.5, -1.5, 5), (3, 3, 3), mat='vine', pattern='leaf')
    return c


def derpling():
    """Derpling: a fat blue jungle bug that bounces about, with huge goofy eyes and stubby legs."""
    c = Creature('derpling', {'main': '#3A70D8', 'dark': '#16285A', 'shell': '#5A90F0', 'eye': '#F8F8F0', 'pupil': '#101018', 'leg': '#24448A'})
    c.part('body', pivot=(0, 24, 0))
    c.box('body', (-7, -10, -6), (14, 8, 13), mat='shell', pattern='plate', edge='dark')
    c.box('body', (-6, -4, -5), (12, 2, 11), mat='main', pattern='skin')
    c.part('head', 'body', pivot=(0, -6, -6))
    c.box('head', (-4.5, -3, -4), (9, 6, 4), pattern='skin', edge='dark')
    for x in (-4, 1):
        c.box('head', (x, -5.5, -4.5), (3, 3, 3), mat='eye', pattern='skin')
        c.box('head', (x + (0.5 if x < 0 else 1.5), -4.5, -4.8), (1, 1, 0.5), mat='pupil', pattern='skin')
    c.anim('head', 'head')
    for i, z in enumerate((-3, 1, 5)):
        for side, nm in ((-1, 'leg_r'), (1, 'leg_l')):
            part = f'{nm}_{i}'
            c.part(part, 'body', pivot=(side * 6, -3, z), rot=(0, 0, side * 0.5))
            c.box(part, ((-1 if side > 0 else -1), 0, -0.5), (2, 5, 1), mat='leg', pattern='skin')
            c.anim(part, nm, 0.6)
    c.anim('body', 'bob', 0.4, 0.3)
    return c


def jungle_hm_creatures():
    return [plantera(), plantera_hook(), snapper('plantera_tentacle', '#E0508A', '#2A0610', 8, vines=0),
            snapper('angry_trapper', '#2E7A22', '#D04060', 12), derpling()]


# ----------------------------------------------------------------------------------------- Lihzahrd Temple (Stage 6)
def lihzahrd():
    """Lihzahrd: an upright temple lizard, rust-brown scales with a sandy belly, a long snout, frills, a heavy tail."""
    c = Creature('lihzahrd', {'main': '#9A5A26', 'belly': '#D8A860', 'dark': '#4A2810', 'eye': '#F8D830', 'glow': '#F8D830', 'claw': '#E8E0C8', 'frill': '#C83A20'})
    c.part('body', pivot=(0, 11, 0))
    c.box('body', (-4, -11, -2.5), (8, 11, 5), pattern='scale', edge='dark')
    c.box('body', (-3, -10, -3), (6, 9, 1), mat='belly', pattern='skin')
    c.part('head', 'body', pivot=(0, -11, 0))
    c.box('head', (-3.5, -6, -4), (7, 6, 6), pattern='scale', edge='dark')
    c.box('head', (-2.5, -4, -9), (5, 3.5, 5), pattern='scale')                      # snout
    c.box('head', (-2.5, -0.5, -8.5), (5, 1, 4.5), mat='belly', pattern='skin')         # jaw
    for x in (-3.6, 2.6):
        c.box('head', (x, -5, -3.5), (1, 1, 1), mat='eye', pattern='glow')
    c.box('head', (-0.5, -9, -2), (1, 3, 6), mat='frill', pattern='scale')             # crest
    c.anim('head', 'head')
    for side, nm in ((-1, 'arm_r'), (1, 'arm_l')):
        c.part(nm, 'body', pivot=(side * 5, -10, 0))
        c.box(nm, ((-2 if side < 0 else 0), -1, -1.5), (2, 9, 3), pattern='scale')
        c.box(nm, ((-2 if side < 0 else 0), 8, -2.5), (2, 1, 2), mat='claw', pattern='horn')
        c.anim(nm, nm, 0.8)
    for side, nm in ((-1, 'leg_r'), (1, 'leg_l')):
        c.part(nm, 'body', pivot=(side * 2, 0, 0))
        c.box(nm, (-1.5, 0, -1.5), (3, 13, 3), pattern='scale')
        c.box(nm, (-1.5, 11, -4), (3, 2, 3), mat='claw', pattern='horn')
        c.anim(nm, nm, 1.0)
    c.part('tail', 'body', pivot=(0, -2, 2.5), rot=(0.6, 0, 0))
    c.box('tail', (-1.5, -1.5, 0), (3, 3, 8), pattern='scale')
    c.part('tail_tip', 'tail', pivot=(0, 0, 8), rot=(0.3, 0, 0))
    c.box('tail_tip', (-1, -1, 0), (2, 2, 6), pattern='scale')
    c.anim('tail', 'tail', 0.3, 0.3)
    c.anim('tail_tip', 'tail', 0.3, 0.3, 1.0)
    return c


def flying_snake():
    """Flying Snake: a long coiling temple serpent with a frilled head and four little feathered wings."""
    c = Creature('flying_snake', {'main': '#C88A2E', 'dark': '#5A3410', 'belly': '#F0D080', 'eye': '#E83A10', 'glow': '#E83A10', 'wing': '#E8C060', 'frill': '#3AA060'})
    c.part('head', pivot=(0, 17, -10))
    c.box('head', (-3, -3, -5), (6, 5, 6), pattern='scale', edge='dark')
    c.box('head', (-2, -1, -8), (4, 3, 3), pattern='scale')
    for x in (-3.4, 2.4):
        c.box('head', (x, -2, -4), (1, 1, 1), mat='eye', pattern='glow')
    c.box('head', (-4, -5, -1), (8, 3, 1), mat='frill', pattern='feather')
    prev, z = 'head', 1
    for i in range(5):
        nm = f'seg_{i}'
        w = 4.5 - i * 0.6
        c.part(nm, prev, pivot=(0, 0, z))
        c.box(nm, (-w / 2, -w / 2, 0), (w, w, 5), pattern='scale')
        c.box(nm, (-w / 2 + 0.5, w / 2 - 0.5, 0.5), (w - 1, 0.6, 4), mat='belly', pattern='skin')
        c.anim(nm, 'wiggle', 0.3, 0.35, i * 0.9)
        if i in (0, 2):
            for side, wn in ((-1, 'wing_r'), (1, 'wing_l')):
                part = f'{wn}_{i}'
                c.part(part, nm, pivot=(side * w / 2, -w / 2, 2.5))
                c.box(part, ((-7 if side < 0 else 0), 0, -2), (7, 0, 4), mat='wing', pattern='feather')
                c.anim(part, wn, 0.6, 0.8, i)
        prev, z = nm, 5
    c.anim('head', 'bob', 0.6, 0.25)
    return c


def golem():
    """Golem's body: a squat idol of temple stone with a golden sun set in its chest, stubby legs and shoulder
    sockets for the chained fists. The head and fists are their own creatures."""
    c = Creature('golem', {'main': '#C8884A', 'dark': '#6A3A18', 'trim': '#E8C080', 'sun': '#F8C838', 'glow': '#D8A868', 'core': '#FFF4B0', 'socket': '#3A2010'})
    c.part('body', pivot=(0, 24, 0))
    c.box('body', (-24, -50, -14), (48, 38, 28), pattern='rock', edge='dark')
    c.box('body', (-26, -52, -15), (52, 6, 30), mat='trim', pattern='plate', edge='dark')      # shoulders
    c.box('body', (-9, -40, -15.5), (18, 18, 2), mat='sun', pattern='gem')                    # sun disc
    c.box('body', (-5, -36, -16), (10, 10, 1), mat='core', pattern='gem')
    for i in range(8):                                                                            # sun rays
        a = i * 3.14159 / 4
        import math as _m
        x, y = _m.cos(a) * 12, _m.sin(a) * 12
        c.box('body', (x - 1.5, -31 + y - 1.5, -15), (3, 3, 1), mat='sun', pattern='gem')
    for side in (-1, 1):
        c.box('body', (side * 26 - (4 if side > 0 else 0) + (0 if side > 0 else 0), -46, -4), (4, 8, 8), mat='socket', pattern='rock')
    for side, nm in ((-1, 'leg_r'), (1, 'leg_l')):
        c.part(nm, 'body', pivot=(side * 13, -12, 0))
        c.box(nm, (-7, 0, -8), (14, 12, 16), pattern='rock', edge='dark')
        c.anim(nm, nm, 0.3)
    return c


def golem_head():
    """Golem's head: a blocky stone idol face with a heavy brow, two glowing eyes and a slot mouth. When it breaks
    free (texture 'free') the eyes burn red-hot."""
    c = Creature('golem_head', {'main': '#C8884A', 'dark': '#6A3A18', 'trim': '#E8C080', 'eye': '#F8E870', 'glow': '#D8A868', 'mouth': '#3A2010'})
    c.variants['free'] = {'eye': '#FF3A10', 'mouth': '#FF8A20'}
    c.part('head', pivot=(0, 24, 0))
    c.box('head', (-16, -30, -14), (32, 30, 28), pattern='rock', edge='dark')
    c.box('head', (-17, -24, -15), (34, 4, 4), mat='trim', pattern='plate', edge='dark')     # brow
    for x in (-12, 4):
        c.box('head', (x, -19, -14.6), (8, 6, 1), mat='eye', pattern='gem')
    c.box('head', (-9, -9, -14.6), (18, 3, 1), mat='mouth', pattern='gem')
    c.box('head', (-6, -36, -6), (12, 6, 12), mat='trim', pattern='plate', edge='dark')       # crown block
    c.anim('head', 'head')
    return c


def golem_fist():
    """Golem's Fist: a great stone fist with knuckle ridges."""
    c = Creature('golem_fist', {'main': '#C8884A', 'dark': '#6A3A18', 'trim': '#E8C080', 'glow': '#D8A868'})
    c.part('fist', pivot=(0, 16, 0))
    c.box('fist', (-8, -8, -7), (16, 16, 14), pattern='rock', edge='dark')
    for y in (-7, -3, 1, 5):
        c.box('fist', (-8.5, y, -8), (17, 3, 2), mat='trim', pattern='plate')                 # knuckles
    c.box('fist', (-10, -6, -2), (3, 8, 6), pattern='rock')                                      # thumb
    c.box('fist', (-5, -5, 7), (10, 10, 4), mat='dark', pattern='rock')                          # wrist
    return c


def temple_creatures():
    return [lihzahrd(), flying_snake(), golem(), golem_head(), golem_fist()]


# ----------------------------------------------------------------------------------------- Stage 7
def truffle_worm():
    """Truffle Worm: a plump glowing-blue mushroom worm with a little cap."""
    c = Creature('truffle_worm', {'main': '#7AA8E8', 'dark': '#2A4A8A', 'cap': '#3A70D0', 'glow': '#A8E0FF'})
    c.part('body', pivot=(0, 24, 0))
    for i in range(4):
        c.box('body', (-1.5, -3, -4 + i * 2.2), (3, 3, 2.4), pattern='skin', edge='dark')
    c.box('body', (-2.5, -5, -5), (5, 2, 3), mat='cap', pattern='glow')
    c.anim('body', 'wiggle', 0.2, 0.6)
    return c


def duke_fishron():
    """Duke Fishron: a green pig-shark dragon - a fat finned body, a snout with tusks, a dorsal fin, small bat wings
    and a forked tail. 'rage' texture: red eyes and a darker hide."""
    c = Creature('duke_fishron', {'main': '#5AA08A', 'dark': '#2A5A4A', 'belly': '#C8D8B0', 'fin': '#3A7A68', 'eye': '#F8E070', 'glow': '#F8E070',
                                  'tusk': '#F0E8D0', 'wing': '#4A8A78'})
    c.variants['rage'] = {'eye': '#FF3020', 'glow': '#FF3020', 'main': '#4A8A74'}
    c.part('body', pivot=(0, 12, 0))
    c.box('body', (-10, -9, -14), (20, 17, 28), pattern='scale', edge='dark')
    c.box('body', (-9, 4, -13), (18, 4, 26), mat='belly', pattern='skin')
    c.box('body', (-1, -16, -6), (2, 8, 12), mat='fin', pattern='membrane')                      # dorsal fin
    c.part('head', 'body', pivot=(0, -2, -14))
    c.box('head', (-8, -7, -12), (16, 13, 12), pattern='scale', edge='dark')
    c.box('head', (-5, -3, -17), (10, 7, 5), pattern='skin')                                       # pig snout
    c.box('head', (-3, -1, -17.6), (2, 2, 1), mat='dark', pattern='skin')
    c.box('head', (1, -1, -17.6), (2, 2, 1), mat='dark', pattern='skin')
    for x in (-6, 5):
        c.box('head', (x, 3, -16), (1, 4, 1), mat='tusk', pattern='horn')
        c.box('head', (x - (1 if x < 0 else -1) + (0 if x < 0 else 0), -5, -12.4), (2, 2, 1), mat='eye', pattern='glow')
    c.anim('head', 'head')
    for side, nm in ((-1, 'wing_r'), (1, 'wing_l')):
        c.part(nm, 'body', pivot=(side * 10, -6, -4))
        c.box(nm, ((-14 if side < 0 else 0), 0, -6), (14, 0, 12), mat='wing', pattern='membrane')
        c.anim(nm, nm, 0.5, 0.5)
    for side in (-1, 1):
        c.box('body', ((-14 if side < 0 else 10), 2, -8), (4, 1, 8), mat='fin', pattern='membrane')    # side fins
    c.part('tail', 'body', pivot=(0, -1, 14))
    c.box('tail', (-5, -5, 0), (10, 9, 10), pattern='scale', edge='dark')
    c.box('tail', (-1, -12, 9), (2, 10, 6), mat='fin', pattern='membrane')
    c.box('tail', (-1, 3, 9), (2, 8, 6), mat='fin', pattern='membrane')
    c.anim('tail', 'tail', 0.3, 0.3)
    c.anim('body', 'bob', 0.8, 0.12)
    return c


def sharkron():
    """Sharkron: a little flying shark with stubby wings."""
    c = Creature('sharkron', {'main': '#6A8AA8', 'dark': '#2A3A4A', 'belly': '#E0E8F0', 'teeth': '#F8F8F0', 'wing': '#5A7A98'})
    c.part('body', pivot=(0, 16, 0))
    c.box('body', (-4, -4, -9), (8, 7, 16), pattern='scale', edge='dark')
    c.box('body', (-3.5, 2, -8), (7, 1.5, 14), mat='belly', pattern='skin')
    c.box('body', (-3, -1, -10), (6, 3, 1), mat='belly', pattern='skin', faces={'north': 'teeth_down'})
    c.box('body', (-0.5, -9, -2), (1, 5, 5), mat='dark', pattern='membrane')
    c.box('body', (-0.5, -5, 7), (1, 9, 4), mat='dark', pattern='membrane')
    for side, nm in ((-1, 'wing_r'), (1, 'wing_l')):
        c.part(nm, 'body', pivot=(side * 4, -1, -2))
        c.box(nm, ((-7 if side < 0 else 0), 0, -3), (7, 0, 6), mat='wing', pattern='membrane')
        c.anim(nm, nm, 0.6, 0.9)
    c.anim('body', 'wiggle', 0.15, 0.5)
    return c


def ghost(name, robe, glow, size=8):
    """Dungeon Spirit / Poltergeist: a wispy hooded ghost with a trailing tail."""
    c = Creature(name, {'main': robe, 'dark': '#101820', 'glow': glow})
    c.part('body', pivot=(0, 12, 0))
    c.box('body', (-size / 2, -size, -size / 2), (size, size, size), pattern='cloth', edge='dark')
    c.box('body', (-size / 2 + 1, -size + 2, -size / 2 - 0.4), (size - 2, 2, 0.5), mat='glow', pattern='glow')
    prev = 'body'
    for i in range(3):
        nm = f'wisp_{i}'
        w = size - 2 - i * 2
        c.part(nm, prev, pivot=(0, 0 if i == 0 else 3, 0))
        c.box(nm, (-w / 2, 0, -w / 2), (w, 3, w), pattern='cloth')
        c.anim(nm, 'wiggle', 0.25, 0.4, i)
        prev = nm
    c.anim('body', 'bob', 0.8, 0.2)
    return c


def splinterling():
    """Splinterling: a little walking tree stump with glowing eyes and twig arms."""
    c = Creature('splinterling', {'main': '#6A4A2A', 'dark': '#2A1A0A', 'glow': '#FF8A20', 'leaf': '#C86A20'})
    c.part('body', pivot=(0, 14, 0))
    c.box('body', (-4, -6, -3), (8, 12, 6), pattern='wood', edge='dark')
    c.box('body', (-3, -3, -3.4), (2, 1, 0.5), mat='glow', pattern='glow')
    c.box('body', (1, -3, -3.4), (2, 1, 0.5), mat='glow', pattern='glow')
    c.box('body', (-5, -9, -4), (10, 3, 8), mat='leaf', pattern='leaf')
    for side, nm in ((-1, 'arm_r'), (1, 'arm_l')):
        c.part(nm, 'body', pivot=(side * 4, -3, 0))
        c.box(nm, ((-1 if side < 0 else 0), 0, -1), (1, 7, 1), pattern='wood')
        c.anim(nm, nm, 0.8)
    for side, nm in ((-1, 'leg_r'), (1, 'leg_l')):
        c.part(nm, 'body', pivot=(side * 2, 6, 0))
        c.box(nm, (-1, 0, -1), (2, 4, 2), pattern='wood')
        c.anim(nm, nm, 1.0)
    return c


def hellhound():
    """Hellhound: a lean black hound with burning eyes and a smoking back."""
    c = Creature('hellhound', {'main': '#2A2020', 'dark': '#100808', 'glow': '#FF5A10', 'fire': '#FF8A20'})
    c.part('body', pivot=(0, 13, 0))
    c.box('body', (-3, -3, -7), (6, 6, 14), pattern='skin', edge='dark')
    c.box('body', (-2, -5, -5), (4, 2, 10), mat='fire', pattern='fire')
    c.part('head', 'body', pivot=(0, -2, -7))
    c.box('head', (-3, -3, -5), (6, 5, 5), pattern='skin', edge='dark')
    c.box('head', (-2, -1, -8), (4, 3, 3), pattern='skin')
    c.box('head', (-2.5, -2, -5.4), (1, 1, 0.5), mat='glow', pattern='glow')
    c.box('head', (1.5, -2, -5.4), (1, 1, 0.5), mat='glow', pattern='glow')
    for x in (-2.5, 1.5):
        c.box('head', (x, -5, -3), (1, 2, 1), pattern='horn')
    c.anim('head', 'head')
    for i, (x, z) in enumerate(((-2, -5), (2, -5), (-2, 5), (2, 5))):
        nm = ('leg_r', 'leg_l')[i % 2] + str(i)
        c.part(nm, 'body', pivot=(x, 3, z))
        c.box(nm, (-1, 0, -1), (2, 8, 2), pattern='skin')
        c.anim(nm, ('leg_r', 'leg_l')[(i + i // 2) % 2], 1.0)
    c.part('tail', 'body', pivot=(0, -2, 7), rot=(-0.6, 0, 0))
    c.box('tail', (-0.5, -0.5, 0), (1, 1, 6), mat='fire', pattern='fire')
    c.anim('tail', 'tail', 0.4, 0.5)
    return c


def mourning_wood():
    """Mourning Wood: a huge haunted dead tree with a carved, burning face and grasping branch arms."""
    c = Creature('mourning_wood', {'main': '#4A3020', 'dark': '#1A0E06', 'glow': '#FF6A10', 'root': '#3A2416'})
    c.part('trunk', pivot=(0, 24, 0))
    c.box('trunk', (-10, -48, -9), (20, 48, 18), pattern='wood', edge='dark')
    c.box('trunk', (-7, -38, -9.6), (5, 4, 1), mat='glow', pattern='glow')
    c.box('trunk', (2, -38, -9.6), (5, 4, 1), mat='glow', pattern='glow')
    c.box('trunk', (-6, -28, -9.6), (12, 4, 1), mat='glow', pattern='glow')
    for i, (x, y, rz) in enumerate(((-10, -46, 0.8), (10, -46, -0.8), (-6, -54, 0.3), (6, -54, -0.3))):
        nm = f'branch_{i}'
        c.part(nm, 'trunk', pivot=(x, y, 0), rot=(0, 0, rz))
        c.box(nm, (-2, -14, -2), (4, 14, 4), pattern='wood')
        c.anim(nm, 'dangle', 0.08, 0.2, i)
    for x in (-12, 8):
        c.box('trunk', (x, -4, -4), (4, 4, 8), mat='root', pattern='wood')
    for side, nm in ((-1, 'arm_r'), (1, 'arm_l')):
        c.part(nm, 'trunk', pivot=(side * 10, -30, 0))
        c.box(nm, ((-12 if side < 0 else 0), -2, -2), (12, 4, 4), pattern='wood')
        c.anim(nm, 'cast', 0.2, 0.15)
    return c


def pumpking():
    """Pumpking: a giant floating jack-o'-lantern with a crown of leaves, burning face and two scythe hands."""
    c = Creature('pumpking', {'main': '#E8781A', 'dark': '#7A3A08', 'glow': '#FFD040', 'stem': '#3A6A20', 'blade': '#C8C8D0', 'cloak': '#2A1A2A'})
    c.part('head', pivot=(0, 14, 0))
    c.box('head', (-14, -14, -12), (28, 22, 24), pattern='skin', edge='dark')
    for x in (-10, -4, 2, 8):
        c.box('head', (x, -14.4, -12), (2, 22, 24.2), mat='dark', pattern='skin', inflate=0.1)
    c.box('head', (-10, -8, -12.6), (6, 5, 1), mat='glow', pattern='glow')
    c.box('head', (4, -8, -12.6), (6, 5, 1), mat='glow', pattern='glow')
    c.box('head', (-9, 0, -12.6), (18, 4, 1), mat='glow', pattern='glow')
    c.box('head', (-2, -20, -2), (4, 6, 4), mat='stem', pattern='leaf')
    c.box('head', (-8, 8, -8), (16, 8, 16), mat='cloak', pattern='cloth')
    for side, nm in ((-1, 'arm_r'), (1, 'arm_l')):
        c.part(nm, 'head', pivot=(side * 18, 0, -2))
        c.box(nm, (-2, -2, -2), (4, 4, 4), mat='cloak', pattern='cloth')
        c.box(nm, ((-12 if side < 0 else 0), -10, -1), (12, 3, 1), mat='blade', pattern='blade')
        c.anim(nm, 'cast', 0.4, 0.2)
    c.anim('head', 'bob', 1.0, 0.1)
    return c


def flocko():
    """Flocko: a living snowflake."""
    c = Creature('flocko', {'main': '#C8E8FF', 'dark': '#6A9AC8', 'glow': '#E8F8FF'})
    c.part('body', pivot=(0, 16, 0))
    c.box('body', (-2, -2, -2), (4, 4, 4), mat='glow', pattern='glow')
    for i in range(6):
        nm = f'spoke_{i}'
        c.part(nm, 'body', pivot=(0, 0, 0), rot=(0, 0, i * 1.0472))
        c.box(nm, (-0.5, -9, -0.5), (1, 7, 1), pattern='gem')
        c.box(nm, (-2, -7, -0.5), (4, 1, 1), pattern='gem')
    c.anim('body', 'wiggle', 3.0, 0.1)
    return c


def everscream():
    """Everscream: an evil giant pine tree with a screaming face and baubles."""
    c = Creature('everscream', {'main': '#1E4A2A', 'dark': '#0A2010', 'trunk': '#4A3020', 'glow': '#FF3A20', 'bauble': '#E8C030'})
    c.part('tree', pivot=(0, 24, 0))
    c.box('tree', (-4, -12, -4), (8, 12, 8), mat='trunk', pattern='wood')
    for i, (w, y) in enumerate(((36, -28), (28, -44), (20, -60), (12, -74))):
        c.box('tree', (-w / 2, y, -w / 2), (w, 16 if i < 3 else 12, w), pattern='leaf', edge='dark')
    c.box('tree', (-8, -40, -14.6), (5, 3, 1), mat='glow', pattern='glow')
    c.box('tree', (3, -40, -14.6), (5, 3, 1), mat='glow', pattern='glow')
    c.box('tree', (-5, -34, -14.6), (10, 4, 1), mat='dark', pattern='skin')
    for x, y in ((-12, -22), (10, -26), (-6, -48), (7, -52), (0, -66)):
        c.box('tree', (x, y, -18 if y > -30 else -14 if y > -55 else -10), (2, 2, 1), mat='bauble', pattern='gem')
    c.box('tree', (-2, -80, -2), (4, 4, 4), mat='bauble', pattern='gem')
    return c


def ice_queen():
    """Ice Queen: a floating frozen queen - a pale face under a tall ice crown, with a flowing gown of icicles."""
    c = Creature('ice_queen', {'main': '#A8D8F8', 'dark': '#4A7AA8', 'skin': '#E0F0FF', 'glow': '#60E0FF', 'crown': '#E8F8FF'})
    c.part('body', pivot=(0, 12, 0))
    c.box('body', (-6, -12, -5), (12, 12, 10), pattern='gem', edge='dark')
    for i, x in enumerate((-6, -2, 2)):
        c.box('body', (x, 0, -4), (4, 6 + (i % 2) * 3, 8), pattern='gem')
    c.part('head', 'body', pivot=(0, -12, 0))
    c.box('head', (-5, -9, -5), (10, 9, 10), mat='skin', pattern='skin', edge='dark')
    c.box('head', (-3, -6, -5.4), (2, 1, 0.5), mat='glow', pattern='glow')
    c.box('head', (1, -6, -5.4), (2, 1, 0.5), mat='glow', pattern='glow')
    for x, h in ((-5, 6), (-2, 9), (1, 9), (4, 6)):
        c.box('head', (x, -9 - h, -1), (1.5, h, 1.5), mat='crown', pattern='gem')
    c.anim('head', 'head')
    for side, nm in ((-1, 'wing_r'), (1, 'wing_l')):
        c.part(nm, 'body', pivot=(side * 6, -10, 3))
        c.box(nm, ((-10 if side < 0 else 0), -6, 0), (10, 14, 0), pattern='gem')
        c.anim(nm, nm, 0.2, 0.3)
    c.anim('body', 'bob', 1.0, 0.15)
    return c


def prismatic_lacewing():
    """Prismatic Lacewing: a moth with rainbow wings."""
    c = Creature('prismatic_lacewing', {'main': '#E8E0F8', 'dark': '#6A5A8A', 'wing': '#F0A0E0', 'glow': '#A0F0FF'})
    c.part('body', pivot=(0, 18, 0))
    c.box('body', (-1, -1, -3), (2, 2, 6), pattern='skin', edge='dark')
    for side, nm, mat in ((-1, 'wing_r', 'wing'), (1, 'wing_l', 'glow')):
        c.part(nm, 'body', pivot=(side, -1, 0))
        c.box(nm, ((-6 if side < 0 else 0), 0, -3), (6, 0, 6), mat=mat, pattern='membrane')
        c.anim(nm, nm, 0.9, 1.2)
    return c


def empress_of_light():
    """Empress of Light: a radiant fairy queen - a slender glowing figure with a pale face, flowing hair and four
    great prismatic butterfly wings."""
    c = Creature('empress_of_light', {'main': '#F8F0FF', 'dark': '#A898C8', 'hair': '#F8D8F0', 'glow': '#FFF8C8', 'wing': '#F0A8F0', 'wing2': '#A8E8FF'})
    c.part('body', pivot=(0, 22, 0))
    c.box('body', (-4, -24, -2.5), (8, 14, 5), pattern='cloth', edge='dark')
    c.box('body', (-6, -10, -4), (12, 10, 8), pattern='cloth')                                    # gown
    c.box('body', (-3, -20, -3), (6, 4, 0.5), mat='glow', pattern='glow')
    c.part('head', 'body', pivot=(0, -24, 0))
    c.box('head', (-3.5, -7, -3.5), (7, 7, 7), pattern='skin', edge='dark')
    c.box('head', (-4, -8, 0), (8, 10, 5), mat='hair', pattern='cloth')
    c.box('head', (-2, -4, -3.8), (1, 1, 0.3), mat='glow', pattern='glow')
    c.box('head', (1, -4, -3.8), (1, 1, 0.3), mat='glow', pattern='glow')
    c.box('head', (-3, -10, -1), (6, 2, 2), mat='glow', pattern='gem')                             # tiara
    c.anim('head', 'head')
    for side, nm in ((-1, 'wing_r'), (1, 'wing_l')):
        c.part(nm, 'body', pivot=(side * 3, -20, 2.5))
        c.box(nm, ((-22 if side < 0 else 0), -18, 0), (22, 18, 0), mat='wing', pattern='membrane')
        c.box(nm, ((-16 if side < 0 else 0), 0, 0.2), (16, 14, 0), mat='wing2', pattern='membrane')
        c.anim(nm, nm, 0.35, 0.35)
    for side, nm in ((-1, 'arm_r'), (1, 'arm_l')):
        c.part(nm, 'body', pivot=(side * 5, -23, 0))
        c.box(nm, (-1, 0, -1), (2, 11, 2), pattern='skin')
        c.anim(nm, 'cast', 0.3, 0.2)
    c.anim('body', 'bob', 1.2, 0.12)
    return c


def martian_probe():
    """Martian Probe: a small saucer-shaped scanner with a green eye."""
    c = Creature('martian_probe', {'main': '#A8B0B8', 'dark': '#4A5058', 'glow': '#60FF90'})
    c.part('body', pivot=(0, 18, 0))
    c.box('body', (-6, -2, -6), (12, 3, 12), pattern='plate', edge='dark')
    c.box('body', (-3, -5, -3), (6, 3, 6), mat='glow', pattern='glow')
    c.box('body', (-1, 1, -1), (2, 2, 2), mat='glow', pattern='glow')
    c.anim('body', 'bob', 0.8, 0.2)
    return c


def martian_drone():
    """Martian Drone: a round bomb drone with a blinking red light."""
    c = Creature('martian_drone', {'main': '#8A9098', 'dark': '#3A4048', 'glow': '#FF4020'})
    c.part('body', pivot=(0, 16, 0))
    c.box('body', (-5, -5, -5), (10, 9, 10), pattern='plate', edge='dark')
    c.box('body', (-1, -7, -1), (2, 2, 2), mat='glow', pattern='glow')
    for side in (-1, 1):
        c.box('body', ((-9 if side < 0 else 5), -2, -1), (4, 1, 2), mat='dark', pattern='plate')
    c.anim('body', 'bob', 0.6, 0.3)
    return c


def scutlix():
    """Scutlix: a martian's four-legged insectoid mount with a laser snout."""
    c = Creature('scutlix', {'main': '#5A8A4A', 'dark': '#1A3A1A', 'glow': '#60FF90', 'plate': '#8AA0A8'})
    c.part('body', pivot=(0, 12, 0))
    c.box('body', (-6, -4, -9), (12, 8, 18), pattern='scale', edge='dark')
    c.box('body', (-5, -6, -6), (10, 2, 12), mat='plate', pattern='plate')
    c.part('head', 'body', pivot=(0, -1, -9))
    c.box('head', (-4, -3, -7), (8, 6, 7), pattern='scale', edge='dark')
    c.box('head', (-1, -1, -11), (2, 2, 4), mat='plate', pattern='plate')
    c.box('head', (-3, -2, -7.4), (2, 1, 0.5), mat='glow', pattern='glow')
    c.box('head', (1, -2, -7.4), (2, 1, 0.5), mat='glow', pattern='glow')
    c.anim('head', 'head')
    for i, (x, z) in enumerate(((-6, -6), (6, -6), (-6, 6), (6, 6))):
        nm = f'leg_{i}'
        c.part(nm, 'body', pivot=(x, 2, z), rot=(0, 0, 0.5 if x > 0 else -0.5))
        c.box(nm, (-1, 0, -1), (2, 11, 2), pattern='scale')
        c.anim(nm, 'spider', 0.4, 0.5, i)
    return c


def martian_saucer():
    """The Martian Saucer: a wide flying saucer with a glass dome, running lights and laser turrets underneath."""
    c = Creature('martian_saucer', {'main': '#A8B0B8', 'dark': '#3A4048', 'glow': '#60FF90', 'dome': '#80D8C8', 'light': '#FFD040'})
    c.part('body', pivot=(0, 16, 0))
    c.box('body', (-40, -4, -40), (80, 6, 80), pattern='plate', edge='dark')
    c.box('body', (-30, -8, -30), (60, 4, 60), pattern='plate', edge='dark')
    c.box('body', (-14, -20, -14), (28, 12, 28), mat='dome', pattern='gem')
    c.box('body', (-20, 2, -20), (40, 4, 40), mat='dark', pattern='plate')
    for i in range(8):
        a = i * 3.14159 / 4
        import math as _m
        c.box('body', (_m.cos(a) * 36 - 1.5, -2, _m.sin(a) * 36 - 1.5), (3, 2, 3), mat='light', pattern='gem')
    for x, z in ((-12, -12), (12, -12), (-12, 12), (12, 12)):
        c.box('body', (x - 2, 6, z - 2), (4, 4, 4), mat='glow', pattern='glow')
    c.anim('body', 'bob', 1.0, 0.1)
    return c


def stage7_creatures():
    return [truffle_worm(), duke_fishron(), sharkron(), ghost('dungeon_spirit', '#5A90E8', '#A8E0FF'), ghost('poltergeist', '#C8C8D8', '#FF8A20', 7),
            splinterling(), hellhound(), mourning_wood(), pumpking(), flocko(), everscream(), ice_queen(), prismatic_lacewing(),
            empress_of_light(), martian_probe(), martian_drone(), scutlix(), martian_saucer()]


# ----------------------------------------------------------------------------------------- Stage 8
def celestial_pillar(kind, main, dark, glow, shield):
    """A Celestial Pillar (drawn at scale 2): a towering crystal monolith - a tall faceted shaft on a heavy base,
    bands of its element around it and a glowing core near the top. 'shielded': the shell glows with its shield."""
    c = Creature(f'{kind}_pillar', {'main': main, 'dark': dark, 'glow': glow, 'band': shade_hex(main, 1.3), 'shell': dark})
    c.variants['shielded'] = {'shell': shield, 'glow': '#FFFFFF'}
    c.part('body', pivot=(0, 24, 0))
    c.box('body', (-16, -10, -16), (32, 10, 32), mat='dark', pattern='rock')                       # base
    c.box('body', (-12, -70, -12), (24, 60, 24), pattern='gem', edge='dark')                        # shaft
    for y in (-26, -44, -62):
        c.box('body', (-14, y, -14), (28, 3, 28), mat='band', pattern='plate', edge='dark')
    c.box('body', (-8, -80, -8), (16, 10, 16), pattern='gem', edge='dark')                           # cap
    c.box('body', (-5, -58, -12.6), (10, 10, 1), mat='glow', pattern='glow')                          # core
    c.box('body', (-5, -58, 11.6), (10, 10, 1), mat='glow', pattern='glow')
    c.box('body', (-15, -78, -15), (30, 1, 30), mat='shell', pattern='gem')                           # shield ring
    c.box('body', (-15, -16, -15), (30, 1, 30), mat='shell', pattern='gem')
    return c


def shade_hex(color, f):
    r, g, b = int(color[1:3], 16), int(color[3:5], 16), int(color[5:7], 16)
    return '#%02X%02X%02X' % tuple(max(0, min(255, int(v * f))) for v in (r, g, b))


def sroller():
    """Sroller: a solar beast curled into a spiked rolling ball of flame."""
    c = Creature('sroller', {'main': '#E8781A', 'dark': '#6A2A10', 'glow': '#FFD040', 'spike': '#FFB030'})
    c.part('body', pivot=(0, 16, 0))
    c.box('body', (-7, -7, -7), (14, 14, 14), pattern='scale', edge='dark')
    for x, y, z in ((0, -9, 0), (0, 7, 0), (-9, 0, 0), (7, 0, 0), (0, 0, -9), (0, 0, 7)):
        c.box('body', (x - 1, y, z - 1) if y else (x, -1, z - 1) if x else (-1, -1, z), (2, 2, 2), mat='spike', pattern='horn')
    c.box('body', (-3, -3, -7.4), (2, 2, 0.5), mat='glow', pattern='glow')
    c.box('body', (1, -3, -7.4), (2, 2, 0.5), mat='glow', pattern='glow')
    c.anim('body', 'wiggle', 0.4, 1.2)
    return c


def corite():
    """Corite: a flying ball of solar rock wreathed in flame, with a glaring eye."""
    c = Creature('corite', {'main': '#8A3A1A', 'dark': '#3A1408', 'glow': '#FF8A20', 'eye': '#FFF0A0'})
    c.part('body', pivot=(0, 16, 0))
    c.box('body', (-6, -6, -6), (12, 12, 12), pattern='rock', edge='dark')
    c.box('body', (-3, -3, -6.5), (6, 4, 1), mat='eye', pattern='glow')
    c.part('flame', 'body', pivot=(0, 0, 6))
    c.box('flame', (-4, -4, 0), (8, 8, 8), mat='glow', pattern='fire')
    c.anim('flame', 'flicker', 0.3, 1.0)
    c.anim('body', 'bob', 0.6, 0.3)
    return c


def alien_hornet():
    """Alien Hornet: a vortex wasp - a teal armored abdomen with a glowing sting and buzzing wings."""
    c = Creature('alien_hornet', {'main': '#2A6A60', 'dark': '#0E2420', 'glow': '#60F0B0', 'wing': '#B0FFE0'})
    c.part('body', pivot=(0, 14, 0))
    c.box('body', (-3, -3, -6), (6, 6, 6), pattern='scale', edge='dark')
    c.box('body', (-3.5, -3, 0), (7, 7, 9), pattern='bands', edge='dark')
    c.box('body', (-1, 1, 9), (2, 2, 3), mat='glow', pattern='glow')
    c.box('body', (-2, -2, -6.4), (1.5, 1.5, 0.5), mat='glow', pattern='glow')
    c.box('body', (0.5, -2, -6.4), (1.5, 1.5, 0.5), mat='glow', pattern='glow')
    for side, nm in ((-1, 'wing_r'), (1, 'wing_l')):
        c.part(nm, 'body', pivot=(side * 3, -3, -2))
        c.box(nm, ((-9 if side < 0 else 0), 0, -3), (9, 0, 7), mat='wing', pattern='membrane')
        c.anim(nm, nm, 0.7, 2.0)
    c.anim('body', 'bob', 0.5, 0.3)
    return c


def nebula_floater():
    """Nebula Floater: a hovering violet brain-squid with a single great eye and dangling tendrils."""
    c = Creature('nebula_floater', {'main': '#9A4AC8', 'dark': '#2A1040', 'glow': '#FF80F0', 'eye': '#FFE0FF'})
    c.part('body', pivot=(0, 8, 0))
    c.box('body', (-7, -8, -7), (14, 10, 14), pattern='flesh', edge='dark')
    c.box('body', (-3, -5, -7.5), (6, 5, 1), mat='eye', pattern='glow')
    for i, (x, z) in enumerate(((-4, -4), (4, -4), (-4, 4), (4, 4))):
        nm = f'tendril_{i}'
        c.part(nm, 'body', pivot=(x, 2, z))
        c.box(nm, (-1, 0, -1), (2, 12, 2), mat='glow', pattern='skin')
        c.anim(nm, 'wiggle', 0.3, 0.5, i)
    c.anim('body', 'bob', 1.0, 0.15)
    return c


def brain_suckler():
    """Brain Suckler: a small pink nebula jellyfish that latches onto heads."""
    c = Creature('brain_suckler', {'main': '#E080D8', 'dark': '#4A1A48', 'glow': '#FFC0F8'})
    c.part('body', pivot=(0, 14, 0))
    c.box('body', (-5, -6, -5), (10, 6, 10), pattern='flesh', edge='dark')
    c.box('body', (-2, -4, -5.4), (4, 2, 0.5), mat='glow', pattern='glow')
    for i, x in enumerate((-3, 0, 3)):
        nm = f'leg_{i}'
        c.part(nm, 'body', pivot=(x, 0, 0))
        c.box(nm, (-0.5, 0, -0.5), (1, 7, 1), mat='glow', pattern='skin')
        c.anim(nm, 'wiggle', 0.3, 0.8, i)
    return c


def star_cell():
    """Star Cell: a glowing blue star-shaped cell that splits when hurt."""
    c = Creature('star_cell', {'main': '#60A8F8', 'dark': '#1A3A78', 'glow': '#E0F4FF'})
    c.part('body', pivot=(0, 16, 0))
    c.box('body', (-5, -5, -5), (10, 10, 10), pattern='gem', edge='dark')
    for x, y, z in ((0, -9, 0), (0, 5, 0), (-9, 0, 0), (5, 0, 0)):
        c.box('body', (x - 2 if not x else x, y if y else -2, -2), (4, 4, 4), mat='main', pattern='gem')
    c.box('body', (-2, -2, -5.5), (4, 4, 0.5), mat='glow', pattern='glow')
    c.anim('body', 'wiggle', 0.25, 0.6)
    return c


def flow_invader():
    """Flow Invader: a stardust squid - a pale blue mantle with drifting fins and a ring of tentacles."""
    c = Creature('flow_invader', {'main': '#A8D0F8', 'dark': '#2A4A88', 'glow': '#60C0FF'})
    c.part('body', pivot=(0, 10, 0))
    c.box('body', (-5, -12, -5), (10, 12, 10), pattern='skin', edge='dark')
    c.box('body', (-3, -6, -5.4), (2, 2, 0.5), mat='glow', pattern='glow')
    c.box('body', (1, -6, -5.4), (2, 2, 0.5), mat='glow', pattern='glow')
    for i, (x, z) in enumerate(((-3, -3), (3, -3), (-3, 3), (3, 3))):
        nm = f'tendril_{i}'
        c.part(nm, 'body', pivot=(x, 0, z))
        c.box(nm, (-1, 0, -1), (2, 9, 2), mat='glow', pattern='skin')
        c.anim(nm, 'wiggle', 0.35, 0.5, i)
    c.anim('body', 'bob', 1.0, 0.2)
    return c


def moon_lord():
    """Moon Lord's body (drawn at scale 2): a towering pale-green eldritch torso with a ribcage over the heart,
    tentacled jaw and stumpy legs. The head and hands are their own creatures. 'exposed': the heart glows bare."""
    c = Creature('moon_lord', {'main': '#8AA898', 'dark': '#2A3A34', 'flesh': '#6A8878', 'rib': '#D8E0C8', 'heart': '#3A6A58',
                               'glow': '#60F0D0'})
    c.variants['exposed'] = {'heart': '#80FFE8', 'rib': '#A8B8A0'}
    c.part('body', pivot=(0, 24, 0))
    c.box('body', (-14, -64, -8), (28, 28, 16), pattern='flesh', edge='dark')                       # chest
    c.box('body', (-10, -36, -6), (20, 12, 12), mat='flesh', pattern='flesh', edge='dark')          # waist
    c.box('body', (-5, -58, -8.6), (10, 12, 1), mat='heart', pattern='glow')                          # heart
    for y in (-60, -55, -50, -45):
        c.box('body', (-12, y, -9), (24, 2, 1), mat='rib', pattern='bone')
    for side, nm in ((-1, 'leg_r'), (1, 'leg_l')):
        c.part(nm, 'body', pivot=(side * 6, -24, 0))
        c.box(nm, (-4, 0, -4), (8, 24, 8), mat='flesh', pattern='flesh', edge='dark')
        c.anim(nm, nm, 0.15)
    for side in (-1, 1):                                                                               # shoulders/arms to the hands
        c.box('body', ((-24 if side < 0 else 14), -62, -4), (10, 8, 8), pattern='flesh', edge='dark')
    c.part('jaw', 'body', pivot=(0, -64, -4))
    for x in (-6, -2, 2, 6):
        c.box('jaw', (x - 1, -4, -2), (2, 6, 2), mat='flesh', pattern='skin')
    c.anim('body', 'bob', 0.6, 0.06)
    return c


def moon_lord_eye(name, w, h):
    """Moon Lord's hand or head: a pale-green mass with a huge glaring eye in it."""
    c = Creature(name, {'main': '#8AA898', 'dark': '#2A3A34', 'eye': '#E8F8F0', 'iris': '#40C8A8', 'glow': '#60F0D0'})
    c.part('body', pivot=(0, 24, 0))
    c.box('body', (-w / 2, -h, -w / 2 + 2), (w, h, w - 4), pattern='flesh', edge='dark')
    c.box('body', (-w / 2 + 4, -h + 6, -w / 2 + 1.4), (w - 8, h - 12, 1), mat='eye', pattern='skin')
    c.box('body', (-3, -h / 2 - 3, -w / 2 + 1), (6, 6, 1), mat='iris', pattern='glow')
    if name.endswith('hand'):
        for i, x in enumerate((-w / 2 + 2, -2, w / 2 - 6)):
            nm = f'finger_{i}'
            c.part(nm, 'body', pivot=(x + 2, -h, 0))
            c.box(nm, (-2, -12, -2), (4, 12, 4), pattern='flesh', edge='dark')
            c.anim(nm, 'wiggle', 0.15, 0.4, i)
    else:
        for x in (-w / 2 - 2, w / 2 - 2):                                                            # head tendrils
            c.box('body', (x, -h - 8, -2), (4, 10, 4), pattern='flesh', edge='dark')
    c.anim('body', 'bob', 0.5, 0.15)
    return c


def stage8_creatures():
    return [celestial_pillar('solar', '#E8781A', '#5A1A08', '#FFF0A0', '#FFD040'),
            celestial_pillar('vortex', '#2A8A78', '#0E2A24', '#B0FFE0', '#60F0B0'),
            celestial_pillar('nebula', '#9A4AC8', '#2A1040', '#FFD0FF', '#FF80F0'),
            celestial_pillar('stardust', '#60A8F8', '#14306A', '#E0F4FF', '#A8E8FF'),
            sroller(), corite(), alien_hornet(), nebula_floater(), brain_suckler(), star_cell(), flow_invader(),
            moon_lord(), moon_lord_eye('moon_lord_hand', 16, 22), moon_lord_eye('moon_lord_head', 22, 26)]


# ----------------------------------------------------------------------------------------- Stage 9: minions, sentries, Solar Eclipse
def renamed(creature, name):
    creature.name = name
    return creature


def slime_minion():
    """Slime Staff minion: a little blue slime with a gold crown."""
    c = Creature('slime_minion', {'main': '#5A9AF0', 'dark': '#1A3A80', 'eye': '#101828', 'crown': '#F0C838', 'glow': '#A8D0FF'})
    c.part('body', pivot=(0, 24, 0))
    c.box('body', (-4, -7, -4), (8, 7, 8), pattern='gem', edge='dark')
    c.box('body', (-2.5, -5, -4.4), (1.5, 2, 0.5), mat='eye', pattern='skin')
    c.box('body', (1, -5, -4.4), (1.5, 2, 0.5), mat='eye', pattern='skin')
    c.box('body', (-2.5, -9, -2.5), (5, 2, 5), mat='crown', pattern='gem')
    c.anim('body', 'bob', 0.8, 0.3)
    return c


def hornet_minion():
    """Hornet Staff minion: a striped little hornet with a stinger and buzzing wings."""
    c = Creature('hornet_minion', {'main': '#F0C030', 'dark': '#2A2010', 'glow': '#FFE070', 'wing': '#E0F0FF'})
    c.part('body', pivot=(0, 18, 0))
    c.box('body', (-2, -2, -4), (4, 4, 4), pattern='scale', edge='dark')
    c.box('body', (-2.5, -2, 0), (5, 5, 6), pattern='bands', edge='dark')
    c.box('body', (-0.5, 1, 6), (1, 1, 2), mat='dark', pattern='horn')
    for side, nm in ((-1, 'wing_r'), (1, 'wing_l')):
        c.part(nm, 'body', pivot=(side * 2, -2, -1))
        c.box(nm, ((-6 if side < 0 else 0), 0, -2), (6, 0, 5), mat='wing', pattern='membrane')
        c.anim(nm, nm, 0.7, 2.2)
    c.anim('body', 'bob', 0.5, 0.4)
    return c


def optic_minion():
    """Optic Staff minion: a small mechanical eye with plating and a red iris."""
    c = Creature('optic_minion', {'main': '#E8E8F0', 'dark': '#3A3A48', 'plate': '#8A8A98', 'iris': '#D83030', 'glow': '#FF6060'})
    c.part('body', pivot=(0, 16, 0))
    c.box('body', (-4, -4, -4), (8, 8, 8), pattern='skin', edge='dark')
    c.box('body', (-2, -2, -4.5), (4, 4, 1), mat='iris', pattern='glow')
    c.box('body', (-4.5, -4.5, 0), (9, 9, 4.5), mat='plate', pattern='plate', edge='dark')
    c.anim('body', 'bob', 0.6, 0.25)
    return c


def pygmy_minion():
    """Pygmy Staff minion: a little jungle warrior in a tribal mask with a spear."""
    c = Creature('pygmy_minion', {'main': '#8A5A3A', 'dark': '#2A1A10', 'mask': '#C8A050', 'paint': '#D83030', 'leaf': '#4A9A30',
                                  'spear': '#C8C8D0', 'glow': '#FFE070'})
    c.part('body', pivot=(0, 18, 0))
    c.box('body', (-2, -6, -1.5), (4, 6, 3), pattern='skin', edge='dark')
    c.box('body', (-2.5, -1, -2), (5, 2, 4), mat='leaf', pattern='leaf')
    c.part('head', 'body', pivot=(0, -6, 0))
    c.box('head', (-3, -6, -3), (6, 6, 6), mat='mask', pattern='wood', edge='dark')
    c.box('head', (-2, -4, -3.4), (1, 1, 0.5), mat='paint', pattern='gem')
    c.box('head', (1, -4, -3.4), (1, 1, 0.5), mat='paint', pattern='gem')
    c.box('head', (-1, -9, -1), (2, 3, 2), mat='leaf', pattern='leaf')
    c.part('arm_r', 'body', pivot=(-2.5, -5, 0))
    c.box('arm_r', (-1, 0, -1), (2, 5, 2), pattern='skin')
    c.box('arm_r', (-0.5, -6, -0.5), (1, 14, 1), mat='spear', pattern='plate')
    c.anim('arm_r', 'cast', 0.4, 0.3)
    c.anim('body', 'bob', 0.5, 0.25)
    return c


def tempest_minion():
    """Tempest Staff minion: a little sharknado - a spinning funnel of water with a shark in it."""
    c = Creature('tempest_minion', {'main': '#7AB8D8', 'dark': '#2A5A7A', 'glow': '#C8F0FF', 'shark': '#6A8AA8'})
    c.part('body', pivot=(0, 24, 0))
    for i, w in enumerate((3, 5, 7, 9, 11)):
        nm = f'ring_{i}'
        c.part(nm, 'body', pivot=(0, -i * 3, 0))
        c.box(nm, (-w / 2, -3, -w / 2), (w, 3, w), mat='main' if i % 2 else 'glow', pattern='gem')
        c.anim(nm, 'wiggle', 0.3, 1.5, i * 0.6)
    c.box('body', (-1.5, -10, -4), (3, 3, 6), mat='shark', pattern='scale')
    return c


def deadly_sphere(name):
    """Deadly Sphere: a hovering steel ball with spikes and a single red eye."""
    c = Creature(name, {'main': '#7A7A88', 'dark': '#22222A', 'spike': '#C8C8D0', 'iris': '#E83030', 'glow': '#FF5050'})
    c.part('body', pivot=(0, 16, 0))
    c.box('body', (-4, -4, -4), (8, 8, 8), pattern='plate', edge='dark')
    c.box('body', (-1.5, -1.5, -4.5), (3, 3, 1), mat='iris', pattern='glow')
    for x, y, z in ((0, -6, 0), (0, 4, 0), (-6, 0, 0), (4, 0, 0), (0, 0, 4)):
        c.box('body', (x - 1 if not x else x, y if y else -1, z - 1 if not z else z), (2, 2, 2), mat='spike', pattern='horn')
    c.anim('body', 'wiggle', 0.3, 1.0)
    return c


def terraprisma_minion():
    """Terraprisma: a floating sword of rainbow light."""
    c = Creature('terraprisma_minion', {'main': '#F0A8F8', 'dark': '#7A48A8', 'glow': '#A8F0FF', 'hilt': '#FFF0A8'})
    c.part('body', pivot=(0, 16, 0))
    c.box('body', (-1, -12, -0.5), (2, 12, 1), pattern='gem', edge='dark')
    c.box('body', (-0.5, -14, -0.5), (1, 2, 1), mat='glow', pattern='glow')
    c.box('body', (-3, 0, -1), (6, 1, 2), mat='hilt', pattern='gem')
    c.box('body', (-0.5, 1, -0.5), (1, 3, 1), mat='glow', pattern='glow')
    c.anim('body', 'bob', 0.6, 0.3)
    return c


def stardust_dragon_minion():
    """Stardust Dragon: a glowing star-blue dragon head with a trail of body segments."""
    c = Creature('stardust_dragon_minion', {'main': '#A8D0F8', 'dark': '#2A4A88', 'glow': '#E0F4FF', 'eye': '#FFE070'})
    c.part('body', pivot=(0, 16, 0))
    c.box('body', (-3, -3, -5), (6, 5, 7), pattern='scale', edge='dark')
    c.box('body', (-2, -1, -8), (4, 3, 3), pattern='scale')
    c.box('body', (-2.5, -2.5, -5.5), (1, 1, 0.5), mat='eye', pattern='glow')
    c.box('body', (1.5, -2.5, -5.5), (1, 1, 0.5), mat='eye', pattern='glow')
    for side in (-1, 1):
        c.box('body', ((-3 if side < 0 else 2), -6, -2), (1, 3, 3), mat='glow', pattern='horn')
    prev = 'body'
    for i in range(3):
        nm = f'segment_{i}'
        c.part(nm, prev, pivot=(0, 0, 2 if i == 0 else 4))
        c.box(nm, (-2.5 + i * 0.4, -2, 0), (5 - i * 0.8, 4 - i * 0.6, 4), mat='main' if i % 2 else 'glow', pattern='scale')
        c.anim(nm, 'wiggle', 0.35, 0.5, i)
        prev = nm
    c.anim('body', 'bob', 0.5, 0.2)
    return c


def rainbow_crystal():
    """Rainbow Crystal (sentry): a floating rainbow gem on a glowing core."""
    c = Creature('rainbow_crystal', {'main': '#F0A8F8', 'dark': '#5A2A88', 'glow': '#FFFFFF', 'c2': '#A8F0FF', 'c3': '#FFF0A0'})
    c.part('body', pivot=(0, 18, 0))
    c.box('body', (-3, -6, -3), (6, 6, 6), pattern='gem', edge='dark')
    c.box('body', (-2, -10, -2), (4, 4, 4), mat='c2', pattern='gem')
    c.box('body', (-2, 0, -2), (4, 4, 4), mat='c3', pattern='gem')
    c.box('body', (-1, -12, -1), (2, 2, 2), mat='glow', pattern='glow')
    c.anim('body', 'wiggle', 0.3, 0.6)
    return c


def lunar_portal():
    """Lunar Portal (sentry): a ring of moonstone around a swirling green-blue portal."""
    c = Creature('lunar_portal', {'main': '#5A7A78', 'dark': '#1A2A2A', 'glow': '#80FFE8', 'core': '#2AA898'})
    c.part('body', pivot=(0, 24, 0))
    c.box('body', (-8, -26, -1), (16, 2, 2), pattern='rock', edge='dark')
    c.box('body', (-8, -10, -1), (16, 2, 2), pattern='rock', edge='dark')
    c.box('body', (-8, -24, -1), (2, 14, 2), pattern='rock', edge='dark')
    c.box('body', (6, -24, -1), (2, 14, 2), pattern='rock', edge='dark')
    c.part('core', 'body', pivot=(0, -17, 0))
    c.box('core', (-6, -7, -0.5), (12, 14, 1), mat='core', pattern='glow')
    c.box('core', (-3, -3, -0.7), (6, 6, 1.4), mat='glow', pattern='glow')
    c.anim('core', 'wiggle', 0.2, 1.0)
    c.anim('body', 'bob', 0.5, 0.1)
    return c


def mothron():
    """Mothron: a huge brown moth with four patterned wings and glowing eyes."""
    c = Creature('mothron', {'main': '#8A6A4A', 'dark': '#2A1A10', 'wing': '#B88A50', 'wing2': '#E0C080', 'glow': '#FFE070'})
    c.part('body', pivot=(0, 12, 0))
    c.box('body', (-4, -4, -10), (8, 8, 20), pattern='scale', edge='dark')
    c.box('body', (-3, -3, -15), (6, 6, 5), pattern='skin', edge='dark')
    c.box('body', (-2.5, -2, -15.5), (1.5, 1.5, 0.5), mat='glow', pattern='glow')
    c.box('body', (1, -2, -15.5), (1.5, 1.5, 0.5), mat='glow', pattern='glow')
    for side in (-1, 1):
        c.box('body', ((-3 if side < 0 else 2), -9, -15), (1, 6, 1), mat='dark', pattern='horn')
    for side, nm in ((-1, 'wing_r'), (1, 'wing_l')):
        c.part(nm, 'body', pivot=(side * 4, -3, -4))
        c.box(nm, ((-22 if side < 0 else 0), 0, -6), (22, 0, 12), mat='wing', pattern='membrane')
        c.box(nm, ((-16 if side < 0 else 0), 0.1, 6), (16, 0, 9), mat='wing2', pattern='membrane')
        c.anim(nm, nm, 0.5, 0.5)
    c.anim('body', 'bob', 0.8, 0.15)
    return c


def stage9_creatures():
    return [slime_minion(), hornet_minion(), renamed(imp(), 'imp_minion'), optic_minion(), pygmy_minion(), tempest_minion(),
            renamed(martian_probe(), 'ufo_minion'), deadly_sphere('deadly_sphere_minion'), terraprisma_minion(),
            renamed(star_cell(), 'stardust_cell_minion'), stardust_dragon_minion(), rainbow_crystal(), lunar_portal(),
            ghost('reaper', '#2A2A34', '#E8E0C0', 9), mothron(), deadly_sphere('deadly_sphere')]


# ----------------------------------------------------------------------------------------- Stage 10: desert, snow, sky
def quadruped(name, mats, body, head, legs=4, leg_len=6, tail=True, ears=False, shell=None):
    """A four-legged creature: body (w, h, l), head size, legs; optional ears and a shell on the back."""
    c = Creature(name, mats)
    w, h, l = body
    c.part('body', pivot=(0, 24 - leg_len - h / 2, 0))
    c.box('body', (-w / 2, -h / 2, -l / 2), (w, h, l), pattern='skin', edge='dark')
    if shell:
        c.box('body', (-w / 2 - 1, -h / 2 - 3, -l / 2 - 1), (w + 2, 4, l + 2), mat='shell', pattern=shell, edge='dark')
    c.part('head', 'body', pivot=(0, -h / 4, -l / 2))
    hw, hh, hl = head
    c.box('head', (-hw / 2, -hh / 2, -hl), (hw, hh, hl), pattern='skin', edge='dark')
    c.box('head', (-hw / 2 + 0.5, -hh / 2 + 1, -hl - 0.4), (1, 1, 0.5), mat='eye', pattern='glow')
    c.box('head', (hw / 2 - 1.5, -hh / 2 + 1, -hl - 0.4), (1, 1, 0.5), mat='eye', pattern='glow')
    if ears:
        for x in (-hw / 2, hw / 2 - 1.5):
            c.box('head', (x, -hh / 2 - 2, -hl / 2), (1.5, 2, 1), pattern='skin')
    c.anim('head', 'head')
    for i, (x, z) in enumerate(((-w / 2 + 1, -l / 2 + 2), (w / 2 - 1, -l / 2 + 2), (-w / 2 + 1, l / 2 - 2), (w / 2 - 1, l / 2 - 2))[:legs]):
        nm = ('leg_r', 'leg_l')[i % 2] + str(i)
        c.part(nm, 'body', pivot=(x, h / 2, z))
        c.box(nm, (-1, 0, -1), (2, leg_len, 2), pattern='skin')
        c.anim(nm, ('leg_r', 'leg_l')[(i + i // 2) % 2], 1.0)
    if tail:
        c.part('tail', 'body', pivot=(0, -h / 4, l / 2), rot=(-0.5, 0, 0))
        c.box('tail', (-0.5, -0.5, 0), (1, 1, 5), pattern='skin')
        c.anim('tail', 'tail', 0.4, 0.5)
    return c


def antlion():
    """Antlion: a buried sand-coloured head with huge pincers, spitting sand."""
    c = Creature('antlion', {'main': '#C8A060', 'dark': '#5A3A18', 'eye': '#2A1A08', 'glow': '#2A1A08', 'pincer': '#7A5028'})
    c.part('body', pivot=(0, 24, 0))
    c.box('body', (-6, -6, -6), (12, 6, 12), mat='dark', pattern='rock')
    c.part('head', 'body', pivot=(0, -6, 0))
    c.box('head', (-4, -6, -4), (8, 6, 8), pattern='scale', edge='dark')
    c.box('head', (-3, -5, -4.4), (1.5, 1.5, 0.5), mat='eye', pattern='skin')
    c.box('head', (1.5, -5, -4.4), (1.5, 1.5, 0.5), mat='eye', pattern='skin')
    for side, nm in ((-1, 'jaw_r'), (1, 'jaw_l')):
        c.part(nm, 'head', pivot=(side * 2.5, -2, -4))
        c.box(nm, (-0.75, -1, -6), (1.5, 1.5, 6), mat='pincer', pattern='horn')
        c.anim(nm, 'jaw', 0.3 * side, 0.4)
    c.anim('head', 'head')
    return c


def vulture():
    """Vulture: a brown bird with a bald pink head and wide wings."""
    c = Creature('vulture', {'main': '#5A4030', 'dark': '#2A1A10', 'head': '#D88A80', 'beak': '#E8D0A0', 'eye': '#101010', 'glow': '#101010', 'wing': '#4A3020'})
    c.part('body', pivot=(0, 16, 0))
    c.box('body', (-3, -3, -4), (6, 6, 9), pattern='feather', edge='dark')
    c.part('head', 'body', pivot=(0, -3, -4))
    c.box('head', (-1.5, -4, -3), (3, 4, 3), mat='head', pattern='skin')
    c.box('head', (-0.5, -2, -5), (1, 1.5, 2), mat='beak', pattern='horn')
    c.anim('head', 'head')
    wings(c, 'body', -2, (12, 7), mat='wing', pattern='feather', amp=0.6, speed=0.5)
    c.anim('body', 'bob', 0.6, 0.15)
    return c


def antlion_charger():
    return quadruped('antlion_charger', {'main': '#C89A58', 'dark': '#5A3A18', 'eye': '#2A1A08', 'glow': '#2A1A08'}, (8, 5, 12), (6, 5, 5), tail=False)


def antlion_swarmer():
    """Antlion Swarmer: a flying sand-coloured insect with four clear wings."""
    c = Creature('antlion_swarmer', {'main': '#C8A060', 'dark': '#5A3A18', 'eye': '#FF4020', 'glow': '#FF4020', 'wing': '#F0E8D0'})
    c.part('body', pivot=(0, 16, 0))
    c.box('body', (-2.5, -2.5, -5), (5, 5, 10), pattern='bands', edge='dark')
    c.box('body', (-2, -2, -7), (4, 4, 2), pattern='scale')
    c.box('body', (-1.8, -1.5, -7.4), (1, 1, 0.5), mat='eye', pattern='glow')
    c.box('body', (0.8, -1.5, -7.4), (1, 1, 0.5), mat='eye', pattern='glow')
    wings(c, 'body', -2, (8, 4), amp=0.7, speed=2.0)
    c.anim('body', 'bob', 0.5, 0.4)
    return c


def ice_bat():
    """Ice Bat: a pale blue bat with icy wings."""
    c = Creature('ice_bat', {'main': '#A8D8F8', 'dark': '#3A6A98', 'eye': '#101828', 'glow': '#101828', 'wing': '#C8E8FF'})
    c.part('body', pivot=(0, 16, 0))
    c.box('body', (-2, -2, -2), (4, 4, 4), pattern='skin', edge='dark')
    c.box('body', (-1.5, -1, -2.4), (1, 1, 0.5), mat='eye', pattern='glow')
    c.box('body', (0.5, -1, -2.4), (1, 1, 0.5), mat='eye', pattern='glow')
    for x in (-2, 1):
        c.box('body', (x, -4, -1), (1, 2, 1), pattern='skin')
    wings(c, 'body', -1, (7, 5), amp=0.8, speed=1.4)
    return c


def ice_elemental():
    """Ice Elemental: a floating crystal spirit wrapped in a cloak of snow."""
    c = Creature('ice_elemental', {'main': '#C8E8FF', 'dark': '#3A6A98', 'glow': '#80E0FF', 'eye': '#FFFFFF', 'cloak': '#E8F4FF'})
    c.part('body', pivot=(0, 12, 0))
    c.box('body', (-4, -10, -3), (8, 10, 6), mat='cloak', pattern='cloth', edge='dark')
    c.box('body', (-3, -16, -3), (6, 6, 6), pattern='gem', edge='dark')
    c.box('body', (-2, -14, -3.4), (1, 1, 0.5), mat='eye', pattern='glow')
    c.box('body', (1, -14, -3.4), (1, 1, 0.5), mat='eye', pattern='glow')
    for i, x in enumerate((-2, 0, 2)):
        c.box('body', (x - 0.5, -20 + abs(x), -0.5), (1, 4 - abs(x), 1), mat='glow', pattern='gem')
    c.anim('body', 'bob', 1.0, 0.15)
    return c


def ice_golem():
    """Ice Golem: a hulking golem of ice blocks with a glowing blue core."""
    c = Creature('ice_golem', {'main': '#A8D8F8', 'dark': '#2A5A88', 'glow': '#60D0FF', 'eye': '#E0FFFF'})
    c.part('body', pivot=(0, 24, 0))
    c.box('body', (-9, -32, -6), (18, 18, 12), pattern='gem', edge='dark')
    c.box('body', (-3, -28, -6.5), (6, 6, 1), mat='glow', pattern='glow')
    c.part('head', 'body', pivot=(0, -32, 0))
    c.box('head', (-5, -8, -5), (10, 8, 10), pattern='gem', edge='dark')
    c.box('head', (-3, -5, -5.4), (2, 2, 0.5), mat='eye', pattern='glow')
    c.box('head', (1, -5, -5.4), (2, 2, 0.5), mat='eye', pattern='glow')
    c.anim('head', 'head')
    for side, nm in ((-1, 'arm_r'), (1, 'arm_l')):
        c.part(nm, 'body', pivot=(side * 10, -30, 0))
        c.box(nm, (-3, 0, -3), (6, 16, 6), pattern='gem', edge='dark')
        c.anim(nm, nm, 0.5)
    for side, nm in ((-1, 'leg_r'), (1, 'leg_l')):
        c.part(nm, 'body', pivot=(side * 5, -14, 0))
        c.box(nm, (-3, 0, -3), (6, 14, 6), pattern='gem', edge='dark')
        c.anim(nm, nm, 0.5)
    return c


def harpy():
    """Harpy: a winged woman with blue feather wings, bird legs and long hair."""
    c = Creature('harpy', {'main': '#E8C8A8', 'dark': '#5A3A2A', 'hair': '#3A6AC8', 'wing': '#5A8AE8', 'eye': '#3A2A4A', 'glow': '#3A2A4A',
                           'talon': '#E8C040'})
    c.part('body', pivot=(0, 14, 0))
    c.box('body', (-3, -9, -2), (6, 9, 4), mat='wing', pattern='feather', edge='dark')
    c.part('head', 'body', pivot=(0, -9, 0))
    c.box('head', (-3, -6, -3), (6, 6, 6), pattern='skin', edge='dark')
    c.box('head', (-3.5, -7, -1), (7, 9, 5), mat='hair', pattern='cloth')
    c.box('head', (-2, -4, -3.4), (1, 1, 0.5), mat='eye', pattern='glow')
    c.box('head', (1, -4, -3.4), (1, 1, 0.5), mat='eye', pattern='glow')
    c.anim('head', 'head')
    wings(c, 'body', -7, (11, 9), mat='wing', pattern='feather', amp=0.7, speed=0.8)
    for side, nm in ((-1, 'leg_r'), (1, 'leg_l')):
        c.part(nm, 'body', pivot=(side * 1.5, 0, 0))
        c.box(nm, (-0.75, 0, -0.75), (1.5, 6, 1.5), mat='talon', pattern='horn')
        c.anim(nm, nm, 0.3)
    c.anim('body', 'bob', 0.8, 0.2)
    return c


def stage10_creatures():
    return [antlion(), vulture(), antlion_charger(), antlion_swarmer(), ghost('desert_spirit', '#C8A060', '#FFE070', 8),
            ice_bat(), quadruped('snow_flinx', {'main': '#F0F4FF', 'dark': '#8AA0C0', 'eye': '#101828', 'glow': '#101828'}, (8, 7, 8), (6, 5, 4),
                                 legs=2, leg_len=3, tail=False),
            quadruped('wolf', {'main': '#8A8A98', 'dark': '#3A3A44', 'eye': '#FF3030', 'glow': '#FF3030'}, (7, 6, 14), (5, 5, 6), ears=True),
            ice_golem(), ice_elemental(),
            quadruped('ice_tortoise', {'main': '#6A9AB8', 'dark': '#2A4A68', 'eye': '#101828', 'glow': '#101828', 'shell': '#C8E8FF'},
                      (12, 6, 14), (5, 5, 5), leg_len=4, tail=False, shell='gem'),
            harpy()]


def all_creatures():
    return [imp(), demon(), demon('voodoo_demon', voodoo=True), eater_of_souls(), crimera(), face_monster(), blood_crawler(),
            snapper('man_eater', '#4A9A30', '#E070A0', 9), snapper('snatcher', '#5AAA3A', '#F0A0C0', 7), meteor_head()] + hm_creatures() + mech_creatures() + pirate_creatures() + frost_creatures() \
        + jungle_hm_creatures() + temple_creatures() + stage7_creatures() + stage8_creatures() + stage9_creatures() + stage10_creatures()


def write(assets, tex):
    for creature in all_creatures():
        width, height = am.pack(creature.cubes, 128)
        c = Canvas(width, height)
        rnd = random.Random(creature.name)
        for cube in creature.cubes:
            am.paint(creature, c, cube, rnd)
        c.save(os.path.join(tex, 'entity/model', creature.name + '.png'))
        base_mats = dict(creature.mats)
        for suffix, overrides in creature.variants.items():
            creature.mats = dict(base_mats, **{k: hexc(v) if isinstance(v, str) else v for k, v in overrides.items()})
            v = Canvas(width, height)
            rnd = random.Random(creature.name)
            for cube in creature.cubes:
                am.paint(creature, v, cube, rnd)
            v.save(os.path.join(tex, 'entity/model', f'{creature.name}_{suffix}.png'))
        creature.mats = base_mats
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
