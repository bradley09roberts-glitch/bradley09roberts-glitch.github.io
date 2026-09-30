# Mirelle at hollin.mirelle
execute positioned 143.5 67 111.5 unless entity @a[distance=..72] run return fail
execute unless entity 384bc19c-b8c0-33a7-b149-4b8df6b5d414 run function palemeridian:npc/mirelle/new_0
tp 384bc19c-b8c0-33a7-b149-4b8df6b5d414 143.5 67 111.5 90.0 0
execute if data storage palemeridian:npc {mirelle:"faded"} as 384bc19c-b8c0-33a7-b149-4b8df6b5d414 unless entity @s[tag=pm.skin.faded] run function palemeridian:npc/mirelle/apply_skin
execute if data storage palemeridian:npc {mirelle:"restored"} as 384bc19c-b8c0-33a7-b149-4b8df6b5d414 unless entity @s[tag=pm.skin.restored] run function palemeridian:npc/mirelle/apply_skin
execute unless entity 4452b007-8088-3dfd-b31b-b5166cffaa33 run summon minecraft:interaction 143.5 67 111.5 {UUID:[I;1146269703,-2138554883,-1290029802,1828694579],width:0.9f,height:1.95f,response:1b,Tags:["pm.int","pm.int.mirelle"]}
scoreboard players set 4452b007-8088-3dfd-b31b-b5166cffaa33 pm.nid 7
tp 4452b007-8088-3dfd-b31b-b5166cffaa33 143.5 67 111.5
