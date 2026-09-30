execute unless score #enc.glassworks pm.world matches 1 run return fail
scoreboard players add #t.glassworks pm.world 1
execute store result score #n pm.tmp if entity @a[x=24,y=70,z=-295,distance=..22,gamemode=!spectator]
execute if score #n pm.tmp matches 0 run scoreboard players add #idle.glassworks pm.world 1
execute if score #n pm.tmp matches 1.. run scoreboard players set #idle.glassworks pm.world 0
execute if score #idle.glassworks pm.world matches 15.. run return run function palemeridian:enc/glassworks/reset
scoreboard players operation #cap pm.tmp = #n pm.tmp
scoreboard players remove #cap pm.tmp 1
scoreboard players set #k pm.tmp 2
scoreboard players operation #cap pm.tmp *= #k pm.tmp
scoreboard players add #cap pm.tmp 3
execute if score #set.difficulty pm.world matches 0 run scoreboard players remove #cap pm.tmp 1
execute if score #set.difficulty pm.world matches 2 run scoreboard players add #cap pm.tmp 2
execute if score #cap pm.tmp matches 9.. run scoreboard players set #cap pm.tmp 8
execute if score #cap pm.tmp matches ..0 run scoreboard players set #cap pm.tmp 1
execute store result score #alive pm.tmp if entity @e[type=creaking,tag=pm.surge.glassworks]
scoreboard players add #spawn.glassworks pm.world 1
execute if score #n pm.tmp matches 1.. if score #alive pm.tmp < #cap pm.tmp if score #spawn.glassworks pm.world matches 6.. run function palemeridian:enc/glassworks/spawn_one
execute if score #spawn.glassworks pm.world matches 6.. run scoreboard players set #spawn.glassworks pm.world 0
scoreboard players set #lit pm.tmp 0
execute if block 16 71 -304 #minecraft:candles[lit=true] run scoreboard players add #lit pm.tmp 1
execute if block 32 71 -304 #minecraft:candles[lit=true] run scoreboard players add #lit pm.tmp 1
execute if block 16 71 -288 #minecraft:candles[lit=true] run scoreboard players add #lit pm.tmp 1
execute if block 32 71 -288 #minecraft:candles[lit=true] run scoreboard players add #lit pm.tmp 1
execute store result bossbar palemeridian:encounter value run scoreboard players get #lit pm.tmp
bossbar set palemeridian:encounter players @a[x=24,y=70,z=-295,distance=..38]
scoreboard players set #left.glassworks pm.world 120
scoreboard players operation #left.glassworks pm.world -= #t.glassworks pm.world
bossbar set palemeridian:encounter name [{"text":"The Collapse — ","color":"red"},{"score":{"name":"#left.glassworks","objective":"pm.world"},"color":"white"},{"text":"s","color":"white"}]
execute unless score #lit pm.tmp matches 4.. if score #left.glassworks pm.world matches ..0 run return run function palemeridian:enc/glassworks/fail
execute if score #lit pm.tmp matches 4.. if score #t.glassworks pm.world matches 20.. run function palemeridian:enc/glassworks/success
execute if score #lit pm.tmp matches 4.. unless score #t.glassworks pm.world matches 20.. run title @a[x=24,y=70,z=-295,distance=..22] actionbar {"text":"Hold on — the Pall is still pushing back...","color":"gold"}
