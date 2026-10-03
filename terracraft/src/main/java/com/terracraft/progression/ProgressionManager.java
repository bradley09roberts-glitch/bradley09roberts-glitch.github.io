package com.terracraft.progression;

import com.terracraft.TerraCraft;
import com.terracraft.network.TerraNetwork;
import com.terracraft.network.packet.SyncProgressionPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The single entry point for reading and changing world progression on the server.
 * <p>
 * Other systems should never poke {@link WorldProgression} directly; they call {@link #set} /
 * {@link #has}. Every change:
 * <ol>
 *     <li>persists the flag,</li>
 *     <li>notifies registered {@link Listener}s (hardmode activation, NPC arrivals, spawn tables...),</li>
 *     <li>broadcasts a Terraria-style announcement for notable milestones,</li>
 *     <li>synchronises the new state to every connected client.</li>
 * </ol>
 */
public final class ProgressionManager {
    /** Reacts to flag changes. Called on the server thread after the change was stored. */
    @FunctionalInterface
    public interface Listener {
        void onFlagChanged(MinecraftServer server, ProgressionFlag flag, boolean newValue);
    }

    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();

    /** Messages shown to everyone the first time a flag becomes true (Terraria's world announcements). */
    private static final Map<ProgressionFlag, ChatFormatting> ANNOUNCEMENTS = Map.of(
        ProgressionFlags.HARDMODE, ChatFormatting.LIGHT_PURPLE,
        ProgressionFlags.ORB_SMASHED, ChatFormatting.DARK_PURPLE,
        ProgressionFlags.PLANTERA, ChatFormatting.GREEN,
        ProgressionFlags.GOLEM, ChatFormatting.GOLD,
        ProgressionFlags.CELESTIAL_EVENTS, ChatFormatting.AQUA,
        ProgressionFlags.SKELETRON, ChatFormatting.GRAY
    );

    private ProgressionManager() {}

    public static void addListener(Listener listener) {
        LISTENERS.add(listener);
    }

    public static WorldProgression data(MinecraftServer server) {
        return WorldProgression.get(server);
    }

    public static boolean has(MinecraftServer server, ProgressionFlag flag) {
        return data(server).has(flag);
    }

    /** Client-side mirror of world progression (set by the client; empty on dedicated servers). */
    public static java.util.function.Supplier<ProgressionView> clientView = () -> null;

    /** Progression as seen from either side (server data on the logical server, mirror on the client). */
    public static ProgressionView view(net.minecraft.world.level.Level level) {
        if (level instanceof net.minecraft.server.level.ServerLevel server) {
            return data(server.getServer());
        }
        ProgressionView view = clientView.get();
        return view != null ? view : WorldProgression.EMPTY;
    }

    public static boolean isHardmode(MinecraftServer server) {
        return has(server, ProgressionFlags.HARDMODE);
    }

    /**
     * Sets a stored flag. Derived flags cannot be set (they are computed) and return {@code false}.
     *
     * @return {@code true} if the value changed
     */
    public static boolean set(MinecraftServer server, ProgressionFlag flag, boolean value) {
        if (flag.isDerived()) {
            TerraCraft.LOGGER.warn("Attempted to set derived progression flag {}", flag.id());
            return false;
        }
        WorldProgression data = data(server);
        if (!data.setStored(flag.id(), value)) {
            return false;
        }
        TerraCraft.LOGGER.info("World progression: {} -> {}", flag.id(), value);
        for (Listener listener : LISTENERS) {
            try {
                listener.onFlagChanged(server, flag, value);
            } catch (RuntimeException e) {
                TerraCraft.LOGGER.error("Progression listener failed for {}", flag.id(), e);
            }
        }
        if (value) {
            announce(server, flag);
        }
        syncAll(server);
        return true;
    }

    /** Convenience for boss deaths: marks the flag and returns whether this was the first kill. */
    public static boolean markDefeated(MinecraftServer server, ProgressionFlag flag) {
        return set(server, flag, true);
    }

    public static int incrementCounter(MinecraftServer server, Identifier counter, int delta) {
        int value = data(server).addCounter(counter, delta);
        syncAll(server);
        return value;
    }

    public static void setCounter(MinecraftServer server, Identifier counter, int value) {
        data(server).setCounter(counter, value);
        syncAll(server);
    }

    public static void setVariants(MinecraftServer server, WorldVariants variants) {
        data(server).setVariants(variants);
        syncAll(server);
    }

    /** Clears every stored flag and counter (debug command). Listeners are notified for each flag. */
    public static void reset(MinecraftServer server) {
        WorldProgression data = data(server);
        Set<Identifier> previous = Set.copyOf(data.storedFlags());
        data.clearAll();
        for (Identifier id : previous) {
            ProgressionFlag flag = ProgressionFlags.resolve(id);
            for (Listener listener : LISTENERS) {
                listener.onFlagChanged(server, flag, false);
            }
        }
        syncAll(server);
    }

    private static void announce(MinecraftServer server, ProgressionFlag flag) {
        ChatFormatting color = ANNOUNCEMENTS.get(flag);
        if (color == null) {
            return;
        }
        Component message = Component.translatable("progression.terracraft.announce." + flag.id().getPath()).withStyle(color);
        server.getPlayerList().broadcastSystemMessage(message, false);
    }

    // ------------------------------------------------------------------ sync

    public static SyncProgressionPacket snapshot(MinecraftServer server) {
        WorldProgression data = data(server);
        return new SyncProgressionPacket(List.copyOf(data.storedFlags()), Map.copyOf(data.counters()), data.variants());
    }

    public static void syncTo(ServerPlayer player) {
        TerraNetwork.sendToPlayer(player, snapshot(player.level().getServer()));
    }

    public static void syncAll(MinecraftServer server) {
        TerraNetwork.sendToAll(snapshot(server));
    }
}
