"""The architectural building kit: one spec list drives the Java registry (registry/ArchBlocks.java, generated), the
textures, block and item models, block states, loot tables, recipes, tags and English names. Called from
tools/gen_assets.py (so --prune knows these files); the structure builders use the blocks through ArchBlocks.

Families (16x16, vanilla density):
  plasters (6 colours) + stairs/slabs; timber framing (4 patterns); beams (3 woods, pillars); weathered and dock
  planks + stairs/slabs; roof tiles (terracotta barrel, slate, teal glaze, kawara, thatch, sky scale) + stairs/slabs;
  dressed sandstone, pale masonry, fieldstone, harbour stone + stairs/slabs/walls; sandstone and marble columns;
  louvred shutters (4, trapdoor-based); lattice and mullioned windows (panes); striped awnings (4); props (cargo
  crate, sacks, rope coil, bollard, deck cannon, shop counter, goods shelf, table, bench, fishing net, planter);
  lanterns (red and white paper, brass).
"""
import os

from . import arch_textures as T

NS = 'grandline'

# ------------------------------------------------------------------------------------------ the spec
BLOCKS = []      # dicts: id, kind, ...
TEXTURES = {}    # texture name -> painter


def tex(name, fn):
    TEXTURES[name] = fn
    return f'{NS}:block/{name}'


def add(**b):
    BLOCKS.append(b)
    return b


def family(base_id, copy, tool, textures, name, shapes=('stairs', 'slab'), sound=None, cut_from=None, recipe=None):
    """A cube block plus its stairs/slab/wall. textures: {'all': ref} or {'side','top','bottom'}."""
    add(id=base_id, kind='cube', copy=copy, tool=tool, tex=textures, name=name, recipe=recipe, cut_from=cut_from, family=base_id)
    stem = base_id[:-1] if base_id.endswith('s') and base_id.endswith(('tiles', 'bricks', 'planks')) else base_id
    if 'stairs' in shapes:
        add(id=stem + '_stairs', kind='stairs', base=base_id, copy=copy, tool=tool, tex=textures, name=name.rstrip('s') + ' Stairs' if name.endswith('Tiles') or name.endswith('Planks') else name + ' Stairs', family=base_id)
    if 'slab' in shapes:
        add(id=stem + '_slab', kind='slab', base=base_id, copy=copy, tool=tool, tex=textures, name=name.rstrip('s') + ' Slab' if name.endswith('Tiles') or name.endswith('Planks') else name + ' Slab', family=base_id)
    if 'wall' in shapes:
        add(id=stem + '_wall', kind='wall', base=base_id, copy=copy, tool=tool, tex=textures, name=name + ' Wall', family=base_id)


# plasters: warm lime and coloured stucco
PLASTERS = [('white', '#ece6da', 'White'), ('cream', '#e4d2ac', 'Cream'), ('ochre', '#d9a85a', 'Ochre'),
            ('salmon', '#dc9a82', 'Salmon'), ('sky', '#9fc6dc', 'Sky Blue'), ('mint', '#a9cfac', 'Mint')]
DYES = {'white': 'white_dye', 'cream': 'yellow_dye', 'ochre': 'orange_dye', 'salmon': 'pink_dye', 'sky': 'light_blue_dye', 'mint': 'lime_dye'}
for i, (c, col, nm) in enumerate(PLASTERS):
    family(f'{c}_plaster', 'TERRACOTTA', 'pickaxe', {'all': tex(f'{c}_plaster', lambda col=col, i=i: T.plaster(col, 11 + i))}, f'{nm} Plaster',
           recipe=('plaster', c))

# timber framing: dark oak timber over cream lime
for kind, nm in (('plain', 'Timber Frame'), ('cross', 'Crossed Timber Frame'), ('diagonal', 'Braced Timber Frame'), ('studs', 'Studded Timber Frame')):
    bid = 'timber_frame' if kind == 'plain' else f'timber_frame_{kind}'
    add(id=bid, kind='cube', copy='OAK_PLANKS', tool='axe', tex={'all': tex(bid, lambda kind=kind: T.timber_frame(kind))}, name=nm, recipe=('timber', kind), family='timber_frame')

# beams (pillars)
for bid, col, nm, log in (('oak_beam', '#9a6a3c', 'Oak Beam', 'stripped_oak_log'), ('dark_beam', '#4b3424', 'Dark Timber Beam', 'stripped_dark_oak_log'),
                          ('weathered_beam', '#8b8273', 'Weathered Beam', 'stripped_spruce_log')):
    add(id=bid, kind='pillar', copy='STRIPPED_OAK_LOG', tool='axe', name=nm, recipe=('beam', log), family=bid,
        tex={'side': tex(bid, lambda col=col: T.beam_side(col, 3)), 'end': tex(bid + '_top', lambda col=col: T.beam_end(col, 3))})

# planks
family('weathered_planks', 'OAK_PLANKS', 'axe', {'all': tex('weathered_planks', lambda: T.planks('#8f8676', 7, weathered=True))}, 'Weathered Planks', recipe=('planks', 'weathered'))
family('dock_planks', 'OAK_PLANKS', 'axe', {'all': tex('dock_planks', lambda: T.planks('#8a6440', 9, gap_dark=1.8))}, 'Dock Planks', recipe=('planks', 'dock'))

