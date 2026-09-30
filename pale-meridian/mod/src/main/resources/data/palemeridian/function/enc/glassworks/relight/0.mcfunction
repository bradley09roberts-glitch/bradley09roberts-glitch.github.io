execute unless score #enc.glassworks pm.world matches 1 run return fail
execute if block 16 71 -304 #minecraft:candles[lit=true] run return fail
function palemeridian:enc/_set_candle {x:16,y:71,z:-304,lit:"true"}
particle minecraft:end_rod 16 71 -304 0.3 0.3 0.3 0.02 25 normal
playsound minecraft:block.copper_bulb.turn_on master @a 16 71 -304 1 1
tellraw @a[distance=..48] {"text":"A lamp flares back to life.","color":"gold","italic":true}
