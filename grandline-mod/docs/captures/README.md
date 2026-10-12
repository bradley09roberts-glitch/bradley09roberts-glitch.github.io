# In-game captures

Every image here is a real Minecraft client screenshot, not a render. The setup was:
- NeoForge 21.1.259, a dedicated server on the fixed seed 20261012;
- the scripted client tour `GL_CLIENTTEST=arch`;
- software OpenGL (Mesa llvmpipe) under Xvfb at 1280×720, HUD hidden, noon;
- sea weather held calm with `/grandline test calm true`.

Shaders are off in these shots. The shader pack's own screenshots are in [`../shaders/`](../shaders/).

| Path | What |
|---|---|
| `before/` | 32 views taken with **0.1.0** (commit `e9baef5`) before the overhaul started: towns, streets, a dock and 13 landmarks |
| `after/` | the same 32 views with **0.2.0**, plus 22 more landmarks (`lm_*`) and two alternate angles (`*_2`) |
| `before_after_towns_*.jpg`, `before_after_landmarks_*.jpg` | the 32 shared views side by side |
| `showcase/showcase_<style>.jpg` | `/grandline test showcase <style>`: every settlement building type in that style, in a row, plus a street close-up (15 styles) |
| `showcase/kit.jpg` | `/grandline test kit`: every block of the architectural kit |
| `upgrade_0.1.0_save.jpg` | the 0.1.0 world opened with 0.2.0. Places explored under 0.1.0 keep their old buildings (left); places never visited generate the new ones (right) |

The two 0.1.0 deep-sea views (Ryugu Palace) showed only water: the camera command stood on the water surface in the
deep-sea dimension. That bug is fixed for the 0.2.0 shots.
