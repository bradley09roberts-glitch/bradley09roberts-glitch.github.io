execute unless data storage palemeridian:npc bench_2 run data modify storage palemeridian:npc bench_2 set value "none"
data modify storage palemeridian:tmp skin set value {id:"bench_2",model:"wide"}
data modify storage palemeridian:tmp skin.state set from storage palemeridian:npc bench_2
function palemeridian:npc/_skin with storage palemeridian:tmp skin
