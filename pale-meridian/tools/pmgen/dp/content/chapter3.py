"""CHAPTER 3 — The Glassworks (and the Deepcut)."""
from __future__ import annotations

from ... import poi
from .. import fid, predicate, snbt, tag, tellraw, xyz
from ..encounters import Surge, register as register_surge
from ..engine import (R, Area, Blueprint, Choice, Dlg, NPC, Part, Quest, activate, active, complete, done, not_done, show,
                      uuid_nbt, uuid_str)
from ..items import book_give
from .keepsakes import ELEVEN

GLASS = ["minecraft:glass", "minecraft:tinted_glass"] + [f"minecraft:{c}_stained_glass" for c in (
    "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray", "cyan", "purple", "blue",
    "brown", "green", "red", "black")]

STILLGLASS = snbt({"text": "Stillglass Lamp", "italic": False, "color": "aqua"})
LENS_HEART = ("minecraft:heart_of_the_sea[minecraft:custom_name=" + snbt({"text": "Lens Heart", "italic": False, "color": "aqua"}) +
              ",minecraft:lore=[" + snbt({"text": "Stillglass, forged in Kiln Three.", "italic": False, "color": "gray"}) + "," +
              snbt({"text": "It is cold, and it looks back.", "italic": False, "color": "gray"}) + "]"
              ",minecraft:custom_data={pm:{item:\"lens_heart\"}}]")


def _has(item_id: str) -> str:
    return f"items entity @s container.* *[minecraft:custom_data~{{pm:{{item:\"{item_id}\"}}}}]"


