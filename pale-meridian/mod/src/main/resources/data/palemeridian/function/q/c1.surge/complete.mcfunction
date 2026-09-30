# Quest c1.surge: Hold the Light — complete (idempotent)
execute unless score c1.surge pm.q matches 1 run return fail
scoreboard players set c1.surge pm.q 2
advancement grant @a only palemeridian:journal/c1/surge
scoreboard players add #rev pm.world 1
tellraw @a [{"text":"✔ ","color":"green"},{"text":"Hold the Light","color":"green"}]
playsound minecraft:block.amethyst_block.chime master @a ~ ~ ~ 0.8 0.9
function palemeridian:c1/restored
function palemeridian:q/_advance
function palemeridian:hud/refresh
