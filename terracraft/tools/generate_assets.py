#!/usr/bin/env python3
"""Generates TerraCraft's original pixel-art textures and the client resource JSON (models, item
definitions, blockstates, equipment layers).

Run from the project root:  python3 tools/generate_assets.py
Everything is derived from the tables below; re-running is deterministic.
"""
import json
import math
import os
import random
import re
import sys

sys.path.insert(0, os.path.dirname(__file__))
from pixelart import Canvas, hexc, palette, shade  # noqa: E402

ROOT = os.path.join(os.path.dirname(__file__), '..')
ASSETS = os.path.join(ROOT, 'src/main/resources/assets/terracraft')
TEX = os.path.join(ASSETS, 'textures')

# ----------------------------------------------------------------------------------------- palettes
METAL = {
    'copper': '#C8783C', 'tin': '#B4A88C', 'iron': '#A0A0A8', 'lead': '#5A6478', 'silver': '#D2D7DC',
    'tungsten': '#8CA088', 'gold': '#E6BE3C', 'platinum': '#BED2E6', 'wood': '#8C5A32',
    'demonite': '#7A5CC0', 'crimtane': '#C8404A', 'shadow': '#4A3A70', 'crimson': '#A8343C',
}
WOOD = palette('#7A4E2A')
STONE = palette('#7C7C7C')
DEEPSLATE = palette('#4A4A50')


def metal(name):
    return palette(METAL[name])


# ----------------------------------------------------------------------------------------- item drawers
def draw_sword(c, p, length=11, short=False):
    """Diagonal blade from bottom-left handle to top-right tip."""
    x0, y0 = (5, 10) if not short else (6, 9)
    tip = 14 - (0 if not short else 4)
    for i in range(tip - x0):
        x, y = x0 + i, y0 - i
        c.set(x, y, p[2])
        c.set(x + 1, y, p[3])
        c.set(x, y - 1, p[4] if i < tip - x0 - 1 else p[3])
    c.set(tip, y0 - (tip - x0), p[4])
    guard = palette('#6E5A3C') if p[2] != METAL['wood'] else WOOD
    c.line(x0 - 2, y0 - 1, x0 + 1, y0 + 2, guard[2])
    c.line(x0 - 1, y0 - 1, x0 + 1, y0 + 1, guard[3])
    handle = WOOD
    c.line(x0 - 3, y0 + 4, x0 - 1, y0 + 2, handle[2])
    c.set(x0 - 4, y0 + 5, guard[1])
    c.outline()


def draw_pickaxe(c, p):
    c.line(3, 13, 11, 5, WOOD[2])
    c.line(4, 13, 12, 5, WOOD[1])
    head = [(5, 3), (6, 2), (8, 2), (10, 3), (12, 4), (13, 6), (14, 8), (14, 10)]
    for (x, y) in head:
        c.set(x, y, p[2])
    c.line(6, 3, 11, 4, p[3])
    c.line(11, 4, 13, 8, p[3])
    c.line(7, 2, 9, 2, p[4])
    c.rect(10, 4, 11, 5, p[2])
    c.set(4, 4, p[2])
    c.set(14, 11, p[1])
    c.outline()


def draw_axe(c, p):
    c.line(3, 13, 11, 5, WOOD[2])
    c.line(4, 13, 12, 5, WOOD[1])
    c.polygon([(9, 2), (14, 2), (15, 7), (12, 9), (10, 6)], p[2])
    c.line(13, 2, 14, 7, p[4])
    c.line(10, 3, 12, 3, p[3])
    c.outline()


def draw_hammer(c, p):
    c.line(3, 13, 10, 6, WOOD[2])
    c.line(4, 13, 11, 6, WOOD[1])
    c.polygon([(7, 4), (11, 0), (15, 4), (11, 8)], p[2])
    c.line(8, 4, 11, 1, p[3])
    c.line(11, 1, 14, 4, p[4])
    c.outline()


def draw_bow(c, p):
    pts = [(4, 1), (7, 2), (10, 4), (12, 7), (13, 10), (14, 13)]
    for i in range(len(pts) - 1):
        c.line(pts[i][0], pts[i][1], pts[i + 1][0], pts[i + 1][1], p[2])
    c.line(5, 1, 8, 2, p[3])
    c.line(4, 2, 13, 13, hexc('#E6E6E6'))
    c.outline()


def draw_gun(c, p):
    c.rect(2, 5, 13, 6, p[2])
    c.rect(2, 5, 13, 5, p[3])
    c.rect(13, 4, 14, 6, p[1])
    c.polygon([(3, 7), (7, 7), (6, 12), (3, 12)], WOOD[2])
    c.line(3, 7, 6, 7, WOOD[3])
    c.rect(8, 7, 9, 8, p[1])
    c.outline()


def draw_wand(c, gem):
    c.line(3, 13, 11, 5, WOOD[2])
    c.line(4, 13, 12, 5, WOOD[1])
    g = palette(gem)
    c.circle(12, 3, 2, g[2])
    c.set(11, 2, g[4])
    c.outline()


def draw_staff(c, gem):
    c.line(2, 14, 11, 5, palette('#A0A0A8')[2])
    c.line(3, 14, 12, 5, palette('#A0A0A8')[1])
    g = palette(gem)
    c.polygon([(12, 1), (15, 4), (12, 7), (9, 4)], g[2])
    c.set(11, 3, g[4])
    c.set(12, 2, g[3])
    c.outline()


def draw_book(c, color):
    p = palette(color)
    c.rect(3, 2, 12, 13, p[2])
    c.rect(3, 2, 4, 13, p[1])
    c.rect(5, 3, 12, 3, p[3])
    c.rect(11, 3, 12, 12, hexc('#E8E0C8'))
    c.circle(8, 8, 2, hexc('#78B4FF'))
    c.outline()


def draw_raw(c, p, seed):
    rnd = random.Random(seed)
    c.polygon([(3, 6), (7, 3), (12, 4), (13, 9), (10, 13), (5, 12), (2, 9)], p[2])
    for _ in range(9):
        c.set(rnd.randint(4, 11), rnd.randint(5, 11), rnd.choice([p[1], p[3]]))
    c.light()
    c.outline()


def draw_bar(c, p):
    c.polygon([(2, 9), (5, 5), (14, 5), (13, 9), (11, 12), (1, 12)], p[2])
    c.polygon([(5, 5), (14, 5), (11, 8), (3, 8)], p[3])
    c.line(6, 6, 12, 6, p[4])
    c.outline()


def draw_coin(c, p):
    c.circle(7.5, 7.5, 5.5, p[2])
    c.ring(7.5, 7.5, 4, p[3], 0.8)
    c.set(6, 5, p[4])
    c.set(5, 6, p[4])
    c.outline()


def draw_heart(c, color='#E6283C'):
    p = palette(color)
    c.circle(5, 6, 3, p[2])
    c.circle(10, 6, 3, p[2])
    c.polygon([(2, 7), (13, 7), (7.5, 14)], p[2])
    c.set(4, 4, p[4])
    c.set(5, 4, p[3])
    c.outline()


def draw_star(c, color='#FFE650', size=7.0):
    import math
    p = palette(color)
    pts = []
    for i in range(10):
        ang = -math.pi / 2 + i * math.pi / 5
        r = size if i % 2 == 0 else size * 0.45
        pts.append((7.5 + r * math.cos(ang), 8 + r * math.sin(ang)))
    c.polygon(pts, p[2])
    c.set(7, 5, p[4])
    c.set(8, 6, p[3])
    c.outline()


def draw_crystal(c, color):
    p = palette(color)
    c.polygon([(8, 1), (13, 6), (10, 14), (6, 14), (3, 6)], p[2])
    c.polygon([(8, 1), (10, 6), (8, 14), (6, 6)], p[3])
    c.line(8, 2, 7, 6, p[4])
    c.outline()


def draw_potion(c, liquid, size='normal'):
    glass = hexc('#DCE6F0', 200)
    top = 3 if size == 'normal' else 5
    c.rect(6, top - 2, 9, top - 1, hexc('#8C643C'))
    c.rect(6, top, 9, top + 1, glass)
    c.circle(7.5, 10, 4.5 if size == 'normal' else 3.5, glass)
    lp = palette(liquid)
    c.circle(7.5, 10.5, 3.5 if size == 'normal' else 2.5, lp[2])
    c.set(6, 9, lp[4])
    c.outline()


def draw_gel(c):
    p = palette('#3C8CFF')
    c.polygon([(3, 12), (4, 7), (8, 4), (12, 7), (13, 12)], (p[2][0], p[2][1], p[2][2], 200))
    c.set(6, 7, p[4])
    c.set(7, 6, p[4])
    c.outline()


def draw_lens(c):
    c.circle(7.5, 7.5, 5, hexc('#F0F0F0'))
    c.circle(7.5, 7.5, 3, hexc('#3C8C50'))
    c.circle(7.5, 7.5, 1.5, hexc('#101010'))
    c.set(6, 5, hexc('#FFFFFF'))
    c.outline()


def draw_gem(c, color):
    p = palette(color)
    c.polygon([(4, 6), (7, 3), (11, 3), (13, 6), (8, 13)], p[2])
    c.polygon([(4, 6), (13, 6), (8, 13)], p[1])
    c.line(7, 4, 10, 4, p[4])
    c.outline()


def draw_helmet(c, p):
    c.polygon([(3, 12), (3, 6), (6, 3), (10, 3), (13, 6), (13, 12), (10, 12), (10, 9), (6, 9), (6, 12)], p[2])
    c.line(5, 4, 10, 4, p[4])
    c.line(4, 6, 4, 11, p[3])
    c.outline()


def draw_chest(c, p):
    c.polygon([(2, 3), (5, 2), (11, 2), (14, 3), (14, 7), (12, 7), (12, 14), (4, 14), (4, 7), (2, 7)], p[2])
    c.rect(6, 4, 10, 12, p[3])
    c.line(8, 4, 8, 12, p[1])
    c.outline()


def draw_legs(c, p):
    c.polygon([(4, 2), (12, 2), (12, 14), (9, 14), (8, 7), (7, 14), (4, 14)], p[2])
    c.line(4, 3, 12, 3, p[4])
    c.line(5, 4, 5, 13, p[3])
    c.outline()


def draw_boots(c, color, wings=False):
    p = palette(color)
    c.polygon([(5, 3), (9, 3), (9, 10), (13, 11), (13, 13), (4, 13), (5, 9)], p[2])
    c.line(5, 4, 8, 4, p[4])
    c.rect(4, 12, 13, 13, p[1])
    if wings:
        w = palette('#F0F0F0')
        c.polygon([(9, 5), (14, 2), (13, 6), (10, 8)], w[2])
    c.outline()


def draw_ring(c, color, gem=None):
    p = palette(color)
    c.ring(7.5, 8.5, 5, p[2], 1.6)
    c.set(4, 5, p[4])
    if gem:
        g = palette(gem)
        c.circle(7.5, 3.5, 1.6, g[2])
        c.set(7, 3, g[4])
    c.outline()


def draw_balloon(c):
    p = palette('#E62828')
    c.circle(8, 5.5, 4.5, p[2])
    c.set(6, 3, p[4])
    c.set(7, 3, p[3])
    c.line(8, 10, 7, 15, hexc('#E6E6E6'))
    c.outline()


def draw_bottle(c, content):
    glass = hexc('#C8DCF0', 190)
    c.rect(6, 1, 9, 2, hexc('#8C643C'))
    c.rect(6, 3, 9, 4, glass)
    c.polygon([(4, 6), (11, 6), (12, 14), (3, 14)], glass)
    cp = palette(content)
    c.circle(7.5, 10, 2.5, cp[3])
    c.circle(6, 11, 1.8, cp[4])
    c.outline()


def draw_horseshoe(c):
    p = palette('#E6BE3C')
    c.ring(7.5, 7.5, 5.5, p[2], 2.2)
    c.rect(2, 8, 13, 14, (0, 0, 0, 0))
    c.rect(2, 8, 4, 12, p[2])
    c.rect(11, 8, 13, 12, p[2])
    c.set(4, 4, p[4])
    c.outline()


def draw_skull(c):
    p = palette('#3C2850')
    c.circle(7.5, 6.5, 5, p[2])
    c.rect(5, 10, 10, 13, p[2])
    c.circle(5.5, 6.5, 1.3, hexc('#FF6428'))
    c.circle(9.5, 6.5, 1.3, hexc('#FF6428'))
    c.line(6, 12, 9, 12, p[1])
    c.outline()


def draw_shield(c, color):
    p = palette(color)
    c.polygon([(3, 2), (12, 2), (12, 8), (7.5, 14), (3, 8)], p[2])
    c.polygon([(5, 4), (10, 4), (10, 8), (7.5, 11), (5, 8)], p[3])
    c.set(5, 3, p[4])
    c.outline()


def draw_claws(c):
    p = palette('#C8B496')
    for i in range(3):
        c.line(4 + i * 3, 2, 6 + i * 3, 10, p[3])
    c.rect(3, 9, 13, 13, palette('#78502D')[2])
    c.outline()


def draw_charm(c, color):
    p = palette(color)
    c.line(4, 1, 7, 6, hexc('#B4B4B4'))
    c.line(11, 1, 8, 6, hexc('#B4B4B4'))
    c.polygon([(7.5, 6), (12, 10), (7.5, 15), (3, 10)], p[2])
    c.set(6, 9, p[4])
    c.outline()


def draw_flipper(c):
    p = palette('#28A0C8')
    c.polygon([(3, 2), (8, 2), (13, 12), (11, 14), (3, 14)], p[2])
    c.line(5, 5, 11, 13, p[1])
    c.line(4, 3, 7, 3, p[4])
    c.outline()


def draw_belt(c):
    p = palette('#8C5A32')
    c.rect(1, 6, 14, 9, p[2])
    c.rect(1, 6, 14, 6, p[3])
    c.rect(6, 5, 9, 10, palette('#C8C8C8')[2])
    c.outline()


def draw_flower(c):
    for (x, y) in [(7, 3), (11, 7), (7, 11), (3, 7)]:
        c.circle(x + 0.5, y + 0.5, 2, palette('#78B4FF')[2])
    c.circle(7.5, 7.5, 1.6, hexc('#FFE650'))
    c.line(7, 12, 6, 15, hexc('#3CA03C'))
    c.outline()


def draw_shuriken(c):
    p = palette('#A0A0B4')
    c.polygon([(7.5, 1), (9, 6.5), (14, 7.5), (9, 9), (7.5, 14), (6, 9), (1, 7.5), (6, 6.5)], p[2])
    c.circle(7.5, 7.5, 1, p[0])
    c.outline()


def draw_knife(c):
    p = palette('#C8C8D2')
    c.line(5, 10, 13, 2, p[3], 1)
    c.line(6, 10, 13, 3, p[2], 1)
    c.line(2, 13, 5, 10, WOOD[2], 1)
    c.outline()


def draw_boomerang(c):
    p = palette('#A06E3C')
    c.polygon([(2, 3), (6, 2), (8, 8), (14, 10), (13, 14), (5, 11)], p[2])
    c.line(3, 3, 6, 9, p[3])
    c.outline()


def draw_arrow_item(c, head_color, fletch='#E6E6E6'):
    c.line(3, 12, 12, 3, WOOD[3])
    c.polygon([(10, 2), (14, 2), (14, 6)], palette(head_color)[2])
    c.line(2, 11, 4, 13, hexc(fletch))
    c.line(2, 12, 3, 13, hexc(fletch))
    c.outline()


def draw_ball(c):
    p = palette('#5A5A64')
    c.circle(7.5, 7.5, 3, p[2])
    c.set(6, 6, p[4])
    c.outline()


def draw_tablet(c):
    c.rect(3, 2, 12, 13, hexc('#282832'))
    c.rect(4, 3, 11, 11, hexc('#3CC8A0'))
    c.line(5, 5, 9, 5, hexc('#E6FFF0'))
    c.line(5, 7, 10, 7, hexc('#E6FFF0'))
    c.line(5, 9, 8, 9, hexc('#E6FFF0'))
    c.set(7, 12, hexc('#A0A0A0'))
    c.outline()


# ----------------------------------------------------------------------------------------- item table
ITEMS = {}


def item(name, drawer, handheld=False):
    ITEMS[name] = (drawer, handheld)


for m in ['copper', 'tin', 'iron', 'lead', 'silver', 'tungsten', 'gold', 'platinum']:
    if m != 'copper':
        item(f'{m}_broadsword', lambda c, m=m: draw_sword(c, metal(m)), True)
    item(f'{m}_pickaxe', lambda c, m=m: draw_pickaxe(c, metal(m)), True)
    item(f'{m}_axe', lambda c, m=m: draw_axe(c, metal(m)), True)
    if m in ('tin', 'lead', 'silver', 'tungsten', 'platinum'):
        item(f'raw_{m}', lambda c, m=m: draw_raw(c, metal(m), m))
        item(f'{m}_bar', lambda c, m=m: draw_bar(c, metal(m)))
item('copper_broadsword', lambda c: draw_sword(c, metal('copper')), True)
item('copper_shortsword', lambda c: draw_sword(c, metal('copper'), short=True), True)
item('wooden_sword', lambda c: draw_sword(c, palette('#A0703C')), True)
item('copper_hammer', lambda c: draw_hammer(c, metal('copper')), True)
item('iron_hammer', lambda c: draw_hammer(c, metal('iron')), True)
item('wooden_bow', lambda c: draw_bow(c, palette('#A0703C')), True)
item('copper_bow', lambda c: draw_bow(c, metal('copper')), True)
item('iron_bow', lambda c: draw_bow(c, metal('iron')), True)
item('gold_bow', lambda c: draw_bow(c, metal('gold')), True)
item('flintlock_pistol', lambda c: draw_gun(c, palette('#5A5A64')), False)
item('shuriken', draw_shuriken)
item('throwing_knife', draw_knife, True)
item('wooden_boomerang', draw_boomerang)
item('wand_of_sparking', lambda c: draw_wand(c, '#FF8C28'), True)
item('amethyst_staff', lambda c: draw_staff(c, '#A050DC'), True)
item('magic_missile', lambda c: draw_book(c, '#2850B4'))
item('flaming_arrow', lambda c: draw_arrow_item(c, '#FF8C28', '#FFC850'))
item('musket_ball', draw_ball)
item('copper_coin', lambda c: draw_coin(c, palette('#C8783C')))
item('silver_coin', lambda c: draw_coin(c, palette('#C8CDD2')))
item('gold_coin', lambda c: draw_coin(c, palette('#E6BE3C')))
item('platinum_coin', lambda c: draw_coin(c, palette('#DCE6F0')))
item('life_crystal', lambda c: draw_heart(c))
item('life_fruit', lambda c: draw_heart(c, '#96DC28'))
item('mana_crystal', lambda c: draw_crystal(c, '#3C64F0'))
item('fallen_star', lambda c: draw_star(c))
item('gel', lambda c: draw_gel(c))
item('lens', draw_lens)
item('amethyst', lambda c: draw_gem(c, '#A050DC'))


