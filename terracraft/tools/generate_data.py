#!/usr/bin/env python3
"""Generates TerraCraft's datapack JSON (loot tables, tags, recipes, damage types, vanilla overrides) and
the English language file.

Run from the project root:  python3 tools/generate_data.py [path/to/minecraft-client.jar]
The client jar is only needed to copy vanilla recipes that are disabled through a config condition.
"""
import json
import os
import sys
import zipfile

ROOT = os.path.join(os.path.dirname(__file__), '..')
RES = os.path.join(ROOT, 'src/main/resources')
DATA = os.path.join(RES, 'data')
NS = 'terracraft'


def write(path, data):
    full = os.path.join(DATA, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, 'w') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


def t(name):
    return f'{NS}:{name}'


# ============================================================================== blocks
METALS = ['tin', 'lead', 'silver', 'tungsten', 'platinum']
STONE_TIER = ['tin', 'lead']
IRON_TIER = ['silver', 'tungsten', 'platinum']
ORES = [f'{m}_ore' for m in METALS] + [f'deepslate_{m}_ore' for m in METALS]


def ore_loot(ore, raw):
    return {
        'type': 'minecraft:block',
        'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:alternatives', 'children': [
            {'type': 'minecraft:item', 'name': t(ore), 'conditions': [{'condition': 'minecraft:match_tool', 'predicate': {
                'predicates': {'minecraft:enchantments': [{'enchantments': 'minecraft:silk_touch', 'levels': {'min': 1}}]}}}]},
            {'type': 'minecraft:item', 'name': t(raw), 'functions': [{'function': 'minecraft:explosion_decay'}]}]}]}],
        'random_sequence': f'{NS}:blocks/{ore}'}


def self_loot(block):
    return {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': t(block)}],
                                                  'conditions': [{'condition': 'minecraft:survives_explosion'}]}],
            'random_sequence': f'{NS}:blocks/{block}'}


EVIL_BLOCKS = ['ebonwood', 'shadewood', 'ebonwood_leaves', 'shadewood_leaves', 'ebonstone', 'crimstone', 'corrupt_grass', 'crimson_grass', 'demonite_ore', 'crimtane_ore', 'shadow_orb',
               'crimson_heart', 'demon_altar', 'crimson_altar', 'vile_mushroom', 'vicious_mushroom']


def evil_blocks():
    for b in ['ebonstone', 'crimstone', 'demonite_ore', 'crimtane_ore', 'vile_mushroom', 'vicious_mushroom', 'ebonwood', 'shadewood']:
        write(f'{NS}/loot_table/blocks/{b}.json', self_loot(b))
    for g in ['corrupt_grass', 'crimson_grass']:
        write(f'{NS}/loot_table/blocks/{g}.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [
            {'type': 'minecraft:item', 'name': 'minecraft:dirt'}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    write(f'{NS}/terracraft/mining_power/evil_stone.json', {'pickaxe_power': 65, 'blocks': [t('ebonstone'), t('crimstone')]})
    write(f'{NS}/terracraft/mining_power/evil_ore.json', {'pickaxe_power': 55, 'blocks': [t('demonite_ore'), t('crimtane_ore')]})
    corruption = [t('ebonstone'), t('corrupt_grass'), t('demonite_ore'), t('ebonwood'), t('ebonwood_leaves')]
    crimson = [t('crimstone'), t('crimson_grass'), t('crimtane_ore'), t('shadewood'), t('shadewood_leaves')]
    write(f'{NS}/tags/block/evil/corruption.json', {'values': corruption})
    write(f'{NS}/tags/block/evil/crimson.json', {'values': crimson})
    write(f'{NS}/tags/block/evil/all.json', {'values': corruption + crimson})
    write(f'{NS}/tags/block/stations/demon_altar.json', {'values': [t('demon_altar'), t('crimson_altar')]})
    write('minecraft/tags/block/mineable/shovel.json', {'values': [t('corrupt_grass'), t('crimson_grass'), t('jungle_grass'), t('ash'), t('hallowed_grass'), t('pearlsand')]})
    write('minecraft/tags/block/mineable/axe.json', {'values': [t('work_bench'), t('ebonwood'), t('shadewood'), t('tinkerers_workshop'), t('dungeon_bookshelf'), t('pearlwood')]})
    write('minecraft/tags/block/mineable/hoe.json', {'values': [t('ebonwood_leaves'), t('shadewood_leaves')]})
    balls = {'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': t('musket_ball'), 'functions': [{'function': 'minecraft:set_count', 'count': 100}]}]}
    write(f'{NS}/loot_table/gameplay/shadow_orb.json', {'type': 'minecraft:empty', 'pools': [
        {'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': t('musket')}, {'type': 'minecraft:item', 'name': t('vilethorn')},
                                 {'type': 'minecraft:item', 'name': t('band_of_starpower')}]},
        dict(balls, conditions=[{'condition': 'minecraft:random_chance', 'chance': 0.34}])]})
    write(f'{NS}/loot_table/gameplay/crimson_heart.json', {'type': 'minecraft:empty', 'pools': [
        {'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': t('the_undertaker')}, {'type': 'minecraft:item', 'name': t('panic_necklace')},
                                 {'type': 'minecraft:item', 'name': t('band_of_starpower')}]},
        dict(balls, conditions=[{'condition': 'minecraft:random_chance', 'chance': 0.34}])]})
    write(f'{NS}/worldgen/configured_feature/evil_biome.json', {'type': t('evil_biome'), 'config': {}})
    write(f'{NS}/worldgen/placed_feature/evil_biome.json', {'feature': t('evil_biome'), 'placement': []})
    write(f'{NS}/neoforge/biome_modifier/evil_biome.json', {
        'type': 'neoforge:add_features', 'biomes': '#minecraft:is_overworld', 'features': t('evil_biome'), 'step': 'top_layer_modification'})


DUNGEON_BLOCKS = ['blue_brick', 'green_brick', 'pink_brick', 'spikes', 'locked_gold_chest']


def dungeon():
    bricks = [t('blue_brick'), t('green_brick'), t('pink_brick')]
    for b in ['blue_brick', 'green_brick', 'pink_brick', 'spikes']:
        write(f'{NS}/loot_table/blocks/{b}.json', self_loot(b))
    write(f'{NS}/tags/block/dungeon/bricks.json', {'values': bricks})
    write(f'{NS}/terracraft/mining_power/dungeon_brick.json', {'pickaxe_power': 65, 'blocks': bricks})
    write(f'{NS}/tags/item/keys/golden_key.json', {'values': [t('golden_key')]})
    write(f'{NS}/tags/item/keys/shadow_key.json', {'values': [t('shadow_key')]})
    write(f'{NS}/worldgen/configured_feature/dungeon.json', {'type': t('dungeon'), 'config': {}})
    write(f'{NS}/worldgen/placed_feature/dungeon.json', {'feature': t('dungeon'), 'placement': []})
    write(f'{NS}/neoforge/biome_modifier/dungeon.json', {
        'type': 'neoforge:add_features', 'biomes': '#minecraft:is_overworld', 'features': t('dungeon'), 'step': 'top_layer_modification'})
    common = [
        {'rolls': {'type': 'minecraft:uniform', 'min': 1, 'max': 2}, 'entries': [
            entry('healing_potion', 2, 1, 2), entry('lesser_healing_potion', 3, 2, 5), entry('ironskin_potion', 1),
            entry('regeneration_potion', 1), entry('swiftness_potion', 1), entry('magic_power_potion', 1), entry('mana_regeneration_potion', 1)]},
        {'rolls': 1, 'entries': [entry('minecraft:bone', 3, 5, 15), entry('minecraft:torch', 2, 10, 20), entry('minecraft:arrow', 2, 25, 50),
                                 entry('musket_ball', 1, 30, 60), entry('minecraft:cobweb', 1, 4, 8)]},
        {'rolls': 1, 'entries': [entry('silver_coin', 1, 5, 20)]},
        {'rolls': {'type': 'minecraft:uniform', 'min': 0, 'max': 1}, 'entries': [entry('gold_coin', 1)]},
    ]
    # Dungeon bookcases: books, sometimes a Water Bolt; the shelf itself is always kept
    full = [{'condition': 'minecraft:block_state_property', 'block': t('dungeon_bookshelf'), 'properties': {'books': 'true'}}]
    write(f'{NS}/loot_table/blocks/dungeon_bookshelf.json', {'type': 'minecraft:block', 'pools': [
        {'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': t('dungeon_bookshelf')}]},
        {'rolls': 1, 'conditions': full, 'entries': [entry('minecraft:book', 1, 1, 3)]},
        {'rolls': 1, 'conditions': full + [{'condition': 'minecraft:random_chance', 'chance': 0.06}],
         'entries': [{'type': 'minecraft:item', 'name': t('water_bolt')}]}],
        'random_sequence': f'{NS}:blocks/dungeon_bookshelf'})
    # plain dungeon chest: keys (Water Bolts are on the bookcases)
    write(f'{NS}/loot_table/chests/dungeon.json', {'type': 'minecraft:chest', 'pools': [
        {'rolls': 1, 'entries': [entry('golden_key', 5), entry('minecraft:book', 2, 1, 3)]}] + common})
    # Locked Gold Chest: one of Terraria's dungeon treasures
    write(f'{NS}/loot_table/chests/dungeon_gold.json', {'type': 'minecraft:chest', 'pools': [
        {'rolls': 1, 'entries': [entry('muramasa', 2), entry('cobalt_shield', 2), entry('aqua_scepter', 2), entry('handgun', 2),
                                 entry('magic_missile', 2), entry('shadow_key', 1)]}] + common})


JUNGLE_BLOCKS = ['jungle_grass', 'jungle_spores_plant', 'hive', 'larva']


def jungle():
    write(f'{NS}/loot_table/blocks/jungle_grass.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [
        {'type': 'minecraft:item', 'name': 'minecraft:mud'}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    write(f'{NS}/loot_table/blocks/jungle_spores_plant.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [
        entry('jungle_spores', 1, 1, 2)]}]})
    write(f'{NS}/loot_table/blocks/hive.json', self_loot('hive'))
    write(f'{NS}/tags/block/jungle/ground.json', {'values': [t('jungle_grass'), 'minecraft:mud']})
    write(f'{NS}/worldgen/configured_feature/jungle.json', {'type': t('jungle'), 'config': {}})
    write(f'{NS}/worldgen/placed_feature/jungle.json', {'feature': t('jungle'), 'placement': []})
    write(f'{NS}/neoforge/biome_modifier/jungle.json', {
        'type': 'neoforge:add_features', 'biomes': '#minecraft:is_jungle', 'features': t('jungle'), 'step': 'top_layer_modification'})
    # Ivy chests in the underground jungle
    write(f'{NS}/worldgen/configured_feature/jungle_chest.json', {'type': t('loot_chest'), 'config': {
        'chest': {'Name': 'minecraft:chest', 'Properties': {'facing': 'north', 'type': 'single', 'waterlogged': 'false'}},
        'loot_table': t('chests/jungle')}})
    write(f'{NS}/worldgen/placed_feature/jungle_chest.json', {'feature': t('jungle_chest'), 'placement': [
        {'type': 'minecraft:rarity_filter', 'chance': 2}] + floor_scan(-20, 50)})
    write(f'{NS}/neoforge/biome_modifier/jungle_chest.json', {
        'type': 'neoforge:add_features', 'biomes': '#minecraft:is_jungle', 'features': t('jungle_chest'), 'step': 'underground_decoration'})
    write(f'{NS}/loot_table/chests/jungle.json', chest_table(
        ['anklet_of_the_wind', 'feral_claws', 'natures_gift', 'honey_comb'],
        ['silver_bar', 'tungsten_bar', 'minecraft:gold_ingot', 'platinum_bar'],
        ['regeneration_potion', 'swiftness_potion', 'ironskin_potion', 'mana_regeneration_potion']))
    # Queen Bee: one weapon, Honey Comb, Bee Wax
    write(f'{NS}/loot_table/entities/queen_bee.json', {'type': 'minecraft:entity', 'pools': [
        {'rolls': 1, 'entries': [entry('bee_gun'), entry('bee_keeper'), entry('bees_knees')]},
        {'rolls': 1, 'entries': [entry('honey_comb')], 'conditions': [{'condition': 'minecraft:random_chance', 'chance': 0.33}]},
        {'rolls': 1, 'entries': [entry('bee_wax', 1, 16, 26)]},
        {'rolls': 1, 'entries': [entry('minecraft:honey_block', 1, 5, 10)]}]})


UNDERWORLD_BLOCKS = ['ash', 'hellstone', 'obsidian_brick', 'hellstone_brick', 'hellforge', 'locked_shadow_chest']


def underworld():
    for b in ['ash', 'hellstone', 'obsidian_brick', 'hellstone_brick', 'hellforge']:
        write(f'{NS}/loot_table/blocks/{b}.json', self_loot(b))
    write(f'{NS}/terracraft/mining_power/hellstone.json', {'pickaxe_power': 65, 'blocks': [t('hellstone'), t('hellstone_brick')]})
    write(f'{NS}/terracraft/mining_power/obsidian_brick.json', {'pickaxe_power': 55, 'blocks': [t('obsidian_brick')]})
    write(f'{NS}/tags/block/stations/hellforge.json', {'values': [t('hellforge'), t('adamantite_forge'), t('titanium_forge')]})
    write(f'{NS}/worldgen/configured_feature/underworld.json', {'type': t('underworld'), 'config': {}})
    write(f'{NS}/worldgen/placed_feature/underworld.json', {'feature': t('underworld'), 'placement': []})
    write(f'{NS}/neoforge/biome_modifier/underworld.json', {
        'type': 'neoforge:add_features', 'biomes': '#minecraft:is_overworld', 'features': t('underworld'), 'step': 'top_layer_modification'})
    write(f'{NS}/loot_table/chests/shadow.json', {'type': 'minecraft:chest', 'pools': [
        {'rolls': 1, 'entries': [entry('flamelash', 2), entry('flower_of_fire', 2), entry('hellwing_bow', 2)]},
        {'rolls': {'type': 'minecraft:uniform', 'min': 1, 'max': 2}, 'entries': [entry('hellstone_bar', 1, 3, 8), entry('minecraft:obsidian', 1, 5, 10)]},
        {'rolls': {'type': 'minecraft:uniform', 'min': 1, 'max': 2}, 'entries': [entry('healing_potion', 1, 2, 4), entry('obsidian_skin_potion', 1),
                                                                              entry('regeneration_potion', 1), entry('wrath_potion', 1)]},
        {'rolls': 1, 'entries': [entry('gold_coin', 1, 1, 2)]}]})
    write(f'{NS}/loot_table/entities/wall_of_flesh.json', {'type': 'minecraft:entity', 'pools': [
        {'rolls': 1, 'entries': [entry('pwnhammer')]},
        {'rolls': 1, 'entries': [entry('warrior_emblem'), entry('ranger_emblem'), entry('sorcerer_emblem'), entry('summoner_emblem')]},
        {'rolls': 1, 'entries': [entry('breaker_blade'), entry('laser_rifle')]}]})


GOBLIN_BLOCKS = ['tinkerers_workshop', 'meteorite']
MODIFIER_NAMES = {
    'keen': 'Keen', 'superior': 'Superior', 'forceful': 'Forceful', 'broken': 'Broken', 'damaged': 'Damaged', 'shoddy': 'Shoddy',
    'hurtful': 'Hurtful', 'strong': 'Strong', 'unpleasant': 'Unpleasant', 'weak': 'Weak', 'ruthless': 'Ruthless', 'godly': 'Godly',
    'demonic': 'Demonic', 'zealous': 'Zealous', 'large': 'Large', 'massive': 'Massive', 'dangerous': 'Dangerous', 'savage': 'Savage',
    'sharp': 'Sharp', 'pointy': 'Pointy', 'tiny': 'Tiny', 'terrible': 'Terrible', 'small': 'Small', 'dull': 'Dull', 'unhappy': 'Unhappy',
    'bulky': 'Bulky', 'shameful': 'Shameful', 'heavy': 'Heavy', 'light': 'Light', 'legendary': 'Legendary', 'sighted': 'Sighted',
    'rapid': 'Rapid', 'hasty': 'Hasty', 'intimidating': 'Intimidating', 'deadly': 'Deadly', 'staunch': 'Staunch', 'awful': 'Awful',
    'lethargic': 'Lethargic', 'awkward': 'Awkward', 'powerful': 'Powerful', 'frenzying': 'Frenzying', 'unreal': 'Unreal',
    'mystic': 'Mystic', 'adept': 'Adept', 'masterful': 'Masterful', 'inept': 'Inept', 'ignorant': 'Ignorant', 'deranged': 'Deranged',
    'intense': 'Intense', 'taboo': 'Taboo', 'celestial': 'Celestial', 'furious': 'Furious', 'manic': 'Manic', 'mythical': 'Mythical',
    'hard': 'Hard', 'guarding': 'Guarding', 'armored': 'Armored', 'warding': 'Warding', 'arcane': 'Arcane', 'precise': 'Precise',
    'lucky': 'Lucky', 'jagged': 'Jagged', 'spiked': 'Spiked', 'angry': 'Angry', 'menacing': 'Menacing', 'brisk': 'Brisk',
    'fleeting': 'Fleeting', 'hasty_accessory': 'Hasty', 'quick': 'Quick', 'wild': 'Wild', 'rash': 'Rash', 'intrepid': 'Intrepid',
    'violent': 'Violent',
}


def goblins():
    for b in GOBLIN_BLOCKS:
        write(f'{NS}/loot_table/blocks/{b}.json', self_loot(b))
    write(f'{NS}/tags/block/stations/tinkerers_workshop.json', {'values': [t('tinkerers_workshop')]})
    write(f'{NS}/terracraft/mining_power/meteorite.json', {'pickaxe_power': 50, 'blocks': [t('meteorite')]})
    write(f'{NS}/tags/block/meteorite.json', {'values': [t('meteorite')]})


HM_METALS = ['cobalt', 'palladium', 'mythril', 'orichalcum', 'adamantite', 'titanium']
HM_ORES = [f'{m}_ore' for m in HM_METALS]
HM_BLOCKS = ['pearlstone', 'hallowed_grass', 'pearlsand', 'pearlwood', 'hallowed_leaves', 'mythril_anvil', 'orichalcum_anvil',
             'adamantite_forge', 'titanium_forge'] + HM_ORES


