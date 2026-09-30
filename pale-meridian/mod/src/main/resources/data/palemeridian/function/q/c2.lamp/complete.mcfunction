# Quest c2.lamp: The Orchard Wakelamp — complete (idempotent)
execute unless score c2.lamp pm.q matches 1 run return fail
scoreboard players set c2.lamp pm.q 2
advancement grant @a only palemeridian:journal/c2/lamp
scoreboard players add #rev pm.world 1
tellraw @a [{"text":"✔ ","color":"green"},{"text":"The Orchard Wakelamp","color":"green"}]
playsound minecraft:block.amethyst_block.chime master @a ~ ~ ~ 0.8 0.9
function palemeridian:c2/restored
function palemeridian:q/_advance
function palemeridian:hud/refresh