def register() -> None:
    tag("block", "palemeridian", "glass_blocks", GLASS)
    R.area(Area("glass_gate", poi.get("glassworks.gate_area")["box"]))
    R.area(Area("glass_kiln", poi.get("glassworks.kiln_area")["box"]))
    R.area(Area("glass_workshop", poi.get("glassworks.workshop")["box"]))
    R.area(Area("glass_office", poi.get("glassworks.office")["box"]))
    R.area(Area("deep_gallery", poi.get("deepcut.gallery")["box"]))
    R.area(Area("deep_memorial", poi.get("deepcut.memorial")["box"]))
    R.area(Area("deep_inside", poi.get("deepcut.inside")["box"]))
    R.area(Area("deep_seam", poi.get("deepcut.seam")["box"]))

    # ============================================================== quests
    R.quest(Quest("c3.arrive", 3, "Under the Cliffs", "Follow Tamsin north to the Glassworks.",
                  "Brannoc says Tamsin went north, to the Glassworks at the foot of the cliffs.",
                  prereq=["c2.lamp"], target="glassworks.gate", icon="minecraft:bricks"))
    R.quest(Quest("c3.log", 3, "The Foreman's Log", "Read the last log in the foreman's office.",
                  "The foreman's office stands beside the mine mouth. Whoever ran the Glassworks kept a log.",
                  prereq=["c3.arrive"], target="glassworks.log", icon="minecraft:writable_book"))
    R.quest(Quest("c3.tamsin", 3, "Into the Deepcut", "Find Tamsin inside the Deepcut.",
                  "Tamsin's tracks lead into the mine. Stairs past the assay room climb to an upper gallery.",
                  prereq=["c3.log"], target="deepcut.tamsin", icon="minecraft:lantern",
                  hint="Carry a light, and don't turn your back on anything that moves only when you look away. Follow the adit, then the side tunnel east to the assay room and the stairs."))
    R.quest(Quest("c3.remind", 3, "Something of Hers", "Show Tamsin three things that are hers.",
                  "Tamsin has faded like the others. She might remember herself if she sees things that belong to her.",
                  prereq=["c3.tamsin"], progress_max=3, target="deepcut.tamsin", icon="minecraft:spyglass",
                  hint="Her letter (the noticeboard at the Landing keeps a copy), her field notes (her camp in the Aldercross cider-press loft) and her theodolite (the Glassworks office)."))
    R.quest(Quest("c3.memorial", 3, "The Last Gallery", "Go past the blue seam to where the Deepcut fell.",
                  "Tamsin says the forgetting starts at the far end of the mine, past the old safe limit.",
                  prereq=["c3.remind"], target="deepcut.memorial_wall", icon="minecraft:candle", frame="goal",
                  hint="Follow the main adit north past the blue-stained walls. The way is blocked by rubble: dig through it."))
    R.quest(Quest("c3.kiln", 3, "Kiln Three", "Fire Kiln Three the way the foreman's log describes.",
                  "Stillglass needs fire to wake. Kiln Three can forge a lamp for the Glassworks, and something more.",
                  prereq=["c3.memorial"], target="glassworks.firebox", icon="minecraft:blast_furnace",
                  hint="Set the three levers the way the log says (a wall lever points up when it is off), then put 8 coal or charcoal in the firebox barrel."))
    R.quest(Quest("c3.lamp", 3, "The Glassworks Wakelamp", "Rebuild the Wakelamp on top of the kiln tower.",
                  "The kiln hatch holds a new Stillglass Lamp. The tower's lamp cage wants four glass blocks and two iron bars.",
                  prereq=["c3.kiln"], target="glassworks.wakelamp", progress_max=7, icon="minecraft:pearlescent_froglight", frame="goal",
                  hint="Climb the ladder inside the kiln tower. Glass can be smelted from sand (heaps behind the workshop); the workshop keeps some too."))
    R.quest(Quest("c3.surge", 3, "The Collapse", "Relight the four candles in the kiln yard before the Pall closes in.",
                  "The Pall answers the lamp from the mine mouth. Keep the Watchers in sight and relight the candles.",
                  prereq=["c3.lamp"], target="glassworks.yard", progress_max=4, icon="minecraft:candle", frame="challenge",
                  hint="Use a candle (or flint and steel) to relight it. You have two minutes; if the Pall wins, step back into the yard to try again.",
                  on_complete=[f"function {fid('c3/restored')}"]))

    R.location_hooks.append(("glass_gate", active("c3.arrive"), [complete("c3.arrive")]))
    R.location_hooks.append(("deep_inside", "unless score #deep.first pm.world matches 1", [
        "scoreboard players set #deep.first pm.world 1",
        tellraw("@a", {"text": "The air in the Deepcut is colder than the fog, and it smells of wet stone and old lamp oil. Somewhere ahead, timber creaks.", "color": "gray", "italic": True}),
    ]))
    R.location_hooks.append(("deep_seam", "unless score #deep.seam pm.world matches 1", [
        "scoreboard players set #deep.seam pm.world 1",
        tellraw("@a", {"text": "The walls turn blue: a seam of raw stillglass, cold to the touch. Painted on a post, the old limit of safe work.", "color": "gray", "italic": True}),
    ]))

    # ============================================================== the foreman's log
    R.npc(NPC("foremans_log", "Foreman's log", "", "white", "none", body=False, label="✎ Foreman's log", size=(1.0, 1.4),
              places=[("", "glassworks.log")], talk=[("", "prop/foremans_log")]))
    R.dlg(Dlg("prop/foremans_log", "Vell Glassworks · No. 1 Adit · shift log", [
        {"text": "Kiln Three: bellows UP, flue DOWN, damper UP, then feed the firebox. Eight scoops, no less. — S.C.", "color": "white"},
        {"text": "Stillglass from the Deepcut rings when struck. The Keeper wants more of it than the mountain wants to give.", "color": "gray"},
        {"text": "Keeper's orders: past the blue seam. Tobin says it will hold. I have told him what I think of the roof. — S. Crane, foreman", "color": "white"},
        {"text": "(Last entry, in another hand:) The bells are ringing in Hollin. The Deepcut is down. Eleven.", "color": "gray", "italic": True},
    ], choices=[Choice("Note the kiln procedure", run=[
        tellraw("@s", {"text": "Noted: Kiln Three — bellows UP, flue DOWN, damper UP, then eight coal or charcoal in the firebox.", "color": "dark_aqua"}),
        complete("c3.log")])], exit_label="Close the log"))

    # ============================================================== Tamsin
    R.npc(NPC("tamsin", "Tamsin Reed", "Chartered Survey", "gold", "faded", model="slim", held="minecraft:lantern",
              places=[(not_done("c3.remind"), "deepcut.tamsin"),
                      (not_done("c3.surge"), "glassworks.tamsin"),
                      ("unless score c4.lens pm.q matches 2", "hollin.tamsin"),
                      ("", "meridian.tamsin")],
              talk=[
                  ("unless score c3.tamsin pm.q matches 1..", "npc/tamsin/early"),
                  (active("c3.tamsin"), f"/function {fid('c3/meet')}"),
                  (active("c3.remind"), "npc/tamsin/remind"),
                  (active("c3.memorial"), "npc/tamsin/memorial"),
                  (active("c3.kiln"), "npc/tamsin/kiln"),
                  (active("c3.lamp"), "npc/tamsin/lamp"),
                  (active("c3.surge"), "npc/tamsin/surge"),
              ]))
    poi.add("hollin.tamsin", 111.5, 67, 128.5, yaw=90.0)
    D = R.dlg
    D(Dlg("npc/tamsin/early", "", [
        "...forty-one, forty-two. Don't talk to me, I'm counting the props. If I lose count I have to start again.",
        "Who sent you? Nobody sends anybody here.",
    ], speaker="tamsin"))
    R.location_hooks.append(("deep_gallery", active("c3.tamsin"), [f"function {fid('c3/meet_near')}"]))
    R.func("c3/meet_near", [complete("c3.tamsin"),
                            tellraw("@a", {"text": "A lantern-shaped absence of light at the end of the gallery. Someone is sitting beside a dead lamp, very still.", "color": "gray", "italic": True})])
    R.func("c3/meet", [complete("c3.tamsin"), show("npc/tamsin/remind")])
    D(Dlg("npc/tamsin/remind", "", [
        "You have a Survey coat. Did they send someone? No... I sent something. A letter? I'm sure I sent something.",
        "I had a name. It was short. Everything here is short: short days, short names, short memory.",
    ], speaker="tamsin", choices=[
        Choice("Show her the letter", run=[f"function {fid('c3/show/letter')}"]),
        Choice("Show her the field notes", run=[f"function {fid('c3/show/notes')}"]),
        Choice("Show her the theodolite", run=[f"function {fid('c3/show/theodolite')}"]),
    ], exit_label="Not yet"))
    shows = {
        "letter": ("letter", "the letter", "The noticeboard at the Landing still has a copy of her letter.",
                   "\"Come yourself. Bring light.\" That's my handwriting. That's... I wrote this to you. You came."),
        "notes": ("field_notes", "her field notes", "Her field notes should still be at her camp in the Aldercross cider-press loft.",
                  "My notes. 'Blanks are never accidents.' I underlined that twice. I was very pleased with myself."),
        "theodolite": ("theodolite", "her theodolite", "Her theodolite is in the Glassworks office, by the mine mouth.",
                       "Oh, you beautiful thing. Brass, three screws, and a dent from the time I dropped it off Carrow Bridge. Mine."),
    }
    for key, (item, what, where, reaction) in shows.items():
        R.func(f"c3/show/{key}", [
            f"execute if score #c3.show.{key} pm.world matches 1 run return run tellraw @s {snbt({'text': 'She already remembers ' + what + '.', 'color': 'gray'})}",
            f"execute unless {_has(item)} unless items entity @s weapon.offhand *[minecraft:custom_data~{{pm:{{item:\"{item}\"}}}}] run return run tellraw @s "
            f"{snbt([{'text': 'You are not carrying ' + what + '. ', 'color': 'gray'}, {'text': where, 'color': 'dark_aqua'}])}",
            f"scoreboard players set #c3.show.{key} pm.world 1",
            "scoreboard players add c3.remind pm.qp 1",
            tellraw("@a", [{"text": "Tamsin: ", "color": "gold"}, {"text": reaction, "color": "white"}]),
            "playsound minecraft:block.amethyst_block.chime master @a ~ ~ ~ 0.8 1.2",
            f"function {fid('hud/refresh')}",
            f"execute if score c3.remind pm.qp matches 3.. run return run function {fid('c3/tamsin_restored')}",
            show("npc/tamsin/remind"),
        ])
    R.func("c3/tamsin_restored", [
        "data modify storage palemeridian:npc tamsin set value \"restored\"",
        "function palemeridian:npc/tamsin/apply_skin",
        "particle minecraft:end_rod ~ ~1 ~ 0.6 0.8 0.6 0.02 60 normal",
        complete("c3.remind"),
        show("npc/tamsin/restored"),
    ])
    D(Dlg("npc/tamsin/restored", "", [
        "Tamsin Reed. Chartered Survey. Terrible at letters, apparently, since it took you a year to get mine.",
        "Listen, while I still have all of me. The fog isn't weather. It's attention. This valley stays real because it is looked at: named, lit, charted. The Keeper's Lens on the Meridian did the looking for everyone at once.",
        "Forty years ago the Keeper struck the Deepcut off the Long Chart. Unlooked-at, it faded, and the forgetting spread out from here like damp through a wall.",
        "The far end of this mine, past the blue seam. That's where it starts. Go and look. Somebody has to, and I'd rather it was someone with all their edges.",
    ], speaker="tamsin", choices=[Choice("\"I'll go.\"")], exit_label=None))
    D(Dlg("npc/tamsin/memorial", "", [
        "Past the blue seam, then dig. The last gallery. Take a light, and take your time.",
    ], speaker="tamsin"))
    D(Dlg("npc/tamsin/kiln", "", [
        "Eleven. And one of them was hers. No wonder she couldn't look.",
        "Right. Work. Stillglass needs fire to wake up, and Kiln Three is the only kiln hot enough. The foreman's log has the settings. Then we'll have a lamp for this place, and something for the Lens.",
    ], speaker="tamsin"))
    D(Dlg("npc/tamsin/lamp", "", [
        "Top of the kiln tower. Glass all round it, bars on top. The workshop has glass, and the sand heaps have more if you've a furnace going.",
    ], speaker="tamsin"))
    D(Dlg("npc/tamsin/surge", "", [
        "Watch the mine mouth. Candles first, heroics second.",
    ], speaker="tamsin"))

    # items: Tamsin's field notes (Aldercross loft prop, re-obtainable)
    notes_pages = [
        [{"text": "FIELD NOTES\n", "bold": True}, {"text": "T. Reed, Chartered Survey\nVale of Vell\n\n", "color": "dark_gray", "italic": True},
         {"text": "The fog is not weather. It does not move with the wind. It pools where people stop looking.", "color": "black"}],
        [{"text": "Hollin: they've forgotten their names but not their chores. Habit outlasts memory. The lamplighter lights one lamp. Why that one?", "color": "black"}],
        [{"text": "The locals' creatures: Watchers. They freeze when seen. Of course they do. Everything here does.", "color": "black"}],
        [{"text": "Stillglass: lakebed mineral, holds light (and, I think, attention). The Keeper's Lens on the Meridian gathered the whole valley through it.\n\nSomeone turned the Lens off.", "color": "black"}],
        [{"text": "Going north to the Glassworks. The old Chart shows a blank under the cliffs.\n\nBlanks are never accidents.\n\n— T.", "color": "black"}],
    ]
    R.func("items/field_notes", [
        f"execute if {_has('field_notes')} run return run tellraw @s {snbt({'text': 'You already carry Tamsin' + chr(39) + 's field notes.', 'color': 'gray'})}",
        book_give("Field Notes", "T. Reed", notes_pages, "field_notes", lore=["Tamsin's handwriting, small and fast."]),
    ])
    R.npc(NPC("tamsin_notes", "Tamsin's notes", "", "gold", "none", body=False, label="✎ Tamsin's notes", size=(1.0, 0.8),
              places=[("", "aldercross.tamsin_note")], talk=[("", f"/function {fid('items/field_notes')}")]))

    # theodolite recovery (office chest), while it is still needed
    theo = poi.get("glassworks.theodolite_chest")
    R.location_hooks.append(("glass_office", "unless score #c3.show.theodolite pm.world matches 1", [
        "execute if score #c3.theo pm.world >= #seconds pm.world run return fail",
        "scoreboard players operation #c3.theo pm.world = #seconds pm.world",
        "scoreboard players add #c3.theo pm.world 60",
        f"execute if items block {xyz(theo)} container.* *[minecraft:custom_data~{{pm:{{item:\"theodolite\"}}}}] run return fail",
        "execute if entity @a[predicate=palemeridian:holds_theodolite] run return fail",
        f"item replace block {xyz(theo)} container.13 with minecraft:spyglass[minecraft:custom_name={snbt({'text': 'Tamsin' + chr(39) + 's Theodolite', 'italic': False, 'color': 'gold'})},"
        f"minecraft:custom_data={{pm:{{item:\"theodolite\"}}}}] 1",
    ]))
    predicate("holds_theodolite", {"condition": "minecraft:entity_properties", "entity": "this",
                                   "predicate": {"minecraft:slots": {"container.*": {"items": "minecraft:spyglass", "predicates": {"minecraft:custom_data": "{pm:{item:\"theodolite\"}}"}}}}})

    # ============================================================== the Last Gallery
    R.location_hooks.append(("deep_memorial", active("c3.memorial"), [f"function {fid('c3/memorial')}"]))
    R.func("c3/memorial", [
        "execute if score #c3.truth pm.world matches 1 run return fail",
        "scoreboard players set #c3.truth pm.world 1",
        "playsound minecraft:ambient.cave master @a ~ ~ ~ 1 0.5",
        "playsound minecraft:block.bell.resonate master @a ~ ~ ~ 0.6 0.5",
        "particle minecraft:white_ash ~ ~1 ~ 6 2 6 0.01 400 normal",
        tellraw("@a", {"text": "Eleven figures stand in the dark with their lamps out, facing a wall of blank plaques. None of them turns around.", "color": "gray", "italic": True}),
        f"schedule function {fid('c3/memorial_2')} 60t replace",
        complete("c3.memorial"),
    ])
    R.func("c3/memorial_2", [
        tellraw("@a", [{"text": "A woman's voice, very far away, as if across water: ", "color": "gray", "italic": True},
                       {"text": "\"Strike it. Strike all of it from the Chart. I will not look at that place again.\"", "color": "white", "italic": True}]),
        f"schedule function {fid('c3/memorial_3')} 80t replace",
    ])
    R.func("c3/memorial_3", [
        tellraw("@a", [{"text": "A younger voice, cheerful, from somewhere behind the figures: ", "color": "gray", "italic": True},
                       {"text": "\"It'll hold, Mother. Past the blue seam and we're done. One more shift.\"", "color": "white", "italic": True}]),
        f"schedule function {fid('c3/memorial_4')} 80t replace",
    ])
    R.func("c3/memorial_4", [
        tellraw("@a", {"text": "Tobin Vane led the crew. The Keeper sent her own son past the safe seam, and when the mountain came down she took his name off the map.", "color": "gray", "italic": True}),
        tellraw("@a", {"text": "(Tamsin is waiting at the Glassworks.)", "color": "dark_aqua"}),
    ])
    # the eleven echoes (present until the valley agrees to write their names)
    for n, name, thing, echo in ELEVEN:
        R.npc(NPC(f"echo_{n}", "A pale miner", "", "gray", "faded", model="slim" if n in (2, 3, 5, 9) else "wide",
                  places=[("unless score #ending pm.world matches 1", f"deepcut.fig.{n}")],
                  talk=[(f"if score #k.{n} pm.world matches 1", f"npc/echo_{n}/named"), ("", "npc/echo/unnamed")]))
        D(Dlg(f"npc/echo_{n}/named", name, [f"Their face is still grey, but you know it now: {name}.", echo], speaker=None, exit_label="Leave them be"))
    D(Dlg("npc/echo/unnamed", "A pale miner", [
        "The figure's face won't come into focus, however long you look.",
        "Something of theirs is still out in the Vale, waiting to be found.",
    ], exit_label="Leave them be"))

    # ============================================================== Kiln Three
    lv = poi.get("glassworks.levers")
    fb, hatch = poi.get("glassworks.firebox"), poi.get("glassworks.hatch")
    kiln = poi.get("glassworks.kiln")
    bel, flu, dam = lv["bellows"], lv["flue"], lv["damper"]
    R.func("c3/kiln_check", [
        f"execute store result score #coal pm.tmp if items block {xyz(fb)} container.* #minecraft:coals",
        "execute if score #coal pm.tmp matches ..7 run return fail",
        f"execute if block {xyz(bel)} minecraft:lever[powered=false] if block {xyz(flu)} minecraft:lever[powered=true] "
        f"if block {xyz(dam)} minecraft:lever[powered=false] run return run function {fid('c3/kiln_fire')}",
        "execute if score #c3.smoke pm.world >= #seconds pm.world run return fail",
        "scoreboard players operation #c3.smoke pm.world = #seconds pm.world",
        "scoreboard players add #c3.smoke pm.world 12",
        f"particle minecraft:large_smoke {xyz(kiln, 2)} 2 1 2 0.02 60 normal",
        f"playsound minecraft:block.fire.extinguish master @a {xyz(kiln)} 1 0.6",
        tellraw(f"@a[x={int(kiln['x'])},y={int(kiln['y'])},z={int(kiln['z'])},distance=..24]",
                {"text": "The firebox chokes and smokes. Something about the kiln's settings is wrong.", "color": "gray", "italic": True}),
    ])
    R.func("c3/kiln_fire", [
        f"data remove block {xyz(fb)} Items",
        *[f"setblock {x} {y} {z} minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]" for (x, y, z) in kiln["fires"]],
        f"particle minecraft:flame {xyz(kiln, 1)} 2 1 2 0.05 200 normal",
        f"particle minecraft:lava {xyz(kiln, 1)} 2 1 2 0.1 40 normal",
        f"playsound minecraft:block.blastfurnace.fire_crackle master @a {xyz(kiln)} 2 0.8",
        f"playsound minecraft:block.fire.ambient master @a {xyz(kiln)} 2 0.6",
        tellraw("@a", {"text": "Kiln Three roars. Heat pours from the grate, and deep in the fire something rings like a struck glass.", "color": "gray", "italic": True}),
        f"function {fid('c3/hatch_refill')}",
        tellraw("@a", {"text": "(The kiln hatch holds a new Stillglass Lamp, and a Lens Heart.)", "color": "dark_aqua"}),
        complete("c3.kiln"),
    ])
    R.slow_hooks.append(f"execute if score c3.kiln pm.q matches 1 positioned {xyz(kiln)} if entity @a[distance=..20] run function {fid('c3/kiln_check')}")
    lamp = poi.get("glassworks.wakelamp")
    R.func("c3/hatch_refill", [
        "# Keeps the forged pieces available: the lamp until it is built, the Lens Heart until it is set in the Lens.",
        f"execute unless block {xyz(lamp)} minecraft:pearlescent_froglight unless items block {xyz(hatch)} container.* minecraft:pearlescent_froglight "
        f"unless entity @a[predicate={fid('holds_stillglass')}] run item replace block {xyz(hatch)} container.12 with minecraft:pearlescent_froglight"
        f"[minecraft:custom_name={STILLGLASS}] 1",
        f"execute unless score c4.lens pm.q matches 2 unless items block {xyz(hatch)} container.* *[minecraft:custom_data~{{pm:{{item:\"lens_heart\"}}}}] "
        f"unless entity @a[predicate={fid('holds_lens_heart')}] run item replace block {xyz(hatch)} container.14 with {LENS_HEART} 1",
    ])
    predicate("holds_lens_heart", {"condition": "minecraft:entity_properties", "entity": "this",
                                   "predicate": {"minecraft:slots": {"container.*": {"items": "minecraft:heart_of_the_sea", "predicates": {"minecraft:custom_data": "{pm:{item:\"lens_heart\"}}"}}}}})
    R.location_hooks.append(("glass_kiln", "if score c3.kiln pm.q matches 2", [
        "execute if score #c3.refill pm.world >= #seconds pm.world run return fail",
        "scoreboard players operation #c3.refill pm.world = #seconds pm.world",
        "scoreboard players add #c3.refill pm.world 60",
        f"function {fid('c3/hatch_refill')}",
    ]))
    # the levers repair themselves while the kiln is still cold (a broken lever would otherwise block the chapter)
    for key, pos, initial in (("bellows", bel, "true"), ("flue", flu, "false"), ("damper", dam, "true")):
        R.location_hooks.append(("glass_kiln", active("c3.kiln"), [
            f"execute unless block {xyz(pos)} minecraft:lever run setblock {xyz(pos)} minecraft:lever[face=wall,facing=east,powered={initial}]"]))
    R.load_hooks.append(f"execute if score c3.kiln pm.q matches 2 run function {fid('c3/kiln_relight')}")
    R.func("c3/kiln_relight", [f"setblock {x} {y} {z} minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]" for (x, y, z) in kiln["fires"]])

    # ============================================================== the Glassworks Wakelamp
    accepts = {"pearlescent_froglight": ["minecraft:pearlescent_froglight"], "glass": ["#palemeridian:glass_blocks"],
               "iron_bars": ["minecraft:iron_bars"]}
    R.blueprint(Blueprint("glass_lamp", "c3.lamp", [Part(tuple(p["pos"]), accepts[p["block"]], p["block"]) for p in lamp["parts"]],
                          center=(lamp["x"], lamp["y"], lamp["z"]), radius=12,
                          on_complete=[f"particle minecraft:end_rod {xyz(lamp, 0.5)} 0.5 0.5 0.5 0.03 80 normal",
                                       f"playsound minecraft:block.beacon.activate master @a {xyz(lamp)} 2 0.9",
                                       tellraw("@a", {"text": "The lamp kindles high above the kiln yard, and down at the mine mouth the fog stirs like something waking.", "color": "gray", "italic": True}),
                                       complete("c3.lamp")]))
    sup = poi.get("glassworks.supply")
    R.location_hooks.append(("glass_workshop", active("c3.lamp"), [
        "execute if score #c3.glass pm.world >= #seconds pm.world run return fail",
        "scoreboard players operation #c3.glass pm.world = #seconds pm.world",
        "scoreboard players add #c3.glass pm.world 60",
        f"execute if items block {xyz(sup)} container.* #palemeridian:glass_items run return fail",
        f"item replace block {xyz(sup)} container.11 with minecraft:glass 4",
        f"item replace block {xyz(sup)} container.15 with minecraft:iron_bars 2",
    ]))
    tag("item", "palemeridian", "glass_items", GLASS)

    # ============================================================== the collapse surge
    yard = poi.get("glassworks.yard")
    yx, yz = int(yard["x"]), int(yard["z"])
    candles = [tuple(c) for c in yard["candles"]]
    spawns = [(22, 70, -318), (24, 70, -317), (26, 70, -318), (10, 70, -284), (38, 70, -284), (8, 70, -300), (40, 70, -300)]
    register_surge(Surge("glassworks", "c3.surge", "The Collapse", (yx, 70, yz), 22, spawns, candles,
                         min_seconds=20, base_watchers=3, per_player=2, cap=8, interval=6, kind="candle", time_limit=120,
                         on_start=[tellraw("@a", {"text": "At the mine mouth, timber groans. Figures that are not the eleven step out of the dark.", "color": "gray", "italic": True})]))

    # ============================================================== restoration of the Glassworks
    glamps = poi.get("glassworks.lamps")["lamps"]
    R.func("c3/restored", [
        "scoreboard players set #r.glassworks pm.world 1",
        "scoreboard players set #req.glassworks pm.world 1",
        "time of palemeridian:pall set 4000",
        "scoreboard players set #chapter pm.world 4",
        *[f"function {fid('enc/_set_bulb')} {{x:{int(x)},y:{int(y)},z:{int(z)},lit:\"true\"}}" for (x, y, z) in glamps],
        f"title @a title {snbt({'text': 'The Glassworks', 'color': 'white'})}",
        f"title @a subtitle {snbt({'text': 'remembers', 'color': 'gray', 'italic': True})}",
        "playsound minecraft:block.bell.resonate master @a ~ ~ ~ 1.5 0.9",
        tellraw("@a", {"text": "The fog drains out of the kiln yard and down the road. The mine mouth stays dark: no lamp holds there, because the Deepcut is not on the Chart.", "color": "gray", "italic": True}),
        tellraw("@a", {"text": "(The Glassworks is restored. Three lamps burn. Tamsin has gone ahead to Hollin.)", "color": "dark_aqua"}),
    ])
