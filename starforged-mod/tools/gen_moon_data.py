#!/usr/bin/env python3
"""
Moonforged data/asset generator. Run AFTER gen_data.py and gen_sun_data.py - it adds the Moonforged blocks, items,
equipment, particles, sounds, lang, recipes, loot, tags, damage types, advancements and the Pale Reach dimension
(dimension type, noise settings, five biomes, floating islands, the Tidal Orrery structure).
"""
import json
import os

from gen_sun_data import (A, D, RES, ROOT, chance, cook, elements, item_entry, model_item, pool, read, shaped, shapeless, sid,
                          silk, simple_state, spawner, surface_patch, tag, write)
from gen_sun_data import cube, placed, configured  # noqa: F401

# ----------------------------------------------------------------------------------------------------------------
# Block models & states
# ----------------------------------------------------------------------------------------------------------------

CUBE = ["moonstone", "regolith", "umbral_regolith", "silver_sand", "moonsilver_ore", "moonsilver_block", "lunar_bricks",
        "cracked_lunar_bricks", "chiseled_lunar_bricks", "moon_lantern"]
PHASES = ("new", "waxing", "full", "waning")


def blocks():
    for b in CUBE:
        cube(b)
        simple_state(b)
    cube("moon_seal")
    write(f"{A}/blockstates/moon_seal.json", {"variants": {"opening=false": {"model": sid("block/moon_seal")},
                                                           "opening=true": {"model": sid("block/moon_seal")}}})
    write(f"{A}/models/block/moon_glass.json", {"parent": "minecraft:block/cube_all",
                                                 "textures": {"all": {"sprite": sid("block/moon_glass"), "force_translucent": True}}})
    simple_state("moon_glass")

    write(f"{A}/models/block/selenite_cluster.json", {"parent": "minecraft:block/cross", "textures": {"cross": sid("block/selenite_cluster")}})
    rot = {"down": {"x": 180}, "east": {"x": 90, "y": 90}, "north": {"x": 90}, "south": {"x": 90, "y": 180}, "up": {}, "west": {"x": 90, "y": 270}}
    write(f"{A}/blockstates/selenite_cluster.json", {"variants": {
        f"facing={k}": dict({"model": sid("block/selenite_cluster")}, **v) for k, v in rot.items()}})
    write(f"{A}/models/block/moonpetal.json", {"parent": "minecraft:block/cross", "textures": {"cross": sid("block/moonpetal")}})
    simple_state("moonpetal")

    tex = sid("block/lunar_bricks")
    for suffix, parent in (("", "stairs"), ("_inner", "inner_stairs"), ("_outer", "outer_stairs")):
        write(f"{A}/models/block/lunar_brick_stairs{suffix}.json",
              {"parent": f"minecraft:block/{parent}", "textures": {"bottom": tex, "side": tex, "top": tex}})
    stairs = json.load(open(os.path.join(ROOT, "tools/stone_brick_stairs_blockstate.json")))
    write(f"{A}/blockstates/lunar_brick_stairs.json",
          json.loads(json.dumps(stairs).replace("minecraft:block/stone_brick_stairs", sid("block/lunar_brick_stairs"))))
    write(f"{A}/models/block/lunar_brick_slab.json", {"parent": "minecraft:block/slab", "textures": {"bottom": tex, "side": tex, "top": tex}})
    write(f"{A}/models/block/lunar_brick_slab_top.json", {"parent": "minecraft:block/slab_top", "textures": {"bottom": tex, "side": tex, "top": tex}})
    write(f"{A}/blockstates/lunar_brick_slab.json", {"variants": {
        "type=bottom": {"model": sid("block/lunar_brick_slab")},
        "type=double": {"model": sid("block/lunar_bricks")},
        "type=top": {"model": sid("block/lunar_brick_slab_top")}}})

    # Tidal clam: a low shell; open clams show their pearly lining (and pearl).
    shell, inner, pearl = sid("block/tidal_clam_shell"), sid("block/tidal_clam_inner"), sid("block/tidal_clam_pearl")
    write(f"{A}/models/block/tidal_clam.json", {
        "parent": "minecraft:block/block", "textures": {"particle": shell, "side": shell, "top": shell, "bottom": shell},
        "elements": elements([([1, 0, 1], [15, 6, 15])])})
    for variant, with_pearl in (("tidal_clam_open", True), ("tidal_clam_open_empty", False)):
        els = elements([([1, 0, 1], [15, 3, 15])], top="#inner")
        els += elements([([1, 3, 13], [15, 11, 15])])
        if with_pearl:
            els += elements([([6, 3, 6], [10, 7, 10])], side="#pearl", top="#pearl", bottom="#pearl")
        write(f"{A}/models/block/{variant}.json", {
            "parent": "minecraft:block/block",
            "textures": {"particle": shell, "side": shell, "top": shell, "bottom": shell, "inner": inner, "pearl": pearl},
            "elements": els})
    write(f"{A}/blockstates/tidal_clam.json", {"variants": {
        "open=false,pearl=false": {"model": sid("block/tidal_clam")},
        "open=false,pearl=true": {"model": sid("block/tidal_clam")},
        "open=true,pearl=false": {"model": sid("block/tidal_clam_open_empty")},
        "open=true,pearl=true": {"model": sid("block/tidal_clam_open")}}})

    side = sid("block/lunar_bricks")
    write(f"{A}/models/block/gravity_plate.json", {
        "parent": "minecraft:block/block", "textures": {"particle": side, "side": side, "top": sid("block/gravity_plate"), "bottom": side},
        "elements": elements([([0, 0, 0], [16, 2, 16])])})
    simple_state("gravity_plate")

    ring_variants, mural_variants = {}, {}
    for i, name in enumerate(PHASES):
        write(f"{A}/models/block/orrery_ring_{name}.json", {"parent": "minecraft:block/cube_bottom_top", "textures": {
            "top": sid(f"block/orrery_ring_{name}"), "side": sid("block/orrery_ring_side"), "bottom": sid("block/orrery_ring_side")}})
        ring_variants[f"phase={i}"] = {"model": sid(f"block/orrery_ring_{name}")}
        cube(f"lunar_mural_{name}")
        mural_variants[f"phase={i}"] = {"model": sid(f"block/lunar_mural_{name}")}
    write(f"{A}/blockstates/orrery_ring.json", {"variants": ring_variants})
    write(f"{A}/blockstates/lunar_mural.json", {"variants": mural_variants})

    write(f"{A}/models/block/orrery_console.json", {"parent": "minecraft:block/cube_bottom_top", "textures": {
        "top": sid("block/orrery_console_top"), "side": sid("block/orrery_console_side"), "bottom": sid("block/orrery_console_side")}})
    simple_state("orrery_console")

    side, top, bottom = sid("block/moon_altar_side"), sid("block/moon_altar_top"), sid("block/moon_altar_bottom")
    write(f"{A}/models/block/moon_altar.json", {
        "parent": "minecraft:block/block", "textures": {"particle": side, "side": side, "top": top, "bottom": bottom},
        "elements": elements([([0, 0, 0], [16, 3, 16]), ([2, 3, 2], [14, 10, 14]), ([0, 10, 0], [16, 14, 16])])})
    simple_state("moon_altar")

    g = sid("block/lunar_gateway")
    pool_ = elements([([0, 0, 0], [16, 12, 16])])
    for face in ("north", "south", "east", "west", "down"):
        pool_[0]["faces"][face]["cullface"] = face
    write(f"{A}/models/block/lunar_gateway.json", {
        "parent": "minecraft:block/block", "textures": {"particle": g, "side": g, "top": g, "bottom": g}, "elements": pool_})
    simple_state("lunar_gateway")


# ----------------------------------------------------------------------------------------------------------------
# Items
# ----------------------------------------------------------------------------------------------------------------

GENERATED = ["raw_moonsilver", "moonsilver_ingot", "selenite_shard", "lunar_dust", "lunar_pearl", "moon_heart", "lunar_key", "tidal_sigil",
             "moonsilver_helmet", "moonsilver_chestplate", "moonsilver_leggings", "moonsilver_boots", "crown_of_tides", "stasis_bell",
             "moonshot_bolt"]
