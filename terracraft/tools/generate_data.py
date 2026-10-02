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


def blocks():
    for m in METALS:
        for ore in (f'{m}_ore', f'deepslate_{m}_ore'):
            write(f'{NS}/loot_table/blocks/{ore}.json', ore_loot(ore, f'raw_{m}'))
    for b in ['work_bench', 'iron_anvil', 'lead_anvil']:
        write(f'{NS}/loot_table/blocks/{b}.json', self_loot(b))
    write(f'{NS}/loot_table/blocks/life_crystal_block.json', {
        'type': 'minecraft:block', 'pools': [{'rolls': 1, 'entries': [{'type': 'minecraft:item', 'name': t('life_crystal')}]}],
        'random_sequence': f'{NS}:blocks/life_crystal_block'})
    write('minecraft/tags/block/mineable/pickaxe.json', {'values': [t(o) for o in ORES] + [t('iron_anvil'), t('lead_anvil'), t('life_crystal_block')]})
    write('minecraft/tags/block/mineable/axe.json', {'values': [t('work_bench')]})
    write('minecraft/tags/block/needs_stone_tool.json', {'values': [t(f'{p}{m}_ore') for m in STONE_TIER for p in ('', 'deepslate_')]})
    write('minecraft/tags/block/needs_iron_tool.json', {'values': [t(f'{p}{m}_ore') for m in IRON_TIER for p in ('', 'deepslate_')]})
    write(f'{NS}/tags/block/mineable/hammer.json', {'values': []})
    # crafting station tags: any block in the tag counts as that station
    write(f'{NS}/tags/block/stations/work_bench.json', {'values': [t('work_bench')]})
    write(f'{NS}/tags/block/stations/furnace.json', {'values': ['minecraft:furnace', 'minecraft:blast_furnace']})
    write(f'{NS}/tags/block/stations/anvil.json', {'values': [t('iron_anvil'), t('lead_anvil'), 'minecraft:anvil', 'minecraft:chipped_anvil', 'minecraft:damaged_anvil']})
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
    recipe('slime_crown', t('slime_crown'), [(t('gel'), 20), ('minecraft:gold_ingot', 5)], [WB], category='consumables')
    recipe('slime_crown_platinum', t('slime_crown'), [(t('gel'), 20), (t('platinum_bar'), 5)], [WB], category='consumables')
    recipe('suspicious_looking_eye', t('suspicious_looking_eye'), [(t('lens'), 6)], [WB], category='consumables')
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
                      'ruined_portal_mountain', 'ruined_portal_ocean', 'ruined_portal_standard', 'ruined_portal_swamp']


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
                data = {'forge:condition': {'type': 'terracraft:config', 'option': option, 'value': False}, **data}
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
        key = ('block.' if name in ORES or name in ('work_bench', 'iron_anvil', 'lead_anvil') else 'item.') + f'{NS}.{name}'
        L[key] = title(name)
    L['block.terracraft.life_crystal_block'] = 'Life Crystal'
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
               'water_walking', 'endurance', 'wrath', 'rage', 'potion_sickness', 'mana_sickness']
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
        'progression.terracraft.announce.altar_smashed': 'Your world has been blessed with new ores!',
        'progression.terracraft.announce.boss_plantera_defeated': 'The Jungle Temple trembles...',
        'progression.terracraft.announce.boss_golem_defeated': 'The ancient guardian has fallen.',
        'progression.terracraft.announce.celestial_events_active': 'The celestial pillars have appeared!',
        'progression.terracraft.announce.boss_skeletron_defeated': 'The curse of the Dungeon has been lifted.',
    })
    L.update({
        'message.terracraft.boss.awoken': '%s has awoken!',
        'message.terracraft.boss.defeated': '%s has been defeated!',
        'message.terracraft.boss.nothing_happens': 'Nothing happens...',
        'message.terracraft.boss.already_active': 'That boss is already here!',
        'item.terracraft.slime_crown.tooltip': 'Summons King Slime',
        'item.terracraft.suspicious_looking_eye.tooltip': 'Summons the Eye of Cthulhu (use at night)',
        'item.terracraft.demonite_ore.tooltip': 'Pulsing with dark energy',
        'item.terracraft.crimtane_ore.tooltip': 'Its veins throb',
    })
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
    'king_slime': ('King Slime', [('gel', 40, 80, 1.0), ('slime_crown', 1, 1, 0.1)]),
    'eye_of_cthulhu': ('Eye of Cthulhu', [('lens', 3, 6, 1.0)]),
}

