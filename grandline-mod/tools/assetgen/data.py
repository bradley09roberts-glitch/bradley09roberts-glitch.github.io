"""JSON resources: item/block models, blockstates, loot tables, recipes and tags (Minecraft 1.21.1 formats)."""
import math

NS = 'grandline'

# ============================================================================================ weapon groups
SWORDS = ['katana', 'cutlass', 'rapier', 'nodachi', 'dual_blades', 'knife', 'fine_katana', 'fine_cutlass', 'enma',
          'ame_no_habakiri', 'yoru', 'shusui', 'wado_ichimonji', 'sandai_kitetsu', 'yubashiri', 'kurenai_shigure', 'abyssal_fang',
          'naginata', 'murakumogiri']
POLEARMS = ['spear', 'steel_spear', 'war_trident']
BLUNT = ['bo_staff', 'kanabo', 'iron_kanabo', 'war_hammer', 'tonfa', 'whip', 'brass_knuckles', 'seastone_jitte', 'anchor_chain',
         'tidebreaker']
RANGED = ['flintlock', 'musket', 'bazooka', 'hand_cannon', 'slingshot', 'war_bow', 'weather_staff', 'impact_dial']
NO_DURABILITY = {'throwing_knife'}

# long weapons are drawn on the 16px diagonal but scaled up in hand, keeping the grip in the fist
LONG = {
    'nodachi': 1.3, 'yoru': 1.45, 'spear': 1.4, 'steel_spear': 1.4, 'naginata': 1.4, 'murakumogiri': 1.45, 'war_trident': 1.4,
    'bo_staff': 1.45, 'kanabo': 1.2, 'iron_kanabo': 1.25, 'war_hammer': 1.25, 'anchor_chain': 1.2, 'tidebreaker': 1.3,
    'weather_staff': 1.3,
}
# grip pixel (texture x, y) for each long weapon (default: the handle near the bottom-left corner)
GRIP = {'anchor_chain': (1.5, 13.5), 'tidebreaker': (1.5, 13.5)}

# guns: (barrel direction in the texture, x right / y up), "down" side of the gun, grip pixel, third-person scale
GUNS = {
    'flintlock': ((1, 0), (0, -1), (2.5, 11.0), 0.85),
    'hand_cannon': ((1, 0), (0, -1), (6.0, 12.0), 0.95),
    'musket': ((1, 1), (1, -1), (5.5, 11.5), 1.15),
    'bazooka': ((1, 1), (1, -1), (9.5, 11.5), 1.15),
}


# ============================================================================================ display math
def _rx(a):
    c, s = math.cos(a), math.sin(a)
    return [[1, 0, 0], [0, c, -s], [0, s, c]]


def _ry(a):
    c, s = math.cos(a), math.sin(a)
    return [[c, 0, s], [0, 1, 0], [-s, 0, c]]


def _rz(a):
    c, s = math.cos(a), math.sin(a)
    return [[c, -s, 0], [s, c, 0], [0, 0, 1]]


def _mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(3)) for j in range(3)] for i in range(3)]


def _apply(m, v):
    return [sum(m[i][k] * v[k] for k in range(3)) for i in range(3)]


def euler_matrix(rot):
    """ItemTransform rotation: Quaternionf.rotationXYZ -> R = Rx * Ry * Rz (degrees)."""
    rx, ry, rz = (math.radians(a) for a in rot)
    return _mul(_rx(rx), _mul(_ry(ry), _rz(rz)))


def matrix_euler(m):
    """Inverse of euler_matrix (handles the gimbal-locked case |sin b| = 1 by putting all roll into z)."""
    b = math.asin(max(-1.0, min(1.0, m[0][2])))
    if abs(m[0][2]) > 0.99999:
        a, c = 0.0, math.atan2(m[1][0], m[1][1])
    else:
        a = math.atan2(-m[1][2], m[2][2])
        c = math.atan2(-m[0][1], m[0][0])
    return [round(math.degrees(a), 2) + 0.0, round(math.degrees(b), 2) + 0.0, round(math.degrees(c), 2) + 0.0]


def _cross(a, b):
    return [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]]


def _norm(v):
    L = math.sqrt(sum(x * x for x in v))
    return [x / L for x in v]