HANDHELD = ["moonsilver_sword", "moonsilver_pickaxe", "moonsilver_axe", "moonsilver_shovel", "moonsilver_hoe", "crescent_glaive",
            "orrery_staff", "phase_daggers", "moonshot_crossbow", "tether_hook", "tidecaller_glaive"]
EGGS = ["regolith_skimmer", "lunar_moth", "selenite_sentinel", "umbral_lurker", "moonkit", "moonleaper", "pale_matriarch"]
BLOCK_ITEMS = CUBE + ["moon_glass", "lunar_brick_stairs", "lunar_brick_slab", "gravity_plate", "orrery_console", "moon_altar",
                      "moon_seal", "tidal_clam"]


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
    for name in BLOCK_ITEMS:
        model_item(name, sid(f"block/{name}"))
    model_item("orrery_ring", sid("block/orrery_ring_full"))
    model_item("lunar_mural", sid("block/lunar_mural_full"))
    for name in ("selenite_cluster", "moonpetal"):
        write(f"{A}/models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": sid(f"block/{name}")}})
        model_item(name, sid(f"item/{name}"))


def equipment_particles():
    write(f"{A}/equipment/moonsilver.json", {"layers": {"humanoid": [{"texture": sid("moonsilver")}],
                                                        "humanoid_leggings": [{"texture": sid("moonsilver")}]}})
    write(f"{A}/equipment/tides.json", {"layers": {"humanoid": [{"texture": sid("tides")}]}})
    write(f"{A}/particles/moon_dust.json", {"textures": [sid(f"mote_{i}") for i in range(4)]})
    write(f"{A}/particles/lunar_glimmer.json", {"textures": [sid(f"glint_{i}") for i in range(4)]})


