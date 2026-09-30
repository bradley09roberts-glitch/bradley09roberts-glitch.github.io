# Hesper Vane at meridian.hesper
execute positioned -3.5 68 -18.5 unless entity @a[distance=..72] run return fail
execute unless entity 7419ca57-7245-3c29-a99b-76449898ad3d run function palemeridian:npc/hesper/new_1
tp 7419ca57-7245-3c29-a99b-76449898ad3d -3.5 68 -18.5 -90.0 0
execute if data storage palemeridian:npc {hesper:"faded"} as 7419ca57-7245-3c29-a99b-76449898ad3d unless entity @s[tag=pm.skin.faded] run function palemeridian:npc/hesper/apply_skin
execute if data storage palemeridian:npc {hesper:"restored"} as 7419ca57-7245-3c29-a99b-76449898ad3d unless entity @s[tag=pm.skin.restored] run function palemeridian:npc/hesper/apply_skin
execute unless entity 70c770e7-0913-359d-8abe-304172b303ad run summon minecraft:interaction -3.5 68 -18.5 {UUID:[I;1892118759,152253853,-1967247295,1924334509],width:0.9f,height:1.95f,response:1b,Tags:["pm.int","pm.int.hesper"]}
scoreboard players set 70c770e7-0913-359d-8abe-304172b303ad pm.nid 28
tp 70c770e7-0913-359d-8abe-304172b303ad -3.5 68 -18.5
