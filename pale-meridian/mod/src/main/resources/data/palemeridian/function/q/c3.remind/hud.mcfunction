scoreboard players set #cur pm.world 21
bossbar set palemeridian:objective max 3
execute store result bossbar palemeridian:objective value run scoreboard players get c3.remind pm.qp
bossbar set palemeridian:objective name [{"text":"Something of Hers: ","color":"aqua"},{"text":"Show Tamsin three things that are hers.","color":"white"},{"text":" (","color":"gray"},{"score":{"name":"c3.remind","objective":"pm.qp"},"color":"gray"},{"text":"/3)","color":"gray"}]
bossbar set palemeridian:objective players @a[scores={pm.optbar=1}]
bossbar set palemeridian:objective visible true
function palemeridian:hud/wp_at {poi:"deepcut.tamsin"}
