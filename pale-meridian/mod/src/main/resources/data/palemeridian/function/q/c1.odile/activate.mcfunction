# Quest c1.odile: The Lamplighter — activate (idempotent)
execute unless score c1.odile pm.q matches 0 run return fail
scoreboard players set c1.odile pm.q 1
tellraw @a [{"text":"» ","color":"dark_aqua"},{"text":"The Lamplighter","color":"aqua","bold":true},{"text":" — Speak with the lamplighter at Hollin's gate.","color":"gray"}]
playsound minecraft:item.book.page_turn master @a ~ ~ ~ 0.6 1.0
setworldspawn 107 67 150 180 0
function palemeridian:hud/refresh