# roof tiles
ROOFS = [('terracotta_roof_tiles', 'Terracotta Roof Tiles', lambda: T.barrel_tiles('#b5532e', 4), 'BRICKS', 'terracotta'),
         ('slate_roof_tiles', 'Slate Roof Tiles', lambda: T.slate_tiles('#56616e', 6), 'DEEPSLATE_TILES', 'cobbled_deepslate'),
         ('teal_roof_tiles', 'Teal Glazed Roof Tiles', lambda: T.barrel_tiles('#2b8b84', 8, glaze=True), 'BRICKS', 'cyan_terracotta'),
         ('kawara_roof_tiles', 'Kawara Roof Tiles', lambda: T.barrel_tiles('#56606e', 10, cap=True), 'DEEPSLATE_TILES', 'gray_terracotta'),
         ('sky_roof_tiles', 'Sky Scale Roof Tiles', lambda: T.scale_tiles('#86bde3', 12), 'BRICKS', 'light_blue_terracotta'),
         ('thatch', 'Thatch', lambda: T.thatch('#c7a14c', 14), 'HAY_BLOCK', None)]
for rid, nm, fn, copy, src in ROOFS:
    tool = 'hoe' if rid == 'thatch' else 'pickaxe'
    family(rid, copy, tool, {'all': tex(rid, fn)}, nm, cut_from=src, recipe=('thatch', None) if rid == 'thatch' else None)

# dressed stone
family('dressed_sandstone', 'SANDSTONE', 'pickaxe', {'all': tex('dressed_sandstone', lambda: T.ashlar('#d9c38c', '#b49c66', 3))}, 'Dressed Sandstone',
       shapes=('stairs', 'slab', 'wall'), cut_from='sandstone')
family('pale_masonry', 'STONE_BRICKS', 'pickaxe', {'all': tex('pale_masonry', lambda: T.ashlar('#e2e0d8', '#aeb0ac', 5, h=8, w=16, chisel=False))}, 'Pale Masonry',
       shapes=('stairs', 'slab', 'wall'), cut_from='calcite')
family('fieldstone', 'COBBLESTONE', 'pickaxe', {'all': tex('fieldstone', lambda: T.voronoi_stones(['#8d8a83', '#7c7a76', '#9a8e7a', '#77808a', '#8a8478'], '#4a4642', 7, 3))},
       'Fieldstone', shapes=('stairs', 'slab', 'wall'), recipe=('fieldstone', None))
family('harbour_stone', 'STONE_BRICKS', 'pickaxe', {'all': tex('harbour_stone', lambda: T.ashlar('#6b706a', '#3f4440', 9, h=8, w=8))}, 'Harbour Stone',
       shapes=('stairs', 'slab', 'wall'), recipe=('harbour', None))

# columns
for bid, col, nm, src in (('sandstone_column', '#dcc690', 'Sandstone Column', 'sandstone'), ('marble_column', '#ebe7df', 'Marble Column', 'quartz_block')):
    add(id=bid, kind='pillar', copy='QUARTZ_PILLAR', tool='pickaxe', name=nm, cut_from=src, family=bid,
        tex={'side': tex(bid, lambda col=col: T.fluted(col, 1)), 'end': tex(bid + '_top', lambda col=col: T.column_top(col))})

# shutters (open trapdoors beside windows)
for c, col, nm, dye in (('green', '#4f7d3a', 'Green', 'green_dye'), ('blue', '#3b6aa0', 'Blue', 'blue_dye'), ('red', '#a63b32', 'Red', 'red_dye'),
                        ('oak', '#9a6a3c', 'Oak', None)):
    add(id=f'{c}_shutter', kind='trapdoor', copy='OAK_TRAPDOOR', tool='axe', name=f'{nm} Shutter', recipe=('shutter', dye), family='shutters',
        tex={'texture': tex(f'{c}_shutter', lambda col=col: T.shutter(col, 2))})

# windows
add(id='lattice_window', kind='pane', copy='OAK_FENCE', tool='axe', name='Lattice Window', render='cutout', recipe=('lattice', None), family='windows',
    tex={'pane': tex('lattice_window', lambda: T.lattice('#6e4a2c', 1)), 'edge': tex('lattice_window_edge', lambda: T.pane_edge('#6e4a2c'))})
add(id='mullion_window', kind='pane', copy='GLASS_PANE', tool=None, name='Mullioned Window', render='translucent', recipe=('mullion', None), family='windows',
    tex={'pane': tex('mullion_window', lambda: T.mullion('#e8e2d4')), 'edge': tex('mullion_window_edge', lambda: T.pane_edge('#e8e2d4'))})

# striped awnings
for c, col, nm, wool in (('red', '#b8382f', 'Red', 'red_wool'), ('blue', '#2f5f9e', 'Blue', 'blue_wool'), ('green', '#3f8a46', 'Green', 'green_wool'),
                         ('yellow', '#d9a632', 'Yellow', 'yellow_wool')):
    add(id=f'{c}_awning', kind='decor', shape='AWNING', copy='WHITE_WOOL', tool=None, name=f'{nm} Striped Awning', model='awning', recipe=('awning', wool),
        family='awnings', tex={'canvas': tex(f'{c}_awning', lambda col=col: T.awning(col, 1)), 'edge': tex(f'{c}_awning_edge', lambda col=col: T.awning_edge(col))})

