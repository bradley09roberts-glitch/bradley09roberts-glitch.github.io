scoreboard players set #r.glassworks pm.world 1
scoreboard players set #req.glassworks pm.world 1
time of palemeridian:pall set 4000
scoreboard players set #chapter pm.world 4
function palemeridian:enc/_set_bulb {x:20,y:73,z:-283,lit:"true"}
function palemeridian:enc/_set_bulb {x:30,y:73,z:-283,lit:"true"}
function palemeridian:enc/_set_bulb {x:38,y:73,z:-296,lit:"true"}
function palemeridian:enc/_set_bulb {x:12,y:73,z:-288,lit:"true"}
function palemeridian:enc/_set_bulb {x:26,y:73,z:-312,lit:"true"}
title @a title {"text":"The Glassworks","color":"white"}
title @a subtitle {"text":"remembers","color":"gray","italic":true}
playsound minecraft:block.bell.resonate master @a ~ ~ ~ 1.5 0.9
tellraw @a {"text":"The fog drains out of the kiln yard and down the road. The mine mouth stays dark: no lamp holds there, because the Deepcut is not on the Chart.","color":"gray","italic":true}
tellraw @a {"text":"(The Glassworks is restored. Three lamps burn. Tamsin has gone ahead to Hollin.)","color":"dark_aqua"}
