# Quest p.letter: A Letter at the Landing — activate (idempotent)
execute unless score p.letter pm.q matches 0 run return fail
scoreboard players set p.letter pm.q 1
tellraw @a [{"text":"» ","color":"dark_aqua"},{"text":"A Letter at the Landing","color":"aqua","bold":true},{"text":" — Read the letter pinned to the noticeboard.","color":"gray"}]
playsound minecraft:item.book.page_turn master @a ~ ~ ~ 0.6 1.0
function palemeridian:hud/refresh
