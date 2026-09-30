scoreboard players set #cur pm.world 1
bossbar set palemeridian:objective max 1
bossbar set palemeridian:objective value 0
bossbar set palemeridian:objective name [{"text":"A Letter at the Landing: ","color":"aqua"},{"text":"Read the letter pinned to the noticeboard.","color":"white"}]
bossbar set palemeridian:objective players @a[scores={pm.optbar=1}]
bossbar set palemeridian:objective visible true
function palemeridian:hud/wp_at {poi:"landing.noticeboard"}
