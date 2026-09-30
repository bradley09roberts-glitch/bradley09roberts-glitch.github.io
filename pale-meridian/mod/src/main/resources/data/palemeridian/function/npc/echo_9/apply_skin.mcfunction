execute unless data storage palemeridian:npc echo_9 run data modify storage palemeridian:npc echo_9 set value "faded"
data modify storage palemeridian:tmp skin set value {id:"echo_9",model:"slim"}
data modify storage palemeridian:tmp skin.state set from storage palemeridian:npc echo_9
function palemeridian:npc/_skin with storage palemeridian:tmp skin
