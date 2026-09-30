tp @e[type=creaking,tag=pm.boss,limit=1] ~ ~ ~ facing entity @s
particle minecraft:white_ash ~ ~2 ~ 1 2 1 0.02 120 normal
playsound minecraft:entity.creaking.freeze master @a ~ ~ ~ 1.5 0.5
effect give @a[x=-26,y=95,z=-24,dx=22,dy=12,dz=22,scores={pm.optfx=1}] minecraft:darkness 3 0 true
title @a[x=-26,y=95,z=-24,dx=22,dy=12,dz=22,scores={pm.optfx=0}] actionbar {"text":"The Unlooked steps through the fog behind someone!","color":"red"}
