# Test route

A short route through the game that touches everything the overhaul changed. It takes about an hour. Use a **new
world** (and, separately, a **copy** of an existing save to check the upgrade — never your only copy).

## 1. Start (10 min)

1. Create a world: World Type already shows *Grand Line Ocean World*. Make a human pirate from the East Blue.
2. You arrive on **Shiokaze Isle**. Press **U**: the quest log shows the chapter, who is involved, why, the next step,
   where to go and the danger. The minimap (top right) draws a yellow trail to it.
3. Type **/controls**: every key with its binding, the fight loop and how to read a foe. "Change keys…" opens the
   normal key menu.

## 2. Learning to fight (10 min)

1. Talk to the elder and take the combat lesson, or type **/spar**: a sparring partner appears. Its blows do not
   hurt you.
2. Press **R** (Combat Mode). A small card lists the basics once. Strike (tap), heavy blow (hold), guard (hold right
   click), parry (guard just before its blow), dodge (Left Alt), a technique (1–9). Each first success shows a
   one-line tip. Watch for the **red flash** over its head before a heavy blow.
3. The partner bows out at a third of its health.

## 3. Bandits and fairness (10 min)

1. Walk into the wilds until bandits come. Look at one: the **target frame** (top left) shows "· name, Bandit ·
   Wild, health, ⚔ Hostile, = Matched".
2. A tier-1 bandit should fall to about three light blows and take roughly a tenth of your health per light blow.
   With three or more around you, only two press you at once; the others circle and wait.
3. **Cover:** step behind a house corner or a wall. Their blows stop ("Blocked by cover" when you try it yourself,
   a spark on the wall). Through an open door, blows pass; through a closed door, they do not. A waist-high wall
   only covers your legs.
4. Run out of sight: after about 8 seconds they give up and go home.
5. If you are knocked down, outlaws rob you once and leave you be for a while.

## 4. Who is who (5 min)

1. Look at townsfolk (Neutral or Friendly), a Marine (Suspicious or Hostile if you are wanted), and any named
   character (◆ elite mark). Name tags never show through walls.
2. Optional (op): `/grandline test npc marine_captain 10 6 true` spawns an overwhelming foe in front of you: the
   frame shows ▲▲ Overwhelming and it warns you over the hotbar if it attacks.

## 5. The opening arc and the first recruit (20 min)

Follow the quest from Shiokaze Isle to **Shells Town** (the trail runs to the pier, across the sea, along the
streets). Play the arc to its end: the scenes, your choice, the fight with its boss (one tier above the island,
about one to three minutes for a prepared character), the town afterwards, and recruiting **Petra**. Then open the
crew roster (**V**) and give orders (**N** while looking at a foe: focus; elsewhere: regroup).

## 6. Save and quit, come back (5 min)

Quit to the menu and load the world again: your quest step, the journal recap, your crew and their roles are as you
left them; nobody is duplicated.

## 7. Upgrade an old save (separately)

On a **copy** of a world from 0.1.0 or 0.2.0, install the new jar and load it. Foes already in the world are re-tuned
when they load; quest progress continues from the equivalent step; crew members keep following. See
SAVE_COMPAT.md.
