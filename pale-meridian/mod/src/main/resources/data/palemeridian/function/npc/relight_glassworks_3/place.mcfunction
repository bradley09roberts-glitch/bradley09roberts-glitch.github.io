# Ensure the NPC exists at the place matching the current state (no duplicates: fixed UUIDs)
execute if score #enc.glassworks pm.world matches 1 unless block 32 71 -288 #minecraft:candles[lit=true] run return run function palemeridian:npc/relight_glassworks_3/at_0
function palemeridian:npc/relight_glassworks_3/despawn
