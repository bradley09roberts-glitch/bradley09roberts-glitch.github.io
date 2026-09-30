# Quest p.road: Into the Pall — activate (idempotent)
execute unless score p.road pm.q matches 0 run return fail
scoreboard players set p.road pm.q 1
tellraw @a [{"text":"» ","color":"dark_aqua"},{"text":"Into the Pall","color":"aqua","bold":true},{"text":" — Follow the lamp road north to Hollin.","color":"gray"}]
playsound minecraft:item.book.page_turn master @a ~ ~ ~ 0.6 1.0
function palemeridian:prologue/figure_appear
function palemeridian:hud/refresh
