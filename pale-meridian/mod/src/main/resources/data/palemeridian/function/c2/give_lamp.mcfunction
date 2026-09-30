execute if score #c2.lamp_given pm.world matches 1 if entity @a[predicate=palemeridian:holds_stillglass] run return run tellraw @s {"text":"Brannoc: Somebody already has Col's lamp. Up the mill with it.","color":"gold"}
execute if block 356 93 -46 minecraft:pearlescent_froglight run return fail
scoreboard players set #c2.lamp_given pm.world 1
give @s minecraft:pearlescent_froglight[minecraft:custom_name={"text":"Stillglass Lamp","italic":false,"color":"aqua"},minecraft:lore=[{"text":"Col Hale made this one. His initials are on the base.","italic":false,"color":"gray"}]] 1
