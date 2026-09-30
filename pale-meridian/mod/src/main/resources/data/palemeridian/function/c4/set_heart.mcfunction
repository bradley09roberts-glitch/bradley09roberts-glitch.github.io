execute unless items entity @s container.* minecraft:heart_of_the_sea[minecraft:custom_data~{pm:{item:"lens_heart"}}] unless items entity @s weapon.offhand minecraft:heart_of_the_sea[minecraft:custom_data~{pm:{item:"lens_heart"}}] run return run tellraw @s {"text":"You need the Lens Heart forged in Kiln Three. (If it was lost, the kiln hatch keeps another.)","color":"gray"}
clear @s minecraft:heart_of_the_sea[minecraft:custom_data~{pm:{item:"lens_heart"}}] 1
particle minecraft:end_rod -16 101 -14 0.2 3 3 0.02 120 normal
playsound minecraft:block.respawn_anchor.charge master @a -16 101 -14 2 0.6
tellraw @a {"text":"The Lens Heart settles into the cradle with a sound like a held breath. Then the fog on the gallery begins to move against the wind.","color":"gray","italic":true}
tellraw @a {"text":"Far below, boats are putting out from Hollin.","color":"gray","italic":true}
function palemeridian:q/c4.lens/complete