SOUNDS = {
    "item.lunar_key.forge": ("moon_gateway_form", "A Lunar Gateway opens"),
    "block.lunar_gateway.travel": ("moon_gateway_travel", "Lunar Gateway shimmers"),
    "event.tide.high": ("moon_tide_high", "The tide rises"),
    "event.tide.low": ("moon_tide_low", "The tide ebbs"),
    "block.tidal_clam.open": ("moon_clam_open", "Tidal Clam opens"),
    "block.gravity_plate.launch": ("moon_gravity_plate", "Gravity Plate launches"),
    "block.orrery_ring.turn": ("moon_ring_turn", "Orrery ring turns"),
    "block.orrery.align": ("moon_orrery_align", "The orrery aligns"),
    "block.orrery.misalign": ("moon_orrery_fail", "The orrery inverts"),
    "block.moon_seal.open": ("moon_seal_open", "Moon Seal dissolves"),
    "block.moon_altar.activate": ("moon_altar_activate", "Moon Altar awakens"),
    "item.crescent_glaive.throw": ("moon_glaive_throw", "Crescent Glaive whirls"),
    "item.orrery_staff.fire": ("moon_staff_fire", "A moon is flung"),
    "item.orrery_staff.moonfall": ("moon_staff_moonfall", "Moonfall"),
    "item.phase_daggers.dash": ("moon_dagger_phase", "Phase dash"),
    "item.phase_daggers.detonate": ("moon_dagger_detonate", "Crescent cuts detonate"),
    "item.moonshot_crossbow.ricochet": ("moon_bolt_ricochet", "Moonshot ricochets"),
    "item.stasis_bell.ring": ("moon_bell_ring", "Stasis Bell tolls"),
    "item.tether_hook.fire": ("moon_hook_fire", "Tether Hook fires"),
    "item.tidecaller_glaive.wave": ("moon_tide_wave", "Silver Tide surges"),
    "item.lunar_ward.break": ("moon_ward_break", "Lunar Ward shatters"),
    "entity.regolith_skimmer.ambient": ("skimmer_ambient", "Regolith Skimmer burrows"),
    "entity.regolith_skimmer.erupt": ("skimmer_erupt", "Regolith Skimmer erupts"),
    "entity.regolith_skimmer.death": ("skimmer_death", "Regolith Skimmer dies"),
    "entity.lunar_moth.ambient": ("moth_ambient", "Lunar Moth flutters"),
    "entity.lunar_moth.eat": ("moth_eat", "Lunar Moth devours a light"),
    "entity.lunar_moth.death": ("moth_death", "Lunar Moth dies"),
    "entity.selenite_sentinel.ambient": ("sentinel_ambient", "Selenite Sentinel hums"),
    "entity.selenite_sentinel.reflect": ("sentinel_reflect", "Projectile reflected"),
    "entity.selenite_sentinel.death": ("sentinel_death", "Selenite Sentinel shatters"),
    "entity.umbral_lurker.ambient": ("lurker_ambient", "Something breathes in the dark"),
    "entity.umbral_lurker.lunge": ("lurker_lunge", "Umbral Lurker lunges"),
    "entity.umbral_lurker.death": ("lurker_death", "Umbral Lurker dissolves"),
    "entity.moonkit.ambient": ("moonkit_ambient", "Moonkit chirps"),
    "entity.moonkit.sniff": ("moonkit_sniff", "Moonkit sniffs"),
    "entity.moonleaper.ambient": ("leaper_ambient", "Moonleaper snorts"),
    "entity.moonleaper.leap": ("leaper_leap", "Moonleaper leaps"),
    "entity.pale_matriarch.roar": ("matriarch_roar", "The Pale Matriarch sings"),
    "entity.pale_matriarch.hurt": ("matriarch_hurt", "The Pale Matriarch cracks"),
    "entity.pale_matriarch.death": ("matriarch_death", "The Pale Matriarch falls"),
    "entity.pale_matriarch.wave": ("matriarch_wave", "Tidal wave"),
    "entity.pale_matriarch.lance": ("matriarch_lance", "Moonlight lance"),
    "entity.pale_matriarch.teleport": ("matriarch_teleport", "The Matriarch phases"),
    "entity.pale_matriarch.inversion": ("matriarch_inversion", "Gravity inverts"),
    "entity.pale_matriarch.moonfall": ("matriarch_moonfall", "The moon is falling"),
    "entity.lunar_anchor.shatter": ("anchor_shatter", "Lunar Anchor shatters"),
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
        "itemGroup.starforged.moonforged": "Moonforged",
        # Blocks
        "block.starforged.moonstone": "Moonstone",
        "block.starforged.regolith": "Regolith",
        "block.starforged.umbral_regolith": "Umbral Regolith",
        "block.starforged.silver_sand": "Silver Sand",
        "block.starforged.moonsilver_ore": "Moonsilver Ore",
        "block.starforged.selenite_cluster": "Selenite Cluster",
        "block.starforged.moonpetal": "Moonpetal",
        "block.starforged.tidal_clam": "Tidal Clam",
        "block.starforged.tidal_clam.closed": "The clam is shut tight. It only opens at Low Tide.",
        "block.starforged.tidal_clam.empty": "The clam is empty. A new pearl will grow in time.",
        "block.starforged.moonsilver_block": "Block of Moonsilver",
        "block.starforged.lunar_bricks": "Lunar Bricks",
        "block.starforged.cracked_lunar_bricks": "Cracked Lunar Bricks",
        "block.starforged.chiseled_lunar_bricks": "Chiseled Lunar Bricks",
        "block.starforged.lunar_brick_stairs": "Lunar Brick Stairs",
        "block.starforged.lunar_brick_slab": "Lunar Brick Slab",
        "block.starforged.moon_glass": "Moon Glass",
        "block.starforged.moon_lantern": "Moon Lantern",
        "block.starforged.gravity_plate": "Gravity Plate",
        "block.starforged.orrery_ring": "Orrery Ring",
        "block.starforged.orrery_ring.phase.new": "New Moon",
        "block.starforged.orrery_ring.phase.waxing": "Waxing Moon",
        "block.starforged.orrery_ring.phase.full": "Full Moon",
        "block.starforged.orrery_ring.phase.waning": "Waning Moon",
        "block.starforged.orrery_ring.turned": "☾ The ring turns to the %s.",
        "block.starforged.lunar_mural": "Lunar Mural",
        "block.starforged.lunar_mural.read": "☾ The mural shows the %s.",
        "block.starforged.orrery_console": "Orrery Console",
        "block.starforged.orrery_console.wait": "The orrery is still settling...",
        "block.starforged.orrery_console.aligned": "☾ Every ring matches its mural - the orrery aligns and the Moon Seal dissolves!",
        "block.starforged.orrery_console.inversion": "☾ Misaligned (%s of %s rings correct)! Gravity inverts and the sentinels wake!",
        "block.starforged.moon_seal": "Moon Seal",
        "block.starforged.moon_seal.hint": "☾ A veil of solid moonlight. Set each orrery ring to the phase shown on the mural behind it, then pull the console.",
        "block.starforged.moon_altar": "Moon Altar",
        "block.starforged.moon_altar.hint": "☾ The altar waits for a Tidal Sigil.",
        "block.starforged.moon_altar.busy": "The Pale Matriarch is already awake.",
        "block.starforged.lunar_gateway": "Lunar Gateway",
        "block.starforged.bed.pale_reach": "Night never ends in the Pale Reach - there is nothing to sleep through.",
        # Items
        "item.starforged.raw_moonsilver": "Raw Moonsilver",
        "item.starforged.moonsilver_ingot": "Moonsilver Ingot",
        "item.starforged.selenite_shard": "Selenite Shard",
        "item.starforged.selenite_shard.desc0": "Frozen moonlight. It rings when tapped.",
        "item.starforged.lunar_dust": "Lunar Dust",
        "item.starforged.lunar_dust.desc0": "Shed from the wings of things that eat the light.",
        "item.starforged.lunar_pearl": "Lunar Pearl",
        "item.starforged.lunar_pearl.desc0": "Grown by Tidal Clams. Moonkits adore them.",
        "item.starforged.moon_heart": "Heart of the Moon",
        "item.starforged.moon_heart.desc0": "The Pale Matriarch's core, still pulling at the tides.",
        "item.starforged.moon_heart.desc1": "Repairs the Matriarch's relics.",
        "item.starforged.lunar_key": "Lunar Key",
        "item.starforged.lunar_key.desc0": "Forged around the Heart of the Sun, cooled in moonlight,",
        "item.starforged.lunar_key.desc1": "it remembers the way to the Pale Reach.",
        "item.starforged.lunar_key.opened": "☾ A Lunar Gateway shimmers open! Step in to cross to the Pale Reach.",
        "item.starforged.lunar_key.wrong_world": "The key only answers in the Overworld or the Pale Reach.",
        "item.starforged.tidal_sigil": "Tidal Sigil",
        "item.starforged.tidal_sigil.desc0": "The seal of the Tidal Orrery.",
        "item.starforged.tidal_sigil.desc1": "Offer it on the Moon Altar atop a Tidal Orrery",
        "item.starforged.tidal_sigil.desc2": "to wake the Pale Matriarch.",
        "item.starforged.moonsilver_sword": "Moonsilver Sword",
        "item.starforged.moonsilver_pickaxe": "Moonsilver Pickaxe",
        "item.starforged.moonsilver_axe": "Moonsilver Axe",
        "item.starforged.moonsilver_shovel": "Moonsilver Shovel",
        "item.starforged.moonsilver_hoe": "Moonsilver Hoe",
        "item.starforged.moonsilver_helmet": "Moonsilver Helmet",
        "item.starforged.moonsilver_chestplate": "Moonsilver Chestplate",
        "item.starforged.moonsilver_leggings": "Moonsilver Leggings",
        "item.starforged.moonsilver_boots": "Moonsilver Boots",
        "item.starforged.crown_of_tides": "Crown of Tides",
        "item.starforged.crescent_glaive": "Crescent Glaive",
        "item.starforged.orrery_staff": "Orrery Staff",
        "item.starforged.orrery_staff.moons": "Moons: %s / %s",
        "item.starforged.phase_daggers": "Phase Daggers",
        "item.starforged.phase_daggers.charges": "Phase charges: %s / %s",
        "item.starforged.moonshot_crossbow": "Moonshot Crossbow",
        "item.starforged.moonshot_bolt": "Moonshot Bolt",
        "item.starforged.stasis_bell": "Stasis Bell",
        "item.starforged.tether_hook": "Tether Hook",
        "item.starforged.tidecaller_glaive": "Tidecaller Glaive",
        # Abilities
        "ability.starforged.moonsilver_set.title": "Full Set: Lunar Ward",
        "ability.starforged.moonsilver_set.text": "Absorbs one heavy hit every 30 s. Sneak + Jump toggles light gravity anywhere. Speed and Haste at High Tide.",
        "ability.starforged.lunar_ward.absorbed": "☾ Your Lunar Ward absorbs the blow!",
        "ability.starforged.gravity_shift.on": "☾ Gravity Shift: light as moondust",
        "ability.starforged.gravity_shift.off": "☾ Gravity Shift: grounded",
        "ability.starforged.crown_of_tides.title": "Tidesight",
        "ability.starforged.crown_of_tides.text": "Water Breathing and light gravity. Creatures lurking in the dark nearby are outlined.",
        "ability.starforged.crescent_orbit.title": "Right-click: Crescent Orbit",
        "ability.starforged.crescent_orbit.text": "Hurl the glaive in a wide double orbit around you, cutting everything it passes.",
        "ability.starforged.gravity_nail.title": "Sneak + Right-click: Gravity Nail",
        "ability.starforged.gravity_nail.text": "Plant the glaive and pin nearby enemies to the ground.",
        "ability.starforged.orrery_staff.title": "Right-click: Fling a Moon",
        "ability.starforged.orrery_staff.text": "Three moons orbit you while held. Fling one at your target; they slowly re-form.",
        "ability.starforged.moonfall.title": "Sneak + Right-click: Moonfall",
        "ability.starforged.moonfall.text": "Merge every moon into one that crashes down where you aim. More moons, bigger impact.",
        "ability.starforged.phase_daggers.title": "Right-click: Phase",
        "ability.starforged.phase_daggers.text": "Dash through enemies, leaving crescent cuts that detonate a moment later. Three charges.",
        "ability.starforged.moonshot.title": "Moonshot",
        "ability.starforged.moonshot.text": "Needs no bolts. Shots of hard moonlight ricochet off walls and enemies up to five times.",
        "ability.starforged.gravity_beacon.title": "Sneak + Right-click: Gravity Beacon",
        "ability.starforged.gravity_beacon.text": "Fires a beacon that sticks where it lands and drags enemies together.",
        "ability.starforged.stasis_bell.title": "Right-click: Stasis",
        "ability.starforged.stasis_bell.text": "Time stands still around you for four seconds: creatures and projectiles freeze in place.",
        "ability.starforged.tether_hook.title": "Right-click: Grapple",
        "ability.starforged.tether_hook.text": "Hook a creature to reel it in, or a block to swing from it. Use again or sneak to let go.",
        "ability.starforged.silver_tide.title": "Right-click: Silver Tide",
        "ability.starforged.silver_tide.text": "Send a rolling wave of moonlit water crashing forward.",
        "ability.starforged.reverse_gravity.title": "Sneak + Right-click: Reverse Gravity",
        "ability.starforged.reverse_gravity.text": "Enemies around you float helplessly upward, then slam back down.",
        "ability.starforged.lunar_key.title": "Use on the ground: Lunar Gateway",
        "ability.starforged.lunar_key.text": "Raises a gateway to the Pale Reach (Overworld or Pale Reach only).",
        # Entities
        "entity.starforged.regolith_skimmer": "Regolith Skimmer",
        "entity.starforged.lunar_moth": "Lunar Moth",
        "entity.starforged.selenite_sentinel": "Selenite Sentinel",
        "entity.starforged.umbral_lurker": "Umbral Lurker",
        "entity.starforged.moonkit": "Moonkit",
        "entity.starforged.moonkit.wait": "Your Moonkit sits and waits.",
        "entity.starforged.moonkit.follow": "Your Moonkit follows you.",
        "entity.starforged.moonleaper": "Moonleaper",
        "entity.starforged.pale_matriarch": "The Pale Matriarch",
        "entity.starforged.pale_matriarch.title": "Mother of Tides",
        "entity.starforged.pale_matriarch.inversion": "INVERSION",
        "entity.starforged.pale_matriarch.inversion_sub": "Shatter the Lunar Anchors!",
        "entity.starforged.pale_matriarch.immune": "The Pale Matriarch is held aloft by her Lunar Anchors!",
        "entity.starforged.pale_matriarch.stunned": "☾ The anchors are broken! The Matriarch falls - STRIKE NOW!",
        "entity.starforged.pale_matriarch.waning": "☾ The Matriarch rises again, her light waning...",
        "entity.starforged.pale_matriarch.new_moon": "NEW MOON",
        "entity.starforged.pale_matriarch.new_moon_sub": "Only one of them is real",
        "entity.starforged.pale_matriarch.moonfall": "MOONFALL",
        "entity.starforged.pale_matriarch.moonfall_sub": "Take cover beneath the anchors' ruins!",
        "entity.starforged.pale_matriarch.moonfall_countdown": "☾ The moon strikes in %s seconds!",
        "entity.starforged.pale_matriarch.moonfall_impact": "☾ The moon crashes into the summit!",
        "entity.starforged.pale_matriarch.moonfall_shattered": "☾ The falling moon shatters!",
        "entity.starforged.pale_matriarch.dying": "The Matriarch's light flickers and dims...",
        "entity.starforged.pale_matriarch.defeated": "☾ The Pale Matriarch is vanquished. The tides answer to you now.",
        "entity.starforged.pale_matriarch.retreat": "The Matriarch sinks back beneath the tide... the Sigil lies on the altar.",
        "entity.starforged.matriarch_echo": "Echo of the Matriarch",
        "entity.starforged.lunar_anchor": "Lunar Anchor",
        "entity.starforged.falling_moon": "Falling Moon",
        "entity.starforged.moonlet": "Moonlet",
        "entity.starforged.crescent_glaive": "Crescent Glaive",
        "entity.starforged.moonshot_bolt": "Moonshot Bolt",
        "entity.starforged.tether_hook": "Tether Hook",
        "entity.starforged.tide_wave": "Silver Tide",
        # Events & commands
        "event.starforged.pale_reach.title": "☾ THE PALE REACH ☾",
        "event.starforged.pale_reach.subtitle": "Where the tides answer to the moon",
        "event.starforged.tide.high": "HIGH TIDE",
        "event.starforged.tide.high_sub": "The seas swell and the Reach grows restless",
        "event.starforged.tide.low": "LOW TIDE",
        "event.starforged.tide.low_sub": "The waters recede - the clams open",
        "event.starforged.tide.status_high": "☾ High Tide - it turns in %s min",
        "event.starforged.tide.status_low": "☾ Low Tide - it turns in %s min",
        "event.starforged.moon_summon.altar": "The Moon Altar drinks the Sigil. The sea begins to rise...",
        "event.starforged.moon_summon.title": "THE TIDE TURNS",
        "event.starforged.moon_summon.subtitle": "Its mother rises",
        "commands.starforged.orrery": "☾ A Tidal Orrery rises at %s, %s, %s",
        "commands.starforged.matriarch": "☾ The Moon Altar glows - the Matriarch is waking...",
        "commands.starforged.moonkit": "☾ You are Moonforged. (Kit given)",
        "commands.starforged.tide": "Not in the Pale Reach - there are no tides here.",
        # Biomes
        "biome.starforged.regolith_flats": "Regolith Flats",
        "biome.starforged.silver_seas": "Silver Seas",
        "biome.starforged.selenite_hollows": "Selenite Hollows",
        "biome.starforged.far_side": "The Far Side",
        "biome.starforged.shattered_rim": "Shattered Rim",
        # Death messages
        "death.attack.starforged.moonlight": "%1$s was pierced by moonlight",
        "death.attack.starforged.moonlight.player": "%1$s was pierced by %2$s's moonlight",
        "death.attack.starforged.gravity": "%1$s was crushed by gravity",
        "death.attack.starforged.gravity.player": "%1$s was crushed by %2$s's gravity",
    })
    for mob in EGGS:
        L[f"item.starforged.{mob}_spawn_egg"] = L[f"entity.starforged.{mob}"].replace("The ", "") + " Spawn Egg"
    for event, (_, subtitle) in SOUNDS.items():
        L[f"subtitles.starforged.{event}"] = subtitle
    for key, (title, desc) in ADVANCEMENTS_TEXT.items():
        L[f"advancements.starforged.moon.{key}.title"] = title
        L[f"advancements.starforged.moon.{key}.description"] = desc
    write(path, L)


