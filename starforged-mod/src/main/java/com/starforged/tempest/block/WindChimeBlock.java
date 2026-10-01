package com.starforged.tempest.block;

import com.mojang.serialization.MapCodec;
import com.starforged.tempest.TempestSounds;
import com.starforged.tempest.world.StormreachTravel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Wind Chime: hangs from a ceiling and sings in the wind - constantly in Stormreach, now and then elsewhere. */
public class WindChimeBlock extends Block {
    public static final MapCodec<WindChimeBlock> CODEC = simpleCodec(WindChimeBlock::new);
    private static final VoxelShape SHAPE = Block.box(4, 2, 4, 12, 16, 12);

    public WindChimeBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return Block.canSupportCenter(level, pos.above(), Direction.DOWN);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
                                     BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return direction == Direction.UP && !this.canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState()
            : super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbourState, random);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        int chance = StormreachTravel.isStormreach(level) || level.isRaining() ? 12 : 60;
        if (random.nextInt(chance) == 0) {
            level.playLocalSound(pos, TempestSounds.CHIME.get(), SoundSource.BLOCKS, 0.5F, 0.8F + random.nextFloat() * 0.5F, false);
        }
    }
}
