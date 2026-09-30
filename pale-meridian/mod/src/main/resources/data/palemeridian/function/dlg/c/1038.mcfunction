# prop/chart/choice: Not yet. (Hear what the others think first.)
execute unless score @s pm.dctx matches 82 run return fail
scoreboard players set @s pm.dctx 0
tellraw @s {"text":"Tamsin, Brannoc, Odile and Hesper are all here in the Chart Room.","color":"dark_aqua"}
