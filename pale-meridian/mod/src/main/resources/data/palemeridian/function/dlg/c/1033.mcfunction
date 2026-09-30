# npc/hesper/meet_tobin: "Tell me about the Lens."
execute unless score @s pm.dctx matches 55 run return fail
scoreboard players set @s pm.dctx 0
function palemeridian:dlg/show/npc/hesper/meet_lens
