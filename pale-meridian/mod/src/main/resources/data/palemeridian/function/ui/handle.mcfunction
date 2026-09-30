scoreboard players operation #ui pm.tmp = @s pm.ui
scoreboard players set @s pm.ui 0
scoreboard players enable @s pm.ui
scoreboard players enable @s pm.talk
execute if score #ui pm.tmp matches 1 run return run function palemeridian:journal/current
execute if score #ui pm.tmp matches 2 run return run function palemeridian:ui/recap
execute if score #ui pm.tmp matches 3 run return run function palemeridian:ui/people
execute if score #ui pm.tmp matches 4 run return run function palemeridian:ui/eleven
execute if score #ui pm.tmp matches 5 run return run dialog show @s palemeridian:journal/help
execute if score #ui pm.tmp matches 6 run return run function palemeridian:ui/settings
execute if score #ui pm.tmp matches 7 run return run function palemeridian:ui/replace_book
execute if score #ui pm.tmp matches 9 run return run dialog show @s palemeridian:journal/menu
execute if score #ui pm.tmp matches 20 run function palemeridian:ui/toggle_bar
execute if score #ui pm.tmp matches 21 run function palemeridian:ui/toggle_fx
execute if score #ui pm.tmp matches 22 run function palemeridian:ui/toggle_wp
execute if score #ui pm.tmp matches 23 run function palemeridian:ui/toggle_chill
execute if score #ui pm.tmp matches 24 run function palemeridian:ui/toggle_keepinv
execute if score #ui pm.tmp matches 25 run function palemeridian:ui/cycle_difficulty
execute if score #ui pm.tmp matches 20..29 run function palemeridian:ui/settings
execute if score #ui pm.tmp matches 100..199 run function palemeridian:ui/people_dispatch
