scoreboard players set #cur pm.world 18
bossbar set palemeridian:objective max 1
bossbar set palemeridian:objective value 0
bossbar set palemeridian:objective name [{"text":"Under the Cliffs: ","color":"aqua"},{"text":"Follow Tamsin north to the Glassworks.","color":"white"}]
bossbar set palemeridian:objective players @a[scores={pm.optbar=1}]
bossbar set palemeridian:objective visible true
function palemeridian:hud/wp_at {poi:"glassworks.gate"}
