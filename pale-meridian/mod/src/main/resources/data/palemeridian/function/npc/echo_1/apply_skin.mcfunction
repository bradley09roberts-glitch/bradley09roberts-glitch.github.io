execute unless data storage palemeridian:npc echo_1 run data modify storage palemeridian:npc echo_1 set value "faded"
data modify storage palemeridian:tmp skin set value {id:"echo_1",model:"wide"}
data modify storage palemeridian:tmp skin.state set from storage palemeridian:npc echo_1
function palemeridian:npc/_skin with storage palemeridian:tmp skin
