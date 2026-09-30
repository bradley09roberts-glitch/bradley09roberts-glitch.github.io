"""Core systems: lifecycle, players, journal UI, HUD/waypoints, the Pall's chill, settings, admin tools."""
from __future__ import annotations

from .. import poi as poimod
from . import NS, adv, dialog, fid, fn, predicate, snbt, tag, tellraw, xyz
from .engine import R, uuid_nbt, uuid_str

SCHEMA = 1
WP = "hud:waypoint"


def _objectives() -> list[str]:
    objs = [
        ("pm.world", "dummy"), ("pm.q", "dummy"), ("pm.qp", "dummy"), ("pm.tmp", "dummy"), ("pm.nid", "dummy"),
        ("pm.talk", "trigger"), ("pm.ui", "trigger"),
        ("pm.joined", "dummy"), ("pm.seen", "dummy"), ("pm.chill", "dummy"), ("pm.dctx", "dummy"),
        ("pm.optbar", "dummy"), ("pm.optfx", "dummy"), ("pm.optwp", "dummy"),
        ("pm.leave", "minecraft.custom:minecraft.leave_game"),
        ("pm.death", "deathCount"),
        ("pm.kit", "dummy"),
    ]
    return [f"scoreboard objectives add {n} {c}" for n, c in objs]


