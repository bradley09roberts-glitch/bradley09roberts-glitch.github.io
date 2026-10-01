package com.starforged.tempest.block;

import com.mojang.serialization.MapCodec;
import com.starforged.registry.ModDamageTypes;
import com.starforged.registry.ModParticles;
import com.starforged.tempest.TempestSounds;
import com.starforged.tempest.world.CitadelPuzzle;
import com.starforged.util.Fx;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Overload Relay: a trap in a storm circuit. A pulse that reaches it blows it out in a burst of lightning that shocks
 * everyone close by - and in a Tempest Citadel it knocks every Citadel Core in the wing back offline.
 */
public class OverloadRelayBlock extends Block implements StormNode {
    public static final MapCodec<OverloadRelayBlock> CODEC = simpleCodec(OverloadRelayBlock::new);

    public OverloadRelayBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public List<Direction> receive(ServerLevel level, BlockPos pos, BlockState state, Direction travel) {
        Vec3 c = Vec3.atCenterOf(pos);
        level.sendParticles(ParticleTypes.EXPLOSION, c.x, c.y, c.z, 1, 0, 0, 0, 0);
        Fx.sphere(level, ModParticles.STATIC_SPARK.get(), c, 1.0, 60, 0.5);
        level.playSound(null, pos, TempestSounds.OVERLOAD.get(), SoundSource.BLOCKS, 2.0F, 1.0F);
        for (Player player : level.getEntitiesOfClass(Player.class, new AABB(pos).inflate(5.0))) {
            player.hurtServer(level, ModDamageTypes.source(level, ModDamageTypes.STORM, null), 6.0F);
            player.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 60, 2));
            player.sendOverlayMessage(Component.translatable("block.starforged.overload_relay.overload").withStyle(ChatFormatting.RED));
        }
        CitadelPuzzle.resetCores(level, pos, 8);
        return List.of();
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(3) == 0) {
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, pos.getX() + random.nextDouble(), pos.getY() + 1.02, pos.getZ() + random.nextDouble(),
                0.0, 0.05, 0.0);
        }
    }
}
