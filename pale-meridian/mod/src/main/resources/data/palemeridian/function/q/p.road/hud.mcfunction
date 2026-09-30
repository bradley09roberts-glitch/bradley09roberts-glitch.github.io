scoreboard players set #cur pm.world 4
bossbar set palemeridian:objective max 1
bossbar set palemeridian:objective value 0
bossbar set palemeridian:objective name [{"text":"Into the Pall: ","color":"aqua"},{"text":"Follow the lamp road north to Hollin.","color":"white"}]
bossbar set palemeridian:objective players @a[scores={pm.optbar=1}]
bossbar set palemeridian:objective visible true
function palemeridian:hud/wp_at {poi:"hollin.gate"}
