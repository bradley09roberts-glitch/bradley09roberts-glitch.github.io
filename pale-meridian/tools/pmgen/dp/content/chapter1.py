"""CHAPTER 1 — Hollin, Unremembered."""
from __future__ import annotations

from ... import poi
from .. import fid, snbt, tag, tellraw, xyz, adv
from ..encounters import Surge, register as register_surge
from ..engine import (R, Area, Blueprint, Choice, Dlg, NPC, Part, Quest, activate, active, announce, complete, done,
                      not_done, show, uuid_nbt, uuid_str)
from .prologue import CHAINS

GRATES = [f"minecraft:{p}copper_grate" for p in ("", "exposed_", "weathered_", "oxidized_", "waxed_", "waxed_exposed_", "waxed_weathered_", "waxed_oxidized_")]
ORDER = ["dunn", "pell", "marsh", "tarn"]   # the Round: bread, water, words, home


def _echo(place: str, lines: list, x, y, z) -> list:
    """A brief ghost-scene: narration, a hanging line of pale text, particles and sound."""
    key = f"echo:{place}"
    u = uuid_str(key)
    return [
        f"scoreboard players set #c1.name.{place} pm.world 1",
        "scoreboard players add c1.names pm.qp 1",
        f"particle minecraft:white_ash {x} {y + 1} {z} 1.2 1 1.2 0.01 120 normal",
        f"playsound minecraft:block.amethyst_block.resonate master @a {x} {y} {z} 1 0.6",
        f"execute unless entity {u} run summon minecraft:text_display {x} {y + 1.6} {z} {{UUID:{uuid_nbt(key)},billboard:\"center\","
        f"text:{snbt({'text': lines[0], 'color': 'white', 'italic': True})},background:0,text_opacity:170,"
        f"Tags:[\"pm.echo\"],transformation:{{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.7f,0.7f,0.7f]}}}}",
        f"schedule function {fid('c1/echo_fade')} 200t append",
        *[tellraw("@a", {"text": ln, "color": "gray", "italic": True}) for ln in lines[1:]],
        f"function {fid('hud/refresh')}",
        f"execute if score c1.names pm.qp matches 4.. run {complete('c1.names')}",
    ]


