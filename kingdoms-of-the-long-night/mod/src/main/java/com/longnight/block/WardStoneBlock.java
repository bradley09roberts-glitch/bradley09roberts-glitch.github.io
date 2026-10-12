package com.longnight.block;

import com.longnight.game.Wards;
import com.longnight.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Hearth-witchery made solid: a rune pillar that, once chalked and kindled, keeps the restless
 * dead and other night-spawn from rising nearby. The ward fades and must be renewed with chalk.
 * Cost: chalk (bone meal + flint) and the ward weakens the Long Night's grip only locally.
 */
public class WardStoneBlock extends Block {
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public WardStoneBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(ACTIVE);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                              InteractionHand hand, BlockHitResult hit) {
        if (!stack.is(ModItems.WARD_CHALK.get())) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (level instanceof ServerLevel sl) {
            if (!player.getAbilities().instabuild) stack.shrink(1);
            level.setBlock(pos, state.setValue(ACTIVE, true), 3);
            Wards.kindle(sl, pos);
            level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1f, 0.6f);
            player.displayClientMessage(Component.literal("The runes drink the chalk and begin to glow. The ward will hold for about three days."), true);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel sl) Wards.remove(sl, pos);
        super.onRemove(state, level, pos, newState, moved);
    }
}
