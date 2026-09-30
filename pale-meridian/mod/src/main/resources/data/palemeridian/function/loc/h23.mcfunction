# location hook: glass_office
execute if score #c3.theo pm.world >= #seconds pm.world run return fail
scoreboard players operation #c3.theo pm.world = #seconds pm.world
scoreboard players add #c3.theo pm.world 60
execute if items block 31 70 -316 container.* *[minecraft:custom_data~{pm:{item:"theodolite"}}] run return fail
execute if entity @a[predicate=palemeridian:holds_theodolite] run return fail
item replace block 31 70 -316 container.13 with minecraft:spyglass[minecraft:custom_name={"text":"Tamsin's Theodolite","italic":false,"color":"gold"},minecraft:custom_data={pm:{item:"theodolite"}}] 1
