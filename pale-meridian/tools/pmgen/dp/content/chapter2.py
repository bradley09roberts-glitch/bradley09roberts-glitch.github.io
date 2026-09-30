"""CHAPTER 2 — The Heartwood (Aldercross)."""
from __future__ import annotations

from ... import poi
from .. import fid, snbt, tag, tellraw, xyz
from ..engine import (R, Area, Blueprint, Choice, Dlg, NPC, Part, Quest, activate, active, complete, done, not_done, show,
                      uuid_nbt, uuid_str)
from .prologue import LANTERNS


def _echo(key: str, lines: list, x, y, z, counter: str, total: int, quest: str) -> list:
    u = uuid_str(f"echo:{key}")
    return [
        f"scoreboard players set #c2.mem.{key} pm.world 1",
        f"scoreboard players add {counter} pm.qp 1",
        f"particle minecraft:white_ash {x} {y + 1} {z} 1.2 1 1.2 0.01 120 normal",
        f"playsound minecraft:block.amethyst_block.resonate master @a {x} {y} {z} 1 0.7",
        f"execute unless entity {u} run summon minecraft:text_display {x} {y + 1.6} {z} {{UUID:{uuid_nbt('echo:' + key)},billboard:\"center\","
        f"text:{snbt({'text': lines[0], 'color': 'white', 'italic': True})},background:0,text_opacity:170,Tags:[\"pm.echo\"],"
        f"transformation:{{left_rotation:[0f,0f,0f,1f],right_rotation:[0f,0f,0f,1f],translation:[0f,0f,0f],scale:[0.7f,0.7f,0.7f]}}}}",
        f"schedule function {fid('c1/echo_fade')} 200t append",
        *[tellraw("@a", {"text": ln, "color": "gray", "italic": True}) for ln in lines[1:]],
        f"function {fid('hud/refresh')}",
        f"execute if score {counter} pm.qp matches {total}.. run {complete(quest)}",
    ]


