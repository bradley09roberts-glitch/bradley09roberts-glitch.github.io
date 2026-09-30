execute unless score #enc.hollin pm.world matches 1 run return fail
execute if block 104 70 112 #palemeridian:bulbs[lit=true] run return fail
function palemeridian:enc/_set_bulb {x:104,y:70,z:112,lit:"true"}
particle minecraft:end_rod 104 70 112 0.3 0.3 0.3 0.02 25 normal
playsound minecraft:block.copper_bulb.turn_on master @a 104 70 112 1 1
tellraw @a[distance=..48] {"text":"A lamp flares back to life.","color":"gold","italic":true}
