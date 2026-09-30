execute unless data storage palemeridian:npc echo_11 run data modify storage palemeridian:npc echo_11 set value "faded"
tag 78271077-3405-300e-a2ac-31987c510b11 remove pm.skin.faded
execute if data storage palemeridian:npc {echo_11:"faded"} run data modify entity 78271077-3405-300e-a2ac-31987c510b11 profile set value {texture:"palemeridian:entity/npc/echo_11_faded",model:"wide"}
execute if data storage palemeridian:npc {echo_11:"faded"} run tag 78271077-3405-300e-a2ac-31987c510b11 add pm.skin.faded
