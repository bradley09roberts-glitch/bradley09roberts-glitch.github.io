package com.squidgame.client;

import com.squidgame.SquidGameMod;
import com.squidgame.entity.ContestantEntity;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * {@code /squidclient npcs}: reports what this client knows about the contestant bodies (how many, how far, whether the
 * renderer would draw them). It prints to the chat and to the game log, so a player who sees something odd can paste the
 * lines into a bug report. Purely client side; it changes nothing.
 */
public final class ClientDiagnostics {
    private ClientDiagnostics() {
    }

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> dispatcher.register(
                ClientCommandManager.literal("squidclient")
                        .then(ClientCommandManager.literal("npcs").executes(c -> {
                            report(c.getSource());
                            return 1;
                        }))));
    }

    private static void report(FabricClientCommandSource src) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) {
            src.sendError(Component.literal("Not in a world."));
            return;
        }
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        List<ContestantEntity> all = new ArrayList<>();
        for (Entity e : level.entitiesForRendering()) {
            if (e instanceof ContestantEntity c) {
                all.add(c);
            }
        }
        all.sort(Comparator.comparingDouble(e -> e.distanceToSqr(cam)));
        int d8 = 0, d16 = 0, d32 = 0, d64 = 0, far = 0, distOk = 0, compiled = 0, invisible = 0, removed = 0;
        for (ContestantEntity e : all) {
            double d = Math.sqrt(e.distanceToSqr(cam));
            if (d < 8) d8++;
            else if (d < 16) d16++;
            else if (d < 32) d32++;
            else if (d < 64) d64++;
            else far++;
            if (e.shouldRender(cam.x, cam.y, cam.z)) distOk++;
            if (mc.levelRenderer.isSectionCompiled(e.blockPosition())) compiled++;
            if (e.isInvisible()) invisible++;
            if (e.isRemoved()) removed++;
        }
        List<String> lines = new ArrayList<>();
        lines.add(String.format(Locale.ROOT, "[squid client] %d contestants known; distance <8: %d, <16: %d, <32: %d, <64: %d, further: %d",
                all.size(), d8, d16, d32, d64, far));
        lines.add(String.format(Locale.ROOT, "[squid client] passing the distance check: %d, in a compiled render section: %d, invisible flag: %d, removed: %d",
                distOk, compiled, invisible, removed));
        lines.add("[squid client] " + mc.levelRenderer.getEntityStatistics() + "  (entities drawn/known, last frame)");
        for (int i = 0; i < Math.min(4, all.size()); i++) {
            ContestantEntity e = all.get(i);
            BlockPos bp = e.blockPosition();
            lines.add(String.format(Locale.ROOT, "[squid client] No. %03d  %.1f blocks away at %d %d %d  age %d  activity %s  section compiled %s",
                    e.contestantNumber(), Math.sqrt(e.distanceToSqr(cam)), bp.getX(), bp.getY(), bp.getZ(), e.tickCount,
                    e.getActivity(), mc.levelRenderer.isSectionCompiled(bp)));
        }
        for (String line : lines) {
            src.sendFeedback(Component.literal(line));
            SquidGameMod.LOGGER.info(line);
        }
    }
}
