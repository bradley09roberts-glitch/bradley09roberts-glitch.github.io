scoreboard players add #b.hp pm.world 3
execute if score #b.hp pm.world > #b.max pm.world run scoreboard players operation #b.hp pm.world = #b.max pm.world
execute store result entity @e[type=creaking,tag=pm.boss,limit=1] Health float 1 run scoreboard players get #b.hp pm.world
execute at @e[type=creaking,tag=pm.boss,limit=1] run particle minecraft:white_ash ~ ~3 ~ 1 2 1 0.02 40 normal
title @a[x=-26,y=95,z=-24,dx=22,dy=12,dz=22] actionbar {"text":"The relay lamps are dark: the Unlooked is healing. Relight them!","color":"gold"}
