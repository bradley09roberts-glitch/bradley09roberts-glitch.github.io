package com.starforged.event;

import com.starforged.registry.ModDamageTypes;
import com.starforged.registry.ModParticles;
import com.starforged.registry.ModSounds;
import com.starforged.util.Combat;
import com.starforged.util.Fx;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Server-side tracking of Meteor Hammer leaps. When the player lands, the ground explodes around them.
 */
public final class HammerSlams {
    private static final Map<UUID, Slam> ACTIVE = new HashMap<>();
    /** Players who landed a slam recently; their fall damage is cancelled. */
    private static final Map<UUID, Long> FALL_IMMUNE_UNTIL = new HashMap<>();

    private record Slam(long startTick, boolean dive, double startY) {
    }

    private HammerSlams() {
    }

    public static void begin(Player player, boolean dive) {
        ACTIVE.put(player.getUUID(), new Slam(player.level().getGameTime(), dive, player.getY()));
        FALL_IMMUNE_UNTIL.put(player.getUUID(), player.level().getGameTime() + 200);
    }

    public static boolean isFallImmune(Player player) {
        Long until = FALL_IMMUNE_UNTIL.get(player.getUUID());
        return until != null && player.level().getGameTime() <= until;
    }

    /** Called every server tick for every player. */
    public static void tick(Player player) {
        Slam slam = ACTIVE.get(player.getUUID());
        if (slam == null || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        long age = level.getGameTime() - slam.startTick;
        if (age > 160) {
            ACTIVE.remove(player.getUUID());
            return;
        }
        if (age > 4 && (player.onGround() || player.isInWater())) {
            ACTIVE.remove(player.getUUID());
            FALL_IMMUNE_UNTIL.put(player.getUUID(), level.getGameTime() + 5);
            double drop = Math.max(0.0, slam.startY - player.getY());
            impact(level, player, Math.min(1.0, drop / 16.0) + (slam.dive ? 0.25 : 0.0));
        }
    }

    private static void impact(ServerLevel level, Player player, double power) {
        Vec3 center = player.position();
        double radius = 5.0 + power * 3.0;
        float damage = (float) (9.0 + power * 8.0);
        for (LivingEntity target : Combat.targetsAround(level, player, center, radius)) {
            double falloff = 1.0 - Math.min(0.6, target.position().distanceTo(center) / radius * 0.6);
            target.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.SHOCKWAVE, player), (float) (damage * falloff));
            Combat.blast(target, center, 1.1 * falloff, 0.75);
        }

        BlockState below = level.getBlockState(BlockPos.containing(center).below());
        if (!below.isAir()) {
            BlockParticleOption debris = new BlockParticleOption(ParticleTypes.BLOCK, below);
            for (int ring = 1; ring <= 3; ring++) {
                Fx.ring(level, debris, center.add(0, 0.2, 0), radius * ring / 3.0, 26 + ring * 8, 0.2, 0.35);
            }
        }
        Fx.ring(level, ParticleTypes.CLOUD, center.add(0, 0.3, 0), 1.0, 36, 0.55, 0.02);
        Fx.ring(level, ModParticles.METEOR_EMBER.get(), center.add(0, 0.3, 0), 1.5, 30, 0.45, 0.15);
        Fx.burst(level, ParticleTypes.EXPLOSION_EMITTER, center, 1, 0.0, 0.0);
        Fx.burst(level, ModParticles.STAR_SPARKLE.get(), center.add(0, 0.5, 0), 40, 1.2, 0.2);
        level.playSound(null, center.x, center.y, center.z, ModSounds.HAMMER_SLAM.get(), SoundSource.PLAYERS, 2.0F, 0.9F + level.getRandom().nextFloat() * 0.2F);
        Fx.shake(level, center, 28.0, 1.2F, 14);
        player.getItemBySlot(EquipmentSlot.MAINHAND).hurtAndBreak(2, player, EquipmentSlot.MAINHAND);
    }

    public static void clear(Player player) {
        ACTIVE.remove(player.getUUID());
        FALL_IMMUNE_UNTIL.remove(player.getUUID());
    }
}
