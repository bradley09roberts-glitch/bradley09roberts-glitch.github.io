scoreboard players set #enc.glassworks pm.world 2
execute as @e[type=creaking,tag=pm.surge.glassworks] at @s run particle minecraft:white_ash ~ ~1 ~ 0.4 1 0.4 0.01 60 normal
kill @e[type=creaking,tag=pm.surge.glassworks]
bossbar set palemeridian:encounter visible false
time of palemeridian:surge pause
time of palemeridian:surge set 0
function palemeridian:q/c3.surge/complete
