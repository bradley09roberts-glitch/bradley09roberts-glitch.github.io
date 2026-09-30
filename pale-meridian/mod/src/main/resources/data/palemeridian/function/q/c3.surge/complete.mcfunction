# Quest c3.surge: The Collapse — complete (idempotent)
execute unless score c3.surge pm.q matches 1 run return fail
scoreboard players set c3.surge pm.q 2
advancement grant @a only palemeridian:journal/c3/surge
scoreboard players add #rev pm.world 1
tellraw @a [{"text":"✔ ","color":"green"},{"text":"The Collapse","color":"green"}]
playsound minecraft:block.amethyst_block.chime master @a ~ ~ ~ 0.8 0.9
function palemeridian:c3/restored
function palemeridian:q/_advance
function palemeridian:hud/refresh
