package com.grandline.player;

import com.grandline.registry.ModAttachments;
import net.minecraft.world.entity.player.Player;

/** Accessors for character data. */
public final class Characters {
    public static CharacterData get(Player player) {
        return player.getData(ModAttachments.CHARACTER);
    }

    private Characters() {}
}
