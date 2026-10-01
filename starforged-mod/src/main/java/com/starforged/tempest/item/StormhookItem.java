package com.starforged.tempest.item;

import com.starforged.item.LoreItem;
import com.starforged.tempest.TempestSounds;
import com.starforged.tempest.entity.StormhookEntity;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/** Stormhook: fire a charged chain; use again (or sneak) to let go. See {@link StormhookEntity}. */
public class StormhookItem extends Item {
    private static final Map<UUID, Integer> ACTIVE = new HashMap<>();

    public StormhookItem(Item.Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level instanceof ServerLevel server) {
            Integer active = ACTIVE.remove(player.getUUID());
            Entity existing = active == null ? null : server.getEntity(active);
            if (existing instanceof StormhookEntity hook && hook.isAlive()) {
                hook.discard();
                return InteractionResult.SUCCESS;
            }
            StormhookEntity hook = StormhookEntity.fire(server, player);
            ACTIVE.put(player.getUUID(), hook.getId());
            server.playSound(null, player.getX(), player.getY(), player.getZ(), TempestSounds.HOOK_FIRE.get(), SoundSource.PLAYERS, 1.0F, 1.0F);
            player.getCooldowns().addCooldown(stack, 6);
            stack.hurtAndBreak(1, player, hand);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> builder, TooltipFlag flag) {
        LoreItem.addAbility(builder, "ability.starforged.stormhook");
        LoreItem.addAbility(builder, "ability.starforged.live_wire");
    }
}
