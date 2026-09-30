execute unless data storage palemeridian:npc bench_4 run data modify storage palemeridian:npc bench_4 set value "none"
data modify storage palemeridian:tmp skin set value {id:"bench_4",model:"wide"}
data modify storage palemeridian:tmp skin.state set from storage palemeridian:npc bench_4
function palemeridian:npc/_skin with storage palemeridian:tmp skin