OVERWORLD_LAND = {'exclude_biomes': ['#minecraft:is_ocean', '#minecraft:is_river']}
PRE_HM = '!hardmode_active'
SPAWNS = {
    'surface_day': [
        dict(entity='green_slime', weight=10, time='day', layers=['surface'], **OVERWORLD_LAND),
        dict(entity='blue_slime', weight=8, time='day', layers=['surface'], **OVERWORLD_LAND),
        dict(entity='purple_slime', weight=1, time='day', layers=['surface'], **OVERWORLD_LAND),
    ],
    'surface_night': [
        dict(entity='zombie', weight=10, time='night', layers=['surface'], group=[1, 2], **OVERWORLD_LAND),
        dict(entity='demon_eye', weight=6, time='night', layers=['surface'], placement='air', sky=True),
        dict(entity='blue_slime', weight=2, time='night', layers=['surface'], **OVERWORLD_LAND),
    ],
    'underground': [
        dict(entity='red_slime', weight=8, layers=['underground']),
        dict(entity='yellow_slime', weight=3, layers=['underground']),
        dict(entity='blue_slime', weight=3, layers=['underground']),
        dict(entity='cave_bat', weight=6, layers=['underground'], placement='air'),
        dict(entity='skeleton', weight=5, layers=['underground']),
    ],
    'cavern': [
        dict(entity='black_slime', weight=6, layers=['cavern']),
        dict(entity='yellow_slime', weight=5, layers=['cavern']),
        dict(entity='red_slime', weight=3, layers=['cavern']),
        dict(entity='mother_slime', weight=2, layers=['cavern'], condition=PRE_HM),
        dict(entity='cave_bat', weight=6, layers=['cavern'], placement='air'),
        dict(entity='skeleton', weight=8, layers=['cavern']),
    ],
}


def mobs():
    for mob, (_, drops) in MOBS.items():
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
    write(f'{NS}/forge/biome_modifier/remove_vanilla_ores.json', {
        'type': 'forge:remove_features', 'biomes': '#minecraft:is_overworld',
        'features': VANILLA_ORE_FEATURES, 'steps': ['underground_ores']})
    write(f'{NS}/forge/biome_modifier/terraria_ores.json', {
        'type': 'forge:add_features', 'biomes': '#minecraft:is_overworld', 'features': features, 'step': 'underground_ores'})

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
    write(f'{NS}/forge/biome_modifier/terraria_cave_loot.json', {
        'type': 'forge:add_features', 'biomes': '#minecraft:is_overworld',
        'features': [t('life_crystal'), t('wooden_chest'), t('underground_chest')], 'step': 'underground_decoration'})

    write(f'{NS}/loot_table/chests/surface_cave.json', chest_table(
        ['wooden_boomerang', 'aglet', 'wand_of_sparking', 'wooden_bow', 'copper_shortsword'],
        ['minecraft:copper_ingot', 'tin_bar', 'minecraft:iron_ingot', 'lead_bar'],
        ['ironskin_potion', 'swiftness_potion', 'mining_potion', 'archery_potion']))
    write(f'{NS}/loot_table/chests/underground.json', chest_table(
        ['band_of_regeneration', 'cloud_in_a_bottle', 'hermes_boots', 'magic_missile', 'flintlock_pistol'],
        ['silver_bar', 'tungsten_bar', 'minecraft:gold_ingot', 'platinum_bar'],
        ['regeneration_potion', 'obsidian_skin_potion', 'water_walking_potion', 'magic_power_potion', 'mana_regeneration_potion']))


def main():
    jar = sys.argv[1] if len(sys.argv) > 1 else os.path.expanduser(
        '~/.gradle/caches/minecraftforge/forgegradle/mavenizer/caches/minecraft_tasks/26.2/client.jar')
    blocks()
    damage()
    smelting()
    terraria_recipes()
    vanilla_overrides(jar)
    mobs()
    worldgen()
    n = lang()
    print(f'Generated {len(RECIPES)} Terraria recipes and {n} language entries')


if __name__ == '__main__':
    main()
