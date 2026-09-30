execute unless predicate palemeridian:in_pall run return run function palemeridian:player/chill_warm
execute if predicate palemeridian:holding_light run return run function palemeridian:player/chill_warm
execute if entity @a[distance=0.1..6,predicate=palemeridian:holding_light] run return run function palemeridian:player/chill_warm
execute if function palemeridian:player/_near_warm run return run function palemeridian:player/chill_warm
scoreboard players add @s pm.chill 1
execute if score @s pm.chill matches 8 run title @s actionbar {"text":"The Pall is cold without light. Hold a torch or lantern.","color":"gray","italic":true}
execute if score @s pm.chill matches 20.. run effect give @s minecraft:slowness 3 0 true
execute if score @s pm.chill matches 20 run title @s actionbar {"text":"Your thoughts go grey at the edges...","color":"gray","italic":true}
execute if score @s pm.chill matches 35.. if score @s pm.optfx matches 1 run effect give @s minecraft:darkness 3 0 true
execute if score @s pm.chill matches 35.. unless score @s pm.optfx matches 1 run title @s actionbar {"text":"(The fog presses in — find light.)","color":"gray"}
execute if score @s pm.chill matches 50.. run effect give @s minecraft:mining_fatigue 3 0 true
execute if score @s pm.chill matches 60.. run scoreboard players set @s pm.chill 60
