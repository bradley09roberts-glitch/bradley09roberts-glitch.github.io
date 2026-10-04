package com.squidgame.block;

import com.mojang.serialization.MapCodec;
import com.squidgame.tournament.TournamentManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.TransparentBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Custom block classes for the tournament (kept in one file; each is tiny). */
public final class SquidBlocks {
    private SquidBlocks() {
    }

    /**
     * The glass bridge panel. Safe and fragile panels are <b>the same block</b>: which is which exists only in the
     * server-side game state, so there is no visual (or debug-screen) tell.
     */
    public static class BridgeGlassBlock extends TransparentBlock {
        public static final MapCodec<BridgeGlassBlock> CODEC = simpleCodec(BridgeGlassBlock::new);

        public BridgeGlassBlock(Properties props) {
            super(props);
        }

        @Override
        protected MapCodec<? extends TransparentBlock> codec() {
            return CODEC;
        }
    }

    /** Plain full cube. */
    public static class PlainBlock extends Block {
        public static final MapCodec<PlainBlock> CODEC = simpleCodec(PlainBlock::new);

        public PlainBlock(Properties props) {
            super(props);
        }

        @Override
        protected MapCodec<? extends Block> codec() {
            return CODEC;
        }
    }

    /** Invisible collision-only wall used for arena boundaries and closed doorways. */
    public static class InvisibleWallBlock extends Block {
        public static final MapCodec<InvisibleWallBlock> CODEC = simpleCodec(InvisibleWallBlock::new);

        public InvisibleWallBlock(Properties props) {
            super(props);
        }

        @Override
        protected MapCodec<? extends Block> codec() {
            return CODEC;
        }

        @Override
        protected RenderShape getRenderShape(BlockState state) {
            return RenderShape.INVISIBLE;
        }

        @Override
        protected float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
            return 1.0F;
        }

        @Override
        protected boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
            return true;
        }
    }

    /** Wall-mounted monitor. {@code FACING} is the direction the screen faces. */
    public static class MonitorBlock extends HorizontalDirectionalBlock {
        public static final MapCodec<MonitorBlock> CODEC = simpleCodec(MonitorBlock::new);
        private static final VoxelShape NORTH = Block.box(0, 0, 10, 16, 16, 16);
        private static final VoxelShape SOUTH = Block.box(0, 0, 0, 16, 16, 6);
        private static final VoxelShape WEST = Block.box(10, 0, 0, 16, 16, 16);
        private static final VoxelShape EAST = Block.box(0, 0, 0, 6, 16, 16);

        public MonitorBlock(Properties props) {
            super(props);
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
        public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext ctx) {
            return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return switch (state.getValue(FACING)) {
                case SOUTH -> SOUTH;
                case WEST -> WEST;
                case EAST -> EAST;
                default -> NORTH;
            };
        }
    }

    /** Sign-up kiosk: using it registers / unregisters the player for the tournament. */
    public static class RegistrationTerminalBlock extends HorizontalDirectionalBlock {
        public static final MapCodec<RegistrationTerminalBlock> CODEC = simpleCodec(RegistrationTerminalBlock::new);
        private static final VoxelShape SHAPE = Block.box(2, 0, 2, 14, 16, 14);

        public RegistrationTerminalBlock(Properties props) {
            super(props);
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
        public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext ctx) {
            return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return SHAPE;
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (!level.isClientSide && player instanceof ServerPlayer sp) {
                TournamentManager.onTerminalUse(sp, pos);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
    }

    /** Dalgona table: using it starts the carving challenge for a seated contestant (handled by the game). */
    public static class DalgonaStationBlock extends HorizontalDirectionalBlock {
        public static final MapCodec<DalgonaStationBlock> CODEC = simpleCodec(DalgonaStationBlock::new);
        private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 8, 16);

        public DalgonaStationBlock(Properties props) {
            super(props);
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
        public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext ctx) {
            return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
        }

        @Override
        protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext ctx) {
            return SHAPE;
        }

        @Override
        protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
            if (!level.isClientSide && player instanceof ServerPlayer sp) {
                TournamentManager.onBlockUse(sp, pos);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
    }

    static DirectionProperty facingProperty() {
        return HorizontalDirectionalBlock.FACING;
    }

    static VoxelShape empty() {
        return Shapes.empty();
    }
}
