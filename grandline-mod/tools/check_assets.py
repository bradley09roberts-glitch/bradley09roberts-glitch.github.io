#!/usr/bin/env python3
"""Checks that every id the Java code references has its generated resources, and that every item id used by the
loot tables, recipes and tags exists.

    python3 -I tools/check_assets.py [--vanilla <extracted client jar root>]

With --vanilla, minecraft: item ids are validated against <root>/assets/minecraft/models/item/*.json; otherwise
against the built-in list below. Exit code 1 on any problem.
"""
import glob
import json
import os
import struct
import sys
import zlib

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from assetgen import source  # noqa: E402
from assetgen.canvas import load_png  # noqa: E402

RES = source.RES
A = os.path.join(RES, 'assets')
D = os.path.join(RES, 'data')

# minecraft: item ids the generated data may use (used when no --vanilla dump is given)
VANILLA_ITEMS = set('''
apple arrow baked_potato barrel blue_dye bone bread carrot coal cobblestone compass cooked_beef cooked_cod cooked_salmon copper_ingot
diamond emerald flint glass glass_bottle gold_block gold_ingot gold_nugget golden_apple gravel gunpowder honey_bottle iron_chestplate
iron_helmet iron_ingot iron_nugget lantern lead leather oak_log oak_planks paper potato potion spruce_planks stick string sugar torch wheat
rotten_flesh nautilus_shell heart_of_the_sea prismarine_shard prismarine_crystals feather amethyst_shard candle experience_bottle map  painting
bowl kelp red_mushroom brown_mushroom fishing_rod
'''.split())
VANILLA_TAGS = {'minecraft:logs', 'minecraft:planks', 'minecraft:swords', 'minecraft:wooden_slabs', 'minecraft:small_flowers'}

problems = []
warnings = []


def err(msg):
    problems.append(msg)


def png_size(path):
    with open(path, 'rb') as f:
        head = f.read(33)
    if head[:8] != b'\x89PNG\r\n\x1a\n':
        return None
    w, h = struct.unpack('>II', head[16:24])
    return w, h


def tex_path(ref):
    ns, _, p = ref.partition(':') if ':' in ref else ('minecraft', ':', ref)
    return os.path.join(A, ns, 'textures', p + '.png'), ns


def model_path(ref):
    ns, _, p = ref.partition(':') if ':' in ref else ('minecraft', ':', ref)
    return os.path.join(A, ns, 'models', p + '.json'), ns


def check_model(ref, seen=None, ctx=''):
    seen = seen or set()
    if ref in seen:
        return
    seen.add(ref)
    path, ns = model_path(ref)
    if ns != 'grandline':
        return
    if not os.path.isfile(path):
        err(f'{ctx}: missing model {ref}')
        return
    try:
        m = json.load(open(path, encoding='utf-8'))
    except ValueError as e:
        err(f'{ctx}: bad json {path}: {e}')
        return
    for k, t in m.get('textures', {}).items():
        if t.startswith('#'):
            continue
        tp, tns = tex_path(t)
        if tns == 'grandline' and not os.path.isfile(tp):
            err(f'{ctx}: model {ref} texture {k} -> missing {t}')
        elif tns == 'grandline' and png_size(tp) is None:
            err(f'{ctx}: texture {t} is not a PNG')
    if 'parent' in m:
        check_model(m['parent'], seen, ctx)


