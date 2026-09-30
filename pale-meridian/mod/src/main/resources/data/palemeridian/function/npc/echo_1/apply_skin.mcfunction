execute unless data storage palemeridian:npc echo_1 run data modify storage palemeridian:npc echo_1 set value "faded"
tag 6187e44a-275b-326d-958b-679508ea6556 remove pm.skin.faded
execute if data storage palemeridian:npc {echo_1:"faded"} run data modify entity 6187e44a-275b-326d-958b-679508ea6556 profile set value {texture:"palemeridian:entity/npc/echo_1_faded",model:"wide"}
execute if data storage palemeridian:npc {echo_1:"faded"} run tag 6187e44a-275b-326d-958b-679508ea6556 add pm.skin.faded
