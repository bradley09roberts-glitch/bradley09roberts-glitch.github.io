# Recompute the objective HUD (bossbar + waypoint) from quest state
scoreboard players set #cur pm.world 0
execute if score #cur pm.world matches 0 if score p.road pm.q matches 1 run function palemeridian:q/p.road/hud
execute if score #cur pm.world matches 0 if score p.lamp pm.q matches 1 run function palemeridian:q/p.lamp/hud
execute if score #cur pm.world matches 0 if score p.camp pm.q matches 1 run function palemeridian:q/p.camp/hud
execute if score #cur pm.world matches 0 if score p.letter pm.q matches 1 run function palemeridian:q/p.letter/hud
execute if score #cur pm.world matches 0 run function palemeridian:hud/none
