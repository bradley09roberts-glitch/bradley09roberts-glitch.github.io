# Several players: trust, bonds, a job board, mourning, notes, deliveries and siege nights

This part of Hardcore Friends 2.0 is for worlds with more than one player. One player owns the camp and chooses whom
to trust. Each friend gets to know each player. The camp posts what it needs, and anyone can bring it. When a player
dies, the friends mourn them and keep their things safe. Players can leave notes for the camp, get deliveries at a
mailbox of their own, and, if the server wants it, face a siege night together.

On a world you play alone, nothing here gets in your way.

## The camp's owner and the players they trust

The first player to change anything becomes the camp's **owner**: setting the camp, recruiting someone, giving an
order, opening a backpack. In a world from before this update, that is the first player to use a `/friends` command
that changes something. The owner is told when this happens.

While `requireTrust` is on (it is by default), only the owner and the players they trust may:

- give orders: `follow`, `stay`, `work` and `dismiss`;
- recruit the named friends, or ask a newcomer to join (anyone may still talk to a stranger and hear what they would
  like);
- open a friend's backpack (sneak + right-click, or `/friends backpack` up close);
- hand a friend anything other than food they eat (rotten flesh and the like are not food here);
- put a friend on a lead;
- move the camp or the supply chest (`/friends camp set`, `/friends chest`);
- change how chatty the friends are (`/friends chatter`);
- ask for a delivery (`/friends send`).

Anyone may look: `list`, `needs`, `where`, `camp`, `plan`, `gear`, `skills`, `trips`, `jobs`, `bond`, `notes` and
the rest. Anyone may hand a friend **food** they eat (bread, meat, apples and so on): kindness is always allowed.
Anyone may bring the camp what it asks for (`/friends deliver`) and leave a note.

A player who is not trusted gets a polite "no" (a friend nearby says so too) and is told whom to ask.

The host of a single-player world, including one opened to LAN, and server operators are always allowed. A player who
plays alone (the host, or the only player online on a server) is never limited: every friend follows them when asked,
and no friend refuses them.

**An untrusted player keeps no hold on the friends.** While `requireTrust` is on:

- a player who is not trusted cannot hurt a friend at all, not even with a sneaking hit, so nobody can kill a friend to
  take their backpack;
- when the owner takes a player's trust back (or trust is switched on), friends following that player, or on their
  lead, let go within a few seconds. The lead drops at the friend's feet. In the camp's world they go back to work. In
  another world (the Nether, say) they wait where they are rather than wander about lost, and the camp's players are
  told where to fetch them (`/friends follow` once you are near).

**The camp keeps running only for its own players.** Survival's camp loader (the camp lives on while you are away)
now runs only while the owner or a trusted player is online. Other players being online does not keep the camp
loaded.

| Command | Who | What it does |
|---|---|---|
| `/friends trust <player>` | the owner | Trust a player (online, or one the camp has seen before) |
| `/friends untrust <player>` | the owner | Take that trust back |
| `/friends trusted` | anyone | Who owns the camp and who is trusted |
| `/friends owner <player>` | the owner | Hand the camp over. The old owner stays trusted |

## A bond with each player

Every friend, named or newcomer, has a bond with every player, from -100 to 100. It starts at 0.

**It grows** (each kind of gain is capped per friend and player each in-game day):

- handing them food (+3), more if they were hurt and the food heals them (+5); other gifts (+2). At most 12 a day.
- hurting a monster that hurt them in the last 15 seconds (+1, at most 6 a day);
- delivering what the camp needs while they are within 16 blocks (+2, at most 8 a day);
- spending time within 16 blocks of each other (+1 a minute, at most 4 a day).

**It falls** when you:

- hit them on purpose (a sneaking hit): -8 each time (while trust is required, only the owner and trusted players can
  hurt a friend at all);
- dismiss them: -15. One of the nine friends who comes back later remembers this.

When a friend dies, their bonds go with them. Whoever comes back under that name later is somebody new.

**What a bond does:**

