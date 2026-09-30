execute if items entity @s container.* *[minecraft:custom_data~{pm:{item:"keepers_glass"}}] run return fail
execute if score @s pm.glass matches 1 run return fail
scoreboard players set @s pm.glass 1
give @s minecraft:spyglass[minecraft:custom_name={"text":"Keeper's Glass","italic":false,"color":"aqua"},minecraft:lore=[{"text":"Keeper of the Meridian","italic":false,"color":"gold"},{"text":"A chart is a promise to look.","italic":false,"color":"gray"}],minecraft:enchantment_glint_override=true,minecraft:custom_data={pm:{item:"keepers_glass"}}] 1