def draw_crown(c):
    gold = palette('#F0C030')
    c.polygon([(2, 12), (2, 6), (5, 9), (8, 4), (11, 9), (14, 6), (14, 12)], gold[2])
    c.rect(2, 11, 14, 12, gold[1])
    c.set(8, 8, hexc('#E03040'))
    c.set(5, 11, hexc('#40A0F0')); c.set(11, 11, hexc('#40A0F0'))
    c.circle(8, 13.5, 1.6, (90, 160, 240, 200))   # gel cushion
    c.light()
    c.outline()


def draw_eye_item(c):
    c.circle(7.5, 8, 5.5, hexc('#F2EEE8'))
    c.circle(6, 8, 3, hexc('#C22B2B'))
    c.circle(5.5, 8, 1.2, hexc('#1A0A0A'))
    c.line(12, 5, 14, 3, hexc('#B33A3A')); c.line(12, 11, 14, 13, hexc('#B33A3A'))
    c.light(1.15, 0.85)
    c.outline()


def draw_housing_query(c):
    wall = palette('#C8A070')
    roof = palette('#B04030')
    c.polygon([(1, 8), (8, 2), (15, 8)], roof[2])
    c.rect(3, 8, 13, 14, wall[2])
    c.rect(7, 10, 9, 14, palette('#6A4428')[2])
    c.rect(4, 9, 5, 10, hexc('#FFE070')); c.rect(11, 9, 12, 10, hexc('#FFE070'))
    c.light()
    c.outline()


item('housing_query', draw_housing_query)
item('purification_powder', lambda c: draw_powder(c, '#80E0A0'))
item('slime_crown', draw_crown)
item('suspicious_looking_eye', draw_eye_item)
def draw_chunk(c):
    p = palette('#7A8A4A')
    c.polygon([(3, 5), (9, 3), (13, 7), (11, 12), (5, 13), (2, 9)], p[2])
    rnd = random.Random(5)
    for _ in range(8):
        c.set(rnd.randint(4, 11), rnd.randint(5, 11), rnd.choice([p[1], hexc('#8A3A4A')]))
    c.light()
    c.outline()


def draw_vertebra(c):
    bone = palette('#E8D8C0')
    c.rect(5, 5, 10, 10, bone[2])
    c.rect(2, 7, 13, 8, bone[2])
    c.rect(7, 2, 8, 13, bone[2])
    c.circle(7.5, 7.5, 1, hexc('#B03030'))
    c.light()
    c.outline()


def draw_scale(c, color):
    p = palette(color)
    c.polygon([(8, 2), (13, 7), (8, 14), (3, 7)], p[2])
    c.line(8, 3, 8, 13, p[3])
    c.light()
    c.outline()


def draw_powder(c, color):
    p = palette(color)
    c.polygon([(3, 12), (8, 7), (13, 12)], p[2])
    for x, y in ((6, 10), (9, 9), (8, 11), (11, 11), (5, 12)):
        c.set(x, y, p[4])
    c.set(7, 5, p[3]); c.set(10, 4, p[3]); c.set(5, 6, p[3])
    c.outline()


def draw_worm_food(c):
    p = palette('#8A7A5A')
    for i in range(5):
        c.circle(3 + i * 2.2, 9 - (i % 2), 2, p[2] if i % 2 else p[3])
    c.set(12, 7, (20, 10, 10, 255))
    c.light()
    c.outline()


def draw_spine(c):
    bone = palette('#E0C8B0')
    for i in range(5):
        y = 2 + i * 2.5
        c.rect(5, int(y), 10, int(y) + 1, bone[2])
        c.set(4, int(y), hexc('#B03030')); c.set(11, int(y), hexc('#B03030'))
    c.line(7, 2, 7, 14, hexc('#C04040'))
    c.outline()


item('shadow_scale', lambda c: draw_scale(c, '#5A4A8A'))
item('tissue_sample', lambda c: draw_scale(c, '#C05050'))
item('vile_powder', lambda c: draw_powder(c, '#9A7AC8'))
item('vicious_powder', lambda c: draw_powder(c, '#D86060'))
item('worm_food', draw_worm_food)
item('bloody_spine', draw_spine)
item('rotten_chunk', draw_chunk)
item('vertebra', draw_vertebra)
item('demonite_bar', lambda c: draw_bar(c, palette('#7A5CC0')))
item('crimtane_bar', lambda c: draw_bar(c, palette('#C8404A')))

POTIONS = {
    'lesser_healing_potion': ('#E63C50', 'small'), 'healing_potion': ('#E63C50', 'normal'),
    'lesser_mana_potion': ('#3C64F0', 'small'), 'mana_potion': ('#3C64F0', 'normal'),
    'ironskin_potion': ('#C8C8B4', 'normal'), 'swiftness_potion': ('#5AE65A', 'normal'),
    'regeneration_potion': ('#FF6496', 'normal'), 'mana_regeneration_potion': ('#7864FF', 'normal'),
    'magic_power_potion': ('#C864FF', 'normal'), 'archery_potion': ('#C8A064', 'normal'),
    'mining_potion': ('#C8C864', 'normal'), 'obsidian_skin_potion': ('#50285A', 'normal'),
    'water_walking_potion': ('#3C8CFF', 'normal'), 'endurance_potion': ('#8C8CB4', 'normal'),
    'wrath_potion': ('#DC3C3C', 'normal'), 'rage_potion': ('#FF7814', 'normal'),
}
for name, (liquid, size) in POTIONS.items():
    item(name, lambda c, l=liquid, s=size: draw_potion(c, l, s))
ARMOR_SETS = ['wood', 'copper', 'tin', 'iron', 'lead', 'silver', 'tungsten', 'gold', 'platinum']
for s in ARMOR_SETS:
    col = metal(s) if s != 'wood' else palette('#A0703C')
    item(f'{s}_helmet', lambda c, col=col: draw_helmet(c, col))
    item(f'{s}_{"breastplate" if s == "wood" else "chainmail"}', lambda c, col=col: draw_chest(c, col))
    item(f'{s}_greaves', lambda c, col=col: draw_legs(c, col))
# Demonite / Crimtane gear
item('lights_bane', lambda c: draw_sword(c, metal('demonite')), True)
item('blood_butcherer', lambda c: draw_sword(c, metal('crimtane')), True)
item('demon_bow', lambda c: draw_bow(c, metal('demonite')), True)
item('tendon_bow', lambda c: draw_bow(c, metal('crimtane')), True)
item('musket', lambda c: draw_gun(c, palette('#6A4A2A')), False)
item('the_undertaker', lambda c: draw_gun(c, palette('#8A2A2A')), False)
item('vilethorn', lambda c: draw_wand(c, '#6AA040'), True)
item('nightmare_pickaxe', lambda c: draw_pickaxe(c, metal('demonite')), True)
item('deathbringer_pickaxe', lambda c: draw_pickaxe(c, metal('crimtane')), True)
item('war_axe_of_the_night', lambda c: draw_axe(c, metal('demonite')), True)
item('blood_lust_cluster', lambda c: draw_axe(c, metal('crimtane')), True)
item('the_breaker', lambda c: draw_hammer(c, metal('demonite')), True)
item('flesh_grinder', lambda c: draw_hammer(c, metal('crimtane')), True)
EVIL_ARMOR = ['shadow', 'crimson']
for s in EVIL_ARMOR:
    col = metal(s)
    item(f'{s}_helmet', lambda c, col=col: draw_helmet(c, col))
    item(f'{s}_scalemail', lambda c, col=col: draw_chest(c, col))
    item(f'{s}_greaves', lambda c, col=col: draw_legs(c, col))
ACCESSORIES = {
    'panic_necklace': lambda c: draw_charm(c, '#C03040'),
    'hermes_boots': lambda c: draw_boots(c, '#C8A050', wings=True),
    'cloud_in_a_bottle': lambda c: draw_bottle(c, '#F0F0F0'),
    'shiny_red_balloon': draw_balloon,
    'lucky_horseshoe': draw_horseshoe,
    'band_of_regeneration': lambda c: draw_ring(c, '#C8A03C', '#E63C50'),
    'band_of_starpower': lambda c: draw_ring(c, '#C8A03C', '#3C64F0'),
    'mana_regeneration_band': lambda c: draw_ring(c, '#B4B4C8', '#7864FF'),
    'natures_gift': draw_flower,
    'shackle': lambda c: draw_ring(c, '#787882'),
    'aglet': lambda c: draw_charm(c, '#C8A050'),
    'anklet_of_the_wind': lambda c: draw_ring(c, '#3CC864', '#B4FFB4'),
    'feral_claws': draw_claws,
    'obsidian_skull': draw_skull,
    'lava_charm': lambda c: draw_charm(c, '#FF6428'),
    'cobalt_shield': lambda c: draw_shield(c, '#2850DC'),
    'flipper': draw_flipper,
    'water_walking_boots': lambda c: draw_boots(c, '#3C8CFF'),
    'toolbelt': draw_belt,
}
for name, drawer in ACCESSORIES.items():
    item(name, drawer)
item('dev_tablet', draw_tablet)


# ----------------------------------------------------------------------------------------- blocks
def stone_base(seed, deep=False):
    c = Canvas()
    p = DEEPSLATE if deep else STONE
    c.noise([p[2], p[2], p[2], p[1], p[3]], seed)
    if deep:
        for y in (3, 8, 13):
            c.line(0, y, 15, y, p[1])
    return c


def ore_block(name, deep):
    c = stone_base(sum(map(ord, name)) * 31, deep)
    p = metal(name)
    rnd = random.Random(name + str(deep))
    for _ in range(6):
        x, y = rnd.randint(1, 13), rnd.randint(1, 13)
        c.set(x, y, p[2])
        c.set(x + 1, y, p[3])
        c.set(x, y + 1, p[1])
        c.set(x + 1, y + 1, p[2])
    return c


def wood_planks(seed):
    c = Canvas()
    c.noise([WOOD[2], WOOD[2], WOOD[3]], seed)
    for y in (0, 4, 8, 12):
        c.line(0, y, 15, y, WOOD[1])
    return c


def metal_block(name, seed):
    p = metal(name)
    c = Canvas()
    c.noise([p[2], p[2], p[3], p[1]], seed, 1.0)
    c.rect(0, 0, 15, 0, p[4])
    c.rect(0, 15, 15, 15, p[0])
    return c


BLOCK_TEXTURES = {}
for m in ['tin', 'lead', 'silver', 'tungsten', 'platinum']:
    BLOCK_TEXTURES[f'{m}_ore'] = lambda m=m: ore_block(m, False)
    BLOCK_TEXTURES[f'deepslate_{m}_ore'] = lambda m=m: ore_block(m, True)
BLOCK_TEXTURES['work_bench'] = lambda: wood_planks(11)
BLOCK_TEXTURES['iron_anvil'] = lambda: metal_block('iron', 21)
BLOCK_TEXTURES['lead_anvil'] = lambda: metal_block('lead', 22)


def life_crystal_block():
    c = Canvas()
    draw_heart(c)
    return c


BLOCK_TEXTURES['life_crystal_block'] = life_crystal_block

# --- Corruption / Crimson blocks ---
EVIL = {'corruption': {'stone': '#5A4A78', 'grass': '#7E5BA8', 'ore': '#8A6AD8', 'orb': '#6A3AA8'},
        'crimson': {'stone': '#7A3A3A', 'grass': '#C03838', 'ore': '#E04050', 'orb': '#C02838'}}


def evil_stone(kind, seed):
    p = palette(EVIL[kind]['stone'])
    c = Canvas()
    c.noise([p[2], p[2], p[2], p[1], p[3]], seed)
    rnd = random.Random(seed + 1)
    for _ in range(5):
        x, y = rnd.randint(0, 14), rnd.randint(0, 14)
        c.set(x, y, p[0]); c.set(x + 1, y, p[1])
    return c


def evil_ore(kind, seed):
    c = evil_stone(kind, seed)
    p = palette(EVIL[kind]['ore'])
    rnd = random.Random(seed + 7)
    for _ in range(6):
        x, y = rnd.randint(1, 13), rnd.randint(1, 13)
        c.set(x, y, p[3]); c.set(x + 1, y, p[4]); c.set(x, y + 1, p[2]); c.set(x + 1, y + 1, p[3])
    return c


def evil_grass_top(kind, seed):
    p = palette(EVIL[kind]['grass'])
    c = Canvas()
    c.noise([p[2], p[2], p[1], p[3]], seed)
    return c


def evil_grass_side(kind, seed):
    c = Canvas()
    dirt = palette('#79553A')
    c.noise([dirt[2], dirt[2], dirt[1], dirt[3]], seed)
    p = palette(EVIL[kind]['grass'])
    rnd = random.Random(seed)
    for x in range(16):
        depth = 3 + rnd.randint(0, 2)
        for y in range(depth):
            c.set(x, y, rnd.choice([p[2], p[1], p[3]]))
    return c


def orb_texture(kind, seed):
    p = palette(EVIL[kind]['orb'])
    c = Canvas()
    c.noise([p[2], p[2], p[3], p[1]], seed)
    rnd = random.Random(seed)
    for _ in range(10):
        c.set(rnd.randint(0, 15), rnd.randint(0, 15), p[4])
    return c


def altar_texture(kind, seed):
    c = evil_stone(kind, seed)
    p = palette(EVIL[kind]['ore'])
    for x in range(0, 16, 3):
        c.set(x, 2, p[3]); c.set(x + 1, 13, p[3])
    return c


def mushroom_texture(kind):
    c = Canvas()
    cap = palette(EVIL[kind]['grass'])
    stem = palette('#D8CCB8')
    c.rect(7, 8, 8, 15, stem[2])
    c.circle(7.5, 6, 4, cap[2])
    c.rect(3, 7, 12, 8, (0, 0, 0, 0))
    c.rect(4, 7, 11, 7, cap[1])
    c.set(6, 4, cap[4]); c.set(9, 5, cap[4])
    c.outline()
    return c


def bark(color, seed):
    p = palette(color)
    c = Canvas()
    c.noise([p[2], p[2], p[1]], seed)
    for x in (2, 6, 11, 14):
        c.line(x, 0, x, 15, p[1])
    return c


def log_top(color, seed):
    p = palette(color)
    c = Canvas()
    c.rect(0, 0, 15, 15, p[1])
    c.circle(7.5, 7.5, 6, p[3])
    c.ring(7.5, 7.5, 4, p[2], 0.8)
    c.ring(7.5, 7.5, 2, p[2], 0.8)
    return c


def evil_leaves(color, seed):
    p = palette(color)
    c = Canvas()
    rnd = random.Random(seed)
    for x in range(16):
        for y in range(16):
            if rnd.random() < 0.8:
                c.set(x, y, rnd.choice([p[2], p[1], p[3]]))
    return c


BLOCK_TEXTURES['ebonwood'] = lambda: bark('#5A4A6A', 401)
BLOCK_TEXTURES['ebonwood_top'] = lambda: log_top('#7A6A8A', 402)
BLOCK_TEXTURES['shadewood'] = lambda: bark('#6A3A3A', 403)
BLOCK_TEXTURES['shadewood_top'] = lambda: log_top('#8A5050', 404)
BLOCK_TEXTURES['ebonwood_leaves'] = lambda: evil_leaves('#7A5AA0', 405)
BLOCK_TEXTURES['shadewood_leaves'] = lambda: evil_leaves('#B03838', 406)

for _k, _prefix in (('corruption', 'ebon'), ('crimson', 'crim')):
    _stone = 'ebonstone' if _k == 'corruption' else 'crimstone'
    _grass = 'corrupt_grass' if _k == 'corruption' else 'crimson_grass'
    _ore = 'demonite_ore' if _k == 'corruption' else 'crimtane_ore'
    _orb = 'shadow_orb' if _k == 'corruption' else 'crimson_heart'
    _altar = 'demon_altar' if _k == 'corruption' else 'crimson_altar'
    _mush = 'vile_mushroom' if _k == 'corruption' else 'vicious_mushroom'
    BLOCK_TEXTURES[_stone] = lambda k=_k: evil_stone(k, 301)
    BLOCK_TEXTURES[_ore] = lambda k=_k: evil_ore(k, 302)
    BLOCK_TEXTURES[_grass + '_top'] = lambda k=_k: evil_grass_top(k, 303)
    BLOCK_TEXTURES[_grass + '_side'] = lambda k=_k: evil_grass_side(k, 304)
    BLOCK_TEXTURES[_orb] = lambda k=_k: orb_texture(k, 305)
    BLOCK_TEXTURES[_altar] = lambda k=_k: altar_texture(k, 306)
    BLOCK_TEXTURES[_mush] = lambda k=_k: mushroom_texture(k)

# ----------------------------------------------------------------------------------------- projectiles (drawn pointing +x)
PROJECTILES = {}


def proj(name, drawer):
    PROJECTILES[name] = drawer


def p_arrow(c, head='#B4B4B4', fletch='#E6E6E6', glow=None):
    c.line(1, 8, 12, 8, WOOD[3])
    c.polygon([(12, 6), (15, 8), (12, 10)], palette(head)[2])
    c.line(0, 6, 2, 8, hexc(fletch))
    c.line(0, 10, 2, 8, hexc(fletch))
    if glow:
        c.set(14, 7, hexc(glow))


