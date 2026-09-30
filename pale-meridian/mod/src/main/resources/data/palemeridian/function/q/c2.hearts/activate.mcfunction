# Quest c2.hearts: The Heartwood — activate (idempotent)
execute unless score c2.hearts pm.q matches 0 run return fail
scoreboard players set c2.hearts pm.q 1
execute unless score c2.hearts pm.qp matches 0.. run scoreboard players set c2.hearts pm.qp 0
tellraw @a [{"text":"» ","color":"dark_aqua"},{"text":"The Heartwood","color":"aqua","bold":true},{"text":" — Break the three hearts grown into the Heartwood.","color":"gray"}]
playsound minecraft:item.book.page_turn master @a ~ ~ ~ 0.6 1.0
function palemeridian:hud/refresh
