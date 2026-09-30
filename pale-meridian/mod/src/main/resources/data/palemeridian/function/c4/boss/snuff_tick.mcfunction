execute if score #b.tele pm.world matches 0.. run return run function palemeridian:c4/boss/tele_tick
scoreboard players remove #b.snuff pm.world 1
execute if score #b.snuff pm.world matches 1.. run return 0
scoreboard players set #b.snuff pm.world 20
execute if score #b.phase pm.world matches 2.. run scoreboard players set #b.snuff pm.world 12
execute if score #set.difficulty pm.world matches 0 run scoreboard players add #b.snuff pm.world 6
execute store result score #pick pm.tmp run random value 0..3
execute if score #b.tele pm.world matches -1 if score #pick pm.tmp matches 0 if block -16 98 -23 #palemeridian:bulbs[lit=true] run scoreboard players set #b.tele pm.world 0
execute if score #b.tele pm.world matches -1 if score #pick pm.tmp matches 1 if block -7 98 -14 #palemeridian:bulbs[lit=true] run scoreboard players set #b.tele pm.world 1
execute if score #b.tele pm.world matches -1 if score #pick pm.tmp matches 2 if block -16 98 -5 #palemeridian:bulbs[lit=true] run scoreboard players set #b.tele pm.world 2
execute if score #b.tele pm.world matches -1 if score #pick pm.tmp matches 3 if block -25 98 -14 #palemeridian:bulbs[lit=true] run scoreboard players set #b.tele pm.world 3
execute if score #b.tele pm.world matches -1 if block -16 98 -23 #palemeridian:bulbs[lit=true] run scoreboard players set #b.tele pm.world 0
execute if score #b.tele pm.world matches -1 if block -7 98 -14 #palemeridian:bulbs[lit=true] run scoreboard players set #b.tele pm.world 1
execute if score #b.tele pm.world matches -1 if block -16 98 -5 #palemeridian:bulbs[lit=true] run scoreboard players set #b.tele pm.world 2
execute if score #b.tele pm.world matches -1 if block -25 98 -14 #palemeridian:bulbs[lit=true] run scoreboard players set #b.tele pm.world 3
scoreboard players set #b.telet pm.world 3
