# Quest c3.remind: Something of Hers — activate (idempotent)
execute unless score c3.remind pm.q matches 0 run return fail
scoreboard players set c3.remind pm.q 1
execute unless score c3.remind pm.qp matches 0.. run scoreboard players set c3.remind pm.qp 0
tellraw @a [{"text":"» ","color":"dark_aqua"},{"text":"Something of Hers","color":"aqua","bold":true},{"text":" — Show Tamsin three things that are hers.","color":"gray"}]
playsound minecraft:item.book.page_turn master @a ~ ~ ~ 0.6 1.0
function palemeridian:hud/refresh
