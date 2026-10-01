package com.starforged.tempest.event;

import com.starforged.registry.ModDamageTypes;
import com.starforged.registry.ModParticles;
import com.starforged.tempest.TempestFx;
import com.starforged.tempest.TempestItems;
import com.starforged.tempest.TempestSounds;
import com.starforged.tempest.block.StormDynamoBlock;
import com.starforged.tempest.entity.StormRocEntity;
import com.starforged.tempest.entity.ThunderjawEntity;
import com.starforged.tempest.world.AetheriumCharging;
import com.starforged.tempest.world.StormNetwork;
import com.starforged.util.Combat;
import com.starforged.util.Fx;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Server-side logic for Tempestforged gear and storms:
 * <ul>
 *     <li>Aetherium set: <b>Stormstep</b> (press jump in mid-air to dash - three dashes before you touch ground again),
 *     immunity to lightning, and much less knockback.</li>
 *     <li>Tempest Crown: immunity to lightning, and every six seconds a bolt of arc lightning leaps from you to the nearest
 *     hostile creature.</li>
 *     <li>The Skybreaker Halberd's Thunderfall slam, and timed lightning strikes (Skycleaver, Veyr).</li>
 *     <li>Every bolt of lightning charges nearby Aetherium and Thunderjaws, and fires Storm Dynamos.</li>
 * </ul>
 */
public final class TempestAbilities {
    public static final int MAX_DASHES = 3;
    private static final Map<UUID, Integer> DASHES = new HashMap<>();
    private static final Map<UUID, Boolean> LAST_JUMP = new HashMap<>();
    private static final Map<UUID, Integer> SLAM = new HashMap<>();
    private static final Map<UUID, Integer> CROWN = new HashMap<>();
    private static final List<Strike> STRIKES = new ArrayList<>();

    private record Strike(ResourceKey<Level> dimension, long at, Vec3 pos, int attacker, float damage, double radius) {
    }

    private TempestAbilities() {
    }

    // --- Timed strikes -----------------------------------------------------------------------------------------

    public static void scheduleStrike(ServerLevel level, int delay, Vec3 pos, @Nullable Entity attacker, float damage, double radius) {
        STRIKES.add(new Strike(level.dimension(), level.getGameTime() + delay, pos, attacker == null ? -1 : attacker.getId(), damage, radius));
    }

    public static void tickLevel(ServerLevel level) {
        long now = level.getGameTime();
        for (Iterator<Strike> it = STRIKES.iterator(); it.hasNext(); ) {
            Strike s = it.next();
            if (s.dimension != level.dimension() || now < s.at) {
                continue;
            }
            it.remove();
            Entity attacker = s.attacker < 0 ? null : level.getEntity(s.attacker);
            TempestFx.strike(level, s.pos, attacker, s.damage, s.radius);
        }
    }

    // --- Players -----------------------------------------------------------------------------------------------

