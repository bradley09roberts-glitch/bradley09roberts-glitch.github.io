# Quest c1.odile: The Lamplighter — complete (idempotent)
execute unless score c1.odile pm.q matches 1 run return fail
scoreboard players set c1.odile pm.q 2
advancement grant @a only palemeridian:journal/c1/odile
scoreboard players add #rev pm.world 1
tellraw @a [{"text":"✔ ","color":"green"},{"text":"The Lamplighter","color":"green"}]
playsound minecraft:block.amethyst_block.chime master @a ~ ~ ~ 0.8 0.9
function palemeridian:q/_advance
function palemeridian:hud/refresh
