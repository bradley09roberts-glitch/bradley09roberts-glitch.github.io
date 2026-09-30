execute unless data storage palemeridian:npc bench_6 run data modify storage palemeridian:npc bench_6 set value "none"
data modify storage palemeridian:tmp skin set value {id:"bench_6",model:"wide"}
data modify storage palemeridian:tmp skin.state set from storage palemeridian:npc bench_6
function palemeridian:npc/_skin with storage palemeridian:tmp skin