def p_orb(c, color, r=4):
    p = palette(color)
    c.circle(7.5, 7.5, r + 1.5, (p[3][0], p[3][1], p[3][2], 90))
    c.circle(7.5, 7.5, r, p[3])
    c.circle(7.5, 7.5, r - 1.5, p[4])


proj('wooden_arrow', lambda c: p_arrow(c))
proj('flaming_arrow', lambda c: p_arrow(c, '#FF8C28', '#FFC850', '#FFE650'))
proj('musket_ball', lambda c: (c.circle(7.5, 7.5, 2.5, palette('#5A5A64')[2]), c.set(6, 6, palette('#5A5A64')[4])))
proj('shuriken', draw_shuriken)
proj('throwing_knife', lambda c: (c.line(2, 8, 13, 8, palette('#C8C8D2')[3]), c.line(3, 9, 12, 9, palette('#C8C8D2')[2]),
                                  c.line(0, 8, 2, 8, WOOD[2])))
proj('wooden_boomerang', draw_boomerang)
proj('spark', lambda c: p_orb(c, '#FF8C28', 2))
proj('amethyst_bolt', lambda c: p_orb(c, '#B464FF', 3))
proj('magic_missile', lambda c: p_orb(c, '#64A0FF', 4))


def p_vilethorn(c):
    p = palette('#6AA040')
    c.line(1, 8, 14, 8, p[2], 2)
    for x in range(3, 14, 3):
        c.set(x, 6, p[3]); c.set(x + 1, 11, p[1])
    c.outline()


proj('vilethorn', p_vilethorn)

# ----------------------------------------------------------------------------------------- HUD sprites (9x9)
HUD = {}


def hud_heart(color, empty=False):
    c = Canvas(9, 9)
    p = palette(color)
    fill = p[1] if empty else p[2]
    c.circle(2.5, 2.5, 2, fill)
    c.circle(5.5, 2.5, 2, fill)
    c.polygon([(0.5, 3), (8.5, 3), (4.5, 8)], fill)
    if not empty:
        c.set(2, 1, p[4])
        c.set(1, 2, p[3])
    c.outline(shade(p[0], 0.6))
    return c


def hud_star(empty=False):
    import math
    c = Canvas(9, 9)
    p = palette('#4664F0' if not empty else '#2A2A50')
    pts = []
    for i in range(10):
        ang = -math.pi / 2 + i * math.pi / 5
        r = 4.3 if i % 2 == 0 else 1.9
        pts.append((4.5 + r * math.cos(ang), 4.8 + r * math.sin(ang)))
    c.polygon(pts, p[2])
    if not empty:
        c.set(4, 3, p[4])
    c.outline(shade(p[0], 0.6))
    return c


def hud_shield():
    c = Canvas(9, 9)
    p = palette('#A0A0B4')
    c.polygon([(1, 0.5), (8, 0.5), (8, 5), (4.5, 8.5), (1, 5)], p[2])
    c.line(2, 1, 6, 1, p[4])
    c.outline(p[0])
    return c


HUD['heart_full'] = lambda: hud_heart('#E6283C')
HUD['heart_golden'] = lambda: hud_heart('#F0C828')
HUD['heart_empty'] = lambda: hud_heart('#783C46', empty=True)
HUD['star_full'] = lambda: hud_star()
HUD['star_empty'] = lambda: hud_star(empty=True)
HUD['defense'] = hud_shield

# ----------------------------------------------------------------------------------------- mob effect icons (18x18)
EFFECTS = {
    'ironskin': '#B4B4B4', 'swiftness': '#5AE65A', 'regeneration': '#FF6496', 'mana_regeneration': '#6464FF',
    'magic_power': '#C864FF', 'archery': '#C8A064', 'mining': '#C8C864', 'obsidian_skin': '#78508C',
    'water_walking': '#3C8CFF', 'endurance': '#8C8CB4', 'wrath': '#DC3C3C', 'rage': '#FF7814',
    'potion_sickness': '#784646', 'mana_sickness': '#463278',
    'well_fed': '#E6B450', 'plenty_satisfied': '#E68C3C', 'exquisitely_stuffed': '#E6643C',
}


def effect_icon(color):
    c = Canvas(18, 18)
    p = palette(color)
    c.circle(8.5, 8.5, 7, p[1])
    c.circle(8.5, 8.5, 5.5, p[2])
    c.circle(7, 7, 2, p[4])
    c.outline(p[0])
    return c


# ----------------------------------------------------------------------------------------- armor layers (64x32)
def armor_layer(color, leggings):
    c = Canvas(64, 32)
    p = palette(color)
    rnd = random.Random(color + str(leggings))

    def region(x0, y0, x1, y1):
        for x in range(x0, x1):
            for y in range(y0, y1):
                col = p[2] if rnd.random() > 0.15 else p[3]
                c.set(x, y, col)
        for x in range(x0, x1):
            c.set(x, y0, p[4])
            c.set(x, y1 - 1, p[1])
    if not leggings:
        # helmet: head box faces (u0 v0, 8x8x8 -> 32x16 area); leave the face front partly open
        region(0, 0, 32, 16)
        for x in range(9, 15):
            for y in range(11, 15):
                c.set(x, y, (0, 0, 0, 0))
        region(16, 16, 40, 32)   # chest
        region(40, 16, 56, 32)   # arms
        region(0, 24, 16, 32)    # boots (lower legs)
    else:
        region(16, 24, 40, 32)   # belt / lower body
        region(0, 16, 16, 32)    # legs
    return c


# ----------------------------------------------------------------------------------------- writers
def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


def item_assets(name, handheld):
    write_json(os.path.join(ASSETS, 'items', name + '.json'),
               {'model': {'type': 'minecraft:model', 'model': f'terracraft:item/{name}'}})
    write_json(os.path.join(ASSETS, 'models/item', name + '.json'),
               {'parent': 'minecraft:item/handheld' if handheld else 'minecraft:item/generated',
                'textures': {'layer0': f'terracraft:item/{name}'}})


BLOCKS_CUBE = ['ebonstone', 'crimstone', 'demonite_ore', 'crimtane_ore', 'tin_ore', 'deepslate_tin_ore', 'lead_ore', 'deepslate_lead_ore', 'silver_ore', 'deepslate_silver_ore',
               'tungsten_ore', 'deepslate_tungsten_ore', 'platinum_ore', 'deepslate_platinum_ore']


def block_assets():
    for name in BLOCKS_CUBE:
        write_json(os.path.join(ASSETS, 'blockstates', name + '.json'), {'variants': {'': {'model': f'terracraft:block/{name}'}}})
        write_json(os.path.join(ASSETS, 'models/block', name + '.json'),
                   {'parent': 'minecraft:block/cube_all', 'textures': {'all': f'terracraft:block/{name}'}})
        write_json(os.path.join(ASSETS, 'items', name + '.json'),
                   {'model': {'type': 'minecraft:model', 'model': f'terracraft:block/{name}'}})
    # work bench: table model
    write_json(os.path.join(ASSETS, 'models/block/work_bench.json'), {
        'parent': 'minecraft:block/block',
        'textures': {'particle': 'terracraft:block/work_bench', 'wood': 'terracraft:block/work_bench'},
        'elements': [
            {'from': [0, 12, 0], 'to': [16, 16, 16], 'faces': {f: {'texture': '#wood'} for f in ['north', 'south', 'east', 'west', 'up', 'down']}},
        ] + [
            {'from': [x, 0, z], 'to': [x + 3, 12, z + 3], 'faces': {f: {'texture': '#wood'} for f in ['north', 'south', 'east', 'west', 'down']}}
            for x, z in [(1, 1), (12, 1), (1, 12), (12, 12)]
        ]})
    for anvil in ['iron_anvil', 'lead_anvil']:
        write_json(os.path.join(ASSETS, f'models/block/{anvil}.json'), {
            'parent': 'minecraft:block/block',
            'textures': {'particle': f'terracraft:block/{anvil}', 'metal': f'terracraft:block/{anvil}'},
            'elements': [
                {'from': [2, 0, 3], 'to': [14, 4, 13], 'faces': {f: {'texture': '#metal'} for f in ['north', 'south', 'east', 'west', 'up', 'down']}},
                {'from': [5, 4, 5], 'to': [11, 9, 11], 'faces': {f: {'texture': '#metal'} for f in ['north', 'south', 'east', 'west']}},
                {'from': [0, 9, 2], 'to': [16, 15, 14], 'faces': {f: {'texture': '#metal'} for f in ['north', 'south', 'east', 'west', 'up', 'down']}},
            ]})
    for name in ['work_bench', 'iron_anvil', 'lead_anvil']:
        write_json(os.path.join(ASSETS, 'blockstates', name + '.json'), {'variants': {'': {'model': f'terracraft:block/{name}'}}})
        write_json(os.path.join(ASSETS, 'items', name + '.json'), {'model': {'type': 'minecraft:model', 'model': f'terracraft:block/{name}'}})
    for log in ('ebonwood', 'shadewood'):
        write_json(os.path.join(ASSETS, 'models/block', log + '.json'), {'parent': 'minecraft:block/cube_column',
                   'textures': {'end': f'terracraft:block/{log}_top', 'side': f'terracraft:block/{log}'}})
        write_json(os.path.join(ASSETS, 'models/block', log + '_horizontal.json'), {'parent': 'minecraft:block/cube_column_horizontal',
                   'textures': {'end': f'terracraft:block/{log}_top', 'side': f'terracraft:block/{log}'}})
        write_json(os.path.join(ASSETS, 'blockstates', log + '.json'), {'variants': {
            'axis=y': {'model': f'terracraft:block/{log}'},
            'axis=z': {'model': f'terracraft:block/{log}_horizontal', 'x': 90},
            'axis=x': {'model': f'terracraft:block/{log}_horizontal', 'x': 90, 'y': 90}}})
        write_json(os.path.join(ASSETS, 'items', log + '.json'), {'model': {'type': 'minecraft:model', 'model': f'terracraft:block/{log}'}})
        leaves = log + '_leaves'
        write_json(os.path.join(ASSETS, 'models/block', leaves + '.json'), {'parent': 'minecraft:block/cube_all', 'render_type': 'minecraft:cutout_mipped',
                   'textures': {'all': f'terracraft:block/{leaves}'}})
        write_json(os.path.join(ASSETS, 'blockstates', leaves + '.json'), {'variants': {'': {'model': f'terracraft:block/{leaves}'}}})
        write_json(os.path.join(ASSETS, 'items', leaves + '.json'), {'model': {'type': 'minecraft:model', 'model': f'terracraft:block/{leaves}'}})
    for grass in ('corrupt_grass', 'crimson_grass'):
        write_json(os.path.join(ASSETS, 'blockstates', grass + '.json'), {'variants': {'': {'model': f'terracraft:block/{grass}'}}})
        write_json(os.path.join(ASSETS, 'models/block', grass + '.json'), {'parent': 'minecraft:block/cube_bottom_top', 'textures': {
            'top': f'terracraft:block/{grass}_top', 'side': f'terracraft:block/{grass}_side', 'bottom': 'minecraft:block/dirt'}})
        write_json(os.path.join(ASSETS, 'items', grass + '.json'), {'model': {'type': 'minecraft:model', 'model': f'terracraft:block/{grass}'}})
    for orb in ('shadow_orb', 'crimson_heart'):
        write_json(os.path.join(ASSETS, 'blockstates', orb + '.json'), {'variants': {'': {'model': f'terracraft:block/{orb}'}}})
        write_json(os.path.join(ASSETS, 'models/block', orb + '.json'), {
            'parent': 'minecraft:block/block', 'textures': {'particle': f'terracraft:block/{orb}', 'all': f'terracraft:block/{orb}'},
            'elements': [{'from': [3, 3, 3], 'to': [13, 13, 13], 'faces': {f: {'texture': '#all'} for f in ['north', 'south', 'east', 'west', 'up', 'down']}},
                         {'from': [2, 5, 5], 'to': [14, 11, 11], 'faces': {f: {'texture': '#all'} for f in ['north', 'south', 'east', 'west', 'up', 'down']}},
                         {'from': [5, 2, 5], 'to': [11, 14, 11], 'faces': {f: {'texture': '#all'} for f in ['north', 'south', 'east', 'west', 'up', 'down']}}]})
        write_json(os.path.join(ASSETS, 'items', orb + '.json'), {'model': {'type': 'minecraft:model', 'model': f'terracraft:block/{orb}'}})
    for altar in ('demon_altar', 'crimson_altar'):
        write_json(os.path.join(ASSETS, 'blockstates', altar + '.json'), {'variants': {'': {'model': f'terracraft:block/{altar}'}}})
        write_json(os.path.join(ASSETS, 'models/block', altar + '.json'), {
            'parent': 'minecraft:block/block', 'textures': {'particle': f'terracraft:block/{altar}', 'all': f'terracraft:block/{altar}'},
            'elements': [{'from': [0, 0, 2], 'to': [16, 6, 14], 'faces': {f: {'texture': '#all'} for f in ['north', 'south', 'east', 'west', 'up', 'down']}},
                         {'from': [2, 6, 3], 'to': [14, 11, 13], 'faces': {f: {'texture': '#all'} for f in ['north', 'south', 'east', 'west', 'up']}},
                         {'from': [1, 11, 4], 'to': [3, 15, 6], 'faces': {f: {'texture': '#all'} for f in ['north', 'south', 'east', 'west', 'up']}},
                         {'from': [13, 11, 10], 'to': [15, 15, 12], 'faces': {f: {'texture': '#all'} for f in ['north', 'south', 'east', 'west', 'up']}}]})
        write_json(os.path.join(ASSETS, 'items', altar + '.json'), {'model': {'type': 'minecraft:model', 'model': f'terracraft:block/{altar}'}})
    for mush in ('vile_mushroom', 'vicious_mushroom'):
        write_json(os.path.join(ASSETS, 'blockstates', mush + '.json'), {'variants': {'': {'model': f'terracraft:block/{mush}'}}})
        write_json(os.path.join(ASSETS, 'models/block', mush + '.json'),
                   {'parent': 'minecraft:block/cross', 'render_type': 'minecraft:cutout', 'textures': {'cross': f'terracraft:block/{mush}'}})
        write_json(os.path.join(ASSETS, 'items', mush + '.json'), {'model': {'type': 'minecraft:model', 'model': f'terracraft:item/{mush}'}})
        write_json(os.path.join(ASSETS, 'models/item', mush + '.json'), {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'terracraft:block/{mush}'}})
    write_json(os.path.join(ASSETS, 'blockstates/life_crystal_block.json'), {'variants': {'': {'model': 'terracraft:block/life_crystal_block'}}})
    write_json(os.path.join(ASSETS, 'models/block/life_crystal_block.json'),
               {'parent': 'minecraft:block/cross', 'render_type': 'minecraft:cutout', 'textures': {'cross': 'terracraft:block/life_crystal_block'}})


def armor_assets():
    """3D armor: geometry + textures for every set (see tools/armor_models.py)."""
    import armor_models
    armor_models.write(ASSETS, TEX)


# --- Enemy sprite sheets ---------------------------------------------------------------------------
# Frames are stacked vertically and face LEFT (Terraria's convention); <id>.json holds the frame data.
MOB_SPRITES = {}


def mob_sprite(name, frame_w, frame_h, frames, drawer, **meta):
    MOB_SPRITES[name] = (frame_w, frame_h, frames, drawer, meta)


def slime_frame(c, color, frame, w, h, alpha=190, baby=None):
    p = palette(color)
    squash = 1 if frame == 1 else 0
    top = 2 + squash * 2
    left, right, bottom = 1 - squash, w - 2 + squash, h - 1
    for x in range(w):
        for y in range(h):
            # dome: flat bottom, rounded top
            nx = (x - (left + right) / 2) / ((right - left) / 2 + 0.5)
            ny = (y - bottom) / (bottom - top + 0.5)
            if nx * nx + ny * ny <= 1.0 and y <= bottom:
                c.set(x, y, (*p[2][:3], alpha))
    if baby:
        bx, by = w // 2, bottom - 3
        c.circle(bx, by, 2, (*hexc(baby)[:3], 220))
    c.light(1.3, 0.75)
    c.outline()
    # shine and eyes (looking left)
    c.set(left + 3, top + 2, (*p[4][:3], 230))
    c.set(left + 4, top + 1 + (1 if squash else 0), (*p[4][:3], 200))
    eye_y = top + (bottom - top) // 2
    c.rect(left + 3, eye_y, left + 3, eye_y + 1, (20, 20, 30, 255))
    c.rect(left + 6, eye_y, left + 6, eye_y + 1, (20, 20, 30, 255))


SLIMES = {'green_slime': '#5DC95D', 'blue_slime': '#4F8EF2', 'red_slime': '#E04B4B', 'purple_slime': '#A355E3',
          'yellow_slime': '#F0D23C', 'black_slime': '#3A3A48'}
for _name, _color in SLIMES.items():
    mob_sprite(_name, 16, 12, 2, lambda c, f, col=_color: slime_frame(c, col, f, 16, 12), frame_time=10)
mob_sprite('baby_slime', 12, 9, 2, lambda c, f: slime_frame(c, '#7FBFF8', f, 12, 9), frame_time=8)
mob_sprite('mother_slime', 22, 16, 2, lambda c, f: slime_frame(c, '#D46FC4', f, 22, 16, baby='#7FBFF8'), frame_time=12)


def humanoid_frame(c, frame, skin, shirt, pants, eyes, bony=False):
    # 16x24 frame, body facing left, arms reaching forward (Terraria zombie pose)
    sk, sh, pa = palette(skin), palette(shirt), palette(pants)
    c.rect(5, 1, 10, 7, sk[2])                     # head
    c.rect(5, 1, 10, 2, sk[1] if not bony else sk[3])
    c.set(5, 4, eyes); c.set(6, 4, eyes)           # eyes (left side)
    c.rect(5, 6, 7, 6, sk[1])                      # jaw/mouth
    c.rect(6, 8, 10, 15, sh[2])                    # torso
    if not bony:
        c.set(9, 12, sk[2]); c.set(7, 14, sk[2])   # torn shirt
    # arms forward
    arm_y = 9 + (frame % 2)
    c.rect(1, arm_y, 7, arm_y + 1, sk[2] if bony else sh[1])
    c.rect(0, arm_y, 1, arm_y + 1, sk[3])
    # legs, 3-frame walk
    offsets = [(0, 0), (-2, 2), (2, -2)][frame]
    c.rect(6 + offsets[0], 16, 7 + offsets[0], 22, pa[2])
    c.rect(9 + offsets[1], 16, 10 + offsets[1], 22, pa[1])
    c.rect(5 + offsets[0], 23, 7 + offsets[0], 23, sk[1] if bony else pa[0])
    c.rect(8 + offsets[1], 23, 10 + offsets[1], 23, sk[1] if bony else pa[0])
    c.light(1.2, 0.8)
    c.outline()