# ----------------------------------------------------------------------------------------------------------------
# Recipes
# ----------------------------------------------------------------------------------------------------------------

def recipes():
    I = sid("moonsilver_ingot")
    rod = "minecraft:breeze_rod"
    cook("moonsilver_ingot", sid("raw_moonsilver"), I, 1.4)
    cook("moonsilver_ingot_ore", sid("moonsilver_ore"), I, 1.4)
    shaped("moonsilver_block", ["###", "###", "###"], {"#": I}, sid("moonsilver_block"), category="building")
    shapeless("moonsilver_ingot_from_block", [sid("moonsilver_block")], I, 9)
    shaped("moonsilver_sword", ["X", "X", "#"], {"X": I, "#": rod}, sid("moonsilver_sword"), category="equipment")
    shaped("moonsilver_pickaxe", ["XXX", " # ", " # "], {"X": I, "#": rod}, sid("moonsilver_pickaxe"), category="equipment")
    shaped("moonsilver_axe", ["XX", "X#", " #"], {"X": I, "#": rod}, sid("moonsilver_axe"), category="equipment")
    shaped("moonsilver_shovel", ["X", "#", "#"], {"X": I, "#": rod}, sid("moonsilver_shovel"), category="equipment")
    shaped("moonsilver_hoe", ["XX", " #", " #"], {"X": I, "#": rod}, sid("moonsilver_hoe"), category="equipment")
    shaped("moonsilver_helmet", ["XXX", "X X"], {"X": I}, sid("moonsilver_helmet"), category="equipment")
    shaped("moonsilver_chestplate", ["X X", "XXX", "XXX"], {"X": I}, sid("moonsilver_chestplate"), category="equipment")
    shaped("moonsilver_leggings", ["XXX", "X X", "X X"], {"X": I}, sid("moonsilver_leggings"), category="equipment")
    shaped("moonsilver_boots", ["X X", "X X"], {"X": I}, sid("moonsilver_boots"), category="equipment")

    shaped("lunar_bricks", ["##", "##"], {"#": sid("moonstone")}, sid("lunar_bricks"), 4, "building")
    cook("cracked_lunar_bricks", sid("lunar_bricks"), sid("cracked_lunar_bricks"), 0.1, ("smelting",))
    shaped("chiseled_lunar_bricks", ["#", "#"], {"#": sid("lunar_brick_slab")}, sid("chiseled_lunar_bricks"), category="building")
    shaped("lunar_brick_stairs", ["#  ", "## ", "###"], {"#": sid("lunar_bricks")}, sid("lunar_brick_stairs"), 4, "building")
    shaped("lunar_brick_slab", ["###"], {"#": sid("lunar_bricks")}, sid("lunar_brick_slab"), 6, "building")
    shaped("moon_glass", ["GGG", "GSG", "GGG"], {"G": "minecraft:glass", "S": sid("selenite_shard")}, sid("moon_glass"), 8, "building")
    shaped("moon_lantern", [" S ", "SGS", " S "], {"S": sid("selenite_shard"), "G": "minecraft:glowstone"}, sid("moon_lantern"), 2, "building")
    shaped("gravity_plate", [" D ", "BSB"], {"D": sid("lunar_dust"), "B": sid("lunar_bricks"), "S": sid("selenite_shard")},
           sid("gravity_plate"), 2, "redstone")
    shaped("moon_altar", [" P ", "CMC", "MMM"], {"P": sid("lunar_pearl"), "C": sid("chiseled_lunar_bricks"), "M": sid("moonsilver_block")},
           sid("moon_altar"), category="building")

    shaped("lunar_key", ["APA", "SHS", "APA"], {"A": "minecraft:amethyst_shard", "P": "minecraft:ender_pearl", "S": sid("sunsteel_block"),
                                                 "H": sid("sun_heart")}, sid("lunar_key"), category="equipment")
    shaped("tidal_sigil", ["PDP", "DBD", "PDP"], {"P": sid("lunar_pearl"), "D": sid("lunar_dust"), "B": sid("moonsilver_block")},
           sid("tidal_sigil"))
    shaped("crescent_glaive", [" SI", " RS", "R  "], {"S": sid("selenite_shard"), "I": I, "R": rod}, sid("crescent_glaive"), category="equipment")
    shaped("orrery_staff", [" PS", " IP", "I  "], {"P": sid("lunar_pearl"), "S": sid("selenite_shard"), "I": I}, sid("orrery_staff"),
           category="equipment")
    shaped("phase_daggers", ["I I", " D ", "R R"], {"I": I, "D": sid("lunar_dust"), "R": rod}, sid("phase_daggers"), category="equipment")
    shaped("moonshot_crossbow", ["ICI", "SPS", " I "], {"I": I, "C": "minecraft:crossbow", "S": "minecraft:string", "P": sid("lunar_pearl")},
           sid("moonshot_crossbow"), category="equipment")
    shaped("stasis_bell", [" I ", "IPI", "IDI"], {"I": I, "P": sid("lunar_pearl"), "D": sid("lunar_dust")}, sid("stasis_bell"),
           category="equipment")
    shaped("tether_hook", ["  I", " CS", "C  "], {"I": I, "C": "minecraft:iron_chain", "S": sid("selenite_shard")}, sid("tether_hook"),
           category="equipment")


