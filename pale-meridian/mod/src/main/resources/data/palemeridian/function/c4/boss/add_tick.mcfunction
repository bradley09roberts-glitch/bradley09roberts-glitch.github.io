scoreboard players remove #b.add pm.world 1
execute if score #b.add pm.world matches 1.. run return 0
scoreboard players set #b.add pm.world 10
execute store result score #alive pm.tmp if entity @e[type=creaking,tag=pm.bossadd]
execute store result score #cap pm.tmp if entity @a[x=-26,y=95,z=-24,dx=22,dy=12,dz=22,gamemode=!spectator]
scoreboard players add #cap pm.tmp 1
execute if score #set.difficulty pm.world matches 0 run scoreboard players remove #cap pm.tmp 1
execute if score #alive pm.tmp >= #cap pm.tmp run return 0
execute store result score #pick pm.tmp run random value 0..7
execute if score #pick pm.tmp matches 0 run summon minecraft:creaking -7 96 -10 {Tags:["pm.bossadd","pm.bossfx"],PersistenceRequired:1b}
execute if score #pick pm.tmp matches 1 run summon minecraft:creaking -12 96 -5 {Tags:["pm.bossadd","pm.bossfx"],PersistenceRequired:1b}
execute if score #pick pm.tmp matches 2 run summon minecraft:creaking -20 96 -5 {Tags:["pm.bossadd","pm.bossfx"],PersistenceRequired:1b}
execute if score #pick pm.tmp matches 3 run summon minecraft:creaking -25 96 -10 {Tags:["pm.bossadd","pm.bossfx"],PersistenceRequired:1b}
execute if score #pick pm.tmp matches 4 run summon minecraft:creaking -25 96 -18 {Tags:["pm.bossadd","pm.bossfx"],PersistenceRequired:1b}
execute if score #pick pm.tmp matches 5 run summon minecraft:creaking -20 96 -23 {Tags:["pm.bossadd","pm.bossfx"],PersistenceRequired:1b}
execute if score #pick pm.tmp matches 6 run summon minecraft:creaking -12 96 -23 {Tags:["pm.bossadd","pm.bossfx"],PersistenceRequired:1b}
execute if score #pick pm.tmp matches 7 run summon minecraft:creaking -7 96 -18 {Tags:["pm.bossadd","pm.bossfx"],PersistenceRequired:1b}
