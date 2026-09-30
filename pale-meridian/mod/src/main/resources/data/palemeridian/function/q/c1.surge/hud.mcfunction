scoreboard players set #cur pm.world 10
bossbar set palemeridian:objective max 4
execute store result bossbar palemeridian:objective value run scoreboard players get c1.surge pm.qp
bossbar set palemeridian:objective name [{"text":"Hold the Light: ","color":"aqua"},{"text":"Relight the four plaza lamps while the Pall pushes back.","color":"white"},{"text":" (","color":"gray"},{"score":{"name":"c1.surge","objective":"pm.qp"},"color":"gray"},{"text":"/4)","color":"gray"}]
bossbar set palemeridian:objective players @a[scores={pm.optbar=1}]
bossbar set palemeridian:objective visible true
function palemeridian:hud/wp_at {poi:"hollin.plaza"}
