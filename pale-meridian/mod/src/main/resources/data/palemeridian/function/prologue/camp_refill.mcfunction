# Recovery: keep the lamp materials available while the lamp is unfinished.
execute unless score p.lamp pm.q matches 0..1 run return fail
execute if score #camp_refill pm.world >= #seconds pm.world run return fail
scoreboard players operation #camp_refill pm.world = #seconds pm.world
scoreboard players add #camp_refill pm.world 120
execute unless items block 18 86 332 container.* minecraft:stone_bricks run item replace block 18 86 332 container.3 with minecraft:stone_bricks 2
execute unless items block 18 86 332 container.* #palemeridian:chains run item replace block 18 86 332 container.4 with minecraft:iron_chain 1
execute unless items block 18 86 332 container.* minecraft:iron_nugget run item replace block 18 86 332 container.2 with minecraft:iron_nugget 8
execute unless items block 18 86 332 container.* minecraft:torch run item replace block 18 86 332 container.1 with minecraft:torch 6
