package com.terracraft.command;

import com.terracraft.TerraCraft;
import com.terracraft.config.TerraConfig;
import com.terracraft.player.HealthManager;
import com.terracraft.player.ManaManager;
import com.terracraft.player.PlayerEvents;
import com.terracraft.player.TerraPlayerData;
import com.terracraft.progression.ProgressionFlag;
import com.terracraft.progression.ProgressionFlags;
import com.terracraft.progression.ProgressionManager;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;

/**
 * Server-side implementation of developer-menu actions. Shared by the dev menu packet and commands so both
 * paths behave identically. Every entry point re-checks permissions.
 */
public final class DevActions {
    private DevActions() {}

    public static boolean isAllowed(ServerPlayer player) {
        return player.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER);
    }

    public static void perform(ServerPlayer player, String action, String argument) {
        if (!isAllowed(player)) {
            player.sendSystemMessage(Component.translatable("message.terracraft.dev.no_permission").withStyle(ChatFormatting.RED));
            return;
        }
        MinecraftServer server = player.level().getServer();
        TerraPlayerData data = TerraPlayerData.get(player);
        switch (action) {
            case "toggle_flag" -> {
                ProgressionFlag flag = ProgressionFlags.resolve(argument);
                boolean value = !ProgressionManager.has(server, flag);
                ProgressionManager.set(server, flag, value);
                feedback(player, flag.id() + " = " + value);
            }
            case "reset_progression" -> {
                ProgressionManager.reset(server);
                feedback(player, "World progression reset");
            }
            case "heal" -> {
                HealthManager.healToFull(player);
                ManaManager.restore(data, data.maxMana());
                feedback(player, "Healed");
            }
            case "max_stats" -> {
                data.setLifeCrystals(TerraConfig.COMMON.maxLifeCrystals.get());
                data.setLifeFruit(TerraConfig.COMMON.maxLifeFruit.get());
                data.setManaCrystals(TerraConfig.COMMON.maxManaCrystals.get());
                PlayerEvents.refreshAndSync(player);
                HealthManager.healToFull(player);
                ManaManager.restore(data, data.maxMana());
                feedback(player, "Maxed life and mana upgrades");
            }
            case "reset_stats" -> {
                data.setLifeCrystals(0);
                data.setLifeFruit(0);
                data.setManaCrystals(0);
                PlayerEvents.refreshAndSync(player);
                feedback(player, "Reset life and mana upgrades");
            }
            case "add_life_crystal" -> {
                data.setLifeCrystals(data.lifeCrystals() + 1);
                PlayerEvents.refreshAndSync(player);
            }
            case "add_mana_crystal" -> {
                data.setManaCrystals(data.manaCrystals() + 1);
                PlayerEvents.refreshAndSync(player);
            }
            case "time_day" -> runCommand(player, "time set day");
            case "time_night" -> runCommand(player, "time set night");
            default -> TerraCraft.LOGGER.warn("Unknown dev action '{}' from {}", action, player.getGameProfile().name());
        }
    }

    /** Runs a vanilla command as the player (used where 26.2 APIs are clock based, e.g. time). */
    private static void runCommand(ServerPlayer player, String command) {
        MinecraftServer server = player.level().getServer();
        server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
    }

    private static void feedback(ServerPlayer player, String text) {
        player.sendSystemMessage(Component.literal("[TerraCraft] " + text).withStyle(ChatFormatting.GOLD));
    }
}
