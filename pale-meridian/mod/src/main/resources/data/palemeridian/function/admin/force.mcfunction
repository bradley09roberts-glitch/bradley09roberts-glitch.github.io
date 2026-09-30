# Operator recovery: /function palemeridian:admin/force {q:"c1.round"} marks a stuck quest done and runs its rewards
$tellraw @s {"text":"Forcing quest $(q) (activate, then complete).","color":"gold"}
$scoreboard players set $(q) pm.q 1
$function palemeridian:q/$(q)/complete