# ----------------------------------------------------------------------------------------------------------------
# Loot
# ----------------------------------------------------------------------------------------------------------------

def loot():
    for b in ("moonstone", "regolith", "umbral_regolith", "silver_sand", "moonsilver_block", "lunar_bricks", "cracked_lunar_bricks",
              "chiseled_lunar_bricks", "lunar_brick_stairs", "moon_lantern", "gravity_plate", "moon_altar", "moonpetal", "tidal_clam"):
        write(f"{D}/loot_table/blocks/{b}.json", {"type": "minecraft:block", "pools": [
            {"rolls": 1, "entries": [{"type": "minecraft:item", "name": sid(b)}], "conditions": [{"condition": "minecraft:survives_explosion"}]}],
            "random_sequence": sid(f"blocks/{b}")})
    write(f"{D}/loot_table/blocks/lunar_brick_slab.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [
        {"type": "minecraft:item", "name": sid("lunar_brick_slab"), "functions": [
            {"function": "minecraft:set_count", "count": 2, "conditions": [{"condition": "minecraft:block_state_property",
                                                                            "block": sid("lunar_brick_slab"), "properties": {"type": "double"}}]},
            {"function": "minecraft:explosion_decay"}]}]}]})
    write(f"{D}/loot_table/blocks/moon_glass.json", {"type": "minecraft:block", "pools": [
        {"rolls": 1, "conditions": [silk()], "entries": [{"type": "minecraft:item", "name": sid("moon_glass")}]}]})
    write(f"{D}/loot_table/blocks/moonsilver_ore.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [
        {"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "name": sid("moonsilver_ore"), "conditions": [silk()]},
            {"type": "minecraft:item", "name": sid("raw_moonsilver"), "functions": [
                {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"},
                {"function": "minecraft:explosion_decay"}]}]}]}]})
    write(f"{D}/loot_table/blocks/selenite_cluster.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [
        {"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "name": sid("selenite_cluster"), "conditions": [silk()]},
            {"type": "minecraft:item", "name": sid("selenite_shard"), "functions": [
                {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 2, "max": 4}},
                {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"},
                {"function": "minecraft:explosion_decay"}]}]}]}]})

    def ent(name, pools):
        write(f"{D}/loot_table/entities/{name}.json", {"type": "minecraft:entity", "pools": pools, "random_sequence": sid(f"entities/{name}")})
    ent("regolith_skimmer", [pool([item_entry(sid("regolith"), 1, 1, 3)]),
                             pool([item_entry(sid("raw_moonsilver"), 1, 1, 2, looting=True)], conditions=chance(0.35)),
                             pool([item_entry(sid("lunar_dust"))], conditions=chance(0.3))])
    ent("lunar_moth", [pool([item_entry(sid("lunar_dust"), 1, 1, 2, looting=True)]),
                       pool([item_entry("minecraft:glowstone_dust", 1, 0, 2)])])
    ent("selenite_sentinel", [pool([item_entry(sid("selenite_shard"), 1, 2, 4, looting=True)]),
                              pool([item_entry(sid("moonsilver_ingot"), 1, 1, 2)], conditions=chance(0.35)),
                              pool([item_entry(sid("lunar_pearl"))], conditions=chance(0.06))])
    ent("umbral_lurker", [pool([item_entry(sid("lunar_dust"), 1, 1, 3, looting=True)]),
                          pool([item_entry("minecraft:ender_pearl")], conditions=chance(0.35)),
                          pool([item_entry(sid("lunar_pearl"))], conditions=chance(0.15))])
    ent("moonkit", [pool([item_entry(sid("lunar_dust"), 1, 0, 1)])])
    ent("moonleaper", [pool([item_entry("minecraft:rabbit_hide", 1, 0, 2, looting=True)]),
                       pool([item_entry(sid("lunar_dust"))], conditions=chance(0.3))])
    ent("pale_matriarch", [pool([item_entry(sid("moon_heart"))]), pool([item_entry(sid("tidecaller_glaive"))]),
                           pool([item_entry(sid("crown_of_tides"))]), pool([item_entry(sid("moonsilver_ingot"), 1, 10, 18)]),
                           pool([item_entry(sid("lunar_pearl"), 1, 4, 8)]), pool([item_entry(sid("lunar_dust"), 1, 8, 16)]),
                           pool([item_entry(sid("stasis_bell"))], conditions=chance(0.5)),
                           pool([item_entry("minecraft:enchanted_golden_apple", 1, 1, 2)])])

    def chest(name, pools):
        write(f"{D}/loot_table/chests/{name}.json", {"type": "minecraft:chest", "pools": pools, "random_sequence": sid(f"chests/{name}")})
    chest("tidal_orrery_common", [
        pool([item_entry(sid("selenite_shard"), 10, 2, 6), item_entry(sid("raw_moonsilver"), 7, 1, 4), item_entry(sid("lunar_dust"), 7, 1, 4),
              item_entry(sid("lunar_pearl"), 3, 1, 2), item_entry(sid("moonpetal"), 4, 1, 4), item_entry("minecraft:prismarine_crystals", 4, 2, 6),
              item_entry("minecraft:breeze_rod", 4, 1, 3), item_entry("minecraft:cooked_cod", 5, 2, 6), item_entry("minecraft:ender_pearl", 4, 1, 3),
              item_entry(sid("moonsilver_ingot"), 4, 1, 3)], rolls=(4, 8))])
    chest("tidal_orrery_vault", [
        pool([item_entry(sid("tidal_sigil"))], conditions=chance(0.65)),
        pool([item_entry(sid("orrery_staff"), 3), item_entry(sid("moonshot_crossbow"), 3), item_entry(sid("phase_daggers"), 3),
              item_entry(sid("crescent_glaive"), 3), item_entry(sid("tether_hook"), 3), item_entry(sid("stasis_bell"), 1)], rolls=1),
        pool([item_entry(sid("moonsilver_helmet"), 2), item_entry(sid("moonsilver_chestplate"), 1), item_entry(sid("moonsilver_leggings"), 1),
              item_entry(sid("moonsilver_boots"), 2)], rolls=1),
        pool([item_entry(sid("lunar_pearl"), 5, 1, 3), item_entry(sid("moonsilver_block"), 2, 1, 2), item_entry("minecraft:diamond", 4, 2, 5),
              item_entry("minecraft:enchanted_golden_apple", 1), item_entry("minecraft:book", 3, enchant=True),
              item_entry(sid("lunar_dust"), 5, 3, 8)], rolls=(4, 7))])


# ----------------------------------------------------------------------------------------------------------------
# Tags
# ----------------------------------------------------------------------------------------------------------------

