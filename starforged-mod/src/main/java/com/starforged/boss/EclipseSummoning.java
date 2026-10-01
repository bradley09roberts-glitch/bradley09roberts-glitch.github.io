package com.starforged.boss;

import com.starforged.StarforgedConfig;
import com.starforged.entity.boss.EclipseSovereignEntity;
import com.starforged.event.StarfallManager;
import com.starforged.registry.ModBlocks;
import com.starforged.registry.ModEntities;
import com.starforged.registry.ModParticles;
import com.starforged.registry.ModSounds;
import com.starforged.util.Fx;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The summoning ritual of the Eclipse Sovereign, a ~7 second cinematic:
 * a beam of light erupts from the altar, the sky darkens into an eclipse, lightning walks around the arena,
 * the observatory dome shatters - and the Sovereign descends.
 */
public final class EclipseSummoning {
    private static final List<Ritual> RITUALS = new ArrayList<>();
    private static final int SPAWN_TICK = 140;

    private static final class Ritual {
        final ResourceKey<Level> dimension;
        final BlockPos altar;
        int tick;

        Ritual(ResourceKey<Level> dimension, BlockPos altar) {
            this.dimension = dimension;
            this.altar = altar;
        }
    }

    private EclipseSummoning() {
    }

    public static boolean tryBegin(ServerLevel level, BlockPos altar, ServerPlayer player) {
        for (Ritual ritual : RITUALS) {
            if (ritual.dimension.equals(level.dimension()) && ritual.altar.closerThan(altar, 96)) {
                player.sendOverlayMessage(Component.translatable("block.starforged.celestial_altar.busy").withStyle(ChatFormatting.RED));
                return false;
            }
        }
        if (!level.getEntitiesOfClass(EclipseSovereignEntity.class, new AABB(altar).inflate(128)).isEmpty()) {
            player.sendOverlayMessage(Component.translatable("block.starforged.celestial_altar.busy").withStyle(ChatFormatting.RED));
            return false;
        }
        RITUALS.add(new Ritual(level.dimension(), altar.immutable()));
        return true;
    }

    /** Starts the ritual without an altar check (used by /starforged boss). */
    public static void force(ServerLevel level, BlockPos altar) {
        RITUALS.add(new Ritual(level.dimension(), altar.immutable()));
    }

    public static void tick(ServerLevel level) {
        if (RITUALS.isEmpty()) {
            return;
        }
        Iterator<Ritual> it = RITUALS.iterator();
        while (it.hasNext()) {
            Ritual ritual = it.next();
            if (!ritual.dimension.equals(level.dimension())) {
                continue;
            }
            ritual.tick++;
            if (step(level, ritual)) {
                it.remove();
            }
        }
    }

