# npc/brannoc/lamp: Take Col's lamp
execute unless score @s pm.dctx matches 27 run return fail
scoreboard players set @s pm.dctx 0
function palemeridian:c2/give_lamp
