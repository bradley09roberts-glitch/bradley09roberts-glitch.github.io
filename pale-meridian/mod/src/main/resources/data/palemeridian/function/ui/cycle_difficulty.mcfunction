execute unless entity @s[tag=pm.host] if entity @a[tag=pm.host] run return run tellraw @s {"text":"Only the host can change world settings on this server.","color":"red"}
scoreboard players add #set.difficulty pm.world 1
execute if score #set.difficulty pm.world matches 3.. run scoreboard players set #set.difficulty pm.world 0
