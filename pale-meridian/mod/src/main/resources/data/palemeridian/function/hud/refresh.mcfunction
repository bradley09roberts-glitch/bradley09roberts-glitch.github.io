# Recompute the objective HUD (bossbar + waypoint) from quest state
scoreboard players set #cur pm.world 0
execute if score #cur pm.world matches 0 if score c2.lamp pm.q matches 1 run function palemeridian:q/c2.lamp/hud
execute if score #cur pm.world matches 0 if score c2.hearts pm.q matches 1 run function palemeridian:q/c2.hearts/hud
execute if score #cur pm.world matches 0 if score c2.memories pm.q matches 1 run function palemeridian:q/c2.memories/hud
execute if score #cur pm.world matches 0 if score c2.brannoc pm.q matches 1 run function palemeridian:q/c2.brannoc/hud
execute if score #cur pm.world matches 0 if score c2.arrive pm.q matches 1 run function palemeridian:q/c2.arrive/hud
execute if score #cur pm.world matches 0 if score c1.surge pm.q matches 1 run function palemeridian:q/c1.surge/hud
execute if score #cur pm.world matches 0 if score c1.lamp pm.q matches 1 run function palemeridian:q/c1.lamp/hud
execute if score #cur pm.world matches 0 if score c1.round pm.q matches 1 run function palemeridian:q/c1.round/hud
execute if score #cur pm.world matches 0 if score c1.names pm.q matches 1 run function palemeridian:q/c1.names/hud
execute if score #cur pm.world matches 0 if score c1.odile pm.q matches 1 run function palemeridian:q/c1.odile/hud
execute if score #cur pm.world matches 0 if score p.road pm.q matches 1 run function palemeridian:q/p.road/hud
execute if score #cur pm.world matches 0 if score p.lamp pm.q matches 1 run function palemeridian:q/p.lamp/hud
execute if score #cur pm.world matches 0 if score p.camp pm.q matches 1 run function palemeridian:q/p.camp/hud
execute if score #cur pm.world matches 0 if score p.letter pm.q matches 1 run function palemeridian:q/p.letter/hud
execute if score #cur pm.world matches 0 run function palemeridian:hud/none
