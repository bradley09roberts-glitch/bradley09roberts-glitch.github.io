scoreboard players set #cur pm.world 22
bossbar set palemeridian:objective max 1
bossbar set palemeridian:objective value 0
bossbar set palemeridian:objective name [{"text":"The Last Gallery: ","color":"aqua"},{"text":"Go past the blue seam to where the Deepcut fell.","color":"white"}]
bossbar set palemeridian:objective players @a[scores={pm.optbar=1}]
bossbar set palemeridian:objective visible true
function palemeridian:hud/wp_at {poi:"deepcut.memorial_wall"}
