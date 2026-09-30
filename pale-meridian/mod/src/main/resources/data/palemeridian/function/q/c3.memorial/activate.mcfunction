# Quest c3.memorial: The Last Gallery — activate (idempotent)
execute unless score c3.memorial pm.q matches 0 run return fail
scoreboard players set c3.memorial pm.q 1
tellraw @a [{"text":"» ","color":"dark_aqua"},{"text":"The Last Gallery","color":"aqua","bold":true},{"text":" — Go past the blue seam to where the Deepcut fell.","color":"gray"}]
playsound minecraft:item.book.page_turn master @a ~ ~ ~ 0.6 1.0
function palemeridian:hud/refresh
