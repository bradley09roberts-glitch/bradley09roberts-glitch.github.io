scoreboard players set #cur pm.world 20
bossbar set palemeridian:objective max 1
bossbar set palemeridian:objective value 0
bossbar set palemeridian:objective name [{"text":"Into the Deepcut: ","color":"aqua"},{"text":"Find Tamsin inside the Deepcut.","color":"white"}]
bossbar set palemeridian:objective players @a[scores={pm.optbar=1}]
bossbar set palemeridian:objective visible true
function palemeridian:hud/wp_at {poi:"deepcut.tamsin"}