def hardmode():
    for m in HM_METALS:
        write(f'{NS}/loot_table/blocks/{m}_ore.json', ore_loot(f'{m}_ore', f'raw_{m}'))
        write(f'c/tags/item/ingots/{m}.json', {'values': [t(f'{m}_bar')]})
        write(f'c/tags/item/raw_materials/{m}.json', {'values': [t(f'raw_{m}')]})
    for b in ['pearlstone', 'pearlsand', 'pearlwood', 'mythril_anvil', 'orichalcum_anvil', 'adamantite_forge', 'titanium_forge']:
        write(f'{NS}/loot_table/blocks/{b}.json', self_loot(b))
    write(f'{NS}/loot_table/blocks/hallowed_grass.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [
        {'type': 'minecraft:item', 'name': 'minecraft:dirt'}], 'conditions': [{'condition': 'minecraft:survives_explosion'}]}]})
    write(f'{NS}/loot_table/blocks/hallowed_leaves.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [
        {'type': 'minecraft:item', 'name': t('hallowed_leaves'), 'conditions': [{'condition': 'minecraft:match_tool', 'predicate': {'items': 'minecraft:shears'}}]}]}]})
    write(f'{NS}/tags/block/hallow/all.json', {'values': [t('pearlstone'), t('hallowed_grass'), t('pearlsand'), t('pearlwood'), t('hallowed_leaves')]})
    write(f'{NS}/tags/block/stations/hardmode_anvil.json', {'values': [t('mythril_anvil'), t('orichalcum_anvil')]})
    write(f'{NS}/tags/block/stations/hardmode_forge.json', {'values': [t('adamantite_forge'), t('titanium_forge')]})
    for tier, power in ((('cobalt', 'palladium'), 100), (('mythril', 'orichalcum'), 110), (('adamantite', 'titanium'), 150)):
        write(f'{NS}/terracraft/mining_power/{tier[0]}_ore.json', {'pickaxe_power': power, 'blocks': [t(f'{m}_ore') for m in tier]})
    write(f'{NS}/terracraft/mining_power/pearlstone.json', {'pickaxe_power': 65, 'blocks': [t('pearlstone')]})


QS_BLOCKS = ['crystal_shard']


def queen_slime():
    write(f'{NS}/loot_table/blocks/crystal_shard.json', self_loot('crystal_shard'))
    write(f'{NS}/loot_table/blocks/gelatin_crystal_block.json', {
        'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': t('gelatin_crystal')}]}],
        'random_sequence': f'{NS}:blocks/gelatin_crystal_block'})


# ============================================================================== Stage 6: jungle Hardmode, temple, Golem
S6_BLOCKS = ['chlorophyte_ore', 'planteras_bulb', 'life_fruit_plant', 'lihzahrd_brick', 'dart_trap', 'wooden_spikes',
             'locked_lihzahrd_door', 'lihzahrd_altar']
S6_PICKAXE = ['chlorophyte_ore', 'lihzahrd_brick', 'dart_trap', 'wooden_spikes']


def stage6():
    write(f'{NS}/loot_table/blocks/chlorophyte_ore.json', ore_loot('chlorophyte_ore', 'raw_chlorophyte'))
    write(f'{NS}/terracraft/mining_power/chlorophyte_ore.json', {'pickaxe_power': 200, 'blocks': [t('chlorophyte_ore')]})
    write(f'c/tags/item/ingots/chlorophyte.json', {'values': [t('chlorophyte_bar')]})
    # Plantera
    write(f'{NS}/loot_table/blocks/life_fruit_plant.json', {'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [
        {'type': 'minecraft:item', 'name': t('life_fruit')}]}], 'random_sequence': f'{NS}:blocks/life_fruit_plant'})
    write(f'{NS}/loot_table/blocks/planteras_bulb.json', {'type': 'minecraft:block', 'pools': []})
    write(f'{NS}/tags/item/keys/temple.json', {'values': [t('temple_key')]})
    MOBS.update({
        'plantera': ('Plantera', []),
        'plantera_hook': ("Plantera's Hook", []),
        'plantera_tentacle': ("Plantera's Tentacle", []),
        'angry_trapper': ('Angry Trapper', [('minecraft:vine', 1, 2, 0.5), ('jungle_spores', 1, 3, 0.5)]),
        'derpling': ('Derpling', [('jungle_spores', 1, 2, 0.3)]),
    })
    # Lihzahrd Temple
    for b in ('lihzahrd_brick', 'dart_trap', 'wooden_spikes'):
        write(f'{NS}/loot_table/blocks/{b}.json', self_loot(b))
    write(f'{NS}/terracraft/mining_power/lihzahrd_brick.json', {'pickaxe_power': 210,
                                                               'blocks': [t('lihzahrd_brick'), t('dart_trap'), t('wooden_spikes')]})
    write(f'{NS}/tags/block/temple/bricks.json', {'values': [t('lihzahrd_brick'), t('dart_trap')]})
    write(f'{NS}/loot_table/chests/temple.json', {'type': 'minecraft:chest', 'pools': [
        {'rolls': 1, 'entries': [entry('lihzahrd_power_cell', 1)], 'conditions': [{'condition': 'minecraft:random_chance', 'chance': 0.6}]},
        {'rolls': 1, 'entries': [entry('greater_healing_potion', 3, 2, 5), entry('gold_coin', 2, 2, 6), entry('lihzahrd_brick', 1, 10, 30)]},
        {'rolls': 1, 'entries': [entry('beetle_husk', 1), entry('chlorophyte_bar', 2, 2, 6), entry('soul_of_light', 1, 2, 5),
                                 entry('soul_of_night', 1, 2, 5)]},
    ]})
    MOBS.update({
        'lihzahrd': ('Lihzahrd', [('lihzahrd_power_cell', 1, 1, 0.02)]),
        'flying_snake': ('Flying Snake', [('lihzahrd_power_cell', 1, 1, 0.02)]),
        'golem': ('Golem', []),
        'golem_head': ('Golem Head', []),
        'golem_fist': ('Golem Fist', []),
    })
    SPAWNS['temple'] = [
        dict(entity='lihzahrd', weight=10, layers=['underground', 'cavern'], ground=['#terracraft:temple/bricks']),
        dict(entity='flying_snake', weight=6, layers=['underground', 'cavern'], placement='air', ground=['#terracraft:temple/bricks']),
    ]
    SPAWNS['jungle_hardmode'] = [
        dict(entity='derpling', weight=8, time='day', layers=['surface'], biomes=['#minecraft:is_jungle'], condition='hardmode_active'),
        dict(entity='angry_trapper', weight=6, layers=['underground', 'cavern'], ground=['#terracraft:jungle/ground'], condition='hardmode_active'),
        dict(entity='derpling', weight=4, layers=['underground', 'cavern'], ground=['#terracraft:jungle/ground'], condition='hardmode_active'),
    ]


def stage7():
    MOBS.update({
        'truffle_worm': ('Truffle Worm', []), 'duke_fishron': ('Duke Fishron', []), 'sharkron': ('Sharkron', []),
        'blue_armored_bones': ('Blue Armored Bones', [('ectoplasm', 1, 1, 0.1), ('paladins_shield', 1, 1, 0.003)]),
        'hell_armored_bones': ('Hell Armored Bones', [('ectoplasm', 1, 1, 0.1)]),
        'paladin': ('Paladin', [('paladins_hammer', 1, 1, 0.07), ('paladins_shield', 1, 1, 0.06), ('ectoplasm', 2, 4, 1.0)]),
        'skeleton_sniper': ('Skeleton Sniper', [('sniper_rifle', 1, 1, 0.08), ('ectoplasm', 1, 1, 0.1)]),
        'tactical_skeleton': ('Tactical Skeleton', [('ectoplasm', 1, 1, 0.1)]),
        'skeleton_commando': ('Skeleton Commando', [('ectoplasm', 1, 1, 0.1)]),
        'ragged_caster': ('Ragged Caster', [('ectoplasm', 1, 1, 0.1)]),
        'necromancer': ('Necromancer', [('shadowbeam_staff', 1, 1, 0.08), ('ectoplasm', 1, 1, 0.1)]),
        'dungeon_spirit': ('Dungeon Spirit', [('ectoplasm', 1, 3, 1.0)]),
        'scarecrow': ('Scarecrow', [('spooky_wood', 1, 5, 1.0)]), 'splinterling': ('Splinterling', [('spooky_wood', 2, 6, 1.0)]),
        'hellhound': ('Hellhound', [('spooky_wood', 1, 5, 1.0)]), 'poltergeist': ('Poltergeist', [('spooky_wood', 1, 5, 1.0)]),
        'headless_horseman': ('Headless Horseman', [('spooky_wood', 5, 10, 1.0)]),
        'mourning_wood': ('Mourning Wood', [('spooky_wood', 10, 30, 1.0), ('stake_launcher', 1, 1, 0.3)]),
        'pumpking': ('Pumpking', [('the_horsemans_blade', 1, 1, 0.25), ('bat_scepter', 1, 1, 0.25), ('candy_corn_rifle', 1, 1, 0.25)]),
        'zombie_elf': ('Zombie Elf', []), 'gingerbread_man': ('Gingerbread Man', []), 'elf_archer': ('Elf Archer', []),
        'nutcracker': ('Nutcracker', []), 'yeti': ('Yeti', []), 'flocko': ('Flocko', []),
        'everscream': ('Everscream', [('christmas_tree_sword', 1, 1, 0.25), ('razorpine', 1, 1, 0.25)]),
        'santa_nk1': ('Santa-NK1', [('chain_gun', 1, 1, 0.25), ('elf_melter', 1, 1, 0.25)]),
        'ice_queen': ('Ice Queen', [('north_pole', 1, 1, 0.25), ('blizzard_staff', 1, 1, 0.25)]),
        'prismatic_lacewing': ('Prismatic Lacewing', []), 'empress_of_light': ('Empress of Light', []),
        'martian_probe': ('Martian Probe', []), 'gray_grunt': ('Gray Grunt', []), 'ray_gunner': ('Ray Gunner', []),
        'brain_scrambler': ('Brain Scrambler', []), 'gigazapper': ('Gigazapper', []), 'martian_officer': ('Martian Officer', []),
        'martian_drone': ('Martian Drone', []), 'scutlix': ('Scutlix', []), 'martian_saucer': ('Martian Saucer', []),
    })
    PLANTERA = 'boss_plantera_defeated'
    DUNGEON_HM = {'ground': ['#terracraft:dungeon/bricks'], 'condition': PLANTERA}
    SPAWNS['dungeon_hardmode'] = [
        dict(entity=e, weight=w, layers=['surface', 'underground', 'cavern'], **DUNGEON_HM) for e, w in (
            ('blue_armored_bones', 8), ('hell_armored_bones', 6), ('skeleton_sniper', 4), ('tactical_skeleton', 4), ('skeleton_commando', 4),
            ('ragged_caster', 4), ('necromancer', 4), ('paladin', 2))]
    SPAWNS['stage7_critters'] = [
        dict(entity='truffle_worm', weight=4, layers=['surface', 'underground'], biomes=['minecraft:mushroom_fields'], condition='hardmode_active'),
        dict(entity='prismatic_lacewing', weight=3, time='night', layers=['surface'], placement='air',
             ground=['#terracraft:hallow/all'], condition=PLANTERA),
    ]
    MARTIAN = dict(layers=['surface'], event='martian_madness')
    SPAWNS['martian_madness'] = [
        dict(entity='gray_grunt', weight=10, **MARTIAN), dict(entity='ray_gunner', weight=8, **MARTIAN),
        dict(entity='brain_scrambler', weight=6, **MARTIAN), dict(entity='gigazapper', weight=6, **MARTIAN),
        dict(entity='martian_officer', weight=4, **MARTIAN), dict(entity='martian_drone', weight=5, placement='air', **MARTIAN),
        dict(entity='scutlix', weight=4, **MARTIAN)]


def stage7_recipes():
    HM_ANVIL, HM_FORGE = t('hardmode_anvil'), t('hardmode_forge')
    SB = t('spectre_bar')
    recipe('spectre_bar', SB, [(t('chlorophyte_bar'), 1), (t('ectoplasm'), 1)], [HM_FORGE], category='materials')
    recipe('spectre_hood', t('spectre_hood'), [(SB, 12)], [HM_ANVIL], category='armor')
    recipe('spectre_robe', t('spectre_robe'), [(SB, 24)], [HM_ANVIL], category='armor')
    recipe('spectre_pants', t('spectre_pants'), [(SB, 18)], [HM_ANVIL], category='armor')
    recipe('pumpkin_moon_medallion', t('pumpkin_moon_medallion'), [('minecraft:pumpkin', 30), (t('ectoplasm'), 5), (t('hallowed_bar'), 10)],
           [HM_ANVIL], category='consumables')
    recipe('naughty_present', t('naughty_present'), [('minecraft:white_wool', 20), (t('ectoplasm'), 5), (t('soul_of_fright'), 5)],
           [HM_ANVIL], category='consumables')


def stage7_lang(L):
    L.update({
        'event.terracraft.wave': '%s: Wave %s',
        'event.terracraft.pumpkin_moon': 'Pumpkin Moon', 'event.terracraft.pumpkin_moon.start': 'The Pumpkin Moon is rising...',
        'event.terracraft.pumpkin_moon.end': 'The Pumpkin Moon has set.',
        'event.terracraft.frost_moon': 'Frost Moon', 'event.terracraft.frost_moon.start': 'The Frost Moon is rising...',
        'event.terracraft.frost_moon.end': 'The Frost Moon has set.',
        'event.terracraft.martian_madness': 'Martian Madness', 'event.terracraft.martian_madness.start': 'Martians are invading!',
        'event.terracraft.martian_madness.end': 'The martians have been defeated!',
        'event.terracraft.martian_madness.probe': 'The probe has finished its scan and is getting away...',
        'event.terracraft.martian_madness.saucer': 'A Martian Saucer is approaching!',
        'item.terracraft.truffle_worm.tooltip': "Bait for something big. Cast a line into the ocean with one in your pack\n'Caught in the Hardmode mushroom biome'",
        'item.terracraft.tsunami.tooltip': 'Shoots five arrows at once',
        'item.terracraft.razorblade_typhoon.tooltip': 'Casts fast moving razorwheels',
        'item.terracraft.bubble_gun.tooltip': 'Rapidly shoots forceful bubbles',
        'item.terracraft.ectoplasm.tooltip': "'The building blocks of the afterlife'",
        'armor_set.terracraft.spectre.bonus': 'Magic damage done to enemies heals you',
        'ability.terracraft.spectre_heal': 'Magic damage heals you',
        'item.terracraft.paladins_shield.tooltip': 'Grants immunity to knockback and reduces damage taken',
        'item.terracraft.paladins_hammer.tooltip': 'A powerful returning hammer',
        'item.terracraft.sniper_rifle.tooltip': "Shoots a powerful, high velocity bullet",
        'item.terracraft.shadowbeam_staff.tooltip': 'Creates a shadow beam that bounces off walls',
        'item.terracraft.pumpkin_moon_medallion.tooltip': 'Summons the Pumpkin Moon (at night)',
        'item.terracraft.naughty_present.tooltip': 'Summons the Frost Moon (at night)',
        'item.terracraft.the_horsemans_blade.tooltip': 'Swings throw flaming pumpkin heads',
        'item.terracraft.bat_scepter.tooltip': 'Summons bats to attack your enemies',
        'item.terracraft.christmas_tree_sword.tooltip': 'Shoots Christmas ornaments',
        'item.terracraft.razorpine.tooltip': 'Shoots razor sharp pine needles',
        'item.terracraft.chain_gun.tooltip': "'Half shark, half gun, completely awesome.'",
        'item.terracraft.elf_melter.tooltip': 'Sprays fire',
        'item.terracraft.north_pole.tooltip': 'Shoots an icy spear that rains snowflakes',
        'item.terracraft.blizzard_staff.tooltip': 'Showers an area with icicles',
        'item.terracraft.nightglow.tooltip': 'Releases homing lights',
        'item.terracraft.starlight.tooltip': "'Rapid thrusts of light'",
        'item.terracraft.influx_waver.tooltip': 'Releases homing waves',
        'item.terracraft.laser_machinegun.tooltip': 'Fires a stream of lasers',
        'item.terracraft.xenopopper.tooltip': 'Fires a spread of bullets',
        'entity.terracraft.santa_nk1': 'Santa-NK1',
        'item.terracraft.paladins_hammer': "Paladin's Hammer", 'item.terracraft.paladins_shield': "Paladin's Shield",
        'item.terracraft.the_horsemans_blade': "The Horseman's Blade",
    })


# ============================================================================== Stage 8: Cultist, pillars, Moon Lord
S6_BLOCKS.append('ancient_manipulator')
S6_PICKAXE.append('ancient_manipulator')
FRAGMENTS = ['solar', 'vortex', 'nebula', 'stardust']


def stage8():
    write(f'{NS}/loot_table/blocks/ancient_manipulator.json', self_loot('ancient_manipulator'))
    write(f'{NS}/tags/block/stations/ancient_manipulator.json', {'values': [t('ancient_manipulator')]})
    MOBS.update({
        'lunatic_cultist': ('Lunatic Cultist', []), 'cultist_devotee': ('Cultist Devotee', []), 'cultist_clone': ('Ancient Cultist', []),
        'solar_pillar': ('Solar Pillar', []), 'vortex_pillar': ('Vortex Pillar', []), 'nebula_pillar': ('Nebula Pillar', []),
        'stardust_pillar': ('Stardust Pillar', []),
        'selenian': ('Selenian', []), 'sroller': ('Sroller', []), 'corite': ('Corite', []),
        'storm_diver': ('Storm Diver', []), 'alien_hornet': ('Alien Hornet', []), 'vortexian': ('Vortexian', []),
        'nebula_floater': ('Nebula Floater', []), 'brain_suckler': ('Brain Suckler', []), 'predictor': ('Predictor', []),
        'star_cell': ('Star Cell', []), 'flow_invader': ('Flow Invader', []), 'twinkle_popper': ('Twinkle Popper', []),
        'moon_lord': ('Moon Lord', []), 'moon_lord_hand': ("Moon Lord's Hand", []), 'moon_lord_head': ("Moon Lord's Head", []),
    })


def stage8_recipes():
    AM = t('ancient_manipulator')
    LB = t('luminite_bar')
    recipe('luminite_bar', LB, [(t('luminite'), 4)], [AM], category='materials')
    recipe('celestial_sigil', t('celestial_sigil'), [(t(f'{f}_fragment'), 12) for f in FRAGMENTS], [AM], category='consumables')
    for armor_set, frag in (('solar_flare', 'solar'), ('vortex', 'vortex'), ('nebula', 'nebula'), ('stardust', 'stardust')):
        F = t(f'{frag}_fragment')
        recipe(f'{armor_set}_helmet', t(f'{armor_set}_helmet'), [(F, 10), (LB, 8)], [AM], category='armor')
        recipe(f'{armor_set}_breastplate', t(f'{armor_set}_breastplate'), [(F, 20), (LB, 16)], [AM], category='armor')
        recipe(f'{armor_set}_leggings', t(f'{armor_set}_leggings'), [(F, 15), (LB, 12)], [AM], category='armor')
        recipe(f'{armor_set}_pickaxe', t(f'{armor_set}_pickaxe'), [(F, 12), (LB, 10)], [AM], category='tools')
        recipe(f'{frag}_wings', t(f'{frag}_wings'), [(F, 14), (LB, 10)], [AM], category='accessories')
    for weapon, frag in (('solar_eruption', 'solar'), ('daybreak', 'solar'), ('vortex_beater', 'vortex'), ('phantasm', 'vortex'),
                         ('nebula_blaze', 'nebula'), ('nebula_arcanum', 'nebula'), ('stardust_dragon_staff', 'stardust'),
                         ('stardust_cell_staff', 'stardust')):
        recipe(weapon, t(weapon), [(t(f'{frag}_fragment'), 18)], [AM], category='weapons')


