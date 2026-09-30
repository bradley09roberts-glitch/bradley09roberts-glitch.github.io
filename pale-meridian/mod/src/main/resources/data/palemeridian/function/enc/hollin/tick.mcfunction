execute unless score #enc.hollin pm.world matches 1 run return fail
scoreboard players add #t.hollin pm.world 1
execute store result score #n pm.tmp if entity @a[x=120,y=67,z=118,distance=..30,gamemode=!spectator]
execute if score #n pm.tmp matches 0 run scoreboard players add #idle.hollin pm.world 1
execute if score #n pm.tmp matches 1.. run scoreboard players set #idle.hollin pm.world 0
execute if score #idle.hollin pm.world matches 15.. run return run function palemeridian:enc/hollin/reset
scoreboard players operation #cap pm.tmp = #n pm.tmp
scoreboard players remove #cap pm.tmp 1
scoreboard players set #k pm.tmp 2
scoreboard players operation #cap pm.tmp *= #k pm.tmp
scoreboard players add #cap pm.tmp 2
execute if score #set.difficulty pm.world matches 0 run scoreboard players remove #cap pm.tmp 1
execute if score #set.difficulty pm.world matches 2 run scoreboard players add #cap pm.tmp 2
execute if score #cap pm.tmp matches 8.. run scoreboard players set #cap pm.tmp 7
execute if score #cap pm.tmp matches ..0 run scoreboard players set #cap pm.tmp 1
execute store result score #alive pm.tmp if entity @e[type=creaking,tag=pm.surge.hollin]
scoreboard players add #spawn.hollin pm.world 1
execute if score #n pm.tmp matches 1.. if score #alive pm.tmp < #cap pm.tmp if score #spawn.hollin pm.world matches 7.. run function palemeridian:enc/hollin/spawn_one
execute if score #spawn.hollin pm.world matches 7.. run scoreboard players set #spawn.hollin pm.world 0
scoreboard players set #lit pm.tmp 0
execute if block 104 70 112 #palemeridian:bulbs[lit=true] run scoreboard players add #lit pm.tmp 1
execute if block 136 70 112 #palemeridian:bulbs[lit=true] run scoreboard players add #lit pm.tmp 1
execute if block 104 70 126 #palemeridian:bulbs[lit=true] run scoreboard players add #lit pm.tmp 1
execute if block 136 70 126 #palemeridian:bulbs[lit=true] run scoreboard players add #lit pm.tmp 1
execute store result bossbar palemeridian:encounter value run scoreboard players get #lit pm.tmp
bossbar set palemeridian:encounter players @a[x=120,y=67,z=118,distance=..46]
execute if score #lit pm.tmp matches 4.. if score #t.hollin pm.world matches 30.. run function palemeridian:enc/hollin/success
execute if score #lit pm.tmp matches 4.. unless score #t.hollin pm.world matches 30.. run title @a[x=120,y=67,z=118,distance=..30] actionbar {"text":"Hold on — the Pall is still pushing back...","color":"gold"}