# props
WOODTEX = {'wood': tex('prop_wood', lambda: T.planks('#9a7048', 13, nails=False))}
add(id='cargo_crate', kind='cube', copy='OAK_PLANKS', tool='axe', name='Cargo Crate', recipe=('crate', None), family='cargo',
    tex={'side': tex('cargo_crate', lambda: T.crate_side(2)), 'top': tex('cargo_crate_top', lambda: T.crate_top(2))})
add(id='cargo_sacks', kind='decor', shape='SACKS', copy='WHITE_WOOL', tool=None, name='Cargo Sacks', model='sacks', recipe=('sacks', None), family='cargo',
    tex={'cloth': tex('burlap', lambda: T.burlap('#b39765', 3))})
add(id='rope_coil', kind='decor', shape='ROPE_COIL', copy='WHITE_WOOL', tool=None, name='Rope Coil', model='rope_coil', recipe=('rope', None), family='cargo',
    tex={'rope': tex('rope', lambda: T.rope('#c2a06a', 1))})
add(id='bollard', kind='decor', shape='BOLLARD', copy='IRON_BLOCK', tool='pickaxe', name='Mooring Bollard', model='bollard', recipe=('bollard', None), family='dock',
    tex={'iron': tex('dock_iron', lambda: T.iron('#3d4147', 5))})
add(id='deck_cannon', kind='decor', shape='CANNON', copy='IRON_BLOCK', tool='pickaxe', name='Deck Cannon', model='cannon', recipe=('cannon', None), family='dock',
    tex={'iron': f'{NS}:block/dock_iron', 'wood': WOODTEX['wood']})
add(id='shop_counter', kind='decor', shape='COUNTER', copy='OAK_PLANKS', tool='axe', name='Shop Counter', model='counter', recipe=('counter', None), family='furniture',
    tex={'front': tex('counter_front', lambda: T.counter_front('#8a5f38', 4)), 'top': tex('counter_top', lambda: T.polished_wood('#5c3b22', 2)), 'wood': WOODTEX['wood']})
add(id='goods_shelf', kind='decor', shape='FULL', copy='OAK_PLANKS', tool='axe', name='Goods Shelf', model='shelf', recipe=('shelf', None), family='furniture',
    tex={'front': tex('goods_shelf', lambda: T.shelf_front(5)), 'wood': WOODTEX['wood']})
add(id='plank_table', kind='decor', shape='TABLE', copy='OAK_PLANKS', tool='axe', name='Plank Table', model='table', recipe=('table', None), family='furniture',
    tex={'top': f'{NS}:block/counter_top', 'wood': WOODTEX['wood']})
add(id='plank_bench', kind='decor', shape='BENCH', copy='OAK_PLANKS', tool='axe', name='Plank Bench', model='bench', recipe=('bench', None), family='furniture',
    tex={'top': f'{NS}:block/counter_top', 'wood': WOODTEX['wood']})
add(id='fishing_net', kind='decor', shape='WALL_PANEL', copy='WHITE_WOOL', tool=None, name='Fishing Net', model='panel', recipe=('net', None), family='dock',
    render='cutout', nocollide=True, tex={'panel': tex('fishing_net', lambda: T.net(1))})
add(id='flower_planter', kind='decor', shape='PLANTER', copy='OAK_PLANKS', tool='axe', name='Window Planter', model='planter', recipe=('planter', None), family='furniture',
    render='cutout', tex={'wood': WOODTEX['wood'], 'soil': 'minecraft:block/rooted_dirt', 'flower_a': 'minecraft:block/poppy',
                          'flower_b': 'minecraft:block/cornflower'})

# lanterns
PAPER = ('BlockBehaviour.Properties.of().mapColor(net.minecraft.world.level.material.MapColor.COLOR_RED).strength(0.6f).sound(SoundType.WOOL)'
         '.lightLevel(s -> 15).noOcclusion().pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)')
add(id='red_paper_lantern', kind='lantern', copy='LANTERN', tool=None, props=PAPER, name='Red Paper Lantern', model='paper_lantern', recipe=('paper_lantern', 'red_dye'), family='lanterns',
    sound='WOOL', tex={'paper': tex('red_paper_lantern', lambda: T.paper_lantern('#c23a2c', 1)), 'cap': tex('lantern_cap', lambda: T.lantern_cap(1))})
add(id='white_paper_lantern', kind='lantern', copy='LANTERN', tool=None, props=PAPER.replace('COLOR_RED', 'SNOW'), name='White Paper Lantern', model='paper_lantern', recipe=('paper_lantern', None), family='lanterns',
    sound='WOOL', tex={'paper': tex('white_paper_lantern', lambda: T.paper_lantern('#efe6cf', 2)), 'cap': f'{NS}:block/lantern_cap'})
add(id='brass_lantern', kind='lantern', copy='LANTERN', tool='pickaxe', name='Brass Lantern', model='brass_lantern', recipe=('brass_lantern', None), family='lanterns',
    tex={'glass': tex('brass_lantern', lambda: T.brass_lantern(1)), 'brass': tex('brass', lambda: T.brass(1))})

BY_ID = {b['id']: b for b in BLOCKS}


