execute unless data storage palemeridian:npc odile run data modify storage palemeridian:npc odile set value "faded"
data modify storage palemeridian:tmp skin set value {id:"odile",model:"slim"}
data modify storage palemeridian:tmp skin.state set from storage palemeridian:npc odile
function palemeridian:npc/_skin with storage palemeridian:tmp skin
