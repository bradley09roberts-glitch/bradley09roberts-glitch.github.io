# Quest c1.names: Hollin, Unremembered — activate (idempotent)
execute unless score c1.names pm.q matches 0 run return fail
scoreboard players set c1.names pm.q 1
execute unless score c1.names pm.qp matches 0.. run scoreboard players set c1.names pm.qp 0
tellraw @a [{"text":"» ","color":"dark_aqua"},{"text":"Hollin, Unremembered","color":"aqua","bold":true},{"text":" — Find the names of Hollin's four lost places.","color":"gray"}]
playsound minecraft:item.book.page_turn master @a ~ ~ ~ 0.6 1.0
function palemeridian:hud/refresh
