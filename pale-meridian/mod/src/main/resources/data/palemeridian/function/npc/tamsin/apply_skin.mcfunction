execute unless data storage palemeridian:npc tamsin run data modify storage palemeridian:npc tamsin set value "faded"
tag db6205b6-e232-38b4-8a9e-626e74e210bf remove pm.skin.faded
tag db6205b6-e232-38b4-8a9e-626e74e210bf remove pm.skin.restored
execute if data storage palemeridian:npc {tamsin:"faded"} run data modify entity db6205b6-e232-38b4-8a9e-626e74e210bf profile set value {texture:"palemeridian:entity/npc/tamsin_faded",model:"slim"}
execute if data storage palemeridian:npc {tamsin:"faded"} run tag db6205b6-e232-38b4-8a9e-626e74e210bf add pm.skin.faded
execute if data storage palemeridian:npc {tamsin:"restored"} run data modify entity db6205b6-e232-38b4-8a9e-626e74e210bf profile set value {texture:"palemeridian:entity/npc/tamsin_restored",model:"slim"}
execute if data storage palemeridian:npc {tamsin:"restored"} run tag db6205b6-e232-38b4-8a9e-626e74e210bf add pm.skin.restored
