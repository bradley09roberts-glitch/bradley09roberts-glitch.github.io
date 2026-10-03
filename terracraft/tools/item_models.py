"""3D held models for weapons and tools.

Every sword, pickaxe, axe, hammer, bow, gun, staff, wand and spell tome gets a real 3D model (crossguards that stick
out, a pickaxe head with depth, a gun with a barrel and grip, a bow with curved limbs and a string...) used in the
hand, on the ground and in item frames; the inventory keeps the flat 16x16 icon. Colours are read from the item's own
icon (blade, handle, gem...), so a resource pack that changes the icon keeps the 3D model's colours close.

The model is built upright in its own frame, then:
  * swords, tools, staffs and wands are tilted 45 degrees and use vanilla's handheld display (blade forward);
  * bows stand upright with the string towards the player;
  * guns point their barrel forward along the player's aim;
  * tomes are held open-side out in front of the hand.

Writes models/item/<name>_3d.json, textures/item/3d/<name>.png and the items/<name>.json switch (icon in the GUI).
"""
import json
import os
import random
from collections import Counter

from PIL import Image

import armor_models as am
from pixelart import Canvas, shade

SWORDS = ['wooden_sword', 'copper_broadsword', 'tin_broadsword', 'iron_broadsword', 'lead_broadsword', 'silver_broadsword',
          'tungsten_broadsword', 'gold_broadsword', 'platinum_broadsword', 'lights_bane', 'blood_butcherer', 'blade_of_grass',
          'bee_keeper']
SHORTSWORDS = ['copper_shortsword']
GREATSWORDS = ['fiery_greatsword', 'breaker_blade']
KATANAS = ['muramasa']
PICKAXES = ['copper_pickaxe', 'tin_pickaxe', 'iron_pickaxe', 'lead_pickaxe', 'silver_pickaxe', 'tungsten_pickaxe', 'gold_pickaxe',
            'platinum_pickaxe', 'nightmare_pickaxe', 'deathbringer_pickaxe', 'molten_pickaxe']
AXES = ['copper_axe', 'tin_axe', 'iron_axe', 'lead_axe', 'silver_axe', 'tungsten_axe', 'gold_axe', 'platinum_axe',
        'war_axe_of_the_night', 'blood_lust_cluster']
HAMAXES = ['molten_hamaxe']
HAMMERS = ['copper_hammer', 'iron_hammer', 'the_breaker', 'flesh_grinder', 'pwnhammer']
BOWS = ['wooden_bow', 'copper_bow', 'iron_bow', 'gold_bow', 'demon_bow', 'tendon_bow', 'bees_knees', 'molten_fury', 'hellwing_bow']
GUNS = ['flintlock_pistol', 'handgun', 'phoenix_blaster', 'bee_gun', 'space_gun']
LONG_GUNS = ['musket', 'the_undertaker', 'laser_rifle']
STAFFS = ['amethyst_staff', 'aqua_scepter', 'flamelash']
WANDS = ['wand_of_sparking', 'vilethorn', 'flower_of_fire']
BOOKS = ['magic_missile', 'book_of_skulls', 'demon_scythe', 'water_bolt']

# ----------------------------------------------------------------------------------------- colours from the icon


def load_icon(tex, name):
    return Image.open(os.path.join(tex, 'item', name + '.png')).convert('RGBA')


def lum(c):
    return 0.3 * c[0] + 0.59 * c[1] + 0.11 * c[2]


def sat(c):
    return max(c[:3]) - min(c[:3])


