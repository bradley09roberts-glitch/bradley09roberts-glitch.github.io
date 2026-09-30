execute unless data storage palemeridian:npc jory run data modify storage palemeridian:npc jory set value "faded"
tag 0e7c3147-5e85-3788-83c8-3d9350da5d92 remove pm.skin.faded
tag 0e7c3147-5e85-3788-83c8-3d9350da5d92 remove pm.skin.restored
execute if data storage palemeridian:npc {jory:"faded"} run data modify entity 0e7c3147-5e85-3788-83c8-3d9350da5d92 profile set value {texture:"palemeridian:entity/npc/jory_faded",model:"wide"}
execute if data storage palemeridian:npc {jory:"faded"} run tag 0e7c3147-5e85-3788-83c8-3d9350da5d92 add pm.skin.faded
execute if data storage palemeridian:npc {jory:"restored"} run data modify entity 0e7c3147-5e85-3788-83c8-3d9350da5d92 profile set value {texture:"palemeridian:entity/npc/jory_restored",model:"wide"}
execute if data storage palemeridian:npc {jory:"restored"} run tag 0e7c3147-5e85-3788-83c8-3d9350da5d92 add pm.skin.restored
