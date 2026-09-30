execute unless data storage palemeridian:npc cradle run data modify storage palemeridian:npc cradle set value "none"
data modify storage palemeridian:tmp skin set value {id:"cradle",model:"wide"}
data modify storage palemeridian:tmp skin.state set from storage palemeridian:npc cradle
function palemeridian:npc/_skin with storage palemeridian:tmp skin
