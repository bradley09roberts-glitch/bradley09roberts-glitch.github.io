scoreboard players set #cur pm.world 28
bossbar set palemeridian:objective max 1
bossbar set palemeridian:objective value 0
bossbar set palemeridian:objective name [{"text":"The Great Lens: ","color":"aqua"},{"text":"Set the Lens Heart in the Great Lens atop the tower.","color":"white"}]
bossbar set palemeridian:objective players @a[scores={pm.optbar=1}]
bossbar set palemeridian:objective visible true
function palemeridian:hud/wp_at {poi:"meridian.cradle"}