# ------------------------------------------------------------------------------------------ models
def el(f, t, faces, rot=None, shade=True):
    e = {'from': list(f), 'to': list(t), 'faces': faces}
    if rot:
        e['rotation'] = rot
    if not shade:
        e['shade'] = False
    return e


def box(f, t, texv, cull=None, uv=None):
    faces = {}
    for d in ('down', 'up', 'north', 'south', 'west', 'east'):
        fd = {'texture': texv}
        if uv:
            fd['uv'] = uv
        if cull and d in cull:
            fd['cullface'] = d
        faces[d] = fd
    return el(f, t, faces)


def faces_tex(f, t, mapping):
    """mapping: dir -> texture var."""
    return el(f, t, {d: {'texture': v} for d, v in mapping.items()})


DECOR_ELEMENTS = {
    'sacks': lambda: [box((1, 0, 1), (8, 7, 8), '#cloth'), box((8, 0, 2), (15, 6, 9), '#cloth'), box((3, 0, 8), (11, 9, 15), '#cloth'),
                      box((4, 7, 3), (6, 8, 5), '#cloth'), box((6, 9, 10), (8, 10, 12), '#cloth')],
    'rope_coil': lambda: [box((2, 0, 4), (14, 4, 12), '#rope'), box((4, 0, 2), (12, 4, 14), '#rope'), box((5, 4, 5), (11, 5, 11), '#rope')],
    'bollard': lambda: [box((5, 0, 5), (11, 10, 11), '#iron'), box((4, 10, 4), (12, 12, 12), '#iron'), box((6, 12, 6), (10, 14, 10), '#iron'),
                        box((4, 0, 4), (12, 2, 12), '#iron')],
    'cannon': lambda: [box((3, 1, 3), (13, 4, 14), '#wood'), box((2, 0, 3), (3, 4, 7), '#wood'), box((13, 0, 3), (14, 4, 7), '#wood'),
                       box((2, 0, 10), (3, 4, 14), '#wood'), box((13, 0, 10), (14, 4, 14), '#wood'),
                       box((5, 4, 1), (11, 9, 15), '#iron'), box((4, 3, 0), (12, 10, 2), '#iron'), box((7, 5, 15), (9, 7, 16), '#iron')],
    'counter': lambda: [faces_tex((0, 0, 2), (16, 13, 16), {'north': '#front', 'south': '#wood', 'west': '#wood', 'east': '#wood', 'down': '#wood'}),
                        box((0, 13, 0), (16, 16, 16), '#top')],
    'shelf': lambda: [faces_tex((0, 0, 0), (16, 16, 16), {'north': '#front', 'south': '#wood', 'west': '#wood', 'east': '#wood', 'up': '#wood', 'down': '#wood'})],
    'table': lambda: [box((0, 13, 0), (16, 16, 16), '#top'), box((6, 1, 6), (10, 13, 10), '#wood'), box((2, 0, 7), (14, 1, 9), '#wood'), box((7, 0, 2), (9, 1, 14), '#wood')],
    'bench': lambda: [box((0, 6, 3), (16, 8, 13), '#top'), box((1, 0, 4), (3, 6, 12), '#wood'), box((13, 0, 4), (15, 6, 12), '#wood')],
    'panel': lambda: [el((0, 0, 15.2), (16, 16, 15.2), {'north': {'texture': '#panel'}, 'south': {'texture': '#panel', 'uv': [16, 0, 0, 16]}})],
    'planter': lambda: [box((1, 0, 1), (15, 6, 15), '#wood'), box((2, 6, 2), (14, 7, 14), '#soil'),
                        el((3, 7, 3), (13, 13, 13), {'north': {'texture': '#flower_a'}, 'south': {'texture': '#flower_a'}},
                           rot={'origin': [8, 8, 8], 'axis': 'y', 'angle': 45, 'rescale': True}, shade=False),
                        el((3, 7, 3), (13, 13, 13), {'west': {'texture': '#flower_b'}, 'east': {'texture': '#flower_b'}},
                           rot={'origin': [8, 8, 8], 'axis': 'y', 'angle': 45, 'rescale': True}, shade=False)],
    'awning': lambda: [el((0, 11.5, 0), (16, 12.5, 16), {'up': {'texture': '#canvas'}, 'down': {'texture': '#canvas'},
                                                          'north': {'texture': '#canvas', 'uv': [0, 0, 16, 1]}, 'west': {'texture': '#canvas', 'uv': [0, 0, 16, 1]},
                                                          'east': {'texture': '#canvas', 'uv': [0, 0, 16, 1]}},
                          rot={'origin': [8, 12, 8], 'axis': 'x', 'angle': -22.5, 'rescale': True}),
                       el((0, 4.5, 0.0), (16, 8.7, 0.0), {'north': {'texture': '#edge', 'uv': [0, 0, 16, 16]}, 'south': {'texture': '#edge', 'uv': [16, 0, 0, 16]}})],
}