def grip_vec(px, py):
    """Texture pixel -> item-centred model coordinates in 1/16 block units (y up)."""
    return [px + 0.5 - 8.0, 8.0 - py - 0.5, 0.0]


HANDHELD = {
    'thirdperson_righthand': ([0, -90, 55], [0, 4.0, 0.5], 0.85),
    'firstperson_righthand': ([0, -90, 25], [1.13, 3.2, 1.13], 0.68),
}
SWORD_GRIP = grip_vec(2.0, 13.0)


def _left(rot, tr, sc):
    return {'rotation': [rot[0], -rot[1] if rot[1] else 0, -rot[2] if rot[2] else 0], 'translation': tr, 'scale': [sc, sc, sc]}


def _r(v):
    return [round(x, 3) + 0.0 for x in v]


def long_display(scale, grip):
    out = {}
    for view, (rot, tr, s0) in HANDHELD.items():
        s1 = s0 * (scale if view.startswith('third') else 1.0 + (scale - 1.0) * 0.6)
        R = euler_matrix(rot)
        d = _apply(R, [(s0 - s1) * g for g in grip])
        t = _r([tr[i] + d[i] for i in range(3)])
        t = [max(-80.0, min(80.0, x)) for x in t]
        out[view] = {'rotation': rot, 'translation': t, 'scale': [round(s1, 3)] * 3}
        out[view.replace('right', 'left')] = _left(rot, t, round(s1, 3))
    return out


def gun_display(barrel, down, grip_px, scale3):
    """Rotation that points the barrel along the arm (so it aims forward when the arm is raised, like the vanilla
    crossbow/bow) with the grip hanging below; translated so the grip sits where a vanilla handheld item's grip sits."""
    b = _norm([barrel[0], barrel[1], 0.0])
    dn = _norm([down[0], down[1], 0.0])
    n = _cross(b, dn)
    tb, td = [0.0, 0.0, -1.0], [0.0, -1.0, 0.0]
    tn = _cross(tb, td)
    S = [[b[i], dn[i], n[i]] for i in range(3)]                  # columns: source frame
    T = [[tb[i], td[i], tn[i]] for i in range(3)]                # columns: target frame
    St = [[S[j][i] for j in range(3)] for i in range(3)]
    R = _mul(T, St)
    rot = matrix_euler(R)
    R2 = euler_matrix(rot)
    assert all(abs(R[i][j] - R2[i][j]) < 2e-3 for i in range(3) for j in range(3)), 'euler decomposition failed'
    g = grip_vec(*grip_px)
    out = {}
    for view, (hrot, htr, hs) in HANDHELD.items():
        # where the vanilla handheld transform puts a sword's grip
        target = [htr[i] + v for i, v in enumerate(_apply(euler_matrix(hrot), [hs * x for x in SWORD_GRIP]))]
        s = scale3 if view.startswith('third') else 0.68 * scale3 / 0.85
        off = _apply(R, [s * x for x in g])
        t = _r([target[i] - off[i] for i in range(3)])
        t = [max(-80.0, min(80.0, x)) for x in t]
        out[view] = {'rotation': rot, 'translation': t, 'scale': [round(s, 3)] * 3}
        out[view.replace('right', 'left')] = _left(rot, t, round(s, 3))
    return out


BOW_DISPLAY = {
    'thirdperson_righthand': {'rotation': [-80, 260, -40], 'translation': [-1, -2, 2.5], 'scale': [0.9, 0.9, 0.9]},
    'thirdperson_lefthand': {'rotation': [-80, -280, 40], 'translation': [-1, -2, 2.5], 'scale': [0.9, 0.9, 0.9]},
    'firstperson_righthand': {'rotation': [0, -90, 25], 'translation': [1.13, 3.2, 1.13], 'scale': [0.68, 0.68, 0.68]},
    'firstperson_lefthand': {'rotation': [0, 90, -25], 'translation': [1.13, 3.2, 1.13], 'scale': [0.68, 0.68, 0.68]},
}


