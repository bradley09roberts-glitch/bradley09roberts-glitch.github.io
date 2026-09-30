# Quest c3.surge: The Collapse — activate (idempotent)
execute unless score c3.surge pm.q matches 0 run return fail
scoreboard players set c3.surge pm.q 1
execute unless score c3.surge pm.qp matches 0.. run scoreboard players set c3.surge pm.qp 0
tellraw @a [{"text":"» ","color":"dark_aqua"},{"text":"The Collapse","color":"aqua","bold":true},{"text":" — Relight the four candles in the kiln yard before the Pall closes in.","color":"gray"}]
playsound minecraft:item.book.page_turn master @a ~ ~ ~ 0.6 1.0
function palemeridian:hud/refresh
