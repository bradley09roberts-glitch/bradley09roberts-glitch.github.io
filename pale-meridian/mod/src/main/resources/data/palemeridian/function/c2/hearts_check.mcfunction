scoreboard players set #hearts pm.tmp 0
execute unless block 358 85 -14 minecraft:creaking_heart run scoreboard players add #hearts pm.tmp 1
execute unless block 352 79 -14 minecraft:creaking_heart run scoreboard players add #hearts pm.tmp 1
execute unless block 364 79 -14 minecraft:creaking_heart run scoreboard players add #hearts pm.tmp 1
execute unless score #hearts pm.tmp = c2.hearts pm.qp run function palemeridian:c2/hearts_progress
execute if score #hearts pm.tmp matches 3.. run function palemeridian:c2/hearts_done
