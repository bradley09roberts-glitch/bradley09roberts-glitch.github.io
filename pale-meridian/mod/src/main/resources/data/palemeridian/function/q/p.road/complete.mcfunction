# Quest p.road: Into the Pall — complete (idempotent)
execute unless score p.road pm.q matches 1 run return fail
scoreboard players set p.road pm.q 2
advancement grant @a only palemeridian:journal/p/road
scoreboard players add #rev pm.world 1
tellraw @a [{"text":"✔ ","color":"green"},{"text":"Into the Pall","color":"green"}]
playsound minecraft:block.amethyst_block.chime master @a ~ ~ ~ 0.8 0.9
scoreboard players set #chapter pm.world 1
function palemeridian:q/_advance
function palemeridian:hud/refresh
