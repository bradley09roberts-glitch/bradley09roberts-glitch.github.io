# The Saltfire Saga — Story Bible

Original main story for **Grand Line Odyssey**. The world, its geography, its powers, races, equipment and the
great institutions (World Government, Marines, Cipher Pol, the Warlord charter, the Revolutionary Army, the Four
Emperors, the Poneglyphs and the legend of the final island) are preserved from the source. **Every character in
this document is original.** No canon character appears, is named or is alluded to by name; every title (Pirate
King, Emperor, Admiral, Warlord, Steward...) is held by someone new.

Implementation status is noted inline: cast entries are data in
`src/main/java/com/grandline/npc/notable/NotableCharacters.java` (plus `NotableCharactersExtra.java` for the Sky
People, Fish-Man Island and the islanders of §3.5); quests are in `src/main/java/com/grandline/quest/StoryQuests.java`
(plus `StoryQuestsExtra.java` for Skypiea, Fish-Man Island and the local stories); all text is in
`src/main/lang/en_us/story.json` and `story_extra.json`.

Tone: adventurous, comedic, sincere. Fights end in defeat, surrender, capture or a fall from which nobody gets up —
never in gore.

---

## 1. Premise and era

### The Saltfire King
Twenty-four years before the game begins, **Hokuto D. Calloway, "the Saltfire King"**, became the first captain
of his age to reach the final island at the end of the Grand Line. He came back, disbanded his crew, and let the
World Government take him. On the execution platform at **Loguetown**, with half the East Blue watching, he was
asked what he had found. He laughed:

> "My treasure? I left it burning at the end of the world. Follow the salt and the fire — and bring it home!"

That night a pale blue flame was seen dancing on the open sea off every Blue at once — fire that burned on salt
water. Sailors called it **the Saltfire**. It has appeared once a year since, always on the anniversary, always
on the horizon, always out of reach. The age that followed is the **Saltfire Age**: a flood of pirates,
adventurers, bounty hunters and dreamers chasing the King's last words.

### What the Saltfire really is
The treasure at the end of the Grand Line is the same treasure the world has always whispered about. What the
legends do not say is what Calloway did there. He found the truth of the Void Century written in stone and
understood it was useless while one government controlled who could read it. So he **lit a beacon**: a flame
bound to the island's ancient engine that, once fully kindled by a second visitor, will rise high enough to be seen
from every sea — and carry the stone's words in its light, readable by anyone who looks into it. The annual
Saltfire on the horizon is the beacon's pilot light, still waiting.

Whoever reaches the final island next decides what to do with it: kindle it, smother it, sell it, or carry it to
the Holy Land and set the Pale Throne alight. That choice is the end of the saga.

### The present day
- The **World Government** is ruled in public by the kings of the Reverie and in truth by **the Five Stewards of
  the Pale Throne**, who answer to an empty throne in Mary Geoise that nobody is permitted to look at.
- The **Marines** are led by **Fleet Admiral Constance Ironwake** and the **Three Compass Admirals** (Zenith,
  Nadir, Meridian).
- The **Warlord charter** (the Seven Warlords of the Sea) is in force when the game begins and **collapses during
  Part III** — see §4.
- The **Four Emperors** rule the New World: Hrolf Kumobashi, Kurogane Shiomaru, Barnum Candlewick and Aurelia
  Hinode. After the war a fifth claimant, Sterling Okanemochi, breaks the balance.
- The **Revolutionary Army** is led from Baltigo by **Sigrun Yoake, "the Dawnbringer"**.
- A new generation of rookies with bounties over 100 million — **the Supernovas** — is converging on Sabaody. The
  player is one of them, whatever their path.

### The thread through the whole saga: the Embers
Calloway's crew scattered after his death. Four of them retired to the four Blues — one to each starter island —
to watch for the next generation. They are **the Four Embers**, and every player meets one in the prologue. The
Embers know the King's navigator, **Haruka "the Lamplighter" Lanthorn**, still lives and is the only person alive
who can find the final island without the Road Poneglyphs. The Government knows it too.

---

## 2. Themes

1. **Inheritance vs. invention** — the Embers hand you a legend; your crew helps you write your own.
2. **Who owns history** — the Stewards burn it, scholars read it, the press prints it, pirates set it on fire.
3. **Justice that bends** — Marines meet corrupt captains, honourable pirates and impossible orders; their path
   asks what justice is for.
4. **Debt and freedom** — the Gilded Ledger syndicate, Sterling "the Fence" and the slave auctions of Sabaody all
   treat people as entries in a ledger. Freeing them is the recurring victory.
5. **Food, music and found family** — every arc has a meal, a song or a promise; the crew is the real treasure,
   and permadeath makes it precious.

---

## 3. Structure: four parts, one arc per major island

Quest ids are given in brackets. Variants use the suffix `.<faction>` (`.marine`, `.revolutionary`,
`.bounty_hunter`, `.civilian`); the unsuffixed quest is the Pirate (default) version. When a chained quest
finishes, the next quest is resolved to the player's faction variant automatically.

### PART I — Embers of the Blue

**Prologue (every starter island).** The existing "Embers" quests (first steps, bandits, the shipwright, the first
voyage, choosing a path) teach the basics. Each starter island also holds one of the Four Embers, whose lesson
points the player at the first canon island of their Blue:

| Blue | Starter isle | Ember | First destination |
|---|---|---|---|
| East | Shiokaze Isle | Old Fumio "Rigging" Tsunatori, retired rigger | Shells Town |
| North | Fuyugane Isle | Grandma Sólveig "Frostbite" Yukimura, retired gunner | Spider Miles → Lvneel |
| West | Kirinoha Isle | Uncle Percival "Gust" Hayate, retired helmsman | Kano Country → Ilisia |
| South | Hinata Isle | Auntie Mahina "Sunspot" Hidamari, retired cook | Karate Island → Sorbet |

`[ember_east_1, ember_east_2]`, `[ember_north_1, ember_north_2]`, `[ember_west_1, ember_west_2]`,
`[ember_south_1, ember_south_2]`.

