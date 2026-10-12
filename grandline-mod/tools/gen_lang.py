#!/usr/bin/env python3
"""Generates en_us language fragments from the Java sources (abilities, fruits, styles, islands...) plus hand-written text.
Run from the grandline-mod directory: python3 tools/gen_lang.py"""
import json, os, re, glob

SRC = 'src/main/java/com/grandline'
OUT = 'src/main/lang/en_us'
os.makedirs(OUT, exist_ok=True)

def title(s):
    return ' '.join(w.capitalize() for w in re.split(r'[_\s]+', s) if w)

def read(p):
    return open(os.path.join(SRC, p)).read()

lang = {}

# ---------------- races ----------------
race_desc = {
 'human': 'Adaptable and resilient: learns any style and toughens up when badly hurt. Powers: Second Wind, Rallying Cry, Dirty Trick, Last Stand.',
 'fishman': 'Breathes water, swims fast, far stronger in the sea; dries out in hot sun. Fish-Man Karate from birth. Powers: Riptide Dash, Droplet Volley, Buraikan, Water Heart.',
 'merfolk': "Unmatched swimmers who breathe water and are hard to hit in it; slow on land. Powers: Tail Whirl, Siren Song, Call of the Sea (sea creatures obey), Mermaid's Embrace.",
 'mink': 'Fast beast folk with Electro in their fur (it shocks attackers). Powers: Electro, Electro Pounce, Electro Storm, and Sulong under the full moon.',
 'sky_people': 'Small wings to glide; quicker in thin air; Mantra (Observation) affinity; an Impact Dial soaks part of every blow. Powers: Jet, Flame, Impact and Reject Dials.',
 'long_arm': 'Arms with an extra joint: longer reach and harder blows from range. Powers: Long Grab, Extended Flurry, Windmill Sweep, High Snatch.',
 'long_leg': 'Very long legs: fast, high-jumping, long-reaching kicks. Powers: Stilt Kick, Sky Vault, Crescent Sweep, Heaven Drop.',
 'three_eye': 'A third eye that sees a moment ahead: Observation affinity, sometimes dodges blows on instinct. Powers: Foresight, Piercing Gaze, Precognitive Counter.',
 'tontatta': 'Tiny, very fast and immensely strong for their size; hard for big foes to hit. Powers: Blur Dash, Vanish, Mighty Lift, Tiny Fist Flurry.',
 'giant': 'Scaled giant (about 3.5 blocks): huge health and reach, slower, bullets barely sting; no rowboats. Powers: Giant Stomp, Battle Cry, Elbaph Smash, Boulder Hurl.',
 'lunarian': 'Black-winged people with a body of fire: glide, shrug off flames, immense toughness. Powers: Flame Wings, Blaze Burst, Wing Dive, Lunarian Fortitude. (NPC race.)',
 'buccaneer': 'A towering, powerful lineage that shrugs off blows. Powers: Bull Rush, Bone Crusher, Ground Breaker, Unchained. (NPC race.)',
 'snakeneck': 'Long-necked folk whose swaying neck makes them hard to hit. Powers: Neck Lash, Coil Strike, Serpent Sway. (NPC race.)',
 'reefborn': 'ORIGINAL RACE: coral-skinned islanders; breathe water and regenerate in it, coral skin pricks attackers. Powers: Coral Spikes, Reef Shell, Tidepool, Coral Lance.',
 'stormwing': 'ORIGINAL RACE: light feathered sky-folk; glide, quicker in storms, shrug off wind and lightning; fragile. Powers: Gale Burst, Updraft, Feather Volley, Thunderhead.',
 'stonehide': 'ORIGINAL RACE: mountain folk with stone plating; blades glance off; slow, sinks in water. Powers: Stone Skin, Rockslide Charge, Earthen Spine, Living Fortress.',
 'abyssal': 'ORIGINAL RACE: deep-sea people with glowing eyes; see in the dark, breathe water, deadlier in darkness, weak in sun. Powers: Lure Light, Ink Cloud, Crushing Depths, Abyssal Maw.',
 'emberkin': 'ORIGINAL RACE: volcanic islanders with ember veins; immune to fire, blows can ignite; sluggish in water and cold. Powers: Ember Flare, Magma Horns, Cinder Shot, Eruption.',
 'vinefolk': 'ORIGINAL RACE: plant-blooded people with leaf hair; heal in sunlight, burn easily. Powers: Vine Lash, Entangle, Photosynthesis, Thorn Burst.',
 'whalefolk': 'ORIGINAL RACE: huge, gentle sea people; enormous lungs and health, strong and fast in water. Powers: Blowhole Geyser, Whale Song, Breach Slam, Sounding Dive.',
 'starborn': 'ORIGINAL RACE: star-freckled wanderers; quicker and stronger under the night sky, see in the dark. Powers: Starfall, Astral Step, Constellation Bind, Supernova.',
}
for r, d in race_desc.items():
    lang[f'race.grandline.{r}'] = {'fishman': 'Fish-Man', 'sky_people': 'Sky People', 'long_arm': 'Long-Arm Tribe', 'long_leg': 'Long-Leg Tribe', 'three_eye': 'Three-Eye Tribe'}.get(r, title(r))
    lang[f'race.grandline.{r}.desc'] = d

