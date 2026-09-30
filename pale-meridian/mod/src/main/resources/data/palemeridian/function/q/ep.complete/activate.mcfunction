# Quest ep.complete: Survey Complete — activate (idempotent)
execute unless score ep.complete pm.q matches 0 run return fail
scoreboard players set ep.complete pm.q 1
tellraw @a [{"text":"» ","color":"dark_aqua"},{"text":"Survey Complete","color":"aqua","bold":true},{"text":" — Free play: the Vale is yours to explore.","color":"gray"}]
playsound minecraft:item.book.page_turn master @a ~ ~ ~ 0.6 1.0
function palemeridian:q/ep.complete/complete
function palemeridian:hud/refresh
