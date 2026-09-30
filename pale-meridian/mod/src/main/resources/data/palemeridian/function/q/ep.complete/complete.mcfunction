# Quest ep.complete: Survey Complete — complete (idempotent)
execute unless score ep.complete pm.q matches 1 run return fail
scoreboard players set ep.complete pm.q 2
advancement grant @a only palemeridian:journal/ep/complete
scoreboard players add #rev pm.world 1
tellraw @a [{"text":"✔ ","color":"green"},{"text":"Survey Complete","color":"green"}]
playsound minecraft:block.amethyst_block.chime master @a ~ ~ ~ 0.8 0.9
function palemeridian:c4/epilogue
function palemeridian:q/_advance
function palemeridian:hud/refresh
