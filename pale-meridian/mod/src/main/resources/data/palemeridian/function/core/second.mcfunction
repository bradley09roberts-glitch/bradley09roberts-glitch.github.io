# Once per second (self-scheduling). All checks are gated by state and proximity.
schedule function palemeridian:core/second 20t replace
scoreboard players add #seconds pm.world 1
function palemeridian:player/chill_all
function palemeridian:npc/_maintain_all
execute if score p.chill pm.q matches 1 as @a[gamemode=!spectator,predicate=palemeridian:in_pall,predicate=palemeridian:holding_light] run function palemeridian:q/p.chill/complete
execute if score p.camp pm.q matches 1 as @a[predicate=palemeridian:area/landing_camp] at @s run function palemeridian:loc/h0
execute as @a[predicate=palemeridian:area/landing_camp] at @s run function palemeridian:loc/h1
execute if score #figure pm.world matches 1 as @a[predicate=palemeridian:area/landing_figure_near] at @s run function palemeridian:loc/h2
execute if score p.road pm.q matches 1 as @a[predicate=palemeridian:area/hollin_gate] at @s run function palemeridian:loc/h3
execute if score p.lamp pm.q matches 1 positioned 5 86 346 if entity @a[distance=..18] run function palemeridian:bp/landing_lamp/check
