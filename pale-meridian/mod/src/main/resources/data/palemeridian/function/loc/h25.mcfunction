# location hook: glass_kiln
execute if score #c3.refill pm.world >= #seconds pm.world run return fail
scoreboard players operation #c3.refill pm.world = #seconds pm.world
scoreboard players add #c3.refill pm.world 60
function palemeridian:c3/hatch_refill
