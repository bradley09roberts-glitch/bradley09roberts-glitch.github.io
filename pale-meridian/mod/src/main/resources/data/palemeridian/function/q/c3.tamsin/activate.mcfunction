# Quest c3.tamsin: Into the Deepcut — activate (idempotent)
execute unless score c3.tamsin pm.q matches 0 run return fail
scoreboard players set c3.tamsin pm.q 1
tellraw @a [{"text":"» ","color":"dark_aqua"},{"text":"Into the Deepcut","color":"aqua","bold":true},{"text":" — Find Tamsin inside the Deepcut.","color":"gray"}]
playsound minecraft:item.book.page_turn master @a ~ ~ ~ 0.6 1.0
function palemeridian:hud/refresh
