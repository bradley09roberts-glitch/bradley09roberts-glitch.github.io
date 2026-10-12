# Building, texture and shader overhaul (0.2.0)

This release changes how the world is built and how it looks. Combat, Devil Fruits, NPCs, quests, ships, factions and the
other game systems are unchanged. The only exceptions are the integration points that buildings need: plots,
gameplay markers and loot tables keep their meaning.

**Status of this document:** it's filled in as the work lands. The validation report at the end lists only checks
that were actually run.

## What changed

1. **Settlement buildings** (`world/settlement/Buildings.java`)
   - All 22 building types now have their own designed plan, built from a shared architecture library (`world/build/Arch.java`):
     - massing, real roofs (gable, low-pitch, steep, hip, flared, flat with terraces);
     - façade depth (plinths, posts, beam bands, framed windows with shutters and planters, door hoods, eave brackets);
     - furnished interiors with stairs.
   - Each of the 15 architectural styles decides materials and roof form. Some styles also change the plan:
     - Alabasta: terraces and domes.
     - Jungle: stilts.
     - Wano: skirt roofs and a shrine with torii.
     - Drum and Thriller Bark: steep roofs.
   - Per-house variation comes from the seed: colours, roof tiles, ridge direction, chimney, dormers and lean-tos.
2. **Towns** (`world/settlement/Settlement.java`, `world/gen/ArchipelagoChunkGenerator.java`)
   - Streets are a connected network of axis-aligned streets, with a three-wide main street to the harbour and style lamp
     posts. They replace the star of diagonal spokes.
   - Harbour piers stand on pilings, with a T-head, bollards, lamps, a ladder and cargo.
   - Plot positions are unchanged.
3. **Landmarks** (`world/landmark/*`): every one of the 78 registered set pieces was reviewed and rebuilt or improved.
   Coverage and notes are in `docs/STRUCTURE_AUDIT.md` and `docs/audit/`.
4. **Ship interiors** (`ship/ShipInteriors.java`)
   - Rooms below deck now have:
     - a curved hull and deck beams;
     - crew bunks, a galley and mess, a cargo hold;
     - a captain's cabin with stern windows.
   - Interiors that already exist are never rebuilt.
5. **Architectural block kit:** 91 new blocks (`registry/ArchBlocks.java`). See `docs/BUILDING_KIT.md`. Their textures,
   models, loot, recipes and tags ship inside the mod, so no extra resource pack is needed.
6. **Shader pack:** "Grand Line Shaders", an original Iris pack, is distributed separately as
   `dist/GrandLineShaders-1.0.0.zip`. Source: `shaderpack/GrandLineShaders/` (its README documents every feature,
   option and profile). In short:
   - **Water first:** world-anchored swell and chop waves with animated normals, Fresnel sky and cloud reflections
     (plus guarded screen-space reflections on HIGH), refraction that never bleeds above-water pixels, per-channel
     depth absorption from turquoise shallows to deep blue, shore and contact foam, caustics, and an underwater
     look with Snell's window and a wet lens after surfacing.
   - **Light and sky:** filtered sun and moon shadows, warm sun and cool shade, readable interiors and nights,
     glowing lanterns (including the kit's paper and brass lanterns), soft bloom, hue-preserving tone mapping,
     painted clouds, sky-matched distance haze, and looks for the sky, deep-sea and ship-interior dimensions.
   - **Profiles:** LOW, BALANCED (default) and HIGH; a Stylisation slider (default 0.15) and optional outlines.
   - The pack's `block.properties` classifies every `grandline:` block, the 91 kit blocks included (materials,
     emissive lanterns, swaying foliage, non-moving thatch and beams).
7. **Developer tools**
   - **Structure dumps:** `GL_SELFTEST=structures`.
   - **Offline renderer:** `tools/render_structure.py`.
   - **Audit generator:** `tools/gen_structure_audit.py`.
   - **Building check:** `test/BuildingCheck`, which tests every type × style × rotation.
   - **Placement and showcase commands (op):**
     - `/grandline test place <type> <style> [rot]`
     - `/grandline test showcase <style>`
     - `/grandline test kit`
     - `/grandline test view <target> <n>`

## Files

| File | What it is |
|---|---|
| `dist/grandline-0.2.0.jar` | the mod; **replaces** `grandline-0.1.0.jar` (remove the old one) |
| `dist/geckolib-neoforge-1.21.1-4.9.3.jar` | required dependency, unchanged |
| `dist/GrandLineShaders-1.0.0.zip` | the optional shader pack (needs Sodium 0.6.13 and Iris 1.8.12, see below) |
| no resource pack | every texture and model of the new blocks ships inside the mod jar |
| source | `grandline-mod/` (Java, assets, tools, shader source) |

## Installing

1. Use Minecraft Java 1.21.1 with NeoForge 21.1.259. Client and server both need the mod.
2. Put `grandline-0.2.0.jar` and `geckolib-neoforge-1.21.1-4.9.3.jar` in `mods/`. Delete `grandline-0.1.0.jar`.
3. **Optional shaders** (client only; a server needs nothing):
   1. Put these two jars in `mods/` too (they are not redistributed here):
      - **Sodium 0.6.13** for NeoForge 1.21.1, `sodium-neoforge-0.6.13+mc1.21.1.jar`:
        <https://modrinth.com/mod/sodium/version/Pb3OXVqC>
      - **Iris 1.8.12** for NeoForge 1.21.1, `iris-neoforge-1.8.12+mc1.21.1.jar`:
        <https://modrinth.com/mod/iris/version/t3ruzodq>

      Iris 1.8.12 declares exactly this Sodium version as its dependency on Modrinth, and the jar asks for Sodium
      0.6. The newer Iris 1.8.14 beta pairs with Sodium 0.8 and was not tested.
   2. Put `GrandLineShaders-1.0.0.zip` in `shaderpacks/` (don't unzip it).
   3. Pick it under Options → Video Settings → Shader Packs, and choose a profile under Shader Pack Settings.
4. No resource pack is required. If you use one, put it above the default pack as usual. The mod's own textures stay
   available underneath.

## Seeing the new buildings

- **New chunks:** structures are generated when chunks are first created. In a new world, or in parts of an existing world nobody has visited yet, every town, pier and landmark uses the new design.
- **Existing saves:** the mod never rewrites chunks that already exist, so towns you have already explored keep their old buildings.
  - A town that was only partly generated can show old and new buildings side by side. A building that straddled the edge of the explored area can even be cut in half.
  - For the full effect, start a new world or sail to islands you haven't visited yet.
  - There is no automatic migration, on purpose.
- **Without exploring (op):**
  - `/grandline test showcase <style>` builds every settlement type of a style next to you.
  - `/grandline test place <type> <style>` builds one building.
  - `/grandline test kit` lays out every kit block.

  These commands write into the world where you stand. Use them in a test world.

## Rollback

- Back up your world before upgrading.
- To go back, put `grandline-0.1.0.jar` back in place of `grandline-0.2.0.jar`.
- A world created or explored with 0.2.0 contains kit blocks. When 0.1.0 loads it, Minecraft removes the blocks it doesn't know: they turn into air, which damages the new buildings. So roll back only with a backup taken before 0.2.0.
- The shader pack can be switched off at any time from the Shader Packs screen.

## Validation report

(Filled in from the actual runs; see the bottom of PROGRESS.md for the log.)
