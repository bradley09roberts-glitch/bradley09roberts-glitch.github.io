# Quest c4.crossing: The Crossing — activate (idempotent)
execute unless score c4.crossing pm.q matches 0 run return fail
scoreboard players set c4.crossing pm.q 1
tellraw @a [{"text":"» ","color":"dark_aqua"},{"text":"The Crossing","color":"aqua","bold":true},{"text":" — Cross Vellmere to the Meridian.","color":"gray"}]
playsound minecraft:item.book.page_turn master @a ~ ~ ~ 0.6 1.0
tellraw @a {"text":"Out on Vellmere the fog thins into a corridor, and for the first time you can see the white tower on the island.","color":"gray","italic":true}
function palemeridian:hud/refresh
