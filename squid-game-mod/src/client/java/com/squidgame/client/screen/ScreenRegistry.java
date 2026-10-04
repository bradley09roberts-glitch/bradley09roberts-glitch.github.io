package com.squidgame.client.screen;

import com.squidgame.SquidGameMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * Registry for server-opened game screens. Games register a factory under a screen id and receive OPEN / UPDATE /
 * CLOSE messages ({@code OpenScreenPayload}). Game client classes are discovered by name:
 * {@code com.squidgame.client.game.<pkg>.<Name>Client#register()}.
 */
public final class ScreenRegistry {
    public interface Handler {
        /** Create and show the screen. */
        Screen open(Minecraft mc, CompoundTag data);

        /** Update the already open screen (return false if it is not open any more and should be reopened). */
        default boolean update(Minecraft mc, Screen current, CompoundTag data) {
            return false;
        }
    }

    private static final Map<String, Handler> HANDLERS = new HashMap<>();

    private ScreenRegistry() {
    }

    public static void register(String id, Handler handler) {
        HANDLERS.put(id, handler);
    }

    public static void register(String id, Function<CompoundTag, Screen> factory) {
        HANDLERS.put(id, (mc, data) -> factory.apply(data));
    }

    public static void handle(String id, int action, CompoundTag data) {
        Minecraft mc = Minecraft.getInstance();
        Handler h = HANDLERS.get(id);
        if (h == null) {
            SquidGameMod.LOGGER.warn("No client screen registered for '{}'", id);
            return;
        }
        switch (action) {
            case 0 -> mc.setScreen(h.open(mc, data));
            case 1 -> {
                Screen cur = mc.screen;
                if (cur == null || !h.update(mc, cur, data)) {
                    mc.setScreen(h.open(mc, data));
                }
            }
            default -> {
                Screen cur = mc.screen;
                if (cur instanceof ClosableByServer c && c.screenId().equals(id)) {
                    mc.setScreen(null);
                }
            }
        }
    }

    /** Screens implementing this can be closed by the server. */
    public interface ClosableByServer {
        String screenId();
    }

    /** Looks up optional per-game client registration classes. */
    public static void discoverGameClients() {
        for (String cls : new String[]{
                "com.squidgame.client.game.redlight.RedLightClient", "com.squidgame.client.game.dalgona.DalgonaClient",
                "com.squidgame.client.game.tug.TugClient", "com.squidgame.client.game.marbles.MarblesClient",
                "com.squidgame.client.game.bridge.BridgeClient", "com.squidgame.client.game.finale.FinaleClient"}) {
            try {
                Method m = Class.forName(cls).getMethod("register");
                m.invoke(null);
                SquidGameMod.LOGGER.info("Registered game client {}", cls);
            } catch (ClassNotFoundException ignored) {
                // optional
            } catch (ReflectiveOperationException e) {
                SquidGameMod.LOGGER.error("Failed to register {}", cls, e);
            }
        }
    }
}
