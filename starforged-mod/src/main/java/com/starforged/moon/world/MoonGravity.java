package com.starforged.moon.world;

import com.starforged.Starforged;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * Low gravity. In the Pale Reach everything weighs about a third of normal (a quarter at high tide), so jumps go
 * three times higher and falls are slow. Moonsilver's Gravity Shift and the Crown of Tides lighten their wearer
 * anywhere, and the Pale Matriarch's Inversion leaves you all but weightless.
 */
public final class MoonGravity {
    private static final Identifier GRAVITY_ID = Starforged.id("lunar_gravity");
    private static final Identifier FALL_ID = Starforged.id("lunar_fall");
    /** Players caught in the Matriarch's Inversion, with the game time it wears off. */
    private static final Map<UUID, Long> INVERTED = new HashMap<>();

    private MoonGravity() {
    }

    public static void invert(Player player, int ticks) {
        INVERTED.put(player.getUUID(), player.level().getGameTime() + ticks);
    }

    public static void release(Player player) {
        INVERTED.remove(player.getUUID());
    }

    public static boolean isInverted(Player player) {
        Long until = INVERTED.get(player.getUUID());
        return until != null && until > player.level().getGameTime();
    }

    /** Gravity factor from the world alone. */
    public static double worldFactor(ServerLevel level) {
        if (!PaleReachTravel.isPaleReach(level)) {
            return 1.0;
        }
        return MoonTides.isHighTide(level) ? 0.25 : 0.35;
    }

    public static void apply(LivingEntity entity, double factor) {
        set(entity, Attributes.GRAVITY, GRAVITY_ID, factor - 1.0);
        set(entity, Attributes.FALL_DAMAGE_MULTIPLIER, FALL_ID, factor < 0.99 ? -0.6 : 0.0);
    }

    private static void set(LivingEntity entity, Holder<Attribute> attribute, Identifier id, double amount) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier current = instance.getModifier(id);
        if (Math.abs(amount) < 1.0E-4) {
            if (current != null) {
                instance.removeModifier(id);
            }
            return;
        }
        if (current == null || Math.abs(current.amount() - amount) > 1.0E-4) {
            instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        }
    }

    /** Mobs entering the Pale Reach (or spawning there) become light. */
    public static void onJoin(Entity entity) {
        if (entity instanceof LivingEntity living && !(entity instanceof Player) && entity.level() instanceof ServerLevel level) {
            apply(living, worldFactor(level));
        }
    }

    /** Re-applies the world factor to every creature in the dimension (on a change of tide). */
    public static void refreshAll(ServerLevel level) {
        double factor = worldFactor(level);
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof LivingEntity living && !(entity instanceof Player)) {
                apply(living, factor);
            }
        }
    }

    /** Player gravity, combined from the world, their gear and the boss. */
    public static void tickPlayer(Player player, boolean personalLight) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        double factor = worldFactor(level);
        if (personalLight) {
            factor *= PaleReachTravel.isPaleReach(level) ? 0.7 : 0.4;
        }
        if (isInverted(player)) {
            factor = 0.04;
        }
        apply(player, factor);
    }

    public static void clear(Player player) {
        INVERTED.remove(player.getUUID());
    }
}
