execute unless data storage palemeridian:npc echo_3 run data modify storage palemeridian:npc echo_3 set value "faded"
data modify storage palemeridian:tmp skin set value {id:"echo_3",model:"slim"}
data modify storage palemeridian:tmp skin.state set from storage palemeridian:npc echo_3
function palemeridian:npc/_skin with storage palemeridian:tmp skin
