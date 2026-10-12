package com.longnight.client;

import com.longnight.LongNight;
import com.longnight.sim.Settlement;
import com.longnight.sim.WorldSim;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.commands.CommandSourceStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Development-only visual test pilot. Does nothing unless the JVM is started with
 * -Dlongnight.autoshot=true. It flies the camera to the largest settlement, lets it materialize,
 * takes screenshots (day, street level, night), prints a status line, and quits the game.
 */
@EventBusSubscriber(modid = LongNight.MODID, value = Dist.CLIENT)
public final class AutoShot {
    private AutoShot() {}

    static final boolean ENABLED = Boolean.getBoolean("longnight.autoshot");
    static int ticks;
    static int step;
    static Settlement target;

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post e) {
        if (!ENABLED) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null) return;
        ticks++;
        if (step == 0 && ticks > 100) {
            WorldSim sim = WorldSim.get(server);
            List<Settlement> list = new ArrayList<>();
            for (Settlement s : sim.settlements.values()) if (s.kind == Settlement.Kind.SETTLEMENT) list.add(s);
            list.sort(Comparator.comparingInt((Settlement s) -> s.residents.size()).reversed());
            if (list.isEmpty()) return;
            target = list.get(0);
            LongNight.LOG.info("[autoshot] flying to {}", target.name);
            run(server, "gamemode spectator @a");
            run(server, "time set 2000");
            run(server, "weather clear");
            run(server, "gamerule doDaylightCycle false");
            view(server, 0, 34, 46, 180f, 35f);
            step = 1;
            ticks = 0;
        } else if (step == 1 && ticks > 900) {
            shot(mc, "longnight_overview");
            view(server, 0, 3, 12, 180f, 10f);
            step = 2;
            ticks = 0;
        } else if (step == 2 && ticks > 300) {
            shot(mc, "longnight_street");
            view(server, -20, 18, -20, -45f, 30f);
            step = 3;
            ticks = 0;
        } else if (step == 3 && ticks > 300) {
            shot(mc, "longnight_side");
            run(server, "time set 14000");
            view(server, 0, 24, 36, 180f, 30f);
            step = 4;
            ticks = 0;
        } else if (step == 4 && ticks > 300) {
            shot(mc, "longnight_night");
            WorldSim sim = WorldSim.get(server);
            LongNight.LOG.info("[autoshot] done: settlement {} materialized={} bodies={} buildings={}", target.name, target.materialized,
                    com.longnight.world.Embodiment.bodyCount(), target.buildings.size());
            step = 5;
            ticks = 0;
        } else if (step == 5 && ticks > 60) {
            step = 6;
            mc.stop();
        }
    }

    static void view(IntegratedServer server, int dx, int dy, int dz, float yaw, float pitch) {
        var c = target.center;
        run(server, String.format("tp @a %d %d %d %.1f %.1f", c.getX() + dx, c.getY() + dy, c.getZ() + dz, yaw, pitch));
    }

    static void run(IntegratedServer server, String cmd) {
        CommandSourceStack src = server.createCommandSourceStack().withPermission(4).withSuppressedOutput();
        server.getCommands().performPrefixedCommand(src, cmd);
    }

    static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), msg -> LongNight.LOG.info("[autoshot] {}", msg.getString()));
    }
}
