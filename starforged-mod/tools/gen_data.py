#!/usr/bin/env python3
"""
Starforged data/asset JSON generator: blockstates, models, item definitions, equipment, particles, sounds.json,
lang, recipes, loot tables, tags, worldgen, biome modifiers, damage types and advancements.
"""
import json
import os
import shutil

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src/main/resources")
A = os.path.join(RES, "assets/starforged")
D = os.path.join(RES, "data/starforged")
NS = "starforged"


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(obj, f, indent=2, ensure_ascii=False)
        f.write("\n")


def sid(name):
    return f"{NS}:{name}"


# ----------------------------------------------------------------------------------------------------------------
# Blocks
# ----------------------------------------------------------------------------------------------------------------

CUBE_BLOCKS = ["meteorite_rock", "starmetal_ore", "starmetal_block", "astral_bricks", "cracked_astral_bricks",
               "chiseled_astral_bricks", "star_lantern", "vault_seal"]


def blocks():
    for b in CUBE_BLOCKS:
        write(f"{A}/models/block/{b}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": sid(f"block/{b}")}})
        if b == "vault_seal":
            write(f"{A}/blockstates/{b}.json", {"variants": {"opening=false": {"model": sid(f"block/{b}")},
                                                              "opening=true": {"model": sid(f"block/{b}")}}})
        else:
            write(f"{A}/blockstates/{b}.json", {"variants": {"": {"model": sid(f"block/{b}")}}})

    write(f"{A}/models/block/starglass.json", {"parent": "minecraft:block/cube_all",
                                                "textures": {"all": {"sprite": sid("block/starglass"), "force_translucent": True}}})
    write(f"{A}/blockstates/starglass.json", {"variants": {"": {"model": sid("block/starglass")}}})

    write(f"{A}/models/block/astral_crystal_cluster.json", {"parent": "minecraft:block/cross",
                                                             "textures": {"cross": sid("block/astral_crystal_cluster")}})
    rot = {"down": {"x": 180}, "east": {"x": 90, "y": 90}, "north": {"x": 90}, "south": {"x": 90, "y": 180}, "up": {}, "west": {"x": 90, "y": 270}}
    write(f"{A}/blockstates/astral_crystal_cluster.json", {"variants": {
        f"facing={k}": dict({"model": sid("block/astral_crystal_cluster")}, **v) for k, v in rot.items()}})

    # Stairs & slab using vanilla templates.
    tex = sid("block/astral_bricks")
    for suffix, parent in (("", "stairs"), ("_inner", "inner_stairs"), ("_outer", "outer_stairs")):
        write(f"{A}/models/block/astral_brick_stairs{suffix}.json",
              {"parent": f"minecraft:block/{parent}", "textures": {"bottom": tex, "side": tex, "top": tex}})
    vanilla_stairs = json.load(open(os.path.join(ROOT, "tools/stone_brick_stairs_blockstate.json")))
    text = json.dumps(vanilla_stairs).replace("minecraft:block/stone_brick_stairs", sid("block/astral_brick_stairs"))
    write(f"{A}/blockstates/astral_brick_stairs.json", json.loads(text))
    write(f"{A}/models/block/astral_brick_slab.json", {"parent": "minecraft:block/slab", "textures": {"bottom": tex, "side": tex, "top": tex}})
    write(f"{A}/models/block/astral_brick_slab_top.json", {"parent": "minecraft:block/slab_top", "textures": {"bottom": tex, "side": tex, "top": tex}})
    write(f"{A}/blockstates/astral_brick_slab.json", {"variants": {
        "type=bottom": {"model": sid("block/astral_brick_slab")},
        "type=double": {"model": sid("block/astral_bricks")},
        "type=top": {"model": sid("block/astral_brick_slab_top")}}})

    # Runes: dim and charged variants.
    for rune in ("gravity_rune", "starfire_rune"):
        write(f"{A}/models/block/{rune}.json", {"parent": "minecraft:block/cube_bottom_top", "textures": {
            "top": sid(f"block/{rune}"), "bottom": sid("block/astral_bricks"), "side": sid("block/astral_bricks")}})
        write(f"{A}/models/block/{rune}_active.json", {"parent": "minecraft:block/cube_bottom_top", "textures": {
            "top": sid(f"block/{rune}_active"), "bottom": sid("block/astral_bricks"), "side": sid("block/astral_bricks")}})
        write(f"{A}/blockstates/{rune}.json", {"variants": {"powered=false": {"model": sid(f"block/{rune}")},
                                                            "powered=true": {"model": sid(f"block/{rune}_active")}}})

    # Celestial altar: base, column, table.
    side, top, bottom = sid("block/celestial_altar_side"), sid("block/celestial_altar_top"), sid("block/celestial_altar_bottom")

    def el(frm, to):
        faces = {}
        for f in ("north", "south", "east", "west"):
            faces[f] = {"texture": "#side"}
        faces["up"] = {"texture": "#top"}
        faces["down"] = {"texture": "#bottom"}
        return {"from": frm, "to": to, "faces": faces}
    write(f"{A}/models/block/celestial_altar.json", {
        "parent": "minecraft:block/block",
        "textures": {"particle": side, "side": side, "top": top, "bottom": bottom},
        "elements": [el([0, 0, 0], [16, 4, 16]), el([3, 4, 3], [13, 11, 13]), el([1, 11, 1], [15, 15, 15])]})
    write(f"{A}/blockstates/celestial_altar.json", {"variants": {"": {"model": sid("block/celestial_altar")}}})


# ----------------------------------------------------------------------------------------------------------------
# Items
# ----------------------------------------------------------------------------------------------------------------

GENERATED = ["stardust", "raw_starmetal", "starmetal_ingot", "astral_shard", "void_essence", "celestial_core", "sovereign_heart",
             "eclipse_sigil", "astral_egg", "star_chart", "starmetal_helmet", "starmetal_chestplate", "starmetal_leggings",
             "starmetal_boots", "comet_boots", "nebula_cloak", "eclipse_crown", "singularity_grenade", "rift_pearl",
             "starbolt", "void_bolt", "crystal_shard_bolt", "gravity_gauntlet"]
HANDHELD = ["starmetal_sword", "starmetal_pickaxe", "starmetal_axe", "starmetal_shovel", "starmetal_hoe", "starcaller_staff",
            "meteor_hammer", "void_scythe", "eclipse_blade"]
EGGS = ["star_mite", "void_stalker", "astral_golem", "mimic", "astral_wraith", "starling", "nebula_ray", "eclipse_sovereign"]
BLOCK_ITEMS = CUBE_BLOCKS + ["starglass", "astral_brick_stairs", "astral_brick_slab", "gravity_rune", "starfire_rune", "celestial_altar"]


def simple_item(name, model):
    write(f"{A}/items/{name}.json", {"model": {"type": "minecraft:model", "model": model}})


