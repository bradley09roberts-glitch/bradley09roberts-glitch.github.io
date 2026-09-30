execute unless data storage palemeridian:npc tamsin run data modify storage palemeridian:npc tamsin set value "faded"
data modify storage palemeridian:tmp skin set value {id:"tamsin",model:"slim"}
data modify storage palemeridian:tmp skin.state set from storage palemeridian:npc tamsin
function palemeridian:npc/_skin with storage palemeridian:tmp skin
