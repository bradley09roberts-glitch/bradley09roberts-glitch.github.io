# Quest c1.round: The Lamplighters' Round — complete (idempotent)
execute unless score c1.round pm.q matches 1 run return fail
scoreboard players set c1.round pm.q 2
advancement grant @a only palemeridian:journal/c1/round
scoreboard players add #rev pm.world 1
tellraw @a [{"text":"✔ ","color":"green"},{"text":"The Lamplighters' Round","color":"green"}]
playsound minecraft:block.amethyst_block.chime master @a ~ ~ ~ 0.8 0.9
function palemeridian:q/_advance
function palemeridian:hud/refresh
