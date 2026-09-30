execute if score #k.9 pm.world matches 1 run return fail
scoreboard players set #k.9 pm.world 1
scoreboard players add #k.count pm.world 1
function palemeridian:q/s.eleven/activate
scoreboard players operation s.eleven pm.qp = #k.count pm.world
playsound minecraft:block.amethyst_block.resonate master @a ~ ~ ~ 0.8 0.5
particle minecraft:white_ash ~ ~1 ~ 0.6 0.8 0.6 0.01 60 normal
tellraw @a [{"text":"A name surfaces: ","color":"gray","italic":true},{"text":"Hob Tulley","color":"white","bold":true},{"text":"  (bone dice)","color":"dark_gray"}]
tellraw @a {"text":"Hob Tulley's dice were loaded. Everyone knew. Nobody minded.","color":"gray","italic":true}
execute if score #k.count pm.world matches 11.. run function palemeridian:q/s.eleven/complete
