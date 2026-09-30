execute unless data storage palemeridian:npc relight_hollin_3 run data modify storage palemeridian:npc relight_hollin_3 set value "none"
data modify storage palemeridian:tmp skin set value {id:"relight_hollin_3",model:"wide"}
data modify storage palemeridian:tmp skin.state set from storage palemeridian:npc relight_hollin_3
function palemeridian:npc/_skin with storage palemeridian:tmp skin