def items():
    for name in GENERATED:
        write(f"{A}/models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": sid(f"item/{name}")}})
        simple_item(name, sid(f"item/{name}"))
    for name in HANDHELD:
        write(f"{A}/models/item/{name}.json", {"parent": "minecraft:item/handheld", "textures": {"layer0": sid(f"item/{name}")}})
        simple_item(name, sid(f"item/{name}"))
    for mob in EGGS:
        name = f"{mob}_spawn_egg"
        write(f"{A}/models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": sid(f"item/{name}")}})
        simple_item(name, sid(f"item/{name}"))
    for name in BLOCK_ITEMS:
        simple_item(name, sid(f"block/{name}"))
    write(f"{A}/models/item/astral_crystal_cluster.json", {"parent": "minecraft:item/generated",
                                                            "textures": {"layer0": sid("block/astral_crystal_cluster")}})
    simple_item("astral_crystal_cluster", sid("item/astral_crystal_cluster"))

    # Constellation bow with pulling states.
    write(f"{A}/models/item/constellation_bow.json", {"parent": "minecraft:item/bow", "textures": {"layer0": sid("item/constellation_bow")}})
    for i in range(3):
        write(f"{A}/models/item/constellation_bow_pulling_{i}.json",
              {"parent": "minecraft:item/bow", "textures": {"layer0": sid(f"item/constellation_bow_pulling_{i}")}})
    write(f"{A}/items/constellation_bow.json", {"model": {
        "type": "minecraft:condition", "property": "minecraft:using_item",
        "on_false": {"type": "minecraft:model", "model": sid("item/constellation_bow")},
        "on_true": {"type": "minecraft:range_dispatch", "property": "minecraft:use_duration", "scale": 0.05,
                    "fallback": {"type": "minecraft:model", "model": sid("item/constellation_bow_pulling_0")},
                    "entries": [{"threshold": 0.65, "model": {"type": "minecraft:model", "model": sid("item/constellation_bow_pulling_1")}},
                                {"threshold": 0.9, "model": {"type": "minecraft:model", "model": sid("item/constellation_bow_pulling_2")}}]}}})

    # Astral compass: 32 needle frames pointing at the tracked observatory.
    for i in range(32):
        write(f"{A}/models/item/astral_compass_{i:02d}.json",
              {"parent": "minecraft:item/generated", "textures": {"layer0": sid(f"item/astral_compass_{i:02d}")}})

    def dial(target):
        entries = [{"threshold": i - 0.5 if i else 0.0, "model": {"type": "minecraft:model", "model": sid(f"item/astral_compass_{(i + 16) % 32:02d}")}}
                   for i in range(32)]
        entries.append({"threshold": 31.5, "model": {"type": "minecraft:model", "model": sid("item/astral_compass_16")}})
        return {"type": "minecraft:range_dispatch", "property": "minecraft:compass", "target": target, "scale": 32.0, "entries": entries}
    write(f"{A}/items/astral_compass.json", {"model": {"type": "minecraft:condition", "component": "minecraft:lodestone_tracker",
                                                       "property": "minecraft:has_component",
                                                       "on_true": dial("lodestone"), "on_false": dial("none")}})


# ----------------------------------------------------------------------------------------------------------------
# Equipment, particles, sounds
# ----------------------------------------------------------------------------------------------------------------

def equipment_particles_sounds():
    write(f"{A}/equipment/starmetal.json", {"layers": {"humanoid": [{"texture": sid("starmetal")}],
                                                       "humanoid_leggings": [{"texture": sid("starmetal")}]}})
    write(f"{A}/equipment/comet.json", {"layers": {"humanoid": [{"texture": sid("comet")}]}})
    write(f"{A}/equipment/nebula.json", {"layers": {"humanoid": [{"texture": sid("nebula")}], "wings": [{"texture": sid("nebula")}]}})
    write(f"{A}/equipment/eclipse.json", {"layers": {"humanoid": [{"texture": sid("eclipse")}]}})

    frames = {"star_sparkle": [f"star_{i}" for i in (0, 1, 2, 3)], "astral_glint": [f"glint_{i}" for i in range(4)],
              "void_mote": [f"mote_{i}" for i in range(4)], "meteor_ember": [f"ember_{i}" for i in range(4)],
              "eclipse_flare": ["flare_0", "flare_1"], "shooting_star": ["star_0"]}
    for name, tex in frames.items():
        write(f"{A}/particles/{name}.json", {"textures": [sid(t) for t in tex]})

    events = {
        "event.starfall_begin": ("starfall_begin", 1.0), "entity.meteor.whoosh": ("meteor_whoosh", 1.0),
        "entity.meteor.impact": ("meteor_impact", 1.0), "block.vault_seal.open": ("vault_open", 1.0),
        "block.rune.trigger": ("rune_trigger", 1.0), "block.celestial_altar.activate": ("altar_activate", 1.0),
        "block.astral_crystal.chime": ("crystal_chime", 1.0), "item.starcaller_staff.cast": ("staff_cast", 1.0),
        "item.meteor_hammer.leap": ("hammer_leap", 1.0), "item.meteor_hammer.slam": ("hammer_slam", 1.0),
        "item.void_scythe.reap": ("scythe_reap", 1.0), "item.constellation_bow.shoot": ("bow_starshot", 1.0),
        "item.eclipse_blade.wave": ("eclipse_blade_wave", 1.0), "item.eclipse_blade.total_eclipse": ("eclipse_blade_total", 1.0),
        "entity.singularity.hum": ("singularity_hum", 1.0), "entity.singularity.collapse": ("singularity_collapse", 1.0),
        "item.rift_pearl.teleport": ("rift_teleport", 1.0), "item.gravity_gauntlet.grab": ("gauntlet_grab", 1.0),
        "item.gravity_gauntlet.throw": ("gauntlet_throw", 1.0), "item.astral_compass.ping": ("compass_ping", 1.0),
        "item.comet_boots.jump": ("comet_jump", 1.0), "item.astral_egg.hatch": ("egg_hatch", 1.0),
        "item.starmetal_armor.starburst": ("starburst", 1.0), "entity.star_mite.ambient": ("mite_chitter", 0.8),
        "entity.star_mite.death": ("mite_death", 1.0), "entity.void_stalker.ambient": ("void_stalker_ambient", 1.0),
        "entity.void_stalker.teleport": ("void_stalker_teleport", 1.0), "entity.void_stalker.hurt": ("void_stalker_hurt", 1.0),
        "entity.void_stalker.death": ("void_stalker_death", 1.0), "entity.astral_golem.slam": ("golem_slam", 1.0),
        "entity.astral_golem.hurt": ("golem_hurt", 1.0), "entity.astral_golem.death": ("golem_death", 1.0),
        "entity.mimic.chomp": ("mimic_chomp", 1.0), "entity.mimic.reveal": ("mimic_reveal", 1.0),
        "entity.astral_wraith.ambient": ("astral_wraith_ambient", 0.8), "entity.astral_wraith.cast": ("astral_wraith_cast", 1.0),
        "entity.astral_wraith.death": ("astral_wraith_death", 1.0), "entity.starling.ambient": ("starling_ambient", 0.7),
        "entity.nebula_ray.ambient": ("nebula_ray_ambient", 1.0), "entity.eclipse_sovereign.roar": ("boss_roar", 1.0),
        "entity.eclipse_sovereign.hurt": ("boss_hurt", 1.0), "entity.eclipse_sovereign.beam_charge": ("boss_beam_charge", 1.0),
        "entity.eclipse_sovereign.beam_fire": ("boss_beam_fire", 1.0), "entity.eclipse_sovereign.slam": ("boss_slam", 1.0),
        "entity.eclipse_sovereign.death": ("boss_death", 1.0), "entity.eclipse_sovereign.summon": ("boss_summon", 1.0),
        "entity.eclipse_crystal.shatter": ("crystal_shatter", 1.0),
    }
    sounds = {}
    for event, (file, vol) in events.items():
        entry = {"name": sid(file)}
        if vol != 1.0:
            entry["volume"] = vol
        sounds[event] = {"subtitle": f"subtitles.starforged.{event}", "sounds": [entry]}
    write(f"{A}/sounds.json", sounds)
    return events