| Bond | Effect |
|---|---|
| 60 and up ("close") | When you are hurt by a monster within 16 blocks of them, they go for it first. When you are hungry, they feed you before anyone else |
| 40 and up ("warm") | A warm hello when you right-click them |
| -30 and below ("cool") | A cool hello |
| -50 and below ("distant") | They will not follow you, and say so politely. A friend already following you who falls this far goes back to work once you are both back in the camp's world (away from it, they stay with you rather than be left lost). Never on a world you play alone |

Nothing here ever makes a friend hurt a player.

`/friends bond` shows your bond with every friend. `/friends bond <name>` shows one friend's bonds with every player.

## The job board

`/friends jobs` lists what the camp needs right now, numbered, most pressing first:

1. what the builder (or Terra levelling a site) is short of, item by item, for example "Building: 12 planks";
2. requests from other parts of the mod, such as Sage's plan (for example "10 iron ingots for the anvil");
3. the camp's everyday needs, when they are pressing: food (counted in loaves' worth), wood, cobblestone, dirt,
   torches (coal counts as 4), iron and seeds.

It also shows who has helped the camp most, and how much you have helped.

**Bringing things:** stand within 8 blocks of the supply chest and use:

- `/friends deliver` for the building and camp materials on the board (planks, logs, cobblestone, dirt, torches, coal,
  iron, seeds...). It never gives food, and never what Sage's plan wants (diamonds, ender pearls and the like). Those
  jobs are marked "(by number)" on the board.
- `/friends deliver <number>` for one job: the one you saw under that number the last time you used `/friends jobs`
  (in the last five minutes), even if the board has changed since. This is the only way to give food or what the plan
  wants. If that job is no longer needed, you are told and nothing is taken.

In a Hardcore world your kit is your life, so delivering is careful:

- only what a job still asks for is taken, and only from your **main inventory**: never your hotbar, armour or off
  hand;
- never tools, weapons, armour, shields, flint and steel or anything else that wears out, never a bucket (empty or
  full), a totem, a golden apple or a golden carrot;
- never anything enchanted, renamed or worn;
- a thing that fits several jobs (iron for the camp and for the plan, say) counts towards each, so you never give it
  twice over;
- what you deliver counts against its job until the board catches up (for up to five minutes), so using
  `/friends deliver` twice does not take the same things again.

For each delivery:

- the camp gains Unity (+1 for every 16 things, at most 20 a day);
- every friend within 16 blocks grows closer to you;
- one of them says thank you;
- your "helped the camp" tally goes up.

## When a player dies

**In a Hardcore world**, the friends who were closest to the player say goodbye, and the closest is heard by everyone.
The camp remembers them: `/friends camp` lists everyone the camp has lost, with the day, the cause and the place.

**In any world**, for five minutes after a player dies, friends within 48 blocks of the spot who are at work stop what
they are doing to keep the dropped items safe. They pick up the items lying within 8 blocks of where the player died
into a bag named after them ("Bob's Belongings"). Then they take the bag to the supply chest. Everyone is told who is
gathering the things and where the bag will be. Using the bag empties it into your inventory, like a friend's backpack.

Friends are never sent into whatever killed the player. They only go:

- when a monster (or another creature) killed the player, or hunger, poison or a potion did. Never after lava, fire,
  a fall, drowning, freezing, lightning, an explosion (a creeper's too), the void, suffocation or anything else the
  place itself did, since it could catch a friend there too;
- by day, in a world with day and night (never in the Nether or the End);
- when the spot is lit (daylight in the open, or a well-lit place: never a dark cave) and not in or next to water or
  lava;
- with no monster within about 24 blocks of the spot (a skeleton's bow reaches that far from the items' edge). They
  stop and head for the chest as soon as one comes near.

Each item they fetch must lie in the light, out of water and not next to lava. An item a friend cannot reach is left
for the rest of the five minutes. Several friends help at once, each with different items. When there is nothing left
they can safely fetch, they go back to what they were doing.

The bag keeps the things together, so no friend wears the armour or eats the food. Items another player threw down
nearby are never taken, nor a fallen friend's backpack lying nearby (it stays where everyone was told it lies). If the
supply chest has no free slot, the bag stays safe in the friend's backpack until it has. Nothing about the player's own
death, respawn or spectator mode changes.

## Notes

`/friends note <text>` leaves a note for the camp. Anyone may leave one, spectators too, at most one every 30 seconds,
up to 200 characters. The next time a friend is at the camp, awake and not fighting, with a player within 16 blocks,
they read it out ("A note from Bob! It says: ..."). The last 20 notes are kept. `/friends notes` lists them, and
shows which have not been read out yet.

## Mailboxes and deliveries

Look at a chest or barrel of your own, within 5 blocks, and use `/friends mailbox` to make it your mailbox. It cannot be
the camp's supply chest, someone else's mailbox, a locked container, or a loot chest nobody has opened. Nor can a
mailbox become the supply chest: `/friends chest` refuses it, and `/friends camp set` passes it by. Friends only ever
**put things into** your mailbox. They never take anything out, and never touch any other container.
`/friends mailbox` on its own shows where your mailbox is. `/friends mailbox remove` stops deliveries.

Deliveries go to the players who may give the friends orders (the owner, trusted players, the host and operators, or
everyone when `requireTrust` is off) whose mailbox is in the camp's dimension, within `maxDeliveryDistance` blocks of
the camp (400 by default, 600 at most: a delivery is a day trip there and back).

- **Ask for something:** `/friends send <item> <count>` (up to 64, for example `/friends send torch 16`). You can
  have up to 3 requests waiting at once. The friend packs as much as the chest has when they set off. A request not
  carried out within three in-game days is dropped, and you are told.
- **A share of the plenty:** every 3 in-game days, when the camp has plenty, you get a share: 8 of its most plentiful
  food (when the camp is well fed and the chest holds 32 or more), 16 torches (when it holds 48 or more), 8 coal (32
  or more) and 16 arrows (64 or more). The camp always keeps a good store.

A delivery is a trip like survival's trading trips. It happens by day, one friend at a time, only for a friend who is
healthy, fed and carrying food, and only with daylight enough to get there and back. Rowan (and newcomer foragers)
are keenest. The land around the friend keeps running on the way (survival's roaming loader). Hurt, hungry, tired, a
storm or the evening send them home early with the delivery. You are told when they set off, what they left, and when
they had to turn back or could not reach your mailbox; a delivery you asked for then goes back in the queue for
another trip. A mailbox they cannot reach is left alone for the rest of the day. A mailbox that is gone is forgotten.
Anything that did not fit goes back into the supply chest. Only what was packed for you is delivered: the friend's own
food and things of the same kind stay with them.

## Fair play on a server

| Setting | Default | What it does |
|---|---|---|
| `requireTrust` | `true` | Only the owner and trusted players may give orders, recruit, open backpacks, move the camp, ask for deliveries, or hurt a friend. It also decides who keeps the camp loaded |
| `maxFollowersPerPlayer` | `4` | The most friends who may follow one player at once, so nobody leads the whole team away from the camp. Not for a player who plays alone |
| `maxDeliveryDistance` | `400` | How far from the camp a mailbox may be, in blocks (0 turns deliveries off, 600 at most) |
| `maxSettlersPerPlayer` | `6` | (settler package) The most newcomers one player may ask in |
| `maxRoamingFriends` | `3` | (survival package) The most friends away on trips (exploring, trading, deliveries) at once |
| `siegeNights` | `false` | Siege nights, below |

## Siege nights (off by default)

Set `siegeNights` to `true` in `config/hardcorefriends.json` for a group event. Once the camp is a **Village**, every
five to eight nights a wave of monsters gathers at the edge of the camp at midnight.

- At dusk, a friend warns everyone: "Something's stirring tonight. Stay close." So players can come and help.
- The wave is 4 to 12 zombies, skeletons and spiders. It is never creepers, which would blow up buildings. A bigger
  camp, more friends and more players nearby mean a bigger wave.
- They only appear on one side, just outside the camp's edge, on dark ground where monsters could spawn anyway (a
  well-lit edge keeps them away). They never appear on or right beside anything built (the friends' work, or anything
  that looks player-made: paths, fences, chests...), within 24 blocks of a player or within 8 blocks of a friend. Each
  goes for the nearest friend or player.
- At dawn the wave's monsters still near the camp are gone (one a player has named stays).
- The wave only comes if a player is within about 110 blocks of the camp. Monsters far from every player would simply
  vanish. With nobody near, or on Peaceful, the night passes quietly.
- If every friend and every player in the camp's dimension lives to see the dawn, the camp gains 30 Unity.

## Honest limits

Nothing here has been run in the game yet. It compiles, and was checked by reading it against the game's own
sources, but it has not been played.

- **Who becomes owner.** In a world from before this update, whoever first changes something becomes owner, even if
  someone else set up the camp. The owner (or an operator) can hand it over with `/friends owner`.
- **Trust is all or nothing.** A trusted player may do everything the owner may, except choose whom to trust. There
  are no finer permissions.
- **Name tags.** The game applies a name tag before the friends get a say, so any player can still rename a friend
  with one. This changes only the name shown above them, not who they are.
- **Bonds are counted, not felt.** Bonds follow fixed amounts and daily caps. A player can grow a bond by delivering
  things, taking them back out of the supply chest and delivering them again (within the daily caps). The "helped the
  camp" tally can be padded the same way. Unity from deliveries is capped at 20 a day.
- **Healing.** Only food handed to a hurt friend counts as healing them. Splash potions thrown at a friend do not
  count towards a bond.
- **Defending first.** "Defending first" means the friend picks your attacker as their target if they could stand and
  fight it. It does not make a cautious friend braver.
- **Job board.**
  - The building jobs are read from what the builder last said they were short of, and stay on the board for up to
    five minutes, even if the builder has since found the materials elsewhere. The everyday targets are the camp's
    own. The plan's wants appear only once that part of the update adds them.
  - A delivery counts against its job until the job's own wording changes (for up to five minutes). If the camp uses
    the things straight away, the board may ask for a little less than it really needs for a while.
  - Numbers from `/friends jobs` are good for five minutes. After that, look again before `/friends deliver <number>`.
  - Since buckets, flint and steel, tools and golden food are never taken from a player, a job asking for one (Sage's
    plan may want a bucket or flint and steel) can only be filled by putting it in the supply chest yourself.
- **Keeping things safe.**
  - Friends gather a fallen player's items only by day, in the light, with no monster near, and only after a death
    whose cause is gone (a monster, hunger). After lava, a fall, drowning, an explosion and the like, and at night, the
    items stay where they lie, and dropped items vanish after five minutes, as in vanilla. This is on purpose: in a
    Hardcore world, a friend's life is worth more than the things.
  - A spot right next to water (a riverbank, a beach) counts as unsafe, so items lying there are left too.
  - In a world that is not Hardcore, a respawned player racing back for their things may find a friend got there
    first. The things will be in the supply chest.
  - If the dead player leaves the server straight away, the everyday tidying may put some of their items loose in the
    chest instead of in the bag. Items a player threw down after the death and then logged out may be taken into the
    bag.
- **Trust.** A friend who was following an untrusted player in another world waits where they are until someone from
  the camp fetches them; nobody else can order them, and if nobody comes they wait there. In the camp's world, a
  friend let go far from the camp walks home on their own.
- **Deliveries.** A friend on a delivery may eat food from it if they get very hungry on the road. If the delivery is
  interrupted (a fight, nightfall), the everyday deposit job may put some of it back in the chest. They then deliver
  what is left. A delivery can be blocked by rivers, ravines or mountains, like any trip. A mailbox more than about
  500 blocks away is only reached when a friend can set off first thing in the morning.
- **Siege nights.**
  - The wave's monsters despawn like any others when no player is near. Those still near the camp at dawn simply
    vanish, even mid-fight.
  - "Everyone survived" counts every player death in the camp's dimension that night, wherever it happened.
  - Turning `siegeNights` off in the middle of a siege night lets that night finish.
- **Notes.** Notes are read out once, in the order they were left, to whoever is near. Anyone can fill the list of 20
  and push older notes out.