def stage8_lang(L):
    L.update({
        'event.terracraft.pillars.start': 'The celestial pillars have descended! Find them far out from spawn.',
        'event.terracraft.pillar.bar': '%s %s',
        'event.terracraft.pillar.shield': '(Shield: %s)',
        'event.terracraft.pillar.shield_down': "The %s's shield is down!",
        'event.terracraft.moon_lord.doom': 'Impending doom approaches...',
        'progression.terracraft.pillar_solar_defeated': 'Solar Pillar destroyed',
        'progression.terracraft.pillar_vortex_defeated': 'Vortex Pillar destroyed',
        'progression.terracraft.pillar_nebula_defeated': 'Nebula Pillar destroyed',
        'progression.terracraft.pillar_stardust_defeated': 'Stardust Pillar destroyed',
        'progression.terracraft.celestial_pillars_defeated': 'All celestial pillars destroyed',
        'block.terracraft.ancient_manipulator': 'Ancient Manipulator', 'station.terracraft.ancient_manipulator': 'Ancient Manipulator',
        'item.terracraft.sdmg': 'S.D.M.G.',
        'item.terracraft.solar_fragment.tooltip': "'The power of the sun's fury'",
        'item.terracraft.vortex_fragment.tooltip': "'Swirling energies of the galaxy'",
        'item.terracraft.nebula_fragment.tooltip': "'Waves of mystical energy'",
        'item.terracraft.stardust_fragment.tooltip': "'Glittering particles of the cosmos'",
        'item.terracraft.luminite.tooltip': "'Glows with the light of the moon'",
        'item.terracraft.luminite_bar.tooltip': "'It is very light'",
        'item.terracraft.celestial_sigil.tooltip': 'Summons the Moon Lord',
        'armor_set.terracraft.solar_flare.bonus': 'Damage taken reduced by 30% and faster melee swings',
        'armor_set.terracraft.vortex.bonus': '20% increased ranged damage and critical strike chance',
        'armor_set.terracraft.nebula.bonus': 'Greatly increased mana and mana regeneration',
        'armor_set.terracraft.stardust.bonus': '15% increased damage and movement speed',
        'item.terracraft.solar_eruption.tooltip': "'Strike with the fury of the sun'",
        'item.terracraft.daybreak.tooltip': "'Rend your foes asunder with a spear of light!'",
        'item.terracraft.vortex_beater.tooltip': "Fires a rapid stream of bullets\n'The catastrophic mixture of pew pew and boom boom'",
        'item.terracraft.phantasm.tooltip': 'Fires a volley of arrows',
        'item.terracraft.nebula_blaze.tooltip': "'From Orion's belt to the palm of your hand'",
        'item.terracraft.nebula_arcanum.tooltip': "'Conjure masses of astral energy to chase down your foes'",
        'item.terracraft.stardust_dragon_staff.tooltip': 'Summons a stardust dragon that seeks out enemies',
        'item.terracraft.stardust_cell_staff.tooltip': 'Summons stardust cells to fight for you',
        'item.terracraft.meowmere.tooltip': 'Shoots bouncing rainbow cats',
        'item.terracraft.star_wrath.tooltip': 'Rains stars from the sky',
        'item.terracraft.sdmg.tooltip': "'It came from the edge of space'",
        'item.terracraft.last_prism.tooltip': "Fires a rainbow beam of light\n'A modern day light show'",
        'item.terracraft.lunar_flare.tooltip': 'Calls down bolts of lunar light',
        'item.terracraft.solar_flare_pickaxe.tooltip': 'Can mine anything',
    })


def stage6_recipes():
    HM_ANVIL, HM_FORGE = t('hardmode_anvil'), t('hardmode_forge')
    CB = t('chlorophyte_bar')
    recipe('chlorophyte_bar', CB, [(t('raw_chlorophyte'), 5)], [HM_FORGE], category='materials')
    recipe('chlorophyte_claymore', t('chlorophyte_claymore'), [(CB, 12)], [HM_ANVIL], category='weapons')
    recipe('chlorophyte_shotbow', t('chlorophyte_shotbow'), [(CB, 18)], [HM_ANVIL], category='weapons')
    recipe('chlorophyte_pickaxe', t('chlorophyte_pickaxe'), [(CB, 18)], [HM_ANVIL], category='tools')
    recipe('chlorophyte_helmet', t('chlorophyte_helmet'), [(CB, 12)], [HM_ANVIL], category='armor')
    recipe('chlorophyte_plate_mail', t('chlorophyte_plate_mail'), [(CB, 24)], [HM_ANVIL], category='armor')
    recipe('chlorophyte_greaves', t('chlorophyte_greaves'), [(CB, 18)], [HM_ANVIL], category='armor')


def stage6_lang(L):
    L.update({
        'ability.terracraft.leaf_crystal': 'Summons a leaf crystal that shoots at nearby enemies',
        'armor_set.terracraft.chlorophyte.bonus': 'Summons a powerful leaf crystal to shoot at nearby enemies',
        'item.terracraft.chlorophyte_claymore.tooltip': 'Shoots a powerful orb',
        'item.terracraft.chlorophyte_shotbow.tooltip': 'Fires a spread of arrows',
        'item.terracraft.raw_chlorophyte.tooltip': "'Reacts to the light'",
        'block.terracraft.planteras_bulb': "Plantera's Bulb",
        'block.terracraft.life_fruit_plant': 'Life Fruit',
        'item.terracraft.temple_key.tooltip': 'Opens the jungle temple door',
        'item.terracraft.seedler.tooltip': 'Throws seeds with every swing',
        'item.terracraft.venus_magnum.tooltip': "'A flower that fires bullets'",
        'item.terracraft.leaf_blower.tooltip': 'Rapidly shoots razor-sharp leaves',
        'block.terracraft.locked_lihzahrd_door': 'Lihzahrd Door',
        'block.terracraft.lihzahrd_altar': 'Lihzahrd Altar',
        'block.terracraft.dart_trap': 'Super Dart Trap',
        'message.terracraft.temple.locked': 'It is locked. A Temple Key might open it...',
        'message.terracraft.temple.altar': 'It has a slot shaped like a Lihzahrd Power Cell',
        'item.terracraft.lihzahrd_power_cell.tooltip': 'Used at the Lihzahrd Altar',
        'item.terracraft.picksaw.tooltip': 'Capable of mining Lihzahrd Bricks',
        'item.terracraft.heat_ray.tooltip': "Shoots a piercing heat ray\n'Oolaa!!'",
        'item.terracraft.possessed_hatchet.tooltip': 'Chases after your enemy',
        'item.terracraft.sun_stone.tooltip': 'Increases all stats if worn during the day',
        'item.terracraft.eye_of_the_golem.tooltip': '10% increased critical strike chance',
        'item.terracraft.beetle_husk.tooltip': "'Beetles drop these sometimes'",
        'ability.terracraft.sun_stone': 'All stats up during the day',
    })


def blocks():
    for m in METALS:
        for ore in (f'{m}_ore', f'deepslate_{m}_ore'):
            write(f'{NS}/loot_table/blocks/{ore}.json', ore_loot(ore, f'raw_{m}'))
    for b in ['work_bench', 'iron_anvil', 'lead_anvil']:
        write(f'{NS}/loot_table/blocks/{b}.json', self_loot(b))
    write(f'{NS}/loot_table/blocks/life_crystal_block.json', {
        'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': t('life_crystal')}]}],
        'random_sequence': f'{NS}:blocks/life_crystal_block'})
    write('minecraft/tags/block/mineable/pickaxe.json', {'values': [t(o) for o in ORES] + [t('iron_anvil'), t('lead_anvil'), t('life_crystal_block'),
                                                                   t('ebonstone'), t('crimstone'), t('demonite_ore'), t('crimtane_ore'),
                                                                   t('blue_brick'), t('green_brick'), t('pink_brick'), t('spikes'), t('hive'), t('hellstone'),
                                                                   t('obsidian_brick'), t('hellstone_brick'), t('hellforge'), t('meteorite')]
                                                                   + [t(b) for b in HM_ORES + ['pearlstone', 'mythril_anvil', 'orichalcum_anvil', 'adamantite_forge', 'titanium_forge', 'crystal_shard', 'gelatin_crystal_block'] + S6_PICKAXE]})
    write('minecraft/tags/block/needs_stone_tool.json', {'values': [t(f'{p}{m}_ore') for m in STONE_TIER for p in ('', 'deepslate_')]})
    write('minecraft/tags/block/needs_iron_tool.json', {'values': [t(f'{p}{m}_ore') for m in IRON_TIER for p in ('', 'deepslate_')]})
    write(f'{NS}/tags/block/mineable/hammer.json', {'values': [t('shadow_orb'), t('crimson_heart'), t('demon_altar'), t('crimson_altar')]})
    # crafting station tags: any block in the tag counts as that station
    write(f'{NS}/tags/block/stations/work_bench.json', {'values': [t('work_bench')]})
    write(f'{NS}/tags/block/stations/furnace.json', {'values': ['minecraft:furnace', 'minecraft:blast_furnace', t('hellforge'), t('adamantite_forge'), t('titanium_forge')]})
    write(f'{NS}/tags/block/stations/anvil.json', {'values': [t('iron_anvil'), t('lead_anvil'), t('mythril_anvil'), t('orichalcum_anvil'), 'minecraft:anvil', 'minecraft:chipped_anvil', 'minecraft:damaged_anvil']})
    write(f'{NS}/tags/block/stations/alchemy.json', {'values': ['minecraft:brewing_stand']})
    write(f'{NS}/tags/block/stations/sawmill.json', {'values': []})
    write(f'{NS}/tags/block/stations/loom.json', {'values': ['minecraft:loom']})
    # Terraria pickaxe power requirements (vanilla blocks; TerraCraft ores add theirs as they are introduced)
    write(f'{NS}/terracraft/mining_power/obsidian.json', {'pickaxe_power': 55, 'blocks': ['minecraft:obsidian', 'minecraft:crying_obsidian']})
    write(f'{NS}/terracraft/mining_power/ancient_debris.json', {'pickaxe_power': 100, 'blocks': ['minecraft:ancient_debris']})
    # common ore tags so other systems / mods can find them
    for m in METALS:
        write(f'c/tags/item/ingots/{m}.json', {'values': [t(f'{m}_bar')]})
        write(f'c/tags/item/raw_materials/{m}.json', {'values': [t(f'raw_{m}')]})


# ============================================================================== damage types
def damage():
    for name, scaling in [('projectile', 'never'), ('magic', 'never'), ('enemy', 'never'), ('enemy_projectile', 'never')]:
        write(f'{NS}/damage_type/{name}.json', {'exhaustion': 0.1, 'message_id': f'{NS}.{name}', 'scaling': scaling})
    write('minecraft/tags/damage_type/no_knockback.json', {'values': [t('projectile'), t('magic'), t('enemy_projectile')]})
    write('minecraft/tags/damage_type/is_projectile.json', {'values': [t('projectile'), t('enemy_projectile')]})


# ============================================================================== recipes
def smelting():
    for m in METALS:
        for kind, time in (('smelting', 200), ('blasting', 100)):
            write(f'{NS}/recipe/{m}_bar_from_{kind}.json', {
                'type': f'minecraft:{kind}', 'category': 'misc', 'cookingtime': time, 'experience': 0.7,
                'ingredient': t(f'raw_{m}'), 'result': {'id': t(f'{m}_bar')}})
            write(f'{NS}/recipe/{m}_bar_from_{kind}_ore.json', {
                'type': f'minecraft:{kind}', 'category': 'misc', 'cookingtime': time, 'experience': 0.7,
                'ingredient': [t(f'{m}_ore'), t(f'deepslate_{m}_ore')], 'result': {'id': t(f'{m}_bar')}})


RECIPES = {}


def recipe(name, result, ingredients, stations=(), count=1, category='misc', condition=None):
    data = {'result': {'item': result, 'count': count}, 'ingredients': [], 'stations': list(stations), 'category': category}
    for ing, amount in ingredients:
        if ing.startswith('#'):
            data['ingredients'].append({'tag': ing[1:], 'count': amount})
        else:
            data['ingredients'].append({'item': ing, 'count': amount})
    if condition:
        data['condition'] = condition
    RECIPES[name] = data


WB = t('work_bench')
ANVIL = t('anvil')
FURNACE = t('furnace')
ALCHEMY = t('alchemy')
PLANKS = '#minecraft:planks'
BAR = {'copper': 'minecraft:copper_ingot', 'iron': 'minecraft:iron_ingot', 'gold': 'minecraft:gold_ingot',
       'tin': t('tin_bar'), 'lead': t('lead_bar'), 'silver': t('silver_bar'), 'tungsten': t('tungsten_bar'), 'platinum': t('platinum_bar')}
TIER = {'copper': 0, 'tin': 0, 'iron': 1, 'lead': 1, 'silver': 2, 'tungsten': 2, 'gold': 3, 'platinum': 3}


