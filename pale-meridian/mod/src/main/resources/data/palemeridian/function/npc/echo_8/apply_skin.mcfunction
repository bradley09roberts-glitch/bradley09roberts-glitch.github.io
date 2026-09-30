execute unless data storage palemeridian:npc echo_8 run data modify storage palemeridian:npc echo_8 set value "faded"
tag d15c7dc9-ad9e-34f5-b019-9b855c29ec1a remove pm.skin.faded
execute if data storage palemeridian:npc {echo_8:"faded"} run data modify entity d15c7dc9-ad9e-34f5-b019-9b855c29ec1a profile set value {texture:"palemeridian:entity/npc/echo_8_faded",model:"wide"}
execute if data storage palemeridian:npc {echo_8:"faded"} run tag d15c7dc9-ad9e-34f5-b019-9b855c29ec1a add pm.skin.faded
