# Game 3 - Tug of War - design spec

Package names: `core/tug/`, `game/tug/` (`TugOfWarGame`, `TugNpcBehavior`), `client/game/tug/` (`TugClient`, `RopeRenderer`...),
fixture `build/placeholder/TugOfWarPlaceholder`. Game id `tug_of_war` (`GameKind.TUG_OF_WAR`, min 4 participants), arena
`ArenaId.TUG_OF_WAR` (markers `tug.slot_a/b`, `tug.rope_a/b/center`, regions `tug.edge_a/b`, `tug.pit`, `tug.pit_floor`,
`tug.waiting_a/b`, `tug.spare` and the common ones; X axis, platforms at y=40 over a pit, see `docs/ARENA_MARKERS.md`).
Entities `RopeEntity` (stub exists, synced offset/strain/length/axisYaw) with a renderer you write, textures
`entity/rope.png`, `entity/rope_flag.png` exist. Elimination causes `FELL` and `LOST_TEAM`. Port range 25620/25621.
Test: `squid debug play tug_of_war 16`.

## The game
Two teams stand single file on two high platforms and pull a rope across a deep pit. The team whose end is pulled over the
edge falls to its death (all its members are eliminated); the winning team survives. Smart pulling beats raw numbers:
**stamina, rhythm and bracing** matter.

### Team formation (`core/tug/TeamPlanner`, pure, tested)
Split the alive contestants into two **balanced** teams (sizes differ by at most 1; balance summed strength = skill + stamina +
cooperation traits; humans spread over both teams; an odd one out gets a small handicap to the smaller team). At most 32 per team
(slot markers): with more than 64 alive, run **heats** (shuffled groups of at most 64, sequential; the losing team of each heat
falls, the winners of all heats survive). Announce teams (title + chat + coloured team markers e.g. glowing armor-stand-free
approach: team name/colour in HUD and over heads via `NumbersPayload`/text displays - your choice), put each team in its
`tug.waiting_*` area in `placeContestants` then walk them onto `tug.slot_*` in `begin` (NPCs by navigation, humans teleported
with a short fade - keep it simple and robust). Contestants not in the current heat wait/watch in `tug.spare`.

### Rope simulation (`core/tug/TugSim`, pure, tested)
State: rope offset in [-1,1] (+1 = team B wins), velocity, per-member stamina (0..1), beat phase. Each member has `strength`
(trait-based), an `effort` in [0,1] (pulling), `braced` (leaning back: cheaper stamina, adds to the anchor term) and a
`heave` event (a timed burst). Team pull = sum(strength x effort x staminaFactor) x **sync bonus** (heaves inside the shared
rhythm window - a visible/audible beat every ~1.2-1.6 s, window +-3 ticks - give x1.6-2.0 for that pulse and cost extra stamina;
mistimed heaves cost stamina and give nothing). Rope acceleration = (pullB - pullA)/inertia with drag and a gentle spring
towards the centre so the rope feels heavy; exhausted members (stamina 0) can only brace until they recover. Tune so that equal
teams stay close and swing, a team that wastes stamina early loses late (comebacks possible), and a human timing heaves well is
worth several average members. Deterministic given inputs. Difficulty table: heat time limit (e.g. 100/85/70 s), NPC skill
bonus on the *opposing* team, stamina costs (`resourceScale`), window width (`toleranceScale`).
Win: offset reaches +-1 => that team wins at once. Timeout: rope on one side wins; exact tie => 10 s sudden death with
refilled stamina, then coin flip. Everything pure and unit-tested (balance, win conditions, stamina, sync, determinism).

### Human controls (client key bindings in `TugClient`, registered with Fabric's `KeyBindingHelper`; show the real bound key
names in a small client overlay and in the instructions)
* hold **Pull** (default R) to pull (drains stamina), release to recover,
* tap **Heave** (default F) on the beat for the burst,
* hold **Brace** (default Left Shift/sneak) to lean back (anchors, saves stamina).
The client sends compact state changes via `ClientActionPayload` (e.g. `tug.input`); the server validates (rate limit, phase,
membership, `ctx.now()` vs beat window with +-2 tick latency tolerance). Overlay: personal stamina bar, beat ring that pulses
on the beat, rope position indicator with team colours. The server HUD widgets carry team stamina and the rope.

### Rope entity and visuals
`RopeEntity` is moved by the server along the X axis between the platforms; extend/render the rope across both platforms (held by
all members: rope drawn from the rear-most member of team A to the rear-most of team B at hand height, sag in the gap,
vibration proportional to `strain`, a red flag/ribbon centred on the rope that shifts with the offset). Members use
`Activity.PULL_IDLE` / `PULL_STRAIN` (NPCs and AI stand-ins; humans are posed by the client-visible animation of their
tracksuit overlay model if available, otherwise just standing), heave = a one-shot pull gesture (see the animation list in
`docs/ASSET_CONTRACT.md`). Camera/HUD: `ctx.danger(...)` pulses when the own team is about to lose.

### The fall
When a team loses: announcement + flag flash, the winning team cheers (`CELEBRATE`), the losing team stumbles (one-shot
`stumble`), is dragged toward the gap and the platform edge region (`tug.edge_a/b`) drops away (remove its floor blocks with
particles/sound; restore them in `cleanup` and between heats) so the members fall into `tug.pit`; eliminate each with
`EliminationCause.FELL`/`LOST_TEAM` when they are below the platform (y < deck - 6) or after a safety timeout (never leave anyone
alive on the losing side; handle humans who stand on a barrier or cling to the edge). Guards watch from their posts.

### NPC behaviour (`TugNpcBehavior`)
Effort policy from personality: patience (conserve stamina early vs go all out), courage (keep pulling when losing/exhausted),
cooperation + reactionSpeed (probability and accuracy of heaving on the beat), aggression (early burst), skill/stamina trait;
they read only public information (rope position, beat, own stamina, teammates' visible strain) and show it with animations;
panic/rally reactions when the rope swings; they rest (brace) when the rope is stable. Performance: O(1) per NPC per tick.

### Edge cases
Odd contestant counts, a team made only of NPCs, all humans on one team (still fair), a human disconnecting mid-match (stand-in
continues), tie at the timeout, 4 contestants (2 v 2), 64+ contestants (heats), contestant removed by the tournament mid-match
(admin/disconnect) - the sim must drop them without crashing.
