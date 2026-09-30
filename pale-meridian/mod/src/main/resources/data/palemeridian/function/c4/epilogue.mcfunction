execute as @a run function palemeridian:c4/keepers_glass
execute as @a run dialog show @s palemeridian:journal/credits
playsound minecraft:ui.toast.challenge_complete master @a ~ ~ ~ 0.8 1
tellraw @a {"text":"(The survey is complete. The Vale is yours: every district keeps its people, its services and its lamps. The world beyond the rim is ordinary Minecraft.)","color":"dark_aqua"}
