scoreboard players set #cur pm.world 30
bossbar set palemeridian:objective max 1
bossbar set palemeridian:objective value 0
bossbar set palemeridian:objective name [{"text":"The Blank Space: ","color":"aqua"},{"text":"Decide what the Long Chart says about the Deepcut.","color":"white"}]
bossbar set palemeridian:objective players @a[scores={pm.optbar=1}]
bossbar set palemeridian:objective visible true
function palemeridian:hud/wp_at {poi:"meridian.chart_table"}
