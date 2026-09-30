kill @e[type=block_display,tag=pm.ghost.glass_lamp]
particle minecraft:end_rod 0 99.5 -308 0.5 0.5 0.5 0.03 80 normal
playsound minecraft:block.beacon.activate master @a 0 99 -308 2 0.9
tellraw @a {"text":"The lamp kindles high above the kiln yard, and down at the mine mouth the fog stirs like something waking.","color":"gray","italic":true}
function palemeridian:q/c3.lamp/complete
