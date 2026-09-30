# Quest c4.chart: The Blank Space — complete (idempotent)
execute unless score c4.chart pm.q matches 1 run return fail
scoreboard players set c4.chart pm.q 2
advancement grant @a only palemeridian:journal/c4/chart
scoreboard players add #rev pm.world 1
tellraw @a [{"text":"✔ ","color":"green"},{"text":"The Blank Space","color":"green"}]
playsound minecraft:block.amethyst_block.chime master @a ~ ~ ~ 0.8 0.9
function palemeridian:q/_advance
function palemeridian:hud/refresh
