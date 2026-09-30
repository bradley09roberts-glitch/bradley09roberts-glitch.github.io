# npc/tamsin/restored: "I'll go."
execute unless score @s pm.dctx matches 34 run return fail
scoreboard players set @s pm.dctx 0
