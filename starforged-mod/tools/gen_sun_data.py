#!/usr/bin/env python3
"""
Sunforged data/asset generator. Run AFTER gen_data.py - it adds the Sunforged blocks, items, equipment, particles,
sounds, lang, recipes, loot, tags, damage types, advancements and the whole Sunlands dimension (dimension type,
noise settings, four biomes, features, the Sun Temple structure), merging into files gen_data.py wrote.
"""
import json
import os

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


def read(path):
    with open(path) as f:
        return json.load(f)


def sid(name):
    return f"{NS}:{name}"


# ----------------------------------------------------------------------------------------------------------------
# Block models & states
# ----------------------------------------------------------------------------------------------------------------

CUBE = ["scorchstone", "sunstone_ore", "sunsand", "sunsteel_block", "sunbaked_bricks", "cracked_sunbaked_bricks",
        "chiseled_sunbaked_bricks", "sun_lantern"]


def cube(name, tex=None):
    write(f"{A}/models/block/{name}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": sid(f"block/{tex or name}")}})


def simple_state(name, model=None):
    write(f"{A}/blockstates/{name}.json", {"variants": {"": {"model": sid(f"block/{model or name}")}}})


def elements(boxes, side="#side", top="#top", bottom="#bottom"):
    out = []
    for frm, to in boxes:
        faces = {f: {"texture": side} for f in ("north", "south", "east", "west")}
        faces["up"] = {"texture": top}
        faces["down"] = {"texture": bottom}
        out.append({"from": frm, "to": to, "faces": faces})
    return out


def blocks():
    for b in CUBE:
        cube(b)
        simple_state(b)
    cube("sun_seal")
    write(f"{A}/blockstates/sun_seal.json", {"variants": {"opening=false": {"model": sid("block/sun_seal")},
                                                          "opening=true": {"model": sid("block/sun_seal")}}})
    write(f"{A}/models/block/solar_glass.json", {"parent": "minecraft:block/cube_all",
                                                  "textures": {"all": {"sprite": sid("block/solar_glass"), "force_translucent": True}}})
    simple_state("solar_glass")
    write(f"{A}/models/block/ashen_soil.json", {"parent": "minecraft:block/cube_bottom_top", "textures": {
        "top": sid("block/ashen_soil_top"), "side": sid("block/ashen_soil_side"), "bottom": sid("block/ashen_soil_bottom")}})
    simple_state("ashen_soil")

    write(f"{A}/models/block/ember_crystal_cluster.json", {"parent": "minecraft:block/cross",
                                                            "textures": {"cross": sid("block/ember_crystal_cluster")}})
    rot = {"down": {"x": 180}, "east": {"x": 90, "y": 90}, "north": {"x": 90}, "south": {"x": 90, "y": 180}, "up": {}, "west": {"x": 90, "y": 270}}
    write(f"{A}/blockstates/ember_crystal_cluster.json", {"variants": {
        f"facing={k}": dict({"model": sid("block/ember_crystal_cluster")}, **v) for k, v in rot.items()}})
    write(f"{A}/models/block/sunbloom.json", {"parent": "minecraft:block/cross", "textures": {"cross": sid("block/sunbloom")}})
    simple_state("sunbloom")

    tex = sid("block/sunbaked_bricks")
    for suffix, parent in (("", "stairs"), ("_inner", "inner_stairs"), ("_outer", "outer_stairs")):
        write(f"{A}/models/block/sunbaked_brick_stairs{suffix}.json",
              {"parent": f"minecraft:block/{parent}", "textures": {"bottom": tex, "side": tex, "top": tex}})
    stairs = json.load(open(os.path.join(ROOT, "tools/stone_brick_stairs_blockstate.json")))
    write(f"{A}/blockstates/sunbaked_brick_stairs.json",
          json.loads(json.dumps(stairs).replace("minecraft:block/stone_brick_stairs", sid("block/sunbaked_brick_stairs"))))
    write(f"{A}/models/block/sunbaked_brick_slab.json", {"parent": "minecraft:block/slab", "textures": {"bottom": tex, "side": tex, "top": tex}})
    write(f"{A}/models/block/sunbaked_brick_slab_top.json", {"parent": "minecraft:block/slab_top", "textures": {"bottom": tex, "side": tex, "top": tex}})
    write(f"{A}/blockstates/sunbaked_brick_slab.json", {"variants": {
        "type=bottom": {"model": sid("block/sunbaked_brick_slab")},
        "type=double": {"model": sid("block/sunbaked_bricks")},
        "type=top": {"model": sid("block/sunbaked_brick_slab_top")}}})

    # Solar brazier: foot, post and bowl.
    for lit in (False, True):
        name = "solar_brazier_lit" if lit else "solar_brazier"
        write(f"{A}/models/block/{name}.json", {
            "parent": "minecraft:block/block",
            "textures": {"particle": sid("block/solar_brazier_side"), "side": sid("block/solar_brazier_side"),
                         "top": sid("block/solar_brazier_top_lit" if lit else "block/solar_brazier_top"), "bottom": sid("block/solar_brazier_side")},
            "elements": elements([([5, 0, 5], [11, 2, 11]), ([6.5, 2, 6.5], [9.5, 9, 9.5]), ([2, 9, 2], [14, 13, 14])])})
    write(f"{A}/blockstates/solar_brazier.json", {"variants": {"lit=false": {"model": sid("block/solar_brazier")},
                                                               "lit=true": {"model": sid("block/solar_brazier_lit")}}})

    # Sunfire vent: grate top over sunbaked brick.
    for stage in ("sunfire_vent", "sunfire_vent_warning", "sunfire_vent_active"):
        write(f"{A}/models/block/{stage}.json", {"parent": "minecraft:block/cube_bottom_top", "textures": {
            "top": sid(f"block/{stage}"), "side": sid("block/sunbaked_bricks"), "bottom": sid("block/sunbaked_bricks")}})
    variants = {}
    for phase in range(14):
        variants[f"phase={phase}"] = {"model": sid("block/" + ("sunfire_vent" if phase == 0 else "sunfire_vent_warning" if phase == 1 else "sunfire_vent_active"))}
    write(f"{A}/blockstates/sunfire_vent.json", {"variants": variants})

    side, top, bottom = sid("block/sun_altar_side"), sid("block/sun_altar_top"), sid("block/sun_altar_bottom")
    write(f"{A}/models/block/sun_altar.json", {
        "parent": "minecraft:block/block", "textures": {"particle": side, "side": side, "top": top, "bottom": bottom},
        "elements": elements([([0, 0, 0], [16, 3, 16]), ([2, 3, 2], [14, 10, 14]), ([0, 10, 0], [16, 14, 16])])})
    simple_state("sun_altar")

    # The pool's sides are culled against neighbouring gateway blocks (SolarGatewayBlock.skipRendering),
    # so a 3x3 gateway reads as one sheet of light instead of nine boxes.
    g = sid("block/solar_gateway")
    pool = elements([([0, 0, 0], [16, 12, 16])])
    for face in ("north", "south", "east", "west", "down"):
        pool[0]["faces"][face]["cullface"] = face
    write(f"{A}/models/block/solar_gateway.json", {
        "parent": "minecraft:block/block", "textures": {"particle": g, "side": g, "top": g, "bottom": g},
        "elements": pool})
    simple_state("solar_gateway")


# ----------------------------------------------------------------------------------------------------------------
# Items
# ----------------------------------------------------------------------------------------------------------------

GENERATED = ["raw_sunsteel", "sunsteel_ingot", "ember_shard", "solar_essence", "phoenix_feather", "sun_heart", "solar_key", "sunfire_sigil",
             "phoenix_egg", "sunsteel_helmet", "sunsteel_chestplate", "sunsteel_leggings", "sunsteel_boots", "phoenix_mantle", "magma_treads",
             "solar_crown", "sunburst_flask", "solar_flare", "cinder_chakram"]
