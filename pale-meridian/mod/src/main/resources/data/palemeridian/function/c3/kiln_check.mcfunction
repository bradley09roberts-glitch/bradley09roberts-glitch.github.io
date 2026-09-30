execute store result score #coal pm.tmp if items block 12 70 -298 container.* #minecraft:coals
execute if score #coal pm.tmp matches ..7 run return fail
execute if block 15 71 -304 minecraft:lever[powered=false] if block 15 71 -303 minecraft:lever[powered=true] if block 15 71 -302 minecraft:lever[powered=false] run return run function palemeridian:c3/kiln_fire
execute if score #c3.smoke pm.world >= #seconds pm.world run return fail
scoreboard players operation #c3.smoke pm.world = #seconds pm.world
scoreboard players add #c3.smoke pm.world 12
particle minecraft:large_smoke 5.5 72 -297.5 2 1 2 0.02 60 normal
playsound minecraft:block.fire.extinguish master @a 5.5 70 -297.5 1 0.6
tellraw @a[x=5,y=70,z=-297,distance=..24] {"text":"The firebox chokes and smokes. Something about the kiln's settings is wrong.","color":"gray","italic":true}
