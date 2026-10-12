# Grand Line Odyssey — Design Record

Original One Piece RPG mod for Minecraft Java Edition. This file records every agreed decision
(from the upfront questionnaire) and every default I chose for questions left open.

## Technical setup (adopted)

| Item | Choice |
|---|---|
| Minecraft | Java Edition **1.21.1** |
| Loader | **NeoForge 21.1.259** (accepts ≥ 21.1.200) |
| Toolchain | ModDevGradle 2.0.148, Gradle 8.14.3, Java 21, Parchment 2024.11.17 |
| Required library | **GeckoLib 4.9.3** (non-humanoid animated models: Zoan forms, beasts, Sea Kings) |
| Soft-compatible | JEI 19.57 / EMI 1.1.24, Sodium 0.8.13, Iris 1.8.14-beta, Distant Horizons 3.3.3, Curios 9.5.1 |
| Alternative considered | MC 26.1.2 + NeoForge 26.1.2.116 (Java 25, unobfuscated) — rejected for tonight due to API churn and missing ecosystem pieces |

Mod id `grandline`, title **Grand Line Odyssey**, private distribution (canon names used freely).

## Agreed answers (questionnaire)

### Project
1. Mod lives in `grandline-mod/` inside `bradley09roberts-glitch.github.io`, branch `claude/friendly-darwin-gv73pq`.
2. No existing work to preserve. Start fresh.
3. Private distribution, canon names used.
4. Art: **mix of code-authored assets and Higgsfield AI** (concept art, painted textures such as posters/maps/GUI art). Models and animations are code-authored (Blockbench/Bedrock-format JSON for GeckoLib; vanilla model parts + own keyframe system for humanoids).
5. Version: my research pick (above).
6. Libraries: a few maintained ones (GeckoLib; soft-compat with JEI/EMI etc.). All gameplay is original code.
7. Multiplayer: single-player, LAN and dedicated servers, server-authoritative, sized for ~20 players.
8. Hardware: user has RTX 5090 / 32 GB / 7800X3D → **high-end visual target** (dense islands, more effects); servers still kept efficient.

### World
9. Custom world preset **replaces the Overworld** (`level-type=grandline:ocean_world` on servers). Nether/End remain optional.
10. Island roster: **massive — 100–200+ islands**. All canon islands (everything published to date) at fixed positions + 100–200+ original generated islands. Lots of new places, people, fruits, powers and races.
11. Cutoff: **everything published so far, plus our own additions.**
12. Scale: **Vast** — islands 2,000–5,000+ blocks apart.
13. Generation: canon islands fixed and hand-designed on the canon map layout; original islands generated from themed templates with towns/ruins/bases/bosses/secrets; count configurable.
14. Sky islands and deep sea are **their own dimensions** (Knock Up Stream → sky sea; coated ship → deep sea).
15. Navigation: canon (Log Pose, Eternal Pose, Vivre Cards, self-filling charts, weather, currents, Calm Belt, Sea Kings, Red Line crossing) + earned speed (Coup de Burst, paddles, Sea Train, ferries within a sea). No teleport between seas.
16. Interiors: all key buildings and most houses enterable; dungeon-style story interiors.

### Survival and progression
17. Full survival kept + RPG on top; towns protected (configurable).
18. Vanilla hostile mobs replaced in the ocean world; passives kept; toggle.
19. Character level + per-weapon/style/Haki/fruit mastery + stat points + Beli/shops/bounty payouts.
20. **Permadeath**: "if you die once you die, end of." Implementation agreed: **Final death + knockdown** — defeat knocks you down (crew/allies revive within ~30 s; Marines may capture you → prison escape). True death erases the character; you create a new one in the same world; your fruit regrows elsewhere; your gear stays in a grave/wreck.

### Characters
21. Races: 13 canon (10 playable: Human, Fish-Man, Merfolk, Mink, Sky People, Long-Arm, Long-Leg, Three-Eye, Tontatta, Giant; 3 NPC/rare: Lunarian, Buccaneer, Snakeneck) + 8 original races.
22. Body sizes: scaled giants — playable 0.4×–2.0×; full giants are NPCs on giant-scale Elbaph; 3-block doors, crawl mode, large ship seats.
23. Appearance: player's own skin + recolorable race parts, height/build sliders, hair/eye colours, scars/tattoos, cosmetic outfits, barber.
24. Start: choose home Blue (original starter island in each) + path (Pirate, Marine, Revolutionary, Bounty Hunter, Civilian); defection later.

