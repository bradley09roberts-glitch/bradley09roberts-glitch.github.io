#!/usr/bin/env python3
"""Generates the mod's JSON resources (blockstates, models, loot, recipes, tags, lang).

Re-run after adding blocks/items:  python3 -I tools/gen_resources.py
"""
import json
import os
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "mod", "src", "main", "resources")
NS = "longnight"


def write(rel, data):
    path = os.path.join(ROOT, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


def tex(name):
    return f"{NS}:block/{name}"


FACING_Y = {"north": 0, "east": 90, "south": 180, "west": 270}

# ---------------------------------------------------------------- blocks
# hearthstone
write(f"assets/{NS}/models/block/hearthstone.json", {
    "parent": "minecraft:block/cube_bottom_top",
    "textures": {"top": tex("hearthstone_top"), "bottom": tex("hearthstone_side"), "side": tex("hearthstone_side")}})
write(f"assets/{NS}/blockstates/hearthstone.json", {"variants": {"": {"model": f"{NS}:block/hearthstone"}}})

# boundary stone (a squat marker pillar)
write(f"assets/{NS}/models/block/boundary_stone.json", {
    "parent": "minecraft:block/block",
    "textures": {"top": tex("boundary_stone_top"), "side": tex("boundary_stone_side"), "particle": tex("boundary_stone_side")},
    "elements": [{"from": [3, 0, 3], "to": [13, 14, 13], "faces": {
        "down": {"texture": "#top", "cullface": "down"}, "up": {"texture": "#top"},
        "north": {"texture": "#side"}, "south": {"texture": "#side"}, "east": {"texture": "#side"}, "west": {"texture": "#side"}}}]})
write(f"assets/{NS}/blockstates/boundary_stone.json", {"variants": {"": {"model": f"{NS}:block/boundary_stone"}}})

# ward stone (inactive / active)
for active in (False, True):
    sfx = "_active" if active else ""
    write(f"assets/{NS}/models/block/ward_stone{sfx}.json", {
        "parent": "minecraft:block/cube_column",
        "textures": {"end": tex("ward_stone_top" + sfx), "side": tex("ward_stone_side" + sfx)}})
write(f"assets/{NS}/blockstates/ward_stone.json", {"variants": {
    "active=false": {"model": f"{NS}:block/ward_stone"}, "active=true": {"model": f"{NS}:block/ward_stone_active"}}})

# storehouse (orientable)
write(f"assets/{NS}/models/block/storehouse.json", {
    "parent": "minecraft:block/orientable",
    "textures": {"top": tex("storehouse_top"), "front": tex("storehouse_front"), "side": tex("storehouse_side")}})
write(f"assets/{NS}/blockstates/storehouse.json", {"variants": {
    f"facing={d}": {"model": f"{NS}:block/storehouse", "y": y} for d, y in FACING_Y.items()}})

# market stall counter
write(f"assets/{NS}/models/block/market_stall.json", {
    "parent": "minecraft:block/block",
    "textures": {"top": tex("market_stall_top"), "side": tex("market_stall_side"), "particle": tex("market_stall_side")},
    "elements": [
        {"from": [1, 0, 1], "to": [15, 10, 15], "faces": {
            "down": {"texture": "#side", "cullface": "down"}, "up": {"texture": "#side"},
            "north": {"texture": "#side"}, "south": {"texture": "#side"}, "east": {"texture": "#side"}, "west": {"texture": "#side"}}},
        {"from": [0, 10, 0], "to": [16, 12, 16], "faces": {
            "down": {"texture": "#top"}, "up": {"texture": "#top"},
            "north": {"texture": "#top"}, "south": {"texture": "#top"}, "east": {"texture": "#top"}, "west": {"texture": "#top"}}}]})
write(f"assets/{NS}/blockstates/market_stall.json", {"variants": {
    f"facing={d}": {"model": f"{NS}:block/market_stall", "y": y} for d, y in FACING_Y.items()}})

# gravestone: a headstone with a rounded top
write(f"assets/{NS}/models/block/gravestone.json", {
    "parent": "minecraft:block/block",
    "textures": {"stone": tex("gravestone"), "particle": tex("gravestone")},
    "elements": [
        {"from": [2, 0, 6], "to": [14, 12, 10], "faces": {f: {"texture": "#stone"} for f in ("down", "up", "north", "south", "east", "west")}},
        {"from": [3, 12, 6], "to": [13, 14, 10], "faces": {f: {"texture": "#stone"} for f in ("down", "up", "north", "south", "east", "west")}},
        {"from": [5, 14, 6], "to": [11, 15, 10], "faces": {f: {"texture": "#stone"} for f in ("down", "up", "north", "south", "east", "west")}},
        {"from": [1, 0, 4], "to": [15, 1, 12], "faces": {f: {"texture": "#stone"} for f in ("down", "up", "north", "south", "east", "west")}}]})
write(f"assets/{NS}/blockstates/gravestone.json", {"variants": {
    f"facing={d}": {"model": f"{NS}:block/gravestone", "y": y} for d, y in FACING_Y.items()}})

# notice board on posts
write(f"assets/{NS}/models/block/notice_board.json", {
    "parent": "minecraft:block/block",
    "textures": {"front": tex("notice_board"), "side": tex("notice_board_side"), "particle": tex("notice_board_side")},
    "elements": [
        {"from": [1, 4, 7], "to": [15, 16, 9], "faces": {
            "north": {"texture": "#front"}, "south": {"texture": "#side"}, "east": {"texture": "#side"},
            "west": {"texture": "#side"}, "up": {"texture": "#side"}, "down": {"texture": "#side"}}},
        {"from": [2, 0, 7], "to": [4, 4, 9], "faces": {f: {"texture": "#side"} for f in ("down", "up", "north", "south", "east", "west")}},
        {"from": [12, 0, 7], "to": [14, 4, 9], "faces": {f: {"texture": "#side"} for f in ("down", "up", "north", "south", "east", "west")}}]})
write(f"assets/{NS}/blockstates/notice_board.json", {"variants": {
    f"facing={d}": {"model": f"{NS}:block/notice_board", "y": y} for d, y in FACING_Y.items()}})

BLOCKS = ["hearthstone", "storehouse", "market_stall", "gravestone", "boundary_stone", "notice_board", "ward_stone"]
for b in BLOCKS:
    write(f"assets/{NS}/models/item/{b}.json", {"parent": f"{NS}:block/{b}"})
    write(f"data/{NS}/loot_table/blocks/{b}.json", {
        "type": "minecraft:block",
        "pools": [{"rolls": 1, "entries": [{"type": "minecraft:item", "name": f"{NS}:{b}"}],
                   "conditions": [{"condition": "minecraft:survives_explosion"}]}]})

ITEMS = ["copper_penny", "silver_mark", "gold_crown", "founding_charter", "settlement_ledger", "land_deed", "wedding_token",
         "trade_seal", "letter", "ward_chalk", "ember_relic", "night_shard", "star_chart", "blighted_wheat"]
for i in ITEMS:
    write(f"assets/{NS}/models/item/{i}.json", {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:item/{i}"}})

# ---------------------------------------------------------------- tags
write("data/minecraft/tags/block/mineable/pickaxe.json", {"replace": False, "values": [
    f"{NS}:hearthstone", f"{NS}:gravestone", f"{NS}:boundary_stone", f"{NS}:ward_stone"]})
write("data/minecraft/tags/block/mineable/axe.json", {"replace": False, "values": [
    f"{NS}:storehouse", f"{NS}:market_stall", f"{NS}:notice_board"]})
# One society: vanilla villages (and their separate villagers) do not generate.
for v in ("plains", "desert", "savanna", "snowy", "taiga"):
    write(f"data/minecraft/tags/worldgen/biome/has_structure/village_{v}.json", {"replace": True, "values": []})

# ---------------------------------------------------------------- recipes
def shaped(name, pattern, key, result, count=1):
    k = {}
    for ch, v in key.items():
        k[ch] = {"tag": v[1:]} if v.startswith("#") else {"item": v}
    write(f"data/{NS}/recipe/{name}.json", {"type": "minecraft:crafting_shaped", "category": "misc", "pattern": pattern, "key": k,
                                            "result": {"id": result, "count": count}})


def shapeless(name, ingredients, result, count=1):
    ing = [{"tag": v[1:]} if v.startswith("#") else {"item": v} for v in ingredients]
    write(f"data/{NS}/recipe/{name}.json", {"type": "minecraft:crafting_shapeless", "category": "misc", "ingredients": ing,
                                            "result": {"id": result, "count": count}})


shaped("hearthstone", ["SSS", "SCS", "SSS"], {"S": "minecraft:stone_bricks", "C": "minecraft:campfire"}, f"{NS}:hearthstone")
shaped("storehouse", ["PPP", "PCP", "PIP"], {"P": "#minecraft:planks", "C": "minecraft:chest", "I": "minecraft:iron_ingot"}, f"{NS}:storehouse")
shaped("market_stall", ["WWW", "P P", "PBP"], {"W": "#minecraft:wool", "P": "#minecraft:planks", "B": "minecraft:barrel"}, f"{NS}:market_stall")
shaped("notice_board", ["PPP", "PXP", " S "], {"P": "#minecraft:planks", "X": "minecraft:paper", "S": "minecraft:stick"}, f"{NS}:notice_board")
shaped("boundary_stone", ["M", "S"], {"M": "minecraft:mossy_cobblestone", "S": "minecraft:stone_bricks"}, f"{NS}:boundary_stone")
shaped("gravestone", ["C", "C"], {"C": "minecraft:cobblestone"}, f"{NS}:gravestone")
shaped("ward_stone", ["DAD", "DAD", "DDD"], {"D": "minecraft:polished_deepslate", "A": "minecraft:amethyst_shard"}, f"{NS}:ward_stone")
shapeless("ward_chalk", ["minecraft:bone_meal", "minecraft:flint"], f"{NS}:ward_chalk", 2)
shapeless("founding_charter", ["minecraft:paper", "minecraft:paper", "minecraft:paper", "minecraft:gold_ingot", "minecraft:ink_sac",
                               "#minecraft:wool"], f"{NS}:founding_charter")
shapeless("settlement_ledger", ["minecraft:book", "minecraft:feather", "minecraft:ink_sac"], f"{NS}:settlement_ledger")
# No coin-exchange recipes: a 3x3 grid holds 9 coins but a mark is worth 10 pennies, which would mint money.
# Trades give exact change instead (game/Coins.java), so money only enters the world through trade.

# ---------------------------------------------------------------- lang
lang = {
    "itemGroup.longnight": "Kingdoms of the Long Night",
    "entity.longnight.citizen": "Person",
    "block.longnight.hearthstone": "Hearthstone",
    "block.longnight.storehouse": "Storehouse Ledger",
    "block.longnight.market_stall": "Market Stall",
    "block.longnight.gravestone": "Gravestone",
    "block.longnight.boundary_stone": "Boundary Stone",
    "block.longnight.notice_board": "Notice Board",
    "block.longnight.ward_stone": "Ward Stone",
    "item.longnight.copper_penny": "Copper Penny",
    "item.longnight.silver_mark": "Silver Mark",
    "item.longnight.gold_crown": "Gold Crown",
    "item.longnight.founding_charter": "Founding Charter",
    "item.longnight.settlement_ledger": "Traveller's Journal",
    "item.longnight.land_deed": "Land Deed",
    "item.longnight.wedding_token": "Wedding Token",
    "item.longnight.trade_seal": "Trade Seal",
    "item.longnight.letter": "Sealed Letter",
    "item.longnight.ward_chalk": "Ward Chalk",
    "item.longnight.ember_relic": "Ember Relic",
    "item.longnight.night_shard": "Night Shard",
    "item.longnight.star_chart": "Star Chart",
    "item.longnight.blighted_wheat": "Blighted Wheat",
}
write(f"assets/{NS}/lang/en_us.json", lang)
print("resources generated under", os.path.normpath(ROOT))
