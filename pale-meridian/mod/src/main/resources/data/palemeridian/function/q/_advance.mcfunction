# Activate every auto quest whose prerequisites are complete
execute if score p.letter pm.q matches 0 run function palemeridian:q/p.letter/activate
execute if score p.camp pm.q matches 0 if score p.letter pm.q matches 2 run function palemeridian:q/p.camp/activate
execute if score p.lamp pm.q matches 0 if score p.camp pm.q matches 2 run function palemeridian:q/p.lamp/activate
execute if score p.road pm.q matches 0 if score p.lamp pm.q matches 2 run function palemeridian:q/p.road/activate
execute if score p.chill pm.q matches 0 if score p.camp pm.q matches 2 run function palemeridian:q/p.chill/activate
execute if score c1.odile pm.q matches 0 if score p.road pm.q matches 2 run function palemeridian:q/c1.odile/activate
execute if score c1.names pm.q matches 0 if score c1.odile pm.q matches 2 run function palemeridian:q/c1.names/activate
execute if score c1.round pm.q matches 0 if score c1.names pm.q matches 2 run function palemeridian:q/c1.round/activate
execute if score c1.lamp pm.q matches 0 if score c1.round pm.q matches 2 run function palemeridian:q/c1.lamp/activate
execute if score c1.surge pm.q matches 0 if score c1.lamp pm.q matches 2 run function palemeridian:q/c1.surge/activate
execute if score c1.home pm.q matches 0 if score c1.surge pm.q matches 2 run function palemeridian:q/c1.home/activate
execute if score c2.arrive pm.q matches 0 if score c1.surge pm.q matches 2 run function palemeridian:q/c2.arrive/activate
execute if score c2.brannoc pm.q matches 0 if score c2.arrive pm.q matches 2 run function palemeridian:q/c2.brannoc/activate
execute if score c2.memories pm.q matches 0 if score c2.brannoc pm.q matches 2 run function palemeridian:q/c2.memories/activate
execute if score c2.hearts pm.q matches 0 if score c2.memories pm.q matches 2 run function palemeridian:q/c2.hearts/activate
execute if score c2.lamp pm.q matches 0 if score c2.hearts pm.q matches 2 run function palemeridian:q/c2.lamp/activate
execute if score s.eleven pm.q matches 0 if score c2.lamp pm.q matches 2 run function palemeridian:q/s.eleven/activate
