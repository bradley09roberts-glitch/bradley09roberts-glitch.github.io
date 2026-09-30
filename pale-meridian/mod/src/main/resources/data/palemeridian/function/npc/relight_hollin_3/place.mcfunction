# Ensure the NPC exists at the place matching the current state (no duplicates: fixed UUIDs)
execute if score #enc.hollin pm.world matches 1 unless block 136 70 126 #palemeridian:bulbs[lit=true] run return run function palemeridian:npc/relight_hollin_3/_at {poi:"surge.hollin.relight3"}
function palemeridian:npc/relight_hollin_3/despawn
