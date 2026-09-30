execute unless entity @s[tag=pm.host] if entity @a[tag=pm.host] run return run tellraw @s {"text":"Only the host can change world settings on this server.","color":"red"}
execute store success score #t pm.tmp if score #set.chill pm.world matches 1
execute if score #t pm.tmp matches 1 run scoreboard players set #set.chill pm.world 0
execute if score #t pm.tmp matches 0 run scoreboard players set #set.chill pm.world 1
execute as @a run scoreboard players set @s pm.chill 0
