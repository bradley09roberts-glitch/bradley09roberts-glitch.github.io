# npc/brannoc/later: Take apples and honey
execute unless score @s pm.dctx matches 30 run return fail
scoreboard players set @s pm.dctx 0
function palemeridian:c2/gift
