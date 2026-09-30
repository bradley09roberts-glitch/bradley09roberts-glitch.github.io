scoreboard players set #cur pm.world 24
bossbar set palemeridian:objective max 7
execute store result bossbar palemeridian:objective value run scoreboard players get c3.lamp pm.qp
bossbar set palemeridian:objective name [{"text":"The Glassworks Wakelamp: ","color":"aqua"},{"text":"Rebuild the Wakelamp on top of the kiln tower.","color":"white"},{"text":" (","color":"gray"},{"score":{"name":"c3.lamp","objective":"pm.qp"},"color":"gray"},{"text":"/7)","color":"gray"}]
bossbar set palemeridian:objective players @a[scores={pm.optbar=1}]
bossbar set palemeridian:objective visible true
function palemeridian:hud/wp_at {poi:"glassworks.wakelamp"}
