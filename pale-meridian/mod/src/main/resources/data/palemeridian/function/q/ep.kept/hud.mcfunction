scoreboard players set #cur pm.world 34
bossbar set palemeridian:objective max 1
bossbar set palemeridian:objective value 0
bossbar set palemeridian:objective name [{"text":"Kept Safe: ","color":"aqua"},{"text":"You gave the keepsakes to the Keeper.","color":"white"}]
bossbar set palemeridian:objective players @a[scores={pm.optbar=1}]
bossbar set palemeridian:objective visible true
function palemeridian:hud/wp_hide
