execute unless data storage palemeridian:npc foremans_log run data modify storage palemeridian:npc foremans_log set value "none"
data modify storage palemeridian:tmp skin set value {id:"foremans_log",model:"wide"}
data modify storage palemeridian:tmp skin.state set from storage palemeridian:npc foremans_log
function palemeridian:npc/_skin with storage palemeridian:tmp skin
