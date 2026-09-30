# Quest p.lamp: The Landing Lamp — complete (idempotent)
execute unless score p.lamp pm.q matches 1 run return fail
scoreboard players set p.lamp pm.q 2
advancement grant @a only palemeridian:journal/p/lamp
scoreboard players add #rev pm.world 1
tellraw @a [{"text":"✔ ","color":"green"},{"text":"The Landing Lamp","color":"green"}]
playsound minecraft:block.amethyst_block.chime master @a ~ ~ ~ 0.8 0.9
function palemeridian:prologue/lamp_lit
function palemeridian:q/_advance
function palemeridian:hud/refresh
