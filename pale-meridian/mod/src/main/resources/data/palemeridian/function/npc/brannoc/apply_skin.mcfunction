execute unless data storage palemeridian:npc brannoc run data modify storage palemeridian:npc brannoc set value "faded"
tag afbde27c-30a9-3951-99de-07d41fd64350 remove pm.skin.faded
tag afbde27c-30a9-3951-99de-07d41fd64350 remove pm.skin.restored
execute if data storage palemeridian:npc {brannoc:"faded"} run data modify entity afbde27c-30a9-3951-99de-07d41fd64350 profile set value {texture:"palemeridian:entity/npc/brannoc_faded",model:"wide"}
execute if data storage palemeridian:npc {brannoc:"faded"} run tag afbde27c-30a9-3951-99de-07d41fd64350 add pm.skin.faded
execute if data storage palemeridian:npc {brannoc:"restored"} run data modify entity afbde27c-30a9-3951-99de-07d41fd64350 profile set value {texture:"palemeridian:entity/npc/brannoc_restored",model:"wide"}
execute if data storage palemeridian:npc {brannoc:"restored"} run tag afbde27c-30a9-3951-99de-07d41fd64350 add pm.skin.restored