mob_sprite('zombie', 16, 24, 3, lambda c, f: humanoid_frame(c, f, '#7FA06A', '#4A6FA5', '#4B3B2F', (230, 40, 40, 255)),
           frame_time=6, animate='move')
mob_sprite('skeleton', 16, 24, 3, lambda c, f: humanoid_frame(c, f, '#E8E2CF', '#BDB59C', '#CFC8B0', (20, 20, 20, 255), bony=True),
           frame_time=6, animate='move')


def demon_eye_frame(c, frame):
    # eyeball facing left with a veiny tail trailing right
    c.circle(6, 6, 5, hexc('#F2EEE8'))
    c.circle(4, 6, 3, hexc('#C22B2B'))
    c.circle(3, 6, 1, hexc('#1A0A0A'))
    c.set(4, 4, hexc('#FFFFFF'))
    wave = [0, 1, 0, -1][frame % 4]
    for i, x in enumerate(range(11, 16)):
        y = 6 + (wave if i % 2 else -wave)
        c.set(x, y, hexc('#B33A3A'))
        c.set(x, y + (1 if i < 2 else 0), hexc('#8E2A2A'))
    c.set(8, 3, hexc('#D86A6A')); c.set(9, 8, hexc('#D86A6A'))   # veins
    c.light(1.15, 0.85)
    c.outline()


mob_sprite('demon_eye', 16, 12, 4, demon_eye_frame, frame_time=4, rotate=True)


def bat_frame(c, frame):
    body = palette('#6B4A3A')
    c.circle(8, 6, 2, body[2])
    c.set(6, 3, body[1]); c.set(9, 3, body[1])      # ears
    c.set(7, 6, (255, 220, 80, 255))                # eye (left side)
    wing = palette('#4E3428')
    tip = [-3, 0, 3][frame]
    for side in (-1, 1):
        for i in range(1, 7):
            x = 8 + side * (2 + i)
            y = 6 + round(tip * i / 6)
            c.set(x, y, wing[2])
            c.set(x, y + 1, wing[1])
    c.light(1.2, 0.8)
    c.outline()


mob_sprite('cave_bat', 16, 12, 3, bat_frame, frame_time=3)


def king_slime_frame(c, frame):
    slime_frame(c, '#3C78E6', frame, 32, 24, alpha=200)
    # ninja silhouette inside
    top = 8 + frame * 2
    c.rect(14, top + 4, 18, top + 9, (40, 40, 60, 160))
    c.circle(16, top + 2, 2, (40, 40, 60, 160))
    # crown
    gold = palette('#F0C030')
    cy = 2 + frame * 2
    c.polygon([(10, cy + 5), (10, cy + 1), (13, cy + 3), (16, cy - 1), (19, cy + 3), (22, cy + 1), (22, cy + 5)], gold[2])
    c.set(16, cy + 2, hexc('#E03040'))
    c.rect(10, cy + 4, 22, cy + 5, gold[1])


mob_sprite('king_slime', 32, 24, 2, king_slime_frame, frame_time=12)


def big_eye_frame(c, frame, mouth=False):
    c.circle(10, 9, 8, hexc('#F2EEE8'))
    if mouth:
        # open maw facing left with teeth
        c.polygon([(1, 4), (9, 7), (9, 11), (1, 14)], hexc('#5A0A10'))
        for y in (5, 7, 11, 13):
            c.set(2, y, hexc('#F8F0D8')); c.set(4, y + (1 if y < 9 else -1), hexc('#F8F0D8'))
    else:
        c.circle(6, 9, 4, hexc('#3A62C8'))
        c.circle(5, 9, 2, hexc('#0A0A14'))
        c.set(4, 7, hexc('#FFFFFF'))
    for vx, vy in ((12, 3), (14, 14), (16, 6)):
        c.set(vx, vy, hexc('#D86A6A'))
    wave = [0, 1, 0, -1][frame % 4]
    for i, x in enumerate(range(17, 24)):
        for strand, base in enumerate((6, 9, 12)):
            y = base + (wave if (i + strand) % 2 else -wave)
            c.set(x, y, hexc('#A83232'))
    c.light(1.15, 0.85)
    c.outline()


mob_sprite('eye_of_cthulhu', 24, 18, 4, big_eye_frame, frame_time=4, rotate=True, fullbright=True)
mob_sprite('eye_of_cthulhu_mouth', 24, 18, 4, lambda c, f: big_eye_frame(c, f, mouth=True), frame_time=3, rotate=True, fullbright=True)


def servant_frame(c, frame):
    c.circle(5, 5, 4, hexc('#F2EEE8'))
    c.circle(3, 5, 2, hexc('#3A62C8'))
    c.set(3, 5, hexc('#0A0A14'))
    wave = [0, 1, 0, -1][frame % 4]
    for i, x in enumerate(range(9, 13)):
        c.set(x, 5 + (wave if i % 2 else -wave), hexc('#A83232'))
    c.light(1.15, 0.85)
    c.outline()


mob_sprite('servant_of_cthulhu', 14, 10, 4, servant_frame, frame_time=3, rotate=True)


