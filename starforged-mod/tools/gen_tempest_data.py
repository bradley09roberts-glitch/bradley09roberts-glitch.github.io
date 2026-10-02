#!/usr/bin/env python3
"""
Tempestforged data/asset generator. Run AFTER gen_data.py, gen_sun_data.py and gen_moon_data.py - it adds the
Tempestforged blocks, items, equipment, particles, sounds, lang, recipes, loot, tags, damage types, advancements and
the Stormreach dimension (dimension type, floating-island noise settings, five biomes, the Tempest Citadel).
Needs the vanilla worldgen data for the floating-islands router and the cherry tree shape: --vanilla-data <.../data/minecraft>.
"""
import json
import os
import sys

from gen_sun_data import (A, D, ROOT, chance, cook, elements, item_entry, model_item, pool, read, shaped, shapeless, sid, silk,
                          simple_state, spawner, surface_patch, tag, write)
from gen_sun_data import cube, placed, configured  # noqa: F401

VANILLA = ""

# ----------------------------------------------------------------------------------------------------------------
# Block models & states
# ----------------------------------------------------------------------------------------------------------------

CUBE = ["stormstone", "skyrock", "skysoil", "stormwood_planks", "aetherium_ore", "aetherium_block", "charged_aetherium_block",
        "tempest_bricks", "chiseled_tempest_bricks", "splitter_relay", "overload_relay"]
# Facing -> rotation for a model whose front is its north face.
FACING_ROT = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}, "up": {"x": 270}, "down": {"x": 90}}


def front_model(name, front, side, top=None):
    write(f"{A}/models/block/{name}.json", {"parent": "minecraft:block/orientable_with_bottom", "textures": {
        "front": sid(f"block/{front}"), "side": sid(f"block/{side}"), "top": sid(f"block/{top or side}"), "bottom": sid(f"block/{top or side}")}})


def bottom_top(name, top, side, bottom=None):
    write(f"{A}/models/block/{name}.json", {"parent": "minecraft:block/cube_bottom_top", "textures": {
        "top": sid(f"block/{top}"), "side": sid(f"block/{side}"), "bottom": sid(f"block/{bottom or side}")}})


def blocks():
    for b in CUBE:
        cube(b)
        simple_state(b)
    bottom_top("stormgrass", "stormgrass_top", "stormgrass_side", "skysoil")
    simple_state("stormgrass")
    write(f"{A}/models/block/stormwood_log.json", {"parent": "minecraft:block/cube_column", "textures": {
        "end": sid("block/stormwood_log_top"), "side": sid("block/stormwood_log")}})
    write(f"{A}/models/block/stormwood_log_horizontal.json", {"parent": "minecraft:block/cube_column_horizontal", "textures": {
        "end": sid("block/stormwood_log_top"), "side": sid("block/stormwood_log")}})
    write(f"{A}/blockstates/stormwood_log.json", {"variants": {
        "axis=x": {"model": sid("block/stormwood_log_horizontal"), "x": 90, "y": 90},
        "axis=y": {"model": sid("block/stormwood_log")},
        "axis=z": {"model": sid("block/stormwood_log_horizontal"), "x": 90}}})
    write(f"{A}/models/block/stormleaves.json", {"parent": "minecraft:block/leaves", "textures": {"all": sid("block/stormleaves")}})
    simple_state("stormleaves")
    for name in ("gale_seed",):
        write(f"{A}/models/block/{name}.json", {"parent": "minecraft:block/cross", "textures": {"cross": sid(f"block/{name}")}})
        simple_state(name)
    write(f"{A}/models/block/thunder_crystal_cluster.json", {"parent": "minecraft:block/cross",
                                                             "textures": {"cross": sid("block/thunder_crystal_cluster")}})
    rot = {"down": {"x": 180}, "east": {"x": 90, "y": 90}, "north": {"x": 90}, "south": {"x": 90, "y": 180}, "up": {}, "west": {"x": 90, "y": 270}}
    write(f"{A}/blockstates/thunder_crystal_cluster.json", {"variants": {
        f"facing={k}": dict({"model": sid("block/thunder_crystal_cluster")}, **v) for k, v in rot.items()}})

    tex = sid("block/tempest_bricks")
    for suffix, parent in (("", "stairs"), ("_inner", "inner_stairs"), ("_outer", "outer_stairs")):
        write(f"{A}/models/block/tempest_brick_stairs{suffix}.json",
              {"parent": f"minecraft:block/{parent}", "textures": {"bottom": tex, "side": tex, "top": tex}})
    stairs = json.load(open(os.path.join(ROOT, "tools/stone_brick_stairs_blockstate.json")))
    write(f"{A}/blockstates/tempest_brick_stairs.json",
          json.loads(json.dumps(stairs).replace("minecraft:block/stone_brick_stairs", sid("block/tempest_brick_stairs"))))
    write(f"{A}/models/block/tempest_brick_slab.json", {"parent": "minecraft:block/slab", "textures": {"bottom": tex, "side": tex, "top": tex}})
    write(f"{A}/models/block/tempest_brick_slab_top.json", {"parent": "minecraft:block/slab_top", "textures": {"bottom": tex, "side": tex, "top": tex}})
    write(f"{A}/blockstates/tempest_brick_slab.json", {"variants": {
        "type=bottom": {"model": sid("block/tempest_brick_slab")},
        "type=double": {"model": sid("block/tempest_bricks")},
        "type=top": {"model": sid("block/tempest_brick_slab_top")}}})
    write(f"{A}/models/block/aetherglass.json", {"parent": "minecraft:block/cube_all",
                                                  "textures": {"all": {"sprite": sid("block/aetherglass"), "force_translucent": True}}})
    simple_state("aetherglass")

    # Storm lantern: the vanilla lantern shapes.
    write(f"{A}/models/block/storm_lantern.json", {"parent": "minecraft:block/template_lantern", "textures": {"lantern": sid("block/storm_lantern")}})
    write(f"{A}/models/block/storm_lantern_hanging.json", {"parent": "minecraft:block/template_hanging_lantern",
                                                           "textures": {"lantern": sid("block/storm_lantern")}})
    write(f"{A}/blockstates/storm_lantern.json", {"variants": {
        "hanging=false": {"model": sid("block/storm_lantern")}, "hanging=true": {"model": sid("block/storm_lantern_hanging")}}})
    chime = sid("block/wind_chime")
    write(f"{A}/models/block/wind_chime.json", {"parent": "minecraft:block/block", "textures": {"particle": chime, "chime": chime},
                                                 "elements": [{"from": [4, 2, 8], "to": [12, 16, 8], "shade": False,
                                                               "faces": {"north": {"texture": "#chime"}, "south": {"texture": "#chime"}}},
                                                              {"from": [8, 2, 4], "to": [8, 16, 12], "shade": False,
                                                               "faces": {"east": {"texture": "#chime"}, "west": {"texture": "#chime"}}}]})
    simple_state("wind_chime")

    # Directional machines.
    def directional(name, model_of):
        variants = {}
        for facing, r in FACING_ROT.items():
            for powered in ("false", "true"):
                variants[f"facing={facing},powered={powered}"] = dict({"model": model_of(powered == "true")}, **r)
        write(f"{A}/blockstates/{name}.json", {"variants": variants})

    for on in (False, True):
        suffix = "_on" if on else ""
        front_model(f"storm_dynamo{suffix}", f"storm_dynamo_front{suffix}", "storm_machine_side", "storm_machine_top")
        for c in ("aetherium_conductor", "rotating_conductor"):
            front_model(f"{c}{suffix}", f"{c}_front{suffix}", f"{c}_side")
    directional("storm_dynamo", lambda on: sid("block/storm_dynamo" + ("_on" if on else "")))
    directional("aetherium_conductor", lambda on: sid("block/aetherium_conductor" + ("_on" if on else "")))
    directional("rotating_conductor", lambda on: sid("block/rotating_conductor" + ("_on" if on else "")))
    # orientable_with_bottom has no vertical front: an up/down facing rotates it with x, which is what FACING_ROT does.

    for name, prop, a, b in (("storm_relay", "powered", "storm_relay", "storm_relay_on"), ("citadel_core", "lit", "citadel_core", "citadel_core_lit")):
        cube(a)
        cube(b)
        write(f"{A}/blockstates/{name}.json", {"variants": {f"{prop}=false": {"model": sid(f"block/{a}")}, f"{prop}=true": {"model": sid(f"block/{b}")}}})
    for lvl in range(4):
        cube(f"storm_capacitor_{lvl}")
    write(f"{A}/blockstates/storm_capacitor.json", {"variants": {
        f"charge={c}": {"model": sid(f"block/storm_capacitor_{0 if c == 0 else min(3, 1 + (c - 1) // 5)}")} for c in range(16)}})

    bottom_top("lightning_beacon", "lightning_beacon_top", "lightning_beacon_side", "storm_machine_side")
    simple_state("lightning_beacon")
    bottom_top("weather_engine", "weather_engine_top", "weather_engine_side", "storm_machine_side")
    simple_state("weather_engine")
    bottom_top("tempest_wind_vent", "wind_vent_top", "wind_vent_side", "storm_machine_side")
    simple_state("wind_vent")
    for on in (False, True):
        bottom_top("storm_lift" + ("_on" if on else ""), "storm_lift_top" + ("_on" if on else ""), "wind_vent_side", "storm_machine_side")
    write(f"{A}/blockstates/storm_lift.json", {"variants": {"powered=false": {"model": sid("block/storm_lift")},
                                                          "powered=true": {"model": sid("block/storm_lift_on")}}})
    front_model("gale_vent", "gale_vent_front", "storm_machine_side", "storm_machine_top")
    write(f"{A}/blockstates/gale_vent.json", {"variants": {f"facing={f}": dict({"model": sid("block/gale_vent")}, **FACING_ROT[f])
                                                         for f in ("north", "east", "south", "west")}})
    side = sid("block/storm_machine_side")
    write(f"{A}/models/block/shock_plate.json", {
        "parent": "minecraft:block/block", "textures": {"particle": side, "side": side, "top": sid("block/shock_plate"), "bottom": side},
        "elements": elements([([0, 0, 0], [16, 2, 16])])})
    simple_state("shock_plate")
    bottom_top("sky_anchor", "sky_anchor_top", "sky_anchor_side", "storm_machine_side")
    simple_state("sky_anchor")
    for rune in ("thunder_rune", "gale_rune"):
        cube(rune)
        cube(f"{rune}_spent")
        write(f"{A}/blockstates/{rune}.json", {"variants": {"armed=true": {"model": sid(f"block/{rune}")},
                                                           "armed=false": {"model": sid(f"block/{rune}_spent")}}})
    bottom_top("cyclone_emitter", "cyclone_emitter_top", "cyclone_emitter_side", "storm_machine_side")
    simple_state("cyclone_emitter")
    cube("tempest_seal")
    write(f"{A}/blockstates/tempest_seal.json", {"variants": {"opening=false": {"model": sid("block/tempest_seal")},
                                                             "opening=true": {"model": sid("block/tempest_seal")}}})
    side, top, bottom = sid("block/tempest_altar_side"), sid("block/tempest_altar_top"), sid("block/tempest_altar_bottom")
    write(f"{A}/models/block/tempest_altar.json", {
        "parent": "minecraft:block/block", "textures": {"particle": side, "side": side, "top": top, "bottom": bottom},
        "elements": elements([([0, 0, 0], [16, 3, 16]), ([2, 3, 2], [14, 10, 14]), ([0, 10, 0], [16, 14, 16])])})
    simple_state("tempest_altar")
    g = sid("block/stormgate")
    pool_ = elements([([0, 0, 0], [16, 12, 16])])
    for face in ("north", "south", "east", "west", "down"):
        pool_[0]["faces"][face]["cullface"] = face
    write(f"{A}/models/block/stormgate.json", {
        "parent": "minecraft:block/block", "textures": {"particle": g, "side": g, "top": g, "bottom": g}, "elements": pool_})
    simple_state("stormgate")