for f, n in {'pirate': 'Pirate', 'marine': 'Marine', 'revolutionary': 'Revolutionary', 'bounty_hunter': 'Bounty Hunter', 'civilian': 'Civilian', 'world_government': 'World Government', 'cipher_pol': 'Cipher Pol', 'wild': 'Wild'}.items():
    lang[f'faction.grandline.{f}'] = n
for b in ['east', 'west', 'north', 'south']:
    lang[f'blue.grandline.{b}'] = f'{b.capitalize()} Blue'
for s in ['strength', 'endurance', 'agility', 'willpower', 'technique']:
    lang[f'stat.grandline.{s}'] = s.capitalize()
for z, n in {'east_blue': 'East Blue', 'west_blue': 'West Blue', 'north_blue': 'North Blue', 'south_blue': 'South Blue', 'calm_belt': 'Calm Belt', 'paradise': 'Grand Line — Paradise', 'new_world': 'Grand Line — New World', 'red_line': 'Red Line', 'world_edge': 'Edge of the World'}.items():
    lang[f'zone.grandline.{z}'] = n

# ---------------- fruits ----------------
fruit_names = {
 'mera': ('Mera Mera no Mi', 'Flame-Flame Fruit: become and control fire (Logia).'),
 'hie': ('Hie Hie no Mi', 'Ice-Ice Fruit: become and control ice; freeze the sea (Logia).'),
 'goro': ('Goro Goro no Mi', 'Rumble-Rumble Fruit: become lightning (Logia).'),
 'pika': ('Pika Pika no Mi', 'Glint-Glint Fruit: become light (Logia).'),
 'magu': ('Magu Magu no Mi', 'Magma-Magma Fruit: become magma (Logia).'),
 'suna': ('Suna Suna no Mi', 'Sand-Sand Fruit: become sand and dry things out (Logia).'),
 'moku': ('Moku Moku no Mi', 'Smoke-Smoke Fruit (Logia).'), 'yuki': ('Yuki Yuki no Mi', 'Snow-Snow Fruit (Logia).'),
 'gasu': ('Gasu Gasu no Mi', 'Gas-Gas Fruit (Logia).'), 'yami': ('Yami Yami no Mi', 'Dark-Dark Fruit (Logia).'),
 'numa': ('Numa Numa no Mi', 'Swamp-Swamp Fruit (Logia).'), 'mori': ('Mori Mori no Mi', 'Forest-Forest Fruit (Logia).'),
 'gomu': ('Gomu Gomu no Mi', 'Gum-Gum Fruit: a rubber body. Its true nature awakens later...'),
 'bara': ('Bara Bara no Mi', 'Chop-Chop Fruit: split your body apart; immune to cuts.'),
 'ope': ('Ope Ope no Mi', 'Op-Op Fruit: create a ROOM and rearrange anything inside it.'),
 'hana': ('Hana Hana no Mi', 'Flower-Flower Fruit.'), 'ito': ('Ito Ito no Mi', 'String-String Fruit.'), 'gura': ('Gura Gura no Mi', 'Tremor-Tremor Fruit.'),
 'bari': ('Bari Bari no Mi', 'Barrier-Barrier Fruit.'), 'bomu': ('Bomu Bomu no Mi', 'Bomb-Bomb Fruit.'), 'supa': ('Supa Supa no Mi', 'Dice-Dice Fruit.'),
 'nikyu': ('Nikyu Nikyu no Mi', 'Paw-Paw Fruit.'), 'mochi': ('Mochi Mochi no Mi', 'Mochi-Mochi Fruit (Special Paramecia).'), 'horo': ('Horo Horo no Mi', 'Hollow-Hollow Fruit.'),
 'doru': ('Doru Doru no Mi', 'Wax-Wax Fruit.'), 'soru': ('Soru Soru no Mi', 'Soul-Soul Fruit.'), 'hobi': ('Hobi Hobi no Mi', 'Hobby-Hobby Fruit.'), 'toki': ('Toki Toki no Mi', 'Time-Time Fruit.'),
 'ushi_bison': ('Ushi Ushi no Mi, Model: Bison', 'Ox-Ox Fruit (Zoan).'), 'inu_wolf': ('Inu Inu no Mi, Model: Wolf', 'Dog-Dog Fruit, Model: Wolf: transform into a wolf-man or a great wolf (Zoan).'),
 'neko_leopard': ('Neko Neko no Mi, Model: Leopard', 'Cat-Cat Fruit (Zoan).'), 'zou_elephant': ('Zou Zou no Mi', 'Elephant-Elephant Fruit (Zoan).'),
 'ryu_allosaurus': ('Ryu Ryu no Mi, Model: Allosaurus', 'Ancient Zoan.'), 'ryu_pteranodon': ('Ryu Ryu no Mi, Model: Pteranodon', 'Ancient Zoan.'), 'ryu_spinosaurus': ('Ryu Ryu no Mi, Model: Spinosaurus', 'Ancient Zoan.'),
 'tori_phoenix': ('Tori Tori no Mi, Model: Phoenix', 'Mythical Zoan.'), 'uo_seiryu': ('Uo Uo no Mi, Model: Seiryu', 'Mythical Zoan.'), 'hebi_orochi': ('Hebi Hebi no Mi, Model: Yamata no Orochi', 'Mythical Zoan.'),
 'kaze': ('Kaze Kaze no Mi', 'ORIGINAL — Gale-Gale Fruit: become the wind. Fills sails, cuts with air, raises cyclones (Logia).'),
 'shio': ('Shio Shio no Mi', 'ORIGINAL — Salt-Salt Fruit.'), 'kagami': ('Kagami Kagami no Mi', 'ORIGINAL — Mirror-Mirror Fruit.'), 'tako_kraken': ('Tako Tako no Mi, Model: Kraken', 'ORIGINAL — Mythical Zoan.'),
 'rasen': ('Rasen Rasen no Mi', 'ORIGINAL — Spiral-Spiral Fruit.'), 'kane': ('Kane Kane no Mi', 'ORIGINAL — Bell-Bell Fruit.'), 'tsuchi': ('Tsuchi Tsuchi no Mi', 'ORIGINAL — Soil-Soil Fruit (Logia).'),
 'kiri': ('Kiri Kiri no Mi', 'ORIGINAL — Fog-Fog Fruit (Logia).'), 'tenbin': ('Tenbin Tenbin no Mi', 'ORIGINAL — Scale-Scale Fruit.'), 'hari': ('Hari Hari no Mi', 'ORIGINAL — Needle-Needle Fruit.'),
 'hoshi': ('Hoshi Hoshi no Mi', 'ORIGINAL — Star-Star Fruit (Logia).'), 'uzu': ('Uzu Uzu no Mi', 'ORIGINAL — Whirl-Whirl Fruit.'),
}
for k, (n, d) in fruit_names.items():
    lang[f'fruit.grandline.{k}'] = n
    lang[f'fruit.grandline.{k}.desc'] = d
