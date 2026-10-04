package com.terracraft.block;

import com.mojang.serialization.MapCodec;
import com.terracraft.combat.DamageClass;
import com.terracraft.combat.TerrariaDifficulty;
import com.terracraft.entity.projectile.ProjectileKinds;
import com.terracraft.entity.projectile.TerrariaProjectile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Super Dart Trap: a Lihzahrd brick with a hole in its face. It watches the {@value #RANGE} blocks in front of it
 * and shoots a poison dart when a player steps in (Terraria triggers them with pressure plates; here the trap sees
 * you). Runs on scheduled block ticks, so it costs nothing while its chunk is unloaded.
 */
public class DartTrapBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<DartTrapBlock> CODEC = simpleCodec(DartTrapBlock::new);
    private static final int RANGE = 12;
    private static final int WATCH_TICKS = 10;
    private static final int RELOAD_TICKS = 50;

    public DartTrapBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(this)) {
            level.scheduleTick(pos, this, WATCH_TICKS);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        Direction facing = state.getValue(FACING);
        BlockPos front = pos.relative(facing);
        AABB watch = new AABB(front).expandTowards(facing.getStepX() * (RANGE - 1), 1, facing.getStepZ() * (RANGE - 1)).inflate(0.4, 0, 0.4);
        Player victim = level.getEntitiesOfClass(Player.class, watch, p -> p.isAlive() && !p.isCreative() && !p.isSpectator())
            .stream().findFirst().orElse(null);
        boolean seen = victim != null;
        if (seen) {
            // straight out of the hole, nudged towards whoever stepped in front of it
            Vec3 from = Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(facing.getUnitVec3i()).scale(0.7));
            Vec3 dir = victim.position().add(0, victim.getBbHeight() * 0.5, 0).subtract(from).normalize();
            TerrariaProjectile.shoot(level, null, ProjectileKinds.POISON_DART, from, dir, 12.0F, 0.0F,
                60.0F * TerrariaDifficulty.enemyDamageMultiplier(level), DamageClass.GENERIC, 0, 1.0F);
            level.playSound(null, pos, SoundEvents.DISPENSER_LAUNCH, SoundSource.BLOCKS, 1.0F, 1.6F);
        }
        level.scheduleTick(pos, this, seen ? RELOAD_TICKS : WATCH_TICKS);
    }
}