def lantern_elements(model, hanging):
    if model == 'paper_lantern':
        y = 2 if hanging else 0
        els = [box((4, y + 1, 4), (12, y + 11, 12), '#paper'), box((5, y, 5), (11, y + 1, 11), '#cap'), box((5, y + 11, 5), (11, y + 12, 11), '#cap')]
        if hanging:
            els.append(box((7.5, y + 12, 7.5), (8.5, 16, 8.5), '#cap'))
        else:
            els.append(box((7.5, 12, 7.5), (8.5, 13, 8.5), '#cap'))
        return els
    y = 3 if hanging else 0
    els = [box((5, y, 5), (11, y + 1, 11), '#brass'), box((5, y + 1, 5), (11, y + 8, 11), '#glass'), box((4, y + 8, 4), (12, y + 9, 12), '#brass'),
           box((7, y + 9, 7), (9, y + 11, 9), '#brass')]
    if hanging:
        els.append(box((7.5, y + 11, 7.5), (8.5, 16, 8.5), '#brass'))
    return els


def block_models():
    """model name -> json for every model of every kit block."""
    out = {}
    for b in BLOCKS:
        i, t, k = b['id'], b['tex'], b['kind']
        rt = {'render_type': f"minecraft:{b['render']}"} if b.get('render') else {}
        if k in ('cube', 'stairs', 'slab', 'wall'):
            fam = BY_ID[b.get('base', i)]
            ft = fam['tex']
            side = ft.get('all', ft.get('side'))
            top = ft.get('all', ft.get('top'))
            bottom = ft.get('all', ft.get('bottom', top))
            if k == 'cube':
                if 'all' in ft:
                    out[i] = {'parent': 'minecraft:block/cube_all', 'textures': {'all': side}}
                else:
                    out[i] = {'parent': 'minecraft:block/cube_bottom_top', 'textures': {'side': side, 'top': top, 'bottom': bottom}}
            elif k == 'stairs':
                tx = {'bottom': bottom, 'top': top, 'side': side}
                out[i] = {'parent': 'minecraft:block/stairs', 'textures': tx}
                out[i + '_inner'] = {'parent': 'minecraft:block/inner_stairs', 'textures': tx}
                out[i + '_outer'] = {'parent': 'minecraft:block/outer_stairs', 'textures': tx}
            elif k == 'slab':
                tx = {'bottom': bottom, 'top': top, 'side': side}
                out[i] = {'parent': 'minecraft:block/slab', 'textures': tx}
                out[i + '_top'] = {'parent': 'minecraft:block/slab_top', 'textures': tx}
            else:
                out[i + '_post'] = {'parent': 'minecraft:block/template_wall_post', 'textures': {'wall': side}}
                out[i + '_side'] = {'parent': 'minecraft:block/template_wall_side', 'textures': {'wall': side}}
                out[i + '_side_tall'] = {'parent': 'minecraft:block/template_wall_side_tall', 'textures': {'wall': side}}
                out[i + '_inventory'] = {'parent': 'minecraft:block/wall_inventory', 'textures': {'wall': side}}
        elif k == 'pillar':
            tx = {'side': t['side'], 'end': t['end']}
            out[i] = {'parent': 'minecraft:block/cube_column', 'textures': tx}
            out[i + '_horizontal'] = {'parent': 'minecraft:block/cube_column_horizontal', 'textures': tx}
        elif k == 'trapdoor':
            for part in ('bottom', 'top', 'open'):
                out[f'{i}_{part}'] = {'parent': f'minecraft:block/template_trapdoor_{part}', 'textures': {'texture': t['texture']}, **rt}
        elif k == 'pane':
            for part in ('post', 'side', 'side_alt', 'noside', 'noside_alt'):
                out[f'{i}_{part}'] = {'parent': f'minecraft:block/template_glass_pane_{part}', 'textures': {'pane': t['pane'], 'edge': t['edge']}, **rt}
        elif k == 'decor':
            tx = dict(t)
            tx['particle'] = next(iter(t.values()))
            out[i] = {'parent': 'minecraft:block/block', 'textures': tx, 'elements': DECOR_ELEMENTS[b['model']](), **rt}
        elif k == 'lantern':
            tx = dict(t)
            tx['particle'] = next(iter(t.values()))
            out[i] = {'parent': 'minecraft:block/block', 'textures': tx, 'elements': lantern_elements(b['model'], False), **rt}
            out[i + '_hanging'] = {'parent': 'minecraft:block/block', 'textures': tx, 'elements': lantern_elements(b['model'], True), **rt}
    return out


def m(name):
    return f'{NS}:block/{name}'


def stairs_state(i):
    base = {'east': 0, 'south': 90, 'west': 180, 'north': 270}
    v = {}
    for f, r in base.items():
        for half in ('bottom', 'top'):
            for shape in ('straight', 'inner_left', 'inner_right', 'outer_left', 'outer_right'):
                model = m(i) if shape == 'straight' else m(i + ('_inner' if shape.startswith('inner') else '_outer'))
                if half == 'bottom':
                    y = r - 90 if shape.endswith('left') else r
                else:
                    y = r + 90 if shape.endswith('right') else r
                e = {'model': model}
                y %= 360
                if half == 'top':
                    e['x'] = 180
                if y:
                    e['y'] = y
                if half == 'top' or y:
                    e['uvlock'] = True
                v[f'facing={f},half={half},shape={shape}'] = e
    return {'variants': v}