# ----------------------------------------------------------------------------------------------------------------
# Lang
# ----------------------------------------------------------------------------------------------------------------

def lang(events):
    L = {
        "itemGroup.starforged": "Starforged",
        # Blocks
        "block.starforged.meteorite_rock": "Meteorite Rock",
        "block.starforged.starmetal_ore": "Starmetal Ore",
        "block.starforged.astral_crystal_cluster": "Astral Crystal Cluster",
        "block.starforged.starmetal_block": "Block of Starmetal",
        "block.starforged.astral_bricks": "Astral Bricks",
        "block.starforged.cracked_astral_bricks": "Cracked Astral Bricks",
        "block.starforged.chiseled_astral_bricks": "Chiseled Astral Bricks",
        "block.starforged.astral_brick_stairs": "Astral Brick Stairs",
        "block.starforged.astral_brick_slab": "Astral Brick Slab",
        "block.starforged.starglass": "Starglass",
        "block.starforged.star_lantern": "Star Lantern",
        "block.starforged.celestial_altar": "Celestial Altar",
        "block.starforged.gravity_rune": "Gravity Rune",
        "block.starforged.starfire_rune": "Starfire Rune",
        "block.starforged.vault_seal": "Vault Seal",
        "block.starforged.vault_seal.hint": "✦ The vault answers only to starlight... offer it Stardust.",
        "block.starforged.celestial_altar.hint": "✦ The altar hungers for an Eclipse Sigil.",
        "block.starforged.celestial_altar.busy": "The heavens are already torn open here.",
        # Items
        "item.starforged.stardust": "Stardust",
        "item.starforged.stardust.desc0": "Glittering dust shed by fallen stars.",
        "item.starforged.raw_starmetal": "Raw Starmetal",
        "item.starforged.starmetal_ingot": "Starmetal Ingot",
        "item.starforged.astral_shard": "Astral Shard",
        "item.starforged.astral_shard.desc0": "Solidified starlight. Hums when held to the sky.",
        "item.starforged.void_essence": "Void Essence",
        "item.starforged.void_essence.desc0": "What remains of a Void Stalker. Cold, and hungry.",
        "item.starforged.celestial_core": "Celestial Core",
        "item.starforged.celestial_core.desc0": "The beating heart of an Astral Golem.",
        "item.starforged.sovereign_heart": "Sovereign's Heart",
        "item.starforged.sovereign_heart.desc0": "A black sun, still warm.",
        "item.starforged.sovereign_heart.desc1": "Repairs the relics of the Eclipse.",
        "item.starforged.eclipse_sigil": "Eclipse Sigil",
        "item.starforged.eclipse_sigil.desc0": "The key that breaks the last seal.",
        "item.starforged.eclipse_sigil.desc1": "Use on the Celestial Altar atop a Fallen Observatory",
        "item.starforged.eclipse_sigil.desc2": "to summon the Eclipse Sovereign.",
        "item.starforged.astral_egg": "Astral Egg",
        "item.starforged.astral_egg.desc0": "Something inside is glowing.",
        "item.starforged.astral_egg.desc1": "Use to hatch a loyal Starling.",
        "item.starforged.astral_egg.hatched": "✦ A Starling hatches and chirps happily at you!",
        "item.starforged.star_chart": "Star Chart",
        "item.starforged.star_chart.desc0": "\"The stars are not lights. They are bars.\"",
        "item.starforged.star_chart.desc1": "\"Mark the observatories. Seal the altars. Never offer the Sigil.\"",
        "item.starforged.star_chart.desc2": "\"Tip-toe across the runes - they wake to careless feet.\"",
        "item.starforged.starmetal_sword": "Starmetal Sword",
        "item.starforged.starmetal_pickaxe": "Starmetal Pickaxe",
        "item.starforged.starmetal_axe": "Starmetal Axe",
        "item.starforged.starmetal_shovel": "Starmetal Shovel",
        "item.starforged.starmetal_hoe": "Starmetal Hoe",
        "item.starforged.starmetal_helmet": "Starmetal Helmet",
        "item.starforged.starmetal_chestplate": "Starmetal Chestplate",
        "item.starforged.starmetal_leggings": "Starmetal Leggings",
        "item.starforged.starmetal_boots": "Starmetal Boots",
        "item.starforged.comet_boots": "Comet Boots",
        "item.starforged.nebula_cloak": "Nebula Cloak",
        "item.starforged.eclipse_crown": "Crown of the Eclipse",
        "item.starforged.starcaller_staff": "Starcaller Staff",
        "item.starforged.meteor_hammer": "Meteor Hammer",
        "item.starforged.void_scythe": "Void Scythe",
        "item.starforged.constellation_bow": "Constellation Bow",
        "item.starforged.eclipse_blade": "Eclipse Blade",
        "item.starforged.singularity_grenade": "Singularity Grenade",
        "item.starforged.rift_pearl": "Rift Pearl",
        "item.starforged.rift_pearl.blocked": "There is no room on the other side of the rift.",
        "item.starforged.astral_compass": "Astral Compass",
        "item.starforged.astral_compass.found": "✦ A Fallen Observatory lies %s blocks to the %s",
        "item.starforged.astral_compass.none": "The needle spins aimlessly... no observatory answers.",
        "item.starforged.astral_compass.tracking": "Tracking observatory at %s, %s",
        "item.starforged.gravity_gauntlet": "Gravity Gauntlet",
        "item.starforged.gravity_gauntlet.nothing": "Nothing to grab.",
        "item.starforged.starbolt": "Starbolt",
        "item.starforged.void_bolt": "Void Bolt",
        "item.starforged.crystal_shard_bolt": "Crystal Shard",
        # Abilities
        "ability.starforged.starmetal_set.title": "Full Set: Starlit",
        "ability.starforged.starmetal_set.text": "Night Vision at night, half fall damage, and a retaliating Starburst when struck.",
        "ability.starforged.comet_boots.title": "Comet Leap",
        "ability.starforged.comet_boots.text": "Press jump in mid-air to leap again. No fall damage.",
        "ability.starforged.nebula_cloak.title": "Starwind Wings",
        "ability.starforged.nebula_cloak.text": "Glide like an elytra, propelled by a starwind - no rockets needed.",
        "ability.starforged.eclipse_crown.title": "Sovereign's Sight",
        "ability.starforged.eclipse_crown.text": "Night Vision, reveals every hostile creature nearby, resists voidborn.",
        "ability.starforged.starcaller.title": "Right-click: Call a Meteor",
        "ability.starforged.starcaller.text": "A falling star crashes wherever you aim.",
        "ability.starforged.starcaller_shower.title": "Sneak + Right-click: Meteor Shower",
        "ability.starforged.starcaller_shower.text": "Rain seven meteors down on an area.",
        "ability.starforged.meteor_hammer.title": "Right-click: Skyfall Leap",
        "ability.starforged.meteor_hammer.text": "Leap high, then crash down in a shockwave. Use in mid-air to dive.",
        "ability.starforged.meteor_hammer_hit.title": "Heavy Impact",
        "ability.starforged.meteor_hammer_hit.text": "Every swing sends enemies flying. Mines like a pickaxe.",
        "ability.starforged.void_scythe_leech.title": "Life Leech",
        "ability.starforged.void_scythe_leech.text": "Every hit restores your health.",
        "ability.starforged.void_scythe_reap.title": "Right-click: Reap",
        "ability.starforged.void_scythe_reap.text": "A 360° void sweep that drags enemies into the blade.",
        "ability.starforged.constellation_bow.title": "Living Stars",
        "ability.starforged.constellation_bow.text": "Arrows become homing starbolts; a full draw fires three.",
        "ability.starforged.eclipse_wave.title": "Right-click: Eclipse Wave",
        "ability.starforged.eclipse_wave.text": "Hurl a piercing crescent of eclipse-light.",
        "ability.starforged.total_eclipse.title": "Sneak + Right-click: Total Eclipse",
        "ability.starforged.total_eclipse.text": "A dome of darkness that slows, weakens and burns all enemies inside.",
        "ability.starforged.singularity.title": "Throw: Black Hole",
        "ability.starforged.singularity.text": "Tears open a singularity that swallows creatures and items, then collapses.",
        "ability.starforged.rift_pearl.title": "Right-click: Blink",
        "ability.starforged.rift_pearl.text": "Teleport instantly to where you are looking (36 blocks).",
        "ability.starforged.astral_compass.title": "Right-click: Seek",
        "ability.starforged.astral_compass.text": "Attunes to the nearest Fallen Observatory. The needle points the way.",
        "ability.starforged.gravity_gauntlet.title": "Hold Right-click: Telekinesis",
        "ability.starforged.gravity_gauntlet.text": "Lift a creature into the air; release to hurl it.",
        # Entities
        "entity.starforged.star_mite": "Star Mite",
        "entity.starforged.void_stalker": "Void Stalker",
        "entity.starforged.astral_golem": "Astral Golem",
        "entity.starforged.mimic": "Mimic",
        "entity.starforged.astral_wraith": "Astral Wraith",
        "entity.starforged.starling": "Starling",
        "entity.starforged.starling.wait": "Your Starling will wait here.",
        "entity.starforged.starling.follow": "Your Starling follows you.",
        "entity.starforged.nebula_ray": "Nebula Ray",
        "entity.starforged.eclipse_sovereign": "The Eclipse Sovereign",
        "entity.starforged.eclipse_sovereign.title": "Devourer of Stars",
        "entity.starforged.eclipse_sovereign.shielded": "✦ The Sovereign raises its Eclipse Crystals! Shatter them to break its shield!",
        "entity.starforged.eclipse_sovereign.immune": "The Sovereign is shielded by its Eclipse Crystals!",
        "entity.starforged.eclipse_sovereign.stunned": "✦ The shield shatters! The Sovereign reels - STRIKE NOW!",
        "entity.starforged.eclipse_sovereign.enraged": "✦ THE ECLIPSE IS TOTAL. The Sovereign is enraged!",
        "entity.starforged.eclipse_sovereign.dying": "The Sovereign's black sun begins to burst...",
        "entity.starforged.eclipse_sovereign.defeated": "✦ The Eclipse Sovereign has fallen. The stars are free!",
        "entity.starforged.eclipse_sovereign.retreat": "The Sovereign withdraws into the dark between the stars... the Sigil falls to the altar.",
        "entity.starforged.eclipse_crystal": "Eclipse Crystal",
        "entity.starforged.meteor": "Meteor",
        "entity.starforged.starbolt": "Starbolt",
        "entity.starforged.eclipse_wave": "Eclipse Wave",
        "entity.starforged.singularity": "Singularity",
        "entity.starforged.thrown_singularity": "Singularity Grenade",
        # Events & commands
        "event.starforged.starfall.title": "✦ STARFALL ✦",
        "event.starforged.starfall.subtitle": "The stars are falling...",
        "event.starforged.starfall.begin": "✦ A Starfall has begun! Meteors are raining from the sky - find where they land.",
        "event.starforged.starfall.end": "The Starfall fades with the dawn.",
        "event.starforged.summon.altar": "The Celestial Altar awakens. Somewhere above, a star goes out.",
        "event.starforged.summon.dark": "THE STARS GO DARK",
        "event.starforged.summon.dark_sub": "Something is coming",
        "commands.starforged.starfall.start": "Starfall started for %s seconds",
        "commands.starforged.starfall.stop": "Starfall stopped",
        "commands.starforged.meteor": "Called down %s meteor(s)",
        "commands.starforged.kit": "✦ You are Starforged. (Kit given)",
        "commands.starforged.boss": "✦ The summoning ritual has begun...",
        "commands.starforged.observatory": "✦ A Fallen Observatory rises at %s, %s, %s",
        "direction.starforged.north": "north", "direction.starforged.northeast": "northeast", "direction.starforged.east": "east",
        "direction.starforged.southeast": "southeast", "direction.starforged.south": "south", "direction.starforged.southwest": "southwest",
        "direction.starforged.west": "west", "direction.starforged.northwest": "northwest",
        # Death messages
        "death.attack.starforged.meteor": "%1$s was flattened by a falling star",
        "death.attack.starforged.meteor.player": "%1$s was flattened by a falling star called by %2$s",
        "death.attack.starforged.eclipse_beam": "%1$s was erased by the Eclipse",
        "death.attack.starforged.eclipse_beam.player": "%1$s was erased by %2$s's eclipse",
        "death.attack.starforged.singularity": "%1$s was swallowed by a black hole",
        "death.attack.starforged.singularity.player": "%1$s was swallowed by %2$s's black hole",
        "death.attack.starforged.starlight": "%1$s was burned away by starlight",
        "death.attack.starforged.starlight.player": "%1$s was burned away by %2$s's starlight",
        "death.attack.starforged.void_rend": "%1$s was reaped by the void",
        "death.attack.starforged.void_rend.player": "%1$s was reaped by %2$s",
        "death.attack.starforged.shockwave": "%1$s was pulverized by a shockwave",
        "death.attack.starforged.shockwave.player": "%1$s was pulverized by %2$s",
    }
    for mob in EGGS:
        L[f"item.starforged.{mob}_spawn_egg"] = L[f"entity.starforged.{mob}"].replace("The ", "") + " Spawn Egg"
    subtitles = {
        "event.starfall_begin": "The sky shimmers", "entity.meteor.whoosh": "Meteor screams overhead", "entity.meteor.impact": "Meteor impacts",
        "block.vault_seal.open": "Vault seal dissolves", "block.rune.trigger": "Rune triggers", "block.celestial_altar.activate": "Altar awakens",
        "block.astral_crystal.chime": "Crystal chimes", "item.starcaller_staff.cast": "Staff calls a star", "item.meteor_hammer.leap": "Hammer leap",
        "item.meteor_hammer.slam": "Hammer slam", "item.void_scythe.reap": "Scythe reaps", "item.constellation_bow.shoot": "Starbolts fly",
        "item.eclipse_blade.wave": "Eclipse wave", "item.eclipse_blade.total_eclipse": "Total eclipse", "entity.singularity.hum": "Black hole hums",
        "entity.singularity.collapse": "Black hole collapses", "item.rift_pearl.teleport": "Rift opens", "item.gravity_gauntlet.grab": "Gauntlet grabs",
        "item.gravity_gauntlet.throw": "Gauntlet throws", "item.astral_compass.ping": "Compass pings", "item.comet_boots.jump": "Comet leap",
        "item.astral_egg.hatch": "Astral egg hatches", "item.starmetal_armor.starburst": "Starburst", "entity.star_mite.ambient": "Star Mite chitters",
        "entity.star_mite.death": "Star Mite bursts", "entity.void_stalker.ambient": "Something whispers", "entity.void_stalker.teleport": "Void Stalker blinks",
        "entity.void_stalker.hurt": "Void Stalker hisses", "entity.void_stalker.death": "Void Stalker dies", "entity.astral_golem.slam": "Golem slams",
        "entity.astral_golem.hurt": "Golem cracks", "entity.astral_golem.death": "Golem crumbles", "entity.mimic.chomp": "Mimic chomps",
        "entity.mimic.reveal": "A chest snarls", "entity.astral_wraith.ambient": "Wraith moans", "entity.astral_wraith.cast": "Wraith casts",
        "entity.astral_wraith.death": "Wraith fades", "entity.starling.ambient": "Starling chirps", "entity.nebula_ray.ambient": "Nebula Ray sings",
        "entity.eclipse_sovereign.roar": "The Sovereign roars", "entity.eclipse_sovereign.hurt": "The Sovereign growls",
        "entity.eclipse_sovereign.beam_charge": "Eclipse beam charges", "entity.eclipse_sovereign.beam_fire": "Eclipse beam fires",
        "entity.eclipse_sovereign.slam": "Gauntlets slam", "entity.eclipse_sovereign.death": "The black sun bursts",
        "entity.eclipse_sovereign.summon": "A portal tears open", "entity.eclipse_crystal.shatter": "Eclipse Crystal shatters",
    }
    for event in events:
        L[f"subtitles.starforged.{event}"] = subtitles.get(event, event)
    adv = {
        "root": ("Starforged", "When the stars fall, legends are forged"),
        "stardust": ("Starstruck", "Collect Stardust or Raw Starmetal from a fallen star"),
        "ingot": ("Forged in Starlight", "Smelt a Starmetal Ingot"),
        "staff": ("Hand of the Heavens", "Wield a Starcaller Staff"),
        "mimic": ("It Was a Mimic!", "Defeat a Mimic... that chest looked so innocent"),
        "stalker": ("Lights Out", "Slay a Void Stalker"),
        "observatory": ("The Fallen Observatory", "Find one of the old astronomers' observatories"),
        "golem": ("Statue? What Statue?", "Defeat the Astral Golem guarding an observatory vault"),
        "starling": ("A Friend From the Stars", "Hatch or tame a Starling"),
        "nebula_ray": ("Sky Whale Whisperer", "Tame a Nebula Ray"),
        "sigil": ("The Last Seal", "Craft an Eclipse Sigil"),
        "sovereign": ("Devourer No More", "Defeat the Eclipse Sovereign"),
        "blade": ("Total Eclipse", "Wield the Eclipse Blade"),
    }
    for key, (title, desc) in adv.items():
        L[f"advancements.starforged.{key}.title"] = title
        L[f"advancements.starforged.{key}.description"] = desc
    write(f"{A}/lang/en_us.json", L)
    return adv


