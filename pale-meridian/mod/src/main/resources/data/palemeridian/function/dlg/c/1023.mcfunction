# prop/foremans_log: Note the kiln procedure
execute unless score @s pm.dctx matches 31 run return fail
scoreboard players set @s pm.dctx 0
tellraw @s {"text":"Noted: Kiln Three — bellows UP, flue DOWN, damper UP, then eight coal or charcoal in the firebox.","color":"dark_aqua"}
function palemeridian:q/c3.log/complete
