scoreboard players set #cur pm.world 8
bossbar set palemeridian:objective max 1
bossbar set palemeridian:objective value 0
bossbar set palemeridian:objective name [{"text":"The Lamplighters' Round: ","color":"aqua"},{"text":"Ring Hollin's four bells in the order of the Round.","color":"white"}]
bossbar set palemeridian:objective players @a[scores={pm.optbar=1}]
bossbar set palemeridian:objective visible true
function palemeridian:hud/wp_at {poi:"hollin.bell.marsh"}
