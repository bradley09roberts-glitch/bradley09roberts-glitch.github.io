scoreboard players set #cur pm.world 32
bossbar set palemeridian:objective max 1
bossbar set palemeridian:objective value 0
bossbar set palemeridian:objective name [{"text":"The Broken Span: ","color":"aqua"},{"text":"Mend the gap in the Meridian causeway.","color":"white"}]
bossbar set palemeridian:objective players @a[scores={pm.optbar=1}]
bossbar set palemeridian:objective visible true
function palemeridian:hud/wp_at {poi:"meridian.causeway_gap"}
