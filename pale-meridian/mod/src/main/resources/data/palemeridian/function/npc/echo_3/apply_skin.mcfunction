execute unless data storage palemeridian:npc echo_3 run data modify storage palemeridian:npc echo_3 set value "faded"
tag ed011aa7-2737-37ee-a9ce-c5043b2a0afc remove pm.skin.faded
execute if data storage palemeridian:npc {echo_3:"faded"} run data modify entity ed011aa7-2737-37ee-a9ce-c5043b2a0afc profile set value {texture:"palemeridian:entity/npc/echo_3_faded",model:"slim"}
execute if data storage palemeridian:npc {echo_3:"faded"} run tag ed011aa7-2737-37ee-a9ce-c5043b2a0afc add pm.skin.faded
