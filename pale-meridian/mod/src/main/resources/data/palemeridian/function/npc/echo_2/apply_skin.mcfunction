execute unless data storage palemeridian:npc echo_2 run data modify storage palemeridian:npc echo_2 set value "faded"
tag dd3dcead-b65d-3e66-b89e-adcb4e765b70 remove pm.skin.faded
execute if data storage palemeridian:npc {echo_2:"faded"} run data modify entity dd3dcead-b65d-3e66-b89e-adcb4e765b70 profile set value {texture:"palemeridian:entity/npc/echo_2_faded",model:"slim"}
execute if data storage palemeridian:npc {echo_2:"faded"} run tag dd3dcead-b65d-3e66-b89e-adcb4e765b70 add pm.skin.faded
