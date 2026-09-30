# npc/odile/intro_what: "What would it take to wake it up?"
execute unless score @s pm.dctx matches 5 run return fail
scoreboard players set @s pm.dctx 0
function palemeridian:dlg/show/npc/odile/intro_help
