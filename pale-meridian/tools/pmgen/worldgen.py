"""Generates the valley's worldgen data: biomes, noise settings, world preset, tags and placed features.

Design rules (see docs/TECHNICAL.md):
* Every valley biome copies a vanilla biome's feature lists position-for-position (substituting or
  removing entries only), so the global feature order stays acyclic across vanilla + valley biomes.
* Pall variants are what the biome source generates. Clear variants are only ever swapped in by the
  restoration system; they carry identical feature lists.
"""
import copy
import json

from . import vanilla
from .jsonio import write_json
from .paths import DATA, PM_DATA, LAYOUT

LAYOUT_DATA = json.loads(LAYOUT.read_text())

DISTRICTS = ["landing", "hollin", "aldercross", "glassworks", "deepcut", "mere", "fen", "westwood", "southmoor", "rim"]

# Which vanilla biome's feature lists each district copies, and tree density substitution.
DISTRICT_BASE = {
    "landing": ("pale_garden", "sparse"),
    "hollin": ("pale_garden", "sparse"),
    "aldercross": ("pale_garden", "medium"),
    "glassworks": ("pale_garden", "sparse"),
    "deepcut": ("pale_garden", "medium"),
    "mere": ("pale_garden", "island"),
    "fen": ("swamp", None),
    "westwood": ("pale_garden", None),
    "southmoor": ("pale_garden", "sparse"),
    "rim": ("pale_garden", "medium"),
}

# Clear-variant music per district (vanilla sound events).
CLEAR_MUSIC = {
    "landing": "minecraft:music.overworld.meadow",
    "hollin": "minecraft:music.overworld.cherry_grove",
    "aldercross": "minecraft:music.overworld.flower_forest",
    "glassworks": "minecraft:music.overworld.stony_peaks",
    "deepcut": "minecraft:music.overworld.dripstone_caves",
    "mere": "minecraft:music.overworld.lush_caves",
    "fen": "minecraft:music.overworld.swamp",
    "westwood": "minecraft:music.overworld.old_growth_taiga",
    "southmoor": "minecraft:music.overworld.meadow",
    "rim": "minecraft:music.overworld.forest",
}

CLEAR_GRASS = {
    "landing": "#8fb85e", "hollin": "#86b85a", "aldercross": "#7fc45a", "glassworks": "#93b566", "deepcut": "#88a66a",
    "mere": "#7eb65c", "fen": "#6a8f4a", "westwood": "#6da556", "southmoor": "#9cb865", "rim": "#7aa95c",
}

PALL_TINT = {
    # (fog colour, sky colour, fog end distance)
    "landing": ("#a3a6a8", "#b3b7ba", 44.0),
    "hollin": ("#9ea2a6", "#adb2b6", 36.0),
    "aldercross": ("#a0a39b", "#b0b3aa", 34.0),
    "glassworks": ("#a39e99", "#b2ada7", 34.0),
    "deepcut": ("#8f9096", "#9c9da3", 26.0),
    "mere": ("#a4abb1", "#b6bcc1", 40.0),
    "fen": ("#98a096", "#a9b0a6", 30.0),
    "westwood": ("#9a9e9b", "#aaaeab", 32.0),
    "southmoor": ("#a4a7a6", "#b4b7b6", 40.0),
    "rim": ("#a2a5a8", "#b2b5b8", 48.0),
}


def _placed(name: str, count: int, base: str = "pale_garden_vegetation") -> str:
    pf = copy.deepcopy(vanilla.data_json(f"data/minecraft/worldgen/placed_feature/{base}.json"))
    for mod in pf["placement"]:
        if mod.get("type") == "minecraft:count":
            mod["count"] = count
    write_json(PM_DATA / "worldgen" / "placed_feature" / f"{name}.json", pf)
    return f"palemeridian:{name}"


def _features_for(district: str) -> list:
    base, density = DISTRICT_BASE[district]
    src = vanilla.data_json(f"data/minecraft/worldgen/biome/{base}.json")
    features = copy.deepcopy(src["features"])
    # Never generate surface lava lakes inside the valley (story areas, fog, fire).
    features = [[f for f in step if f != "minecraft:lake_lava_surface"] for step in features]
    if base == "pale_garden" and density:
        replacement = {
            "sparse": "palemeridian:pale_trees_sparse",
            "medium": "palemeridian:pale_trees_medium",
            "island": "palemeridian:pale_trees_island",
        }[density]
        features = [[replacement if f == "minecraft:pale_garden_vegetation" else f for f in step] for step in features]
    # Roads and lamp posts (Java feature) run at the end of the surface_structures step, before
    # vegetation, so trees and grass never grow on the path.
    features[4] = features[4] + ["palemeridian:vale_roads"]
    return features


