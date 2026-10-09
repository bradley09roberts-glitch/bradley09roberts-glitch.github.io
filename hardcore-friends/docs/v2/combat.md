# Combat, gear and safer nights (2.0)

This part of the update makes every friend a better fighter and fixes the problems found in the first version of the
night watch. Friends wear the best armour they can get, carry shields, shoot bows, heal themselves in an emergency and
fight as a team. Nights are calmer: friends no longer wake the whole camp over a mob nobody can reach.

## Gear for every friend

Before this update only Aegis took armour and weapons from the supply chest. Now everyone does.

- **Armour.** Every friend wears the best armour they have. A better piece in their backpack (one you handed them, for
  example) goes on straight away, and the old piece goes into the backpack. When the supply chest has a better piece
  than they are wearing, they walk over and put it on. Whatever they took off goes back into the chest for the next
  friend.
- **Shields.** A friend with an empty off hand takes a shield from the chest.
- **Weapons.** A friend takes a better sword or axe from the chest. Aegis, Scout and Sage hold a new sword in their
  hand, and Oak and Rowan hold a new axe. Anyone else carries the weapon in their backpack and takes it out for a
  fight, so the farmer keeps her hoe in her hand. A friend never puts their own work tool back in the chest.
- **Who goes first.** The chest's best pieces go where they matter most. Aegis chooses first. Next come the friends
  who fight at night: whoever is on watch or kept a watch lately, Scout and Sage, and anyone with a sword or axe.
  Everyone else chooses after them. Two friends never go to the chest for the same piece.
- **Bows and arrows.** Scout, Sage and Aegis take a bow from the chest first, then anyone else if there are more. A bow
  carrier with fewer than 16 arrows tops up to 32.
- **Emergency healing.** Each friend takes a golden apple or a potion of healing or regeneration from the chest: two
  for Aegis, one for everyone else. They never take an **enchanted** golden apple from the chest.
- **Room to carry it.** A weapon carried for fights, a bow, arrows and healing go into the backpack, so a friend only
  goes to the chest for them with room in their backpack. Armour and shields are worn, so they always fit.
- This also happens at night. The friend on watch gears up before starting their watch.

**Tip:** the supply chest is shared. If you keep your own spare armour, bows, golden apples or potions in it, the
friends will use them. Keep anything you want for yourself in a different chest.

Friends keep their armour on, and depositing never empties their backpack of their weapon, their bow, up to 64
arrows, a spare shield, their emergency healing or a fire resistance potion.

## The smith

When the supply chest has spare materials, a friend makes gear at the camp's crafting table. The table must be within
8 blocks of the supply chest or the camp centre. Any friend may be the smith, one at a time, by day. The smith fetches
exactly the materials needed, makes one piece (or a batch of arrows), and puts the gear and any leftovers in the
chest. The gear job then hands the gear out. Things are made in this order:

1. **A shield for every friend** (6 planks and 1 iron ingot). These are cheap and well worth having, so they come first.
2. **A sword** for any friend with no sword or axe at all: diamond, iron or stone, whichever the chest can spare.
3. **Armour for the fighters**: chestplate, then leggings, then helmet, then boots. Diamond, iron or leather, whichever
   is the best the chest can spare and is better than what they have.
4. **Bows** (3 sticks and 3 string) for Scout, Sage and Aegis, then for the others if there is plenty of string.
5. **Arrows** (1 flint, 1 stick and 1 feather make 4) while the chest holds fewer than 32. Feathers come from the
   chickens in the pen, flint from gravel and string from spiders.
6. **Armour for everyone else.**

Only spare materials are used:

- **Diamonds:** at least 7 always stay in the chest. That is enough for a diamond pickaxe (3) and an enchanting table
  (2), with some to spare.
- **Iron:** at least 5 ingots always stay in the chest, enough for one of Spark's hoppers. A shield may use iron down
  to the last ingot.
- **Cobblestone:** at least 16 always stays in the chest.
- **Building materials:** while the building Oak is working on is short of iron, wood or stone, the smith does not
  use that material at all.
