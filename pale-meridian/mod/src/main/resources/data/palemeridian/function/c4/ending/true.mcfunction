execute unless score c4.chart pm.q matches 1 run return fail
execute if score #ending pm.world matches 1.. run return fail
scoreboard players set #ending pm.world 1
tellraw @a [{"selector":"@s","color":"white"},{"text":" dips the pen and writes the names.","color":"gray","italic":true}]
scoreboard players set #r.deepcut pm.world 1
scoreboard players set #req.deepcut pm.world 1
scoreboard players set #r.mere pm.world 1
scoreboard players set #req.mere pm.world 1
scoreboard players set #r.fen pm.world 1
scoreboard players set #req.fen pm.world 1
scoreboard players set #r.wilds pm.world 1
scoreboard players set #req.wilds pm.world 1
time of palemeridian:pall set 5000
data merge block 14 72 -423 {front_text:{messages:["","Tobin Vane","",""],color:"white",has_glowing_text:1b}}
data merge block 16 72 -423 {front_text:{messages:["","Col Hale","",""],color:"white",has_glowing_text:1b}}
data merge block 18 72 -423 {front_text:{messages:["","Agnes Pell","",""],color:"white",has_glowing_text:1b}}
data merge block 20 72 -423 {front_text:{messages:["","Wendel Tarn","",""],color:"white",has_glowing_text:1b}}
data merge block 22 72 -423 {front_text:{messages:["","Ruth Anning","",""],color:"white",has_glowing_text:1b}}
data merge block 24 72 -423 {front_text:{messages:["","Emory Dunn","",""],color:"white",has_glowing_text:1b}}
data merge block 26 72 -423 {front_text:{messages:["","Silas Crane","",""],color:"white",has_glowing_text:1b}}
data merge block 28 72 -423 {front_text:{messages:["","Nell Farrow","",""],color:"white",has_glowing_text:1b}}
data merge block 30 72 -423 {front_text:{messages:["","Hob Tulley","",""],color:"white",has_glowing_text:1b}}
data merge block 32 72 -423 {front_text:{messages:["","Mae Ostrander","",""],color:"white",has_glowing_text:1b}}
data merge block 34 72 -423 {front_text:{messages:["","Piet Lund","",""],color:"white",has_glowing_text:1b}}
setblock 14 70 -423 minecraft:candle[candles=1,lit=true,waterlogged=false]
setblock 16 70 -423 minecraft:candle[candles=1,lit=true,waterlogged=false]
setblock 18 70 -423 minecraft:candle[candles=1,lit=true,waterlogged=false]
setblock 20 70 -423 minecraft:candle[candles=1,lit=true,waterlogged=false]
setblock 22 70 -423 minecraft:candle[candles=1,lit=true,waterlogged=false]
setblock 24 70 -423 minecraft:candle[candles=1,lit=true,waterlogged=false]
setblock 26 70 -423 minecraft:candle[candles=1,lit=true,waterlogged=false]
setblock 28 70 -423 minecraft:candle[candles=1,lit=true,waterlogged=false]
setblock 30 70 -423 minecraft:candle[candles=1,lit=true,waterlogged=false]
setblock 32 70 -423 minecraft:candle[candles=1,lit=true,waterlogged=false]
setblock 34 70 -423 minecraft:candle[candles=1,lit=true,waterlogged=false]
setblock 24 73 -392 minecraft:waxed_copper_bulb[lit=true,powered=false]
setblock 24 73 -384 minecraft:waxed_copper_bulb[lit=true,powered=false]
setblock 24 73 -376 minecraft:waxed_copper_bulb[lit=true,powered=false]
setblock 24 73 -368 minecraft:waxed_copper_bulb[lit=true,powered=false]
setblock 24 73 -360 minecraft:waxed_copper_bulb[lit=true,powered=false]
setblock 24 73 -352 minecraft:waxed_copper_bulb[lit=true,powered=false]
setblock 24 73 -344 minecraft:waxed_copper_bulb[lit=true,powered=false]
setblock 24 73 -336 minecraft:waxed_copper_bulb[lit=true,powered=false]
setblock 24 73 -328 minecraft:waxed_copper_bulb[lit=true,powered=false]
setblock 1 67 -19 minecraft:light_blue_glazed_terracotta
setblock 1 67 -18 minecraft:light_blue_glazed_terracotta
setblock 2 67 -19 minecraft:light_blue_glazed_terracotta
setblock 3 67 -19 minecraft:light_blue_glazed_terracotta
function palemeridian:npc/echo_1/despawn
function palemeridian:npc/echo_2/despawn
function palemeridian:npc/echo_3/despawn
function palemeridian:npc/echo_4/despawn
function palemeridian:npc/echo_5/despawn
function palemeridian:npc/echo_6/despawn
function palemeridian:npc/echo_7/despawn
function palemeridian:npc/echo_8/despawn
function palemeridian:npc/echo_9/despawn
function palemeridian:npc/echo_10/despawn
function palemeridian:npc/echo_11/despawn
data modify storage palemeridian:npc hesper set value "restored"
function palemeridian:npc/hesper/despawn
setblock 19 70 -317 minecraft:potted_cornflower
setblock 21 70 -317 minecraft:potted_cornflower
setblock 19 70 -315 minecraft:potted_cornflower
setblock 21 70 -315 minecraft:potted_cornflower
time set 23500
title @a times 20 100 30
title @a title {"text":"Every Name","color":"white"}
title @a subtitle {"text":"The Deepcut is on the Chart again.","color":"gray","italic":true}
playsound minecraft:block.bell.resonate master @a ~ ~ ~ 2 0.8
tellraw @a {"text":"The Lens blazes. Light runs down the north cliffs like water finding its level, into the adit, along the rails, to a wall of plaques that are not blank any more. In the last gallery eleven candles take flame, and eleven figures are simply not there.","color":"gray","italic":true}
execute if score #k.all pm.world matches 1 run function palemeridian:c4/ending/spoken
schedule function palemeridian:c4/ending/after 200t replace
