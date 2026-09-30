scoreboard players set #cur pm.world 19
bossbar set palemeridian:objective max 1
bossbar set palemeridian:objective value 0
bossbar set palemeridian:objective name [{"text":"The Foreman's Log: ","color":"aqua"},{"text":"Read the last log in the foreman's office.","color":"white"}]
bossbar set palemeridian:objective players @a[scores={pm.optbar=1}]
bossbar set palemeridian:objective visible true
function palemeridian:hud/wp_at {poi:"glassworks.log"}
