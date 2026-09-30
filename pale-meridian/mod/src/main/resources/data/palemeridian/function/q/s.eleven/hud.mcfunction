scoreboard players set #cur pm.world 17
bossbar set palemeridian:objective max 11
execute store result bossbar palemeridian:objective value run scoreboard players get s.eleven pm.qp
bossbar set palemeridian:objective name [{"text":"The Eleven: ","color":"aqua"},{"text":"Find the keepsakes of the eleven lost miners.","color":"white"},{"text":" (","color":"gray"},{"score":{"name":"s.eleven","objective":"pm.qp"},"color":"gray"},{"text":"/11)","color":"gray"}]
bossbar set palemeridian:objective players @a[scores={pm.optbar=1}]
bossbar set palemeridian:objective visible true
function palemeridian:hud/wp_hide