def terraria_recipes():
    for evil, bar, mat in (('demonite', t('demonite_bar'), t('shadow_scale')), ('crimtane', t('crimtane_bar'), t('tissue_sample'))):
        corrupt = evil == 'demonite'
        recipe('lights_bane' if corrupt else 'blood_butcherer', t('lights_bane' if corrupt else 'blood_butcherer'), [(bar, 10)], [ANVIL], category='weapons')
        recipe('demon_bow' if corrupt else 'tendon_bow', t('demon_bow' if corrupt else 'tendon_bow'), [(bar, 10)], [ANVIL], category='weapons')
        recipe('nightmare_pickaxe' if corrupt else 'deathbringer_pickaxe', t('nightmare_pickaxe' if corrupt else 'deathbringer_pickaxe'),
               [(bar, 12), (mat, 6)], [ANVIL], category='tools')
        recipe('war_axe_of_the_night' if corrupt else 'blood_lust_cluster', t('war_axe_of_the_night' if corrupt else 'blood_lust_cluster'),
               [(bar, 10), (mat, 5)], [ANVIL], category='tools')
        recipe('the_breaker' if corrupt else 'flesh_grinder', t('the_breaker' if corrupt else 'flesh_grinder'), [(bar, 10), (mat, 5)], [ANVIL], category='tools')
        armor = 'shadow' if corrupt else 'crimson'
        recipe(f'{armor}_helmet', t(f'{armor}_helmet'), [(bar, 15), (mat, 10)], [ANVIL], category='armor')
        recipe(f'{armor}_scalemail', t(f'{armor}_scalemail'), [(bar, 25), (mat, 20)], [ANVIL], category='armor')
        recipe(f'{armor}_greaves', t(f'{armor}_greaves'), [(bar, 20), (mat, 15)], [ANVIL], category='armor')
    recipe('ebonwood_planks', 'minecraft:dark_oak_planks', [(t('ebonwood'), 1)], [], count=4, category='materials')
    recipe('shadewood_planks', 'minecraft:mangrove_planks', [(t('shadewood'), 1)], [], count=4, category='materials')
    ALTAR = t('demon_altar')
    recipe('vile_powder', t('vile_powder'), [(t('vile_mushroom'), 1)], [ALCHEMY], count=5, category='materials')
    recipe('vicious_powder', t('vicious_powder'), [(t('vicious_mushroom'), 1)], [ALCHEMY], count=5, category='materials')
    recipe('worm_food', t('worm_food'), [(t('vile_powder'), 30), (t('rotten_chunk'), 15)], [ALTAR], category='consumables')
    recipe('bloody_spine', t('bloody_spine'), [(t('vicious_powder'), 30), (t('vertebra'), 15)], [ALTAR], category='consumables')
    recipe('slime_crown', t('slime_crown'), [(t('gel'), 20), ('minecraft:gold_ingot', 5)], [WB], category='consumables')
    recipe('slime_crown_platinum', t('slime_crown'), [(t('gel'), 20), (t('platinum_bar'), 5)], [WB], category='consumables')
    recipe('suspicious_looking_eye', t('suspicious_looking_eye'), [(t('lens'), 6)], [WB], category='consumables')
    recipe('abeemination', t('abeemination'), [('minecraft:honey_block', 5), ('minecraft:honey_bottle', 1), (t('stinger'), 1)], [WB], category='consumables')
    recipe('blade_of_grass', t('blade_of_grass'), [(t('jungle_spores'), 12), (t('stinger'), 15), ('minecraft:vine', 3)], [ANVIL], category='weapons')
    recipe('jungle_hat', t('jungle_hat'), [(t('jungle_spores'), 8), (t('stinger'), 12)], [ANVIL], category='armor')
    recipe('jungle_shirt', t('jungle_shirt'), [(t('jungle_spores'), 10), (t('stinger'), 15), ('minecraft:vine', 1)], [ANVIL], category='armor')
    recipe('jungle_pants', t('jungle_pants'), [(t('jungle_spores'), 8), (t('stinger'), 12)], [ANVIL], category='armor')
    HELLFORGE = t('hellforge')
    HBAR = t('hellstone_bar')
    # Hardmode wings at a Mythril/Orichalcum Anvil
    HM_ANVIL, HM_FORGE = t('hardmode_anvil'), t('hardmode_forge')
    hm_tiers = {'cobalt': (3, FURNACE, ANVIL, 15), 'palladium': (4, FURNACE, ANVIL, 15), 'mythril': (4, FURNACE, HM_ANVIL, 10),
                'orichalcum': (5, FURNACE, HM_ANVIL, 15), 'adamantite': (4, HM_FORGE, HM_ANVIL, 12), 'titanium': (4, HM_FORGE, HM_ANVIL, 13)}
    for m, (raws, smelt, anvil, base) in hm_tiers.items():
        bar = t(f'{m}_bar')
        recipe(f'{m}_bar', bar, [(t(f'raw_{m}'), raws)], [smelt], category='materials')
        recipe(f'{m}_pickaxe', t(f'{m}_pickaxe'), [(bar, base + 3)], [anvil], category='tools')
        recipe(f'{m}_sword', t(f'{m}_sword'), [(bar, base)], [anvil], category='weapons')
        recipe(f'{m}_repeater', t(f'{m}_repeater'), [(bar, base)], [anvil], category='weapons')
        recipe(f'{m}_helmet', t(f'{m}_helmet'), [(bar, base - 3)], [anvil], category='armor')
        recipe(f'{m}_breastplate', t(f'{m}_breastplate'), [(bar, base + 5)], [anvil], category='armor')
        recipe(f'{m}_leggings', t(f'{m}_leggings'), [(bar, base + 1)], [anvil], category='armor')
    recipe('mythril_anvil', t('mythril_anvil'), [(t('mythril_bar'), 10)], [ANVIL], category='furniture')
    recipe('orichalcum_anvil', t('orichalcum_anvil'), [(t('orichalcum_bar'), 12)], [ANVIL], category='furniture')
    recipe('adamantite_forge', t('adamantite_forge'), [(t('raw_adamantite'), 30), (t('hellforge'), 1)], [HM_ANVIL], category='furniture')
    recipe('titanium_forge', t('titanium_forge'), [(t('raw_titanium'), 30), (t('hellforge'), 1)], [HM_ANVIL], category='furniture')
    # Mechanical boss summons and Hallowed gear
    recipe('mechanical_worm', t('mechanical_worm'), [(t('rotten_chunk'), 6), ('minecraft:iron_ingot', 5), (t('soul_of_night'), 6)], [HM_ANVIL], category='consumables')
    recipe('mechanical_worm_vertebra', t('mechanical_worm'), [(t('vertebra'), 6), ('minecraft:iron_ingot', 5), (t('soul_of_night'), 6)], [HM_ANVIL], category='consumables')
    recipe('mechanical_eye', t('mechanical_eye'), [(t('lens'), 3), ('minecraft:iron_ingot', 5), (t('soul_of_light'), 6)], [HM_ANVIL], category='consumables')
    recipe('mechanical_skull', t('mechanical_skull'), [('minecraft:bone', 30), ('minecraft:iron_ingot', 5), (t('soul_of_light'), 3), (t('soul_of_night'), 3)],
           [HM_ANVIL], category='consumables')
    HB = t('hallowed_bar')
    recipe('excalibur', t('excalibur'), [(HB, 12)], [HM_ANVIL], category='weapons')
    recipe('hallowed_repeater', t('hallowed_repeater'), [(HB, 12)], [HM_ANVIL], category='weapons')
    recipe('pickaxe_axe', t('pickaxe_axe'), [(HB, 18), (t('soul_of_might'), 1), (t('soul_of_sight'), 1), (t('soul_of_fright'), 1)], [HM_ANVIL], category='tools')
    recipe('hallowed_mask', t('hallowed_mask'), [(HB, 12)], [HM_ANVIL], category='armor')
    recipe('hallowed_plate_mail', t('hallowed_plate_mail'), [(HB, 24)], [HM_ANVIL], category='armor')
    recipe('hallowed_greaves', t('hallowed_greaves'), [(HB, 18)], [HM_ANVIL], category='armor')
    recipe('pearlwood_planks', 'minecraft:birch_planks', [(t('pearlwood'), 1)], [], count=4, category='materials')
    recipe('angel_wings', t('angel_wings'), [('minecraft:feather', 10), (t('soul_of_light'), 25), (t('soul_of_flight'), 20)], [HM_ANVIL], category='accessories')
    recipe('demon_wings', t('demon_wings'), [('minecraft:feather', 10), (t('soul_of_night'), 25), (t('soul_of_flight'), 20)], [HM_ANVIL], category='accessories')
    recipe('hellforge', t('hellforge'), [('minecraft:furnace', 1), (t('hellstone'), 10), ('minecraft:obsidian', 20)], [ANVIL], category='furniture')
    recipe('hellstone_bar', HBAR, [(t('hellstone'), 3), ('minecraft:obsidian', 1)], [HELLFORGE], category='materials')
    recipe('obsidian_brick', t('obsidian_brick'), [('minecraft:obsidian', 2)], [FURNACE], category='blocks')
    recipe('hellstone_brick', t('hellstone_brick'), [(t('hellstone'), 1), ('#minecraft:stone_crafting_materials', 1)], [FURNACE], category='blocks')
    recipe('molten_pickaxe', t('molten_pickaxe'), [(HBAR, 20)], [ANVIL], category='tools')
    recipe('molten_hamaxe', t('molten_hamaxe'), [(HBAR, 15)], [ANVIL], category='tools')
    recipe('fiery_greatsword', t('fiery_greatsword'), [(HBAR, 20)], [ANVIL], category='weapons')
    recipe('molten_fury', t('molten_fury'), [(HBAR, 15)], [ANVIL], category='weapons')
    recipe('phoenix_blaster', t('phoenix_blaster'), [(t('handgun'), 1), (HBAR, 10)], [ANVIL], category='weapons')
    recipe('molten_helmet', t('molten_helmet'), [(HBAR, 10)], [ANVIL], category='armor')
    recipe('molten_breastplate', t('molten_breastplate'), [(HBAR, 20)], [ANVIL], category='armor')
    recipe('molten_greaves', t('molten_greaves'), [(HBAR, 15)], [ANVIL], category='armor')
    TINKER = t('tinkerers_workshop')
    recipe('goblin_battle_standard', t('goblin_battle_standard'), [(t('tattered_cloth'), 10), (PLANKS, 5)], [WB], category='consumables')
    recipe('obsidian_horseshoe', t('obsidian_horseshoe'), [(t('lucky_horseshoe'), 1), (t('obsidian_skull'), 1)], [TINKER], category='accessories')
    recipe('cloud_in_a_balloon', t('cloud_in_a_balloon'), [(t('cloud_in_a_bottle'), 1), (t('shiny_red_balloon'), 1)], [TINKER], category='accessories')
    recipe('obsidian_shield', t('obsidian_shield'), [(t('cobalt_shield'), 1), (t('obsidian_skull'), 1)], [TINKER], category='accessories')
    recipe('obsidian_water_walking_boots', t('obsidian_water_walking_boots'), [(t('water_walking_boots'), 1), (t('obsidian_skull'), 1)], [TINKER],
           category='accessories')
    recipe('lava_waders', t('lava_waders'), [(t('obsidian_water_walking_boots'), 1), (t('lava_charm'), 1)], [TINKER], category='accessories')
    recipe('mana_flower', t('mana_flower'), [(t('natures_gift'), 1), (t('lesser_mana_potion'), 1)], [TINKER], category='accessories')
    recipe('meteorite_bar', t('meteorite_bar'), [(t('meteorite'), 3)], [FURNACE], category='materials')
    recipe('space_gun', t('space_gun'), [(t('meteorite_bar'), 20)], [ANVIL], category='weapons')
    recipe('meteor_helmet', t('meteor_helmet'), [(t('meteorite_bar'), 20)], [ANVIL], category='armor')
    recipe('meteor_suit', t('meteor_suit'), [(t('meteorite_bar'), 30)], [ANVIL], category='armor')
    recipe('meteor_leggings', t('meteor_leggings'), [(t('meteorite_bar'), 25)], [ANVIL], category='armor')
    recipe('demonite_bar', t('demonite_bar'), [(t('demonite_ore'), 3)], [FURNACE], category='materials')
    recipe('crimtane_bar', t('crimtane_bar'), [(t('crimtane_ore'), 3)], [FURNACE], category='materials')
    # ---- by hand / work bench basics
    recipe('work_bench', t('work_bench'), [(PLANKS, 10)], category='furniture')
    recipe('torch', 'minecraft:torch', [(t('gel'), 1), (PLANKS, 1)], count=3, category='furniture')
    recipe('furnace', 'minecraft:furnace', [('#minecraft:stone_crafting_materials', 20), (PLANKS, 4), ('minecraft:torch', 3)], [WB], category='furniture')
    recipe('chest', 'minecraft:chest', [(PLANKS, 8), ('minecraft:iron_ingot', 2)], [WB], category='furniture')
    recipe('wooden_sword', t('wooden_sword'), [(PLANKS, 7)], [WB], category='weapons')
    recipe('wooden_bow', t('wooden_bow'), [(PLANKS, 10)], [WB], category='weapons')
    recipe('wood_helmet', t('wood_helmet'), [(PLANKS, 20)], [WB], category='armor')
    recipe('wood_breastplate', t('wood_breastplate'), [(PLANKS, 30)], [WB], category='armor')
    recipe('wood_greaves', t('wood_greaves'), [(PLANKS, 25)], [WB], category='armor')
    recipe('iron_anvil', t('iron_anvil'), [('minecraft:iron_ingot', 5)], [WB], category='furniture')
    recipe('lead_anvil', t('lead_anvil'), [(t('lead_bar'), 5)], [WB], category='furniture')
    recipe('mana_crystal', t('mana_crystal'), [(t('fallen_star'), 5)], category='consumables')
    recipe('flaming_arrow', t('flaming_arrow'), [('minecraft:arrow', 10), ('minecraft:torch', 1)], count=10, category='ammo')
    recipe('wooden_arrow', 'minecraft:arrow', [(PLANKS, 1), ('#minecraft:stone_crafting_materials', 1)], [WB], count=10, category='ammo')
    recipe('amethyst_staff', t('amethyst_staff'), [(t('amethyst'), 8), ('minecraft:copper_ingot', 10)], [ANVIL], category='weapons')
    recipe('lesser_healing_potion', t('lesser_healing_potion'), [(t('gel'), 2), ('minecraft:red_mushroom', 1), ('minecraft:glass_bottle', 2)], [ALCHEMY], count=2, category='consumables')
    recipe('lesser_mana_potion', t('lesser_mana_potion'), [(t('gel'), 2), (t('fallen_star'), 1), ('minecraft:glass_bottle', 2)], [ALCHEMY], count=2, category='consumables')
    recipe('healing_potion', t('healing_potion'), [(t('lesser_healing_potion'), 2), ('minecraft:glow_berries', 1)], [ALCHEMY], category='consumables')
    recipe('greater_healing_potion', t('greater_healing_potion'), [('minecraft:glass_bottle', 3), (t('pixie_dust'), 3), (t('crystal_shard'), 1)],
           [ALCHEMY], count=3, category='consumables')
    recipe('mana_potion', t('mana_potion'), [(t('lesser_mana_potion'), 2), ('minecraft:glow_berries', 1)], [ALCHEMY], category='consumables')
    # ---- ore equipment at an anvil (Terraria bar counts)
    armor_cost = [(15, 25, 20), (20, 30, 25), (25, 35, 30), (30, 40, 35)]
    for m, bar in BAR.items():
        tier = TIER[m]
        if m != 'copper':
            recipe(f'{m}_broadsword', t(f'{m}_broadsword'), [(bar, 8)], [ANVIL], category='weapons')
        else:
            recipe('copper_broadsword', t('copper_broadsword'), [(bar, 8)], [ANVIL], category='weapons')
            recipe('copper_shortsword', t('copper_shortsword'), [(bar, 7)], [ANVIL], category='weapons')
        recipe(f'{m}_pickaxe', t(f'{m}_pickaxe'), [(bar, 12), (PLANKS, 3)], [ANVIL], category='tools')
        recipe(f'{m}_axe', t(f'{m}_axe'), [(bar, 9), (PLANKS, 3)], [ANVIL], category='tools')
        helmet, chest, legs = armor_cost[tier]
        recipe(f'{m}_helmet', t(f'{m}_helmet'), [(bar, helmet)], [ANVIL], category='armor')
        recipe(f'{m}_chainmail', t(f'{m}_chainmail'), [(bar, chest)], [ANVIL], category='armor')
        recipe(f'{m}_greaves', t(f'{m}_greaves'), [(bar, legs)], [ANVIL], category='armor')
    recipe('copper_hammer', t('copper_hammer'), [('minecraft:copper_ingot', 10), (PLANKS, 3)], [ANVIL], category='tools')
    recipe('iron_hammer', t('iron_hammer'), [('minecraft:iron_ingot', 10), (PLANKS, 3)], [ANVIL], category='tools')
    for m in ['copper', 'iron', 'gold']:
        recipe(f'{m}_bow', t(f'{m}_bow'), [(BAR[m], 7)], [ANVIL], category='weapons')
    stage6_recipes()
    stage7_recipes()
    stage8_recipes()
    for name, data in RECIPES.items():
        write(f'{NS}/terracraft/recipe/{name}.json', data)


# ============================================================================== vanilla overrides
DISABLED = {
    'disableDiamondGear': ['diamond_sword', 'diamond_pickaxe', 'diamond_axe', 'diamond_shovel', 'diamond_hoe', 'diamond_spear',
                           'diamond_helmet', 'diamond_chestplate', 'diamond_leggings', 'diamond_boots'],
    'disableNetheriteGear': ['netherite_sword_smithing', 'netherite_pickaxe_smithing', 'netherite_axe_smithing',
                             'netherite_shovel_smithing', 'netherite_hoe_smithing', 'netherite_spear_smithing',
                             'netherite_helmet_smithing', 'netherite_chestplate_smithing', 'netherite_leggings_smithing',
                             'netherite_boots_smithing', 'netherite_upgrade_smithing_template'],
    'disableEnchanting': ['enchanting_table'],
    'disableVanillaBrewing': ['brewing_stand'],
}

REMOVED_STRUCTURES = ['village_plains', 'village_desert', 'village_savanna', 'village_snowy', 'village_taiga',
                      'pillager_outpost', 'woodland_mansion', 'stronghold', 'ruined_portal_desert', 'ruined_portal_jungle',
                      'ruined_portal_mountain', 'ruined_portal_ocean', 'ruined_portal_standard', 'ruined_portal_swamp',
                      'ancient_city']   # the deep dark would sit inside the Underworld


OVERRIDES = os.path.join(RES, 'packs/vanilla_overrides')


def write_override(path, data):
    full = os.path.join(OVERRIDES, 'data', path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, 'w') as f:
        json.dump(data, f, indent=2)
        f.write('\n')


def vanilla_overrides(client_jar):
    """Replacing overrides live in a nested built-in pack that TerraCraft pins above vanilla and Forge."""
    os.makedirs(OVERRIDES, exist_ok=True)
    with open(os.path.join(OVERRIDES, 'pack.mcmeta'), 'w') as f:
        json.dump({'pack': {'description': 'TerraCraft: removes vanilla progression bypasses (config controlled)',
                            'max_format': 107, 'min_format': [107, 1]}}, f, indent=2)
    with zipfile.ZipFile(client_jar) as jar:
        for option, names in DISABLED.items():
            for name in names:
                data = json.loads(jar.read(f'data/minecraft/recipe/{name}.json'))
                data = {'neoforge:conditions': [{'type': 'terracraft:config', 'option': option, 'value': False}], **data}
                write_override(f'minecraft/recipe/{name}.json', data)
    for structure in REMOVED_STRUCTURES:
        write_override(f'minecraft/tags/worldgen/biome/has_structure/{structure}.json', {'replace': True, 'values': []})


# ============================================================================== language
def title(name):
    small = {'of', 'the', 'in', 'a'}
    words = name.split('_')
    return ' '.join(w if (i > 0 and w in small) else w.capitalize() for i, w in enumerate(words))


