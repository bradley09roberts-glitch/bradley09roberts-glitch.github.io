scoreboard players set #cur pm.world 11
bossbar set palemeridian:objective max 1
bossbar set palemeridian:objective value 0
bossbar set palemeridian:objective name [{"text":"A Place to Rest: ","color":"aqua"},{"text":"Place a bed in the Surveyor's Rest.","color":"white"}]
bossbar set palemeridian:objective players @a[scores={pm.optbar=1}]
bossbar set palemeridian:objective visible true
function palemeridian:hud/wp_at {poi:"hollin.home.rest"}
