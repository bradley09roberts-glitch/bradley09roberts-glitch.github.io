execute unless data storage palemeridian:npc relay_3 run data modify storage palemeridian:npc relay_3 set value "none"
data modify storage palemeridian:tmp skin set value {id:"relay_3",model:"wide"}
data modify storage palemeridian:tmp skin.state set from storage palemeridian:npc relay_3
function palemeridian:npc/_skin with storage palemeridian:tmp skin
