# Ensure the NPC exists at the place matching the current state (no duplicates: fixed UUIDs)
execute run return run function palemeridian:npc/bench_7/_at {poi:"bench.7"}
function palemeridian:npc/bench_7/despawn
