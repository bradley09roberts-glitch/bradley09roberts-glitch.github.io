# Quest c2.arrive: The Orchard Road — activate (idempotent)
execute unless score c2.arrive pm.q matches 0 run return fail
scoreboard players set c2.arrive pm.q 1
tellraw @a [{"text":"» ","color":"dark_aqua"},{"text":"The Orchard Road","color":"aqua","bold":true},{"text":" — Travel east to the orchards of Aldercross.","color":"gray"}]
playsound minecraft:item.book.page_turn master @a ~ ~ ~ 0.6 1.0
function palemeridian:hud/refresh
