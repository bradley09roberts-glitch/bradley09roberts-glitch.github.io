# npc/hesper/meet: "I found the Last Gallery."
execute unless score @s pm.dctx matches 52 run return fail
scoreboard players set @s pm.dctx 0
function palemeridian:dlg/show/npc/hesper/meet_tobin
