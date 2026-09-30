# Quest c1.round: The Lamplighters' Round — activate (idempotent)
execute unless score c1.round pm.q matches 0 run return fail
scoreboard players set c1.round pm.q 1
tellraw @a [{"text":"» ","color":"dark_aqua"},{"text":"The Lamplighters' Round","color":"aqua","bold":true},{"text":" — Ring Hollin's four bells in the order of the Round.","color":"gray"}]
playsound minecraft:item.book.page_turn master @a ~ ~ ~ 0.6 1.0
function palemeridian:hud/refresh
