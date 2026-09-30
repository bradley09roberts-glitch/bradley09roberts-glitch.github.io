scoreboard players set @s pm.leave 0
scoreboard players enable @s pm.talk
scoreboard players enable @s pm.ui
scoreboard players set @s pm.dctx 0
function palemeridian:journal/sync
execute unless score @s pm.seen = #rev pm.world run function palemeridian:ui/recap
scoreboard players operation @s pm.seen = #rev pm.world
function palemeridian:hud/refresh
execute if score #ending pm.world matches 1.. run function palemeridian:c4/keepers_glass
