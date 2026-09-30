# Grant the journal entries of every completed quest to this player (late joiners, rejoins)
execute if score p.letter pm.q matches 2 run advancement grant @s only palemeridian:journal/p/letter
execute if score p.camp pm.q matches 2 run advancement grant @s only palemeridian:journal/p/camp
execute if score p.lamp pm.q matches 2 run advancement grant @s only palemeridian:journal/p/lamp
execute if score p.road pm.q matches 2 run advancement grant @s only palemeridian:journal/p/road
execute if score p.chill pm.q matches 2 run advancement grant @s only palemeridian:journal/p/chill