def blockstate(b):
    i, k = b['id'], b['kind']
    if k == 'cube':
        return {'variants': {'': {'model': m(i)}}}
    if k == 'stairs':
        return stairs_state(i)
    if k == 'slab':
        return {'variants': {'type=bottom': {'model': m(i)}, 'type=top': {'model': m(i + '_top')}, 'type=double': {'model': m(b['base'])}}}
    if k == 'wall':
        parts = [{'when': {'up': 'true'}, 'apply': {'model': m(i + '_post')}}]
        for d, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
            for h, suf in (('low', '_side'), ('tall', '_side_tall')):
                a = {'model': m(i + suf), 'uvlock': True}
                if y:
                    a['y'] = y
                parts.append({'when': {d: h}, 'apply': a})
        return {'multipart': parts}
    if k == 'pillar':
        return {'variants': {'axis=y': {'model': m(i)}, 'axis=z': {'model': m(i + '_horizontal'), 'x': 90},
                             'axis=x': {'model': m(i + '_horizontal'), 'x': 90, 'y': 90}}}
    if k == 'trapdoor':
        v = {}
        for f, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
            for half in ('bottom', 'top'):
                v[f'facing={f},half={half},open=false'] = {'model': m(f'{i}_{half}')}
                e = {'model': m(f'{i}_open')}
                if y:
                    e['y'] = y
                v[f'facing={f},half={half},open=true'] = e
        return {'variants': v}
    if k == 'pane':
        return {'multipart': [
            {'apply': {'model': m(i + '_post')}},
            {'when': {'north': 'true'}, 'apply': {'model': m(i + '_side')}},
            {'when': {'east': 'true'}, 'apply': {'model': m(i + '_side'), 'y': 90}},
            {'when': {'south': 'true'}, 'apply': {'model': m(i + '_side_alt')}},
            {'when': {'west': 'true'}, 'apply': {'model': m(i + '_side_alt'), 'y': 90}},
            {'when': {'north': 'false'}, 'apply': {'model': m(i + '_noside')}},
            {'when': {'east': 'false'}, 'apply': {'model': m(i + '_noside_alt')}},
            {'when': {'south': 'false'}, 'apply': {'model': m(i + '_noside_alt'), 'y': 90}},
            {'when': {'west': 'false'}, 'apply': {'model': m(i + '_noside'), 'y': 270}}]}
    if k == 'decor':
        v = {}
        for f, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
            e = {'model': m(i)}
            if y:
                e['y'] = y
            v[f'facing={f}'] = e
        return {'variants': v}
    if k == 'lantern':
        return {'variants': {'hanging=false': {'model': m(i)}, 'hanging=true': {'model': m(i + '_hanging')}}}
    raise ValueError(k)


def item_model(b):
    i, k = b['id'], b['kind']
    if k == 'trapdoor':
        return {'parent': m(i + '_bottom')}
    if k == 'wall':
        return {'parent': m(i + '_inventory')}
    if k == 'pane':
        return {'parent': 'minecraft:item/generated', 'textures': {'layer0': b['tex']['pane']}}
    return {'parent': m(i)}


# ------------------------------------------------------------------------------------------ data
def loot(b):
    i = b['id']
    entry = {'type': 'minecraft:item', 'name': f'{NS}:{i}'}
    if b['kind'] == 'slab':
        entry['functions'] = [{'function': 'minecraft:set_count', 'count': 2.0, 'add': False, 'conditions': [
            {'condition': 'minecraft:block_state_property', 'block': f'{NS}:{i}', 'properties': {'type': 'double'}}]},
            {'function': 'minecraft:explosion_decay'}]
    return {'type': 'minecraft:block', 'random_sequence': f'{NS}:blocks/{i}', 'pools': [{
        'rolls': 1.0, 'bonus_rolls': 0.0, 'conditions': [{'condition': 'minecraft:survives_explosion'}], 'entries': [entry]}]}


def _ing(v):
    if v.startswith('#'):
        return {'tag': v[1:]}
    return {'item': v if ':' in v else f'minecraft:{v}'}


def shaped(pattern, key, result, n=1):
    return {'type': 'minecraft:crafting_shaped', 'category': 'building', 'pattern': pattern,
            'key': {k: _ing(v) for k, v in key.items()}, 'result': {'id': f'{NS}:{result}', 'count': n}}


def shapeless(ings, result, n=1):
    return {'type': 'minecraft:crafting_shapeless', 'category': 'building', 'ingredients': [_ing(v) for v in ings],
            'result': {'id': f'{NS}:{result}', 'count': n}}


def cutting(src, result, n=1):
    return {'type': 'minecraft:stonecutting', 'ingredient': _ing(src), 'result': {'id': f'{NS}:{result}', 'count': n}}


