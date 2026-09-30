execute unless data storage palemeridian:npc hesper run data modify storage palemeridian:npc hesper set value "faded"
data modify storage palemeridian:tmp skin set value {id:"hesper",model:"slim"}
data modify storage palemeridian:tmp skin.state set from storage palemeridian:npc hesper
function palemeridian:npc/_skin with storage palemeridian:tmp skin
