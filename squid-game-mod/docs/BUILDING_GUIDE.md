# Building guide for arena builders

You are writing a **pure-Java structure builder** (no Minecraft classes) in `src/main/java/com/squidgame/build/arena/`.
It draws into a virtual `BlockBuffer` through the `BuildContext` DSL; the mod later copies the buffer into the
world in time slices. You can iterate in seconds without launching Minecraft:

```bash
tools/dump.sh red_light                         # compile just your builder + build/core, run it, validate markers
python3 tools/preview.py tools/out/red_light.sqbuf --views top,iso          # whole structure (top map + 3D view)
python3 tools/preview.py tools/out/red_light.sqbuf --views iso --clip=-30,30,0,20,0,40 --scale 5   # zoom (LOCAL coords)
python3 tools/preview.py tools/out/red_light.sqbuf --views slice --slice-y 41 --clip=-8,8,0,0,-4,60 # one storey, floor plan
python3 tools/preview.py tools/out/red_light.sqbuf --views front,side       # orthographic elevations
python3 tools/check_markers.py tools/out/red_light.sqbuf                    # standing markers must have floor below + 2 free blocks
```
View the PNGs with the Read tool (it displays images). `--clip=x0,x1,y0,y1,z0,z1` (use the `=` form; local
coordinates; `y0=y1=0` = all heights) lets you cut a building open: clip y1 below the roof to see inside.
Look at your work constantly: build in layers (shell, floor, structure, detail, lighting), render after each,
fix proportions. Use `iso` for the 3D impression, `slice` for plans, `top` for layout, `front`/`side` for elevations.

Files: `build/arena/<Name>Builder.java` implements `ArenaBuilder` (public no-arg constructor; the registry finds it by
name); helpers go in your package `build/arena/<pkg>/`; shared prefabs in `build/arena/prefab/` (use, don't edit,
unless you own it). Do not touch other agents' files. See `docs/ARENA_MARKERS.md` for the marker contract.

## The DSL (`BuildContext c`)

All coordinates are local (arena origin = 0,0,0; floor y = 0; you may use negative y for pits, down to -120, and up
to +250). Block arguments are state strings: `"minecraft:stone_bricks"`,
`"minecraft:oak_stairs[facing=north,half=bottom,shape=straight]"`, `"squidgame:pastel_pink"`.

* `set(x,y,z,state)` `setIfFree` `get` `isSolid` `air(x,y,z)`
* `fill(x1,y1,z1,x2,y2,z2,state)` inclusive cuboid (fast, use it generously) `clear(...)` (air) `shell(...)` (6 faces)
  `walls(...)` (4 vertical walls) `room(x1,y1,z1,x2,y2,z2,floor,wall,ceiling)` (carved room) `fillWhere(..., predicate, state)`
* `line(x1,y1,z1,x2,y2,z2,state)` `disc(cx,y,cz,r,state)` `ring(...)` `cylinder(cx,y1,y2,cz,r,state)`
  `hollowCylinder` `sphere(cx,cy,cz,r,state,hollow)` `ellipsoid(cx,cy,cz,rx,ry,rz,state,hollow)`
* `pattern(x1,y1,z1,x2,y2,z2,(x,y,z)->state|null)` function fill, `noise(box, states[], weights[])` weighted random
  (weathering), `scatter(box, state, density)`, `checker(x1,y,z1,x2,z2,a,b)`
* `at(dx,dy,dz,rotSteps,() -> {...})` runs the body in a translated + rotated frame (rotSteps quarter turns clockwise
  from above; stairs/axes/facing rotate with it) - write a prefab once (bunk bed, house, lamp post, bench) and place it
  many times. Nest freely. `c.worldX/Y/Z` convert if you need absolute numbers.
* `marker(name,x,y,z,yaw[,data])` (x/z block centre `.5`, y = standing floor level, i.e. top of the floor block) and
  `region(name,x1,y1,z1,x2,y2,z2)`; `markerOn(name,bx,by,bz,yaw)` stands on top of block (bx,by,bz).