HANDHELD = ["sunsteel_sword", "sunsteel_pickaxe", "sunsteel_axe", "sunsteel_shovel", "sunsteel_hoe", "solar_lance", "flare_greatsword",
            "helios_scepter"]
EGGS = ["cinder_imp", "magma_crawler", "ember_hound", "ashen_knight", "solar_phoenix", "sun_warden"]
BLOCK_ITEMS = CUBE + ["solar_glass", "ashen_soil", "sunbaked_brick_stairs", "sunbaked_brick_slab", "solar_brazier", "sunfire_vent",
                      "sun_altar", "sun_seal"]


def model_item(name, model):
    write(f"{A}/items/{name}.json", {"model": {"type": "minecraft:model", "model": model}})


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
    for name, tex in (("ember_crystal_cluster", "ember_crystal_cluster"), ("sunbloom", "sunbloom")):
        write(f"{A}/models/item/{name}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": sid(f"block/{tex}")}})
        model_item(name, sid(f"item/{name}"))

    write(f"{A}/models/item/phoenix_bow.json", {"parent": "minecraft:item/bow", "textures": {"layer0": sid("item/phoenix_bow")}})
    for i in range(3):
        write(f"{A}/models/item/phoenix_bow_pulling_{i}.json",
              {"parent": "minecraft:item/bow", "textures": {"layer0": sid(f"item/phoenix_bow_pulling_{i}")}})
    write(f"{A}/items/phoenix_bow.json", {"model": {
        "type": "minecraft:condition", "property": "minecraft:using_item",
        "on_false": {"type": "minecraft:model", "model": sid("item/phoenix_bow")},
        "on_true": {"type": "minecraft:range_dispatch", "property": "minecraft:use_duration", "scale": 0.05,
                    "fallback": {"type": "minecraft:model", "model": sid("item/phoenix_bow_pulling_0")},
                    "entries": [{"threshold": 0.65, "model": {"type": "minecraft:model", "model": sid("item/phoenix_bow_pulling_1")}},
                                {"threshold": 0.9, "model": {"type": "minecraft:model", "model": sid("item/phoenix_bow_pulling_2")}}]}}})


def equipment_particles():
    write(f"{A}/equipment/sunsteel.json", {"layers": {"humanoid": [{"texture": sid("sunsteel")}], "humanoid_leggings": [{"texture": sid("sunsteel")}]}})
    write(f"{A}/equipment/phoenix.json", {"layers": {"humanoid": [{"texture": sid("phoenix")}], "wings": [{"texture": sid("phoenix")}]}})
    write(f"{A}/equipment/magma.json", {"layers": {"humanoid": [{"texture": sid("magma")}]}})
    write(f"{A}/equipment/solar.json", {"layers": {"humanoid": [{"texture": sid("solar")}]}})
    write(f"{A}/particles/solar_spark.json", {"textures": [sid(f"ember_{i}") for i in range(4)]})
    write(f"{A}/particles/ash_flake.json", {"textures": [sid(f"mote_{i}") for i in range(4)]})


SOUNDS = {
    "block.solar_brazier.ignite": ("sun_brazier_ignite", "Brazier roars to life"),
    "block.sun_seal.open": ("sun_seal_open", "Sun Seal burns away"),
    "block.sunfire_vent.erupt": ("sun_vent_erupt", "Sunfire vent erupts"),
    "block.sun_altar.activate": ("sun_altar_activate", "Sun Altar awakens"),
    "block.solar_gateway.travel": ("sun_gateway_travel", "Solar Gateway hums"),
    "item.solar_key.forge": ("sun_gateway_form", "A Solar Gateway opens"),
    "item.solar_lance.dash": ("sun_lance_dash", "Lance charge"),
    "item.flare_greatsword.flare": ("sun_greatsword_flare", "Solar flare"),
    "item.phoenix_bow.shoot": ("sun_bow_shoot", "Phoenix arrow"),
    "item.helios_scepter.summon": ("sun_scepter_summon", "A small sun ignites"),
    "item.cinder_chakram.throw": ("sun_chakram_throw", "Chakram whirls"),
    "item.sunburst_flask.burst": ("sun_flask_burst", "Sunburst"),
    "item.phoenix_mantle.rebirth": ("sun_rebirth", "Rebirth!"),
    "item.phoenix_egg.hatch": ("sun_egg_hatch", "Phoenix hatches"),
    "entity.cinder_imp.ambient": ("imp_ambient", "Cinder Imp cackles"),
    "entity.cinder_imp.hurt": ("imp_hurt", "Cinder Imp shrieks"),
    "entity.cinder_imp.death": ("imp_death", "Cinder Imp fizzles out"),
    "entity.magma_crawler.ambient": ("crawler_ambient", "Magma Crawler hisses"),
    "entity.magma_crawler.death": ("crawler_death", "Magma Crawler dies"),
    "entity.ember_hound.ambient": ("hound_ambient", "Ember Hound pants"),
    "entity.ember_hound.growl": ("hound_growl", "Ember Hound growls"),
    "entity.ember_hound.death": ("hound_death", "Ember Hound dies"),
    "entity.ashen_knight.ambient": ("knight_ambient", "Ashen Knight rasps"),
    "entity.ashen_knight.block": ("knight_block", "Shield blocks"),
    "entity.ashen_knight.death": ("knight_death", "Ashen Knight crumbles"),
    "entity.solar_phoenix.ambient": ("phoenix_ambient", "Phoenix sings"),
    "entity.solar_phoenix.cry": ("phoenix_cry", "Phoenix cries"),
    "entity.sun_warden.roar": ("warden_roar", "The Sun Warden roars"),
    "entity.sun_warden.hurt": ("warden_hurt", "The Sun Warden groans"),
    "entity.sun_warden.death": ("warden_death", "The Sun Warden falls"),
    "entity.sun_warden.flare": ("warden_flare", "Sunfall"),
    "entity.sun_warden.beam": ("warden_beam", "Solar lance"),
    "entity.sun_warden.pillar": ("warden_pillar", "Flame pillars"),
    "entity.sun_warden.slam": ("warden_slam", "Molten slam"),
    "entity.sun_warden.supernova": ("warden_supernova", "Supernova"),
    "entity.solar_pylon.shatter": ("pylon_shatter", "Solar Pylon shatters"),
}


def sounds():
    path = f"{A}/sounds.json"
    data = read(path)
    for event, (file, subtitle) in SOUNDS.items():
        data[event] = {"subtitle": f"subtitles.starforged.{event}", "sounds": [{"name": sid(file)}]}
    write(path, data)


# ----------------------------------------------------------------------------------------------------------------
# Lang
# ----------------------------------------------------------------------------------------------------------------

