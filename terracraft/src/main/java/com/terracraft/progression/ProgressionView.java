package com.terracraft.progression;

import net.minecraft.resources.Identifier;

import java.util.Set;

/**
 * Read-only view of world progression. Implemented by the server-side {@link WorldProgression} saved data
 * and by the client-side mirror, so derived flags and {@link ProgressionCondition}s evaluate identically
 * on both sides.
 */
public interface ProgressionView {
    /** Raw stored flags (never contains derived flags). */
    Set<Identifier> storedFlags();

    /** Value of a numeric world counter (altars smashed, invasions completed...). */
    int counter(Identifier id);

    WorldVariants variants();

    default boolean has(ProgressionFlag flag) {
        if (flag.derivation() != null) {
            return flag.derivation().test(this);
        }
        return storedFlags().contains(flag.id());
    }

    default boolean has(Identifier id) {
        return has(ProgressionFlags.resolve(id));
    }

    default boolean isHardmode() {
        return has(ProgressionFlags.HARDMODE);
    }
}
