# Quest c1.surge: Hold the Light — activate (idempotent)
execute unless score c1.surge pm.q matches 0 run return fail
scoreboard players set c1.surge pm.q 1
execute unless score c1.surge pm.qp matches 0.. run scoreboard players set c1.surge pm.qp 0
tellraw @a [{"text":"» ","color":"dark_aqua"},{"text":"Hold the Light","color":"aqua","bold":true},{"text":" — Relight the four plaza lamps while the Pall pushes back.","color":"gray"}]
playsound minecraft:item.book.page_turn master @a ~ ~ ~ 0.6 1.0
function palemeridian:hud/refresh
