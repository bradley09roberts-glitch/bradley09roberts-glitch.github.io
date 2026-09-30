execute unless data storage palemeridian:npc echo_10 run data modify storage palemeridian:npc echo_10 set value "faded"
tag f0691d83-7354-357f-be9e-756d4e105284 remove pm.skin.faded
execute if data storage palemeridian:npc {echo_10:"faded"} run data modify entity f0691d83-7354-357f-be9e-756d4e105284 profile set value {texture:"palemeridian:entity/npc/echo_10_faded",model:"wide"}
execute if data storage palemeridian:npc {echo_10:"faded"} run tag f0691d83-7354-357f-be9e-756d4e105284 add pm.skin.faded
