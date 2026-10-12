package com.grandline.block;

import com.grandline.fruit.FruitWorldData;
import com.grandline.item.DevilFruitItem;
import com.grandline.world.SeaZone;
import com.grandline.world.WorldLayout;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A strange blossom on rare trees where Devil Fruits regrow. When harvested it bears a fruit from the world pool
 * (one of each fruit exists per world), weighted to the danger of the sea it grows in. It then needs days to regrow.
 */
public class FruitBloomBlock extends Block {
    public FruitBloomBlock(Properties props) {
        super(props);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        ServerLevel sl = (ServerLevel) level;
        BloomData data = BloomData.get(sl.getServer());
        long now = sl.getGameTime();
        if (!data.ready(pos, now)) {
            player.displayClientMessage(Component.translatable("grandline.bloom.empty").withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.CONSUME;
        }
        SeaZone zone = WorldLayout.zoneAt(pos.getX(), pos.getZ());
        int max = switch (zone) {
            case EAST_BLUE, WEST_BLUE, NORTH_BLUE, SOUTH_BLUE -> 5;
            case PARADISE -> 7;
            case CALM_BELT -> 8;
            default -> 10;
        };
        String fruit = FruitWorldData.get(sl.getServer()).takeFromPool(sl.random, 1, max);
        data.harvested(pos, now + 24000L * (3 + sl.random.nextInt(4)));
        if (fruit == null) {
            player.displayClientMessage(Component.translatable("grandline.bloom.none_left").withStyle(ChatFormatting.GRAY), true);
            return InteractionResult.CONSUME;
        }
        player.getInventory().add(DevilFruitItem.of(fruit));
        player.sendSystemMessage(Component.translatable("grandline.bloom.found").withStyle(ChatFormatting.LIGHT_PURPLE));
        return InteractionResult.CONSUME;
    }
}
