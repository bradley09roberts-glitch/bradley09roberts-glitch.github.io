scoreboard players set #cur pm.world 31
bossbar set palemeridian:objective max 1
bossbar set palemeridian:objective value 0
bossbar set palemeridian:objective name [{"text":"Survey Complete: ","color":"aqua"},{"text":"Free play: the Vale is yours to explore.","color":"white"}]
bossbar set palemeridian:objective players @a[scores={pm.optbar=1}]
bossbar set palemeridian:objective visible true
function palemeridian:hud/wp_hide
