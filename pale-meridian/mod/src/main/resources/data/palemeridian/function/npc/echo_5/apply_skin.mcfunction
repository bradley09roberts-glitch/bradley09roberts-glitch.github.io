execute unless data storage palemeridian:npc echo_5 run data modify storage palemeridian:npc echo_5 set value "faded"
tag d2b0fb54-cab4-39b4-8e67-abd8df224940 remove pm.skin.faded
execute if data storage palemeridian:npc {echo_5:"faded"} run data modify entity d2b0fb54-cab4-39b4-8e67-abd8df224940 profile set value {texture:"palemeridian:entity/npc/echo_5_faded",model:"slim"}
execute if data storage palemeridian:npc {echo_5:"faded"} run tag d2b0fb54-cab4-39b4-8e67-abd8df224940 add pm.skin.faded
