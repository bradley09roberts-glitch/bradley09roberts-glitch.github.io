execute unless score c1.round pm.q matches 1 run return fail
$scoreboard players set #bell pm.tmp $(n)
scoreboard players operation #expect pm.tmp = #round.step pm.world
scoreboard players add #expect pm.tmp 1
execute unless score #bell pm.tmp = #expect pm.tmp run return run function palemeridian:c1/bell_wrong
scoreboard players add #round.step pm.world 1
execute if score #round.step pm.world matches 1 run playsound minecraft:block.note_block.bell master @a ~ ~ ~ 1 0.7
execute if score #round.step pm.world matches 2 run playsound minecraft:block.note_block.bell master @a ~ ~ ~ 1 0.84
execute if score #round.step pm.world matches 3 run playsound minecraft:block.note_block.bell master @a ~ ~ ~ 1 1.0
execute if score #round.step pm.world matches 4 run playsound minecraft:block.note_block.bell master @a ~ ~ ~ 1 1.26
execute if score #round.step pm.world matches 4.. run function palemeridian:c1/round_solved