# ----------------------------------------------------------------------------------------------------------------
# Items
# ----------------------------------------------------------------------------------------------------------------

GENERATED = ["raw_aetherium", "aetherium_ingot", "charged_aetherium_ingot", "thunder_shard", "static_mote", "charged_aether_dust",
             "shardwing_crystal", "storm_feather", "stormbound_plate", "charged_scrap", "thunderjaw_horn", "stormhide", "breeze_shard",
             "alpha_conductor_horn", "stormheart", "skybreaker_core", "tempest_sigil", "aetherium_helmet", "aetherium_chestplate",
             "aetherium_leggings", "aetherium_boots", "tempest_crown", "arc_cannon", "storm_lantern"]
HANDHELD = ["aetherium_sword", "aetherium_pickaxe", "aetherium_axe", "aetherium_shovel", "aetherium_hoe", "skybreaker_halberd",
            "tempest_javelin", "gale_blades", "stormhook", "skycleaver"]
EGGS = ["static_wisp", "shardwing", "stormbound", "thunderjaw", "zephyr_sprite", "storm_roc", "thunderjaw_alpha", "veyr"]
BLOCK_ITEMS = {
    "stormstone": "stormstone", "skyrock": "skyrock", "skysoil": "skysoil", "stormgrass": "stormgrass", "stormwood_log": "stormwood_log",
    "stormwood_planks": "stormwood_planks", "stormleaves": "stormleaves", "aetherium_ore": "aetherium_ore", "aetherium_block": "aetherium_block",
    "charged_aetherium_block": "charged_aetherium_block", "tempest_bricks": "tempest_bricks", "chiseled_tempest_bricks": "chiseled_tempest_bricks",
    "tempest_brick_stairs": "tempest_brick_stairs", "tempest_brick_slab": "tempest_brick_slab", "aetherglass": "aetherglass",
    "storm_dynamo": "storm_dynamo", "aetherium_conductor": "aetherium_conductor", "rotating_conductor": "rotating_conductor",
    "splitter_relay": "splitter_relay", "storm_relay": "storm_relay", "overload_relay": "overload_relay", "storm_capacitor": "storm_capacitor_0",
    "citadel_core": "citadel_core", "lightning_beacon": "lightning_beacon", "weather_engine": "weather_engine", "wind_vent": "wind_vent",
    "gale_vent": "gale_vent", "storm_lift": "storm_lift", "shock_plate": "shock_plate", "sky_anchor": "sky_anchor", "thunder_rune": "thunder_rune",
    "gale_rune": "gale_rune", "cyclone_emitter": "cyclone_emitter", "tempest_seal": "tempest_seal", "tempest_altar": "tempest_altar",
}


def items():
    for name in GENERATED:
        write(f"{A}/models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": sid(f"item/{name}")}})
        model_item(name, sid(f"item/{name}"))
    for name in HANDHELD:
        write(f"{A}/models/item/{name}.json", {"parent": "minecraft:item/handheld", "textures": {"layer0": sid(f"item/{name}")}})
        model_item(name, sid(f"item/{name}"))
    for mob in EGGS:
        name = f"{mob}_spawn_egg"
        write(f"{A}/models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": sid(f"item/{name}")}})
        model_item(name, sid(f"item/{name}"))
    for name, model in BLOCK_ITEMS.items():
        model_item(name, sid(f"block/{model}"))
    for name in ("thunder_crystal_cluster", "gale_seed", "wind_chime"):
        write(f"{A}/models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": sid(f"block/{name}")}})
        model_item(name, sid(f"item/{name}"))


def equipment_particles():
    write(f"{A}/equipment/aetherium.json", {"layers": {"humanoid": [{"texture": sid("aetherium")}],
                                                       "humanoid_leggings": [{"texture": sid("aetherium")}]}})
    write(f"{A}/equipment/tempest.json", {"layers": {"humanoid": [{"texture": sid("tempest")}]}})
    write(f"{A}/particles/static_spark.json", {"textures": [sid(f"glint_{i}") for i in range(4)]})
    write(f"{A}/particles/storm_wisp.json", {"textures": [sid(f"mote_{i}") for i in range(4)]})


SOUNDS = {
    "item.skybreaker_core.forge": ("tempest_gate_form", "A Stormgate tears open"),
    "block.stormgate.travel": ("tempest_gate_travel", "Stormgate crackles"),
    "ambient.stormreach.wind": ("stormreach_wind", "Storm winds howl"),
    "ambient.stormreach.gust": ("stormreach_gust", "A crosswind gusts"),
    "event.supercell": ("tempest_supercell", "A Supercell forms"),
    "item.aetherium.charge": ("tempest_aetherium_charge", "Aetherium charges"),
    "block.wind_vent.blow": ("tempest_wind_vent", "Wind Vent blows"),
    "block.shock_plate.zap": ("tempest_shock_plate", "Shock Plate zaps"),
    "block.wind_chime.chime": ("tempest_wind_chime", "Wind Chime rings"),
    "block.rotating_conductor.turn": ("tempest_conductor_turn", "Conductor turns"),
    "block.storm_network.pulse": ("tempest_network_pulse", "Storm pulse arcs"),
    "block.citadel_core.online": ("tempest_core_online", "Citadel Core powers up"),
    "block.overload_relay.overload": ("tempest_overload", "Overload!"),
    "block.tempest_seal.open": ("tempest_seal_open", "Tempest Seal shatters"),
    "block.tempest_altar.activate": ("tempest_altar_activate", "Tempest Altar awakens"),
    "item.stormstep.dash": ("tempest_stormstep_dash", "Stormstep"),
    "item.skybreaker_halberd.launch": ("tempest_halberd_launch", "Halberd leaps skyward"),
    "item.skybreaker_halberd.slam": ("tempest_halberd_slam", "Thunderfall"),
    "item.tempest_javelin.throw": ("tempest_javelin_throw", "Javelin thrown"),
    "item.tempest_javelin.recall": ("tempest_javelin_recall", "Javelin recalled"),
    "item.gale_blades.cyclone": ("tempest_blades_cyclone", "Cyclone"),
    "item.stormhook.fire": ("tempest_hook_fire", "Stormhook fires"),
    "item.stormhook.live_wire": ("tempest_live_wire", "Live Wire crackles"),
    "item.arc_cannon.charge": ("tempest_cannon_charge", "Arc Cannon charges"),
    "item.arc_cannon.fire": ("tempest_cannon_fire", "Arc Cannon fires"),
    "item.skycleaver.judgement": ("tempest_judgement", "Heaven's Judgement"),
    "entity.static_wisp.ambient": ("tempest_wisp_ambient", "Static Wisp crackles"),
    "entity.static_wisp.arc": ("tempest_wisp_arc", "Static Wisp arcs"),
    "entity.static_wisp.death": ("tempest_wisp_death", "Static Wisp fizzles out"),
    "entity.shardwing.ambient": ("tempest_shardwing_ambient", "Shardwing trills"),
    "entity.shardwing.dive": ("tempest_shardwing_dive", "Shardwing dives"),
    "entity.shardwing.death": ("tempest_shardwing_death", "Shardwing shatters"),
    "entity.stormbound.ambient": ("tempest_stormbound_ambient", "Stormbound hums"),
    "entity.stormbound.thunderstep": ("tempest_stormbound_step", "Stormbound Thundersteps"),
    "entity.stormbound.death": ("tempest_stormbound_death", "Stormbound collapses"),
    "entity.thunderjaw.ambient": ("tempest_thunderjaw_ambient", "Thunderjaw growls"),
    "entity.thunderjaw.stamp": ("tempest_thunderjaw_stamp", "Thunderjaw stamps"),
    "entity.thunderjaw.death": ("tempest_thunderjaw_death", "Thunderjaw dies"),
    "entity.zephyr_sprite.ambient": ("tempest_sprite_ambient", "Zephyr Sprite whistles"),
    "entity.zephyr_sprite.windguard": ("tempest_sprite_guard", "Windguard deflects"),
    "entity.storm_roc.ambient": ("tempest_roc_ambient", "Storm Roc calls"),
    "entity.storm_roc.screech": ("tempest_roc_screech", "Storm Roc screeches"),
    "entity.thunderjaw_alpha.roar": ("tempest_alpha_roar", "Thunderjaw Alpha roars"),
    "entity.veyr.roar": ("tempest_veyr_roar", "Veyr roars"),
    "entity.veyr.hurt": ("tempest_veyr_hurt", "Veyr's armour cracks"),
    "entity.veyr.death": ("tempest_veyr_death", "Veyr falls"),
    "entity.veyr.thunderstep": ("tempest_veyr_step", "Veyr Thundersteps"),
    "entity.veyr.tornado": ("tempest_veyr_tornado", "Tornado Wall"),
    "entity.veyr.shatter": ("tempest_veyr_shatter", "The summit shatters"),
    "entity.veyr.last_thunder": ("tempest_veyr_last_thunder", "The Last Thunder"),
    "entity.storm_conductor.redirect": ("tempest_conductor_redirect", "Lightning redirected"),
}


