# Quest c2.hearts: The Heartwood — complete (idempotent)
execute unless score c2.hearts pm.q matches 1 run return fail
scoreboard players set c2.hearts pm.q 2
advancement grant @a only palemeridian:journal/c2/hearts
scoreboard players add #rev pm.world 1
tellraw @a [{"text":"✔ ","color":"green"},{"text":"The Heartwood","color":"green"}]
playsound minecraft:block.amethyst_block.chime master @a ~ ~ ~ 0.8 0.9
function palemeridian:q/_advance
function palemeridian:hud/refresh
