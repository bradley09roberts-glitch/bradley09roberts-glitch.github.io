"""Chest loot tables referenced by the authored sites (containers that are not hand-filled).

Kept deliberately modest: household supplies that support the campaign's crafting (copper, iron
nuggets, candles, food) without replacing mining and exploration.
"""
from __future__ import annotations

from . import loot


def _item(name: str, weight: int, lo: int = 1, hi: int = 1) -> dict:
    e = {"type": "minecraft:item", "name": name, "weight": weight}
    if hi > 1 or lo != 1:
        e["functions"] = [{"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": lo, "max": hi}}]
    return e


def _table(path: str, rolls: tuple, entries: list) -> None:
    loot(path, {
        "type": "minecraft:chest",
        "pools": [{"rolls": {"type": "minecraft:uniform", "min": rolls[0], "max": rolls[1]}, "entries": entries}],
        "random_sequence": f"palemeridian:{path}",
    })


def generate() -> None:
    _table("chests/cottage", (3, 6), [
        _item("minecraft:bread", 10, 1, 3), _item("minecraft:candle", 6, 1, 3), _item("minecraft:string", 5, 1, 4),
        _item("minecraft:white_wool", 4, 1, 2), _item("minecraft:torch", 8, 2, 5), _item("minecraft:paper", 4, 1, 3),
        _item("minecraft:book", 2), _item("minecraft:flint", 3, 1, 2), _item("minecraft:wheat_seeds", 4, 2, 6),
        _item("minecraft:iron_nugget", 5, 2, 6), _item("minecraft:raw_copper", 5, 1, 4), _item("minecraft:coal", 5, 1, 4),
        _item("minecraft:bowl", 2), _item("minecraft:shears", 1), _item("minecraft:bucket", 1),
    ])
    _table("chests/pantry", (3, 6), [
        _item("minecraft:bread", 10, 1, 4), _item("minecraft:apple", 8, 1, 3), _item("minecraft:potato", 6, 1, 4),
        _item("minecraft:carrot", 6, 1, 4), _item("minecraft:wheat", 5, 2, 6), _item("minecraft:sugar", 3, 1, 3),
        _item("minecraft:egg", 3, 1, 3), _item("minecraft:dried_kelp", 4, 2, 6), _item("minecraft:honey_bottle", 1),
    ])
    _table("chests/cider", (2, 4), [
        _item("minecraft:apple", 10, 2, 6), _item("minecraft:glass_bottle", 5, 1, 3), _item("minecraft:sugar", 4, 1, 3),
        _item("minecraft:honey_bottle", 2), _item("minecraft:stick", 3, 2, 6), _item("minecraft:oak_planks", 3, 2, 6),
    ])
    _table("chests/works", (3, 6), [
        _item("minecraft:coal", 10, 2, 6), _item("minecraft:charcoal", 6, 1, 4), _item("minecraft:sand", 6, 2, 8),
        _item("minecraft:glass", 5, 1, 4), _item("minecraft:iron_nugget", 5, 2, 8), _item("minecraft:iron_ingot", 2, 1, 2),
        _item("minecraft:copper_ingot", 4, 1, 4), _item("minecraft:brick", 4, 2, 6), _item("minecraft:clay_ball", 4, 2, 6),
        _item("minecraft:torch", 5, 2, 6), _item("minecraft:bread", 3, 1, 2),
    ])
    _table("chests/mine", (2, 5), [
        _item("minecraft:torch", 10, 2, 6), _item("minecraft:coal", 8, 1, 5), _item("minecraft:raw_iron", 4, 1, 3),
        _item("minecraft:iron_nugget", 5, 2, 6), _item("minecraft:rail", 4, 2, 6), _item("minecraft:stick", 3, 2, 5),
        _item("minecraft:bread", 3, 1, 2), _item("minecraft:amethyst_shard", 3, 1, 3), _item("minecraft:candle", 4, 1, 3),
    ])
