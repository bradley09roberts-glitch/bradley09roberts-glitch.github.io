execute unless data storage palemeridian:npc brannoc run data modify storage palemeridian:npc brannoc set value "faded"
data modify storage palemeridian:tmp skin set value {id:"brannoc",model:"wide"}
data modify storage palemeridian:tmp skin.state set from storage palemeridian:npc brannoc
function palemeridian:npc/_skin with storage palemeridian:tmp skin