# ============================================================================================ models
def item_model(item_id, weapons, block_ids):
    if item_id in block_ids:
        if item_id == 'fruit_bloom':
            return {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{NS}:block/fruit_bloom'}}
        return {'parent': f'{NS}:block/{item_id}'}
    if item_id == 'devil_fruit':
        return {'parent': 'minecraft:item/generated',
                'textures': {'layer0': f'{NS}:item/devil_fruit', 'layer1': f'{NS}:item/devil_fruit_swirl'}}
    tex = {'layer0': f'{NS}:item/{item_id}'}
    if item_id not in weapons:
        return {'parent': 'minecraft:item/generated', 'textures': tex}
    m = {'parent': 'minecraft:item/handheld', 'textures': tex}
    if item_id in LONG:
        m['display'] = long_display(LONG[item_id], grip_vec(*GRIP.get(item_id, (2.0, 13.0))))
    elif item_id in GUNS:
        m['display'] = gun_display(*GUNS[item_id])
    elif item_id == 'war_bow':
        m['display'] = BOW_DISPLAY
    return m


def block_models():
    cube = lambda b: {'parent': 'minecraft:block/cube_all', 'textures': {'all': f'{NS}:block/{b}'}}
    return {
        'seastone': cube('seastone'),
        'seastone_bricks': cube('seastone_bricks'),
        'seastone_ore': cube('seastone_ore'),
        'poneglyph': {'parent': 'minecraft:block/cube_column',
                      'textures': {'end': f'{NS}:block/poneglyph_top', 'side': f'{NS}:block/poneglyph'}},
        'fruit_bloom': {'parent': 'minecraft:block/cross', 'textures': {'cross': f'{NS}:block/fruit_bloom'},
                        'render_type': 'minecraft:cutout'},
    }


def blockstate(b):
    return {'variants': {'': {'model': f'{NS}:block/{b}'}}}


# ============================================================================================ loot helpers
def count(lo, hi=None):
    if hi is None or hi == lo:
        return {'function': 'minecraft:set_count', 'count': float(lo), 'add': False}
    return {'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': float(lo), 'max': float(hi)}, 'add': False}


def worn(lo=0.25, hi=0.8):
    return {'function': 'minecraft:set_damage', 'damage': {'type': 'minecraft:uniform', 'min': lo, 'max': hi}}


def comp(key, value):
    return {'function': 'minecraft:set_components', 'components': {f'{NS}:{key}': value}}


def item(name, weight=1, lo=None, hi=None, *fns):
    e = {'type': 'minecraft:item', 'name': name if ':' in name else f'minecraft:{name}', 'weight': weight}
    f = []
    if lo is not None:
        f.append(count(lo, hi))
    f.extend(fns)
    if f:
        e['functions'] = f
    return e


def gl(name, weight=1, lo=None, hi=None, *fns):
    return item(f'{NS}:{name}', weight, lo, hi, *fns)


def empty(weight):
    return {'type': 'minecraft:empty', 'weight': weight}


def pool(entries, lo, hi=None):
    rolls = float(lo) if hi is None else {'type': 'minecraft:uniform', 'min': float(lo), 'max': float(hi)}
    return {'rolls': rolls, 'bonus_rolls': 0.0, 'entries': entries}


def chest(name, pools):
    return {'type': 'minecraft:chest', 'pools': pools, 'random_sequence': f'{NS}:chests/{name}'}


def potion(pid, weight):
    return {'type': 'minecraft:item', 'name': 'minecraft:potion', 'weight': weight,
            'functions': [{'function': 'minecraft:set_potion', 'id': f'minecraft:{pid}'}]}


STYLES_COMMON = ['black_leg', 'ittoryu', 'nitoryu', 'gunslinger', 'sniper', 'storm_dance', 'anchor_brawl', 'tide_grapple']
STYLES_RARE = ['rokushiki', 'fishman_karate', 'santoryu', 'iaido', 'hasshoken', 'ryuo']
POSE_TARGETS = ['alabasta', 'water_7', 'drum_island', 'jaya', 'sabaody_archipelago', 'enies_lobby', 'thriller_bark', 'whisky_peak']