for t in ['logia', 'paramecia', 'special_paramecia', 'zoan', 'ancient_zoan', 'mythical_zoan']:
    lang[f'fruit_type.grandline.{t}'] = title(t)

# ---------------- abilities (parsed from source) ----------------
ability_names = {
 'fire_fist': 'Fire Fist', 'fire_gun': 'Fire Gun', 'flame_dash': 'Flame Dash', 'flame_ring': 'Flame Commandment', 'great_flame_emperor': 'Great Flame Emperor',
 'ice_age': 'Ice Age', 'ice_saber': 'Ice Saber', 'ice_wall': 'Ice Wall', 'partisan': 'Partisan', 'ice_time': 'Ice Time',
 'el_thor': 'El Thor', 'vari': 'Vari Discharge', 'sango': 'Sango', 'lightning_step': 'Lightning Step', 'raigo': 'Raigo',
 'pistol': 'Gum-Gum Pistol', 'rocket': 'Gum-Gum Rocket', 'gatling': 'Gum-Gum Gatling', 'bazooka': 'Gum-Gum Bazooka', 'gear_second': 'Gear Second', 'gear_third': 'Gear Third', 'nika_drums': 'Drums of Liberation',
 'chop_cannon': 'Chop-Chop Cannon', 'split_dodge': 'Emergency Escape', 'chop_festival': 'Chop-Chop Festival', 'flying_chop': 'Chop-Chop Flight',
 'room': 'ROOM', 'shambles': 'Shambles', 'amputate': 'Amputate', 'takt': 'Takt', 'injection_shot': 'Injection Shot', 'counter_shock': 'Counter Shock',
 'transform': 'Transform', 'howl': 'Howl', 'pounce': 'Pounce', 'rending_claws': 'Rending Claws', 'moon_fang': 'Moon Fang (Awakening)',
 'gale_blade': 'Gale Blade', 'updraft': 'Updraft', 'cyclone': 'Cyclone', 'tailwind': 'Tailwind', 'tempest_spear': 'Tempest Spear',
 'shoulder_charge': 'Shoulder Charge', 'ground_pound': 'Ground Pound', 'headbutt': 'Headbutt',
 'collier_shoot': 'Collier Shoot', 'party_table': 'Party Table Kick Course', 'concasse': 'Concassé', 'diable_jambe': 'Diable Jambe', 'sky_walk': 'Sky Walk',
 'soru': 'Soru', 'geppo': 'Geppo', 'tekkai': 'Tekkai', 'shigan': 'Shigan', 'rankyaku': 'Rankyaku', 'kami_e': 'Kami-e', 'rokuogan': 'Rokuogan',
 'water_heart': 'Water Heart', 'shark_tile_fist': 'Shark Tile Fist', 'water_shot': 'Water Shot', 'thousand_brick_fist': 'Thousand Brick Fist',
 'uchimizu': 'Uchimizu', 'water_whip': 'Water Whip', 'undertow_grip': 'Undertow Grip', 'swan_kick': 'Swan Kick', 'pirouette': 'Pirouette', 'ballet_dash': 'Ballet Dash',
 'electro_claw': 'Electro Claw', 'electro_burst': 'Electro Burst', 'sulong': 'Sulong', 'flowing_palm': 'Flowing Palm', 'dragon_flow_burst': 'Dragon Flow Burst',
 'vibration_strike': 'Vibration Strike', 'quake_palm': 'Quake Palm', 'vital_restore': 'Life Return: Vital Restore', 'iron_hair_guard': 'Iron Hair Guard',
 'lion_strike': 'Lion Strike', 'flying_slash': 'Flying Slash', 'tornado_slash': 'Tornado Slash', 'rhino_charge': 'Rhino Charge', 'twin_flying_slash': 'Twin Flying Slash',
 'oni_giri': 'Oni Giri', 'tatsumaki': 'Tatsumaki', 'pound_cannon': '108 Pound Cannon', 'asura': 'Asura',
 'quick_draw': 'Quick Draw', 'sheathed_counter': 'Sheathed Counter', 'moon_cut': 'Moon Cut',
 'fan_the_hammer': 'Fan the Hammer', 'trick_shot': 'Trick Shot', 'dodge_shot': 'Dodge Shot', 'lead_star': 'Lead Star', 'fire_bird_star': 'Fire Bird Star', 'pop_green_thicket': 'Pop Green: Thicket',
 'riptide_throw': 'Riptide Throw', 'undertow': 'Undertow', 'breaking_wave': 'Breaking Wave', 'gale_lunge': 'Gale Lunge', 'whirl_dance': 'Whirl Dance', 'lightning_rod_thrust': 'Lightning Rod Thrust',
 'anchor_drop': 'Anchor Drop', 'chain_sweep': 'Chain Sweep', 'harbor_pull': 'Harbor Pull',
 'rising_slash': 'Rising Slash', 'piercing_dash': 'Piercing Dash', 'air_slash': 'Air Slash', 'sweeping_arc': 'Sweeping Arc', 'pole_vault': 'Pole Vault', 'javelin': 'Javelin',
 'earthshaker': 'Earthshaker', 'guard_crusher': 'Guard Crusher', 'lash': 'Lash', 'seastone_bind': 'Seastone Bind', 'bleeding_cuts': 'Bleeding Cuts',
 'aimed_shot': 'Aimed Shot', 'volley': 'Volley', 'explosive_shell': 'Explosive Shell', 'thunder_tempo': 'Thunderbolt Tempo', 'mirage_tempo': 'Mirage Tempo', 'impact_release': 'Impact Release',
 'toggle': 'Toggle', 'future_sight': 'Future Sight', 'hardening': 'Hardening', 'emission': 'Emission', 'internal_destruction': 'Internal Destruction', 'burst': 'Conqueror\'s Burst', 'infusion': 'Conqueror\'s Infusion',
}
for path in glob.glob(os.path.join(SRC, 'fruit/impl/*.java')):
    s = open(path).read()
    m = re.search(r'DevilFruits.get\("(\w+)"\)', open(os.path.join(SRC, 'fruit/DevilFruits.java')).read())
