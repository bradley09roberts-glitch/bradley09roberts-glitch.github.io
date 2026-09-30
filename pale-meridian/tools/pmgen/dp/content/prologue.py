"""PROLOGUE — The Letter (the Landing). SPOILER-FREE identifiers; see docs/spoilers for the story."""
from __future__ import annotations

from ... import poi
from .. import fid, snbt, tellraw, xyz
from ..engine import (R, Area, Blueprint, Choice, Dlg, NPC, Part, Quest, activate, active, announce, complete, done,
                      show, uuid_nbt, uuid_str)

LANTERNS = ["minecraft:lantern", "minecraft:soul_lantern", "minecraft:copper_lantern", "minecraft:exposed_copper_lantern",
            "minecraft:weathered_copper_lantern", "minecraft:oxidized_copper_lantern", "minecraft:waxed_copper_lantern",
            "minecraft:waxed_exposed_copper_lantern", "minecraft:waxed_weathered_copper_lantern", "minecraft:waxed_oxidized_copper_lantern"]
CHAINS = ["minecraft:iron_chain", "minecraft:copper_chain", "minecraft:exposed_copper_chain", "minecraft:weathered_copper_chain",
          "minecraft:oxidized_copper_chain", "minecraft:waxed_copper_chain", "minecraft:waxed_exposed_copper_chain",
          "minecraft:waxed_weathered_copper_chain", "minecraft:waxed_oxidized_copper_chain"]
STONE_BRICKS = ["minecraft:stone_bricks", "minecraft:mossy_stone_bricks", "minecraft:cracked_stone_bricks", "minecraft:chiseled_stone_bricks"]


