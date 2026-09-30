# location hook: hollin_tower_top
execute if score #c1.refill pm.world >= #seconds pm.world run return fail
scoreboard players operation #c1.refill pm.world = #seconds pm.world
scoreboard players add #c1.refill pm.world 60
execute unless entity @a[predicate=palemeridian:holds_stillglass] run function palemeridian:c1/cradle_refill
