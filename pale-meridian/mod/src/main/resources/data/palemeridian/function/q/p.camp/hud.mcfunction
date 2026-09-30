scoreboard players set #cur pm.world 2
bossbar set palemeridian:objective max 1
bossbar set palemeridian:objective value 0
bossbar set palemeridian:objective name [{"text":"Tamsin's Camp: ","color":"aqua"},{"text":"Find Tamsin's camp up the road.","color":"white"}]
bossbar set palemeridian:objective players @a[scores={pm.optbar=1}]
bossbar set palemeridian:objective visible true
function palemeridian:hud/wp_at {poi:"landing.camp"}
