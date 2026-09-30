data remove block 12 70 -298 Items
setblock 5 70 -298 minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]
setblock 3 70 -298 minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]
setblock 7 70 -298 minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]
setblock 5 70 -300 minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]
setblock 5 70 -296 minecraft:campfire[facing=north,lit=true,signal_fire=false,waterlogged=false]
particle minecraft:flame 5.5 71 -297.5 2 1 2 0.05 200 normal
particle minecraft:lava 5.5 71 -297.5 2 1 2 0.1 40 normal
playsound minecraft:block.blastfurnace.fire_crackle master @a 5.5 70 -297.5 2 0.8
playsound minecraft:block.fire.ambient master @a 5.5 70 -297.5 2 0.6
tellraw @a {"text":"Kiln Three roars. Heat pours from the grate, and deep in the fire something rings like a struck glass.","color":"gray","italic":true}
function palemeridian:c3/hatch_refill
tellraw @a {"text":"(The kiln hatch holds a new Stillglass Lamp, and a Lens Heart.)","color":"dark_aqua"}
function palemeridian:q/c3.kiln/complete
