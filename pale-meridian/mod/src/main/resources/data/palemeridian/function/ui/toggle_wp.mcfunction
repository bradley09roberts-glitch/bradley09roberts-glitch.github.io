execute store success score #t pm.tmp if score @s pm.optwp matches 1
execute if score #t pm.tmp matches 1 run scoreboard players set @s pm.optwp 0
execute if score #t pm.tmp matches 0 run scoreboard players set @s pm.optwp 1
execute if score @s pm.optwp matches 0 run attribute @s minecraft:waypoint_receive_range base set 0
execute if score @s pm.optwp matches 1 run attribute @s minecraft:waypoint_receive_range base reset