def main():
    vanilla_items = VANILLA_ITEMS
    if '--vanilla' in sys.argv:
        root = sys.argv[sys.argv.index('--vanilla') + 1]
        vanilla_items = {os.path.splitext(os.path.basename(p))[0] for p in glob.glob(os.path.join(root, 'assets/minecraft/models/item/*.json'))}
        vanilla_items.add('potion')
        print(f'validating against {len(vanilla_items)} vanilla item models from {root}')

    plain, weapons = source.item_ids()
    blocks = source.block_ids() + source.arch_block_ids()
    items = set(plain) | set(weapons) | set(blocks)
    gl_items = {f'grandline:{i}' for i in items}

    # ---------------------------------------------------------------- items & blocks
    for iid in sorted(items):
        check_model(f'grandline:item/{iid}', ctx=f'item {iid}')
    for b in blocks:
        bs = os.path.join(A, 'grandline/blockstates', b + '.json')
        if not os.path.isfile(bs):
            err(f'block {b}: missing blockstate')
            continue
        state = json.load(open(bs))
        for v in state.get('variants', {}).values():
            for var in (v if isinstance(v, list) else [v]):
                check_model(var['model'], ctx=f'block {b}')
        for part in state.get('multipart', []):
            for var in (part['apply'] if isinstance(part['apply'], list) else [part['apply']]):
                check_model(var['model'], ctx=f'block {b}')
        if 'variants' in state and '' in state['variants']:
            check_model(f'grandline:block/{b}', ctx=f'block {b}')

    # ---------------------------------------------------------------- entities
    for sid in source.skin_ids():
        p = os.path.join(A, 'grandline/textures/entity/npc', sid + '.png')
        if not os.path.isfile(p):
            err(f'npc skin {sid}: missing {p}')
            continue
        if png_size(p) != (64, 64):
            err(f'npc skin {sid}: not 64x64')
            continue
        c = load_png(p)
        # every base-layer face must be opaque (player model UVs, classic arms)
        for (x0, y0, x1, y1) in [(8, 0, 24, 8), (0, 8, 32, 16), (20, 16, 36, 20), (16, 20, 40, 32), (44, 16, 52, 20), (40, 20, 56, 32),
                                 (4, 16, 12, 20), (0, 20, 16, 32), (20, 48, 28, 52), (16, 52, 32, 64), (36, 48, 44, 52), (32, 52, 48, 64)]:
            if any(c.get(x, y)[3] != 255 for y in range(y0, y1) for x in range(x0, x1)):
                err(f'npc skin {sid}: transparent pixel in base region {(x0, y0, x1, y1)}')
                break
    for kind in source.projectile_kinds():
        p = os.path.join(A, 'grandline/textures/entity/projectile', kind + '.png')
        if not os.path.isfile(p):
            err(f'projectile {kind}: missing texture')
        elif png_size(p) != (32, 32):
            err(f'projectile {kind}: expected 32x32')
    rp = os.path.join(A, 'grandline/textures/entity/race_parts.png')
    if not os.path.isfile(rp) or png_size(rp) != (64, 64):
        err('race_parts.png missing or not 64x64')
    for eid in source.mob_effect_ids():
        if not os.path.isfile(os.path.join(A, 'grandline/textures/mob_effect', eid + '.png')):
            err(f'mob effect {eid}: missing icon')

    # ---------------------------------------------------------------- data
    def known_item(name, ctx):
        if name.startswith('grandline:'):
            if name not in gl_items:
                err(f'{ctx}: unknown item {name}')
        else:
            n = name.split(':', 1)[1] if ':' in name else name
            if n not in vanilla_items:
                err(f'{ctx}: unknown vanilla item {name}')

    def walk_loot(node, ctx):
        if isinstance(node, dict):
            if node.get('type') == 'minecraft:item' and 'name' in node:
                known_item(node['name'], ctx)
            for v in node.values():
                walk_loot(v, ctx)
        elif isinstance(node, list):
            for v in node:
                walk_loot(v, ctx)

    for lid in source.loot_table_ids():
        if not os.path.isfile(os.path.join(D, 'grandline/loot_table', lid + '.json')):
            err(f'loot table grandline:{lid} referenced by the code is missing')
    for p in glob.glob(os.path.join(D, 'grandline/loot_table/**/*.json'), recursive=True):
        walk_loot(json.load(open(p)), os.path.relpath(p, D))
    for b in blocks:
        if b == 'poneglyph':
            continue  # .noLootTable()
        if not os.path.isfile(os.path.join(D, 'grandline/loot_table/blocks', b + '.json')):
            err(f'block {b}: missing block loot table')

    never_craftable = {'grandline:' + n for n in ('devil_fruit', 'enma', 'ame_no_habakiri', 'murakumogiri', 'yoru', 'shusui',
                                                   'wado_ichimonji', 'sandai_kitetsu', 'yubashiri', 'kurenai_shigure', 'abyssal_fang',
                                                   'tidebreaker')}
    for p in glob.glob(os.path.join(D, 'grandline/recipe/*.json')):
        r = json.load(open(p))
        ctx = os.path.relpath(p, D)
        known_item(r['result']['id'], ctx)
        if r['result']['id'] in never_craftable:
            err(f'{ctx}: {r["result"]["id"]} must not be craftable')
        ings = list(r.get('key', {}).values()) + list(r.get('ingredients', [])) + ([r['ingredient']] if 'ingredient' in r else [])
        for ing in [x for i in ings for x in (i if isinstance(i, list) else [i])]:
            if 'item' in ing:
                known_item(ing['item'], ctx)
            elif 'tag' in ing and ing['tag'].startswith('minecraft:') and ing['tag'] not in VANILLA_TAGS:
                warnings.append(f'{ctx}: unverified tag {ing["tag"]}')
            elif 'tag' in ing and ing['tag'].startswith('grandline:') and not os.path.isfile(
                    os.path.join(D, 'grandline/tags/item', ing['tag'].split(':', 1)[1] + '.json')):
                err(f'{ctx}: missing item tag {ing["tag"]}')
        if 'pattern' in r:
            keys = set(''.join(r['pattern'])) - {' '}
            if keys != set(r['key']):
                err(f'{ctx}: pattern keys {keys} != key map {set(r["key"])}')
    for p in glob.glob(os.path.join(D, 'minecraft/tags/item/**/*.json'), recursive=True) + \
            glob.glob(os.path.join(D, 'grandline/tags/item/**/*.json'), recursive=True):
        for v in json.load(open(p)).get('values', []):
            if not v.startswith('#'):
                known_item(v, os.path.relpath(p, D))
    for p in glob.glob(os.path.join(D, 'minecraft/tags/block/**/*.json'), recursive=True):
        for v in json.load(open(p)).get('values', []):
            if v.startswith('grandline:') and v.split(':')[1] not in blocks:
                err(f'{os.path.relpath(p, D)}: unknown block {v}')

    # ---------------------------------------------------------------- informational: names
    lang = {}
    for p in glob.glob(os.path.join(source.ROOT, 'src/main/lang/en_us/*.json')):
        lang.update(json.load(open(p, encoding='utf-8')))

    # ---------------------------------------------------------------- sounds (ModSounds.IDS vs sounds.json and the .ogg files)
    import re
    ms = open(os.path.join(source.ROOT, 'src/main/java/com/grandline/registry/ModSounds.java'), encoding='utf-8').read()
    ids = re.findall(r'"([a-z_]+\.[a-z_]+)"', ms[ms.index('IDS = {'):ms.index('};', ms.index('IDS = {'))])
    registered = set(re.findall(r'sfx\("([a-z_.]+)"\)', ms))
    if set(ids) != registered:
        err(f'ModSounds.IDS and the registered events differ: {sorted(set(ids) ^ registered)}')
    sj = os.path.join(A, 'grandline/sounds.json')
    sounds = json.load(open(sj)) if os.path.isfile(sj) else {}
    for sid in ids:
        if sid not in sounds:
            err(f'sound {sid}: missing from sounds.json (run tools/gen_sounds.py)')
            continue
        for e in sounds[sid]['sounds']:
            name = e['name'] if isinstance(e, dict) else e
            f = os.path.join(A, 'grandline/sounds', name.split(':')[1] + '.ogg')
            if not os.path.isfile(f):
                err(f'sound {sid}: missing file {os.path.relpath(f, A)}')
        sub = sounds[sid].get('subtitle')
        if sub and sub not in lang:
            err(f'sound {sid}: no lang entry for its subtitle {sub}')
    for iid in sorted(items):
        key = ('block.grandline.' if iid in blocks else 'item.grandline.') + iid
        if key not in lang:
            warnings.append(f'no lang entry {key}')

    for w in warnings:
        print('warning:', w)
    if problems:
        print(f'{len(problems)} problem(s):')
        for p in problems:
            print('  ', p)
        sys.exit(1)
    print(f'OK: {len(items)} items, {len(blocks)} blocks, {len(source.skin_ids())} skins, {len(source.projectile_kinds())} projectiles, '
          f'{len(source.loot_table_ids())} chest loot tables referenced by code, {len(ids)} sound events; all resources present.')


if __name__ == '__main__':
    main()
