package com.starforged.tempest.item;

import com.starforged.item.LoreItem;
import com.starforged.tempest.entity.TempestJavelinEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * Tempest Javelin: right-click to throw a bolt of the storm (<b>Storm Spear</b>); whatever it skewers is struck by
 * lightning and where it lands it becomes a lightning rod. Right-click again to recall it - it tears back to your hand
 * through everything in its way.
 */
public class TempestJavelinItem extends Item {
    private static final Map<UUID, Integer> THROWN = new HashMap<>();

    public TempestJavelinItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel server) {
            Integer id = THROWN.get(player.getUUID());
            Entity existing = id == null ? null : server.getEntity(id);
            if (existing instanceof TempestJavelinEntity javelin && javelin.isAlive()) {
                javelin.recall();
                return InteractionResult.SUCCESS;
            }
            TempestJavelinEntity javelin = TempestJavelinEntity.throwFrom(server, player, 1.0F);
            THROWN.put(player.getUUID(), javelin.getId());
            player.getCooldowns().addCooldown(stack, 10);
            stack.hurtAndBreak(1, player, hand);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        LoreItem.addAbility(builder, "ability.starforged.storm_spear");
        LoreItem.addAbility(builder, "ability.starforged.javelin_recall");
    }
}
