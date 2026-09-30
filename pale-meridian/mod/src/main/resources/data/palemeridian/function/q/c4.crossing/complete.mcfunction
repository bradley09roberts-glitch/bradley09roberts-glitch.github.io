# Quest c4.crossing: The Crossing — complete (idempotent)
execute unless score c4.crossing pm.q matches 1 run return fail
scoreboard players set c4.crossing pm.q 2
advancement grant @a only palemeridian:journal/c4/crossing
scoreboard players add #rev pm.world 1
tellraw @a [{"text":"✔ ","color":"green"},{"text":"The Crossing","color":"green"}]
playsound minecraft:block.amethyst_block.chime master @a ~ ~ ~ 0.8 0.9
function palemeridian:q/_advance
function palemeridian:hud/refresh
