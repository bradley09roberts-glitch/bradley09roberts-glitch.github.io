# Quest c2.brannoc: The Orchard Keeper — activate (idempotent)
execute unless score c2.brannoc pm.q matches 0 run return fail
scoreboard players set c2.brannoc pm.q 1
tellraw @a [{"text":"» ","color":"dark_aqua"},{"text":"The Orchard Keeper","color":"aqua","bold":true},{"text":" — Speak with the orchard keeper.","color":"gray"}]
playsound minecraft:item.book.page_turn master @a ~ ~ ~ 0.6 1.0
function palemeridian:hud/refresh
