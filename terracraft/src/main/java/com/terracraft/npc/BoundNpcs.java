package com.terracraft.npc;

import com.terracraft.economy.Coins;
import com.terracraft.item.TerraItemStats;
import com.terracraft.item.modifier.Modifier;
import com.terracraft.item.modifier.Modifiers;
import com.terracraft.progression.ProgressionFlags;
import com.terracraft.progression.ProgressionManager;
import com.terracraft.world.TerrariaLayer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;

import java.util.Locale;

/**
 * NPCs that must be found before they move in, and the Goblin Tinkerer's services:
 * <ul>
 *     <li>after the Goblin Army is defeated, a Bound Goblin waits tied up somewhere in the caverns near a player;
 *     talking to him frees him and the Goblin Tinkerer joins the town;</li>
 *     <li>the Goblin Tinkerer reforges the held weapon, tool or accessory for coins (a new random prefix).</li>
 * </ul>
 */
public final class BoundNpcs {
    private BoundNpcs() {}

    public static void register() {
        TickEvent.ServerTickEvent.Post.BUS.addListener(BoundNpcs::onServerTick);
    }

    private static void onServerTick(TickEvent.ServerTickEvent.Post event) {
        MinecraftServer server = event.server();
        if (server.getTickCount() % 200 != 51 || !ProgressionManager.has(server, ProgressionFlags.GOBLIN_ARMY)
            || ProgressionManager.has(server, ProgressionFlags.GOBLIN_TINKERER_RESCUED) || NpcManager.isPresent(server, TownNpcs.BOUND_GOBLIN.id())) {
            return;
        }
        ServerLevel level = server.overworld();
        for (ServerPlayer player : level.players()) {
            TerrariaLayer layer = TerrariaLayer.ofHeight(player.getBlockY());
            if (player.isSpectator() || layer != TerrariaLayer.UNDERGROUND && layer != TerrariaLayer.CAVERN) {
                continue;
            }
            BlockPos spot = findSpot(level, player.blockPosition(), level.getRandom());
            if (spot != null) {
                NpcManager.spawn(level, NpcWorldData.get(server), TownNpcs.BOUND_GOBLIN, spot, "", false);
                return;
            }
        }
    }

    /** An open cave floor 16-36 blocks from the player (out of sight, like Terraria's off-screen placement). */
    private static BlockPos findSpot(ServerLevel level, BlockPos center, RandomSource random) {
        for (int attempt = 0; attempt < 30; attempt++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double distance = 16 + random.nextDouble() * 20;
            int x = center.getX() + (int) (Math.cos(angle) * distance);
            int z = center.getZ() + (int) (Math.sin(angle) * distance);
            for (int dy = 8; dy >= -8; dy--) {
                BlockPos feet = new BlockPos(x, center.getY() + dy, z);
                if (!level.isLoaded(feet)) {
                    break;
                }
                if (level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP) && level.getBlockState(feet).isAir()
                    && level.getBlockState(feet.above()).isAir() && level.getFluidState(feet).isEmpty() && !level.canSeeSky(feet)) {
                    return feet;
                }
            }
        }
        return null;
    }

    /** Talking to the Bound Goblin frees him: he becomes the (homeless) Goblin Tinkerer. */
    public static void rescue(ServerPlayer player, TownNpc bound) {
        ServerLevel level = player.level();
        MinecraftServer server = level.getServer();
        ProgressionManager.set(server, ProgressionFlags.GOBLIN_TINKERER_RESCUED, true);
        BlockPos at = bound.blockPosition();
        bound.discard();
        NpcManager.forget(server, TownNpcs.BOUND_GOBLIN.id());
        TownNpc tinkerer = NpcManager.spawn(level, NpcWorldData.get(server), TownNpcs.GOBLIN_TINKERER, null, "", true, at);
        if (tinkerer != null) {
            NpcManager.openChat(player, tinkerer, "npc.terracraft.goblin_tinkerer.rescued", player.getName().getString());
        }
    }

    /** Reforge: a new random prefix for the held item, costing about a third of its value (at least 1 silver). */
    public static void reforge(ServerPlayer player, TownNpc npc) {
        ItemStack stack = player.getMainHandItem();
        Modifier.Category type = Modifiers.typeOf(stack);
        if (type == null) {
            NpcManager.openChat(player, npc, "npc.terracraft.goblin_tinkerer.cannot_reforge", "");
            return;
        }
        int baseValue = stack.getItem().components().getOrDefault(com.terracraft.registry.ModDataComponents.STATS, TerraItemStats.NONE).value();
        long cost = Math.max(Coins.SILVER, baseValue / 3L);
        if (Coins.total(player) < cost) {
            NpcManager.openChat(player, npc, "npc.terracraft.goblin_tinkerer.too_poor", Coins.format(cost).getString());
            return;
        }
        Coins.remove(player, cost);
        Modifier modifier = Modifiers.roll(stack, player.getRandom());
        if (modifier != null) {
            Modifiers.apply(stack, modifier);
        }
        player.level().playSound(null, player.blockPosition(), SoundEvents.ANVIL_USE, SoundSource.PLAYERS, 0.8F, 1.2F);
        player.sendOverlayMessage(Component.translatable("message.terracraft.reforged", stack.getHoverName()).withStyle(ChatFormatting.GOLD));
        NpcManager.openChat(player, npc, "npc.terracraft.goblin_tinkerer.reforged", modifier == null ? "" : prettyName(modifier));
    }

    private static String prettyName(Modifier modifier) {
        String id = modifier.id().replace("_accessory", "");
        return id.substring(0, 1).toUpperCase(Locale.ROOT) + id.substring(1);
    }
}
