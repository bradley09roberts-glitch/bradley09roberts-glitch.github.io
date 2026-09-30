execute unless score #boss pm.world matches 1 run return fail
execute if block -16 98 -23 #palemeridian:bulbs[lit=true] run return fail
function palemeridian:enc/_set_bulb {x:-16,y:98,z:-23,lit:"true"}
execute if score #b.tele pm.world matches 0 run scoreboard players set #b.tele pm.world -1
particle minecraft:end_rod -16 98 -23 0.3 0.3 0.3 0.02 25 normal
playsound minecraft:block.copper_bulb.turn_on master @a -16 98 -23 1 1