def lang():
    L = {}
    items_dir = os.path.join(RES, 'assets/terracraft/items')
    for fname in sorted(os.listdir(items_dir)):
        name = fname[:-5]
        key = ('block.' if name in ORES or name in EVIL_BLOCKS or name in DUNGEON_BLOCKS or name in JUNGLE_BLOCKS or name in UNDERWORLD_BLOCKS or name in GOBLIN_BLOCKS or name in HM_BLOCKS or name in QS_BLOCKS or name in S6_BLOCKS or name in ('work_bench', 'iron_anvil', 'lead_anvil')
               else 'item.') + f'{NS}.{name}'
        L[key] = title(name)
    L['block.terracraft.life_crystal_block'] = 'Life Crystal'
    L['item.terracraft.lights_bane'] = "Light's Bane"
    L['item.terracraft.the_undertaker'] = 'The Undertaker'
    L['item.terracraft.war_axe_of_the_night'] = 'War Axe of the Night'
    L['item.terracraft.panic_necklace.tooltip'] = 'Increases movement speed after taking damage'
    L['item.terracraft.vilethorn.tooltip'] = 'Summons a vile thorn'
    L['ability.terracraft.panic'] = 'Speed burst after taking damage'
    L.update({
        'item.terracraft.natures_gift': "Nature's Gift",
        'item.terracraft.dev_tablet': 'Developer Tablet',
        'item.terracraft.wand_of_sparking': 'Wand of Sparking',
        'item.terracraft.cloud_in_a_bottle': 'Cloud in a Bottle',
        'item.terracraft.anklet_of_the_wind': 'Anklet of the Wind',
        'item.terracraft.band_of_regeneration': 'Band of Regeneration',
        'item.terracraft.band_of_starpower': 'Band of Starpower',
        # tooltips
        'item.terracraft.life_crystal.tooltip': 'Permanently increases maximum life by 20',
        'item.terracraft.life_fruit.tooltip': 'Permanently increases maximum life by 5 (requires all Life Crystals)',
        'item.terracraft.mana_crystal.tooltip': 'Permanently increases maximum mana by 20',
        'item.terracraft.fallen_star.tooltip': 'Disappears after the sunrise',
        'item.terracraft.dev_tablet.tooltip': 'Opens the TerraCraft developer menu (operators only)',
        'item.terracraft.magic_missile.tooltip': 'Casts a controllable missile',
        'item.terracraft.wand_of_sparking.tooltip': 'Shoots a small spark',
        'item.terracraft.lesser_healing_potion.tooltip': 'Restores 50 life',
        'item.terracraft.healing_potion.tooltip': 'Restores 100 life',
        'item.terracraft.lesser_mana_potion.tooltip': 'Restores 50 mana',
        'item.terracraft.mana_potion.tooltip': 'Restores 100 mana',
        'item.terracraft.ironskin_potion.tooltip': 'Increases defense by 8',
        'item.terracraft.swiftness_potion.tooltip': 'Increases movement speed by 25%',
        'item.terracraft.regeneration_potion.tooltip': 'Provides life regeneration',
        'item.terracraft.mana_regeneration_potion.tooltip': 'Increases mana regeneration',
        'item.terracraft.magic_power_potion.tooltip': 'Increases magic damage by 20%',
        'item.terracraft.archery_potion.tooltip': 'Increases ranged damage by 10%',
        'item.terracraft.mining_potion.tooltip': 'Increases mining speed by 25%',
        'item.terracraft.obsidian_skin_potion.tooltip': 'Provides immunity to lava',
        'item.terracraft.water_walking_potion.tooltip': 'Allows the ability to walk on water',
        'item.terracraft.endurance_potion.tooltip': 'Reduces damage taken by 10%',
        'item.terracraft.wrath_potion.tooltip': 'Increases damage by 10%',
        'item.terracraft.rage_potion.tooltip': 'Increases critical chance by 10%',
        'item.terracraft.hermes_boots.tooltip': 'The wearer can run super fast',
        'item.terracraft.cloud_in_a_bottle.tooltip': 'Allows the holder to double jump',
        'item.terracraft.shiny_red_balloon.tooltip': 'Increases jump height',
        'item.terracraft.lucky_horseshoe.tooltip': 'Negates fall damage',
        'item.terracraft.obsidian_skull.tooltip': 'Grants immunity to fire blocks',
        'item.terracraft.lava_charm.tooltip': 'Provides 7 seconds of immunity to lava',
        'item.terracraft.cobalt_shield.tooltip': 'Grants immunity to knockback',
        'item.terracraft.flipper.tooltip': 'Grants the ability to swim',
        'item.terracraft.water_walking_boots.tooltip': 'Provides the ability to walk on water',
        'item.terracraft.toolbelt.tooltip': 'Increases block placement range',
        # creative tabs
        'itemGroup.terracraft.blocks': 'TerraCraft: Blocks', 'itemGroup.terracraft.materials': 'TerraCraft: Materials',
        'itemGroup.terracraft.weapons': 'TerraCraft: Weapons', 'itemGroup.terracraft.tools_armor': 'TerraCraft: Tools & Armor',
        'itemGroup.terracraft.accessories': 'TerraCraft: Accessories', 'itemGroup.terracraft.consumables': 'TerraCraft: Consumables',
        'itemGroup.terracraft.dev': 'TerraCraft: Developer',
        # entities and damage
        'entity.terracraft.projectile': 'Projectile',
        'death.attack.terracraft.projectile': '%1$s was slain',
        'death.attack.terracraft.projectile.player': '%1$s was slain by %2$s',
        'death.attack.terracraft.magic': '%1$s was slain by magic',
        'death.attack.terracraft.magic.player': '%1$s was slain by %2$s',
        'death.attack.terracraft.enemy': '%1$s was slain',
        'death.attack.terracraft.enemy.player': '%1$s was slain by %2$s',
        'death.attack.terracraft.enemy_projectile': '%1$s was slain',
        'death.attack.terracraft.enemy_projectile.player': '%1$s was slain by %2$s',
        # keys and screens
        'key.category.terracraft.terracraft': 'TerraCraft',
        'key.terracraft.equipment': 'Open Equipment',
        'key.terracraft.crafting': 'Open Crafting',
        'screen.terracraft.dev_menu': 'TerraCraft Developer Menu',
        'screen.terracraft.equipment': 'Equipment',
        'screen.terracraft.equipment.button': 'Equip',
        'screen.terracraft.crafting.button': 'Craft',
        'screen.terracraft.equipment.accessories': 'Accessories',
        'screen.terracraft.equipment.defense': 'Defense: %s',
        'screen.terracraft.equipment.life': 'Max life: %s',
        'screen.terracraft.equipment.mana': 'Max mana: %s',
        'screen.terracraft.crafting': 'Crafting',
        'screen.terracraft.crafting.craft': 'Craft',
        'screen.terracraft.crafting.craft_10': 'Craft x10',
        'screen.terracraft.crafting.filter.craftable': 'Showing: craftable now',
        'screen.terracraft.crafting.filter.nearby': 'Showing: stations nearby',
        'screen.terracraft.crafting.filter.all': 'Showing: all recipes',
        'screen.terracraft.crafting.materials': 'Materials',
        'screen.terracraft.crafting.stations': 'Required stations',
        'screen.terracraft.crafting.by_hand': 'By hand',
        'screen.terracraft.crafting.empty': 'Nothing can be crafted here. Gather materials or stand near crafting stations (Work Bench, Furnace, Anvil...).',
        # stations
        'station.terracraft.work_bench': 'Work Bench', 'station.terracraft.furnace': 'Furnace', 'station.terracraft.anvil': 'Iron/Lead Anvil',
        'station.terracraft.sawmill': 'Sawmill', 'station.terracraft.loom': 'Loom', 'station.terracraft.alchemy': 'Alchemy Station',
        'station.terracraft.hellforge': 'Hellforge', 'station.terracraft.demon_altar': 'Demon/Crimson Altar',
        'station.terracraft.hardmode_anvil': 'Mythril/Orichalcum Anvil', 'station.terracraft.hardmode_forge': 'Adamantite/Titanium Forge',
        'station.terracraft.tinkerers_workshop': "Tinkerer's Workshop", 'station.terracraft.water': 'Water', 'station.terracraft.lava': 'Lava',
        # messages
        'message.terracraft.dev.no_permission': 'You need operator permissions to use developer tools.',
        'message.terracraft.no_ammo': 'You have no %s.',
        'message.terracraft.crafting.missing_station': 'You are not close enough to the required crafting station.',
        'message.terracraft.pickaxe_too_weak': 'Your pickaxe is not powerful enough (requires %s%% pickaxe power).',
        'message.terracraft.life_crystal.max': 'You have already used the maximum number of Life Crystals.',
        'message.terracraft.life_fruit.need_crystals': 'You must use every Life Crystal before Life Fruit has any effect.',
        'message.terracraft.life_fruit.max': 'You have already eaten the maximum amount of Life Fruit.',
        'message.terracraft.mana_crystal.max': 'You have already used the maximum number of Mana Crystals.',
        'message.terracraft.death.coins': 'You dropped %s.',
        'message.terracraft.nether_disabled': 'The way to the Nether is sealed. The Underworld lies far beneath your feet.',
        'message.terracraft.end_disabled': 'The End is sealed in this world.',
        'message.terracraft.enchanting_disabled': 'Enchanting has no power here. Reforging will replace it.',
        'message.terracraft.brewing_disabled': 'Potions are crafted at an alchemy station, not brewed.',
        # coins
        'coin.terracraft.platinum': '%s platinum', 'coin.terracraft.gold': '%s gold', 'coin.terracraft.silver': '%s silver', 'coin.terracraft.copper': '%s copper',
        # tooltips
        'tooltip.terracraft.damage': '%s %s damage', 'tooltip.terracraft.crit': '%s%% critical strike chance',
        'tooltip.terracraft.mana': 'Uses %s mana', 'tooltip.terracraft.uses_ammo': 'Uses %s',
        'tooltip.terracraft.ammo': 'Ammo', 'tooltip.terracraft.equipable': 'Equipable',
        'tooltip.terracraft.defense': '%s defense', 'tooltip.terracraft.set_bonus': 'Set bonus: %s',
        'tooltip.terracraft.pickaxe_power': '%s%% pickaxe power', 'tooltip.terracraft.axe_power': '%s%% axe power',
        'tooltip.terracraft.hammer_power': '%s%% hammer power', 'tooltip.terracraft.sell': 'Sell price: %s',
        'tooltip.terracraft.speed.insanely_fast': 'Insanely fast speed', 'tooltip.terracraft.speed.very_fast': 'Very fast speed',
        'tooltip.terracraft.speed.fast': 'Fast speed', 'tooltip.terracraft.speed.average': 'Average speed',
        'tooltip.terracraft.speed.slow': 'Slow speed', 'tooltip.terracraft.speed.very_slow': 'Very slow speed',
        'tooltip.terracraft.speed.extremely_slow': 'Extremely slow speed', 'tooltip.terracraft.speed.snail': 'Snail speed',
        'tooltip.terracraft.knockback.none': 'No knockback', 'tooltip.terracraft.knockback.extremely_weak': 'Extremely weak knockback',
        'tooltip.terracraft.knockback.very_weak': 'Very weak knockback', 'tooltip.terracraft.knockback.weak': 'Weak knockback',
        'tooltip.terracraft.knockback.average': 'Average knockback', 'tooltip.terracraft.knockback.strong': 'Strong knockback',
        'tooltip.terracraft.knockback.very_strong': 'Very strong knockback', 'tooltip.terracraft.knockback.extremely_strong': 'Extremely strong knockback',
        'tooltip.terracraft.knockback.insane': 'Insane knockback',
        'damage_class.terracraft.generic': '', 'damage_class.terracraft.melee': 'melee', 'damage_class.terracraft.ranged': 'ranged',
        'damage_class.terracraft.magic': 'magic', 'damage_class.terracraft.summon': 'summon',
        'ammo.terracraft.arrow': 'arrows', 'ammo.terracraft.bullet': 'bullets', 'ammo.terracraft.rocket': 'rockets',
        'ammo.terracraft.dart': 'darts', 'ammo.terracraft.gel': 'gel', 'ammo.terracraft.fallen_star': 'fallen stars',
        'layer.terracraft.space': 'Space', 'layer.terracraft.surface': 'Surface', 'layer.terracraft.underground': 'Underground',
        'layer.terracraft.cavern': 'Cavern', 'layer.terracraft.underworld': 'Underworld',
    })
    stats = {'defense': 'defense', 'max_life': 'maximum life', 'max_mana': 'maximum mana', 'life_regen': 'life regeneration',
             'mana_regen': 'mana regeneration', 'mana_cost': 'mana cost', 'endurance': 'damage reduction', 'damage': 'damage',
             'melee_damage': 'melee damage', 'ranged_damage': 'ranged damage', 'magic_damage': 'magic damage',
             'summon_damage': 'summon damage', 'crit': 'critical strike chance', 'melee_crit': 'melee critical strike chance',
             'ranged_crit': 'ranged critical strike chance', 'magic_crit': 'magic critical strike chance', 'melee_speed': 'melee speed',
             'knockback': 'knockback', 'armor_penetration': 'armor penetration', 'max_minions': 'maximum minions',
             'max_sentries': 'maximum sentries', 'ammo_conservation': 'chance to not consume ammo', 'move_speed': 'movement speed',
             'jump_height': 'jump height', 'extra_jumps': 'extra jumps', 'mining_speed': 'mining speed', 'reach': 'block reach',
             'fall_damage': 'fall damage', 'lava_immunity_seconds': 'seconds of lava immunity', 'breath': 'breath'}
    for k, v in stats.items():
        L[f'stat.terracraft.{k}'] = v
    abilities = {'no_fall_damage': 'Negates fall damage', 'knockback_immune': 'Grants immunity to knockback',
                 'fire_block_immune': 'Grants immunity to fire blocks', 'lava_immune': 'Grants immunity to lava',
                 'water_walking': 'Allows walking on water', 'lava_walking': 'Allows walking on lava', 'swimming': 'Grants the ability to swim',
                 'water_breathing': 'Allows breathing underwater', 'night_vision': 'Improves vision in the dark',
                 'spelunker': 'Shows the location of treasure and ore', 'danger_sense': 'Shows nearby traps',
                 'hunter': 'Shows the location of enemies', 'shine': 'Emits an aura of light', 'auto_reuse': 'Weapons swing automatically',
                 'mana_flower': 'Automatically uses mana potions when needed', 'magnet': 'Increases pickup range for items',
                 'thorns': 'Attackers also take damage', 'dash': 'Allows the player to dash'}
    for k, v in abilities.items():
        L[f'ability.terracraft.{k}'] = v
    effects = ['ironskin', 'swiftness', 'regeneration', 'mana_regeneration', 'magic_power', 'archery', 'mining', 'obsidian_skin',
               'water_walking', 'endurance', 'wrath', 'rage', 'potion_sickness', 'mana_sickness', 'well_fed', 'plenty_satisfied', 'exquisitely_stuffed']
    for e in effects:
        L[f'effect.terracraft.{e}'] = title(e)
    sets = {'wood': '+1 defense', 'copper': '+2 defense', 'tin': '+2 defense', 'iron': '+2 defense', 'lead': '+3 defense',
            'silver': '+3 defense', 'tungsten': '+3 defense', 'gold': '+3 defense', 'platinum': '+4 defense'}
    for s, bonus in sets.items():
        L[f'armor_set.terracraft.{s}.bonus'] = bonus
    flags = {
        'boss_king_slime_defeated': 'King Slime defeated', 'boss_eye_defeated': 'Eye of Cthulhu defeated',
        'boss_eater_of_worlds_defeated': 'Eater of Worlds defeated', 'boss_brain_of_cthulhu_defeated': 'Brain of Cthulhu defeated',
        'boss_evil_defeated': 'World evil boss defeated', 'boss_queen_bee_defeated': 'Queen Bee defeated',
        'boss_deerclops_defeated': 'Deerclops defeated', 'boss_skeletron_defeated': 'Skeletron defeated',
        'boss_wall_of_flesh_defeated': 'Wall of Flesh defeated', 'hardmode_active': 'Hardmode',
        'boss_queen_slime_defeated': 'Queen Slime defeated', 'boss_destroyer_defeated': 'The Destroyer defeated',
        'boss_twins_defeated': 'The Twins defeated', 'boss_skeletron_prime_defeated': 'Skeletron Prime defeated',
        'mech_bosses_defeated': 'All mechanical bosses defeated', 'boss_plantera_defeated': 'Plantera defeated',
        'boss_golem_defeated': 'Golem defeated', 'boss_duke_fishron_defeated': 'Duke Fishron defeated',
        'boss_empress_of_light_defeated': 'Empress of Light defeated', 'boss_cultist_defeated': 'Lunatic Cultist defeated',
        'celestial_events_active': 'Celestial events', 'moon_lord_defeated': 'Moon Lord defeated',
    }
    for k, v in flags.items():
        L[f'progression.terracraft.{k}'] = v
    L.update({
        'progression.terracraft.announce.hardmode_active': 'The ancient spirits of light and dark have been released.',
        'progression.terracraft.announce.shadow_orb_smashed': 'A horrible chill goes down your spine...',
        'progression.terracraft.announce.boss_plantera_defeated': 'The Jungle Temple trembles...',
        'progression.terracraft.announce.boss_golem_defeated': 'The ancient guardian has fallen.',
        'progression.terracraft.announce.celestial_events_active': 'The celestial pillars have appeared!',
        'progression.terracraft.announce.boss_skeletron_defeated': 'The curse of the Dungeon has been lifted.',
    })
    L.update({
        'message.terracraft.boss.awoken': '%s has awoken!',
        'event.terracraft.blood_moon.start': 'The Blood Moon is rising...',
        'event.terracraft.blood_moon.end': 'The Blood Moon has set.',
        'event.terracraft.slime_rain.start': 'Slime is falling from the sky!',
        'event.terracraft.slime_rain.end': 'Slime has stopped falling from the sky.',
        'message.terracraft.orb.first': 'A horrible chill goes down your spine...',
        'message.terracraft.orb.second': 'Screams echo around you...',
        'message.terracraft.boss.defeated': '%s has been defeated!',
        'message.terracraft.boss.nothing_happens': 'Nothing happens...',
        'message.terracraft.boss.already_active': 'That boss is already here!',
        'item.terracraft.slime_crown.tooltip': 'Summons King Slime',
        'item.terracraft.worm_food.tooltip': 'Summons the Eater of Worlds (use in the Corruption)',
        'item.terracraft.bloody_spine.tooltip': 'Summons the Brain of Cthulhu (use in the Crimson)',
        'item.terracraft.suspicious_looking_eye.tooltip': 'Summons the Eye of Cthulhu (use at night)',
        'item.terracraft.demonite_ore.tooltip': 'Pulsing with dark energy',
        'item.terracraft.crimtane_ore.tooltip': 'Its veins throb',
    })
    L['npc.terracraft.name_format'] = '%s the %s'
    for npc, name in NPC_NAMES.items():
        L[f'npc.terracraft.{npc}'] = name
        L[f'entity.terracraft.{npc}'] = name
        for i, line in enumerate(NPC_DIALOGUE[npc], 1):
            L[f'npc.terracraft.{npc}.dialogue.{i}'] = line
    for mid, name in MODIFIER_NAMES.items():
        L[f'modifier.terracraft.{mid}'] = name
    for i, line in enumerate(GUIDE_HELP, 1):
        L[f'npc.terracraft.guide.help.{i}'] = line
    L.update({
        'block.terracraft.jungle_spores_plant': 'Jungle Spores',
        'block.terracraft.larva': 'Larva',
        'item.terracraft.jungle_spores.tooltip': 'Glows faintly',
        'item.terracraft.abeemination.tooltip': 'Summons the Queen Bee (use in the Jungle)',
        'item.terracraft.honey_comb.tooltip': 'Releases bees and increases life regeneration when damaged',
        'item.terracraft.bee_gun.tooltip': 'Shoots bees that will chase your enemy',
        'item.terracraft.bee_keeper.tooltip': 'Summons killer bees after striking your foe',
        'item.terracraft.bees_knees.tooltip': 'Arrows turn into bees',
        'item.terracraft.blade_of_grass.tooltip': 'Has a chance to poison enemies',
        'item.terracraft.bees_knees': "The Bee's Knees",
        'ability.terracraft.honey_comb': 'Releases bees when damaged',
        'armor_set.terracraft.jungle.bonus': '16% reduced mana cost',
        'entity.terracraft.imp': 'Fire Imp',
        'item.terracraft.guide_voodoo_doll.tooltip': 'You are a terrible person. (Throw it into Underworld lava)',
        'item.terracraft.pwnhammer.tooltip': 'Strong enough to destroy Demon Altars',
        'item.terracraft.hellstone.tooltip': 'Hot to the touch',
        'block.terracraft.hellstone.tooltip': 'Hot to the touch',
        'item.terracraft.fiery_greatsword.tooltip': "It's made out of fire!",
        'item.terracraft.flamelash.tooltip': 'Summons a homing ball of fire',
        'item.terracraft.flower_of_fire.tooltip': 'Throws balls of fire',
        'item.terracraft.demon_scythe.tooltip': 'Casts a demon scythe',
        'item.terracraft.molten_fury.tooltip': 'Lights arrows ablaze',
        'item.terracraft.hellwing_bow.tooltip': 'Arrows turn into flaming bats',
        'item.terracraft.molten_hamaxe.tooltip': 'Axe and hammer in one',
        'item.terracraft.warrior_emblem.tooltip': '15% increased melee damage',
        'item.terracraft.ranger_emblem.tooltip': '15% increased ranged damage',
        'item.terracraft.sorcerer_emblem.tooltip': '15% increased magic damage',
        'item.terracraft.summoner_emblem.tooltip': '15% increased summon damage',
        'armor_set.terracraft.molten.bonus': '17% increased melee damage',
        'block.terracraft.locked_shadow_chest': 'Shadow Chest',
        'event.terracraft.goblin_army': 'Goblin Army',
        'event.terracraft.goblin_army.start': 'A goblin army is approaching!',
        'event.terracraft.goblin_army.end': 'The Goblin Army has been defeated!',
        'npc.terracraft.mechanic.rescued': "Thank you, %s! I'd have been stuck in that dungeon forever. I'll set up shop in your town.",
        'npc.terracraft.wizard.rescued': "Ah, %s! The spell I was casting on myself went... sideways. Thank you. I'll see myself to your town.",
        'item.terracraft.clentaminator.tooltip': 'Sprays the solution in your inventory to convert biomes',
        'item.terracraft.green_solution.tooltip': 'Purifies the Corruption, Crimson and Hallow',
        'item.terracraft.blue_solution.tooltip': 'Spreads the Hallow',
        'item.terracraft.purple_solution.tooltip': 'Spreads the Corruption',
        'item.terracraft.red_solution.tooltip': 'Spreads the Crimson',
        'item.terracraft.spell_tome.tooltip': 'Can be enchanted',
        'npc.terracraft.goblin_tinkerer.rescued': "Thanks, %s! Those goblins left me tied up down here. I'll find a place in your town.",
        'npc.terracraft.goblin_tinkerer.reforged': "There you go - %s! Much better. Probably.",
        'npc.terracraft.goblin_tinkerer.cannot_reforge': "Hold a weapon, a tool or an accessory and I'll reforge it.",
        'npc.terracraft.goblin_tinkerer.too_poor': "That'll cost %s. Come back when your purse is heavier.",
        'screen.terracraft.npc.reforge': 'Reforge',
        'message.terracraft.reforged': 'Reforged: %s',
        'message.terracraft.meteor.landed': 'A meteorite has landed!',
        'modifier.terracraft.name_format': '%s %s',
        'modifier.terracraft.line.damage': '%s damage',
        'modifier.terracraft.line.speed': '%s speed',
        'modifier.terracraft.line.crit': '%s critical strike chance',
        'modifier.terracraft.line.mana_cost_reduction': '%s mana cost reduction',
        'modifier.terracraft.line.size': '%s size',
        'modifier.terracraft.line.velocity': '%s velocity',
        'modifier.terracraft.line.knockback': '%s knockback',
        'modifier.terracraft.line.defense': '%s defense',
        'modifier.terracraft.line.max_mana': '%s mana',
        'modifier.terracraft.line.move_speed': '%s movement speed',
        'modifier.terracraft.line.melee_speed': '%s melee speed',
        'ability.terracraft.free_space_gun': 'Space Gun costs no mana',
        'ability.terracraft.volatile_gelatin': 'Flings bouncing gel at nearby enemies',
        'ability.terracraft.coin_magnet': 'Increases coin pickup range',
        'ability.terracraft.lucky_coin': 'Hitting enemies will sometimes drop extra coins',
        'ability.terracraft.discount': 'Shops have lower prices',
        'item.terracraft.pirate_map.tooltip': 'Summons a Pirate Invasion',
        'item.terracraft.coin_gun.tooltip': "Uses coins for ammo\n'Higher valued coins do more damage'",
        'item.terracraft.cannonball.tooltip': 'Explodes on impact',
        'item.terracraft.lucky_coin.tooltip': 'Hitting enemies will sometimes drop extra coins',
        'item.terracraft.gold_ring.tooltip': 'Increases coin pickup range',
        'item.terracraft.discount_card.tooltip': 'Shops have lower prices',
        'item.terracraft.pirate_hat.tooltip': "Vanity",
        'item.terracraft.pirate_shirt.tooltip': "Vanity",
        'item.terracraft.pirate_pants.tooltip': "Vanity",
        'ammo.terracraft.coin': 'coins',
        'event.terracraft.frost_legion': 'Frost Legion',
        'event.terracraft.frost_legion.start': 'The Frost Legion is coming!',
        'event.terracraft.frost_legion.end': 'The Frost Legion has been defeated!',
        'item.terracraft.present.tooltip': 'Right click to open',
        'item.terracraft.snow_globe.tooltip': 'Summons the Frost Legion',
        'event.terracraft.pirate_invasion': 'Pirate Invasion',
        'event.terracraft.pirate_invasion.start': 'Pirates are approaching!',
        'event.terracraft.pirate_invasion.end': 'The pirates have been defeated!',
        'event.terracraft.pirate_invasion.dutchman': 'The Flying Dutchman has appeared!',
        'item.terracraft.gelatin_crystal.tooltip': 'Summons Queen Slime in the Hallow',
        'item.terracraft.volatile_gelatin.tooltip': 'Releases volatile gelatin that bounces off enemies',
        'item.terracraft.crystal_shard.tooltip': "'Grows in the underground Hallow'",
        'block.terracraft.gelatin_crystal_block': 'Gelatin Crystal',
        'message.terracraft.ore_blessing': 'Your world has been blessed with %s!',
        'ore.terracraft.cobalt': 'Cobalt',
        'ore.terracraft.palladium': 'Palladium',
        'ore.terracraft.mythril': 'Mythril',
        'ore.terracraft.orichalcum': 'Orichalcum',
        'ore.terracraft.adamantite': 'Adamantite',
        'ore.terracraft.titanium': 'Titanium',
        'armor_set.terracraft.cobalt.bonus': '15% increased melee speed, 20% chance not to consume ammo',
        'armor_set.terracraft.palladium.bonus': 'Greatly increases life regeneration after striking an enemy',
        'armor_set.terracraft.mythril.bonus': '10% increased critical strike chance',
        'armor_set.terracraft.orichalcum.bonus': '8% increased damage and 10% increased movement speed',
        'armor_set.terracraft.adamantite.bonus': '18% increased melee and movement speed',
        'armor_set.terracraft.titanium.bonus': '10% damage reduction and 5% increased damage',
        'item.terracraft.cobalt_repeater': 'Cobalt Repeater',
        'block.terracraft.pearlstone.tooltip': 'Shimmers with holy light',
        'entity.terracraft.the_twins': 'The Twins',
        'armor_set.terracraft.hallowed.bonus': '15% increased melee and movement speed',
        'armor_set.terracraft.crystal_assassin.bonus': '20% increased movement speed, 10% increased damage and higher jumps',
        'item.terracraft.mechanical_worm.tooltip': 'Summons the Destroyer',
        'item.terracraft.mechanical_eye.tooltip': 'Summons the Twins',
        'item.terracraft.mechanical_skull.tooltip': 'Summons Skeletron Prime',
        'item.terracraft.soul_of_might.tooltip': "'The essence of the destroyer'",
        'item.terracraft.soul_of_sight.tooltip': "'The essence of omniscient watchers'",
        'item.terracraft.soul_of_fright.tooltip': "'The essence of pure terror'",
        'item.terracraft.pickaxe_axe': 'Pickaxe Axe',
        'item.terracraft.titan_glove.tooltip': 'Increases melee knockback, enables autoswing for melee weapons',
        'item.terracraft.pixie_dust.tooltip': "'A shimmering dust'",
        'armor_set.terracraft.meteor.bonus': 'Space Gun costs 0 mana',
        'item.terracraft.goblin_battle_standard.tooltip': 'Summons a Goblin Army',
        'item.terracraft.obsidian_horseshoe.tooltip': 'Negates fall damage and grants immunity to fire blocks',
        'item.terracraft.cloud_in_a_balloon.tooltip': 'Allows the holder to double jump and increases jump height',
        'item.terracraft.fledgling_wings.tooltip': 'Allows flight and slow fall',
        'item.terracraft.angel_wings.tooltip': 'Allows flight and slow fall',
        'item.terracraft.demon_wings.tooltip': 'Allows flight and slow fall',
        'item.terracraft.leaf_wings.tooltip': 'Allows flight and slow fall',
        'item.terracraft.soul_of_light.tooltip': "'The essence of light creatures'",
        'item.terracraft.soul_of_night.tooltip': "'The essence of dark creatures'",
        'item.terracraft.soul_of_flight.tooltip': "'The essence of powerful flying creatures'",
        'item.terracraft.obsidian_shield.tooltip': 'Grants immunity to knockback and fire blocks',
        'item.terracraft.obsidian_water_walking_boots.tooltip': 'Provides the ability to walk on water and immunity to fire blocks',
        'item.terracraft.lava_waders.tooltip': 'Provides the ability to walk on water and lava; 7 seconds of lava immunity',
        'item.terracraft.mana_flower.tooltip': '8% reduced mana usage; automatically use mana potions when needed',
        'item.terracraft.space_gun.tooltip': 'Free to fire in full Meteor armor',
        'item.terracraft.meteorite.tooltip': 'Warm to the touch',
        'npc.terracraft.old_man.day': "My master cannot be summoned under the light of day.",
        'screen.terracraft.npc.curse': 'Curse',
        'message.terracraft.chest.locked.golden_key': 'It is locked. A Golden Key would open it.',
        'message.terracraft.chest.locked.shadow_key': 'It is locked. It needs a Shadow Key.',
        'item.terracraft.golden_key.tooltip': 'Opens one Locked Gold Chest',
        'item.terracraft.shadow_key.tooltip': 'Opens all Shadow Chests and Obsidian Lock Boxes',
        'item.terracraft.water_bolt.tooltip': 'Casts a slow moving bolt of water',
        'item.terracraft.aqua_scepter.tooltip': 'Sprays out a shower of water',
        'item.terracraft.book_of_skulls.tooltip': 'Shoots a skull',
        'item.terracraft.muramasa.tooltip': 'A blade forged in the depths of the Dungeon',
        'block.terracraft.locked_gold_chest': 'Locked Gold Chest',
        'block.terracraft.spikes': 'Spikes',
        'block.terracraft.dungeon_bookshelf': 'Dungeon Bookcase',
        'block.terracraft.blue_brick': 'Blue Brick',
        'block.terracraft.green_brick': 'Green Brick',
        'block.terracraft.pink_brick': 'Pink Brick',
        'npc.terracraft.nurse.healthy': "You look perfectly healthy. Come back when something's broken.",
        'npc.terracraft.nurse.too_poor': "Healing you would cost %s. I don't work for free, you know.",
        'npc.terracraft.nurse.healed': "All better! That'll be %s.",
        'message.terracraft.npc.arrived': '%s has arrived!',
        'message.terracraft.npc.slain': '%s was slain...',
        'message.terracraft.housing.valid': 'This housing is suitable (%s blocks of space).',
        'message.terracraft.housing.free': 'Nobody lives here yet.',
        'message.terracraft.housing.occupied': 'This is where %s lives.',
        'message.terracraft.housing.not_enclosed': 'This housing is not valid: it is not enclosed (or too large).',
        'message.terracraft.housing.too_small': 'This housing is not valid: it is too small.',
        'message.terracraft.housing.no_door': 'This housing is not valid: it is missing a door.',
        'message.terracraft.housing.no_light': 'This housing is not valid: it is missing a light source.',
        'message.terracraft.housing.no_comfort': 'This housing is not valid: it is missing a comfort item (bed or chair).',
        'message.terracraft.housing.no_table': 'This housing is not valid: it is missing a flat surface item (table or work bench).',
        'message.terracraft.housing.not_air': 'Click inside a room to check it.',
        'message.terracraft.shop.too_poor': "You don't have enough money.",
        'message.terracraft.shop.cannot_sell': "Nobody wants to buy that.",
        'message.terracraft.shop.sold': 'Sold for %s',
        'screen.terracraft.npc.shop': 'Shop',
        'screen.terracraft.npc.heal': 'Heal',
        'screen.terracraft.npc.help': 'Help',
        'screen.terracraft.npc.close': 'Close',
        'screen.terracraft.npc.back': 'Back',
        'screen.terracraft.npc.sell': 'Sell held item',
        'screen.terracraft.npc.savings': 'Savings: ',
        'screen.terracraft.npc.price': 'Price: ',
        'item.terracraft.purification_powder.tooltip': 'Cleanses the Corruption and the Crimson',
        'item.terracraft.housing_query.tooltip': 'Right-click inside a room to check whether an NPC can live there',
    })
    stage6_lang(L)
    stage7_lang(L)
    stage8_lang(L)
    for mob, (name, _) in MOBS.items():
        L[f'entity.terracraft.{mob}'] = name
    path = os.path.join(RES, 'assets/terracraft/lang/en_us.json')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(dict(sorted(L.items())), f, indent=2, ensure_ascii=False)
        f.write('\n')
    return len(L)


