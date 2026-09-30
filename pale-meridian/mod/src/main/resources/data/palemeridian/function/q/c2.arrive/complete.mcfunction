# Quest c2.arrive: The Orchard Road — complete (idempotent)
execute unless score c2.arrive pm.q matches 1 run return fail
scoreboard players set c2.arrive pm.q 2
advancement grant @a only palemeridian:journal/c2/arrive
scoreboard players add #rev pm.world 1
tellraw @a [{"text":"✔ ","color":"green"},{"text":"The Orchard Road","color":"green"}]
playsound minecraft:block.amethyst_block.chime master @a ~ ~ ~ 0.8 0.9
function palemeridian:q/_advance
function palemeridian:hud/refresh
