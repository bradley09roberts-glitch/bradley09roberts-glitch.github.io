#!/usr/bin/env python3
"""Grand Line Odyssey asset pipeline: generates every texture, model, blockstate, loot table, recipe and tag the mod
code references. Pure Python 3 (zlib + struct PNG writer), no Pillow needed; output is deterministic.

    python3 -I tools/gen_assets.py            # from grandline-mod/ (or any cwd)
    python3 -I tools/gen_assets.py --prune    # also delete stale files in the directories this script owns

Then check coverage with:  python3 -I tools/check_assets.py
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from assetgen import source, data                      # noqa: E402
from assetgen import items, misc_items, blocks, fx, fx_extra  # noqa: E402,F401  (misc_items, fx_extra register sprites)
from assetgen import skins, npcs                        # noqa: E402,F401  (npcs registers skins)
from assetgen import naval_items                        # noqa: E402  (registers sprites; recipes merged below)
from assetgen import activity_items                     # noqa: E402  (sea activities: sprites, recipes, tags, loot modifier)

RES = source.RES
A = os.path.join(RES, 'assets/grandline')
D = os.path.join(RES, 'data')
written = set()


def out(rel_base, rel):
    p = os.path.join(rel_base, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    written.add(os.path.normpath(p))
    return p


def png(rel, canvas):
    canvas.save(out(A, rel))


def js(base, rel, obj):
    with open(out(base, rel), 'w', encoding='utf-8', newline='\n') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')


def main(prune=False):
    plain, weapons = source.item_ids()
    block_ids = source.block_ids()
    all_items = plain + weapons + block_ids
    missing = []

    # ------------------------------------------------------------------ item textures + models
    for iid in plain + weapons:
        if iid in data.LONG and iid not in weapons:
            missing.append(f'{iid}: LONG display configured for a non-weapon')
        if iid in items.LAYERED:
            layers = items.LAYERED[iid]()
            png(f'textures/item/{iid}.png', layers[0])
            png(f'textures/item/{iid}_swirl.png', layers[1])
        elif iid in items.ITEMS:
            png(f'textures/item/{iid}.png', items.ITEMS[iid]())
        else:
            missing.append(f'item sprite: {iid}')
            continue
        js(A, f'models/item/{iid}.json', data.item_model(iid, set(weapons), set(block_ids)))

    # ------------------------------------------------------------------ blocks
    bm = data.block_models()
    for b in block_ids:
        if b not in bm:
            missing.append(f'block model: {b}')
            continue
        js(A, f'models/block/{b}.json', bm[b])
        js(A, f'blockstates/{b}.json', data.blockstate(b))
        js(A, f'models/item/{b}.json', data.item_model(b, set(weapons), set(block_ids)))
    for name, fn in blocks.BLOCKS.items():
        png(f'textures/block/{name}.png', fn())

    # ------------------------------------------------------------------ entities
    for sid in source.skin_ids():
        if sid not in skins.SKINS:
            missing.append(f'npc skin: {sid}')
            continue
        png(f'textures/entity/npc/{sid}.png', skins.SKINS[sid]())
    for kind in sorted(set(source.projectile_kinds()) | set(fx.PROJECTILES)):
        if kind not in fx.PROJECTILES:
            missing.append(f'projectile texture: {kind}')
            continue
        png(f'textures/entity/projectile/{kind}.png', fx.PROJECTILES[kind]())
    png('textures/entity/race_parts.png', fx.race_parts())
    for eid in source.mob_effect_ids():
        if eid in fx.EFFECTS:
            png(f'textures/mob_effect/{eid}.png', fx.EFFECTS[eid]())
        else:
            missing.append(f'mob effect icon: {eid}')

    # ------------------------------------------------------------------ data
    chests = data.chest_tables()
    for lid in source.loot_table_ids():
        name = lid.split('/', 1)[1]
        if name not in chests:
            missing.append(f'loot table: grandline:{lid}')
    for name, table in chests.items():
        js(D, f'grandline/loot_table/chests/{name}.json', table)
    for name, table in data.block_loot().items():
        js(D, f'grandline/loot_table/blocks/{name}.json', table)
    for name, r in {**data.recipes(), **naval_items.recipes(), **activity_items.recipes()}.items():
        js(D, f'grandline/recipe/{name}.json', r)
    for rel, obj in activity_items.data_files().items():
        js(D, rel, obj)
    for name, t in data.item_tags(weapons).items():
        js(D, f'minecraft/tags/item/{name}.json', t)
    for name, t in data.block_tags().items():
        js(D, f'minecraft/tags/block/{name}.json', t)

    if prune:
        owned = [os.path.join(A, d) for d in ('textures/item', 'textures/block', 'textures/entity/npc', 'textures/entity/projectile',
                                              'textures/mob_effect', 'models/item', 'models/block', 'blockstates')]
        owned += [os.path.join(D, 'grandline/loot_table'), os.path.join(D, 'grandline/recipe')]
        for d in owned:
            for dirpath, _, files in os.walk(d):
                for f in files:
                    p = os.path.normpath(os.path.join(dirpath, f))
                    if p not in written:
                        os.remove(p)
                        print('pruned', os.path.relpath(p, RES))

    print(f'wrote {len(written)} files ({len(all_items)} items, {len(block_ids)} blocks, {len(source.skin_ids())} skins, '
          f'{len(source.projectile_kinds())} projectile textures)')
    if missing:
        print('MISSING:\n  ' + '\n  '.join(missing))
        sys.exit(1)


if __name__ == '__main__':
    main(prune='--prune' in sys.argv)