# --- Enemies -------------------------------------------------------------------------------------
MOBS = {
    # id: (English name, [(item, min, max, chance)])
    'green_slime': ('Green Slime', [('gel', 1, 2, 1.0)]),
    'blue_slime': ('Blue Slime', [('gel', 1, 2, 1.0)]),
    'red_slime': ('Red Slime', [('gel', 1, 3, 1.0)]),
    'purple_slime': ('Purple Slime', [('gel', 1, 3, 1.0)]),
    'yellow_slime': ('Yellow Slime', [('gel', 2, 4, 1.0)]),
    'black_slime': ('Black Slime', [('gel', 2, 4, 1.0)]),
    'baby_slime': ('Baby Slime', [('gel', 1, 1, 0.5)]),
    'mother_slime': ('Mother Slime', [('gel', 2, 5, 1.0)]),
    'zombie': ('Zombie', [('shackle', 1, 1, 0.02)]),
    'demon_eye': ('Demon Eye', [('lens', 1, 1, 0.33)]),
    'skeleton': ('Skeleton', [('minecraft:bone', 1, 2, 0.5)]),
    'cave_bat': ('Cave Bat', []),
    'servant_of_cthulhu': ('Servant of Cthulhu', []),
    'devourer': ('Devourer', []),
    'giant_worm': ('Giant Worm', []),
    'eater_of_worlds': ('Eater of Worlds', []),
    'blood_zombie': ('Blood Zombie', [('vertebra', 1, 1, 0.1)]),
    'drippler': ('Drippler', [('lens', 1, 2, 0.5)]),
    'brain_of_cthulhu': ('Brain of Cthulhu', []),
    'creeper': ('Creeper', []),
    'eater_of_souls': ('Eater of Souls', [('rotten_chunk', 1, 1, 0.33)]),
    'crimera': ('Crimera', [('vertebra', 1, 1, 0.33)]),
    'face_monster': ('Face Monster', [('vertebra', 1, 1, 0.33)]),
    'blood_crawler': ('Blood Crawler', [('vertebra', 1, 1, 0.25)]),
    'angry_bones': ('Angry Bones', [('golden_key', 1, 1, 0.025), ('minecraft:bone', 1, 3, 0.5)]),
    'dark_caster': ('Dark Caster', [('golden_key', 1, 1, 0.025), ('minecraft:bone', 1, 2, 0.5)]),
    'cursed_skull': ('Cursed Skull', [('golden_key', 1, 1, 0.025), ('minecraft:bone', 1, 2, 0.5)]),
    'dungeon_slime': ('Dungeon Slime', [('golden_key', 1, 1, 1.0), ('gel', 2, 5, 1.0)]),
    'dungeon_guardian': ('Dungeon Guardian', []),
    'skeletron': ('Skeletron', [('book_of_skulls', 1, 1, 0.143)]),
    'skeletron_hand': ('Skeletron Hand', []),
    'jungle_slime': ('Jungle Slime', [('gel', 2, 5, 1.0)]),
    'jungle_bat': ('Jungle Bat', []),
    'hornet': ('Hornet', [('stinger', 1, 1, 0.5)]),
    'man_eater': ('Man Eater', [('minecraft:vine', 1, 2, 0.5)]),
    'snatcher': ('Snatcher', [('minecraft:vine', 1, 1, 0.3)]),
    'bee': ('Bee', []),
    'queen_bee': ('Queen Bee', None),
    'imp': ('Fire Imp', []),
    'demon': ('Demon', [('demon_scythe', 1, 1, 0.033)]),
    'voodoo_demon': ('Voodoo Demon', [('guide_voodoo_doll', 1, 1, 1.0), ('demon_scythe', 1, 1, 0.033)]),
    'lava_slime': ('Lava Slime', [('gel', 2, 4, 1.0)]),
    'hellbat': ('Hellbat', []),
    'bone_serpent': ('Bone Serpent', []),
    'wall_of_flesh': ('Wall of Flesh', None),
    'wall_of_flesh_eye': ('Wall of Flesh', []),
    'the_hungry': ('The Hungry', []),
    'goblin_peon': ('Goblin Peon', [('tattered_cloth', 1, 1, 0.25)]),
    'goblin_thief': ('Goblin Thief', [('tattered_cloth', 1, 1, 0.25)]),
    'goblin_warrior': ('Goblin Warrior', [('tattered_cloth', 1, 1, 0.25)]),
    'goblin_sorcerer': ('Goblin Sorcerer', [('tattered_cloth', 1, 1, 0.25)]),
    'goblin_archer': ('Goblin Archer', [('tattered_cloth', 1, 1, 0.25)]),
    'meteor_head': ('Meteor Head', []),
    'pixie': ('Pixie', [('pixie_dust', 1, 3, 1.0)]),
    'unicorn': ('Unicorn', [('unicorn_horn', 1, 2, 1.0)]),
    'gastropod': ('Gastropod', [('gel', 1, 3, 1.0)]),
    'illuminant_bat': ('Illuminant Bat', []),
    'illuminant_slime': ('Illuminant Slime', [('gel', 2, 5, 1.0)]),
    'chaos_elemental': ('Chaos Elemental', []),
    'corruptor': ('Corruptor', [('rotten_chunk', 1, 2, 0.5)]),
    'slimer': ('Slimer', [('gel', 2, 4, 1.0)]),
    'crimslime': ('Crimslime', [('gel', 2, 5, 1.0)]),
    'herpling': ('Herpling', [('vertebra', 1, 2, 0.5)]),
    'floaty_gross': ('Floaty Gross', [('vertebra', 1, 2, 0.5)]),
    'wraith': ('Wraith', []),
    'possessed_armor': ('Possessed Armor', []),
    'werewolf': ('Werewolf', []),
    'wyvern': ('Wyvern', [('soul_of_flight', 4, 6, 1.0)]),
    'armored_skeleton': ('Armored Skeleton', [('minecraft:bone', 1, 3, 0.5)]),
    'giant_bat': ('Giant Bat', []),
    'mimic': ('Mimic', [('titan_glove', 1, 1, 0.33)]),
    'retinazer': ('Retinazer', []),
    'spazmatism': ('Spazmatism', []),
    'destroyer': ('The Destroyer', []),
    'probe': ('Probe', []),
    'skeletron_prime': ('Skeletron Prime', []),
    'prime_cannon': ('Prime Cannon', []),
    'prime_saw': ('Prime Saw', []),
    'prime_vice': ('Prime Vice', []),
    'prime_laser': ('Prime Laser', []),
    'queen_slime': ('Queen Slime', []),
    'mister_stabby': ('Mister Stabby', [('minecraft:snowball', 2, 5, 0.5)]),
    'snowman_gangsta': ('Snowman Gangsta', [('minecraft:snowball', 2, 5, 0.5)]),
    'snow_balla': ('Snow Balla', [('minecraft:snowball', 2, 5, 0.5)]),
    'pirate_deckhand': ('Pirate Deckhand', [('cutlass', 1, 1, 0.005), ('gold_ring', 1, 1, 0.004), ('lucky_coin', 1, 1, 0.004), ('discount_card', 1, 1, 0.004), ('coin_gun', 1, 1, 0.0005)]),
    'pirate_corsair': ('Pirate Corsair', [('cutlass', 1, 1, 0.005), ('gold_ring', 1, 1, 0.004), ('lucky_coin', 1, 1, 0.004), ('discount_card', 1, 1, 0.004), ('coin_gun', 1, 1, 0.0005)]),
    'pirate_crossbower': ('Pirate Crossbower', [('cutlass', 1, 1, 0.005), ('gold_ring', 1, 1, 0.004), ('lucky_coin', 1, 1, 0.004), ('discount_card', 1, 1, 0.004), ('coin_gun', 1, 1, 0.0005)]),
    'pirate_deadeye': ('Pirate Deadeye', [('cutlass', 1, 1, 0.005), ('gold_ring', 1, 1, 0.004), ('lucky_coin', 1, 1, 0.004), ('discount_card', 1, 1, 0.004), ('coin_gun', 1, 1, 0.0005)]),
    'pirate_captain': ('Pirate Captain', [('cutlass', 1, 1, 0.02), ('gold_ring', 1, 1, 0.016), ('lucky_coin', 1, 1, 0.016), ('discount_card', 1, 1, 0.016), ('coin_gun', 1, 1, 0.002)]),
    'parrot': ('Parrot', [('cutlass', 1, 1, 0.005), ('gold_ring', 1, 1, 0.004), ('lucky_coin', 1, 1, 0.004), ('discount_card', 1, 1, 0.004), ('coin_gun', 1, 1, 0.0005)]),
    'flying_dutchman': ('Flying Dutchman', [('cutlass', 1, 1, 0.25), ('gold_ring', 1, 1, 0.25), ('lucky_coin', 1, 1, 0.25), ('discount_card', 1, 1, 0.25), ('coin_gun', 1, 1, 0.05), ('gold_coin', 3, 8, 1.0)]),
    'crystal_slime': ('Crystal Slime', [('gel', 1, 3, 1.0)]),
    'bouncy_slime': ('Bouncy Slime', [('gel', 1, 3, 1.0)]),
    'heavenly_slime': ('Heavenly Slime', [('gel', 1, 3, 1.0)]),
    'king_slime': ('King Slime', [('gel', 40, 80, 1.0), ('slime_crown', 1, 1, 0.1)]),
    'eye_of_cthulhu': ('Eye of Cthulhu', [('lens', 3, 6, 1.0)]),
}

