# Original Devil Fruits — design notes

Grand Line Odyssey's own fruits (`canon=false` in `fruit/DevilFruits.java`). Each follows the official rules
(`fruit/FruitRules.java`) on top of its own design:

- **Every user** is drained by the sea (waist-deep water) and by seastone, cannot swim, and dies if they eat a second fruit.
- **Logia** bodies let physical attacks pass through — but Armament Haki, seastone, Water-element attacks and a
  staggered (off-balance) body always connect. Each Logia below adds its own extra rule.
- **Element immunity**: a fruit's own non-physical element cannot hurt its user (e.g. Kiri ignores Water, Tenki
  ignores Lightning, Kabi/Kusuri/Kaeru ignore Poison).
- **Terrain** changes are always temporary (`fruit/TerrainChanges`), revert on their own and never touch towns.
- **Unlocks** are fruit-mastery levels; *Awakening* abilities also need an awakened fruit (NPCs: tier 9+).
- All abilities work for NPC users (they aim at their combat target) and have client effects in the
  "Devil Fruits wave 3 — ORIGINAL fruits" block of `client/ClientFx.java`.

Implementation: `fruit/impl/<Name>Fruit.java`; shared helpers in `OriginalKit`, damage-reactive rules (sleep,
chain echo, oil + fire) in `OriginalFruitEvents`. Text: `src/main/lang/en_us/fruits_wave3_original.json`.

| Fruit | Type | Element | Tier | Signature |
|---|---|---|---|---|
| Kaze Kaze no Mi | Logia | Wind | 7 | wind body, ship tailwind |
| Shio Shio no Mi | Paramecia | Physical | 5 | salt vs. sea-folk, salt flats on the sea |
| Kagami Kagami no Mi | Paramecia | Light | 7 | reflection |
| Uzu Uzu no Mi | Paramecia | Water | 6 | whirlpools that drag boats under |
| Kane Kane no Mi | Paramecia | Sound | 5 | ringing stacks + Resonance |
| Tako Tako no Mi, Model: Kraken | Mythical Zoan (`kraken`) | Blunt | 8 | crushes ship hulls |
| Rasen Rasen no Mi | Paramecia | Impact | 6 | twisting bodies, spiral staircases |
| Tsuchi Tsuchi no Mi | Logia | Physical | 6 | intangible only while grounded |
| Kiri Kiri no Mi | Logia | Water | 5 | crew-wide stealth, drowning mist |
| Tenbin Tenbin no Mi | Paramecia | Physical | 6 | balance HP / ailments, judge by bounty |
| Hari Hari no Mi | Paramecia | Pierce | 4 | needles, traps, acupuncture |
| Hoshi Hoshi no Mi | Logia | Light | 9 | gravity wells, starfall, night power |
| Mitsu Mitsu no Mi | Logia | Physical | 6 | honey traps, lure, royal jelly |
| Kabi Kabi no Mi | Logia | Poison | 6 | mould: rot (incl. ship hulls), sentries |
| Funa Funa no Mi | Paramecia | Blunt | 5 | living ships, hull repair, gangplanks |
| Tenki Tenki no Mi | Paramecia | Lightning | 8 | weather cells, changes the world's sky |
| Kusuri Kusuri no Mi | Paramecia | Poison | 5 | medicine and poison, revives |
| Wana Wana no Mi | Paramecia | Pierce | 4 | armed hidden traps |
| Kusari Kusari no Mi | Paramecia | Blunt | 4 | chain-linked enemies share damage |
| Nemu Nemu no Mi | Paramecia | Sound | 6 | real sleep (helpless until hurt) |
| Nendo Nendo no Mi | Special Paramecia | Blunt | 5 | clay soldiers and colossus (summons) |
| Abura Abura no Mi | Special Paramecia | Physical | 4 | oil: any fire +75%, burning ships |
| Same Same no Mi, Model: Megalodon | Ancient Zoan (`megalodon`) | Slash | 6 | ground-swimming, hull breaking |
| Kame Kame no Mi, Model: Genbu | Mythical Zoan (`genbu`) | Water | 8 | shell fortress, crew ward |
| Kaeru Kaeru no Mi, Model: Poison Dart Frog | Zoan (`dart_frog`) | Poison | 3 | toxic skin, tongue, leaps |

---

