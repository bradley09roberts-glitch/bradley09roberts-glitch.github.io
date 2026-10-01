package com.starforged.moon.world;

import com.starforged.moon.MoonBlocks;
import com.starforged.moon.MoonEntities;
import com.starforged.moon.MoonSounds;
import com.starforged.moon.block.MoonSealBlock;
import com.starforged.moon.block.OrreryRingBlock;
import com.starforged.moon.entity.SeleniteSentinelEntity;
import com.starforged.registry.ModParticles;
import com.starforged.sun.SunFx;
import com.starforged.util.Fx;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The Orrery Alignment puzzle. Each of the four rings around the console must show the moon phase carved on the mural
 * in its direction. Pull the console: if every ring matches, the Moon Seal on the stair tower melts away; if not,
 * gravity inverts and the hall's sentinels wake.
 */
public final class OrreryPuzzle {
    private static final int[][] DIRS = {{0, -1}, {1, 0}, {0, 1}, {-1, 0}};
    private static final Map<BlockPos, Long> COOLDOWN = new HashMap<>();

    private OrreryPuzzle() {
    }

    public static void pull(ServerLevel level, BlockPos console, Player player) {
        Long ready = COOLDOWN.get(console);
        if (ready != null && ready > level.getGameTime()) {
            player.sendOverlayMessage(Component.translatable("block.starforged.orrery_console.wait").withStyle(ChatFormatting.GRAY));
            return;
        }
        COOLDOWN.put(console.immutable(), level.getGameTime() + 100);
        int matched = 0;
        int rings = 0;
        for (int[] d : DIRS) {
            BlockState ring = level.getBlockState(console.offset(d[0] * 3, 0, d[1] * 3));
            BlockState mural = level.getBlockState(console.offset(d[0] * 18, 5, d[1] * 18));
            if (!ring.is(MoonBlocks.ORRERY_RING.get())) {
                continue;
            }
            rings++;
            if (mural.is(MoonBlocks.LUNAR_MURAL.get()) && mural.getValue(OrreryRingBlock.PHASE).equals(ring.getValue(OrreryRingBlock.PHASE))) {
                matched++;
            }
        }
        Vec3 c = Vec3.atBottomCenterOf(console).add(0, 1, 0);
        if (rings > 0 && matched == rings) {
            align(level, console, c);
        } else {
            misalign(level, console, c, matched, rings);
        }
    }

    private static void align(ServerLevel level, BlockPos console, Vec3 c) {
        level.playSound(null, c.x, c.y, c.z, MoonSounds.ORRERY_ALIGN.get(), SoundSource.BLOCKS, 3.0F, 1.0F);
        Fx.column(level, ModParticles.LUNAR_GLIMMER.get(), c, 8.0, 120, 0.6, 0.1);
        Fx.ring(level, ModParticles.MOON_DUST.get(), c, 1.0, 60, 0.5, 0.0);
        int opened = 0;
        for (BlockPos pos : BlockPos.betweenClosed(console.offset(-18, -3, -18), console.offset(18, 8, 18))) {
            if (level.getBlockState(pos).is(MoonBlocks.MOON_SEAL.get())) {
                MoonSealBlock.open(level, pos, 10 + opened);
                opened++;
            }
        }
        SunFx.messageNear(level, c, 40.0, Component.translatable("block.starforged.orrery_console.aligned").withStyle(ChatFormatting.AQUA));
    }

    private static void misalign(ServerLevel level, BlockPos console, Vec3 c, int matched, int rings) {
        level.playSound(null, c.x, c.y, c.z, MoonSounds.ORRERY_FAIL.get(), SoundSource.BLOCKS, 3.0F, 1.0F);
        SunFx.messageNear(level, c, 40.0, Component.translatable("block.starforged.orrery_console.inversion", matched, rings)
            .withStyle(ChatFormatting.RED));
        for (Player victim : level.getEntitiesOfClass(Player.class, new AABB(console).inflate(20.0, 8.0, 20.0), p -> !p.isSpectator())) {
            victim.addEffect(new MobEffectInstance(MobEffects.LEVITATION, 50, 5, false, false, true));
        }
        Fx.sphere(level, ModParticles.LUNAR_GLIMMER.get(), c, 1.0, 80, 0.8);
        for (int i = 0; i < 2; i++) {
            SeleniteSentinelEntity sentinel = MoonEntities.SELENITE_SENTINEL.get().create(level, EntitySpawnReason.EVENT);
            if (sentinel != null) {
                double a = level.getRandom().nextDouble() * Math.PI * 2;
                sentinel.snapTo(c.x + Math.cos(a) * 6, c.y - 1, c.z + Math.sin(a) * 6, 0.0F, 0.0F);
                level.addFreshEntity(sentinel);
            }
        }
    }
}