def maw_frame(c, frame, color):
    p = palette(color)
    c.circle(5, 6, 4, p[2])
    open_ = 1 + frame % 2
    c.rect(0, 6 - open_, 3, 6 + open_, (40, 10, 15, 255))
    for i, x in enumerate(range(9, 15)):
        c.set(x, 6 + (1 if (i + frame) % 2 else -1) * (i // 3), p[1])
        c.set(x, 6, p[2])
    c.light(1.2, 0.8)
    c.outline()


mob_sprite('eater_of_souls', 16, 12, 2, lambda c, f: maw_frame(c, f, '#6A6A4A'), frame_time=4, rotate=True)
mob_sprite('crimera', 16, 12, 2, lambda c, f: maw_frame(c, f, '#A83A3A'), frame_time=4, rotate=True)
mob_sprite('face_monster', 16, 24, 3, lambda c, f: humanoid_frame(c, f, '#B04848', '#8A2828', '#5A1818', (250, 230, 80, 255)),
           frame_time=6, animate='move')


def crawler_frame(c, frame):
    p = palette('#8A2020')
    c.circle(8, 6, 3, p[2])
    c.circle(4, 6, 2, p[1])
    c.set(3, 5, (250, 220, 60, 255))
    for i, x in enumerate((6, 8, 10, 12)):
        lift = (frame + i) % 2
        c.line(x, 8, x - 1, 11 - lift, p[0])
    c.outline()


mob_sprite('blood_crawler', 16, 12, 2, crawler_frame, frame_time=4, animate='move')
mob_sprite('blood_zombie', 16, 24, 3, lambda c, f: humanoid_frame(c, f, '#A85A5A', '#6A2020', '#3A1A1A', (255, 240, 80, 255)),
           frame_time=6, animate='move')


def drippler_frame(c, frame):
    p = palette('#C04848')
    c.circle(7, 6, 5, p[2])
    for ex, ey in ((5, 5), (9, 4), (8, 8)):
        c.set(ex, ey, (240, 220, 60, 255))
    for i, x in enumerate((4, 7, 10)):
        c.line(x, 11, x, 12 + (frame + i) % 3, p[1])
    c.outline()


mob_sprite('drippler', 14, 16, 2, drippler_frame, frame_time=8)


def worm_frame(c, frame, color, part):
    p = palette(color)
    c.circle(6, 6, 5 if part != 'tail' else 4, p[2])
    if part == 'head':
        c.rect(0, 5, 2, 7, (40, 10, 15, 255))
    for x in (3, 6, 9):
        c.line(x, 2, x, 10, p[1])
    c.outline()


mob_sprite('creeper', 14, 10, 4, servant_frame, frame_time=3, rotate=True)


def brain_frame(c, frame, exposed=False):
    p = palette('#E890A0' if not exposed else '#C85060')
    c.circle(10, 8, 7, p[2])
    c.line(10, 2, 10, 14, p[1])
    for i in range(4):
        c.line(5 + i * 3, 14, 5 + i * 3 + (frame % 2), 19, p[1])
    if exposed:
        c.circle(6, 8, 2, (250, 240, 200, 255)); c.set(5, 8, (20, 10, 10, 255))
    c.light(1.2, 0.8)
    c.outline()


mob_sprite('brain_of_cthulhu', 20, 20, 2, brain_frame, frame_time=8)
mob_sprite('brain_of_cthulhu_exposed', 20, 20, 2, lambda c, f: brain_frame(c, f, True), frame_time=6)
for _w, _col in (('devourer', '#6A5A7A'), ('giant_worm', '#B08070'), ('eater_of_worlds', '#5A4A78')):
    for _part in ('head', 'body', 'tail'):
        mob_sprite(f'{_w}_{_part}', 12, 12, 1, lambda c, f, col=_col, part=_part: worm_frame(c, f, col, part), rotate=True)


def npc_frame(c, frame, skin, hair, shirt, pants, hat=None, beard=None, cross=False):
    # 16x24 town NPC facing left, arms at the sides, 3-frame walk
    sk, hr, sh, pa = palette(skin), palette(hair), palette(shirt), palette(pants)
    c.rect(5, 2, 10, 8, sk[2])                       # head
    c.rect(5, 1, 10, 3, hr[2]); c.rect(9, 3, 10, 6, hr[2])   # hair (back of head on the right)
    c.set(5, 5, (30, 30, 40, 255))                   # eye
    c.set(5, 7, sk[1])                               # mouth
    if beard:
        bd = palette(beard)
        c.rect(5, 6, 9, 9, bd[2]); c.set(5, 7, bd[1])
    if hat:
        ht = palette(hat)
        c.rect(4, 0, 11, 2, ht[2]); c.rect(3, 2, 6, 2, ht[1])
        if cross:
            c.set(7, 1, hexc('#E03030')); c.set(8, 1, hexc('#E03030')); c.set(7, 0, hexc('#E03030'))
    c.rect(6, 9, 10, 16, sh[2])                      # torso
    if cross:
        c.rect(8, 11, 8, 13, hexc('#E03030')); c.rect(7, 12, 9, 12, hexc('#E03030'))
    swing = [0, 1, -1][frame]
    c.rect(5 + swing, 10, 5 + swing, 15, sh[1])      # front arm
    c.set(5 + swing, 16, sk[2])
    offsets = [(0, 0), (-2, 2), (2, -2)][frame]
    c.rect(6 + offsets[0], 17, 7 + offsets[0], 22, pa[2])
    c.rect(9 + offsets[1], 17, 10 + offsets[1], 22, pa[1])
    c.rect(5 + offsets[0], 23, 7 + offsets[0], 23, pa[0])
    c.rect(8 + offsets[1], 23, 10 + offsets[1], 23, pa[0])
    c.light(1.2, 0.8)
    c.outline()


mob_sprite('arms_dealer', 16, 24, 3, lambda c, f: npc_frame(c, f, '#8A5A3A', '#1A1A1A', '#7A5A3A', '#3A3A44', hat='#2A2A2A'),
           frame_time=6, animate='move')
mob_sprite('dryad', 16, 24, 3, lambda c, f: npc_frame(c, f, '#E8C8A0', '#4AA040', '#5A9A3A', '#3A7A2A'), frame_time=6, animate='move')
mob_sprite('guide', 16, 24, 3, lambda c, f: npc_frame(c, f, '#E8B890', '#6A4428', '#6E8C3A', '#5A4430'), frame_time=6, animate='move')
mob_sprite('merchant', 16, 24, 3, lambda c, f: npc_frame(c, f, '#E8B890', '#E8E8E8', '#5A6A8C', '#3A3A44', hat='#6A6A70', beard='#F0F0F0'),
           frame_time=6, animate='move')
mob_sprite('nurse', 16, 24, 3, lambda c, f: npc_frame(c, f, '#F0C8A8', '#C84838', '#F4F4F4', '#F0F0F0', hat='#F8F8F8', cross=True),
           frame_time=6, animate='move')
mob_sprite('demolitionist', 16, 24, 3, lambda c, f: npc_frame(c, f, '#D8A880', '#C86A28', '#8A5A30', '#4A3A2A', hat='#E8C030', beard='#C86A28'),
           frame_time=6, animate='move')


def mob_assets():
    for name, (fw, fh, frames, drawer, meta) in MOB_SPRITES.items():
        sheet = Canvas(fw, fh * frames)
        for f in range(frames):
            frame = Canvas(fw, fh)
            drawer(frame, f)
            sheet.img.paste(frame.img, (0, f * fh))
        sheet.save(os.path.join(TEX, 'entity/mob', name + '.png'))
        data = {'frames': frames, 'frame_time': meta.get('frame_time', 8), 'faces': 'left'}
        if meta.get('rotate'):
            data['rotate'] = True
        if meta.get('fullbright'):
            data['fullbright'] = True
        if meta.get('animate'):
            data['animate'] = meta['animate']
        write_json(os.path.join(TEX, 'entity/mob', name + '.json'), data)
    white = Canvas(4, 4)
    white.rect(0, 0, 3, 3, (255, 255, 255, 255))
    white.save(os.path.join(TEX, 'entity/white.png'))


# --- 3D model textures (box UV layouts matching the Java model classes) -----------------------------
MODEL_TEXTURES = {}


def model_texture(name, w, h, painter):
    MODEL_TEXTURES[name] = (w, h, painter)


def box_faces(u, v, w, h, d):
    """Texture regions of a Minecraft model box (x, y, width, height) per face."""
    return {
        'top': (u + d, v, w, d), 'bottom': (u + d + w, v, w, d),
        'right': (u, v + d, d, h), 'front': (u + d, v + d, w, h),
        'left': (u + d + w, v + d, d, h), 'back': (u + 2 * d + w, v + d, w, h),
    }


def fill(c, region, color, noise=None, seed=0):
    x0, y0, fw, fh = region
    rnd = random.Random(seed)
    for x in range(x0, x0 + fw):
        for y in range(y0, y0 + fh):
            col = color
            if noise:
                col = shade(color, 1.0 + (rnd.random() - 0.5) * noise)
            c.set(x, y, col)


def paint_box(c, u, v, w, h, d, color, noise=0.12, seed=0, top=None, front=None):
    for face, region in box_faces(u, v, w, h, d).items():
        base = top if face == 'top' and top else color
        fill(c, region, base, noise, seed + sum(map(ord, face)))
        if face == 'front' and front:
            front(c, *region)


def slime_texture(color, alpha=170, baby=None, king=False, core=None):
    def paint(c):
        col = hexc(color, alpha)
        light = shade(col, 1.35)
        paint_box(c, 0, 0, 14, 10, 14, col, 0.08, 1, top=light)
        paint_box(c, 0, 24, 10, 3, 10, light, 0.08, 2, top=shade(col, 1.6))
        if core:
            paint_box(c, 0, 40, 6, 6, 6, hexc(core), 0.15, 3)   # something trapped inside (Dungeon Slime's key)
        else:
            paint_box(c, 0, 40, 6, 6, 6, shade(hexc(color, 225), 0.6), 0.1, 3)
        paint_box(c, 32, 40, 2, 3, 1, (15, 15, 25, 255), 0.0)
        paint_box(c, 32, 44, 2, 3, 1, (15, 15, 25, 255), 0.0)
        if baby:
            paint_box(c, 40, 48, 4, 3, 4, hexc(baby, 220), 0.1, 4)
        if king:
            gold = hexc('#F0C030')
            paint_box(c, 64, 0, 9, 2, 9, gold, 0.15, 5, top=shade(gold, 1.2))
            paint_box(c, 64, 16, 1, 2, 1, gold, 0.1)
            paint_box(c, 70, 16, 1, 3, 1, hexc('#E03040'), 0.1)
            paint_box(c, 64, 24, 3, 4, 2, (35, 35, 55, 235), 0.1)
            paint_box(c, 80, 24, 3, 3, 3, (35, 35, 55, 235), 0.1)
    return paint


for _name, _color in SLIMES.items():
    model_texture(_name, 64, 64, slime_texture(_color, 200 if _name == 'black_slime' else 170))
model_texture('baby_slime', 64, 64, slime_texture('#4A4A54', 200))
model_texture('mother_slime', 64, 64, slime_texture('#3C3C44', 210, baby='#5A5A64'))
model_texture('king_slime', 128, 64, slime_texture('#3C78E6', 180, king=True))


def skin_texture(skin, hair, shirt, pants, shoes='#3A2A20', eyes=(30, 30, 40, 255), beard=None, hat=None, hat_band=None,
                 torn=False, bony=False, short_sleeves=True, cross=False, lamp=False, **extra):
    """Humanoid skins: people and goblins use the detailed painter in tools/skins.py; skeletons keep this one."""
    if not bony:
        import skins
        return skins.skin_texture(skin, hair, shirt, pants, shoes=shoes, eyes=eyes, beard=beard, hat=hat, hat_band=hat_band, torn=torn,
                                  short_sleeves=short_sleeves, cross=cross, lamp=lamp, **extra)

    def paint(c):
        sk, hr, sh, pa = hexc(skin), hexc(hair), hexc(shirt), hexc(pants)
        # head (0,0) 8x8x8
        paint_box(c, 0, 0, 8, 8, 8, sk, 0.08, 10, top=hr)
        for face in ('right', 'left', 'back'):
            x, y, fw, fh = box_faces(0, 0, 8, 8, 8)[face]
            fill(c, (x, y, fw, 3 if face != 'back' else 6), hr, 0.1, 11)
        fx, fy = 8, 8
        fill(c, (fx, fy, 8, 2), hr, 0.1, 12)                       # fringe
        if bony:
            fill(c, (fx + 1, fy + 3, 2, 2), (25, 20, 20, 255)); fill(c, (fx + 5, fy + 3, 2, 2), (25, 20, 20, 255))
            fill(c, (fx + 3, fy + 5, 2, 1), (60, 50, 45, 255)); fill(c, (fx + 2, fy + 7, 4, 1), (200, 195, 180, 255))
        else:
            c.set(fx + 2, fy + 4, (245, 245, 245, 255)); c.set(fx + 1, fy + 4, eyes)
            c.set(fx + 5, fy + 4, eyes); c.set(fx + 6, fy + 4, (245, 245, 245, 255))
            fill(c, (fx + 3, fy + 6, 2, 1), shade(sk, 0.7))
        if beard:
            bd = hexc(beard)
            fill(c, (fx, fy + 5, 8, 3), bd, 0.1, 13)
            c.set(fx + 3, fy + 5, shade(sk, 0.7)); c.set(fx + 4, fy + 5, shade(sk, 0.7))
            for face in ('right', 'left'):
                x, y, fw, fh = box_faces(0, 0, 8, 8, 8)[face]
                fill(c, (x, y + 5, fw, 3), bd, 0.1, 14)
        if hat:
            ht = hexc(hat)
            # hat layer (32,0): only the top rows of the overlay are painted
            for face, (x, y, fw, fh) in box_faces(32, 0, 8, 8, 8).items():
                if face == 'top':
                    fill(c, (x, y, fw, fh), ht, 0.1, 15)
                elif face != 'bottom':
                    fill(c, (x, y, fw, 3), ht, 0.1, 16)
                    if hat_band:
                        fill(c, (x, y + 2, fw, 1), hexc(hat_band))
            if cross:
                fill(c, (43, 8, 2, 3), hexc('#E03030')); fill(c, (42, 9, 4, 1), hexc('#E03030'))
            if lamp:
                fill(c, (43, 9, 2, 2), hexc('#FFF8C0'))
        # body (16,16) 8x12x4
        paint_box(c, 16, 16, 8, 12, 4, sh, 0.1, 20)
        if torn:
            rnd = random.Random(21)
            for _ in range(10):
                c.set(20 + rnd.randint(0, 7), 20 + rnd.randint(0, 11), sk)
        if cross:
            fill(c, (23, 22, 2, 5), hexc('#E03030')); fill(c, (22, 23, 4, 2), hexc('#E03030'))
        fill(c, (20, 30, 8, 2), shade(pa, 0.8))                       # belt
        # arms (40,16) 4x12x4
        arm = sk if bony else sh
        paint_box(c, 40, 16, 4, 12, 4, arm, 0.1, 30)
        if not bony:
            for face, (x, y, fw, fh) in box_faces(40, 16, 4, 12, 4).items():
                if face not in ('top', 'bottom'):
                    fill(c, (x, y + (4 if short_sleeves else 10), fw, fh - (4 if short_sleeves else 10)), sk, 0.08, 31)
        # legs (0,16) 4x12x4
        paint_box(c, 0, 16, 4, 12, 4, sk if bony else pa, 0.1, 40)
        for face, (x, y, fw, fh) in box_faces(0, 16, 4, 12, 4).items():
            if face not in ('top', 'bottom'):
                fill(c, (x, y + fh - 2, fw, 2), shade(hexc(shoes), 1.0) if not bony else shade(sk, 0.85), 0.05, 41)
        if bony:
            for face, (x, y, fw, fh) in list(box_faces(0, 16, 4, 12, 4).items()) + list(box_faces(40, 16, 4, 12, 4).items()):
                if face not in ('top', 'bottom'):
                    fill(c, (x, y + 5, fw, 1), shade(sk, 0.65))
            for y in (19, 22, 25):
                fill(c, (20, y, 8, 1), shade(sk, 0.6))
    return paint


model_texture('zombie', 64, 64, skin_texture('#7FA06A', '#2E3A22', '#4A6FA5', '#4B3B2F', eyes=(230, 40, 40, 255), torn=True, shoes='#2A2018'))
model_texture('skeleton', 64, 64, skin_texture('#D8D0A8', '#D8D0A8', '#3A5AA0', '#D8D0A8', bony=True, torn=True))
model_texture('arms_dealer', 64, 64, skin_texture('#8A5A3A', '#1A1A1A', '#C8B890', '#3A3A44', hat='#2A2A2A', hat_band='#8A1A1A', short_sleeves=False,
                                                   jacket='#5A3A2A', straps='#2A1A10'))
model_texture('dryad', 64, 64, skin_texture('#E8C8A0', '#4AA040', '#5A9A3A', '#3A7A2A', shoes='#2A5A1A', hat='#6AC050', hair_style='long',
                                             apron='#8AC060', belt='#3A6A2A', buckle='#E070A0'))
model_texture('guide', 64, 64, skin_texture('#E8B890', '#6A4428', '#C8B080', '#5A4430', short_sleeves=False, jacket='#6E8C3A',
                                             iris=(60, 110, 60, 255)))
model_texture('merchant', 64, 64, skin_texture('#E8B890', '#E8E8E8', '#C8C0B0', '#3A3A44', beard='#F0F0F0', hat='#6A6A70', short_sleeves=False,
                                                jacket='#5A6A8C'))
model_texture('nurse', 64, 64, skin_texture('#F0C8A8', '#C84838', '#F4F4F4', '#F0F0F0', shoes='#F0F0F0', hat='#F8F8F8', cross=True,
                                             hair_style='long', belt='#E8E8E8', buckle='#E03030'))
model_texture('demolitionist', 64, 64, skin_texture('#D8A880', '#C86A28', '#B88A50', '#4A3A2A', beard='#C86A28', hat='#E8C030',
                                                     hat_band='#7A6018', lamp=True, suspenders='#5A3A1A'))


def eye_texture(iris, mouth=False, sclera='#F2EEE8'):
    def paint(c):
        white = hexc(sclera)
        vein = hexc('#C03838')
        for i, (u, v, w, h, d) in enumerate([(0, 0, 8, 8, 8), (0, 16, 10, 6, 6), (32, 0, 6, 10, 6), (0, 28, 6, 6, 10)]):
            paint_box(c, u, v, w, h, d, white, 0.06, 50 + i)
        rnd = random.Random(55)
        for _ in range(60):
            x, y = rnd.randint(0, 63), rnd.randint(0, 43)
            if c.get(x, y)[3] > 0 and rnd.random() < 0.5:
                c.set(x, y, vein)
        # front of the forward slab (6x6 at 10,38) and of the core (8x8 at 8,8)
        if mouth:
            fill(c, (10, 38, 6, 6), hexc('#4A0810'))
            for x in range(10, 16, 2):
                c.set(x, 38, (245, 240, 220, 255)); c.set(x + 1, 43, (245, 240, 220, 255))
            fill(c, (8, 8, 8, 8), hexc('#7A1A20'), 0.15, 56)
            for x in range(8, 16, 2):
                c.set(x, 8, (245, 240, 220, 255)); c.set(x + 1, 15, (245, 240, 220, 255))
        else:
            ir = hexc(iris)
            fill(c, (10, 38, 6, 6), ir, 0.15, 57)
            fill(c, (12, 40, 2, 2), (10, 10, 15, 255))
            c.set(11, 39, (255, 255, 255, 255))
            fill(c, (9, 9, 6, 6), shade(ir, 0.8))
        for i in range(3):
            paint_box(c, 40, 20 + i * 10, 1, 1, 8, hexc('#A83232'), 0.2, 60 + i)
    return paint


model_texture('demon_eye', 64, 64, eye_texture('#3A3AA8'))
model_texture('servant_of_cthulhu', 64, 64, eye_texture('#3A62C8'))
model_texture('eye_of_cthulhu', 64, 64, eye_texture('#3A62C8'))
model_texture('eye_of_cthulhu_mouth', 64, 64, eye_texture('#3A62C8', mouth=True))


def worm_texture(color, plate):
    def paint(c):
        body = hexc(color)
        pl = hexc(plate)
        paint_box(c, 0, 0, 10, 10, 12, body, 0.15, 80, top=pl)
        x, y, w, h = box_faces(0, 0, 10, 10, 12)['front']
        fill(c, (x + 2, y + 3, 6, 4), (40, 10, 15, 255))                  # mouth
        for tx in range(x + 2, x + 8, 2):
            c.set(tx, y + 3, (235, 225, 200, 255))
        paint_box(c, 48, 0, 1, 2, 5, hexc('#E8DCC0'), 0.1)
        paint_box(c, 0, 24, 10, 10, 10, body, 0.15, 81, top=pl)
        for face in ('right', 'left', 'top', 'bottom'):
            fx, fy, fw, fh = box_faces(0, 24, 10, 10, 10)[face]
            for i in range(0, fw, 4):
                fill(c, (fx + i, fy, 1, fh), shade(body, 0.6))
        paint_box(c, 0, 44, 8, 8, 10, shade(body, 0.9), 0.15, 82, top=pl)
        paint_box(c, 40, 44, 4, 4, 4, shade(body, 0.8), 0.1, 83)
    return paint


for _part in ('head', 'body', 'tail'):
    model_texture(f'devourer_{_part}', 64, 64, worm_texture('#6A5A7A', '#8A7A9A'))
    model_texture(f'giant_worm_{_part}', 64, 64, worm_texture('#B08070', '#C89888'))


def maw_texture(color, accent):
    def paint(c):
        body = hexc(color)
        paint_box(c, 0, 0, 8, 8, 8, body, 0.18, 90)
        x, y, w, h = box_faces(0, 0, 8, 8, 8)['front']
        fill(c, (x + 1, y + 1, 6, 6), (35, 8, 12, 255))
        fill(c, (x + 2, y + 2, 4, 4), hexc(accent))
        paint_box(c, 0, 16, 6, 6, 6, shade(body, 0.85), 0.18, 91)
        paint_box(c, 24, 16, 4, 4, 5, shade(body, 0.7), 0.18, 92)
        paint_box(c, 40, 0, 1, 1, 5, hexc('#E8DCC0'), 0.1)
    return paint


for _part in ('head', 'body', 'tail'):
    model_texture(f'eater_of_worlds_{_part}', 64, 64, worm_texture('#5A4A78', '#7E5BA8'))
model_texture('creeper', 64, 64, eye_texture('#E07030', sclera='#F0E8E0'))


def brain_texture(exposed=False):
    def paint(c):
        pink = hexc('#E890A0' if not exposed else '#C85060')
        dark = shade(pink, 0.7)
        for u, v in ((0, 0), (0, 26)):
            paint_box(c, u, v, 7, 10, 16, pink, 0.12, 110 + v)
            for face, (x, y, w, h) in box_faces(u, v, 7, 10, 16).items():
                rnd = random.Random(x * 7 + y)
                for _ in range(w * h // 4):
                    px, py = x + rnd.randint(0, w - 1), y + rnd.randint(0, h - 1)
                    c.set(px, py, dark)
        if exposed:
            x, y, w, h = box_faces(0, 0, 7, 10, 16)['front']
            fill(c, (x + 2, y + 3, 4, 4), (250, 240, 200, 255))
            fill(c, (x + 3, y + 4, 2, 2), (20, 10, 10, 255))
        paint_box(c, 46, 0, 4, 5, 4, hexc('#C86070'), 0.1, 120)
        paint_box(c, 46, 10, 1, 9, 1, hexc('#A04050'), 0.1, 121)
    return paint


model_texture('brain_of_cthulhu', 64, 64, brain_texture())
model_texture('brain_of_cthulhu_exposed', 64, 64, brain_texture(True))
model_texture('blood_zombie', 64, 64, skin_texture('#D05050', '#6A1A1A', '#4A2A24', '#3A2420', eyes=(255, 240, 80, 255), torn=True, blood=True))
model_texture('drippler', 64, 64, eye_texture('#3A62C8', sclera='#C84848'))


# ----------------------------------------------------------------------------------------- Dungeon (Stage 4a)
BRICKS = {'blue_brick': '#4A5AA8', 'green_brick': '#4A8A5A', 'pink_brick': '#A85A8A'}


def brick_texture(color, seed):
    base = hexc(color)
    c = Canvas()
    c.noise([base, base, base, shade(base, 1.06), shade(base, 0.94)], seed)
    mortar = shade(base, 0.55)
    for y in (0, 4, 8, 12):
        c.line(0, y, 15, y, mortar)
        offset = 0 if (y // 4) % 2 == 0 else 4
        for x in range(offset, 16, 8):
            c.line(x, y, x, y + 3, mortar)
    for y in (1, 5, 9, 13):
        for x in range(16):
            if c.get(x, y) != mortar:
                c.set(x, y, shade(base, 1.14))
    for y in (3, 7, 11, 15):
        for x in range(16):
            if c.get(x, y) != mortar:
                c.set(x, y, shade(base, 0.86))
    return c


for _name, _color in BRICKS.items():
    BLOCK_TEXTURES[_name] = lambda col=_color, seed=len(_name) * 77: brick_texture(col, seed)


def spikes_texture():
    c = Canvas()
    p = palette('#9AA0AA')
    c.rect(0, 0, 15, 15, p[1])
    for x in range(0, 16, 4):
        c.polygon([(x, 15), (x + 2, 2), (x + 3, 15)], p[3])
        c.line(x + 2, 2, x + 2, 6, p[4])
    return c


def gold_chest_texture():
    c = Canvas()
    g = palette('#D8A830')
    c.noise([g[2], g[2], g[2], shade(g[2], 1.08)], 991)
    c.rect(0, 0, 15, 1, g[4]); c.rect(0, 14, 15, 15, g[0])
    c.rect(0, 7, 15, 8, palette('#6A5020')[2])
    for x in (0, 15):
        c.rect(x, 0, x, 15, g[1])
    return c


def gold_lock_texture():
    c = Canvas()
    c.rect(0, 0, 15, 15, palette('#7A7A82')[2])
    c.rect(6, 6, 9, 10, (20, 20, 20, 255))
    c.set(7, 7, (60, 60, 60, 255))
    return c


def bookshelf_texture(books):
    """Dark dungeon bookcase: two shelves of coloured book spines (or bare shelves once taken)."""
    c = Canvas()
    wood = palette('#5A3A28')
    c.rect(0, 0, 15, 15, wood[1])
    c.rect(0, 0, 15, 1, wood[3]); c.rect(0, 14, 15, 15, wood[0])
    c.rect(0, 7, 15, 8, wood[2])
    c.rect(0, 0, 0, 15, wood[2]); c.rect(15, 0, 15, 15, wood[0])
    if books:
        rnd = random.Random(4417)
        colours = ['#3A50A0', '#A03A3A', '#3A8A50', '#8A6A30', '#6A3A8A', '#2A6A8A']
        for top in (2, 9):
            x = 1
            while x < 15:
                w = rnd.choice((1, 2, 2))
                h = rnd.choice((4, 5, 5))
                col = palette(rnd.choice(colours))
                c.rect(x, top + 5 - h, min(14, x + w - 1), top + 4, col[2])
                c.rect(x, top + 5 - h, x, top + 4, col[3])
                c.set(x, top + 6 - h, col[4])
                x += w
    return c


BLOCK_TEXTURES['dungeon_bookshelf'] = lambda: bookshelf_texture(True)
BLOCK_TEXTURES['dungeon_bookshelf_empty'] = lambda: bookshelf_texture(False)
BLOCK_TEXTURES['dungeon_bookshelf_top'] = lambda: (lambda c: (c.noise(palette('#5A3A28')[1:4], 3381), c)[1])(Canvas())
BLOCK_TEXTURES['spikes'] = spikes_texture
BLOCK_TEXTURES['locked_gold_chest'] = gold_chest_texture
BLOCK_TEXTURES['locked_gold_chest_lock'] = gold_lock_texture


def dungeon_block_assets():
    for name in BRICKS:
        write_json(os.path.join(ASSETS, 'blockstates', name + '.json'), {'variants': {'': {'model': f'terracraft:block/{name}'}}})
        write_json(os.path.join(ASSETS, 'models/block', name + '.json'),
                   {'parent': 'minecraft:block/cube_all', 'textures': {'all': f'terracraft:block/{name}'}})
        write_json(os.path.join(ASSETS, 'items', name + '.json'), {'model': {'type': 'minecraft:model', 'model': f'terracraft:block/{name}'}})
    all_faces = ['north', 'south', 'east', 'west', 'up', 'down']
    for model, side in (('dungeon_bookshelf', 'dungeon_bookshelf'), ('dungeon_bookshelf_empty', 'dungeon_bookshelf_empty')):
        write_json(os.path.join(ASSETS, f'models/block/{model}.json'), {'parent': 'minecraft:block/cube_column',
                   'textures': {'end': 'terracraft:block/dungeon_bookshelf_top', 'side': f'terracraft:block/{side}'}})
    write_json(os.path.join(ASSETS, 'blockstates/dungeon_bookshelf.json'), {'variants': {
        'books=true': {'model': 'terracraft:block/dungeon_bookshelf'}, 'books=false': {'model': 'terracraft:block/dungeon_bookshelf_empty'}}})
    write_json(os.path.join(ASSETS, 'items/dungeon_bookshelf.json'), {'model': {'type': 'minecraft:model', 'model': 'terracraft:block/dungeon_bookshelf_empty'}})
    spikes = [{'from': [0, 0, 0], 'to': [16, 2, 16], 'faces': {f: {'texture': '#all'} for f in all_faces}}]
    for x in (2, 7, 12):
        for z in (2, 7, 12):
            spikes.append({'from': [x, 2, z], 'to': [x + 2, 5, z + 2], 'faces': {f: {'texture': '#all'} for f in all_faces}})
            spikes.append({'from': [x + 0.5, 5, z + 0.5], 'to': [x + 1.5, 8, z + 1.5], 'faces': {f: {'texture': '#all'} for f in all_faces}})
    write_json(os.path.join(ASSETS, 'models/block/spikes.json'), {'parent': 'minecraft:block/block',
               'textures': {'particle': 'terracraft:block/spikes', 'all': 'terracraft:block/spikes'}, 'elements': spikes})
    write_json(os.path.join(ASSETS, 'blockstates/spikes.json'), {'variants': {'': {'model': 'terracraft:block/spikes'}}})
    write_json(os.path.join(ASSETS, 'items/spikes.json'), {'model': {'type': 'minecraft:model', 'model': 'terracraft:block/spikes'}})
    chest = [
        {'from': [1, 0, 1], 'to': [15, 10, 15], 'faces': {f: {'texture': '#body'} for f in all_faces}},
        {'from': [1, 10, 1], 'to': [15, 14, 15], 'faces': {f: {'texture': '#body'} for f in all_faces}},
        {'from': [7, 7, 0], 'to': [9, 11, 1], 'faces': {f: {'texture': '#lock'} for f in all_faces}},
    ]
    write_json(os.path.join(ASSETS, 'models/block/locked_gold_chest.json'), {'parent': 'minecraft:block/block', 'textures': {
        'particle': 'terracraft:block/locked_gold_chest', 'body': 'terracraft:block/locked_gold_chest', 'lock': 'terracraft:block/locked_gold_chest_lock'},
        'elements': chest})
    write_json(os.path.join(ASSETS, 'blockstates/locked_gold_chest.json'), {'variants': {
        'facing=north': {'model': 'terracraft:block/locked_gold_chest'},
        'facing=east': {'model': 'terracraft:block/locked_gold_chest', 'y': 90},
        'facing=south': {'model': 'terracraft:block/locked_gold_chest', 'y': 180},
        'facing=west': {'model': 'terracraft:block/locked_gold_chest', 'y': 270}}})
    write_json(os.path.join(ASSETS, 'items/locked_gold_chest.json'), {'model': {'type': 'minecraft:model', 'model': 'terracraft:block/locked_gold_chest'}})


def draw_key(c, color):
    p = palette(color)
    c.circle(5, 5, 3.5, p[2])
    c.circle(5, 5, 1.5, (0, 0, 0, 0))
    c.line(7, 7, 13, 13, p[2], 2)
    c.rect(11, 12, 12, 14, p[2]); c.rect(9, 10, 10, 12, p[2])
    c.set(4, 3, p[4])
    c.outline()


def draw_skull_book(c):
    draw_book(c, '#6A4A30')
    bone = palette('#E8E0C8')
    c.circle(7.5, 7.5, 2.6, bone[2])
    c.set(6, 7, (20, 20, 20, 255)); c.set(9, 7, (20, 20, 20, 255))
    c.rect(6, 10, 9, 10, bone[1])


def draw_water_book(c):
    p = palette('#2860C8')
    c.rect(3, 2, 12, 13, p[2])
    c.rect(3, 2, 4, 13, p[1])
    c.rect(11, 3, 12, 12, hexc('#E8E0C8'))
    c.circle(8, 8, 2.5, hexc('#70C8FF'))
    c.set(7, 7, hexc('#E8FFFF'))
    c.outline()


def draw_katana(c):
    blade = palette('#78A8E8')
    c.line(4, 11, 13, 2, blade[3], 1)
    c.line(5, 11, 14, 2, blade[2], 1)
    c.line(5, 12, 14, 3, blade[1], 1)
    c.rect(3, 10, 6, 13, palette('#3A3A50')[2])
    c.line(1, 15, 4, 12, palette('#2A2A3A')[2], 2)
    c.light()
    c.outline()


item('golden_key', lambda c: draw_key(c, '#E8C040'))
item('shadow_key', lambda c: draw_key(c, '#6A5A8A'))
item('muramasa', draw_katana, True)
item('handgun', lambda c: draw_gun(c, palette('#3A3A44')))
item('aqua_scepter', lambda c: draw_staff(c, '#50A8F0'), True)
item('water_bolt', draw_water_book)
item('book_of_skulls', draw_skull_book)


def p_skull(c, bone='#E8E0C8', eyes=(20, 20, 20, 255), glow=None):
    p = palette(bone)
    if glow:
        c.circle(7.5, 7.5, 7, (*hexc(glow)[:3], 70))
    c.circle(7.5, 7, 5, p[2])
    c.rect(5, 10, 10, 13, p[2])
    c.rect(5, 6, 6, 8, eyes); c.rect(9, 6, 10, 8, eyes)
    c.set(7, 10, p[0]); c.set(8, 10, p[0])
    for x in (5, 7, 9):
        c.set(x, 13, p[0])
    c.outline()


proj('water_bolt', lambda c: p_orb(c, '#50A8FF', 3))
proj('aqua_stream', lambda c: p_orb(c, '#70C8FF', 2))
proj('water_sphere', lambda c: p_orb(c, '#3C78F0', 4))
proj('book_skull', lambda c: p_skull(c, glow='#A050FF'))
proj('skeletron_skull', lambda c: p_skull(c, eyes=(200, 40, 40, 255), glow='#FF5030'))


def skull_texture(bone, eyes, glow=False):
    def paint(c):
        b = hexc(bone)
        paint_box(c, 0, 0, 10, 8, 10, b, 0.1, 501)
        paint_box(c, 0, 18, 8, 3, 8, shade(b, 0.92), 0.1, 502)
        fx, fy, fw, fh = box_faces(0, 0, 10, 8, 10)['front']
        socket = (15, 12, 12, 255)
        fill(c, (fx + 1, fy + 3, 3, 3), socket); fill(c, (fx + 6, fy + 3, 3, 3), socket)
        if eyes:
            c.set(fx + 2, fy + 4, hexc(eyes)); c.set(fx + 7, fy + 4, hexc(eyes))
            if glow:
                c.set(fx + 1, fy + 4, hexc(eyes)); c.set(fx + 8, fy + 4, hexc(eyes))
        fill(c, (fx + 4, fy + 6, 2, 2), socket)
        for x in range(fx + 1, fx + fw - 1, 2):
            c.set(x, fy + fh - 1, shade(b, 1.15)); c.set(x + 1, fy + fh - 1, socket)
        jx, jy, jw, jh = box_faces(0, 18, 8, 3, 8)['front']
        for x in range(jx, jx + jw, 2):
            c.set(x, jy, shade(b, 1.15)); c.set(x + 1, jy, socket)
        # cracks
        tx, ty, tw, th = box_faces(0, 0, 10, 8, 10)['top']
        c.line(tx + 3, ty + 2, tx + 5, ty + 6, shade(b, 0.6)); c.line(fx + 7, fy, fx + 8, fy + 2, shade(b, 0.6))
    return paint


def bone_hand_texture(bone):
    def paint(c):
        b = hexc(bone)
        paint_box(c, 0, 0, 6, 5, 3, b, 0.1, 511)
        paint_box(c, 0, 10, 1, 5, 1, b, 0.1, 512)
        paint_box(c, 4, 10, 1, 4, 1, b, 0.1, 513)
        paint_box(c, 8, 10, 2, 9, 2, shade(b, 0.95), 0.1, 514)
        for face, (x, y, w, h) in box_faces(8, 10, 2, 9, 2).items():
            if face not in ('top', 'bottom'):
                fill(c, (x, y + 4, w, 1), shade(b, 0.7))
        fx, fy, fw, fh = box_faces(0, 0, 6, 5, 3)['front']
        c.line(fx + 1, fy + 1, fx + 1, fy + 4, shade(b, 0.7)); c.line(fx + 4, fy + 1, fx + 4, fy + 4, shade(b, 0.7))
    return paint


model_texture('skeletron', 64, 32, skull_texture('#D8CCA0', '#200A0A'))
model_texture('dungeon_guardian', 64, 32, skull_texture('#D8CCA0', '#1A0A0A'))
model_texture('cursed_skull', 64, 32, skull_texture('#D0C49A', '#70B8FF', glow=True))
model_texture('skeletron_hand', 32, 32, bone_hand_texture('#D8CCA0'))
model_texture('angry_bones', 64, 64, skin_texture('#D8D0A0', '#D8D0A0', '#A0303A', '#8A2A30', eyes=(200, 30, 30, 255), bony=True))
model_texture('dark_caster', 64, 64, skin_texture('#D8D0C0', '#2A3A8A', '#2A3A8A', '#24306A', shoes='#24306A', eyes=(120, 200, 255, 255),
                                                  hat='#2A3A8A', hat_band='#6A8AE0', short_sleeves=False, robe='#2A3A8A',
                                                  hair_style='bald', belt='#6A8AE0', buckle='#C0E0FF'))
model_texture('old_man', 64, 64, skin_texture('#E8C8A8', '#E8E8E8', '#3A3A58', '#2A2A40', beard='#F4F4F4', short_sleeves=False, robe='#3A3A58',
                                               belt='#5A4A3A'))
model_texture('clothier', 64, 64, skin_texture('#E8B890', '#2A2A2A', '#E8E8E8', '#2A2A30', hat='#1A1A1A', hat_band='#A02828',
                                                short_sleeves=False, jacket='#A02828', collar='#E8E8E8'))
model_texture('dungeon_slime', 64, 64, slime_texture('#8A86D0', 190, core='#E8C040'))


def skull_frame(c, frame, bone, eyes, size=16):
    p = palette(bone)
    r = size / 2 - 1
    c.circle(size / 2, size / 2 - 1, r, p[2])
    c.rect(int(size * 0.3), int(size * 0.7), int(size * 0.7), size - 2, p[2])
    ey = int(size * 0.45)
    c.rect(int(size * 0.22), ey, int(size * 0.38), ey + 2, (20, 15, 15, 255))
    c.rect(int(size * 0.58), ey, int(size * 0.74), ey + 2, (20, 15, 15, 255))
    if eyes:
        c.set(int(size * 0.3), ey + 1, hexc(eyes)); c.set(int(size * 0.66), ey + 1, hexc(eyes))
    jaw = size - 2 + (frame % 2)
    for x in range(int(size * 0.3), int(size * 0.7) + 1, 2):
        c.set(x, min(size - 1, jaw), p[0])
    c.light(1.2, 0.8)
    c.outline()


def hand_frame(c, frame):
    p = palette('#E8E0C8')
    c.rect(3, 0, 4, 8, p[2]); c.rect(7, 0, 8, 8, p[2])
    c.rect(2, 8, 9, 12, p[2])
    curl = frame % 2
    for i, x in enumerate((2, 4, 6, 8)):
        c.rect(x, 13, x, 16 - curl, p[2])
    c.rect(0, 9, 1, 10, p[2])
    c.light(1.2, 0.8)
    c.outline()


mob_sprite('skeletron', 24, 24, 2, lambda c, f: skull_frame(c, f, '#E8E0C8', None, 24), frame_time=8)
mob_sprite('dungeon_guardian', 20, 20, 2, lambda c, f: skull_frame(c, f, '#D8D0BC', '#FF3020', 20), frame_time=6, fullbright=True)
mob_sprite('cursed_skull', 14, 14, 2, lambda c, f: skull_frame(c, f, '#B8D8C0', '#40FF80', 14), frame_time=6, fullbright=True)
mob_sprite('skeletron_hand', 11, 18, 2, hand_frame, frame_time=10)
mob_sprite('angry_bones', 16, 24, 3, lambda c, f: humanoid_frame(c, f, '#C8CED6', '#A8B0BA', '#B8C0C8', (20, 20, 20, 255), bony=True),
           frame_time=6, animate='move')
mob_sprite('dark_caster', 16, 24, 3, lambda c, f: npc_frame(c, f, '#D8D0C0', '#2A3A8A', '#2A3A8A', '#24306A', hat='#2A3A8A'), frame_time=8)
mob_sprite('dungeon_slime', 16, 12, 2, lambda c, f: slime_frame(c, '#5A6A88', f, 16, 12, baby='#E8C040'), frame_time=10)
mob_sprite('old_man', 16, 24, 3, lambda c, f: npc_frame(c, f, '#E8C8A8', '#E8E8E8', '#3A3A58', '#2A2A40', beard='#F4F4F4'),
           frame_time=6, animate='move')
mob_sprite('clothier', 16, 24, 3, lambda c, f: npc_frame(c, f, '#E8B890', '#2A2A2A', '#A02828', '#2A2A30', hat='#1A1A1A'),
           frame_time=6, animate='move')


# ----------------------------------------------------------------------------------------- Jungle (Stage 4b)
def jungle_grass_top():
    c = Canvas()
    p = palette('#4CB830')
    c.noise([p[2], p[2], p[1], p[3]], 611)
    return c


def jungle_grass_side():
    c = Canvas()
    mud = palette('#4A3A3A')
    c.noise([mud[2], mud[2], mud[1], mud[3]], 612)
    p = palette('#4CB830')
    rnd = random.Random(613)
    for x in range(16):
        for y in range(3 + rnd.randint(0, 3)):
            c.set(x, y, rnd.choice([p[2], p[1], p[3]]))
    return c


def spores_texture():
    c = Canvas()
    stem = palette('#3A8A2A')
    c.line(7, 15, 7, 6, stem[2]); c.line(8, 15, 9, 9, stem[1])
    c.line(7, 10, 4, 7, stem[2]); c.line(8, 11, 12, 8, stem[2])
    glow = palette('#B8F040')
    for x, y in ((7, 5), (4, 6), (12, 7), (10, 9), (5, 9)):
        c.circle(x, y, 1.2, glow[3])
        c.set(x, y, glow[4])
    return c


def hive_texture():
    c = Canvas()
    p = palette('#E0A020')
    c.rect(0, 0, 15, 15, p[1])
    for row in range(4):
        for col in range(3):
            cx = col * 6 + (3 if row % 2 else 0)
            cy = row * 4 + 2
            for dx in range(-2, 3):
                for dy in range(-1, 2):
                    x, y = (cx + dx) % 16, cy + dy
                    if 0 <= y < 16:
                        c.set(x, y, p[3] if abs(dx) + abs(dy) < 3 else p[2])
    return c


def larva_texture():
    c = Canvas()
    p = palette('#F0E0A0')
    c.rect(0, 0, 15, 15, p[2])
    for y in range(0, 16, 3):
        c.line(0, y, 15, y, p[1])
    c.circle(8, 4, 2, p[3])
    c.set(7, 3, (30, 20, 10, 255)); c.set(9, 3, (30, 20, 10, 255))
    return c


BLOCK_TEXTURES['jungle_grass_top'] = jungle_grass_top
BLOCK_TEXTURES['jungle_grass_side'] = jungle_grass_side
BLOCK_TEXTURES['jungle_spores_plant'] = spores_texture
BLOCK_TEXTURES['hive'] = hive_texture
BLOCK_TEXTURES['larva'] = larva_texture


def jungle_block_assets():
    write_json(os.path.join(ASSETS, 'blockstates/jungle_grass.json'), {'variants': {'': {'model': 'terracraft:block/jungle_grass'}}})
    write_json(os.path.join(ASSETS, 'models/block/jungle_grass.json'), {'parent': 'minecraft:block/cube_bottom_top', 'textures': {
        'top': 'terracraft:block/jungle_grass_top', 'side': 'terracraft:block/jungle_grass_side', 'bottom': 'minecraft:block/mud'}})
    write_json(os.path.join(ASSETS, 'items/jungle_grass.json'), {'model': {'type': 'minecraft:model', 'model': 'terracraft:block/jungle_grass'}})
    write_json(os.path.join(ASSETS, 'blockstates/hive.json'), {'variants': {'': {'model': 'terracraft:block/hive'}}})
    write_json(os.path.join(ASSETS, 'models/block/hive.json'), {'parent': 'minecraft:block/cube_all', 'textures': {'all': 'terracraft:block/hive'}})
    write_json(os.path.join(ASSETS, 'items/hive.json'), {'model': {'type': 'minecraft:model', 'model': 'terracraft:block/hive'}})
    faces = ['north', 'south', 'east', 'west', 'up', 'down']
    write_json(os.path.join(ASSETS, 'blockstates/larva.json'), {'variants': {'': {'model': 'terracraft:block/larva'}}})
    write_json(os.path.join(ASSETS, 'models/block/larva.json'), {'parent': 'minecraft:block/block',
        'textures': {'particle': 'terracraft:block/larva', 'all': 'terracraft:block/larva'}, 'elements': [
            {'from': [3, 0, 3], 'to': [13, 10, 13], 'faces': {f: {'texture': '#all'} for f in faces}},
            {'from': [4, 10, 4], 'to': [12, 14, 12], 'faces': {f: {'texture': '#all'} for f in faces}}]})
    write_json(os.path.join(ASSETS, 'items/larva.json'), {'model': {'type': 'minecraft:model', 'model': 'terracraft:block/larva'}})
    name = 'jungle_spores_plant'
    write_json(os.path.join(ASSETS, 'blockstates', name + '.json'), {'variants': {'': {'model': f'terracraft:block/{name}'}}})
    write_json(os.path.join(ASSETS, 'models/block', name + '.json'),
               {'parent': 'minecraft:block/cross', 'render_type': 'minecraft:cutout', 'textures': {'cross': f'terracraft:block/{name}'}})
    write_json(os.path.join(ASSETS, 'items', name + '.json'), {'model': {'type': 'minecraft:model', 'model': f'terracraft:item/{name}'}})
    write_json(os.path.join(ASSETS, 'models/item', name + '.json'), {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'terracraft:block/{name}'}})


def draw_spore_item(c):
    glow = palette('#A8E838')
    for x, y, r in ((6, 6, 2.5), (10, 9, 2.0), (5, 11, 1.8), (11, 4, 1.5)):
        c.circle(x, y, r, glow[2])
        c.set(int(x) - 1, int(y) - 1, glow[4])
    c.outline()


def draw_stinger(c):
    p = palette('#3A3A30')
    c.polygon([(3, 13), (12, 3), (13, 4), (5, 14)], p[2])
    c.line(4, 13, 12, 4, p[3])
    c.set(13, 2, p[1])
    c.outline()


def draw_wax(c):
    p = palette('#E8C040')
    c.polygon([(3, 6), (8, 3), (13, 6), (13, 11), (8, 14), (3, 11)], p[2])
    c.polygon([(5, 7), (8, 5), (11, 7), (11, 10), (8, 12), (5, 10)], p[3])
    c.outline()


def draw_abeemination(c):
    p = palette('#E8B030')
    c.circle(8, 9, 5, p[2])
    for y in (7, 10):
        c.line(4, y, 12, y, (40, 30, 20, 255))
    c.rect(5, 2, 6, 4, hexc('#E0E8F0')); c.rect(10, 2, 11, 4, hexc('#E0E8F0'))
    c.set(6, 8, (20, 10, 10, 255))
    c.light()
    c.outline()


def draw_honey_comb(c):
    p = palette('#F0B020')
    for cx, cy in ((5, 5), (11, 5), (8, 10), (4, 11), (12, 11)):
        c.polygon([(cx - 2, cy - 1), (cx, cy - 3), (cx + 2, cy - 1), (cx + 2, cy + 1), (cx, cy + 3), (cx - 2, cy + 1)], p[2])
        c.set(cx, cy, p[4])
    c.outline()


def draw_bee_gun(c):
    p = palette('#E8B030')
    c.rect(2, 5, 12, 8, p[2])
    for x in (4, 7, 10):
        c.line(x, 5, x, 8, (50, 35, 20, 255))
    c.rect(12, 6, 14, 7, p[1])
    c.polygon([(4, 9), (7, 9), (6, 13), (4, 13)], WOOD[2])
    c.outline()


item('jungle_spores', draw_spore_item)
item('stinger', draw_stinger)
item('bee_wax', draw_wax)
item('abeemination', draw_abeemination)
item('honey_comb', draw_honey_comb)
item('bee_gun', draw_bee_gun)
item('blade_of_grass', lambda c: draw_sword(c, palette('#4CB830')), True)
item('bee_keeper', lambda c: draw_sword(c, palette('#E8B030')), True)
item('bees_knees', lambda c: draw_bow(c, palette('#E8B030')), True)
METAL['jungle'] = '#5AA040'
_jungle = metal('jungle')
item('jungle_hat', lambda c: draw_helmet(c, _jungle))
item('jungle_shirt', lambda c: draw_chest(c, _jungle))
item('jungle_pants', lambda c: draw_legs(c, _jungle))
EVIL_ARMOR.append('jungle')


def p_bee(c):
    c.circle(7.5, 8, 3, hexc('#F0C020'))
    c.line(7, 5, 7, 11, (40, 30, 20, 255))
    c.circle(6, 5, 1.6, (230, 240, 255, 200)); c.circle(9, 5, 1.6, (230, 240, 255, 200))


proj('stinger', lambda c: (c.line(2, 8, 13, 8, palette('#3A3A30')[2]), c.set(14, 8, palette('#3A3A30')[0])))
proj('bee', p_bee)
proj('bee_arrow', lambda c: (p_arrow(c, '#E8B030', '#F0E070'), c.circle(13, 8, 1.5, hexc('#F0C020'))))


def bee_texture(stripe_a, stripe_b, wing=(220, 235, 255, 150), crown=False):
    def paint(c):
        a, b = hexc(stripe_a), hexc(stripe_b)
        paint_box(c, 0, 0, 5, 5, 4, b, 0.1, 621)                    # head
        fx, fy, fw, fh = box_faces(0, 0, 5, 5, 4)['front']
        c.set(fx, fy + 1, (200, 30, 30, 255)); c.set(fx + 4, fy + 1, (200, 30, 30, 255))
        c.set(fx + 1, fy + 1, (30, 10, 10, 255)); c.set(fx + 3, fy + 1, (30, 10, 10, 255))
        paint_box(c, 20, 0, 6, 6, 5, shade(a, 0.9), 0.15, 622)       # thorax (fuzzy)
        paint_box(c, 0, 12, 7, 7, 9, a, 0.08, 623)                   # abdomen with stripes
        for face, (x, y, w, h) in box_faces(0, 12, 7, 7, 9).items():
            if face in ('left', 'right', 'top', 'bottom'):
                horizontal = face in ('top', 'bottom')
                for i in range(1, 9, 3):
                    if horizontal:
                        fill(c, (x, y + i, w, 1), b)
                    else:
                        fill(c, (x + i, y, 1, h), b)
        paint_box(c, 34, 12, 1, 1, 3, (30, 25, 20, 255), 0.0)
        for face, region in box_faces(32, 20, 8, 0, 5).items():
            fill(c, region, wing)
        if crown:
            tx, ty, tw, th = box_faces(0, 0, 5, 5, 4)['top']
            fill(c, (tx, ty, tw, th), hexc('#F0D030'))
    return paint


def bat_texture_colored(fur_hex, wing_hex):
    def paint(c):
        fur = hexc(fur_hex)
        paint_box(c, 0, 0, 4, 5, 3, fur, 0.15, 70)
        paint_box(c, 16, 0, 4, 4, 4, fur, 0.15, 71)
        c.set(21, 6, (255, 220, 80, 255)); c.set(22, 6, (255, 220, 80, 255))
        paint_box(c, 32, 0, 1, 2, 1, shade(fur, 0.8), 0.1)
        wing = hexc(wing_hex)
        for v in (16, 24):
            fill(c, (0, v, 18, 7), wing, 0.15, 72 + v)
            for x in (2, 5, 8, 11, 14):
                for y in range(v, v + 6):
                    c.set(x, y, shade(wing, 0.6))
    return paint


model_texture('hornet', 64, 32, bee_texture('#E89040', '#C8607A'))
model_texture('bee', 64, 32, bee_texture('#F0C828', '#2A2018'))
model_texture('queen_bee', 64, 32, bee_texture('#F0C030', '#3A2050', crown=True))
model_texture('jungle_slime', 64, 64, slime_texture('#6AB040', 180))
model_texture('jungle_bat', 64, 32, bat_texture_colored('#A0602A', '#6A3A1A'))
model_texture('cave_bat', 64, 32, bat_texture_colored('#2A6AA8', '#1A4A7A'))   # Terraria's Cave Bat is blue


def bee_frame(c, frame, body='#F0C828', size=12):
    p = palette(body)
    c.circle(size * 0.55, size * 0.55, size * 0.3, p[2])
    c.circle(size * 0.25, size * 0.5, size * 0.17, (40, 30, 20, 255))
    for x in range(int(size * 0.45), int(size * 0.85), 2):
        c.line(x, int(size * 0.35), x, int(size * 0.75), (40, 30, 20, 255))
    wing = (220, 235, 255, 180)
    c.circle(size * 0.5, size * (0.2 if frame % 2 else 0.3), size * 0.18, wing)
    c.light(1.2, 0.8)
    c.outline()


mob_sprite('hornet', 16, 16, 2, lambda c, f: bee_frame(c, f, '#E0A020', 16), frame_time=2)
mob_sprite('bee', 10, 10, 2, lambda c, f: bee_frame(c, f, '#F0C828', 10), frame_time=2)
mob_sprite('queen_bee', 32, 28, 2, lambda c, f: bee_frame(c, f, '#F0B020', 28), frame_time=3)
mob_sprite('jungle_slime', 16, 12, 2, lambda c, f: slime_frame(c, '#40B0A0', f, 16, 12), frame_time=10)
mob_sprite('jungle_bat', 16, 12, 3, bat_frame, frame_time=3)
mob_sprite('man_eater', 16, 12, 2, lambda c, f: maw_frame(c, f, '#4A9A30'), frame_time=4, rotate=True)
mob_sprite('snatcher', 16, 12, 2, lambda c, f: maw_frame(c, f, '#5AAA3A'), frame_time=4, rotate=True)

# ----------------------------------------------------------------------------------------- Underworld (Stage 4c)
def ash_texture():
    c = Canvas()
    p = palette('#4A4448')
    c.noise([p[2], p[2], p[1], p[3]], 701)
    return c


def hellstone_texture():
    c = Canvas()
    p = palette('#5A2A24')
    c.noise([p[2], p[2], p[1]], 702)
    glow = palette('#FF6A20')
    rnd = random.Random(703)
    for _ in range(9):
        x, y = rnd.randint(1, 13), rnd.randint(1, 13)
        c.set(x, y, glow[3]); c.set(x + 1, y, glow[2]); c.set(x, y + 1, glow[2]); c.set(x + 1, y + 1, glow[4])
    return c


def obsidian_brick_texture():
    c = Canvas()
    base = hexc('#2A2038')
    c.noise([base, base, shade(base, 1.1), shade(base, 0.9)], 704)
    mortar = shade(base, 0.5)
    for y in (0, 8):
        c.line(0, y, 15, y, mortar)
        for x in ((0, 8) if y == 0 else (4, 12)):
            c.line(x, y, x, y + 7, mortar)
    return c


def hellstone_brick_texture():
    c = Canvas()
    base = hexc('#A8402A')
    c.noise([base, base, shade(base, 1.12), shade(base, 0.88)], 705)
    mortar = hexc('#3A1A14')
    for y in (0, 8):
        c.line(0, y, 15, y, mortar)
        for x in ((0, 8) if y == 0 else (4, 12)):
            c.line(x, y, x, y + 7, mortar)
    return c


def hellforge_texture():
    c = obsidian_brick_texture()
    fire = palette('#FF7A20')
    c.rect(4, 7, 11, 13, (30, 10, 10, 255))
    for x in range(5, 11):
        for y in range(9, 13):
            if (x + y) % 3:
                c.set(x, y, fire[3] if y > 10 else fire[4])
    return c


def shadow_chest_texture():
    c = Canvas()
    p = palette('#3A2A4A')
    c.noise([p[2], p[2], p[2], shade(p[2], 1.1)], 706)
    c.rect(0, 0, 15, 1, p[4]); c.rect(0, 14, 15, 15, p[0])
    c.rect(0, 7, 15, 8, palette('#8A2A3A')[2])
    return c


BLOCK_TEXTURES['ash'] = ash_texture
BLOCK_TEXTURES['hellstone'] = hellstone_texture
BLOCK_TEXTURES['obsidian_brick'] = obsidian_brick_texture
BLOCK_TEXTURES['hellstone_brick'] = hellstone_brick_texture
BLOCK_TEXTURES['hellforge'] = hellforge_texture
BLOCK_TEXTURES['locked_shadow_chest'] = shadow_chest_texture


def underworld_block_assets():
    for name in ('ash', 'hellstone', 'obsidian_brick', 'hellstone_brick'):
        write_json(os.path.join(ASSETS, 'blockstates', name + '.json'), {'variants': {'': {'model': f'terracraft:block/{name}'}}})
        write_json(os.path.join(ASSETS, 'models/block', name + '.json'),
                   {'parent': 'minecraft:block/cube_all', 'textures': {'all': f'terracraft:block/{name}'}})
        write_json(os.path.join(ASSETS, 'items', name + '.json'), {'model': {'type': 'minecraft:model', 'model': f'terracraft:block/{name}'}})
    faces = ['north', 'south', 'east', 'west', 'up', 'down']
    write_json(os.path.join(ASSETS, 'blockstates/hellforge.json'), {'variants': {'': {'model': 'terracraft:block/hellforge'}}})
    write_json(os.path.join(ASSETS, 'models/block/hellforge.json'), {'parent': 'minecraft:block/block', 'textures': {
        'particle': 'terracraft:block/hellforge', 'all': 'terracraft:block/hellforge'},
        'elements': [{'from': [0, 0, 1], 'to': [16, 14, 15], 'faces': {f: {'texture': '#all'} for f in faces}}]})
    write_json(os.path.join(ASSETS, 'items/hellforge.json'), {'model': {'type': 'minecraft:model', 'model': 'terracraft:block/hellforge'}})
    chest = [
        {'from': [1, 0, 1], 'to': [15, 10, 15], 'faces': {f: {'texture': '#body'} for f in faces}},
        {'from': [1, 10, 1], 'to': [15, 14, 15], 'faces': {f: {'texture': '#body'} for f in faces}},
        {'from': [7, 7, 0], 'to': [9, 11, 1], 'faces': {f: {'texture': '#lock'} for f in faces}},
    ]
    write_json(os.path.join(ASSETS, 'models/block/locked_shadow_chest.json'), {'parent': 'minecraft:block/block', 'textures': {
        'particle': 'terracraft:block/locked_shadow_chest', 'body': 'terracraft:block/locked_shadow_chest',
        'lock': 'terracraft:block/locked_gold_chest_lock'}, 'elements': chest})
    write_json(os.path.join(ASSETS, 'blockstates/locked_shadow_chest.json'), {'variants': {
        'facing=north': {'model': 'terracraft:block/locked_shadow_chest'},
        'facing=east': {'model': 'terracraft:block/locked_shadow_chest', 'y': 90},
        'facing=south': {'model': 'terracraft:block/locked_shadow_chest', 'y': 180},
        'facing=west': {'model': 'terracraft:block/locked_shadow_chest', 'y': 270}}})
    write_json(os.path.join(ASSETS, 'items/locked_shadow_chest.json'), {'model': {'type': 'minecraft:model', 'model': 'terracraft:block/locked_shadow_chest'}})


def draw_voodoo_doll(c):
    cloth = palette('#C8A070')
    c.circle(8, 4, 3, cloth[2])
    c.rect(6, 7, 10, 12, cloth[2])
    c.line(3, 8, 6, 9, cloth[2]); c.line(10, 9, 13, 8, cloth[2])
    c.rect(6, 13, 7, 15, cloth[1]); c.rect(9, 13, 10, 15, cloth[1])
    c.set(7, 4, (30, 20, 20, 255)); c.set(9, 4, (30, 20, 20, 255))
    c.line(5, 2, 11, 8, hexc('#B0B0B8'))   # pin
    c.set(5, 2, hexc('#E03030'))
    c.outline()


def draw_emblem(c, color):
    p = palette(color)
    c.circle(8, 8, 6, palette('#C8A040')[2])
    c.circle(8, 8, 4.5, p[2])
    c.circle(8, 8, 2, p[4])
    c.light()
    c.outline()


def draw_scythe_book(c):
    draw_book(c, '#5A2A6A')
    c.line(6, 6, 10, 10, hexc('#C060FF'))
    c.line(6, 10, 10, 6, hexc('#C060FF'))


_molten = palette('#E05A20')
METAL['molten'] = '#E05A20'
item('hellstone_bar', lambda c: draw_bar(c, palette('#E05A20')))
item('guide_voodoo_doll', draw_voodoo_doll)
item('molten_pickaxe', lambda c: draw_pickaxe(c, _molten), True)
item('molten_hamaxe', lambda c: draw_axe(c, _molten), True)
item('fiery_greatsword', lambda c: draw_sword(c, _molten), True)
item('molten_fury', lambda c: draw_bow(c, _molten), True)
item('phoenix_blaster', lambda c: draw_gun(c, _molten))
item('hellwing_bow', lambda c: draw_bow(c, palette('#8A2A2A')), True)
item('flamelash', lambda c: draw_staff(c, '#FF6A20'), True)
item('flower_of_fire', lambda c: draw_wand(c, '#FF4020'), True)
item('demon_scythe', draw_scythe_book)
item('breaker_blade', lambda c: draw_sword(c, palette('#8A8AA0'), length=13), True)
item('laser_rifle', lambda c: draw_gun(c, palette('#6A4A8A')))
item('pwnhammer', lambda c: draw_hammer(c, palette('#C8A040')), True)
item('molten_helmet', lambda c: draw_helmet(c, _molten))
item('molten_breastplate', lambda c: draw_chest(c, _molten))
item('molten_greaves', lambda c: draw_legs(c, _molten))
EVIL_ARMOR.append('molten')
for _e, _col in (('warrior', '#D04030'), ('ranger', '#40A040'), ('sorcerer', '#4060E0'), ('summoner', '#A040C0')):
    item(f'{_e}_emblem', lambda c, col=_col: draw_emblem(c, col))

proj('imp_fireball', lambda c: p_orb(c, '#FF6A20', 3))
proj('demon_scythe', lambda c: (c.ring(7.5, 7.5, 6, palette('#A040E0')[3], 1.6), c.ring(7.5, 7.5, 3.5, palette('#E080FF')[3], 1.0)))
proj('wof_laser', lambda c: (c.line(0, 8, 15, 8, hexc('#FF60A0'), 2), c.line(2, 8, 13, 8, hexc('#FFE0F0'))))
proj('flamelash', lambda c: p_orb(c, '#FF8A30', 4))
proj('flower_of_fire', lambda c: p_orb(c, '#FF5020', 3))
proj('hellwing', lambda c: (c.circle(7.5, 8, 2.5, hexc('#FF7020')), c.line(1, 6, 6, 8, hexc('#C03010'), 2), c.line(9, 8, 14, 6, hexc('#C03010'), 2)))
proj('laser', lambda c: (c.line(0, 8, 15, 8, hexc('#80FF60'), 2), c.line(2, 8, 13, 8, hexc('#E8FFE0'))))


def mouth_texture():
    def paint(c):
        flesh = hexc('#B04858')
        paint_box(c, 0, 0, 18, 8, 8, flesh, 0.15, 711, top=shade(flesh, 0.85))
        paint_box(c, 0, 18, 18, 8, 8, flesh, 0.15, 712, top=shade(flesh, 0.85))
        paint_box(c, 0, 40, 16, 8, 6, (40, 8, 14, 255), 0.1, 713)
        teeth = hexc('#E8E0C8')
        for v in (54, 58):
            paint_box(c, 0, v, 16, 2, 1, teeth, 0.05, 714 + v)
            x, y, w, h = box_faces(0, v, 16, 2, 1)['front']
            for tx in range(x, x + w, 2):
                c.set(tx, y + (1 if v == 54 else 0), (60, 20, 20, 255))
        for face in ('front',):
            for v0 in (0, 18):
                x, y, w, h = box_faces(0, v0, 18, 8, 8)[face]
                for vx in range(x, x + w, 3):
                    fill(c, (vx, y, 1, h), shade(flesh, 0.7))
    return paint


def wall_tile():
    c = Canvas(32, 32)
    flesh = hexc('#A84050')
    c.noise([flesh, flesh, shade(flesh, 1.1), shade(flesh, 0.85)], 721)
    rnd = random.Random(722)
    for _ in range(9):
        x, y = rnd.randint(0, 31), rnd.randint(0, 31)
        length = rnd.randint(5, 12)
        for i in range(length):
            c.set((x + i) % 32, (y + (i // 3)) % 32, shade(flesh, 0.6))
    for _ in range(6):
        x, y = rnd.randint(2, 29), rnd.randint(2, 29)
        c.circle(x, y, 1.8, shade(flesh, 1.25))
    return c


model_texture('wall_of_flesh', 64, 64, mouth_texture())
model_texture('wall_of_flesh_wall', 32, 32, lambda c: c.img.paste(wall_tile().img))
model_texture('wall_of_flesh_eye', 64, 64, eye_texture('#4A2A6A', sclera='#E8D0C8'))
model_texture('the_hungry', 64, 32, maw_texture('#A84050', '#F0E0C0'))
model_texture('lava_slime', 64, 64, slime_texture('#FF7A20', 220))
model_texture('hellbat', 64, 32, bat_texture_colored('#E85A20', '#A83010'))
for _part in ('head', 'body', 'tail'):
    model_texture(f'bone_serpent_{_part}', 64, 64, worm_texture('#D8D0B8', '#B8B098'))
    mob_sprite(f'bone_serpent_{_part}', 12, 12, 1, lambda c, f, part=_part: worm_frame(c, f, '#D8D0B8', part), rotate=True)


def wall_frame(c, frame):
    p = palette('#A84050')
    c.rect(0, 0, 23, 39, p[2])
    for y in range(0, 40, 5):
        c.line(0, y, 23, y + 2, p[1])
    c.circle(12, 20, 5, (40, 8, 14, 255))
    for x in range(8, 17, 2):
        c.set(x, 16 + frame, hexc('#E8E0C8')); c.set(x, 24 - frame, hexc('#E8E0C8'))
    c.outline()


mob_sprite('wall_of_flesh', 24, 40, 2, wall_frame, frame_time=8)
mob_sprite('wall_of_flesh_eye', 24, 18, 4, big_eye_frame, frame_time=4, rotate=True)
mob_sprite('the_hungry', 16, 12, 2, lambda c, f: maw_frame(c, f, '#A84050'), frame_time=4, rotate=True)
mob_sprite('imp', 16, 24, 3, lambda c, f: npc_frame(c, f, '#A8384A', '#2A1A2A', '#5A2A6A', '#3A1A3A'), frame_time=8)
mob_sprite('demon', 16, 12, 3, bat_frame, frame_time=4)
mob_sprite('voodoo_demon', 16, 12, 3, bat_frame, frame_time=4)
mob_sprite('lava_slime', 16, 12, 2, lambda c, f: slime_frame(c, '#FF7A20', f, 16, 12), frame_time=10, fullbright=True)
mob_sprite('hellbat', 16, 12, 3, bat_frame, frame_time=3)

# ----------------------------------------------------------------------------------------- Goblin Army & Meteorite (Stage 4d/4e)
def meteorite_texture():
    """Terraria's meteorite: dark maroon-purple rock in lumpy clusters with pink highlights and a few hot cracks."""
    c = Canvas()
    p = palette('#5A2A40')
    rnd = random.Random(801)
    c.noise([p[1], p[2], p[2], shade(p[1], 0.85)], 801)
    for _ in range(9):                      # rounded lumps: dark rim, pink top-left highlight
        cx, cy, r = rnd.randint(0, 15), rnd.randint(0, 15), rnd.choice((1.5, 2, 2.5))
        for x in range(16):
            for y in range(16):
                d = ((x - cx) ** 2 + (y - cy) ** 2) ** 0.5
                if d <= r:
                    col = hexc('#C88AA0') if (x - cx) + (y - cy) < -r * 0.6 else p[3] if d < r - 0.8 else p[0]
                    c.set(x, y, col)
    hot = palette('#FF7A40')
    for _ in range(4):
        x, y = rnd.randint(0, 15), rnd.randint(0, 15)
        c.set(x, y, hot[3])
    return c


def workshop_texture():
    c = wood_planks(811)
    for x, y in ((3, 3), (12, 4), (6, 11)):
        c.circle(x, y, 1.5, palette('#A0A0A8')[3])
    c.line(8, 2, 13, 7, palette('#C8A040')[2])
    return c


BLOCK_TEXTURES['meteorite'] = meteorite_texture
BLOCK_TEXTURES['tinkerers_workshop'] = workshop_texture


def goblin_block_assets():
    write_json(os.path.join(ASSETS, 'blockstates/meteorite.json'), {'variants': {'': {'model': 'terracraft:block/meteorite'}}})
    write_json(os.path.join(ASSETS, 'models/block/meteorite.json'), {'parent': 'minecraft:block/cube_all', 'textures': {'all': 'terracraft:block/meteorite'}})
    write_json(os.path.join(ASSETS, 'items/meteorite.json'), {'model': {'type': 'minecraft:model', 'model': 'terracraft:block/meteorite'}})
    faces = ['north', 'south', 'east', 'west', 'up', 'down']
    write_json(os.path.join(ASSETS, 'models/block/tinkerers_workshop.json'), {
        'parent': 'minecraft:block/block', 'textures': {'particle': 'terracraft:block/tinkerers_workshop', 'wood': 'terracraft:block/tinkerers_workshop'},
        'elements': [{'from': [0, 10, 0], 'to': [16, 13, 16], 'faces': {f: {'texture': '#wood'} for f in faces}}] + [
            {'from': [x, 0, z], 'to': [x + 3, 10, z + 3], 'faces': {f: {'texture': '#wood'} for f in ['north', 'south', 'east', 'west', 'down']}}
            for x, z in [(1, 1), (12, 1), (1, 12), (12, 12)]] + [
            {'from': [3, 13, 3], 'to': [7, 16, 6], 'faces': {f: {'texture': '#wood'} for f in faces}}]})
    write_json(os.path.join(ASSETS, 'blockstates/tinkerers_workshop.json'), {'variants': {'': {'model': 'terracraft:block/tinkerers_workshop'}}})
    write_json(os.path.join(ASSETS, 'items/tinkerers_workshop.json'), {'model': {'type': 'minecraft:model', 'model': 'terracraft:block/tinkerers_workshop'}})


def draw_cloth(c):
    p = palette('#8A7A5A')
    c.polygon([(2, 3), (13, 2), (14, 12), (9, 14), (3, 12)], p[2])
    for x in range(3, 13, 3):
        c.line(x, 4, x + 1, 12, p[1])
    c.set(14, 12, (0, 0, 0, 0)); c.set(2, 3, (0, 0, 0, 0))
    c.outline()


def draw_standard(c):
    c.line(4, 15, 4, 1, WOOD[1], 1)
    p = palette('#7A3A8A')
    c.polygon([(5, 2), (14, 3), (12, 7), (14, 11), (5, 10)], p[2])
    c.circle(9, 6, 1.5, hexc('#E8C040'))
    c.outline()


def draw_meteor_gun(c):
    p = palette('#C04030')
    c.rect(3, 5, 12, 8, p[2]); c.rect(12, 6, 14, 7, hexc('#80FF60'))
    c.polygon([(4, 9), (7, 9), (6, 13), (4, 13)], p[1])
    c.light()
    c.outline()


item('tattered_cloth', draw_cloth)
item('goblin_battle_standard', draw_standard)
item('obsidian_horseshoe', lambda c: (draw_horseshoe(c), c.set(8, 8, hexc('#3A2A4A'))))
item('cloud_in_a_balloon', lambda c: draw_balloon(c))
item('obsidian_shield', lambda c: draw_shield(c, '#3A2A4A'))
item('obsidian_water_walking_boots', lambda c: draw_boots(c, '#3A2A4A'))
item('lava_waders', lambda c: draw_boots(c, '#E05A20', wings=True))
item('mana_flower', lambda c: (draw_flower(c), c.circle(8, 6, 1.5, hexc('#3C64F0'))))
item('meteorite_bar', lambda c: draw_bar(c, palette('#8A3A5A')))
item('space_gun', draw_meteor_gun)
METAL['meteor'] = '#B04030'
_meteor = metal('meteor')
item('meteor_helmet', lambda c: draw_helmet(c, _meteor))
item('meteor_suit', lambda c: draw_chest(c, _meteor))
item('meteor_leggings', lambda c: draw_legs(c, _meteor))
EVIL_ARMOR.append('meteor')
proj('chaos_ball', lambda c: p_orb(c, '#C040FF', 3))

GOBLIN_SKIN = '#6A9AA8'      # the Tinkerer's blue-grey; goblin enemies are green/olive
model_texture('goblin_peon', 64, 64, skin_texture('#C8B848', '#3A2A2A', '#7A5A6A', '#5A4A3A', eyes=(220, 60, 40, 255), ears=True,
                                                  suspenders='#4A3020', hair_style='bald'))
model_texture('goblin_thief', 64, 64, skin_texture('#A8A040', '#2A2A2A', '#3A3A44', '#2A2A30', eyes=(220, 60, 40, 255), hat='#2A2A30', ears=True,
                                                   straps='#6A4A2A'))
model_texture('goblin_warrior', 64, 64, skin_texture('#78B050', '#5A5A60', '#8A8A90', '#5A5A60', eyes=(220, 60, 40, 255), hat='#8A8A90',
                                                     hat_band='#5A5A60', short_sleeves=False, ears=True, straps='#5A3A2A'))
model_texture('goblin_sorcerer', 64, 64, skin_texture('#C8C050', '#7A3A8A', '#7A3A8A', '#5A2A6A', eyes=(255, 120, 255, 255), hat='#7A3A8A',
                                                      short_sleeves=False, shoes='#5A2A6A', ears=True, robe='#7A3A8A',
                                                      belt='#C8A040', buckle='#FF80FF'))
model_texture('goblin_archer', 64, 64, skin_texture('#78B0A0', '#3A2A2A', '#3A6A8A', '#2A4A5A', eyes=(220, 60, 40, 255), hat='#3A6A8A', ears=True,
                                                   straps='#6A4A2A'))
model_texture('bound_goblin', 64, 64, skin_texture(GOBLIN_SKIN, '#2A3A44', '#8A6A4A', '#5A4A3A', torn=True, ears=True, eyes=(40, 40, 60, 255)))
model_texture('goblin_tinkerer', 64, 64, skin_texture(GOBLIN_SKIN, '#2A3A44', '#E8E8EC', '#3A3440', hat='#C8A040', hat_band='#6A5020',
                                                      short_sleeves=False, ears=True, jacket='#3A3440', collar='#C82828',
                                                      eyes=(230, 200, 60, 255)))
for _g, _skin, _shirt in (('goblin_peon', '#C8B848', '#7A5A6A'), ('goblin_thief', '#A8A040', '#3A3A44'), ('goblin_warrior', '#78B050', '#8A8A90'),
                          ('goblin_sorcerer', '#C8C050', '#7A3A8A'), ('goblin_archer', '#78B0A0', '#3A6A8A'), ('bound_goblin', GOBLIN_SKIN, '#8A6A4A'),
                          ('goblin_tinkerer', GOBLIN_SKIN, '#C8C8D4')):
    mob_sprite(_g, 16, 24, 3, lambda c, f, sk=_skin, sh=_shirt: npc_frame(c, f, sk, '#3A2A2A', sh, '#4A3A2A'), frame_time=6, animate='move')
mob_sprite('meteor_head', 14, 14, 2, lambda c, f: skull_frame(c, f, '#8A5A40', '#FF8030', 14), frame_time=4, fullbright=True)

# ----------------------------------------------------------------------------------------- Wings
# Worn wings: WingsModel panels are 14x16 (box UV at 0,0 with depth 1). In the front face the left column is the
# wing tip; the back face is the mirror image. mask(ix, iy) -> colour or None, ix 0 = root at the back, 13 = tip.
WING_STYLES = {
    # style: (main, shade, edge)
    'fledgling': ('#E8DCC8', '#B89A78', '#8A6A4A'),
    'angel': ('#F4F4FA', '#B8C8E8', '#8898C0'),
    'demon': ('#7A2A3A', '#4A1424', '#C8B0A0'),
    'leaf': ('#5AAA3A', '#3A7A2A', '#2A5A1A'),
}


def wing_mask(style, ix, iy):
    """Wing silhouette: the leading edge rises from the shoulder to the tip, feathers/membrane hang below it."""
    main, dark, edge = (hexc(c) for c in WING_STYLES[style])
    if style == 'fledgling':
        if ix > 10:
            return None
        top = round(6 - ix * 5 / 10)
        bottom = round(9 + 3 * math.sin(math.pi * (ix + 2) / 14)) - (ix % 2)
    elif style == 'demon':
        top = round(4 - ix * 4 / 13)
        fingers = (4, 8, 13)
        near = min(abs(ix - f) for f in fingers)
        bottom = round(13 - ix / 3) - min(near, 2) * 2
    else:
        top = round(4 - ix * 4 / 13)
        bottom = round(9 + 6 * math.sin(math.pi * (ix + 2) / 17)) - (ix % 2)
    if iy < top or iy > bottom:
        return None
    if style == 'demon':
        if iy == top or (ix in (4, 8, 13) and iy < bottom):
            return edge                  # bone arm and fingers
        return main if (ix * 3 + iy) % 7 else dark
    if style == 'leaf':
        if ix % 3 == 1:
            return dark                  # leaf veins
        return edge if (iy - top) % 5 == 4 else main
    if iy <= top:
        return edge                      # leading edge
    if iy > top + 4:                     # long flight feathers
        return dark if ix % 2 == 0 and iy > top + 5 else main
    return main if (ix + iy) % 3 else shade(main, 0.92)


def wing_texture(style):
    c = Canvas(64, 32)
    edge = hexc(WING_STYLES[style][2])
    for ix in range(14):
        for iy in range(16):
            col = wing_mask(style, ix, iy)
            if col is None:
                continue
            c.set(1 + (13 - ix), 1 + iy, col)          # front: tip on the left
            c.set(16 + ix, 1 + iy, shade(col, 0.9))    # back: mirrored
    for ix in range(14):
        c.set(1 + ix, 0, edge)
        c.set(15 + ix, 0, edge)
    for iy in range(16):
        c.set(0, 1 + iy, edge)
        c.set(15, 1 + iy, edge)
    return c


def draw_wings_item(c, style):
    """Both wings spread, seen from behind, on a 16x16 icon."""
    for px in range(8):
        for py in range(15):
            col = wing_mask(style, min(13, px * 13 // 7), py)
            if col is not None:
                c.set(8 + px, py, col)
                c.set(7 - px, py, col)
    c.outline()


def draw_soul(c, color):
    p = palette(color)
    c.circle(7.5, 7, 4.5, p[2])
    c.circle(7.5, 7, 3, p[3])
    c.circle(7, 6, 1.5, p[4])
    c.polygon([(5, 11), (7.5, 15), (10, 11)], p[2])
    c.outline()


for _style in WING_STYLES:
    item(f'{_style}_wings', lambda c, st=_style: draw_wings_item(c, st))
item('soul_of_light', lambda c: draw_soul(c, '#F0A0E0'))
item('soul_of_night', lambda c: draw_soul(c, '#8A50C8'))
item('soul_of_flight', lambda c: draw_soul(c, '#60D8F0'))


def check_registered_items():
    """Fails if a Java-registered item id has no texture recipe here (keeps assets in sync)."""
    java_root = os.path.join(ROOT, 'src/main/java/com/terracraft/registry/content')
    ids = set()
    pattern = re.compile(r'\b(?:register|sword|bow|ranged|thrown|magic|ammo|pickaxe|axe|hammer|accessory|material|coin|potion|buffPotion)\(\s*"([a-z0-9_]+)"')
    for fname in os.listdir(java_root):
        if fname == 'MobContent.java':
            continue
        with open(os.path.join(java_root, fname)) as f:
            ids.update(pattern.findall(f.read()))
    known = set(ITEMS) | set(BLOCKS_CUBE) | {'work_bench', 'iron_anvil', 'lead_anvil', 'life_crystal_block', 'corrupt_grass', 'crimson_grass',
                                             'shadow_orb', 'crimson_heart', 'demon_altar', 'crimson_altar', 'vile_mushroom', 'vicious_mushroom',
                                             'ebonwood', 'shadewood', 'ebonwood_leaves', 'shadewood_leaves'} | set(BRICKS) | {'spikes', 'locked_gold_chest', 'jungle_grass',
                                                                                        'jungle_spores_plant', 'hive', 'larva', 'ash', 'hellstone',
                                                                                        'obsidian_brick', 'hellstone_brick', 'hellforge', 'locked_shadow_chest',
                                                                                        'meteorite', 'tinkerers_workshop', 'dungeon_bookshelf'}
    missing = sorted(i for i in ids if i not in known and not i.endswith("_"))
    if missing:
        print('ERROR: items without generated assets:', missing)
        sys.exit(1)


def main():
    for name, (drawer, handheld) in ITEMS.items():
        c = Canvas()
        drawer(c)
        c.save(os.path.join(TEX, 'item', name + '.png'))
        item_assets(name, handheld)
    for name, factory in BLOCK_TEXTURES.items():
        factory().save(os.path.join(TEX, 'block', name + '.png'))
    block_assets()
    dungeon_block_assets()
    jungle_block_assets()
    underworld_block_assets()
    goblin_block_assets()
    for name, drawer in PROJECTILES.items():
        c = Canvas()
        drawer(c)
        c.save(os.path.join(TEX, 'entity/projectile', name + '.png'))
    for name, factory in HUD.items():
        factory().save(os.path.join(TEX, 'gui/sprites/hud', name + '.png'))
    for name, color in EFFECTS.items():
        effect_icon(color).save(os.path.join(TEX, 'mob_effect', name + '.png'))
    armor_assets()
    mob_assets()
    for name, (w, h, painter) in MODEL_TEXTURES.items():
        c = Canvas(w, h)
        painter(c)
        c.save(os.path.join(TEX, 'entity/model', name + '.png'))
    for style in WING_STYLES:
        wing_texture(style).save(os.path.join(TEX, 'entity/wings', style + '.png'))
    import creature_models          # creatures with their own models (Imp, Demons, Crimera...)
    creature_models.write(ASSETS, TEX)
    import item_models              # 3D held models for weapons and tools (after the icons exist)
    item_models.write(ASSETS, TEX)
    check_registered_items()
    print(f'Generated {len(ITEMS)} items, {len(BLOCK_TEXTURES)} block textures, {len(PROJECTILES)} projectiles, '
          f'{len(HUD)} HUD sprites, {len(EFFECTS)} effect icons, {len(ARMOR_SETS)} armor sets')


if __name__ == '__main__':
    main()