- **Sage's plan:** what the plan is collecting stays in the chest: iron in the iron age, leather for the library's
  books, a flint for the flint and steel, the diamonds it keeps back. While the plan is still short of one of these,
  the smith uses none of it. Once it has enough, only what is beyond the plan's amount is used.
- **The smith's own things:** nothing the smith carries for their own work is ever used up. A smith called away
  mid-job (by nightfall or a fight) remembers what they fetched and puts it back in the chest first thing next day.

## Bows

Anyone carrying a bow and at least one arrow can shoot.

- **What they shoot:**
  - skeletons, strays, witches, blazes, pillagers, phantoms, ghasts and breezes, at up to 16 blocks;
  - creepers, from at least 7 blocks away (better than running; closer than that, they still back away);
  - any hostile, if the friend has no sword or axe, is hurt, or is Scout or Sage (they prefer the bow);
  - any hostile nobody can walk to: one on a ledge, behind a fence, or in the air.
- **Aegis** prefers his blade. He shoots only what he cannot reach, and creepers.
- **Distance:** a healthy friend shoots from 3 blocks out. A hurt friend keeps 6 blocks away from anything that fights
  hand to hand, and backs away when it comes closer. Up close, a friend with a sword or axe uses it instead.
- **How a shot works:**
  - Each arrow takes a full draw (one second), as yours does, then a short pause.
  - The friend aims above the target to allow for the drop, and the bow's enchantments count.
  - An arrow is used up unless the bow has Infinity. An arrow that misses can be picked up again.
- **A clear line of fire.** A friend never lets an arrow go while anyone or anything other than a hostile is in or near
  its flight, or just past the target. That includes you, other players, friends, villagers, golems, animals (yours,
  the camp's or wild ones), armour stands, boats and carts. They hold the draw and step to one side for a clear shot.
- **Kill credit.** When a friend kills a hostile, with a blade or an arrow, it counts as killed by a player: their
  leader, or else the nearest player within 32 blocks. So drops that only come from a player's kill (blaze rods, for
  example) still drop, and so does the experience, just as when a tamed wolf makes the kill. Animals the friends hunt
  or butcher are not credited.

Turn bows off with `friendsUseBows: false` in `config/hardcorefriends.json`. Friends then neither shoot nor fetch or make
bows.

## Shields

A friend with a shield in their off hand raises it, facing the danger, when:

- an archer within 20 blocks is drawing a bow on them, or on someone within 3 blocks of them;
- a creeper within 5 blocks starts to swell.

They lower it to strike, to draw a bow, and when the danger has passed. A raised shield blocks hits from the front, as
yours does, and wears down the same way.

## Emergency healing

A friend in a fight with less than 40% of their health (or 3 hearts or less) uses the best thing in their backpack.
"In a fight" means they have a target, a monster (or anyone) hurt them in the last five seconds, or they are falling
back with danger near. Hunger, falls, fire and drowning do not count, so a famine does not use up the camp's potions.
They use, in this order:

1. a potion of healing (it works at once);
2. a golden apple;
3. a potion of regeneration;
4. an enchanted golden apple, but only when nearly dead (below a quarter of their health).

The items are really used up, with their real effects. The empty bottle goes back into the backpack. A friend who is on
fire or in lava drinks a potion of fire resistance if they carry one. Normal food is still only eaten for hunger.

## Fighting as a team

- **Focus fire.** When there are several hostiles, a friend prefers one a teammate is already fighting, if they can get
  at it or shoot it.
- **Tag-out.** When a friend falls back hurt, the healthiest armed friend within 16 blocks takes over their fight, so the
  hostile does not follow them home.
- **Nobody turns berserker.** Friends without a bow still back away from creepers and keep out of archers' line of fire.
  Aegis keeps his guard duty.

## Safer nights

- **Mobs nobody can reach.** Inside the camp, a friend goes for a hostile out of sight (or more than 8 blocks away) only
  when a whole path leads to it. Before, a zombie in a fenced field, a spider on a roof or a skeleton on a ledge kept
  friends getting up all night.
