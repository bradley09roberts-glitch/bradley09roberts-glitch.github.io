scoreboard players set #cur pm.world 25
bossbar set palemeridian:objective max 4
execute store result bossbar palemeridian:objective value run scoreboard players get c3.surge pm.qp
bossbar set palemeridian:objective name [{"text":"The Collapse: ","color":"aqua"},{"text":"Relight the four candles in the kiln yard before the Pall closes in.","color":"white"},{"text":" (","color":"gray"},{"score":{"name":"c3.surge","objective":"pm.qp"},"color":"gray"},{"text":"/4)","color":"gray"}]
bossbar set palemeridian:objective players @a[scores={pm.optbar=1}]
bossbar set palemeridian:objective visible true
function palemeridian:hud/wp_at {poi:"glassworks.yard"}