### Combat
25. Combat Mode toggle (R): keys 1–9 become ability slots, scroll switches weapon/style/fruit/Haki sets, Left Alt dodge, hold right-click block/parry; rebindable.
26. Camera: both first- and third-person fully; over-the-shoulder + soft lock-on in Combat Mode.
27. Combat feel: action combos (light/heavy chains, timing windows, stamina, perfect parry, i-frame dodge, stagger, hit reactions, size-scaled knockback).
28. Weapons: full canon roster (~25 types) + named swords (4 grades + Black Blades, unique per world) + original weapons.
29. Styles: full canon roster (17) + original styles.
30. PvE-first; PvP ×0.6 damage, opt-in (flag / contested waters); all numbers configurable.

### Haki
31. Conqueror's: rare birth trait (10 % roll, configurable), awakened by story event; each new character rolls fresh.
32. Full advanced Haki (future sight, emission, internal destruction, infusion, clashes), trained by use/meditation/master trials; NPCs use Haki.

### Devil Fruits
33. Roster: ~38 canon + ~40 original, added in waves.
34. Canon rules: one of each fruit per world; regrows when the user dies; second fruit kills (after warning); water waist-deep weakens and disables, seastone cancels, Haki hits Logia, users sink; found in the world, never crafted.
35. Mastery + trials for awakening; Zoan hybrid/full/awakened forms with custom models; Paramecia awakening reshapes surroundings; Logia awakening widens area.
36. Terrain changes only in the wild, revert over time, towns immune, configurable.

### Ships
37. Ready-made, upgradable ship classes (rowboat → flagship) built at shipwrights; walkable deck while moving; below-deck interior via hatch; no physics mod.
38. Full crew + naval combat (role bonuses, shared player crews, aimed/crew cannons, grappling boarding, ramming, sectional hull damage, repairs; sunk ships rebuildable for a fee).

### Story
39. Original era: same world/history/legends, no canon characters shown or named; all titles held by original characters.
40. Tone like the source; four-part original saga (Blues → Paradise → the great war → New World), faction branching, arc per major island.
41. NPCs: authored + reactive dialogue, schedules, patrols, fleeing, surrender, reinforcements; NPCs use weapons/styles/Haki/fruits; distant world simulated as data; offline.
42. Bounties/reputation: full system with wanted posters of your character; per-faction and per-island reputation; 12–16+ recruitable original crew with personal quests.

### Execution
43. Build order: core/saving → ocean world + first islands → character creation/races → combat → Haki → fruits → ships → smart enemies + first arc → expand.
44. Overnight window: ~16–17 h (≈ 01:00 → 17:00–18:00 user time). Checkpoints pushed at every milestone.

## Defaults I chose (recorded assumptions)

- Built JARs are committed to `grandline-mod/dist/`.
- World layout (flat projection of the canon globe, x = east, z = south):
  - Red Line A along x ≈ 0 with **Reverse Mountain** at (0, 0).
  - **Paradise** runs east along the Grand Line band (|z| < 4,000) from x = 0 to x ≈ 60,000.
  - **Red Line B / Mary Geoise** at x ≈ 60,000. Crossing goes via Sabaody → Fish-Man Island (deep-sea dimension) → exits into the New World.
  - **New World** occupies the Grand Line band from x ≈ −60,000 to 0, sailing east back toward Reverse Mountain, with Laugh Tale near its end (the Grand Line is a ring; the flat map cuts it at Red Line B).
  - **Calm Belts**: 4,000 < |z| < 6,500 on both sides.
  - **Four Blues** beyond the Calm Belts: North (x>0, z<0), East (x>0, z>0), South (x<0, z>0), West (x<0, z<0).
  - World border ±64,000 (configurable).
- Character death with Minecraft "keepInventory" is irrelevant: inventory drops into a grave chest at the death site; the character record is archived and a fresh character must be created.
- Knockdown window 30 s; a knocked-down player can be revived by a crewmate or ally; NPC Marines with arrest capability may capture instead (→ Impel Down/Marine-base cell escape).
- Humanoid NPCs and players share one rig (vanilla humanoid model parts) and one keyframe animation system; non-humanoid creatures use GeckoLib.
- Dialogue/quests are data-driven JSON in `data/grandline/quests` and `data/grandline/dialogue`.
