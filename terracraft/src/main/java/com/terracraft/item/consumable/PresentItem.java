package com.terracraft.item.consumable;

import com.terracraft.economy.Coins;
import com.terracraft.item.TerraItem;
import com.terracraft.progression.ProgressionManager;
import com.terracraft.registry.content.CoreItems;
import com.terracraft.registry.content.FrostLegionContent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Present: opens into a small gift. In Hardmode one present in ten holds a Snow Globe (which calls the Frost Legion);
 * the rest give coins, treats, snow or potions.
 */
public class PresentItem extends TerraItem {
    public PresentItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            for (ItemStack gift : roll(serverPlayer, serverPlayer.getRandom())) {
                if (!serverPlayer.getInventory().add(gift)) {
                    serverPlayer.drop(gift, false);
                }
            }
            if (!player.isCreative()) {
                player.getItemInHand(hand).shrink(1);
            }
            level.playSound(null, player.blockPosition(), SoundEvents.BUNDLE_DROP_CONTENTS, SoundSource.PLAYERS, 1.0F, 1.0F);
        }
        return InteractionResult.SUCCESS;
    }

    private static List<ItemStack> roll(ServerPlayer player, RandomSource random) {
        List<ItemStack> gifts = new ArrayList<>();
        if (ProgressionManager.isHardmode(player.level().getServer()) && random.nextInt(10) == 0) {
            gifts.add(new ItemStack(FrostLegionContent.SNOW_GLOBE.get()));
            return gifts;
        }
        switch (random.nextInt(6)) {
            case 0 -> gifts.addAll(Coins.toStacks(Coins.SILVER * (20 + random.nextInt(81))));
            case 1 -> gifts.add(new ItemStack(Items.COOKIE, 5 + random.nextInt(6)));
            case 2 -> gifts.add(new ItemStack(Items.PUMPKIN_PIE, 2 + random.nextInt(3)));
            case 3 -> gifts.add(new ItemStack(Items.SNOWBALL, 16));
            case 4 -> gifts.add(new ItemStack(CoreItems.HEALING_POTION.get(), 3 + random.nextInt(3)));
            default -> gifts.add(new ItemStack(Items.SNOW_BLOCK, 20 + random.nextInt(21)));
        }
        return gifts;
    }
}
