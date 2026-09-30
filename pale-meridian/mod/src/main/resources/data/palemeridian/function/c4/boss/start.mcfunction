execute if score #boss pm.world matches 1..2 run return fail
scoreboard players set #boss pm.world 1
scoreboard players set #b.t pm.world 0
scoreboard players set #b.idle pm.world 0
scoreboard players set #b.tele pm.world -1
scoreboard players set #b.snuff pm.world 15
scoreboard players set #b.add pm.world 8
scoreboard players set #b.blink pm.world 12
scoreboard players set #b.phase pm.world 1
execute store result score #n pm.tmp if entity @a[x=-26,y=95,z=-24,dx=22,dy=12,dz=22,gamemode=!spectator]
scoreboard players remove #n pm.tmp 1
execute if score #n pm.tmp matches 4.. run scoreboard players set #n pm.tmp 3
scoreboard players set #b.max pm.world 180
scoreboard players set #k pm.tmp 90
execute if score #set.difficulty pm.world matches 0 run scoreboard players set #b.max pm.world 110
execute if score #set.difficulty pm.world matches 0 run scoreboard players set #k pm.tmp 60
execute if score #set.difficulty pm.world matches 2 run scoreboard players set #b.max pm.world 240
execute if score #set.difficulty pm.world matches 2 run scoreboard players set #k pm.tmp 110
scoreboard players operation #k pm.tmp *= #n pm.tmp
scoreboard players operation #b.max pm.world += #k pm.tmp
function palemeridian:enc/_set_bulb {x:-16,y:98,z:-23,lit:"true"}
function palemeridian:enc/_set_bulb {x:-7,y:98,z:-14,lit:"true"}
function palemeridian:enc/_set_bulb {x:-16,y:98,z:-5,lit:"true"}
function palemeridian:enc/_set_bulb {x:-25,y:98,z:-14,lit:"true"}
summon minecraft:creaking -15 96 -20 {Tags:["pm.boss","pm.bossfx"],PersistenceRequired:1b,CustomName:{"text":"The Unlooked","color":"white"},attributes:[{id:"minecraft:scale",base:2.5d},{id:"minecraft:attack_damage",base:5.0d},{id:"minecraft:knockback_resistance",base:0.8d}]}
execute store result storage palemeridian:tmp boss.hp int 1 run scoreboard players get #b.max pm.world
function palemeridian:c4/boss/_hp with storage palemeridian:tmp boss
execute store result bossbar palemeridian:boss max run scoreboard players get #b.max pm.world
execute store result bossbar palemeridian:boss value run scoreboard players get #b.max pm.world
bossbar set palemeridian:boss players @a[x=-26,y=95,z=-24,dx=22,dy=12,dz=22]
bossbar set palemeridian:boss visible true
time of palemeridian:surge resume
particle minecraft:white_ash -15 98 -20 2 3 2 0.02 300 normal
playsound minecraft:entity.warden.emerge master @a -15 96 -13 2 0.6
tellraw @a[x=-26,y=95,z=-24,dx=22,dy=12,dz=22] [{"text":"The fog on the gallery gathers itself, and stands up. ","color":"red","italic":true},{"text":"It moves only while nobody is watching it.","color":"gray","italic":true}]
