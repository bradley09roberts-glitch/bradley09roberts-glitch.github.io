# The Great Lens: choose the conversation for the current state
execute if score c4.lens pm.q matches 1 run return run function palemeridian:dlg/show/prop/cradle/set
execute if score c4.unlooked pm.q matches 2 run return run function palemeridian:dlg/show/prop/cradle/burning
execute if score c4.lens pm.q matches 2 run return run function palemeridian:dlg/show/prop/cradle/waiting
execute run return run function palemeridian:dlg/show/prop/cradle/cold
