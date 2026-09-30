data modify storage palemeridian:tmp s set value {bar:"On",fx:"On",wp:"On",chill:"On",keep:"Off",diff:"Normal"}
execute if score @s pm.optbar matches 0 run data modify storage palemeridian:tmp s.bar set value "Off"
execute if score @s pm.optfx matches 0 run data modify storage palemeridian:tmp s.fx set value "Off"
execute if score @s pm.optwp matches 0 run data modify storage palemeridian:tmp s.wp set value "Off"
execute unless score #set.chill pm.world matches 1 run data modify storage palemeridian:tmp s.chill set value "Off"
execute store result score #t pm.tmp run gamerule minecraft:keep_inventory
execute if score #t pm.tmp matches 1 run data modify storage palemeridian:tmp s.keep set value "On"
execute if score #set.difficulty pm.world matches 0 run data modify storage palemeridian:tmp s.diff set value "Story (fewer Watchers)"
execute if score #set.difficulty pm.world matches 2 run data modify storage palemeridian:tmp s.diff set value "Hard (more Watchers)"
scoreboard players enable @s pm.ui
function palemeridian:ui/_settings_show with storage palemeridian:tmp s