## Earlier originals (waves 1–2)

### Kaze Kaze no Mi — Gale-Gale Fruit (Logia, Wind)
**Concept:** the user becomes the wind — air blades, updrafts, a pulling cyclone, and the power to fill a ship's sails.
- 0 **Gale Blade** — piercing blade of wind.
- 5 **Updraft** — launch yourself (slow fall) and toss nearby enemies into the air.
- 10 **Tailwind** — for a minute the ship you stand on sails at 125% (works even in the Calm Belt); you run faster.
- 20 **Cyclone** — a cyclone at a point pulls enemies in and grinds them.
- 45 **Tempest Spear** — a huge exploding spear of compressed wind.

**Passive:** no fall damage; you drift down slowly when falling fast. **Weaknesses:** standard Logia (Haki, seastone, water, the sea).

### Shio Shio no Mi — Salt-Salt Fruit (Paramecia)
**Concept:** create and control salt — the bane of the sea's children.
- 0 **Salt Shards** — three dehydrating shards. 5 **Crystal Spikes** — a line of salt crystals bursts from the ground.
- 10 **Desiccate** — dries the target out over time. 15 **Salt Wall** — a wall that crystallises even out of sea water.
- 25 **Salt Flats** — ground and sea surface turn to salt (standing ground at sea); enemies on it dry out.
- 50 **Pillar of Salt** *(Awakening)* — seals enemies inside salt.

**Passive:** +50% damage against Fish-Men, Merfolk, Whalefolk, water-dwellers and Water-element fruit users. **Weaknesses:** standard.

### Kagami Kagami no Mi — Mirror-Mirror Fruit (Paramecia, Light)
**Concept:** make and step through mirrors; reflection is the fruit's core.
- 0 **Mirror Shard** (blinds). 5 **Mirror Step** — step out of the reflection behind the target.
- 10 **Mirror Wall** — projectiles are turned back; ranged hits are reflected as a mirror beam.
- 20 **Decoy Flash** — blinding flash, enemies lose you, you slip aside invisible.
- 35 **Kaleidoscope** — a prism fan of five light rays. 55 **Hall of Mirrors** *(Awakening)* — a dome of mirrors.

**Passive:** immune to light; with the Mirror Wall up, arriving projectiles are reflected. **Weaknesses:** standard; close-range blows are only softened.

### Uzu Uzu no Mi — Whirl-Whirl Fruit (Paramecia, Water)
**Concept:** spin anything into a vortex. Near water the whirls are real sea water (reaching Logia users); on land, whirlwinds.
- 0 **Vortex**. 5 **Spiral Drill** (bores through soft blocks). 10 **Riptide Dash**. 20 **Tidal Spiral**.
- 35 **Maelstrom** — at sea drags enemies and boats under. 60 **Charybdis** *(Awakening)* — colossal maelstrom / spiral trench.

**Passive:** whirling body — close attackers are spun aside, their blows deflected (-15%). **Weaknesses:** standard.

### Kane Kane no Mi — Bell-Bell Fruit (Paramecia, Sound)
**Concept:** the body rings like a great bell; every hit leaves the target *ringing* (up to 5 stacks).
- 0 **Peal**. 5 **Toll** (stun). 10 **Sonic Peal** (sound beam). 20 **Resonance** — detonates ringing through any guard.
- 30 **Bell Jar** — untouchable for a moment, then a crushing toll. 55 **Great Bell of Doom** *(Awakening)*.

**Passive:** close physical attackers are left ringing and dizzy; immune to sound. **Weaknesses:** standard.

---

## Wave 3 — roster entries implemented

### Tako Tako no Mi, Model: Kraken — Octo-Octo Fruit (Mythical Zoan, Blunt, model `kraken`)
**Concept:** the sea monster of sailors' nightmares — eight arms, ink, and the strength to break a warship. Like every
fruit user it still sinks: its terror is reaching *out of* the water.
- 0 **Transform** (hybrid → full → awakened).
- 0 **Tentacle Lash** — 170° sweep; struck enemies are dragged in.
- 5 **Ink Jet** — blinding ink cone (enemies lose track); the jet throws you backwards.
- 15 **Constrict** — hold one target in place and squeeze for 3 s (unblockable).
- 25 **Ship Wrecker** — crush the nearest enemy ship's hull (8 pulses) and shake its crew; with no ship, haul the target up and slam it down (crater).
- 55 **Abyssal Leviathan** *(Awakening)* — ring of colossal arms: everything inside is dragged to the centre (under, at sea), ships in it crushed, final slam.

