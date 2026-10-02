package com.terracraft.progression;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * A named world progression flag such as {@code terracraft:boss_eye_defeated}.
 * <p>
 * Stored flags are kept in {@link WorldProgression}. Derived flags (for example
 * {@code mech_bosses_defeated}) have a {@link #derivation} and are always computed from other flags,
 * so they can never get out of sync.
 * <p>
 * Flags are intentionally just identifiers: datapacks and future content can set and query arbitrary
 * flags without Java changes. {@link ProgressionFlags} merely gives the well-known ones names, categories
 * and descriptions for commands and the developer menu.
 */
public record ProgressionFlag(Identifier id, Category category, @Nullable Predicate<ProgressionView> derivation) {

    public enum Category {
        /** Boss kills. */
        BOSS,
        /** World state such as hardmode. */
        WORLD,
        /** Invasion / event completion. */
        EVENT,
        /** NPC rescues and unlocks. */
        NPC,
        /** Flags that are never stored, only computed from other flags. */
        DERIVED,
        /** Flags created by datapacks or unknown to Java code. */
        CUSTOM
    }

    public boolean isDerived() {
        return derivation != null;
    }

    public Component displayName() {
        return Component.translatableWithFallback("progression." + id.getNamespace() + "." + id.getPath(), id.getPath());
    }

    @Override
    public String toString() {
        return id.toString();
    }
}
