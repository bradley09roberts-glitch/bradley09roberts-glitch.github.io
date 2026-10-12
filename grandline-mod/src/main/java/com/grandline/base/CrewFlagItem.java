package com.grandline.base;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;

/**
 * The crew flag: planting it claims an uninhabited original island for the player's crew. Every rule is checked on
 * the server before the block goes down ({@link CrewBases#check}); a refused flag stays in the hand with the reason.
 * A flag renamed at an anvil gives the crew its name on the sea chart.
 */
public class CrewFlagItem extends BlockItem {
    public CrewFlagItem(Block block, Properties p) {
        super(block, p);
    }

    @Override
    public InteractionResult place(BlockPlaceContext ctx) {
        if (ctx.getLevel().isClientSide || !(ctx.getPlayer() instanceof ServerPlayer sp)) return super.place(ctx);
        CrewBases.Refusal why = CrewBases.check(sp, ctx.getClickedPos());
        if (why != null) {
            CrewBases.tellRefusal(sp, why);
            return InteractionResult.FAIL;
        }
        ItemStack stack = ctx.getItemInHand();
        String name = stack.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME) ? stack.getHoverName().getString() : "";
        InteractionResult r = super.place(ctx);
        if (r.consumesAction() && ctx.getLevel().getBlockState(ctx.getClickedPos()).is(getBlock()))
            CrewBases.claim(sp, ctx.getClickedPos(), name);
        return r;
    }
}
