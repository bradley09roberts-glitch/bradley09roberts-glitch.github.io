# Hardcore Friends 2.0: what to test

You asked me not to run any tests, so **none of 2.0 has been played**. Everything compiles, and every part was read
through by a separate reviewer against Minecraft 26.3's own code. The reviewers found 46 real problems (5 in the night
watch, 9 in combat and independence, 10 in Sage's plan and newcomers, 12 in the multiplayer features, 10 in
expeditions), and all 46 were fixed before this release; one more was checked and wasn't real. That is not the same as
playing it. 1.0's test results (still valid for the 1.0 parts) are in `TEST-RESULTS.md`.

**Use a copy of a world, or a new one.** 2.0 adds new world generation (survivor camps), so test in a fresh world first.

Below are the things most worth watching, roughly in order of risk. If something goes wrong, `/friends log` shows the
friends' last block changes, and the game log (`logs/latest.log`) shows any errors.

## 1. Start-up and world creation

- [ ] The game starts and a **new world loads** (new world generation and data files: survivor camps, damage types).
- [ ] An existing 1.0 world (a copy!) loads, and your friends are still there with their backpacks.
- [ ] `/friends help` lists the new commands.

## 2. Nights (the watch, the alarm, standing together)

- [ ] At dusk friends go to bed, and someone keeps watch (`/friends list` shows "keeping watch").
- [ ] A zombie walking into camp raises the alarm, sleepers wake and armed friends fight it, then go back to bed.
- [ ] A mob nobody can reach (in a fenced field, on a roof) does **not** keep waking the camp all night.
- [ ] A daytime thunderstorm: friends come home but keep working in camp.

## 3. Gear and fighting

- [ ] Put iron armour, a shield and a bow with arrows in the chest: friends put them on and pick them up (Aegis first).
- [ ] Friends with bows shoot skeletons, and **never shoot while you or a friend is in the way**. Their arrows never
  hurt you.
- [ ] A badly hurt friend in a fight drinks a healing potion or eats a golden apple from their backpack.
- [ ] The smith makes shields from spare planks and iron (with a crafting table near the chest).

## 4. Independence

- [ ] Walk 300+ blocks away (same dimension): the camp keeps working (crops grow, friends move). Server load stays OK.
- [ ] Scout goes on a far trip by day and reports villages and other places (`/friends trips`), and is home by dusk.
- [ ] With a village known and surplus in the chest, a friend goes trading and comes back with things.
- [ ] A building that needs room on bumpy ground: Terra levels the site, and **then stops** (she must never dig out
  the building afterwards). Oak builds on it.
- [ ] A friend caught far out at night builds a shelter, and **gets out of it in the morning** (stand next to it and
  watch).
- [ ] `/friends skills` shows levels going up.

## 5. Newcomers

- [ ] Walk into a new village: one or two strangers may appear near the bell. Right-click: they introduce themselves
  and ask for something. Bring it and right-click again: they join, with their own name and a default skin (Steve,
  Alex...).
- [ ] Find a survivor camp in newly explored land (tents, campfire, chest, strangers). With cheats on in a test copy:
  `/locate structure hardcorefriends:survivor_camp`.
- [ ] A traveller arrives at your camp after a few days (camp at stage 1 or more).
- [ ] Newcomers on the team work, eat, sleep and take orders by name (`/friends follow <name>`).

## 6. Sage's plan

- [ ] `/friends goals` shows the plan and the checklist for the step in hand.
- [ ] With an iron pickaxe, Flint starts a **deep mine**. Watch the first trips down: no digging straight down, no
  opening into lava, caves walled off, **and he always gets back up**.
- [ ] With a diamond pickaxe and a water bucket: obsidian from cave lava (not surface lava pools).
- [ ] Sugar cane grows at the farm; Sage makes paper and books (needs leather).
- [ ] The library, anvil and brewing stand get built when their materials are there.

## 7. Expeditions (the riskiest part: try with a test copy)

- [ ] `/friends party add <name>`, `/friends party go`: they follow you. Go through a Nether portal: they come with
  you, **with their backpacks and armour**. Come back: they come back.
- [ ] Leave one behind on purpose: they find their way through after you, or go home after 3 minutes.
- [ ] In the Nether: calm piglins are left alone; friends barter gold; blaze kills drop rods.
- [ ] The camp portal gets built and lit once the plan is Nether ready.
- [ ] Scout's stronghold search (a long trip of two or three days) ends with a marker and coordinates.
- [ ] In the stronghold, friends fill the End portal frames.
- [ ] **The dragon fight:** archers shoot crystals (never with anyone within 12 blocks), a friend climbs to caged
  crystals, fighters strike the perched dragon. **Don't shoot a caged crystal yourself while a friend is up beside
  it.** Friends can die here: it is Hardcore.

## 8. Several players (on a server or LAN)

- [ ] The first player to change something becomes owner. An untrusted player can't give orders, open backpacks,
  put friends on leads, or hurt friends. `/friends trust <player>` lets them.
- [ ] Single player: you are never refused, and `/friends follow all` takes everyone.
- [ ] `/friends jobs` and `/friends deliver`: only main-inventory items are taken, **never your hotbar, tools, armour,
  buckets or golden food**.
- [ ] When a player dies (by a mob, by day, somewhere lit), friends collect the items into a bag in the chest.
- [ ] `/friends mailbox` on your own chest, then `/friends send torch 16`: a friend delivers by day.
- [ ] Optional: `siegeNights: true` in the config, in a throwaway copy.

## Known limits worth knowing before you start

- A friend following you far from camp in the overworld stops when you log out or die there (nothing runs near them
  any more), and carries on when a player comes back. Friends in the Nether or the End are handled: they head home.
- The supply chest is shared: friends use armour, bows, golden apples and potions you leave in it.
- Books for the library need 46 leather; obsidian needs cave lava or lava below y = 40. These steps are slow.
- Plain dirt or stone you placed inside a site being levelled can look natural and be dug away.
- Each part's full "Honest limits" list is at the end of its file in `docs/v2/`.