    public static boolean hasFullSet(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.HEAD).is(TempestItems.AETHERIUM_HELMET.get())
            && entity.getItemBySlot(EquipmentSlot.CHEST).is(TempestItems.AETHERIUM_CHESTPLATE.get())
            && entity.getItemBySlot(EquipmentSlot.LEGS).is(TempestItems.AETHERIUM_LEGGINGS.get())
            && entity.getItemBySlot(EquipmentSlot.FEET).is(TempestItems.AETHERIUM_BOOTS.get());
    }

    public static boolean hasCrown(LivingEntity entity) {
        return entity.getItemBySlot(EquipmentSlot.HEAD).is(TempestItems.TEMPEST_CROWN.get());
    }

    /** Halberd: the player was just launched; slam when they come back down. */
    public static void startSlam(Player player) {
        SLAM.put(player.getUUID(), 0);
    }

    public static void tick(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        UUID id = player.getUUID();
        Integer slam = SLAM.get(id);
        if (slam != null) {
            player.resetFallDistance();
            SLAM.put(id, slam + 1);
            if (slam > 6 && (player.onGround() || player.isInWater())) {
                SLAM.remove(id);
                thunderfall(level, player);
            } else if (slam > 120) {
                SLAM.remove(id);
            }
        }
        boolean fullSet = hasFullSet(player);
        boolean jump = serverPlayer.getLastClientInput().jump();
        boolean wasJump = LAST_JUMP.getOrDefault(id, false);
        LAST_JUMP.put(id, jump);
        if (player.onGround() || player.isInWater() || player.getAbilities().flying) {
            DASHES.remove(id);
        } else if (fullSet && jump && !wasJump && !player.isPassenger()) {
            int used = DASHES.getOrDefault(id, 0);
            if (used < MAX_DASHES) {
                DASHES.put(id, used + 1);
                stormstep(level, player, used + 1);
            }
        }
        if (hasCrown(player)) {
            int t = CROWN.getOrDefault(id, 0) + 1;
            if (t >= 120) {
                t = 0;
                crownArc(level, player);
            }
            CROWN.put(id, t);
        }
    }

    private static void stormstep(ServerLevel level, Player player, int dash) {
        Vec3 look = player.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z).normalize();
        player.setDeltaMovement(flat.scale(1.25).add(0, 0.32, 0));
        player.hurtMarked = true;
        player.resetFallDistance();
        Vec3 at = player.position().add(0, 1.0, 0);
        Fx.burst(level, ModParticles.STATIC_SPARK.get(), at, 20, 0.4, 0.1);
        StormNetwork.arcTo(level, at, at.subtract(flat.scale(2.5)));
        level.playSound(null, player.getX(), player.getY(), player.getZ(), TempestSounds.DASH.get(), SoundSource.PLAYERS, 0.9F, 1.0F + dash * 0.1F);
        player.sendOverlayMessage(Component.translatable("ability.starforged.stormstep.dashes", MAX_DASHES - dash, MAX_DASHES)
            .withStyle(ChatFormatting.AQUA));
    }

    private static void thunderfall(ServerLevel level, Player player) {
        Vec3 at = player.position();
        TempestFx.strike(level, at, player, 14.0F, 5.0);
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3;
            scheduleStrike(level, 4 + i, at.add(Math.cos(a) * 4.0, 0, Math.sin(a) * 4.0), player, 8.0F, 2.5);
        }
        for (LivingEntity victim : Combat.targetsAround(level, player, at, 5.0)) {
            Combat.blast(victim, at, 1.0, 0.8);
        }
        Fx.ring(level, ModParticles.STATIC_SPARK.get(), at.add(0, 0.2, 0), 1.0, 60, 0.7, 0.02);
        Fx.shake(level, at, 20.0, 0.8F, 12);
        level.playSound(null, at.x, at.y, at.z, TempestSounds.HALBERD_SLAM.get(), SoundSource.PLAYERS, 1.5F, 1.0F);
    }

    private static void crownArc(ServerLevel level, Player player) {
        LivingEntity best = null;
        double bestDist = 100.0;
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(10.0),
            e -> e instanceof net.minecraft.world.entity.monster.Enemy && Combat.canHit(level, player, e))) {
            double d = e.distanceToSqr(player);
            if (d < bestDist) {
                bestDist = d;
                best = e;
            }
        }
        if (best != null) {
            TempestFx.zap(level, player.position().add(0, 2.0, 0), best, player, 8.0F);
        }
    }

    /** Returns the damage multiplier for {@code victim} (0 = immune). */
    public static float onHurt(LivingEntity victim, DamageSource source, float amount) {
        boolean lightning = source.is(DamageTypes.LIGHTNING_BOLT) || source.is(ModDamageTypes.STORM);
        if (lightning && (hasFullSet(victim) || hasCrown(victim) || victim.getVehicle() instanceof StormRocEntity)) {
            return 0.0F;
        }
        if (source.is(DamageTypeTags.IS_FALL) && SLAM.containsKey(victim.getUUID())) {
            return 0.0F;
        }
        return 1.0F;
    }

    public static void clear(Player player) {
        UUID id = player.getUUID();
        DASHES.remove(id);
        LAST_JUMP.remove(id);
        SLAM.remove(id);
        CROWN.remove(id);
    }

    // --- Lightning ---------------------------------------------------------------------------------------------

    /** Every bolt that appears charges Aetherium and Thunderjaws near it; bolts that aren't a dynamo's own fire dynamos. */
    public static void onBolt(ServerLevel level, LightningBolt bolt) {
        Vec3 at = bolt.position();
        AetheriumCharging.chargeAround(level, at, 3.0);
        ThunderjawEntity.chargeNear(level, at, 4.0);
        if (bolt.entityTags().contains(StormNetwork.OWN_BOLT)) {
            return;
        }
        BlockPos center = BlockPos.containing(at);
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-2, -3, -2), center.offset(2, 1, 2))) {
            BlockState state = level.getBlockState(p);
            if (state.getBlock() instanceof StormDynamoBlock) {
                StormDynamoBlock.fire(level, p.immutable(), state);
            }
        }
    }
}
