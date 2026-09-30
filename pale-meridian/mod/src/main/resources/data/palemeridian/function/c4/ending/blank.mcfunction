execute unless score c4.chart pm.q matches 1 run return fail
execute if score #ending pm.world matches 1.. run return fail
scoreboard players set #ending pm.world 2
tellraw @a [{"selector":"@s","color":"white"},{"text":" sets down the pen. The white space stays white.","color":"gray","italic":true}]
scoreboard players set #r.mere pm.world 1
scoreboard players set #req.mere pm.world 1
scoreboard players set #r.fen pm.world 1
scoreboard players set #req.fen pm.world 1
scoreboard players set #r.wilds pm.world 1
scoreboard players set #req.wilds pm.world 1
time of palemeridian:pall set 6000
function palemeridian:npc/hesper/despawn
title @a times 20 100 30
title @a title {"text":"The Kept Silence","color":"white"}
title @a subtitle {"text":"One place in Vell is allowed to be forgotten.","color":"gray","italic":true}
playsound minecraft:block.bell.resonate master @a ~ ~ ~ 2 0.6
tellraw @a {"text":"The Lens burns over the lake, the orchards, the kilns and the fen, and stops at the north cliffs as if at a closed door. Beyond it the Deepcut keeps its fog, and its eleven, and now its Keeper.","color":"gray","italic":true}
execute if score #k.all pm.world matches 1 run function palemeridian:c4/ending/kept
schedule function palemeridian:c4/ending/after 200t replace
