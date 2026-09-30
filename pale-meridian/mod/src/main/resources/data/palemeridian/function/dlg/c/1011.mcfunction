# npc/odile/restored_4: "Thank you, Odile."
execute unless score @s pm.dctx matches 14 run return fail
scoreboard players set @s pm.dctx 0
scoreboard players set #odile.told pm.world 1
