execute unless data storage palemeridian:npc vow run data modify storage palemeridian:npc vow set value "none"
data modify storage palemeridian:tmp skin set value {id:"vow",model:"wide"}
data modify storage palemeridian:tmp skin.state set from storage palemeridian:npc vow
function palemeridian:npc/_skin with storage palemeridian:tmp skin