def sounds():
    path = f"{A}/sounds.json"
    data = read(path)
    for event, (file, _subtitle) in SOUNDS.items():
        data[event] = {"subtitle": f"subtitles.starforged.{event}", "sounds": [{"name": sid(file)}]}
    write(path, data)


# ----------------------------------------------------------------------------------------------------------------
# Lang
# ----------------------------------------------------------------------------------------------------------------

def lang():
    path = f"{A}/lang/en_us.json"
    L = read(path)
    L.update({
        "itemGroup.starforged.tempestforged": "Tempestforged",
        # Blocks
        "block.starforged.stormstone": "Stormstone",
        "block.starforged.skyrock": "Skyrock",
        "block.starforged.skysoil": "Skysoil",
        "block.starforged.stormgrass": "Stormgrass",
        "block.starforged.stormwood_log": "Stormwood Log",
        "block.starforged.stormwood_planks": "Stormwood Planks",
        "block.starforged.stormleaves": "Stormleaves",
        "block.starforged.gale_seed": "Gale Seed",
        "block.starforged.aetherium_ore": "Aetherium Ore",
        "block.starforged.aetherium_block": "Block of Aetherium",
        "block.starforged.charged_aetherium_block": "Block of Charged Aetherium",
        "block.starforged.thunder_crystal_cluster": "Thunder Crystal Cluster",
        "block.starforged.tempest_bricks": "Tempest Bricks",
        "block.starforged.chiseled_tempest_bricks": "Chiseled Tempest Bricks",
        "block.starforged.tempest_brick_stairs": "Tempest Brick Stairs",
        "block.starforged.tempest_brick_slab": "Tempest Brick Slab",
        "block.starforged.aetherglass": "Aetherglass",
        "block.starforged.storm_lantern": "Storm Lantern",
        "block.starforged.wind_chime": "Wind Chime",
        "block.starforged.storm_dynamo": "Storm Dynamo",
        "block.starforged.aetherium_conductor": "Aetherium Conductor",
        "block.starforged.rotating_conductor": "Rotating Conductor",
        "block.starforged.rotating_conductor.turned": "⚡ The conductor now points %s.",
        "block.starforged.rotating_conductor.north": "north",
        "block.starforged.rotating_conductor.south": "south",
        "block.starforged.rotating_conductor.east": "east",
        "block.starforged.rotating_conductor.west": "west",
        "block.starforged.rotating_conductor.up": "up",
        "block.starforged.rotating_conductor.down": "down",
        "block.starforged.splitter_relay": "Splitter Relay",
        "block.starforged.storm_relay": "Storm Relay",
        "block.starforged.overload_relay": "Overload Relay",
        "block.starforged.overload_relay.overload": "⚡ OVERLOAD! The circuit was routed wrong - the wing's core goes dark.",
        "block.starforged.storm_capacitor": "Storm Capacitor",
        "block.starforged.storm_capacitor.charge": "⚡ Charge: %s / 15",
        "block.starforged.storm_capacitor.low": "⚡ Not enough charge (%s / %s) to charge an ingot.",
        "block.starforged.citadel_core": "Citadel Core",
        "block.starforged.citadel_core.progress": "⚡ Citadel Core online - %s of %s cores lit.",
        "block.starforged.citadel_core.all": "⚡ Every Citadel Core is lit - the Tempest Seals shatter!",
        "block.starforged.citadel_core.online": "⚡ This core is online. Sneak + right-click to reset it.",
        "block.starforged.citadel_core.offline": "⚡ This core is dark. Route a storm pulse into it.",
        "block.starforged.lightning_beacon": "Lightning Beacon",
        "block.starforged.weather_engine": "Weather Engine",
        "block.starforged.weather_engine.clear": "⚡ The skies clear.",
        "block.starforged.weather_engine.rain": "⚡ Rain begins to fall.",
        "block.starforged.weather_engine.thunder": "⚡ A thunderstorm rolls in!",
        "block.starforged.weather_engine.hint": "⚡ Feed it a Charged Aetherium Ingot to turn the weather.",
        "block.starforged.weather_engine.stormreach": "The storm over the Stormreach cannot be turned.",
        "block.starforged.wind_vent": "Wind Vent",
        "block.starforged.gale_vent": "Gale Vent",
        "block.starforged.storm_lift": "Storm Lift",
        "block.starforged.shock_plate": "Shock Plate",
        "block.starforged.sky_anchor": "Sky Anchor",
        "block.starforged.sky_anchor.set": "⚡ Sky Anchor set - you will respawn here.",
        "block.starforged.thunder_rune": "Thunder Rune",
        "block.starforged.gale_rune": "Gale Rune",
        "block.starforged.cyclone_emitter": "Cyclone Emitter",
        "block.starforged.tempest_seal": "Tempest Seal",
        "block.starforged.tempest_seal.hint": "⚡ A wall of living lightning. Light every Citadel Core in the citadel to break it.",
        "block.starforged.tempest_altar": "Tempest Altar",
        "block.starforged.tempest_altar.hint": "⚡ The altar waits for a Tempest Sigil.",
        "block.starforged.tempest_altar.busy": "Veyr is already awake.",
        "block.starforged.stormgate": "Stormgate",
        # Items
        "item.starforged.raw_aetherium": "Raw Aetherium",
        "item.starforged.aetherium_ingot": "Aetherium Ingot",
        "item.starforged.aetherium_ingot.desc0": "Light as air and harder than diamond.",
        "item.starforged.aetherium_ingot.desc1": "Leave it out in a lightning storm to charge it.",
        "item.starforged.charged_aetherium_ingot": "Charged Aetherium Ingot",
        "item.starforged.charged_aetherium_ingot.desc0": "Still humming with a captured bolt.",
        "item.starforged.thunder_shard": "Thunder Shard",
        "item.starforged.thunder_shard.desc0": "A splinter of lightning that forgot to fade.",
        "item.starforged.static_mote": "Static Mote",
        "item.starforged.static_mote.desc0": "It crackles against your fingers.",
        "item.starforged.charged_aether_dust": "Charged Aether Dust",
        "item.starforged.charged_aether_dust.desc0": "Storm Rocs will follow anyone who carries it.",
        "item.starforged.shardwing_crystal": "Shardwing Crystal",
        "item.starforged.shardwing_crystal.desc0": "Grown along a Shardwing's wing edge.",
        "item.starforged.storm_feather": "Storm Feather",
        "item.starforged.storm_feather.desc0": "It never quite stops moving.",
        "item.starforged.stormbound_plate": "Stormbound Plate",
        "item.starforged.stormbound_plate.desc0": "Armour from a soldier of the Tempest Regent.",
        "item.starforged.charged_scrap": "Charged Scrap",
        "item.starforged.charged_scrap.desc0": "Bent metal, still sparking.",
        "item.starforged.thunderjaw_horn": "Thunderjaw Horn",
        "item.starforged.thunderjaw_horn.desc0": "It draws lightning like a rod.",
        "item.starforged.stormhide": "Stormhide",
        "item.starforged.stormhide.desc0": "Tough hide, veined with old lightning scars.",
        "item.starforged.breeze_shard": "Breeze Shard",
        "item.starforged.breeze_shard.desc0": "A knot of wind left behind by a Zephyr Sprite.",
        "item.starforged.alpha_conductor_horn": "Alpha Conductor Horn",
        "item.starforged.alpha_conductor_horn.desc0": "Torn from the Thunderjaw Alpha.",
        "item.starforged.alpha_conductor_horn.desc1": "Every bolt in the Supercell once ran through it.",
        "item.starforged.stormheart": "Stormheart",
        "item.starforged.stormheart.desc0": "The beating heart of the storm.",
        "item.starforged.stormheart.desc1": "Repairs the Regent's relics.",
        "item.starforged.skybreaker_core": "Skybreaker Core",
        "item.starforged.skybreaker_core.desc0": "Forged around the Heart of the Moon and struck by lightning,",
        "item.starforged.skybreaker_core.desc1": "it remembers the way to the Stormreach.",
        "item.starforged.skybreaker_core.opened": "⚡ A Stormgate tears open! Step in to cross to the Stormreach.",
        "item.starforged.skybreaker_core.wrong_world": "The core only answers in the Overworld, the Stormreach, or the Pale Reach at High Tide.",
        "item.starforged.tempest_sigil": "Tempest Sigil",
        "item.starforged.tempest_sigil.desc0": "The seal of the Tempest Citadel.",
        "item.starforged.tempest_sigil.desc1": "Offer it on the Tempest Altar atop a Tempest Citadel",
        "item.starforged.tempest_sigil.desc2": "to call down Veyr, the Tempest Regent.",
        "item.starforged.aetherium_sword": "Aetherium Sword",
        "item.starforged.aetherium_pickaxe": "Aetherium Pickaxe",
        "item.starforged.aetherium_axe": "Aetherium Axe",
        "item.starforged.aetherium_shovel": "Aetherium Shovel",
        "item.starforged.aetherium_hoe": "Aetherium Hoe",
        "item.starforged.aetherium_helmet": "Aetherium Helmet",
        "item.starforged.aetherium_chestplate": "Aetherium Chestplate",
        "item.starforged.aetherium_leggings": "Aetherium Leggings",
        "item.starforged.aetherium_boots": "Aetherium Boots",
        "item.starforged.tempest_crown": "Tempest Crown",
        "item.starforged.skybreaker_halberd": "Skybreaker Halberd",
        "item.starforged.tempest_javelin": "Tempest Javelin",
        "item.starforged.gale_blades": "Gale Blades",
        "item.starforged.stormhook": "Stormhook",
        "item.starforged.arc_cannon": "Arc Cannon",
        "item.starforged.arc_cannon.overcharged": "⚡ OVERCHARGED!",
        "item.starforged.skycleaver": "Skycleaver",
        # Abilities
        "ability.starforged.aetherium_set.title": "Full Set: Stormstep",
        "ability.starforged.aetherium_set.text": "Jump in mid-air to dash on the wind, three times before you land. Immune to lightning.",
        "ability.starforged.stormstep.dashes": "⚡ Stormstep: %s / %s",
        "ability.starforged.tempest_crown.title": "Storm's Favour",
        "ability.starforged.tempest_crown.text": "Immune to lightning. Every few seconds an arc leaps from you to the nearest enemy.",
        "ability.starforged.thunderfall.title": "Right-click: Thunderfall",
        "ability.starforged.thunderfall.text": "Vault into the sky, then crash down and call lightning on everything around you.",
        "ability.starforged.storm_spear.title": "Hold & release: Storm Spear",
        "ability.starforged.storm_spear.text": "Throw the javelin. It sticks where it lands and becomes a lightning rod.",
        "ability.starforged.javelin_recall.title": "Right-click again: Recall",
        "ability.starforged.javelin_recall.text": "Call the javelin home - it tears through every enemy on its way back.",
        "ability.starforged.cyclone.title": "Right-click: Cyclone",
        "ability.starforged.cyclone.text": "Send out a whirling cyclone that cuts and flings everything it touches.",
        "ability.starforged.stormhook.title": "Right-click: Grapple",
        "ability.starforged.stormhook.text": "Hook a block to swing to it, or a creature to drag it in.",
        "ability.starforged.live_wire.title": "Live Wire",
        "ability.starforged.live_wire.text": "While hooked, the line arcs into up to three nearby enemies.",
        "ability.starforged.arc_cannon.title": "Right-click: Arc Bolt",
        "ability.starforged.arc_cannon.text": "Fire a beam of lightning that chains between enemies.",
        "ability.starforged.overcharge.title": "Hold: Overcharge",
        "ability.starforged.overcharge.text": "Hold for two seconds to fire a piercing beam that calls lightning along its path.",
        "ability.starforged.heavens_judgement.title": "Right-click: Heaven's Judgement",
        "ability.starforged.heavens_judgement.text": "Raise the blade and call eight bolts down on the enemies around you.",
        "ability.starforged.skybreaker_core.title": "Use on the ground: Stormgate",
        "ability.starforged.skybreaker_core.text": "Opens a gateway to the Stormreach (Overworld, Stormreach, or the Pale Reach at High Tide).",
        # Entities
        "entity.starforged.static_wisp": "Static Wisp",
        "entity.starforged.shardwing": "Shardwing",
        "entity.starforged.stormbound": "Stormbound",
        "entity.starforged.thunderjaw": "Thunderjaw",
        "entity.starforged.zephyr_sprite": "Zephyr Sprite",
        "entity.starforged.zephyr_sprite.wait": "Your Zephyr Sprite hovers in place.",
        "entity.starforged.zephyr_sprite.follow": "Your Zephyr Sprite follows you.",
        "entity.starforged.zephyr_sprite.tired": "Your Zephyr Sprite is catching its breath (%s s).",
        "entity.starforged.zephyr_sprite.tailwind": "⚡ Tailwind! The wind is at your back.",
        "entity.starforged.storm_roc": "Storm Roc",
        "entity.starforged.thunderjaw_alpha": "Thunderjaw Alpha",
        "entity.starforged.thunderjaw_alpha.title": "Heart of the Supercell",
        "entity.starforged.thunderjaw_alpha.cage": "⚡ STORM CAGE - stay inside the ring!",
        "entity.starforged.veyr": "Veyr, the Tempest Regent",
        "entity.starforged.veyr.name_title": "VEYR",
        "entity.starforged.veyr.title": "The Tempest Regent, Sovereign of the Storm",
        "entity.starforged.veyr.tornado": "⚡ The winds rise into a wall of cyclones!",
        "entity.starforged.veyr.conduction": "CONDUCTION",
        "entity.starforged.veyr.conduction_sub": "Raise the Storm Conductors - turn his lightning back on him!",
        "entity.starforged.veyr.immune": "Veyr's storm shield turns the blow aside! Raise the conductors!",
        "entity.starforged.veyr.stunned": "⚡ The shield breaks! Veyr crashes down - STRIKE NOW!",
        "entity.starforged.veyr.shattered": "SHATTERED SKY",
        "entity.starforged.veyr.shattered_sub": "The summit breaks apart beneath you",
        "entity.starforged.veyr.last_thunder": "THE LAST THUNDER",
        "entity.starforged.veyr.last_thunder_sub": "Stay inside the Eyes of the Storm!",
        "entity.starforged.veyr.dying": "Veyr's armour comes apart in a storm of sparks...",
        "entity.starforged.veyr.defeated": "⚡ Veyr is vanquished. The storm answers to you now.",
        "entity.starforged.veyr.retreat": "Veyr dissolves back into the storm... the Sigil lies on the altar.",
        "entity.starforged.storm_conductor": "Storm Conductor",
        "entity.starforged.storm_conductor.raised": "⚡ The conductor rises - it will catch the next bolt!",
        "entity.starforged.storm_eye": "Eye of the Storm",
        "entity.starforged.cyclone": "Cyclone",
        "entity.starforged.tempest_javelin": "Tempest Javelin",
        "entity.starforged.stormhook": "Stormhook",
        "entity.starforged.storm_shard": "Storm Shard",
        "entity.starforged.arc_beam": "Arc",
        # Events & commands
        "event.starforged.stormreach.title": "⚡ THE STORMREACH ⚡",
        "event.starforged.stormreach.subtitle": "Where the storm never ends",
        "event.starforged.stormreach.gust": "⚡ A crosswind howls across the cliffs!",
        "event.starforged.supercell.title": "SUPERCELL",
        "event.starforged.supercell.subtitle": "The storm is gathering into something worse",
        "event.starforged.supercell.end": "The Supercell breaks apart.",
        "event.starforged.regent_summon.altar": "The Tempest Altar drinks the Sigil. The storm closes in...",
        "event.starforged.regent_summon.title": "THE STORM ANSWERS",
        "event.starforged.regent_summon.subtitle": "Its Regent descends",
        "commands.starforged.citadel": "⚡ A Tempest Citadel rises at %s, %s, %s",
        "commands.starforged.veyr": "⚡ The Tempest Altar crackles - Veyr is coming...",
        "commands.starforged.tempestkit": "⚡ You are Tempestforged. (Kit given)",
        "commands.starforged.supercell": "⚡ A Supercell forms overhead!",
        "commands.starforged.supercell.busy": "A Supercell is already raging.",
        # Biomes
        "biome.starforged.thunderhead_steppe": "Thunderhead Steppe",
        "biome.starforged.shardwind_cliffs": "Shardwind Cliffs",
        "biome.starforged.static_grove": "Static Grove",
        "biome.starforged.stormgrave": "Stormgrave",
        "biome.starforged.endless_eye": "The Endless Eye",
        # Death messages
        "death.attack.starforged.storm": "%1$s was struck down by the storm",
        "death.attack.starforged.storm.player": "%1$s was struck down by %2$s's storm",
        "death.attack.starforged.gale": "%1$s was torn apart by the wind",
        "death.attack.starforged.gale.player": "%1$s was torn apart by %2$s's wind",
    })
    for egg in EGGS:
        L[f"item.starforged.{egg}_spawn_egg"] = f"{L.get(f'entity.starforged.{egg}', egg.replace('_', ' ').title()).split(',')[0]} Spawn Egg"
    for event, (_file, subtitle) in SOUNDS.items():
        L[f"subtitles.starforged.{event}"] = subtitle
    for key, (title, desc) in ADVANCEMENTS_TEXT.items():
        L[f"advancements.starforged.tempest.{key}.title"] = title
        L[f"advancements.starforged.tempest.{key}.description"] = desc
    write(path, L)


