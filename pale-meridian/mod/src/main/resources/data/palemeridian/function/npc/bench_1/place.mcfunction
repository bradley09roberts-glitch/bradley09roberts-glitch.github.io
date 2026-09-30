# Ensure the NPC exists at the place matching the current state (no duplicates: fixed UUIDs)
execute run return run function palemeridian:npc/bench_1/_at {poi:"bench.1"}
function palemeridian:npc/bench_1/despawn
