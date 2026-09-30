# location hook: alder_apiary
# Recovery: keep enough honeycomb available for the orchard lamp (checked at most once a minute).
execute if score #c2.refill pm.world >= #seconds pm.world run return fail
scoreboard players operation #c2.refill pm.world = #seconds pm.world
scoreboard players add #c2.refill pm.world 60
execute if items block 306 78 -80 container.* minecraft:honeycomb run return fail
execute if entity @a[predicate=palemeridian:holds_honeycomb] run return fail
item replace block 306 78 -80 container.0 with minecraft:honeycomb 8
