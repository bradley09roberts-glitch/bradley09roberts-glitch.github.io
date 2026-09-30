execute unless data storage palemeridian:npc relay_2 run data modify storage palemeridian:npc relay_2 set value "none"
data modify storage palemeridian:tmp skin set value {id:"relay_2",model:"wide"}
data modify storage palemeridian:tmp skin.state set from storage palemeridian:npc relay_2
function palemeridian:npc/_skin with storage palemeridian:tmp skin
