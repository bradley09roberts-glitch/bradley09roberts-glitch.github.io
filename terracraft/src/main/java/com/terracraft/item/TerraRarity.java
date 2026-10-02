package com.terracraft.item;

import com.mojang.serialization.Codec;
import net.minecraft.network.chat.TextColor;

/** Terraria's item rarity tiers and their name colours. */
public enum TerraRarity {
    GRAY(-1, 0x828282),
    WHITE(0, 0xFFFFFF),
    BLUE(1, 0x9696FF),
    GREEN(2, 0x96FF96),
    ORANGE(3, 0xFFC896),
    LIGHT_RED(4, 0xFF9696),
    PINK(5, 0xFF96FF),
    LIGHT_PURPLE(6, 0xD2A0FF),
    LIME(7, 0x96FF0A),
    YELLOW(8, 0xFFFF0A),
    CYAN(9, 0x05C8FF),
    RED(10, 0xFF2864),
    PURPLE(11, 0xB428FF),
    /** Expert-mode exclusive items. */
    RAINBOW(-12, 0xFF6EC7),
    /** Quest items. */
    AMBER(-11, 0xFFAF00);

    public static final Codec<TerraRarity> CODEC = Codec.INT.xmap(TerraRarity::byLevel, TerraRarity::level);

    private final int level;
    private final int rgb;

    TerraRarity(int level, int rgb) {
        this.level = level;
        this.rgb = rgb;
    }

    public int level() {
        return level;
    }

    public int rgb() {
        return rgb;
    }

    public TextColor color() {
        return TextColor.fromRgb(rgb);
    }

    public static TerraRarity byLevel(int level) {
        for (TerraRarity rarity : values()) {
            if (rarity.level == level) {
                return rarity;
            }
        }
        return WHITE;
    }
}
