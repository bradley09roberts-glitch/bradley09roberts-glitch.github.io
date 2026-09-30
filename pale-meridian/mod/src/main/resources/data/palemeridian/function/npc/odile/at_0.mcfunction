# Odile at meridian.odile
execute positioned 8.5 68 -13.5 unless entity @a[distance=..72] run return fail
execute unless entity fccb238b-18e4-3959-9019-36c7fc30e60d run function palemeridian:npc/odile/new_0
tp fccb238b-18e4-3959-9019-36c7fc30e60d 8.5 68 -13.5 90.0 0
execute if data storage palemeridian:npc {odile:"faded"} as fccb238b-18e4-3959-9019-36c7fc30e60d unless entity @s[tag=pm.skin.faded] run function palemeridian:npc/odile/apply_skin
execute if data storage palemeridian:npc {odile:"restored"} as fccb238b-18e4-3959-9019-36c7fc30e60d unless entity @s[tag=pm.skin.restored] run function palemeridian:npc/odile/apply_skin
execute unless entity 7f43cb8f-46e5-330b-8826-43e191f2fd4f run summon minecraft:interaction 8.5 68 -13.5 {UUID:[I;2135149455,1189425931,-2010758175,-1846346417],width:0.9f,height:1.95f,response:1b,Tags:["pm.int","pm.int.odile"]}
scoreboard players set 7f43cb8f-46e5-330b-8826-43e191f2fd4f pm.nid 6
tp 7f43cb8f-46e5-330b-8826-43e191f2fd4f 8.5 68 -13.5