OVERWORLD_LAND = {'exclude_biomes': ['#minecraft:is_ocean', '#minecraft:is_river'], 'exclude_ground': ['#terracraft:evil/all', '#terracraft:dungeon/bricks', '#terracraft:hallow/all']}
NOT_EVIL = {'exclude_ground': ['#terracraft:evil/all', '#terracraft:dungeon/bricks', '#terracraft:jungle/ground', '#terracraft:hallow/all']}
JUNGLE = {'biomes': ['#minecraft:is_jungle'], 'exclude_ground': ['#terracraft:evil/all', '#terracraft:dungeon/bricks']}
JUNGLE_DEEP = {'ground': ['#terracraft:jungle/ground']}
DUNGEON = {'ground': ['#terracraft:dungeon/bricks'], 'condition': 'boss_skeletron_defeated'}
CORRUPT = {'ground': ['#terracraft:evil/corruption']}
CRIMSON = {'ground': ['#terracraft:evil/crimson']}
PRE_HM = '!hardmode_active'
HM = 'hardmode_active'
HALLOW = {'ground': ['#terracraft:hallow/all'], 'condition': HM}
CORRUPT_HM = {'ground': ['#terracraft:evil/corruption'], 'condition': HM}
CRIMSON_HM = {'ground': ['#terracraft:evil/crimson'], 'condition': HM}
SPAWNS = {
    'hallow': [
        dict(entity='pixie', weight=10, time='day', layers=['surface'], placement='air', **HALLOW),
        dict(entity='unicorn', weight=5, time='day', layers=['surface'], **HALLOW),
        dict(entity='gastropod', weight=8, time='night', layers=['surface'], placement='air', **HALLOW),
        dict(entity='pixie', weight=3, time='night', layers=['surface'], placement='air', **HALLOW),
        dict(entity='illuminant_slime', weight=8, layers=['underground', 'cavern'], **HALLOW),
        dict(entity='illuminant_bat', weight=8, layers=['underground', 'cavern'], placement='air', **HALLOW),
        dict(entity='chaos_elemental', weight=4, layers=['underground', 'cavern'], **HALLOW),
    ],
    'corruption_hardmode': [
        dict(entity='corruptor', weight=6, layers=['surface', 'underground', 'cavern'], placement='air', **CORRUPT_HM),
        dict(entity='slimer', weight=6, layers=['underground', 'cavern'], placement='air', **CORRUPT_HM),
    ],
    'crimson_hardmode': [
        dict(entity='herpling', weight=6, layers=['surface', 'underground', 'cavern'], **CRIMSON_HM),
        dict(entity='crimslime', weight=6, layers=['surface', 'underground', 'cavern'], **CRIMSON_HM),
        dict(entity='floaty_gross', weight=4, time='night', layers=['surface'], placement='air', **CRIMSON_HM),
    ],
    'hardmode': [
        dict(entity='wraith', weight=4, time='night', layers=['surface'], placement='air', condition=HM, **NOT_EVIL),
        dict(entity='possessed_armor', weight=5, time='night', layers=['surface'], condition=HM, **OVERWORLD_LAND),
        dict(entity='werewolf', weight=3, time='night', layers=['surface'], condition=HM, **OVERWORLD_LAND),
        dict(entity='wyvern', weight=2, layers=['space'], placement='air', condition=HM),
        dict(entity='armored_skeleton', weight=6, layers=['cavern'], condition=HM, **NOT_EVIL),
        dict(entity='giant_bat', weight=6, layers=['cavern'], placement='air', condition=HM, **NOT_EVIL),
        dict(entity='mimic', weight=1, layers=['cavern'], condition=HM),
    ],
    'surface_day': [
        dict(entity='green_slime', weight=10, time='day', layers=['surface'], exclude_biomes=['#minecraft:is_ocean', '#minecraft:is_river', '#minecraft:is_jungle'],
             exclude_ground=OVERWORLD_LAND['exclude_ground']),
        dict(entity='blue_slime', weight=8, time='day', layers=['surface'], exclude_biomes=['#minecraft:is_ocean', '#minecraft:is_river', '#minecraft:is_jungle'],
             exclude_ground=OVERWORLD_LAND['exclude_ground']),
        dict(entity='purple_slime', weight=1, time='day', layers=['surface'], **OVERWORLD_LAND),
    ],
    'surface_night': [
        dict(entity='zombie', weight=10, time='night', layers=['surface'], group=[1, 2], **OVERWORLD_LAND),
        dict(entity='demon_eye', weight=6, time='night', layers=['surface'], placement='air', sky=True, **NOT_EVIL),
        dict(entity='blue_slime', weight=2, time='night', layers=['surface'], **OVERWORLD_LAND),
    ],
    'underground': [
        dict(entity='red_slime', weight=8, layers=['underground'], **NOT_EVIL),
        dict(entity='yellow_slime', weight=3, layers=['underground'], **NOT_EVIL),
        dict(entity='blue_slime', weight=3, layers=['underground'], **NOT_EVIL),
        dict(entity='cave_bat', weight=6, layers=['underground'], placement='air', **NOT_EVIL),
        dict(entity='skeleton', weight=5, layers=['underground'], **NOT_EVIL),
        dict(entity='giant_worm', weight=3, layers=['underground', 'cavern'], **NOT_EVIL),
    ],
    'cavern': [
        dict(entity='black_slime', weight=6, layers=['cavern'], **NOT_EVIL),
        dict(entity='yellow_slime', weight=5, layers=['cavern'], **NOT_EVIL),
        dict(entity='red_slime', weight=3, layers=['cavern'], **NOT_EVIL),
        dict(entity='mother_slime', weight=2, layers=['cavern'], condition=PRE_HM, **NOT_EVIL),
        dict(entity='cave_bat', weight=6, layers=['cavern'], placement='air', **NOT_EVIL),
        dict(entity='skeleton', weight=8, layers=['cavern'], **NOT_EVIL),
    ],
    'blood_moon': [
        dict(entity='blood_zombie', weight=8, time='night', layers=['surface'], event='blood_moon', **OVERWORLD_LAND),
        dict(entity='drippler', weight=5, time='night', layers=['surface'], placement='air', event='blood_moon', **NOT_EVIL),
        dict(entity='zombie', weight=6, time='night', layers=['surface'], group=[1, 3], event='blood_moon', **OVERWORLD_LAND),
    ],
    'corruption': [
        dict(entity='eater_of_souls', weight=10, layers=['surface', 'underground', 'cavern'], placement='air', **CORRUPT),
        dict(entity='devourer', weight=3, layers=['surface', 'underground', 'cavern'], **CORRUPT),
    ],
    'jungle': [
        dict(entity='jungle_slime', weight=10, time='day', layers=['surface'], **JUNGLE),
        dict(entity='snatcher', weight=4, layers=['surface'], **JUNGLE),
        dict(entity='jungle_slime', weight=6, layers=['underground', 'cavern'], **JUNGLE_DEEP),
        dict(entity='hornet', weight=10, layers=['underground', 'cavern'], placement='air', **JUNGLE_DEEP),
        dict(entity='man_eater', weight=5, layers=['underground', 'cavern'], **JUNGLE_DEEP),
        dict(entity='jungle_bat', weight=6, layers=['underground', 'cavern'], placement='air', **JUNGLE_DEEP),
    ],
    'underworld': [
        dict(entity='imp', weight=8, layers=['underworld']),
        dict(entity='lava_slime', weight=8, layers=['underworld']),
        dict(entity='demon', weight=6, layers=['underworld'], placement='air'),
        dict(entity='voodoo_demon', weight=2, layers=['underworld'], placement='air'),
        dict(entity='hellbat', weight=6, layers=['underworld'], placement='air'),
        dict(entity='bone_serpent', weight=2, layers=['underworld']),
    ],
    'frost_legion': [
        dict(entity='mister_stabby', weight=10, layers=['surface'], group=[1, 3], event='frost_legion'),
        dict(entity='snowman_gangsta', weight=7, layers=['surface'], group=[1, 2], event='frost_legion'),
        dict(entity='snow_balla', weight=7, layers=['surface'], group=[1, 2], event='frost_legion'),
    ],
    'pirate_invasion': [
        dict(entity='pirate_deckhand', weight=10, layers=['surface'], group=[1, 3], event='pirate_invasion'),
        dict(entity='pirate_corsair', weight=7, layers=['surface'], group=[1, 2], event='pirate_invasion'),
        dict(entity='pirate_crossbower', weight=5, layers=['surface'], event='pirate_invasion'),
        dict(entity='pirate_deadeye', weight=5, layers=['surface'], event='pirate_invasion'),
        dict(entity='parrot', weight=6, layers=['surface'], placement='air', event='pirate_invasion'),
        dict(entity='pirate_captain', weight=1, layers=['surface'], event='pirate_invasion'),
    ],
    'goblin_army': [
        dict(entity='goblin_peon', weight=10, layers=['surface'], group=[1, 3], event='goblin_army'),
        dict(entity='goblin_thief', weight=7, layers=['surface'], group=[1, 2], event='goblin_army'),
        dict(entity='goblin_warrior', weight=6, layers=['surface'], event='goblin_army'),
        dict(entity='goblin_archer', weight=4, layers=['surface'], event='goblin_army'),
        dict(entity='goblin_sorcerer', weight=3, layers=['surface'], event='goblin_army'),
    ],
    'dungeon': [
        dict(entity='angry_bones', weight=10, layers=['surface', 'underground', 'cavern'], group=[1, 2], **DUNGEON),
        dict(entity='dark_caster', weight=4, layers=['surface', 'underground', 'cavern'], **DUNGEON),
        dict(entity='cursed_skull', weight=5, layers=['surface', 'underground', 'cavern'], placement='air', **DUNGEON),
        dict(entity='dungeon_slime', weight=1, layers=['surface', 'underground', 'cavern'], **DUNGEON),
    ],
    'crimson': [
        dict(entity='crimera', weight=10, layers=['surface', 'underground', 'cavern'], placement='air', **CRIMSON),
        dict(entity='face_monster', weight=4, layers=['surface', 'underground', 'cavern'], **CRIMSON),
        dict(entity='blood_crawler', weight=4, layers=['underground', 'cavern'], **CRIMSON),
    ],
}


def mobs():
    for mob, (_, drops) in MOBS.items():
        if drops is None:
            continue   # hand-written loot table
        pools = []
        for item, lo, hi, chance in drops:
            entry = {'type': 'minecraft:item', 'name': item if ':' in item else t(item)}
            if hi > 1 or lo > 1:
                entry['functions'] = [{'function': 'minecraft:set_count',
                                       'count': {'type': 'minecraft:uniform', 'min': lo, 'max': hi}}]
            pool = {'rolls': 1, 'entries': [entry]}
            if chance < 1.0:
                pool['conditions'] = [{'condition': 'minecraft:random_chance', 'chance': chance}]
            pools.append(pool)
        write(f'{NS}/loot_table/entities/{mob}.json', {'type': 'minecraft:entity', 'pools': pools})
    for name, rules in SPAWNS.items():
        out = []
        for rule in rules:
            rule = dict(rule)
            rule['entity'] = t(rule['entity'])
            out.append(rule)
        write(f'{NS}/terracraft/spawns/{name}.json', {'spawns': out})


# --- World generation ------------------------------------------------------------------------------
# Terraria ore pairs: (pair, primary, secondary, count per chunk, vein size, min y, max y)
ORE_PAIRS = [
    ('copper_tin', 'copper', 'tin', 16, 10, -16, 112),
    ('iron_lead', 'iron', 'lead', 12, 9, -40, 72),
    ('silver_tungsten', 'silver', 'tungsten', 9, 8, -56, 40),
    ('gold_platinum', 'gold', 'platinum', 7, 8, -64, 16),
]
VANILLA_ORE_FEATURES = ['minecraft:ore_copper', 'minecraft:ore_copper_large', 'minecraft:ore_iron_middle',
                        'minecraft:ore_iron_small', 'minecraft:ore_iron_upper', 'minecraft:ore_gold',
                        'minecraft:ore_gold_lower', 'minecraft:ore_gold_extra']


def ore_block(metal, deep):
    if metal in ('copper', 'iron', 'gold'):
        return f'minecraft:{"deepslate_" if deep else ""}{metal}_ore'
    return t(f'{"deepslate_" if deep else ""}{metal}_ore')


def floor_scan(min_y, max_y):
    return [
        {'type': 'minecraft:in_square'},
        {'type': 'minecraft:height_range', 'height': {'type': 'minecraft:uniform',
                                                      'min_inclusive': {'absolute': min_y}, 'max_inclusive': {'absolute': max_y}}},
        {'type': 'minecraft:environment_scan', 'direction_of_search': 'down', 'max_steps': 16,
         'target_condition': {'type': 'minecraft:solid'},
         'allowed_search_condition': {'type': 'minecraft:matching_blocks', 'blocks': 'minecraft:air'}},
        {'type': 'minecraft:random_offset', 'xz_spread': 0, 'y_spread': 1},
        {'type': 'minecraft:biome'},
    ]


def entry(item, weight=1, lo=1, hi=1):
    e = {'type': 'minecraft:item', 'name': item if ':' in item else t(item), 'weight': weight}
    if hi > 1:
        e['functions'] = [{'function': 'minecraft:set_count', 'count': {'type': 'minecraft:uniform', 'min': lo, 'max': hi}}]
    return e


def chest_table(primary, bars, potions):
    return {'type': 'minecraft:chest', 'pools': [
        {'rolls': 1, 'entries': [entry(i) for i in primary]},
        {'rolls': {'type': 'minecraft:uniform', 'min': 1, 'max': 2}, 'entries': [entry(b, 1, 3, 10) for b in bars]},
        {'rolls': {'type': 'minecraft:uniform', 'min': 1, 'max': 2}, 'entries': [entry(p, 1, 1, 2) for p in potions]},
        {'rolls': 1, 'entries': [entry('lesser_healing_potion', 3, 3, 5), entry('shuriken', 2, 25, 50),
                                 entry('throwing_knife', 2, 25, 50), entry('minecraft:arrow', 2, 25, 50),
                                 entry('flaming_arrow', 1, 25, 50), entry('minecraft:torch', 3, 10, 20)]},
        {'rolls': 1, 'entries': [entry('silver_coin', 1, 1, 6)]},
    ]}


def worldgen():
    features = []
    for pair, first, second, count, size, lo, hi in ORE_PAIRS:
        for secondary, metal in ((False, first), (True, second)):
            name = f'ore_{metal}'
            write(f'{NS}/worldgen/configured_feature/{name}.json', {'type': t('paired_ore'), 'config': {
                'pair': pair, 'secondary': secondary, 'ore': {
                    'size': size, 'discard_chance_on_air_exposure': 0.0, 'targets': [
                        {'target': {'predicate_type': 'minecraft:tag_match', 'tag': 'minecraft:stone_ore_replaceables'},
                         'state': {'Name': ore_block(metal, False)}},
                        {'target': {'predicate_type': 'minecraft:tag_match', 'tag': 'minecraft:deepslate_ore_replaceables'},
                         'state': {'Name': ore_block(metal, True)}}]}}})
            write(f'{NS}/worldgen/placed_feature/{name}.json', {'feature': t(name), 'placement': [
                {'type': 'minecraft:count', 'count': count}, {'type': 'minecraft:in_square'},
                {'type': 'minecraft:height_range', 'height': {'type': 'minecraft:trapezoid',
                                                              'min_inclusive': {'absolute': lo}, 'max_inclusive': {'absolute': hi}}},
                {'type': 'minecraft:biome'}]})
            features.append(t(name))
    write(f'{NS}/neoforge/biome_modifier/remove_vanilla_ores.json', {
        'type': 'neoforge:remove_features', 'biomes': '#minecraft:is_overworld',
        'features': VANILLA_ORE_FEATURES, 'steps': ['underground_ores']})
    write(f'{NS}/neoforge/biome_modifier/terraria_ores.json', {
        'type': 'neoforge:add_features', 'biomes': '#minecraft:is_overworld', 'features': features, 'step': 'underground_ores'})

    # Life Crystals on cave floors below y=40
    write(f'{NS}/worldgen/configured_feature/life_crystal.json', {'type': 'minecraft:simple_block', 'config': {
        'to_place': {'type': 'minecraft:simple_state_provider', 'state': {'Name': t('life_crystal_block')}}}})
    write(f'{NS}/worldgen/placed_feature/life_crystal.json', {'feature': t('life_crystal'), 'placement': [
        {'type': 'minecraft:count', 'count': 3}] + floor_scan(-56, 40)})
    # Chests: wooden (shallow caves) and gold-tier (deep caves)
    chests = {
        'wooden_chest': ('chests/surface_cave', 2, 0, 60),
        'underground_chest': ('chests/underground', 1, -60, 0),
    }
    for name, (table, rarity, lo, hi) in chests.items():
        write(f'{NS}/worldgen/configured_feature/{name}.json', {'type': t('loot_chest'), 'config': {
            'chest': {'Name': 'minecraft:chest', 'Properties': {'facing': 'north', 'type': 'single', 'waterlogged': 'false'}},
            'loot_table': t(table)}})
        write(f'{NS}/worldgen/placed_feature/{name}.json', {'feature': t(name), 'placement': [
            {'type': 'minecraft:rarity_filter', 'chance': rarity}] + floor_scan(lo, hi)})
    write(f'{NS}/neoforge/biome_modifier/terraria_cave_loot.json', {
        'type': 'neoforge:add_features', 'biomes': '#minecraft:is_overworld',
        'features': [t('life_crystal'), t('wooden_chest'), t('underground_chest')], 'step': 'underground_decoration'})

    write(f'{NS}/loot_table/chests/surface_cave.json', chest_table(
        ['wooden_boomerang', 'aglet', 'wand_of_sparking', 'wooden_bow', 'copper_shortsword'],
        ['minecraft:copper_ingot', 'tin_bar', 'minecraft:iron_ingot', 'lead_bar'],
        ['ironskin_potion', 'swiftness_potion', 'mining_potion', 'archery_potion']))
    write(f'{NS}/loot_table/chests/underground.json', chest_table(
        ['band_of_regeneration', 'cloud_in_a_bottle', 'hermes_boots', 'magic_missile', 'flintlock_pistol', 'fledgling_wings'],
        ['silver_bar', 'tungsten_bar', 'minecraft:gold_ingot', 'platinum_bar'],
        ['regeneration_potion', 'obsidian_skin_potion', 'water_walking_potion', 'magic_power_potion', 'mana_regeneration_potion']))