**Passive:** transformed, blunt/impact damage -25%; below half health lost flesh regrows (1.5 HP / 2 s).
**Weaknesses:** standard Zoan (sea, seastone); abilities need the transformed body.

### Rasen Rasen no Mi — Spiral-Spiral Fruit (Paramecia, Impact)
**Concept:** whatever the user touches can be made to spiral — unlike Uzu (which pulls things in), Rasen twists the thing itself.
- 0 **Corkscrew** — boring punch along a line, ignores guard.
- 5 **Coil Spring** — spring-legged launch; the landing sends a shockwave.
- 12 **Twist** — wrench the target around: it faces the wrong way, dizzy, slowed, loses its target.
- 20 **Spiral Staircase** — a temporary helix of steps rises around you (climb cliffs, take high ground); foes at its foot are flung up.
- 30 **Screw Lance** — piercing, block-drilling lance that twists what it hits.
- 55 **Helix Ascension** *(Awakening)* — enemies are carried up a rising helix, then slammed down.

**Passive:** spring-coiled body — no fall damage; immune to Impact (own element), blunt -15%. **Weaknesses:** standard.

### Tsuchi Tsuchi no Mi — Soil-Soil Fruit (Logia)
**Concept:** a body of earth that burrows, walls off and buries.
- 0 **Clod Shot**. 5 **Earth Wall** (pushes enemies back). 12 **Landslide** (rolling wave; ground becomes coarse dirt).
- 20 **Burrow** — sink into the earth (invisible, i-frames) and erupt under the target with an uppercut.
- 30 **Earth Prison** — encased in packed earth for 5 s.
- 55 **Tectonic Upheaval** *(Awakening)* — earth pillars erupt across the area, launching enemies.

**Passive / rules:** intangible **only while touching the ground** — knock a Tsuchi user into the air and they can be hit;
earth grounds lightning (immune). **Weaknesses:** standard Logia + water turns the body to mud (+25%, solid while wet).

### Kiri Kiri no Mi — Fog-Fog Fruit (Logia, Water)
**Concept:** sea mist — stealth for a whole crew and real water that reaches other Logia.
- 0 **Mist Lance** — water jet (hits Logia bodies, puts out fire).
- 5 **Vanishing Mist** — blind nearby enemies, become invisible and drift back.
- 15 **Fog Bank** — area fog: enemies blinded/slowed/lose track, allies inside invisible.
- 20 **Drowning Mist** — mist condenses in the lungs: Water damage over 3 s, air supply drained (water-breathers only chilled).
- 30 **Sea Fog** — long-lasting fog that follows you: the crew (on deck or ashore) vanishes, enemies left in darkness.
- 55 **White Abyss** *(Awakening)* — freezing downpour, then everything inside freezes solid.

**Passive:** crouch still to fade into mist (invisible); water cannot hurt the mist. **Weaknesses:** wind scatters mist — Wind attacks always connect and deal +50%; standard sea/seastone/Haki.

### Tenbin Tenbin no Mi — Scale-Scale Fruit (Paramecia)
**Concept:** weigh and balance — heaviness, lightness, equal health, transferred burdens and judgement by crimes.
- 0 **Weigh Down** — heavy slow, pulled out of the air.
- 5 **Featherweight** — you and allies: speed, jump boost, slow falling.
- 15 **Equilibrium** — balance your health % with the target's; the healthier one pays (capped).
- 20 **Balance of Burdens** — your ailments (and fire) move onto the target; its blessings move to you.
- 30 **Scales of Judgement** — damage/stun scale with the target's *crime weight*: player bounty, NPC faction (pirates/bandits heavy, civilians light), notoriety and tier.
- 55 **Absolute Balance** *(Awakening)* — field: enemies crushed (2% max HP per pulse), allies lightened; at the end all enemies are levelled to the weakest one's health %.

**Passive:** no single blow tips the scales — damage above 20% of max health in one hit is halved. **Weaknesses:** standard.

