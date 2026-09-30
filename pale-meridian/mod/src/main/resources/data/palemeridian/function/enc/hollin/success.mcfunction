scoreboard players set #enc.hollin pm.world 2
execute as @e[type=creaking,tag=pm.surge.hollin] at @s run particle minecraft:white_ash ~ ~1 ~ 0.4 1 0.4 0.01 60 normal
kill @e[type=creaking,tag=pm.surge.hollin]
bossbar set palemeridian:encounter visible false
time of palemeridian:surge pause
time of palemeridian:surge set 0
function palemeridian:q/c1.surge/complete