- **Giving up.**
  - A friend who has gone 10 seconds without landing or taking a blow, and has no way through to the hostile, gives it
    up for 30 seconds. A long way round is followed to the end.
  - If they give up on the same hostile a second time, they leave it alone until dawn at night, or for five minutes by
    day.
  - A friend who is shooting at a hostile never gives it up.
  - A hostile they gave up on that comes within arm's reach, or starts landing blows on someone, is fought again
    straight away.
- **Sleepers wake only for real danger.**
  - A friend nearby who is just staring at a mob they cannot reach does not wake the camp. Sleepers get up when a
    friend within 16 blocks is trading blows (in the last five seconds) or has a hostile within 4 blocks.
  - A monster within 8 blocks wakes a sleeper only if it could get at them: it is within 3 blocks, or a path leads
    to it. A zombie shut in a fenced field next to the beds no longer wakes everyone each time they lie down. If it
    gets out, or hurts anyone, they wake as before.
- **No more false alarms from caves.** The watcher raises the alarm about any hostile inside the camp that they can
  see. Out of sight, a hostile only counts when it is on the camp's own ground: within 4 blocks of the watcher's
  height or the camp centre's, and with a path to it. A cave under the camp no longer wakes everyone.
- **One alarm per hostile per night.** A hostile that walks out past the camp's edge and back in is gone for again, but
  does not wake the camp a second time.
- **Thunderstorms by day.**
  - A storm darkens the sky, so friends still come home from the woods, the mine and the wilds.
  - In camp they carry on with the camp's work. Only a tired friend lies down for a nap.
  - No watch is kept: the watches follow the clock, so last night's watcher is no longer kept up all morning.
- **The watch stays at the camp.**
  - On his watch, Aegis only stays beside players inside the camp or within 8 blocks of its edge.
  - A watcher who is more than 24 blocks beyond the camp's edge for over 30 seconds hands the watch to someone at the
    camp.
  - When choosing a watcher, friends at the camp come before friends away from it.

## Commands

- `/friends gear` shows what each friend nearby wears and carries: armour points and pieces, their best weapon,
  shield, bow and arrows, and emergency healing. It needs no cheats and changes nothing.

## Settings

- `friendsUseBows` (default `true`): friends shoot, fetch and make bows and arrows.

## Corrections to GUIDE.md and DESIGN.md

These existing statements are no longer true, or were never quite true, and should be updated when this is merged:

- **GUIDE 6b, "Standing together":**
  - "Only Aegis goes after skeletons and other archers." Now anyone with a bow and arrows shoots them.
  - "gives up on it after about 10 seconds". Now: only when no path leads to it. A second give-up lasts until dawn
    (five minutes by day). Out of sight, or more than 8 blocks away, a hostile with no path to it is not chosen at all.
- **GUIDE 6b, last line:** "A thunderstorm darkens the sky as much as night does, so friends head home and rest through
  one by day too." Now friends head home and keep working in camp. Only tired friends nap, and no watch is kept.
- **GUIDE 6b, "Once asleep":**
  - "a friend nearby starts fighting" now means a friend trading blows, or with a hostile within 4 blocks.
  - "a monster comes close" now means a monster within 8 blocks that could get at them (DESIGN 6, `needs.sleep`,
    too).
- **GUIDE 6b, "The watch rota":** add that a watcher away from the camp for over 30 seconds hands the watch on, and
  that Aegis, on his watch, only stays beside players in or near the camp.
- **GUIDE 3 and DESIGN 11, Aegis:** "takes the best sword, armour and shield from the chest" is now every friend's job,
  with Aegis first. The smith is new.
- **GUIDE 11:**
  - "Only Aegis goes after skeletons and other archers; everyone else gets out of their line of fire." Friends with a
    bow now shoot them.
  - Friends also heal themselves with golden apples and potions.
- **GUIDE 13, Honest limits:**
  - The first bullet ("there is no second watch yet", "Sage carries no weapon") was already out of date before this
    update.
  - The cave-beneath-the-camp bullet is now true.
