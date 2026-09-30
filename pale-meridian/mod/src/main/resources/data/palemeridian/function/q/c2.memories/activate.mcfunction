# Quest c2.memories: Remember Aldercross — activate (idempotent)
execute unless score c2.memories pm.q matches 0 run return fail
scoreboard players set c2.memories pm.q 1
execute unless score c2.memories pm.qp matches 0.. run scoreboard players set c2.memories pm.qp 0
tellraw @a [{"text":"» ","color":"dark_aqua"},{"text":"Remember Aldercross","color":"aqua","bold":true},{"text":" — Find the three places Aldercross remembers.","color":"gray"}]
playsound minecraft:item.book.page_turn master @a ~ ~ ~ 0.6 1.0
function palemeridian:hud/refresh
