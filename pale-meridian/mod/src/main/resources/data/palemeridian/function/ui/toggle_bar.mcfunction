execute store success score #t pm.tmp if score @s pm.optbar matches 1
execute if score #t pm.tmp matches 1 run scoreboard players set @s pm.optbar 0
execute if score #t pm.tmp matches 0 run scoreboard players set @s pm.optbar 1
function palemeridian:hud/refresh
