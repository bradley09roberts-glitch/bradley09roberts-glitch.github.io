# Quest c1.lamp: Hollin's Wakelamp — activate (idempotent)
execute unless score c1.lamp pm.q matches 0 run return fail
scoreboard players set c1.lamp pm.q 1
execute unless score c1.lamp pm.qp matches 0.. run scoreboard players set c1.lamp pm.qp 0
tellraw @a [{"text":"» ","color":"dark_aqua"},{"text":"Hollin's Wakelamp","color":"aqua","bold":true},{"text":" — Rebuild the Wakelamp at the top of the bell tower.","color":"gray"}]
playsound minecraft:item.book.page_turn master @a ~ ~ ~ 0.6 1.0
function palemeridian:hud/refresh
