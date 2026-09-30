scoreboard players set #cur pm.world 16
bossbar set palemeridian:objective max 7
execute store result bossbar palemeridian:objective value run scoreboard players get c2.lamp pm.qp
bossbar set palemeridian:objective name [{"text":"The Orchard Wakelamp: ","color":"aqua"},{"text":"Rebuild the Wakelamp in the windmill's cap.","color":"white"},{"text":" (","color":"gray"},{"score":{"name":"c2.lamp","objective":"pm.qp"},"color":"gray"},{"text":"/7)","color":"gray"}]
bossbar set palemeridian:objective players @a[scores={pm.optbar=1}]
bossbar set palemeridian:objective visible true
function palemeridian:hud/wp_at {poi:"aldercross.wakelamp"}
