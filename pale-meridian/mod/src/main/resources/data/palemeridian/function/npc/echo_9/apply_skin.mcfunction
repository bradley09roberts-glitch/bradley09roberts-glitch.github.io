execute unless data storage palemeridian:npc echo_9 run data modify storage palemeridian:npc echo_9 set value "faded"
tag 816e3979-45b6-3612-9bf8-b899620fafda remove pm.skin.faded
execute if data storage palemeridian:npc {echo_9:"faded"} run data modify entity 816e3979-45b6-3612-9bf8-b899620fafda profile set value {texture:"palemeridian:entity/npc/echo_9_faded",model:"slim"}
execute if data storage palemeridian:npc {echo_9:"faded"} run tag 816e3979-45b6-3612-9bf8-b899620fafda add pm.skin.faded
