scoreboard players set #enc.glassworks pm.world 0
scoreboard players operation #cool.glassworks pm.world = #seconds pm.world
scoreboard players add #cool.glassworks pm.world 10
kill @e[type=creaking,tag=pm.surge.glassworks]
bossbar set palemeridian:encounter visible false
time of palemeridian:surge pause
time of palemeridian:surge set 0
tellraw @a {"text":"The surge ebbs back into the fog. The lamp still waits.","color":"gray","italic":true}
