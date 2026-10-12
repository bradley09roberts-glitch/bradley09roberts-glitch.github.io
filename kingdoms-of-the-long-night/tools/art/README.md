# Kingdoms of the Long Night: texture generator

Every texture of the `longnight` mod is generated procedurally from code in this
folder. The output is deterministic: all randomness uses fixed seeds, so a
re-run produces byte-identical PNGs.

```bash
python3 tools/art/generate_textures.py              # textures + previews + verification
python3 tools/art/generate_textures.py --no-preview # textures + verification only
```

You need Python 3.10+, Pillow and numpy. The script writes into
`mod/src/main/resources/assets/longnight/textures/`, prints a manifest and then
verifies the output. If any check fails it exits with status 1. The checks cover:
exact sizes, RGBA mode, binary alpha, pixels only inside the player UV layout,
opaque base faces on bodies, greyscale tint layers, no clothes on the face or
hands, armour only in overlay regions, opaque full-block textures, and the GUI
area. Running it does not create `__pycache__`.

| Module | Contents |
| --- | --- |
| `generate_textures.py` | Entry point. Generates, verifies and writes the previews. |
| `art_pixel.py` | Colour helpers (ramps, mix, tint multiply) and a small RGBA canvas. |
| `art_skin.py` | Player-skin UV layout, face-local painting, grey-level painter, compositing, flat views and the 3D preview renderer. |
| `art_bodies.py` | The 20 body layers: race face and ear templates, skin-tone table. |
| `art_clothes.py` | The 10 clothing layers, built from torso, sleeve, skirt, trouser, belt and boot blocks. |
| `art_hair.py` | The 10 hair layers, hand-authored per head face as digit maps. |
| `art_armor.py` | Optional mail and plate overlays. |
| `art_blocks.py`, `art_items.py`, `art_gui.py` | 16x16 blocks (plus one animated strip), 16x16 items, GUI art. |
| `art_preview.py` | Preview sheets in `preview/`. |

## Citizen layer system

Citizens are drawn with a vanilla `PlayerModel`: classic 4 px arms and the
modern 64x64 skin layout, with separate left arm and left leg. The same geometry
is rendered once per layer, in this order:

1. **body** `entity/citizen/body/<id>.png`. Full colour, **untinted**. Only the
   base UV regions are used; every base face is opaque. It holds the skin, the
   face (eyes with whites and iris, brows, nose shading, mouth), a linen
   undertunic (dark hide for orcs and goblins), short braies and shoes.
   Halflings have bare feet. All overlay regions are transparent.
2. **clothes** `entity/citizen/clothes/<id>.png`. **Greyscale, tinted** by RGB
   multiply. Covers torso, arms and legs in the base regions. Some outfits also
   use overlay regions (see below). Face and hands are always transparent.
3. **hair** `entity/citizen/hair/<id>.png`. **Greyscale, tinted**. Uses the
   head base region plus the hat overlay. Beards, braids and long hair also use
   the jacket overlay.
4. **armour** (optional) `entity/citizen/armor/{mail,plate}.png`. Full colour,
   **untinted**, overlay regions only.

A later layer replaces an earlier one wherever its pixel is opaque. Vanilla does
the same when it re-renders a model, for example the villager profession layers.
In the base regions, clothes cover the body and hair covers the head.

### Tint expectations

Tinted layers use 9 grey levels from 0.36 to 1.0: `92 108 126 146 166 188 210 232 255`.

* **Clothes.** The main fabric sits around 0.73–0.82. Trousers, leather and
  cloaks sit around 0.45–0.65. Trims, cuffs, buttons and lacing use 0.9–1.0.
  The tint is roughly the colour of the brightest trim, so pick a dye colour a
  little brighter than the fabric colour you want.
* **Hair.** The mid tone is about 0.82 and highlights reach 1.0, so tint with
  the colour of the hair's sunlit highlight.
* **Near-black accent.** A few pixels use 0.18 (`46`), which stays near-black
  under any tint. These are buckle prongs, drawstrings and the iron bead in
  `beard_long`.

Dye and hair tints used in the previews (they all read well):

| Clothes dyes | Hair |
| --- | --- |
| undyed `#d8cba8`, straw `#c8a860`, russet `#a07040`, leather `#8a5a3a`, madder red `#8a2a2e`, rust `#b05a40`, woad blue `#3a5a9a`, night blue `#2e3e70`, moss green `#6a8a4a`, forest `#4e5e3a`, purple `#5a3a7a`, grey-brown `#7a7060` | black `#201818`, dark brown `#4a3020`, brown `#6e4a2e`, chestnut `#8a5a30`, auburn `#a04a26`, ginger `#b8542a`, blonde `#e2c27e`, platinum `#ece2c4`, grey `#a6a4a0`, white `#dcdce6` |

### Ids

