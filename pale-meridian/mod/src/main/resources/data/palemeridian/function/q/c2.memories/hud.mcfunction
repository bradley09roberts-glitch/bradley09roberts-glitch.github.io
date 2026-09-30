scoreboard players set #cur pm.world 14
bossbar set palemeridian:objective max 3
execute store result bossbar palemeridian:objective value run scoreboard players get c2.memories pm.qp
bossbar set palemeridian:objective name [{"text":"Remember Aldercross: ","color":"aqua"},{"text":"Find the three places Aldercross remembers.","color":"white"},{"text":" (","color":"gray"},{"score":{"name":"c2.memories","objective":"pm.qp"},"color":"gray"},{"text":"/3)","color":"gray"}]
bossbar set palemeridian:objective players @a[scores={pm.optbar=1}]
bossbar set palemeridian:objective visible true
function palemeridian:hud/wp_at {poi:"aldercross.tree"}