- **DESIGN 4, goals table:**
  - Add `BowAttackGoal` (priority 3, the melee goal's; the two never run together) and `ShieldGoal` (priority 2, no
    controls claimed).
  - `canStandAndFight` now also holds for a friend who would shoot the threat and has it within bow range
    (`Archery.standsWithBow`).
- **DESIGN 4, "The rally inside the camp":**
  - "a hostile beyond 8 blocks with no path to it is left alone for 10 s" was false: a partial path always counted as
    a path. Now only a whole path counts (`ai/goal/Reach`), for hostiles out of sight too, and "no path" is remembered
    for 10 s.
  - The 10-second give-up now also needs no whole path to the target. The second give-up lasts until dawn.
- **DESIGN 4, "The night is for sleep":** `Camp.isNight` is now `Camp.isNightTime` (dark and after 12000). The
  `NIGHT_JOBS` list has `combat.gear` instead of Aegis's gear job (`aegis.equip_gear` no longer exists).
- **DESIGN 6, "The night watch":**
  - The alarm rule is as described under "Safer nights" above.
  - Hostiles stay on the alarm list until 8 blocks beyond the camp's edge.
  - The rota ranks friends at the camp before friends away from it.
  - Newcomers are remembered in the rota by their own id rather than their archetype's name.
- **DESIGN 14, last line:** the daytime thunderstorm sentence, as above.

## Honest limits

- **Not run in game.** None of this has been tested in game yet. Everything here was checked by reading the code
  against the game's own sources.
- **Path checks are vanilla path finding.** A mob the path finder cannot reach within its search range (32 blocks)
  counts as out of reach even if a very long way round exists. A mob that becomes reachable (a gate opened) is noticed
  again within 10 seconds. To keep big fights cheap, a friend works out at most one new path every half second to
  choose between bow and blade and which fight to help with, so in a crowd it can take a few seconds to notice that a
  mob is out of reach and switch to the bow.
- **Aiming.** Friends aim as skeletons do. They do not lead a moving target, so a running mob is often missed at
  range, and arrows can glance off walls. Friends shoot at the speed and damage of a skeleton's arrow, not a fully
  drawn player's.
- **Shields:**
  - Shields only go up against archers drawing a bow (skeletons, strays, pillagers charging a crossbow) and swelling
    creepers. They do not go up against blaze or ghast fireballs, or against hand-to-hand attackers.
  - A shield only covers the front. A friend running away from a creeper may not be facing it.
- **The line of fire is cautious.** A wild chicken near the line stops a shot, so in a busy farmyard friends may hold
  their fire for a while. A chicken jockey cannot be shot at all.
- **Gear from the chest is everyone's.** The friends use whatever armour, weapons, bows, golden apples and healing
  potions you put in the supply chest (not enchanted golden apples).
- **Smithing:**
  - Smithing needs a crafting table within 8 blocks of the supply chest or the camp centre.
  - Iron armour for nine friends uses a lot of iron. The reserve of 5 ingots, the building check and Sage's plan
    protect what is needed now and for the plan's step in hand, not what a later step will need (the anvil's iron,
    for one).
  - In the iron age no iron goes to shields or armour until the plan has its iron; while the library is short of
    books, leather armour waits too.
  - A smith called away mid-job puts back next day only what they still carry of what they fetched; anything the
    chest has no room for goes with their next deposit trip.
  - The order of what gets made is fixed. Axes are never made: they are already tools for Oak and Rowan.
- **Weapons are ranked "any sword before any axe".** This is the old rule, so a stone sword counts as better than an
  iron axe.
- **Emergency healing is instant.** A friend eats or drinks in a moment rather than over a second and a half. There is a
  two-second pause between uses.
- **Kill credit.** The credited player gets the experience orbs and player-only drops on the ground where the mob died,
  even if they are not there to see it. Like a tamed wolf's kills, this can count towards that player's kill
  statistics.
- **Daytime storms.** Work in camp carries on in a daytime thunderstorm. Monsters do spawn under a storm sky, and no
  watch is kept by day, though everyone is awake and stands together as usual.
