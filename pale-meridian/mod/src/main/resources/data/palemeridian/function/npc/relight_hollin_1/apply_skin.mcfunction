execute unless data storage palemeridian:npc relight_hollin_1 run data modify storage palemeridian:npc relight_hollin_1 set value "none"
data modify storage palemeridian:tmp skin set value {id:"relight_hollin_1",model:"wide"}
data modify storage palemeridian:tmp skin.state set from storage palemeridian:npc relight_hollin_1
function palemeridian:npc/_skin with storage palemeridian:tmp skin
