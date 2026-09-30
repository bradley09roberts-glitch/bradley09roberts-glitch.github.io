# npc/odile/intro_tamsin: "Then help me find her."
execute unless score @s pm.dctx matches 3 run return fail
scoreboard players set @s pm.dctx 0
function palemeridian:dlg/show/npc/odile/intro_help
