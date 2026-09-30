# prop/noticeboard/letter: Take the letter and go
execute unless score @s pm.dctx matches 1 run return fail
scoreboard players set @s pm.dctx 0
function palemeridian:items/letter
function palemeridian:q/p.letter/complete
