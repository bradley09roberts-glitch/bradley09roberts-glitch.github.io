package com.starforged.tempest.block;

import com.mojang.serialization.MapCodec;
import com.starforged.registry.ModParticles;
import com.starforged.tempest.TempestSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Gale Vent: blows a sideways gale out of its face for eight blocks, shoving creatures, items and projectiles. */
public class GaleVentBlock extends StormTickingBlock {
    public static final MapCodec<GaleVentBlock> CODEC = simpleCodec(GaleVentBlock::new);
    public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
    private static final int REACH = 8;

    public GaleVentBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public void stormTick(ServerLevel level, BlockPos pos, BlockState state, StormTickerBlockEntity entity) {
        Direction d = state.getValue(FACING);
        BlockPos start = pos.relative(d);
        BlockPos end = pos.relative(d, REACH);
        AABB box = new AABB(Vec3.atLowerCornerOf(start), Vec3.atLowerCornerOf(end).add(1, 2, 1)).inflate(0.1);
        Vec3 push = new Vec3(d.getStepX(), 0, d.getStepZ());
        for (Entity e : level.getEntitiesOfClass(Entity.class, box, e -> e instanceof LivingEntity || e instanceof ItemEntity || e instanceof Projectile)) {
            if (e instanceof net.minecraft.world.entity.player.Player p && (p.getAbilities().flying || p.isSpectator())) {
                continue;
            }
            e.setDeltaMovement(e.getDeltaMovement().add(push.scale(e instanceof Projectile ? 0.25 : 0.12)));
            e.hurtMarked = true;
        }
        if (level.getGameTime() % 50 == pos.asLong() % 50) {
            level.playSound(null, pos, TempestSounds.GUST.get(), SoundSource.BLOCKS, 0.4F, 1.2F);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        Direction d = state.getValue(FACING);
        for (int i = 0; i < 2; i++) {
            level.addParticle(ModParticles.STORM_WISP.get(), pos.getX() + 0.5 + d.getStepX() * 0.6 + (d.getStepX() == 0 ? random.nextDouble() - 0.5 : 0),
                pos.getY() + 0.2 + random.nextDouble() * 0.6, pos.getZ() + 0.5 + d.getStepZ() * 0.6 + (d.getStepZ() == 0 ? random.nextDouble() - 0.5 : 0),
                d.getStepX() * 0.4, 0.0, d.getStepZ() * 0.4);
        }
    }
}