def chest_tables():
    t = {}
    t['house'] = chest('house', [
        pool([item('bread', 15, 1, 4), item('apple', 10, 1, 3), item('potato', 10, 2, 5), item('carrot', 8, 1, 4),
              item('cooked_cod', 6, 1, 3), item('wheat', 8, 2, 6), item('string', 8, 1, 4), item('coal', 8, 1, 4),
              item('paper', 5, 1, 3), item('leather', 4, 1, 2), item('iron_nugget', 5, 2, 6), item('iron_ingot', 2, 1, 2),
              item('torch', 6, 2, 6), gl('meat_on_bone', 3, 1, 1)], 3, 7),
    ])
    t['tavern'] = chest('tavern', [
        pool([item('bread', 14, 2, 5), item('cooked_cod', 12, 1, 4), item('cooked_salmon', 10, 1, 3), item('baked_potato', 8, 1, 4),
              gl('meat_on_bone', 8, 1, 2), item('honey_bottle', 6, 1, 2), item('glass_bottle', 8, 1, 3), item('sugar', 5, 1, 4),
              item('apple', 6, 1, 3)], 3, 6),
        pool([potion('healing', 4), potion('regeneration', 2), potion('strength', 2), potion('swiftness', 2), potion('water_breathing', 2),
              item('emerald', 6, 1, 3), item('gold_nugget', 5, 2, 8), empty(6)], 1, 2),
    ])
    t['shop'] = chest('shop', [
        pool([item('paper', 10, 2, 6), item('string', 10, 2, 5), item('torch', 10, 4, 10), item('leather', 6, 1, 3),
              item('iron_ingot', 5, 1, 3), item('lead', 4, 1, 2), item('lantern', 3, 1, 1), item('emerald', 6, 1, 4),
              gl('bullet', 9, 4, 12), gl('pellet', 8, 8, 20), gl('sea_chart', 2, 1, 1), item('compass', 2, 1, 1)], 3, 6),
        pool([gl('log_pose', 1, 1, 1), empty(14)], 1),
    ])
    t['blacksmith'] = chest('blacksmith', [
        pool([item('iron_ingot', 12, 1, 5), item('iron_nugget', 10, 3, 9), item('coal', 10, 2, 6), item('flint', 6, 1, 3),
              item('gold_ingot', 3, 1, 2), gl('bullet', 6, 4, 10), gl('cannonball', 3, 1, 3), gl('seastone_shard', 2, 1, 2)], 3, 6),
        pool([gl('cutlass', 8, None, None, worn(0.0, 0.5)), gl('knife', 10, None, None, worn(0.0, 0.5)), gl('flintlock', 5),
              gl('katana', 4, None, None, worn(0.0, 0.4)), gl('spear', 5), gl('brass_knuckles', 4), gl('war_hammer', 2),
              gl('fine_cutlass', 1), item('iron_chestplate', 2), item('iron_helmet', 3), empty(6)], 1, 2),
    ])
    t['marine'] = chest('marine', [
        pool([gl('bullet', 14, 6, 16), gl('cannonball', 6, 2, 5), item('bread', 10, 2, 5), item('cooked_cod', 6, 1, 3),
              item('iron_ingot', 6, 1, 3), item('gunpowder', 6, 1, 4), item('paper', 6, 1, 4), gl('wanted_poster', 6, 1, 3),
              gl('seastone_shard', 5, 1, 3)], 3, 6),
        pool([gl('seastone_cuffs', 6), gl('musket', 4, None, None, worn(0.0, 0.4)), gl('flintlock', 5, None, None, worn(0.0, 0.4)),
              gl('cutlass', 6, None, None, worn(0.0, 0.4)), gl('seastone_jitte', 1), gl('sea_chart', 3), empty(6)], 1, 2),
    ])
    t['warehouse'] = chest('warehouse', [
        pool([item('oak_log', 10, 2, 8), item('oak_planks', 12, 4, 16), item('spruce_planks', 8, 4, 12), item('wheat', 10, 4, 12),
              item('potato', 8, 3, 9), item('carrot', 6, 2, 8), item('apple', 6, 2, 5), item('string', 6, 2, 6),
              item('leather', 4, 1, 4), item('coal', 6, 2, 8), item('gunpowder', 4, 1, 4), gl('cannonball', 8, 2, 6),
              gl('meat_on_bone', 4, 1, 3), item('barrel', 2, 1, 1)], 4, 8),
    ])
    t['ruin'] = chest('ruin', [
        pool([item('gold_nugget', 14, 2, 9), item('bone', 10, 1, 4), item('gold_ingot', 4, 1, 2), item('emerald', 4, 1, 2),
              item('arrow', 6, 2, 8), item('string', 5, 1, 3), gl('seastone_shard', 4, 1, 2), item('paper', 4, 1, 3)], 2, 5),
        pool([gl('katana', 4, None, None, worn(0.5, 0.9)), gl('cutlass', 5, None, None, worn(0.5, 0.9)),
              gl('spear', 5, None, None, worn(0.5, 0.9)), gl('kanabo', 2, None, None, worn(0.4, 0.8)),
              gl('naginata', 2, None, None, worn(0.5, 0.9)), gl('rapier', 2, None, None, worn(0.5, 0.9)), empty(10)], 1),
        pool([gl('style_scroll', 1, None, None, comp('style', s)) for s in STYLES_COMMON] + [empty(40)], 1),
        pool([gl('poneglyph_rubbing', 1), empty(60)], 1),
    ])
    t['treasure'] = chest('treasure', [
        pool([item('gold_ingot', 14, 2, 7), item('gold_nugget', 10, 6, 18), item('emerald', 10, 2, 6), item('diamond', 6, 1, 3),
              item('gold_block', 2, 1, 1), item('golden_apple', 3, 1, 1)], 4, 8),
        pool([gl('eternal_pose', 1, None, None, comp('pose_target', t_)) for t_ in POSE_TARGETS] + [empty(10)], 1),
        pool([gl('fine_katana', 6), gl('fine_cutlass', 6), gl('steel_spear', 4), gl('iron_kanabo', 3), gl('yubashiri', 2),
              gl('wado_ichimonji', 1), gl('sandai_kitetsu', 1), gl('kurenai_shigure', 1), gl('abyssal_fang', 2), empty(40)], 1),
        pool([gl('style_scroll', 2, None, None, comp('style', s)) for s in STYLES_COMMON]
             + [gl('style_scroll', 1, None, None, comp('style', s)) for s in STYLES_RARE] + [empty(30)], 1),
    ])
    t['starter'] = chest('starter', [
        pool([item('bread', 1, 6, 8)], 1),
        pool([gl('meat_on_bone', 1, 2, 3)], 1),
        pool([gl('knife', 1)], 1),
        pool([gl('log_pose', 1)], 1),
        pool([item('oak_planks', 3, 8, 16), item('torch', 3, 6, 12), item('apple', 3, 2, 4), item('string', 2, 2, 4),
              item('cooked_cod', 2, 2, 4), gl('sea_chart', 1)], 2, 3),
    ])
    return t