def register() -> None:
    R.area(Area("alder_gate", poi.get("aldercross.gate_area")["box"]))
    for place in ("apiary", "press", "tree"):
        R.area(Area(f"alder_{place}", poi.get(f"aldercross.{place}")["box"]))
    R.area(Area("alder_heartwood", poi.get("aldercross.heartwood")["box"]))

    # ============================================================== quests
    R.quest(Quest("c2.arrive", 2, "The Orchard Road", "Travel east to the orchards of Aldercross.",
                  "Odile says Tamsin went east, to Brannoc's orchards at Aldercross.",
                  prereq=["c1.surge"], target="aldercross.gate", icon="minecraft:apple"))
    R.quest(Quest("c2.brannoc", 2, "The Orchard Keeper", "Speak with the orchard keeper.",
                  "A broad, bearded man stands at the edge of his orchard as though guarding it from the trees.",
                  prereq=["c2.arrive"], target="aldercross.brannoc", icon="minecraft:honeycomb"))
    R.quest(Quest("c2.memories", 2, "Remember Aldercross", "Find the three places Aldercross remembers.",
                  "Brannoc won't say what the orchard has forgotten. The hives, the cider press and the old tree by the wall might.",
                  prereq=["c2.brannoc"], progress_max=3, target="aldercross.tree", icon="minecraft:name_tag",
                  hint="Walk into the apiary, the cider press barn and the shade of the old oak by the south wall."))
    R.quest(Quest("c2.hearts", 2, "The Heartwood", "Break the three hearts grown into the Heartwood.",
                  "Pale roots have strangled the windmill's lamp chamber. They grow from the Heartwood, and the Heartwood grows from three hearts.",
                  prereq=["c2.memories"], progress_max=3, target="aldercross.heartwood", icon="minecraft:creaking_heart", frame="challenge",
                  hint="A Watcher bound to a heart cannot be hurt. Hit it anyway and watch where the resin light runs: that is its heart. One heart is in the trunk, one in each root arm."))
    R.quest(Quest("c2.lamp", 2, "The Orchard Wakelamp", "Rebuild the Wakelamp in the windmill's cap.",
                  "With the roots withered, the windmill's lamp chamber is open. Brannoc has something for you.",
                  prereq=["c2.hearts"], target="aldercross.wakelamp", progress_max=7, icon="minecraft:pearlescent_froglight", frame="goal",
                  hint="The lamp cage here is four lanterns at the corners and two honeycomb blocks. Brannoc keeps honeycomb in the apiary shed.",
                  on_complete=[f"function {fid('c2/restored')}"]))

    # ============================================================== arrival
    R.location_hooks.append(("alder_gate", active("c2.arrive"), [complete("c2.arrive")]))

    # ============================================================== memories (echoes)
    echoes = {
        "apiary": ["Col! Leave the queen alone, you'll get us both stung!",
                   "Bees that aren't there hum around the empty hives. Two boys' voices, laughing.",
                   "Brannoc and his brother kept these hives together."],
        "press": ["One more barrel and then the Deepcut. Tobin says the pay's double past the blue seam.",
                  "The smell of crushed apples. A young man's voice, cheerful, saying goodbye to someone.",
                  "In the loft, a bedroll and a lamp that is still warm, and a note in a hand you know."],
        "tree": ["B + C. Carved the summer we were nine and ten.",
                 "Bark cut deep with two sets of initials. Someone has touched them so often the letters shine.",
                 "The brothers' tree."],
    }
    for key, lines in echoes.items():
        box = poi.get(f"aldercross.{key}")["box"]
        x, y, z = (box[0] + box[3]) // 2, box[1] + 1, (box[2] + box[5]) // 2
        R.func(f"c2/echo/{key}", [f"execute if score #c2.mem.{key} pm.world matches 1 run return fail"]
               + _echo(key, lines, x, y, z, "c2.memories", 3, "c2.memories"))
        R.location_hooks.append((f"alder_{key}", active("c2.memories"), [f"function {fid('c2/echo/' + key)}"]))
    # Tamsin's second note (always readable once found)
    R.func("c2/tamsin_note", [
        tellraw("@a", [{"text": "Tamsin's note, pinned to the loft beam: ", "color": "gray", "italic": True},
                       {"text": "\"It isn't weather. It's attention. The fog is worst where the valley refuses to look. I'm going to the Glassworks. Something up there was struck off the map.\"", "color": "white", "italic": True}]),
    ])
    R.location_hooks.append(("alder_press", "if score #c2.mem.press pm.world matches 1 unless score #c2.note pm.world matches 1",
                             ["scoreboard players set #c2.note pm.world 1", f"function {fid('c2/tamsin_note')}"]))

    # ============================================================== the Heartwood
    hearts = [tuple(int(v) for v in h) for h in poi.get("aldercross.hearts")["hearts"]]
    count = ["scoreboard players set #hearts pm.tmp 0"]
    for (x, y, z) in hearts:
        count.append(f"execute unless block {x} {y} {z} minecraft:creaking_heart run scoreboard players add #hearts pm.tmp 1")
    R.func("c2/hearts_check", count + [
        "execute unless score #hearts pm.tmp = c2.hearts pm.qp run function palemeridian:c2/hearts_progress",
        f"execute if score #hearts pm.tmp matches {len(hearts)}.. run function {fid('c2/hearts_done')}",
    ])
    R.func("c2/hearts_progress", [
        "scoreboard players operation c2.hearts pm.qp = #hearts pm.tmp",
        "playsound minecraft:block.creaking_heart.break master @a ~ ~ ~ 1 0.6",
        f"function {fid('hud/refresh')}",
    ])
    roots = poi.get("aldercross.roots")["blocks"]
    R.func("c2/hearts_done", [
        *[f"setblock {int(x)} {int(y)} {int(z)} minecraft:air destroy" for (x, y, z) in roots],
        tellraw("@a", {"text": "With the last heart broken, the Heartwood shudders. Up in the windmill, pale roots crack, blacken and fall away.", "color": "gray", "italic": True}),
        complete("c2.hearts"),
    ])
    wx, wy, wz = poi.get("aldercross.heartwood")["x"], poi.get("aldercross.heartwood")["y"], poi.get("aldercross.heartwood")["z"]
    R.slow_hooks.append(f"execute if score c2.hearts pm.q matches 1 positioned {int(wx)} {int(wy)} {int(wz)} if entity @a[distance=..40] run function {fid('c2/hearts_check')}")

    # ============================================================== the orchard Wakelamp
    lamp = poi.get("aldercross.wakelamp")
    accepts = {"pearlescent_froglight": ["minecraft:pearlescent_froglight"], "lantern[hanging=false]": LANTERNS,
               "honeycomb_block": ["minecraft:honeycomb_block"]}
    R.blueprint(Blueprint("orchard_lamp", "c2.lamp", [Part(tuple(p["pos"]), accepts[p["block"]], p["block"]) for p in lamp["parts"]],
                          center=(lamp["x"], lamp["y"], lamp["z"]), radius=12,
                          on_complete=[f"particle minecraft:end_rod {xyz(lamp, 0.5)} 0.5 0.5 0.5 0.03 80 normal",
                                       f"playsound minecraft:block.beacon.activate master @a {xyz(lamp)} 2 1.1",
                                       complete("c2.lamp")]))
    R.func("c2/give_lamp", [
        "execute if score #c2.lamp_given pm.world matches 1 if entity @a[predicate=palemeridian:holds_stillglass] run return run tellraw @s {\"text\":\"Brannoc: Somebody already has Col's lamp. Up the mill with it.\",\"color\":\"gold\"}",
        f"execute if block {xyz(lamp)} minecraft:pearlescent_froglight run return fail",
        "scoreboard players set #c2.lamp_given pm.world 1",
        f"give @s minecraft:pearlescent_froglight[minecraft:custom_name={snbt({'text': 'Stillglass Lamp', 'italic': False, 'color': 'aqua'})},minecraft:lore=[{snbt({'text': 'Col Hale made this one. His initials are on the base.', 'italic': False, 'color': 'gray'})}]] 1",
    ])
    hives = poi.get("aldercross.hives")["hives"]
    ab = poi.get("aldercross.apiary")["box"]
    shed = poi.get("aldercross.apiary_chest")
    R.location_hooks.append(("alder_apiary", active("c2.lamp"), [
        "# Recovery: keep enough honeycomb available for the orchard lamp (checked at most once a minute).",
        "execute if score #c2.refill pm.world >= #seconds pm.world run return fail",
        "scoreboard players operation #c2.refill pm.world = #seconds pm.world",
        "scoreboard players add #c2.refill pm.world 60",
        f"execute if items block {xyz(shed)} container.* minecraft:honeycomb run return fail",
        "execute if entity @a[predicate=palemeridian:holds_honeycomb] run return fail",
        f"item replace block {xyz(shed)} container.0 with minecraft:honeycomb 8",
    ]))
    from .. import predicate
    predicate("holds_honeycomb", {"condition": "minecraft:entity_properties", "entity": "this",
                                  "predicate": {"minecraft:slots": {"container.*": {"items": ["minecraft:honeycomb", "minecraft:honeycomb_block"]}}}})
    alamps = poi.get("aldercross.lamps")["lamps"]
    R.func("c2/restored", [
        "scoreboard players set #r.aldercross pm.world 1",
        "scoreboard players set #req.aldercross pm.world 1",
        "time of palemeridian:pall set 3000",
        "scoreboard players set #chapter pm.world 3",
        *[f"function {fid('enc/_set_bulb')} {{x:{int(x)},y:{int(y)},z:{int(z)},lit:\"true\"}}" for (x, y, z) in alamps],
        *[f"summon minecraft:bee {int(x)} {int(y) + 1} {int(z) + 1} {{PersistenceRequired:1b,hive_pos:[I;{int(x)},{int(y)},{int(z)}]}}" for (x, y, z) in hives[:6]],
        f"fill {ab[0]} {ab[1]} {ab[2]} {ab[3]} {ab[4]} {ab[5]} minecraft:cornflower replace minecraft:closed_eyeblossom",
        f"fill {ab[0]} {ab[1]} {ab[2]} {ab[3]} {ab[4]} {ab[5]} minecraft:dandelion replace minecraft:pale_moss_carpet",
        "data modify storage palemeridian:npc brannoc set value \"restored\"",
        "function palemeridian:npc/brannoc/apply_skin",
        f"title @a title {snbt({'text': 'Aldercross', 'color': 'white'})}",
        f"title @a subtitle {snbt({'text': 'remembers', 'color': 'gray', 'italic': True})}",
        "playsound minecraft:block.beehive.work master @a ~ ~ ~ 1 1",
        tellraw("@a", {"text": "The windmill's lamp swings into life. The fog peels off the orchard rows, and somewhere in the apiary the first bee in forty years goes about its business.", "color": "gray", "italic": True}),
        tellraw("@a", {"text": "(Aldercross is restored. Speak with Brannoc.)", "color": "dark_aqua"}),
    ])

    # ============================================================== Brannoc
    R.npc(NPC("brannoc", "Brannoc", "the Orchard Keeper", "yellow", "faded",
              places=[("", "aldercross.brannoc")],
              talk=[
                  ("unless score c2.arrive pm.q matches 1..", "npc/brannoc/early"),
                  (not_done("c2.brannoc"), "npc/brannoc/intro"),
                  (active("c2.memories"), "npc/brannoc/memories"),
                  (active("c2.hearts"), "npc/brannoc/hearts"),
                  (active("c2.lamp"), "npc/brannoc/lamp"),
                  ("unless score #brannoc.told pm.world matches 1 if score c2.lamp pm.q matches 2", "npc/brannoc/restored"),
                  ("if score c2.lamp pm.q matches 2", "npc/brannoc/later"),
              ]))
    D = R.dlg
    D(Dlg("npc/brannoc/early", "", [
        "Stop there. Don't touch the trees. They're watching.",
        "Nobody comes up from the west any more. The bells in Hollin haven't rung in years. When they do, come back and I'll believe you.",
    ], speaker="brannoc"))
    D(Dlg("npc/brannoc/intro", "", [
        "Stop there. Don't touch the trees. They're watching.",
        "The big one. The Heartwood. It wasn't big before the fog. It grew on what we forgot.",
    ], speaker="brannoc", choices=[
        Choice("\"Who are you?\"", goto="npc/brannoc/intro_who"),
        Choice("\"I'm looking for Tamsin Reed.\"", goto="npc/brannoc/intro_tamsin"),
        Choice("\"I lit Hollin's lamp. I can help here too.\"", goto="npc/brannoc/intro_help"),
    ], exit_label="(Back away)"))
    D(Dlg("npc/brannoc/intro_who", "", [
        "Brannoc. I keep bees. Kept. No flowers, no bees. No bees, no Brannoc worth the name.",
    ], speaker="brannoc", choices=[Choice("\"What does the orchard need?\"", goto="npc/brannoc/intro_help")], exit_label="Back"))
    D(Dlg("npc/brannoc/intro_tamsin", "", [
        "The woman with the lantern. Slept in my barn. Asked about my brother. I told her to mind her business.",
        "She went on north. They all go north, in the end.",
    ], speaker="brannoc", choices=[Choice("\"What does the orchard need?\"", goto="npc/brannoc/intro_help")], exit_label="Back"))
    D(Dlg("npc/brannoc/intro_help", "", [
        "Help? Then remember something. Anything. This orchard's forgotten its own name.",
        "The hives. The press. The old tree by the south wall. Go and stand in them. I can't. Not any more.",
    ], speaker="brannoc", choices=[Choice("\"I'll go.\"", run=[complete("c2.brannoc")])], exit_label=None))
    D(Dlg("npc/brannoc/memories", "", ["The hives. The press. The old tree by the wall. Go on."], speaker="brannoc"))
    D(Dlg("npc/brannoc/hearts", "", [
        "You've seen him, haven't you. Col. My little brother. He laughed in this orchard every summer of his life.",
        "And the fog made a tree out of what's left.",
        "There's three hearts in the Heartwood. Hit one of those staring things and watch where the light runs. That's where its heart is. Break the hearts, and the roots will let go of my mill.",
    ], speaker="brannoc"))
    D(Dlg("npc/brannoc/lamp", "", [
        "Roots are gone. I heard them go.",
        "Col made that lamp for the mill, the year before. Take it up. Four lanterns round it and the honeycomb blocks either side, the way he had it.",
    ], speaker="brannoc", choices=[Choice("Take Col's lamp", run=[f"function {fid('c2/give_lamp')}"])], exit_label="Not yet"))
    D(Dlg("npc/brannoc/restored", "", [
        "Hear that? Bees. Forty years of quiet, and now bees.",
        "Col died in the Deepcut. Eleven of them did.",
    ], speaker="brannoc", choices=[Choice("\"What happened in the Deepcut?\"", goto="npc/brannoc/restored_2")], exit_label=None))
    D(Dlg("npc/brannoc/restored_2", "", [
        "The Keeper sent them past the blue seam. The safe seam. More stillglass, for her lens. Her great lens that could see the whole valley at once.",
        "Hesper Vane. Remember that name, Surveyor. I have. For forty years. Even in the fog.",
        "Your Tamsin went north, to the Glassworks. She had the look of someone who'd done a sum and hated the answer.",
    ], speaker="brannoc", choices=[Choice("\"I'm sorry about Col.\"", run=["scoreboard players set #brannoc.told pm.world 1",
                                                                          f"function {fid('c2/col_token')}"])], exit_label=None))
    R.func("c2/col_token", [
        "execute if score #k.2 pm.world matches 1 run return run tellraw @s {\"text\":\"Brannoc: Keep his token. He'd have liked you.\",\"color\":\"yellow\"}",
        "tellraw @s {\"text\":\"Brannoc: So am I. Here. He carved it for luck underground. Didn't work, but it was his.\",\"color\":\"yellow\"}",
        f"give @s minecraft:apple[minecraft:custom_name={snbt({'text': 'Carved apple token', 'italic': False})},minecraft:lore=[{snbt({'text': 'Wood, carved into an apple. C.H.', 'italic': False, 'color': 'gray'})}],minecraft:custom_data={{pm:{{keepsake:2}}}},!minecraft:consumable] 1",
    ])
    D(Dlg("npc/brannoc/later", "", [
        "Honey's coming back. Apples too. Take some, you've earned them.",
    ], speaker="brannoc", choices=[Choice("Take apples and honey", run=[f"function {fid('c2/gift')}"])], exit_label="Maybe later"))
    R.func("c2/gift", [
        "execute store result score #day pm.tmp run time query minecraft:day repetition",
        "execute if score @s pm.gift2 = #day pm.tmp run return run tellraw @s {\"text\":\"Brannoc: Tomorrow. Trees don't hurry.\",\"color\":\"yellow\"}",
        "scoreboard players operation @s pm.gift2 = #day pm.tmp",
        "give @s minecraft:apple 3",
        "give @s minecraft:honey_bottle 1",
    ])
    R.load_hooks.append("scoreboard objectives add pm.gift2 dummy")