def generate() -> None:
    wp_uuid = uuid_str(WP)
    wp_nbt = uuid_nbt(WP)

    # ---------------------------------------------------------------- POI storage (static data)
    poi_lines = ["data remove storage palemeridian:poi all"]
    for name, p in sorted(poimod.POI.items()):
        yaw = p.get("yaw", 0.0)
        bx, by, bz = int(p['x'] // 1), int(p['y'] // 1), int(p['z'] // 1)
        poi_lines.append(
            f"data modify storage palemeridian:poi {name} set value "
            f"{{x:{float(p['x'])}d,y:{float(p['y'])}d,z:{float(p['z'])}d,yaw:{float(yaw)}f,bx:{bx},by:{by},bz:{bz}}}")
    fn("poi/init", poi_lines, header="Static points of interest (generated from the site builders)")
    fn("poi/get", ["$data modify storage palemeridian:tmp poi set from storage palemeridian:poi $(name)"])

    # ---------------------------------------------------------------- load / tick
    load = [
        "# Runs on every data-pack (re)load. Everything here is idempotent.",
        *_objectives(),
        f"bossbar add {NS}:objective \"\"",
        f"bossbar set {NS}:objective color white",
        f"bossbar set {NS}:objective style progress",
        f"bossbar add {NS}:encounter \"\"",
        f"bossbar set {NS}:encounter color red",
        f"function {fid('poi/init')}",
        f"execute unless score #schema pm.world matches 1.. run scoreboard players set #schema pm.world {SCHEMA}",
        f"function {fid('core/migrate')}",
        *R.load_hooks,
        f"schedule function {fid('core/second')} 20t replace",
        f"function {fid('hud/refresh')}",
    ]
    fn("core/load", load, header="Pale Meridian — load")
    fn("core/migrate", [
        "# Save-schema migrations. Schema 1 is the first release; future versions add steps here, e.g.:",
        "# execute if score #schema pm.world matches 1 run function palemeridian:core/migrate/1_to_2",
        f"execute if score #schema pm.world matches {SCHEMA + 1}.. run tellraw @a[tag=pm.admin] {{\"text\":\"[Pale Meridian] This world was saved by a newer version of the pack.\",\"color\":\"red\"}}",
    ])
    tick = [
        "# Every tick: only cheap selector checks.",
        f"execute unless score #init pm.world matches 1 run function {fid('core/world_init')}",
        f"execute as @a[scores={{pm.talk=1..}}] at @s run function {fid('dlg/handle')}",
        f"execute as @a[scores={{pm.ui=1..}}] at @s run function {fid('ui/handle')}",
        f"execute as @a unless score @s pm.joined matches 1.. at @s run function {fid('player/first_join')}",
        f"execute as @a[scores={{pm.leave=1..}}] at @s run function {fid('player/rejoin')}",
        f"execute as @a[scores={{pm.death=1..}}] run function {fid('player/died')}",
    ]
    fn("core/tick", tick)
    tag("function", "minecraft", "load", [fid("core/load")])
    tag("function", "minecraft", "tick", [fid("core/tick")])

    second = [
        f"schedule function {fid('core/second')} 20t replace",
        "scoreboard players add #seconds pm.world 1",
        f"function {fid('player/chill_all')}",
        f"function {fid('npc/_maintain_all')}",
        *R.slow_hooks,
    ]
    fn("core/second", second, header="Once per second (self-scheduling). All checks are gated by state and proximity.")

    # ---------------------------------------------------------------- world init (once)
    init = [
        "# First-time world initialisation (runs once; guarded by #init).",
        "scoreboard players set #init pm.world 1",
        "scoreboard players set #chapter pm.world 0",
        "scoreboard players set #rev pm.world 0",
        "scoreboard players set #set.chill pm.world 1",
        "scoreboard players set #set.difficulty pm.world 1",
        "gamerule minecraft:respawn_radius 0",
        "gamerule minecraft:spawn_patrols false",
        "gamerule minecraft:spawn_wandering_traders false",
        "gamerule minecraft:pvp false",
        f"time of {NS}:pall pause",
        f"time of {NS}:pall set 0",
        f"time of {NS}:surge pause",
        f"time of {NS}:surge set 0",
        *R.world_init,
        f"function {fid('q/_advance')}",
        f"function {fid('hud/refresh')}",
    ]
    fn("core/world_init", init)

    # ---------------------------------------------------------------- players
    first = [
        "scoreboard players set @s pm.joined 1",
        "scoreboard players set @s pm.optbar 1",
        "scoreboard players set @s pm.optfx 1",
        "scoreboard players set @s pm.optwp 1",
        "scoreboard players set @s pm.leave 0",
        "scoreboard players set @s pm.death 0",
        "scoreboard players enable @s pm.talk",
        "scoreboard players enable @s pm.ui",
        f"function {fid('items/give_kit')}",
        f"function {fid('journal/sync')}",
        f"execute if score #chapter pm.world matches 0 unless score p.letter pm.q matches 2 run function {fid('player/intro')}",
        f"execute unless score #chapter pm.world matches 0 run function {fid('player/late_join')}",
        f"execute unless score #chapter pm.world matches 0 run function {fid('player/late_join')}" if False else None,
        "scoreboard players operation @s pm.seen = #rev pm.world",
        f"function {fid('hud/refresh')}",
        *R.player_first_join,
    ]
    fn("player/first_join", [l for l in first if l])
    fn("player/intro", [
        f"title @s times 10 70 20",
        f"title @s title {snbt({'text': 'PALE MERIDIAN', 'color': 'white', 'bold': False})}",
        f"title @s subtitle {snbt({'text': 'Chart what the fog forgot.', 'color': 'gray', 'italic': True})}",
        "playsound minecraft:block.bell.resonate master @s ~ ~ ~ 0.5 0.6",
        f"schedule function {fid('player/intro_hint')} 80t append",
    ])
    fn("player/intro_hint", [
        tellraw("@a[scores={pm.seen=0}]", [
            {"text": "\n A letter is pinned to the noticeboard beside the waystation. ", "color": "gray"},
            {"text": "[Use it]", "color": "aqua"},
            {"text": "\n Your Field Journal: press ", "color": "gray"}, {"keybind": "key.quickActions", "color": "yellow"},
            {"text": " (Quick Actions) or open the pause menu.\n", "color": "gray"}]),
    ])
    fn("player/late_join", [
        tellraw("@s", [{"text": "You arrive late to the survey. ", "color": "gray"},
                       {"text": "Open your Field Journal (", "color": "gray"}, {"keybind": "key.quickActions", "color": "yellow"},
                       {"text": ") for a recap of what your companions have done.", "color": "gray"}]),
        f"function {fid('ui/recap')}",
    ])
    fn("player/rejoin", [
        "scoreboard players set @s pm.leave 0",
        "scoreboard players enable @s pm.talk",
        "scoreboard players enable @s pm.ui",
        "scoreboard players set @s pm.dctx 0",
        f"function {fid('journal/sync')}",
        f"execute unless score @s pm.seen = #rev pm.world run function {fid('ui/recap')}",
        "scoreboard players operation @s pm.seen = #rev pm.world",
        f"function {fid('hud/refresh')}",
        *R.player_rejoin,
    ])
    fn("player/died", [
        "scoreboard players set @s pm.death 0",
        "scoreboard players set @s pm.chill 0",
        "scoreboard players enable @s pm.talk",
        "scoreboard players enable @s pm.ui",
    ])

    # ---------------------------------------------------------------- the Pall's chill
    tag("item", NS, "light_sources", [
        "minecraft:torch", "minecraft:soul_torch", "minecraft:copper_torch", "minecraft:lantern", "minecraft:soul_lantern",
        "minecraft:copper_lantern", "minecraft:exposed_copper_lantern", "minecraft:weathered_copper_lantern",
        "minecraft:oxidized_copper_lantern", "minecraft:waxed_copper_lantern", "minecraft:waxed_exposed_copper_lantern",
        "minecraft:waxed_weathered_copper_lantern", "minecraft:waxed_oxidized_copper_lantern", "minecraft:glowstone",
        "minecraft:sea_lantern", "minecraft:shroomlight", "minecraft:jack_o_lantern", "minecraft:campfire",
        "minecraft:soul_campfire", "minecraft:glow_berries", "minecraft:glow_ink_sac", "minecraft:ochre_froglight",
        "minecraft:pearlescent_froglight", "minecraft:verdant_froglight", "minecraft:end_rod", "minecraft:redstone_torch",
        "minecraft:copper_bulb", "minecraft:exposed_copper_bulb", "minecraft:waxed_copper_bulb", "minecraft:waxed_exposed_copper_bulb",
        "#palemeridian:story_lights",
    ])
    tag("item", NS, "story_lights", [])
    predicate("holding_light", {"condition": "minecraft:any_of", "terms": [
        {"condition": "minecraft:entity_properties", "entity": "this", "predicate": {"minecraft:equipment": {"mainhand": {"items": "#palemeridian:light_sources"}}}},
        {"condition": "minecraft:entity_properties", "entity": "this", "predicate": {"minecraft:equipment": {"offhand": {"items": "#palemeridian:light_sources"}}}},
    ]})
    predicate("in_pall", {"condition": "minecraft:location_check", "predicate": {"biomes": "#palemeridian:pall"}})
    fn("player/chill_all", [
        "execute unless score #set.chill pm.world matches 1 run return fail",
        f"execute as @a[gamemode=!creative,gamemode=!spectator] at @s run function {fid('player/chill')}",
    ])
    fn("player/chill", [
        # safe: not in the Pall, holding a light, or a companion with a light within 6 blocks, or near a light block
        f"execute unless predicate {fid('in_pall')} run return run function {fid('player/chill_warm')}",
        f"execute if predicate {fid('holding_light')} run return run function {fid('player/chill_warm')}",
        f"execute if entity @a[distance=0.1..6,predicate={fid('holding_light')}] run return run function {fid('player/chill_warm')}",
        f"execute if block ~ ~1 ~ #palemeridian:warm_blocks run return run function {fid('player/chill_warm')}",
        "scoreboard players add @s pm.chill 1",
        "execute if score @s pm.chill matches 8 run title @s actionbar {\"text\":\"The Pall is cold without light. Hold a torch or lantern.\",\"color\":\"gray\",\"italic\":true}",
        "execute if score @s pm.chill matches 20.. run effect give @s minecraft:slowness 3 0 true",
        "execute if score @s pm.chill matches 20 run title @s actionbar {\"text\":\"Your thoughts go grey at the edges...\",\"color\":\"gray\",\"italic\":true}",
        "execute if score @s pm.chill matches 35.. if score @s pm.optfx matches 1 run effect give @s minecraft:darkness 3 0 true",
        "execute if score @s pm.chill matches 35.. unless score @s pm.optfx matches 1 run title @s actionbar {\"text\":\"(The fog presses in — find light.)\",\"color\":\"gray\"}",
        "execute if score @s pm.chill matches 50.. run effect give @s minecraft:mining_fatigue 3 0 true",
        "execute if score @s pm.chill matches 60.. run scoreboard players set @s pm.chill 60",
    ])
    tag("block", NS, "warm_blocks", ["#minecraft:campfires", "minecraft:lantern", "minecraft:soul_lantern", "minecraft:torch"])
    fn("player/chill_warm", [
        "execute if score @s pm.chill matches 1.. run scoreboard players remove @s pm.chill 2",
        "execute if score @s pm.chill matches ..-1 run scoreboard players set @s pm.chill 0",
    ])

    # ---------------------------------------------------------------- HUD / waypoint
    fn("hud/none", [
        f"bossbar set {NS}:objective visible false",
        f"function {fid('hud/wp_hide')}",
    ])
    fn("hud/wp_at", [
        f"$function {fid('poi/get')} {{name:\"$(poi)\"}}",
        f"function {fid('hud/_wp_move')} with storage palemeridian:tmp poi",
    ])
    fn("hud/_wp_move", [
        "execute if data storage palemeridian:state wp_chunk run function palemeridian:hud/_wp_unforce with storage palemeridian:state wp_chunk",
        "$forceload add $(bx) $(bz)",
        "$data modify storage palemeridian:state wp_chunk set value {x:$(bx),z:$(bz)}",
        f"$execute unless entity {wp_uuid} run summon minecraft:armor_stand $(x) $(y) $(z) {{UUID:{wp_nbt},Invisible:1b,Marker:1b,NoGravity:1b,Invulnerable:1b,Silent:1b,Tags:[\"pm.wp\"],attributes:[{{id:\"minecraft:waypoint_transmit_range\",base:100000d}}]}}",
        f"$tp {wp_uuid} $(x) $(y) $(z)",
        f"waypoint modify {wp_uuid} color hex 8FD8FF",
        f"waypoint modify {wp_uuid} style set {NS}:lamp",
    ])
    fn("hud/_wp_unforce", ["$forceload remove $(x) $(z)"])
    fn("hud/wp_hide", [
        f"execute if data storage palemeridian:state wp_chunk run function palemeridian:hud/_wp_unforce with storage palemeridian:state wp_chunk",
        "data remove storage palemeridian:state wp_chunk",
        f"kill {wp_uuid}",
    ])

    # ---------------------------------------------------------------- items / kit
    fn("items/give_kit", [
        "execute unless score @s pm.kit matches 1.. run function palemeridian:items/field_book",
        "scoreboard players set @s pm.kit 1",
    ])

    # ---------------------------------------------------------------- journal UI (Quick Actions)
    menu_buttons = [
        ("Current objective", 1), ("Recap: the story so far", 2), ("People of the Vale", 3), ("The Eleven", 4),
        ("Help and controls", 5), ("Comfort and accessibility", 6), ("Replace a lost Field Book", 7),
    ]
    dialog("journal/menu", {
        "type": "minecraft:multi_action",
        "title": {"text": "Field Journal", "color": "aqua"},
        "external_title": {"text": "Pale Meridian: Field Journal"},
        "body": [{"type": "minecraft:plain_message", "contents": {"text": "Surveyor's notes. Everything the valley has taught you so far.", "color": "gray", "italic": True}}],
        "columns": 1,
        "actions": [{"label": {"text": t}, "width": 260, "action": {"type": "minecraft:run_command", "command": f"/trigger pm.ui set {n}"}} for t, n in menu_buttons],
        "exit_action": {"label": {"text": "Close"}, "width": 200},
        "can_close_with_escape": True,
        "pause": False,
        "after_action": "close",
    })
    tag("dialog", "minecraft", "quick_actions", [fid("journal/menu")])
    tag("dialog", "minecraft", "pause_screen_additions", [fid("journal/menu")])
    dialog("journal/free_play", {
        "type": "minecraft:notice",
        "title": {"text": "No open objective", "color": "aqua"},
        "body": [{"type": "minecraft:plain_message", "contents": {"text": "The valley is yours to explore, build and tend.", "color": "gray"}}],
        "action": {"label": {"text": "Back"}, "action": {"type": "minecraft:run_command", "command": "/trigger pm.ui set 1"}},
    })
    ui = [
        "scoreboard players operation #ui pm.tmp = @s pm.ui",
        "scoreboard players set @s pm.ui 0",
        "scoreboard players enable @s pm.ui",
        "scoreboard players enable @s pm.talk",
        f"execute if score #ui pm.tmp matches 1 run return run function {fid('journal/current')}",
        f"execute if score #ui pm.tmp matches 2 run return run function {fid('ui/recap')}",
        f"execute if score #ui pm.tmp matches 3 run return run function {fid('ui/people')}",
        f"execute if score #ui pm.tmp matches 4 run return run function {fid('ui/eleven')}",
        f"execute if score #ui pm.tmp matches 5 run return run dialog show @s {fid('journal/help')}",
        f"execute if score #ui pm.tmp matches 6 run return run function {fid('ui/settings')}",
        f"execute if score #ui pm.tmp matches 7 run return run function {fid('ui/replace_book')}",
        f"execute if score #ui pm.tmp matches 9 run return run dialog show @s {fid('journal/menu')}",
        # settings toggles 20..39
        f"execute if score #ui pm.tmp matches 20 run function {fid('ui/toggle_bar')}",
        f"execute if score #ui pm.tmp matches 21 run function {fid('ui/toggle_fx')}",
        f"execute if score #ui pm.tmp matches 22 run function {fid('ui/toggle_wp')}",
        f"execute if score #ui pm.tmp matches 23 run function {fid('ui/toggle_chill')}",
        f"execute if score #ui pm.tmp matches 24 run function {fid('ui/toggle_keepinv')}",
        f"execute if score #ui pm.tmp matches 25 run function {fid('ui/cycle_difficulty')}",
        f"execute if score #ui pm.tmp matches 20..29 run function {fid('ui/settings')}",
        f"execute if score #ui pm.tmp matches 100..199 run function {fid('ui/people_dispatch')}",
    ]
    fn("ui/handle", ui)
    fn("ui/replace_book", [
        "execute if items entity @s container.* *[minecraft:custom_data~{pm:{item:\"field_book\"}}] run return run tellraw @s {\"text\":\"You already carry your Field Book.\",\"color\":\"gray\"}",
        "function palemeridian:items/field_book",
        "tellraw @s {\"text\":\"A fresh Field Book, copied from your notes.\",\"color\":\"gray\"}",
    ])
    # settings
    fn("ui/toggle_bar", [
        "execute store success score #t pm.tmp if score @s pm.optbar matches 1",
        "execute if score #t pm.tmp matches 1 run scoreboard players set @s pm.optbar 0",
        "execute if score #t pm.tmp matches 0 run scoreboard players set @s pm.optbar 1",
        f"function {fid('hud/refresh')}",
    ])
    fn("ui/toggle_fx", [
        "execute store success score #t pm.tmp if score @s pm.optfx matches 1",
        "execute if score #t pm.tmp matches 1 run scoreboard players set @s pm.optfx 0",
        "execute if score #t pm.tmp matches 0 run scoreboard players set @s pm.optfx 1",
    ])
    fn("ui/toggle_wp", [
        "execute store success score #t pm.tmp if score @s pm.optwp matches 1",
        "execute if score #t pm.tmp matches 1 run scoreboard players set @s pm.optwp 0",
        "execute if score #t pm.tmp matches 0 run scoreboard players set @s pm.optwp 1",
        "execute if score @s pm.optwp matches 0 run attribute @s minecraft:waypoint_receive_range base set 0",
        "execute if score @s pm.optwp matches 1 run attribute @s minecraft:waypoint_receive_range base reset",
    ])
    world_setting_guard = "execute unless entity @s[tag=pm.host] if entity @a[tag=pm.host] run return run tellraw @s {\"text\":\"Only the host can change world settings on this server.\",\"color\":\"red\"}"
    fn("ui/toggle_chill", [
        world_setting_guard,
        "execute store success score #t pm.tmp if score #set.chill pm.world matches 1",
        "execute if score #t pm.tmp matches 1 run scoreboard players set #set.chill pm.world 0",
        "execute if score #t pm.tmp matches 0 run scoreboard players set #set.chill pm.world 1",
        "execute as @a run scoreboard players set @s pm.chill 0",
    ])
    fn("ui/toggle_keepinv", [
        world_setting_guard,
        "execute store result score #t pm.tmp run gamerule minecraft:keep_inventory",
        "execute if score #t pm.tmp matches 1 run gamerule minecraft:keep_inventory false",
        "execute if score #t pm.tmp matches 0 run gamerule minecraft:keep_inventory true",
    ])
    fn("ui/cycle_difficulty", [
        world_setting_guard,
        "scoreboard players add #set.difficulty pm.world 1",
        "execute if score #set.difficulty pm.world matches 3.. run scoreboard players set #set.difficulty pm.world 0",
    ])
    # settings dialog is dynamic (shows current values): built inline with macros
    fn("ui/settings", [
        "data modify storage palemeridian:tmp s set value {bar:\"On\",fx:\"On\",wp:\"On\",chill:\"On\",keep:\"Off\",diff:\"Normal\"}",
        "execute if score @s pm.optbar matches 0 run data modify storage palemeridian:tmp s.bar set value \"Off\"",
        "execute if score @s pm.optfx matches 0 run data modify storage palemeridian:tmp s.fx set value \"Off\"",
        "execute if score @s pm.optwp matches 0 run data modify storage palemeridian:tmp s.wp set value \"Off\"",
        "execute unless score #set.chill pm.world matches 1 run data modify storage palemeridian:tmp s.chill set value \"Off\"",
        "execute store result score #t pm.tmp run gamerule minecraft:keep_inventory",
        "execute if score #t pm.tmp matches 1 run data modify storage palemeridian:tmp s.keep set value \"On\"",
        "execute if score #set.difficulty pm.world matches 0 run data modify storage palemeridian:tmp s.diff set value \"Story (fewer Watchers)\"",
        "execute if score #set.difficulty pm.world matches 2 run data modify storage palemeridian:tmp s.diff set value \"Hard (more Watchers)\"",
        "scoreboard players enable @s pm.ui",
        f"function {fid('ui/_settings_show')} with storage palemeridian:tmp s",
    ])
    btn = lambda label, n: f'{{label:{{text:"{label}"}},width:300,action:{{type:"minecraft:run_command",command:"/trigger pm.ui set {n}"}}}}'
    fn("ui/_settings_show", [
        "$dialog show @s {type:\"minecraft:multi_action\",title:{text:\"Comfort and accessibility\",color:\"aqua\"},"
        "body:[{type:\"minecraft:plain_message\",contents:{text:\"Personal settings affect only you. World settings affect everyone (host only on servers).\",color:\"gray\"},width:300}],"
        "columns:1,actions:["
        + btn("Objective bar (you): $(bar)", 20) + ","
        + btn("Screen effects: darkness pulses (you): $(fx)", 21) + ","
        + btn("Locator-bar waypoints (you): $(wp)", 22) + ","
        + btn("The Pall's chill (world): $(chill)", 23) + ","
        + btn("Keep inventory on death (world): $(keep)", 24) + ","
        + btn("Encounter difficulty (world): $(diff)", 25)
        + "],exit_action:{label:{text:\"Back\"},action:{type:\"minecraft:run_command\",command:\"/trigger pm.ui set 9\"}},after_action:\"close\",pause:false}",
    ])

    dialog("journal/help", {
        "type": "minecraft:notice",
        "title": {"text": "Help and controls", "color": "aqua"},
        "body": [{"type": "minecraft:plain_message", "width": 320, "contents": c} for c in [
            [{"text": "Field Journal: ", "color": "yellow"}, {"keybind": "key.quickActions", "color": "white"},
             {"text": " (Quick Actions), the pause menu, or the links inside your Field Book.", "color": "gray"}],
            [{"text": "Talk: ", "color": "yellow"}, {"keybind": "key.use", "color": "white"}, {"text": " on a person or a glowing object.", "color": "gray"}],
            [{"text": "Journal entries: ", "color": "yellow"}, {"keybind": "key.advancements", "color": "white"}, {"text": " opens the Field Journal tab of the advancements screen.", "color": "gray"}],
            {"text": "The Pall: standing in the fog without light slowly chills you. Hold a torch or lantern in either hand, or stay near a companion who does.", "color": "gray"},
            {"text": "Watchers move only while nobody is looking at them. Keep your eyes on them.", "color": "gray"},
            {"text": "Ghostly outlines show missing pieces of broken lamps. Place the matching blocks to rebuild them.", "color": "gray"},
            {"text": "The glowing marker on your locator bar points to your current objective (toggle it in Comfort settings).", "color": "gray"},
        ]],
        "action": {"label": {"text": "Back"}, "action": {"type": "minecraft:run_command", "command": "/trigger pm.ui set 9"}},
    })

    # ---------------------------------------------------------------- admin (operators only)
    fn("admin/status", [
        "tellraw @s {\"text\":\"== Pale Meridian status ==\",\"color\":\"gold\"}",
        "tellraw @s [{\"text\":\"schema \"},{\"score\":{\"name\":\"#schema\",\"objective\":\"pm.world\"}},{\"text\":\"  chapter \"},{\"score\":{\"name\":\"#chapter\",\"objective\":\"pm.world\"}},{\"text\":\"  ending \"},{\"score\":{\"name\":\"#ending\",\"objective\":\"pm.world\"}},{\"text\":\"  current objective #\"},{\"score\":{\"name\":\"#cur\",\"objective\":\"pm.world\"}}]",
        f"function {fid('admin/quests')}",
    ], header="Operator diagnostics: /function palemeridian:admin/status")
    fn("admin/quests", [
        f"tellraw @s [{{\"text\":\"{q.id}: \",\"color\":\"gray\"}},{{\"score\":{{\"name\":\"{q.id}\",\"objective\":\"pm.q\"}},\"color\":\"white\"}}]"
        for q in R.quests
    ])
    fn("admin/force", [
        "$tellraw @s {\"text\":\"Forcing quest $(q) (activate, then complete).\",\"color\":\"gold\"}",
        "$scoreboard players set $(q) pm.q 1",
        "$function palemeridian:q/$(q)/complete",
    ], header="Operator recovery: /function palemeridian:admin/force {q:\"c1.round\"} marks a stuck quest done and runs its rewards")
    surge_resets = sorted(p_ for p_ in R.functions if p_.startswith("enc/") and p_.endswith("/reset"))
    fn("admin/reset_encounters", [
        *[f"function {fid(p_)}" for p_ in surge_resets],
        f"execute if score #boss pm.world matches 1 run function {fid('c4/boss/reset')}",
        "tellraw @s {\"text\":\"All encounters reset (they restart when a player re-enters them).\",\"color\":\"gold\"}",
    ], header="Operator recovery: stop and reset every running encounter")
    fn("admin/goto", [
        f"$function {fid('poi/get')} {{name:\"$(poi)\"}}",
        f"function {fid('admin/_goto')} with storage palemeridian:tmp poi",
    ], header="QA helper: /function palemeridian:admin/goto {poi:\"hollin.plaza\"} (names in tools/generated/poi.json)")
    fn("admin/_goto", ["$tp @s $(x) $(y) $(z)"])
    fn("admin/host", ["tag @s add pm.host", "tellraw @s {\"text\":\"You are now the Pale Meridian host (can change world settings).\",\"color\":\"gold\"}"])
    fn("admin/refresh", [
        f"function {fid('q/_advance')}", f"function {fid('hud/refresh')}", f"function {fid('npc/_maintain_all')}",
        "execute as @a run function palemeridian:journal/sync",
        "tellraw @s {\"text\":\"Re-evaluated quests, HUD, NPCs and journals (non-destructive).\",\"color\":\"gold\"}",
    ], header="Non-destructive repair: re-run activation rules, HUD, NPC placement and journal sync")
