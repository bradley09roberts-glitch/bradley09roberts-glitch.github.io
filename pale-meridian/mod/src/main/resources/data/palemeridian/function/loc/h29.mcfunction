# location hook: glass_workshop
execute if score #c3.glass pm.world >= #seconds pm.world run return fail
scoreboard players operation #c3.glass pm.world = #seconds pm.world
scoreboard players add #c3.glass pm.world 60
execute if items block 40 70 -302 container.* #palemeridian:glass_items run return fail
item replace block 40 70 -302 container.11 with minecraft:glass 4
item replace block 40 70 -302 container.15 with minecraft:iron_bars 2
