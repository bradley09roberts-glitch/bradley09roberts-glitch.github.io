package com.terracraft.mining;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.bus.BusGroup;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;

import java.lang.invoke.MethodHandles;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Enforces pickaxe power: blocks whose requirement exceeds the held tool's power cannot be broken and do
 * not drop. Runs on both sides with the same synced table, so the client never shows false progress.
 */
public final class MiningEvents {
    private static final Map<UUID, Long> LAST_WARNING = new ConcurrentHashMap<>();

    private MiningEvents() {}

    public static void register() {
        BusGroup.DEFAULT.register(MethodHandles.lookup(), MiningEvents.class);
    }

    private static boolean insufficient(Player player, BlockState state) {
        if (player.getAbilities().instabuild) {
            return false;
        }
        int required = MiningPower.required(state);
        return required > 0 && ToolPowers.pickaxePower(player.getMainHandItem()) < required;
    }

    @SubscribeEvent
    static boolean onBreakSpeed(PlayerEvent.BreakSpeed event) {
        Player player = event.getEntity();
        BlockState state = event.getState();
        if (!insufficient(player, state)) {
            return false;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            long now = serverPlayer.level().getGameTime();
            Long last = LAST_WARNING.get(player.getUUID());
            if (last == null || now - last > 40) {
                LAST_WARNING.put(player.getUUID(), now);
                serverPlayer.sendOverlayMessage(Component.translatable("message.terracraft.pickaxe_too_weak",
                    MiningPower.required(state)).withStyle(ChatFormatting.RED));
            }
        }
        return true;
    }

    @SubscribeEvent
    static void onHarvestCheck(PlayerEvent.HarvestCheck event) {
        if (insufficient(event.getEntity(), event.getTargetBlock())) {
            event.setCanHarvest(false);
        }
    }
}
