# Quest p.lamp: The Landing Lamp — activate (idempotent)
execute unless score p.lamp pm.q matches 0 run return fail
scoreboard players set p.lamp pm.q 1
execute unless score p.lamp pm.qp matches 0.. run scoreboard players set p.lamp pm.qp 0
tellraw @a [{"text":"» ","color":"dark_aqua"},{"text":"The Landing Lamp","color":"aqua","bold":true},{"text":" — Rebuild the broken lamp beside the road.","color":"gray"}]
playsound minecraft:item.book.page_turn master @a ~ ~ ~ 0.6 1.0
function palemeridian:hud/refresh
