execute unless data storage palemeridian:npc echo_6 run data modify storage palemeridian:npc echo_6 set value "faded"
tag 5f771b17-c653-3db3-92c1-97a91e6ea351 remove pm.skin.faded
execute if data storage palemeridian:npc {echo_6:"faded"} run data modify entity 5f771b17-c653-3db3-92c1-97a91e6ea351 profile set value {texture:"palemeridian:entity/npc/echo_6_faded",model:"wide"}
execute if data storage palemeridian:npc {echo_6:"faded"} run tag 5f771b17-c653-3db3-92c1-97a91e6ea351 add pm.skin.faded