def _spawners_pall(base: str) -> dict:
    return {
        "ambient": [{"type": "minecraft:bat", "maxCount": 8, "minCount": 8, "weight": 10}],
        "axolotls": [],
        "creature": [],
        "misc": [],
        "monster": [
            {"type": "minecraft:zombie", "maxCount": 3, "minCount": 1, "weight": 60},
            {"type": "minecraft:skeleton", "maxCount": 2, "minCount": 1, "weight": 40},
            {"type": "minecraft:spider", "maxCount": 2, "minCount": 1, "weight": 20},
        ],
        "underground_water_creature": [{"type": "minecraft:glow_squid", "maxCount": 6, "minCount": 4, "weight": 10}],
        "water_ambient": [],
        "water_creature": [],
    }


def _spawners_clear(district: str) -> dict:
    creature = [
        {"type": "minecraft:sheep", "maxCount": 4, "minCount": 2, "weight": 12},
        {"type": "minecraft:chicken", "maxCount": 4, "minCount": 2, "weight": 10},
        {"type": "minecraft:cow", "maxCount": 4, "minCount": 2, "weight": 8},
        {"type": "minecraft:pig", "maxCount": 4, "minCount": 2, "weight": 8},
        {"type": "minecraft:rabbit", "maxCount": 3, "minCount": 2, "weight": 6},
    ]
    if district == "fen":
        creature = [{"type": "minecraft:frog", "maxCount": 5, "minCount": 2, "weight": 10}]
    if district == "mere":
        creature = [{"type": "minecraft:sheep", "maxCount": 3, "minCount": 2, "weight": 6}]
    water = []
    if district in ("mere", "fen"):
        water = [{"type": "minecraft:salmon", "maxCount": 5, "minCount": 1, "weight": 5}]
    return {
        "ambient": [{"type": "minecraft:bat", "maxCount": 8, "minCount": 8, "weight": 10}],
        "axolotls": [],
        "creature": creature,
        "misc": [],
        "monster": [
            {"type": "minecraft:zombie", "maxCount": 4, "minCount": 2, "weight": 90},
            {"type": "minecraft:skeleton", "maxCount": 4, "minCount": 2, "weight": 80},
            {"type": "minecraft:spider", "maxCount": 3, "minCount": 1, "weight": 60},
            {"type": "minecraft:enderman", "maxCount": 2, "minCount": 1, "weight": 8},
        ],
        "underground_water_creature": [{"type": "minecraft:glow_squid", "maxCount": 6, "minCount": 4, "weight": 10}],
        "water_ambient": [],
        "water_creature": water,
    }


def _biome(district: str, variant: str) -> dict:
    features = _features_for(district)
    fog, sky, fog_end = PALL_TINT[district]
    if variant == "pall":
        attributes = {
            "minecraft:audio/background_music": {},
            "minecraft:audio/music_volume": 0.0,
            "minecraft:audio/ambient_sounds": {
                "loop": "minecraft:ambient.soul_sand_valley.loop" if district != "mere" else "minecraft:ambient.underwater.loop",
                "mood": {"sound": "minecraft:ambient.cave", "tick_delay": 6000, "block_search_extent": 8, "offset": 2.0},
                "additions": [{"sound": "minecraft:ambient.soul_sand_valley.additions", "tick_chance": 0.0045}],
            },
            "minecraft:visual/fog_color": fog,
            "minecraft:visual/sky_color": sky,
            "minecraft:visual/cloud_color": "#ccc6cacd",
            "minecraft:visual/fog_start_distance": 2.0,
            "minecraft:visual/fog_end_distance": fog_end,
            "minecraft:visual/sky_fog_end_distance": fog_end * 0.8,
            "minecraft:visual/water_fog_color": "#4d5a66",
            "minecraft:visual/water_fog_end_distance": 18.0,
            "minecraft:visual/ambient_particles": [{"particle": {"type": "minecraft:white_ash"}, "probability": 0.012}],
            "minecraft:gameplay/creaking_active": True,
            "minecraft:gameplay/eyeblossom_open": "true",
        }
        effects = {"grass_color": "#8b9285", "foliage_color": "#8c9283", "dry_foliage_color": "#a0a69c", "water_color": "#6f8190"}
        if district == "fen":
            effects["grass_color"] = "#7f8a78"
        spawners = _spawners_pall(district)
    else:
        attributes = {
            "minecraft:audio/background_music": {
                "default": {"sound": CLEAR_MUSIC[district], "min_delay": 12000, "max_delay": 24000}
            },
            "minecraft:visual/sky_color": "#7ba7ff",
            "minecraft:visual/fog_color": "#c7dcff",
            "minecraft:visual/water_fog_color": "#3d6a8a",
        }
        effects = {"grass_color": CLEAR_GRASS[district], "foliage_color": "#6fae4c", "water_color": "#3f76e4"}
        if district == "fen":
            effects = {"grass_color_modifier": "swamp", "foliage_color": "#6a7039", "water_color": "#617b64"}
        spawners = _spawners_clear(district)
    return {
        "attributes": attributes,
        "carvers": [],
        "downfall": 0.8,
        "effects": effects,
        "features": features,
        "has_precipitation": True,
        "spawn_costs": {},
        "spawners": spawners,
        "temperature": 0.7,
    }