def lang():
    path = f"{A}/lang/en_us.json"
    L = read(path)
    L.update({
        "itemGroup.starforged.sunforged": "Sunforged",
        # Blocks
        "block.starforged.scorchstone": "Scorchstone",
        "block.starforged.sunstone_ore": "Sunstone Ore",
        "block.starforged.sunsand": "Sunsand",
        "block.starforged.ashen_soil": "Ashen Soil",
        "block.starforged.ember_crystal_cluster": "Ember Crystal Cluster",
        "block.starforged.sunbloom": "Sunbloom",
        "block.starforged.sunsteel_block": "Block of Sunsteel",
        "block.starforged.sunbaked_bricks": "Sunbaked Bricks",
        "block.starforged.cracked_sunbaked_bricks": "Cracked Sunbaked Bricks",
        "block.starforged.chiseled_sunbaked_bricks": "Chiseled Sunbaked Bricks",
        "block.starforged.sunbaked_brick_stairs": "Sunbaked Brick Stairs",
        "block.starforged.sunbaked_brick_slab": "Sunbaked Brick Slab",
        "block.starforged.solar_glass": "Solar Glass",
        "block.starforged.sun_lantern": "Sun Lantern",
        "block.starforged.solar_brazier": "Solar Brazier",
        "block.starforged.solar_brazier.hint": "✦ A cold brazier. Light it with Flint and Steel, a Fire Charge or an Ember Shard.",
        "block.starforged.solar_brazier.progress": "✦ %s of %s braziers burn...",
        "block.starforged.solar_brazier.done": "✦ Every brazier blazes - the Sun Seal burns away!",
        "block.starforged.sun_seal": "Sun Seal",
        "block.starforged.sun_seal.hint": "✦ A wall of solid sunlight. Perhaps the braziers of this hall hold the answer...",
        "block.starforged.sunfire_vent": "Sunfire Vent",
        "block.starforged.sun_altar": "Sun Altar",
        "block.starforged.sun_altar.hint": "✦ The altar waits for a Sunfire Sigil.",
        "block.starforged.sun_altar.busy": "The Sun Warden is already awake.",
        "block.starforged.solar_gateway": "Solar Gateway",
        "block.starforged.bed.sunlands": "The sun never sets in the Sunlands - there is no sleeping here.",
        # Items
        "item.starforged.raw_sunsteel": "Raw Sunsteel",
        "item.starforged.sunsteel_ingot": "Sunsteel Ingot",
        "item.starforged.ember_shard": "Ember Shard",
        "item.starforged.ember_shard.desc0": "Crystallised sunfire. Still warm.",
        "item.starforged.solar_essence": "Solar Essence",
        "item.starforged.solar_essence.desc0": "The living flame of a Sunlands creature.",
        "item.starforged.phoenix_feather": "Phoenix Feather",
        "item.starforged.phoenix_feather.desc0": "It never stops smouldering.",
        "item.starforged.sun_heart": "Heart of the Sun",
        "item.starforged.sun_heart.desc0": "The Sun Warden's core - a sliver of the last star.",
        "item.starforged.sun_heart.desc1": "Repairs the Warden's relics.",
        "item.starforged.solar_key": "Solar Key",
        "item.starforged.solar_key.desc0": "Forged from a Sovereign's stolen light,",
        "item.starforged.solar_key.desc1": "it still remembers the way home to the Sun.",
        "item.starforged.solar_key.opened": "✦ A Solar Gateway blazes open! Step in to cross to the Sunlands.",
        "item.starforged.solar_key.wrong_world": "The key only answers in the Overworld or the Sunlands.",
        "item.starforged.sunfire_sigil": "Sunfire Sigil",
        "item.starforged.sunfire_sigil.desc0": "A seal of the Sun Temple.",
        "item.starforged.sunfire_sigil.desc1": "Offer it on the Sun Altar atop a Sun Temple",
        "item.starforged.sunfire_sigil.desc2": "to wake the Sun Warden.",
        "item.starforged.phoenix_egg": "Phoenix Egg",
        "item.starforged.phoenix_egg.desc0": "Hot to the touch. Something inside is singing.",
        "item.starforged.phoenix_egg.desc1": "Use to hatch a rideable Solar Phoenix.",
        "item.starforged.phoenix_egg.hatched": "✦ A Solar Phoenix bursts from the egg in a blaze of light!",
        "item.starforged.sunsteel_sword": "Sunsteel Sword",
        "item.starforged.sunsteel_pickaxe": "Sunsteel Pickaxe",
        "item.starforged.sunsteel_axe": "Sunsteel Axe",
        "item.starforged.sunsteel_shovel": "Sunsteel Shovel",
        "item.starforged.sunsteel_hoe": "Sunsteel Hoe",
        "item.starforged.sunsteel_helmet": "Sunsteel Helmet",
        "item.starforged.sunsteel_chestplate": "Sunsteel Chestplate",
        "item.starforged.sunsteel_leggings": "Sunsteel Leggings",
        "item.starforged.sunsteel_boots": "Sunsteel Boots",
        "item.starforged.phoenix_mantle": "Phoenix Mantle",
        "item.starforged.magma_treads": "Magma Treads",
        "item.starforged.solar_crown": "Solar Crown",
        "item.starforged.solar_lance": "Solar Lance",
        "item.starforged.flare_greatsword": "Flare Greatsword",
        "item.starforged.phoenix_bow": "Phoenix Bow",
        "item.starforged.helios_scepter": "Helios Scepter",
        "item.starforged.cinder_chakram": "Cinder Chakram",
        "item.starforged.sunburst_flask": "Sunburst Flask",
        "item.starforged.solar_flare": "Solar Flare",
        # Abilities
        "ability.starforged.sunsteel_set.title": "Full Set: Sunborn",
        "ability.starforged.sunsteel_set.text": "Immune to fire and lava, and anything that strikes you bursts into flame.",
        "ability.starforged.phoenix_mantle.title": "Rebirth",
        "ability.starforged.phoenix_mantle.text": "Glide without rockets. Once every five minutes, cheat death in an explosion of fire.",
        "ability.starforged.phoenix_mantle.rebirth": "✦ REBIRTH! The Phoenix Mantle drags you back from death!",
        "ability.starforged.magma_treads.title": "Lavawalker",
        "ability.starforged.magma_treads.text": "Lava hardens beneath your feet - walk across it. Sneak to sink. Fire immune.",
        "ability.starforged.solar_crown.title": "Radiance",
        "ability.starforged.solar_crown.text": "Nearby enemies smoulder and burn. Fire immunity and Night Vision.",
        "ability.starforged.solar_lance.title": "Right-click: Solar Charge",
        "ability.starforged.solar_lance.text": "Dash forward as a streak of sunfire, skewering everything in your path.",
        "ability.starforged.solar_flare.title": "Right-click: Solar Flare",
        "ability.starforged.solar_flare.text": "Hurl an exploding ball of sunfire.",
        "ability.starforged.solar_eruption.title": "Sneak + Right-click: Eruption",
        "ability.starforged.solar_eruption.text": "Plant the blade and erupt in a ring of sunfire.",
        "ability.starforged.phoenix_bow.title": "Phoenix Shot",
        "ability.starforged.phoenix_bow.text": "Needs no arrows. Homing phoenixes of flame; a full draw looses three that hunt separate foes.",
        "ability.starforged.helios_scepter.title": "Right-click: Miniature Sun",
        "ability.starforged.helios_scepter.text": "Summon a sun that orbits you for 20 seconds, lancing enemies with sunbeams.",
        "ability.starforged.cinder_chakram.title": "Right-click: Throw",
        "ability.starforged.cinder_chakram.text": "A ring of fire that leaps between up to four enemies, then flies back to you.",
        "ability.starforged.sunburst_flask.title": "Throw: Sunburst",
        "ability.starforged.sunburst_flask.text": "A blinding solar flash that burns, dazzles and slows.",
        "ability.starforged.solar_key.title": "Use on the ground: Solar Gateway",
        "ability.starforged.solar_key.text": "Raises a gateway to the Sunlands (Overworld or Sunlands only).",
        # Entities
        "entity.starforged.cinder_imp": "Cinder Imp",
        "entity.starforged.magma_crawler": "Magma Crawler",
        "entity.starforged.ember_hound": "Ember Hound",
        "entity.starforged.ember_hound.wait": "Your Ember Hound sits and waits.",
        "entity.starforged.ember_hound.follow": "Your Ember Hound follows you.",
        "entity.starforged.ashen_knight": "Ashen Knight",
        "entity.starforged.solar_phoenix": "Solar Phoenix",
        "entity.starforged.sun_warden": "The Sun Warden",
        "entity.starforged.sun_warden.title": "Last Light of the Sky",
        "entity.starforged.sun_warden.supernova": "SUPERNOVA",
        "entity.starforged.sun_warden.supernova_sub": "Shatter the Solar Pylons!",
        "entity.starforged.sun_warden.countdown": "✦ The Supernova ignites in %s seconds!",
        "entity.starforged.sun_warden.immune": "The Sun Warden is shielded by its Solar Pylons!",
        "entity.starforged.sun_warden.stunned": "✦ The pylons are shattered! The Warden kneels, burning out - STRIKE NOW!",
        "entity.starforged.sun_warden.detonated": "✦ The Supernova scorches the summit!",
        "entity.starforged.sun_warden.enraged": "✦ THE SUN BURNS WHITE-HOT. The Warden is enraged!",
        "entity.starforged.sun_warden.dying": "The Warden's halo cracks and flares...",
        "entity.starforged.sun_warden.defeated": "✦ The Sun Warden is vanquished. The last light is yours.",
        "entity.starforged.sun_warden.retreat": "The Warden sinks back into the temple stone... the Sigil lies on the altar.",
        "entity.starforged.solar_pylon": "Solar Pylon",
        "entity.starforged.solar_flare": "Solar Flare",
        "entity.starforged.cinder_chakram": "Cinder Chakram",
        "entity.starforged.sunburst_flask": "Sunburst Flask",
        "entity.starforged.helios_orb": "Helios Orb",
        # Events & commands
        "event.starforged.sunlands.title": "✦ THE SUNLANDS ✦",
        "event.starforged.sunlands.subtitle": "Where the last star still burns",
        "event.starforged.sun_summon.altar": "The Sun Altar drinks the Sigil. The temple begins to shake...",
        "event.starforged.sun_summon.title": "THE SUN AWAKENS",
        "event.starforged.sun_summon.subtitle": "Its guardian rises",
        "commands.starforged.suntemple": "✦ A Sun Temple rises at %s, %s, %s",
        "commands.starforged.sunwarden": "✦ The Sun Altar blazes - the Warden is waking...",
        "commands.starforged.sunkit": "✦ You are Sunforged. (Kit given)",
        # Biomes
        "biome.starforged.ember_plains": "Ember Plains",
        "biome.starforged.gilded_dunes": "Gilded Dunes",
        "biome.starforged.basalt_spires": "Basalt Spires",
        "biome.starforged.ember_caldera": "Ember Caldera",
        # Death messages
        "death.attack.starforged.sunfire": "%1$s was burned to ash by sunfire",
        "death.attack.starforged.sunfire.player": "%1$s was burned to ash by %2$s's sunfire",
        "death.attack.starforged.solar_beam": "%1$s was vaporised by a beam of sunlight",
        "death.attack.starforged.solar_beam.player": "%1$s was vaporised by %2$s's sunbeam",
    })
    for mob in EGGS:
        L[f"item.starforged.{mob}_spawn_egg"] = L[f"entity.starforged.{mob}"].replace("The ", "") + " Spawn Egg"
    for event, (_, subtitle) in SOUNDS.items():
        L[f"subtitles.starforged.{event}"] = subtitle
    for key, (title, desc) in ADVANCEMENTS_TEXT.items():
        L[f"advancements.starforged.sun.{key}.title"] = title
        L[f"advancements.starforged.sun.{key}.description"] = desc
    write(path, L)


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
    I = sid("sunsteel_ingot")
    rod = "minecraft:blaze_rod"
    cook("sunsteel_ingot", sid("raw_sunsteel"), I, 1.2)
    cook("sunsteel_ingot_ore", sid("sunstone_ore"), I, 1.2)
    shaped("sunsteel_block", ["###", "###", "###"], {"#": I}, sid("sunsteel_block"), category="building")
    shapeless("sunsteel_ingot_from_block", [sid("sunsteel_block")], I, 9)
    shaped("sunsteel_sword", ["X", "X", "#"], {"X": I, "#": rod}, sid("sunsteel_sword"), category="equipment")
    shaped("sunsteel_pickaxe", ["XXX", " # ", " # "], {"X": I, "#": rod}, sid("sunsteel_pickaxe"), category="equipment")
    shaped("sunsteel_axe", ["XX", "X#", " #"], {"X": I, "#": rod}, sid("sunsteel_axe"), category="equipment")
    shaped("sunsteel_shovel", ["X", "#", "#"], {"X": I, "#": rod}, sid("sunsteel_shovel"), category="equipment")
    shaped("sunsteel_hoe", ["XX", " #", " #"], {"X": I, "#": rod}, sid("sunsteel_hoe"), category="equipment")
    shaped("sunsteel_helmet", ["XXX", "X X"], {"X": I}, sid("sunsteel_helmet"), category="equipment")
    shaped("sunsteel_chestplate", ["X X", "XXX", "XXX"], {"X": I}, sid("sunsteel_chestplate"), category="equipment")
    shaped("sunsteel_leggings", ["XXX", "X X", "X X"], {"X": I}, sid("sunsteel_leggings"), category="equipment")
    shaped("sunsteel_boots", ["X X", "X X"], {"X": I}, sid("sunsteel_boots"), category="equipment")

    cook("sunbaked_bricks", sid("sunsand"), sid("sunbaked_bricks"), 0.1, ("smelting",))
    shaped("sunbaked_bricks_from_scorchstone", ["##", "##"], {"#": sid("scorchstone")}, sid("sunbaked_bricks"), 4, "building")
    cook("cracked_sunbaked_bricks", sid("sunbaked_bricks"), sid("cracked_sunbaked_bricks"), 0.1, ("smelting",))
    shaped("chiseled_sunbaked_bricks", ["#", "#"], {"#": sid("sunbaked_brick_slab")}, sid("chiseled_sunbaked_bricks"), category="building")
    shaped("sunbaked_brick_stairs", ["#  ", "## ", "###"], {"#": sid("sunbaked_bricks")}, sid("sunbaked_brick_stairs"), 4, "building")
    shaped("sunbaked_brick_slab", ["###"], {"#": sid("sunbaked_bricks")}, sid("sunbaked_brick_slab"), 6, "building")
    shaped("solar_glass", ["GGG", "GEG", "GGG"], {"G": "minecraft:glass", "E": sid("ember_shard")}, sid("solar_glass"), 8, "building")
    shaped("sun_lantern", [" E ", "EGE", " E "], {"E": sid("ember_shard"), "G": "minecraft:glowstone"}, sid("sun_lantern"), 2, "building")
    shaped("solar_brazier", ["G G", "GEG", " G "], {"G": "minecraft:gold_ingot", "E": sid("ember_shard")}, sid("solar_brazier"), category="building")
    shaped("sunfire_vent", ["BIB", "IEI", "BIB"], {"B": sid("sunbaked_bricks"), "I": "minecraft:iron_bars", "E": sid("ember_shard")},
           sid("sunfire_vent"), 4, "redstone")
    shaped("sun_altar", [" A ", "CSC", "SSS"], {"A": sid("solar_essence"), "C": sid("chiseled_sunbaked_bricks"), "S": sid("sunsteel_block")},
           sid("sun_altar"), category="building")

    shaped("solar_key", ["DSD", "SHS", "DSD"], {"D": sid("stardust"), "S": sid("starmetal_block"), "H": sid("sovereign_heart")},
           sid("solar_key"), category="equipment")
    shaped("sunfire_sigil", ["EAE", "ABA", "EAE"], {"E": sid("ember_shard"), "A": sid("solar_essence"), "B": sid("sunsteel_block")},
           sid("sunfire_sigil"))
    shaped("solar_lance", [" EI", " RE", "R  "], {"E": sid("ember_shard"), "I": I, "R": rod}, sid("solar_lance"), category="equipment")
    shaped("phoenix_bow", [" IS", "P S", " IS"], {"I": I, "S": "minecraft:string", "P": sid("phoenix_feather")}, sid("phoenix_bow"),
           category="equipment")
    shaped("helios_scepter", [" AE", " IA", "I  "], {"A": sid("solar_essence"), "E": sid("ember_crystal_cluster"), "I": I},
           sid("helios_scepter"), category="equipment")
    shaped("cinder_chakram", [" I ", "IAI", " I "], {"I": I, "A": sid("solar_essence")}, sid("cinder_chakram"), category="equipment")
    shapeless("sunburst_flask", ["minecraft:glass_bottle", sid("ember_shard"), sid("solar_essence")], sid("sunburst_flask"), 2, "equipment")
    shaped("phoenix_mantle", ["FAF", "FCF", "FAF"], {"F": sid("phoenix_feather"), "A": sid("solar_essence"), "C": sid("sunsteel_chestplate")},
           sid("phoenix_mantle"), category="equipment")
    shapeless("magma_treads", [sid("sunsteel_boots"), "minecraft:magma_block", "minecraft:magma_block", sid("solar_essence")],
              sid("magma_treads"), category="equipment")


