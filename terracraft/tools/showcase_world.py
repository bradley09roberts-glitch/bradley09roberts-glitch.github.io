#!/usr/bin/env python3
"""Writes the "showcase" datapack: functions that build the TerraCraft showcase hub and run its buttons.

    python tools/showcase_world.py <world folder>

The hub floats at y=210 above spawn (Terraria's Space layer, so nothing spawns there):
  * north wall - time, weather, Hardmode, world evil, game mode, player upgrades
  * west wall  - events (Blood Moon, Slime Rain, Goblin Army, meteor) and town NPCs
  * east wall  - teleports to every biome and TerraCraft structure (coordinates for seed 12345)
  * south wall - gear kits
  * centre     - a zoo of every enemy behind glass, and chests holding every item
  * east wing  - boss arena with summon buttons; south wing - houses for town NPCs
Each button is a command block calling one function, so every control is also usable by hand:
/function showcase:<name> (e.g. /function showcase:hub to get back).
After copying, run /reload and /function showcase:build (and :build_underworld from inside the Underworld).
"""
import json
import os
import sys

ROOT = os.path.join(os.path.dirname(__file__), '..')
ITEMS_DIR = os.path.join(ROOT, 'src/main/resources/assets/terracraft/items')
LANG = json.load(open(os.path.join(ROOT, 'src/main/resources/assets/terracraft/lang/en_us.json')))
FLOOR = 209          # floor blocks; players stand at FLOOR + 1
Y = FLOOR + 1
FUNCS = {}


def fn(name, *lines):
    FUNCS[name] = list(lines)


def sign_nbt(*lines):
    lines = (list(lines) + ['', '', '', ''])[:4]
    msgs = ','.join(json.dumps(l) for l in lines)
    return '{is_waxed:1b,front_text:{has_glowing_text:1b,color:"white",messages:[%s]}}' % msgs


def label(text):
    """Splits a label over the middle two sign lines."""
    words = text.split()
    if len(text) <= 15 or len(words) == 1:
        return ['', text, '', '']
    best = None
    for i in range(1, len(words)):
        a, b = ' '.join(words[:i]), ' '.join(words[i:])
        score = max(len(a), len(b))
        if best is None or score < best[0]:
            best = (score, a, b)
    return ['', best[1], best[2], '']


# ------------------------------------------------------------------------------------------------ buttons
# wall: (axis of the wall, fixed coordinate of the wall blocks, offset toward the room, facing of buttons/signs)
WALLS = {
    'north': ('z', -31, 1, 'south'),
    'south': ('z', 31, -1, 'north'),
    'west': ('x', -31, 1, 'east'),
    'east': ('x', 31, -1, 'west'),
    'arena': ('x', 40, 1, 'east'),
}
BUILD = []


def place_button(wall, index, row, text, function):
    axis, fixed, inward, facing = WALLS[wall]
    along = -26 + 4 * index
    y = Y + 1 if row == 0 else Y + 4
    def pos(offset, dy):
        if axis == 'z':
            return f'{along} {y + dy} {fixed + offset}'
        return f'{fixed + offset} {y + dy} {along}'
    BUILD.append(f'setblock {pos(0, 0)} minecraft:command_block[facing={facing}]{{Command:"function showcase:{function}",TrackOutput:0b}}')
    BUILD.append(f'setblock {pos(inward, 0)} minecraft:birch_button[face=wall,facing={facing}]')
    BUILD.append(f'setblock {pos(inward, 1)} minecraft:dark_oak_wall_sign[facing={facing}]{sign_nbt(*label(text))}')


def title(wall, text):
    axis, fixed, inward, facing = WALLS[wall]
    for along in (-4, 0, 4):
        pos = f'{along} {Y + 6} {fixed + inward}' if axis == 'z' else f'{fixed + inward} {Y + 6} {along}'
        BUILD.append(f'setblock {pos} minecraft:dark_oak_wall_sign[facing={facing}]{sign_nbt("", text, "", "")}')


