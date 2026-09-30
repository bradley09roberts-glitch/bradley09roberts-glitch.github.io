kill @e[type=block_display,tag=pm.ghost.hollin_lamp]
particle minecraft:end_rod 120 88.5 96 0.5 0.5 0.5 0.03 80 normal
playsound minecraft:block.beacon.activate master @a 120 88 96 2 1.0
tellraw @a {"text":"The Stillglass Lamp kindles inside its copper cage — and the whole fog seems to flinch.","color":"gray","italic":true}
function palemeridian:q/c1.lamp/complete