# ----------------------------------------------------------------------------------------------------------------
# Loot
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


def pool(entries, rolls=1, conditions=None):
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


def loot():
    for b in ("scorchstone", "sunsand", "ashen_soil", "sunsteel_block", "sunbaked_bricks", "cracked_sunbaked_bricks", "chiseled_sunbaked_bricks",
              "sunbaked_brick_stairs", "sun_lantern", "solar_brazier", "sunfire_vent", "sun_altar", "sunbloom"):
        write(f"{D}/loot_table/blocks/{b}.json", {"type": "minecraft:block", "pools": [
            {"rolls": 1, "entries": [{"type": "minecraft:item", "name": sid(b)}], "conditions": [{"condition": "minecraft:survives_explosion"}]}],
            "random_sequence": sid(f"blocks/{b}")})
    write(f"{D}/loot_table/blocks/sunbaked_brick_slab.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [
        {"type": "minecraft:item", "name": sid("sunbaked_brick_slab"), "functions": [
            {"function": "minecraft:set_count", "count": 2, "conditions": [{"condition": "minecraft:block_state_property",
                                                                            "block": sid("sunbaked_brick_slab"), "properties": {"type": "double"}}]},
            {"function": "minecraft:explosion_decay"}]}]}]})
    write(f"{D}/loot_table/blocks/solar_glass.json", {"type": "minecraft:block", "pools": [
        {"rolls": 1, "conditions": [silk()], "entries": [{"type": "minecraft:item", "name": sid("solar_glass")}]}]})
    write(f"{D}/loot_table/blocks/sunstone_ore.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [
        {"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "name": sid("sunstone_ore"), "conditions": [silk()]},
            {"type": "minecraft:item", "name": sid("raw_sunsteel"), "functions": [
                {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"},
                {"function": "minecraft:explosion_decay"}]}]}]}]})
    write(f"{D}/loot_table/blocks/ember_crystal_cluster.json", {"type": "minecraft:block", "pools": [{"rolls": 1, "entries": [
        {"type": "minecraft:alternatives", "children": [
            {"type": "minecraft:item", "name": sid("ember_crystal_cluster"), "conditions": [silk()]},
            {"type": "minecraft:item", "name": sid("ember_shard"), "functions": [
                {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 2, "max": 4}},
                {"function": "minecraft:apply_bonus", "enchantment": "minecraft:fortune", "formula": "minecraft:ore_drops"},
                {"function": "minecraft:explosion_decay"}]}]}]}]})

    def ent(name, pools):
        write(f"{D}/loot_table/entities/{name}.json", {"type": "minecraft:entity", "pools": pools, "random_sequence": sid(f"entities/{name}")})
    ent("cinder_imp", [pool([item_entry(sid("ember_shard"), 1, 0, 2, looting=True)]),
                       pool([item_entry(sid("solar_essence"))], conditions=chance(0.3)),
                       pool([item_entry("minecraft:blaze_powder", 1, 1, 2)], conditions=chance(0.4))])
    ent("magma_crawler", [pool([item_entry("minecraft:magma_cream", 1, 1, 2, looting=True)]),
                          pool([item_entry(sid("solar_essence"))], conditions=chance(0.6)),
                          pool([item_entry(sid("raw_sunsteel"))], conditions=chance(0.25))])
    ent("ember_hound", [pool([item_entry(sid("solar_essence"), 1, 1, 2, looting=True)]),
                        pool([item_entry("minecraft:coal", 1, 1, 3)])])
    ent("ashen_knight", [pool([item_entry(sid("solar_essence"), 1, 1, 3, looting=True)]),
                         pool([item_entry(sid("sunsteel_ingot"), 1, 1, 2)], conditions=chance(0.5)),
                         pool([item_entry(sid("sunsteel_sword"), 1, enchant=True)], conditions=chance(0.06))])
    ent("solar_phoenix", [pool([item_entry(sid("phoenix_feather"), 1, 1, 3, looting=True)])])
    ent("sun_warden", [pool([item_entry(sid("sun_heart"))]), pool([item_entry(sid("flare_greatsword"))]),
                       pool([item_entry(sid("solar_crown"))]), pool([item_entry(sid("phoenix_egg"))], conditions=chance(0.6)),
                       pool([item_entry(sid("sunsteel_ingot"), 1, 10, 18)]), pool([item_entry(sid("solar_essence"), 1, 8, 16)]),
                       pool([item_entry(sid("phoenix_feather"), 1, 2, 5)]),
                       pool([item_entry("minecraft:enchanted_golden_apple", 1, 1, 2)])])

    def chest(name, pools):
        write(f"{D}/loot_table/chests/{name}.json", {"type": "minecraft:chest", "pools": pools, "random_sequence": sid(f"chests/{name}")})
    chest("sun_temple_common", [
        pool([item_entry(sid("ember_shard"), 10, 2, 6), item_entry(sid("raw_sunsteel"), 7, 1, 4), item_entry(sid("solar_essence"), 6, 1, 3),
              item_entry("minecraft:gold_ingot", 6, 2, 6), item_entry(sid("sunbloom"), 4, 1, 4), item_entry(sid("sunburst_flask"), 4, 1, 3),
              item_entry("minecraft:blaze_rod", 5, 1, 3), item_entry("minecraft:golden_carrot", 4, 2, 6),
              item_entry(sid("phoenix_feather"), 2, 1, 2), item_entry("minecraft:fire_charge", 4, 2, 5)], rolls=(4, 8))])
    chest("sun_temple_armory", [
        pool([item_entry(sid("sunsteel_sword"), 3, enchant=True), item_entry(sid("sunsteel_pickaxe"), 3), item_entry(sid("sunsteel_helmet"), 3),
              item_entry(sid("sunsteel_chestplate"), 2), item_entry(sid("sunsteel_leggings"), 2), item_entry(sid("sunsteel_boots"), 3),
              item_entry(sid("solar_lance"), 2), item_entry(sid("cinder_chakram"), 2), item_entry(sid("magma_treads"), 1),
              item_entry(sid("phoenix_bow"), 1), item_entry(sid("helios_scepter"), 1)], rolls=1),
        pool([item_entry(sid("sunsteel_ingot"), 6, 2, 5), item_entry(sid("sunburst_flask"), 4, 2, 4), item_entry(sid("solar_essence"), 4, 1, 3),
              item_entry("minecraft:diamond", 2, 1, 3), item_entry("minecraft:golden_apple", 2)], rolls=(3, 6))])
    chest("sun_temple_vault", [
        pool([item_entry(sid("sunfire_sigil"))], conditions=chance(0.6)),
        pool([item_entry(sid("phoenix_egg"))], conditions=chance(0.4)),
        pool([item_entry(sid("helios_scepter"), 3), item_entry(sid("phoenix_mantle"), 2), item_entry(sid("phoenix_bow"), 3),
              item_entry(sid("cinder_chakram"), 3)], rolls=1),
        pool([item_entry(sid("phoenix_feather"), 5, 1, 3), item_entry(sid("sunsteel_block"), 2, 1, 2), item_entry("minecraft:diamond", 4, 2, 5),
              item_entry("minecraft:enchanted_golden_apple", 1), item_entry("minecraft:book", 3, enchant=True),
              item_entry(sid("solar_essence"), 5, 3, 8)], rolls=(4, 7))])


