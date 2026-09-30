"""CHAPTER 4 — The Meridian: the crossing, the Keeper, the Lens, the Unlooked, the choice, both endings."""
from __future__ import annotations

from ... import poi
from .. import adv, dialog, fid, predicate, snbt, tag, tellraw, xyz
from ..engine import (R, Area, Blueprint, Choice, Dlg, NPC, Part, Quest, activate, active, complete, done, not_done, show)
from .keepsakes import ELEVEN

ARENA_R = 11


def _npc(npc_id: str) -> NPC:
    for n in R.npcs:
        if n.id == npc_id:
            return n
    raise KeyError(npc_id)


def register() -> None:
    arena = poi.get("meridian.arena")
    TX, GY, TZ = int(arena["x"]), int(arena["y"]), int(arena["z"])
    SEL = f"x={TX - ARENA_R},y={GY - 1},z={TZ - ARENA_R},dx={2 * ARENA_R},dy=12,dz={2 * ARENA_R}"
    relays = [tuple(r) for r in arena["relays"]]
    spawns = [tuple(s) for s in poi.get("meridian.arena_spawns")["spawns"]]
    lens = poi.get("meridian.lens")
    chart = poi.get("meridian.chart")

    R.area(Area("mer_island", poi.get("meridian.island")["box"]))
    R.area(Area("mer_chartroom", poi.get("meridian.chartroom")["box"]))

    # ============================================================== quests
    R.quest(Quest("c4.crossing", 4, "The Crossing", "Cross Vellmere to the Meridian.",
                  "With three lamps lit, the lake remembers its shores. The island at its heart is no longer folded away.",
                  prereq=["c3.surge"], target="meridian.dock", icon="minecraft:oak_boat",
                  hint="Take a boat from the Hollin jetty, or follow the causeway north from the south shore. The causeway has a broken span: bridge it or row round it.",
                  on_activate=[tellraw("@a", {"text": "Out on Vellmere the fog thins into a corridor, and for the first time you can see the white tower on the island.", "color": "gray", "italic": True})]))
    R.quest(Quest("c4.keeper", 4, "The Keeper", "Find the Keeper in the Chart Room.",
                  "Someone still lives on the Meridian. The Chart Room is dark.",
                  prereq=["c4.crossing"], target="meridian.hesper", icon="minecraft:filled_map"))
    R.quest(Quest("c4.lens", 4, "The Great Lens", "Set the Lens Heart in the Great Lens atop the tower.",
                  "The Keeper will not stop you. The stair climbs to the Lens Gallery.",
                  prereq=["c4.keeper"], target="meridian.cradle", icon="minecraft:heart_of_the_sea", frame="goal",
                  hint="The Lens Heart was forged in Kiln Three; if it was lost, the kiln hatch keeps another."))
    R.quest(Quest("c4.unlooked", 4, "The Unlooked", "Face what the Pall has become.",
                  "Everything the valley refused to look at has gathered on the Lens Gallery.",
                  prereq=["c4.lens"], target="meridian.arena", icon="minecraft:creaking_heart", frame="challenge",
                  hint="It moves only while nobody watches it. It snuffs the four relay lamps; relight them (use them) or it heals. Late in the fight it steps through the fog behind you. If everyone leaves or falls, it resets. The beds in the Keeper's quarters set your spawn on the island.",
                  on_complete=[f"function {fid('c4/lens_burns')}"]))
    R.quest(Quest("c4.chart", 4, "The Blank Space", "Decide what the Long Chart says about the Deepcut.",
                  "The Lens burns and the Long Chart glows, except for one white space under the north cliffs.",
                  prereq=["c4.unlooked"], target="meridian.chart_table", icon="minecraft:writable_book", frame="goal",
                  hint="Everyone on the island has an opinion. Ask them before you pick up the pen."))
    R.quest(Quest("ep.complete", 5, "Survey Complete", "Free play: the Vale is yours to explore.",
                  "The survey of Vell is complete.", prereq=["c4.chart"], icon="minecraft:spyglass", frame="challenge",
                  on_activate=[complete("ep.complete")], on_complete=[f"function {fid('c4/epilogue')}"]))
    R.quest(Quest("c4.causeway", 9, "The Broken Span", "Mend the gap in the Meridian causeway.",
                  "The causeway to the Meridian lost a span when the fog came. The old stones' outlines still glow.",
                  prereq=["c4.crossing"], main=False, target="meridian.causeway_gap", icon="minecraft:stone_bricks",
                  on_complete=[f"function {fid('c4/causeway_lit')}"]))
    R.quest(Quest("ep.spoken", 9, "Every Name, Spoken", "You read all eleven names aloud.", "All eleven were found, and all eleven were read.",
                  auto=False, main=False, icon="minecraft:candle"))
    R.quest(Quest("ep.kept", 9, "Kept Safe", "You gave the keepsakes to the Keeper.", "All eleven were found, and entrusted to the one who keeps them now.",
                  auto=False, main=False, icon="minecraft:chest"))

    # ============================================================== the island is folded away until Chapter 4
    R.slow_hooks.append(f"execute unless score c4.crossing pm.q matches 1.. as @a[x=-54,y=0,z=-54,dx=88,dy=320,dz=88,gamemode=!creative,gamemode=!spectator] at @s run function {fid('c4/fold_back')}")
    R.func("c4/fold_back", [
        "ride @s dismount",
        "effect give @s minecraft:blindness 2 0 true",
        "tp @s 72.5 64 73.5 -135 0",
        "playsound minecraft:block.beacon.deactivate master @s ~ ~ ~ 0.6 0.5",
        tellraw("@s", {"text": "The fog folds around you, and folds you back to the shore. The island isn't ready to be seen.", "color": "gray", "italic": True}),
    ])
    R.location_hooks.append(("mer_island", active("c4.crossing"), [complete("c4.crossing")]))

    # the broken span (optional)
    gap = poi.get("meridian.causeway_gap")
    tag("block", "palemeridian", "causeway_blocks", ["#minecraft:stone_bricks", "minecraft:stone", "minecraft:cobblestone", "minecraft:mossy_cobblestone",
                                                     "minecraft:smooth_stone", "#minecraft:planks", "minecraft:polished_andesite", "minecraft:andesite",
                                                     "minecraft:deepslate_bricks", "minecraft:cobbled_deepslate", "minecraft:tuff_bricks", "minecraft:bricks"])
    R.blueprint(Blueprint("causeway", "c4.causeway", [Part(tuple(p["pos"]), ["#palemeridian:causeway_blocks"], "stone_bricks") for p in gap["parts"]],
                          center=(gap["x"], gap["y"], gap["z"]), radius=24, on_complete=[complete("c4.causeway")]))
    clamps = poi.get("meridian.causeway_lamps")["lamps"]
    R.func("c4/causeway_lit", [*[f"function {fid('enc/_set_bulb')} {{x:{x},y:{y},z:{z},lit:\"true\"}}" for (x, y, z) in clamps],
                               tellraw("@a", {"text": "The causeway is whole again. Its lamps remember how to burn.", "color": "gray", "italic": True})])

    # ============================================================== Hesper, the Keeper
    poi.add("glassworks.hesper_garden", 20.5, 70, -316.5, yaw=0.0)
    R.npc(NPC("hesper", "Hesper Vane", "Keeper of the Meridian", "dark_aqua", "faded", model="slim",
              places=[("if score #ending pm.world matches 1..", "glassworks.hesper_garden"), ("", "meridian.hesper")],
              talk=[
                  ("unless score c4.keeper pm.q matches 1..", "npc/hesper/early"),
                  (active("c4.keeper"), "npc/hesper/meet"),
                  (active("c4.lens"), "npc/hesper/lens"),
                  (active("c4.unlooked"), "npc/hesper/fight"),
                  (active("c4.chart"), "npc/hesper/view"),
                  ("if score #ending pm.world matches 1", "npc/hesper/ep_true"),
                  ("if score #ending pm.world matches 2", "npc/hesper/ep_blank"),
              ]))
    D = R.dlg
    D(Dlg("npc/hesper/early", "", ["The Keeper does not receive visitors."], speaker="hesper"))
    D(Dlg("npc/hesper/meet", "", [
        "You are the one who has been lighting my lamps. I felt each of them, like a hand on a shoulder I had forgotten I had.",
        "I am Hesper Vane. I kept the Meridian, once. I keep the dark now.",
    ], speaker="hesper", choices=[
        Choice("\"Why did you let the valley fade?\"", goto="npc/hesper/meet_why"),
        Choice("\"Tamsin says you struck the Deepcut from the Chart.\"", goto="npc/hesper/meet_struck"),
        Choice("\"I found the Last Gallery.\"", goto="npc/hesper/meet_tobin"),
    ], exit_label="(Say nothing)"))
    D(Dlg("npc/hesper/meet_why", "", [
        "Let it? I made it. A chart is a promise to look. I broke mine.",
        "After that the looking stopped everywhere, a little at a time, like a tide going out.",
    ], speaker="hesper", choices=[Choice("\"Why break it?\"", goto="npc/hesper/meet_tobin")], exit_label="Back"))
    D(Dlg("npc/hesper/meet_struck", "", [
        "Yes. With this hand. I sat where you are standing and painted over the cliffs until the paper was white.",
        "Then I put out the Lens, so that it would never see what I had done.",
    ], speaker="hesper", choices=[Choice("\"Why?\"", goto="npc/hesper/meet_tobin")], exit_label="Back"))
    D(Dlg("npc/hesper/meet_tobin", "", [
        "My son is down there. Tobin. He led the crew.",
        "I sent them past the blue seam because I wanted a lens that could hold the whole valley at once. It held everything except the roof.",
    ], speaker="hesper", choices=[Choice("\"Tell me about the Lens.\"", goto="npc/hesper/meet_lens")], exit_label=None))
    D(Dlg("npc/hesper/meet_lens", "", [
        "The Great Lens gathers the valley into one image, and the Long Chart keeps it. Set a new heart in it and it will see everything again. Every field. Every name. Even him.",
        "I will not stop you. I only ask you to think about what a blank is for.",
    ], speaker="hesper", choices=[Choice("\"I have to light it.\"", run=[complete("c4.keeper")])], exit_label="Not yet"))
    D(Dlg("npc/hesper/lens", "", ["The stair is the long way up. I climbed it every night for thirty years. The cradle is at the top."], speaker="hesper"))
    D(Dlg("npc/hesper/fight", "", ["It is everything this valley refused to look at. Whatever you do, don't look away from it."], speaker="hesper"))
    D(Dlg("npc/hesper/view", "", [
        "Leave him in the dark, Surveyor. Leave all of them.",
        "Let the Deepcut be the one place in Vell that is allowed to be forgotten. Some things are kinder unseen. I have had forty years to decide that, and I am still not sure. But I am asking.",
    ], speaker="hesper"))
    D(Dlg("npc/hesper/ep_true", "", ["I will look at it every day. That is the least of what I owe them."], speaker="hesper"))
    D(Dlg("npc/hesper/ep_blank", "", ["Thank you. I will keep them company. Someone should."], speaker="hesper"))

    # ============================================================== companions gather after the Lens is set
    gathered = "if score c4.lens pm.q matches 2 unless score #ending pm.world matches 1.."
    _npc("odile").places.insert(0, (gathered, "meridian.odile"))
    _npc("brannoc").places.insert(0, (gathered, "meridian.brannoc"))
    views = {
        "tamsin": ["Truth first. The Survey doesn't get to choose which parts of the world count.",
                   "But it's your pen, not mine. Whatever you write, I'll sign the survey under it."],
        "brannoc": ["Write them. Every one. Col's name has been rotting in that dark for forty years.",
                    "He was not a blank. None of them were."],
        "odile": ["I'm only a lamplighter. But I'll say this: whatever you choose, choose it for the living.",
                  "We're the ones who have to walk past it every day."],
    }
    epilogue = {
        "tamsin": (["I'll file it. All of it. The Survey owes them that, and so do I."],
                   ["Some blanks are a kindness. I hope this one is. I'll file the rest."]),
        "brannoc": (["Col. Col Hale. I can say it without the fog in my mouth now.", "Thank you, Surveyor. Come for honey whenever you like."],
                    ["You let her keep him.", "...I'll not forget that you chose. That's something, I suppose. Come for honey anyway."]),
        "odile": (["The sun came up this morning. Properly up. I'd forgotten it did that."],
                  ["The haze never quite lifts, does it? But the lamps are lit. That's enough for most evenings."]),
        "mirelle": (["Bread's better when the sun's out. Don't ask me how, it just is."],
                    ["Morning, Surveyor. Loaf's on the counter."]),
        "jory": (["I polished the bells for the names. Eleven rings, one for each. Agnes got two."],
                 ["I ring the Round every evening now. Loud enough for the ones who can't hear it."]),
    }
    for who, lines in views.items():
        D(Dlg(f"npc/{who}/view", "", lines, speaker=who))
        _npc(who).talk.insert(0, (active("c4.chart"), f"npc/{who}/view"))
    for who, (t_lines, b_lines) in epilogue.items():
        D(Dlg(f"npc/{who}/ep_true", "", t_lines, speaker=who))
        D(Dlg(f"npc/{who}/ep_blank", "", b_lines, speaker=who))
        _npc(who).talk.insert(0, ("if score #ending pm.world matches 2", f"npc/{who}/ep_blank"))
        _npc(who).talk.insert(0, ("if score #ending pm.world matches 1", f"npc/{who}/ep_true"))
    D(Dlg("npc/tamsin/c4", "", [
        "The Meridian. Hesper Vane's island. Everything goes back to that Lens.",
        "Take a boat from the jetty, or walk the causeway if you fancy mending it. I'll be along when there's something to see.",
    ], speaker="tamsin"))
    _npc("tamsin").talk.append(("if score #chapter pm.world matches 4", "npc/tamsin/c4"))

    # ============================================================== the Great Lens (cradle prop)
    R.npc(NPC("cradle", "The Great Lens", "", "aqua", "none", body=False, label="✦ The Great Lens", size=(1.2, 1.6),
              places=[("", "meridian.cradle")],
              talk=[(active("c4.lens"), "prop/cradle/set"),
                    ("if score c4.unlooked pm.q matches 2", "prop/cradle/burning"),
                    ("if score c4.lens pm.q matches 2", "prop/cradle/waiting"),
                    ("", "prop/cradle/cold")]))
    D(Dlg("prop/cradle/cold", "The Great Lens", ["A disc of stillglass in a brass cradle, cold as the lake. The heart of it is empty."]))
    D(Dlg("prop/cradle/waiting", "The Great Lens", ["The Lens Heart sits in its cradle, dim, as if it is waiting to be sure of you."]))
    D(Dlg("prop/cradle/burning", "The Great Lens", ["The Lens burns steadily. From up here you can see every lamp in Vell."]))
    D(Dlg("prop/cradle/set", "The Great Lens", [
        "The cradle waits for a heart. When it is set, the Lens will begin to look again, at everything.",
    ], choices=[Choice("Set the Lens Heart", run=[f"function {fid('c4/set_heart')}"])], exit_label="Not yet"))
    R.func("c4/set_heart", [
        "execute unless items entity @s container.* minecraft:heart_of_the_sea[minecraft:custom_data~{pm:{item:\"lens_heart\"}}] "
        "unless items entity @s weapon.offhand minecraft:heart_of_the_sea[minecraft:custom_data~{pm:{item:\"lens_heart\"}}] run return run tellraw @s "
        + snbt({"text": "You need the Lens Heart forged in Kiln Three. (If it was lost, the kiln hatch keeps another.)", "color": "gray"}),
        "clear @s minecraft:heart_of_the_sea[minecraft:custom_data~{pm:{item:\"lens_heart\"}}] 1",
        f"particle minecraft:end_rod {xyz(lens)} 0.2 3 3 0.02 120 normal",
        f"playsound minecraft:block.respawn_anchor.charge master @a {xyz(lens)} 2 0.6",
        tellraw("@a", {"text": "The Lens Heart settles into the cradle with a sound like a held breath. Then the fog on the gallery begins to move against the wind.", "color": "gray", "italic": True}),
        tellraw("@a", {"text": "Far below, boats are putting out from Hollin.", "color": "gray", "italic": True}),
        complete("c4.lens"),
    ])

    # ============================================================== the Unlooked
    R.load_hooks += [
        "bossbar add palemeridian:boss \"\"",
        "bossbar set palemeridian:boss color white",
        "bossbar set palemeridian:boss style notched_10",
        f"bossbar set palemeridian:boss name {snbt({'text': 'The Unlooked', 'color': 'white'})}",
        f"execute if score #boss pm.world matches 1 run function {fid('c4/boss/reset')}",
    ]
    predicate("arena_floor", {"condition": "minecraft:location_check", "predicate": {"position": {
        "x": {"min": TX - ARENA_R + 1, "max": TX + ARENA_R - 1}, "y": {"min": GY - 0.5, "max": GY + 1.5}, "z": {"min": TZ - ARENA_R + 1, "max": TZ + ARENA_R - 1}}}})
    tag("block", "palemeridian", "passable", ["minecraft:air", "minecraft:light", "#minecraft:slabs", "#minecraft:wool_carpets"])
    R.slow_hooks.append(f"execute if score c4.unlooked pm.q matches 1 unless score #boss pm.world matches 1..2 unless score #cool.boss pm.world > #seconds pm.world "
                        f"if entity @a[{SEL},gamemode=!spectator] run function {fid('c4/boss/start')}")
    R.slow_hooks.append(f"function {fid('c4/boss/tick')}")
    R.slow_hooks.append(f"execute unless score #boss pm.world matches 1 run kill @e[type=creaking,tag=pm.bossfx]")
    boss_sel = "@e[type=creaking,tag=pm.boss,limit=1]"
    R.func("c4/boss/start", [
        "execute if score #boss pm.world matches 1..2 run return fail",
        "scoreboard players set #boss pm.world 1",
        "scoreboard players set #b.t pm.world 0",
        "scoreboard players set #b.idle pm.world 0",
        "scoreboard players set #b.tele pm.world -1",
        "scoreboard players set #b.snuff pm.world 15",
        "scoreboard players set #b.add pm.world 8",
        "scoreboard players set #b.blink pm.world 12",
        "scoreboard players set #b.phase pm.world 1",
        f"execute store result score #n pm.tmp if entity @a[{SEL},gamemode=!spectator]",
        "scoreboard players remove #n pm.tmp 1",
        "execute if score #n pm.tmp matches 4.. run scoreboard players set #n pm.tmp 3",
        # health = base + per extra player (difficulty setting: 0 gentle, 1 normal, 2 hard)
        "scoreboard players set #b.max pm.world 180",
        "scoreboard players set #k pm.tmp 90",
        "execute if score #set.difficulty pm.world matches 0 run scoreboard players set #b.max pm.world 110",
        "execute if score #set.difficulty pm.world matches 0 run scoreboard players set #k pm.tmp 60",
        "execute if score #set.difficulty pm.world matches 2 run scoreboard players set #b.max pm.world 240",
        "execute if score #set.difficulty pm.world matches 2 run scoreboard players set #k pm.tmp 110",
        "scoreboard players operation #k pm.tmp *= #n pm.tmp",
        "scoreboard players operation #b.max pm.world += #k pm.tmp",
        *[f"function {fid('enc/_set_bulb')} {{x:{x},y:{y},z:{z},lit:\"true\"}}" for (x, y, z) in relays],
        f"summon minecraft:creaking {TX} {GY} {TZ - 7} {{Tags:[\"pm.boss\",\"pm.bossfx\"],PersistenceRequired:1b,"
        f"CustomName:{snbt({'text': 'The Unlooked', 'color': 'white'})},"
        f"attributes:[{{id:\"minecraft:scale\",base:2.5d}},{{id:\"minecraft:attack_damage\",base:5.0d}},{{id:\"minecraft:knockback_resistance\",base:0.8d}}]}}",
        "execute store result storage palemeridian:tmp boss.hp int 1 run scoreboard players get #b.max pm.world",
        f"function {fid('c4/boss/_hp')} with storage palemeridian:tmp boss",
        "execute store result bossbar palemeridian:boss max run scoreboard players get #b.max pm.world",
        "execute store result bossbar palemeridian:boss value run scoreboard players get #b.max pm.world",
        f"bossbar set palemeridian:boss players @a[{SEL}]",
        "bossbar set palemeridian:boss visible true",
        "time of palemeridian:surge resume",
        f"particle minecraft:white_ash {TX} {GY + 2} {TZ - 7} 2 3 2 0.02 300 normal",
        f"playsound minecraft:entity.warden.emerge master @a {TX} {GY} {TZ} 2 0.6",
        tellraw(f"@a[{SEL}]", [{"text": "The fog on the gallery gathers itself, and stands up. ", "color": "red", "italic": True},
                               {"text": "It moves only while nobody is watching it.", "color": "gray", "italic": True}]),
    ])
    R.func("c4/boss/_hp", [
        f"$attribute {boss_sel} minecraft:max_health base set $(hp)",
        f"$data modify entity {boss_sel} Health set value $(hp)f",
    ])
    from .. import _SAMPLE_BY_PREFIX
    _SAMPLE_BY_PREFIX["c4/boss/_hp"] = "{hp:180}"
    lit_count = [f"execute if block {x} {y} {z} #palemeridian:bulbs[lit=true] run scoreboard players add #lit pm.tmp 1" for (x, y, z) in relays]
    R.func("c4/boss/tick", [
        "execute unless score #boss pm.world matches 1 run return fail",
        "scoreboard players add #b.t pm.world 1",
        f"execute store result score #n pm.tmp if entity @a[{SEL},gamemode=!spectator]",
        "execute if score #n pm.tmp matches 0 run scoreboard players add #b.idle pm.world 1",
        "execute if score #n pm.tmp matches 1.. run scoreboard players set #b.idle pm.world 0",
        f"execute if score #b.idle pm.world matches 10.. run return run function {fid('c4/boss/reset')}",
        f"execute unless entity {boss_sel} run return run function {fid('c4/boss/win')}",
        f"execute as {boss_sel} unless entity @s[x={TX - ARENA_R - 1},y={GY - 3},z={TZ - ARENA_R - 1},dx={2 * ARENA_R + 2},dy=16,dz={2 * ARENA_R + 2}] run tp @s {TX} {GY} {TZ - 6}",
        f"execute store result score #b.hp pm.world run data get entity {boss_sel} Health",
        "execute store result bossbar palemeridian:boss value run scoreboard players get #b.hp pm.world",
        f"bossbar set palemeridian:boss players @a[{SEL}]",
        # phase from health percentage
        "scoreboard players operation #b.pct pm.world = #b.hp pm.world",
        "scoreboard players set #c pm.tmp 100",
        "scoreboard players operation #b.pct pm.world *= #c pm.tmp",
        "scoreboard players operation #b.pct pm.world /= #b.max pm.world",
        f"execute if score #b.phase pm.world matches 1 if score #b.pct pm.world matches ..66 run function {fid('c4/boss/phase2')}",
        f"execute if score #b.phase pm.world matches 2 if score #b.pct pm.world matches ..33 run function {fid('c4/boss/phase3')}",
        # relays: two or more dark and it heals
        "scoreboard players set #lit pm.tmp 0",
        *lit_count,
        f"execute if score #lit pm.tmp matches ..2 run function {fid('c4/boss/regen')}",
        f"function {fid('c4/boss/snuff_tick')}",
        f"execute if score #b.phase pm.world matches 2.. run function {fid('c4/boss/add_tick')}",
        f"execute if score #b.phase pm.world matches 3 run function {fid('c4/boss/blink_tick')}",
    ])
    R.func("c4/boss/phase2", [
        "scoreboard players set #b.phase pm.world 2",
        tellraw(f"@a[{SEL}]", {"text": "The Unlooked shudders. Smaller shapes climb over the parapet out of the fog.", "color": "red", "italic": True}),
        f"playsound minecraft:entity.creaking.twitch master @a {TX} {GY} {TZ} 2 0.5",
    ])
    R.func("c4/boss/phase3", [
        "scoreboard players set #b.phase pm.world 3",
        tellraw(f"@a[{SEL}]", {"text": "The Unlooked comes apart at the edges. It no longer walks; it is simply somewhere else, behind whoever looks away.", "color": "red", "italic": True}),
        f"playsound minecraft:entity.warden.roar master @a {TX} {GY} {TZ} 1.5 0.5",
    ])
    R.func("c4/boss/regen", [
        "scoreboard players add #b.hp pm.world 3",
        "execute if score #b.hp pm.world > #b.max pm.world run scoreboard players operation #b.hp pm.world = #b.max pm.world",
        f"execute store result entity {boss_sel} Health float 1 run scoreboard players get #b.hp pm.world",
        f"execute at {boss_sel} run particle minecraft:white_ash ~ ~3 ~ 1 2 1 0.02 40 normal",
        f"title @a[{SEL}] actionbar {snbt({'text': 'The relay lamps are dark: the Unlooked is healing. Relight them!', 'color': 'gold'})}",
    ])
    # telegraphed snuffing of a relay lamp
    pick = []
    for i in range(4):
        x, y, z = relays[i]
        pick.append(f"execute if score #b.tele pm.world matches -1 if score #pick pm.tmp matches {i} if block {x} {y} {z} #palemeridian:bulbs[lit=true] run scoreboard players set #b.tele pm.world {i}")
    for i in range(4):
        x, y, z = relays[i]
        pick.append(f"execute if score #b.tele pm.world matches -1 if block {x} {y} {z} #palemeridian:bulbs[lit=true] run scoreboard players set #b.tele pm.world {i}")
    R.func("c4/boss/snuff_tick", [
        f"execute if score #b.tele pm.world matches 0.. run return run function {fid('c4/boss/tele_tick')}",
        "scoreboard players remove #b.snuff pm.world 1",
        "execute if score #b.snuff pm.world matches 1.. run return 0",
        "scoreboard players set #b.snuff pm.world 20",
        "execute if score #b.phase pm.world matches 2.. run scoreboard players set #b.snuff pm.world 12",
        "execute if score #set.difficulty pm.world matches 0 run scoreboard players add #b.snuff pm.world 6",
        "execute store result score #pick pm.tmp run random value 0..3",
        *pick,
        "scoreboard players set #b.telet pm.world 3",
    ])
    tele = ["scoreboard players remove #b.telet pm.world 1"]
    for i, (x, y, z) in enumerate(relays):
        tele.append(f"execute if score #b.tele pm.world matches {i} run particle minecraft:smoke {x} {y} {z} 0.3 0.3 0.3 0.02 30 normal")
        tele.append(f"execute if score #b.tele pm.world matches {i} run playsound minecraft:block.copper_bulb.turn_off master @a {x} {y} {z} 1 0.5")
    tele.append("execute if score #b.telet pm.world matches 1.. run return 0")
    for i, (x, y, z) in enumerate(relays):
        tele.append(f"execute if score #b.tele pm.world matches {i} run function {fid('enc/_set_bulb')} {{x:{x},y:{y},z:{z},lit:\"false\"}}")
    tele += [tellraw(f"@a[{SEL}]", {"text": "The Pall snuffs a relay lamp!", "color": "gold"}), "scoreboard players set #b.tele pm.world -1"]
    R.func("c4/boss/tele_tick", tele)
    for i, (x, y, z) in enumerate(relays):
        poi.add(f"meridian.relay{i}", x + 0.5, y - 2.0, z + 0.5)
        R.npc(NPC(f"relay_{i}", "Relay lamp", "", "gold", "none", body=False, label="✦ Relight", size=(1.2, 2.4),
                  places=[(f"if score #boss pm.world matches 1 unless block {x} {y} {z} #palemeridian:bulbs[lit=true]", f"meridian.relay{i}")],
                  talk=[("", f"/function {fid(f'c4/boss/relight/{i}')}")]))
        R.func(f"c4/boss/relight/{i}", [
            "execute unless score #boss pm.world matches 1 run return fail",
            f"execute if block {x} {y} {z} #palemeridian:bulbs[lit=true] run return fail",
            f"function {fid('enc/_set_bulb')} {{x:{x},y:{y},z:{z},lit:\"true\"}}",
            f"execute if score #b.tele pm.world matches {i} run scoreboard players set #b.tele pm.world -1",
            f"particle minecraft:end_rod {x} {y} {z} 0.3 0.3 0.3 0.02 25 normal",
            f"playsound minecraft:block.copper_bulb.turn_on master @a {x} {y} {z} 1 1",
        ])
    add_lines = [f"execute if score #pick pm.tmp matches {i} run summon minecraft:creaking {x} {y} {z} {{Tags:[\"pm.bossadd\",\"pm.bossfx\"],PersistenceRequired:1b}}"
                 for i, (x, y, z) in enumerate(spawns)]
    R.func("c4/boss/add_tick", [
        "scoreboard players remove #b.add pm.world 1",
        "execute if score #b.add pm.world matches 1.. run return 0",
        "scoreboard players set #b.add pm.world 10",
        "execute store result score #alive pm.tmp if entity @e[type=creaking,tag=pm.bossadd]",
        f"execute store result score #cap pm.tmp if entity @a[{SEL},gamemode=!spectator]",
        "scoreboard players add #cap pm.tmp 1",
        "execute if score #set.difficulty pm.world matches 0 run scoreboard players remove #cap pm.tmp 1",
        "execute if score #alive pm.tmp >= #cap pm.tmp run return 0",
        f"execute store result score #pick pm.tmp run random value 0..{len(spawns) - 1}",
        *add_lines,
    ])
    R.func("c4/boss/blink_tick", [
        "scoreboard players remove #b.blink pm.world 1",
        "execute if score #b.blink pm.world matches 1.. run return 0",
        "scoreboard players set #b.blink pm.world 15",
        f"execute as @r[{SEL},gamemode=!spectator] at @s rotated ~ 0 positioned ^ ^ ^-4 if predicate {fid('arena_floor')} "
        f"if block ~ ~ ~ #palemeridian:passable if block ~ ~1 ~ #palemeridian:passable run function {fid('c4/boss/blink')}",
    ])
    R.func("c4/boss/blink", [
        f"tp {boss_sel} ~ ~ ~ facing entity @s",
        "particle minecraft:white_ash ~ ~2 ~ 1 2 1 0.02 120 normal",
        "playsound minecraft:entity.creaking.freeze master @a ~ ~ ~ 1.5 0.5",
        f"effect give @a[{SEL},scores={{pm.optfx=1}}] minecraft:darkness 3 0 true",
        f"title @a[{SEL},scores={{pm.optfx=0}}] actionbar {snbt({'text': 'The Unlooked steps through the fog behind someone!', 'color': 'red'})}",
    ])
    R.func("c4/boss/win", [
        "scoreboard players set #boss pm.world 2",
        "kill @e[type=creaking,tag=pm.bossadd]",
        "bossbar set palemeridian:boss visible false",
        "time of palemeridian:surge pause",
        "time of palemeridian:surge set 0",
        *[f"function {fid('enc/_set_bulb')} {{x:{x},y:{y},z:{z},lit:\"true\"}}" for (x, y, z) in relays],
        f"particle minecraft:white_ash {TX} {GY + 3} {TZ} 6 4 6 0.05 800 normal",
        f"playsound minecraft:entity.creaking.death master @a {TX} {GY} {TZ} 2 0.5",
        complete("c4.unlooked"),
    ])
    R.func("c4/boss/reset", [
        "scoreboard players set #boss pm.world 0",
        "kill @e[type=creaking,tag=pm.boss]",
        "kill @e[type=creaking,tag=pm.bossadd]",
        "bossbar set palemeridian:boss visible false",
        "time of palemeridian:surge pause",
        "time of palemeridian:surge set 0",
        *[f"function {fid('enc/_set_bulb')} {{x:{x},y:{y},z:{z},lit:\"true\"}}" for (x, y, z) in relays],
        "scoreboard players operation #cool.boss pm.world = #seconds pm.world",
        "scoreboard players add #cool.boss pm.world 10",
        tellraw("@a", {"text": "The shape on the gallery slumps back into fog. It will stand up again when someone returns to the Lens.", "color": "gray", "italic": True}),
    ])

    # ============================================================== the Lens burns
    mlamps = poi.get("meridian.lamps")["lamps"]
    bx, by, bz = lens["beacon"]
    cfrom, cto = (min(c[0] for c in chart["frame"]), min(c[2] for c in chart["frame"])), (max(c[0] for c in chart["frame"]), max(c[2] for c in chart["frame"]))
    R.func("c4/lens_burns", [
        f"setblock {bx} {by} {bz} minecraft:beacon",
        f"particle minecraft:end_rod {xyz(lens)} 0.3 3 3 0.05 400 normal",
        f"playsound minecraft:block.beacon.activate master @a {xyz(lens)} 3 0.8",
        "playsound minecraft:block.bell.resonate master @a ~ ~ ~ 2 1.2",
        *[f"function {fid('enc/_set_bulb')} {{x:{x},y:{y},z:{z},lit:\"true\"}}" for (x, y, z) in mlamps],
        *[f"setblock {x} {y} {z} minecraft:sea_lantern" for (x, y, z) in chart["frame"][::2]],
        f"fill {cfrom[0]} {int(chart['y'])} {cfrom[1]} {cto[0]} {int(chart['y'])} {cto[1]} minecraft:light[level=12,waterlogged=false] replace minecraft:air",
        tellraw("@a", {"text": "The Lens catches. A column of cold blue light stands up from the Meridian through the fog, and the fog, for the first time in forty years, has to look back.", "color": "gray", "italic": True}),
        tellraw("@a", {"text": "(Go down to the Chart Room. The Long Chart is waiting, and so is everyone else.)", "color": "dark_aqua"}),
    ])

    # ============================================================== the choice
    R.npc(NPC("chart_table", "The Long Chart", "", "white", "none", body=False, label="✎ The Long Chart", size=(1.2, 1.4),
              places=[("", "meridian.chart_table")],
              talk=[(active("c4.chart"), "prop/chart/choice"),
                    ("if score #ending pm.world matches 1..", "prop/chart/after"),
                    ("", "prop/chart/dark")]))
    D(Dlg("prop/chart/dark", "The Long Chart", [
        "A floor-wide map of the Vale, every field and lane set in coloured stone. Under the north cliffs the stone is white, as if nobody ever went there.",
    ]))
    D(Dlg("prop/chart/after", "The Long Chart", ["The Chart is finished. It will be kept, and looked at."]))
    D(Dlg("prop/chart/choice", "The Long Chart", [
        "The Long Chart glows under the Lens: every field and lane of Vell, drawn true again. Except the north cliffs. There the stone is white, painted over by a hand that shook.",
        "Pen and ink wait on the table. What does the Chart say about the Deepcut?",
    ], choices=[
        Choice("Write the names", goto="prop/chart/confirm_true"),
        Choice("Leave it blank", goto="prop/chart/confirm_blank"),
        Choice("Not yet. (Hear what the others think first.)", run=[tellraw("@s", {"text": "Tamsin, Brannoc, Odile and Hesper are all here in the Chart Room.", "color": "dark_aqua"})]),
    ], exit_label="Step back"))
    D(Dlg("prop/chart/confirm_true", "Write the names?", [
        "Write the eleven names into the blank, and put the Deepcut back on the Chart. The whole valley will see it, always, and the Pall will have nowhere left to hide.",
        {"text": "This decides the ending for everyone in this world. It cannot be undone.", "color": "gold"},
    ], choices=[Choice("Write the names", run=[f"function {fid('c4/ending/true')}"])], exit_label="Not yet"))
    D(Dlg("prop/chart/confirm_blank", "Leave it blank?", [
        "Leave the Deepcut blank: one place the valley agrees not to look at. The rest of Vell will clear. The eleven stay in the dark, and Hesper with them.",
        {"text": "This decides the ending for everyone in this world. It cannot be undone.", "color": "gold"},
    ], choices=[Choice("Leave it blank", run=[f"function {fid('c4/ending/blank')}"])], exit_label="Not yet"))

    mem = poi.get("deepcut.memorial_wall")
    bulbs = poi.get("deepcut.lamps")["bulbs"]
    names = {n: name for n, name, _t, _e in ELEVEN}
    plaque_lines = []
    for (x, y, z), n in zip(sorted(mem["plaques"], key=lambda p: p[0]), sorted(names)):
        plaque_lines.append(f"data merge block {x} {y} {z} {{front_text:{{messages:[\"\",{snbt(names[n])},\"\",\"\"],color:\"white\",has_glowing_text:1b}}}}")
    garden = poi.get("glassworks.hesper_garden")
    gx, gy, gz = int(garden["x"]), int(garden["y"]), int(garden["z"])
    common_start = [
        "execute unless score c4.chart pm.q matches 1 run return fail",
        "execute if score #ending pm.world matches 1.. run return fail",
    ]
    R.func("c4/ending/true", common_start + [
        "scoreboard players set #ending pm.world 1",
        tellraw("@a", [{"selector": "@s", "color": "white"}, {"text": " dips the pen and writes the names.", "color": "gray", "italic": True}]),
        *[f"scoreboard players set #{p}.{g} pm.world 1" for g in ("deepcut", "mere", "fen", "wilds") for p in ("r", "req")],
        "time of palemeridian:pall set 5000",
        *plaque_lines,
        *[f"setblock {x} {y} {z} minecraft:candle[candles=1,lit=true,waterlogged=false]" for (x, y, z) in mem["candles"]],
        *[f"setblock {x} {y} {z} minecraft:waxed_copper_bulb[lit=true,powered=false]" for (x, y, z) in bulbs],
        *[f"setblock {x} {y} {z} minecraft:light_blue_glazed_terracotta" for (x, y, z) in chart["blank"]],
        *[f"function {fid(f'npc/echo_{n}/despawn')}" for n in range(1, 12)],
        "data modify storage palemeridian:npc hesper set value \"restored\"",
        f"function {fid('npc/hesper/despawn')}",
        *[f"setblock {gx + dx} {gy} {gz + dz} minecraft:potted_cornflower" for (dx, dz) in ((-1, -1), (1, -1), (-1, 1), (1, 1))],
        "time set 23500",
        "title @a times 20 100 30",
        f"title @a title {snbt({'text': 'Every Name', 'color': 'white'})}",
        f"title @a subtitle {snbt({'text': 'The Deepcut is on the Chart again.', 'color': 'gray', 'italic': True})}",
        "playsound minecraft:block.bell.resonate master @a ~ ~ ~ 2 0.8",
        tellraw("@a", {"text": "The Lens blazes. Light runs down the north cliffs like water finding its level, into the adit, along the rails, to a wall of plaques that are not blank any more. In the last gallery eleven candles take flame, and eleven figures are simply not there.", "color": "gray", "italic": True}),
        f"execute if score #k.all pm.world matches 1 run function {fid('c4/ending/spoken')}",
        f"schedule function {fid('c4/ending/after')} 200t replace",
    ])
    R.func("c4/ending/spoken", [
        tellraw("@a", {"text": "You carried every one of their keepsakes home. You read the names aloud, one by one, and on the Lens gallery someone who has not wept in forty years weeps.", "color": "gray", "italic": True}),
        activate("ep.spoken"), complete("ep.spoken"),
    ])
    R.func("c4/ending/blank", common_start + [
        "scoreboard players set #ending pm.world 2",
        tellraw("@a", [{"selector": "@s", "color": "white"}, {"text": " sets down the pen. The white space stays white.", "color": "gray", "italic": True}]),
        *[f"scoreboard players set #{p}.{g} pm.world 1" for g in ("mere", "fen", "wilds") for p in ("r", "req")],
        "time of palemeridian:pall set 6000",
        f"function {fid('npc/hesper/despawn')}",
        "title @a times 20 100 30",
        f"title @a title {snbt({'text': 'The Kept Silence', 'color': 'white'})}",
        f"title @a subtitle {snbt({'text': 'One place in Vell is allowed to be forgotten.', 'color': 'gray', 'italic': True})}",
        "playsound minecraft:block.bell.resonate master @a ~ ~ ~ 2 0.6",
        tellraw("@a", {"text": "The Lens burns over the lake, the orchards, the kilns and the fen, and stops at the north cliffs as if at a closed door. Beyond it the Deepcut keeps its fog, and its eleven, and now its Keeper.", "color": "gray", "italic": True}),
        f"execute if score #k.all pm.world matches 1 run function {fid('c4/ending/kept')}",
        f"schedule function {fid('c4/ending/after')} 200t replace",
    ])
    R.func("c4/ending/kept", [
        tellraw("@a", {"text": "You give Hesper the eleven keepsakes. She holds them the way you would hold something that might still be warm. \"Kept safe,\" she says. \"All of them.\"", "color": "gray", "italic": True}),
        activate("ep.kept"), complete("ep.kept"),
    ])
    R.func("c4/ending/after", [complete("c4.chart")])

    # ============================================================== epilogue: credits, the Keeper's Glass, free play
    glass = ("minecraft:spyglass[minecraft:custom_name=" + snbt({"text": "Keeper's Glass", "italic": False, "color": "aqua"}) +
             ",minecraft:lore=[" + snbt({"text": "Keeper of the Meridian", "italic": False, "color": "gold"}) + "," +
             snbt({"text": "A chart is a promise to look.", "italic": False, "color": "gray"}) + "]"
             ",minecraft:enchantment_glint_override=true,minecraft:custom_data={pm:{item:\"keepers_glass\"}}]")
    R.func("c4/keepers_glass", [
        "execute if items entity @s container.* *[minecraft:custom_data~{pm:{item:\"keepers_glass\"}}] run return fail",
        "execute if score @s pm.glass matches 1 run return fail",
        "scoreboard players set @s pm.glass 1",
        f"give @s {glass} 1",
    ])
    R.load_hooks.append("scoreboard objectives add pm.glass dummy")
    R.player_first_join.append(f"execute if score #ending pm.world matches 1.. run function {fid('c4/keepers_glass')}")
    R.player_rejoin.append(f"execute if score #ending pm.world matches 1.. run function {fid('c4/keepers_glass')}")
    R.func("c4/epilogue", [
        f"execute as @a run function {fid('c4/keepers_glass')}",
        f"execute as @a run dialog show @s {fid('journal/credits')}",
        "playsound minecraft:ui.toast.challenge_complete master @a ~ ~ ~ 0.8 1",
        tellraw("@a", {"text": "(The survey is complete. The Vale is yours: every district keeps its people, its services and its lamps. The world beyond the rim is ordinary Minecraft.)", "color": "dark_aqua"}),
    ])
    dialog("journal/credits", {
        "type": "minecraft:notice",
        "title": {"text": "Pale Meridian", "color": "white"},
        "body": [
            {"type": "minecraft:plain_message", "width": 320, "contents": {"text": "Chart what the fog forgot.", "color": "gray", "italic": True}},
            {"type": "minecraft:plain_message", "width": 320, "contents": {"text": "Story, world, systems and art: the Pale Meridian project. Built for Minecraft Java Edition 26.2 with Fabric. Third-party mods in the pack belong to their authors (see ATTRIBUTION.md).", "color": "white"}},
            {"type": "minecraft:plain_message", "width": 320, "contents": {"text": "Thank you for charting the Vale of Vell. Odile will keep the lamps lit.", "color": "white"}},
        ],
        "action": {"label": {"text": "Continue in free play"}, "width": 200},
        "can_close_with_escape": True, "pause": False, "after_action": "close",
    })
