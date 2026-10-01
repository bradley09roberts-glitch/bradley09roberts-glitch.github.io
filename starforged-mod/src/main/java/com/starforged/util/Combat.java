package com.starforged.util;

import com.starforged.registry.ModTags;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Target selection helpers shared by weapons, creatures and the boss.
 */
public final class Combat {
    private Combat() {
    }

    /**
     * Returns living entities around {@code center} that {@code attacker} is allowed to hurt with an area attack:
     * never the attacker itself, its own pets, its riders/mount, spectators or creative players; other players only when PvP is enabled.
     */
    public static List<LivingEntity> targetsAround(ServerLevel level, @Nullable Entity attacker, Vec3 center, double radius) {
        AABB box = new AABB(center, center).inflate(radius);
        double radiusSq = radius * radius;
        return level.getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive()
            && e.position().add(0, e.getBbHeight() * 0.5, 0).distanceToSqr(center) <= radiusSq + e.getBbWidth() * e.getBbWidth()
            && canHit(level, attacker, e));
    }

    public static boolean canHit(ServerLevel level, @Nullable Entity attacker, LivingEntity target) {
        if (target == attacker || target.isSpectator() || target instanceof ArmorStand) {
            return false;
        }
        if (target instanceof Player player && (player.isCreative())) {
            return false;
        }
        if (attacker == null) {
            return true;
        }
        if (target.isPassengerOfSameVehicle(attacker) || attacker.hasPassenger(target) || target.hasPassenger(attacker)) {
            return false;
        }
        if (target instanceof OwnableEntity ownable && ownable.getOwner() == attacker) {
            return false;
        }
        if (attacker instanceof Player && target instanceof Player && !level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.PVP)) {
            return false;
        }
        if (attacker.is(ModTags.SOVEREIGN_ALLIES) && target.is(ModTags.SOVEREIGN_ALLIES)) {
            return false;
        }
        if (attacker.is(ModTags.MATRIARCH_ALLIES) && target.is(ModTags.MATRIARCH_ALLIES)) {
            return false;
        }
        return !attacker.isAlliedTo(target);
    }

    /** Knocks {@code target} along the horizontal direction {@code dir}. */
    public static void knock(LivingEntity target, double strength, Vec3 dir) {
        Vec3 flat = new Vec3(dir.x, 0, dir.z);
        if (flat.lengthSqr() < 1.0E-4) {
            return;
        }
        double resist = 1.0 - Math.min(0.9, target.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE));
        flat = flat.normalize().scale(strength * resist);
        target.setDeltaMovement(target.getDeltaMovement().add(flat.x, target.onGround() ? 0.25 * strength * resist : 0.0, flat.z));
        target.hurtMarked = true;
    }

    /** Pushes {@code target} away from {@code origin} horizontally with some lift. */
    public static void blast(Entity target, Vec3 origin, double horizontal, double vertical) {
        Vec3 away = target.position().subtract(origin);
        Vec3 flat = new Vec3(away.x, 0, away.z);
        if (flat.lengthSqr() < 1.0E-4) {
            flat = new Vec3(target.getRandom().nextDouble() - 0.5, 0, target.getRandom().nextDouble() - 0.5);
        }
        flat = flat.normalize().scale(horizontal);
        double resist = target instanceof LivingEntity living ? 1.0 - Math.min(0.9, living.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.KNOCKBACK_RESISTANCE)) : 1.0;
        target.setDeltaMovement(target.getDeltaMovement().add(flat.scale(resist)).add(0, vertical * resist, 0));
        target.hurtMarked = true;
    }
}
