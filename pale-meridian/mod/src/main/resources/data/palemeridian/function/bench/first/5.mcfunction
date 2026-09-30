scoreboard players set #bench.5 pm.world 1
scoreboard players add #bench.count pm.world 1
function palemeridian:q/s.bench/activate
scoreboard players operation s.bench pm.qp = #bench.count pm.world
playsound minecraft:item.book.page_turn master @a ~ ~ ~ 0.8 1.2
tellraw @a [{"text":"Benchmark found ","color":"gold"},{"score":{"name":"#bench.count","objective":"pm.world"},"color":"gold"},{"text":"/8","color":"gold"}]
execute if score #bench.count pm.world matches 8.. run function palemeridian:q/s.bench/complete
