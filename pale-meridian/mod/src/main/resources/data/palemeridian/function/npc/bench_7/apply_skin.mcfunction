execute unless data storage palemeridian:npc bench_7 run data modify storage palemeridian:npc bench_7 set value "none"
data modify storage palemeridian:tmp skin set value {id:"bench_7",model:"wide"}
data modify storage palemeridian:tmp skin.state set from storage palemeridian:npc bench_7
function palemeridian:npc/_skin with storage palemeridian:tmp skin
