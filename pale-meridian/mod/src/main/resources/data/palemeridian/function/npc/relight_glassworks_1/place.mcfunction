# Ensure the NPC exists at the place matching the current state (no duplicates: fixed UUIDs)
execute if score #enc.glassworks pm.world matches 1 unless block 32 71 -304 #minecraft:candles[lit=true] run return run function palemeridian:npc/relight_glassworks_1/_at {poi:"surge.glassworks.relight1"}
function palemeridian:npc/relight_glassworks_1/despawn
