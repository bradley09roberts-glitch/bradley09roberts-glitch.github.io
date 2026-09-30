scoreboard players set #cur pm.world 9
bossbar set palemeridian:objective max 7
execute store result bossbar palemeridian:objective value run scoreboard players get c1.lamp pm.qp
bossbar set palemeridian:objective name [{"text":"Hollin's Wakelamp: ","color":"aqua"},{"text":"Rebuild the Wakelamp at the top of the bell tower.","color":"white"},{"text":" (","color":"gray"},{"score":{"name":"c1.lamp","objective":"pm.qp"},"color":"gray"},{"text":"/7)","color":"gray"}]
bossbar set palemeridian:objective players @a[scores={pm.optbar=1}]
bossbar set palemeridian:objective visible true
function palemeridian:hud/wp_at {poi:"hollin.wakelamp"}
