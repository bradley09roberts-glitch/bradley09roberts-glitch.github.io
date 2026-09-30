# Hesper Vane at glassworks.hesper_garden
execute positioned 20.5 70 -316.5 unless entity @a[distance=..72] run return fail
execute unless entity 7419ca57-7245-3c29-a99b-76449898ad3d run function palemeridian:npc/hesper/new_0
tp 7419ca57-7245-3c29-a99b-76449898ad3d 20.5 70 -316.5 0.0 0
execute if data storage palemeridian:npc {hesper:"faded"} as 7419ca57-7245-3c29-a99b-76449898ad3d unless entity @s[tag=pm.skin.faded] run function palemeridian:npc/hesper/apply_skin
execute if data storage palemeridian:npc {hesper:"restored"} as 7419ca57-7245-3c29-a99b-76449898ad3d unless entity @s[tag=pm.skin.restored] run function palemeridian:npc/hesper/apply_skin
execute unless entity 70c770e7-0913-359d-8abe-304172b303ad run summon minecraft:interaction 20.5 70 -316.5 {UUID:[I;1892118759,152253853,-1967247295,1924334509],width:0.9f,height:1.95f,response:1b,Tags:["pm.int","pm.int.hesper"]}
scoreboard players set 70c770e7-0913-359d-8abe-304172b303ad pm.nid 28
tp 70c770e7-0913-359d-8abe-304172b303ad 20.5 70 -316.5
