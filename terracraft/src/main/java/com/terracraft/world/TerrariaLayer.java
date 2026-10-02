package com.terracraft.world;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;

import java.util.Locale;

/**
 * Terraria's horizontal world layers translated to Minecraft heights.
 * <p>
 * Terraria's map is a 2D slice: Space, Surface, Underground (dirt layer), Cavern (rock layer) and the
 * Underworld. In TerraCraft these are absolute height bands of the overworld, so spawn tables, ore
 * generation and biome logic can ask "which Terraria layer is this position in?".
 */
public enum TerrariaLayer {
    SPACE,
    SURFACE,
    UNDERGROUND,
    CAVERN,
    UNDERWORLD;

    /** Above this height the sky counts as Space (floating islands, harpies, low gravity). */
    public static final int SPACE_START = 200;
    /** Below this height is the Underground layer. */
    public static final int UNDERGROUND_START = 50;
    /** Below this height is the Cavern layer. */
    public static final int CAVERN_START = 0;
    /** Below this height is the Underworld. */
    public static final int UNDERWORLD_START = -40;

    public static TerrariaLayer of(Level level, BlockPos pos) {
        return ofHeight(pos.getY());
    }

    public static TerrariaLayer ofHeight(int y) {
        if (y >= SPACE_START) {
            return SPACE;
        }
        if (y >= UNDERGROUND_START) {
            return SURFACE;
        }
        if (y >= CAVERN_START) {
            return UNDERGROUND;
        }
        if (y >= UNDERWORLD_START) {
            return CAVERN;
        }
        return UNDERWORLD;
    }

    public Component displayName() {
        return Component.translatable("layer.terracraft." + name().toLowerCase(Locale.ROOT));
    }
}
