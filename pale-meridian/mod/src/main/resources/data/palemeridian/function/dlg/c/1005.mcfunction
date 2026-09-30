# npc/odile/intro_who: "Maybe I can help you find it."
execute unless score @s pm.dctx matches 4 run return fail
scoreboard players set @s pm.dctx 0
function palemeridian:dlg/show/npc/odile/intro_help
