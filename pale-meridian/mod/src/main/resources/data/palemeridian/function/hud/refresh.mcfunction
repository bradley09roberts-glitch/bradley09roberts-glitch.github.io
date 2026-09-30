# Recompute the objective HUD (bossbar + waypoint) from quest state
scoreboard players set #cur pm.world 0
execute if score #cur pm.world matches 0 if score ep.complete pm.q matches 1 run function palemeridian:q/ep.complete/hud
execute if score #cur pm.world matches 0 if score c4.chart pm.q matches 1 run function palemeridian:q/c4.chart/hud
execute if score #cur pm.world matches 0 if score c4.unlooked pm.q matches 1 run function palemeridian:q/c4.unlooked/hud
execute if score #cur pm.world matches 0 if score c4.lens pm.q matches 1 run function palemeridian:q/c4.lens/hud
execute if score #cur pm.world matches 0 if score c4.keeper pm.q matches 1 run function palemeridian:q/c4.keeper/hud
execute if score #cur pm.world matches 0 if score c4.crossing pm.q matches 1 run function palemeridian:q/c4.crossing/hud
execute if score #cur pm.world matches 0 if score c3.surge pm.q matches 1 run function palemeridian:q/c3.surge/hud
execute if score #cur pm.world matches 0 if score c3.lamp pm.q matches 1 run function palemeridian:q/c3.lamp/hud
execute if score #cur pm.world matches 0 if score c3.kiln pm.q matches 1 run function palemeridian:q/c3.kiln/hud
execute if score #cur pm.world matches 0 if score c3.memorial pm.q matches 1 run function palemeridian:q/c3.memorial/hud
execute if score #cur pm.world matches 0 if score c3.remind pm.q matches 1 run function palemeridian:q/c3.remind/hud
execute if score #cur pm.world matches 0 if score c3.tamsin pm.q matches 1 run function palemeridian:q/c3.tamsin/hud
execute if score #cur pm.world matches 0 if score c3.log pm.q matches 1 run function palemeridian:q/c3.log/hud
execute if score #cur pm.world matches 0 if score c3.arrive pm.q matches 1 run function palemeridian:q/c3.arrive/hud
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
