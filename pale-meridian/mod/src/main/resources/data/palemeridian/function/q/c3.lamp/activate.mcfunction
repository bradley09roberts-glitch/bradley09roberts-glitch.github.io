# Quest c3.lamp: The Glassworks Wakelamp — activate (idempotent)
execute unless score c3.lamp pm.q matches 0 run return fail
scoreboard players set c3.lamp pm.q 1
execute unless score c3.lamp pm.qp matches 0.. run scoreboard players set c3.lamp pm.qp 0
tellraw @a [{"text":"» ","color":"dark_aqua"},{"text":"The Glassworks Wakelamp","color":"aqua","bold":true},{"text":" — Rebuild the Wakelamp on top of the kiln tower.","color":"gray"}]
playsound minecraft:item.book.page_turn master @a ~ ~ ~ 0.6 1.0
function palemeridian:hud/refresh
