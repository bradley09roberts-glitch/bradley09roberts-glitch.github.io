# npc/hesper/meet_lens: "I have to light it."
execute unless score @s pm.dctx matches 56 run return fail
scoreboard players set @s pm.dctx 0
function palemeridian:q/c4.keeper/complete
