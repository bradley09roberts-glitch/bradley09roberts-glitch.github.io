# Quest c3.arrive: Under the Cliffs — activate (idempotent)
execute unless score c3.arrive pm.q matches 0 run return fail
scoreboard players set c3.arrive pm.q 1
tellraw @a [{"text":"» ","color":"dark_aqua"},{"text":"Under the Cliffs","color":"aqua","bold":true},{"text":" — Follow Tamsin north to the Glassworks.","color":"gray"}]
playsound minecraft:item.book.page_turn master @a ~ ~ ~ 0.6 1.0
function palemeridian:hud/refresh
