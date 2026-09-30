# Jory at hollin.jory
execute positioned 118.5 67 104.5 unless entity @a[distance=..72] run return fail
execute unless entity 0e7c3147-5e85-3788-83c8-3d9350da5d92 run function palemeridian:npc/jory/new_0
tp 0e7c3147-5e85-3788-83c8-3d9350da5d92 118.5 67 104.5 180.0 0
execute if data storage palemeridian:npc {jory:"faded"} as 0e7c3147-5e85-3788-83c8-3d9350da5d92 unless entity @s[tag=pm.skin.faded] run function palemeridian:npc/jory/apply_skin
execute if data storage palemeridian:npc {jory:"restored"} as 0e7c3147-5e85-3788-83c8-3d9350da5d92 unless entity @s[tag=pm.skin.restored] run function palemeridian:npc/jory/apply_skin
execute unless entity fd0b185e-bb6a-30ae-af17-be891144b9c8 run summon minecraft:interaction 118.5 67 104.5 {UUID:[I;-49604514,-1150668626,-1357398391,289716680],width:0.9f,height:1.95f,response:1b,Tags:["pm.int","pm.int.jory"]}
scoreboard players set fd0b185e-bb6a-30ae-af17-be891144b9c8 pm.nid 8
tp fd0b185e-bb6a-30ae-af17-be891144b9c8 118.5 67 104.5