* **Bodies:** `human_1` (very fair) to `human_6` (deep brown); `elf_1` (pale),
  `elf_2` (golden), `elf_3` (dusk grey-blue); `dwarf_1..3` (ruddy); `halfling_1..3`
  (rosy cheeks, bare feet); `orc_1` (grey-green), `orc_2` (deep green), `orc_3`
  (ashen), all with small tusks; `goblin_1` (olive), `goblin_2` (sickly
  yellow-green), both with big ears and a sharp grin. Elves have taller, brighter
  eyes and pointed ears on the head sides. Dwarves have a broad nose and heavy
  brows.
* **Clothes:** `tunic`, `smock`, `apron`, `robe`, `dress`, `gambeson`, `noble`,
  `traveller`, `rags`, `fur`.
* **Hair:** `short`, `long`, `braided`, `bald` (brows and a short horseshoe
  fringe), `topknot`, `hood_short`, `beard_short`, `beard_long`, `bun`, `wild`.

### Overlay usage and compatibility

| Layer | Overlay regions used |
| --- | --- |
| clothes `traveller` | hat (hood), jacket (cloak), sleeves, pants (cloak behind the legs) |
| clothes `robe` | jacket (cowl), sleeves (bell cuffs) |
| clothes `noble` | jacket (short cape), sleeve tops |
| clothes `dress` | pants (flared skirt) |
| clothes `gambeson` | jacket (standing collar) |
| clothes `fur` | jacket (mantle), sleeves (pauldrons), pants (boot fur) |
| clothes `tunic`, `smock`, `apron`, `rags` | base only |
| hair `short` | hat top + front (quiff) |
| hair `long` | hat sides/front/back, jacket back and sides (hair down the back) |
| hair `braided` | hat front, jacket front + top (two braids over the chest) |
| hair `topknot`, `bun` | hat top + back |
| hair `wild` | hat on every face except the bottom |
| hair `beard_short` | hat front (chin row) + bottom |
| hair `beard_long` | hat front, bottom, cheek corners; jacket front (braided beard) |
| hair `bald`, `hood_short` | base only |
| armour `mail`, `plate` | hat, jacket, sleeves, pants |

The hood of `traveller` and the armour helmets live in the **hat overlay**.
Hair is drawn after clothes, so hair that also paints the hat top, back or sides
would show on top of the hood. With `traveller`, or with any armour, use
`hood_short`, `bald`, `braided`, `beard_short` or `beard_long`. In those styles
only the braids and beards come out of the hood, which is intended. `hood_short`
is a tidy crop in the base region only: its fringe peeks out of the hood
opening. Armour drawn last covers anything hair puts in the jacket overlay,
such as a long beard under mail.

## Blocks, items, GUI

* `textures/block/`: `hearthstone_top/side` (glowing ember rune),
  `storehouse_top/side/front` (iron-banded strongbox, open ledger and lock),
  `gravestone` (one weathered stone material for every face of a small model),
  `boundary_stone_top/side` (mossy marker with carved survey ring and claim
  pennant), `ward_stone_side/top` (dark pillar, silver runes),
  `ward_stone_top_active`, `ward_stone_side_active` (**16x64, 4 frames** of
  pale-blue glowing runes, with `ward_stone_side_active.png.mcmeta` set to
  `{"animation":{"frametime":8}}`), `notice_board` and `notice_board_side`,
  `market_stall_top/side`, `blight_crop_stage0..2`. The crop stages have a
  transparent background, so use the cutout render type with the cross model.
  All the other blocks are fully opaque.
* `textures/item/`: `copper_penny`, `silver_mark` and `gold_crown` (the size
  grows with the value), `land_deed`, `trade_seal`, `wedding_token`,
  `founding_charter`, `settlement_ledger`, `letter`, `ward_chalk`,
  `blighted_wheat`, `ember_relic`, `night_shard`, `star_chart`,
  `maker_mark_stamp`, `banner_shield`.
* `textures/gui/ledger_background.png`: 256x256. Only the top-left 248x180 is
  painted; the rest is transparent. It is aged parchment in flat tones with a
  thin double ink frame, and the interior stays light enough for dark text.
* `textures/gui/logo.png`: 128x128 emblem. A dark sun with an ember corona
  hangs over a castle silhouette, inside a bronze ring.

## Previews (`preview/`)

| File | Shows |
| --- | --- |
| `contact_sheet.png` | Every block and item at 8x, with animation frames split out. |
| `citizens.png` / `citizens_back.png` | 22 assembled citizens, flattened front and back views at 6x. |
| `citizens_3d.png` | 12 citizens on the real player geometry, front and back three-quarter views. |
| `uv_layers.png` | Every body, clothes, hair and armour texture in raw UV form. |
| `gui.png` | The ledger background with sample text, and the logo. |

## Adding variants

* **New skin tone:** add a row to `BODIES` in `art_bodies.py`, giving the kind,
  skin colour, shadow colour, iris and brow.
* **New outfit:** write a function in `art_clothes.py` from the building blocks
  (`torso`, `sleeve`, `skirt`, `trousers`, `belt`, `boots`) and register it in
  `CLOTHES`.
* **New hairstyle:** add an entry to `STYLES` in `art_hair.py`. Each face is a
  list of digit rows: `0`–`8` grey level, `.` transparent, `x` near-black.

The verifier picks up new entries automatically.
