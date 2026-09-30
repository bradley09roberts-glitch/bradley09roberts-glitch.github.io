# npc/mirelle/restored: Take the bread
execute unless score @s pm.dctx matches 17 run return fail
scoreboard players set @s pm.dctx 0
function palemeridian:c1/mirelle_bread
