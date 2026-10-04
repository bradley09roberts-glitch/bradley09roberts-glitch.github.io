package com.terracraft.combat;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Whip tags: an enemy struck by a whip is marked for a few seconds. Its owner's minions go after the tagged enemy
 * first and deal the whip's tag damage on top of their own (Terraria's summon tag). Server-side and transient.
 */
public final class WhipTags {
    private record Tag(UUID owner, int bonus, long until) {}

    /** Seconds a tag lasts. */
    private static final int DURATION_TICKS = 4 * 20;
    private static final Map<LivingEntity, Tag> TAGS = new WeakHashMap<>();
    /** The latest enemy each player tagged. */
    private static final Map<UUID, LivingEntity> LAST = new WeakHashMap<>();

    private WhipTags() {}

    public static void tag(LivingEntity target, Player owner, int bonus) {
        TAGS.put(target, new Tag(owner.getUUID(), bonus, target.level().getGameTime() + DURATION_TICKS));
        LAST.put(owner.getUUID(), target);
    }

    /** Extra damage this owner's minions deal to the target (0 when untagged or the tag ran out). */
    public static int bonus(LivingEntity target, @Nullable UUID owner) {
        Tag tag = TAGS.get(target);
        if (tag == null || owner == null || !tag.owner().equals(owner) || target.level().getGameTime() > tag.until()) {
            return 0;
        }
        return tag.bonus();
    }

    /** The enemy the player's minions should attack first, if its tag still holds. */
    public static @Nullable LivingEntity focus(Player owner) {
        LivingEntity target = LAST.get(owner.getUUID());
        if (target == null || !target.isAlive() || bonus(target, owner.getUUID()) <= 0 && !tagged(target, owner)) {
            return null;
        }
        return target;
    }

    private static boolean tagged(LivingEntity target, Player owner) {
        Tag tag = TAGS.get(target);
        return tag != null && tag.owner().equals(owner.getUUID()) && target.level().getGameTime() <= tag.until();
    }
}
