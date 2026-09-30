execute unless data storage palemeridian:npc tamsin_notes run data modify storage palemeridian:npc tamsin_notes set value "none"
data modify storage palemeridian:tmp skin set value {id:"tamsin_notes",model:"wide"}
data modify storage palemeridian:tmp skin.state set from storage palemeridian:npc tamsin_notes
function palemeridian:npc/_skin with storage palemeridian:tmp skin