def _replace_refs(node, mapping: dict):
    if isinstance(node, str):
        return mapping.get(node, node)
    if isinstance(node, list):
        return [_replace_refs(n, mapping) for n in node]
    if isinstance(node, dict):
        return {k: _replace_refs(v, mapping) for k, v in node.items()}
    return node


def _guard_underground(node):
    """Wrap vanilla's deep-cave branch so caves never open close under sites and roads."""
    if isinstance(node, dict):
        if node.get("type") == "minecraft:range_choice" and node.get("max_exclusive") == 1.5625:
            node = dict(node)
            node["when_out_of_range"] = {
                "type": "minecraft:max",
                "argument1": _guard_underground(node["when_out_of_range"]),
                "argument2": "palemeridian:vale/guard",
            }
            node["when_in_range"] = _guard_underground(node["when_in_range"])
            node["input"] = _guard_underground(node["input"])
            return node
        return {k: _guard_underground(v) for k, v in node.items()}
    if isinstance(node, list):
        return [_guard_underground(n) for n in node]
    return node


def _valley_biome_ids(districts=None) -> list:
    out = []
    for d in districts or DISTRICTS:
        out += [f"palemeridian:{d}_pall", f"palemeridian:{d}_clear"]
    return out


def _block(name: str, props: dict | None = None) -> dict:
    st = {"Name": name}
    if props:
        st["Properties"] = props
    return {"type": "minecraft:block", "result_state": st}


def _floor(then_run) -> dict:
    return {
        "type": "minecraft:condition",
        "if_true": {"type": "minecraft:stone_depth", "add_surface_depth": False, "offset": 0, "secondary_depth_range": 0, "surface_type": "floor"},
        "then_run": then_run,
    }


def _under_floor(depth_add: bool, then_run) -> dict:
    return {
        "type": "minecraft:condition",
        "if_true": {"type": "minecraft:stone_depth", "add_surface_depth": depth_add, "offset": 0, "secondary_depth_range": 0, "surface_type": "floor"},
        "then_run": then_run,
    }


def _above_water(offset: int, then_run) -> dict:
    return {
        "type": "minecraft:condition",
        "if_true": {"type": "minecraft:water", "add_stone_depth": False, "offset": offset, "surface_depth_multiplier": 0},
        "then_run": then_run,
    }


def _y_above(y: int) -> dict:
    return {"type": "minecraft:y_above", "anchor": {"absolute": y}, "surface_depth_multiplier": 0, "add_stone_depth": False}


def _noise_band(noise: str, lo: float, hi: float, then_run) -> dict:
    return {
        "type": "minecraft:condition",
        "if_true": {"type": "minecraft:noise_threshold", "noise": noise, "min_threshold": lo, "max_threshold": hi},
        "then_run": then_run,
    }


def _surface_rules() -> dict:
    mere = _valley_biome_ids(["mere"])
    fen = _valley_biome_ids(["fen"])
    rocky = _valley_biome_ids(["deepcut", "glassworks", "rim", "westwood"])
    rules = [
        # Lake bed and shore: sand, clay and gravel below the waterline; a sand margin at the shore.
        {
            "type": "minecraft:condition",
            "if_true": {"type": "minecraft:biome", "biome_is": mere},
            "then_run": {"type": "minecraft:sequence", "sequence": [
                {"type": "minecraft:condition",
                 "if_true": {"type": "minecraft:not", "invert": _y_above(63)},
                 "then_run": {"type": "minecraft:sequence", "sequence": [
                     _floor({"type": "minecraft:sequence", "sequence": [
                         _noise_band("minecraft:surface", -0.15, 0.25, _block("minecraft:clay")),
                         _noise_band("minecraft:surface", 0.25, 9.0, _block("minecraft:gravel")),
                         _block("minecraft:sand"),
                     ]}),
                     _under_floor(True, _block("minecraft:sand")),
                 ]}},
                {"type": "minecraft:condition",
                 "if_true": {"type": "minecraft:not", "invert": _y_above(65)},
                 "then_run": _under_floor(True, _block("minecraft:sand"))},
            ]},
        },
        # The Fen: mud flats with grass hummocks.
        {
            "type": "minecraft:condition",
            "if_true": {"type": "minecraft:biome", "biome_is": fen},
            "then_run": {"type": "minecraft:sequence", "sequence": [
                _floor({"type": "minecraft:sequence", "sequence": [
                    _noise_band("minecraft:surface_swamp", -9.0, 0.05, _block("minecraft:mud")),
                ]}),
            ]},
        },
        # Cliffs and rocky rims: bare stone on steep faces.
        {
            "type": "minecraft:condition",
            "if_true": {"type": "minecraft:biome", "biome_is": rocky},
            "then_run": {"type": "minecraft:condition", "if_true": {"type": "minecraft:steep"},
                         "then_run": _under_floor(True, {"type": "minecraft:sequence", "sequence": [
                             _noise_band("minecraft:surface", 0.2, 9.0, _block("minecraft:andesite")),
                             _block("minecraft:stone"),
                         ]})},
        },
    ]
    return {
        "type": "minecraft:condition",
        "if_true": {"type": "minecraft:above_preliminary_surface"},
        "then_run": {"type": "minecraft:sequence", "sequence": rules},
    }


