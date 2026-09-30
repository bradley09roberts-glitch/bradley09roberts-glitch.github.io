# Dispatch a dialog choice (pm.talk trigger). Choices validate their dialog context.
scoreboard players operation #code pm.tmp = @s pm.talk
scoreboard players set @s pm.talk 0
scoreboard players enable @s pm.talk
execute unless score #code pm.tmp matches 1000..99999 run return fail
execute store result storage palemeridian:tmp code int 1 run scoreboard players get #code pm.tmp
function palemeridian:dlg/_call with storage palemeridian:tmp
