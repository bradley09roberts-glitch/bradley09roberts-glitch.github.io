package com.grandline.test;

import com.grandline.GrandLine;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Server half of the automated client test (env GL_SELFTEST=client on the dedicated server): grants op to joining
 * players so the scripted client can drive /grandline test commands, and registers /gltest_dock (used by the shader
 * tour to look at a harbour). Inactive otherwise.
 */
@EventBusSubscriber(modid = GrandLine.MODID)
public final class ClientTestSupport {
    static final boolean ACTIVE = "client".equals(System.getenv("GL_SELFTEST"));

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!ACTIVE || !(e.getEntity() instanceof ServerPlayer sp)) return;
        sp.server.getPlayerList().op(sp.getGameProfile());
        GrandLine.LOGGER.info("[clienttest] opped {}", sp.getGameProfile().getName());
    }

    /** /gltest_dock: puts the player over the water off the nearest settlement dock, looking back along it to the town. */
    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        if (!ACTIVE) return;
        e.getDispatcher().register(Commands.literal("gltest_dock").requires(s -> s.hasPermission(2)).executes(c -> {
            ServerPlayer sp = c.getSource().getPlayerOrException();
            var plan = com.grandline.world.WorldPlan.of(sp.server);
            if (plan == null) return 0;
            com.grandline.world.settlement.Settlement best = null;
            double bd = Double.MAX_VALUE;
            for (var isl : plan.islands) {
                var st = isl.settlement();
                if (!st.hasDock) continue;
                double d = Math.hypot(st.dockX - sp.getX(), st.dockZ - sp.getZ());
                if (d < bd) {
                    bd = d;
                    best = st;
                }
            }
            if (best == null) return 0;
            double ux = best.dockDirX / 1000.0, uz = best.dockDirZ / 1000.0;
            double cx = best.dockTipX() + ux * 12 - uz * 12, cz = best.dockTipZ() + uz * 12 + ux * 12, cy = 74;
            double tx = best.dockX - ux * 10, tz = best.dockZ - uz * 10, ty = 66;
            float yaw = (float) Math.toDegrees(Math.atan2(-(tx - cx), tz - cz));
            float pitch = (float) -Math.toDegrees(Math.atan2(ty - cy, Math.hypot(tx - cx, tz - cz)));
            sp.teleportTo(sp.server.overworld(), cx, cy, cz, yaw, pitch);
            String msg = "Dock at " + best.dockX + "," + best.dockZ + " tip " + best.dockTipX() + "," + best.dockTipZ();
            c.getSource().sendSuccess(() -> Component.literal(msg), false);
            return 1;
        }));
    }

    private ClientTestSupport() {}
}
