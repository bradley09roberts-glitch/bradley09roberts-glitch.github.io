execute if score #c3.truth pm.world matches 1 run return fail
scoreboard players set #c3.truth pm.world 1
playsound minecraft:ambient.cave master @a ~ ~ ~ 1 0.5
playsound minecraft:block.bell.resonate master @a ~ ~ ~ 0.6 0.5
particle minecraft:white_ash ~ ~1 ~ 6 2 6 0.01 400 normal
tellraw @a {"text":"Eleven figures stand in the dark with their lamps out, facing a wall of blank plaques. None of them turns around.","color":"gray","italic":true}
schedule function palemeridian:c3/memorial_2 60t replace
function palemeridian:q/c3.memorial/complete
