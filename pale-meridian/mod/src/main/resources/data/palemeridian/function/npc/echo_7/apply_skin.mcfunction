execute unless data storage palemeridian:npc echo_7 run data modify storage palemeridian:npc echo_7 set value "faded"
tag adf443d0-fec4-33f8-854c-e0dd4c9bf4f4 remove pm.skin.faded
execute if data storage palemeridian:npc {echo_7:"faded"} run data modify entity adf443d0-fec4-33f8-854c-e0dd4c9bf4f4 profile set value {texture:"palemeridian:entity/npc/echo_7_faded",model:"wide"}
execute if data storage palemeridian:npc {echo_7:"faded"} run tag adf443d0-fec4-33f8-854c-e0dd4c9bf4f4 add pm.skin.faded
