"""Pall Surges: arena encounters where the fog snuffs lamps and Watchers (creakings) close in.

Rules shared by every surge (readable, telegraphed):
* Watchers move only while nobody is looking at them (vanilla creaking behaviour) and die in one hit.
* The Pall snuffs the arena lamps at the start; players relight them by using them (no item needed).
* The surge ends in success when every lamp is lit (and the minimum duration has passed).
* If nobody is inside the arena for 15 seconds (all left, died or disconnected) it resets cleanly and
  starts again the next time a player enters while the quest is active. Server restarts reset it.
Watcher count scales with the number of players present and the world difficulty setting; health is
never scaled.
"""
from __future__ import annotations

from dataclasses import dataclass, field

from . import fid, snbt, tellraw, xyz
from .engine import NPC, R, complete, uuid_nbt, uuid_str


@dataclass
class Surge:
    id: str
    quest: str
    title: str
    center: tuple
    radius: int
    spawns: list                    # list of (x, y, z) spawn points (in the fog, arena edge)
    lamps: list                     # list of (x, y, z) bulb positions to relight
    min_seconds: int = 30
    base_watchers: int = 2
    per_player: int = 1
    cap: int = 6
    interval: int = 8               # seconds between spawns while below the cap
    on_start: list = field(default_factory=list)
    on_success: list = field(default_factory=list)