cls_to_fruit = dict(re.findall(r'impl\.(\w+)\.init\(get\("(\w+)"\)\)', read('fruit/DevilFruits.java')))
for cls, fid in cls_to_fruit.items():
    s = read(f'fruit/impl/{cls}.java')
    for a in re.findall(r'f\.ability\("(\w+)"\)', s):
        lang[f'ability.grandline.fruit.{fid}.{a}'] = ability_names.get(a, title(a))
styles_src = read('combat/style/FightingStyles.java')
for var, sid in re.findall(r'FightingStyle (\w+) = def\("(\w+)"', styles_src):
    for a in re.findall(rf'{var}\.ability\("(\w+)"\)', styles_src):
        lang[f'ability.grandline.style.{sid}.{a}'] = ability_names.get(a, title(a))
classes = re.findall(r'^    ([A-Z_]+)\(', read('combat/weapon/WeaponClass.java'), re.M)
arts = read('combat/weapon/WeaponArts.java')
for c in classes:
    cid = c.lower()
    lang[f'weapon_class.grandline.{cid}'] = {'trident_pole': 'War Trident', 'gauntlets': 'Knuckles', 'dial': 'Dial', 'jitte': 'Jitte'}.get(cid, title(cid))
    for a in set(re.findall(r'art\(wc, "(\w+)"\)', arts)):
        lang[f'ability.grandline.weapon.{cid}.{a}'] = ability_names.get(a, title(a))
