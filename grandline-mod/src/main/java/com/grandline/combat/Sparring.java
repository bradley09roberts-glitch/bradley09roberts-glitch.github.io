package com.grandline.combat;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/**
 * STUB so the story branch compiles on its own: the real sparring partner lives on the combat branch and replaces this
 * file when the branches are merged (same signature). Returns false: no bout is started here.
 */
public final class Sparring {
    private Sparring() {}

    /** Start a sparring bout for this player, offered by {@code by} (an elder or master), if possible. */
    public static boolean start(ServerPlayer sp, @Nullable LivingEntity by) {
        return false;
    }
}
