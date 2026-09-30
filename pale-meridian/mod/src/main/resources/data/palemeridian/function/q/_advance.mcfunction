# Activate every auto quest whose prerequisites are complete
execute if score p.letter pm.q matches 0 run function palemeridian:q/p.letter/activate
execute if score p.camp pm.q matches 0 if score p.letter pm.q matches 2 run function palemeridian:q/p.camp/activate
execute if score p.lamp pm.q matches 0 if score p.camp pm.q matches 2 run function palemeridian:q/p.lamp/activate
execute if score p.road pm.q matches 0 if score p.lamp pm.q matches 2 run function palemeridian:q/p.road/activate
execute if score p.chill pm.q matches 0 if score p.camp pm.q matches 2 run function palemeridian:q/p.chill/activate
