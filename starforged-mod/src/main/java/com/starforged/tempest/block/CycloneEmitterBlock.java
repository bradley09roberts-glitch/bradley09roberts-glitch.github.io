package com.starforged.tempest.block;

import com.mojang.serialization.MapCodec;
import com.starforged.registry.ModParticles;
import com.starforged.tempest.entity.CycloneEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** Cyclone Emitter: a Citadel trap that looses a wandering cyclone every seven seconds while someone is near. */
public class CycloneEmitterBlock extends StormTickingBlock {
    public static final MapCodec<CycloneEmitterBlock> CODEC = simpleCodec(CycloneEmitterBlock::new);

    public CycloneEmitterBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    public void stormTick(ServerLevel level, BlockPos pos, BlockState state, StormTickerBlockEntity entity) {
        if (--entity.timer > 0) {
            return;
        }
        entity.timer = 140;
        if (level.hasNeighborSignal(pos)
            || level.getNearestPlayer(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 18.0, p -> !((net.minecraft.world.entity.player.Player) p)
            .isCreative() && !p.isSpectator()) == null) {
            return;
        }
        CycloneEntity.spawnTrap(level, Vec3.atBottomCenterOf(pos.above()));
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        double a = (level.getGameTime() % 20) / 20.0 * Math.PI * 2;
        for (int i = 0; i < 2; i++) {
            double ang = a + i * Math.PI;
            level.addParticle(ModParticles.STORM_WISP.get(), pos.getX() + 0.5 + Math.cos(ang) * 0.6, pos.getY() + 1.1, pos.getZ() + 0.5 + Math.sin(ang) * 0.6,
                -Math.sin(ang) * 0.1, 0.08, Math.cos(ang) * 0.1);
        }
    }
}
