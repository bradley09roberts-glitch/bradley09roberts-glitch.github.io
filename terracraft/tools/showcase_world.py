#!/usr/bin/env python3
"""Writes the "showcase" datapack: functions that build the TerraCraft showcase and run its buttons.

    python tools/showcase_world.py <world folder>

Everything floats at y=210 above spawn (Terraria's Space layer, so nothing spawns there). The Grand Hall in the
middle has the controls on its walls and doors to four wings:
  * Grand Hall  - north wall: time, weather, Hardmode, world settings; west wall: events and town NPCs;
                  east wall: teleports to every biome, structure and wing; south wall: gear kits
  * north       - the Bestiary: an avenue of themed rooms (Forest, Caverns, Corruption, Crimson, Jungle, Dungeon,
                  Underworld, Hallow, Events, Sky), every enemy on display in its own biome
  * east        - the Hall of Bosses (every boss on a pedestal), then the Boss Arena with summon buttons
  * west        - the Armory: every armor set on an armor stand with its weapons, every item in a frame on the
                  walls, and chests holding one of each
  * south       - the Town (houses for every NPC), the Workshop (every crafting station) and the Block Garden
Each button is a command block calling one function, so every control is also usable by hand:
/function showcase:<name> (e.g. /function showcase:hub to get back).
To build: /reload, then /function showcase:prepare (loads the area; the build follows by itself after ten
seconds). /function showcase:build_underworld from inside the Underworld adds the lava viewing room.
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
AREA = (-172, -246, 252, 172)   # x0, z0, x1, z1 of everything the showcase builds


def fn(name, *lines):
    FUNCS[name] = list(lines)


def sign_nbt(*lines, color='white'):
    lines = (list(lines) + ['', '', '', ''])[:4]
    msgs = ','.join(json.dumps(l) for l in lines)
    return '{is_waxed:1b,front_text:{has_glowing_text:1b,color:"%s",messages:[%s]}}' % (color, msgs)


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


def name_of(entity):
    return LANG.get('entity.terracraft.' + entity, entity.replace('_', ' ').title())


# ------------------------------------------------------------------------------------------------ buttons
# wall: (axis of the wall, fixed coordinate of the wall blocks, offset toward the room, facing of buttons/signs)
WALLS = {
    'north': ('z', -40, 1, 'south'),
    'south': ('z', 40, -1, 'north'),
    'west': ('x', -40, 1, 'east'),
    'east': ('x', 40, -1, 'west'),
    'arena': ('x', 160, 1, 'east'),
}
SLOTS = [a for a in range(-36, 37, 4) if abs(a) > 4]     # button positions along a wall (the door is in the middle)
BUILD = []


def place_button(wall, index, row, text, function):
    axis, fixed, inward, facing = WALLS[wall]
    along = SLOTS[index]
    y = Y + 1 + 3 * row
    def pos(offset, dy):
        if axis == 'z':
            return f'{along} {y + dy} {fixed + offset}'
        return f'{fixed + offset} {y + dy} {along}'
    BUILD.append(f'setblock {pos(0, 0)} minecraft:command_block[facing={facing}]{{Command:"function showcase:{function}",TrackOutput:0b}}')
    BUILD.append(f'setblock {pos(inward, 0)} minecraft:polished_blackstone_button[face=wall,facing={facing}]')
    BUILD.append(f'setblock {pos(inward, 1)} minecraft:dark_oak_wall_sign[facing={facing}]{sign_nbt(*label(text))}')


def title(wall, text):
    axis, fixed, inward, facing = WALLS[wall]
    for along in (-1, 0, 1):
        pos = f'{along} {Y + 7} {fixed + inward}' if axis == 'z' else f'{fixed + inward} {Y + 7} {along}'
        BUILD.append(f'setblock {pos} minecraft:dark_oak_wall_sign[facing={facing}]{sign_nbt("", text, "", "", color="yellow")}')


def buttons(wall, heading, entries):
    title(wall, heading)
    assert len(entries) <= 2 * len(SLOTS), (wall, len(entries))
    for i, (text, function) in enumerate(entries):
        place_button(wall, i % len(SLOTS), i // len(SLOTS), text, function)


# ------------------------------------------------------------------------------------------------ controls
P = 'execute as @p at @s run '
fn('hub', f'tp @p 0 {Y} 16 180 0')
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

NPCS = ['guide', 'merchant', 'nurse', 'demolitionist', 'arms_dealer', 'dryad', 'clothier', 'goblin_tinkerer', 'wizard', 'steampunker', 'witch_doctor',
        'mechanic', 'pirate']
for npc in NPCS:
    fn(f'npc_{npc}', P + f'terraria npc spawn {npc}')
fn('npc_all', *[P + f'terraria npc spawn {npc}' for npc in NPCS])
fn('npc_remove', 'terraria npc killall')
fn('blood_moon', 'time set night', 'terraria event start blood_moon', 'say Go down to the surface to see it!')
fn('slime_rain', 'time set day', 'terraria event start slime_rain')
fn('goblin_army', 'time set day', 'terraria event start goblin_army', 'say Go down to the surface to fight them!')
fn('pirate_invasion', 'time set day', 'terraria hardmode true', 'terraria event start pirate_invasion',
   'say Pirates! Go down to the surface to fight them - the Flying Dutchman comes once a third of them are beaten.')
fn('frost_legion', 'time set day', 'terraria hardmode true', 'terraria event start frost_legion', 'say The Frost Legion is on the surface!')
fn('stop_event', 'terraria event stop')
fn('meteor', 'time set night', P + 'terraria meteor', 'say A meteor landed near you. Look for the smoke!')
buttons('west', 'EVENTS & NPCS', [
    ('Blood Moon', 'blood_moon'), ('Slime Rain', 'slime_rain'), ('Goblin Army', 'goblin_army'), ('Pirate Invasion', 'pirate_invasion'),
    ('Frost Legion', 'frost_legion'), ('Stop Event', 'stop_event'),
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
tp('arena', 168, Y, 0, -90, 'Boss Arena: press a button on the wall behind you to summon a boss')
tp('bestiary', 0, Y, -50, 180, 'The Bestiary: every enemy, room by room, biome by biome')
tp('boss_hall', 52, Y, 0, -90, 'The Hall of Bosses')
tp('armory', -52, Y, 0, 90, 'The Armory: every armor set, and every item on the walls')
tp('workshop', 0, Y, 112, 0, 'The Workshop: every crafting station and every block')
tp('town', 0, Y, 52, 0, 'Town: NPCs move into these houses')
buttons('east', 'TELEPORTS', [
    ('Forest', 'go_forest'), ('Forest Hills', 'go_forest_hills'), ('Snow', 'go_snow'), ('Boreal Forest', 'go_boreal'),
    ('Desert', 'go_desert'), ('Ocean', 'go_ocean'), ('Beach', 'go_beach'), ('Jungle', 'go_jungle'), ('Bee Hive', 'go_hive'),
    ('Corruption', 'go_corruption'), ('Glowing Mushroom', 'go_mushroom'), ('Dungeon Entrance', 'go_dungeon'),
    ('Inside Dungeon', 'go_dungeon_inside'), ('Underworld', 'go_underworld'), ('Spawn Ground', 'go_spawn_ground'),
    ('The Hallow', 'go_hallow'), ('Bestiary', 'go_bestiary'), ('Hall of Bosses', 'go_boss_hall'), ('Boss Arena', 'go_arena'),
    ('Armory', 'go_armory'), ('Town', 'go_town'), ('Workshop', 'go_workshop'),
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
fn('kit_queen_slime', *give('crystal_assassin_hood', 'crystal_assassin_shirt', 'crystal_assassin_pants', 'volatile_gelatin',
                            'gelatin_crystal*3', 'crystal_shard*20', 'greater_healing_potion*20'))
fn('kit_pirates', *give('cutlass', 'coin_gun', 'gold_coin*50', 'cannonball*30', 'gold_ring', 'lucky_coin', 'discount_card', 'pirate_hat',
                        'pirate_shirt', 'pirate_pants', 'pirate_map*3', 'present*5', 'snow_globe*3'),
   'say Coin Gun: coins are its ammo (copper 25, silver 50, gold 100, platinum 200 damage).')
fn('kit_accessories', *give(*[i for i in ALL_ITEMS if i in {
    'hermes_boots', 'cloud_in_a_bottle', 'shiny_red_balloon', 'lucky_horseshoe', 'band_of_regeneration', 'band_of_starpower',
    'mana_regeneration_band', 'natures_gift', 'aglet', 'anklet_of_the_wind', 'feral_claws', 'obsidian_skull', 'lava_charm', 'cobalt_shield',
    'flipper', 'water_walking_boots', 'toolbelt', 'panic_necklace', 'honey_comb', 'obsidian_horseshoe', 'cloud_in_a_balloon',
    'obsidian_shield', 'obsidian_water_walking_boots', 'lava_waders', 'mana_flower'}]))
fn('kit_summons', *give('slime_crown*3', 'suspicious_looking_eye*3', 'worm_food*3', 'bloody_spine*3', 'abeemination*3',
                        'guide_voodoo_doll', 'goblin_battle_standard*3', 'gelatin_crystal*3', 'mechanical_eye*3', 'mechanical_worm*3',
                        'mechanical_skull*3', 'pirate_map*3', 'snow_globe*3'))
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
    ('Hardmode Ores Kit', 'kit_hm_ores'), ('Hallowed Kit', 'kit_hallowed'), ('Queen Slime Kit', 'kit_queen_slime'), ('Pirate Kit', 'kit_pirates'),
    ('All Accessories', 'kit_accessories'), ('Boss Summons', 'kit_summons'), ('Potions', 'kit_potions'), ('Crafting Stations', 'kit_stations'),
    ('Wings', 'kit_wings'), ('Food Buffs', 'kit_food'), ('Coins', 'kit_coins'), ('Life & Mana Crystals', 'kit_life'),
    ('Clear Inventory', 'clear_inventory'),
])

# ------------------------------------------------------------------------------------------------ boss arena
BOSSES = [('King Slime', 'king_slime'), ('Eye of Cthulhu', 'eye_of_cthulhu'), ('Eater of Worlds', 'eater_of_worlds'),
          ('Brain of Cthulhu', 'brain_of_cthulhu'), ('Queen Bee', 'queen_bee'), ('Skeletron', 'skeletron'), ('Wall of Flesh', 'wall_of_flesh'),
          ('Queen Slime', 'queen_slime'), ('The Twins', 'the_twins'), ('The Destroyer', 'destroyer'), ('Skeletron Prime', 'skeletron_prime')]
for text, boss in BOSSES:
    fn(f'boss_{boss}', 'gamemode survival @p', P + f'terraria boss spawn {boss}')
fn('boss_skeletron', 'gamemode survival @p', 'time set night', P + 'terraria boss spawn skeletron')
fn('boss_eye_of_cthulhu', 'gamemode survival @p', 'time set night', P + 'terraria boss spawn eye_of_cthulhu')
for _mech in ('the_twins', 'destroyer', 'skeletron_prime'):   # Hardmode, night only
    fn(f'boss_{_mech}', 'gamemode survival @p', 'terraria hardmode true', 'time set night', P + f'terraria boss spawn {_mech}')
fn('boss_queen_slime', 'gamemode survival @p', 'terraria hardmode true', P + 'terraria boss spawn queen_slime')
buttons('arena', 'BOSS ARENA', [(t, f'boss_{b}') for t, b in BOSSES] + [
    ('Night', 'night'), ('Day', 'day'), ('Heal Me', 'heal'), ('Kill Bosses', 'kill_bosses'), ('Creative', 'creative'), ('Back to Hub', 'hub')])

# ------------------------------------------------------------------------------------------------ structure helpers
S = []          # blocks
E = []          # display entities (tagged "showcase", rebuilt by showcase:build_displays)
TAG = 'Tags:["showcase"],'


def fill(x0, y0, z0, x1, y1, z1, block, mode=''):
    """/fill split into pieces under the 32768-block limit ("hollow" = a solid box with the inside cleared)."""
    x0, x1 = sorted((x0, x1)); y0, y1 = sorted((y0, y1)); z0, z1 = sorted((z0, z1))
    if mode == 'hollow':
        fill(x0, y0, z0, x1, y1, z1, block)
        if x1 - x0 > 1 and y1 - y0 > 1 and z1 - z0 > 1:
            fill(x0 + 1, y0 + 1, z0 + 1, x1 - 1, y1 - 1, z1 - 1, 'minecraft:air')
        return
    step_x = max(1, min(x1 - x0 + 1, 32))
    step_z = max(1, min(z1 - z0 + 1, 32768 // (step_x * (y1 - y0 + 1))))
    for x in range(x0, x1 + 1, step_x):
        for z in range(z0, z1 + 1, step_z):
            S.append(f'fill {x} {y0} {z} {min(x + step_x - 1, x1)} {y1} {min(z + step_z - 1, z1)} {block}' + (f' {mode}' if mode else ''))


def put(x, y, z, block):
    S.append(f'setblock {x} {y} {z} {block}')


def sign(x, y, z, rotation, *lines, color='white'):
    put(x, y, z, f'minecraft:dark_oak_sign[rotation={rotation}]' + sign_nbt(*lines, color=color))


def wall_sign(x, y, z, facing, *lines, color='white'):
    put(x, y, z, f'minecraft:dark_oak_wall_sign[facing={facing}]' + sign_nbt(*lines, color=color))


def mob(entity, x, y, z, yaw, extra=''):
    E.append(f'summon terracraft:{entity} {x} {y} {z} {{{TAG}NoAI:1b,Invulnerable:1b,PersistenceRequired:1b,Silent:1b,'
             f'Rotation:[{yaw}f,0f]{extra}}}')


def tree(x, z, log, leaves, height=5):
    fill(x - 2, Y + height - 2, z - 2, x + 2, Y + height - 1, z + 2, leaves)
    fill(x - 1, Y + height, z - 1, x + 1, Y + height, z + 1, leaves)
    put(x, Y + height + 1, z, leaves)
    fill(x, Y, z, x, Y + height - 1, z, log)


# 5x7 pixel letters for the giant title
FONT = {
    'T': ['#####', '..#..', '..#..', '..#..', '..#..', '..#..', '..#..'],
    'E': ['#####', '#....', '#....', '####.', '#....', '#....', '#####'],
    'R': ['####.', '#...#', '#...#', '####.', '#.#..', '#..#.', '#...#'],
    'A': ['.###.', '#...#', '#...#', '#####', '#...#', '#...#', '#...#'],
    'C': ['.####', '#....', '#....', '#....', '#....', '#....', '.####'],
    'F': ['#####', '#....', '#....', '####.', '#....', '#....', '#....'],
}


def big_text(text, x_center, y_bottom, z, block):
    width = 6 * len(text) - 1
    x = x_center - width // 2
    for ch in text:
        for row, line in enumerate(FONT[ch]):
            for col, px in enumerate(line):
                if px == '#':
                    put(x + col, y_bottom + 6 - row, z, block)
        x += 6


# ------------------------------------------------------------------------------------------------ Grand Hall
H = 40          # half size: walls at +-40
fill(-H, FLOOR, -H, H, FLOOR, H, 'minecraft:smooth_quartz')
fill(-H + 1, FLOOR, -H + 1, H - 1, FLOOR, -H + 2, 'minecraft:polished_blackstone_bricks')
fill(-H + 1, FLOOR, H - 2, H - 1, FLOOR, H - 1, 'minecraft:polished_blackstone_bricks')
fill(-H + 1, FLOOR, -H + 1, -H + 2, FLOOR, H - 1, 'minecraft:polished_blackstone_bricks')
fill(H - 2, FLOOR, -H + 1, H - 1, FLOOR, H - 1, 'minecraft:polished_blackstone_bricks')
fill(-2, FLOOR, -H, 2, FLOOR, H, 'minecraft:quartz_bricks')          # avenues to the four doors
fill(-H, FLOOR, -2, H, FLOOR, 2, 'minecraft:quartz_bricks')
fill(0, FLOOR, -H, 0, FLOOR, H, 'minecraft:gold_block')
fill(-H, FLOOR, 0, H, FLOOR, 0, 'minecraft:gold_block')
for r in (12, 22):     # rings around the monument
    for a in range(0, 360, 2):
        import math
        put(round(r * math.cos(math.radians(a))), FLOOR, round(r * math.sin(math.radians(a))), 'minecraft:cyan_glazed_terracotta')
for x in range(-32, 33, 8):
    for z in range(-32, 33, 8):
        if abs(x) > 2 and abs(z) > 2:
            put(x, FLOOR, z, 'minecraft:sea_lantern')
# walls: blackstone with quartz pillars, a gilded band, glass clerestory and battlements
for wall in ('{a} {y0} -40 {b} {y1} -40', '{a} {y0} 40 {b} {y1} 40', '-40 {y0} {a} -40 {y1} {b}', '40 {y0} {a} 40 {y1} {b}'):
    S.append('fill ' + wall.format(a=-40, b=40, y0=Y, y1=Y + 9) + ' minecraft:polished_blackstone_bricks')
    S.append('fill ' + wall.format(a=-40, b=40, y0=Y + 10, y1=Y + 10) + ' minecraft:gilded_blackstone')
    S.append('fill ' + wall.format(a=-40, b=40, y0=Y + 11, y1=Y + 13) + ' minecraft:glass')
    S.append('fill ' + wall.format(a=-40, b=40, y0=Y + 14, y1=Y + 14) + ' minecraft:polished_blackstone_bricks')
    for a in range(-38, 39, 4):
        if abs(a) > 4:
            S.append('fill ' + wall.format(a=a, b=a, y0=Y, y1=Y + 9) + ' minecraft:quartz_pillar')
            S.append('setblock ' + ' '.join(wall.format(a=a, b=a, y0=Y + 9, y1=0).split()[:3]) + ' minecraft:sea_lantern')
    for a in range(-40, 41, 2):
        S.append('setblock ' + ' '.join(wall.format(a=a, b=a, y0=Y + 15, y1=Y + 15).split()[:3]) + ' minecraft:polished_blackstone_brick_wall')
for c in (-40, 40):    # corner towers
    for d in (-40, 40):
        fill(c - 2, Y, d - 2, c + 2, Y + 18, d + 2, 'minecraft:deepslate_tiles')
        fill(c - 2, Y + 19, d - 2, c + 2, Y + 19, d + 2, 'minecraft:polished_deepslate_slab')
        put(c, Y + 20, d, 'minecraft:sea_lantern')
        put(c, Y + 21, d, 'minecraft:end_rod')
# doors with arches
for door in ('-2 {y0} -40 2 {y1} -40', '-2 {y0} 40 2 {y1} 40', '-40 {y0} -2 -40 {y1} 2', '40 {y0} -2 40 {y1} 2'):
    S.append('fill ' + door.format(y0=Y, y1=Y + 4) + ' minecraft:air')
    S.append('fill ' + door.format(y0=Y + 5, y1=Y + 5) + ' minecraft:chiseled_quartz_block')
# central monument: a stepped dais and a glowing crystal spire
fill(-6, Y, -6, 6, Y, 6, 'minecraft:polished_deepslate')
fill(-4, Y + 1, -4, 4, Y + 1, 4, 'minecraft:deepslate_tiles')
fill(-2, Y + 2, -2, 2, Y + 2, 2, 'minecraft:polished_blackstone')
fill(-1, Y + 3, -1, 1, Y + 14, 1, 'minecraft:amethyst_block')
fill(0, Y + 3, 0, 0, Y + 15, 0, 'minecraft:sea_lantern')
fill(-1, Y + 15, -1, 1, Y + 15, 1, 'minecraft:gold_block')
put(0, Y + 16, 0, 'minecraft:beacon')
for x, z in ((-1, -1), (-1, 1), (1, -1), (1, 1)):
    put(x, Y + 16, z, 'minecraft:end_rod')
for x, z in ((-4, -4), (-4, 4), (4, -4), (4, 4)):
    put(x, Y + 2, z, 'terracraft:life_crystal_block')
for x, z, rot in ((0, 7, 0), (0, -7, 8), (7, 0, 12), (-7, 0, 4)):
    sign(x, Y, z, rot, 'Welcome to', 'TERRACRAFT', 'SHOWCASE', '', color='yellow')
sign(0, Y, 9, 0, 'Walk through', 'a door to visit', 'a wing. Buttons', 'are on the walls')
big_text('TERRACRAFT', 0, Y + 25, -42, 'minecraft:shroomlight')
big_text('TERRACRAFT', 0, Y + 25, -43, 'minecraft:gold_block')


def bridge(x0, z0, x1, z1):
    fill(x0, FLOOR, z0, x1, FLOOR, z1, 'minecraft:polished_andesite')
    if x0 == x1 or abs(x1 - x0) > abs(z1 - z0):     # runs along x
        fill(x0, Y, z0 - 1, x1, Y, z0 - 1, 'minecraft:glass_pane')
        fill(x0, Y, z1 + 1, x1, Y, z1 + 1, 'minecraft:glass_pane')
        fill(x0, FLOOR, z0 - 1, x1, FLOOR, z1 + 1, 'minecraft:polished_andesite')
    else:
        fill(x0 - 1, Y, z0, x0 - 1, Y, z1, 'minecraft:glass_pane')
        fill(x1 + 1, Y, z0, x1 + 1, Y, z1, 'minecraft:glass_pane')
        fill(x0 - 1, FLOOR, z0, x1 + 1, FLOOR, z1, 'minecraft:polished_andesite')


# ------------------------------------------------------------------------------------------------ Bestiary (north)
ROOMS = [   # name, floor, wall, light, (log, leaves) or None, enemies
    ('Forest', 'minecraft:grass_block', 'minecraft:mossy_stone_bricks', 'minecraft:glowstone', ('minecraft:oak_log', 'minecraft:oak_leaves[persistent=true]'),
     ['green_slime', 'blue_slime', 'purple_slime', 'zombie', 'demon_eye', 'possessed_armor', 'werewolf', 'wraith']),
    ('Caverns', 'minecraft:stone', 'minecraft:deepslate_bricks', 'minecraft:glowstone', None,
     ['red_slime', 'yellow_slime', 'black_slime', 'mother_slime', 'baby_slime', 'skeleton', 'cave_bat', 'giant_worm', 'armored_skeleton', 'giant_bat',
      'mimic']),
    ('Corruption', 'terracraft:corrupt_grass', 'terracraft:ebonstone', 'minecraft:crying_obsidian',
     ('terracraft:ebonwood', 'terracraft:ebonwood_leaves'), ['eater_of_souls', 'devourer', 'corruptor', 'slimer']),
    ('Crimson', 'terracraft:crimson_grass', 'terracraft:crimstone', 'minecraft:shroomlight',
     ('terracraft:shadewood', 'terracraft:shadewood_leaves'), ['crimera', 'face_monster', 'blood_crawler', 'herpling', 'crimslime', 'floaty_gross']),
    ('Jungle', 'terracraft:jungle_grass', 'minecraft:mud_bricks', 'minecraft:ochre_froglight',
     ('minecraft:jungle_log', 'minecraft:jungle_leaves[persistent=true]'), ['jungle_slime', 'jungle_bat', 'hornet', 'bee', 'man_eater', 'snatcher']),
    ('Dungeon', 'terracraft:blue_brick', 'terracraft:green_brick', 'minecraft:sea_lantern', None,
     ['angry_bones', 'dark_caster', 'cursed_skull', 'dungeon_slime', 'dungeon_guardian']),
    ('Underworld', 'terracraft:ash', 'terracraft:hellstone_brick', 'minecraft:shroomlight', None,
     ['imp', 'demon', 'voodoo_demon', 'lava_slime', 'hellbat', 'bone_serpent']),
    ('The Hallow', 'terracraft:hallowed_grass', 'terracraft:pearlstone', 'minecraft:sea_lantern',
     ('terracraft:pearlwood', 'terracraft:hallowed_leaves'), ['pixie', 'unicorn', 'gastropod', 'illuminant_bat', 'illuminant_slime', 'chaos_elemental',
                                                              'crystal_slime', 'bouncy_slime', 'heavenly_slime']),
    ('Blood Moon & Goblin Army', 'minecraft:coarse_dirt', 'minecraft:red_nether_bricks', 'minecraft:shroomlight', None,
     ['blood_zombie', 'drippler', 'goblin_peon', 'goblin_thief', 'goblin_warrior', 'goblin_archer', 'goblin_sorcerer']),
    ('Sky & Meteorite', 'terracraft:meteorite', 'minecraft:polished_blackstone_bricks', 'minecraft:sea_lantern', None,
     ['meteor_head', 'wyvern', 'probe']),
    ('Pirate Invasion', 'minecraft:spruce_planks', 'minecraft:dark_oak_planks', 'minecraft:lantern', None,
     ['pirate_deckhand', 'pirate_corsair', 'pirate_crossbower', 'pirate_deadeye', 'pirate_captain', 'parrot']),
    ('Frost Legion', 'minecraft:snow_block', 'minecraft:packed_ice', 'minecraft:sea_lantern', ('minecraft:spruce_log', 'minecraft:spruce_leaves[persistent=true]'),
     ['mister_stabby', 'snowman_gangsta', 'snow_balla']),
]
FLYERS = {'demon_eye', 'cave_bat', 'giant_bat', 'eater_of_souls', 'corruptor', 'slimer', 'crimera', 'floaty_gross', 'jungle_bat', 'hornet', 'bee',
          'cursed_skull', 'demon', 'voodoo_demon', 'hellbat', 'pixie', 'gastropod', 'illuminant_bat', 'drippler', 'meteor_head', 'wyvern', 'probe',
          'wraith', 'servant_of_cthulhu', 'parrot', 'heavenly_slime'}
fill(-4, FLOOR, -41, 4, FLOOR, -237, 'minecraft:polished_andesite')            # the avenue
fill(-5, FLOOR, -41, -5, FLOOR, -237, 'minecraft:polished_blackstone_bricks')
fill(5, FLOOR, -41, 5, FLOOR, -237, 'minecraft:polished_blackstone_bricks')
for z in range(-44, -237, -4):
    put(0, FLOOR, z, 'minecraft:sea_lantern')
for z in range(-44, -237, -12):
    for x in (-4, 4):
        put(x, Y, z, 'minecraft:polished_blackstone_wall')
        put(x, Y + 1, z, 'minecraft:lantern')
fill(-6, Y, -238, 6, Y + 8, -238, 'minecraft:polished_blackstone_bricks')
wall_sign(0, Y + 3, -237, 'south', '', 'THE BESTIARY', 'every enemy', '', color='yellow')
ZOO_COUNT = 0
for k, (room, floor, wall, light, tree_kind, enemies) in enumerate(ROOMS):
    side = 1 if k % 2 else -1
    z0 = -48 - (k // 2) * 32          # the room's south wall; it reaches 28 blocks north
    zn = z0 - 28
    xi, xo = 6 * side, 64 * side      # inner wall (on the avenue) and outer wall
    fill(xi, FLOOR, z0, xo, Y + 11, zn, wall, 'hollow')
    fill(xi + side, FLOOR, z0 - 1, xo - side, FLOOR, zn + 1, floor)
    fill(xi, Y + 11, z0, xo, Y + 11, zn, 'minecraft:glass')
    for x in range(xi + 4 * side, xo, 4 * side):
        for z in range(z0 - 4, zn, -4):
            put(x, Y + 11, z, light)
    fill(xi, Y + 3, z0 - 2, xi, Y + 6, zn + 2, 'minecraft:glass')            # windows onto the avenue
    aisle = z0 - 14
    fill(xi, Y, aisle - 2, xi, Y + 4, aisle + 2, 'minecraft:air')           # entrance
    wall_sign(xi - side, Y + 6, aisle, 'east' if side < 0 else 'west', '', room.upper(), f'{len(enemies)} enemies', '', color='yellow')
    if 'Hallow' in room or room in ('Sky & Meteorite', 'Pirate Invasion', 'Frost Legion') or 'goblin_peon' in enemies:
        wall_sign(xi - side, Y + 5, aisle, 'east' if side < 0 else 'west', '', '',
                  '(Hardmode)' if 'Hallow' in room or room in ('Pirate Invasion', 'Frost Legion') else '', '')
    for i, enemy in enumerate(enemies):
        row, col = divmod(i, 7)
        cx = (10 + 7 * col) * side
        cz = z0 - 7 if row == 0 else z0 - 21
        fill(cx - 2, FLOOR, cz - 2, cx + 2, Y + 5, cz + 2, 'minecraft:glass', 'hollow')
        fill(cx - 1, FLOOR, cz - 1, cx + 1, FLOOR, cz + 1, floor)
        put(cx, Y + 5, cz, light)
        if row == 0:
            sign(cx, Y, cz - 3, 8, *label(name_of(enemy)))
        else:
            sign(cx, Y, cz + 3, 0, *label(name_of(enemy)))
        mob(enemy, cx + 0.5, Y + (1.2 if enemy in FLYERS else 0), cz + 0.5, 180 if row == 0 else 0)
        ZOO_COUNT += 1
    # scenery at the far end of the aisle
    end = 58 * side
    if tree_kind:
        tree(end, aisle, *tree_kind)
        tree(end - 4 * side, aisle - 4, *tree_kind, height=4)
    elif room == 'Caverns':
        for dz in (-3, 0, 3):
            fill(end, Y, aisle + dz, end, Y + 2 + abs(dz) // 3, aisle + dz, 'minecraft:dripstone_block')
            put(end - side, Y, aisle + dz, 'terracraft:platinum_ore')
        put(end - 2 * side, Y, aisle, 'terracraft:life_crystal_block')
    elif room == 'Dungeon':
        fill(end, Y, aisle - 2, end, Y + 3, aisle + 2, 'terracraft:dungeon_bookshelf')
        fill(end - 2 * side, Y, aisle - 2, end - 2 * side, Y, aisle + 2, 'terracraft:spikes')
        put(end - side, Y, aisle + 4, 'terracraft:locked_gold_chest')
    elif room == 'Underworld':
        fill(end - 2, FLOOR - 1, aisle - 3, end + 2, Y, aisle + 3, 'minecraft:glass', 'hollow')
        fill(end - 1, FLOOR, aisle - 2, end + 1, FLOOR, aisle + 2, 'minecraft:lava')
        fill(end - 3 * side, Y, aisle - 3, end - 3 * side, Y + 3, aisle - 3, 'terracraft:obsidian_brick')
        fill(end - 3 * side, Y, aisle + 3, end - 3 * side, Y + 3, aisle + 3, 'terracraft:obsidian_brick')
        put(end - 4 * side, Y, aisle, 'terracraft:hellforge')
    elif room == 'Sky & Meteorite':
        fill(end - 1, Y, aisle - 1, end + 1, Y + 1, aisle + 1, 'terracraft:meteorite')
        put(end, Y + 2, aisle, 'terracraft:meteorite')
    else:
        put(end, Y, aisle, 'minecraft:campfire')
        fill(end - 2, Y, aisle + 3, end + 2, Y + 2, aisle + 3, 'minecraft:white_wool')
    if room == 'Corruption':
        put(end - 6 * side, Y, aisle, 'terracraft:shadow_orb')
        put(end - 9 * side, Y, aisle, 'terracraft:demon_altar')
    if room == 'Crimson':
        put(end - 6 * side, Y, aisle, 'terracraft:crimson_heart')
        put(end - 9 * side, Y, aisle, 'terracraft:crimson_altar')
    if room == 'Jungle':
        fill(end - 7 * side, Y, aisle - 1, end - 9 * side, Y + 2, aisle + 1, 'terracraft:hive')
        put(end - 8 * side, Y + 1, aisle, 'terracraft:larva')
        fill(xi + side, Y + 10, z0 - 1, xo - side, Y + 10, z0 - 1, 'minecraft:vine[south=true]')

# ------------------------------------------------------------------------------------------------ Hall of Bosses and Arena (east)
bridge(41, -3, 47, 3)
fill(48, FLOOR, -34, 154, Y + 24, 34, 'minecraft:deepslate_tiles', 'hollow')
fill(49, FLOOR, -33, 153, FLOOR, 33, 'minecraft:polished_deepslate')
fill(48, Y + 24, -34, 154, Y + 24, 34, 'minecraft:glass')
for x in range(52, 154, 6):
    for z in (-34, 34):
        fill(x, Y + 4, z, x, Y + 18, z, 'minecraft:red_stained_glass')
for x in range(51, 154, 6):
    for z in (-34, 34):
        put(x, Y + 2, z, 'minecraft:sea_lantern')
        put(x, Y + 12, z, 'minecraft:sea_lantern')
for x in range(54, 154, 8):
    for z in (-24, -8, 8, 24):
        put(x, Y + 24, z, 'minecraft:sea_lantern')
        S.append(f'fill {x} {Y + 20} {z} {x} {Y + 23} {z} minecraft:iron_chain')
        put(x, Y + 19, z, 'minecraft:lantern[hanging=true]')
fill(48, FLOOR, -3, 154, FLOOR, 3, 'minecraft:red_wool')
fill(48, FLOOR, -4, 154, FLOOR, -4, 'minecraft:gold_block')
fill(48, FLOOR, 4, 154, FLOOR, 4, 'minecraft:gold_block')
fill(48, Y, -3, 48, Y + 4, 3, 'minecraft:air')
fill(154, Y, -3, 154, Y + 4, 3, 'minecraft:air')
wall_sign(49, Y + 6, 0, 'east', '', 'HALL OF BOSSES', '', '', color='yellow')
BOSS_HALL = [   # (boss entities with offsets, name, summoned with, flying height)
    ([('king_slime', 0, 0)], 'King Slime', 'Slime Crown', 0),
    ([('eye_of_cthulhu', 0, 0)], 'Eye of Cthulhu', 'Suspicious Looking Eye', 3),
    ([('eater_of_worlds', 0, 0)], 'Eater of Worlds', 'Worm Food / Shadow Orbs', 2),
    ([('brain_of_cthulhu', 0, 0), ('creeper', -3, 2), ('creeper', 3, 2)], 'Brain of Cthulhu', 'Bloody Spine / Crimson Hearts', 3),
    ([('queen_bee', 0, 0)], 'Queen Bee', 'Abeemination / Larva', 2),
    ([('skeletron', 0, 0), ('skeletron_hand', -3, -1), ('skeletron_hand', 3, -1)], 'Skeletron', 'curse the Old Man', 4),
    ([('wall_of_flesh_eye', -2.5, 0), ('wall_of_flesh_eye', 2.5, 0), ('the_hungry', 0, 2)], 'Wall of Flesh', 'Guide Voodoo Doll in lava', 2),
    ([('queen_slime', 0, 0), ('heavenly_slime', -3, 2)], 'Queen Slime', 'Gelatin Crystal (Hallow)', 0),
    ([('retinazer', -2.5, 0), ('spazmatism', 2.5, 0)], 'The Twins', 'Mechanical Eye (Hardmode)', 3),
    ([('destroyer', 0, 0), ('probe', -3, 1), ('probe', 3, 1)], 'The Destroyer', 'Mechanical Worm (Hardmode)', 3),
    ([('skeletron_prime', 0, 0), ('prime_cannon', -3.5, 0), ('prime_laser', 3.5, 0), ('prime_saw', -3, -3), ('prime_vice', 3, -3)],
     'Skeletron Prime', 'Mechanical Skull (Hardmode)', 5),
    ([('flying_dutchman', 0, 0)], 'Flying Dutchman', 'Pirate Invasion', 6),
]
for i, (parts, name, summon, lift) in enumerate(BOSS_HALL):
    north = i < 6
    x = 58 + 17 * (i % 6)
    z = -20 if north else 20
    fill(x - 5, Y, z - 5, x + 5, Y, z + 5, 'minecraft:polished_blackstone_bricks')
    fill(x - 4, Y + 1, z - 4, x + 4, Y + 1, z + 4, 'minecraft:smooth_quartz')
    fill(x - 5, Y + 1, z - 5 if north else z + 5, x + 5, Y + 1, z - 5 if north else z + 5, 'minecraft:gold_block')
    for cx in (x - 5, x + 5):
        for cz in (z - 5, z + 5):
            fill(cx, Y + 1, cz, cx, Y + 3, cz, 'minecraft:quartz_pillar')
            put(cx, Y + 4, cz, 'minecraft:sea_lantern')
    front = z + 6 if north else z - 6
    sign(x, Y, front, 0 if north else 8, '', name.upper(), summon if len(summon) <= 15 else '', '', color='yellow')
    if len(summon) > 15:
        sign(x + 1, Y, front, 0 if north else 8, *label(summon))
    for entity, dx, dz in parts:
        dz = dz if north else -dz
        mob(entity, x + 0.5 + dx, Y + 2 + lift, z + 0.5 + dz, 0 if north else 180)
# the arena
bridge(155, -3, 159, 3)
fill(160, FLOOR, -44, 244, Y + 20, 44, 'minecraft:glass', 'hollow')
fill(160, FLOOR, -44, 244, FLOOR, 44, 'minecraft:smooth_stone')
fill(160, Y, -40, 160, Y + 8, 40, 'minecraft:polished_blackstone_bricks')
fill(160, Y, -2, 160, Y + 3, 2, 'minecraft:air')
for x in range(168, 244, 10):
    for z in range(-40, 41, 10):
        put(x, FLOOR, z, 'minecraft:sea_lantern')

# ------------------------------------------------------------------------------------------------ Armory (west)
bridge(-47, -3, -41, 3)
fill(-48, FLOOR, -30, -164, Y + 14, 30, 'minecraft:stone_bricks', 'hollow')
fill(-49, FLOOR, -29, -163, FLOOR, 29, 'minecraft:dark_oak_planks')
fill(-48, Y + 14, -30, -164, Y + 14, 30, 'minecraft:glass')
fill(-48, FLOOR, -2, -164, FLOOR, 2, 'minecraft:red_wool')
fill(-48, Y, -3, -48, Y + 4, 3, 'minecraft:air')
for x in range(-52, -164, -8):
    for z in (-30, 30):
        fill(x, Y, z, x, Y + 13, z, 'minecraft:chiseled_stone_bricks')
    for z in (-14, 14):
        put(x, Y + 14, z, 'minecraft:glowstone')
    put(x, Y + 14, 0, 'minecraft:glowstone')
wall_sign(-49, Y + 6, 0, 'west', '', 'THE ARMORY', '', '', color='yellow')
ALL_ITEMS = sorted(f[:-5] for f in os.listdir(ITEMS_DIR))
known = set(ALL_ITEMS)


def first(*names):
    for n in names:
        if n in known:
            return n
    return None


SET_NAMES = ['wood', 'copper', 'tin', 'iron', 'lead', 'silver', 'tungsten', 'gold', 'platinum', 'jungle', 'meteor', 'shadow', 'crimson', 'molten',
             'cobalt', 'palladium', 'mythril', 'orichalcum', 'adamantite', 'titanium', 'hallowed']
WEAPON = {'wood': 'wooden_sword', 'shadow': 'lights_bane', 'crimson': 'blood_butcherer', 'jungle': 'blade_of_grass', 'molten': 'fiery_greatsword',
          'meteor': 'space_gun', 'hallowed': 'excalibur'}
TOOL = {'shadow': 'nightmare_pickaxe', 'crimson': 'deathbringer_pickaxe', 'molten': 'molten_pickaxe', 'hallowed': 'pickaxe_axe', 'jungle': 'bee_keeper'}
for i, s in enumerate(SET_NAMES):
    head = first(f'{s}_helmet', f'{s}_hat', f'{s}_mask')
    chest = first(f'{s}_chainmail', f'{s}_breastplate', f'{s}_scalemail', f'{s}_shirt', f'{s}_suit', f'{s}_plate_mail')
    legs = first(f'{s}_greaves', f'{s}_leggings', f'{s}_pants')
    weapon = first(WEAPON.get(s, ''), f'{s}_broadsword', f'{s}_sword', f'{s}_shortsword')
    tool = first(TOOL.get(s, ''), f'{s}_pickaxe', f'{s}_bow', f'{s}_repeater')
    north = i % 2 == 0
    x = -56 - 10 * (i // 2)
    z = -10 if north else 10
    fill(x - 1, Y, z - 1, x + 1, Y, z + 1, 'minecraft:polished_andesite')
    put(x, Y, z, 'minecraft:gold_block' if s in ('gold', 'hallowed') else 'minecraft:polished_diorite')
    equip = []
    for slot, item in (('head', head), ('chest', chest), ('legs', legs), ('mainhand', weapon), ('offhand', tool)):
        if item:
            equip.append(f'{slot}:{{id:"terracraft:{item}"}}')
    E.append(f'summon armor_stand {x + 0.5} {Y + 1} {z + 0.5} {{{TAG}ShowArms:1b,NoBasePlate:1b,Invulnerable:1b,Rotation:[{0 if north else 180}f,0f],'
             f'Pose:{{RightArm:[-20f,0f,10f],LeftArm:[-20f,0f,-10f]}},equipment:{{{",".join(equip)}}}}}')
    title_text = {'shadow': 'Shadow', 'crimson': 'Crimson', 'hallowed': 'Hallowed'}.get(s, s.title())
    sign(x, Y, z + 2 if north else z - 2, 0 if north else 8, '', title_text, 'armor', '')


# every item in a glow frame on the walls, grouped
def category(item):
    if any(k in item for k in ('wings', 'boots', 'balloon', 'bottle', 'emblem', 'charm', 'shield', 'band_', 'necklace', 'horseshoe', 'claws',
                               'skull', 'aglet', 'anklet', 'shackle', 'flipper', 'toolbelt', 'glove', 'waders', 'mana_flower', 'natures_gift',
                               'honey_comb')) and 'skull' != item[-5:] or item in ('obsidian_skull', 'mechanical_skull'):
        return 'Accessories' if item != 'mechanical_skull' else 'Boss summons'
    if any(item.endswith(k) for k in ('_helmet', '_hat', '_mask', '_chainmail', '_breastplate', '_scalemail', '_shirt', '_suit', '_plate_mail',
                                      '_greaves', '_leggings', '_pants')):
        return 'Armor'
    if any(k in item for k in ('pickaxe', '_axe', 'hammer', 'hamaxe', 'pwnhammer', 'clentaminator', 'axe_')) or item.endswith('_axe'):
        return 'Tools'
    if any(k in item for k in ('potion', 'crystal', 'fruit', 'powder')) and 'block' not in item:
        return 'Potions & upgrades'
    if any(k in item for k in ('crown', 'eye', 'worm', 'spine', 'abeemination', 'voodoo', 'standard', 'mechanical')):
        return 'Boss summons'
    if any(k in item for k in ('sword', 'bow', 'repeater', 'gun', 'blaster', 'musket', 'pistol', 'staff', 'wand', 'scepter', 'missile', 'bolt',
                               'spear', 'trident', 'boomerang', 'flail', 'excalibur', 'muramasa', 'blade', 'bane', 'butcherer', 'scythe',
                               'thorn', 'flamelash', 'fire', 'book', 'shuriken', 'knife', 'fury', 'rifle', 'arrow', 'ball', 'cutlass',
                               'yoyo', 'grenade', 'dynamite', 'bomb', 'undertaker', 'keeper', 'knees')):
        return 'Weapons'
    if any(k in item for k in ('ore', 'brick', 'stone', 'grass', 'wood', 'leaves', 'sand', 'ash', 'hive', 'larva', 'altar', 'orb', 'heart',
                               'anvil', 'forge', 'bench', 'workshop', 'bookshelf', 'chest', 'spikes', 'meteorite', 'mushroom', 'plant',
                               'block')) and not item.startswith('raw_'):
        return 'Blocks'
    return 'Materials & other'


GROUPS = {}
for item in ALL_ITEMS:
    GROUPS.setdefault(category(item), []).append(item)
WALL_ORDER = [('north', ['Weapons', 'Tools', 'Armor']), ('south', ['Accessories', 'Potions & upgrades', 'Boss summons', 'Materials & other', 'Blocks'])]
for wall, groups in WALL_ORDER:
    z_frame = -29 if wall == 'north' else 29
    facing = 3 if wall == 'north' else 2
    x = -54
    for group in groups:
        items = GROUPS.get(group, [])
        wall_sign(x, Y + 9, z_frame, 'south' if wall == 'north' else 'north', '', group.upper(), f'{len(items)} items', '', color='yellow')
        for n, item in enumerate(items):
            col, row = divmod(n, 7)
            fx = x - col
            fy = Y + 7 - row
            E.append(f'summon glow_item_frame {fx} {fy} {z_frame} {{{TAG}Facing:{facing}b,Fixed:1b,Invulnerable:1b,'
                     f'Item:{{id:"terracraft:{item}",count:1}}}}')
        x -= (len(items) + 6) // 7 + 2
    assert x > -164, (wall, x)
# item vault: chests holding one of every item at the far end
VAULT = []
for n, item in enumerate(ALL_ITEMS):
    chest, slot = divmod(n, 27)
    z = -16 + 2 * chest
    if slot == 0:
        put(-160, Y, z, 'minecraft:chest[facing=east]')
    VAULT.append(f'item replace block -160 {Y} {z} container.{slot} with terracraft:{item}')
sign(-158, Y, -18, 12, '', 'ITEM VAULT', 'one of every', 'item')

# ------------------------------------------------------------------------------------------------ Town, Workshop, Block Garden (south)
bridge(-3, 41, 3, 47)
fill(-60, FLOOR, 48, 60, FLOOR, 170, 'minecraft:grass_block')
for x in (-60, 60):
    fill(x, Y, 48, x, Y, 170, 'minecraft:oak_fence')
fill(-60, Y, 170, 60, Y, 170, 'minecraft:oak_fence')
fill(-60, Y, 48, 60, Y, 48, 'minecraft:oak_fence')
fill(-2, Y, 48, 2, Y, 48, 'minecraft:air')
fill(-2, FLOOR, 48, 2, FLOOR, 170, 'minecraft:dirt_path')
for z in range(52, 170, 8):
    for x in (-4, 4):
        put(x, Y, z, 'minecraft:oak_fence')
        put(x, Y + 1, z, 'minecraft:lantern')
HOUSES = []
for z0 in (54, 70, 86):
    fill(-58, FLOOR, z0 + 9, 58, FLOOR, z0 + 10, 'minecraft:dirt_path')
    for x0 in (-56, -44, -32, 8, 20, 32, 44):
        if x0 == 44 and z0 == 86:
            continue
        x1, z1 = x0 + 8, z0 + 7
        fill(x0, FLOOR, z0, x1, Y + 4, z1, 'minecraft:oak_planks', 'hollow')
        fill(x0, FLOOR, z0, x1, FLOOR, z1, 'minecraft:stone_bricks')
        for cx in (x0, x1):
            for cz in (z0, z1):
                fill(cx, FLOOR, cz, cx, Y + 4, cz, 'minecraft:stripped_spruce_log')
        fill(x0 - 1, Y + 5, z0 - 1, x1 + 1, Y + 5, z1 + 1, 'minecraft:dark_oak_slab')
        fill(x0 + 1, Y + 5, z0 + 1, x1 - 1, Y + 5, z1 - 1, 'minecraft:dark_oak_planks')
        fill(x0 + 2, Y + 1, z1, x0 + 2, Y + 2, z1, 'minecraft:glass_pane')
        fill(x1 - 2, Y + 1, z1, x1 - 2, Y + 2, z1, 'minecraft:glass_pane')
        put(x0 + 4, Y, z1, 'minecraft:oak_door[half=lower,facing=south]')
        put(x0 + 4, Y + 1, z1, 'minecraft:oak_door[half=upper,facing=south]')
        put(x0 + 1, Y, z0 + 1, 'minecraft:crafting_table')
        put(x0 + 2, Y, z0 + 1, 'minecraft:oak_stairs[facing=west]')
        put(x1 - 1, Y, z0 + 1, 'minecraft:lantern')
        put(x0 + 4, Y + 3, z0 + 3, 'minecraft:lantern[hanging=true]')
        put(x0 + 6, Y, z0 + 1, 'minecraft:red_bed[facing=north,part=head]')
        put(x0 + 6, Y, z0 + 2, 'minecraft:red_bed[facing=north,part=foot]')
        put(x0 + 2, Y, z1 + 1, 'minecraft:potted_poppy')
        HOUSES.append((x0 + 4, Y, z0 + 3))
sign(0, Y, 50, 0, '', 'TOWN', 'NPCs move into', 'these houses')
# Workshop: every crafting station on a pedestal
STATIONS = [('terracraft:work_bench', 'Work Bench'), ('minecraft:furnace', 'Furnace'), ('terracraft:iron_anvil', 'Iron Anvil'),
            ('terracraft:lead_anvil', 'Lead Anvil'), ('minecraft:brewing_stand', 'Alchemy (Bottle)'), ('minecraft:loom', 'Loom'),
            ('terracraft:demon_altar', 'Demon Altar'), ('terracraft:crimson_altar', 'Crimson Altar'), ('terracraft:hellforge', 'Hellforge'),
            ('terracraft:tinkerers_workshop', "Tinkerer's Workshop"), ('terracraft:mythril_anvil', 'Mythril Anvil'),
            ('terracraft:orichalcum_anvil', 'Orichalcum Anvil'), ('terracraft:adamantite_forge', 'Adamantite Forge'),
            ('terracraft:titanium_forge', 'Titanium Forge')]
fill(-52, FLOOR, 104, 52, FLOOR, 124, 'minecraft:polished_andesite')
fill(-52, Y + 7, 104, 52, Y + 7, 124, 'minecraft:spruce_slab[type=bottom]')
for x in range(-52, 53, 8):
    for z in (104, 124):
        fill(x, Y, z, x, Y + 6, z, 'minecraft:stripped_spruce_log')
for x in range(-48, 53, 8):
    put(x, Y + 6, 114, 'minecraft:lantern[hanging=true]')
sign(0, Y, 102, 0, '', 'THE WORKSHOP', 'every crafting', 'station', color='yellow')
for i, (block, name) in enumerate(STATIONS):
    x = -42 + 12 * (i % 7)
    z = 108 if i < 7 else 118
    fill(x - 1, Y, z - 1, x + 1, Y, z + 1, 'minecraft:smooth_quartz')
    put(x, Y + 1, z, block)
    sign(x, Y, z + 2, 0, *label(name))
# Block Garden: every TerraCraft block on a pedestal
BLOCKS = sorted(k[17:] for k in LANG if k.startswith('block.terracraft.') and '.tooltip' not in k and k.count('.') == 2)
PLANT_SOIL = {'vile_mushroom': 'terracraft:corrupt_grass', 'vicious_mushroom': 'terracraft:crimson_grass',
              'jungle_spores_plant': 'terracraft:jungle_grass', 'larva': 'terracraft:hive'}
sign(0, Y, 128, 0, '', 'BLOCK GARDEN', 'every block', '', color='yellow')
for i, block in enumerate(BLOCKS):
    col, row = i % 16, i // 16
    x = -45 + 6 * col
    z = 132 + 7 * row
    put(x, Y, z, PLANT_SOIL.get(block, 'minecraft:polished_blackstone'))
    put(x, Y + 1, z, f'terracraft:{block}')
    sign(x, Y, z + 1, 0, *label(LANG.get(f'block.terracraft.{block}', block)))
assert 132 + 7 * ((len(BLOCKS) - 1) // 16) + 1 < 170

# ------------------------------------------------------------------------------------------------ functions


def forceload_tiles():
    x0, z0, x1, z1 = AREA
    out = []
    for cx in range(x0 // 16, x1 // 16 + 1, 15):
        for cz in range(z0 // 16, z1 // 16 + 1, 15):
            out.append(f'forceload add {cx * 16} {cz * 16} {min(cx + 14, x1 // 16) * 16} {min(cz + 14, z1 // 16) * 16}')
    return out


CLEAR = []
x0, z0, x1, z1 = AREA
_saved = S
S = CLEAR
fill(x0, FLOOR - 3, z0, x1, Y + 30, z1, 'minecraft:air')
S = _saved
fn('prepare', 'say Loading the showcase area... the build starts in 10 seconds.', *forceload_tiles(), 'schedule function showcase:build 200t')
fn('build', 'kill @e[tag=showcase]', f'kill @e[type=!player,x={x0},y={FLOOR - 3},z={z0},dx={x1 - x0},dy=40,dz={z1 - z0}]', *CLEAR, *S, *BUILD,
   *VAULT, 'function showcase:build_displays', f'setworldspawn 0 {Y} 16', 'forceload remove all', 'time set noon', 'function showcase:hub',
   'say Showcase built! Walk through the four doors of the Grand Hall.')
fn('build_displays', 'kill @e[tag=showcase]', *E)
fn('build_zoo', 'function showcase:build_displays')
fn('build_underworld', 'fill 34 -52 34 46 -44 46 minecraft:obsidian hollow', 'fill 35 -51 35 45 -45 45 minecraft:air',
   'fill 34 -50 35 34 -46 45 minecraft:glass', 'fill 46 -50 35 46 -46 45 minecraft:glass', 'fill 35 -50 34 45 -46 34 minecraft:glass',
   'fill 35 -50 46 45 -46 46 minecraft:glass', 'setblock 40 -45 40 minecraft:glowstone',
   'setblock 40 -51 44 minecraft:command_block{Command:"function showcase:hub",TrackOutput:0b}',
   'setblock 40 -50 44 minecraft:polished_blackstone_button[face=floor,facing=north]',
   'setblock 40 -51 42 minecraft:dark_oak_sign[rotation=8]' + sign_nbt('', 'Back to Hub', '', ''))
ENEMIES = [e for room in ROOMS for e in room[5]]


def check_items():
    bad = []
    for name, lines in FUNCS.items():
        for line in lines:
            for token in line.replace('"', ' ').replace(',', ' ').split():
                if token.startswith('terracraft:'):
                    thing = token.split(':')[1].split('[')[0].split('{')[0]
                    if thing not in known and thing not in BLOCKS and f'entity.terracraft.{thing}' not in LANG \
                            and thing not in ('wyvern', 'creeper', 'skeletron_hand', 'wall_of_flesh_eye'):
                        bad.append((name, thing))
    if bad:
        sys.exit(f'unknown ids: {sorted(set(bad))}')


def main():
    check_items()
    world = sys.argv[1]
    out = os.path.join(world, 'datapacks/showcase/data/showcase/function')
    os.makedirs(out, exist_ok=True)
    for old in os.listdir(out):
        os.remove(os.path.join(out, old))
    with open(os.path.join(world, 'datapacks/showcase/pack.mcmeta'), 'w') as f:
        json.dump({'pack': {'description': 'TerraCraft showcase controls', 'min_format': [107, 1], 'max_format': 107}}, f)
    for name, lines in FUNCS.items():
        with open(os.path.join(out, name + '.mcfunction'), 'w') as f:
            f.write('\n'.join(lines) + '\n')
    print(f'{len(FUNCS)} functions, {len(BUILD) // 3} buttons, {len(ENEMIES)} enemies, {len(E)} display entities, {len(ALL_ITEMS)} items, '
          f'{len(BLOCKS)} blocks, {len(S)} build commands, {len(HOUSES)} houses')


if __name__ == '__main__':
    main()
