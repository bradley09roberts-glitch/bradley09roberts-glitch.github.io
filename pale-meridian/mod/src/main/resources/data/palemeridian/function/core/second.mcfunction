# Once per second (self-scheduling). All checks are gated by state and proximity.
schedule function palemeridian:core/second 20t replace
scoreboard players add #seconds pm.world 1
function palemeridian:player/chill_all
function palemeridian:npc/_maintain_all
execute if score p.chill pm.q matches 1 as @a[gamemode=!spectator,predicate=palemeridian:in_pall,predicate=palemeridian:holding_light] run function palemeridian:q/p.chill/complete
execute if score c1.surge pm.q matches 1 unless score #enc.hollin pm.world matches 1..2 unless score #cool.hollin pm.world > #seconds pm.world if entity @a[x=120,y=67,z=118,distance=..30,gamemode=!spectator] run function palemeridian:enc/hollin/start
function palemeridian:enc/hollin/tick
execute unless score #enc.hollin pm.world matches 1 run kill @e[type=creaking,tag=pm.surge.hollin]
execute if score c2.hearts pm.q matches 1 positioned 358 89 -14 if entity @a[distance=..40] run function palemeridian:c2/hearts_check
execute if score c3.kiln pm.q matches 1 positioned 5.5 70 -297.5 if entity @a[distance=..20] run function palemeridian:c3/kiln_check
execute if score c3.surge pm.q matches 1 unless score #enc.glassworks pm.world matches 1..2 unless score #cool.glassworks pm.world > #seconds pm.world if entity @a[x=24,y=70,z=-295,distance=..22,gamemode=!spectator] run function palemeridian:enc/glassworks/start
function palemeridian:enc/glassworks/tick
execute unless score #enc.glassworks pm.world matches 1 run kill @e[type=creaking,tag=pm.surge.glassworks]
execute unless score c4.crossing pm.q matches 1.. as @a[x=-54,y=0,z=-54,dx=88,dy=320,dz=88,gamemode=!creative,gamemode=!spectator] at @s run function palemeridian:c4/fold_back
execute if score c4.unlooked pm.q matches 1 unless score #boss pm.world matches 1..2 unless score #cool.boss pm.world > #seconds pm.world if entity @a[x=-26,y=95,z=-24,dx=22,dy=12,dz=22,gamemode=!spectator] run function palemeridian:c4/boss/start
function palemeridian:c4/boss/tick
execute unless score #boss pm.world matches 1 run kill @e[type=creaking,tag=pm.bossfx]
execute if score s.fen pm.q matches 1 positioned -341 65 66 if entity @a[distance=..16] run function palemeridian:fen/check
execute if score p.camp pm.q matches 1 as @a[predicate=palemeridian:area/landing_camp] at @s run function palemeridian:loc/h0
execute as @a[predicate=palemeridian:area/landing_camp] at @s run function palemeridian:loc/h1
execute if score #figure pm.world matches 1 as @a[predicate=palemeridian:area/landing_figure_near] at @s run function palemeridian:loc/h2
execute if score p.road pm.q matches 1 as @a[predicate=palemeridian:area/hollin_gate] at @s run function palemeridian:loc/h3
execute if score c1.names pm.q matches 1 as @a[predicate=palemeridian:area/hollin_bakery] at @s run function palemeridian:loc/h4
execute if score c1.names pm.q matches 1 as @a[predicate=palemeridian:area/hollin_well] at @s run function palemeridian:loc/h5
execute if score c1.names pm.q matches 1 as @a[predicate=palemeridian:area/hollin_hall] at @s run function palemeridian:loc/h6
execute if score c1.names pm.q matches 1 as @a[predicate=palemeridian:area/hollin_ferry] at @s run function palemeridian:loc/h7
execute if score c1.lamp pm.q matches 1 as @a[predicate=palemeridian:area/hollin_tower_top] at @s run function palemeridian:loc/h8
execute if score c1.round pm.q matches 1 as @a[predicate=palemeridian:area/hollin_belfry] at @s run function palemeridian:loc/h9
execute if score c1.round pm.q matches 1 as @a[predicate=palemeridian:area/hollin_belfry] at @s run function palemeridian:loc/h10
execute if score c1.round pm.q matches 1 as @a[predicate=palemeridian:area/hollin_belfry] at @s run function palemeridian:loc/h11
execute if score c1.round pm.q matches 1 as @a[predicate=palemeridian:area/hollin_belfry] at @s run function palemeridian:loc/h12
execute if score c2.arrive pm.q matches 1 as @a[predicate=palemeridian:area/alder_gate] at @s run function palemeridian:loc/h13
execute if score c2.memories pm.q matches 1 as @a[predicate=palemeridian:area/alder_apiary] at @s run function palemeridian:loc/h14
execute if score c2.memories pm.q matches 1 as @a[predicate=palemeridian:area/alder_press] at @s run function palemeridian:loc/h15
execute if score c2.memories pm.q matches 1 as @a[predicate=palemeridian:area/alder_tree] at @s run function palemeridian:loc/h16
execute if score #c2.mem.press pm.world matches 1 unless score #c2.note pm.world matches 1 as @a[predicate=palemeridian:area/alder_press] at @s run function palemeridian:loc/h17
execute if score c2.lamp pm.q matches 1 as @a[predicate=palemeridian:area/alder_apiary] at @s run function palemeridian:loc/h18
execute if score c3.arrive pm.q matches 1 as @a[predicate=palemeridian:area/glass_gate] at @s run function palemeridian:loc/h19
execute unless score #deep.first pm.world matches 1 as @a[predicate=palemeridian:area/deep_inside] at @s run function palemeridian:loc/h20
execute unless score #deep.seam pm.world matches 1 as @a[predicate=palemeridian:area/deep_seam] at @s run function palemeridian:loc/h21
execute if score c3.tamsin pm.q matches 1 as @a[predicate=palemeridian:area/deep_gallery] at @s run function palemeridian:loc/h22
execute unless score #c3.show.theodolite pm.world matches 1 as @a[predicate=palemeridian:area/glass_office] at @s run function palemeridian:loc/h23
execute if score c3.memorial pm.q matches 1 as @a[predicate=palemeridian:area/deep_memorial] at @s run function palemeridian:loc/h24
execute if score c3.kiln pm.q matches 2 as @a[predicate=palemeridian:area/glass_kiln] at @s run function palemeridian:loc/h25
execute if score c3.kiln pm.q matches 1 as @a[predicate=palemeridian:area/glass_kiln] at @s run function palemeridian:loc/h26
execute if score c3.kiln pm.q matches 1 as @a[predicate=palemeridian:area/glass_kiln] at @s run function palemeridian:loc/h27
execute if score c3.kiln pm.q matches 1 as @a[predicate=palemeridian:area/glass_kiln] at @s run function palemeridian:loc/h28
execute if score c3.lamp pm.q matches 1 as @a[predicate=palemeridian:area/glass_workshop] at @s run function palemeridian:loc/h29
execute if score c4.crossing pm.q matches 1 as @a[predicate=palemeridian:area/mer_island] at @s run function palemeridian:loc/h30
execute unless score s.fen pm.q matches 1.. as @a[predicate=palemeridian:area/fen_chapel] at @s run function palemeridian:loc/h31
execute if score s.fen pm.q matches 1 as @a[predicate=palemeridian:area/fen_chapel] at @s run function palemeridian:loc/h32
execute if score s.fen pm.q matches 1 as @a[predicate=palemeridian:area/fen_chapel] at @s run function palemeridian:loc/h33
execute if score s.fen pm.q matches 1 as @a[predicate=palemeridian:area/fen_chapel] at @s run function palemeridian:loc/h34
execute if score p.lamp pm.q matches 1 positioned 5 86 346 if entity @a[distance=..18] run function palemeridian:bp/landing_lamp/check
execute if score c1.lamp pm.q matches 1 positioned 120 88 96 if entity @a[distance=..12] run function palemeridian:bp/hollin_lamp/check
execute if score c2.lamp pm.q matches 1 positioned 356 93 -46 if entity @a[distance=..12] run function palemeridian:bp/orchard_lamp/check
execute if score c3.lamp pm.q matches 1 positioned 0 99 -308 if entity @a[distance=..12] run function palemeridian:bp/glass_lamp/check
execute if score c4.causeway pm.q matches 1 positioned -10 66 54.5 if entity @a[distance=..24] run function palemeridian:bp/causeway/check