def register(s: Surge) -> None:
    sid = s.id
    tag = f"pm.surge.{sid}"
    state = f"#enc.{sid}"
    cx, cy, cz = s.center
    # relight props (one interaction per lamp, only while the surge runs and the lamp is dark)
    for i, (lx, ly, lz) in enumerate(s.lamps):
        from .. import poi
        name = f"{sid}.relight{i}"
        if name not in poi.POI:
            poi.add(f"surge.{name}", lx + 0.5, ly - 0.2, lz + 0.5)
        R.npc(NPC(f"relight_{sid}_{i}", "Dark lamp", "", "gold", "none", body=False, label="✦ Relight", size=(1.2, 1.4),
                  places=[(f"if score {state} pm.world matches 1 unless block {lx} {ly} {lz} #palemeridian:bulbs[lit=true]", f"surge.{name}")],
                  talk=[("", f"/function {fid(f'enc/{sid}/relight/{i}')}")]))
        R.func(f"enc/{sid}/relight/{i}", [
            f"execute unless score {state} pm.world matches 1 run return fail",
            f"execute if block {lx} {ly} {lz} #palemeridian:bulbs[lit=true] run return fail",
            f"function {fid('enc/_set_bulb')} {{x:{lx},y:{ly},z:{lz},lit:\"true\"}}",
            f"particle minecraft:end_rod {lx} {ly} {lz} 0.3 0.3 0.3 0.02 25 normal",
            f"playsound minecraft:block.copper_bulb.turn_on master @a {lx} {ly} {lz} 1 1",
            tellraw("@a[distance=..48]", {"text": "A lamp flares back to life.", "color": "gold", "italic": True}),
        ])
    lamp_count_lines = [f"execute if block {x} {y} {z} #palemeridian:bulbs[lit=true] run scoreboard players add #lit pm.tmp 1" for (x, y, z) in s.lamps]
    R.func(f"enc/{sid}/start", [
        f"execute if score {state} pm.world matches 1 run return fail",
        f"scoreboard players set {state} pm.world 1",
        f"scoreboard players set #t.{sid} pm.world 0",
        f"scoreboard players set #idle.{sid} pm.world 0",
        f"scoreboard players set #spawn.{sid} pm.world 0",
        *[f"function {fid('enc/_set_bulb')} {{x:{x},y:{y},z:{z},lit:\"false\"}}" for (x, y, z) in s.lamps],
        f"bossbar set palemeridian:encounter name {snbt({'text': s.title, 'color': 'red'})}",
        f"bossbar set palemeridian:encounter max {len(s.lamps)}",
        f"bossbar set palemeridian:encounter value 0",
        f"bossbar set palemeridian:encounter players @a[x={cx},y={cy},z={cz},distance=..{s.radius + 16}]",
        f"bossbar set palemeridian:encounter visible true",
        "time of palemeridian:surge resume",
        f"playsound minecraft:entity.warden.nearby_closer master @a {cx} {cy} {cz} 2 0.5",
        tellraw(f"@a[x={cx},y={cy},z={cz},distance=..{s.radius + 24}]", [
            {"text": "The Pall surges. ", "color": "red", "italic": True},
            {"text": "The lamps gutter and die. Watchers step out of the fog — they move only when you look away. Relight the lamps.", "color": "gray", "italic": True}]),
        *s.on_start,
    ])
    spawn_lines = []
    for i, (x, y, z) in enumerate(s.spawns):
        spawn_lines.append(f"execute if score #pick pm.tmp matches {i} run summon minecraft:creaking {x} {y} {z} "
                           f"{{Tags:[\"pm.watcher\",\"{tag}\"],PersistenceRequired:1b}}")
    R.func(f"enc/{sid}/spawn_one", [
        f"execute store result score #pick pm.tmp run random value 0..{len(s.spawns) - 1}",
        *spawn_lines,
        f"execute at @e[type=creaking,tag={tag},limit=1,sort=nearest] run particle minecraft:white_ash ~ ~1 ~ 0.5 1 0.5 0.01 40 normal",
    ])
    R.func(f"enc/{sid}/tick", [
        f"execute unless score {state} pm.world matches 1 run return fail",
        f"scoreboard players add #t.{sid} pm.world 1",
        f"execute store result score #n pm.tmp if entity @a[x={cx},y={cy},z={cz},distance=..{s.radius},gamemode=!spectator]",
        f"execute if score #n pm.tmp matches 0 run scoreboard players add #idle.{sid} pm.world 1",
        f"execute if score #n pm.tmp matches 1.. run scoreboard players set #idle.{sid} pm.world 0",
        f"execute if score #idle.{sid} pm.world matches 15.. run return run function {fid(f'enc/{sid}/reset')}",
        # cap = base + per_player * (players - 1), adjusted by difficulty
        f"scoreboard players operation #cap pm.tmp = #n pm.tmp",
        "scoreboard players remove #cap pm.tmp 1",
        f"scoreboard players set #k pm.tmp {s.per_player}",
        "scoreboard players operation #cap pm.tmp *= #k pm.tmp",
        f"scoreboard players add #cap pm.tmp {s.base_watchers}",
        "execute if score #set.difficulty pm.world matches 0 run scoreboard players remove #cap pm.tmp 1",
        "execute if score #set.difficulty pm.world matches 2 run scoreboard players add #cap pm.tmp 2",
        f"execute if score #cap pm.tmp matches {s.cap + 1}.. run scoreboard players set #cap pm.tmp {s.cap}",
        "execute if score #cap pm.tmp matches ..0 run scoreboard players set #cap pm.tmp 1",
        f"execute store result score #alive pm.tmp if entity @e[type=creaking,tag={tag}]",
        f"scoreboard players add #spawn.{sid} pm.world 1",
        f"execute if score #n pm.tmp matches 1.. if score #alive pm.tmp < #cap pm.tmp if score #spawn.{sid} pm.world matches {s.interval}.. run function {fid(f'enc/{sid}/spawn_one')}",
        f"execute if score #spawn.{sid} pm.world matches {s.interval}.. run scoreboard players set #spawn.{sid} pm.world 0",
        "scoreboard players set #lit pm.tmp 0",
        *lamp_count_lines,
        "execute store result bossbar palemeridian:encounter value run scoreboard players get #lit pm.tmp",
        f"bossbar set palemeridian:encounter players @a[x={cx},y={cy},z={cz},distance=..{s.radius + 16}]",
        f"execute if score #lit pm.tmp matches {len(s.lamps)}.. if score #t.{sid} pm.world matches {s.min_seconds}.. run function {fid(f'enc/{sid}/success')}",
        f"execute if score #lit pm.tmp matches {len(s.lamps)}.. unless score #t.{sid} pm.world matches {s.min_seconds}.. run title @a[x={cx},y={cy},z={cz},distance=..{s.radius}] actionbar {snbt({'text': 'Hold on — the Pall is still pushing back...', 'color': 'gold'})}",
    ])
    R.func(f"enc/{sid}/success", [
        f"scoreboard players set {state} pm.world 2",
        f"execute as @e[type=creaking,tag={tag}] at @s run particle minecraft:white_ash ~ ~1 ~ 0.4 1 0.4 0.01 60 normal",
        f"kill @e[type=creaking,tag={tag}]",
        "bossbar set palemeridian:encounter visible false",
        "time of palemeridian:surge pause",
        "time of palemeridian:surge set 0",
        *s.on_success,
        complete(s.quest),
    ])
    R.func(f"enc/{sid}/reset", [
        f"scoreboard players set {state} pm.world 0",
        f"kill @e[type=creaking,tag={tag}]",
        "bossbar set palemeridian:encounter visible false",
        "time of palemeridian:surge pause",
        "time of palemeridian:surge set 0",
        tellraw("@a", {"text": "The surge ebbs back into the fog. The lamp still waits.", "color": "gray", "italic": True}),
    ])
    # start when the quest is active and a player is in the arena; tick while running; clean leftovers
    R.slow_hooks.append(f"execute if score {s.quest} pm.q matches 1 unless score {state} pm.world matches 1..2 "
                        f"if entity @a[x={cx},y={cy},z={cz},distance=..{s.radius},gamemode=!spectator] run function {fid(f'enc/{sid}/start')}")
    R.slow_hooks.append(f"function {fid(f'enc/{sid}/tick')}")
    R.slow_hooks.append(f"execute unless score {state} pm.world matches 1 run kill @e[type=creaking,tag={tag}]")
    # a restart mid-surge resets it (the load hook runs on every server start / reload)
    R.load_hooks.append(f"execute if score {state} pm.world matches 1 run function {fid(f'enc/{sid}/reset')}")


def generate_shared() -> None:
    from . import tag
    tag("block", "palemeridian", "bulbs", [f"minecraft:{p}copper_bulb" for p in ("", "exposed_", "weathered_", "oxidized_", "waxed_", "waxed_exposed_", "waxed_weathered_", "waxed_oxidized_")])
    R.func("enc/_set_bulb", [
        "$execute if block $(x) $(y) $(z) #palemeridian:bulbs run setblock $(x) $(y) $(z) minecraft:waxed_exposed_copper_bulb[lit=$(lit)]",
    ])