* `text(x,y,z,"TEXT","#RRGGBB"|"white",scale,yaw,background)` signage (a text display entity, 1 line; avoid apostrophes);
  `entity(type,x,y,z,yaw,snbt)` for other displays (e.g. item displays).
* `rng()` deterministic seeded RNG; the structure must be deterministic.

## Blocks you can use

Any vanilla block id. Custom blocks (textures exist): `squidgame:tile_pink|tile_white|tile_black` (glossy corridor tiles),
`pastel_<pink|mint|yellow|sky|lilac|peach|cream>` (+ `_stairs` / `_slab`, full stair/slab state support),
`panel_light_white|warm|pink` (light 15), `playground_ground`, `cash_block`, `monitor[facing=...]` (animated screen),
`registration_terminal[facing=...]`, `dalgona_station[facing=...]`, `symbol_circle|triangle|square`, `bridge_glass`,
`invisible_wall` (collision only; used for barriers/boundaries - never visible). Stairs/slabs/walls/fences/panes get
their connections fixed automatically after placement. Light: `minecraft:light[level=15]` is invisible light; prefer visible
fixtures (sea lanterns, shroomlights, glowstone, lanterns, panel_light_*, end rods).
Tinted/transparent: stained glass (and panes), `minecraft:iron_bars`, `chain`, `ladder`, `scaffolding`, `trapdoors`
(good for fine detail), `minecraft:item_frame` is not supported (entities via `entity()` only).

## Quality bar (this is a showcase)

* **Recognisable and accurate** to the brief: proportions, palette, landmark features. Look at it from the player's
  eye height too: `--clip` + `iso` at scale 6 on the important spots.
* **Deliberate block palettes** (3-5 main blocks + accents), variation by `noise`/`pattern` (no flat single-block
  slabs), trims, pilasters, beams, recessed panels, stairs/slabs for relief, banding, symmetric details where the real
  thing is symmetric. Add props: benches, lamps, cameras (black blocks), cables (chains), speakers, signs.
* **Lighting**: every walkable area is bright (>= level 10 everywhere you can walk) using visible fixtures; the world has
  no ambient light inside buildings. Outdoor arenas are lit by the dimension's fixed noon sun, so make sure roofs / overhangs
  have lamps.
* **Walkability** (NPCs path with vanilla navigation, humans walk too): floors continuous and flat (no 1-wide ledges
  on routes; steps <= 1 block or use stairs/slabs), doorways >= 3 wide x 4 high, no cobweb/slime/fences in routes,
  nothing blocking 2 blocks above marker positions. Keep `waiting.spawn`-style grids 1.2+ apart.
* **Safety**: arenas must be enclosed so nobody can fall out of the world or leave (walls, or `invisible_wall` fences
  at the edges of open areas and along cliffs at the arena.bounds). The pits of the tug-of-war / bridge are open on top
  by design but enclosed all around.
* **Size/performance**: aim for < 6 million written cells and < 10 seconds in `tools/dump.sh`. Use `fill` for big volumes,
  avoid per-block `set` calls inside nested loops over millions of cells (a few hundred thousand is fine). Every arena
  must fit inside its 1000x1000 region (centre = origin; stay within +-450 of it).

## Markers and verification

Emit every required marker from `docs/ARENA_MARKERS.md` (your builder's `requiredMarkers()` / `requiredRegions()` must
list them so `tools/dump.sh` fails loudly when something is missing). Run `tools/check_markers.py` and fix every invalid
standing marker. Finish by rendering your final structure from several angles and describing what you verified.

Do NOT: run git commands that change state, run Gradle, or edit files outside your own builder files. Other agents
are editing other builders at the same time; compile errors in their files are not yours (the dump script compiles only
your sources + shared packages).
