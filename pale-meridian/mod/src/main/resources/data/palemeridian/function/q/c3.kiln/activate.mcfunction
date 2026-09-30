# Quest c3.kiln: Kiln Three — activate (idempotent)
execute unless score c3.kiln pm.q matches 0 run return fail
scoreboard players set c3.kiln pm.q 1
tellraw @a [{"text":"» ","color":"dark_aqua"},{"text":"Kiln Three","color":"aqua","bold":true},{"text":" — Fire Kiln Three the way the foreman's log describes.","color":"gray"}]
playsound minecraft:item.book.page_turn master @a ~ ~ ~ 0.6 1.0
function palemeridian:hud/refresh
