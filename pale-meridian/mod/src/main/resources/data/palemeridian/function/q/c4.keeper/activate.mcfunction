# Quest c4.keeper: The Keeper — activate (idempotent)
execute unless score c4.keeper pm.q matches 0 run return fail
scoreboard players set c4.keeper pm.q 1
tellraw @a [{"text":"» ","color":"dark_aqua"},{"text":"The Keeper","color":"aqua","bold":true},{"text":" — Find the Keeper in the Chart Room.","color":"gray"}]
playsound minecraft:item.book.page_turn master @a ~ ~ ~ 0.6 1.0
function palemeridian:hud/refresh