def tags():
    pick = ["moonstone", "moonsilver_ore", "selenite_cluster", "moonsilver_block", "lunar_bricks", "cracked_lunar_bricks",
            "chiseled_lunar_bricks", "lunar_brick_stairs", "lunar_brick_slab", "moon_lantern", "gravity_plate", "moon_altar", "tidal_clam"]
    tag("block", "minecraft", "mineable/pickaxe", [sid(b) for b in pick])
    tag("block", "minecraft", "mineable/shovel", [sid("regolith"), sid("umbral_regolith"), sid("silver_sand")])
    tag("block", "minecraft", "needs_diamond_tool", [sid("moonsilver_ore"), sid("moonsilver_block"), sid("moon_altar")])
    # Moonsilver Ore needs Sunsteel (or better): every vanilla tier and the Overworld legendaries are too weak.
    for tier in ("wooden", "stone", "copper", "iron", "gold", "diamond", "netherite"):
        tag("block", "minecraft", f"incorrect_for_{tier}_tool", [sid("moonsilver_ore")])
    tag("block", "starforged", "incorrect_for_sunsteel_tool", [])
    tag("block", "starforged", "incorrect_for_moonsilver_tool", [])
    tag("block", "c", "ores", [sid("moonsilver_ore")])
    tag("block", "minecraft", "beacon_base_blocks", [sid("moonsilver_block")])
    tag("block", "minecraft", "stairs", [sid("lunar_brick_stairs")])
    tag("block", "minecraft", "slabs", [sid("lunar_brick_slab")])
    tag("block", "minecraft", "impermeable", [sid("moon_glass")])
    tag("block", "minecraft", "supports_vegetation", [sid("regolith"), sid("silver_sand"), sid("umbral_regolith")])
    tag("block", "minecraft", "small_flowers", [sid("moonpetal")])
    tag("block", "minecraft", "overworld_carver_replaceables", [sid("moonstone"), sid("regolith"), sid("umbral_regolith"), sid("silver_sand")])
    tag("block", "starforged", "meteor_proof", [sid(b) for b in ("moon_seal", "orrery_ring", "lunar_mural", "orrery_console", "moon_altar",
                                                                 "lunar_gateway")])
    tag("block", "starforged", "lunar_moth_lights", ["minecraft:torch", "minecraft:wall_torch", "minecraft:soul_torch", "minecraft:soul_wall_torch",
                                                     "minecraft:lantern", "minecraft:soul_lantern", "minecraft:jack_o_lantern",
                                                     sid("star_lantern"), sid("sun_lantern")])

    tag("item", "starforged", "moonsilver_repair_materials", [sid("moonsilver_ingot")])
    tag("item", "starforged", "tidal_repair_materials", [sid("moon_heart"), sid("moonsilver_block")])
    tag("item", "minecraft", "beacon_payment_items", [sid("moonsilver_ingot")])
    tag("item", "minecraft", "swords", [sid("moonsilver_sword"), sid("crescent_glaive"), sid("phase_daggers"), sid("tidecaller_glaive")])
    tag("item", "minecraft", "pickaxes", [sid("moonsilver_pickaxe")])
    tag("item", "minecraft", "axes", [sid("moonsilver_axe")])
    tag("item", "minecraft", "shovels", [sid("moonsilver_shovel")])
    tag("item", "minecraft", "hoes", [sid("moonsilver_hoe")])
    tag("item", "minecraft", "head_armor", [sid("moonsilver_helmet"), sid("crown_of_tides")])
    tag("item", "minecraft", "chest_armor", [sid("moonsilver_chestplate")])
    tag("item", "minecraft", "leg_armor", [sid("moonsilver_leggings")])
    tag("item", "minecraft", "foot_armor", [sid("moonsilver_boots")])
    tag("item", "minecraft", "enchantable/crossbow", [sid("moonshot_crossbow")])
    tag("item", "minecraft", "enchantable/durability", [sid("orrery_staff"), sid("moonshot_crossbow"), sid("stasis_bell"), sid("tether_hook")])
    tag("item", "minecraft", "stairs", [sid("lunar_brick_stairs")])
    tag("item", "minecraft", "slabs", [sid("lunar_brick_slab")])
    tag("item", "minecraft", "small_flowers", [sid("moonpetal")])

    tag("entity_type", "starforged", "matriarch_allies", [sid("pale_matriarch"), sid("matriarch_echo"), sid("lunar_anchor"), sid("selenite_sentinel")])
    tag("entity_type", "starforged", "tidebound", [sid("regolith_skimmer"), sid("lunar_moth"), sid("selenite_sentinel"), sid("umbral_lurker")])
    tag("entity_type", "minecraft", "fall_damage_immune", [sid("lunar_moth"), sid("moonleaper"), sid("pale_matriarch"), sid("matriarch_echo")])
    tag("worldgen/structure", "starforged", "tidal_orreries", [sid("tidal_orrery")])
    tag("worldgen/biome", "starforged", "has_tidal_orrery", [sid("regolith_flats"), sid("selenite_hollows"), sid("far_side")])
    tag("worldgen/biome", "starforged", "is_pale_reach", [sid(b) for b in BIOMES])
    tag("timeline", "starforged", "in_pale_reach", ["#minecraft:universal"])


# ----------------------------------------------------------------------------------------------------------------
# The Pale Reach
# ----------------------------------------------------------------------------------------------------------------

BIOMES = {
    # name: (temperature, humidity, continentalness, sky, fog, sky light factor, music)
    "regolith_flats": ((-1.0, 0.0), (-1.0, 1.0), (-0.15, 1.0), "#06070d", "#1c2232", 0.75, "minecraft:music.overworld.snowy_slopes"),
    "silver_seas": ((-1.0, 1.0), (-1.0, 1.0), (-1.0, -0.15), "#070912", "#22304a", 0.8, "minecraft:music.overworld.frozen_peaks"),
    "selenite_hollows": ((0.0, 1.0), (0.0, 1.0), (-0.15, 1.0), "#080a14", "#202a44", 0.7, "minecraft:music.overworld.lush_caves"),
    "far_side": ((0.0, 0.5), (-1.0, 0.0), (-0.15, 1.0), "#020204", "#0a0a10", 0.25, "minecraft:music.overworld.deep_dark"),
    "shattered_rim": ((0.5, 1.0), (-1.0, 0.0), (-0.15, 1.0), "#05060c", "#181c2a", 0.65, "minecraft:music.overworld.jagged_peaks"),
}


