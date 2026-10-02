#!/usr/bin/env python3
"""Generates TerraCraft's original pixel-art textures and the client resource JSON (models, item
definitions, blockstates, equipment layers).

Run from the project root:  python3 tools/generate_assets.py
Everything is derived from the tables below; re-running is deterministic.
"""
import json
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
item('slime_crown', draw_crown)
item('suspicious_looking_eye', draw_eye_item)
item('demonite_ore', lambda c: draw_raw(c, palette('#6A4FA8'), 'demonite'))
item('demonite_bar', lambda c: draw_bar(c, palette('#7A5CC0')))
item('crimtane_ore', lambda c: draw_raw(c, palette('#B8323C'), 'crimtane'))
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
ACCESSORIES = {
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
    c = stone_base(hash(name) & 0xFFFF, deep)
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


BLOCKS_CUBE = ['tin_ore', 'deepslate_tin_ore', 'lead_ore', 'deepslate_lead_ore', 'silver_ore', 'deepslate_silver_ore',
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
    write_json(os.path.join(ASSETS, 'blockstates/life_crystal_block.json'), {'variants': {'': {'model': 'terracraft:block/life_crystal_block'}}})
    write_json(os.path.join(ASSETS, 'models/block/life_crystal_block.json'),
               {'parent': 'minecraft:block/cross', 'render_type': 'minecraft:cutout', 'textures': {'cross': 'terracraft:block/life_crystal_block'}})


def armor_assets():
    for s in ARMOR_SETS:
        color = METAL[s] if s != 'wood' else '#A0703C'
        armor_layer(color, False).save(os.path.join(TEX, f'entity/equipment/humanoid/{s}.png'))
        armor_layer(color, True).save(os.path.join(TEX, f'entity/equipment/humanoid_leggings/{s}.png'))
        write_json(os.path.join(ASSETS, f'equipment/{s}.json'), {'layers': {
            'humanoid': [{'texture': f'terracraft:{s}'}],
            'humanoid_leggings': [{'texture': f'terracraft:{s}'}]}})


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


def slime_texture(color, alpha=170, baby=None, king=False):
    def paint(c):
        col = hexc(color, alpha)
        light = shade(col, 1.35)
        paint_box(c, 0, 0, 14, 10, 14, col, 0.08, 1, top=light)
        paint_box(c, 0, 24, 10, 3, 10, light, 0.08, 2, top=shade(col, 1.6))
        core = hexc(color, 225)
        paint_box(c, 0, 40, 6, 6, 6, shade(core, 0.6), 0.1, 3)
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
model_texture('baby_slime', 64, 64, slime_texture('#7FBFF8'))
model_texture('mother_slime', 64, 64, slime_texture('#D46FC4', baby='#7FBFF8'))
model_texture('king_slime', 128, 64, slime_texture('#3C78E6', 180, king=True))


def skin_texture(skin, hair, shirt, pants, shoes='#3A2A20', eyes=(30, 30, 40, 255), beard=None, hat=None, hat_band=None,
                 torn=False, bony=False, short_sleeves=True, cross=False, lamp=False):
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


model_texture('zombie', 64, 64, skin_texture('#7FA06A', '#3E4A2E', '#4A6FA5', '#4B3B2F', eyes=(220, 40, 40, 255), torn=True))
model_texture('skeleton', 64, 64, skin_texture('#E8E2CF', '#E8E2CF', '#E8E2CF', '#E8E2CF', bony=True))
model_texture('guide', 64, 64, skin_texture('#E8B890', '#6A4428', '#6E8C3A', '#5A4430', short_sleeves=False))
model_texture('merchant', 64, 64, skin_texture('#E8B890', '#E8E8E8', '#5A6A8C', '#3A3A44', beard='#F0F0F0', hat='#6A6A70', short_sleeves=False))
model_texture('nurse', 64, 64, skin_texture('#F0C8A8', '#C84838', '#F4F4F4', '#F0F0F0', shoes='#F0F0F0', hat='#F8F8F8', cross=True))
model_texture('demolitionist', 64, 64, skin_texture('#D8A880', '#C86A28', '#8A5A30', '#4A3A2A', beard='#C86A28', hat='#E8C030',
                                                     hat_band='#7A6018', lamp=True))


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


model_texture('demon_eye', 64, 64, eye_texture('#C22B2B'))
model_texture('servant_of_cthulhu', 64, 64, eye_texture('#3A62C8'))
model_texture('eye_of_cthulhu', 64, 64, eye_texture('#3A62C8'))
model_texture('eye_of_cthulhu_mouth', 64, 64, eye_texture('#3A62C8', mouth=True))


def bat_texture(c):
    fur = hexc('#6B4A3A')
    paint_box(c, 0, 0, 4, 5, 3, fur, 0.15, 70)
    paint_box(c, 16, 0, 4, 4, 4, fur, 0.15, 71)
    c.set(21, 6, (255, 220, 80, 255)); c.set(22, 6, (255, 220, 80, 255))
    paint_box(c, 32, 0, 1, 2, 1, shade(fur, 0.8), 0.1)
    wing = hexc('#4E3428')
    for v in (16, 24):
        fill(c, (0, v, 18, 7), wing, 0.15, 72 + v)
        for x in (2, 5, 8, 11, 14):
            for y in range(v, v + 7):
                if (y - v) <= 5:
                    c.set(x, y, shade(wing, 0.6))


model_texture('cave_bat', 64, 32, bat_texture)


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
    known = set(ITEMS) | set(BLOCKS_CUBE) | {'work_bench', 'iron_anvil', 'lead_anvil', 'life_crystal_block'}
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
    check_registered_items()
    print(f'Generated {len(ITEMS)} items, {len(BLOCK_TEXTURES)} block textures, {len(PROJECTILES)} projectiles, '
          f'{len(HUD)} HUD sprites, {len(EFFECTS)} effect icons, {len(ARMOR_SETS)} armor sets')


if __name__ == '__main__':
    main()
