# Quest c3.log: The Foreman's Log — complete (idempotent)
execute unless score c3.log pm.q matches 1 run return fail
scoreboard players set c3.log pm.q 2
advancement grant @a only palemeridian:journal/c3/log
scoreboard players add #rev pm.world 1
tellraw @a [{"text":"✔ ","color":"green"},{"text":"The Foreman's Log","color":"green"}]
playsound minecraft:block.amethyst_block.chime master @a ~ ~ ~ 0.8 0.9
function palemeridian:q/_advance
function palemeridian:hud/refresh
