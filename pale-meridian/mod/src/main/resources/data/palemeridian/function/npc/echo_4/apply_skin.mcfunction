execute unless data storage palemeridian:npc echo_4 run data modify storage palemeridian:npc echo_4 set value "faded"
tag 003086e7-986c-3fb9-b1a0-4845edacc4bc remove pm.skin.faded
execute if data storage palemeridian:npc {echo_4:"faded"} run data modify entity 003086e7-986c-3fb9-b1a0-4845edacc4bc profile set value {texture:"palemeridian:entity/npc/echo_4_faded",model:"wide"}
execute if data storage palemeridian:npc {echo_4:"faded"} run tag 003086e7-986c-3fb9-b1a0-4845edacc4bc add pm.skin.faded
