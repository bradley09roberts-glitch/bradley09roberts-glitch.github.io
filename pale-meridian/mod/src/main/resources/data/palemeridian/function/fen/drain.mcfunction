execute if score #fen.drained pm.world matches 1 run return fail
scoreboard players set #fen.drained pm.world 1
fill -346 58 67 -337 62 73 minecraft:air replace minecraft:water
setblock -336 58 67 minecraft:air destroy
setblock -336 59 67 minecraft:air destroy
setblock -336 60 67 minecraft:air destroy
playsound minecraft:block.water.ambient master @a -341.5 58 70 2 0.6
playsound minecraft:block.iron_door.open master @a -341.5 58 70 1 0.6
tellraw @a {"text":"Somewhere under the chapel, stone grinds on stone. Water rushes out into the fen, and the window into the crypt cracks and falls away.","color":"gray","italic":true}
function palemeridian:q/s.fen/complete