# ----------------------------------------------------------------------------------------------------------------
# Tags
# ----------------------------------------------------------------------------------------------------------------

def tag(kind, ns, name, values):
    path = RES + f"/data/{ns}/tags/{kind}/{name}.json"
    data = read(path) if os.path.exists(path) else {"replace": False, "values": []}
    for v in values:
        if v not in data["values"]:
            data["values"].append(v)
    write(path, data)


def tags():
    pick = ["scorchstone", "sunstone_ore", "ember_crystal_cluster", "sunsteel_block", "sunbaked_bricks", "cracked_sunbaked_bricks",
            "chiseled_sunbaked_bricks", "sunbaked_brick_stairs", "sunbaked_brick_slab", "solar_brazier", "sunfire_vent", "sun_altar"]
    tag("block", "minecraft", "mineable/pickaxe", [sid(b) for b in pick])
    tag("block", "minecraft", "mineable/shovel", [sid("sunsand"), sid("ashen_soil")])
    tag("block", "minecraft", "needs_diamond_tool", [sid("sunstone_ore"), sid("sunsteel_block"), sid("sun_altar")])
    tag("block", "minecraft", "beacon_base_blocks", [sid("sunsteel_block")])
    tag("block", "minecraft", "stairs", [sid("sunbaked_brick_stairs")])
    tag("block", "minecraft", "slabs", [sid("sunbaked_brick_slab")])
    tag("block", "minecraft", "impermeable", [sid("solar_glass")])
    tag("block", "minecraft", "supports_vegetation", [sid("ashen_soil"), sid("sunsand")])
    tag("block", "minecraft", "sand", [sid("sunsand")])
    tag("block", "minecraft", "small_flowers", [sid("sunbloom")])
    tag("block", "minecraft", "overworld_carver_replaceables", [sid("scorchstone"), sid("sunsand"), sid("ashen_soil")])
    tag("block", "minecraft", "infiniburn_overworld", [sid("scorchstone")])
    tag("block", NS, "meteor_proof", [sid("sun_altar"), sid("sun_seal"), sid("solar_gateway")])

    tag("item", NS, "sunsteel_repair_materials", [sid("sunsteel_ingot")])
    tag("item", NS, "solar_repair_materials", [sid("sun_heart"), sid("sunsteel_block")])
    tag("item", "minecraft", "beacon_payment_items", [sid("sunsteel_ingot")])
    tag("item", "minecraft", "swords", [sid("sunsteel_sword"), sid("flare_greatsword"), sid("solar_lance")])
    tag("item", "minecraft", "pickaxes", [sid("sunsteel_pickaxe")])
    tag("item", "minecraft", "axes", [sid("sunsteel_axe")])
    tag("item", "minecraft", "shovels", [sid("sunsteel_shovel")])
    tag("item", "minecraft", "hoes", [sid("sunsteel_hoe")])
    tag("item", "minecraft", "head_armor", [sid("sunsteel_helmet"), sid("solar_crown")])
    tag("item", "minecraft", "chest_armor", [sid("sunsteel_chestplate"), sid("phoenix_mantle")])
    tag("item", "minecraft", "leg_armor", [sid("sunsteel_leggings")])
    tag("item", "minecraft", "foot_armor", [sid("sunsteel_boots"), sid("magma_treads")])
    tag("item", "minecraft", "enchantable/bow", [sid("phoenix_bow")])
    tag("item", "minecraft", "enchantable/durability", [sid("phoenix_bow"), sid("helios_scepter"), sid("cinder_chakram")])
    tag("item", "minecraft", "stairs", [sid("sunbaked_brick_stairs")])
    tag("item", "minecraft", "slabs", [sid("sunbaked_brick_slab")])
    tag("item", "minecraft", "small_flowers", [sid("sunbloom")])

    tag("entity_type", NS, "warden_allies", [sid("sun_warden"), sid("cinder_imp"), sid("ember_hound"), sid("solar_pylon")])
    tag("entity_type", "minecraft", "fall_damage_immune", [sid("cinder_imp"), sid("solar_phoenix")])
    tag("worldgen/structure", NS, "sun_temples", [sid("sun_temple")])
    tag("worldgen/biome", NS, "has_sun_temple", [sid("ember_plains"), sid("gilded_dunes"), sid("basalt_spires")])
    tag("worldgen/biome", NS, "is_sunlands", [sid(b) for b in BIOMES])
    tag("timeline", NS, "in_sunlands", ["#minecraft:universal"])


