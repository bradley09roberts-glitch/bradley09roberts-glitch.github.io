#!/usr/bin/env python3
"""Generates the journey advancements (data/grandline/advancement/journey/*.json, their own tab) and their titles and
descriptions (the advancements.* keys of src/main/lang/en_us/journey.json; the fragment's other keys are kept).

Every advancement is granted through the one custom criterion "grandline:milestone" with an id; the game fires the
ids from its hooks (com.grandline.journey.Journey). Run from anywhere:

    python3 -I tools/gen_journey.py            # then python3 -I tools/gen_lang.py
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ADV = os.path.join(ROOT, 'src/main/resources/data/grandline/advancement/journey')
LANG = os.path.join(ROOT, 'src/main/lang/en_us/journey.json')
NS = 'grandline'
BACKGROUND = f'{NS}:textures/gui/advancements/backgrounds/sea.png'
KEY = 'advancements.grandline.journey.'

# name, parent, icon (grandline: unless namespaced), frame, title, description[, options]
# options: criteria={criterion: milestone id} (all required unless 'any' groups given), any=[[criterion, ...], ...],
#          hidden, toast, chat
A = []


def adv(name, parent, icon, frame, title, desc, **opt):
    A.append((name, parent, icon, frame, title, desc, opt))


# ------------------------------------------------------------------ the tab
adv('root', None, 'sea_chart', 'task', 'Grand Line Odyssey',
    'Make a character and step ashore on your home island. These milestones belong to you, not to one life: they stay when a character dies for good',
    criteria={'begin': 'begin'}, toast=False, chat=False)

# ------------------------------------------------------------------ the route through the seas
adv('set_sail', 'root', 'ship_hatch', 'task', 'Set Sail', 'Raise the sails of a ship you command')
adv('first_island', 'set_sail', 'torn_map', 'task', 'Landfall', 'Discover an island beyond your home island')
adv('east_blue', 'first_island', 'blue_sardine', 'task', 'East Blue', 'Discover an island of the East Blue, the most peaceful sea')
adv('west_blue', 'first_island', 'harbor_bream', 'task', 'West Blue', 'Discover an island of the West Blue')
adv('north_blue', 'first_island', 'fisherman_stew', 'task', 'North Blue', 'Discover an island of the cold North Blue')
adv('south_blue', 'first_island', 'meat_on_bone', 'task', 'South Blue', 'Discover an island of the South Blue')
adv('four_blues', 'first_island', 'eternal_pose', 'challenge', 'All Four Blues', 'Discover islands in the East, West, North and South Blue',
    criteria={'east_blue': 'east_blue', 'west_blue': 'west_blue', 'north_blue': 'north_blue', 'south_blue': 'south_blue'})
adv('reverse_mountain', 'first_island', 'log_pose', 'task', 'The Mountain That Flows Upward',
    'Ride a canal over Reverse Mountain into the Grand Line (or reach the Twin Capes at its foot)')
adv('paradise', 'reverse_mountain', 'rainbow_tuna', 'task', 'Welcome to Paradise', 'Sail into Paradise, the first half of the Grand Line')
adv('alabasta', 'paradise', 'weather_staff', 'task', 'Kingdom of Sand', 'Discover the desert kingdom of Alabasta')
adv('water_7', 'alabasta', 'war_hammer', 'task', 'City of Water', 'Discover Water 7, home of the finest shipwrights')
adv('sabaody', 'water_7', 'beli_pouch', 'task', 'The Archipelago at the Wall', 'Discover the Sabaody Archipelago at the foot of the Red Line')
adv('holy_land', 'sabaody', 'seastone_bricks', 'task', 'The Holy Land', 'Stand on the Red Line at Mary Geoise')
adv('coating', 'sabaody', 'bubble_membrane', 'task', 'Bubble Coated', 'Have a ship coated at Sabaody for the dive under the Red Line')
adv('fishman_island', 'coating', 'abyss_lanternfish', 'goal', '10,000 Metres Under', 'Reach Fish-Man Island at the bottom of the sea')
adv('new_world', 'fishman_island', 'crimson_marlin', 'goal', 'The New World', 'Sail into the New World, the second half of the Grand Line')
adv('laugh_tale', 'new_world', 'poneglyph_rubbing', 'challenge', 'Laugh Tale', 'Reach the last island of the Grand Line')
adv('calm_belt', 'paradise', 'sea_king_fry', 'task', 'Windless Waters', 'Enter the Calm Belt, where no wind blows and the Sea Kings nest')
adv('skypiea', 'paradise', 'island_cloud', 'goal', 'The Knock Up Stream', 'Ride the Knock Up Stream off Jaya up to the White Sea')

# ------------------------------------------------------------------ ships
adv('ship_buy', 'set_sail', 'anchor_chain', 'task', 'A Ship of Your Own', 'Buy a ship from a shipwright')
adv('ship_upgrade', 'ship_buy', 'chain_shot', 'task', 'Refit', 'Buy an upgrade for your ship')
adv('ship_customise', 'ship_upgrade', 'cutlass', 'task', 'Under Your Own Colours', 'Give your ship a figurehead, emblem and paint of your choosing')
adv('ship_prize', 'ship_buy', 'grappling_hook', 'task', 'Prize Crew', 'Beat the crew of an NPC ship and take her as your prize')
adv('sea_king', 'ship_buy', 'sea_king_steak', 'goal', 'Sea King Slayer', 'Slay a Sea King')

# ------------------------------------------------------------------ Devil Fruits
adv('fruit_eat', 'root', 'devil_fruit', 'task', 'A Cursed Bite', 'Eat a Devil Fruit. The sea will never hold you up again')
adv('fruit_master', 'fruit_eat', 'style_scroll', 'goal', 'Second Nature', 'Raise your Devil Fruit mastery to 50: the fruit stirs')
adv('fruit_awaken', 'fruit_master', 'fruit_bloom', 'challenge', 'Awakening', 'Pass the Awakening Trial and awaken your Devil Fruit')

# ------------------------------------------------------------------ Haki
adv('haki_observation', 'root', 'war_bow', 'task', 'The Voice of All Things', 'Awaken Observation Haki')
adv('haki_future_sight', 'haki_observation', 'musket', 'goal', 'Future Sight', 'See a moment ahead with advanced Observation Haki')
adv('haki_armament', 'root', 'brass_knuckles', 'task', 'Armament', 'Awaken Armament Haki and harden your body')
adv('haki_emission', 'haki_armament', 'iron_kanabo', 'goal', 'Emission', 'Send Armament Haki out beyond your fists')
adv('haki_internal', 'haki_emission', 'tonfa', 'challenge', 'Internal Destruction', 'Flow your Haki into a foe and break them from within')
adv('haki_conqueror', 'root', 'yoru', 'goal', "Conqueror's Haki", "Awaken the rare Haki of kings (one in many is born with it)")
adv('haki_infusion', 'haki_conqueror', 'enma', 'challenge', "Conqueror's Infusion", "Coat your strikes in Conqueror's Haki")

# ------------------------------------------------------------------ bounties
adv('bounty_poster', 'root', 'wanted_poster', 'task', 'Wanted', 'Have your first wanted poster printed (a bounty of 1,000,000 Beli)')
adv('bounty_10m', 'bounty_poster', 'beli_pouch', 'task', 'Ten Million', 'Carry a bounty of 10,000,000 Beli')
adv('bounty_100m', 'bounty_10m', 'seastone_cuffs', 'goal', 'Supernova', 'Carry a bounty of 100,000,000 Beli')
adv('bounty_1b', 'bounty_100m', 'seastone_jitte', 'challenge', 'A Billion-Beli Head', 'Carry a bounty of 1,000,000,000 Beli')
adv('buster_call_survived', 'bounty_100m', 'cannonball', 'challenge', 'Survived the Buster Call',
    'Live through a Buster Call: outlast the bombardment, outrun the fleet or sink it')

# ------------------------------------------------------------------ crew
adv('crew_recruit', 'root', 'vivre_card', 'task', 'First Mate', 'Recruit someone into your crew')
adv('crew_five', 'crew_recruit', 'grilled_fish', 'goal', 'A Proper Crew', 'Sail with five crew members')
POSTS = ['navigator', 'cook', 'doctor', 'shipwright', 'musician', 'sniper', 'helmsman', 'archaeologist']
adv('crew_full', 'crew_five', 'pirates_feast', 'challenge', 'Every Post Filled',
    'Crew a navigator, cook, doctor, shipwright, musician, sniper, helmsman, swordsman and archaeologist',
    criteria={**{p: 'crew_role_' + p for p in POSTS}, 'swordsman': 'crew_role_swordsman', 'swordswoman': 'crew_role_swordswoman'},
    any=[[p] for p in POSTS] + [['swordsman', 'swordswoman']])

# ------------------------------------------------------------------ the Saltfire Saga
adv('story_part1', 'root', 'torn_map', 'task', 'Embers of the Blue', 'Finish Part I of the Saltfire Saga in your Blue')
adv('story_part2', 'story_part1', 'poneglyph', 'task', 'The Paradise Gauntlet', 'Finish Part II of the Saltfire Saga at Sabaody')
adv('story_part3', 'story_part2', 'shusui', 'goal', 'The War of the Crescent Bay', 'Finish Part III of the Saltfire Saga at Marineford')
adv('story_part4', 'story_part3', 'poneglyph_rubbing', 'goal', 'Saltfire Rising', 'Finish Part IV of the Saltfire Saga and take the way to Laugh Tale')
adv('ending_saltfire_kindled', 'story_part4', 'fruit_bloom', 'challenge', 'The Saltfire Kindled', 'Kindle the Saltfire: every sea reads the stone (pirate ending)', hidden=True)
adv('ending_true_justice', 'story_part4', 'seastone_jitte', 'challenge', 'True Justice', 'Stand guard over the light (Marine ending)', hidden=True)
adv('ending_dawn', 'story_part4', 'weather_staff', 'challenge', 'The Dawn', 'Carry the flame to the Holy Land (Revolutionary ending)', hidden=True)
adv('ending_last_bounty', 'story_part4', 'wanted_poster', 'challenge', 'The Last Bounty', 'Cash in the bounty on the Saltfire itself (Bounty Hunter ending)', hidden=True)
adv('ending_free_press', 'story_part4', 'sea_chart', 'challenge', 'The Free Press', 'Print the truth in a thousand ports (Civilian ending)', hidden=True)

# ------------------------------------------------------------------ sea activities
adv('treasure_map', 'root', 'treasure_map', 'task', 'X Marks the Spot', 'Follow a treasure map to its island and open the chest')
adv('giant_catch', 'root', 'behemoth_carp', 'task', "The One That Didn't Get Away", 'Land a giant of the Calm Belt or the New World on a rod')
adv('signature_dish', 'root', 'saltfire_curry', 'task', 'Signature Dish', 'Cook a signature dish: Saltfire Curry, Tidewater Sashimi, Sea King Hotpot or a Pirate\'s Feast')

# ------------------------------------------------------------------ Den Den Mushi
adv('den_call', 'root', 'den_den_mushi', 'task', 'Puru Puru Puru', 'Get through to another player on a Den Den Mushi')
adv('den_crew', 'den_call', 'crew_den_den_mushi', 'task', 'All Hands', 'Speak to a crewmate on a crew Den Den Mushi')
adv('den_eavesdrop', 'den_call', 'black_den_den_mushi', 'task', 'On the Marine Wavelength', 'Overhear the Marines on a black Den Den Mushi')

# ------------------------------------------------------------------ careers (rank-ups: com.grandline.career.Careers.MILESTONES)
FIRST_RANKS = {'marine': 'career_marine_seaman', 'pirate': 'career_pirate_supernova', 'revolutionary': 'career_revolutionary_officer',
               'hunter': 'career_hunter_licensed', 'civilian': 'career_civilian_apprentice'}
adv('career_path', 'root', 'marine_coat', 'task', 'A Calling',
    'Earn your first rank on your path: Seaman, Supernova, Revolutionary Officer, a hunter\'s licence or an apprentice\'s name',
    criteria=FIRST_RANKS, any=[list(FIRST_RANKS)])
adv('career_marine_seaman', 'career_path', 'marine_coat', 'task', 'Swabbie No More', 'Rise from Seaman Recruit to Seaman in the Marines')
adv('career_marine_ensign', 'career_marine_seaman', 'seastone_jitte', 'task', 'Commissioned', 'Earn an officer\'s commission: Ensign')
adv('career_marine_captain', 'career_marine_ensign', 'fine_katana', 'goal', 'Captain of the Marines', 'Make Captain: a sword of your own and a squad to command')
adv('career_marine_vice_admiral', 'career_marine_captain', 'black_den_den_mushi', 'goal', 'Vice Admiral',
    'Rise to Vice Admiral: Headquarters will hear your request for a Buster Call')
adv('career_marine_admiral', 'career_marine_vice_admiral', 'seastone_cuffs', 'challenge', 'The Compass of the Sky', 'Take one of the three Admirals\' seats')
adv('career_marine_fleet_admiral', 'career_marine_admiral', 'enma', 'challenge', 'Fleet Admiral', 'Command every Marine from the Fleet Admiral\'s seat')
adv('career_pirate_supernova', 'career_path', 'wanted_poster', 'goal', 'One of the Supernovas',
    'Make your name in Paradise: a bounty of 100,000,000 Beli and deeds to match')
adv('career_pirate_warlord', 'career_pirate_supernova', 'letter_of_marque', 'goal', 'Warlord of the Sea', 'Accept the World Government\'s letter of marque')
adv('career_pirate_emperor', 'career_pirate_supernova', 'beli_pouch', 'challenge', 'Emperor of the Sea',
    'Rise to Emperor: a billion-Beli head who has beaten an Emperor or holds territory')
adv('career_revolutionary_officer', 'career_path', 'flintlock', 'task', 'Revolutionary Officer', 'Run missions for your cell and report for promotion')
adv('career_revolutionary_commander', 'career_revolutionary_officer', 'musket', 'goal', 'Army Commander', 'Command a revolutionary army')
adv('career_revolutionary_chief', 'career_revolutionary_commander', 'eternal_pose', 'challenge', 'Chief of Staff',
    'Become the Revolutionary Army\'s Chief of Staff')
adv('career_hunter_licensed', 'career_path', 'seastone_cuffs', 'task', 'Licensed', 'Hold a bounty hunter\'s licence')
adv('career_hunter_gold', 'career_hunter_licensed', 'grappling_hook', 'goal', 'Gold Licence', 'Collect 2,500,000 Beli of bounties and bring in a notable outlaw alive')
adv('career_hunter_black', 'career_hunter_gold', 'wanted_poster', 'challenge', 'Black Licence', 'The Lodge\'s highest licence: 10,000,000 Beli collected, five notable heads')
adv('career_civilian_apprentice', 'career_path', 'sea_chart', 'task', 'Making a Name', 'Make a name in your trade: charting islands, reading poneglyphs or trading')
adv('career_civilian_master', 'career_civilian_apprentice', 'poneglyph_rubbing', 'goal', 'Master of the Trade', 'Become a master cartographer, scholar or merchant')
adv('career_civilian_grand', 'career_civilian_master', 'eternal_pose', 'challenge', 'Legend of the Seas', 'Become a grand cartographer, scholar or merchant')

# ------------------------------------------------------------------ races (each life may add one)
adv('races', 'root', 'meat_on_bone', 'task', 'Born of the Sea', 'Begin a life at sea. Each new character after a death can add their race below',
    criteria={'begin': 'begin'}, toast=False, chat=False)
RACES = [  # id, title, icon
    ('human', 'Born Human', 'katana'), ('fishman', 'Born a Fish-Man', 'needlenose_gar'), ('merfolk', 'Born of the Merfolk', 'ghost_ray'),
    ('mink', 'Born a Mink', 'brass_knuckles'), ('sky_people', 'Born of the Sky People', 'impact_dial'),
    ('long_arm', 'Born of the Long-Arm Tribe', 'whip'), ('long_leg', 'Born of the Long-Leg Tribe', 'spear'),
    ('three_eye', 'Born of the Three-Eye Tribe', 'eternal_pose'), ('tontatta', 'Born a Tontatta', 'knife'), ('giant', 'Born a Giant', 'iron_kanabo'),
    ('reefborn', 'Born Reefborn', 'tidewater_sashimi'), ('stormwing', 'Born a Stormwing', 'cloud_skipper'), ('stonehide', 'Born Stonehide', 'seastone'),
    ('abyssal', 'Born Abyssal', 'abyss_lanternfish'), ('emberkin', 'Born Emberkin', 'sea_king_roast'), ('vinefolk', 'Born of the Vinefolk', 'fruit_bloom'),
    ('whalefolk', 'Born of the Whalefolk', 'behemoth_carp'), ('starborn', 'Born Starborn', 'thunder_eel'),
]
for rid, title, icon in RACES:
    adv('race_' + rid, 'races', icon, 'task', title, 'Begin a character of this race')
adv('many_lives', 'races', 'vivre_card', 'challenge', 'Many Lives', 'Live as three different races (your journey carries over from one life to the next)')


def build(name, parent, icon, frame, title, desc, opt):
    crit = opt.get('criteria') or {name: name}
    display = {
        'icon': {'id': icon if ':' in icon else f'{NS}:{icon}'},
        'title': {'translate': KEY + name + '.title'},
        'description': {'translate': KEY + name + '.description'},
        'frame': frame,
        'show_toast': opt.get('toast', True),
        'announce_to_chat': opt.get('chat', True),
        'hidden': opt.get('hidden', False),
    }
    if parent is None:
        display['background'] = BACKGROUND
    out = {}
    if parent is not None:
        out['parent'] = f'{NS}:journey/{parent}'
    out['display'] = display
    out['criteria'] = {c: {'trigger': f'{NS}:milestone', 'conditions': {'id': mid}} for c, mid in crit.items()}
    out['requirements'] = opt.get('any') or [[c] for c in crit]
    return out


def main():
    names = [a[0] for a in A]
    assert len(names) == len(set(names)), 'duplicate advancement'
    for a in A:
        assert a[1] is None or a[1] in names, f'unknown parent {a[1]} of {a[0]}'
    os.makedirs(ADV, exist_ok=True)
    for f in os.listdir(ADV):
        if f.endswith('.json') and f[:-5] not in names:
            os.remove(os.path.join(ADV, f))
    for a in A:
        with open(os.path.join(ADV, a[0] + '.json'), 'w', encoding='utf-8', newline='\n') as f:
            json.dump(build(*a), f, indent=2)
            f.write('\n')
    lang = {}
    if os.path.exists(LANG):
        lang = {k: v for k, v in json.load(open(LANG, encoding='utf-8')).items() if not k.startswith(KEY)}
    for name, _, _, _, title, desc, _ in A:
        lang[KEY + name + '.title'] = title
        lang[KEY + name + '.description'] = desc
    with open(LANG, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(lang, f, indent=1, ensure_ascii=False)
        f.write('\n')
    print(f'{len(A)} journey advancements written; {len(lang)} keys in {os.path.relpath(LANG, ROOT)}')


if __name__ == '__main__':
    main()
