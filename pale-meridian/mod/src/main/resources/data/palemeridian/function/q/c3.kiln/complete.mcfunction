# Quest c3.kiln: Kiln Three — complete (idempotent)
execute unless score c3.kiln pm.q matches 1 run return fail
scoreboard players set c3.kiln pm.q 2
advancement grant @a only palemeridian:journal/c3/kiln
scoreboard players add #rev pm.world 1
tellraw @a [{"text":"✔ ","color":"green"},{"text":"Kiln Three","color":"green"}]
playsound minecraft:block.amethyst_block.chime master @a ~ ~ ~ 0.8 0.9
function palemeridian:q/_advance
function palemeridian:hud/refresh
