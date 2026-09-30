scoreboard players set #cur pm.world 13
bossbar set palemeridian:objective max 1
bossbar set palemeridian:objective value 0
bossbar set palemeridian:objective name [{"text":"The Orchard Keeper: ","color":"aqua"},{"text":"Speak with the orchard keeper.","color":"white"}]
bossbar set palemeridian:objective players @a[scores={pm.optbar=1}]
bossbar set palemeridian:objective visible true
function palemeridian:hud/wp_at {poi:"aldercross.brannoc"}
