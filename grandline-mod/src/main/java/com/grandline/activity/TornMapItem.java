package com.grandline.activity;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Half of a torn treasure map (left or right). Using one while carrying the other half pieces them together into a
 * grand treasure map: it leads further out, and its find pays twice the experience plus a purse of Beli.
 */
public class TornMapItem extends Item {
    public TornMapItem(Properties p) {
        super(p);
    }

    public static int half(ItemStack s) {
        Integer h = s.get(ActivityRegistry.MAP_HALF.get());
        return h == null ? 0 : h;
    }

    public static ItemStack of(int half) {
        ItemStack s = new ItemStack(ActivityItems.TORN_MAP.get());
        s.set(ActivityRegistry.MAP_HALF.get(), half);
        return s;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer sp) {
            ItemStack map = combine(sp, stack);
            if (map.isEmpty()) sp.displayClientMessage(Component.translatable("grandline.treasure.torn_alone").withStyle(ChatFormatting.GRAY), true);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** Pieces this half together with the other half from the inventory; returns the grand map (EMPTY if no match). */
    public static ItemStack combine(ServerPlayer sp, ItemStack stack) {
        int want = 1 - half(stack);
        var inv = sp.getInventory();
        ItemStack other = ItemStack.EMPTY;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack st = inv.getItem(i);
            if (st != stack && st.getItem() instanceof TornMapItem && half(st) == want) {
                other = st;
                break;
            }
        }
        if (other.isEmpty() || sp.level().dimension() != Level.OVERWORLD) return ItemStack.EMPTY;
        long salt = sp.getUUID().getMostSignificantBits() ^ sp.level().getGameTime();
        TreasureTarget t = TreasureMaps.pick(sp.server, sp.blockPosition(), salt, sp, true);
        if (t == null) return ItemStack.EMPTY;
        stack.shrink(1);
        other.shrink(1);
        ItemStack map = new ItemStack(ActivityItems.TREASURE_MAP.get());
        map.set(ActivityRegistry.TREASURE.get(), t);
        ItemStack given = map.copy();
        if (!inv.add(map)) sp.drop(map, false);
        sp.level().playSound(null, sp.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1f, 0.8f);
        sp.sendSystemMessage(Component.translatable("grandline.treasure.pieced", t.islandName()).withStyle(ChatFormatting.LIGHT_PURPLE));
        return given;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext ctx, List<Component> tip, TooltipFlag flag) {
        tip.add(Component.translatable("item.grandline.torn_map.half." + half(stack)).withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("item.grandline.torn_map.hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
