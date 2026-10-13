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
        if (COMPANY) {
            company(mc, server);
            return;
        }
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
            LongNight.LOG.info("[autoshot] {} materialized={} after {} ticks; while raising it: {}", target.name, target.materialized, ticks,
                    com.longnight.world.Perf.report());
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
            // close-up of a real person, from a spot with open air around the camera
            server.execute(() -> {
                var level = server.overworld();
                var people = level.getEntitiesOfClass(com.longnight.entity.CitizenEntity.class,
                        new net.minecraft.world.phys.AABB(target.center).inflate(48), c -> !c.isSleeping());
                if (people.isEmpty()) return;
                var c = people.get(0);
                c.brain.pause(400);
                c.getNavigation().stop();
                double px = c.getX() + 3, pz = c.getZ() + 3, py = c.getY() + 0.5;
                for (int i = 0; i < 8; i++) {
                    double a = i * Math.PI / 4;
                    double x = c.getX() + Math.cos(a) * 3, z = c.getZ() + Math.sin(a) * 3;
                    if (open(level, net.minecraft.core.BlockPos.containing(x, c.getY() + 0.5, z))) {
                        px = x;
                        pz = z;
                        break;
                    }
                }
                py = clearY(level, (int) Math.floor(px), (int) Math.floor(py), (int) Math.floor(pz));
                float yaw = (float) Math.toDegrees(Math.atan2(-(c.getX() - px), c.getZ() - pz));
                float pitch = (float) Math.toDegrees(Math.atan2(py - c.getY(), 3));
                run(server, String.format(java.util.Locale.ROOT, "tp @a %.1f %.1f %.1f %.1f %.1f", px, py, pz, yaw, pitch));
                LongNight.LOG.info("[autoshot] portrait of {}", c.getCustomName() == null ? "?" : c.getCustomName().getString());
            });
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
            LongNight.LOG.info("[autoshot] {}", com.longnight.world.Perf.report());
            step = 5;
            ticks = 0;
        } else if (step == 5 && ticks > 60) {
            step = 6;
            if (!"false".equals(System.getProperty("longnight.autoshot.quit"))) mc.stop();
        }
    }

    static final boolean COMPANY = Boolean.getBoolean("longnight.autoshot.company");
    static int tries;

    /**
     * Company mode: the new player sets out with their company, settles, and the pilot watches the village rise
     * (skipping days in steps), taking a picture at each stage.
     */
    static void company(Minecraft mc, IntegratedServer server) {
        if (step == 0 && ticks > 200) {
            server.execute(() -> {
                var sp = server.getPlayerList().getPlayers().get(0);
                WorldSim sim = WorldSim.get(server);
                if (com.longnight.sim.Band.of(sim, sp.getUUID()) == null) com.longnight.game.Founding.startBand(sp, sim, 6);
                run(server, "gamemode creative @a"); // the pilot must not die on the road; the company does not care
                sp.getAbilities().flying = false;
                sp.onUpdateAbilities();
                run(server, "time set 2000");
                run(server, "weather clear");
                run(server, "gamerule doDaylightCycle false");
                var top = server.overworld().getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, sp.blockPosition());
                sp.teleportTo(top.getX() + 0.5, top.getY(), top.getZ() + 0.5);
                LongNight.LOG.info("[autoshot] company of {} people sets out", com.longnight.sim.Band.of(sim, sp.getUUID()).residents.size());
            });
            step = 1;
            ticks = 0;
        } else if (step == 1 && ticks > 200 && (ticks > 1600 || companions(server) >= 3)) {
            server.execute(() -> {
                var sp = server.getPlayerList().getPlayers().get(0);
                var level = server.overworld();
                var near = level.getEntitiesOfClass(com.longnight.entity.CitizenEntity.class, sp.getBoundingBox().inflate(16));
                if (near.isEmpty()) return;
                double cx = 0, cz = 0, cy = 0;
                for (var c : near) {
                    cx += c.getX();
                    cy += c.getY();
                    cz += c.getZ();
                }
                cx /= near.size();
                cy /= near.size();
                cz /= near.size();
                float yaw = (float) Math.toDegrees(Math.atan2(-(cx - sp.getX()), cz - sp.getZ()));
                run(server, String.format(java.util.Locale.ROOT, "tp @a %.1f %.1f %.1f %.1f 12", sp.getX(), sp.getY(), sp.getZ(), yaw));
                LongNight.LOG.info("[autoshot] {} companions in view", near.size());
            });
            step = 2;
            ticks = 0;
        } else if (step == 2 && ticks > 80) {
            shot(mc, "company_road");
            step = 3;
            ticks = 0;
        } else if (step == 3 && ticks > 40) {
            server.execute(() -> {
                var sp = server.getPlayerList().getPlayers().get(0);
                WorldSim sim = WorldSim.get(server);
                var band = com.longnight.sim.Band.of(sim, sp.getUUID());
                if (band == null) return;
                String why = com.longnight.sim.Band.unsuitable(sim, sp.blockPosition().below(), sp.getUUID());
                if (why != null && tries++ < 6) {
                    // too close to a town: walk on, away from it (the company catches up)
                    var near = sim.nearestSettlement(sp.blockPosition(), com.longnight.sim.Settlement.Kind.SETTLEMENT);
                    double dx = near == null ? 1 : sp.getX() - near.center.getX(), dz = near == null ? 0 : sp.getZ() - near.center.getZ();
                    double len = Math.max(1, Math.sqrt(dx * dx + dz * dz));
                    int x = (int) (sp.getX() + dx / len * 160), z = (int) (sp.getZ() + dz / len * 160);
                    run(server, "forceload add " + x + " " + z);
                    var top = server.overworld().getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new net.minecraft.core.BlockPos(x, 0, z));
                    sp.teleportTo(x + 0.5, top.getY(), z + 0.5);
                    LongNight.LOG.info("[autoshot] {} Walking on to {} {}", why, x, z);
                    return;
                }
                boolean ok = com.longnight.game.Founding.settleBand(sp, server.overworld(), sp.blockPosition().below(), true);
                target = band;
                LongNight.LOG.info("[autoshot] settled={} as {} at {}", ok, band.name, band.center);
            });
            if (target != null || tries >= 6) {
                step = 4;
            }
            ticks = 0;
        } else if (step == 4 && ticks > 500 && target != null) {
            view(server, 0, 14, 20, 180f, 30f);
            step = 5;
            ticks = 0;
        } else if (step == 5 && ticks > 120) {
            shot(mc, "company_camp");
            run(server, "longnight admin skip 5");
            view(server, 0, 24, 32, 180f, 35f);
            step = 6;
            ticks = 0;
        } else if (step == 6 && ticks > 1200) {
            shot(mc, "company_day5");
            report();
            run(server, "longnight admin skip 10");
            step = 7;
            ticks = 0;
        } else if (step == 7 && ticks > 1500) {
            view(server, 0, 26, 34, 180f, 35f);
            step = 8;
            ticks = 0;
        } else if (step == 8 && ticks > 120) {
            shot(mc, "company_day15");
            report();
            run(server, "longnight admin skip 10");
            step = 9;
            ticks = 0;
        } else if (step == 9 && ticks > 1500) {
            view(server, -24, 26, 24, -135f, 35f);
            step = 10;
            ticks = 0;
        } else if (step == 10 && ticks > 120) {
            shot(mc, "company_day25");
            report();
            step = 11;
        }
    }

    static int companions(IntegratedServer server) {
        var sp = server.getPlayerList().getPlayers().get(0);
        return server.overworld().getEntitiesOfClass(com.longnight.entity.CitizenEntity.class, sp.getBoundingBox().inflate(16)).size();
    }

    static void report() {
        if (target == null) return;
        StringBuilder b = new StringBuilder();
        for (var x : target.buildings) b.append(x.type.id()).append(x.usable() ? "" : "(" + x.progress + "/" + x.total + ")").append(" placed=").append(x.placed).append(' ');
        LongNight.LOG.info("[autoshot] {} {} pop={} buildings: {}", target.name, target.tier, target.residents.size(), b);
    }

    static void view(IntegratedServer server, int dx, int dy, int dz, float yaw, float pitch) {
        server.execute(() -> {
            var c = target.center;
            for (var p : server.getPlayerList().getPlayers()) {
                p.getAbilities().mayfly = true;
                p.getAbilities().flying = true; // hover where the camera is put
                p.onUpdateAbilities();
            }
            int y = clearY(server.overworld(), c.getX() + dx, c.getY() + dy, c.getZ() + dz);
            run(server, String.format("tp @a %d %d %d %.1f %.1f", c.getX() + dx, y, c.getZ() + dz, yaw, pitch));
        });
    }

    /** True if a camera standing here would see out: feet and eyes in air, not in a tree or a wall. */
    static boolean open(net.minecraft.server.level.ServerLevel level, net.minecraft.core.BlockPos p) {
        return level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir();
    }

    /** The lowest height at or above y where the camera is not buried in terrain, a trunk or a roof. */
    static int clearY(net.minecraft.server.level.ServerLevel level, int x, int y, int z) {
        for (int i = 0; i < 48; i++) {
            if (open(level, new net.minecraft.core.BlockPos(x, y + i, z))) return y + i;
        }
        return y + 48;
    }

    static void run(IntegratedServer server, String cmd) {
        CommandSourceStack src = server.createCommandSourceStack().withPermission(4).withSuppressedOutput();
        server.getCommands().performPrefixedCommand(src, cmd);
    }

    static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name + ".png", mc.getMainRenderTarget(), msg -> LongNight.LOG.info("[autoshot] {}", msg.getString()));
    }
}