# ----------------------------------------------------------------------------------------------------------------
# The Sunlands
# ----------------------------------------------------------------------------------------------------------------

BIOMES = {
    # name: (temperature range, humidity range, sky, fog, music, ash probability)
    "ember_plains": ((-1.0, 0.0), (-1.0, 0.0), "#ffae66", "#ffcf9a", "minecraft:music.overworld.badlands", 0.0015),
    "gilded_dunes": ((0.0, 1.0), (-1.0, 0.0), "#ffcb7a", "#ffe2b0", "minecraft:music.overworld.desert", 0.0),
    "basalt_spires": ((-1.0, 0.0), (0.0, 1.0), "#e88658", "#c99a86", "minecraft:music.nether.basalt_deltas", 0.008),
    "ember_caldera": ((0.0, 1.0), (0.0, 1.0), "#ff7a3e", "#ffa070", "minecraft:music.nether.nether_wastes", 0.004),
}


def placed(name, feature, placement):
    write(f"{D}/worldgen/placed_feature/{name}.json", {"feature": feature, "placement": placement})


def configured(name, ftype, config):
    write(f"{D}/worldgen/configured_feature/{name}.json", {"type": ftype, "config": config})


def surface_patch(count, spread, below):
    return [{"type": "minecraft:count", "count": count}, {"type": "minecraft:in_square"},
            {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"}, {"type": "minecraft:biome"},
            {"type": "minecraft:count", "count": spread},
            {"type": "minecraft:random_offset", "xz_spread": {"type": "minecraft:trapezoid", "max": 6, "min": -6, "plateau": 0},
             "y_spread": {"type": "minecraft:trapezoid", "max": 2, "min": -2, "plateau": 0}},
            {"type": "minecraft:block_predicate_filter", "predicate": {"type": "minecraft:all_of", "predicates": [
                {"type": "minecraft:matching_block_tag", "tag": "minecraft:air"},
                {"type": "minecraft:matching_blocks", "blocks": below, "offset": [0, -1, 0]}]}}]


def features():
    scorch = {"predicate_type": "minecraft:block_match", "block": sid("scorchstone")}
    configured("ore_sunstone", "minecraft:ore", {"discard_chance_on_air_exposure": 0.0, "size": 7,
                                                  "targets": [{"state": {"Name": sid("sunstone_ore")}, "target": scorch}]})
    placed("ore_sunstone", sid("ore_sunstone"), [
        {"type": "minecraft:count", "count": 9}, {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": {"type": "minecraft:trapezoid", "min_inclusive": {"absolute": -40}, "max_inclusive": {"absolute": 120}}},
        {"type": "minecraft:biome"}])
    placed("ore_sunstone_rich", sid("ore_sunstone"), [
        {"type": "minecraft:count", "count": 8}, {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": 40}, "max_inclusive": {"absolute": 140}}},
        {"type": "minecraft:biome"}])
    configured("ore_ember_magma", "minecraft:ore", {"discard_chance_on_air_exposure": 0.0, "size": 24,
                                                     "targets": [{"state": {"Name": "minecraft:magma_block"}, "target": scorch}]})
    placed("ore_ember_magma", sid("ore_ember_magma"), [
        {"type": "minecraft:count", "count": 5}, {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": -40}, "max_inclusive": {"absolute": 100}}},
        {"type": "minecraft:biome"}])
    configured("ember_crystals", "minecraft:simple_block", {"to_place": {"type": "minecraft:simple_state_provider",
                                                                          "state": {"Name": sid("ember_crystal_cluster"), "Properties": {"facing": "up", "waterlogged": "false"}}}})
    placed("ember_crystals", sid("ember_crystals"), surface_patch(4, 6, [sid("scorchstone"), "minecraft:magma_block", "minecraft:blackstone"]))
    placed("ember_crystals_sparse", sid("ember_crystals"), surface_patch(1, 3, [sid("scorchstone"), sid("ashen_soil"), "minecraft:basalt"]))
    configured("sunbloom", "minecraft:simple_block", {"to_place": {"type": "minecraft:simple_state_provider", "state": {"Name": sid("sunbloom")}}})
    placed("sunbloom_patch", sid("sunbloom"), surface_patch(3, 10, [sid("ashen_soil")]))
    placed("sunbloom_dunes", sid("sunbloom"), surface_patch(1, 4, [sid("sunsand")]))
    configured("sun_fire", "minecraft:simple_block", {"to_place": {"type": "minecraft:simple_state_provider", "state": {
        "Name": "minecraft:fire", "Properties": {"age": "0", "east": "false", "north": "false", "south": "false", "up": "false", "west": "false"}}}})
    placed("sun_fire_patch", sid("sun_fire"), surface_patch(2, 8, [sid("scorchstone"), "minecraft:magma_block", "minecraft:basalt", sid("ashen_soil")]))
    configured("dead_bush_dunes", "minecraft:simple_block", {"to_place": {"type": "minecraft:simple_state_provider", "state": {"Name": "minecraft:dead_bush"}}})
    placed("dead_bush_dunes", sid("dead_bush_dunes"), surface_patch(2, 4, [sid("sunsand")]))
    placed("sun_delta", "minecraft:delta", [{"type": "minecraft:count_on_every_layer", "count": 6}, {"type": "minecraft:biome"}])
    placed("sun_basalt_columns_small", "minecraft:small_basalt_columns", [{"type": "minecraft:count_on_every_layer", "count": 3}, {"type": "minecraft:biome"}])
    placed("sun_basalt_columns_large", "minecraft:large_basalt_columns", [{"type": "minecraft:count_on_every_layer", "count": 2}, {"type": "minecraft:biome"}])
    placed("sun_lava_lake", "minecraft:lake_lava", [{"type": "minecraft:rarity_filter", "chance": 24}, {"type": "minecraft:in_square"},
                                                     {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"}, {"type": "minecraft:biome"}])


def biome_features(name):
    steps = [[] for _ in range(11)]
    steps[6] += [sid("ore_sunstone"), sid("ore_ember_magma")]
    if name == "ember_plains":
        steps[1].append(sid("sun_lava_lake"))
        steps[9] += [sid("sunbloom_patch"), sid("ember_crystals_sparse"), sid("sun_fire_patch")]
    elif name == "gilded_dunes":
        steps[6].append(sid("ore_sunstone_rich"))
        steps[9] += [sid("dead_bush_dunes"), sid("sunbloom_dunes")]
    elif name == "basalt_spires":
        steps[4] += [sid("sun_basalt_columns_small"), sid("sun_basalt_columns_large")]
        steps[9] += [sid("sun_fire_patch"), sid("ember_crystals_sparse")]
    else:
        steps[1].append(sid("sun_lava_lake"))
        steps[4].append(sid("sun_delta"))
        steps[9] += [sid("ember_crystals"), sid("sun_fire_patch")]
    # Every biome must list shared features in the same order, or world generation fails with a "feature order cycle".
    order = [sid(n) for n in ("sun_lava_lake", "sun_delta", "sun_basalt_columns_small", "sun_basalt_columns_large", "ore_sunstone",
                              "ore_sunstone_rich", "ore_ember_magma", "dead_bush_dunes", "sunbloom_patch", "sunbloom_dunes", "ember_crystals",
                              "ember_crystals_sparse", "sun_fire_patch")]
    return [sorted(step, key=order.index) for step in steps]


def spawner(t, w, lo, hi):
    return {"type": t, "weight": w, "minCount": lo, "maxCount": hi}


def biome_spawns(name):
    monster = {
        "ember_plains": [spawner(sid("ember_hound"), 40, 2, 4), spawner(sid("cinder_imp"), 25, 1, 3)],
        "gilded_dunes": [spawner(sid("magma_crawler"), 20, 1, 2), spawner(sid("cinder_imp"), 30, 2, 3), spawner(sid("ashen_knight"), 6, 1, 1)],
        "basalt_spires": [spawner(sid("ashen_knight"), 20, 1, 2), spawner(sid("cinder_imp"), 30, 2, 4), spawner(sid("ember_hound"), 12, 2, 3)],
        "ember_caldera": [spawner(sid("magma_crawler"), 50, 1, 3), spawner(sid("cinder_imp"), 30, 2, 4)],
    }[name]
    creature = [spawner(sid("solar_phoenix"), 3, 1, 1)] if name in ("ember_plains", "gilded_dunes") else []
    return {"ambient": [], "axolotls": [], "creature": creature, "misc": [], "monster": monster, "underground_water_creature": [],
            "water_ambient": [], "water_creature": []}


def worldgen():
    features()
    for name, (temp, hum, sky, fog, music, ash) in BIOMES.items():
        attributes = {
            "minecraft:audio/background_music": {"default": {"max_delay": 24000, "min_delay": 12000, "sound": music}},
            "minecraft:visual/sky_color": sky,
            "minecraft:visual/fog_color": fog,
        }
        if ash > 0:
            attributes["minecraft:visual/ambient_particles"] = [{"particle": {"type": sid("ash_flake")}, "probability": ash}]
        write(f"{D}/worldgen/biome/{name}.json", {
            "attributes": attributes,
            "carvers": ["minecraft:cave", "minecraft:canyon"],
            "downfall": 0.0,
            "effects": {"water_color": "#ff8a3a", "grass_color": "#c8a050", "foliage_color": "#b08040"},
            "features": biome_features(name),
            "has_precipitation": False,
            "spawn_costs": {},
            "spawners": biome_spawns(name),
            "temperature": 2.0,
        })

    # Dimension type: eternal noon under a golden sky.
    write(f"{D}/dimension_type/sunlands.json", {
        "ambient_light": 0.15,
        "attributes": {
            "minecraft:audio/background_music": {"default": {"max_delay": 24000, "min_delay": 12000, "sound": "minecraft:music.overworld.badlands"}},
            "minecraft:gameplay/bed_rule": {"can_set_spawn": "never", "can_sleep": "never", "error_message": {"translate": "block.starforged.bed.sunlands"}},
            "minecraft:gameplay/water_evaporates": True,
            "minecraft:gameplay/can_start_raid": False,
            "minecraft:visual/sky_color": "#ffb46a",
            "minecraft:visual/fog_color": "#ffd2a0",
            "minecraft:visual/cloud_color": "#ccffe6b0",
            "minecraft:visual/cloud_height": 230.0,
            "minecraft:visual/sun_angle": 18.0,
            "minecraft:visual/moon_angle": 198.0,
            "minecraft:visual/star_brightness": 0.0,
            "minecraft:visual/sky_light_color": "#fff0d6",
            "minecraft:visual/ambient_light_color": "#3a2010",
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
        "timelines": "#starforged:in_sunlands",
    })

    base = json.load(open(os.path.join(ROOT, "tools/overworld_noise_router.json")))
    rules = []
    for name, top in (("ember_plains", {"type": "minecraft:block", "result_state": {"Name": sid("ashen_soil")}}),
                      ("gilded_dunes", {"type": "minecraft:block", "result_state": {"Name": sid("sunsand")}}),
                      ("basalt_spires", {"type": "minecraft:sequence", "sequence": [
                          {"type": "minecraft:condition", "if_true": {"type": "minecraft:noise_threshold", "noise": "minecraft:surface",
                                                                      "min_threshold": 0.0, "max_threshold": 10.0},
                           "then_run": {"type": "minecraft:block", "result_state": {"Name": "minecraft:blackstone"}}},
                          {"type": "minecraft:block", "result_state": {"Name": "minecraft:basalt", "Properties": {"axis": "y"}}}]}),
                      ("ember_caldera", {"type": "minecraft:sequence", "sequence": [
                          {"type": "minecraft:condition", "if_true": {"type": "minecraft:noise_threshold", "noise": "minecraft:surface",
                                                                      "min_threshold": 0.25, "max_threshold": 10.0},
                           "then_run": {"type": "minecraft:block", "result_state": {"Name": "minecraft:magma_block"}}},
                          {"type": "minecraft:block", "result_state": {"Name": sid("scorchstone")}}]})):
        rules.append({"type": "minecraft:condition", "if_true": {"type": "minecraft:biome", "biome_is": [sid(name)]}, "then_run": top})
    sub = []
    for name, filler in (("gilded_dunes", sid("sunsand")),):
        sub.append({"type": "minecraft:condition", "if_true": {"type": "minecraft:biome", "biome_is": [sid(name)]},
                    "then_run": {"type": "minecraft:block", "result_state": {"Name": filler}}})
    surface_rule = {"type": "minecraft:sequence", "sequence": [
        {"type": "minecraft:condition", "if_true": {"type": "minecraft:vertical_gradient", "random_name": "minecraft:bedrock_floor",
                                                    "true_at_and_below": {"above_bottom": 0}, "false_at_and_above": {"above_bottom": 5}},
         "then_run": {"type": "minecraft:block", "result_state": {"Name": "minecraft:bedrock"}}},
        {"type": "minecraft:condition", "if_true": {"type": "minecraft:above_preliminary_surface"}, "then_run": {"type": "minecraft:sequence", "sequence": [
            {"type": "minecraft:condition", "if_true": {"type": "minecraft:stone_depth", "offset": 0, "add_surface_depth": False,
                                                        "secondary_depth_range": 0, "surface_type": "floor"},
             "then_run": {"type": "minecraft:sequence", "sequence": rules}},
            {"type": "minecraft:condition", "if_true": {"type": "minecraft:stone_depth", "offset": 0, "add_surface_depth": True,
                                                        "secondary_depth_range": 0, "surface_type": "floor"},
             "then_run": {"type": "minecraft:sequence", "sequence": sub}},
        ]}},
    ]}
    write(f"{D}/worldgen/noise_settings/sunlands.json", {
        "aquifers_enabled": False,
        "default_block": {"Name": sid("scorchstone")},
        "default_fluid": {"Name": "minecraft:lava", "Properties": {"level": "0"}},
        "disable_mob_generation": False,
        "legacy_random_source": False,
        "noise": base["noise"],
        "noise_router": base["noise_router"],
        "ore_veins_enabled": False,
        "sea_level": 48,
        "spawn_target": base["spawn_target"],
        "surface_rule": surface_rule,
    })

    biomes = []
    for name, (temp, hum, *_rest) in BIOMES.items():
        biomes.append({"biome": sid(name), "parameters": {
            "temperature": list(temp), "humidity": list(hum), "continentalness": [-1.0, 1.0], "erosion": [-1.0, 1.0],
            "weirdness": [-1.0, 1.0], "depth": 0.0, "offset": 0.0}})
    write(f"{D}/dimension/sunlands.json", {
        "type": sid("sunlands"),
        "generator": {"type": "minecraft:noise", "settings": sid("sunlands"),
                      "biome_source": {"type": "minecraft:multi_noise", "biomes": biomes}}})

    write(f"{D}/worldgen/structure/sun_temple.json", {
        "type": sid("sun_temple"), "biomes": "#starforged:has_sun_temple", "step": "surface_structures", "terrain_adaptation": "none",
        "spawn_overrides": {"monster": {"bounding_box": "piece", "spawns": [
            spawner(sid("cinder_imp"), 20, 1, 2), spawner(sid("ashen_knight"), 10, 1, 1)]}}})
    write(f"{D}/worldgen/structure_set/sun_temples.json", {
        "placement": {"type": "minecraft:random_spread", "salt": 772461903, "separation": 6, "spacing": 20},
        "structures": [{"structure": sid("sun_temple"), "weight": 1}]})

    for name, effects in (("sunfire", "burning"), ("solar_beam", "burning")):
        write(f"{D}/damage_type/{name}.json", {"effects": effects, "exhaustion": 0.1, "message_id": f"starforged.{name}",
                                               "scaling": "when_caused_by_living_non_player"})


# ----------------------------------------------------------------------------------------------------------------
# Advancements (their own "Sunforged" tab)
# ----------------------------------------------------------------------------------------------------------------

ADVANCEMENTS_TEXT = {
    "root": ("Sunforged", "Forge a Solar Key from the Sovereign's heart"),
    "sunlands": ("The Last Star", "Step through a Solar Gateway into the Sunlands"),
    "sunsteel": ("Forged in Sunfire", "Smelt a Sunsteel Ingot"),
    "temple": ("Temple of the Sun", "Find a Sun Temple"),
    "knight": ("Ashes to Ashes", "Defeat an Ashen Knight"),
    "hound": ("Who's a Good Boy?", "Tame an Ember Hound"),
    "phoenix": ("Born of Fire", "Hatch or tame a Solar Phoenix"),
    "sigil": ("Light the Way", "Obtain a Sunfire Sigil"),
    "warden": ("Eclipse of the Sun", "Defeat the Sun Warden"),
    "greatsword": ("Wield the Dawn", "Claim the Flare Greatsword"),
}


def advancements():
    def a(key, parent, icon, criteria, frame="task", xp=None, background=None):
        display = {"title": {"translate": f"advancements.starforged.sun.{key}.title"},
                   "description": {"translate": f"advancements.starforged.sun.{key}.description"},
                   "icon": {"id": icon}, "frame": frame, "show_toast": True, "announce_to_chat": True, "hidden": False}
        if background:
            display["background"] = background
            display["show_toast"] = False
            display["announce_to_chat"] = False
        d = {"display": display, "criteria": criteria, "requirements": [list(criteria.keys())]}
        if parent:
            d["parent"] = sid(f"sun/{parent}")
        if xp:
            d["rewards"] = {"experience": xp}
        write(f"{D}/advancement/sun/{key}.json", d)

    def has(item):
        return {"trigger": "minecraft:inventory_changed", "conditions": {"items": [{"items": item}]}}

    def kill(entity):
        return {"trigger": "minecraft:player_killed_entity", "conditions": {"entity": [
            {"condition": "minecraft:entity_properties", "entity": "this", "predicate": {"minecraft:entity_type": entity}}]}}

    def tame(entity):
        return {"trigger": "minecraft:tame_animal", "conditions": {"entity": [
            {"condition": "minecraft:entity_properties", "entity": "this", "predicate": {"minecraft:entity_type": entity}}]}}

    a("root", None, sid("solar_key"), {"key": has(sid("solar_key")),
                                        "sunlands": {"trigger": "minecraft:changed_dimension", "conditions": {"to": sid("sunlands")}}},
      background="starforged:block/sunbaked_bricks")
    a("sunlands", "root", sid("ashen_soil"), {"enter": {"trigger": "minecraft:changed_dimension", "conditions": {"to": sid("sunlands")}}})
    a("sunsteel", "sunlands", sid("sunsteel_ingot"), {"ingot": has(sid("sunsteel_ingot"))})
    a("temple", "sunlands", sid("chiseled_sunbaked_bricks"), {"enter": {"trigger": "minecraft:location", "conditions": {"player": [
        {"condition": "minecraft:entity_properties", "entity": "this", "predicate": {"minecraft:location": {"structures": sid("sun_temple")}}}]}}})
    a("knight", "temple", sid("sunsteel_sword"), {"kill": kill(sid("ashen_knight"))})
    a("hound", "sunlands", sid("solar_essence"), {"tame": tame(sid("ember_hound"))})
    a("phoenix", "hound", sid("phoenix_egg"), {"tame": tame(sid("solar_phoenix"))}, frame="goal")
    a("sigil", "temple", sid("sunfire_sigil"), {"sigil": has(sid("sunfire_sigil"))})
    a("warden", "sigil", sid("sun_heart"), {"kill": kill(sid("sun_warden"))}, frame="challenge", xp=750)
    a("greatsword", "warden", sid("flare_greatsword"), {"blade": has(sid("flare_greatsword"))})


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
    print("sunforged data written")


if __name__ == "__main__":
    main()
