package com.squidgame.core;

import com.squidgame.core.util.Rng;

/**
 * Visual identity of a contestant. Rendered purely through per-bone tint and bone visibility on
 * the shared contestant model (see docs/ASSET_CONTRACT.md): skin tone, one of 8 hair styles
 * (0 = bald), a hair colour, one of 6 face decals, optional glasses and small build scaling.
 */
public record Appearance(int skinTone, int hairStyle, int hairColor, int face, boolean glasses,
                         float heightScale, float widthScale) {

    /** ARGB skin tones applied to the greyscale skin bones (index = skinTone). */
    public static final int[] SKIN_TONES = {
            0xFFFFE0CC, 0xFFF2C9A5, 0xFFD9A57A, 0xFFB98055, 0xFF8D5A38, 0xFF5E3B26
    };
    /** ARGB hair colours applied to the greyscale hair bones (index = hairColor). */
    public static final int[] HAIR_COLORS = {
            0xFF1A1512, 0xFF2B1D14, 0xFF4A3322, 0xFF6B4A2B, 0xFF8A8A8A, 0xFFC9B27A, 0xFF7A2E22, 0xFF22313F
    };
    public static final int HAIR_STYLES = 8;
    public static final int FACES = 6;

    public static final String[] HAIR_BONES = {
            null, "hair_buzz", "hair_short", "hair_parted", "hair_curly", "hair_long", "hair_ponytail", "hair_bun"
    };

    public static Appearance generate(Rng rng) {
        int skin = rng.nextInt(SKIN_TONES.length);
        int style = rng.nextInt(HAIR_STYLES);
        int color = rng.nextInt(HAIR_COLORS.length);
        // mostly dark hair, as in the source material; grey hair mostly on older-looking faces
        if (rng.chance(0.7)) {
            color = rng.nextInt(4);
        }
        int face = rng.nextInt(FACES);
        boolean glasses = rng.chance(0.14);
        float h = (float) rng.range(0.93, 1.06);
        float w = (float) rng.range(0.94, 1.06);
        return new Appearance(skin, style, color, face, glasses, h, w);
    }

    /** Pack into a single int for entity data sync (height/width are quantised to 1/64). */
    public long pack() {
        long h = Math.round((heightScale - 0.8f) * 100f) & 0xFF;
        long w = Math.round((widthScale - 0.8f) * 100f) & 0xFF;
        return (skinTone & 0x7L)
                | ((hairStyle & 0x7L) << 3)
                | ((hairColor & 0x7L) << 6)
                | ((face & 0x7L) << 9)
                | ((glasses ? 1L : 0L) << 12)
                | (h << 13)
                | (w << 21);
    }

    public static Appearance unpack(long v) {
        int skin = (int) (v & 0x7);
        int style = (int) ((v >> 3) & 0x7);
        int color = (int) ((v >> 6) & 0x7);
        int face = (int) ((v >> 9) & 0x7);
        boolean glasses = ((v >> 12) & 1) != 0;
        float h = ((v >> 13) & 0xFF) / 100f + 0.8f;
        float w = ((v >> 21) & 0xFF) / 100f + 0.8f;
        return new Appearance(Math.min(skin, SKIN_TONES.length - 1), style,
                Math.min(color, HAIR_COLORS.length - 1), Math.min(face, FACES - 1), glasses, h, w);
    }

    public static Appearance defaults() {
        return new Appearance(1, 2, 0, 0, false, 1f, 1f);
    }
}
