scoreboard players set #cur pm.world 23
bossbar set palemeridian:objective max 1
bossbar set palemeridian:objective value 0
bossbar set palemeridian:objective name [{"text":"Kiln Three: ","color":"aqua"},{"text":"Fire Kiln Three the way the foreman's log describes.","color":"white"}]
bossbar set palemeridian:objective players @a[scores={pm.optbar=1}]
bossbar set palemeridian:objective visible true
function palemeridian:hud/wp_at {poi:"glassworks.firebox"}