### Hari Hari no Mi — Needle-Needle Fruit (Paramecia, Pierce)
**Concept:** needles as weapons, traps and medicine.
- 0 **Needle Volley** — fan of five. 5 **Pincushion** — bristle (close attackers skewered, -20% damage).
- 12 **Pressure Point** — locks the target up (stun, guard broken).
- 20 **Caltrops** — needle trap on the ground for ~10 s (prick, slow, bleed).
- 30 **Acupuncture** — heal + cleanse knots + regeneration and strength for you and allies.
- 50 **Thousand Needle Rain** *(Awakening)* — needles rain over an area.

**Passive:** needle skin — close attackers are always pricked (harder while bristling). **Weaknesses:** standard.

### Hoshi Hoshi no Mi — Star-Star Fruit (Logia, Light)
**Concept:** starlight and star-stuff — gravity and light.
- 0 **Shooting Star**. 8 **Gravity Well** — pulls enemies *and their projectiles* in, then bursts.
- 12 **Comet Dash**. 25 **Starfall** — 12 falling stars with small craters.
- 35 **Constellation** — up to six enemies marked, struck by lines of light and dragged together.
- 60 **Supernova** *(Awakening)* — i-frames, gravitational pull, blinding blast and crater.

**Passive:** under the open night sky +20% damage and slow regeneration. **Weaknesses:** standard Logia + darkness swallows starlight (Darkness always connects, +25%).

---

## Wave 3 — new originals

### Mitsu Mitsu no Mi — Honey-Honey Fruit (Logia)
**Concept:** a golden honey body — traps, a lure (tank) and healing.
- 0 **Honey Shot** — sticks the target. 5 **Honey Trap** — real honey blocks flood the ground (temporary): feet stick.
- 12 **Sweet Lure** — enemies are pulled to you and fixate on you.
- 20 **Royal Jelly** — heal, regeneration, absorption and food for you and nearby allies.
- 30 **Amber Coffin** — target sealed in honeycomb for 5 s.
- 55 **Honey Flood** *(Awakening)* — area (even the sea surface) floods with honey, then sets hard.

**Passive:** whatever strikes or passes through the honey body comes away stuck (slowed). **Weaknesses:** standard Logia + fire caramelises honey (Fire/Magma always connect, +30%).

### Kabi Kabi no Mi — Mould-Mould Fruit (Logia, Poison)
**Concept:** a living colony of mould — spreading rot, carpets and self-acting sentries.
- 0 **Spore Shot**. 5 **Spore Cloud** — poison, nausea, weakness; enemies lose track.
- 15 **Mycelium Carpet** — temporary mycelium: enemies rooted and sickened, allies fed.
- 20 **Rot** — wither/weakness on a target; an enemy **ship** near the aim point has its timber eaten (hull damage over 10 s).
- 30 **Puffball Sentries** — three puffballs (summons, temporary blocks) spray spores at enemies for 12 s.
- 55 **Blight** *(Awakening)* — the whole area rots; enemy ships nearby are eaten through.

**Passive:** immune to poison; rain and darkness feed the colony (regeneration); attackers who touch it are infected.
**Weaknesses:** standard Logia + fire burns spores (Fire/Magma always connect, +50%).

