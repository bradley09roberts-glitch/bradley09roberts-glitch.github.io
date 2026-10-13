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
            run(server, "gamemode creative @a");
            run(server, "effect give @a minecraft:night_vision 99999 0 true");
            run(server, "time set 2000");
            run(server, "weather clear");
            run(server, "gamerule doDaylightCycle false");
            view(server, 0, 34, 46, 180f, 35f);
            step = 1;
            ticks = 0;
        } else if (step == 1 && ticks > 400 && (ticks > 4000 || target.materialized && !com.longnight.world.Placer.pending(target))) {
            LongNight.LOG.info("[autoshot] {} materialized={} after {} ticks", target.name, target.materialized, ticks);
            step = 8; // give chunk rendering a moment after the last block
            ticks = 0;
            view(server, 0, 30, 44, 180f, 32f); // re-aim: the town's real ground height is known now
        } else if (step == 8 && ticks > 200) {
            shot(mc, "longnight_overview");
            view(server, 0, 3, 12, 180f, 10f);
            step = 2;
            ticks = 0;
        } else if (step == 2 && ticks > 300) {
            shot(mc, "longnight_street");
            // close-up of a real person
            var level = server.overworld();
            var people = level.getEntitiesOfClass(com.longnight.entity.CitizenEntity.class,
                    new net.minecraft.world.phys.AABB(target.center).inflate(48), c -> !c.isSleeping());
            if (!people.isEmpty()) {
                var c = people.get(0);
                c.brain.pause(400);
                c.getNavigation().stop();
                double px = c.getX() + 3, pz = c.getZ() + 3;
                float yaw = (float) Math.toDegrees(Math.atan2(-(c.getX() - px), c.getZ() - pz));
                run(server, String.format(java.util.Locale.ROOT, "tp @a %.1f %.1f %.1f %.1f 15", px, c.getY() + 0.5, pz, yaw));
                LongNight.LOG.info("[autoshot] portrait of {}", c.getCustomName() == null ? "?" : c.getCustomName().getString());
            }
            step = 7;
            ticks = 0;
        } else if (step == 7 && ticks > 100) {
            shot(mc, "longnight_person");
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
            // then follow a road out of town
            WorldSim sim0 = WorldSim.get(server);
            for (WorldSim.Road r : sim0.roads) {
                Settlement other = r.a.equals(target.id) ? sim0.settlement(r.b) : r.b.equals(target.id) ? sim0.settlement(r.a) : null;
                if (other == null) continue;
                double dx = other.center.getX() - target.center.getX(), dz = other.center.getZ() - target.center.getZ();
                double len = Math.sqrt(dx * dx + dz * dz);
                int ox = (int) (dx / len * (target.radius + 30)), oz = (int) (dz / len * (target.radius + 30));
                float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
                run(server, "time set 4000");
                view(server, ox, 14, oz, yaw, 25f);
                LongNight.LOG.info("[autoshot] road towards {}", other.name);
                break;
            }
            step = 9;
            ticks = 0;
        } else if (step == 9 && ticks > 400) {
            shot(mc, "longnight_road");
            WorldSim sim = WorldSim.get(server);
            LongNight.LOG.info("[autoshot] done: settlement {} materialized={} bodies={} buildings={}", target.name, target.materialized,
                    com.longnight.world.Embodiment.bodyCount(), target.buildings.size());
            step = 5;
            ticks = 0;
        } else if (step == 5 && ticks > 60) {
            step = 6;
            if (!"false".equals(System.getProperty("longnight.autoshot.quit"))) mc.stop();
        }
    }

    static void view(IntegratedServer server, int dx, int dy, int dz, float yaw, float pitch) {
        var c = target.center;
        for (var p : server.getPlayerList().getPlayers()) {
            p.getAbilities().mayfly = true;
            p.getAbilities().flying = true; // hover where the camera is put
            p.onUpdateAbilities();
        }
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
