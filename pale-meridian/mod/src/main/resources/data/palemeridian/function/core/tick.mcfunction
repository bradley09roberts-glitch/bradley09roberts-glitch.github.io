# Every tick: only cheap selector checks.
execute unless score #init pm.world matches 1 run function palemeridian:core/world_init
execute as @a[scores={pm.talk=1..}] at @s run function palemeridian:dlg/handle
execute as @a[scores={pm.ui=1..}] at @s run function palemeridian:ui/handle
execute as @a unless score @s pm.joined matches 1.. at @s run function palemeridian:player/first_join
execute as @a[scores={pm.leave=1..}] at @s run function palemeridian:player/rejoin
execute as @a[scores={pm.death=1..}] run function palemeridian:player/died
