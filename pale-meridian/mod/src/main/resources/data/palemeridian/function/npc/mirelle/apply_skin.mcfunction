execute unless data storage palemeridian:npc mirelle run data modify storage palemeridian:npc mirelle set value "faded"
tag 384bc19c-b8c0-33a7-b149-4b8df6b5d414 remove pm.skin.faded
tag 384bc19c-b8c0-33a7-b149-4b8df6b5d414 remove pm.skin.restored
execute if data storage palemeridian:npc {mirelle:"faded"} run data modify entity 384bc19c-b8c0-33a7-b149-4b8df6b5d414 profile set value {texture:"palemeridian:entity/npc/mirelle_faded",model:"slim"}
execute if data storage palemeridian:npc {mirelle:"faded"} run tag 384bc19c-b8c0-33a7-b149-4b8df6b5d414 add pm.skin.faded
execute if data storage palemeridian:npc {mirelle:"restored"} run data modify entity 384bc19c-b8c0-33a7-b149-4b8df6b5d414 profile set value {texture:"palemeridian:entity/npc/mirelle_restored",model:"slim"}
execute if data storage palemeridian:npc {mirelle:"restored"} run tag 384bc19c-b8c0-33a7-b149-4b8df6b5d414 add pm.skin.restored