# ----------------------------------------------------------------------------------------------------------------
# Recipes
# ----------------------------------------------------------------------------------------------------------------

def shaped(name, pattern, key, result, count=1, category="misc"):
    res = {"id": result}
    if count > 1:
        res["count"] = count
    write(f"{D}/recipe/{name}.json", {"type": "minecraft:crafting_shaped", "category": category, "key": key, "pattern": pattern, "result": res})


def shapeless(name, ingredients, result, count=1, category="misc"):
    res = {"id": result}
    if count > 1:
        res["count"] = count
    write(f"{D}/recipe/{name}.json", {"type": "minecraft:crafting_shapeless", "category": category, "ingredients": ingredients, "result": res})


def cook(name, ingredient, result, xp, kinds=("smelting", "blasting")):
    for kind in kinds:
        write(f"{D}/recipe/{name}_from_{kind}.json", {"type": f"minecraft:{kind}", "category": "misc", "experience": xp,
                                                       "ingredient": ingredient, "result": {"id": result},
                                                       "cookingtime": 200 if kind == "smelting" else 100})


def recipes():
    I = sid("starmetal_ingot")
    cook("starmetal_ingot", sid("raw_starmetal"), I, 1.0)
    cook("starmetal_ingot_ore", sid("starmetal_ore"), I, 1.0)
    shaped("starmetal_block", ["###", "###", "###"], {"#": I}, sid("starmetal_block"), category="building")
    shapeless("starmetal_ingot_from_block", [sid("starmetal_block")], I, 9)
    stick = "minecraft:stick"
    shaped("starmetal_sword", ["X", "X", "#"], {"X": I, "#": stick}, sid("starmetal_sword"), category="equipment")
    shaped("starmetal_pickaxe", ["XXX", " # ", " # "], {"X": I, "#": stick}, sid("starmetal_pickaxe"), category="equipment")
    shaped("starmetal_axe", ["XX", "X#", " #"], {"X": I, "#": stick}, sid("starmetal_axe"), category="equipment")
    shaped("starmetal_shovel", ["X", "#", "#"], {"X": I, "#": stick}, sid("starmetal_shovel"), category="equipment")
    shaped("starmetal_hoe", ["XX", " #", " #"], {"X": I, "#": stick}, sid("starmetal_hoe"), category="equipment")
    shaped("starmetal_helmet", ["XXX", "X X"], {"X": I}, sid("starmetal_helmet"), category="equipment")
    shaped("starmetal_chestplate", ["X X", "XXX", "XXX"], {"X": I}, sid("starmetal_chestplate"), category="equipment")
    shaped("starmetal_leggings", ["XXX", "X X", "X X"], {"X": I}, sid("starmetal_leggings"), category="equipment")
    shaped("starmetal_boots", ["X X", "X X"], {"X": I}, sid("starmetal_boots"), category="equipment")

    shaped("astral_bricks", ["SSS", "SAS", "SSS"], {"S": "minecraft:stone_bricks", "A": sid("astral_shard")}, sid("astral_bricks"), 8, "building")
    cook("cracked_astral_bricks", sid("astral_bricks"), sid("cracked_astral_bricks"), 0.1, ("smelting",))
    shaped("chiseled_astral_bricks", ["#", "#"], {"#": sid("astral_brick_slab")}, sid("chiseled_astral_bricks"), category="building")
    shaped("astral_brick_stairs", ["#  ", "## ", "###"], {"#": sid("astral_bricks")}, sid("astral_brick_stairs"), 4, "building")
    shaped("astral_brick_slab", ["###"], {"#": sid("astral_bricks")}, sid("astral_brick_slab"), 6, "building")
    shaped("starglass", ["GGG", "GDG", "GGG"], {"G": "minecraft:glass", "D": sid("stardust")}, sid("starglass"), 8, "building")
    shaped("star_lantern", [" D ", "DGD", " D "], {"D": sid("stardust"), "G": "minecraft:glowstone"}, sid("star_lantern"), 2, "building")
    shaped("gravity_rune", [" A ", "SBS"], {"A": sid("astral_shard"), "S": "minecraft:slime_ball", "B": sid("astral_bricks")}, sid("gravity_rune"), 2, "redstone")
    shaped("starfire_rune", [" A ", "PBP"], {"A": sid("astral_shard"), "P": "minecraft:blaze_powder", "B": sid("astral_bricks")}, sid("starfire_rune"), 2, "redstone")
    shaped("celestial_altar", [" C ", "ZBZ", "BBB"], {"C": sid("celestial_core"), "Z": sid("chiseled_astral_bricks"), "B": sid("starmetal_block")},
           sid("celestial_altar"), category="building")

    shaped("starcaller_staff", [" DA", " ID", "I  "], {"D": sid("stardust"), "A": sid("astral_shard"), "I": I}, sid("starcaller_staff"), category="equipment")
    shaped("meteor_hammer", ["RBR", "RIR", " I "], {"R": sid("meteorite_rock"), "B": sid("starmetal_block"), "I": I}, sid("meteor_hammer"), category="equipment")
    shaped("void_scythe", ["VVI", "  I", " I "], {"V": sid("void_essence"), "I": I}, sid("void_scythe"), category="equipment")
    shaped("constellation_bow", [" IS", "A S", " IS"], {"I": I, "A": sid("astral_shard"), "S": "minecraft:string"}, sid("constellation_bow"), category="equipment")
    shaped("singularity_grenade", [" G ", "GVG", " G "], {"G": "minecraft:glass", "V": sid("void_essence")}, sid("singularity_grenade"), 2, "equipment")
    shaped("rift_pearl", ["AVA", "VEV", "AVA"], {"A": sid("astral_shard"), "V": sid("void_essence"), "E": "minecraft:ender_pearl"}, sid("rift_pearl"), category="equipment")
    shaped("gravity_gauntlet", ["IAI", "IVI", " I "], {"I": I, "A": sid("astral_shard"), "V": sid("void_essence")}, sid("gravity_gauntlet"), category="equipment")
    shaped("astral_compass", [" D ", "DCD", " A "], {"D": sid("stardust"), "C": "minecraft:compass", "A": sid("astral_shard")}, sid("astral_compass"), category="equipment")
    shaped("eclipse_sigil", ["VAV", "ACA", "VAV"], {"V": sid("void_essence"), "A": sid("astral_shard"), "C": sid("celestial_core")}, sid("eclipse_sigil"))
    shaped("nebula_cloak", ["VPV", "SCS", "VPV"], {"V": sid("void_essence"), "P": "minecraft:phantom_membrane", "S": sid("astral_shard"),
                                                   "C": sid("starmetal_chestplate")}, sid("nebula_cloak"), category="equipment")
    shapeless("comet_boots", [sid("starmetal_boots"), "minecraft:feather", "minecraft:feather", sid("stardust"), "minecraft:blaze_powder"],
              sid("comet_boots"), category="equipment")
    shapeless("star_chart", ["minecraft:paper", sid("stardust")], sid("star_chart"))


