execute store result score #day pm.tmp run time query minecraft:day repetition
execute if score @s pm.gift2 = #day pm.tmp run return run tellraw @s {"text":"Brannoc: Tomorrow. Trees don't hurry.","color":"yellow"}
scoreboard players operation @s pm.gift2 = #day pm.tmp
give @s minecraft:apple 3
give @s minecraft:honey_bottle 1
