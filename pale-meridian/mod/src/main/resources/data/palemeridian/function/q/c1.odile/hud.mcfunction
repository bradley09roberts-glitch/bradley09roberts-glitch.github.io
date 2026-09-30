scoreboard players set #cur pm.world 6
bossbar set palemeridian:objective max 1
bossbar set palemeridian:objective value 0
bossbar set palemeridian:objective name [{"text":"The Lamplighter: ","color":"aqua"},{"text":"Speak with the lamplighter at Hollin's gate.","color":"white"}]
bossbar set palemeridian:objective players @a[scores={pm.optbar=1}]
bossbar set palemeridian:objective visible true
function palemeridian:hud/wp_at {poi:"hollin.odile"}
