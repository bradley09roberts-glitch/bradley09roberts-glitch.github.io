scoreboard players set #cur pm.world 7
bossbar set palemeridian:objective max 4
execute store result bossbar palemeridian:objective value run scoreboard players get c1.names pm.qp
bossbar set palemeridian:objective name [{"text":"Hollin, Unremembered: ","color":"aqua"},{"text":"Find the names of Hollin's four lost places.","color":"white"},{"text":" (","color":"gray"},{"score":{"name":"c1.names","objective":"pm.qp"},"color":"gray"},{"text":"/4)","color":"gray"}]
bossbar set palemeridian:objective players @a[scores={pm.optbar=1}]
bossbar set palemeridian:objective visible true
function palemeridian:hud/wp_at {poi:"hollin.well"}
