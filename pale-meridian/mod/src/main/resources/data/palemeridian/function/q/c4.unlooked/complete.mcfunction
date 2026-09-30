# Quest c4.unlooked: The Unlooked — complete (idempotent)
execute unless score c4.unlooked pm.q matches 1 run return fail
scoreboard players set c4.unlooked pm.q 2
advancement grant @a only palemeridian:journal/c4/unlooked
scoreboard players add #rev pm.world 1
tellraw @a [{"text":"✔ ","color":"green"},{"text":"The Unlooked","color":"green"}]
playsound minecraft:block.amethyst_block.chime master @a ~ ~ ~ 0.8 0.9
function palemeridian:c4/lens_burns
function palemeridian:q/_advance
function palemeridian:hud/refresh
