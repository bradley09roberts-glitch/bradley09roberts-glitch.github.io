# Quest c4.lens: The Great Lens — activate (idempotent)
execute unless score c4.lens pm.q matches 0 run return fail
scoreboard players set c4.lens pm.q 1
tellraw @a [{"text":"» ","color":"dark_aqua"},{"text":"The Great Lens","color":"aqua","bold":true},{"text":" — Set the Lens Heart in the Great Lens atop the tower.","color":"gray"}]
playsound minecraft:item.book.page_turn master @a ~ ~ ~ 0.6 1.0
function palemeridian:hud/refresh