def block_loot():
    def self_drop(b):
        return {'type': 'minecraft:block', 'random_sequence': f'{NS}:blocks/{b}', 'pools': [{
            'rolls': 1.0, 'bonus_rolls': 0.0, 'conditions': [{'condition': 'minecraft:survives_explosion'}],
            'entries': [{'type': 'minecraft:item', 'name': f'{NS}:{b}'}]}]}
    ore = {'type': 'minecraft:block', 'random_sequence': f'{NS}:blocks/seastone_ore', 'pools': [{
        'rolls': 1.0, 'bonus_rolls': 0.0,
        'entries': [{'type': 'minecraft:alternatives', 'children': [
            {'type': 'minecraft:item', 'name': f'{NS}:seastone_ore', 'conditions': [{
                'condition': 'minecraft:match_tool',
                'predicate': {'predicates': {'minecraft:enchantments': [{'enchantments': 'minecraft:silk_touch', 'levels': {'min': 1}}]}}}]},
            {'type': 'minecraft:item', 'name': f'{NS}:seastone_shard', 'functions': [
                count(1, 2),
                {'function': 'minecraft:apply_bonus', 'enchantment': 'minecraft:fortune', 'formula': 'minecraft:ore_drops'},
                {'function': 'minecraft:explosion_decay'}]}]}]}]}
    return {
        'seastone': self_drop('seastone'),
        'seastone_bricks': self_drop('seastone_bricks'),
        'seastone_ore': ore,
        'fruit_bloom': {'type': 'minecraft:block', 'random_sequence': f'{NS}:blocks/fruit_bloom', 'pools': []},
    }