def _noise_settings() -> dict:
    ns = copy.deepcopy(vanilla.data_json("data/minecraft/worldgen/noise_settings/overworld.json"))
    router = ns["noise_router"]
    mapping = {
        "minecraft:overworld/sloped_cheese": "palemeridian:vale/sloped_cheese",
        "minecraft:overworld/caves/entrances": "palemeridian:vale/entrances",
        "minecraft:overworld/caves/noodle": "palemeridian:vale/noodle",
    }
    router["final_density"] = _guard_underground(_replace_refs(router["final_density"], mapping))
    router["preliminary_surface_level"] = {
        "type": "palemeridian:valley", "mode": "height", "vanilla": router["preliminary_surface_level"],
    }
    # Initial spawn search starts at the origin; the mod then moves the world spawn to the Landing.
    ns["spawn_target"] = []
    ns["surface_rule"]["sequence"].insert(1, _surface_rules())
    return ns


def generate() -> None:
    wg = PM_DATA / "worldgen"
    # Tree density variants (same configured feature as vanilla pale gardens).
    _placed("pale_trees_sparse", 2)
    _placed("pale_trees_medium", 6)
    _placed("pale_trees_island", 1)
    write_json(wg / "configured_feature" / "vale_roads.json", {"type": "palemeridian:vale_roads", "config": {}})
    write_json(wg / "placed_feature" / "vale_roads.json", {"feature": "palemeridian:vale_roads", "placement": []})

    for d in DISTRICTS:
        write_json(wg / "biome" / f"{d}_pall.json", _biome(d, "pall"))
        write_json(wg / "biome" / f"{d}_clear.json", _biome(d, "clear"))

    write_json(wg / "density_function" / "vale" / "sloped_cheese.json",
               {"type": "palemeridian:valley", "mode": "surface", "vanilla": "minecraft:overworld/sloped_cheese"})
    write_json(wg / "density_function" / "vale" / "guard.json", {"type": "palemeridian:valley", "mode": "guard"})
    for name, ref in (("entrances", "minecraft:overworld/caves/entrances"), ("noodle", "minecraft:overworld/caves/noodle")):
        write_json(wg / "density_function" / "vale" / f"{name}.json",
                   {"type": "minecraft:max", "argument1": ref, "argument2": "palemeridian:vale/guard"})

    write_json(wg / "noise_settings" / "vale.json", _noise_settings())

    preset = copy.deepcopy(vanilla.data_json("data/minecraft/worldgen/world_preset/normal.json"))
    preset["dimensions"]["minecraft:overworld"]["generator"] = {
        "type": "minecraft:noise",
        "settings": "palemeridian:vale",
        "biome_source": {
            "type": "palemeridian:valley",
            "vanilla": {"type": "minecraft:multi_noise", "preset": "minecraft:overworld"},
            "districts": {d: f"palemeridian:{d}_pall" for d in DISTRICTS},
        },
    }
    write_json(DATA / "minecraft" / "worldgen" / "world_preset" / "normal.json", preset)

    tags = PM_DATA / "tags" / "worldgen" / "biome"
    write_json(tags / "valley.json", {"values": _valley_biome_ids()})
    write_json(tags / "pall.json", {"values": [f"palemeridian:{d}_pall" for d in DISTRICTS]})
    write_json(tags / "clear.json", {"values": [f"palemeridian:{d}_clear" for d in DISTRICTS]})
    for d in DISTRICTS:
        write_json(tags / "district" / f"{d}.json", {"values": [f"palemeridian:{d}_pall", f"palemeridian:{d}_clear"]})
    # Mineshafts may run under the valley (underground exploration and ores).
    write_json(DATA / "minecraft" / "tags" / "worldgen" / "biome" / "has_structure" / "mineshaft.json",
               {"replace": False, "values": _valley_biome_ids()})
