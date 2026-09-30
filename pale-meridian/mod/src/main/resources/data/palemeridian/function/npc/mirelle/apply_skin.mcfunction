execute unless data storage palemeridian:npc mirelle run data modify storage palemeridian:npc mirelle set value "faded"
data modify storage palemeridian:tmp skin set value {id:"mirelle",model:"slim"}
data modify storage palemeridian:tmp skin.state set from storage palemeridian:npc mirelle
function palemeridian:npc/_skin with storage palemeridian:tmp skin
