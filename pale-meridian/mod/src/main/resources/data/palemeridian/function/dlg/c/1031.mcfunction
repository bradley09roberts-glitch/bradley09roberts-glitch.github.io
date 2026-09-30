# npc/hesper/meet_why: "Why break it?"
execute unless score @s pm.dctx matches 53 run return fail
scoreboard players set @s pm.dctx 0
function palemeridian:dlg/show/npc/hesper/meet_tobin