### Funa Funa no Mi — Ship-Ship Fruit (Paramecia)
**Concept:** the spirit of a ship lives in the user (a Klabautermann's fruit) — built for sailing and sea battles.
- 0 **Broadside** — three-cannon salvo from the arms.
- 5 **Gangplank** — a temporary plank bridge unrolls across water or a gap (boarding, crossing without swimming).
- 12 **Living Ship** — the ship you stand on (or your nearest ship) raises anchor, sets full sail, **sails itself** and turns where you look for 30+ s — wind or no wind (Calm Belt). The spell ends early if you leave the ship behind, and always expires on its own (even if you fall). Without a ship: your body becomes a sail (speed).
- 20 **Hull Mend** — repairs 25% of your ship's hull (+30 + mastery) over 5 s and heals the crew on deck; without a ship, patches you up.
- 30 **Prow Ram** — ram forward; enemy ships in the path take 60 + mastery hull damage.
- 55 **Ghost Fleet** *(Awakening)* — three phantom ships sail through the enemy line firing broadsides (creatures and ships).

**Passive:** standing on a ship, its hull slowly mends. **Weaknesses:** standard (a ship-fruit user still cannot swim).

### Tenki Tenki no Mi — Weather-Weather Fruit (Paramecia, Lightning)
**Concept:** a walking weather front.
- 0 **Thunderclap** — lightning on the target. 5 **Sunshine** — heal/warm allies, dazzle enemies.
- 12 **Rain Squall** — real water rain: douses fire, wets Suna, reaches Logia bodies; cools allies.
- 20 **Hailstorm** — freezing ice pellets. 30 **Storm Cell** — a parked thunderstorm striking a random enemy each second.
- 55 **Change the Sky** *(Awakening)* — lightning on every enemy around, and (players only) the **world's weather turns**: clear → 3-minute thunderstorm (stronger Grand Line winds for ships), storm → clear skies.

**Passive:** immune to lightning; in rain or thunder you are faster and deal +15%. **Weaknesses:** standard.

### Kusuri Kusuri no Mi — Medicine-Medicine Fruit (Paramecia, Poison)
**Concept:** the crew doctor's dream fruit — the body brews remedies and poisons alike.
- 0 **Toxin Dart**. 5 **Antidote** — cleanse every ailment from you and nearby allies.
- 12 **Stimulant** — speed, strength, haste and +35 stamina for nearby allies, then a short crash.
- 20 **Anaesthetic** — numbing cone: slow, weak, guard dropped.
- 30 **Miracle Draught** — revives a knocked-down crewmate on the spot, or restores the most injured ally by 40%.
- 55 **Pharmacopoeia** *(Awakening)* — a cloud that heals/cleanses allies and poisons/withers enemies.

**Passive:** immune to poison; poisoned or withering enemies take +15% from you. **Weaknesses:** standard.

### Wana Wana no Mi — Trap-Trap Fruit (Paramecia, Pierce)
**Concept:** any patch of ground becomes a trap. Traps are armed zones (one cheap check per trap) that wait for an
enemy, and only the trapper can see where they lie.
- 0 **Snare** — snaps shut, roots. 5 **Pitfall** — a hidden 3×3 pit opens under the victim (temporary).
- 12 **Bear Trap** — steel jaws: heavy damage, pinned, bleeding.
- 20 **Tripwire Ring** — 30 s ring of wires around you: crossers are tripped, **invisible intruders revealed** (glowing) and you are warned.
- 30 **Iron Cage** — iron bars drop around the target for 6 s.
- 55 **Minefield** *(Awakening)* — eight hidden mines (explosion + small crater).

**Passive:** anything your traps catch takes +25% from you for 10 s. **Weaknesses:** standard.

### Kusari Kusari no Mi — Chain-Chain Fruit (Paramecia)
**Concept:** iron chains — control and *shared pain*.
- 0 **Chain Whip**. 5 **Chain Hook** — haul a target to you, or grapple yourself to a wall.
- 15 **Shackle Link** — chain the target to another enemy for 10 s: **50% of all damage one takes echoes onto the other**, and they cannot drift apart (alone: chained to the spot).
- 20 **Chain Bind** — wrapped head to toe (stun, guard broken). 30 **Chain Cyclone** — whirling chains reel in and batter.
- 55 **Chain Gang** *(Awakening)* — every enemy around is bound into one gang sharing a third of any damage.

**Passive:** enemies held in your chains take +20% from you. **Weaknesses:** standard.

### Nemu Nemu no Mi — Sleep-Sleep Fruit (Paramecia, Sound)
**Concept:** drowsiness and real sleep. A sleeper cannot move, attack or use powers (and can be cuffed with seastone)
until the sleep ends or **anyone** hurts it; three drowsiness stacks put a target to sleep; bosses only nod off briefly.
- 0 **Yawn** — contagious: drowsiness cone. 5 **Lullaby** — three verses of drowsiness around you.
- 12 **Sandman's Touch** — the target falls asleep at once.
- 20 **Nightmare** — sleepers are hurt and withered **without waking**; the waking are haunted and drowsier.
- 30 **Sleepwalk** — unseen and untouchable, reappear behind the target with a Rude Awakening.
- 55 **Eternal Slumber** *(Awakening)* — every enemy in a wide area sleeps; you and allies recover.

**Passive:** Rude Awakening — your hit on a sleeper deals +50%; immune to sound and to being put to sleep. **Weaknesses:** standard.

### Nendo Nendo no Mi — Clay-Clay Fruit (Special Paramecia)
**Concept:** a body of living clay that moulds fighters.
- 0 **Clay Fist**. 5 **Clay Wall**.
- 15 **Clay Soldiers** — three soldiers (summons) march on the nearest enemy and pummel it for 15+ s.
- 20 **Remould** — heal 20% and press out slows, weakness, poison and wither.
- 30 **Clay Cast** — a baked-clay cast holds the target for 5 s.
- 55 **Clay Colossus** *(Awakening)* — a giant clay figure slams its fists on the nearest enemies for 10 s.

**Passive:** the clay absorbs blunt blows and shockwaves (-30%); fire bakes it hard (brief resistance, slower).
**Weaknesses:** standard + water softens clay (+25%).

### Abura Abura no Mi — Oil-Oil Fruit (Special Paramecia)
**Concept:** slick oil — combo enabler for any fire user, and a ship-burner.
- 0 **Oil Shot** — the target (or a ship it splashes) is *oiled*: **any fire** deals it +75%.
- 5 **Oil Slick** — nobody keeps their footing: guards break, enemies slide and stagger; oils ships too.
- 12 **Ignite** — every oiled enemy and **oiled ship** within reach bursts into flame; your slicks become fire pools.
- 20 **Slippery Body** — physical blows slide off (-75%), speed. 30 **Oil Barrage** — five oil bombs, small slicks.
- 55 **Sea of Flames** *(Awakening)* — a vast slick erupts into fire; enemy ships in it burn.

**Passive:** oily skin — physical blows -20%. **Weaknesses:** standard + the oil burns (Fire/Magma +50% to the user).

### Same Same no Mi, Model: Megalodon — Shark-Shark Fruit (Ancient Zoan, Slash, model `megalodon`)
**Concept:** the prehistoric giant shark — with the cruel joke that, like every fruit user, it cannot swim; it hunts on land and from the decks.
- 0 **Transform**. 0 **Jaws** — huge bite, bleeding, heals 25% of the damage.
- 5 **Ground Fin** — swims through solid ground (only a fin shows) and bursts up with a bite.
- 15 **Blood Frenzy** — speed and strength; wounded enemies glow and take +30% from you.
- 25 **Hull Breaker** — ramming charge; enemy ships hit take 80 + mastery hull damage.
- 55 **Abyss Jaws** *(Awakening)* — a phantom maw snaps shut on everything above it (ships too).

**Passive:** transformed, 10% lifesteal and it smells blood (enemies under 30% health nearby glow). **Weaknesses:** standard Zoan.

### Kame Kame no Mi, Model: Genbu — Turtle-Turtle Fruit (Mythical Zoan, Water, model `genbu`)
**Concept:** the Black Tortoise of the North (the northern counterpart of the Azure Dragon) — a guardian/tank.
- 0 **Transform**. 0 **Shell Guard** — withdraw: 85% damage reduction, attackers pushed off.
- 5 **Serpent Tail** — venomous strike along a line.
- 15 **Tidal Stomp** — ring of northern water (bigger beside the sea).
- 30 **Northern Ward** — resistance II + absorption II for the crew around you; hostile projectiles knocked out of the air.
- 55 **Winter of the North** *(Awakening)* — a towering wave crashes down and freezes (temporary ice on the sea surface).

**Passive:** transformed, blows from behind hit the shell (-50%); permanent light resistance and slow regeneration in form.
**Weaknesses:** standard Zoan; immune to Water (own element) but not to the sea.

### Kaeru Kaeru no Mi, Model: Poison Dart Frog — Frog-Frog Fruit (Zoan, Poison, model `dart_frog`)
**Concept:** a small, brilliant frog carrying one of the world's deadliest poisons.
- 0 **Transform**. 0 **Tongue Lash** — sticky tongue reels the target in.
- 5 **Great Leap** — huge jump that lands in a venom splash.
- 12 **Toxic Skin** — attackers badly poisoned; your hits poison too.
- 30 **Venom Spit** — arcing venom: poison III, wither, brief paralysis.
- 55 **Poison Monsoon** *(Awakening)* — toxic rain on enemies; you and allies heal in it.

**Passive:** transformed, close attackers are poisoned; immune to poison. **Weaknesses:** standard + dry heat (Fire +25%).