# ----------------------------------------------------------------------------------------------------------------
# Loot tables
# ----------------------------------------------------------------------------------------------------------------

def item_entry(name, weight=1, lo=1, hi=1, enchant=False, looting=False):
    e = {"type": "minecraft:item", "name": name, "weight": weight}
    funcs = []
    if lo != 1 or hi != 1:
        funcs.append({"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}})
    if enchant:
        funcs.append({"function": "minecraft:enchant_randomly"})
    if looting:
        funcs.append({"function": "minecraft:enchanted_count_increase", "enchantment": "minecraft:looting",
                      "count": {"type": "minecraft:uniform", "min": 0, "max": 1}})
    if funcs:
        e["functions"] = funcs
    return e


def pool(entries, rolls=1, conditions=None, bonus=None):
    p = {"rolls": rolls if isinstance(rolls, (int, float)) else {"type": "minecraft:uniform", "min": rolls[0], "max": rolls[1]},
         "entries": entries}
    if conditions:
        p["conditions"] = conditions
    return p


def chance(p):
    return [{"condition": "minecraft:random_chance", "chance": p}]


def silk():
    return {"condition": "minecraft:match_tool", "predicate": {"predicates": {"minecraft:enchantments": [
        {"enchantments": "minecraft:silk_touch", "levels": {"min": 1}}]}}}


def loot_tables():
    self_drop = ["meteorite_rock", "starmetal_block", "astral_bricks", "cracked_astral_bricks", "chiseled_astral_bricks",
                 "astral_brick_stairs", "star_lantern", "celestial_altar", "gravity_rune", "starfire_rune"]
    for b in self_drop:
        write(f"{D}/loot_table/blocks/{b}.json", {"type": "minecraft:block", "pools": [
            {"rolls": 1, "entries": [{"type": "minecraft:item", "name": sid(b)}], "conditions": [{"condition": "minecraft:survives_explosion"}]}],
            "random_sequence": sid(f"blocks/{b}")})
    write(f"{D}/loot_table/blocks/astral_brick_slab.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [
        {"type": "minecraft:item", "name": sid("astral_brick_slab"), "functions": [
            {"function": "minecraft:set_count", "count": 2, "conditions": [{"condition": "minecraft:block_state_property",
                                                                            "block": sid("astral_brick_slab"), "properties": {"type": "double"}}]},
            {"function": "minecraft:explosion_decay"}]}]}]})
    write(f"{D}/loot_table/blocks/starglass.json", {"type": "minecraft:block", "pools": [
        {"rolls": 1, "conditions": [silk()], "entries": [{"type": "minecraft:item", "name": sid("starglass")}]}]})
    write(f"{D}/loot_table/blocks/starmetal_ore.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [
        {"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "name": sid("starmetal_ore"), "conditions": [silk()]},
            {"type": "minecraft:item", "name": sid("raw_starmetal"), "functions": [
                {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"},
                {"function": "minecraft:explosion_decay"}]}]}]}]})
    write(f"{D}/loot_table/blocks/astral_crystal_cluster.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [
        {"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "name": sid("astral_crystal_cluster"), "conditions": [silk()]},
            {"type": "minecraft:item", "name": sid("astral_shard"), "functions": [
                {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 2, "max": 4}},
                {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"},
                {"function": "minecraft:explosion_decay"}]}]}]},
        {"rolls": 1, "conditions": chance(0.5), "entries": [item_entry(sid("stardust"), 1, 1, 2)]}]})

    ent = lambda name, pools: write(f"{D}/loot_table/entities/{name}.json", {"type": "minecraft:entity", "pools": pools,
                                                                             "random_sequence": sid(f"entities/{name}")})
    ent("star_mite", [pool([item_entry(sid("stardust"), 1, 1, 2, looting=True)])])
    ent("void_stalker", [pool([item_entry(sid("void_essence"), 1, 1, 2, looting=True)]),
                         pool([item_entry(sid("stardust"), 1, 1, 2)], conditions=chance(0.3))])
    ent("astral_golem", [pool([item_entry(sid("celestial_core"))]), pool([item_entry(sid("astral_shard"), 1, 4, 8, looting=True)]),
                         pool([item_entry(sid("starmetal_ingot"), 1, 2, 4)])])
    ent("mimic", [pool([item_entry("minecraft:gold_ingot", 6, 3, 8), item_entry("minecraft:diamond", 3, 1, 3), item_entry(sid("starmetal_ingot"), 4, 1, 3),
                        item_entry("minecraft:emerald", 3, 2, 5)], rolls=(2, 4)),
                  pool([item_entry("minecraft:book", 1, enchant=True)], conditions=chance(0.5)),
                  pool([item_entry(sid("astral_egg"))], conditions=chance(0.08)),
                  pool([item_entry(sid("singularity_grenade"), 1, 1, 3)], conditions=chance(0.35))])
    ent("astral_wraith", [pool([item_entry(sid("stardust"), 1, 1, 3, looting=True)]),
                          pool([item_entry(sid("astral_shard"))], conditions=chance(0.3)),
                          pool([item_entry(sid("star_chart"))], conditions=chance(0.12))])
    ent("nebula_ray", [pool([item_entry(sid("stardust"), 1, 2, 4)])])
    ent("starling", [pool([item_entry(sid("stardust"), 1, 1, 2)])])
    ent("eclipse_sovereign", [pool([item_entry(sid("sovereign_heart"))]), pool([item_entry(sid("eclipse_blade"))]),
                              pool([item_entry(sid("eclipse_crown"))]), pool([item_entry(sid("starmetal_ingot"), 1, 8, 16)]),
                              pool([item_entry(sid("astral_shard"), 1, 6, 12)]), pool([item_entry(sid("astral_egg"))], conditions=chance(0.5)),
                              pool([item_entry("minecraft:enchanted_golden_apple", 1, 1, 2)])])

    chest = lambda name, pools: write(f"{D}/loot_table/chests/{name}.json", {"type": "minecraft:chest", "pools": pools,
                                                                             "random_sequence": sid(f"chests/{name}")})
    chest("observatory_common", [
        pool([item_entry(sid("stardust"), 10, 2, 6), item_entry(sid("astral_shard"), 6, 1, 3), item_entry(sid("raw_starmetal"), 6, 1, 4),
              item_entry("minecraft:bread", 5, 1, 4), item_entry("minecraft:gold_ingot", 4, 1, 4), item_entry("minecraft:iron_ingot", 5, 2, 5),
              item_entry("minecraft:spyglass", 2), item_entry(sid("star_chart"), 3), item_entry(sid("astral_compass"), 1),
              item_entry("minecraft:candle", 3, 1, 3), item_entry("minecraft:arrow", 4, 4, 12)], rolls=(4, 8))])
    chest("observatory_library", [
        pool([item_entry("minecraft:book", 1, enchant=True)], rolls=(1, 3)),
        pool([item_entry(sid("star_chart"), 6), item_entry("minecraft:paper", 6, 2, 8), item_entry(sid("stardust"), 8, 2, 6),
              item_entry("minecraft:experience_bottle", 4, 1, 4), item_entry("minecraft:lapis_lazuli", 5, 3, 9),
              item_entry(sid("astral_compass"), 2), item_entry("minecraft:spyglass", 2)], rolls=(3, 6))])
    chest("observatory_armory", [
        pool([item_entry(sid("starmetal_sword"), 3, enchant=True), item_entry(sid("starmetal_pickaxe"), 3), item_entry(sid("starmetal_helmet"), 3),
              item_entry(sid("starmetal_chestplate"), 2), item_entry(sid("starmetal_leggings"), 2), item_entry(sid("starmetal_boots"), 3),
              item_entry(sid("constellation_bow"), 2), item_entry(sid("starcaller_staff"), 2), item_entry(sid("comet_boots"), 1),
              item_entry(sid("meteor_hammer"), 1)], rolls=1),
        pool([item_entry(sid("starmetal_ingot"), 6, 2, 6), item_entry("minecraft:arrow", 5, 8, 20), item_entry(sid("singularity_grenade"), 3, 1, 4),
              item_entry(sid("void_essence"), 3, 1, 2), item_entry("minecraft:diamond", 2, 1, 2), item_entry("minecraft:golden_apple", 2)], rolls=(3, 6))])
    chest("observatory_vault", [
        pool([item_entry(sid("astral_egg"))], conditions=chance(0.65)),
        pool([item_entry(sid("eclipse_sigil"))], conditions=chance(0.35)),
        pool([item_entry(sid("rift_pearl"), 3), item_entry(sid("gravity_gauntlet"), 3), item_entry(sid("void_scythe"), 2),
              item_entry(sid("nebula_cloak"), 2), item_entry(sid("starcaller_staff"), 2)], rolls=1),
        pool([item_entry(sid("void_essence"), 5, 2, 4), item_entry(sid("astral_shard"), 5, 3, 8), item_entry(sid("starmetal_block"), 2, 1, 2),
              item_entry("minecraft:diamond", 4, 2, 5), item_entry("minecraft:enchanted_golden_apple", 1),
              item_entry("minecraft:book", 3, enchant=True), item_entry(sid("stardust"), 5, 4, 10)], rolls=(4, 7))])


