scoreboard players set @s pm.joined 1
scoreboard players set @s pm.optbar 1
scoreboard players set @s pm.optfx 1
scoreboard players set @s pm.optwp 1
scoreboard players set @s pm.leave 0
scoreboard players set @s pm.death 0
scoreboard players enable @s pm.talk
scoreboard players enable @s pm.ui
function palemeridian:items/give_kit
function palemeridian:journal/sync
execute if score #chapter pm.world matches 0 unless score p.letter pm.q matches 2 run function palemeridian:player/intro
execute unless score #chapter pm.world matches 0 run function palemeridian:player/late_join
scoreboard players operation @s pm.seen = #rev pm.world
function palemeridian:hud/refresh
