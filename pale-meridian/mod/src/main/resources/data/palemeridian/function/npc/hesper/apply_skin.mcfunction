execute unless data storage palemeridian:npc hesper run data modify storage palemeridian:npc hesper set value "faded"
tag 7419ca57-7245-3c29-a99b-76449898ad3d remove pm.skin.faded
tag 7419ca57-7245-3c29-a99b-76449898ad3d remove pm.skin.restored
execute if data storage palemeridian:npc {hesper:"faded"} run data modify entity 7419ca57-7245-3c29-a99b-76449898ad3d profile set value {texture:"palemeridian:entity/npc/hesper_faded",model:"slim"}
execute if data storage palemeridian:npc {hesper:"faded"} run tag 7419ca57-7245-3c29-a99b-76449898ad3d add pm.skin.faded
execute if data storage palemeridian:npc {hesper:"restored"} run data modify entity 7419ca57-7245-3c29-a99b-76449898ad3d profile set value {texture:"palemeridian:entity/npc/hesper_restored",model:"slim"}
execute if data storage palemeridian:npc {hesper:"restored"} run tag 7419ca57-7245-3c29-a99b-76449898ad3d add pm.skin.restored
