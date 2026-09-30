scoreboard players set #enc.hollin pm.world 0
kill @e[type=creaking,tag=pm.surge.hollin]
bossbar set palemeridian:encounter visible false
time of palemeridian:surge pause
time of palemeridian:surge set 0
tellraw @a {"text":"The surge ebbs back into the fog. The lamp still waits.","color":"gray","italic":true}
