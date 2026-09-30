scoreboard players set #cur pm.world 36
bossbar set palemeridian:objective max 8
execute store result bossbar palemeridian:objective value run scoreboard players get s.bench pm.qp
bossbar set palemeridian:objective name [{"text":"Tamsin's Benchmarks: ","color":"aqua"},{"text":"Find the survey benchmarks Tamsin left along her route.","color":"white"},{"text":" (","color":"gray"},{"score":{"name":"s.bench","objective":"pm.qp"},"color":"gray"},{"text":"/8)","color":"gray"}]
bossbar set palemeridian:objective players @a[scores={pm.optbar=1}]
bossbar set palemeridian:objective visible true
function palemeridian:hud/wp_hide
