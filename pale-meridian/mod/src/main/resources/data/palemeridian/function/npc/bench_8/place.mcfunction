# Ensure the NPC exists at the place matching the current state (no duplicates: fixed UUIDs)
execute run return run function palemeridian:npc/bench_8/_at {poi:"bench.8"}
function palemeridian:npc/bench_8/despawn