# ----------------------------------------------------------------------------------------------------------------
# Recipes
# ----------------------------------------------------------------------------------------------------------------

def recipes():
    I = sid("aetherium_ingot")
    C = sid("charged_aetherium_ingot")
    rod = "minecraft:breeze_rod"
    cook("aetherium_ingot", sid("raw_aetherium"), I, 1.6)
    cook("aetherium_ingot_ore", sid("aetherium_ore"), I, 1.6)
    shaped("aetherium_block", ["###", "###", "###"], {"#": I}, sid("aetherium_block"), category="building")
    shapeless("aetherium_ingot_from_block", [sid("aetherium_block")], I, 9)
    shaped("charged_aetherium_block", ["###", "###", "###"], {"#": C}, sid("charged_aetherium_block"), category="building")
    shapeless("charged_aetherium_ingot_from_block", [sid("charged_aetherium_block")], C, 9)
    shaped("aetherium_sword", ["X", "X", "#"], {"X": I, "#": rod}, sid("aetherium_sword"), category="equipment")
    shaped("aetherium_pickaxe", ["XXX", " # ", " # "], {"X": I, "#": rod}, sid("aetherium_pickaxe"), category="equipment")
    shaped("aetherium_axe", ["XX", "X#", " #"], {"X": I, "#": rod}, sid("aetherium_axe"), category="equipment")
    shaped("aetherium_shovel", ["X", "#", "#"], {"X": I, "#": rod}, sid("aetherium_shovel"), category="equipment")
    shaped("aetherium_hoe", ["XX", " #", " #"], {"X": I, "#": rod}, sid("aetherium_hoe"), category="equipment")
    shaped("aetherium_helmet", ["XXX", "X X"], {"X": I}, sid("aetherium_helmet"), category="equipment")
    shaped("aetherium_chestplate", ["X X", "XXX", "XXX"], {"X": I}, sid("aetherium_chestplate"), category="equipment")
    shaped("aetherium_leggings", ["XXX", "X X", "X X"], {"X": I}, sid("aetherium_leggings"), category="equipment")
    shaped("aetherium_boots", ["X X", "X X"], {"X": I}, sid("aetherium_boots"), category="equipment")

    shaped("stormwood_planks", ["#"], {"#": sid("stormwood_log")}, sid("stormwood_planks"), 4, "building")
    shaped("tempest_bricks", ["##", "##"], {"#": sid("stormstone")}, sid("tempest_bricks"), 4, "building")
    shaped("chiseled_tempest_bricks", ["#", "#"], {"#": sid("tempest_brick_slab")}, sid("chiseled_tempest_bricks"), category="building")
    shaped("tempest_brick_stairs", ["#  ", "## ", "###"], {"#": sid("tempest_bricks")}, sid("tempest_brick_stairs"), 4, "building")
    shaped("tempest_brick_slab", ["###"], {"#": sid("tempest_bricks")}, sid("tempest_brick_slab"), 6, "building")
    shaped("aetherglass", ["GGG", "GIG", "GGG"], {"G": "minecraft:glass", "I": I}, sid("aetherglass"), 8, "building")
    shaped("storm_lantern", [" N ", "NMN", "NNN"], {"N": "minecraft:iron_nugget", "M": sid("static_mote")}, sid("storm_lantern"), category="building")
    shaped("tempest_wind_chime", [" C ", "SIS", "S S"], {"C": "minecraft:iron_chain", "S": sid("breeze_shard"), "I": I}, sid("wind_chime"), category="building")

    shaped("storm_dynamo", ["ILI", "CDC", "III"], {"I": I, "L": "minecraft:lightning_rod", "C": "minecraft:copper_ingot", "D": sid("charged_aether_dust")},
           sid("storm_dynamo"), category="redstone")
    shaped("aetherium_conductor", ["C", "I", "C"], {"C": "minecraft:copper_ingot", "I": I}, sid("aetherium_conductor"), 4, "redstone")
    shapeless("rotating_conductor", [sid("aetherium_conductor"), "minecraft:iron_ingot"], sid("rotating_conductor"))
    shaped("splitter_relay", ["CIC"], {"C": sid("aetherium_conductor"), "I": I}, sid("splitter_relay"), category="redstone")
    shaped("storm_relay", ["R", "C"], {"R": "minecraft:redstone", "C": sid("aetherium_conductor")}, sid("storm_relay"), category="redstone")
    shaped("overload_relay", ["SCS"], {"S": sid("charged_scrap"), "C": sid("aetherium_conductor")}, sid("overload_relay"), category="redstone")
    shaped("storm_capacitor", ["ICI", "CRC", "ICI"], {"I": I, "C": "minecraft:copper_ingot", "R": "minecraft:redstone_block"},
           sid("storm_capacitor"), category="redstone")
    shaped("lightning_beacon", ["LLL", "ITI", "III"], {"L": "minecraft:lightning_rod", "I": I, "T": sid("thunder_shard")},
           sid("lightning_beacon"), category="redstone")
    shaped("weather_engine", ["ICI", "FKF", "III"], {"I": I, "C": C, "F": sid("storm_feather"), "K": "minecraft:clock"},
           sid("weather_engine"), category="redstone")
    shaped("tempest_wind_vent", ["III", "BRB", "III"], {"I": I, "B": sid("breeze_shard"), "R": rod}, sid("wind_vent"), 2, "redstone")
    shapeless("gale_vent", [sid("wind_vent"), sid("breeze_shard")], sid("gale_vent"))
    shapeless("storm_lift", [sid("wind_vent"), sid("aetherium_conductor"), sid("charged_aether_dust")], sid("storm_lift"))
    shaped("tempest_shock_plate", ["M", "P"], {"M": sid("static_mote"), "P": "minecraft:heavy_weighted_pressure_plate"}, sid("shock_plate"), 2, "redstone")
    shaped("sky_anchor", ["IFI", "IPI", "III"], {"I": I, "F": sid("storm_feather"), "P": "minecraft:ender_pearl"}, sid("sky_anchor"), category="misc")
    shaped("tempest_altar", ["CHC", "AAA"], {"C": sid("chiseled_tempest_bricks"), "H": sid("alpha_conductor_horn"), "A": sid("aetherium_block")},
           sid("tempest_altar"), category="building")

    shaped("skybreaker_core", ["BMB", "LHL", "BMB"], {"B": "minecraft:breeze_rod", "M": sid("moonsilver_block"), "L": "minecraft:lightning_rod",
                                                     "H": sid("moon_heart")}, sid("skybreaker_core"), category="equipment")
    shaped("tempest_sigil", ["HCH", "CAC", "HCH"], {"H": sid("thunderjaw_horn"), "C": C, "A": sid("alpha_conductor_horn")}, sid("tempest_sigil"))
    shaped("skybreaker_halberd", [" CH", " RC", "R  "], {"C": C, "H": sid("thunderjaw_horn"), "R": rod}, sid("skybreaker_halberd"), category="equipment")
    shaped("tempest_javelin", ["  S", " C ", "F  "], {"S": sid("shardwing_crystal"), "C": C, "F": sid("storm_feather")}, sid("tempest_javelin"),
           category="equipment")
    shaped("gale_blades", ["B B", "I I", "R R"], {"B": sid("breeze_shard"), "I": I, "R": rod}, sid("gale_blades"), category="equipment")
    shaped("stormhook", ["  I", " CM", "C  "], {"I": I, "C": "minecraft:iron_chain", "M": sid("static_mote")}, sid("stormhook"), category="equipment")
    shaped("arc_cannon", ["PCL", "IDI", " I "], {"P": sid("stormbound_plate"), "C": C, "L": "minecraft:lightning_rod", "I": I,
                                                 "D": sid("charged_aether_dust")}, sid("arc_cannon"), category="equipment")


