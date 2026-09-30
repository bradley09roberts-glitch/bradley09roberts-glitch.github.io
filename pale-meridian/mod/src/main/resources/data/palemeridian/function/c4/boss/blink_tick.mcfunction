scoreboard players remove #b.blink pm.world 1
execute if score #b.blink pm.world matches 1.. run return 0
scoreboard players set #b.blink pm.world 15
execute as @r[x=-26,y=95,z=-24,dx=22,dy=12,dz=22,gamemode=!spectator] at @s rotated ~ 0 positioned ^ ^ ^-4 if predicate palemeridian:arena_floor if block ~ ~ ~ #palemeridian:passable if block ~ ~1 ~ #palemeridian:passable run function palemeridian:c4/boss/blink
