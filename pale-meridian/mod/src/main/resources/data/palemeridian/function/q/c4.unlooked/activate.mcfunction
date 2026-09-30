# Quest c4.unlooked: The Unlooked — activate (idempotent)
execute unless score c4.unlooked pm.q matches 0 run return fail
scoreboard players set c4.unlooked pm.q 1
tellraw @a [{"text":"» ","color":"dark_aqua"},{"text":"The Unlooked","color":"aqua","bold":true},{"text":" — Face what the Pall has become.","color":"gray"}]
playsound minecraft:item.book.page_turn master @a ~ ~ ~ 0.6 1.0
function palemeridian:hud/refresh