# ----------------------------------------------------------------------------------------------------------------
# Loot
# ----------------------------------------------------------------------------------------------------------------

def loot():
    for b in ("stormstone", "skyrock", "skysoil", "stormwood_log", "stormwood_planks", "gale_seed", "aetherium_block", "charged_aetherium_block",
              "tempest_bricks", "chiseled_tempest_bricks", "tempest_brick_stairs", "storm_lantern", "wind_chime", "storm_dynamo",
              "aetherium_conductor", "rotating_conductor", "splitter_relay", "storm_relay", "overload_relay", "storm_capacitor", "lightning_beacon",
              "weather_engine", "wind_vent", "gale_vent", "storm_lift", "shock_plate", "sky_anchor", "tempest_altar"):
        write(f"{D}/loot_table/blocks/{b}.json", {"type": "minecraft:block", "pools": [
            {"rolls": 1, "entries": [{"type": "minecraft:item", "name": sid(b)}], "conditions": [{"condition": "minecraft:survives_explosion"}]}],
            "random_sequence": sid(f"blocks/{b}")})
    write(f"{D}/loot_table/blocks/stormgrass.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [
        {"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "name": sid("stormgrass"), "conditions": [silk()]},
            {"type": "minecraft:item", "name": sid("skysoil")}]}]}]})
    write(f"{D}/loot_table/blocks/stormleaves.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [
        {"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "name": sid("stormleaves"), "conditions": [silk()]},
            {"type": "minecraft:item", "name": sid("static_mote"), "conditions": [{"condition": "minecraft:random_chance", "chance": 0.04}]},
            {"type": "minecraft:item", "name": "minecraft:stick", "conditions": [{"condition": "minecraft:random_chance", "chance": 0.05}]}]}]}]})
    write(f"{D}/loot_table/blocks/tempest_brick_slab.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [
        {"type": "minecraft:item", "name": sid("tempest_brick_slab"), "functions": [
            {"function": "minecraft:set_count", "count": 2, "conditions": [{"condition": "minecraft:block_state_property",
                                                                            "block": sid("tempest_brick_slab"), "properties": {"type": "double"}}]},
            {"function": "minecraft:explosion_decay"}]}]}]})
    write(f"{D}/loot_table/blocks/aetherglass.json", {"type": "minecraft:block", "pools": [
        {"rolls": 1, "conditions": [silk()], "entries": [{"type": "minecraft:item", "name": sid("aetherglass")}]}]})
    write(f"{D}/loot_table/blocks/aetherium_ore.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [
        {"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "name": sid("aetherium_ore"), "conditions": [silk()]},
            {"type": "minecraft:item", "name": sid("raw_aetherium"), "functions": [
                {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"},
                {"function": "minecraft:explosion_decay"}]}]}]}]})
    write(f"{D}/loot_table/blocks/thunder_crystal_cluster.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [
        {"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "name": sid("thunder_crystal_cluster"), "conditions": [silk()]},
            {"type": "minecraft:item", "name": sid("thunder_shard"), "functions": [
                {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 3}},
                {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"},
                {"function": "minecraft:explosion_decay"}]}]}]}]})

    def ent(name, pools):
        write(f"{D}/loot_table/entities/{name}.json", {"type": "minecraft:entity", "pools": pools, "random_sequence": sid(f"entities/{name}")})
    ent("static_wisp", [pool([item_entry(sid("static_mote"), 1, 1, 2, looting=True)]),
                        pool([item_entry(sid("charged_aether_dust"))], conditions=chance(0.3))])
    ent("shardwing", [pool([item_entry(sid("storm_feather"), 1, 1, 3, looting=True)]),
                      pool([item_entry(sid("shardwing_crystal"))], conditions=chance(0.4))])
    ent("stormbound", [pool([item_entry(sid("charged_scrap"), 1, 1, 3, looting=True)]),
                       pool([item_entry(sid("stormbound_plate"))], conditions=chance(0.35)),
                       pool([item_entry(sid("aetherium_ingot"))], conditions=chance(0.1))])
    ent("thunderjaw", [pool([item_entry(sid("stormhide"), 1, 1, 3, looting=True)]),
                       pool([item_entry(sid("thunderjaw_horn"))], conditions=chance(0.5)),
                       pool([item_entry("minecraft:beef", 1, 1, 3, looting=True)])])
    ent("zephyr_sprite", [pool([item_entry(sid("breeze_shard"), 1, 1, 2)])])
    ent("storm_roc", [pool([item_entry(sid("storm_feather"), 1, 2, 4, looting=True)]),
                      pool([item_entry(sid("stormheart"))], conditions=chance(0.04))])
    ent("thunderjaw_alpha", [pool([item_entry(sid("alpha_conductor_horn"))]), pool([item_entry(sid("thunderjaw_horn"), 1, 1, 3)]),
                             pool([item_entry(sid("stormhide"), 1, 3, 6)]), pool([item_entry(sid("charged_aetherium_ingot"), 1, 2, 4)]),
                             pool([item_entry(sid("thunder_shard"), 1, 3, 8)])])
    ent("veyr", [pool([item_entry(sid("stormheart"))]), pool([item_entry(sid("skycleaver"))]), pool([item_entry(sid("tempest_crown"))]),
                 pool([item_entry(sid("charged_aetherium_ingot"), 1, 10, 18)]), pool([item_entry(sid("thunder_shard"), 1, 8, 16)]),
                 pool([item_entry(sid("arc_cannon"))], conditions=chance(0.5)),
                 pool([item_entry("minecraft:enchanted_golden_apple", 1, 1, 2)])])

    def chest(name, pools):
        write(f"{D}/loot_table/chests/{name}.json", {"type": "minecraft:chest", "pools": pools, "random_sequence": sid(f"chests/{name}")})
    chest("tempest_citadel_common", [
        pool([item_entry(sid("thunder_shard"), 10, 2, 6), item_entry(sid("raw_aetherium"), 7, 1, 4), item_entry(sid("static_mote"), 7, 1, 4),
              item_entry(sid("charged_aether_dust"), 4, 1, 3), item_entry(sid("gale_seed"), 4, 1, 4), item_entry(sid("storm_feather"), 5, 1, 4),
              item_entry("minecraft:breeze_rod", 4, 1, 3), item_entry("minecraft:cooked_beef", 5, 2, 6), item_entry("minecraft:ender_pearl", 4, 1, 3),
              item_entry(sid("aetherium_ingot"), 4, 1, 3), item_entry(sid("charged_scrap"), 5, 1, 4)], rolls=(4, 8))])
    chest("tempest_citadel_vault", [
        pool([item_entry(sid("tempest_sigil"))], conditions=chance(0.65)),
        pool([item_entry(sid("skybreaker_halberd"), 3), item_entry(sid("tempest_javelin"), 3), item_entry(sid("gale_blades"), 3),
              item_entry(sid("stormhook"), 3), item_entry(sid("arc_cannon"), 2)], rolls=1),
        pool([item_entry(sid("aetherium_helmet"), 2), item_entry(sid("aetherium_chestplate"), 1), item_entry(sid("aetherium_leggings"), 1),
              item_entry(sid("aetherium_boots"), 2)], rolls=1),
        pool([item_entry(sid("charged_aetherium_ingot"), 5, 1, 3), item_entry(sid("aetherium_block"), 2, 1, 2), item_entry("minecraft:diamond", 4, 2, 5),
              item_entry("minecraft:enchanted_golden_apple", 1), item_entry("minecraft:book", 3, enchant=True),
              item_entry(sid("thunder_shard"), 5, 3, 8)], rolls=(4, 7))])


