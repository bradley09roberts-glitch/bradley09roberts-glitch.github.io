# Ensure the NPC exists at the place matching the current state (no duplicates: fixed UUIDs)
execute run return run function palemeridian:npc/bench_2/_at {poi:"bench.2"}
function palemeridian:npc/bench_2/despawn
