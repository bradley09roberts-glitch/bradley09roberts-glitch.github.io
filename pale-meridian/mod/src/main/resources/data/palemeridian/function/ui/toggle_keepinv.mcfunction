execute unless entity @s[tag=pm.host] if entity @a[tag=pm.host] run return run tellraw @s {"text":"Only the host can change world settings on this server.","color":"red"}
execute store result score #t pm.tmp run gamerule minecraft:keep_inventory
execute if score #t pm.tmp matches 1 run gamerule minecraft:keep_inventory false
execute if score #t pm.tmp matches 0 run gamerule minecraft:keep_inventory true
