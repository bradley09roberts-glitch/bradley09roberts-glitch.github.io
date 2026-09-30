execute unless score #enc.hollin pm.world matches 1 run return fail
execute if block 136 70 126 #palemeridian:bulbs[lit=true] run return fail
function palemeridian:enc/_set_bulb {x:136,y:70,z:126,lit:"true"}
particle minecraft:end_rod 136 70 126 0.3 0.3 0.3 0.02 25 normal
playsound minecraft:block.copper_bulb.turn_on master @a 136 70 126 1 1
tellraw @a[distance=..48] {"text":"A lamp flares back to life.","color":"gold","italic":true}