for hid, owner, a in re.findall(r'"haki/((observation|armament|conqueror)/(\w+))"', read('haki/HakiAbilities.java')):
    nm = ability_names.get(a, title(a))
    lang[f'ability.grandline.haki.{owner}.{a}'] = ({'observation': 'Observation', 'armament': 'Armament', 'conqueror': "Conqueror's"}[owner] + ' ' + nm) if a == 'toggle' else nm

style_names = {'brawler': 'Brawler', 'black_leg': 'Black Leg Style', 'rokushiki': 'Rokushiki (Six Powers)', 'fishman_karate': 'Fish-Man Karate', 'fishman_jujutsu': 'Fish-Man Jujutsu',
 'okama_kenpo': 'Okama Kenpo', 'electro': 'Electro', 'ryuo': 'Ryuo', 'hasshoken': 'Hasshoken', 'life_return': 'Life Return', 'ittoryu': 'One-Sword Style', 'nitoryu': 'Two-Sword Style',
 'santoryu': 'Three-Sword Style', 'iaido': 'Iaido', 'gunslinger': 'Gunslinger', 'sniper': 'Sniper', 'tide_grapple': 'Shiodome-ryu Tide Grappling (original)',
 'storm_dance': 'Arashimai Storm Dance (original)', 'anchor_brawl': 'Ikari-ken Anchor Brawling (original)'}
