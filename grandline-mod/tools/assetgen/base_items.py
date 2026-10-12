"""Crew base assets: the crew flag (a Jolly Roger on its pole, a block with its own model) and the base chest
(an iron-bound sea crate), with their block models, loot tables, recipes and tags.

Ids come from base/BaseRegistry.java (see source.block_ids); gen_assets merges block_models(), block_loot(),
recipes() and block_tags(); the textures register themselves into blocks.BLOCKS."""
from .canvas import Canvas, C, mix, hash01
from .blocks import block
from .data import NS, shaped


# ============================================================================================ textures
SKULL = [
    '................',
    '................',
    '.....wwwwww.....',
    '....wwwwwwww....',
    '....wwwwwwww....',
    '....wkkwwkkw....',
    '....wkkwwkkw....',
    '....wwwwwwww....',
    '.....wwkkww.....',
    '......wwww......',
    '..bb..wkwk..bb..',
    '...bb......bb...',
    '.....bbbbbb.....',
    '...bb......bb...',
    '..bb........bb..',
    '................',
]


@block('crew_flag')
def _crew_flag():
    """A black Jolly Roger: skull over crossed bones, a little frayed at the fly."""
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            base = C('#16161a') if (x + y * 3) % 7 else C('#1e1e24')
            if hash01(x, y, 91) > 0.86:
                base = C('#24242c')
            c.set(x, y, base)
    for y, row in enumerate(SKULL):
        for x, ch in enumerate(row):
            if ch == 'w':
                c.set(x, y, mix(C('#f2eedc'), C('#c8c2aa'), 0.25 if (x + y) % 5 == 0 else 0.0))
            elif ch == 'b':
                c.set(x, y, C('#e6e0c8'))
            elif ch == 'k':
                c.set(x, y, C('#0a0a0c'))
    # frayed fly edge and a faint fold shadow
    for y in range(16):
        if hash01(15, y, 7) > 0.6:
            c.set(15, y, (0, 0, 0, 0))
        c.set(0, y, mix(c.get(0, y), C('#000000'), 0.35))
    return c


@block('crew_flag_pole')
def _crew_flag_pole():
    """Dark weathered wood with a brass band near the top."""
    c = Canvas(16, 16)
    for y in range(16):
        for x in range(16):
            stripe = (x // 2) % 3
            col = [C('#4a3220'), C('#5a3e28'), C('#3e2a1a')][stripe]
            if hash01(x, y, 13) > 0.8:
                col = mix(col, C('#2a1c10'), 0.5)
            c.set(x, y, col)
    for x in range(16):
        c.set(x, 1, C('#c8a040'))
        c.set(x, 2, C('#8a6a24'))
    return c


def crate(top=False):
    c = Canvas(16, 16)
    planks = ['#7a5232', '#86603c', '#6e4a2c', '#8e6842']
    for y in range(16):
        for x in range(16):
            band = (x // 4) if top else (y // 4)
            col = C(planks[band % 4])
            if hash01(x, y, 29) > 0.82:
                col = mix(col, C('#4a301c'), 0.4)
            if (top and x % 4 == 3) or (not top and y % 4 == 3):
                col = mix(col, C('#3a2414'), 0.55)
            c.set(x, y, col)
    iron, rivet = C('#4c4f56'), C('#a6aab2')
    for i in range(16):
        for (x, y) in ((i, 0), (0, i), (i, 15), (15, i)):
            c.set(x, y, iron)
    for (x, y) in ((1, 1), (14, 1), (1, 14), (14, 14)):
        c.set(x, y, rivet)
    if top:
        # an anchor branded into the lid
        for (x, y) in ((7, 4), (8, 4), (7, 5), (8, 5), (6, 6), (7, 6), (8, 6), (9, 6), (7, 7), (8, 7), (7, 8), (8, 8), (7, 9), (8, 9),
                       (7, 10), (8, 10), (4, 9), (11, 9), (4, 10), (5, 11), (6, 11), (7, 11), (8, 11), (9, 11), (10, 11), (11, 10)):
            c.set(x, y, C('#3a2210'))
    else:
        # a padlocked hasp in the middle
        for x in range(6, 10):
            for y in range(6, 10):
                c.set(x, y, iron)
        c.set(7, 7, C('#c8a040'))
        c.set(8, 7, C('#c8a040'))
        c.set(7, 8, C('#8a6a24'))
        c.set(8, 8, C('#1a1a1e'))
    return c


@block('base_chest')
def _base_chest_side():
    return crate(False)


@block('base_chest_top')
def _base_chest_top():
    return crate(True)


# ============================================================================================ models and data
def _face(uv, tex):
    return {'uv': uv, 'texture': tex}


def block_models():
    pole = {d: _face([7, 0, 9, 16], '#pole') for d in ('north', 'south', 'east', 'west')}
    pole['up'] = _face([7, 7, 9, 9], '#pole')
    pole['down'] = _face([7, 7, 9, 9], '#pole')
    cloth = {
        'north': _face([16, 0, 0, 16], '#cloth'),
        'south': _face([0, 0, 16, 16], '#cloth'),
        'east': _face([15, 0, 16, 16], '#cloth'),
        'up': _face([0, 0, 16, 1], '#cloth'),
        'down': _face([0, 15, 16, 16], '#cloth'),
    }
    return {
        'crew_flag': {
            'parent': 'minecraft:block/block',
            'render_type': 'minecraft:cutout',
            'textures': {'particle': f'{NS}:block/crew_flag_pole', 'pole': f'{NS}:block/crew_flag_pole', 'cloth': f'{NS}:block/crew_flag'},
            'elements': [
                {'from': [7, 0, 7], 'to': [9, 16, 9], 'faces': pole},
                {'from': [9, 7, 7.5], 'to': [16, 15, 8.5], 'faces': cloth},
            ],
        },
        'base_chest': {'parent': 'minecraft:block/cube_bottom_top',
                       'textures': {'top': f'{NS}:block/base_chest_top', 'bottom': f'{NS}:block/base_chest_top', 'side': f'{NS}:block/base_chest'}},
    }


def block_loot():
    return {
        # the flag never drops (it comes down with the claim)
        'crew_flag': {'type': 'minecraft:block', 'random_sequence': f'{NS}:blocks/crew_flag', 'pools': []},
        'base_chest': {'type': 'minecraft:block', 'random_sequence': f'{NS}:blocks/base_chest', 'pools': [{
            'rolls': 1.0, 'bonus_rolls': 0.0, 'conditions': [{'condition': 'minecraft:survives_explosion'}],
            'entries': [{'type': 'minecraft:item', 'name': f'{NS}:base_chest'}]}]},
    }


def recipes():
    return {
        'crew_flag': shaped(['SLL', 'SLL', 'S  '], {'S': 'stick', 'L': 'leather'}, 'crew_flag', 1, 'misc'),
        'base_chest': shaped([' I ', 'PBP', ' I '], {'I': 'iron_ingot', 'P': '#minecraft:planks', 'B': 'barrel'}, 'base_chest', 1, 'misc'),
    }


def block_tags():
    return {'mineable/axe': {'replace': False, 'values': [f'{NS}:base_chest']}}
