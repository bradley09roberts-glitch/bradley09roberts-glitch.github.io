package com.terracraft.entity;

/** Entities drawn by the 2D Terraria sprite renderer (enemies, bosses, town NPCs). */
public interface SpriteEntity {
    /** Sprite sheet suffix for alternate forms ({@code textures/entity/mob/<id>_<variant>.png}); empty = base sprite. */
    default String spriteVariant() {
        return "";
    }

    /** Extra in-plane rotation of the sprite in degrees (spinning bosses). */
    default float spriteSpin(float partialTicks) {
        return 0.0F;
    }

    /** Whether a health bar is drawn under the sprite while damaged. */
    default boolean showsHealthBar() {
        return true;
    }
}