def register() -> None:
    from .. import tag
    tag("item", "palemeridian", "chains", CHAINS)
    tag("item", "palemeridian", "lanterns", LANTERNS)
    tag("block", "palemeridian", "lanterns", LANTERNS)
    camp = poi.get("landing.camp")
    lamp = poi.get("landing.lamp")
    board = poi.get("landing.noticeboard")
    fig = poi.get("landing.figure")

    # -------------------------------------------------------------- quests
    R.quest(Quest("p.letter", 0, "A Letter at the Landing", "Read the letter pinned to the noticeboard.",
                  "Someone left a letter for you at the Landing waystation.",
                  target="landing.noticeboard", icon="minecraft:paper", hint="Use the noticeboard beside the waystation."))
    R.quest(Quest("p.camp", 0, "Tamsin's Camp", "Find Tamsin's camp up the road.",
                  "Her letter says her camp is up the road, and to take what you need.",
                  prereq=["p.letter"], target="landing.camp", icon="minecraft:campfire",
                  on_complete=[
                      tellraw("@a", {"text": "Tamsin's camp. The bedroll is cold, but the fire is still burning, which makes no sense at all.", "color": "gray", "italic": True}),
                      tellraw("@a", {"text": "Pinned to the tent pole, in her hand: \"Lamp supplies in the chest. Eight iron nuggets and a torch make a lantern. Crafting table's by the fire.\"", "color": "gray", "italic": True}),
                  ]))
    R.quest(Quest("p.lamp", 0, "The Landing Lamp", "Rebuild the broken lamp beside the road.",
                  "The Landing lamp has gone dark. Its missing pieces glow faintly where they belong: two stone bricks, a chain and a lantern.",
                  prereq=["p.camp"], target="landing.lamp", progress_max=4, icon="minecraft:lantern", frame="goal",
                  hint="Stack the pieces bottom to top: brick, brick, chain, lantern. Any stone-brick, chain or lantern variant works.",
                  on_complete=[
                      f"function {fid('prologue/lamp_lit')}",
                  ]))
    R.quest(Quest("p.road", 0, "Into the Pall", "Follow the lamp road north to Hollin.",
                  "Beyond the Landing the road runs north into the fog, marked by old lamp posts. Carry a light.",
                  prereq=["p.lamp"], target="hollin.gate", icon="minecraft:compass",
                  on_activate=[f"function {fid('prologue/figure_appear')}"],
                  on_complete=["scoreboard players set #chapter pm.world 1"]))
    R.quest(Quest("p.chill", 0, "Cold Without Light", "Walk in the fog holding a torch or lantern.",
                  "The Pall chills anyone who walks in it without a light.",
                  prereq=["p.camp"], main=False, icon="minecraft:torch"))

    # -------------------------------------------------------------- the noticeboard (prop)
    R.npc(NPC("noticeboard", "Noticeboard", "", "white", "none", body=False, label="✉ A letter for you", size=(1.1, 1.6),
              places=[("", "landing.noticeboard")],
              talk=[("", "prop/noticeboard/letter")]))
    R.dlg(Dlg("prop/noticeboard/letter", "A letter, addressed to you", [
        {"text": "Surveyor —", "color": "white"},
        {"text": "If this reaches you, then I'm sorry, and also: I was right. The Vale of Vell is not gone. It is here, under the fog they call the Pall, and there are people in it who have forgotten their own names.", "color": "white"},
        {"text": "Don't send the Survey. Come yourself. Bring light. The fog is cold without it.", "color": "white"},
        {"text": "The lamp at the Landing has gone dark. Light it and the road will remember you. My camp is up the road. Take what you need.", "color": "white"},
        {"text": "— T.R.", "color": "gray"},
        {"text": "P.S. If you see me in the fog, don't follow. It won't be me. Not yet.", "color": "dark_gray", "italic": True},
    ], choices=[
        Choice("Take the letter and go", run=[f"function {fid('items/letter')}", complete("p.letter")]),
    ], exit_label="Leave it for now"))

    # -------------------------------------------------------------- camp arrival + chill lesson
    R.area(Area("landing_camp", camp["box"]))
    R.location_hooks.append(("landing_camp", active("p.camp"), [complete("p.camp")]))
    R.location_hooks.append(("landing_camp", "", [f"function {fid('prologue/camp_refill')}"]))
    R.func("prologue/camp_refill", [
        "# Recovery: keep the lamp materials available while the lamp is unfinished.",
        "execute unless score p.lamp pm.q matches 0..1 run return fail",
        "execute if score #camp_refill pm.world >= #seconds pm.world run return fail",
        "scoreboard players operation #camp_refill pm.world = #seconds pm.world",
        "scoreboard players add #camp_refill pm.world 120",
        f"execute unless items block {xyz(poi.get('landing.camp_chest'))} container.* minecraft:stone_bricks run item replace block {xyz(poi.get('landing.camp_chest'))} container.3 with minecraft:stone_bricks 2",
        f"execute unless items block {xyz(poi.get('landing.camp_chest'))} container.* #palemeridian:chains run item replace block {xyz(poi.get('landing.camp_chest'))} container.4 with minecraft:iron_chain 1",
        f"execute unless items block {xyz(poi.get('landing.camp_chest'))} container.* minecraft:iron_nugget run item replace block {xyz(poi.get('landing.camp_chest'))} container.2 with minecraft:iron_nugget 8",
        f"execute unless items block {xyz(poi.get('landing.camp_chest'))} container.* minecraft:torch run item replace block {xyz(poi.get('landing.camp_chest'))} container.1 with minecraft:torch 6",
    ])
    R.slow_hooks.append(f"execute if score p.chill pm.q matches 1 as @a[gamemode=!spectator,predicate={fid('in_pall')},predicate={fid('holding_light')}] run {complete('p.chill')}")

    # -------------------------------------------------------------- the lamp blueprint
    parts = []
    accepts = {"stone_bricks": STONE_BRICKS, "iron_chain[axis=y]": CHAINS, "lantern[hanging=false]": LANTERNS}
    for part in lamp["parts"]:
        parts.append(Part(tuple(part["pos"]), accepts[part["block"]], part["block"]))
    R.blueprint(Blueprint("landing_lamp", "p.lamp", parts, center=(lamp["x"], lamp["y"], lamp["z"]), radius=18,
                          on_complete=[complete("p.lamp")]))
    R.func("prologue/lamp_lit", [
        f"particle minecraft:end_rod {xyz(lamp, 4.5)} 0.3 0.3 0.3 0.02 40 normal",
        f"playsound minecraft:block.beacon.activate master @a {xyz(lamp, 4)} 1.5 1.2",
        f"playsound minecraft:block.bell.resonate master @a {xyz(lamp, 4)} 1.0 0.8",
        tellraw("@a", {"text": "The lamp catches. For a moment it burns much too bright, and the fog draws back from the Landing like a tide going out.", "color": "gray", "italic": True}),
        "scoreboard players set #r.landing pm.world 1",
        "scoreboard players set #req.landing pm.world 1",
        f"time of palemeridian:pall set 1000",
    ])

    # -------------------------------------------------------------- the figure on the road
    fig_uuid = uuid_str("prop:figure")
    R.func("prologue/figure_appear", [
        f"execute unless entity {fig_uuid} run summon minecraft:mannequin {xyz(fig)} {{UUID:{uuid_nbt('prop:figure')},Rotation:[0f,0f],"
        f"profile:{{texture:\"palemeridian:entity/npc/tamsin_faded\",model:\"slim\"}},immovable:1b,Invulnerable:1b,hide_description:1b,"
        f"equipment:{{mainhand:{{id:\"minecraft:lantern\",count:1}}}},Tags:[\"pm.figure\"]}}",
        "scoreboard players set #figure pm.world 1",
    ])
    R.area(Area("landing_figure_near", [int(fig["x"]) - 14, 60, int(fig["z"]) - 14, int(fig["x"]) + 14, 110, int(fig["z"]) + 14]))
    R.location_hooks.append(("landing_figure_near", "if score #figure pm.world matches 1", [f"function {fid('prologue/figure_vanish')}"]))
    R.func("prologue/figure_vanish", [
        "scoreboard players set #figure pm.world 2",
        f"execute at {fig_uuid} run particle minecraft:white_ash ~ ~1 ~ 0.4 0.9 0.4 0.01 120 normal",
        f"execute at {fig_uuid} run playsound minecraft:entity.creaking.freeze master @a ~ ~ ~ 1 0.6",
        f"kill {fig_uuid}",
        tellraw("@a", [{"text": "A figure with a lantern stood on the road, and then there was only fog. ", "color": "gray", "italic": True},
                       {"text": "Tamsin?", "color": "white", "italic": True}]),
    ])

    # -------------------------------------------------------------- arrival at Hollin (area defined by the Hollin site)
    R.location_hooks.append(("hollin_gate", active("p.road"), [complete("p.road")]))

    # -------------------------------------------------------------- world init
    R.world_init.append("scoreboard players set #figure pm.world 0")
