execute unless score #boss pm.world matches 1 run return fail
execute if block -25 98 -14 #palemeridian:bulbs[lit=true] run return fail
function palemeridian:enc/_set_bulb {x:-25,y:98,z:-14,lit:"true"}
execute if score #b.tele pm.world matches 3 run scoreboard players set #b.tele pm.world -1
particle minecraft:end_rod -25 98 -14 0.3 0.3 0.3 0.02 25 normal
playsound minecraft:block.copper_bulb.turn_on master @a -25 98 -14 1 1