def register() -> None:
    tag("block", "palemeridian", "grates", GRATES)
    tag("block", "palemeridian", "chains", CHAINS)
    gate = poi.get("hollin.gate_area")["box"]
    R.area(Area("hollin_gate", gate))
    for place in ("bakery", "hall", "well", "ferry"):
        R.area(Area(f"hollin_{place}", poi.get(f"hollin.{place}")["box"]))
    R.area(Area("hollin_belfry", poi.get("hollin.belfry")["box"]))
    R.area(Area("hollin_plaza", poi.get("hollin.plaza")["box"]))

    # ============================================================== quests
    R.quest(Quest("c1.odile", 1, "The Lamplighter", "Speak with the lamplighter at Hollin's gate.",
                  "Just inside Hollin's gate, an old woman is lighting the only lit lamp in the village.",
                  prereq=["p.road"], target="hollin.odile", icon="minecraft:lantern",
                  on_activate=["setworldspawn 107 67 150 180 0"]))
    R.quest(Quest("c1.names", 1, "Hollin, Unremembered", "Find the names of Hollin's four lost places.",
                  "The lamplighter says places keep what people drop: the bakery, the well, the hall and the ferry steps.",
                  prereq=["c1.odile"], progress_max=4, target="hollin.well", icon="minecraft:name_tag",
                  hint="Walk into the bakery, the hall, the well in the square and the ferry steps down by the lake."))
    R.quest(Quest("c1.round", 1, "The Lamplighters' Round", "Ring Hollin's four bells in the order of the Round.",
                  "Each bell in the tower carries a family name. Each family kept one of Hollin's places. The Round, painted in the Hall, gives the order.",
                  prereq=["c1.names"], target="hollin.bell.marsh", icon="minecraft:bell", frame="goal",
                  hint="Families: Dunn kept the bakery, Pell the well, Marsh the hall, Tarn the ferry. The Round: 'Bread for the morning, water for noon, words for the evening, the ferry for home.'"))
    R.quest(Quest("c1.lamp", 1, "Hollin's Wakelamp", "Rebuild the Wakelamp at the top of the bell tower.",
                  "Above the belfry is the lamp cradle. The Stillglass Lamp needs its cage: four copper grates around it and two chains above.",
                  prereq=["c1.round"], target="hollin.wakelamp", progress_max=7, icon="minecraft:pearlescent_froglight", frame="goal",
                  hint="Copper grates are crafted from copper blocks at a stonecutter or crafting table. Chains need iron. The lamp is in the cradle chest."))
    R.quest(Quest("c1.surge", 1, "Hold the Light", "Relight the four plaza lamps while the Pall pushes back.",
                  "Lighting the Wakelamp woke the fog. Watchers are in the square. They move only when nobody is looking at them.",
                  prereq=["c1.lamp"], target="hollin.plaza", progress_max=4, icon="minecraft:copper_bulb", frame="challenge",
                  hint="Keep your eyes on the Watchers while you walk between lamps. One hit breaks an unbound Watcher.",
                  on_complete=[f"function {fid('c1/restored')}"]))
    R.quest(Quest("c1.home", 1, "A Place to Rest", "Place a bed in the Surveyor's Rest.",
                  "Odile says the empty cottage by the well was always kept for travellers. It is yours.",
                  prereq=["c1.surge"], main=False, target="hollin.home.rest", icon="minecraft:red_bed"))

    # ============================================================== echoes (names)
    echoes = {
        "bakery": ["Mother Dunn says the loaves won't wait!",
                   "The smell of bread that isn't there. Over the counter, painted and half-flaked: DUNN & DAUGHTER.",
                   "This was the Dunns' bakery. Bread."],
        "well": ["One for the bell, one for the rope. Pell always pays.",
                 "A coin rings on the well's edge, and an old man's voice counts under his breath.",
                 "The Pells kept the well. Water."],
        "hall": ["Again, children, and louder. The Marsh way!",
                 "Children's voices recite something in the empty hall, led by a woman who keeps time with a lamp hook.",
                 "The Marshes taught in the hall. Words."],
        "ferry": ["Tarn's ferry! Last crossing for the Keeper's lamp-night!",
                  "Oars dip in water that isn't moving. A ferryman calls from somewhere out on the lake.",
                  "The Tarns ran the ferry. Home."],
    }
    for place, lines in echoes.items():
        box = poi.get(f"hollin.{place}")["box"]
        x, y, z = (box[0] + box[3]) // 2, box[1] + 1, (box[2] + box[5]) // 2
        R.func(f"c1/echo/{place}", ["execute if score #c1.name." + place + " pm.world matches 1 run return fail"] + _echo(place, lines, x, y, z))
        R.location_hooks.append((f"hollin_{place}", active("c1.names"), [f"function {fid('c1/echo/' + place)}"]))
    R.func("c1/echo_fade", ["kill @e[type=text_display,tag=pm.echo]"])

    # ============================================================== the Round (bells)
    for i, fam in enumerate(ORDER):
        bp = poi.get(f"hollin.bell.{fam}")
        R.block_use.append((f"bell_{fam}", (int(bp["x"]), int(bp["y"]), int(bp["z"])), [f"function {fid('c1/bell')} {{n:{i + 1}}}"]))
    R.func("c1/bell", [
        "execute unless score c1.round pm.q matches 1 run return fail",
        "$scoreboard players set #bell pm.tmp $(n)",
        "scoreboard players operation #expect pm.tmp = #round.step pm.world",
        "scoreboard players add #expect pm.tmp 1",
        f"execute unless score #bell pm.tmp = #expect pm.tmp run return run function {fid('c1/bell_wrong')}",
        "scoreboard players add #round.step pm.world 1",
        "execute if score #round.step pm.world matches 1 run playsound minecraft:block.note_block.bell master @a ~ ~ ~ 1 0.7",
        "execute if score #round.step pm.world matches 2 run playsound minecraft:block.note_block.bell master @a ~ ~ ~ 1 0.84",
        "execute if score #round.step pm.world matches 3 run playsound minecraft:block.note_block.bell master @a ~ ~ ~ 1 1.0",
        "execute if score #round.step pm.world matches 4 run playsound minecraft:block.note_block.bell master @a ~ ~ ~ 1 1.26",
        f"execute if score #round.step pm.world matches 4.. run function {fid('c1/round_solved')}",
    ])
    R.func("c1/bell_wrong", [
        "scoreboard players set #round.step pm.world 0",
        "playsound minecraft:block.anvil.land master @a ~ ~ ~ 0.4 0.5",
        "title @s actionbar {\"text\":\"The bells clash. The Round starts again from the beginning.\",\"color\":\"gray\",\"italic\":true}",
    ])
    hatch, hatch_top = poi.get("hollin.hatch"), poi.get("hollin.hatch_top")
    chest = poi.get("hollin.cradle_chest")
    R.func("c1/round_solved", [
        tellraw("@a", {"text": "The last bell's note hangs in the fog, and somewhere above the belfry a latch falls open.", "color": "gray", "italic": True}),
        f"setblock {xyz(hatch)} minecraft:iron_trapdoor[facing=south,half=bottom,open=true,powered=false,waterlogged=false]",
        f"setblock {xyz(hatch_top)} minecraft:iron_trapdoor[facing=south,half=bottom,open=true,powered=false,waterlogged=false]",
        f"function {fid('c1/cradle_refill')}",
        complete("c1.round"),
    ])
    R.func("c1/cradle_refill", [
        "# Places the Stillglass Lamp in the cradle chest if no lamp is in the chest or already built.",
        f"execute if block {xyz(poi.get('hollin.wakelamp'))} minecraft:pearlescent_froglight run return fail",
        f"execute if items block {xyz(chest)} container.* minecraft:pearlescent_froglight run return fail",
        f"item replace block {xyz(chest)} container.13 with minecraft:pearlescent_froglight[minecraft:custom_name={snbt({'text': 'Stillglass Lamp', 'italic': False, 'color': 'aqua'})},"
        f"minecraft:lore=[{snbt({'text': 'It holds light the way a bell holds a note.', 'italic': False, 'color': 'gray'})}]] 1",
    ])
    # recovery: while the lamp quest is active, keep the hatch open and the lamp available (checked on approach)
    R.area(Area("hollin_tower_top", poi.get("hollin.cradle")["box"]))
    R.location_hooks.append(("hollin_tower_top", active("c1.lamp"), [
        "execute if score #c1.refill pm.world >= #seconds pm.world run return fail",
        "scoreboard players operation #c1.refill pm.world = #seconds pm.world",
        "scoreboard players add #c1.refill pm.world 60",
        f"execute unless entity @a[predicate={fid('holds_stillglass')}] run function {fid('c1/cradle_refill')}",
    ]))
    R.load_hooks.append(f"execute if score c1.round pm.q matches 2 run setblock {xyz(hatch)} minecraft:iron_trapdoor[facing=south,half=bottom,open=true,powered=false,waterlogged=false]")
    R.load_hooks.append(f"execute if score c1.round pm.q matches 2 run setblock {xyz(hatch_top)} minecraft:iron_trapdoor[facing=south,half=bottom,open=true,powered=false,waterlogged=false]")
    from .. import predicate
    predicate("holds_stillglass", {"condition": "minecraft:entity_properties", "entity": "this",
                                   "predicate": {"minecraft:slots": {"container.*": {"items": "minecraft:pearlescent_froglight"}}}})
    # bells must exist while the puzzle is open (diegetic auto-repair)
    for fam in ORDER:
        bp = poi.get(f"hollin.bell.{fam}")
        R.location_hooks.append(("hollin_belfry", active("c1.round"), [
            f"execute unless block {xyz(bp)} minecraft:bell run setblock {xyz(bp)} minecraft:bell[attachment=ceiling,facing=north,powered=false]"]))

    # ============================================================== the Wakelamp blueprint
    lamp = poi.get("hollin.wakelamp")
    accepts = {"pearlescent_froglight": ["minecraft:pearlescent_froglight"], "copper_grate": GRATES, "iron_chain[axis=y]": CHAINS}
    R.blueprint(Blueprint("hollin_lamp", "c1.lamp", [Part(tuple(p["pos"]), accepts[p["block"]], p["block"]) for p in lamp["parts"]],
                          center=(lamp["x"], lamp["y"], lamp["z"]), radius=12,
                          on_complete=[
                              f"particle minecraft:end_rod {xyz(lamp, 0.5)} 0.5 0.5 0.5 0.03 80 normal",
                              f"playsound minecraft:block.beacon.activate master @a {xyz(lamp)} 2 1.0",
                              tellraw("@a", {"text": "The Stillglass Lamp kindles inside its copper cage — and the whole fog seems to flinch.", "color": "gray", "italic": True}),
                              complete("c1.lamp")]))

    # ============================================================== the surge in the square
    plaza = poi.get("hollin.plaza")
    lamps = [tuple(p) for p in poi.get("hollin.plaza_lamps")["lamps"]]
    px, py, pz = int(plaza["x"]), 67, int(plaza["z"])
    spawns = [(px - 18, 67, pz - 12), (px + 18, 67, pz - 12), (px - 18, 67, pz + 12), (px + 18, 67, pz + 12),
              (px, 67, pz + 16), (px - 10, 67, pz + 16), (px + 10, 67, pz + 16)]
    register_surge(Surge("hollin", "c1.surge", "Hold the Light", (px, py, pz), 30, spawns, lamps,
                         min_seconds=30, base_watchers=2, per_player=2, cap=7, interval=7))

    # ============================================================== restoration of Hollin
    village_lamps = poi.get("hollin.lamps")["lamps"]
    R.func("c1/restored", [
        "scoreboard players set #r.hollin pm.world 1",
        "scoreboard players set #req.hollin pm.world 1",
        "time of palemeridian:pall set 2000",
        "scoreboard players set #chapter pm.world 2",
        *[f"function {fid('enc/_set_bulb')} {{x:{int(x)},y:{int(y)},z:{int(z)},lit:\"true\"}}" for (x, y, z) in village_lamps + [list(l) for l in lamps]],
        "data modify storage palemeridian:npc odile set value \"restored\"",
        "data modify storage palemeridian:npc mirelle set value \"restored\"",
        "data modify storage palemeridian:npc jory set value \"restored\"",
        "function palemeridian:npc/odile/apply_skin",
        "function palemeridian:npc/mirelle/apply_skin",
        "function palemeridian:npc/jory/apply_skin",
        f"setworldspawn {int(plaza['x'])} 67 {int(plaza['z']) + 10} 180 0",
        "playsound minecraft:block.bell.resonate master @a ~ ~ ~ 2 1.0",
        title_all("Hollin", "remembers"),
        tellraw("@a", {"text": "The fog rolls back from the square like a tide going out. Colour seeps into the thatch, the moss, the faces at the windows.", "color": "gray", "italic": True}),
        tellraw("@a", {"text": "(Hollin is restored. Speak with Odile.)", "color": "dark_aqua"}),
    ])
    # the home plot
    rest = poi.get("hollin.home.rest")["box"]
    adv("trigger/rest_bed", {
        "criteria": {"bed": {"trigger": "minecraft:placed_block", "conditions": {"location": [
            {"condition": "minecraft:location_check", "predicate": {"block": {"blocks": "#minecraft:beds"},
                                                                    "position": {"x": {"min": rest[0], "max": rest[3]}, "y": {"min": rest[1], "max": rest[4]}, "z": {"min": rest[2], "max": rest[5]}}}}]}}},
        "rewards": {"function": fid("c1/rest_bed")},
    })
    R.func("c1/rest_bed", ["advancement revoke @s only palemeridian:trigger/rest_bed", complete("c1.home"),
                           tellraw("@s", {"text": "The Surveyor's Rest has a bed again. It already feels less like a stranger's house.", "color": "gray", "italic": True})])

    # ============================================================== NPCs
    R.npc(NPC("odile", "Odile", "the Lamplighter", "gold", "faded", model="slim", held="minecraft:stick",
              places=[(not_done("c1.surge"), "hollin.odile"), ("", "hollin.odile_home")],
              talk=[
                  (not_done("c1.odile"), "npc/odile/intro"),
                  (active("c1.names"), "npc/odile/names"),
                  (active("c1.round"), "npc/odile/round"),
                  (active("c1.lamp"), "npc/odile/lamp"),
                  (active("c1.surge"), "npc/odile/surge"),
                  ("unless score #odile.told pm.world matches 1", "npc/odile/restored"),
                  ("", "npc/odile/later"),
              ]))
    R.npc(NPC("mirelle", "Mirelle", "the Baker", "red", "faded", model="slim",
              places=[("", "hollin.mirelle")],
              talk=[(not_done("c1.surge"), "npc/mirelle/faded"), ("", "npc/mirelle/restored")]))
    R.npc(NPC("jory", "Jory", "the Bell-ringer", "dark_green", "faded",
              places=[("", "hollin.jory")],
              talk=[(not_done("c1.surge"), "npc/jory/faded"), ("", "npc/jory/restored")]))
    b = poi.get("hollin.bakery")["box"]
    poi.add("hollin.mirelle", b[0] + 2.5, 67, b[2] + 2.5, yaw=90.0)
    poi.add("hollin.jory", 118.5, 67, 104.5, yaw=180.0)
    poi.add("hollin.odile_home", 124.5, 67, 128.5, yaw=200.0)

    # ============================================================== dialogs
    D = R.dlg
    D(Dlg("npc/odile/intro", "", [
        "Oh — a light. You're carrying a light.",
        "I'm the... I light this one. Every evening. I don't remember why the others stay dark.",
        "You came up the Landing road? Nobody comes up the Landing road any more. The road forgets people.",
    ], speaker="odile", choices=[
        Choice("\"I'm looking for a surveyor. Tamsin Reed.\"", goto="npc/odile/intro_tamsin"),
        Choice("\"Who are you?\"", goto="npc/odile/intro_who"),
        Choice("\"What happened to this place?\"", goto="npc/odile/intro_what"),
    ], exit_label="(Say nothing)"))
    D(Dlg("npc/odile/intro_tamsin", "", [
        "A woman with a lantern walks the lanes some nights. She never stops. She never answers.",
        "If she's yours, you'll want to keep her lamp lit. Things that go dark here don't come back on their own.",
    ], speaker="odile", choices=[Choice("\"Then help me find her.\"", goto="npc/odile/intro_help")], exit_label="Back"))
    D(Dlg("npc/odile/intro_who", "", [
        "The lamplighter. That's enough of a name for now, isn't it?",
        "I had another one. It's in here somewhere. Names slip through the fog like fish through a torn net.",
    ], speaker="odile", choices=[Choice("\"Maybe I can help you find it.\"", goto="npc/odile/intro_help")], exit_label="Back"))
    D(Dlg("npc/odile/intro_what", "", [
        "Happened? Nothing happens here. That's the trouble. It's always the same grey evening.",
        "The bells used to ring the Round every night. Now they don't ring at all, and nobody remembers the tune.",
    ], speaker="odile", choices=[Choice("\"What would it take to wake it up?\"", goto="npc/odile/intro_help")], exit_label="Back"))
    D(Dlg("npc/odile/intro_help", "", [
        "Help Hollin remember, then. Places keep what people drop.",
        "The bakery. The well in the square. The hall. The ferry steps down by the water. They all had names once. Walk into them. Listen.",
        "And keep your light up. The fog is cold, and it isn't only cold.",
    ], speaker="odile", choices=[Choice("\"I'll listen.\"", run=[complete("c1.odile")])], exit_label=None))
    D(Dlg("npc/odile/names", "", [
        "Have you found them? The bakery, the well, the hall, the ferry steps.",
        "Stand inside and wait a breath. The old voices are shy.",
    ], speaker="odile"))
    D(Dlg("npc/odile/round", "", [
        "The Round! Oh, I know this. It goes...",
        "Bread for the morning, water for noon, words for the evening, the ferry for home.",
        "Every family kept a bell, you see, and every family kept a place. We rang them in the order of the day.",
    ], speaker="odile"))
    D(Dlg("npc/odile/lamp", "", [
        "The latch is open? Then go up. The lamp needs its cage: copper all round it, and chains to hang from.",
        "My hands remember the shape even if my head doesn't.",
    ], speaker="odile"))
    D(Dlg("npc/odile/surge", "", [
        "Don't look away from them! They only move when you look away!",
    ], speaker="odile"))
    D(Dlg("npc/odile/restored", "", [
        "The fog's gone off the square. I can see the lake. I can see the lake!",
        "Surveyor... what year is it?",
    ], speaker="odile", choices=[Choice("(Tell her)", goto="npc/odile/restored_2")], exit_label=None))
    D(Dlg("npc/odile/restored_2", "", [
        "Forty years. Forty. I thought it was last Tuesday. I thought I'd lit that lamp perhaps a hundred times, not fourteen thousand.",
        "My name is Odile. Odile Marsh. I taught the Round in the hall. I remember now.",
    ], speaker="odile", choices=[Choice("\"What happened forty years ago?\"", goto="npc/odile/restored_3")], exit_label=None))
    D(Dlg("npc/odile/restored_3", "", [
        "The Deepcut fell. The stillglass mine, up under the north cliffs. They rang the bells for hours, calling everyone to dig.",
        "And the Keeper's great light out on the Meridian went dark that same night. Then the fog came in off the water, and it never went home.",
    ], speaker="odile", choices=[Choice("\"And Tamsin?\"", goto="npc/odile/restored_4")], exit_label=None))
    D(Dlg("npc/odile/restored_4", "", [
        "She came through in what I thought was spring. She asked about the Keeper, and then she went east, to the orchards at Aldercross. Brannoc's orchards.",
        "Go carefully. Brannoc was never gentle, and the fog won't have improved him.",
        "Oh — and the empty cottage by the well. The Rest. It was always kept for travellers. It's yours, if you'll have it.",
    ], speaker="odile", choices=[Choice("\"Thank you, Odile.\"", run=["scoreboard players set #odile.told pm.world 1"])], exit_label=None))
    D(Dlg("npc/odile/later", "", [
        "The lamps stay lit on their own now. I keep checking anyway. Forty years is a hard habit.",
    ], speaker="odile"))
    D(Dlg("npc/mirelle/faded", "", ["...the loaves... I was making loaves... is it morning yet?"], speaker="mirelle"))
    D(Dlg("npc/mirelle/restored", "", [
        "You! You're the one who rang the Round. Here, take some bread. Come by any morning; there's always a loaf for whoever keeps the lamps lit.",
    ], speaker="mirelle", choices=[Choice("Take the bread", run=[f"function {fid('c1/mirelle_bread')}"])], exit_label="Maybe later"))
    R.func("c1/mirelle_bread", [
        "execute store result score #day pm.tmp run time query minecraft:day repetition",
        "execute if score @s pm.bread = #day pm.tmp run return run tellraw @s {\"text\":\"Mirelle: You've had today's loaf! Come back in the morning.\",\"color\":\"red\"}",
        "scoreboard players operation @s pm.bread = #day pm.tmp",
        "give @s minecraft:bread 4",
    ])
    R.load_hooks.append("scoreboard objectives add pm.bread dummy")
    D(Dlg("npc/jory/faded", "", ["Pell pays the bell tax. Pell always pays. One for the bell, one for the rope..."], speaker="jory"))
    D(Dlg("npc/jory/restored", "", [
        "Forty years and not one of those bells polished. Disgraceful.",
        "My sister Agnes would have had words. She went up to the Deepcut with the others, that night. Her scarf's still in my loft. I can't bring myself to move it.",
    ], speaker="jory"))


def title_all(t: str, sub: str) -> str:
    return f"title @a title {snbt({'text': t, 'color': 'white'})}" + "\n" + f"title @a subtitle {snbt({'text': sub, 'color': 'gray', 'italic': True})}"