def buttons(wall, heading, entries):
    title(wall, heading)
    for i, (text, function) in enumerate(entries):
        place_button(wall, i % 14, i // 14, text, function)


# ------------------------------------------------------------------------------------------------ controls
P = 'execute as @p at @s run '
fn('hub', f'tp @p 0 {Y} 15 180 0')
fn('sunrise', 'time set 0'); fn('day', 'time set day'); fn('noon', 'time set noon'); fn('sunset', 'time set 12000')
fn('night', 'time set night'); fn('midnight', 'time set midnight')
fn('clear', 'weather clear'); fn('rain', 'weather rain'); fn('thunder', 'weather thunder')
fn('freeze_time', 'gamerule advance_time false', 'say Time frozen'); fn('unfreeze_time', 'gamerule advance_time true', 'say Time flows again')
fn('heal', P + 'terraria heal', 'effect clear @p'); fn('kill_enemies', 'terraria killall'); fn('kill_bosses', 'terraria boss killall')
fn('hardmode_on', 'terraria hardmode true'); fn('hardmode_off', 'terraria hardmode false')
fn('reset_progress', 'terraria progression reset')
fn('corruption', 'terraria evil corruption'); fn('crimson', 'terraria evil crimson')
fn('classic', 'difficulty normal', 'say Classic mode (Normal difficulty)'); fn('expert', 'difficulty hard', 'say Expert mode (Hard difficulty)')
fn('creative', 'gamemode creative @p'); fn('survival', 'gamemode survival @p')
fn('max_life', P + 'terraria set lifecrystals 15', P + 'terraria set lifefruit 20', P + 'terraria heal')
fn('max_mana', P + 'terraria set manacrystals 9')
fn('night_vision', 'effect give @p minecraft:night_vision infinite 0 true')
fn('fire_proof', 'effect give @p minecraft:fire_resistance infinite 0 true')
fn('clear_effects', 'effect clear @p')
buttons('north', 'TIME & WORLD', [
    ('Sunrise', 'sunrise'), ('Day', 'day'), ('Noon', 'noon'), ('Sunset', 'sunset'), ('Night', 'night'), ('Midnight', 'midnight'),
    ('Clear Weather', 'clear'), ('Rain', 'rain'), ('Thunder', 'thunder'), ('Freeze Time', 'freeze_time'), ('Unfreeze Time', 'unfreeze_time'),
    ('Heal Me', 'heal'), ('Kill Enemies', 'kill_enemies'), ('Kill Bosses', 'kill_bosses'),
    ('Hardmode ON', 'hardmode_on'), ('Hardmode OFF', 'hardmode_off'), ('Reset Progress', 'reset_progress'), ('Corruption World', 'corruption'),
    ('Crimson World', 'crimson'), ('Classic Mode', 'classic'), ('Expert Mode', 'expert'), ('Creative', 'creative'), ('Survival', 'survival'),
    ('Max Life', 'max_life'), ('Max Mana', 'max_mana'), ('Night Vision', 'night_vision'), ('Fire Proof', 'fire_proof'),
    ('Clear Effects', 'clear_effects'),
])

NPCS = ['guide', 'merchant', 'nurse', 'demolitionist', 'arms_dealer', 'dryad', 'clothier', 'goblin_tinkerer', 'wizard', 'steampunker', 'witch_doctor']
for npc in NPCS:
    fn(f'npc_{npc}', P + f'terraria npc spawn {npc}')
fn('npc_all', *[P + f'terraria npc spawn {npc}' for npc in NPCS])
fn('npc_remove', 'terraria npc killall')
fn('blood_moon', 'time set night', 'terraria event start blood_moon', 'say Go down to the surface to see it!')
fn('slime_rain', 'time set day', 'terraria event start slime_rain')
fn('goblin_army', 'time set day', 'terraria event start goblin_army', 'say Go down to the surface to fight them!')
fn('stop_event', 'terraria event stop')
fn('meteor', 'time set night', P + 'terraria meteor', 'say A meteor landed near you. Look for the smoke!')
buttons('west', 'EVENTS & NPCS', [
    ('Blood Moon', 'blood_moon'), ('Slime Rain', 'slime_rain'), ('Goblin Army', 'goblin_army'), ('Stop Event', 'stop_event'),
    ('Drop Meteor', 'meteor'), ('All Town NPCs', 'npc_all'), ('Remove NPCs', 'npc_remove'),
] + [(LANG.get(f'npc.terracraft.{n}', n.title()), f'npc_{n}') for n in NPCS])

# ------------------------------------------------------------------------------------------------ teleports (seed 12345)
def surface(name, x, z, text, *extra):
    fn(f'go_{name}', *extra, 'effect give @p minecraft:slow_falling 6 0 true', f'spreadplayers {x} {z} 0 1 false @p', f'say {text}')


def tp(name, x, y, z, yaw, text, *extra):
    fn(f'go_{name}', *extra, f'tp @p {x} {y} {z} {yaw} 0', f'say {text}')


# Only Terraria's biomes exist (TerrariaBiomeSource): no plains, birch, taiga, savanna, swamp, badlands, cherry...
surface('forest', -224, 288, 'Forest'); surface('forest_hills', -384, -160, 'Forest hills')
surface('snow', 672, -608, 'Snow biome'); surface('boreal', 448, -512, 'Snow biome: boreal forest')
surface('desert', -2208, 640, 'Desert'); surface('ocean', 128, 384, 'Ocean')
surface('jungle', -700, -470, 'The Jungle (mud and hives underground)')
surface('corruption', 355, 223, 'The Corruption (or Crimson): chasms lead down to Shadow Orbs')
tp('dungeon', 104, 93, -803, 180, 'The Dungeon entrance. The Old Man waits at the door; at night his chat has a Curse button.')
tp('dungeon_inside', 104, 69, -834, 0, 'Inside the Dungeon. Before Skeletron is beaten the Dungeon Guardian will come for you!')
tp('hive', -654, 22, -429, 90, 'A Bee Hive. Break the Larva to summon the Queen Bee.', 'effect give @p minecraft:night_vision 600 0 true')
tp('underworld', 40, -51, 40, 0, 'The Underworld viewing room. Throw a Guide Voodoo Doll in the lava to summon the Wall of Flesh.',
   'effect give @p minecraft:fire_resistance infinite 0 true')
surface('mushroom', 4000, 2272, 'Glowing Mushroom island'); surface('beach', -1120, 1536, 'Beach')
surface('spawn_ground', 0, 0, 'Spawn (on the ground)')
surface('hallow', 254, 159, 'The Hallow (appears once Hardmode starts: press Hardmode On first)', 'terraria hardmode true')
tp('arena', 75, Y, 0, -90, 'Boss Arena')
tp('town', 0, Y, 36, 0, 'Town: NPCs move into these houses')
buttons('east', 'TELEPORTS', [
    ('Forest', 'go_forest'), ('Forest Hills', 'go_forest_hills'), ('Snow', 'go_snow'), ('Boreal Forest', 'go_boreal'),
    ('Desert', 'go_desert'), ('Ocean', 'go_ocean'), ('Beach', 'go_beach'), ('Jungle', 'go_jungle'), ('Bee Hive', 'go_hive'),
    ('Corruption', 'go_corruption'), ('Glowing Mushroom', 'go_mushroom'), ('Dungeon Entrance', 'go_dungeon'),
    ('Inside Dungeon', 'go_dungeon_inside'), ('Underworld', 'go_underworld'), ('Spawn Ground', 'go_spawn_ground'),
    ('Boss Arena', 'go_arena'), ('Town', 'go_town'), ('The Hallow', 'go_hallow'),
])

# ------------------------------------------------------------------------------------------------ kits
ALL_ITEMS = sorted(f[:-5] for f in os.listdir(ITEMS_DIR))


def give(*items):
    out = []
    for item in items:
        name, _, count = item.partition('*')
        out.append(f'give @p {name if ":" in name else "terracraft:" + name} {count or 1}')
    return out


fn('kit_starter', *give('copper_shortsword', 'copper_pickaxe', 'copper_axe', 'copper_hammer', 'wooden_bow', 'minecraft:arrow*99',
                        'wood_helmet', 'wood_breastplate', 'wood_greaves', 'lesser_healing_potion*10', 'minecraft:torch*64', 'work_bench'))
fn('kit_ore', *give('platinum_broadsword', 'platinum_pickaxe', 'platinum_axe', 'gold_bow', 'minecraft:arrow*99', 'platinum_helmet',
                    'platinum_chainmail', 'platinum_greaves', 'hermes_boots', 'cloud_in_a_bottle', 'shiny_red_balloon', 'band_of_regeneration',
                    'healing_potion*10'))
fn('kit_evil', *give('lights_bane', 'blood_butcherer', 'nightmare_pickaxe', 'war_axe_of_the_night', 'the_breaker', 'demon_bow', 'musket',
                     'musket_ball*99', 'vilethorn', 'shadow_helmet', 'shadow_scalemail', 'shadow_greaves', 'panic_necklace'))
fn('kit_jungle', *give('blade_of_grass', 'bee_keeper', 'bees_knees', 'bee_gun', 'minecraft:arrow*99', 'jungle_hat', 'jungle_shirt',
                       'jungle_pants', 'honey_comb', 'anklet_of_the_wind', 'feral_claws', 'abeemination*3'))
fn('kit_dungeon', *give('muramasa', 'handgun', 'musket_ball*99', 'aqua_scepter', 'water_bolt', 'book_of_skulls', 'magic_missile',
                        'cobalt_shield', 'golden_key*10', 'shadow_key'))
fn('kit_molten', *give('molten_pickaxe', 'molten_hamaxe', 'fiery_greatsword', 'molten_fury', 'phoenix_blaster', 'minecraft:arrow*99',
                       'musket_ball*99', 'flamelash', 'flower_of_fire', 'demon_scythe', 'hellwing_bow', 'molten_helmet', 'molten_breastplate',
                       'molten_greaves', 'obsidian_skull', 'lava_charm', 'lava_waders', 'obsidian_skin_potion*5'))
fn('kit_meteor', *give('space_gun', 'meteor_helmet', 'meteor_suit', 'meteor_leggings', 'mana_flower', 'mana_potion*10'))
fn('kit_hardmode', *give('pwnhammer', 'breaker_blade', 'laser_rifle', 'warrior_emblem', 'ranger_emblem', 'sorcerer_emblem', 'summoner_emblem',
                         'titan_glove', 'clentaminator', 'green_solution*50', 'blue_solution*50', 'purple_solution*50', 'red_solution*50'))
fn('kit_hm_ores', *give('cobalt_pickaxe', 'mythril_sword', 'orichalcum_repeater', 'minecraft:arrow*99', 'adamantite_helmet', 'adamantite_breastplate',
                        'adamantite_leggings', 'titanium_helmet', 'titanium_breastplate', 'titanium_leggings', 'mythril_anvil', 'adamantite_forge',
                        'raw_cobalt*30', 'raw_mythril*30', 'raw_adamantite*30'))
fn('kit_hallowed', *give('excalibur', 'hallowed_repeater', 'pickaxe_axe', 'minecraft:arrow*99', 'hallowed_mask', 'hallowed_plate_mail',
                         'hallowed_greaves', 'greater_healing_potion*20', 'soul_of_might*5', 'soul_of_sight*5', 'soul_of_fright*5'))
fn('kit_accessories', *give(*[i for i in ALL_ITEMS if i in {
    'hermes_boots', 'cloud_in_a_bottle', 'shiny_red_balloon', 'lucky_horseshoe', 'band_of_regeneration', 'band_of_starpower',
    'mana_regeneration_band', 'natures_gift', 'aglet', 'anklet_of_the_wind', 'feral_claws', 'obsidian_skull', 'lava_charm', 'cobalt_shield',
    'flipper', 'water_walking_boots', 'toolbelt', 'panic_necklace', 'honey_comb', 'obsidian_horseshoe', 'cloud_in_a_balloon',
    'obsidian_shield', 'obsidian_water_walking_boots', 'lava_waders', 'mana_flower'}]))
fn('kit_summons', *give('slime_crown*3', 'suspicious_looking_eye*3', 'worm_food*3', 'bloody_spine*3', 'abeemination*3',
                        'guide_voodoo_doll', 'goblin_battle_standard*3', 'mechanical_eye*3', 'mechanical_worm*3', 'mechanical_skull*3'))
fn('kit_potions', *give('healing_potion*20', 'mana_potion*20', 'ironskin_potion*5', 'swiftness_potion*5', 'regeneration_potion*5',
                        'magic_power_potion*5', 'archery_potion*5', 'mining_potion*5', 'obsidian_skin_potion*5', 'endurance_potion*5',
                        'wrath_potion*5', 'rage_potion*5', 'water_walking_potion*5'))
fn('kit_stations', *give('work_bench', 'iron_anvil', 'minecraft:furnace', 'minecraft:brewing_stand', 'hellforge', 'tinkerers_workshop', 'mythril_anvil', 'adamantite_forge',
                         'minecraft:crafting_table'))
fn('kit_wings', *give('fledgling_wings', 'angel_wings', 'demon_wings', 'leaf_wings', 'soul_of_light*25', 'soul_of_night*25', 'soul_of_flight*20'),
   P + 'terraria equip terracraft:angel_wings',
   'say Angel Wings equipped. Jump, then hold jump to fly; keep holding to glide. Swap wings in the Equipment screen.')
fn('kit_food', *give('minecraft:apple*5', 'minecraft:bread*5', 'minecraft:cooked_beef*5', 'minecraft:golden_carrot*5', 'minecraft:rabbit_stew'),
   'say No hunger here: food gives Well Fed, Plenty Satisfied or Exquisitely Stuffed.')
fn('kit_coins', *give('platinum_coin*5', 'gold_coin*50'))
fn('kit_life', *give('life_crystal*15', 'life_fruit*20', 'mana_crystal*9'))
fn('clear_inventory', 'clear @p')
buttons('south', 'GEAR KITS', [
    ('Starter Kit', 'kit_starter'), ('Ore Kit', 'kit_ore'), ('Corruption Kit', 'kit_evil'), ('Jungle Kit', 'kit_jungle'),
    ('Dungeon Kit', 'kit_dungeon'), ('Molten Kit', 'kit_molten'), ('Meteor Kit', 'kit_meteor'), ('Hardmode Kit', 'kit_hardmode'),
    ('Hardmode Ores Kit', 'kit_hm_ores'), ('Hallowed Kit', 'kit_hallowed'),
    ('All Accessories', 'kit_accessories'), ('Boss Summons', 'kit_summons'), ('Potions', 'kit_potions'), ('Crafting Stations', 'kit_stations'),
    ('Wings', 'kit_wings'), ('Food Buffs', 'kit_food'), ('Coins', 'kit_coins'), ('Life & Mana Crystals', 'kit_life'),
    ('Clear Inventory', 'clear_inventory'),
])

# ------------------------------------------------------------------------------------------------ boss arena
BOSSES = [('King Slime', 'king_slime'), ('Eye of Cthulhu', 'eye_of_cthulhu'), ('Eater of Worlds', 'eater_of_worlds'),
          ('Brain of Cthulhu', 'brain_of_cthulhu'), ('Queen Bee', 'queen_bee'), ('Skeletron', 'skeletron'), ('Wall of Flesh', 'wall_of_flesh'),
          ('The Twins', 'the_twins'), ('The Destroyer', 'destroyer'), ('Skeletron Prime', 'skeletron_prime')]
for text, boss in BOSSES:
    fn(f'boss_{boss}', 'gamemode survival @p', P + f'terraria boss spawn {boss}')
fn('boss_skeletron', 'gamemode survival @p', 'time set night', P + 'terraria boss spawn skeletron')
fn('boss_eye_of_cthulhu', 'gamemode survival @p', 'time set night', P + 'terraria boss spawn eye_of_cthulhu')
for _mech in ('the_twins', 'destroyer', 'skeletron_prime'):   # Hardmode, night only
    fn(f'boss_{_mech}', 'gamemode survival @p', 'terraria hardmode true', 'time set night', P + f'terraria boss spawn {_mech}')
buttons('arena', 'BOSS ARENA', [(t, f'boss_{b}') for t, b in BOSSES] + [
    ('Night', 'night'), ('Day', 'day'), ('Heal Me', 'heal'), ('Kill Bosses', 'kill_bosses'), ('Creative', 'creative'), ('Back to Hub', 'hub')])

# ------------------------------------------------------------------------------------------------ structure
S = []
# hub floor, walls, doorways
S += [f'fill -32 {FLOOR} -32 32 {FLOOR} 32 minecraft:smooth_quartz']
for wall in (f'-31 {{y0}} -31 31 {{y1}} -31', f'-31 {{y0}} 31 31 {{y1}} 31', f'-31 {{y0}} -31 -31 {{y1}} 31', f'31 {{y0}} -31 31 {{y1}} 31'):
    S.append(f'fill {wall.format(y0=Y, y1=Y + 6)} minecraft:polished_blackstone_bricks')
    S.append(f'fill {wall.format(y0=Y + 7, y1=Y + 9)} minecraft:glass')
S += [f'fill -1 {Y} 31 1 {Y + 2} 31 minecraft:air',          # to the town
      f'fill 31 {Y} -1 31 {Y + 2} 1 minecraft:air']          # to the arena
for x in range(-28, 29, 8):
    for z in range(-28, 29, 8):
        S.append(f'setblock {x} {FLOOR} {z} minecraft:sea_lantern')
# bridges, arena and town platforms
S += [f'fill 32 {FLOOR} -2 39 {FLOOR} 2 minecraft:smooth_quartz', f'fill 32 {Y} -2 39 {Y + 2} -2 minecraft:glass',
      f'fill 32 {Y} 2 39 {Y + 2} 2 minecraft:glass',
      f'fill 40 {FLOOR} -36 112 {FLOOR} 36 minecraft:smooth_stone',
      f'fill 40 {Y} -36 40 {Y + 10} 36 minecraft:glass', f'fill 112 {Y} -36 112 {Y + 10} 36 minecraft:glass',
      f'fill 40 {Y} -36 112 {Y + 10} -36 minecraft:glass', f'fill 40 {Y} 36 112 {Y + 10} 36 minecraft:glass',
      f'fill 40 {Y} -30 40 {Y + 6} 30 minecraft:polished_blackstone_bricks',
      f'fill 40 {Y} -1 40 {Y + 2} 1 minecraft:air',
      f'fill -34 {FLOOR} 32 34 {FLOOR} 64 minecraft:smooth_quartz',
      f'fill -34 {Y} 64 34 {Y + 1} 64 minecraft:glass', f'fill -34 {Y} 32 -34 {Y + 1} 64 minecraft:glass',
      f'fill 34 {Y} 32 34 {Y + 1} 64 minecraft:glass']
for x in range(48, 112, 12):
    for z in range(-30, 31, 12):
        S.append(f'setblock {x} {FLOOR} {z} minecraft:sea_lantern')
# town houses (valid Terraria housing: walls, door, light, chair, table)
HOUSES = []
for z0 in (40, 52):
    for x0 in (-30, -20, 3, 13, 23):
        x1, z1 = x0 + 8, z0 + 6
        S += [f'fill {x0} {FLOOR} {z0} {x1} {Y + 4} {z1} minecraft:oak_planks hollow',
              f'fill {x0} {Y + 4} {z0} {x1} {Y + 4} {z1} minecraft:spruce_planks',
              f'fill {x0 + 2} {Y + 1} {z0} {x0 + 2} {Y + 2} {z0} minecraft:glass_pane',
              f'fill {x0 + 6} {Y + 1} {z0} {x0 + 6} {Y + 2} {z0} minecraft:glass_pane',
              f'setblock {x0 + 4} {Y} {z0} minecraft:oak_door[half=lower,facing=south]',
              f'setblock {x0 + 4} {Y + 1} {z0} minecraft:oak_door[half=upper,facing=south]',
              f'setblock {x0 + 1} {Y} {z1 - 1} minecraft:crafting_table',
              f'setblock {x0 + 2} {Y} {z1 - 1} minecraft:oak_stairs[facing=west]',
              f'setblock {x0 + 7} {Y} {z1 - 1} minecraft:lantern',
              f'setblock {x0 + 4} {Y + 3} {z0 + 3} minecraft:lantern[hanging=true]']
        HOUSES.append((x0 + 4, Y, z0 + 3))
# bestiary: every enemy in a glass cell with a name sign
ENEMIES = ['green_slime', 'blue_slime', 'red_slime', 'purple_slime', 'yellow_slime', 'black_slime', 'baby_slime', 'mother_slime',
           'jungle_slime', 'dungeon_slime', 'lava_slime', 'zombie', 'blood_zombie', 'skeleton', 'angry_bones', 'demon_eye',
           'servant_of_cthulhu', 'drippler', 'cave_bat', 'jungle_bat', 'hellbat', 'eater_of_souls', 'crimera', 'face_monster', 'blood_crawler',
           'devourer', 'giant_worm', 'bone_serpent', 'dark_caster', 'cursed_skull', 'dungeon_guardian', 'hornet', 'bee', 'man_eater', 'snatcher',
           'imp', 'demon', 'voodoo_demon', 'meteor_head', 'goblin_peon', 'goblin_thief', 'goblin_warrior', 'goblin_archer', 'goblin_sorcerer',
           # Hardmode
           'pixie', 'unicorn', 'gastropod', 'illuminant_bat', 'illuminant_slime', 'chaos_elemental', 'corruptor', 'slimer', 'crimslime',
           'herpling', 'floaty_gross', 'wraith', 'possessed_armor', 'werewolf', 'armored_skeleton', 'giant_bat', 'mimic', 'probe']
ZOO = []
for i, mob in enumerate(ENEMIES):
    x = -30 + 6 * (i % 11)
    z = -20 + 7 * (i // 11)
    S += [f'fill {x - 2} {FLOOR} {z - 2} {x + 2} {Y + 4} {z + 2} minecraft:glass hollow',
          f'fill {x - 1} {FLOOR} {z - 1} {x + 1} {FLOOR} {z + 1} minecraft:moss_block',
          f'setblock {x} {Y} {z + 3} minecraft:dark_oak_sign[rotation=0]{sign_nbt(*label(LANG.get("entity.terracraft." + mob, mob)))}']
    ZOO.append(f'summon terracraft:{mob} {x + 0.5} {Y} {z + 0.5} {{NoAI:1b,Invulnerable:1b,PersistenceRequired:1b,Silent:1b,Rotation:[0f,0f]}}')
# item vault: chests holding one of every TerraCraft item
VAULT = []
STACK1 = ('sword', 'pickaxe', 'axe', 'hammer', 'bow', 'helmet', 'chainmail', 'greaves', 'breastplate', 'scalemail', 'hat', 'shirt', 'pants',
          'suit', 'leggings', 'gun', 'pistol', 'musket', 'staff', 'wand', 'scepter', 'emblem', 'key', 'doll')
for n, item in enumerate(ALL_ITEMS):
    chest, slot = divmod(n, 27)
    x = -26 + 2 * chest
    if slot == 0:
        S.append(f'setblock {x} {Y} 20 minecraft:chest[facing=north]')
    VAULT.append(f'item replace block {x} {Y} 20 container.{slot} with terracraft:{item}')
S.append(f'setblock -28 {Y} 20 minecraft:dark_oak_sign[rotation=8]' + sign_nbt('', 'ITEM VAULT', 'one of every', 'item'))
S.append(f'setblock 0 {Y} -25 minecraft:dark_oak_sign[rotation=0]' + sign_nbt('', 'ENEMY ZOO', 'every creature', ''))

fn('build', *S, *BUILD, *VAULT, f'setworldspawn 0 {Y} 15', 'say Showcase built.')
fn('build_zoo', *ZOO)
fn('build_underworld', 'fill 34 -52 34 46 -44 46 minecraft:obsidian hollow', 'fill 35 -51 35 45 -45 45 minecraft:air',
   'fill 34 -50 35 34 -46 45 minecraft:glass', 'fill 46 -50 35 46 -46 45 minecraft:glass', 'fill 35 -50 34 45 -46 34 minecraft:glass',
   'fill 35 -50 46 45 -46 46 minecraft:glass', 'setblock 40 -45 40 minecraft:glowstone',
   'setblock 40 -51 44 minecraft:command_block{Command:"function showcase:hub",TrackOutput:0b}',
   'setblock 40 -50 44 minecraft:polished_blackstone_button[face=floor,facing=north]',
   'setblock 40 -51 42 minecraft:dark_oak_sign[rotation=8]' + sign_nbt('', 'Back to Hub', '', ''))
HOUSE_LIST = HOUSES


def check_items():
    known = set(ALL_ITEMS)
    bad = []
    for name, lines in FUNCS.items():
        for line in lines:
            if line.startswith('give @p terracraft:'):
                item = line.split()[2].split(':')[1]
                if item not in known:
                    bad.append((name, item))
    if bad:
        sys.exit(f'unknown items: {bad}')


def main():
    check_items()
    world = sys.argv[1]
    out = os.path.join(world, 'datapacks/showcase/data/showcase/function')
    os.makedirs(out, exist_ok=True)
    with open(os.path.join(world, 'datapacks/showcase/pack.mcmeta'), 'w') as f:
        json.dump({'pack': {'description': 'TerraCraft showcase controls', 'min_format': [107, 1], 'max_format': 107}}, f)
    for name, lines in FUNCS.items():
        with open(os.path.join(out, name + '.mcfunction'), 'w') as f:
            f.write('\n'.join(lines) + '\n')
    print(f'{len(FUNCS)} functions, {len(BUILD) // 3} buttons, {len(ENEMIES)} enemies, {len(ALL_ITEMS)} items; houses at {HOUSE_LIST}')


if __name__ == '__main__':
    main()
