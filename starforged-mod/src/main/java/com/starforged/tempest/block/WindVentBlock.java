package com.starforged.tempest.block;

import com.mojang.serialization.MapCodec;
import com.starforged.registry.ModParticles;
import com.starforged.tempest.TempestSounds;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Wind Vent: a constant updraft. Anything standing over it rides the wind up to ten blocks; sneak to sink back down.
 * Storm Lifts are the switchable kind (see {@link StormLiftBlock}).
 */
public class WindVentBlock extends StormTickingBlock {
    public static final MapCodec<WindVentBlock> CODEC = simpleCodec(WindVentBlock::new);

    public WindVentBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    protected boolean active(ServerLevel level, BlockPos pos, BlockState state) {
        return true;
    }

    protected int height() {
        return 10;
    }

    @Override
    public void stormTick(ServerLevel level, BlockPos pos, BlockState state, StormTickerBlockEntity entity) {
        if (!this.active(level, pos, state)) {
            return;
        }
        lift(level, pos, this.height(), 0.55);
        if (level.getGameTime() % 40 == pos.asLong() % 40) {
            level.playSound(null, pos, TempestSounds.WIND_VENT.get(), SoundSource.BLOCKS, 0.4F, 0.9F + level.getRandom().nextFloat() * 0.2F);
        }
    }

    /** Pushes everything in the column above {@code pos} upwards. */
    static void lift(ServerLevel level, BlockPos pos, int height, double speed) {
        AABB column = new AABB(pos.getX() + 0.05, pos.getY() + 1.0, pos.getZ() + 0.05, pos.getX() + 0.95, pos.getY() + 1.0 + height, pos.getZ() + 0.95);
        List<Entity> riders = level.getEntitiesOfClass(Entity.class, column, e -> e instanceof LivingEntity || e instanceof ItemEntity);
        for (Entity e : riders) {
            if (e instanceof net.minecraft.world.entity.player.Player p && p.getAbilities().flying) {
                continue;
            }
            Vec3 v = e.getDeltaMovement();
            double top = pos.getY() + 1.0 + height;
            double vy = e.isShiftKeyDown() ? Math.max(v.y, -0.15) : e.getY() > top - 1.5 ? Math.max(v.y, 0.04) : Math.max(v.y, speed);
            e.setDeltaMovement(v.x * 0.9, vy, v.z * 0.9);
            e.resetFallDistance();
            e.hurtMarked = true;
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        for (int i = 0; i < 2; i++) {
            level.addParticle(ModParticles.STORM_WISP.get(), pos.getX() + 0.2 + random.nextDouble() * 0.6, pos.getY() + 1.05,
                pos.getZ() + 0.2 + random.nextDouble() * 0.6, 0.0, 0.25 + random.nextDouble() * 0.15, 0.0);
        }
    }
}
