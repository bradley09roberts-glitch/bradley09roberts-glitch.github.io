# Quest c3.log: The Foreman's Log — activate (idempotent)
execute unless score c3.log pm.q matches 0 run return fail
scoreboard players set c3.log pm.q 1
tellraw @a [{"text":"» ","color":"dark_aqua"},{"text":"The Foreman's Log","color":"aqua","bold":true},{"text":" — Read the last log in the foreman's office.","color":"gray"}]
playsound minecraft:item.book.page_turn master @a ~ ~ ~ 0.6 1.0
function palemeridian:hud/refresh
