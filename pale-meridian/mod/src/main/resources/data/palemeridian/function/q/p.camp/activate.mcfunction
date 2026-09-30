# Quest p.camp: Tamsin's Camp — activate (idempotent)
execute unless score p.camp pm.q matches 0 run return fail
scoreboard players set p.camp pm.q 1
tellraw @a [{"text":"» ","color":"dark_aqua"},{"text":"Tamsin's Camp","color":"aqua","bold":true},{"text":" — Find Tamsin's camp up the road.","color":"gray"}]
playsound minecraft:item.book.page_turn master @a ~ ~ ~ 0.6 1.0
function palemeridian:hud/refresh