def features():
    stone = {"predicate_type": "minecraft:block_match", "block": sid("moonstone")}
    configured("ore_moonsilver", "minecraft:ore", {"discard_chance_on_air_exposure": 0.0, "size": 6,
                                                    "targets": [{"state": {"Name": sid("moonsilver_ore")}, "target": stone}]})
    placed("ore_moonsilver", sid("ore_moonsilver"), [
        {"type": "minecraft:count", "count": 8}, {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": {"type": "minecraft:trapezoid", "min_inclusive": {"absolute": -48}, "max_inclusive": {"absolute": 100}}},
        {"type": "minecraft:biome"}])
    placed("ore_moonsilver_rich", sid("ore_moonsilver"), [
        {"type": "minecraft:count", "count": 8}, {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": 0}, "max_inclusive": {"absolute": 120}}},
        {"type": "minecraft:biome"}])
    configured("selenite", "minecraft:simple_block", {"to_place": {"type": "minecraft:simple_state_provider",
                                                                    "state": {"Name": sid("selenite_cluster"), "Properties": {"facing": "up", "waterlogged": "false"}}}})
    placed("selenite_dense", sid("selenite"), surface_patch(6, 8, [sid("moonstone"), sid("regolith")]))
    placed("selenite_sparse", sid("selenite"), surface_patch(1, 3, [sid("moonstone"), sid("regolith"), sid("umbral_regolith")]))
    placed("selenite_caves", sid("selenite"), [
        {"type": "minecraft:count", "count": 40}, {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": -48}, "max_inclusive": {"absolute": 60}}},
        {"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:all_of", "predicates": [
            {"type": "minecraft:matching_blocks", "blocks": "minecraft:air"},
            {"type": "minecraft:matching_blocks", "blocks": sid("moonstone"), "offset": [0, -1, 0]}]}},
        {"type": "minecraft:biome"}])
    configured("moonpetal", "minecraft:simple_block", {"to_place": {"type": "minecraft:simple_state_provider", "state": {"Name": sid("moonpetal")}}})
    placed("moonpetal_patch", sid("moonpetal"), surface_patch(2, 8, [sid("regolith")]))
    placed("moonpetal_shore", sid("moonpetal"), surface_patch(1, 4, [sid("silver_sand")]))
    configured("tidal_clam", "minecraft:simple_block", {"to_place": {"type": "minecraft:simple_state_provider", "state": {
        "Name": sid("tidal_clam"), "Properties": {"open": "false", "pearl": "true"}}}})
    placed("tidal_clams", sid("tidal_clam"), [
        {"type": "minecraft:count", "count": 3}, {"type": "minecraft:in_square"},
        {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR_WG"}, {"type": "minecraft:biome"},
        {"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:all_of", "predicates": [
            {"type": "minecraft:matching_fluids", "fluids": "minecraft:water"},
            {"type": "minecraft:matching_blocks", "blocks": sid("silver_sand"), "offset": [0, -1, 0]}]}}])
    configured("moon_boulder", "minecraft:block_blob", {"state": {"Name": sid("moonstone")}, "can_place_on": {
        "type": "minecraft:matching_blocks", "blocks": [sid("regolith"), sid("umbral_regolith"), sid("moonstone")]}})
    placed("moon_boulders", sid("moon_boulder"), [{"type": "minecraft:rarity_filter", "chance": 3}, {"type": "minecraft:in_square"},
                                                   {"type": "minecraft:heightmap", "heightmap": "MOTION_BLOCKING"}, {"type": "minecraft:biome"}])
    configured("floating_island", sid("floating_island"), {})
    placed("floating_islands", sid("floating_island"), [{"type": "minecraft:rarity_filter", "chance": 2}, {"type": "minecraft:in_square"},
                                                         {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"}, {"type": "minecraft:biome"}])
    placed("floating_islands_rare", sid("floating_island"), [{"type": "minecraft:rarity_filter", "chance": 24}, {"type": "minecraft:in_square"},
                                                              {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"}, {"type": "minecraft:biome"}])


def biome_features(name):
    steps = [[] for _ in range(11)]
    steps[6] += [sid("ore_moonsilver")]
    steps[9] += [sid("selenite_caves")]
    if name == "regolith_flats":
        steps[4].append(sid("moon_boulders"))
        steps[9] += [sid("moonpetal_patch"), sid("selenite_sparse"), sid("floating_islands_rare")]
    elif name == "silver_seas":
        steps[9] += [sid("tidal_clams"), sid("moonpetal_shore")]
    elif name == "selenite_hollows":
        steps[6].append(sid("ore_moonsilver_rich"))
        steps[9] += [sid("selenite_dense"), sid("moonpetal_patch")]
    elif name == "far_side":
        steps[4].append(sid("moon_boulders"))
        steps[6].append(sid("ore_moonsilver_rich"))
        steps[9] += [sid("selenite_sparse")]
    else:
        steps[4].append(sid("moon_boulders"))
        steps[9] += [sid("floating_islands"), sid("selenite_sparse")]
    # Every biome must list shared features in the same order, or world generation fails with a "feature order cycle".
    order = [sid(n) for n in ("moon_boulders", "ore_moonsilver", "ore_moonsilver_rich", "floating_islands", "floating_islands_rare",
                              "tidal_clams", "selenite_dense", "selenite_sparse", "selenite_caves", "moonpetal_patch", "moonpetal_shore")]
    return [sorted(step, key=order.index) for step in steps]


def biome_spawns(name):
    monster = {
        "regolith_flats": [spawner(sid("regolith_skimmer"), 40, 1, 2), spawner(sid("lunar_moth"), 25, 1, 3), spawner(sid("selenite_sentinel"), 5, 1, 1)],
        "silver_seas": [spawner(sid("lunar_moth"), 30, 2, 3), spawner(sid("regolith_skimmer"), 10, 1, 1)],
        "selenite_hollows": [spawner(sid("selenite_sentinel"), 20, 1, 1), spawner(sid("lunar_moth"), 30, 1, 3), spawner(sid("regolith_skimmer"), 15, 1, 2)],
        "far_side": [spawner(sid("umbral_lurker"), 40, 1, 2), spawner(sid("lunar_moth"), 15, 1, 2)],
        "shattered_rim": [spawner(sid("regolith_skimmer"), 25, 1, 2), spawner(sid("selenite_sentinel"), 10, 1, 1), spawner(sid("umbral_lurker"), 8, 1, 1)],
    }[name]
    creature = {
        "regolith_flats": [spawner(sid("moonleaper"), 8, 2, 3), spawner(sid("moonkit"), 5, 1, 2)],
        "silver_seas": [spawner(sid("moonkit"), 6, 1, 2)],
        "selenite_hollows": [spawner(sid("moonkit"), 8, 1, 2)],
        "far_side": [],
        "shattered_rim": [spawner(sid("moonleaper"), 10, 2, 4)],
    }[name]
    return {"ambient": [], "axolotls": [], "creature": creature, "misc": [], "monster": monster, "underground_water_creature": [],
            "water_ambient": [], "water_creature": []}


def worldgen():
    features()
    for name, (temp, hum, cont, sky, fog, light, music) in BIOMES.items():
        attributes = {
            "minecraft:audio/background_music": {"default": {"max_delay": 24000, "min_delay": 12000, "sound": music}},
            "minecraft:visual/sky_color": sky,
            "minecraft:visual/fog_color": fog,
            "minecraft:visual/sky_light_factor": light,
            "minecraft:visual/ambient_particles": [{"particle": {"type": sid("moon_dust")}, "probability": 0.004 if name != "far_side" else 0.0015}],
        }
        if name == "selenite_hollows":
            attributes["minecraft:visual/ambient_particles"].append({"particle": {"type": sid("lunar_glimmer")}, "probability": 0.002})
        write(f"{D}/worldgen/biome/{name}.json", {
            "attributes": attributes,
            "carvers": ["minecraft:cave", "minecraft:canyon"],
            "downfall": 0.0,
            "effects": {"water_color": "#8fb4e8", "grass_color": "#a8b4c8", "foliage_color": "#98a4bc"},
            "features": biome_features(name),
            "has_precipitation": False,
            "spawn_costs": {},
            "spawners": biome_spawns(name),
            "temperature": 0.3,
        })

    # Dimension type: an endless night under a black, star-filled sky and a huge full moon.
    write(f"{D}/dimension_type/pale_reach.json", {
        "ambient_light": 0.1,
        "attributes": {
            "minecraft:audio/background_music": {"default": {"max_delay": 24000, "min_delay": 12000, "sound": "minecraft:music.overworld.snowy_slopes"}},
            "minecraft:gameplay/bed_rule": {"can_set_spawn": "never", "can_sleep": "never", "error_message": {"translate": "block.starforged.bed.pale_reach"}},
            "minecraft:gameplay/can_start_raid": False,
            "minecraft:gameplay/monsters_burn": False,
            "minecraft:gameplay/sky_light_level": 9.0,
            "minecraft:visual/sky_color": "#05060c",
            "minecraft:visual/fog_color": "#1a2030",
            "minecraft:visual/cloud_color": "#00000000",
            "minecraft:visual/sun_angle": 200.0,
            "minecraft:visual/moon_angle": 24.0,
            "minecraft:visual/moon_phase": "full_moon",
            "minecraft:visual/star_brightness": 1.0,
            "minecraft:visual/sky_light_color": "#b8ccff",
            "minecraft:visual/ambient_light_color": "#141a2c",
            "minecraft:visual/water_fog_color": "#2a4a80",
        },
        "coordinate_scale": 1.0,
        "has_ceiling": False,
        "has_ender_dragon_fight": False,
        "has_fixed_time": True,
        "has_skylight": True,
        "height": 384,
        "infiniburn": "#minecraft:infiniburn_overworld",
        "logical_height": 384,
        "min_y": -64,
        "monster_spawn_block_light_limit": 15,
        "monster_spawn_light_level": 15,
        "timelines": "#starforged:in_pale_reach",
    })

    base = json.load(open(os.path.join(ROOT, "tools/overworld_noise_router.json")))

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
        "regolith_flats": blk(sid("regolith")),
        "silver_seas": blk(sid("silver_sand")),
        "selenite_hollows": noisy(blk(sid("regolith")), blk(sid("moonstone")), 0.1),
        "far_side": blk(sid("umbral_regolith")),
        "shattered_rim": noisy(blk(sid("regolith")), blk(sid("moonstone")), -0.2),
    }
    subs = {
        "regolith_flats": blk(sid("regolith")),
        "silver_seas": blk(sid("silver_sand")),
        "far_side": blk(sid("umbral_regolith")),
    }
    rules = [{"type": "minecraft:condition", "if_true": {"type": "minecraft:biome", "biome_is": [sid(n)]}, "then_run": r} for n, r in tops.items()]
    sub = [{"type": "minecraft:condition", "if_true": {"type": "minecraft:biome", "biome_is": [sid(n)]}, "then_run": r} for n, r in subs.items()]
    # Underwater floors anywhere are silver sand.
    under_water = {"type": "minecraft:condition", "if_true": {"type": "minecraft:not", "invert": {
        "type": "minecraft:water", "offset": -1, "surface_depth_multiplier": 0, "add_stone_depth": False}},
        "then_run": blk(sid("silver_sand"))}
    surface_rule = {"type": "minecraft:sequence", "sequence": [
        {"type": "minecraft:condition", "if_true": {"type": "minecraft:vertical_gradient", "random_name": "minecraft:bedrock_floor",
                                                    "true_at_and_below": {"above_bottom": 0}, "false_at_and_above": {"above_bottom": 5}},
         "then_run": blk("minecraft:bedrock")},
        {"type": "minecraft:condition", "if_true": {"type": "minecraft:above_preliminary_surface"}, "then_run": {"type": "minecraft:sequence", "sequence": [
            {"type": "minecraft:condition", "if_true": {"type": "minecraft:stone_depth", "offset": 0, "add_surface_depth": False,
                                                        "secondary_depth_range": 0, "surface_type": "floor"},
             "then_run": {"type": "minecraft:sequence", "sequence": [under_water] + rules}},
            {"type": "minecraft:condition", "if_true": {"type": "minecraft:stone_depth", "offset": 0, "add_surface_depth": True,
                                                        "secondary_depth_range": 0, "surface_type": "floor"},
             "then_run": {"type": "minecraft:sequence", "sequence": [under_water] + sub}},
        ]}},
    ]}
    write(f"{D}/worldgen/noise_settings/pale_reach.json", {
        "aquifers_enabled": False,
        "default_block": {"Name": sid("moonstone")},
        "default_fluid": {"Name": "minecraft:water", "Properties": {"level": "0"}},
        "disable_mob_generation": False,
        "legacy_random_source": False,
        "noise": base["noise"],
        "noise_router": base["noise_router"],
        "ore_veins_enabled": False,
        "sea_level": 63,
        "spawn_target": base["spawn_target"],
        "surface_rule": surface_rule,
    })

    biomes = []
    for name, (temp, hum, cont, *_rest) in BIOMES.items():
        biomes.append({"biome": sid(name), "parameters": {
            "temperature": list(temp), "humidity": list(hum), "continentalness": list(cont), "erosion": [-1.0, 1.0],
            "weirdness": [-1.0, 1.0], "depth": 0.0, "offset": 0.0}})
    write(f"{D}/dimension/pale_reach.json", {
        "type": sid("pale_reach"),
        "generator": {"type": "minecraft:noise", "settings": sid("pale_reach"),
                      "biome_source": {"type": "minecraft:multi_noise", "biomes": biomes}}})

    write(f"{D}/worldgen/structure/tidal_orrery.json", {
        "type": sid("tidal_orrery"), "biomes": "#starforged:has_tidal_orrery", "step": "surface_structures", "terrain_adaptation": "none",
        "spawn_overrides": {"monster": {"bounding_box": "piece", "spawns": [
            spawner(sid("selenite_sentinel"), 10, 1, 1), spawner(sid("lunar_moth"), 20, 1, 2)]}}})
    write(f"{D}/worldgen/structure_set/tidal_orreries.json", {
        "placement": {"type": "minecraft:random_spread", "salt": 581239047, "separation": 6, "spacing": 20},
        "structures": [{"structure": sid("tidal_orrery"), "weight": 1}]})

    write(f"{D}/damage_type/moonlight.json", {"exhaustion": 0.1, "message_id": "starforged.moonlight", "scaling": "when_caused_by_living_non_player"})
    write(f"{D}/damage_type/gravity.json", {"exhaustion": 0.0, "message_id": "starforged.gravity", "scaling": "when_caused_by_living_non_player"})


# ----------------------------------------------------------------------------------------------------------------
# Advancements (their own "Moonforged" tab)
# ----------------------------------------------------------------------------------------------------------------

ADVANCEMENTS_TEXT = {
    "root": ("Moonforged", "Forge a Lunar Key around the Heart of the Sun"),
    "pale_reach": ("Fly Me to the Moon", "Step through a Lunar Gateway into the Pale Reach"),
    "moonsilver": ("Silver Lining", "Smelt a Moonsilver Ingot"),
    "pearl": ("Pearl of the Tides", "Harvest a Lunar Pearl from a Tidal Clam"),
    "orrery": ("Clockwork Heavens", "Find a Tidal Orrery"),
    "sentinel": ("Glass Houses", "Shatter a Selenite Sentinel"),
    "moonkit": ("Moon Pup", "Tame a Moonkit"),
    "moonleaper": ("One Giant Leap", "Tame a Moonleaper"),
    "sigil": ("Turn of the Tide", "Obtain a Tidal Sigil"),
    "matriarch": ("Eclipse of the Moon", "Defeat the Pale Matriarch"),
    "tidecaller": ("Master of Tides", "Claim the Tidecaller Glaive"),
}


def advancements():
    def a(key, parent, icon, criteria, frame="task", xp=None, background=None):
        display = {"title": {"translate": f"advancements.starforged.moon.{key}.title"},
                   "description": {"translate": f"advancements.starforged.moon.{key}.description"},
                   "icon": {"id": icon}, "frame": frame, "show_toast": True, "announce_to_chat": True, "hidden": False}
        if background:
            display["background"] = background
            display["show_toast"] = False
            display["announce_to_chat"] = False
        d = {"display": display, "criteria": criteria, "requirements": [list(criteria.keys())]}
        if parent:
            d["parent"] = sid(f"moon/{parent}")
        if xp:
            d["rewards"] = {"experience": xp}
        write(f"{D}/advancement/moon/{key}.json", d)

    def has(item):
        return {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": item}]}}

    def kill(entity):
        return {"trigger": "minecraft:player_killed_entity", "conditions": {"entity": [
            {"condition": "minecraft:entity_properties", "entity": "this", "predicate": {"minecraft:entity_type": entity}}]}}

    def tame(entity):
        return {"trigger": "minecraft:tame_animal", "conditions": {"entity": [
            {"condition": "minecraft:entity_properties", "entity": "this", "predicate": {"minecraft:entity_type": entity}}]}}

    a("root", None, sid("lunar_key"), {"key": has(sid("lunar_key")),
                                        "reach": {"trigger": "minecraft:changed_dimension", "conditions": {"to": sid("pale_reach")}}},
      background="starforged:block/lunar_bricks")
    a("pale_reach", "root", sid("regolith"), {"enter": {"trigger": "minecraft:changed_dimension", "conditions": {"to": sid("pale_reach")}}})
    a("moonsilver", "pale_reach", sid("moonsilver_ingot"), {"ingot": has(sid("moonsilver_ingot"))})
    a("pearl", "pale_reach", sid("lunar_pearl"), {"pearl": has(sid("lunar_pearl"))})
    a("orrery", "pale_reach", sid("chiseled_lunar_bricks"), {"enter": {"trigger": "minecraft:location", "conditions": {"player": [
        {"condition": "minecraft:entity_properties", "entity": "this", "predicate": {"minecraft:location": {"structures": sid("tidal_orrery")}}}]}}})
    a("sentinel", "orrery", sid("selenite_shard"), {"kill": kill(sid("selenite_sentinel"))})
    a("moonkit", "pearl", sid("moonkit_spawn_egg"), {"tame": tame(sid("moonkit"))})
    a("moonleaper", "pale_reach", sid("moonpetal"), {"tame": tame(sid("moonleaper"))}, frame="goal")
    a("sigil", "orrery", sid("tidal_sigil"), {"sigil": has(sid("tidal_sigil"))})
    a("matriarch", "sigil", sid("moon_heart"), {"kill": kill(sid("pale_matriarch"))}, frame="challenge", xp=1000)
    a("tidecaller", "matriarch", sid("tidecaller_glaive"), {"blade": has(sid("tidecaller_glaive"))})


def main():
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
    print("moonforged data written")


if __name__ == "__main__":
    main()
