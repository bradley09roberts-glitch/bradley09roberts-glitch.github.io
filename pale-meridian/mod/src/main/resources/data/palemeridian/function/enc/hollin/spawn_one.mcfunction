execute store result score #pick pm.tmp run random value 0..6
execute if score #pick pm.tmp matches 0 run summon minecraft:creaking 102 67 106 {Tags:["pm.watcher","pm.surge.hollin"],PersistenceRequired:1b}
execute if score #pick pm.tmp matches 1 run summon minecraft:creaking 138 67 106 {Tags:["pm.watcher","pm.surge.hollin"],PersistenceRequired:1b}
execute if score #pick pm.tmp matches 2 run summon minecraft:creaking 102 67 130 {Tags:["pm.watcher","pm.surge.hollin"],PersistenceRequired:1b}
execute if score #pick pm.tmp matches 3 run summon minecraft:creaking 138 67 130 {Tags:["pm.watcher","pm.surge.hollin"],PersistenceRequired:1b}
execute if score #pick pm.tmp matches 4 run summon minecraft:creaking 120 67 134 {Tags:["pm.watcher","pm.surge.hollin"],PersistenceRequired:1b}
execute if score #pick pm.tmp matches 5 run summon minecraft:creaking 110 67 134 {Tags:["pm.watcher","pm.surge.hollin"],PersistenceRequired:1b}
execute if score #pick pm.tmp matches 6 run summon minecraft:creaking 130 67 134 {Tags:["pm.watcher","pm.surge.hollin"],PersistenceRequired:1b}
execute at @e[type=creaking,tag=pm.surge.hollin,limit=1,sort=nearest] run particle minecraft:white_ash ~ ~1 ~ 0.5 1 0.5 0.01 40 normal
