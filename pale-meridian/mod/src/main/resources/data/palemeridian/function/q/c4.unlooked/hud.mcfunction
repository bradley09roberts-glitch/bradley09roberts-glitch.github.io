scoreboard players set #cur pm.world 29
bossbar set palemeridian:objective max 1
bossbar set palemeridian:objective value 0
bossbar set palemeridian:objective name [{"text":"The Unlooked: ","color":"aqua"},{"text":"Face what the Pall has become.","color":"white"}]
bossbar set palemeridian:objective players @a[scores={pm.optbar=1}]
bossbar set palemeridian:objective visible true
function palemeridian:hud/wp_at {poi:"meridian.arena"}
