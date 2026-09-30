execute unless data storage palemeridian:npc chart_table run data modify storage palemeridian:npc chart_table set value "none"
data modify storage palemeridian:tmp skin set value {id:"chart_table",model:"wide"}
data modify storage palemeridian:tmp skin.state set from storage palemeridian:npc chart_table
function palemeridian:npc/_skin with storage palemeridian:tmp skin
