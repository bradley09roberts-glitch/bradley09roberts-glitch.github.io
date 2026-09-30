# Quest c1.names: Hollin, Unremembered — complete (idempotent)
execute unless score c1.names pm.q matches 1 run return fail
scoreboard players set c1.names pm.q 2
advancement grant @a only palemeridian:journal/c1/names
scoreboard players add #rev pm.world 1
tellraw @a [{"text":"✔ ","color":"green"},{"text":"Hollin, Unremembered","color":"green"}]
playsound minecraft:block.amethyst_block.chime master @a ~ ~ ~ 0.8 0.9
function palemeridian:q/_advance
function palemeridian:hud/refresh