for k, v in style_names.items():
    lang[f'style.grandline.{k}'] = v
for k in ['weapon/' + c.lower() for c in classes] + ['style/' + s for s in style_names] + ['haki/observation', 'haki/armament', 'haki/conqueror', 'combat/hits', 'combat/dodge', 'combat/parry', 'combat/meditation']:
    lang['mastery.grandline.' + k.replace('/', '.')] = title(k.split('/')[1]) + (' Mastery' if not k.startswith('combat') else '')
for fid in fruit_names:
    lang[f'mastery.grandline.fruit.{fid}'] = fruit_names[fid][0] + ' Mastery'

# ---------------- items / blocks / entities ----------------
items_src = read('registry/ModItems.java')
for iid in re.findall(r'ITEMS\.register\("(\w+)"', items_src) + re.findall(r'weapon\("(\w+)"', items_src):
    lang[f'item.grandline.{iid}'] = {'enma': 'Enma', 'ame_no_habakiri': 'Ame no Habakiri', 'murakumogiri': 'Murakumogiri', 'yoru': 'Yoru', 'shusui': 'Shusui',
        'wado_ichimonji': 'Wado Ichimonji', 'sandai_kitetsu': 'Sandai Kitetsu', 'yubashiri': 'Yubashiri', 'kurenai_shigure': 'Kurenai Shigure', 'abyssal_fang': 'Abyssal Fang',
        'tidebreaker': 'Tidebreaker', 'musket': 'Musket', 'impact_dial': 'Impact Dial', 'weather_staff': 'Weather Staff', 'war_trident': 'War Trident'}.get(iid, title(iid))
lang['item.grandline.devil_fruit'] = 'Devil Fruit'
lang['item.grandline.devil_fruit.unknown'] = 'Mysterious Devil Fruit'
lang['item.grandline.style_scroll.of'] = 'Scroll: %s'
for bid in re.findall(r'BLOCKS\.register\("(\w+)"', read('registry/ModBlocks.java')):
    lang[f'block.grandline.{bid}'] = {'fruit_bloom': 'Strange Blossom'}.get(bid, title(bid))
lang['entity.grandline.npc'] = 'Islander'
lang['entity.grandline.ability_projectile'] = 'Technique'
lang['effect.grandline.seastone_drain'] = 'Seastone Drain'
lang['effect.grandline.knocked_out'] = 'Knocked Out'
for g, n in {'fine': 'Fine', 'graded': 'Graded', 'skillful': 'Skillful Grade Sword', 'great': 'Great Grade Sword', 'supreme': 'Supreme Grade Sword', 'black_blade': 'Black Blade'}.items():
    lang[f'grade.grandline.{g}'] = n
