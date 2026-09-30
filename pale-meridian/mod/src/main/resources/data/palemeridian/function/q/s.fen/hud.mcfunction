scoreboard players set #cur pm.world 35
bossbar set palemeridian:objective max 1
bossbar set palemeridian:objective value 0
bossbar set palemeridian:objective name [{"text":"The Drowned Chapel: ","color":"aqua"},{"text":"Drain the flooded crypt under the Fen chapel.","color":"white"}]
bossbar set palemeridian:objective players @a[scores={pm.optbar=1}]
bossbar set palemeridian:objective visible true
function palemeridian:hud/wp_at {poi:"fen.levers"}
