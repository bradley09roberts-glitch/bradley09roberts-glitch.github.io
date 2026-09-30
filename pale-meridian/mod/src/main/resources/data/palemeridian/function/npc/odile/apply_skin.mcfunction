execute unless data storage palemeridian:npc odile run data modify storage palemeridian:npc odile set value "faded"
tag fccb238b-18e4-3959-9019-36c7fc30e60d remove pm.skin.faded
tag fccb238b-18e4-3959-9019-36c7fc30e60d remove pm.skin.restored
execute if data storage palemeridian:npc {odile:"faded"} run data modify entity fccb238b-18e4-3959-9019-36c7fc30e60d profile set value {texture:"palemeridian:entity/npc/odile_faded",model:"slim"}
execute if data storage palemeridian:npc {odile:"faded"} run tag fccb238b-18e4-3959-9019-36c7fc30e60d add pm.skin.faded
execute if data storage palemeridian:npc {odile:"restored"} run data modify entity fccb238b-18e4-3959-9019-36c7fc30e60d profile set value {texture:"palemeridian:entity/npc/odile_restored",model:"slim"}
execute if data storage palemeridian:npc {odile:"restored"} run tag fccb238b-18e4-3959-9019-36c7fc30e60d add pm.skin.restored
