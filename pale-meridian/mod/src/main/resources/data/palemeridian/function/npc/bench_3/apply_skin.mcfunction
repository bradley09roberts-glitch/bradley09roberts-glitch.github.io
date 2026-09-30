execute unless data storage palemeridian:npc bench_3 run data modify storage palemeridian:npc bench_3 set value "none"
data modify storage palemeridian:tmp skin set value {id:"bench_3",model:"wide"}
data modify storage palemeridian:tmp skin.state set from storage palemeridian:npc bench_3
function palemeridian:npc/_skin with storage palemeridian:tmp skin