**Blue prologues outside the East.** North Blue players clear out the **Scrap Baron, Rusty Kuzutetsu**, at the
junkyard fortress of Spider Miles and earn passage from **King Haakon Fjellstad** of Lvneel. West Blue players
pass the sword trial of **Master Kagerou Tsubame** in Kano Country and are knighted (or outlawed) by **Queen
Isadora Hanazono** of Ilisia. South Blue players win the sea-karate tournament of **Master Kiko Hamaguri** and the
royal frozen-dessert contest of **King Gelato Sorbetto**. All of them end with the same call: "Reverse Mountain is
waiting." `[north_scrap_baron, north_lvneel_passage, west_kano_trial, west_ilisia_audience, south_karate_cup,
south_sorbet_royal]`. Those players may still sail to Loguetown (the East Blue arcs are open to anyone).

**Shells Town — "The Justice Levy".** Marine **Captain Mordecai "Iron-Collar" Gallowgate** taxes the town for
"protection" and jails anyone who refuses. **Petra "Pocket" Marlinspike**, a teenage Marine dock-hand and the best
shot on the base, was locked in the yard for refusing to fire on a crowd. Pirates and Revolutionaries break the
levy collectors; Marines run a quiet internal-affairs case; everyone ends up fighting Iron-Collar. Petra joins as
**sniper**. `[shells_1 (+.marine), shells_2 (+.marine), recruit_petra]`

**Orange Town — "The Marmalade Siege".** The distiller-pirate **Wendell "Rotgut" Mostaccio** has blockaded the
orange-harvest festival to turn the whole crop into rotgut liquor. The town band scattered; its trumpeter **Carmen
"Brass Thunder" Trombetta** — who ate the Bell-Bell Fruit as a child and can ring like a cathedral — wants it back
together. Defeat Rotgut's deckhands, then Rotgut, and the festival goes on. Carmen joins as **musician**.
`[orange_1, orange_2, recruit_carmen]`

**Syrup Village — "The Clockwork Regatta".** **Gizmo Gearhart, "the Tinker Pirate"**, stole the champion racing
sloop of retired helmsman **Tomas "Steady Hand" Kajitori** and rigged the annual regatta with clockwork sharks.
Tomas lost his nerve after a wreck years ago; winning the race back gives it to him again. Tomas joins as
**helmsman**. `[syrup_1, syrup_2, recruit_tomas]`

**Baratie — "A Feast Under Siege".** The pirate fleet of **Fosco "the Gourmand" Ragout** demands the floating
restaurant's secret recipe book. The head waiter, **Kenji "the Carving Knife" Escoffier**, fights with two carving
blades and refuses to serve anyone who doesn't say please. A Marine **Vice Admiral, Horatio "Hambone" Okonomi**, is
on lunch break at the next table and insists he is "off duty" — Marine players can persuade him to help.
Kenji joins as **swordsman**. `[baratie_1 (+.marine), baratie_2, recruit_kenji]`

**Cocoyasi — "The Coral Keep".** The Fish-Man pirate **Gorgo "Hammerjaw" Shumoku** has walled the village's
waterfront into a fortress ("the Coral Keep", the Fish-Man fortress landmark) and taxes every
tangerine. One of his own, the Fish-Man karate master **Kaimon "Seawall" Funaboshi**, defects when Gorgo threatens
the village children. The existing `fishman_occupation` quest is the assault; the story quests frame it, and the
finale is Hammerjaw himself. Kaimon joins as **fighter**, and shows the crew that Fish-Men and humans fighting
side by side is the beginning of something. `[cocoyasi_1, cocoyasi_2, recruit_kaimon]`

**Loguetown — "The Town of Beginnings and Endings".** Every road in the East Blue ends at the execution platform
where the Saltfire King died. The player meets:
- **Captain Valka "the White Squall" Shiranui**, the Marine hunter who has caught every rookie this year;
- **Tobias "Firecracker" Kagaribi**, a rival rookie with the Bomb-Bomb Fruit and no indoor voice;
- **Odile "the Weathervane" Kazamidori**, a forecaster who sells forged charts to fund her life's work — the first
  almanac of the Grand Line's impossible weather. She joins as **navigator**;
- **Pim "Rainmaker" Amagasa**, a Revolutionary agent with an umbrella;
- **Bartolo "Inkwell" Scoop**, East Blue bureau chief of the *Sea-Gull Gazette*.

A storm blows in on the anniversary night, the Saltfire appears on the horizon, and the whole town sees the player
standing on the platform. Pirates escape Valka; Marines are ordered to arrest Tobias; Revolutionaries carry Pim's
storm signal; bounty hunters claim Tobias's head; civilians get the photo of the decade.
`[logue_1, logue_2 (+.marine, .revolutionary, .bounty_hunter, .civilian), logue_3, recruit_odile]`

### PART II — The Paradise Gauntlet

**Reverse Mountain / Twin Capes — "The Mountain That Flows Upward".** The lighthouse at the Twin Capes is kept by
**Wilhelmina "Old Wick" Lanthorn** — sister of the Lamplighter. She gives every rookie a Log Pose and a warning:
"My sister wrote to me once a year for twenty-four years. This year the letter didn't come." `[capes_1, capes_2]`

**Whisky Peak — "The Gilded Welcome".** The town of bounty hunters throws a party for every crew — and robs it at
midnight. The town is a front for **the Gilded Ledger**, a criminal company whose agents use coin codenames:
**Mr. Doubloon** (Gaspard Kinkasu) and **Miss Farthing** (Penny Kozeni) run this branch. Their bookkeeper
**Ignatz "Abacus" Sorobanji** has found an entry he can't live with — payments for "salting" the wells of a
kingdom — and defects. **Hugo Kariudo**, Grandmaster of the Hunters' Lodge, runs the honest bounty hunters here;
bounty-hunter players work for him and race the Ledger for the same targets. Ignatz joins as **quartermaster**.
`[whisky_1 (+.bounty_hunter), whisky_2, recruit_ignatz]`

**Little Garden — "The Seventy-Year Bake-Off".** Two giant grandmothers, **Astrid Kamameshi** and **Ylva Seiro**,
have been locked in a cooking duel for seventy years on a prehistoric island ("whoever's dish makes the other
cry wins"). Ledger agent **Mr. Shilling** (Cyrus Rosoku, the Wax-Wax Fruit) tries to end it by sealing them in
wax. Astrid's granddaughter **"Little" Hilde Kamameshi** — "little" for a giant — is the best cook on the
island and wants to taste the whole world. She joins as **cook**. `[garden_1, garden_2, recruit_hilde]`

**Drum Island — "Panacea".** **Doctor Quentin Panacea**, a quack, has taken over the castle on the drum-shaped
mountain and sells "Panacea Tonic" that keeps the kingdom just sick enough to keep buying. The real physician,
**Dr. Pippa Frostwhistle** of the Long-Arm Tribe, was run out of town for saying so. Expose the tonic, storm the
castle, and Pippa joins as **doctor**. `[drum_1, drum_2, recruit_pippa]`

**Alabasta — "Salt in the Wells".** The Warlord **Sir Barnaby Saltmarsh, "the Salt Baron"**, is the secret
President of the Gilded Ledger ("Mr. Crown"). With the Salt-Salt Fruit he has been salting the kingdom's oases so
that the kingdom must buy water from his company, and he plans to buy the throne of **King Rashid Sunabashi** (who
ate the Forest-Forest Fruit and is trying, slowly, to green the desert). His vice-president **Madam Sovereign**
(Celestine Ginka, the Flower-Flower Fruit) runs the casino at Rainbase. The young archaeologist **Sefa
"the Stone-Reader" Ishibumi** knows that a poneglyph beneath the palace is what Saltmarsh really wants. Variants:
pirates topple a Warlord and earn a bounty that makes the papers; Marines must first gather evidence because a
Warlord is legally immune, then arrest him; Revolutionaries arm the oasis towns; bounty hunters claim Madam
Sovereign; civilians bring water. Sefa joins as **archaeologist**.
`[alabasta_1, alabasta_2 (+.marine, .revolutionary), alabasta_3 (+.marine, .bounty_hunter, .civilian), recruit_sefa]`

**Jaya — "The Debt and the Sky".** In the lawless pirate town the loan-shark **Usurio "the Debt Collector"
Shakkin** owns every bar and every crew's debts. **Aria "Skylark" Fuusen** is a girl of the Sky People who fell
from a sky island as a child and has been building balloon-ships to go home ever since — Shakkin owns her workshop.
Burn the ledgers, ride the Knock Up Stream (or her balloon). Aria joins as **lookout**. Rival Supernova **Felicity
"Grand Larceny" Nekomata** steals from everyone involved. `[jaya_1, jaya_2, recruit_aria]`

**Skypiea — "The Storm Throne"** (the White Sea, dimension `grandline:sky`; optional branch after Jaya, reached by
the Knock Up Stream). Ten thousand metres up, the Sky People have lived for six years under **"God" Cirrus Tenjōin,
the Storm Throne**, who ate the Weather-Weather Fruit (original) and calls every thunderclap a divine judgment. He
has sealed the ruins of Shandora on Upper Yard, forbidden the Shandian guardians their own forest and set four
priests on its trails, each holding a **trial**:

| Trial | Priest | Power |
|---|---|---|
| Snares | **Ilse Wanazawa** | Trap-Trap Fruit (original) — every root a snare |
| Links | **Garamond Kusabi** | Chain-Chain Fruit (original) — paid by the link |
| Slumber | **Lulla Yumeshiro** | Sleep-Sleep Fruit (original) — pilgrims who sleep never wake to face the god |
| Gales | **Oswin Fūkyō** | no fruit: an Impact Dial, a Breath Dial and forty years of practice |

The gatekeeper **Amarante Kumoi** waves the player through Heaven's Gate ("one billion extol... nobody ever has").
On Angel Island the dial-smith **Ferris Kaigara** holds a Tone Dial that caught the god plotting, and the waver
courier **Lumi Hanegumo** knows every rumour. On Upper Yard **Chief Wakiza Ōtori** leads the Shandian guardians, and
his mother **Ayasha Ōtori** has polished the golden bell of Shandora on top of the Giant Jack beanstalk every morning
for sixty years without being allowed to ring it. On Weatheria the old **Professor Isidore Kumoyomi** and the young
weathercaster **Penelope "Pinwheel" Kazaguruma** have proof that every "judgment" arrived exactly when their
forecasts said a storm would: lightning is not a god, it is a timetable — and a gold-wire conductor made from
Shandora's gold will ground it. With the forecast in hand the player takes the Storm Throne; the Shandians ring the
golden bell, the Blue Sea hears it, and the poneglyph of Shandora can be read again. On the Cloud End the guide has a
message for a Sky People girl who fell to the Blue Sea years ago — Aria "Skylark" Fuusen.
Variants: civilians interview the island for the Gazette and print the forecast instead of fighting the god;
Marines (no jurisdiction above the clouds, "but kidnapping is kidnapping") arrest the priests and the god;
Revolutionaries give Upper Yard back to the Shandians; bounty hunters bring in the first god ever to have a price.
`[sky_1, sky_2 (+.civilian), sky_3 (+.marine), sky_4, sky_5, sky_6 (+.marine, .revolutionary, .bounty_hunter,
.civilian), sky_7, sky_8]`

**Long Ring Long Land — "Tug-of-Tides".** A nomad island of long stretched plains hosts the Tug-of-Tides, a crew vs.
crew contest. Supernova **Ayame "Thousand Knots" Musubi** cheats; the island's champion **Gunnar "Mossback"
Iwakoke**, a Stonehide wrestler, would like to see her lose fairly. He joins as **bodyguard**.
`[ring_1, recruit_gunnar]`

**Water 7 — "The Three Guilds".** The city of shipwrights is torn between three guilds after the assassination
attempt on **Guildmaster Hollis Kugiuchi**. The attackers were Cipher Pol agents hidden as the guild secretary
(**Nimue Kitsunebi**, an Ancient Zoan pteranodon) and a foreman (**Brick Tsuchiya**, the Soil-Soil Fruit), sent to
seize the blueprints of an ancient warship. The young shipwright **Kanna "the Mortise" Dovetail**, whose family
guild was framed, builds the player a real ship and joins as **shipwright**.
`[water7_1, water7_2 (+.marine), water7_3, recruit_kanna]`

**Enies Lobby — "The Gates of Justice".** Cipher Pol drags its prisoners — Kanna's father among them — to the
island of law. **Director Gregor Hanzaki** of CP9 (the Needle-Needle Fruit) holds the Tower of Justice, and
**Vice Admiral Dmitri Kawaragi** commands the fleet. When the attack begins, the Government orders a Buster Call.
CP9 trainee **Nimbus "Ninefold" Kasumi** (the Fog-Fog Fruit) refuses to fire on her own prisoners and defects;
she joins as **spy**. Marines are in the Buster Call fleet and choose between orders and evacuating civilians;
Revolutionaries open the cells; everybody else storms the tower.
`[enies_1 (+.marine), enies_2 (+.marine), enies_3 (+.marine, .revolutionary, .bounty_hunter, .civilian), recruit_nimbus]`

**Thriller Bark — "The Mirror-Monger".** The Warlord **Countess Lucrezia Kagamiya** (the Mirror-Mirror Fruit)
sails a fog-bound island where she steals the reflections of passing sailors and makes them her soldiers. Break
the mirrors, beat the Countess, and the crew gets its reflections back. `[thriller_1, thriller_2 (+.marine)]`

**Sabaody Archipelago — "The Auction of Souls".** Every Supernova arrives at once. **Sterling Okanemochi, "the
Fence"** — a Warlord who owns the human auction house through his auctioneer **Augustin "Gavel" Rakusatsu** —
puts a captured Fish-Man and a Mink on the block for **Saint Pompadour Gildenstern**, a World Noble. Someone
punches a World Noble (everyone claims credit). **Admiral Zenith** lands. The bubble-coater **Ume Shabondama**,
who coated the Saltfire King's ship, coats yours for the deep. In the chaos the rival **Nox "the Miasma"
Tsukuyomi** reveals he has been CP0's informant all along — he sold the Lamplighter's hiding place. The World
Government announces her public execution at Marineford.
`[sabaody_1, sabaody_2 (+.marine, .civilian), sabaody_3 (+.marine)]`

#### Fish-Man Island — "The Lantern in the Abyss"
(the deep sea, dimension `grandline:deep_sea`; a branch from Sabaody that leads to the New World Gate.) Ume coats
the player's ship and sends it ten thousand metres down to **Ryugu Kingdom** in its air bubble. **King Nereo
Umisachi, the Tidecrown**, a huge and gentle merman, rules it; his late wife, **Queen Amaya**, gathered a hundred
thousand names on the **Sunlit Petition** to bring the island into the sun and was shot for it in Gyoncorde Plaza ten
years ago — the island blamed a human. Her daughter **Princess Marisol, the Pearl of Ryugu**, carries her pen and
the petition on. The truth: the Fish-Man supremacist crew **the Abyssal Tide** of **Captain Lorcan Ankō, "the Lantern
of the Abyss"**, killed the queen, and has bought half the palace guard with the "Abyss Pearls" brewed by its
druggist **Doctor Sculpin Okoze** (Medicine-Medicine Fruit, original — a Fish-Man who gave up swimming for it). That
is why the Coral Guard's own soldiers attack visitors: **General Tetsuo "Ironfin" Sawara** cannot tell which half is
loyal. **Madame Perla Sango** of Mermaid Cove welcomes the player on Ume's word; in the **Sea Forest** the old keeper
**Tidwell Kaisō** tends the queen's grave, the wrecks and the poneglyph she died defending ("a promise someone
carved and could not keep"). The player clears the turncoats, carries the petition to the king, lights the graves
with sea pickles from the Coral Hill Reef, breaks the Abyssal Tide, reads the stone of the Sunken Ruins and rises to
the New World Gate as the island's ally. Variants: Marines arrest the druggist and the captain (the queen's murder is
finally a case); bounty hunters bring them in alive; Revolutionaries break the hatred "handed down like a chain";
civilians gather the petition for the Gazette and evacuate the plaza.
`[fmi_1, fmi_2, fmi_3 (+.marine, .bounty_hunter), fmi_4 (+.civilian), fmi_5, fmi_6 (+.marine, .revolutionary,
.bounty_hunter, .civilian), fmi_7, fmi_8]`

#### Calm Belt interlude
- **Amazon Lily — "The Jade Camellia".** The Kuja empress and Warlord **Hisui Benitsubaki** (the Mythical Zoan
  Yamata-no-Orochi) has been summoned to Marineford and refuses. She'll go — for a price. `[lily_1]`
- **Rusukaina — "The Hermit's Silence".** **Tsukumo Okina**, a hermit who taught Haki to two Emperors, teaches the
  player to listen before the war. `[rusukaina_1]`
- **Impel Down — "Descent".** The great prison under **Chief Warden Grimsby Rōgoku**. Pirates and Revolutionaries
  break in (or are thrown in) to reach the Lamplighter before she is moved; Marines and bounty hunters are posted
  as guards. The breakout leads straight into the war. `[impel_1 (+.marine, .bounty_hunter)]`

### PART III — The War of the Crescent Bay

**Marineford — "The Lamplighter's Execution".** The Government will burn the last person alive who has seen the
final island, publicly, to end the Saltfire Age. **Fleet Admiral Constance Ironwake** commands the plaza with the
Three Compass Admirals; **Vice Admiral Osric "Old Anchor" Funayama** trains the recruits. The Warlords are
summoned. The Emperor **Hrolf Kumobashi** — Calloway's old rival and friend — sails into the crescent bay to take
Haruka back, and **Aurelia Hinode**, who was Calloway's cabin girl, arrives at the last moment.

How each path lives the same war:

| Path | Role in the war | Quest |
|---|---|---|
| Pirate | Storms the bay with Hrolf's fleet to reach the scaffold. | `war_1 → war_2 → war_3` |
| Marine | Holds the plaza under Ironwake, and is the one standing next to Haruka when the order to fire comes. The choice: obey, or step aside. | `war_1.marine → war_2.marine → war_3` |
| Revolutionary | Infiltrates to free every prisoner on the execution list and broadcast the Government's orders to the world. | `war_1.revolutionary → war_2.revolutionary → war_3` |
| Bounty Hunter | Hired by Sterling "the Fence" to grab Haruka in the chaos and sell her — then discovers who the buyer is. | `war_1.bounty_hunter → war_2.bounty_hunter → war_3` |
| Civilian | War correspondent for the *Gazette* and field medic, evacuating the town behind the bay. | `war_1.civilian → war_2.civilian → war_3` |

**Outcome (canon of this saga).** Haruka is rescued, but Hrolf's fleet takes heavy losses and the Fleet Admiral's
justice is broken in front of the whole world by the broadcast. During the battle **Sterling Okanemochi** betrays
every side, steals the Government's sealed list of Road Poneglyph holders, and sails off. Afterwards:
- **The Warlord charter is abolished** ("the Charter Burns"). Ex-Warlords lose their immunity and are hunted.
- Sterling proclaims himself **the Fifth Emperor** and buys his way into Hachinosu.
- Ironwake stays Fleet Admiral but Admiral Meridian resigns; Marines on the player's path must decide which
  justice they serve.
- Haruka gives the player one sentence before vanishing again: "Four red stones, then the star that doesn't move."

Kuraigana Island holds the post-war retreat of the swordsman Warlord **Jinpachi "the Last Blade" Grimaldi**, who
trains swordsmen who can survive a blade like his. `[kuraigana_1]`

### PART IV — The New World: Saltfire Rising

To reach the final island the player needs the four Road Poneglyph rubbings, held (as the source's legends say)
by the most powerful people in the sea, and then the last bearing from Lodestar Island.

| Island | Arc | Ruler / threat | Prize |
|---|---|---|---|
| New World Gate / G-5 | "Into the New World" | **Vice Admiral Seraphine Glasswater** of the rough G-5 base | first New World log |
| Punk Hazard | "The Counterfeit Flame" | **Dr. Hesper Galvani**, Government scientist building an artificial Saltfire to fake the treasure | proof of the forgery |
| Dressrosa | "The Marionette Colosseum" | **King Florian Castañuela**, a puppet king owned by Sterling | Road Poneglyph rubbing #1 (the colosseum prize) |
| Green Bit | "The Tiny Tyrant" | ex-Warlord **Pipistrello "the Drill Lord" Minimaro**, the smallest Warlord ever | **Bolt Nejimaki** (recruit, engineer) |
| Zou | "The Thunder-Mane's Oath" | ex-Warlord **Leonhart Raijū**, Mink lion who guarded Zou under the charter | rubbing #2; **Pashmina "Tigerlily" Torafu** (recruit, Mink warrior) |
| Whole Cake Island | "The Midnight Carnival" | Emperor **Barnum "Waxwork" Candlewick**, his daughter **Marzipan "Sugarplum"** and the acrobat **Fennimore "Tightrope" Funambulo** | rubbing #3 |
| Wano Country | "Dawn over the Flower Capital" | puppet **Shogun Hagane Oboro** (Azure Dragon Zoan); ronin **Rin "the Moonlit" Tsukishiro** (recruit) | Wano's freedom |
| Onigashima | "The Kraken's Wager" | Emperor **Kurogane "the Kraken" Shiomaru** with **Gorou "Riptide" Kanemitsu** and **Tsubaki "Backwash" Shiranami** | rubbing #4 |
| Egghead | "The Genius and the Shadow" | **Professor Minerva Tensai** defects; CP0 chief **Vespera Kagenui** raids the island | **Elowen Hoshizora** (recruit, Lunarian stargazer) |
| Baltigo | "The Green Dawn" | **Sigrun Yoake** and chief of staff **Lysander "Firefly" Hotaru** | Revolutionary endgame plan |
| Hachinosu | "The Fifth Emperor" | **Sterling Okanemochi**, the Fence | the stolen holder list |
| Elbaph | "The Giant's Hearth" | Emperor **Hrolf Kumobashi** and his daughter **Sigrid "Hearth-Shield"** | Hrolf's blessing (or his defeat) |
| Lodestar Island | "The Star That Doesn't Move" | Emperor **Aurelia Hinode** with **Asagao "Morning Glory" Bellweather** and **Thaddeus "Old Salt" Shiokara** | the last bearing |
| Laugh Tale | "The Saltfire" | **Nox Tsukuyomi** waits with CP0's last orders | the ending |

`[nw_gate_1, punk_1, dress_1, dress_2, greenbit_1, recruit_bolt, zou_1, recruit_pashmina, cake_1, cake_2, wano_1,
recruit_rin, oni_1, oni_2, egghead_1, recruit_elowen, baltigo_1 (+.revolutionary, .marine), hachinosu_1 (+.marine), elbaph_1, lodestar_1,
laughtale_1, laughtale_2 (+ all variants)]`

### Local stories — every other canon island
Every canon island has something to do. Islands without an arc of their own have a short local story told by an
original islander (or, on the Revolutionary commanders' islands, by the commander), offered on that island:

| Island | Islander | Story | Quest |
|---|---|---|---|
| Dawn Island | Granny Wren Kazamaki, barkeep | The Gray Terminal gang stole the harvest money. | `dawn_1` |
| Shimotsuki Village | Master Ren Shimoyama, one-armed sensei | Bandits stole the Frost-Moon Dojo's blade. | `shimotsuki_1` |
| Island of Rare Animals | Philippa "Fern" Hakobune, naturalist | Poachers before the full moon. | `rare_animals_1` |
| Mirror Ball Island | Commander Tamsin Kujira | Lanterns on the water (Revolutionaries: a Marine patrol). | `mirror_ball_1` (+.revolutionary) |
| Flevance | Sister Agnes Shiraishi | Grave robbers in the White City. | `flevance_1` |
| Minion Island | Sigvald "Snowshoe" Yukibana, trapper | Firewood for a winter that never ends (16 logs). | `minion_1` |
| Swallow Island | Commander Bjorn Sekka | The North Army's forge. | `swallow_1` (+.revolutionary) |
| Spider Miles | Nell "Sprocket" Kuzuya, junk-picker | Salvage iron before the Scrap Baron melts it. | `spider_1` |
| Ohara | Commander Oleander Hisame | Books for the hidden library of the burnt scholars. | `ohara_1` |
| Baterilla | Commander Mireille Taiyō | The South Army's soup kitchen. | `baterilla_1` (+.revolutionary) |
| Torino Kingdom | Mother Kestrel Torigai, bird-keeper | Feathers for the Nest Festival. | `torino_1` |
| San Faldo | Livia Kamenya, mask-maker | Thieves in her masks rob the carnival. | `san_faldo_1` |
| St. Poplar | Rosalind Hanakago, florist | A bouquet for the Spring Queen (12 flowers). | `st_poplar_1` |
| Pucci | Chef Gustave Ajimi | Cod for the gourmet market. | `pucci_1` |
| Boin Archipelago | Ivo Tsurukusa, botanist | Pirates raid the Pop Green beds. | `boin_1` |
| Namakura Island | Hattie Tatara, hermit smith | Iron for a fine blade. | `namakura_1` |
| Banaro Island | Corvin Yakeno, last baker | Looters in the burnt town. | `banaro_1` |
| Momoiro Island | Duchess Peony Momozono | The Pink Pageant: beat her, then bring pink. | `momoiro_1` |
| Karakuri Island | Professor Linnea Zenmai, clockmaker | Redstone for a self-winding heart. | `karakuri_1` |
| Mary Geoise | Juniper "Ashfoot" Kurai, runaway | The back stairs of the Holy Land (passage via Ume and Princess Marisol). | `holy_land_1` |
| New World Gate | Harbourmaster Agathe Minatoya | Pirates preying on fresh arrivals. | `nw_harbour_1` |
| Punk Hazard | Rook Hiyamizu, runaway lab assistant | Galvani's hired guns want him back. | `punk_rook_1` |
| Sphinx | Nana Sabaku | Rookies who don't know the Cloudbridge's flag. | `sphinx_1` |
| Cacao Island | Praline Kakaoyama, chocolatier | Cocoa for the Carnival's tithe. | `cacao_1` |
| Jam Island | Bramble Ichigoya, jam-maker | Berries for the Carnival's tithe. | `jam_1` |
| Foodvalten | Hansel Komugi, mill-master | The Carnival's bread tax. | `foodvalten_1` |
| Onigashima | Old Gonzaburo Sumikura, pit-miner | Free the Kraken's miners. | `oni_miners_1` |
| Hachinosu | Moll "Pinchpenny" Hachiya, barkeep | Smuggle debtors out before the Fence collects. | `hachinosu_moll_1` |
| Laugh Tale | Old Cinder Tomoshibi, the King's ship's boy | He stayed to keep the pilot light company. | `laughtale_cinder_1` |

In the White Sea and the deep sea every named island has a quest given there or one that sends the player there
(the Cloud End's guide gives `sky_8`; the Coral Hill Reef and the Sunken Ruins are destinations of `fmi_5`/`fmi_7`).

### Original islands — a story on every generated island

The ~140 original islands are generated from the world seed, and so are their stories (`quest/story`). Every original
island gets one local story picked from twenty templates by what the island has: its sea and tier, theme, kind of
settlement (none, hamlet, village, town, port, city, Marine base, fortress, ruins), dock, secrets (buried treasure,
hidden cache, lost shrine, shipwreck), landmarks and controlling faction. The same seed always regenerates the same
stories, people and places; nothing about them is saved except the players' progress and the people's fates.

Each story has 2–4 steps chained like the Saltfire Saga's (`story_<island>_<n>`, faction variants `.<faction>`),
offered by a named islander. The people are generated too: names flavoured by their sea (East Blue Wa names, northern
names in the North Blue, Latin in the West, southern and island names in the South, all of them mixed on the Grand
Line), titles, roles, fighting styles and — sometimes — one of the original peoples where the island suits them
(Emberkin on volcanic isles, Vinefolk in the tropics, Stonehide on rock, Reefborn on atolls...). They are notables:
they spawn once near their home when a player comes by, and stay dead when killed. Villains are a tier or two above
the island; rivals and suspects yield instead of dying.

| Template | Fits | Steps |
|---|---|---|
| The Bandit Toll | hamlets, villages, towns | hear the victim → break the bandit camp → bring down the chief (Marine/bounty hunter: arrest; pirate: take the toll money) |
| The Beast of the Bay | coastal towns (Calm Belt: a Sea King) | ask the witness → find the torn nets → slay the named beast (a Sea King offshore in the Calm Belt and sometimes the Grand Line) |
| The Missing Child | villages, towns | talk to the child's friend → find the hollow, deal with its beasts or bandits, talk to the child → back to the parent |
| The Crooked Officer | Marine bases, big towns | find the strongbox with the ledger → defeat the corrupt captain (Marine/bounty hunter: arrest; Revolutionary: his whole squad) |
| Two Houses | hamlets, villages, towns | hear the rival house → drive off the thugs who burnt the orchard → bring a peace gift to both heads |
| The Cursed Shrine | lost shrines, dark/mushroom/swamp isles | reach the shrine → put down its stone guardians → bring an offering to the shrine keeper |
| Contraband at the Docks | ports, towns with a dock | pay the informant → find the smugglers' cache and its guards → bring down the boss (Marine/BH: arrest; pirate: fence the goods) |
| The Lost Heirloom | islands with a shipwreck | hear the old salt → dive to the wreck → return the locket to the widow |
| A Rookie Crew | any island (most often wild ones) | beat the rookie crew → duel their captain (who yields) → earn the rival's respect (Marine/BH: arrest instead) |
| Fever Season | villages, towns | sail to a herbalist on the nearest island → gather the herb (by that island's climate) → bring the cure home |
| The Festival | villages, towns | gather festival goods → drive off the raiders and their captain (who only turns up for that) → open the festival |
| The Warlord's Due | towns of the Grand Line | hear another debtor → thin out the Warlord agent's bodyguards → bring down the agent (pirates keep the ledger) |
| The Castaway | uninhabited islands, ruins | bring food → dig up the castaway's savings → receive an Eternal Pose to their home island |
| The Nest | snow, swamp, tropical, desert and temperate isles | find the tracks → slay the beasts at the nest → back to the hunter |
| Under the Black Flag | pirate-held islands | break the guard post → bring down the self-styled king (Revolutionaries free the island; Marines arrest) → report |
| The Hidden Cell | Revolutionary forts, big Grand Line towns | hear the courier → defeat the Cipher Pol agent hunting the cell → report to the cell leader |
| The Deserter | Marine bases | find the deserter's camp → beat him (Marine/BH: arrest; pirate: take his offer) → report (pirate: dig up the payroll) |
| Dead Man's Map | islands with buried treasure | reach the X and beat the rival treasure hunters → share the loot (pirates take more) |
| Who Did It? | villages, towns | question three suspects → unmask the culprit (who yields; Marines arrest) and tell the guard |
| Stones That Speak | islands with an ancient ruin | reach the ruin → get past its guardians and read the poneglyph → tell the scholar |

Rewards scale with the island's tier (Beli, XP, reputation with the island and the factions involved, sometimes a
style scroll, an Eternal Pose, gold, meat or Sea King steak); pirates who take a cut also raise their bounty.

Two objective types were added for these stories: `reach` (be within ten blocks of a story place: a hideout, a
shrine, a wreck) and `slay` (island beasts — `sea_cat`, `lapahn`, `banana_gator`, `kungfu_dugong` — or `sea_king`).
Foes that a `kill`/`slay` objective needs appear at the story's place while a player with that step is near it, a few
at a time, as many as the step still needs; they are ordinary creatures that leave when the player does.

Text: `lang/en_us/island_stories.json`. Keys are `story.grandline.<template>.<step>` (title), `.desc` (and
`.desc.<faction>` for variants), `.greet.<slot>` / `.thanks.<slot>` (what each person says before and after),
`.rumor` (islanders' gossip), `story.grandline.who.<slot>` (titles) and `story.grandline.site.<place>`. All texts of a
story share one argument list: `%1$s` is the island, then the people in order, then the template's extras (listed on
each template in `StoryTemplate.java`).

#### The notice boards

Right-click a town's bounty board to read it. Besides the five classic jobs, each settled island's board carries jobs
generated for it and scaled to its sea: a sealed letter for the story-giver of a nearby island; an escort (a merchant
goes ahead to another island's dock, where pirates wait — the merchant only turns up for the player on the job); three
named outlaws hunted one after another, each with a wanted poster saying where they were last seen and a bounty
(Marines and bounty hunters bring them in alive for more; the outlaw and their guards only appear for whoever hunts
them); a beast nest (or a bandit camp where no beasts live); salvage from a nearby wreck; and fish, hot meals and
timber for the town. Delivering, escorting, salvage and the odd jobs repeat; the hunts move on to the next outlaw.

---

## 4. The Warlord system

**Status: active at the start, abolished after the war.** Seven pirates hold Government letters of marque:
immunity and a share of plunder in exchange for service. The charter is why Marines cannot simply arrest
Saltmarsh in Alabasta (Marine players must gather evidence first), why Hisui must be bargained with, and why the
Warlords are called to Marineford. Sterling's betrayal during the war ends it. In Part IV the surviving
ex-Warlords are free agents: some become allies, some become prey.

| Warlord | Title | Fruit / style | Seat | Fate |
|---|---|---|---|---|
| Sir Barnaby Saltmarsh | the Salt Baron | Salt-Salt (original) | Alabasta | Falls in Part II |
| Sterling Okanemochi | the Fence | Scale-Scale (original) | Sabaody → Hachinosu | Betrays all; Fifth Emperor |
| Countess Lucrezia Kagamiya | the Mirror-Monger | Mirror-Mirror (original) | Thriller Bark | Falls in Part II |
| Hisui Benitsubaki | the Jade Camellia | Snake-Snake, Model: Yamata-no-Orochi | Amazon Lily | Ally or rival |
| Jinpachi Grimaldi | the Last Blade | Iaido, black blade | Kuraigana Island | Swordmaster |
| Leonhart Raijū | the Thunder-Mane | Electro (Mink) | Zou | Ally after the charter |
| Pipistrello Minimaro | the Drill Lord | Spiral-Spiral (original), Tontatta | Green Bit | Comic villain → ally |

---

## 5. Faction branches

Every path walks the same islands and meets the same people; what changes is **why** you are there, **who** you
fight, and **what the ending means**.

- **Pirate (default variant).** Freedom, crew and reputation. You raise bounties by beating Warlords, storm
  Marineford to save the Lamplighter, and race the Emperors for the rubbings. Ending: kindle the Saltfire and be
  remembered as the captain who brought it home — the next **Pirate King**.
- **Marine.** You start as a recruit under "Hambone" Okonomi's patronage and rise under Valka and Osric. Your
  arcs are investigations: corrupt captains, Warlords protected by law, Buster Calls. At Marineford you stand next
  to the prisoner and choose. Ending: the Saltfire is in your hands — **extinguish it on the Stewards' orders**
  (Fleet Admiral ending) or **guard the light so the world can read it** ("True Justice" ending). The quest grants
  the Marine ending; the epilogue text depends on the choice flag.
- **Revolutionary.** Contact via Pim at Loguetown. You arm oasis towns, free prisoners, broadcast the war, and
  report to Sigrun. Ending: **carry the Saltfire to the Holy Land** and set the Pale Throne alight — "The Dawn".
- **Bounty Hunter.** A Lodge member under Hugo Kariudo. You hunt the people other paths fight, collect on
  Warlords, and are hired by Sterling at the war. Ending: the last bounty is on the Saltfire itself — **sell it**
  (to the Stewards or Sterling) or **claim it for the Lodge** and set every bounty to zero.
- **Civilian.** A journalist, doctor and merchant with the *Sea-Gull Gazette*. You document, heal, feed and
  evacuate. Ending: **print the truth** — the Saltfire's light photographed and published in every port, "The
  Free Press".

Defection is allowed at any time (it already exists in the faction rules); quests already completed stay
completed and the next chained quest resolves to the new faction's variant.

---

## 6. Endings

The final quest `laughtale_2` has five variants, each setting a flag the epilogue reads:

| Flag | Path | Ending |
|---|---|---|
| `ending_saltfire_kindled` | Pirate | The beacon rises; every sea reads the stone. You are the new Pirate King. |
| `ending_true_justice` | Marine | You stand guard over the light. The Marines split; your half reforms around it. |
| `ending_dawn` | Revolutionary | The flame is carried to Mary Geoise; the Pale Throne is empty at last. |
| `ending_last_bounty` | Bounty Hunter | Every bounty in the world is cancelled — including yours. |
| `ending_free_press` | Civilian | The truth goes to press in a thousand ports. |

All endings leave the world playable: the Emperors, Admirals and ex-Warlords remain (unless players have killed
them — permadeath applies to the cast too), and new rookies keep arriving.

---

## 7. Cast

Abbreviations: Obs/Arm/Coq = Observation / Armament / Conqueror's Haki (0–100). Tier = combat tier (1–12).
"Home" is the island where the character lives in the world. Every listed character is spawned as a notable NPC.
Rules for notables: they spawn once per world when a player comes near; if killed they never return (their fruit
regrows elsewhere); friendly characters, rivals and crew **yield** when beaten instead of dying (attacking a
yielded character again kills them).

### 7.1 The legend
- **Hokuto D. Calloway, "the Saltfire King"** — Pirate King, executed 24 years ago. Never appears; present in
  every rumour.
- **Haruka "the Lamplighter" Lanthorn** — his navigator, the only living person who found the final island. Dry
  humour, pipe, unshakeable calm. No fruit; Obs 95. Home: Marineford (on the scaffold). The heart of Part III.
- **Wilhelmina "Old Wick" Lanthorn** — Haruka's sister, keeper of the Twin Capes lighthouse. Gives every rookie a
  Log Pose. Home: Twin Capes.

### 7.2 The Four Embers (Calloway's retired crew)
| Character | Title | Home | Notes |
|---|---|---|---|
| Fumio Tsunatori | "Rigging", East Ember | Shiokaze Isle | Gruff, knot-obsessed, Black Leg kicks at 80. |
| Sólveig Yukimura | "Frostbite", North Ember | Fuyugane Isle | Ex-gunner, deaf in one ear, laughs at cannon fire. |
| Percival Hayate | "Gust", West Ember | Kirinoha Isle | Ex-helmsman, dreamy, Storm Dance staff. |
| Mahina Hidamari | "Sunspot", South Ember | Hinata Isle | Ex-cook, feeds everyone, Life Return master. |

### 7.3 The Four Emperors and their officers
- **Hrolf Kumobashi, "Cloudbridge"** — Emperor, Longhouse Pirates. Giant of Elbaph; no fruit; Hasshoken; Obs 90 /
  Arm 98 / Coq 90. Protector of a hundred harbours; Calloway's rival and friend; leads the rescue at Marineford.
  - **Sigrid Kumobashi, "Hearth-Shield"** — his daughter, giant shieldmaiden (Arm 85).
  - **Ulfric Hanabi, "Fireworks"** — first mate, a human gunner with a hand cannon and a laugh like one.
- **Kurogane Shiomaru, "the Kraken"** — Emperor, Black Tide Pirates. Mythical Zoan Octopus-Octopus Fruit, Model:
  Kraken. A gambler-tyrant who never refuses a wager and never forgives a cheat; rules Wano through a puppet
  shogun from Onigashima. Obs 85 / Arm 95 / Coq 85.
  - **Gorou Kanemitsu, "Riptide"** — Ancient Zoan allosaurus, enforcer.
  - **Tsubaki Shiranami, "Backwash"** — two-sword style, the Kraken's duelist.
- **Barnum Candlewick, "Waxwork"** — Emperor, Midnight Carnival Pirates. A towering Buccaneer showman with the
  Hobby-Hobby Fruit: those he touches become carnival attractions and are forgotten. Rules the candy islands
  around Whole Cake as a never-ending fair. Obs 80 / Arm 90 / Coq 80.
  - **Marzipan Candlewick, "Sugarplum"** — his daughter, Mochi-Mochi Fruit, the real brains of the carnival.
  - **Fennimore Funambulo, "Tightrope"** — acrobat, Storm Dance spear.
- **Aurelia Hinode, "Daybreak"** — Emperor, Daybreak Pirates. Star-Star Fruit (original Logia). Calloway's
  former cabin girl; free-spirited, drinks with enemies, carries his broken compass. Waits at Lodestar Island for
  whoever can beat her to the final island. Obs 92 / Arm 90 / Coq 92.
  - **Asagao Bellweather, "Morning Glory"** — first mate, one-sword style.
  - **Thaddeus Shiokara, "Old Salt"** — navigator, tide grappler, eighty years old and proud of it.

### 7.4 The World Government
**The Five Stewards of the Pale Throne** (Home: Mary Geoise; all tier 11):
| Steward | Office | Fruit |
|---|---|---|
| Saint Aldric Shirokane | Steward of Records — burns history | Time-Time |
| Saint Morwenna Hoshikage | Steward of Faith | Soul-Soul |
| Saint Cassius Tetsugaki | Steward of Arms | Tremor-Tremor |
| Saint Ottavia Kanemura | Steward of the Treasury | Barrier-Barrier |
| Saint Leopold Yamigiri | Steward of the Sea Lanes | Swamp-Swamp |

- **Saint Pompadour Gildenstern** — a World Noble at Sabaody; buys people; gets punched.

**Cipher Pol**
- **Vespera Kagenui, "the Shadow-Stitcher"** — head of CP0 (String-String Fruit). Home: Egghead.
- **Gregor Hanzaki, "Iron Jury"** — Director of CP9 (Needle-Needle Fruit, original). Home: Enies Lobby.
- **Nimue Kitsunebi** — CP9 agent posing as guild secretary (Ancient Zoan pteranodon). Home: Water 7.
- **Brick Tsuchiya** — CP9 agent posing as foreman (Soil-Soil Fruit, original). Home: Water 7.

**Marines**
| Rank | Character | Title | Fruit | Home |
|---|---|---|---|---|
| Fleet Admiral | Constance Ironwake | the Unbending Tide | Whirl-Whirl (original) | Marineford |
| Admiral | Tenma Solberg | Admiral **Zenith** | Glint-Glint | Sabaody |
| Admiral | Ingrid Fukamizu | Admiral **Nadir** | Dark-Dark | Marineford |
| Admiral | Rocco Kirisame | Admiral **Meridian** | Magma-Magma | New Marineford |
| Vice Admiral | Osric Funayama | "Old Anchor" | — (Hasshoken) | Marineford |
| Vice Admiral | Horatio Okonomi | "Hambone" | — (Brawler) | Baratie |
| Vice Admiral | Dmitri Kawaragi | "the Buster" | — (Rokushiki) | Enies Lobby |
| Vice Admiral | Seraphine Glasswater | "Glass Cannon" | — (Gunslinger) | G-5 |
| Vice Admiral | Prudence Hagane | "Steel Sermon" | — (Iaido) | New Marineford |
| Captain | Mordecai Gallowgate | "Iron-Collar" | — | Shells Town |
| Captain | Valka Shiranui | "the White Squall" | Snow-Snow | Loguetown |
| Chief Warden | Grimsby Rōgoku | "the Lock" | — | Impel Down |

The Admirals' codenames come from the compass of the sky: Zenith (the highest point), Nadir (the lowest),
Meridian (the line between).

### 7.5 The Revolutionary Army
- **Sigrun Yoake, "the Dawnbringer"** — Supreme Commander. Mythical Zoan Bird-Bird Fruit, Model: Phoenix. Calm,
  patient, terrifying when she stops being patient. Home: Baltigo.
- **Lysander Hotaru, "Firefly"** — Chief of Staff, iaido master. Home: Baltigo.
- **Tamsin Kujira** — East Army Commander, Whalefolk (original race), tide grappler. Home: Mirror Ball Island.
- **Bjorn Sekka** — North Army Commander, Stonehide, anchor brawler. Home: Swallow Island.
- **Oleander Hisame** — West Army Commander, storm-dance naginata. Home: Ohara (the ruins).
- **Mireille Taiyō** — South Army Commander, Emberkin, black-leg kicks. Home: Baterilla.
- **Pim Amagasa, "Rainmaker"** — field agent with an umbrella, Long-Leg. Home: Loguetown.

### 7.6 Other powers
- **Hugo Kariudo** — Grandmaster of the Hunters' Lodge (bounty hunters). Home: Whisky Peak.
- **Bartolo "Inkwell" Scoop** — East Blue chief of the *Sea-Gull Gazette* (civilian patron). Home: Loguetown.
- **Tsukumo Okina** — the hermit of Rusukaina, Haki master. Home: Rusukaina.
- **Ume Shabondama** — bubble-coater who coated the Saltfire King's ship. Home: Sabaody.
- **Professor Minerva Tensai** — the Government's greatest scientist, who defects. Home: Egghead.
- **Dr. Hesper Galvani** — the scientist forging an artificial Saltfire. Home: Punk Hazard.

### 7.7 The Supernovas (rival rookies)
| Rookie | Title | Race | Fruit / style | Met at |
|---|---|---|---|---|
| Tobias Kagaribi | Firecracker | Human | Bomb-Bomb | Loguetown |
| Felicity Nekomata | Grand Larceny | Mink | Cat-Cat, Model: Leopard (a cat who ate a bigger cat) | Jaya |
| Matthias Kanazuchi | the Iron Abbot | Human | Elephant-Elephant; warrior monk | Water 7 |
| Coralie Utahime | Riptide Diva | Merfolk | Tide Grappling; singer | Sabaody |
| Mortimer Hakaba | the Gravedigger | Long-Arm | Hollow-Hollow; anchor-shovel | Thriller Bark |
| Edison Haguruma | Professor Sparks | Three-Eye | Inventor, bazooka | Sabaody |
| Ayame Musubi | Thousand Knots | Human | Whip | Long Ring Long Land |
| Raven Arashi | Stormcrow | Stormwing | Smoke-Smoke | Drum Island |
| Brunhilde Iwaoka | Mountainside | Giant-blooded | Ox-Ox, Model: Bison | Little Garden |
| Nox Tsukuyomi | the Miasma | Human | Gas-Gas; CP0 informant | Laugh Tale (final rival) |

### 7.8 Island rulers and villains
| Island | Character | Role |
|---|---|---|
| Spider Miles | Rusty Kuzutetsu, "the Scrap Baron" | North Blue villain |
| Lvneel | King Haakon Fjellstad | North Blue ruler |
| Kano Country | Master Kagerou Tsubame | West Blue sword master |
| Ilisia Kingdom | Queen Isadora Hanazono | West Blue ruler |
| Karate Island | Master Kiko Hamaguri | South Blue karate master |
| Sorbet Kingdom | King Gelato Sorbetto | South Blue ruler |
| Shells Town | Captain Mordecai "Iron-Collar" Gallowgate | corrupt Marine |
| Orange Town | Wendell "Rotgut" Mostaccio | distiller-pirate |
| Syrup Village | Gizmo Gearhart, "the Tinker Pirate" | clockwork pirate |
| Baratie | Fosco "the Gourmand" Ragout | glutton fleet admiral |
| Cocoyasi | Gorgo "Hammerjaw" Shumoku | Fish-Man pirate |
| Whisky Peak | Mr. Doubloon (Gaspard Kinkasu), Miss Farthing (Penny Kozeni) | Ledger agents |
| Little Garden | Astrid Kamameshi & Ylva Seiro (giant cooks); Mr. Shilling (Cyrus Rosoku, Wax-Wax) | duelists; Ledger agent |
| Drum Island | Doctor Quentin Panacea | quack |
| Alabasta | King Rashid Sunabashi; Madam Sovereign (Celestine Ginka) | ruler; Ledger VP |
| Jaya | Usurio "the Debt Collector" Shakkin | loan shark |
| Water 7 | Guildmaster Hollis Kugiuchi | ruler |
| Sabaody | Augustin "Gavel" Rakusatsu | auctioneer |
| Dressrosa | King Florian Castañuela | puppet king |
| Wano | Shogun Hagane Oboro | puppet shogun |

### 7.9 Recruitable crew (18)
Each recruit has a **recruitment quest** on their home island (complete it, then ask them to join) and a
**personal quest** they offer once they sail with you. Crew follow their captain, fight the captain's enemies,
never hit crewmates, can revive a knocked-down captain, and are saved per character. If the captain's character
dies for good, the crew disbands and every member goes home.

| # | Crew member | Role | Race | Fruit / style | Home | Recruitment (after the island arc) | Personal quest |
|---|---|---|---|---|---|---|---|
| 1 | **Odile "the Weathervane" Kazamidori** | Navigator | Human | Weather staff | Loguetown | `recruit_odile`: recover her stolen almanac pages (5 bandits) | `personal_odile` "The Impossible Almanac": chart Drum Island, Alabasta and Water 7 |
| 2 | **"Little" Hilde Kamameshi** | Cook | Giant | Hammer, Brawler | Little Garden | `recruit_hilde`: after Mr. Shilling falls, win Grandma Astrid's blessing and save the pantry | `personal_hilde` "A Taste of Every Sea": the Baratie, Alabasta and 8 pirate larders |
| 3 | **Dr. Pippa Frostwhistle** | Doctor | Long-Arm | Life Return | Drum Island | `recruit_pippa`: after Dr. Panacea falls, do her rounds and guard the clinic | `personal_pippa` "The Cure for Seastone": Punk Hazard's labs and Professor Minerva |
| 4 | **Kanna "the Mortise" Dovetail** | Shipwright | Human | Anchor Brawl, chain | Water 7 | `recruit_kanna`: after the CP agents fall, clear her family's name with the Guildmaster | `personal_kanna` "Her Father's Keel": the cells of Enies Lobby |
| 5 | **Petra "Pocket" Marlinspike** | Sniper | Human | Sniper, musket | Shells Town | `recruit_petra`: break Iron-Collar's hired muscle | `personal_petra` "A Clean Shot": Sabaody and 3 pirate captains |
| 6 | **Carmen "Brass Thunder" Trombetta** | Musician | Human | Bell-Bell (original) | Orange Town | `recruit_carmen`: reunite the band after Rotgut falls | `personal_carmen` "The Song Under the Sea": a duet with Coralie at Sabaody |
| 7 | **Sefa "the Stone-Reader" Ishibumi** | Archaeologist | Human | Sand-Sand | Alabasta | `recruit_sefa`: read a poneglyph and tell the King | `personal_sefa` "Words in Red Stone": read 3 poneglyphs |
| 8 | **Tomas "Steady Hand" Kajitori** | Helmsman | Human | Gunslinger | Syrup Village | `recruit_tomas`: after Gizmo falls, sail the regatta course to Mirror Ball Island | `personal_tomas` "The Wreck at Reverse Mountain": back to the Twin Capes |
| 9 | **Kenji "the Carving Knife" Escoffier** | Swordsman | Human | Two-Sword, dual blades | Baratie | `recruit_kenji`: after the Gourmand falls, deliver the Vice Admiral's bill | `personal_kenji` "The Last Blade's Lesson": duel at Kuraigana |
| 10 | **Kaimon "Seawall" Funaboshi** | Fighter | Fish-Man | Fish-Man Karate | Cocoyasi | `recruit_kaimon`: after Hammerjaw falls, make peace with the village | `personal_kaimon` "Two Shores": free the captives at Sabaody's auction |
| 11 | **Aria "Skylark" Fuusen** | Lookout | Sky People | Sniper, bow | Jaya | `recruit_aria`: after the Debt Collector falls, test-fly her balloon | `personal_aria` "The Way Home": back to Jaya's launch site |
| 12 | **Ignatz "Abacus" Sorobanji** | Quartermaster | Human | Gunslinger | Whisky Peak | `recruit_ignatz`: fight off the Ledger's enforcers | `personal_ignatz` "Balancing the Books": bring down Madam Sovereign |
| 13 | **Nimbus "Ninefold" Kasumi** | Spy | Human | Fog-Fog (original), Rokushiki | Enies Lobby | `recruit_nimbus`: after Director Hanzaki falls, stop her old squad | `personal_nimbus` "Ninth Shadow": confront CP0 at Egghead |
| 14 | **Gunnar "Mossback" Iwakoke** | Bodyguard | Stonehide | Hasshoken | Long Ring Long Land | `recruit_gunnar`: beat Ayame fairly, then shake her hand | `personal_gunnar` "Mountain Kin": visit Elbaph's giants |
| 15 | **Rin "the Moonlit" Tsukishiro** | Swordswoman | Human | Iaido | Wano | `recruit_rin`: after the shogun falls, see Onigashima | `personal_rin` "Moon over Onigashima": defeat Backwash |
| 16 | **Pashmina "Tigerlily" Torafu** | Warrior | Mink | Electro | Zou | `recruit_pashmina`: earn the Thunder-Mane's leave | `personal_pashmina` "The Full-Moon Hunt": defeat Riptide |
| 17 | **Bolt Nejimaki** | Engineer | Tontatta | Sniper, slingshot | Green Bit | `recruit_bolt`: after the Drill Lord falls, field-test his inventions | `personal_bolt` "Big Ideas": trade blueprints at Egghead |
| 18 | **Elowen Hoshizora** | Stargazer | Lunarian | Sniper, star-bow | Egghead | `recruit_elowen`: escort Minerva past the raiders | `personal_elowen` "The Star That Doesn't Move": reach Lodestar Island |

How each path frames the crew: pirates call it a crew; Marines a squad of deputised specialists; Revolutionaries a
cell; bounty hunters a hunting party; civilians a travelling company. The mechanics are the same.

---

### 7.10 The White Sea (Skypiea)
| Character | Title | Race | Fruit / style | Home |
|---|---|---|---|---|
| Cirrus Tenjōin | "God", the Storm Throne | Sky People | Weather-Weather (original), Storm Dance, weather staff | Upper Yard |
| Ilse Wanazawa | Priestess of the Trial of Snares | Sky People | Trap-Trap (original), spear | Upper Yard |
| Garamond Kusabi | Priest of the Trial of Links | Sky People | Chain-Chain (original), anchor chain | Upper Yard |
| Lulla Yumeshiro | Priestess of the Trial of Slumber | Sky People | Sleep-Sleep (original), bo staff | Upper Yard |
| Oswin Fūkyō | Priest of the Trial of Gales | Sky People | Impact Dial, Storm Dance | Upper Yard |
| Wakiza Ōtori | Chief of the Shandian guardians | Sky People | Storm Dance spear (teaches it) | Upper Yard (Shandora) |
| Ayasha Ōtori | Bell-Ringer of Shandora | Sky People | Life Return | Upper Yard (Giant Jack) |
| Amarante Kumoi | Gatekeeper of Heaven's Gate | Sky People | — | Heaven's Gate |
| Ferris Kaigara | Dial-Smith of Angel Island | Sky People | — (sells dials) | Angel Island |
| Lumi Hanegumo | Waver courier | Sky People | Storm Dance staff | Angel Island |
| Isidore Kumoyomi | Eldest scholar of Weatheria | Sky People | — | Weatheria |
| Penelope "Pinwheel" Kazaguruma | Weathercaster | Sky People | weather staff | Weatheria |

### 7.11 Fish-Man Island (Ryugu Kingdom)
| Character | Title | Race | Fruit / style | Home |
|---|---|---|---|---|
| King Nereo Umisachi | the Tidecrown | Merfolk | Fish-Man Jujutsu, trident | Ryugu Palace |
| Princess Marisol Umisachi | the Pearl of Ryugu | Merfolk | — | Ryugu Palace |
| General Tetsuo Sawara | "Ironfin", Commander of the Coral Guard | Fish-Man (swordfish) | one-sword style | Ryugu Palace |
| Madame Perla Sango | Proprietress of Mermaid Cove | Merfolk | — | Mermaid Cove |
| Tidwell Kaisō | Keeper of the Sea Forest | Fish-Man | Fish-Man Karate | Sea Forest |
| Captain Lorcan Ankō | "the Lantern of the Abyss", Abyssal Tide | Fish-Man (anglerfish) | Fish-Man Karate, trident | Fish-Man Island outskirts |
| Doctor Sculpin Okoze | the Venom-Druggist | Fish-Man (stonefish) | Medicine-Medicine (original) | Fish-Man Island outskirts |
| Queen Amaya Umisachi | the late queen (never appears, like the Saltfire King) | Merfolk | — | her grave is in the Sea Forest |

### 7.12 Islanders
The 25 original islanders of §3 "Local stories" (Granny Wren to Old Cinder) are friendly notables living on their
islands; Duchess Peony is a rival who yields when beaten in the pageant ring.

## 8. Rumours and world texture

Every island with an arc has local rumours (`rumor.grandline.story.<island>`) that NPCs there tell after their usual
gossip. The *Sea-Gull Gazette* headline changes are left to the event system; the Saltfire's anniversary night is a
planned world event (blue flame on the horizon, Log Poses spin, everyone's mood improves).

## 9. Implementation notes

- Notables spawn as `HumanoidNpc`s when a player comes within ~160 blocks of their home position (once per world).
  `NotableState` (SavedData `grandline_notables`) records each one as unspawned / alive / recruited / defeated /
  arrested. A notable that dies is never respawned; its Devil Fruit returns to the world pool.
- Quest givers that are notables use `giverRole = "notable:<id>"`. If such a giver is gone for good, the islanders
  on that island offer its quests instead, and objectives that need to talk to or defeat a fallen notable are
  credited when the player visits where they lived.
- Crew membership is stored per character id (`grandline_crews`), survives restarts, and follows the captain
  across the sea (members far behind are "aboard" and step off when the captain is back on land).
- Notables live in whichever plan holds their home island: the ocean world, the White Sea (`grandline:sky`) or the
  deep sea (`grandline:deep_sea`). `NotableSpawner` scans every level that has players with that level's own plan.
- Objective types that progress: `kill` (role), `defeat` (notable; beaten, yielded or arrested), `talk` (role or
  notable), `discover` (island id, `any`, `any_canon`), `poneglyph`, `arrest` (role, faction or notable),
  `ship`, `arrested` and `collect` (item id or `#tag`: counted from the inventory on pickup and every two seconds,
  handed over when the quest completes — `quest/QuestCollect.java`), `reach` (a generated story place) and `slay`
  (island beasts and Sea Kings; `quest/story/StorySites.java`). Objectives naming a notable who is already gone
  (`defeat`, `talk`, `arrest`) are credited when the player visits where they lived.