# ----------------------------------------------------------------------------------------------------------------
# Tags
# ----------------------------------------------------------------------------------------------------------------

def tags():
    pick = ["stormstone", "skyrock", "aetherium_ore", "aetherium_block", "charged_aetherium_block", "thunder_crystal_cluster", "tempest_bricks",
            "chiseled_tempest_bricks", "tempest_brick_stairs", "tempest_brick_slab", "storm_lantern", "wind_chime", "storm_dynamo",
            "aetherium_conductor", "rotating_conductor", "splitter_relay", "storm_relay", "overload_relay", "storm_capacitor", "lightning_beacon",
            "weather_engine", "wind_vent", "gale_vent", "storm_lift", "shock_plate", "sky_anchor", "tempest_altar"]
    tag("block", "minecraft", "mineable/pickaxe", [sid(b) for b in pick])
    tag("block", "minecraft", "mineable/shovel", [sid("skysoil"), sid("stormgrass")])
    tag("block", "minecraft", "mineable/axe", [sid("stormwood_log"), sid("stormwood_planks")])
    tag("block", "minecraft", "mineable/hoe", [sid("stormleaves")])
    tag("block", "minecraft", "needs_diamond_tool", [sid("aetherium_ore"), sid("aetherium_block"), sid("charged_aetherium_block"), sid("tempest_altar")])
    # Aetherium Ore needs Moonsilver (or better): every vanilla tier, Starmetal and Sunsteel are too weak.
    for tier in ("wooden", "stone", "copper", "iron", "gold", "diamond", "netherite"):
        tag("block", "minecraft", f"incorrect_for_{tier}_tool", [sid("aetherium_ore")])
    tag("block", "starforged", "incorrect_for_sunsteel_tool", [sid("aetherium_ore")])
    tag("block", "starforged", "incorrect_for_aetherium_tool", [])
    tag("block", "c", "ores", [sid("aetherium_ore")])
    tag("block", "minecraft", "beacon_base_blocks", [sid("aetherium_block"), sid("charged_aetherium_block")])
    tag("block", "minecraft", "stairs", [sid("tempest_brick_stairs")])
    tag("block", "minecraft", "slabs", [sid("tempest_brick_slab")])
    tag("block", "minecraft", "impermeable", [sid("aetherglass")])
    tag("block", "minecraft", "logs", [sid("stormwood_log")])
    tag("block", "minecraft", "logs_that_burn", [sid("stormwood_log")])
    tag("block", "minecraft", "planks", [sid("stormwood_planks")])
    tag("block", "minecraft", "leaves", [sid("stormleaves")])
    tag("block", "minecraft", "small_flowers", [sid("gale_seed")])
    tag("block", "minecraft", "dirt", [sid("skysoil"), sid("stormgrass")])
    tag("block", "minecraft", "supports_vegetation", [sid("skysoil"), sid("stormgrass")])
    tag("block", "starforged", "meteor_proof", [sid(b) for b in ("tempest_seal", "tempest_altar", "stormgate", "thunder_rune", "gale_rune",
                                                                 "cyclone_emitter", "citadel_core")])
    tag("block", "starforged", "lunar_moth_lights", [sid("storm_lantern")])

    tag("item", "starforged", "aetherium_repair_materials", [sid("aetherium_ingot")])
    tag("item", "starforged", "tempest_repair_materials", [sid("stormheart"), sid("charged_aetherium_block")])
    tag("item", "minecraft", "beacon_payment_items", [sid("aetherium_ingot")])
    tag("item", "minecraft", "swords", [sid("aetherium_sword"), sid("gale_blades"), sid("skybreaker_halberd"), sid("skycleaver")])
    tag("item", "minecraft", "pickaxes", [sid("aetherium_pickaxe")])
    tag("item", "minecraft", "axes", [sid("aetherium_axe")])
    tag("item", "minecraft", "shovels", [sid("aetherium_shovel")])
    tag("item", "minecraft", "hoes", [sid("aetherium_hoe")])
    tag("item", "minecraft", "head_armor", [sid("aetherium_helmet"), sid("tempest_crown")])
    tag("item", "minecraft", "chest_armor", [sid("aetherium_chestplate")])
    tag("item", "minecraft", "leg_armor", [sid("aetherium_leggings")])
    tag("item", "minecraft", "foot_armor", [sid("aetherium_boots")])
    tag("item", "minecraft", "enchantable/durability", [sid("tempest_javelin"), sid("stormhook"), sid("arc_cannon")])
    tag("item", "minecraft", "stairs", [sid("tempest_brick_stairs")])
    tag("item", "minecraft", "slabs", [sid("tempest_brick_slab")])
    tag("item", "minecraft", "logs", [sid("stormwood_log")])
    tag("item", "minecraft", "logs_that_burn", [sid("stormwood_log")])
    tag("item", "minecraft", "planks", [sid("stormwood_planks")])
    tag("item", "minecraft", "leaves", [sid("stormleaves")])
    tag("item", "minecraft", "small_flowers", [sid("gale_seed")])

    tag("entity_type", "starforged", "stormborn", [sid(e) for e in ("static_wisp", "shardwing", "stormbound", "thunderjaw", "thunderjaw_alpha", "veyr")])
    tag("entity_type", "starforged", "regent_allies", [sid("veyr"), sid("stormbound"), sid("static_wisp")])
    tag("entity_type", "minecraft", "fall_damage_immune", [sid(e) for e in ("static_wisp", "shardwing", "zephyr_sprite", "storm_roc", "veyr")])
    tag("worldgen/structure", "starforged", "tempest_citadels", [sid("tempest_citadel")])
    tag("worldgen/biome", "starforged", "has_tempest_citadel", [sid(b) for b in BIOMES])
    tag("worldgen/biome", "starforged", "is_stormreach", [sid(b) for b in BIOMES])
    tag("timeline", "starforged", "in_stormreach", ["#minecraft:universal"])


# ----------------------------------------------------------------------------------------------------------------
# The Stormreach
# ----------------------------------------------------------------------------------------------------------------

