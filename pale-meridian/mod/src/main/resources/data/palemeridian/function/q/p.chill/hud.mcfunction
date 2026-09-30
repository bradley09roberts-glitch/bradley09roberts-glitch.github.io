scoreboard players set #cur pm.world 5
bossbar set palemeridian:objective max 1
bossbar set palemeridian:objective value 0
bossbar set palemeridian:objective name [{"text":"Cold Without Light: ","color":"aqua"},{"text":"Walk in the fog holding a torch or lantern.","color":"white"}]
bossbar set palemeridian:objective players @a[scores={pm.optbar=1}]
bossbar set palemeridian:objective visible true
function palemeridian:hud/wp_hide