# ----------------------------------------------------------------------------------------------------------------
# Tags
# ----------------------------------------------------------------------------------------------------------------

def tag(kind, ns, name, values):
    base = RES + f"/data/{ns}/tags/{kind}/{name}.json"
    write(base, {"replace": False, "values": values})


def tags():
    pick = ["meteorite_rock", "starmetal_ore", "astral_crystal_cluster", "starmetal_block", "astral_bricks", "cracked_astral_bricks",
            "chiseled_astral_bricks", "astral_brick_stairs", "astral_brick_slab", "celestial_altar", "gravity_rune", "starfire_rune"]
    tag("block", "minecraft", "mineable/pickaxe", [sid(b) for b in pick])
    tag("block", "minecraft", "needs_iron_tool", [sid("starmetal_ore"), sid("starmetal_block")])
    tag("block", "minecraft", "needs_diamond_tool", [sid("celestial_altar")])
    tag("block", "minecraft", "beacon_base_blocks", [sid("starmetal_block")])
    tag("block", "minecraft", "stairs", [sid("astral_brick_stairs")])
    tag("block", "minecraft", "slabs", [sid("astral_brick_slab")])
    tag("block", "minecraft", "impermeable", [sid("starglass")])
    tag("block", NS, "meteor_proof", ["minecraft:bedrock", "minecraft:obsidian", "minecraft:crying_obsidian", "minecraft:barrier",
                                      "minecraft:end_portal_frame", "minecraft:reinforced_deepslate", "minecraft:spawner",
                                      sid("celestial_altar"), sid("vault_seal")])

    tag("item", NS, "starmetal_repair_materials", [sid("starmetal_ingot")])
    tag("item", NS, "eclipse_repair_materials", [sid("sovereign_heart"), sid("starmetal_block")])
    tag("item", NS, "starling_food", [sid("stardust")])
    tag("item", "minecraft", "beacon_payment_items", [sid("starmetal_ingot")])
    tag("item", "minecraft", "swords", [sid("starmetal_sword"), sid("eclipse_blade"), sid("void_scythe")])
    tag("item", "minecraft", "pickaxes", [sid("starmetal_pickaxe"), sid("meteor_hammer")])
    tag("item", "minecraft", "axes", [sid("starmetal_axe")])
    tag("item", "minecraft", "shovels", [sid("starmetal_shovel")])
    tag("item", "minecraft", "hoes", [sid("starmetal_hoe")])
    tag("item", "minecraft", "head_armor", [sid("starmetal_helmet"), sid("eclipse_crown")])
    tag("item", "minecraft", "chest_armor", [sid("starmetal_chestplate"), sid("nebula_cloak")])
    tag("item", "minecraft", "leg_armor", [sid("starmetal_leggings")])
    tag("item", "minecraft", "foot_armor", [sid("starmetal_boots"), sid("comet_boots")])
    tag("item", "minecraft", "enchantable/bow", [sid("constellation_bow")])
    tag("item", "minecraft", "enchantable/durability", [sid("constellation_bow"), sid("starcaller_staff"), sid("gravity_gauntlet"), sid("rift_pearl")])
    tag("item", "minecraft", "stairs", [sid("astral_brick_stairs")])
    tag("item", "minecraft", "slabs", [sid("astral_brick_slab")])

    tag("entity_type", NS, "voidborn", [sid("void_stalker"), sid("astral_wraith"), sid("eclipse_sovereign")])
    tag("entity_type", NS, "sovereign_allies", [sid("eclipse_sovereign"), sid("void_stalker"), sid("star_mite"), sid("eclipse_crystal")])
    tag("worldgen/structure", NS, "observatories", [sid("fallen_observatory")])
    tag("worldgen/biome", NS, "has_observatory", [f"minecraft:{b}" for b in (
        "plains", "sunflower_plains", "meadow", "savanna", "savanna_plateau", "desert", "snowy_plains", "taiga", "snowy_taiga", "forest",
        "birch_forest", "cherry_grove", "flower_forest", "badlands", "wooded_badlands", "sparse_jungle", "old_growth_birch_forest", "windswept_savanna")])


