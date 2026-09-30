execute unless data storage palemeridian:npc noticeboard run data modify storage palemeridian:npc noticeboard set value "none"
data modify storage palemeridian:tmp skin set value {id:"noticeboard",model:"wide"}
data modify storage palemeridian:tmp skin.state set from storage palemeridian:npc noticeboard
function palemeridian:npc/_skin with storage palemeridian:tmp skin
