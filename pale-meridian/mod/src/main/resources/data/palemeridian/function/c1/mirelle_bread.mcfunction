execute store result score #day pm.tmp run time query minecraft:day repetition
execute if score @s pm.bread = #day pm.tmp run return run tellraw @s {"text":"Mirelle: You've had today's loaf! Come back in the morning.","color":"red"}
scoreboard players operation @s pm.bread = #day pm.tmp
give @s minecraft:bread 4
