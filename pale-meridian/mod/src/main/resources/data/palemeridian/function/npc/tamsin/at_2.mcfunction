# Tamsin Reed at hollin.tamsin
execute positioned 111.5 67 128.5 unless entity @a[distance=..72] run return fail
execute unless entity db6205b6-e232-38b4-8a9e-626e74e210bf run function palemeridian:npc/tamsin/new_2
tp db6205b6-e232-38b4-8a9e-626e74e210bf 111.5 67 128.5 90.0 0
execute if data storage palemeridian:npc {tamsin:"faded"} as db6205b6-e232-38b4-8a9e-626e74e210bf unless entity @s[tag=pm.skin.faded] run function palemeridian:npc/tamsin/apply_skin
execute if data storage palemeridian:npc {tamsin:"restored"} as db6205b6-e232-38b4-8a9e-626e74e210bf unless entity @s[tag=pm.skin.restored] run function palemeridian:npc/tamsin/apply_skin
execute unless entity e0992e0f-4a4e-3d4d-b38d-e81a605756eb run summon minecraft:interaction 111.5 67 128.5 {UUID:[I;-526832113,1246641485,-1282545638,1616336619],width:0.9f,height:1.95f,response:1b,Tags:["pm.int","pm.int.tamsin"]}
scoreboard players set e0992e0f-4a4e-3d4d-b38d-e81a605756eb pm.nid 11
tp e0992e0f-4a4e-3d4d-b38d-e81a605756eb 111.5 67 128.5
