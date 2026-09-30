# Quest c4.chart: The Blank Space — activate (idempotent)
execute unless score c4.chart pm.q matches 0 run return fail
scoreboard players set c4.chart pm.q 1
tellraw @a [{"text":"» ","color":"dark_aqua"},{"text":"The Blank Space","color":"aqua","bold":true},{"text":" — Decide what the Long Chart says about the Deepcut.","color":"gray"}]
playsound minecraft:item.book.page_turn master @a ~ ~ ~ 0.6 1.0
function palemeridian:hud/refresh