# --- Town NPCs -------------------------------------------------------------------------------------
NPC_NAMES = {'guide': 'Guide', 'merchant': 'Merchant', 'nurse': 'Nurse', 'demolitionist': 'Demolitionist',
             'arms_dealer': 'Arms Dealer', 'dryad': 'Dryad', 'old_man': 'Old Man', 'clothier': 'Clothier', 'bound_goblin': 'Bound Goblin',
             'goblin_tinkerer': 'Goblin Tinkerer', 'bound_wizard': 'Bound Wizard', 'wizard': 'Wizard', 'steampunker': 'Steampunker',
             'witch_doctor': 'Witch Doctor', 'bound_mechanic': 'Bound Mechanic', 'mechanic': 'Mechanic', 'pirate': 'Pirate'}
NPC_DIALOGUE = {
    'guide': [
        "Hello, %s. If you're new here, chop some trees and build a shelter before nightfall.",
        "Gel from slimes and a little wood make torches. You'll want plenty of light underground.",
        "Stars fall from the sky at night. Collect them before sunrise - they're worth more than they look.",
        "Life Crystals glow deep in the caverns. Each one makes you a little sturdier.",
        "Build more houses and more people will come to live here. A home needs walls, a door, a light, a chair and a table.",
        "Something watches from the dark... They say enough lenses could call it down on you.",
        "A Work Bench is the first thing every builder needs. Press V near one to see what you can make.",
        "I keep hearing stories about an enormous slime wearing a crown. Ridiculous, surely.",
    ],
    'merchant': [
        "Welcome, %s! Coin talks, and I'm a very good listener.",
        "Torches, potions, arrows - the essentials of a long and happy life. Mostly long.",
        "Everything you carry is worth something to somebody. Usually me.",
        "Prices are fair, friend. Fair to me, at least.",
        "Out exploring after dark? Then you'll want to buy something. Trust me.",
        "Bring me your odds and ends - I'll pay a fifth of what they're worth!",
    ],
    'nurse': [
        "Hold still, %s. This might sting. A lot.",
        "You look terrible. That's not an insult, it's a diagnosis.",
        "I patch people up. I don't do miracles - but I'm close.",
        "Please stop running headfirst into slimes.",
        "My services aren't free, but your life is worth the coin. Probably.",
        "Back again? You must enjoy my company. Or bleeding.",
    ],
    'demolitionist': [
        "Ah, %s! Nothing like the smell of fresh powder in the morning.",
        "Rock in your way? Not for long, it isn't.",
        "Mind your step. And your eyebrows.",
        "Every problem's a nail, and I've got the biggest hammer there is.",
        "Underground's full of riches. You just have to ask it nicely. Loudly.",
        "Careful with the merchandise. Very careful.",
    ],
}
NPC_DIALOGUE['arms_dealer'] = [
    "Hey %s. Looking to make things go bang? You've come to the right man.",
    "Guns don't care how strong your arms are. That's what I like about them.",
    "Musket balls are cheap. Missing is expensive.",
    "Keep your powder dry and your eyes open.",
    "Every gun I sell comes with a promise: it will shoot something.",
    "The Nurse keeps asking me to stop. I keep asking her to buy something.",
]
NPC_DIALOGUE['dryad'] = [
    "Greetings, %s. The land is restless - can you feel it?",
    "Something vile spreads beneath this world. My powder can push it back, a little at a time.",
    "Every tree you plant is a promise to the world.",
    "The Corruption and the Crimson are wounds. Wounds can be healed.",
    "I have watched these forests for longer than you would believe.",
    "Be gentle with the land, and it will be gentle with you.",
]
NPC_DIALOGUE['old_man'] = [
    "I cannot let you enter until you free me of my curse.",
    "Come back at night if you wish to enter.",
    "My master cannot be summoned under the light of day.",
    "You are far too weak to defeat my curse. Come back when you aren't so worthless.",
    "You pathetic fool. You cannot hope to face my master as you are now.",
]
NPC_DIALOGUE['clothier'] = [
    "Thank you for releasing me, %s. I feel like a new man.",
    "I remember nothing of the time I spent cursed... only the cold.",
    "A man is judged by his clothes. And by whether he's a giant floating skull.",
    "If you find any silk, bring it to me. I can work wonders with it.",
    "The Dungeon is yours to explore now. Mind the spikes.",
    "Sometimes I still hear the bones rattling down there.",
]
NPC_DIALOGUE['bound_goblin'] = ["Thank you for freeing me, %s!"]
NPC_DIALOGUE['goblin_tinkerer'] = [
    "Hey, %s. Need something fixed? Or improved? I do both. For a price.",
    "Goblins are great at two things: tinkering and making a mess. I specialise in the first one.",
    "Bring me your gear and some coin and I'll give it a new edge. No promises it's a better one.",
    "A Tinkerer's Workshop lets you combine accessories. Two for the price of one slot!",
    "My old army keeps coming back. Don't worry, I don't take it personally.",
    "Rocket science? I'm more of a rocket boots kind of goblin.",
]
NPC_DIALOGUE['bound_wizard'] = ["Thank you for freeing me, %s!"]
NPC_DIALOGUE['wizard'] = [
    "Greetings, %s. Would you care for a lesson in the arcane? No? Then perhaps a potion.",
    "I once turned a slime into a slightly larger slime. It was the proudest day of my life.",
    "A Spell Tome is the beginning of every great magic weapon. The ending, too, if you're careless.",
    "The spirits of light and dark walk the land now. Mind the Hallow - it is beautiful, and it bites.",
    "Mana is like soup. You should always have more than you think you need.",
    "Do you hear that ringing? No? Good. Neither do I. Probably.",
]
NPC_DIALOGUE['steampunker'] = [
    "Ello, %s! Fancy a bit of gadgetry? I've got just the thing for those pesky biomes.",
    "My Clentaminator sprays solutions far and wide. Green cleans, the others... don't.",
    "You beat one of those mechanical monstrosities? Smashing! I'd love a look at the scrap.",
    "Brass, steam and a bit of elbow grease. That's all a girl needs.",
    "Mind the purple solution. It gets everywhere, and I do mean everywhere.",
    "Tick, tock, tick, tock. Everything runs on gears if you look closely enough.",
]
NPC_DIALOGUE['bound_mechanic'] = ["Thank you for freeing me, %s!"]
NPC_DIALOGUE['mechanic'] = [
    "Wire is cheaper than you'd think, %s. Want to build something that goes click?",
    "That old man had me locked up down there for ages. I spent the time rewiring his traps.",
    "Levers, pressure plates, repeaters... give me a pile of redstone and an afternoon.",
    "If it moves and shouldn't, that's a job for the wrench. If it doesn't and should, that's a job for wire.",
    "My brother tinkers with goblin gadgets. I prefer things that actually stay in one piece.",
    "A good trap starts with a good pressure plate. Trust me, I've stepped on enough of them.",
]
NPC_DIALOGUE['pirate'] = [
    "Arrr, %s! Ye look like a landlubber who could use a cannonball or two.",
    "Me ship? Sunk, matey. Ye scallywags sent it to Davy Jones' locker. No hard feelings.",
    "That ghost ship still haunts me dreams. The Dutchman never pays its crew, ye know.",
    "Treasure be not always gold. Sometimes it be a fine hat. Want one?",
    "Keep yer coins close and yer Lucky Coin closer, I always say.",
    "Yo ho! What be the difference between a pirate and a merchant? About three cannons.",
]
NPC_DIALOGUE['witch_doctor'] = [
    "The jungle speaks, %s. Today it says you look tired.",
    "The Queen of the hive is gone. Her stingers are mine now. Perhaps yours, for a price.",
    "When the world turns dark and strange, leaves can carry you to the sky. Come back at night.",
    "Do not touch the masks. They remember who touched them.",
    "Bees know many things. Mostly about flowers. Still, many things.",
    "Your aura is... green. That is either very good or very bad.",
]
GUIDE_HELP = [
    "Press V to open the crafting menu. It shows everything you can make with the stations around you.",
    "Press R to open your equipment. Accessories go in the slots next to your armor.",
    "Your pickaxe's power decides what you can mine. Obsidian needs something stronger than copper.",
    "Each world has copper or tin, iron or lead, silver or tungsten, and gold or platinum. Look around!",
    "Life Crystals raise your maximum life by 20. You can use fifteen of them.",
    "Magic weapons use mana, which slowly refills. Mana Crystals raise your maximum.",
    "Enemies drop coins. If you die, you'll drop half of the coins you carry.",
    "The Merchant will move in once someone has saved 50 silver and there is an empty house.",
    "The Nurse comes once the Merchant lives here and someone has used a Life Crystal.",
    "Carry some explosives, and a Demolitionist may take an interest in your town.",
    "Use a Housing Query on a room to check whether someone could live there.",
    "Twenty gel and five gold or platinum bars make a Slime Crown. Use it if you dare.",
    "The Dungeon lies to the %s. An old man guards its door - speak to him at night, if you are strong enough.",
]
SHOPS = {
    'merchant': [
        {'item': 'minecraft:torch', 'price': 50},
        {'item': t('lesser_healing_potion')},
        {'item': t('lesser_mana_potion')},
        {'item': 'minecraft:arrow', 'price': 5},
        {'item': t('shuriken'), 'price': 15},
        {'item': t('copper_pickaxe')},
        {'item': t('copper_axe')},
        {'item': t('iron_anvil'), 'price': 5000},
        {'item': 'minecraft:glass_bottle', 'price': 20},
        {'item': 'minecraft:chest', 'price': 500},
        {'item': t('housing_query'), 'price': 100},
        {'item': t('mining_potion'), 'condition': 'boss_eye_defeated'},
    ],
    'arms_dealer': [
        {'item': t('musket_ball'), 'price': 7},
        {'item': t('flintlock_pistol')},
        {'item': t('musket'), 'condition': 'boss_evil_defeated'},
    ],
    'dryad': [
        {'item': t('purification_powder'), 'price': 75},
        {'item': 'minecraft:oak_sapling', 'price': 10},
        {'item': 'minecraft:wheat_seeds', 'price': 25},
        {'item': t('vile_powder'), 'price': 100, 'condition': {'evil': 'corruption'}},
        {'item': t('vicious_powder'), 'price': 100, 'condition': {'evil': 'crimson'}},
    ],
    'clothier': [
        {'item': 'minecraft:white_wool', 'price': 20},
        {'item': 'minecraft:string', 'price': 25},
        {'item': 'minecraft:leather_helmet', 'price': 1000},
        {'item': 'minecraft:leather_chestplate', 'price': 1000},
        {'item': 'minecraft:leather_leggings', 'price': 1000},
        {'item': 'minecraft:leather_boots', 'price': 1000},
        {'item': 'minecraft:red_dye', 'price': 200},
        {'item': 'minecraft:blue_dye', 'price': 200},
        {'item': 'minecraft:black_dye', 'price': 200},
        {'item': 'minecraft:carved_pumpkin', 'price': 1000, 'time': 'night'},
    ],
    'wizard': [
        {'item': t('spell_tome'), 'price': 50000},
        {'item': t('greater_mana_potion')},
        {'item': t('mana_potion')},
        {'item': 'minecraft:book', 'price': 1500},
        {'item': 'minecraft:lapis_lazuli', 'price': 500},
        {'item': 'minecraft:experience_bottle', 'price': 2000},
    ],
    'steampunker': [
        {'item': t('clentaminator'), 'price': 150000},
        {'item': t('green_solution'), 'price': 2500},
        {'item': t('blue_solution'), 'price': 2500},
        {'item': t('purple_solution'), 'price': 2500},
        {'item': t('red_solution'), 'price': 2500},
        {'item': 'minecraft:piston', 'price': 1000},
        {'item': 'minecraft:clock', 'price': 2000},
    ],
    'mechanic': [
        {'item': 'minecraft:redstone', 'price': 50},
        {'item': 'minecraft:redstone_torch', 'price': 200},
        {'item': 'minecraft:lever', 'price': 200},
        {'item': 'minecraft:stone_button', 'price': 200},
        {'item': 'minecraft:stone_pressure_plate', 'price': 500},
        {'item': 'minecraft:heavy_weighted_pressure_plate', 'price': 500},
        {'item': 'minecraft:repeater', 'price': 1000},
        {'item': 'minecraft:comparator', 'price': 1500},
        {'item': 'minecraft:observer', 'price': 1500},
        {'item': 'minecraft:piston', 'price': 1000},
        {'item': 'minecraft:sticky_piston', 'price': 1500},
        {'item': 'minecraft:dispenser', 'price': 2000},
        {'item': 'minecraft:redstone_lamp', 'price': 1000},
        {'item': 'minecraft:daylight_detector', 'price': 2000},
        {'item': 'minecraft:target', 'price': 1000},
        {'item': 'minecraft:tripwire_hook', 'price': 500},
        {'item': 'minecraft:powered_rail', 'price': 1000},
        {'item': 'minecraft:detector_rail', 'price': 1000},
        {'item': 'minecraft:activator_rail', 'price': 1000},
        {'item': 'minecraft:rail', 'price': 50},
    ],
    'pirate': [
        {'item': t('cannonball'), 'price': 1500},
        {'item': t('pirate_hat'), 'price': 20000},
        {'item': t('pirate_shirt'), 'price': 20000},
        {'item': t('pirate_pants'), 'price': 20000},
        {'item': 'minecraft:spyglass', 'price': 5000},
        {'item': 'minecraft:compass', 'price': 2500},
        {'item': 'minecraft:map', 'price': 1000},
        {'item': 'minecraft:oak_boat', 'price': 1000},
    ],
    'witch_doctor': [
        {'item': t('leaf_wings'), 'price': 200000, 'condition': 'hardmode_active', 'time': 'night'},
        {'item': t('stinger'), 'price': 500},
        {'item': t('jungle_spores'), 'price': 300},
        {'item': 'minecraft:vine', 'price': 100},
        {'item': 'minecraft:jungle_sapling', 'price': 200},
    ],
    'goblin_tinkerer': [
        {'item': t('tinkerers_workshop'), 'price': 100000},
        {'item': t('toolbelt'), 'price': 50000},
        {'item': t('goblin_battle_standard'), 'price': 20000},
    ],
    'demolitionist': [
        {'item': 'minecraft:tnt', 'price': 1500},
        {'item': 'minecraft:flint_and_steel', 'price': 500},
        {'item': t('mining_potion')},
    ],
}


def npcs():
    for npc, items in SHOPS.items():
        write(f'{NS}/terracraft/shops/{npc}.json', {'npc': npc, 'items': items})
    write(f'{NS}/tags/block/housing/doors.json', {'values': ['#minecraft:doors', '#minecraft:trapdoors', '#minecraft:fence_gates']})
    write(f'{NS}/tags/block/housing/comfort.json', {'values': ['#minecraft:beds', '#minecraft:wooden_stairs']})
    write(f'{NS}/tags/block/housing/tables.json', {'values': ['minecraft:crafting_table', t('work_bench'), 'minecraft:cartography_table',
                                                            'minecraft:fletching_table', 'minecraft:smithing_table', 'minecraft:loom']})
    write(f'{NS}/tags/block/housing/lights.json', {'values': []})


# --- Terraria biomes only -----------------------------------------------------------------------------
# Every Minecraft-only overworld biome is replaced by its closest Terraria biome (TerrariaBiomeSource).
# Kept: forest (Terraria's Forest), snowy plains/taiga (Snow, with boreal trees), desert, jungle, oceans, beaches
# and mushroom fields (Glowing Mushroom). Corruption/Crimson, Dungeon, Underworld etc. are TerraCraft worldgen.
BIOME_REPLACEMENTS = {
    'forest': ['plains', 'sunflower_plains', 'meadow', 'birch_forest', 'old_growth_birch_forest', 'dark_forest', 'flower_forest',
               'cherry_grove', 'pale_garden', 'savanna', 'savanna_plateau', 'windswept_savanna', 'swamp', 'mangrove_swamp',
               'windswept_hills', 'windswept_gravelly_hills', 'windswept_forest', 'stony_peaks', 'river',
               'lush_caves', 'dripstone_caves', 'deep_dark', 'sulfur_caves'],
    'snowy_taiga': ['taiga', 'old_growth_pine_taiga', 'old_growth_spruce_taiga', 'grove', 'snowy_slopes', 'jagged_peaks', 'frozen_peaks'],
    'snowy_plains': ['ice_spikes', 'frozen_river'],
    'desert': ['badlands', 'eroded_badlands', 'wooded_badlands'],
    'jungle': ['sparse_jungle', 'bamboo_jungle'],
    'ocean': ['cold_ocean', 'lukewarm_ocean', 'frozen_ocean'],
    'deep_ocean': ['deep_cold_ocean', 'deep_lukewarm_ocean', 'deep_frozen_ocean'],
    'beach': ['stony_shore'],
}


def terraria_biomes():
    replace = {f'minecraft:{old}': f'minecraft:{new}' for new, olds in BIOME_REPLACEMENTS.items() for old in olds}
    end = {'type': 'minecraft:the_end', 'generator': {'type': 'minecraft:noise', 'biome_source': {'type': 'minecraft:the_end'},
                                                     'settings': 'minecraft:end'}}
    nether = {'type': 'minecraft:the_nether', 'generator': {'type': 'minecraft:noise', 'settings': 'minecraft:nether',
                                                           'biome_source': {'type': 'minecraft:multi_noise', 'preset': 'minecraft:nether'}}}
    for preset, settings in (('normal', 'overworld'), ('large_biomes', 'large_biomes'), ('amplified', 'amplified')):
        overworld = {'type': 'minecraft:overworld', 'generator': {'type': 'minecraft:noise', 'settings': f'minecraft:{settings}', 'biome_source': {
            'type': t('terraria'), 'base': {'type': 'minecraft:multi_noise', 'preset': 'minecraft:overworld'}, 'replace': replace}}}
        write(f'minecraft/worldgen/world_preset/{preset}.json', {'dimensions': {
            'minecraft:overworld': overworld, 'minecraft:the_end': end, 'minecraft:the_nether': nether}})


def main():
    jar = sys.argv[1] if len(sys.argv) > 1 else os.path.expanduser(
        '~/.gradle/caches/minecraftforge/forgegradle/mavenizer/caches/minecraft_tasks/26.2/client.jar')
    blocks()
    evil_blocks()
    dungeon()
    jungle()
    underworld()
    goblins()
    hardmode()
    queen_slime()
    stage6()
    stage7()
    stage8()
    damage()
    smelting()
    terraria_recipes()
    vanilla_overrides(jar)
    mobs()
    worldgen()
    terraria_biomes()
    npcs()
    n = lang()
    print(f'Generated {len(RECIPES)} Terraria recipes and {n} language entries')


if __name__ == '__main__':
    main()
