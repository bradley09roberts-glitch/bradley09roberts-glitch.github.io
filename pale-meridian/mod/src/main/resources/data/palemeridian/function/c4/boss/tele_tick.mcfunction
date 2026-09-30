scoreboard players remove #b.telet pm.world 1
execute if score #b.tele pm.world matches 0 run particle minecraft:smoke -16 98 -23 0.3 0.3 0.3 0.02 30 normal
execute if score #b.tele pm.world matches 0 run playsound minecraft:block.copper_bulb.turn_off master @a -16 98 -23 1 0.5
execute if score #b.tele pm.world matches 1 run particle minecraft:smoke -7 98 -14 0.3 0.3 0.3 0.02 30 normal
execute if score #b.tele pm.world matches 1 run playsound minecraft:block.copper_bulb.turn_off master @a -7 98 -14 1 0.5
execute if score #b.tele pm.world matches 2 run particle minecraft:smoke -16 98 -5 0.3 0.3 0.3 0.02 30 normal
execute if score #b.tele pm.world matches 2 run playsound minecraft:block.copper_bulb.turn_off master @a -16 98 -5 1 0.5
execute if score #b.tele pm.world matches 3 run particle minecraft:smoke -25 98 -14 0.3 0.3 0.3 0.02 30 normal
execute if score #b.tele pm.world matches 3 run playsound minecraft:block.copper_bulb.turn_off master @a -25 98 -14 1 0.5
execute if score #b.telet pm.world matches 1.. run return 0
execute if score #b.tele pm.world matches 0 run function palemeridian:enc/_set_bulb {x:-16,y:98,z:-23,lit:"false"}
execute if score #b.tele pm.world matches 1 run function palemeridian:enc/_set_bulb {x:-7,y:98,z:-14,lit:"false"}
execute if score #b.tele pm.world matches 2 run function palemeridian:enc/_set_bulb {x:-16,y:98,z:-5,lit:"false"}
execute if score #b.tele pm.world matches 3 run function palemeridian:enc/_set_bulb {x:-25,y:98,z:-14,lit:"false"}
tellraw @a[x=-26,y=95,z=-24,dx=22,dy=12,dz=22] {"text":"The Pall snuffs a relay lamp!","color":"gold"}
scoreboard players set #b.tele pm.world -1