BIOMES = {
    # name: (temperature, humidity, erosion, sky, fog, music)
    "thunderhead_steppe": ((-1.0, 0.0), (-1.0, 0.0), (-1.0, 0.55), "#3a4460", "#5a6680", "minecraft:music.overworld.stony_peaks"),
    "shardwind_cliffs": ((0.0, 1.0), (-1.0, 0.0), (-1.0, 0.55), "#343c56", "#4e5a78", "minecraft:music.overworld.jagged_peaks"),
    "static_grove": ((-1.0, 0.0), (0.0, 1.0), (-1.0, 0.55), "#30405a", "#4a6078", "minecraft:music.overworld.old_growth_taiga"),
    "stormgrave": ((0.0, 1.0), (0.0, 1.0), (-1.0, 0.55), "#24283a", "#3a4058", "minecraft:music.overworld.deep_dark"),
    "endless_eye": ((-1.0, 1.0), (-1.0, 1.0), (0.55, 1.0), "#4a5a7a", "#6a7a98", "minecraft:music.overworld.frozen_peaks"),
}


def features():
    rock = [{"state": {"Name": sid("aetherium_ore")}, "target": {"predicate_type": "minecraft:block_match", "block": sid(b)}}
            for b in ("skyrock", "stormstone")]
    configured("ore_aetherium", "minecraft:ore", {"discard_chance_on_air_exposure": 0.0, "size": 6, "targets": rock})
    placed("ore_aetherium", sid("ore_aetherium"), [
        {"type": "minecraft:count", "count": 12}, {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": 20}, "max_inclusive": {"absolute": 220}}},
        {"type": "minecraft:biome"}])
    configured("stormstone_blob", "minecraft:ore", {"discard_chance_on_air_exposure": 0.0, "size": 33, "targets": [
        {"state": {"Name": sid("stormstone")}, "target": {"predicate_type": "minecraft:block_match", "block": sid("skyrock")}}]})
    placed("stormstone_blobs", sid("stormstone_blob"), [
        {"type": "minecraft:count", "count": 10}, {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": 20}, "max_inclusive": {"absolute": 220}}},
        {"type": "minecraft:biome"}])
    configured("thunder_crystal", "minecraft:simple_block", {"to_place": {"type": "minecraft:simple_state_provider", "state": {
        "Name": sid("thunder_crystal_cluster"), "Properties": {"facing": "up", "waterlogged": "false"}}}})
    placed("thunder_crystals", sid("thunder_crystal"), surface_patch(2, 6, [sid("skyrock"), sid("stormstone"), sid("stormgrass")]))
    placed("thunder_crystals_dense", sid("thunder_crystal"), surface_patch(5, 8, [sid("skyrock"), sid("stormstone")]))
    configured("gale_seed", "minecraft:simple_block", {"to_place": {"type": "minecraft:simple_state_provider", "state": {"Name": sid("gale_seed")}}})
    placed("gale_seed_patch", sid("gale_seed"), surface_patch(3, 8, [sid("stormgrass")]))
    configured("storm_boulder", "minecraft:block_blob", {"state": {"Name": sid("stormstone")}, "can_place_on": {
        "type": "minecraft:matching_blocks", "blocks": [sid("stormgrass"), sid("skyrock"), sid("stormstone")]}})
    placed("storm_boulders", sid("storm_boulder"), [{"type": "minecraft:rarity_filter", "chance": 2}, {"type": "minecraft:in_square"},
                                                     {"type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING"}, {"type": "minecraft:biome"}])

    # Stormwood: the cherry tree's shape, in storm colours.
    cherry = json.load(open(os.path.join(VANILLA, "worldgen/configured_feature/cherry.json")))
    text = json.dumps(cherry).replace("minecraft:cherry_log", sid("stormwood_log")).replace("minecraft:cherry_leaves", sid("stormleaves"))
    text = text.replace('"minecraft:dirt"', '"%s"' % sid("skysoil"))
    tree = json.loads(text)
    write(f"{D}/worldgen/configured_feature/stormwood_tree.json", tree)
    placed("stormwood_trees", sid("stormwood_tree"), [{"type": "minecraft:count", "count": 4}, {"type": "minecraft:in_square"},
                                                      {"type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING"}, {"type": "minecraft:biome"},
                                                      {"type": "minecraft:block_predicate_filter", "predicate": {
                                                          "type": "minecraft:matching_blocks", "blocks": sid("stormgrass"), "offset": [0, -1, 0]}}])
    placed("stormwood_trees_sparse", sid("stormwood_tree"), [{"type": "minecraft:rarity_filter", "chance": 3}, {"type": "minecraft:in_square"},
                                                             {"type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING"}, {"type": "minecraft:biome"},
                                                             {"type": "minecraft:block_predicate_filter", "predicate": {
                                                                 "type": "minecraft:matching_blocks", "blocks": sid("stormgrass"), "offset": [0, -1, 0]}}])


def biome_features(name):
    steps = [[] for _ in range(11)]
    steps[6] += [sid("stormstone_blobs"), sid("ore_aetherium")]
    if name == "thunderhead_steppe":
        steps[9] += [sid("gale_seed_patch"), sid("thunder_crystals"), sid("stormwood_trees_sparse")]
    elif name == "shardwind_cliffs":
        steps[4].append(sid("storm_boulders"))
        steps[9] += [sid("thunder_crystals_dense")]
    elif name == "static_grove":
        steps[9] += [sid("stormwood_trees"), sid("gale_seed_patch"), sid("thunder_crystals")]
    elif name == "stormgrave":
        steps[4].append(sid("storm_boulders"))
        steps[9] += [sid("thunder_crystals")]
    else:
        steps[9] += [sid("thunder_crystals"), sid("gale_seed_patch")]
    # Every biome must list shared features in the same order, or world generation fails with a "feature order cycle".
    order = [sid(n) for n in ("storm_boulders", "stormstone_blobs", "ore_aetherium", "stormwood_trees", "stormwood_trees_sparse", "gale_seed_patch",
                              "thunder_crystals", "thunder_crystals_dense")]
    return [sorted(step, key=order.index) for step in steps]


def biome_spawns(name):
    monster = {
        "thunderhead_steppe": [spawner(sid("thunderjaw"), 30, 1, 2), spawner(sid("static_wisp"), 30, 2, 4)],
        "shardwind_cliffs": [spawner(sid("shardwing"), 45, 1, 3), spawner(sid("static_wisp"), 15, 1, 3)],
        "static_grove": [spawner(sid("static_wisp"), 50, 2, 5), spawner(sid("thunderjaw"), 8, 1, 1)],
        "stormgrave": [spawner(sid("stormbound"), 40, 1, 2), spawner(sid("static_wisp"), 20, 1, 3), spawner(sid("thunderjaw"), 10, 1, 1)],
        "endless_eye": [spawner(sid("shardwing"), 25, 1, 2), spawner(sid("stormbound"), 10, 1, 1)],
    }[name]
    creature = {
        "thunderhead_steppe": [spawner(sid("storm_roc"), 8, 1, 2)],
        "shardwind_cliffs": [spawner(sid("storm_roc"), 10, 1, 2)],
        "static_grove": [spawner(sid("zephyr_sprite"), 12, 1, 3)],
        "stormgrave": [],
        "endless_eye": [spawner(sid("zephyr_sprite"), 10, 1, 2), spawner(sid("storm_roc"), 6, 1, 1)],
    }[name]
    return {"ambient": [], "axolotls": [], "creature": creature, "misc": [], "monster": monster, "underground_water_creature": [],
            "water_ambient": [], "water_creature": []}


def worldgen():
    features()
    for name, (temp, hum, ero, sky, fog, music) in BIOMES.items():
        attributes = {
            "minecraft:audio/background_music": {"default": {"max_delay": 24000, "min_delay": 12000, "sound": music}},
            "minecraft:visual/sky_color": sky,
            "minecraft:visual/fog_color": fog,
            "minecraft:visual/ambient_particles": [{"particle": {"type": sid("storm_wisp")}, "probability": 0.004 if name == "endless_eye" else 0.0015}],
        }
        if name in ("static_grove", "thunderhead_steppe"):
            attributes["minecraft:visual/ambient_particles"].append({"particle": {"type": sid("static_spark")}, "probability": 0.0006})
        write(f"{D}/worldgen/biome/{name}.json", {
            "attributes": attributes,
            "carvers": [],
            "downfall": 0.9,
            "effects": {"water_color": "#5a7aa8", "grass_color": "#5c8a9c", "foliage_color": "#4e7a8c"},
            "features": biome_features(name),
            "has_precipitation": True,
            "spawn_costs": {},
            "spawners": biome_spawns(name),
            "temperature": 0.8,
        })

    # Dimension type: a permanent storm under a low, bruised sky.
    write(f"{D}/dimension_type/stormreach.json", {
        "ambient_light": 0.05,
        "attributes": {
            "minecraft:audio/background_music": {"default": {"max_delay": 24000, "min_delay": 12000, "sound": "minecraft:music.overworld.stony_peaks"}},
            "minecraft:gameplay/can_start_raid": False,
            "minecraft:gameplay/monsters_burn": False,
            "minecraft:gameplay/sky_light_level": 15.0,
            "minecraft:visual/sky_color": "#3a4460",
            "minecraft:visual/fog_color": "#5a6680",
            "minecraft:visual/cloud_color": "#cc4a5068",
            "minecraft:visual/sun_angle": 345.0,
            "minecraft:visual/moon_angle": 120.0,
            "minecraft:visual/star_brightness": 0.0,
            "minecraft:visual/sky_light_color": "#b8c4e0",
            "minecraft:visual/ambient_light_color": "#1a2032",
            "minecraft:visual/water_fog_color": "#2a3a60",
        },
        "coordinate_scale": 1.0,
        "has_ceiling": False,
        "has_ender_dragon_fight": False,
        "has_fixed_time": True,
        "has_skylight": True,
        "height": 256,
        "infiniburn": "#minecraft:infiniburn_overworld",
        "logical_height": 256,
        "min_y": 0,
        "monster_spawn_block_light_limit": 0,
        "monster_spawn_light_level": {"type": "minecraft:uniform", "min_inclusive": 0, "max_inclusive": 7},
        "timelines": "#starforged:in_stormreach",
    })

    floating = json.load(open(os.path.join(VANILLA, "worldgen/noise_settings/floating_islands.json")))
    base = json.load(open(os.path.join(ROOT, "tools/overworld_noise_router.json")))
    router = dict(floating["noise_router"])
    # Biome climate comes from the overworld's noises; the shape stays the floating islands'.
    for key in ("temperature", "vegetation", "continents", "erosion", "ridges"):
        router[key] = base["noise_router"][key]

    def blk(name, props=None):
        s = {"Name": name}
        if props:
            s["Properties"] = props
        return {"type": "minecraft:block", "result_state": s}

    def noisy(low, high, threshold=0.0):
        return {"type": "minecraft:sequence", "sequence": [
            {"type": "minecraft:condition", "if_true": {"type": "minecraft:noise_threshold", "noise": "minecraft:surface",
                                                        "min_threshold": threshold, "max_threshold": 10.0}, "then_run": high}, low]}

    tops = {
        "thunderhead_steppe": blk(sid("stormgrass")),
        "shardwind_cliffs": noisy(blk(sid("stormgrass")), blk(sid("skyrock")), -0.3),
        "static_grove": blk(sid("stormgrass")),
        "stormgrave": noisy(blk(sid("skysoil")), blk(sid("stormstone")), -0.1),
        "endless_eye": noisy(blk(sid("stormgrass")), blk(sid("skyrock")), 0.2),
    }
    subs = {
        "thunderhead_steppe": blk(sid("skysoil")),
        "static_grove": blk(sid("skysoil")),
        "endless_eye": blk(sid("skysoil")),
        "shardwind_cliffs": noisy(blk(sid("skysoil")), blk(sid("skyrock")), -0.3),
    }
    rules = [{"type": "minecraft:condition", "if_true": {"type": "minecraft:biome", "biome_is": [sid(n)]}, "then_run": r} for n, r in tops.items()]
    sub = [{"type": "minecraft:condition", "if_true": {"type": "minecraft:biome", "biome_is": [sid(n)]}, "then_run": r} for n, r in subs.items()]
    surface_rule = {"type": "minecraft:sequence", "sequence": [
        {"type": "minecraft:condition", "if_true": {"type": "minecraft:stone_depth", "offset": 0, "add_surface_depth": False,
                                                    "secondary_depth_range": 0, "surface_type": "floor"},
         "then_run": {"type": "minecraft:sequence", "sequence": rules}},
        {"type": "minecraft:condition", "if_true": {"type": "minecraft:stone_depth", "offset": 0, "add_surface_depth": True,
                                                    "secondary_depth_range": 0, "surface_type": "floor"},
         "then_run": {"type": "minecraft:sequence", "sequence": sub}},
        {"type": "minecraft:condition", "if_true": {"type": "minecraft:stone_depth", "offset": 0, "add_surface_depth": False,
                                                    "secondary_depth_range": 0, "surface_type": "ceiling"},
         "then_run": blk(sid("stormstone"))},
    ]}
    write(f"{D}/worldgen/noise_settings/stormreach.json", {
        "aquifers_enabled": False,
        "default_block": {"Name": sid("skyrock")},
        "default_fluid": {"Name": "minecraft:air"},
        "disable_mob_generation": False,
        "legacy_random_source": False,
        "noise": floating["noise"],
        "noise_router": router,
        "ore_veins_enabled": False,
        "sea_level": -64,
        "spawn_target": base["spawn_target"],
        "surface_rule": surface_rule,
    })

    biomes = []
    for name, (temp, hum, ero, *_rest) in BIOMES.items():
        biomes.append({"biome": sid(name), "parameters": {
            "temperature": list(temp), "humidity": list(hum), "continentalness": [-1.0, 1.0], "erosion": list(ero),
            "weirdness": [-1.0, 1.0], "depth": 0.0, "offset": 0.0}})
    write(f"{D}/dimension/stormreach.json", {
        "type": sid("stormreach"),
        "generator": {"type": "minecraft:noise", "settings": sid("stormreach"),
                      "biome_source": {"type": "minecraft:multi_noise", "biomes": biomes}}})

    write(f"{D}/worldgen/structure/tempest_citadel.json", {
        "type": sid("tempest_citadel"), "biomes": "#starforged:has_tempest_citadel", "step": "surface_structures", "terrain_adaptation": "none",
        "spawn_overrides": {"monster": {"bounding_box": "piece", "spawns": [
            spawner(sid("stormbound"), 10, 1, 1), spawner(sid("static_wisp"), 20, 1, 2)]}}})
    write(f"{D}/worldgen/structure_set/tempest_citadels.json", {
        "placement": {"type": "minecraft:random_spread", "salt": 772341903, "separation": 8, "spacing": 24},
        "structures": [{"structure": sid("tempest_citadel"), "weight": 1}]})

    write(f"{D}/damage_type/storm.json", {"exhaustion": 0.1, "message_id": "starforged.storm", "scaling": "when_caused_by_living_non_player"})
    write(f"{D}/damage_type/gale.json", {"exhaustion": 0.0, "message_id": "starforged.gale", "scaling": "when_caused_by_living_non_player"})


# ----------------------------------------------------------------------------------------------------------------
# Advancements (their own "Tempestforged" tab)
# ----------------------------------------------------------------------------------------------------------------

ADVANCEMENTS_TEXT = {
    "root": ("Tempestforged", "Forge a Skybreaker Core around the Heart of the Moon"),
    "stormreach": ("Riders on the Storm", "Step through a Stormgate into the Stormreach"),
    "aetherium": ("Lighter Than Air", "Smelt an Aetherium Ingot"),
    "charged": ("Lightning in a Bottle", "Let the storm charge an Aetherium Ingot"),
    "citadel": ("Castle in the Clouds", "Find a Tempest Citadel"),
    "stormbound": ("Thunderstruck", "Defeat a Stormbound"),
    "sprite": ("Second Wind", "Tame a Zephyr Sprite"),
    "roc": ("Ride the Lightning", "Tame a Storm Roc"),
    "alpha": ("Eye of the Supercell", "Defeat the Thunderjaw Alpha"),
    "sigil": ("Calm Before the Storm", "Obtain a Tempest Sigil"),
    "veyr": ("The Last Thunder", "Defeat Veyr, the Tempest Regent"),
    "skycleaver": ("Heaven's Edge", "Claim the Skycleaver"),
}


def advancements():
    def a(key, parent, icon, criteria, frame="task", xp=None, background=None):
        display = {"title": {"translate": f"advancements.starforged.tempest.{key}.title"},
                   "description": {"translate": f"advancements.starforged.tempest.{key}.description"},
                   "icon": {"id": icon}, "frame": frame, "show_toast": True, "announce_to_chat": True, "hidden": False}
        if background:
            display["background"] = background
            display["show_toast"] = False
            display["announce_to_chat"] = False
        d = {"display": display, "criteria": criteria, "requirements": [list(criteria.keys())]}
        if parent:
            d["parent"] = sid(f"tempest/{parent}")
        if xp:
            d["rewards"] = {"experience": xp}
        write(f"{D}/advancement/tempest/{key}.json", d)

    def has(item):
        return {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": item}]}}

    def kill(entity):
        return {"trigger": "minecraft:player_killed_entity", "conditions": {"entity": [
            {"condition": "minecraft:entity_properties", "entity": "this", "predicate": {"minecraft:entity_type": entity}}]}}

    def tame(entity):
        return {"trigger": "minecraft:tame_animal", "conditions": {"entity": [
            {"condition": "minecraft:entity_properties", "entity": "this", "predicate": {"minecraft:entity_type": entity}}]}}

    a("root", None, sid("skybreaker_core"), {"core": has(sid("skybreaker_core")),
                                              "reach": {"trigger": "minecraft:changed_dimension", "conditions": {"to": sid("stormreach")}}},
      background="starforged:block/tempest_bricks")
    a("stormreach", "root", sid("stormgrass"), {"enter": {"trigger": "minecraft:changed_dimension", "conditions": {"to": sid("stormreach")}}})
    a("aetherium", "stormreach", sid("aetherium_ingot"), {"ingot": has(sid("aetherium_ingot"))})
    a("charged", "aetherium", sid("charged_aetherium_ingot"), {"ingot": has(sid("charged_aetherium_ingot"))})
    a("citadel", "stormreach", sid("chiseled_tempest_bricks"), {"enter": {"trigger": "minecraft:location", "conditions": {"player": [
        {"condition": "minecraft:entity_properties", "entity": "this", "predicate": {"minecraft:location": {"structures": sid("tempest_citadel")}}}]}}})
    a("stormbound", "citadel", sid("stormbound_plate"), {"kill": kill(sid("stormbound"))})
    a("sprite", "stormreach", sid("breeze_shard"), {"tame": tame(sid("zephyr_sprite"))})
    a("roc", "stormreach", sid("storm_feather"), {"tame": tame(sid("storm_roc"))}, frame="goal")
    a("alpha", "stormreach", sid("alpha_conductor_horn"), {"kill": kill(sid("thunderjaw_alpha"))}, frame="goal", xp=200)
    a("sigil", "citadel", sid("tempest_sigil"), {"sigil": has(sid("tempest_sigil"))})
    a("veyr", "sigil", sid("stormheart"), {"kill": kill(sid("veyr"))}, frame="challenge", xp=1500)
    a("skycleaver", "veyr", sid("skycleaver"), {"blade": has(sid("skycleaver"))})


def main():
    global VANILLA
    VANILLA = os.environ.get("VANILLA_DATA", "")
    if "--vanilla-data" in sys.argv:
        VANILLA = sys.argv[sys.argv.index("--vanilla-data") + 1]
    if not os.path.isdir(VANILLA):
        sys.exit("Pass the vanilla data folder (data/minecraft) with --vanilla-data")
    blocks()
    items()
    equipment_particles()
    sounds()
    lang()
    recipes()
    loot()
    tags()
    worldgen()
    advancements()
    print("tempestforged data written")


if __name__ == "__main__":
    main()