# ============================================================================================ recipes
def shaped(pattern, key, result, n=1, category='equipment', group=None):
    k = {}
    for ch, v in key.items():
        k[ch] = {'tag': v[1:]} if v.startswith('#') else {'item': v if ':' in v else f'minecraft:{v}'}
    r = {'type': 'minecraft:crafting_shaped', 'category': category, 'key': k, 'pattern': pattern,
         'result': {'count': n, 'id': f'{NS}:{result}'}}
    if group:
        r['group'] = group
    return r


def recipes():
    S, I = 'stick', 'iron_ingot'
    return {
        'seastone_bricks': shaped(['##', '##'], {'#': f'{NS}:seastone'}, 'seastone_bricks', 4, 'building'),
        'seastone': shaped(['##', '##'], {'#': f'{NS}:seastone_shard'}, 'seastone', 1, 'building'),
        'seastone_cuffs': shaped(['#I#', '# #'], {'#': f'{NS}:seastone_shard', 'I': I}, 'seastone_cuffs'),
        'bullet': shaped(['NNN', ' G '], {'N': 'iron_nugget', 'G': 'gunpowder'}, 'bullet', 8),
        'cannonball': shaped([' I ', 'IGI', ' I '], {'I': I, 'G': 'gunpowder'}, 'cannonball', 4),
        'pellet_from_gravel': shaped(['##'], {'#': 'gravel'}, 'pellet', 16, group='pellet'),
        'pellet_from_cobblestone': shaped(['##'], {'#': 'cobblestone'}, 'pellet', 16, group='pellet'),
        'katana': shaped(['  I', ' I ', 'S  '], {'I': I, 'S': S}, 'katana'),
        'cutlass': shaped([' I', 'II', 'S '], {'I': I, 'S': S}, 'cutlass'),
        'spear': shaped(['  I', ' S ', 'S  '], {'I': I, 'S': S}, 'spear'),
        'knife': shaped([' I', 'S '], {'I': I, 'S': S}, 'knife'),
        'bo_staff': shaped(['  S', ' S ', 'S  '], {'S': S}, 'bo_staff'),
        'kanabo': shaped(['NLN', ' L ', ' S '], {'N': 'iron_nugget', 'L': '#minecraft:logs', 'S': S}, 'kanabo'),
        'brass_knuckles': shaped([' C ', 'CGC'], {'C': 'copper_ingot', 'G': 'gold_nugget'}, 'brass_knuckles'),
        'flintlock': shaped(['III', 'FP '], {'I': I, 'F': 'flint', 'P': '#minecraft:planks'}, 'flintlock'),
        'slingshot': shaped(['STS', ' S ', ' S '], {'S': S, 'T': 'string'}, 'slingshot'),
        'log_pose': shaped([' G ', 'LCL'], {'G': 'glass', 'L': 'leather', 'C': 'compass'}, 'log_pose', 1, 'misc'),
        'sea_chart': shaped(['PCP', ' B '], {'P': 'paper', 'C': 'compass', 'B': 'blue_dye'}, 'sea_chart', 1, 'misc'),
    }


# ============================================================================================ tags
def item_tags(weapons):
    sword = [f'{NS}:{w}' for w in SWORDS if w in weapons]
    sharp = sword + [f'{NS}:{w}' for w in POLEARMS if w in weapons]
    weapon = sharp + [f'{NS}:{w}' for w in BLUNT if w in weapons]
    durability = [f'{NS}:{w}' for w in weapons if w not in NO_DURABILITY]
    tag = lambda vals: {'replace': False, 'values': vals}
    return {
        'enchantable/sword': tag(sword),
        'enchantable/sharp_weapon': tag(sharp),
        'enchantable/weapon': tag(weapon),
        'enchantable/durability': tag(durability),
    }


def block_tags():
    tag = lambda vals: {'replace': False, 'values': [f'{NS}:{v}' for v in vals]}
    return {
        'mineable/pickaxe': tag(['seastone', 'seastone_bricks', 'seastone_ore']),
        'needs_iron_tool': tag(['seastone', 'seastone_bricks', 'seastone_ore']),
    }