# ----------------------------------------------------------------------------------------------------------------
# Worldgen, damage types, advancements
# ----------------------------------------------------------------------------------------------------------------

def worldgen():
    write(f"{D}/worldgen/structure/fallen_observatory.json", {
        "type": sid("observatory"), "biomes": "#starforged:has_observatory", "step": "surface_structures",
        "terrain_adaptation": "none",
        "spawn_overrides": {"monster": {"bounding_box": "piece", "spawns": [
            {"type": sid("astral_wraith"), "weight": 12, "minCount": 1, "maxCount": 2},
            {"type": sid("void_stalker"), "weight": 4, "minCount": 1, "maxCount": 1},
            {"type": sid("star_mite"), "weight": 6, "minCount": 2, "maxCount": 4}]}}})
    write(f"{D}/worldgen/structure_set/fallen_observatories.json", {
        "placement": {"type": "minecraft:random_spread", "salt": 192837465, "separation": 12, "spacing": 34},
        "structures": [{"structure": sid("fallen_observatory"), "weight": 1}]})
    write(f"{D}/worldgen/configured_feature/meteor_crater.json", {"type": sid("meteor_crater"), "config": {}})
    write(f"{D}/worldgen/placed_feature/meteor_crater.json", {"feature": sid("meteor_crater"), "placement": [
        {"type": "minecraft:rarity_filter", "chance": 70}, {"type": "minecraft:in_square"},
        {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"}, {"type": "minecraft:biome"}]})
    write(f"{D}/forge/biome_modifier/meteor_craters.json", {"type": "forge:add_features", "biomes": "#minecraft:is_overworld",
                                                          "features": sid("meteor_crater"), "step": "local_modifications"})
    write(f"{D}/forge/biome_modifier/void_stalker_spawns.json", {"type": "forge:add_spawns", "biomes": "#minecraft:is_overworld",
                                                               "spawners": [{"type": sid("void_stalker"), "weight": 5, "minCount": 1, "maxCount": 1}]})

    for name, exhaustion, effects in (("meteor", 0.1, "burning"), ("eclipse_beam", 0.1, "burning"), ("singularity", 0.1, None),
                                      ("starlight", 0.1, "burning"), ("void_rend", 0.1, None), ("shockwave", 0.1, None)):
        d = {"exhaustion": exhaustion, "message_id": f"starforged.{name}", "scaling": "when_caused_by_living_non_player"}
        if effects:
            d["effects"] = effects
        write(f"{D}/damage_type/{name}.json", d)


def advancements(adv):
    def a(key, parent, icon, criteria, frame="task", hidden=False, xp=None, background=None):
        display = {"title": {"translate": f"advancements.starforged.{key}.title"},
                   "description": {"translate": f"advancements.starforged.{key}.description"},
                   "icon": {"id": icon}, "frame": frame, "show_toast": True, "announce_to_chat": True, "hidden": hidden}
        if background:
            display["background"] = background
            display["show_toast"] = False
            display["announce_to_chat"] = False
        d = {"display": display, "criteria": criteria, "requirements": [list(criteria.keys())]}
        if len(criteria) > 1:
            d["requirements"] = [list(criteria.keys())]
        if parent:
            d["parent"] = sid(parent)
        if xp:
            d["rewards"] = {"experience": xp}
        write(f"{D}/advancement/{key}.json", d)

    def has(*items):
        return {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": list(items) if len(items) > 1 else items[0]}]}}

    def kill(entity):
        return {"trigger": "minecraft:player_killed_entity", "conditions": {"entity": [
            {"condition": "minecraft:entity_properties", "entity": "this", "predicate": {"minecraft:entity_type": entity}}]}}

    def tame(entity):
        return {"trigger": "minecraft:tame_animal", "conditions": {"entity": [
            {"condition": "minecraft:entity_properties", "entity": "this", "predicate": {"minecraft:entity_type": entity}}]}}

    a("root", None, sid("starcaller_staff"), {"tick": {"trigger": "minecraft:tick"}}, background="starforged:block/astral_bricks")
    a("stardust", "root", sid("stardust"), {"dust": has(sid("stardust")), "metal": has(sid("raw_starmetal"))})
    a("ingot", "stardust", sid("starmetal_ingot"), {"ingot": has(sid("starmetal_ingot"))})
    a("staff", "ingot", sid("starcaller_staff"), {"staff": has(sid("starcaller_staff"))})
    a("stalker", "stardust", sid("void_essence"), {"kill": kill(sid("void_stalker"))})
    a("observatory", "root", sid("chiseled_astral_bricks"), {"enter": {"trigger": "minecraft:location", "conditions": {"player": [
        {"condition": "minecraft:entity_properties", "entity": "this", "predicate": {"minecraft:location": {"structures": sid("fallen_observatory")}}}]}}})
    a("mimic", "observatory", "minecraft:chest", {"kill": kill(sid("mimic"))}, frame="goal")
    a("golem", "observatory", sid("celestial_core"), {"kill": kill(sid("astral_golem"))}, frame="goal")
    a("starling", "root", sid("astral_egg"), {"tame": tame(sid("starling"))})
    a("nebula_ray", "starling", sid("nebula_ray_spawn_egg"), {"tame": tame(sid("nebula_ray"))}, frame="goal")
    a("sigil", "golem", sid("eclipse_sigil"), {"sigil": has(sid("eclipse_sigil"))})
    a("sovereign", "sigil", sid("sovereign_heart"), {"kill": kill(sid("eclipse_sovereign"))}, frame="challenge", xp=500)
    a("blade", "sovereign", sid("eclipse_blade"), {"blade": has(sid("eclipse_blade"))})
    # Fix requirements for "any of" criteria (stardust OR starmetal).
    path = f"{D}/advancement/stardust.json"
    d = json.load(open(path))
    d["requirements"] = [["dust", "metal"]]
    write(path, d)


def main():
    if os.path.isdir(f"{A}/models"):
        shutil.rmtree(f"{A}/models")
    blocks()
    items()
    events = equipment_particles_sounds()
    adv = lang(events)
    recipes()
    loot_tables()
    tags()
    worldgen()
    advancements(adv)
    print("data written")


if __name__ == "__main__":
    main()
