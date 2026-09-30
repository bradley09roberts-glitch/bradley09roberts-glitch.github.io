scoreboard players set #cur pm.world 26
bossbar set palemeridian:objective max 1
bossbar set palemeridian:objective value 0
bossbar set palemeridian:objective name [{"text":"The Crossing: ","color":"aqua"},{"text":"Cross Vellmere to the Meridian.","color":"white"}]
bossbar set palemeridian:objective players @a[scores={pm.optbar=1}]
bossbar set palemeridian:objective visible true
function palemeridian:hud/wp_at {poi:"meridian.dock"}