lang['itemGroup.grandline'] = 'Grand Line Odyssey'
lang['itemGroup.grandline.fruits'] = 'Devil Fruits (Creative)'

# ---------------- roles ----------------
for role in re.findall(r'a\("(\w+)", Faction', read('npc/Archetypes.java')) + re.findall(r'List\.of\(([^)]*)\)\)\s*\n\s*a\(r', read('npc/Archetypes.java')):
    pass
roles = set(re.findall(r'"(\w+)"', ' '.join(re.findall(r'for \(String r : List\.of\(([^)]*)\)\)', read('npc/Archetypes.java')))))
roles |= set(re.findall(r'a\("(\w+)", Faction', read('npc/Archetypes.java')))
for r in roles:
    lang[f'role.grandline.{r}'] = title(r)
for r in roles:
    lang.setdefault(f'dialogue.grandline.greet.{r}', {
        'marine': 'Halt. State your business — the Marines keep the peace in these waters.',
        'marine_officer': 'Another face from the sea. Keep your nose clean and we will get along.',
        'pirate': 'Heh, you look like you could use a drink — or a fight.',
        'bandit': 'Your money or your life, stranger.',
        'bartender': 'Welcome! Rumours are free if you are buying. What will it be?',
        'shopkeeper': 'Come in, come in! Supplies for every voyage.',
        'blacksmith': 'Steel for the sea. Swords, guns, and repairs.',
        'shipwright': 'A ship is a pirate\'s life — or a Marine\'s. Looking for a hull?',
        'elder': 'Ah, young one. The sea has been calling you, hasn\'t it?',
        'master': 'You want to learn? Show me your resolve first.',
        'innkeeper': 'A warm bed and a hot meal — fifty Beli.',
        'priest': 'May the sea be calm for you.',
        'lighthouse_keeper': 'Few ships survive what lies beyond that mountain.',
    }.get(r, 'Hello there, traveller.'))

# ---------------- islands ----------------
canon = re.findall(r'c\("(\w+)", -?\d+, -?\d+, \d+\)', read('world/islands/CanonIslands.java'))
island_names = {'dawn_island': 'Dawn Island', 'shimotsuki_village': 'Shimotsuki Village', 'shells_town': 'Shells Town', 'orange_town': 'Orange Town',
 'island_of_rare_animals': 'Island of Rare Animals', 'syrup_village': 'Syrup Village (Gecko Islands)', 'mirror_ball_island': 'Mirror Ball Island', 'baratie': 'Baratie',
 'cocoyasi': 'Cocoyasi (Conomi Islands)', 'loguetown': 'Loguetown', 'flevance': 'Flevance', 'minion_island': 'Minion Island', 'lvneel': 'Lvneel Kingdom', 'swallow_island': 'Swallow Island',
 'spider_miles': 'Spider Miles', 'ohara': 'Ohara', 'ilisia_kingdom': 'Ilisia Kingdom', 'kano_country': 'Kano Country', 'baterilla': 'Baterilla', 'karate_island': 'Karate Island',
 'sorbet_kingdom': 'Sorbet Kingdom', 'torino_kingdom': 'Torino Kingdom', 'twin_capes': 'Twin Capes', 'whisky_peak': 'Whisky Peak (Cactus Island)', 'little_garden': 'Little Garden',
 'drum_island': 'Drum Island', 'alabasta': 'Alabasta Kingdom', 'jaya': 'Jaya', 'long_ring_long_land': 'Long Ring Long Land', 'san_faldo': 'San Faldo', 'water_7': 'Water 7',
 'st_poplar': 'St. Poplar', 'pucci': 'Pucci', 'enies_lobby': 'Enies Lobby', 'boin_archipelago': 'Boin Archipelago', 'thriller_bark': 'Thriller Bark', 'namakura_island': 'Namakura Island',
 'banaro_island': 'Banaro Island', 'momoiro_island': 'Momoiro Island', 'kuraigana_island': 'Kuraigana Island', 'karakuri_island': 'Karakuri Island', 'marineford': 'Marineford',
 'sabaody_archipelago': 'Sabaody Archipelago', 'amazon_lily': 'Amazon Lily', 'rusukaina': 'Rusukaina', 'impel_down': 'Impel Down', 'mary_geoise': 'Mary Geoise',
 'new_world_gate': 'New World Gate', 'new_marineford': 'New Marineford (G-1)', 'punk_hazard': 'Punk Hazard', 'g5_base': 'G-5 Base', 'dressrosa': 'Dressrosa', 'green_bit': 'Green Bit',
 'zou': 'Zou', 'sphinx': 'Sphinx', 'whole_cake_island': 'Whole Cake Island', 'cacao_island': 'Cacao Island', 'jam_island': 'Jam Island', 'foodvalten': 'Foodvalten',
 'wano_country': 'Wano Country', 'onigashima': 'Onigashima', 'baltigo': 'Baltigo', 'egghead': 'Egghead', 'hachinosu': 'Hachinosu', 'elbaph': 'Elbaph', 'lodestar_island': 'Lodestar Island',
 'laugh_tale': 'Laugh Tale', 'shiokaze_isle': 'Shiokaze Isle', 'fuyugane_isle': 'Fuyugane Isle', 'kirinoha_isle': 'Kirinoha Isle', 'hinata_isle': 'Hinata Isle'}
