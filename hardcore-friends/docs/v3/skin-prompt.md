# Getting lots of skins made

The village people (newcomers, settlers, and children born in the camp) pick their looks from the mod's skin list.
The mod now ships with the 82-skin medieval village set (skins 1000 to 1081, see `people.md`) made from this prompt.
More skins still mean more variety: run the prompt again asking for new people (and new names), and they are added
from number 1082 up.

## The prompt

Copy everything in the box into whatever you use to make skins: an AI that can write image files, an artist, or a
skin-making site. It asks for skins in batches so you can stop at any point.

```text
Make Minecraft Java Edition player skins for the villagers of a medieval-ish survival village.

FORMAT (must be exact, or the game can't use them):
- Each skin is a 64x64 PNG with transparency, in the standard modern Java skin layout (the 1.8+ layout with
  the second "overlay" layer: hat, jacket, sleeves, trouser legs).
- Arms: "wide" (classic, 4-pixel arms) or "slim" (3-pixel arms). Say which for each one.
- Keep areas outside the skin's UV boxes fully transparent. The base layer must be fully opaque (no holes) so
  the body never shows see-through gaps; use the overlay layer for hats, hair volume, aprons, hoods, belts.
- Real pixel art: hand-placed pixels, 2-4 shades per colour, readable from a distance. No photos, no blur,
  no gradients smeared across the whole skin, no text or logos.
- Faces: friendly, varied, simple Minecraft-style eyes (2 pixels wide), no scary or gory looks.
- File names: lower case letters, digits and underscores only, ending in .png.
  Start the name with adult_ for a grown-up and child_ for a child. Example: adult_baker_rosa.png,
  child_pip_freckles.png. Put "wide" or "slim" in your reply next to each file, or deliver them in two folders
  named wide and slim.

THE PEOPLE (mix skin tones, hair colours and textures, ages, body shapes and genders across every batch;
roughly half wide and half slim; earthy, practical medieval-village clothes, each trade recognisable at a glance):

Batch 1, the trades (2 each, different people):
baker (flour-dusted apron), butcher (striped apron), fisher (oilskin coat, knitted cap), shepherd (wool cloak,
crook-carrier look), beekeeper (veiled hat on the overlay), mason (dusty tunic, leather gloves), carpenter (tool
belt), blacksmith (leather apron, soot), tailor (measuring tape, fine waistcoat), teacher (robe, spectacles),
doctor (herbalist's coat, satchel), shopkeeper (waistcoat, ledger), innkeeper (rolled sleeves, tea towel),
farmer (straw hat, overalls), guard (padded gambeson, simple helmet on the overlay).

Batch 2, everyday villagers (20): farmhands, travellers, an old grandparent or two, young adults, a mother and
father pair, a wanderer in a hooded cloak, a miner with a lamp-lit helmet, a hunter in green, a fisher's spouse,
a scholar, a bard. Plain, warm clothes; no armour, no fantasy creatures.

Batch 3, children (20, all named child_...): small kids' clothes (smocks, dungarees, little dresses, knitted
jumpers, scarves), some with freckles, gap teeth or rosy cheeks, varied hair. Keep the proportions of a normal
player skin; the game shrinks them. Nothing an adult would wear (no aprons of a trade, no armour).

Batch 4, biome villagers (12): desert (light linen, head wraps), snowy (fur-lined coats and hoods), jungle and
swamp (light fabrics, leaf-green and brown), savanna (warm earth tones), dark forest (deep greens and greys).

Deliver each batch as PNG files with a short list: file name, wide or slim, who it is.
```

## Where the files go

1. Put each PNG in the mod's skin folders (create them if they aren't there):
   - wide-armed: `hardcore-friends/src/main/resources/assets/hardcorefriends/textures/entity/people/wide/`
   - slim-armed: `hardcore-friends/src/main/resources/assets/hardcorefriends/textures/entity/people/slim/`
2. Run `python3 tools/add_skins.py` from the `hardcore-friends` folder. Every new PNG gets a skin number from 1000
   up; names starting `child_` are worn only by children and `adult_` only by grown-ups. Files that aren't 64x64
   PNGs, or have capitals or spaces in the name, are listed and skipped, so you can fix and re-run.
3. Rebuild the mod (`./gradlew build`) and give everyone the new JAR (the skin list is inside the JAR).

Or just send me the PNGs (or a zip) and I'll drop them in, check them and rebuild.

## Checking a skin before adding it

- Open it in any skin viewer (most skin sites have an upload-and-preview page). Look for see-through holes in the
  body, a hat floating off the head, or arms that look one pixel too wide or too thin (wide/slim mixed up).
- Never renumber or delete a skin a world already uses: people remember their skin by number. Adding more is
  always safe.
