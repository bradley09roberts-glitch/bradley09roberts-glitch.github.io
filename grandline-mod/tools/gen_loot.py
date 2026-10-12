#!/usr/bin/env python3
"""Generate chest loot tables (data/grandline/loot_table/chests/*.json).

Each table: (rolls_min, rolls_max, [(item, weight, min, max, extra)]) where extra may set Beli on a pouch.
Unique named swords and Devil Fruits never come from loot tables (they are tracked one-per-world elsewhere).
"""
import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "src", "main", "resources", "data", "grandline", "loot_table", "chests")

G = "grandline:"
M = "minecraft:"


def beli(amount_min, amount_max, weight):
    return ("beli", weight, amount_min, amount_max)


TABLES = {
    "house": (2, 5, [
        (M + "bread", 12, 1, 4), (M + "apple", 10, 1, 3), (M + "cooked_cod", 8, 1, 3), (M + "string", 6, 1, 4),
        (M + "paper", 6, 1, 5), (M + "book", 3, 1, 2), (M + "torch", 8, 2, 8), (M + "coal", 6, 1, 6),
        (M + "leather", 4, 1, 3), (M + "candle", 3, 1, 2), (M + "carrot", 6, 1, 4), (M + "potato", 6, 1, 4),
        (G + "knife", 2, 1, 1), (G + "slingshot", 1, 1, 1), (G + "pellet", 3, 4, 12), beli(20, 120, 10),
    ]),
    "tavern": (3, 6, [
        (G + "meat_on_bone", 10, 1, 3), (M + "bread", 10, 1, 4), (M + "cooked_beef", 8, 1, 3),
        (M + "cooked_porkchop", 8, 1, 3), (M + "honey_bottle", 6, 1, 3), (M + "milk_bucket", 2, 1, 1),
        (M + "glass_bottle", 6, 1, 4), (M + "sugar", 5, 1, 6), (G + "wanted_poster", 4, 1, 2),
        (M + "pumpkin_pie", 4, 1, 2), (G + "knife", 2, 1, 1), beli(30, 200, 10),
    ]),
    "shop": (3, 6, [
        (M + "bread", 8, 2, 6), (M + "lead", 4, 1, 2), (M + "name_tag", 2, 1, 1), (G + "bullet", 6, 4, 16),
        (G + "pellet", 6, 8, 24), (G + "sea_chart", 2, 1, 1), (G + "log_pose", 1, 1, 1), (M + "compass", 3, 1, 1),
        (M + "map", 3, 1, 1), (M + "lantern", 4, 1, 2), (M + "spyglass", 2, 1, 1), (M + "string", 6, 2, 8),
        (M + "paper", 6, 2, 8), beli(50, 300, 10),
    ]),
    "blacksmith": (3, 6, [
        (M + "iron_ingot", 12, 1, 5), (M + "iron_nugget", 10, 3, 12), (M + "coal", 10, 2, 8), (M + "flint", 5, 1, 3),
        (G + "cutlass", 4, 1, 1), (G + "knife", 5, 1, 1), (G + "spear", 3, 1, 1), (G + "tonfa", 2, 1, 1),
        (G + "brass_knuckles", 2, 1, 1), (G + "iron_kanabo", 1, 1, 1), (G + "fine_katana", 1, 1, 1),
        (G + "fine_cutlass", 1, 1, 1), (G + "steel_spear", 1, 1, 1), (G + "seastone_shard", 1, 1, 2), beli(40, 220, 6),
    ]),
    "marine": (3, 6, [
        (G + "bullet", 12, 6, 20), (M + "bread", 10, 2, 6), (G + "cutlass", 5, 1, 1), (G + "flintlock", 4, 1, 1),
        (G + "musket", 3, 1, 1), (G + "wanted_poster", 8, 1, 3), (G + "sea_chart", 3, 1, 1),
        (G + "seastone_cuffs", 2, 1, 1), (G + "seastone_shard", 3, 1, 3), (G + "cannonball", 4, 2, 6),
        (M + "gunpowder", 6, 1, 4), (G + "log_pose", 1, 1, 1), beli(60, 300, 8),
    ]),
    "warehouse": (3, 7, [
        (M + "wheat", 10, 2, 10), (M + "sugar_cane", 6, 2, 8), (M + "oak_planks", 10, 4, 16), (M + "iron_ingot", 4, 1, 4),
        (G + "cannonball", 6, 2, 8), (M + "gunpowder", 5, 1, 5), (M + "string", 6, 2, 8), (M + "barrel", 2, 1, 1),
        (M + "white_wool", 5, 2, 6), (M + "salmon", 5, 1, 4), (M + "cod", 5, 1, 4), beli(40, 250, 8),
    ]),
    "ruin": (2, 5, [
        (M + "bone", 10, 1, 4), (M + "gold_nugget", 10, 2, 9), (M + "gold_ingot", 4, 1, 3), (M + "emerald", 2, 1, 2),
        (G + "cutlass", 3, 1, 1), (G + "knife", 4, 1, 1), (G + "poneglyph_rubbing", 1, 1, 1), (G + "eternal_pose", 1, 1, 1),
        (M + "amethyst_shard", 4, 1, 4), (M + "rotten_flesh", 6, 1, 3), beli(80, 400, 8),
    ]),
    "starter": (4, 4, [
        (M + "bread", 10, 4, 8), (M + "cooked_cod", 8, 2, 4), (G + "knife", 6, 1, 1), (G + "cutlass", 4, 1, 1),
        (G + "log_pose", 6, 1, 1), (M + "torch", 6, 8, 16), beli(150, 300, 8),
    ]),
    "treasure": (4, 8, [
        (M + "gold_ingot", 12, 2, 8), (M + "gold_nugget", 10, 6, 24), (M + "diamond", 3, 1, 3), (M + "emerald", 6, 1, 5),
        (G + "fine_katana", 2, 1, 1), (G + "fine_cutlass", 2, 1, 1), (G + "steel_spear", 2, 1, 1),
        (G + "eternal_pose", 3, 1, 1), (G + "poneglyph_rubbing", 1, 1, 1), (G + "seastone_shard", 3, 1, 4),
        (M + "golden_apple", 2, 1, 1), (M + "amethyst_shard", 4, 2, 6), beli(500, 3000, 14),
    ]),
    "wreck": (3, 6, [
        (G + "cannonball", 10, 2, 8), (G + "bullet", 8, 4, 16), (M + "gold_nugget", 8, 3, 12), (G + "cutlass", 4, 1, 1),
        (G + "musket", 2, 1, 1), (G + "flintlock", 3, 1, 1), (G + "sea_chart", 4, 1, 1), (G + "log_pose", 2, 1, 1),
        (M + "rotten_flesh", 4, 1, 3), (M + "heart_of_the_sea", 1, 1, 1), (M + "nautilus_shell", 4, 1, 2),
        beli(100, 800, 10),
    ]),
    "shrine": (2, 4, [
        (M + "candle", 8, 1, 3), (M + "gold_nugget", 8, 2, 8), (M + "amethyst_shard", 6, 1, 4),
        (G + "poneglyph_rubbing", 1, 1, 1), (G + "style_scroll", 2, 1, 1), (M + "experience_bottle", 6, 1, 4),
        (G + "eternal_pose", 2, 1, 1), beli(100, 600, 8),
    ]),
    "cache": (3, 5, [
        (M + "gold_ingot", 6, 1, 4), (M + "gold_nugget", 10, 4, 16), (M + "emerald", 4, 1, 3), (M + "diamond", 1, 1, 1),
        (G + "fine_cutlass", 1, 1, 1), (G + "eternal_pose", 2, 1, 1), (G + "bullet", 6, 4, 12), (M + "map", 4, 1, 1),
        beli(200, 1200, 12),
    ]),
}


def entry(e):
    item, weight, lo, hi = e[0], e[1], e[2], e[3]
    if item == "beli":
        return {
            "type": "minecraft:item", "name": "grandline:beli_pouch", "weight": weight,
            "functions": [{"function": "minecraft:set_components",
                           "components": {"grandline:beli": int((lo + hi) / 2)}},
                          {"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": 1, "max": 3}}],
        }
    out = {"type": "minecraft:item", "name": item, "weight": weight}
    if hi > 1:
        out["functions"] = [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]
    return out


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, (rmin, rmax, entries) in TABLES.items():
        table = {
            "type": "minecraft:chest",
            "pools": [{
                "rolls": {"type": "minecraft:uniform", "min": rmin, "max": rmax},
                "entries": [entry(e) for e in entries],
            }],
            "random_sequence": "grandline:chests/" + name,
        }
        with open(os.path.join(OUT, name + ".json"), "w") as f:
            json.dump(table, f, indent=2)
    print("wrote", len(TABLES), "loot tables to", OUT)


if __name__ == "__main__":
    main()
