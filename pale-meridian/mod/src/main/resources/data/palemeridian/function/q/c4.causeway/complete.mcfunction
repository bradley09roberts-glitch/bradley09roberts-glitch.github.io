# Quest c4.causeway: The Broken Span — complete (idempotent)
execute unless score c4.causeway pm.q matches 1 run return fail
scoreboard players set c4.causeway pm.q 2
advancement grant @a only palemeridian:journal/c4/causeway
function palemeridian:c4/causeway_lit
function palemeridian:q/_advance
function palemeridian:hud/refresh
