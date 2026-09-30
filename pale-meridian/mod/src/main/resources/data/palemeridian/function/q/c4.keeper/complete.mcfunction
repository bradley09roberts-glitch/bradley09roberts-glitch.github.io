# Quest c4.keeper: The Keeper — complete (idempotent)
execute unless score c4.keeper pm.q matches 1 run return fail
scoreboard players set c4.keeper pm.q 2
advancement grant @a only palemeridian:journal/c4/keeper
scoreboard players add #rev pm.world 1
tellraw @a [{"text":"✔ ","color":"green"},{"text":"The Keeper","color":"green"}]
playsound minecraft:block.amethyst_block.chime master @a ~ ~ ~ 0.8 0.9
function palemeridian:q/_advance
function palemeridian:hud/refresh
