execute unless data storage palemeridian:npc jory run data modify storage palemeridian:npc jory set value "faded"
data modify storage palemeridian:tmp skin set value {id:"jory",model:"wide"}
data modify storage palemeridian:tmp skin.state set from storage palemeridian:npc jory
function palemeridian:npc/_skin with storage palemeridian:tmp skin