def pick(img, test, how='common', fallback=(128, 128, 128, 255)):
    """A representative colour of the opaque pixels selected by test(x, y): most common, lightest or most saturated,
    ignoring the darkest outline pixels."""
    px = [img.getpixel((x, y)) for x in range(16) for y in range(16) if img.getpixel((x, y))[3] > 0 and test(x, y)]
    if not px:
        return fallback
    px.sort(key=lum)
    body = px[len(px) // 5:] or px
    if how == 'metal':           # the base colour of a blade or head: bright, saturated and frequent (not the outline)
        counts = Counter(px)
        median = lum(px[len(px) // 2])
        best = max((c for c in counts if lum(c) >= median), key=lambda c: (sat(c) + 10) * counts[c] ** 0.5, default=px[-1])
        return best
    if how == 'common':          # the most common of the mid-tones (skip outlines and highlights)
        n = len(px)
        mid = px[n // 4: max(n // 4 + 1, n * 4 // 5)]
        return Counter(mid).most_common(1)[0][0]
    if how == 'light':
        return max(body, key=lum)
    if how == 'saturated':
        return max(body, key=sat)
    if how == 'dark':
        return body[0]
    return Counter(body).most_common(1)[0][0]


def blend(a, b, t=0.6):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3)) + (255,)


def colours(kind, img):
    diag = lambda x, y: (x + (15 - y)) / 30.0        # 0 bottom-left (handle) .. 1 top-right (tip)
    if kind in ('sword', 'shortsword', 'greatsword', 'katana'):
        return {'blade': pick(img, lambda x, y: diag(x, y) > 0.5, 'metal'), 'handle': pick(img, lambda x, y: diag(x, y) < 0.2, 'metal'),
                'guard': pick(img, lambda x, y: 0.2 <= diag(x, y) <= 0.4 and abs(x - (15 - y)) > 1, 'saturated')}
    if kind in ('pickaxe', 'axe', 'hammer', 'hamaxe'):
        return {'blade': pick(img, lambda x, y: y < 7 and x > 4, 'metal'), 'handle': pick(img, lambda x, y: y > 9 and x < 7, 'metal'),
                'guard': pick(img, lambda x, y: y < 7 and x > 4, 'light')}
    if kind == 'bow':
        return {'handle': pick(img, lambda x, y: True, 'metal'), 'string': pick(img, lambda x, y: True, 'light'),
                'guard': pick(img, lambda x, y: True, 'saturated')}
    if kind in ('gun', 'long_gun'):
        return {'body': pick(img, lambda x, y: True, 'metal'), 'barrel': blend(pick(img, lambda x, y: x > 8, 'dark'), (88, 88, 96, 255)),
                'handle': pick(img, lambda x, y: y > 8 and x < 8, 'metal'), 'guard': pick(img, lambda x, y: True, 'saturated')}
    if kind in ('staff', 'wand'):
        return {'handle': pick(img, lambda x, y: diag(x, y) < 0.45), 'gem': pick(img, lambda x, y: diag(x, y) > 0.6, 'saturated'),
                'guard': pick(img, lambda x, y: diag(x, y) > 0.5)}
    return {'body': pick(img, lambda x, y: True, 'metal'), 'page': pick(img, lambda x, y: True, 'light'),
            'guard': pick(img, lambda x, y: True, 'saturated')}


# ----------------------------------------------------------------------------------------- shapes (upright frame)
class Shape:
    def __init__(self):
        self.cubes = []

    def box(self, origin, size, mat, pattern='plate'):
        self.cubes.append(am.Cube(None, 'item', origin, size, mat=mat, pattern=pattern))


def sword(blade_len=12, blade_w=3, guard_w=7, grip=4, katana=False):
    s = Shape()
    base = -3
    s.box((7, base, 7), (2, 2, 2), 'guard')                                  # pommel
    s.box((7.5, base + 2, 7.5), (1, grip, 1), 'handle', 'wrap')               # grip
    gy = base + 2 + grip
    if katana:
        s.box((6, gy, 6), (4, 1, 4), 'guard')                                 # round tsuba
    else:
        s.box((8 - guard_w / 2, gy, 7), (guard_w, 1, 2), 'guard')             # crossguard
        s.box((7, gy - 1, 7.25), (2, 1, 1), 'guard')
    by = gy + 1
    s.box((8 - blade_w / 2, by, 7.5), (blade_w, blade_len, 1), 'blade', 'blade')
    tip = blade_w - 1
    while tip >= 1:                                                         # tapering point
        s.box((8 - tip / 2, by + blade_len + (blade_w - 1 - tip), 7.5), (tip, 1, 1), 'blade', 'blade')
        tip -= 1
    return s


def tool(kind):
    s = Shape()
    s.box((7.5, -3, 7.5), (1, 19, 1), 'handle', 'wood')                       # long handle
    s.box((7, -3, 7), (2, 1, 2), 'guard')
    if kind == 'pickaxe':
        s.box((6.5, 11, 6.5), (3, 3, 3), 'blade')                             # socket
        for side in (-1, 1):
            x = 9.5 if side > 0 else 1.5
            s.box((x, 12, 7.5), (5, 2, 1), 'blade')                           # arms
            s.box((x + (4 if side > 0 else 0), 10, 7.5), (1, 2, 1), 'blade')  # points curving down
            s.box((x + (4 if side > 0 else 0), 9, 7.5), (1, 1, 1), 'guard')
    elif kind in ('axe', 'hamaxe'):
        s.box((7, 11, 7), (2, 4, 2), 'blade')
        s.box((9, 9, 7.5), (4, 7, 1), 'blade', 'blade')                       # broad head
        s.box((13, 8, 7.5), (1, 9, 1), 'guard', 'blade')                      # cutting edge
        if kind == 'hamaxe':
            s.box((3, 10, 6.5), (4, 4, 3), 'blade')                           # hammer back
    elif kind == 'hammer':
        s.box((2.5, 10, 6), (11, 5, 4), 'blade')                              # heavy head
        s.box((2, 9.5, 5.5), (1, 6, 5), 'guard')                              # striking faces
        s.box((13, 9.5, 5.5), (1, 6, 5), 'guard')
    return s


def staff(wand=False):
    s = Shape()
    length = 14 if wand else 19
    s.box((7.5, -3, 7.5), (1, length, 1), 'handle', 'wood')
    top = -3 + length
    if wand:
        s.box((7, top - 1, 7), (2, 1, 2), 'guard')
        s.box((6.5, top, 6.5), (3, 3, 3), 'gem', 'gem')
    else:
        s.box((6.5, top - 2, 6.5), (3, 2, 3), 'guard')                        # claw holding the gem
        for dx, dz in ((-1, 0), (1, 0), (0, -1), (0, 1)):
            s.box((7.5 + dx * 1.5, top, 7.5 + dz * 1.5), (1, 3, 1), 'guard')
        s.box((6.5, top + 0.5, 6.5), (3, 3, 3), 'gem', 'gem')
    return s


def bow():
    """Upright bow in the x-y plane, like vanilla's bow sprite once tilted: the grip bulges to -x, the limbs sweep back
    to the string at +x."""
    s = Shape()
    s.box((5, 5, 7.5), (2, 6, 1), 'handle', 'wrap')                           # grip
    s.box((5.5, 4, 7), (1, 8, 2), 'guard')                                    # riser
    for (x, y, h) in ((6, 11, 3), (7, 14, 2), (8, 16, 2), (9, 18, 1)):        # upper limb
        s.box((x, y, 7.5), (1, h, 1), 'handle', 'wood')
    for (x, y, h) in ((6, 2, 3), (7, 0, 2), (8, -2, 2), (9, -3, 1)):          # lower limb
        s.box((x, y, 7.5), (1, h, 1), 'handle', 'wood')
    s.box((10, -3, 7.5), (0, 22, 1), 'string', 'string')                      # string
    return s


def gun(long=False):
    """Gun built along x: barrel towards +x, grip below at the back."""
    s = Shape()
    length = 13 if long else 8
    s.box((2, 8, 7), (length, 3, 2), 'body')                                  # receiver / stock
    s.box((2 + length, 9, 7.5), (5 if long else 4, 1, 1), 'barrel', 'blade')  # barrel
    s.box((2 + length - 1, 8.5, 7.25), (1, 2, 1), 'guard')                    # muzzle band
    s.box((3, 4, 7.25), (2, 4, 1), 'handle', 'wood')                          # grip
    s.box((5, 6, 7.5), (2, 1, 1), 'guard')                                    # trigger guard
    s.box((4, 11, 7.5), (2, 1, 1), 'guard')                                   # hammer / sight
    if long:
        s.box((-2, 7, 7.25), (4, 3, 1), 'handle', 'wood')                     # stock
    return s


def book():
    s = Shape()
    s.box((4, 2, 6), (8, 11, 1), 'body', 'plate')                             # front cover
    s.box((4, 2, 9), (8, 11, 1), 'body', 'plate')                             # back cover
    s.box((4.5, 2.5, 7), (7, 10, 2), 'page', 'page')                          # pages
    s.box((3, 2, 6), (1, 11, 4), 'body', 'plate')                             # spine
    s.box((6.5, 6, 5.5), (3, 3, 1), 'guard', 'gem')                           # emblem on the cover
    return s


def shape_for(name):
    if name in SWORDS:
        return 'sword', sword()
    if name in SHORTSWORDS:
        return 'shortsword', sword(blade_len=8, blade_w=2, guard_w=5, grip=3)
    if name in GREATSWORDS:
        return 'greatsword', sword(blade_len=14, blade_w=4, guard_w=9, grip=5)
    if name in KATANAS:
        return 'katana', sword(blade_len=15, blade_w=2, grip=5, katana=True)
    for kind, names in (('pickaxe', PICKAXES), ('axe', AXES), ('hamaxe', HAMAXES), ('hammer', HAMMERS)):
        if name in names:
            return kind, tool(kind)
    if name in BOWS:
        return 'bow', bow()
    if name in GUNS:
        return 'gun', gun()
    if name in LONG_GUNS:
        return 'long_gun', gun(long=True)
    if name in STAFFS:
        return 'staff', staff()
    if name in WANDS:
        return 'wand', staff(wand=True)
    if name in BOOKS:
        return 'book', book()
    return None, None


# ----------------------------------------------------------------------------------------- display transforms
HANDHELD_TILT = {'axis': 'z', 'angle': -45}
TILT = {'bow': {'axis': 'z', 'angle': -45}, 'gun': {'axis': 'z', 'angle': 45}, 'long_gun': {'axis': 'z', 'angle': 45}}
DISPLAY = {
    # vanilla bow transforms: the tilted bow ends up upright in the hand, string towards the player
    'bow': {
        'thirdperson_righthand': {'rotation': [-80, 260, -40], 'translation': [-1, -2, 2.5], 'scale': [0.9, 0.9, 0.9]},
        'thirdperson_lefthand': {'rotation': [-80, -280, 40], 'translation': [-1, -2, 2.5], 'scale': [0.9, 0.9, 0.9]},
        'firstperson_righthand': {'rotation': [0, -90, 25], 'translation': [1.13, 3.2, 1.13], 'scale': [0.68, 0.68, 0.68]},
        'firstperson_lefthand': {'rotation': [0, 90, -25], 'translation': [1.13, 3.2, 1.13], 'scale': [0.68, 0.68, 0.68]},
    },
    # the tilted gun is held like a sword but levelled out, so the barrel points along the aim
    'gun': {
        'thirdperson_righthand': {'rotation': [0, -90, 50], 'translation': [0, 3, 1], 'scale': [0.85, 0.85, 0.85]},
        'thirdperson_lefthand': {'rotation': [0, 90, -50], 'translation': [0, 3, 1], 'scale': [0.85, 0.85, 0.85]},
        'firstperson_righthand': {'rotation': [0, -90, 85], 'translation': [1.13, 2.2, 1.13], 'scale': [0.68, 0.68, 0.68]},
        'firstperson_lefthand': {'rotation': [0, 90, -85], 'translation': [1.13, 2.2, 1.13], 'scale': [0.68, 0.68, 0.68]},
    },
    'book': {
        'thirdperson_righthand': {'rotation': [0, 90, 0], 'translation': [0, 3, 1], 'scale': [0.55, 0.55, 0.55]},
        'thirdperson_lefthand': {'rotation': [0, -90, 0], 'translation': [0, 3, 1], 'scale': [0.55, 0.55, 0.55]},
        'firstperson_righthand': {'rotation': [0, -60, 10], 'translation': [2, 2.5, 0], 'scale': [0.35, 0.35, 0.35]},
        'firstperson_lefthand': {'rotation': [0, 60, -10], 'translation': [2, 2.5, 0], 'scale': [0.35, 0.35, 0.35]},
        'ground': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [0.5, 0.5, 0.5]},
    },
}
DISPLAY['long_gun'] = DISPLAY['gun']


class Mats:
    def __init__(self, mats):
        self.mats = mats


def element(cube, tex_w, tex_h, rotation):
    x, y, z = cube.origin
    w, h, d = cube.size
    el = {'from': [x, y, z], 'to': [x + w, y + h, z + d], 'shade': False, 'faces': {}}
    if rotation:
        el['rotation'] = dict(rotation, origin=[8, 8, 8])
    sx, sy = 16.0 / tex_w, 16.0 / tex_h
    for face, (u, v, fw, fh) in am.faces_of(cube).items():
        if fw == 0 or fh == 0:
            continue
        name = {'up': 'up', 'down': 'down', 'east': 'west', 'west': 'east', 'north': 'north', 'south': 'south'}[face]
        el['faces'][name] = {'uv': [u * sx, v * sy, (u + fw) * sx, (v + fh) * sy], 'texture': '#tex'}
    return el


def write(assets, tex):
    written = []
    for names in (SWORDS, SHORTSWORDS, GREATSWORDS, KATANAS, PICKAXES, AXES, HAMAXES, HAMMERS, BOWS, GUNS, LONG_GUNS, STAFFS, WANDS, BOOKS):
        for name in names:
            if not os.path.exists(os.path.join(tex, 'item', name + '.png')):
                continue
            kind, shape = shape_for(name)
            cols = colours(kind, load_icon(tex, name))
            mats = Mats({k: v for k, v in cols.items()})
            for key, base in (('blade', 'guard'), ('handle', 'guard'), ('guard', 'handle'), ('gem', 'guard'), ('string', 'handle'),
                              ('page', 'body'), ('barrel', 'body'), ('body', 'handle')):
                mats.mats.setdefault(key, mats.mats.get(base, (128, 128, 128, 255)))
            mats.mats['dark'] = shade(mats.mats.get('handle', (80, 60, 40, 255)), 0.5)
            w, h = am.pack(shape.cubes, 32)
            canvas = Canvas(w, h)
            rnd = random.Random(name)
            for cube in shape.cubes:
                am.paint(mats, canvas, cube, rnd)
            canvas.save(os.path.join(tex, 'item/3d', name + '.png'))
            rotation = TILT.get(kind, HANDHELD_TILT if kind not in DISPLAY else None)
            model = {'textures': {'tex': f'terracraft:item/3d/{name}', 'particle': f'terracraft:item/{name}'},
                     'elements': [element(c, w, h, rotation) for c in shape.cubes]}
            model['parent'] = 'minecraft:item/handheld'
            if kind in DISPLAY:
                model['display'] = DISPLAY[kind]
            path = os.path.join(assets, 'models/item', name + '_3d.json')
            with open(path, 'w') as f:
                json.dump(model, f, indent=1)
            # icon in the inventory, 3D everywhere else
            with open(os.path.join(assets, 'items', name + '.json'), 'w') as f:
                json.dump({'model': {'type': 'minecraft:select', 'property': 'minecraft:display_context',
                                     'cases': [{'when': ['gui'], 'model': {'type': 'minecraft:model', 'model': f'terracraft:item/{name}'}}],
                                     'fallback': {'type': 'minecraft:model', 'model': f'terracraft:item/{name}_3d'}}}, f, indent=2)
                f.write('\n')
            written.append(name)
    return written


if __name__ == '__main__':
    root = os.path.join(os.path.dirname(__file__), '..', 'src/main/resources/assets/terracraft')
    print(len(write(root, os.path.join(root, 'textures'))), 'item models')
