scoreboard players set #cur pm.world 3
bossbar set palemeridian:objective max 4
execute store result bossbar palemeridian:objective value run scoreboard players get p.lamp pm.qp
bossbar set palemeridian:objective name [{"text":"The Landing Lamp: ","color":"aqua"},{"text":"Rebuild the broken lamp beside the road.","color":"white"},{"text":" (","color":"gray"},{"score":{"name":"p.lamp","objective":"pm.qp"},"color":"gray"},{"text":"/4)","color":"gray"}]
bossbar set palemeridian:objective players @a[scores={pm.optbar=1}]
bossbar set palemeridian:objective visible true
function palemeridian:hud/wp_at {poi:"landing.lamp"}