    /** @return true when the ritual is complete */
    private static boolean step(ServerLevel level, Ritual ritual) {
        int t = ritual.tick;
        BlockPos altar = ritual.altar;
        Vec3 top = Vec3.atBottomCenterOf(altar).add(0, 1.0, 0);

        // Pillar of light rising from the altar for the whole ritual.
        if (t % 2 == 0) {
            double height = Math.min(80.0, t * 1.2);
            for (int i = 0; i < 8; i++) {
                double y = level.getRandom().nextDouble() * height;
                level.sendParticles(i % 2 == 0 ? ParticleTypes.END_ROD : ModParticles.ASTRAL_GLINT.get(), true, true,
                    top.x + (level.getRandom().nextDouble() - 0.5) * 0.6, top.y + y, top.z + (level.getRandom().nextDouble() - 0.5) * 0.6, 1, 0, 0.05, 0, 0.0);
            }
        }
        StarfallManager.setEclipse(level.getServer(), Math.min(1.0F, t / 100.0F));

        if (t == 1) {
            level.playSound(null, top.x, top.y, top.z, ModSounds.ALTAR_ACTIVATE.get(), SoundSource.HOSTILE, 6.0F, 1.0F);
            Fx.sphere(level, ModParticles.STAR_SPARKLE.get(), top, 0.5, 60, 0.5);
            message(level, top, Component.translatable("event.starforged.summon.altar").withStyle(ChatFormatting.LIGHT_PURPLE, ChatFormatting.ITALIC));
        }
        if (t == 40) {
            title(level, top, Component.translatable("event.starforged.summon.dark"), Component.translatable("event.starforged.summon.dark_sub"), 10, 50, 10);
            level.playSound(null, top.x, top.y, top.z, ModSounds.BOSS_ROAR.get(), SoundSource.HOSTILE, 3.0F, 0.5F);
        }
        if (t == 25 || t == 60 || t == 85 || t == 105 || t == 120) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2;
            LightningBolt bolt = EntityTypes.LIGHTNING_BOLT.create(level, EntitySpawnReason.EVENT);
            if (bolt != null) {
                bolt.snapTo(top.x + Math.cos(angle) * 14, top.y - 1, top.z + Math.sin(angle) * 14);
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
            }
        }
        if (t > 60 && t < SPAWN_TICK && t % 10 == 0) {
            Fx.shake(level, top, 48.0, 0.25F + t / (float) SPAWN_TICK * 0.6F, 12);
            Fx.ring(level, ModParticles.ECLIPSE_FLARE.get(), top.add(0, 0.2, 0), 2.0 + (t - 60) * 0.12, 30, 0.1, 0.0);
        }
        if (t == 125 && StarforgedConfig.BOSS_BREAKS_DOME.get()) {
            shatterDome(level, altar);
        }
        if (t == SPAWN_TICK) {
            EclipseSovereignEntity boss = ModEntities.ECLIPSE_SOVEREIGN.get().create(level, EntitySpawnReason.EVENT);
            if (boss != null) {
                boss.setHome(altar);
                boss.snapTo(top.x, top.y + 28.0, top.z, 0.0F, 0.0F);
                level.addFreshEntity(boss);
            }
            level.playSound(null, top.x, top.y + 20, top.z, ModSounds.BOSS_ROAR.get(), SoundSource.HOSTILE, 8.0F, 0.8F);
            title(level, top, Component.translatable("entity.starforged.eclipse_sovereign").withStyle(ChatFormatting.DARK_PURPLE, ChatFormatting.BOLD),
                Component.translatable("entity.starforged.eclipse_sovereign.title").withStyle(ChatFormatting.LIGHT_PURPLE), 10, 70, 20);
            Fx.shake(level, top, 64.0, 1.5F, 30);
            return true;
        }
        return false;
    }

    /** The Sovereign's arrival shatters the starglass dome above the altar. */
    private static void shatterDome(ServerLevel level, BlockPos altar) {
        int broken = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dy = 2; dy <= 24 && broken < 900; dy++) {
            for (int dx = -16; dx <= 16; dx++) {
                for (int dz = -16; dz <= 16; dz++) {
                    pos.set(altar.getX() + dx, altar.getY() + dy, altar.getZ() + dz);
                    BlockState state = level.getBlockState(pos);
                    if (state.is(ModBlocks.STARGLASS.get()) || state.is(BlockTags.IMPERMEABLE) || state.is(Blocks.GLASS_PANE)) {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                        if (broken % 3 == 0) {
                            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), true, true,
                                pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 6, 0.4, 0.4, 0.4, 0.3);
                            level.sendParticles(ModParticles.STAR_SPARKLE.get(), true, true, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 2, 0.3, 0.3, 0.3, 0.1);
                        }
                        broken++;
                    }
                }
            }
        }
        if (broken > 0) {
            Vec3 c = Vec3.atCenterOf(altar).add(0, 12, 0);
            for (int i = 0; i < 4; i++) {
                level.playSound(null, c.x + (i - 2) * 6, c.y, c.z, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 4.0F, 0.6F + i * 0.15F);
            }
            level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 4.0F, 0.7F);
        }
    }

    private static void message(ServerLevel level, Vec3 at, Component text) {
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceTo(at) < 128) {
                player.sendSystemMessage(text);
            }
        }
    }

    private static void title(ServerLevel level, Vec3 at, Component title, Component subtitle, int in, int stay, int out) {
        for (ServerPlayer player : level.players()) {
            if (player.position().distanceTo(at) < 128) {
                player.connection.send(new ClientboundSetTitlesAnimationPacket(in, stay, out));
                player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
                player.connection.send(new ClientboundSetTitleTextPacket(title));
            }
        }
    }
}
