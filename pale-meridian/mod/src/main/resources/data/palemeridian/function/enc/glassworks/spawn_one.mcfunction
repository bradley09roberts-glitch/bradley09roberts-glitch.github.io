execute store result score #pick pm.tmp run random value 0..6
execute if score #pick pm.tmp matches 0 run summon minecraft:creaking 22 70 -318 {Tags:["pm.watcher","pm.surge.glassworks"],PersistenceRequired:1b}
execute if score #pick pm.tmp matches 1 run summon minecraft:creaking 24 70 -317 {Tags:["pm.watcher","pm.surge.glassworks"],PersistenceRequired:1b}
execute if score #pick pm.tmp matches 2 run summon minecraft:creaking 26 70 -318 {Tags:["pm.watcher","pm.surge.glassworks"],PersistenceRequired:1b}
execute if score #pick pm.tmp matches 3 run summon minecraft:creaking 10 70 -284 {Tags:["pm.watcher","pm.surge.glassworks"],PersistenceRequired:1b}
execute if score #pick pm.tmp matches 4 run summon minecraft:creaking 38 70 -284 {Tags:["pm.watcher","pm.surge.glassworks"],PersistenceRequired:1b}
execute if score #pick pm.tmp matches 5 run summon minecraft:creaking 8 70 -300 {Tags:["pm.watcher","pm.surge.glassworks"],PersistenceRequired:1b}
execute if score #pick pm.tmp matches 6 run summon minecraft:creaking 40 70 -300 {Tags:["pm.watcher","pm.surge.glassworks"],PersistenceRequired:1b}
execute at @e[type=creaking,tag=pm.surge.glassworks,limit=1,sort=nearest] run particle minecraft:white_ash ~ ~1 ~ 0.5 1 0.5 0.01 40 normal