def recipes():
    r = {}
    for b in BLOCKS:
        i, k = b['id'], b['kind']
        rec = b.get('recipe')
        if k in ('stairs', 'slab', 'wall'):
            base = f"{NS}:{b['base']}"
            if k == 'stairs':
                r[i] = shaped(['#  ', '## ', '###'], {'#': base}, i, 4)
            elif k == 'slab':
                r[i] = shaped(['###'], {'#': base}, i, 6)
            else:
                r[i] = shaped(['###', '###'], {'#': base}, i, 6)
            if BY_ID[b['base']]['tool'] == 'pickaxe':
                r[i + '_from_' + b['base'] + '_stonecutting'] = cutting(base, i, 2 if k == 'slab' else 1)
            continue
        if b.get('cut_from'):
            r[i + '_stonecutting'] = cutting(b['cut_from'], i)
        if not rec:
            continue
        what, arg = rec
        if what == 'plaster':
            if arg == 'white':
                r[i] = shaped(['SC', 'CS'], {'S': 'sand', 'C': 'clay_ball'}, i, 4)
            else:
                r[i] = shaped(['PPP', 'PDP', 'PPP'], {'P': f'{NS}:white_plaster', 'D': DYES[arg]}, i, 8)
        elif what == 'timber':
            sticks = {'plain': 2, 'cross': 2, 'diagonal': 1, 'studs': 3}[arg]
            src = f'{NS}:cream_plaster' if arg == 'plain' else f'{NS}:timber_frame'
            r[i] = shapeless([src] + ['stick'] * sticks, i, 1)
        elif what == 'beam':
            r[i] = shaped(['L', 'L'], {'L': arg}, i, 2)
        elif what == 'planks':
            if arg == 'weathered':
                r[i] = shapeless(['spruce_planks', 'spruce_planks', 'bone_meal'], i, 2)
            else:
                r[i] = shapeless(['oak_planks', 'oak_planks', 'kelp'], i, 2)
        elif what == 'thatch':
            r[i] = shaped(['WW', 'WW'], {'W': 'wheat'}, i, 2)
        elif what == 'fieldstone':
            r[i] = shaped(['CG', 'GC'], {'C': 'cobblestone', 'G': 'gravel'}, i, 4)
        elif what == 'harbour':
            r[i] = shapeless(['stone_bricks', 'kelp'], i, 1)
        elif what == 'shutter':
            r[i] = shapeless(['oak_trapdoor', arg], i, 1) if arg else shapeless(['oak_trapdoor', 'stick'], i, 1)
        elif what == 'lattice':
            r[i] = shaped(['SSS', 'S S', 'SSS'], {'S': 'stick'}, i, 8)
        elif what == 'mullion':
            r[i] = shapeless(['glass_pane', 'glass_pane', 'stick'], i, 2)
        elif what == 'awning':
            r[i] = shaped(['CWC'], {'C': arg, 'W': 'white_wool'}, i, 3)
        elif what == 'crate':
            r[i] = shaped(['PSP', 'S S', 'PSP'], {'P': '#minecraft:planks', 'S': 'stick'}, i, 2)
        elif what == 'sacks':
            r[i] = shapeless(['white_wool', 'wheat', 'wheat'], i, 1)
        elif what == 'rope':
            r[i] = shapeless(['string', 'string', 'string', 'stick'], i, 1)
        elif what == 'bollard':
            r[i] = shaped(['N', 'I', 'I'], {'N': 'iron_nugget', 'I': 'iron_ingot'}, i, 2)
        elif what == 'cannon':
            r[i] = shaped(['III', 'PSP'], {'I': 'iron_ingot', 'P': '#minecraft:planks', 'S': 'stick'}, i, 1)
        elif what == 'counter':
            r[i] = shaped(['WWW', 'P P', 'PPP'], {'W': '#minecraft:wooden_slabs', 'P': '#minecraft:planks'}, i, 2)
        elif what == 'shelf':
            r[i] = shaped(['P P', 'PFP', 'P P'], {'P': '#minecraft:planks', 'F': 'flower_pot'}, i, 2)
        elif what == 'table':
            r[i] = shaped(['WWW', ' F ', ' F '], {'W': '#minecraft:wooden_slabs', 'F': 'oak_fence'}, i, 1)
        elif what == 'bench':
            r[i] = shaped(['WWW', 'F F'], {'W': '#minecraft:wooden_slabs', 'F': 'oak_fence'}, i, 2)
        elif what == 'net':
            r[i] = shaped(['S S', ' S ', 'S S'], {'S': 'string'}, i, 2)
        elif what == 'planter':
            r[i] = shaped(['F F', 'PDP'], {'F': '#minecraft:small_flowers', 'P': '#minecraft:planks', 'D': 'dirt'}, i, 2)
        elif what == 'paper_lantern':
            ings = ['paper', 'paper', 'torch'] + ([arg] if arg else [])
            r[i] = shapeless(ings, i, 1)
        elif what == 'brass_lantern':
            r[i] = shaped(['NNN', 'NTN', 'NNN'], {'N': 'gold_nugget', 'T': 'torch'}, i, 1)
    return r


def block_tags():
    tag = lambda vals: {'replace': False, 'values': [f'{NS}:{v}' for v in vals]}
    by = lambda pred: [b['id'] for b in BLOCKS if pred(b)]
    return {
        'mineable/pickaxe': tag(by(lambda b: b['tool'] == 'pickaxe')),
        'mineable/axe': tag(by(lambda b: b['tool'] == 'axe')),
        'mineable/hoe': tag(by(lambda b: b['tool'] == 'hoe')),
        'stairs': tag(by(lambda b: b['kind'] == 'stairs')),
        'slabs': tag(by(lambda b: b['kind'] == 'slab')),
        'walls': tag(by(lambda b: b['kind'] == 'wall')),
        'trapdoors': tag(by(lambda b: b['kind'] == 'trapdoor')),
        'wooden_trapdoors': tag(by(lambda b: b['kind'] == 'trapdoor')),
    }


