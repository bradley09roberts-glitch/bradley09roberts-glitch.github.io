# Ensure the NPC exists at the place matching the current state (no duplicates: fixed UUIDs)
execute if score #enc.glassworks pm.world matches 1 unless block 16 71 -288 #minecraft:candles[lit=true] run return run function palemeridian:npc/relight_glassworks_2/at_0
function palemeridian:npc/relight_glassworks_2/despawn
