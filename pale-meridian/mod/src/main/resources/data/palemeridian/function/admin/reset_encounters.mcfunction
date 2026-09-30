# Operator recovery: stop and reset every running encounter
function palemeridian:enc/glassworks/reset
function palemeridian:enc/hollin/reset
execute if score #boss pm.world matches 1 run function palemeridian:c4/boss/reset
tellraw @s {"text":"All encounters reset (they restart when a player re-enters them).","color":"gold"}