for i in canon:
    lang[f'island.grandline.{i}'] = island_names.get(i, title(i))

# ---------------- quests ----------------
quest_text = {
 'first_steps': ('First Steps', 'Speak with the village elder at the shrine.'),
 'bandit_trouble': ('Bandit Trouble', 'Bandits are raiding the island. Defeat 4 of them.'),
 'shipwrights_favor': ("The Shipwright's Favour", 'Talk to the shipwright and get your first ship.'),
 'first_voyage': ('First Voyage', 'Sail to another named island of your Blue.'),
 'path_choice': ('A Path on the Sea', 'Return to the elder and choose your path in this world.'),
 'execution_town': ('The Town of Beginnings and Endings', 'Reach Loguetown and stand before its execution platform.'),
 'reverse_mountain': ('The Mountain That Flows Upward', 'Climb Reverse Mountain and reach the Twin Capes.'),
 'whisky_welcome': ('A Suspiciously Warm Welcome', 'Reach Whisky Peak and survive its hospitality.'),
 'fishman_occupation': ('The Fish-Man Occupation', 'Free Cocoyasi from the Fish-Man pirates of Arlong Park.'),
 'path_pirate': ('Raise the Jolly Roger', 'Prove yourself against the Marines (defeat 3).'),
 'path_marine': ('Enlistment', 'Serve justice: defeat 5 pirates and 5 bandits.'),
 'path_revolutionary': ('Whispers of Revolution', 'Strike at Cipher Pol and travel the seas.'),
 'path_bounty_hunter': ('Licensed Hunter', 'Arrest two pirates with seastone cuffs.'),
 'board_bandits': ('Bandits on the Roads', 'Defeat 5 bandits.'), 'board_pirates': ('Pirate Trouble', 'Defeat 4 pirates.'),
 'board_marines': ('Thin the Marines', 'Defeat 4 Marines (raises your bounty).'), 'board_explore': ('Charting the Sea', 'Discover 2 islands.'),
 'board_ruins': ('Old Ruins', 'Read a poneglyph.'),
}
for q, (t, d) in quest_text.items():
    lang[f'quest.grandline.{q}'] = t
    lang[f'quest.grandline.{q}.desc'] = d

# keys defined by hand in other fragments (messages.json, story.json, fruits_*.json ...) win over generated ones
for frag in glob.glob(os.path.join(OUT, '*.json')):
    if frag.endswith('generated.json'):
        continue
    for k in json.load(open(frag)):
        lang.pop(k, None)
json.dump(lang, open(os.path.join(OUT, 'generated.json'), 'w'), indent=1, sort_keys=True, ensure_ascii=False)
print(len(lang), 'generated keys')
