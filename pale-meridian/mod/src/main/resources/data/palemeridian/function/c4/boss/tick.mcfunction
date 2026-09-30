execute unless score #boss pm.world matches 1 run return fail
scoreboard players add #b.t pm.world 1
execute store result score #n pm.tmp if entity @a[x=-26,y=95,z=-24,dx=22,dy=12,dz=22,gamemode=!spectator]
execute if score #n pm.tmp matches 0 run scoreboard players add #b.idle pm.world 1
execute if score #n pm.tmp matches 1.. run scoreboard players set #b.idle pm.world 0
execute if score #b.idle pm.world matches 10.. run return run function palemeridian:c4/boss/reset
execute unless entity @e[type=creaking,tag=pm.boss,limit=1] run return run function palemeridian:c4/boss/win
execute as @e[type=creaking,tag=pm.boss,limit=1] unless entity @s[x=-27,y=93,z=-25,dx=24,dy=16,dz=24] run tp @s -15 96 -19
execute store result score #b.hp pm.world run data get entity @e[type=creaking,tag=pm.boss,limit=1] Health
execute store result bossbar palemeridian:boss value run scoreboard players get #b.hp pm.world
bossbar set palemeridian:boss players @a[x=-26,y=95,z=-24,dx=22,dy=12,dz=22]
scoreboard players operation #b.pct pm.world = #b.hp pm.world
scoreboard players set #c pm.tmp 100
scoreboard players operation #b.pct pm.world *= #c pm.tmp
scoreboard players operation #b.pct pm.world /= #b.max pm.world
execute if score #b.phase pm.world matches 1 if score #b.pct pm.world matches ..66 run function palemeridian:c4/boss/phase2
execute if score #b.phase pm.world matches 2 if score #b.pct pm.world matches ..33 run function palemeridian:c4/boss/phase3
scoreboard players set #lit pm.tmp 0
execute if block -16 98 -23 #palemeridian:bulbs[lit=true] run scoreboard players add #lit pm.tmp 1
execute if block -7 98 -14 #palemeridian:bulbs[lit=true] run scoreboard players add #lit pm.tmp 1
execute if block -16 98 -5 #palemeridian:bulbs[lit=true] run scoreboard players add #lit pm.tmp 1
execute if block -25 98 -14 #palemeridian:bulbs[lit=true] run scoreboard players add #lit pm.tmp 1
execute if score #lit pm.tmp matches ..2 run function palemeridian:c4/boss/regen
function palemeridian:c4/boss/snuff_tick
execute if score #b.phase pm.world matches 2.. run function palemeridian:c4/boss/add_tick
execute if score #b.phase pm.world matches 3 run function palemeridian:c4/boss/blink_tick