def item_tags():
    tag = lambda vals: {'replace': False, 'values': [f'{NS}:{v}' for v in vals]}
    by = lambda pred: [b['id'] for b in BLOCKS if pred(b)]
    return {'stairs': tag(by(lambda b: b['kind'] == 'stairs')), 'slabs': tag(by(lambda b: b['kind'] == 'slab')),
            'walls': tag(by(lambda b: b['kind'] == 'wall')), 'trapdoors': tag(by(lambda b: b['kind'] == 'trapdoor')),
            'wooden_trapdoors': tag(by(lambda b: b['kind'] == 'trapdoor'))}


def lang():
    out = {f'block.{NS}.{b["id"]}': b['name'] for b in BLOCKS}
    out['itemGroup.grandline.architecture'] = 'Grand Line Architecture'
    return out


# ------------------------------------------------------------------------------------------ Java
def java_registry():
    lines = []
    for b in BLOCKS:
        i, k = b['id'], b['kind']
        F = i.upper()
        copy = f"Blocks.{b['copy']}"
        props = b.get('props') or f'BlockBehaviour.Properties.ofFullCopy({copy})'
        if b.get('sound'):
            props += f".sound(SoundType.{b['sound']})"
        if k == 'cube':
            ctor = f'() -> new Block({props})'
        elif k == 'stairs':
            ctor = f"() -> new StairBlock({b['base'].upper()}.get().defaultBlockState(), {props})"
        elif k == 'slab':
            ctor = f'() -> new SlabBlock({props})'
        elif k == 'wall':
            ctor = f'() -> new WallBlock({props}.forceSolidOn())'
        elif k == 'pillar':
            ctor = f'() -> new RotatedPillarBlock({props})'
        elif k == 'trapdoor':
            ctor = f'() -> new TrapDoorBlock(BlockSetType.OAK, {props})'
        elif k == 'pane':
            ctor = f'() -> new IronBarsBlock({props}.noOcclusion())'
        elif k == 'lantern':
            ctor = f'() -> new LanternBlock({props})'
        elif k == 'decor':
            p = props + '.noOcclusion()'
            if b.get('nocollide'):
                p += '.noCollission()'
            ctor = f"() -> new DecorBlock({p}, DecorBlock.Shape.{b['shape']})"
        else:
            raise ValueError(k)
        lines.append(f'    public static final DeferredBlock<Block> {F} = block("{i}", {ctor});')
    fams = []
    for b in BLOCKS:
        if b['family'] not in fams:
            fams.append(b['family'])
    return f'''package com.grandline.registry;

import com.grandline.block.DecorBlock;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.neoforged.neoforge.registries.DeferredBlock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

/**
 * The architectural building kit ({len(BLOCKS)} blocks in {len(fams)} families). GENERATED by tools/gen_assets.py from
 * tools/assetgen/arch_blocks.py, which also writes every texture, model, block state, loot table, recipe, tag and
 * name; edit the spec there, not this file. Registered into {{@link ModBlocks#BLOCKS}} with block items in
 * {{@link ModItems#ITEMS}}; ids are stable (never rename or remove one: worlds and structures use them).
 */
public final class ArchBlocks {{
    private static final List<DeferredBlock<Block>> ALL_MUTABLE = new ArrayList<>();
    public static final List<DeferredBlock<Block>> ALL = Collections.unmodifiableList(ALL_MUTABLE);

{chr(10).join(lines)}

    private static DeferredBlock<Block> block(String id, Supplier<Block> b) {{
        DeferredBlock<Block> h = ModBlocks.BLOCKS.register(id, b);
        ModItems.ITEMS.registerSimpleBlockItem(h);
        ALL_MUTABLE.add(h);
        return h;
    }}

    /** Forces class loading so every field registers (called from GrandLine before the registries fire). */
    public static void init() {{}}

    private ArchBlocks() {{}}
}}
'''


def generate(png, js, A, D, java_root, tags=True):
    """png(rel, canvas) / js(base, rel, obj) are gen_assets' writers (they record files for --prune)."""
    for name, fn in TEXTURES.items():
        png(f'textures/block/{name}.png', fn())
    for name, model in block_models().items():
        js(A, f'models/block/{name}.json', model)
    for b in BLOCKS:
        js(A, f'blockstates/{b["id"]}.json', blockstate(b))
        js(A, f'models/item/{b["id"]}.json', item_model(b))
        js(D, f'{NS}/loot_table/blocks/{b["id"]}.json', loot(b))
    for name, rec in recipes().items():
        js(D, f'{NS}/recipe/{name}.json', rec)
    if tags:
        for name, t in block_tags().items():
            js(D, f'minecraft/tags/block/{name}.json', t)
        for name, t in item_tags().items():
            js(D, f'minecraft/tags/item/{name}.json', t)
    lang_path = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(java_root))), 'lang', 'en_us', 'arch_blocks.json')
    import json
    with open(lang_path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(lang(), f, indent=2)
        f.write('\n')
    path = os.path.join(java_root, 'registry', 'ArchBlocks.java')
    src = java_registry()
    old = open(path, encoding='utf-8').read() if os.path.exists(path) else None
    if old != src:
        with open(path, 'w', encoding='utf-8', newline='\n') as f:
            f.write(src)
    return len(BLOCKS)
