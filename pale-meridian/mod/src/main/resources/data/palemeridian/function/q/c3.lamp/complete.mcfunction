# Quest c3.lamp: The Glassworks Wakelamp — complete (idempotent)
execute unless score c3.lamp pm.q matches 1 run return fail
scoreboard players set c3.lamp pm.q 2
advancement grant @a only palemeridian:journal/c3/lamp
scoreboard players add #rev pm.world 1
tellraw @a [{"text":"✔ ","color":"green"},{"text":"The Glassworks Wakelamp","color":"green"}]
playsound minecraft:block.amethyst_block.chime master @a ~ ~ ~ 0.8 0.9
function palemeridian:q/_advance
function palemeridian:hud/refresh
