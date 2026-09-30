scoreboard players set #cur pm.world 15
bossbar set palemeridian:objective max 3
execute store result bossbar palemeridian:objective value run scoreboard players get c2.hearts pm.qp
bossbar set palemeridian:objective name [{"text":"The Heartwood: ","color":"aqua"},{"text":"Break the three hearts grown into the Heartwood.","color":"white"},{"text":" (","color":"gray"},{"score":{"name":"c2.hearts","objective":"pm.qp"},"color":"gray"},{"text":"/3)","color":"gray"}]
bossbar set palemeridian:objective players @a[scores={pm.optbar=1}]
bossbar set palemeridian:objective visible true
function palemeridian:hud/wp_at {poi:"aldercross.heartwood"}
